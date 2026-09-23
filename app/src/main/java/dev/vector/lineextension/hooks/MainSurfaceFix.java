package dev.vector.lineextension.hooks;

import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import dev.vector.lineextension.LineVersion;
import dev.vector.lineextension.LoadParam;
import dev.vector.lineextension.Reflect;
import dev.vector.lineextension.Vector;
import dev.vector.lineextension.VectorConfig;

/** Keeps LINE's main surface opaque while returning from a chat activity. */
public final class MainSurfaceFix implements BaseHook {

  @Override
  public void hook(VectorConfig options, LoadParam lpparam) throws Throwable {
    LineVersion.Config cfg = LineVersion.get();
    if (cfg == null || cfg.main.mainActivity.isEmpty()) return;

    Class<?> mainActivity = Reflect.findClass(cfg.main.mainActivity, lpparam.classLoader);
    Vector.module
        .hook(Reflect.findMethodExact(mainActivity, "onCreate", Bundle.class))
        .intercept(
            chain -> {
              Activity activity = (Activity) chain.getThisObject();
              applyWindowBackground(activity);
              Object result = chain.proceed();
              applyOpaqueBackground(activity);
              return result;
            });
    Vector.module
        .hook(Reflect.findMethodExact(mainActivity, "onResume"))
        .intercept(
            chain -> {
              Activity activity = (Activity) chain.getThisObject();
              applyOpaqueBackground(activity);
              Object result = chain.proceed();
              applyOpaqueBackground(activity);
              return result;
            });
  }

  private static void applyOpaqueBackground(Activity activity) {
    if (activity == null) return;
    try {
      int color = resolveBackground(activity);
      Window window = activity.getWindow();
      if (window != null) {
        window.setBackgroundDrawable(new ColorDrawable(color));
        View decor = window.getDecorView();
        if (decor != null) decor.setBackgroundColor(color);
      }
      View content = activity.findViewById(android.R.id.content);
      if (content != null) content.setBackgroundColor(color);
    } catch (Throwable t) {
      Vector.log("Tencha: MainSurfaceFix background update failed: " + t);
    }
  }

  /** Safe before Activity.onCreate: do not ask PhoneWindow for its decor/content yet. */
  private static void applyWindowBackground(Activity activity) {
    if (activity == null) return;
    try {
      Window window = activity.getWindow();
      if (window != null) {
        window.setBackgroundDrawable(new ColorDrawable(resolveBackground(activity)));
      }
    } catch (Throwable t) {
      Vector.log("Tencha: MainSurfaceFix window background update failed: " + t);
    }
  }

  private static int resolveBackground(Activity activity) {
    try {
      int id =
          activity
              .getResources()
              .getIdentifier("primaryBackground", "color", activity.getPackageName());
      if (id != 0) return activity.getColor(id);
    } catch (Throwable ignored) {
    }
    int night =
        activity.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
    return night == Configuration.UI_MODE_NIGHT_YES ? 0xFF111111 : Color.WHITE;
  }
}
