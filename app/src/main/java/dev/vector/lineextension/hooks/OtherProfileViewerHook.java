package dev.vector.lineextension.hooks;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;
import dev.vector.lineextension.LineVersion;
import dev.vector.lineextension.LoadParam;
import dev.vector.lineextension.Main;
import dev.vector.lineextension.Reflect;
import dev.vector.lineextension.SettingsStore;
import dev.vector.lineextension.Vector;
import dev.vector.lineextension.VectorConfig;
import dev.vector.lineextension.utils.ModuleStrings;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Read-only enhancements for another user's profile. Exact mappings are supplied per LINE build.
 */
public final class OtherProfileViewerHook implements BaseHook {

  private static final String MENU_TAG = "tencha_profile_object_mode";
  private static final String NON_FRIEND_ENTRY_TAG = "tencha_non_friend_profile_more";
  private static final String SAVE_TAG = "tencha_profile_image_save";
  private static final Map<Activity, State> STATES =
      Collections.synchronizedMap(new WeakHashMap<>());
  private static final Map<Activity, Object> POPUPS =
      Collections.synchronizedMap(new WeakHashMap<>());

  private LineVersion.Config cfg;
  private ClassLoader classLoader;

  private static final class State {
    final Activity activity;
    final Object baseViewModel;
    final Object decoViewModel;
    final Object editor;
    final int editingMenuId;
    boolean active;
    ViewTreeObserver.OnGlobalLayoutListener menuGuard;
    ViewTreeObserver.OnPreDrawListener preDrawMenuGuard;
    ViewTreeObserver.OnGlobalLayoutListener nonFriendEntryGuard;
    ImageView nonFriendEntry;

    State(Activity activity, Object baseViewModel, Object decoViewModel, Object editor) {
      this.activity = activity;
      this.baseViewModel = baseViewModel;
      this.decoViewModel = decoViewModel;
      this.editor = editor;
      this.editingMenuId = id(activity, "id", "user_profile_deco_menu");
    }
  }

  @Override
  public void hook(VectorConfig config, LoadParam lpparam) throws Throwable {
    if (!config.enhanceOtherProfiles.enabled) return;
    cfg = LineVersion.get();
    classLoader = lpparam.classLoader;
    if (cfg == null || cfg.profileViewer.profileActivityClass.isEmpty()) return;

    hookDecorationController();
    hookPopup();
    hookPopupWindowShow();
    hookEditorActions();
    hookSaveBarrier();
    hookProfileLifecycle();
    hookNativeImageViewer(cfg.profileViewer.profileImageViewerActivityClass, "imageviewer_image");
    hookNativeImageViewer(cfg.profileViewer.coverViewerActivityClass, "cover_image_view");
  }

  private boolean enabled() {
    return SettingsStore.get(
        Main.options.enhanceOtherProfiles.key, Main.options.enhanceOtherProfiles.enabled);
  }

  private void hookDecorationController() throws Throwable {
    Class<?> controller = Reflect.findClass(cfg.profileViewer.decoControllerClass, classLoader);
    Constructor<?> ctor = Reflect.findConstructorExact(controller, "tm6.a", "xl6.b", "k.d");
    Vector.module
        .hook(ctor)
        .intercept(
            chain -> {
              Object result = chain.proceed();
              if (!enabled()) return result;
              try {
                Object hostController = chain.getThisObject();
                Object decoVm =
                    Reflect.getObjectField(
                        hostController, cfg.profileViewer.controllerDecoViewModelField);
                if (Boolean.TRUE.equals(
                    Reflect.getObjectField(decoVm, cfg.profileViewer.ownProfileField))) {
                  return result;
                }
                Context context = (Context) Reflect.getObjectField(hostController, "l");
                Activity activity = activityFrom(context);
                if (activity == null) return result;

                Object renderer =
                    Reflect.getObjectField(
                        hostController, cfg.profileViewer.controllerRendererField);
                Object analytics =
                    Reflect.newInstance(
                        Reflect.findClass(cfg.profileViewer.analyticsClass, classLoader),
                        false,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null);
                Object editor =
                    Reflect.newInstance(
                        Reflect.findClass(cfg.profileViewer.decoEditorControllerClass, classLoader),
                        chain.getArg(0),
                        chain.getArg(1),
                        renderer,
                        chain.getArg(2),
                        analytics);
                Reflect.setObjectField(
                    hostController, cfg.profileViewer.controllerRendererField, editor);
                Object baseVm = Reflect.getObjectField(decoVm, "c");
                State state = new State(activity, baseVm, decoVm, editor);
                STATES.put(activity, state);
                installNonFriendEntryGuard(state);
                Object popup = POPUPS.get(activity);
                if (popup != null) injectPopupRow(activity, popup);
                Vector.log("Tencha: read-only profile decoration controller attached");
              } catch (Throwable t) {
                Vector.log("Tencha: profile decoration controller attach failed: " + t);
              }
              return result;
            });
  }

