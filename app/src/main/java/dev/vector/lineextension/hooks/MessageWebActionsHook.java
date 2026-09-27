package dev.vector.lineextension.hooks;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;
import dev.vector.lineextension.LineVersion;
import dev.vector.lineextension.LoadParam;
import dev.vector.lineextension.Reflect;
import dev.vector.lineextension.Vector;
import dev.vector.lineextension.VectorConfig;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;

/** Adds Google Search and Google Translate to LINE's native message long-press grid. */
public final class MessageWebActionsHook implements BaseHook {
  private static final int TAG_INJECTED = 0x64010031;
  private static final ExecutorService TRANSLATION_EXECUTOR = Executors.newSingleThreadExecutor();
  private static volatile String pendingText;
  private static volatile WeakReference<View> pendingMessageView = new WeakReference<>(null);

  @Override
  public void hook(VectorConfig options, LoadParam lpparam) throws Throwable {
    LineVersion.Config.MessageContextMenu cfg = LineVersion.get().messageContextMenu;
    if (cfg.dialogClass.isEmpty() || cfg.fieldParams.isEmpty()) return;
    Class<?> dialogClass = Reflect.findClass(cfg.dialogClass, lpparam.classLoader);
    boolean constructorHooked = false;
    for (Constructor<?> constructor : dialogClass.getDeclaredConstructors()) {
      if (constructor.getParameterCount() != 8) continue;
      constructor.setAccessible(true);
      Vector.module
          .hook(constructor)
          .intercept(
              chain -> {
                Object result = chain.proceed();
                String text = readMessageText(chain.getThisObject(), cfg);
                if (text != null && !text.trim().isEmpty()) {
                  pendingText = text;
                  Object selectedView = chain.getArg(2);
                  pendingMessageView =
                      new WeakReference<>(
                          selectedView instanceof View ? (View) selectedView : null);
                }
                return result;
              });
      constructorHooked = true;
    }
    if (!constructorHooked)
      throw new NoSuchMethodError(cfg.dialogClass + " 8-argument constructor");
    Vector.module
        .hook(
            Reflect.findMethodExact(
                PopupWindow.class, "showAtLocation", View.class, int.class, int.class, int.class))
        .intercept(
            chain -> {
              try {
                PopupWindow popup = (PopupWindow) chain.getThisObject();
                String text = pendingText;
                if (text != null && !text.trim().isEmpty() && isMessageMenu(popup)) {
                  inject(popup, text, pendingMessageView);
                  pendingText = null;
                  pendingMessageView = new WeakReference<>(null);
                }
              } catch (Throwable t) {
                Vector.log("Tencha: message web actions inject failed: " + t);
              }
              return chain.proceed();
            });
  }

  private static boolean isMessageMenu(PopupWindow popup) {
    View content = popup.getContentView();
    if (content == null) return false;
    Context context = content.getContext();
    int rowContainerId = id(context, "chat_ui_message_context_menu_row_container");
    return rowContainerId != 0 && !findGroups(content, rowContainerId).isEmpty();
  }

