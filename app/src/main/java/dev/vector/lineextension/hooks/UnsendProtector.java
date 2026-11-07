package dev.vector.lineextension.hooks;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.BitmapDrawable;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import dev.vector.lineextension.LineVersion;
import dev.vector.lineextension.LoadParam;
import dev.vector.lineextension.Main;
import dev.vector.lineextension.Reflect;
import dev.vector.lineextension.SettingsStore;
import dev.vector.lineextension.Vector;
import dev.vector.lineextension.VectorConfig;
import dev.vector.lineextension.core.RuntimeReporter;
import io.github.libxposed.api.XposedInterface;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.json.JSONObject;

public class UnsendProtector implements BaseHook {
  private static final int MAX_UNSEND_EVENTS = 10000;

  private static final int TIMESTAMP_MSG_ID_TAG = 0x7f7a0001;
  private static final int INDICATOR_TAG = 0x7f7a0002;
  private static final int TIMESTAMP_VIEW_CLEANUP_INTERVAL = 64;
  private static final Map<String, String> unsendEvents = new ConcurrentHashMap<>();
  private static final Map<String, WeakReference<TextView>> timestampViews =
      new ConcurrentHashMap<>();
  private static volatile Bitmap indicatorIcon;
  private static Toast currentToast;
  private static int timestampBindCount = 0;

  @Override
  public void hook(VectorConfig config, LoadParam lpparam) throws Throwable {
    if (!config.preventUnsendMessage.enabled) return;
    initializeUnsendCache();

    LineVersion.Config cfg = LineVersion.get();

    if (cfg.unsend.unsendDestroyHandlerClass != null
        && !cfg.unsend.unsendDestroyHandlerClass.isEmpty()) {
      try {
        Class<?> handler =
            Reflect.findClass(cfg.unsend.unsendDestroyHandlerClass, lpparam.classLoader);
        final Object handled =
            cfg.unsend.destroyResultClass.isEmpty()
                ? null
                : Reflect.newInstance(
                    Reflect.findClass(cfg.unsend.destroyResultClass, lpparam.classLoader), true);
        int registered = 0;
        for (Method method : handler.getDeclaredMethods()) {
          Class<?>[] types = method.getParameterTypes();
          if (!method.getName().equals(cfg.unsend.methodReadBuffer)
              || types.length != 3
              || !types[1].getName().equals(cfg.unsend.operationClass)) continue;
          Vector.module
              .hook(method)
              .intercept(
                  chain -> {
                    if (!Main.options.preventUnsendMessage.enabled) return chain.proceed();
                    Object operation = chain.getArg(1);
                    String messageId;
                    if (operation == null) return chain.proceed();
                    boolean retain;
                    try {
                      Object type =
                          Reflect.getObjectField(operation, cfg.unsend.operationTypeField);
                      messageId =
                          (String)
                              Reflect.getObjectField(operation, cfg.unsend.operationParam2Field);
                      retain =
                          UnsendPolicy.shouldRetain(
                              type instanceof Enum<?> ? ((Enum<?>) type).name() : null,
                              messageId,
                              cfg.unsend.operationNotifiedUnsendName,
                              cfg.unsend.operationUnsendName);
                    } catch (Throwable error) {
                      Vector.log(
                          "Tencha: Unsend operation read failed: "
                              + error.getClass().getSimpleName());
                      return chain.proceed();
                    }
                    if (!retain) return chain.proceed();
                    if (!unsendEvents.containsKey(messageId))
                      persistUnsendEvent(messageId, getFormattedTime());
                    TextView timestamp = getTimestampView(messageId);
                    if (timestamp != null)
                      applyUnsendIndicator(timestamp, timestamp.getContext(), messageId);
                    RuntimeReporter.working("unsend_retention", "取消イベントの保持をRuntime確認");
                    // 26.16: acknowledge this notification without entering the destructive
                    // DB/media path.
                    // Do not mutate the shared operation: other consumers still need its type and
                    // ID.
                    if (handled != null) return handled;
                    // Older verified handlers use the same ID lookup. Restore it before returning.
                    Reflect.setObjectField(operation, cfg.unsend.operationParam2Field, "");
                    try {
                      return chain.proceed();
                    } finally {
                      Reflect.setObjectField(operation, cfg.unsend.operationParam2Field, messageId);
                    }
                  });
          registered++;
        }
        if (registered == 0) throw new NoSuchMethodException("Unsend operation handler");
      } catch (Throwable t) {
        Vector.log("Tencha: Destroy handler hook failed: " + t);
        throw new IllegalStateException("Unsend retention registration failed", t);
      }
    }

    try {
      Vector.hookAll(
          Reflect.findClass(cfg.unsend.chatMessageViewHolderClass, lpparam.classLoader),
          cfg.unsend.methodBind,
          chain -> {
            try {
              View root = MessageItemView.get(chain.getThisObject(), cfg);
              TextView timestamp = root == null ? null : root.findViewById(cfg.res.idTimestamp);
              if (timestamp != null) {
                clearTimestampViewMapping(timestamp);
                resetViewProperties(timestamp);
              }
            } catch (Throwable error) {
              Vector.log("Tencha: Unsend cleanup failed: " + error.getClass().getSimpleName());
            }
            Object result = chain.proceed();
            if (Main.options.preventUnsendMessage.enabled) {
              try {
                handleViewHolderBinding(chain);
              } catch (Throwable e) {
                Vector.log("Tencha: Bind error: " + e);
              }
            }
            return result;
          });
      Vector.log(
          "Tencha: Unsend indicator binding hooked: "
              + cfg.unsend.chatMessageViewHolderClass
              + "#"
              + cfg.unsend.methodBind);
    } catch (Throwable t) {
      Vector.log("Tencha: Bind hook failed: " + t);
      throw new IllegalStateException("Unsend indicator registration failed", t);
    }
  }

