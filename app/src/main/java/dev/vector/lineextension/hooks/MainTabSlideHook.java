package dev.vector.lineextension.hooks;

import android.animation.ValueAnimator;
import android.os.Looper;
import android.view.View;
import android.view.ViewParent;
import dev.vector.lineextension.LineVersion;
import dev.vector.lineextension.LoadParam;
import dev.vector.lineextension.Reflect;
import dev.vector.lineextension.Vector;
import dev.vector.lineextension.VectorConfig;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;

/** Enables LINE's existing ViewPager animation only for a bottom-navigation click. */
public final class MainTabSlideHook implements BaseHook {
  private WeakReference<View> clickedRoot = new WeakReference<>(null);
  private long clickGeneration;

  @Override
  public void hook(VectorConfig options, LoadParam param) throws Throwable {
    Class<?> pager = Reflect.findClass("androidx.viewpager.widget.ViewPager", param.classLoader);
    Method switchPage;
    try {
      switchPage = Reflect.findMethodExact(pager, "setCurrentItem", int.class, boolean.class);
    } catch (NoSuchMethodError missing) {
      // Verified in 26.15.0 smali and 26.15.1 dex: w(IZ) -> x(IIZZ) -> v(IIZZ).
      // The boolean controls native smooth scrolling; unknown mappings are not guessed.
      String version = LineVersion.getResolvedVersionName();
      if (!"26.15.0".equals(version) && !"26.15.1".equals(version)) return;
      switchPage = Reflect.findMethodExact(pager, "w", int.class, boolean.class);
    }
    Vector.module
        .hook(switchPage)
        .intercept(
            chain -> {
              View view = (View) chain.getThisObject();
              if (view.getClass()
                      .getName()
                      .equals("jp.naver.line.android.customview.MainActivityViewPager")
                  && clickedRoot.get() == view.getRootView()
                  && view.isAttachedToWindow()
                  && ValueAnimator.areAnimatorsEnabled()
                  && InteractionMotionHook.animationsEnabled()) {
                clickedRoot.clear();
                Object[] args = chain.getArgs().toArray();
                args[1] = true;
                Vector.log("Tencha: native main tab slide requested");
                return chain.proceed(args);
              }
              return chain.proceed();
            });
    Vector.module
        .hook(Reflect.findMethodExact(View.class, "performClick"))
        .intercept(
            chain -> {
              View view = (View) chain.getThisObject();
              if (Looper.myLooper() != Looper.getMainLooper()
                  || !InteractionMotionHook.animationsEnabled()
                  || !isBottomTab(view)
                  || !ValueAnimator.areAnimatorsEnabled()) return chain.proceed();
              View root = view.getRootView();
              long generation = ++clickGeneration;
              clickedRoot = new WeakReference<>(root);
              try {
                return chain.proceed();
              } finally {
                // Allow callbacks already posted by LINE's click listener, but never leave
                // a flag that animates startup, activity restoration, or an unrelated pager.
                root.post(
                    () -> {
                      if (generation == clickGeneration) clickedRoot.clear();
                    });
              }
            });
  }

  private static boolean isBottomTab(View view) {
    if (!view.isAttachedToWindow()
        || !view.isEnabled()
        || !view.isShown()
        || !"bnb_button_clickable_area".equals(resource(view))) return false;
    ViewParent parent = view.getParent();
    while (parent instanceof View) {
      if ("main_tab_container".equals(resource((View) parent))) return true;
      parent = parent.getParent();
    }
    return false;
  }

  private static String resource(View view) {
    try {
      return view.getResources().getResourceEntryName(view.getId());
    } catch (RuntimeException ignored) {
      return "";
    }
  }
}