  private static void inject(PopupWindow popup, String text, WeakReference<View> messageView) {
    View root = popup.getContentView();
    if (root == null || Boolean.TRUE.equals(root.getTag(TAG_INJECTED))) return;

    Context context = root.getContext();
    int actionContainerId = id(context, "chat_ui_message_context_menu_action_content_container");
    int rowContainerId = id(context, "chat_ui_message_context_menu_row_container");
    int titleId = id(context, "chat_ui_message_context_menu_item_title");
    int imageId = id(context, "chat_ui_message_context_menu_item_image");
    int clickId = id(context, "chat_ui_message_context_content_layout");
    int dividerId = id(context, "chat_ui_message_context_menu_item_divider");
    int greenDotId = id(context, "chat_ui_message_context_menu_item_green_dot");
    int itemLayout = layout(context, "chat_ui_message_context_menu_content_item");
    int rowLayout = layout(context, "chat_ui_message_context_menu_content_row");
    int rowDividerId = id(context, "chat_ui_message_context_menu_row_divider");
    if (actionContainerId == 0
        || rowContainerId == 0
        || titleId == 0
        || imageId == 0
        || clickId == 0
        || itemLayout == 0
        || rowLayout == 0) return;

    List<ViewGroup> rows = findGroups(root, rowContainerId);
    if (rows.isEmpty()) return;
    int columns = rows.get(0).getChildCount();
    if (columns <= 0) return;
    ColorStateList iconTint = findNativeIconTint(rows, imageId);
    int searchIcon = drawable(context, "moremenu_ic_search");
    int translateIcon = drawable(context, "camera_ic_translate_selected");
    if (searchIcon == 0 || translateIcon == 0) return;

    List<ActionSpec> actions = new ArrayList<>();
    actions.add(ActionSpec.search("Googleで検索", searchIcon, googleSearch(text)));
    actions.add(ActionSpec.translate("翻訳", translateIcon, text, messageView));
    fillBlankItems(rows, actions, popup, titleId, imageId, clickId, greenDotId, iconTint, context);

    if (!actions.isEmpty()) {
      ViewGroup actionContainer = findDeepestGroup(root, actionContainerId);
      if (actionContainer == null) return;
      LayoutInflater inflater = LayoutInflater.from(context);
      while (!actions.isEmpty()) {
        View rowRoot = inflater.inflate(rowLayout, actionContainer, false);
        ViewGroup row = rowRoot.findViewById(rowContainerId);
        View rowDivider = rowDividerId == 0 ? null : rowRoot.findViewById(rowDividerId);
        if (row == null) return;
        if (rowDivider != null) rowDivider.setVisibility(View.VISIBLE);
        for (int column = 0; column < columns; column++) {
          View item = inflater.inflate(itemLayout, row, false);
          row.addView(item);
          View divider = dividerId == 0 ? null : item.findViewById(dividerId);
          if (divider != null) divider.setVisibility(column == 0 ? View.GONE : View.VISIBLE);
          if (!actions.isEmpty()) {
            bind(
                item,
                actions.remove(0),
                popup,
                titleId,
                imageId,
                clickId,
                greenDotId,
                iconTint,
                context);
          }
        }
        actionContainer.addView(rowRoot);
      }
    }
    root.setTag(TAG_INJECTED, Boolean.TRUE);
    popup.update();
  }

  private static String readMessageText(Object dialog, LineVersion.Config.MessageContextMenu cfg) {
    try {
      Object params = Reflect.getObjectField(dialog, cfg.fieldParams);
      Object textParams = Reflect.getObjectField(params, cfg.fieldTextParams);
      Object value = Reflect.getObjectField(textParams, cfg.fieldText);
      return value instanceof CharSequence ? value.toString() : null;
    } catch (Throwable ignored) {
      return null;
    }
  }

  private static void fillBlankItems(
      List<ViewGroup> rows,
      List<ActionSpec> actions,
      PopupWindow popup,
      int titleId,
      int imageId,
      int clickId,
      int greenDotId,
      ColorStateList tint,
      Context context) {
    for (ViewGroup row : rows) {
      for (int i = 0; i < row.getChildCount() && !actions.isEmpty(); i++) {
        View item = row.getChildAt(i);
        TextView title = item.findViewById(titleId);
        if (title != null && title.getText().length() == 0) {
          bind(
              item, actions.remove(0), popup, titleId, imageId, clickId, greenDotId, tint, context);
        }
      }
    }
  }