  private static void initializeUnsendCache() {
    try {
      JSONObject json = SettingsStore.loadUnsendHistory();
      Iterator<String> keys = json.keys();
      while (keys.hasNext()) {
        String id = keys.next();
        unsendEvents.put(id, json.getString(id));
      }
    } catch (Exception e) {
      Vector.log("Tencha: Unsend history load error: " + e);
    }
  }

  private static synchronized void persistUnsendEvent(String msgId, String timestamp) {
    unsendEvents.put(msgId, timestamp);
    while (unsendEvents.size() > MAX_UNSEND_EVENTS) {
      String oldest = null;
      long oldestId = Long.MAX_VALUE;
      for (String id : unsendEvents.keySet()) {
        long numeric;
        try {
          numeric = Long.parseLong(id);
        } catch (NumberFormatException ignored) {
          numeric = Long.MIN_VALUE;
        }
        if (numeric < oldestId) {
          oldestId = numeric;
          oldest = id;
        }
      }
      if (oldest == null) break;
      unsendEvents.remove(oldest);
    }
    try {
      JSONObject json = new JSONObject();
      for (Map.Entry<String, String> entry : unsendEvents.entrySet())
        json.put(entry.getKey(), entry.getValue());
      SettingsStore.saveUnsendHistory(json);
    } catch (Exception e) {
      Vector.log("Tencha: Unsend history save error: " + e);
    }
  }

