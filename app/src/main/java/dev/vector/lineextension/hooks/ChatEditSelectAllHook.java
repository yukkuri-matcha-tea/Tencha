package dev.vector.lineextension.hooks;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Resources;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import dev.vector.lineextension.LineVersion;
import dev.vector.lineextension.LoadParam;
import dev.vector.lineextension.Main;
import dev.vector.lineextension.Reflect;
import dev.vector.lineextension.Vector;
import dev.vector.lineextension.VectorConfig;
import java.lang.reflect.Method;
import java.util.Set;

public class ChatEditSelectAllHook implements BaseHook {

  private LineVersion.Config.ChatEditSelectAll cfg;
  private Class<?> providerClass;

  @Override
  public void hook(VectorConfig config, LoadParam lpparam) throws Throwable {
    if (!config.selectAllInEditMode.enabled) return;
    LineVersion.Config version = LineVersion.get();
    if (version == null) return;
    cfg = version.chatEditSelectAll;
    if (cfg.selectionProviderClass.isEmpty()) return;
    providerClass = Reflect.findClass(cfg.selectionProviderClass, lpparam.classLoader);

    Vector.module
        .hook(
            Reflect.findMethodExact(
                LayoutInflater.class, "inflate", int.class, ViewGroup.class, boolean.class))
        .intercept(
            chain -> {
              View result = (View) chain.proceed();
              if (result != null && Main.options.selectAllInEditMode.enabled) {
                injectToggleButton(result, (int) chain.getArg(0));
              }
              return result;
            });
  }

  private void injectToggleButton(View inflated, int layoutId) {
    Context context = inflated.getContext();
    if (context == null) return;
    Resources res = context.getResources();
    try {
      if (!"chat_ui_edit_mode_bottom_button".equals(res.getResourceEntryName(layoutId))) return;

      int toggleBtnId =
          res.getIdentifier(
              "chat_ui_edit_mode_bottom_option_toggle_button", "id", context.getPackageName());
      if (toggleBtnId == 0) return;

      TextView toggleBtn = inflated.findViewById(toggleBtnId);
      if (toggleBtn == null) return;

      toggleBtn.setVisibility(View.VISIBLE);
      toggleBtn.setText(selectAllLabel(context));
      toggleBtn.setEnabled(true);
      toggleBtn
          .getViewTreeObserver()
          .addOnGlobalLayoutListener(() -> onToggleLayout(toggleBtn, res, context));
    } catch (Exception ignored) {
    }
  }

  private void onToggleLayout(TextView toggleBtn, Resources res, Context context) {
    try {
      int execBtnId =
          res.getIdentifier(
              "chat_ui_edit_mode_bottom_execution_button", "id", context.getPackageName());
      View parent = (View) toggleBtn.getParent();
      if (execBtnId != 0 && parent != null) {
        TextView execBtn = parent.findViewById(execBtnId);
        if (execBtn != null && execBtn.getText().toString().contains(deleteLabel(context))) {
          updateButtonText(toggleBtn);
          toggleBtn.setOnClickListener(v -> handleSelectAll(toggleBtn));
        }
      }
    } catch (Exception ignored) {
    }

    if (toggleBtn.getVisibility() != View.VISIBLE) {
      toggleBtn.setVisibility(View.VISIBLE);
    }
    toggleBtn.setEnabled(true);
  }

  private void handleSelectAll(TextView toggleBtn) {
    try {
      Context context = toggleBtn.getContext();
      Selection selection = resolve(context);
      if (selection == null) {
        Vector.log("Vector SelectAll: provider not found");
        return;
      }

      int selectedCount = selection.selectedCount();
      boolean deselect =
          selectedCount == selection.count
              || (selectedCount > 0
                  && deselectAllLabel(context).equals(toggleBtn.getText().toString()));

      selection.toggleAll(deselect);
      toggleBtn.setText(deselect ? selectAllLabel(context) : deselectAllLabel(context));
      selection.notifyChanged();
    } catch (Throwable t) {
      Vector.log("Vector SelectAll Error: " + t);
    }
  }

  private void updateButtonText(TextView toggleBtn) {
    try {
      Context context = toggleBtn.getContext();
      Selection selection = resolve(context);
      if (selection == null) return;

      int selectedCount = selection.selectedCount();
      if (selectedCount > 0 && selectedCount == selection.count) {
        toggleBtn.setText(deselectAllLabel(context));
      } else if (selectedCount < selection.count) {
        toggleBtn.setText(selectAllLabel(context));
      }
    } catch (Exception ignored) {
    }
  }

  private static String deleteLabel(Context context) {
    return lineString(context, "chat_edit_action_delete");
  }

  private static String selectAllLabel(Context context) {
    return lineString(context, "line_settings_button_selectall");
  }

  private static String deselectAllLabel(Context context) {
    return lineString(context, "line_settings_button_deselectall");
  }

  private static String lineString(Context context, String resName) {
    int id = context.getResources().getIdentifier(resName, "string", context.getPackageName());
    return context.getString(id);
  }

  private Selection resolve(Context context) {
    Object adapter = findAdapter(context);
    Object provider = findProvider(adapter);
    return provider == null ? null : new Selection(adapter, provider);
  }

  private final class Selection {
    final Object adapter;
    final Object provider;
    final Object state;
    final int count;

    Selection(Object adapter, Object provider) {
      this.adapter = adapter;
      this.provider = provider;
      this.state = Reflect.callMethod(provider, cfg.methodGetSelectionState);
      this.count = (int) Reflect.callMethod(provider, cfg.methodGetCount);
    }

    int selectedCount() {
      Object set = Reflect.callMethod(state, cfg.methodGetSelectedIds);
      return set instanceof Set ? ((Set<?>) set).size() : 0;
    }

    void toggleAll(boolean deselect) {
      for (int i = 0; i < count; i++) {
        try {
          Object item = Reflect.callMethod(provider, cfg.methodGetItem, i);
          if (item == null) continue;
          boolean selected = (boolean) Reflect.callMethod(state, cfg.methodIsItemSelected, item);
          if (selected == deselect) Reflect.callMethod(state, cfg.methodToggleItem, item);
        } catch (Exception ignored) {
        }
      }
    }

    void notifyChanged() {
      Reflect.callMethod(adapter, "notifyDataSetChanged");
    }
  }

  private Object findProvider(Object adapter) {
    if (adapter == null) return null;
    for (Method m : adapter.getClass().getMethods()) {
      if (m.getParameterCount() == 0 && providerClass.isAssignableFrom(m.getReturnType())) {
        try {
          m.setAccessible(true);
          Object provider = m.invoke(adapter);
          if (provider != null) return provider;
        } catch (Exception ignored) {
        }
      }
    }
    return null;
  }

  private static Object findAdapter(Context context) {
    Activity activity = resolveActivity(context);
    if (activity == null) return null;
    int listId =
        context
            .getResources()
            .getIdentifier("chathistory_message_list", "id", context.getPackageName());
    View recyclerView = activity.findViewById(listId);
    if (recyclerView == null) return null;
    try {
      return Reflect.callMethod(recyclerView, "getAdapter");
    } catch (Throwable t) {
      return null;
    }
  }

  private static Activity resolveActivity(Context context) {
    if (context instanceof Activity) return (Activity) context;
    if (context instanceof ContextWrapper) {
      Context base = ((ContextWrapper) context).getBaseContext();
      if (base instanceof Activity) return (Activity) base;
    }
    return null;
  }
}