  private static void bind(
      View item,
      ActionSpec action,
      PopupWindow popup,
      int titleId,
      int imageId,
      int clickId,
      int greenDotId,
      ColorStateList tint,
      Context context) {
    TextView title = item.findViewById(titleId);
    ImageView image = item.findViewById(imageId);
    View click = item.findViewById(clickId);
    View greenDot = greenDotId == 0 ? null : item.findViewById(greenDotId);
    if (title == null || image == null || click == null) return;
    title.setText(action.label);
    title.setContentDescription(action.label);
    image.setImageResource(action.iconRes);
    if (tint != null) image.setImageTintList(tint);
    if (greenDot != null) greenDot.setVisibility(View.GONE);
    click.setOnClickListener(
        v -> {
          popup.dismiss();
          if (action.translate) {
            translateAndReplace(context, action.originalText, action.messageView);
          } else {
            open(context, action.uri);
          }
        });
  }

  private static void open(Context context, Uri uri) {
    try {
      Intent intent = new Intent(Intent.ACTION_VIEW, uri);
      if (!(context instanceof Activity)) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      context.startActivity(intent);
    } catch (Throwable t) {
      Vector.log("Tencha: unable to open web action: " + t);
    }
  }

  private static Uri googleSearch(String text) {
    return new Uri.Builder()
        .scheme("https")
        .authority("www.google.com")
        .path("search")
        .appendQueryParameter("q", text)
        .build();
  }

  private static void translateAndReplace(
      Context context, String original, WeakReference<View> messageView) {
    TRANSLATION_EXECUTOR.execute(
        () -> {
          try {
            String translated = requestTranslation(original);
            View target = messageView == null ? null : messageView.get();
            if (target == null || translated.isEmpty())
              throw new IllegalStateException("no target");
            target.post(
                () -> {
                  TextView textView = findMessageTextView(target, original);
                  if (textView != null) {
                    textView.setText(translated);
                  } else {
                    Toast.makeText(context, "翻訳テキストを表示できませんでした", Toast.LENGTH_SHORT).show();
                  }
                });
          } catch (Throwable t) {
            Vector.log("Tencha: Google translation failed: " + t);
            new Handler(Looper.getMainLooper())
                .post(() -> Toast.makeText(context, "翻訳できませんでした", Toast.LENGTH_SHORT).show());
          }
        });
  }

  private static String requestTranslation(String original) throws Exception {
    URL url =
        new URL(
            "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=ja&dt=t");
    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
    connection.setConnectTimeout(8000);
    connection.setReadTimeout(12000);
    connection.setRequestMethod("POST");
    connection.setRequestProperty(
        "Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
    connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) Tencha/1.8");
    connection.setDoOutput(true);
    byte[] body =
        ("q=" + URLEncoder.encode(original, StandardCharsets.UTF_8.name()))
            .getBytes(StandardCharsets.UTF_8);
    try (OutputStream output = connection.getOutputStream()) {
      output.write(body);
    }
    int status = connection.getResponseCode();
    if (status < 200 || status >= 300) throw new IllegalStateException("HTTP " + status);
    StringBuilder json = new StringBuilder();
    try (BufferedReader reader =
        new BufferedReader(
            new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) json.append(line);
    } finally {
      connection.disconnect();
    }
    JSONArray segments = new JSONArray(json.toString()).getJSONArray(0);
    StringBuilder translated = new StringBuilder();
    for (int i = 0; i < segments.length(); i++) {
      JSONArray segment = segments.optJSONArray(i);
      if (segment != null && !segment.isNull(0)) translated.append(segment.optString(0, ""));
    }
    return translated.toString();
  }

  private static TextView findMessageTextView(View root, String original) {
    if (root instanceof TextView && original.contentEquals(((TextView) root).getText())) {
      return (TextView) root;
    }
    if (!(root instanceof ViewGroup)) return null;
    ViewGroup group = (ViewGroup) root;
    for (int i = 0; i < group.getChildCount(); i++) {
      TextView found = findMessageTextView(group.getChildAt(i), original);
      if (found != null) return found;
    }
    return null;
  }

  private static int id(Context context, String name) {
    return context.getResources().getIdentifier(name, "id", context.getPackageName());
  }