  private void hookPopup() throws Throwable {
    Class<?> popup = Reflect.findClass(cfg.profileViewer.popupClass, classLoader);
    Vector.module
        .hook(Reflect.findConstructorExact(popup, Context.class))
        .intercept(
            chain -> {
              Object result = chain.proceed();
              if (!enabled()) return result;
              try {
                Activity activity = activityFrom((Context) chain.getArg(0));
                if (activity == null
                    || !activity
                        .getClass()
                        .getName()
                        .equals(cfg.profileViewer.profileActivityClass)) return result;
                POPUPS.put(activity, chain.getThisObject());
                if (isOtherProfileActivity(activity)) {
                  injectPopupRow(activity, chain.getThisObject());
                }
              } catch (Throwable t) {
                Vector.log("Tencha: object mode menu injection failed: " + t);
              }
              return result;
            });
  }

  private void injectPopupRow(Activity activity, Object popup) {
    Object binding = Reflect.getObjectField(popup, cfg.profileViewer.popupBindingField);
    LinearLayout root =
        (LinearLayout) Reflect.getObjectField(binding, cfg.profileViewer.bindingRootField);
    if (root.findViewWithTag(MENU_TAG) != null) return;
    root.setOrientation(LinearLayout.VERTICAL);
    int layoutId = id(activity, "layout", "userprofile_more_options_popup_item");
    View row = LayoutInflater.from(activity).inflate(layoutId, root, false);
    row.setTag(MENU_TAG);
    TextView title = row.findViewById(id(activity, "id", "title"));
    title.setText(ModuleStrings.PROFILE_OBJECT_MODE);
    row.setOnClickListener(
        v -> {
          try {
            ((PopupWindow) Reflect.getObjectField(popup, cfg.profileViewer.popupWindowField))
                .dismiss();
            State state = STATES.get(activity);
            if (state != null) setObjectMode(state, !state.active);
          } catch (Throwable t) {
            Vector.log("Tencha: object mode toggle failed: " + t);
          }
        });
    root.addView(row);
    ((PopupWindow) Reflect.getObjectField(popup, cfg.profileViewer.popupWindowField))
        .setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
  }

  private void hookPopupWindowShow() throws Throwable {
    hookPopupShowMethod("showAsDropDown", View.class);
    hookPopupShowMethod("showAsDropDown", View.class, int.class, int.class);
    hookPopupShowMethod("showAsDropDown", View.class, int.class, int.class, int.class);
    hookPopupShowMethod("showAtLocation", View.class, int.class, int.class, int.class);
  }

  private void hookPopupShowMethod(String name, Object... params) throws Throwable {
    Vector.module
        .hook(Reflect.findMethodExact(PopupWindow.class, name, params))
        .intercept(
            chain -> {
              try {
                PopupWindow window = (PopupWindow) chain.getThisObject();
                View anchor = (View) chain.getArg(0);
                Activity activity = activityFrom(anchor.getContext());
                State state = activity == null ? null : STATES.get(activity);
                View content = window.getContentView();
                int recyclerId = activity == null ? 0 : id(activity, "id", "user_profile_more_rv");
                if (state != null
                    && content instanceof LinearLayout
                    && recyclerId != 0
                    && content.findViewById(recyclerId) != null) {
                  injectPopupContent(activity, window, (LinearLayout) content);
                }
              } catch (Throwable t) {
                Vector.log("Tencha: object menu show hook failed: " + t);
              }
              return chain.proceed();
            });
  }

