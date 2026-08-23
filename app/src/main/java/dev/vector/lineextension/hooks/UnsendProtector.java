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

    try {
      Vector.hookAll(
          Reflect.findClass(cfg.unsend.notifiedDestroyMessageHandlerClass, lpparam.classLoader),
          cfg.unsend.methodReadBuffer,
          chain -> {
            Object result = chain.proceed();
            if (Main.options.preventUnsendMessage.enabled) {
              try {
                handleIncomingOperation(chain);
              } catch (Exception e) {
                Vector.log("Tencha: Unsend error: " + e);
              }
            }
            return result;
          });
    } catch (Throwable t) {
      Vector.log("Tencha: Unsend op hook failed: " + t);
    }

    if (cfg.unsend.unsendDestroyHandlerClass != null
        && !cfg.unsend.unsendDestroyHandlerClass.isEmpty()) {
      try {
        Vector.hookAll(
            Reflect.findClass(cfg.unsend.unsendDestroyHandlerClass, lpparam.classLoader),
            "b",
            chain -> {
              if (Main.options.preventUnsendMessage.enabled) {
                try {
                  handleDestroyHandler(chain, cfg);
                } catch (Throwable ignored) {
                }
              }
              return chain.proceed();
            });
      } catch (Throwable t) {
        Vector.log("Tencha: Destroy handler hook failed: " + t);
      }
    }

    try {
      Vector.hookAll(
          Reflect.findClass(cfg.unsend.chatMessageViewHolderClass, lpparam.classLoader),
          cfg.unsend.methodBind,
          chain -> {
            Object result = chain.proceed();
            if (Main.options.preventUnsendMessage.enabled) {
              try {
                handleViewHolderBinding(chain);
              } catch (Exception e) {
                Vector.log("Tencha: Bind error: " + e);
              }
            }
            return result;
          });
    } catch (Throwable t) {
      Vector.log("Tencha: Bind hook failed: " + t);
    }
  }

  private static void handleDestroyHandler(XposedInterface.Chain chain, LineVersion.Config cfg) {
    Method m = (Method) chain.getExecutable();
    Class<?>[] types = m.getParameterTypes();
    if (types.length != 3 || !types[1].getName().equals(cfg.unsend.operationClass)) return;

    Object aeVar = chain.getArg(1);
    if (aeVar == null) return;

    String msgId = (String) Reflect.getObjectField(aeVar, "h");
    if (msgId == null || msgId.isEmpty()) return;

    if (!unsendEvents.containsKey(msgId)) {
      Vector.log("Tencha: Blocked unsend (handler), id=" + msgId);
      persistUnsendEvent(msgId, getFormattedTime());
    }

    Reflect.setObjectField(aeVar, "h", "");

    TextView tsView = getTimestampView(msgId);
    if (tsView != null) applyUnsendIndicator(tsView, tsView.getContext(), msgId);
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

  private static void handleIncomingOperation(XposedInterface.Chain chain) throws Exception {
    LineVersion.Config cfg = LineVersion.get();
    Object operation = chain.getArg(1);
    if (operation == null || operation instanceof String) return;
    if (operation.getClass().getName().startsWith("java.")) return;

    Object type = Reflect.getObjectField(operation, cfg.unsend.operationTypeField);
    if (type == null) return;

    String typeStr = type.toString();
    if (!cfg.unsend.operationNotifiedUnsendName.equals(typeStr)
        && !cfg.unsend.operationUnsendName.equals(typeStr)) return;

    String msgId = (String) Reflect.getObjectField(operation, cfg.unsend.operationParam2Field);
    if (msgId == null || msgId.isEmpty()) return;

    if (!unsendEvents.containsKey(msgId)) {
      String time = getFormattedTime();
      Vector.log("Tencha: Blocked unsend, id=" + msgId);
      persistUnsendEvent(msgId, time);
      RuntimeReporter.working("unsend_retention", "取消イベントの保持をRuntime確認");
    }

    Object harmlessType =
        Reflect.callStaticMethod(
            type.getClass(), cfg.unsend.methodOperationTypeValueOf, cfg.unsend.operationTypeDummy);
    Reflect.setObjectField(operation, cfg.unsend.operationTypeField, harmlessType);

    TextView tsView = getTimestampView(msgId);
    if (tsView != null) applyUnsendIndicator(tsView, tsView.getContext(), msgId);
  }

  private static void handleViewHolderBinding(XposedInterface.Chain chain) throws Exception {
    LineVersion.Config cfg = LineVersion.get();
    Object viewData = chain.getArg(cfg.unsend.methodBindIndex);
    if (viewData == null) return;
    Object commonData = Reflect.callMethod(viewData, cfg.unsend.methodGetCommonData);
    if (commonData == null) return;

    String msgId = (String) Reflect.getObjectField(commonData, cfg.unsend.chatMessageIdField);
    View root = (View) Reflect.callMethod(chain.getThisObject(), cfg.unsend.methodGetItemView);
    if (root == null) return;

    TextView tsView = (TextView) root.findViewById(cfg.res.idTimestamp);
    if (tsView == null) return;

    clearTimestampViewMapping(tsView);
    resetViewProperties(tsView);
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
          if (!msgId.equals(tsView.getTag(TIMESTAMP_MSG_ID_TAG))) return;
          tsView.setCompoundDrawables(null, null, draw, null);
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
    tsView.post(
        () -> {
          tsView.setCompoundDrawables(null, null, null, null);
          tsView.setCompoundDrawablePadding(0);
          tsView.setOnClickListener(null);
          tsView.setClickable(false);
        });
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