  private static String getFormattedTime() {
    return new SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault()).format(new Date());
  }

  private static void handleViewHolderBinding(XposedInterface.Chain chain) throws Exception {
    LineVersion.Config cfg = LineVersion.get();
    Object viewData = chain.getArg(cfg.unsend.methodBindIndex);
    if (viewData == null) return;
    Object commonData = Reflect.callMethod(viewData, cfg.unsend.methodGetCommonData);
    if (commonData == null) return;

    String msgId = (String) Reflect.getObjectField(commonData, cfg.unsend.chatMessageIdField);
    View root = MessageItemView.get(chain.getThisObject(), cfg);
    if (root == null) return;

    TextView tsView = (TextView) root.findViewById(cfg.res.idTimestamp);
    if (tsView == null) return;

    if (msgId != null && !msgId.isEmpty()) {
      tsView.setTag(TIMESTAMP_MSG_ID_TAG, msgId);
      timestampViews.put(msgId, new WeakReference<>(tsView));
      if ((++timestampBindCount % TIMESTAMP_VIEW_CLEANUP_INTERVAL) == 0) {
        cleanupStaleTimestampViews();
      }
      if (unsendEvents.containsKey(msgId)) applyUnsendIndicator(tsView, root.getContext(), msgId);
    }
  }

  private static void applyUnsendIndicator(
      final TextView tsView, final Context context, final String msgId) {
    Bitmap rawIcon = resolveIndicatorIcon(context);
    if (rawIcon == null) return;
    Context appContext = context.getApplicationContext();
    final Context toastContext = appContext != null ? appContext : context;
    float dens = tsView.getResources().getDisplayMetrics().density;
    final int targetPx = (int) (14 * dens);
    int padPx = (int) (3 * dens);

    Bitmap scaled = Bitmap.createScaledBitmap(rawIcon, targetPx, targetPx, true);
    Bitmap colored = applyTint(scaled, Color.RED);
    final BitmapDrawable draw = new BitmapDrawable(tsView.getResources(), colored);
    draw.setBounds(0, 0, targetPx, targetPx);

    tsView.post(
        () -> {
          if (!Main.options.preventUnsendMessage.enabled
              || !msgId.equals(tsView.getTag(TIMESTAMP_MSG_ID_TAG))) return;
          if (tsView.getTag(INDICATOR_TAG) == null) {
            tsView.setTag(INDICATOR_TAG, new IndicatorState(tsView));
          }
          IndicatorState state = (IndicatorState) tsView.getTag(INDICATOR_TAG);
          tsView.setCompoundDrawables(
              state.drawables[0], state.drawables[1], draw, state.drawables[3]);
          tsView.setCompoundDrawablePadding(padPx);
          tsView.setOnClickListener(
              v -> {
                String time = unsendEvents.get(msgId);
                if (time != null) {
                  if (currentToast != null) currentToast.cancel();
                  currentToast =
                      Toast.makeText(
                          toastContext,
                          dev.vector.lineextension.utils.ModuleStrings.UNSET_TIME_PREFIX + time,
                          Toast.LENGTH_SHORT);
                  currentToast.show();
                }
              });
        });
  }

  private static TextView getTimestampView(String msgId) {
    WeakReference<TextView> ref = timestampViews.get(msgId);
    if (ref == null) return null;
    TextView view = ref.get();
    if (view == null || !msgId.equals(view.getTag(TIMESTAMP_MSG_ID_TAG))) {
      timestampViews.remove(msgId);
      return null;
    }
    return view;
  }

  private static void clearTimestampViewMapping(TextView view) {
    Object previousMsgId = view.getTag(TIMESTAMP_MSG_ID_TAG);
    if (previousMsgId instanceof String) {
      WeakReference<TextView> ref = timestampViews.get(previousMsgId);
      if (ref == null || ref.get() == view) {
        timestampViews.remove(previousMsgId);
      }
    }
    view.setTag(TIMESTAMP_MSG_ID_TAG, null);
  }

  private static void cleanupStaleTimestampViews() {
    for (Map.Entry<String, WeakReference<TextView>> entry : timestampViews.entrySet()) {
      TextView view = entry.getValue().get();
      if (view == null || !entry.getKey().equals(view.getTag(TIMESTAMP_MSG_ID_TAG))) {
        timestampViews.remove(entry.getKey());
      }
    }
  }

  private static void resetViewProperties(final TextView tsView) {
    Object tag = tsView.getTag(INDICATOR_TAG);
    if (!(tag instanceof IndicatorState)) return;
    IndicatorState state = (IndicatorState) tag;
    tsView.setCompoundDrawables(
        state.drawables[0], state.drawables[1], state.drawables[2], state.drawables[3]);
    tsView.setCompoundDrawablePadding(state.padding);
    tsView.setOnClickListener(null);
    tsView.setClickable(state.clickable);
    tsView.setTag(INDICATOR_TAG, null);
  }

  private static final class IndicatorState {
    final android.graphics.drawable.Drawable[] drawables;
    final int padding;
    final boolean clickable;

    IndicatorState(TextView view) {
      drawables = view.getCompoundDrawables();
      padding = view.getCompoundDrawablePadding();
      clickable = view.isClickable();
    }
  }

  private static Bitmap resolveIndicatorIcon(Context ctx) {
    if (indicatorIcon != null) return indicatorIcon;
    try {
      Context modCtx =
          ctx.createPackageContext("dev.vector.lineextension", Context.CONTEXT_IGNORE_SECURITY);
      int resId =
          modCtx
              .getResources()
              .getIdentifier("message_off", "drawable", "dev.vector.lineextension");
      if (resId != 0) {
        indicatorIcon = BitmapFactory.decodeResource(modCtx.getResources(), resId);
      }
    } catch (Exception ignored) {
    }
    return indicatorIcon;
  }

  private static Bitmap applyTint(Bitmap src, int color) {
    Bitmap out = Bitmap.createBitmap(src.getWidth(), src.getHeight(), Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(out);
    Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    p.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
    canvas.drawBitmap(src, 0, 0, p);
    return out;
  }
}