  private void injectPopupContent(Activity activity, PopupWindow window, LinearLayout root) {
    if (root.findViewWithTag(MENU_TAG) != null) return;
    root.setOrientation(LinearLayout.VERTICAL);
    int layoutId = id(activity, "layout", "userprofile_more_options_popup_item");
    View row = LayoutInflater.from(activity).inflate(layoutId, root, false);
    row.setTag(MENU_TAG);
    ((TextView) row.findViewById(id(activity, "id", "title")))
        .setText(ModuleStrings.PROFILE_OBJECT_MODE);
    row.setOnClickListener(
        v -> {
          window.dismiss();
          State state = STATES.get(activity);
          if (state != null) setObjectMode(state, !state.active);
        });
    root.addView(row);
    window.setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
    root.requestLayout();
  }

  private void hookEditorActions() throws Throwable {
    Class<?> editorClass =
        Reflect.findClass(cfg.profileViewer.decoEditorControllerClass, classLoader);
    Vector.module
        .hook(Reflect.findMethodExact(editorClass, cfg.profileViewer.methodOpenDecorationEditor))
        .intercept(
            chain -> {
              State state = stateForEditor(chain.getThisObject());
              if (state == null || !state.active) return chain.proceed();
              showSelectedObject(state);
              return null;
            });

    Vector.module
        .hook(Reflect.findMethodExact(editorClass, "L", String.class))
        .intercept(
            chain -> {
              Object result = chain.proceed();
              State state = stateForEditor(chain.getThisObject());
              if (state != null && state.active) {
                state
                    .activity
                    .getWindow()
                    .getDecorView()
                    .post(() -> configureSelectionFrame(state));
              }
              return result;
            });
  }

  private void hookSaveBarrier() {
    Class<?> vmClass = Reflect.findClass(cfg.profileViewer.decoViewModelClass, classLoader);
    for (Method method : vmClass.getDeclaredMethods()) {
      if (!method.getName().equals(cfg.profileViewer.methodSaveDecorations)) continue;
      method.setAccessible(true);
      Vector.module
          .hook(method)
          .intercept(
              chain -> {
                Object vm = chain.getThisObject();
                if (!Boolean.TRUE.equals(
                    Reflect.getObjectField(vm, cfg.profileViewer.ownProfileField))) {
                  Vector.log("Tencha: blocked decoration update from read-only profile mode");
                  return null;
                }
                return chain.proceed();
              });
    }
  }

  private void hookProfileLifecycle() throws Throwable {
    Class<?> activity = Reflect.findClass(cfg.profileViewer.profileActivityClass, classLoader);
    Vector.module
        .hook(Reflect.findMethodExact(activity, "onDestroy"))
        .intercept(
            chain -> {
              Activity host = (Activity) chain.getThisObject();
              State state = STATES.remove(host);
              POPUPS.remove(host);
              if (state != null) {
                removeNonFriendEntryGuard(state);
                resetObjectMode(state);
              }
              return chain.proceed();
            });
  }

  private void hookNativeImageViewer(String className, String imageIdName) throws Throwable {
    Class<?> activityClass = Reflect.findClass(className, classLoader);
    Vector.module
        .hook(Reflect.findMethodExact(activityClass, "onCreate", Bundle.class))
        .intercept(
            chain -> {
              Object result = chain.proceed();
              if (!enabled()) return result;
              Activity activity = (Activity) chain.getThisObject();
              String midKey = "imageviewer_image".equals(imageIdName) ? "user_mid" : "USER_MID";
              String mid = activity.getIntent().getStringExtra(midKey);
              if (mid == null || mid.isEmpty()) return result;
              if (activity.getIntent().getBooleanExtra("show_profile_image_change_button", false)
                  || activity
                      .getIntent()
                      .getBooleanExtra("SHOW_PROFILE_COVER_CHANGE_BUTTON", false)) return result;
              activity
                  .getWindow()
                  .getDecorView()
                  .post(() -> attachNativeSaveButton(activity, imageIdName));
              return result;
            });
  }

  private void setObjectMode(State state, boolean active) {
    if (active == state.active) return;
    state.active = active;
    if (active) {
      installMenuGuard(state);
      Reflect.callMethod(state.baseViewModel, cfg.profileViewer.methodSetEditMode, true);
      hideEditingActions(state);
      state
          .activity
          .getWindow()
          .getDecorView()
          .postDelayed(() -> configureReadOnlyEditor(state), 100);
    } else {
      resetObjectMode(state);
    }
  }