  private static int layout(Context context, String name) {
    return context.getResources().getIdentifier(name, "layout", context.getPackageName());
  }

  private static int drawable(Context context, String name) {
    return context.getResources().getIdentifier(name, "drawable", context.getPackageName());
  }

  private static List<ViewGroup> findGroups(View root, int targetId) {
    List<ViewGroup> result = new ArrayList<>();
    collectGroups(root, targetId, result);
    return result;
  }

  private static void collectGroups(View view, int targetId, List<ViewGroup> result) {
    if (!(view instanceof ViewGroup)) return;
    ViewGroup group = (ViewGroup) view;
    if (group.getId() == targetId) result.add(group);
    for (int i = 0; i < group.getChildCount(); i++) {
      collectGroups(group.getChildAt(i), targetId, result);
    }
  }

  private static ViewGroup findDeepestGroup(View root, int targetId) {
    List<ViewGroup> groups = findGroups(root, targetId);
    return groups.isEmpty() ? null : groups.get(groups.size() - 1);
  }

  private static ColorStateList findNativeIconTint(List<ViewGroup> rows, int imageId) {
    for (ViewGroup row : rows) {
      for (int i = 0; i < row.getChildCount(); i++) {
        ImageView image = row.getChildAt(i).findViewById(imageId);
        if (image == null || image.getDrawable() == null) continue;
        if (image.getImageTintList() != null) return image.getImageTintList();
        Integer renderedColor = sampleRenderedColor(image.getDrawable());
        if (renderedColor != null) return ColorStateList.valueOf(renderedColor);
      }
    }
    return null;
  }

  private static Integer sampleRenderedColor(Drawable drawable) {
    try {
      int width = Math.max(1, drawable.getIntrinsicWidth());
      int height = Math.max(1, drawable.getIntrinsicHeight());
      width = Math.min(width, 128);
      height = Math.min(height, 128);
      Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
      Canvas canvas = new Canvas(bitmap);
      RectState state = new RectState(drawable);
      drawable.setBounds(0, 0, width, height);
      drawable.draw(canvas);
      state.restore(drawable);
      int bestColor = 0;
      int bestAlpha = 0;
      for (int y = 0; y < height; y++) {
        for (int x = 0; x < width; x++) {
          int color = bitmap.getPixel(x, y);
          int alpha = color >>> 24;
          if (alpha > bestAlpha) {
            bestAlpha = alpha;
            bestColor = color;
          }
        }
      }
      bitmap.recycle();
      return bestAlpha > 0 ? bestColor : null;
    } catch (Throwable ignored) {
      return null;
    }
  }

  private static final class RectState {
    private final int left;
    private final int top;
    private final int right;
    private final int bottom;

    RectState(Drawable drawable) {
      left = drawable.getBounds().left;
      top = drawable.getBounds().top;
      right = drawable.getBounds().right;
      bottom = drawable.getBounds().bottom;
    }

    void restore(Drawable drawable) {
      drawable.setBounds(left, top, right, bottom);
    }
  }

  private static final class ActionSpec {
    final String label;
    final int iconRes;
    final Uri uri;
    final boolean translate;
    final String originalText;
    final WeakReference<View> messageView;

    private ActionSpec(
        String label,
        int iconRes,
        Uri uri,
        boolean translate,
        String originalText,
        WeakReference<View> messageView) {
      this.label = label;
      this.iconRes = iconRes;
      this.uri = uri;
      this.translate = translate;
      this.originalText = originalText;
      this.messageView = messageView;
    }

    static ActionSpec search(String label, int iconRes, Uri uri) {
      return new ActionSpec(label, iconRes, uri, false, null, null);
    }

    static ActionSpec translate(
        String label, int iconRes, String originalText, WeakReference<View> messageView) {
      return new ActionSpec(label, iconRes, null, true, originalText, messageView);
    }
  }
}