  private void resetObjectMode(State state) {
    state.active = false;
    removeMenuGuard(state);
    try {
      Reflect.callMethod(state.decoViewModel, cfg.profileViewer.methodResetDecorations);
    } catch (Throwable t) {
      Vector.log("Tencha: decoration reset failed: " + t);
    }
    try {
      Reflect.callMethod(state.baseViewModel, cfg.profileViewer.methodSetEditMode, false);
    } catch (Throwable t) {
      Vector.log("Tencha: object mode close failed: " + t);
    }
  }

  private void configureReadOnlyEditor(State state) {
    if (!state.active || state.activity.isFinishing()) return;
    View cancel = find(state.activity, "cancel");
    View save = find(state.activity, "save");
    View clear = find(state.activity, "clear");
    View cover = find(state.activity, "cover");
    View action = find(state.activity, "action");
    if (save != null) save.setVisibility(View.GONE);
    if (cover != null) cover.setVisibility(View.GONE);
    if (action != null) action.setVisibility(View.GONE);
    hideAllEditingMenus(state);
    if (cancel instanceof TextView) {
      ((TextView) cancel).setText(ModuleStrings.PROFILE_OBJECT_MODE_END);
      cancel.setOnClickListener(v -> resetObjectMode(state));
    }
    if (clear instanceof TextView) {
      ((TextView) clear).setText("オブジェクト一覧");
      clear.setEnabled(true);
      clear.setVisibility(View.VISIBLE);
      clear.setOnClickListener(v -> showObjectList(state));
    }
    configureSelectionFrame(state);
    // LINE can create more than one menu with this ID, including after re-entering edit mode.
    // Keep the delayed passes for its asynchronous initial setup as well as the layout guard.
    state.activity.getWindow().getDecorView().postDelayed(() -> hideEditingActions(state), 350);
    state.activity.getWindow().getDecorView().postDelayed(() -> hideEditingActions(state), 1000);
  }

  private void hideEditingActions(State state) {
    if (!state.active || state.activity.isFinishing()) return;
    View save = find(state.activity, "save");
    View cover = find(state.activity, "cover");
    View action = find(state.activity, "action");
    if (save != null) save.setVisibility(View.GONE);
    if (cover != null) cover.setVisibility(View.GONE);
    if (action != null) action.setVisibility(View.GONE);
    hideAllEditingMenus(state);
  }

  private void installMenuGuard(State state) {
    if (state.menuGuard != null) return;
    state.menuGuard =
        () -> {
          if (state.active) hideAllEditingMenus(state);
        };
    state.preDrawMenuGuard = () -> !state.active || !hideAllEditingMenus(state);
    ViewTreeObserver observer = state.activity.getWindow().getDecorView().getViewTreeObserver();
    observer.addOnGlobalLayoutListener(state.menuGuard);
    observer.addOnPreDrawListener(state.preDrawMenuGuard);
  }

  private void removeMenuGuard(State state) {
    if (state.menuGuard == null) return;
    ViewTreeObserver observer = state.activity.getWindow().getDecorView().getViewTreeObserver();
    observer.removeOnGlobalLayoutListener(state.menuGuard);
    observer.removeOnPreDrawListener(state.preDrawMenuGuard);
    state.menuGuard = null;
    state.preDrawMenuGuard = null;
  }

  private void installNonFriendEntryGuard(State state) {
    if (state.nonFriendEntryGuard != null) return;
    state.nonFriendEntryGuard = () -> updateNonFriendEntry(state);
    state
        .activity
        .getWindow()
        .getDecorView()
        .getViewTreeObserver()
        .addOnGlobalLayoutListener(state.nonFriendEntryGuard);
    state.activity.getWindow().getDecorView().post(() -> updateNonFriendEntry(state));
  }

  private void removeNonFriendEntryGuard(State state) {
    if (state.nonFriendEntryGuard == null) return;
    state
        .activity
        .getWindow()
        .getDecorView()
        .getViewTreeObserver()
        .removeOnGlobalLayoutListener(state.nonFriendEntryGuard);
    state.nonFriendEntryGuard = null;
  }

  private void updateNonFriendEntry(State state) {
    if (state.activity.isFinishing()) return;
    boolean nonFriend = false;
    try {
      Object contactState = Reflect.getObjectField(state.baseViewModel, "r");
      Object contact = Reflect.callMethod(contactState, "getValue");
      nonFriend = contact != null && !Boolean.TRUE.equals(Reflect.callMethod(contact, "b"));
    } catch (Throwable ignored) {
      // Do not expose the entry until LINE has supplied a definite non-friend contact state.
    }
    if (!nonFriend) {
      if (state.nonFriendEntry != null) state.nonFriendEntry.setVisibility(View.GONE);
      return;
    }
    LinearLayout header = (LinearLayout) find(state.activity, "user_profile_header_button_binding");
    ImageView nativeMore = (ImageView) find(state.activity, "user_profile_more_button");
    if (header == null || nativeMore == null) return;
    if (state.nonFriendEntry == null || state.nonFriendEntry.getParent() != header) {
      ImageView button = new ImageView(state.activity);
      Drawable icon = nativeMore.getDrawable();
      if (icon != null && icon.getConstantState() != null) {
        icon = icon.getConstantState().newDrawable(state.activity.getResources()).mutate();
      }
      button.setImageDrawable(icon);
      button.setImageTintList(nativeMore.getImageTintList());
      button.setScaleType(nativeMore.getScaleType());
      button.setContentDescription(ModuleStrings.PROFILE_OBJECT_MODE);
      button.setTag(NON_FRIEND_ENTRY_TAG);
      ViewGroup.LayoutParams original = nativeMore.getLayoutParams();
      LinearLayout.LayoutParams params =
          new LinearLayout.LayoutParams(original.width, original.height);
      if (original instanceof ViewGroup.MarginLayoutParams) {
        ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) original;
        params.setMarginStart(margins.getMarginStart());
        params.setMarginEnd(margins.getMarginEnd());
        params.topMargin = margins.topMargin;
        params.bottomMargin = margins.bottomMargin;
      }
      button.setOnClickListener(v -> showNonFriendObjectModeMenu(state, button));
      header.addView(button, header.indexOfChild(nativeMore), params);
      state.nonFriendEntry = button;
    }
    if (state.nonFriendEntry.getVisibility() != View.VISIBLE) {
      state.nonFriendEntry.setVisibility(View.VISIBLE);
    }
  }

  private void showNonFriendObjectModeMenu(State state, View anchor) {
    try {
      Object popup =
          Reflect.newInstance(
              Reflect.findClass(cfg.profileViewer.popupClass, classLoader), state.activity);
      injectPopupRow(state.activity, popup);
      PopupWindow window =
          (PopupWindow) Reflect.getObjectField(popup, cfg.profileViewer.popupWindowField);
      int offsetId = id(state.activity, "dimen", "userprofile_more_option_popup_x_offset");
      int xOffset =
          offsetId == 0 ? 0 : -state.activity.getResources().getDimensionPixelSize(offsetId);
      window.showAsDropDown(anchor, xOffset, 0, Gravity.END);
    } catch (Throwable t) {
      Vector.log("Tencha: non-friend object mode menu failed: " + t);
    }
  }

  private boolean hideAllEditingMenus(State state) {
    return state.editingMenuId != 0
        && hideViewsWithId(state.activity.getWindow().getDecorView(), state.editingMenuId);
  }

  private static boolean hideViewsWithId(View view, int targetId) {
    boolean changed = false;
    if (view.getId() == targetId && view.getVisibility() != View.GONE) {
      view.setVisibility(View.GONE);
      changed = true;
    }
    if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++) {
        changed |= hideViewsWithId(group.getChildAt(i), targetId);
      }
    }
    return changed;
  }

  private void configureSelectionFrame(State state) {
    if (!state.active) return;
    try {
      Object frame = Reflect.getObjectField(state.editor, "F");
      if (frame == null) return;
      ImageView delete = (ImageView) Reflect.getObjectField(frame, "b");
      ImageView edit = (ImageView) Reflect.getObjectField(frame, "c");
      delete.setVisibility(View.GONE);
      boolean hasImage = selectedMedia(state) != null || selectedObjectDrawable(state) != null;
      edit.setVisibility(hasImage ? View.VISIBLE : View.GONE);
      edit.setContentDescription(ModuleStrings.PROFILE_IMAGE_VIEW);
      edit.setOnClickListener(v -> showSelectedObject(state));
    } catch (Throwable t) {
      Vector.log("Tencha: selection frame read-only setup failed: " + t);
    }
  }

  @SuppressWarnings("unchecked")
  private void showObjectList(State state) {
    try {
      List<Object> objects =
          new ArrayList<>((List<Object>) Reflect.getObjectField(state.decoViewModel, "i"));
      List<String> labels = new ArrayList<>();
      List<Object> selectable = new ArrayList<>();
      int layer = 1;
      for (Object object : objects) {
        Object type = Reflect.callMethod(object, "b");
        if (type != null && "COVER".equals(type.toString())) continue;
        labels.add("" + layer++ + "  " + (type == null ? "OBJECT" : type.toString()));
        selectable.add(object);
      }
      new AlertDialog.Builder(state.activity)
          .setTitle("オブジェクト一覧")
          .setItems(
              labels.toArray(new String[0]),
              (dialog, which) -> {
                Object object = selectable.get(which);
                String id = (String) Reflect.getObjectField(object, "a");
                Reflect.callMethod(state.decoViewModel, "D7", id);
              })
          .setNegativeButton(ModuleStrings.COMMON_CLOSE, null)
          .show();
    } catch (Throwable t) {
      Vector.log("Tencha: object list failed: " + t);
    }
  }

  private void showSelectedObject(State state) {
    Object media = selectedMedia(state);
    if (media != null) {
      showImageDialog(state, null, media);
      return;
    }
    Drawable drawable = selectedObjectDrawable(state);
    if (drawable == null) {
      Toast.makeText(state.activity, "このオブジェクトは画像ではありません", Toast.LENGTH_SHORT).show();
      return;
    }
    showImageDialog(state, drawable, null);
  }

  private Object selectedMedia(State state) {
    try {
      Object selected =
          Reflect.callMethod(state.decoViewModel, cfg.profileViewer.methodSelectedDecoration);
      if (selected == null || !"PFRAME".equals(String.valueOf(Reflect.callMethod(selected, "b")))) {
        return null;
      }
      Object items = Reflect.getObjectField(selected, "j");
      if (!(items instanceof List)) return null;
      for (Object item : (List<?>) items) {
        Object value = Reflect.getObjectField(item, "c");
        if ("MEDIA".equals(Reflect.getObjectField(item, "b"))
            && value != null
            && value.getClass().getName().equals("cn6.y$e")) {
          return value;
        }
      }
    } catch (Throwable t) {
      Vector.log("Tencha: profile media lookup failed: " + t);
    }
    return null;
  }

  private Drawable selectedObjectDrawable(State state) {
    try {
      Object selected =
          Reflect.callMethod(state.decoViewModel, cfg.profileViewer.methodSelectedDecoration);
      if (selected == null) return null;
      if ("PFRAME".equals(String.valueOf(Reflect.callMethod(selected, "b")))) return null;
      Object renderer = Reflect.getObjectField(state.editor, cfg.profileViewer.editorRendererField);
      View objectView =
          (View)
              Reflect.callMethod(
                  renderer, cfg.profileViewer.methodGetDecorationView, selected, null);
      if (objectView == null) return null;
      int contentId = id(state.activity, "id", "userprofile_deco_content");
      View exact = contentId == 0 ? null : objectView.findViewById(contentId);
      if (exact instanceof ImageView) return ((ImageView) exact).getDrawable();
      return findLargestDrawable(objectView);
    } catch (Throwable t) {
      Vector.log("Tencha: selected decoration image lookup failed: " + t);
      return null;
    }
  }

  private void showImageDialog(State state, Drawable drawable, Object media) {
    try {
      Activity activity = state.activity;
      Dialog dialog = new Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
      FrameLayout root = new FrameLayout(activity);
      ImageView zoom =
          (ImageView)
              Reflect.newInstance(
                  Reflect.findClass(
                      "jp.naver.line.android.common.view.media.ZoomImageView", classLoader),
                  activity);
      if (media != null) {
        Object renderer =
            Reflect.getObjectField(state.editor, cfg.profileViewer.editorRendererField);
        Object loader = Reflect.getObjectField(renderer, "j");
        Object request = Reflect.callMethod(loader, "b", media, null, true, false);
        if (request == null) throw new IllegalStateException("LINE media URL unavailable");
        Reflect.callMethod(request, "S", zoom);
      } else {
        zoom.setImageBitmap(toBitmap(drawable));
      }
      root.addView(
          zoom,
          new FrameLayout.LayoutParams(
              ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
      TextView close = headerButton(activity, "×");
      FrameLayout.LayoutParams closeLp =
          new FrameLayout.LayoutParams(dp(activity, 44), dp(activity, 44));
      closeLp.gravity = Gravity.TOP | Gravity.START;
      closeLp.setMargins(dp(activity, 8), dp(activity, 8), 0, 0);
      close.setLayoutParams(closeLp);
      close.setOnClickListener(v -> dialog.dismiss());
      root.addView(close);
      TextView save = headerButton(activity, ModuleStrings.PROFILE_IMAGE_SAVE);
      FrameLayout.LayoutParams saveLp =
          new FrameLayout.LayoutParams(dp(activity, 72), dp(activity, 38));
      saveLp.gravity = Gravity.TOP | Gravity.END;
      saveLp.setMargins(0, dp(activity, 11), dp(activity, 13), 0);
      save.setLayoutParams(saveLp);
      save.setOnClickListener(v -> saveDrawable(activity, zoom.getDrawable()));
      root.addView(save);
      dialog.setContentView(root);
      dialog.show();
    } catch (Throwable t) {
      Vector.log("Tencha: decoration image viewer failed: " + t);
    }
  }

  private void attachNativeSaveButton(Activity activity, String imageIdName) {
    try {
      View image = find(activity, imageIdName);
      View edit = find(activity, "edit_button");
      if (!(image instanceof ImageView) || !(edit instanceof ViewGroup)) return;
      if (edit.getTag() != null && SAVE_TAG.equals(edit.getTag())) return;
      edit.setTag(SAVE_TAG);
      edit.setVisibility(View.VISIBLE);
      edit.setOnClickListener(v -> saveDrawable(activity, ((ImageView) image).getDrawable()));
      TextView label = findTextView((ViewGroup) edit);
      if (label != null) label.setText(ModuleStrings.PROFILE_IMAGE_SAVE);
      ImageView icon = findImageView((ViewGroup) edit);
      int download = id(activity, "drawable", "download_ic_download");
      if (download == 0) download = id(activity, "drawable", "navi_top_overlay_download");
      if (download == 0) download = id(activity, "drawable", "ic_download");
      if (icon != null && download != 0) icon.setImageResource(download);
    } catch (Throwable t) {
      Vector.log("Tencha: profile image save button failed: " + t);
    }
  }

  private void saveDrawable(Context context, Drawable drawable) {
    if (drawable == null) {
      Toast.makeText(context, ModuleStrings.PROFILE_IMAGE_SAVE_FAILED, Toast.LENGTH_SHORT).show();
      return;
    }
    Uri uri = null;
    try {
      Bitmap bitmap = toBitmap(drawable);
      ContentValues values = new ContentValues();
      values.put(
          MediaStore.Images.Media.DISPLAY_NAME, "Tencha_" + System.currentTimeMillis() + ".png");
      values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
      if (Build.VERSION.SDK_INT >= 29) {
        values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Tencha");
        values.put(MediaStore.Images.Media.IS_PENDING, 1);
      }
      uri =
          context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
      if (uri == null) throw new IllegalStateException("MediaStore insert returned null");
      try (OutputStream out = context.getContentResolver().openOutputStream(uri)) {
        if (out == null || !bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
          throw new IllegalStateException("PNG write failed");
        }
      }
      if (Build.VERSION.SDK_INT >= 29) {
        ContentValues ready = new ContentValues();
        ready.put(MediaStore.Images.Media.IS_PENDING, 0);
        context.getContentResolver().update(uri, ready, null, null);
      }
      Toast.makeText(context, ModuleStrings.PROFILE_IMAGE_SAVED, Toast.LENGTH_SHORT).show();
    } catch (Throwable t) {
      if (uri != null) context.getContentResolver().delete(uri, null, null);
      Vector.log("Tencha: image save failed: " + t);
      Toast.makeText(context, ModuleStrings.PROFILE_IMAGE_SAVE_FAILED, Toast.LENGTH_SHORT).show();
    }
  }

  private static Bitmap toBitmap(Drawable drawable) {
    if (drawable instanceof BitmapDrawable) {
      Bitmap bitmap = ((BitmapDrawable) drawable).getBitmap();
      if (bitmap != null) return bitmap;
    }
    int width = Math.max(1, drawable.getIntrinsicWidth());
    int height = Math.max(1, drawable.getIntrinsicHeight());
    Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(bitmap);
    int left = drawable.getBounds().left;
    int top = drawable.getBounds().top;
    int right = drawable.getBounds().right;
    int bottom = drawable.getBounds().bottom;
    drawable.setBounds(0, 0, width, height);
    drawable.draw(canvas);
    drawable.setBounds(left, top, right, bottom);
    return bitmap;
  }

  private State stateForEditor(Object editor) {
    synchronized (STATES) {
      for (State state : STATES.values()) if (state.editor == editor) return state;
    }
    return null;
  }

  private boolean isOtherProfileActivity(Activity activity) {
    if (activity == null
        || !activity.getClass().getName().equals(cfg.profileViewer.profileActivityClass))
      return false;
    State state = STATES.get(activity);
    if (state != null) return true;
    try {
      Object base = Reflect.getObjectField(activity, cfg.profileViewer.profileViewModelField);
      return !Boolean.TRUE.equals(Reflect.getObjectField(base, "m"));
    } catch (Throwable ignored) {
      return false;
    }
  }

  private static Activity activityFrom(Context context) {
    while (context instanceof ContextWrapper) {
      if (context instanceof Activity) return (Activity) context;
      Context next = ((ContextWrapper) context).getBaseContext();
      if (next == context) break;
      context = next;
    }
    return context instanceof Activity ? (Activity) context : null;
  }

  private static int id(Context context, String type, String name) {
    return context.getResources().getIdentifier(name, type, context.getPackageName());
  }

  private static View find(Activity activity, String name) {
    int id = id(activity, "id", name);
    return id == 0 ? null : activity.findViewById(id);
  }

  private static Drawable findLargestDrawable(View view) {
    Drawable best = null;
    int bestArea = 0;
    if (view instanceof ImageView) {
      Drawable d = ((ImageView) view).getDrawable();
      if (d != null) {
        best = d;
        bestArea = Math.max(1, view.getWidth()) * Math.max(1, view.getHeight());
      }
    }
    if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++) {
        View child = group.getChildAt(i);
        Drawable candidate = findLargestDrawable(child);
        int area = Math.max(1, child.getWidth()) * Math.max(1, child.getHeight());
        if (candidate != null && area > bestArea) {
          best = candidate;
          bestArea = area;
        }
      }
    }
    return best;
  }

  private static TextView findTextView(ViewGroup group) {
    for (int i = 0; i < group.getChildCount(); i++) {
      View child = group.getChildAt(i);
      if (child instanceof TextView) return (TextView) child;
      if (child instanceof ViewGroup) {
        TextView nested = findTextView((ViewGroup) child);
        if (nested != null) return nested;
      }
    }
    return null;
  }

  private static ImageView findImageView(ViewGroup group) {
    for (int i = 0; i < group.getChildCount(); i++) {
      View child = group.getChildAt(i);
      if (child instanceof ImageView) return (ImageView) child;
      if (child instanceof ViewGroup) {
        ImageView nested = findImageView((ViewGroup) child);
        if (nested != null) return nested;
      }
    }
    return null;
  }

  private static TextView headerButton(Context context, String text) {
    TextView button = new TextView(context);
    button.setText(text);
    button.setTextColor(Color.WHITE);
    button.setTextSize(14);
    button.setGravity(Gravity.CENTER);
    button.setBackgroundColor(0x66000000);
    return button;
  }

  private static int dp(Context context, int value) {
    return Math.round(value * context.getResources().getDisplayMetrics().density);
  }
}
