package dev.vector.lineextension.hooks;

import android.animation.ValueAnimator;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;
import dev.vector.lineextension.LineVersion;
import dev.vector.lineextension.LoadParam;
import dev.vector.lineextension.Reflect;
import dev.vector.lineextension.Vector;
import dev.vector.lineextension.VectorConfig;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Enables LINE's existing ViewPager animation only for a bottom-navigation click. */
public final class MainTabSlideHook implements BaseHook {
  private WeakReference<View> clickedRoot = new WeakReference<>(null);
  private final TabClickLease click = new TabClickLease();
  private final Handler handler = new Handler(Looper.getMainLooper());
  private WeakReference<View> animatedPager = new WeakReference<>(null);
  private long transitionGeneration;
  private Method snapToPage;
  private Field scrollState;

  @Override
  public void hook(VectorConfig options, LoadParam param) throws Throwable {
    Class<?> pager = Reflect.findClass("androidx.viewpager.widget.ViewPager", param.classLoader);
    String version = LineVersion.getResolvedVersionName();
    if (NativeCallBridge.uses2615CallApi(version)) {
      // Actual DEX: T3 is scroll state, v(IIZZ) snaps via native page offsets and e(false).
      // Native scroll duration is at most 600 ms. Do not guess these on other versions.
      snapToPage =
          Reflect.findMethodExact(pager, "v", int.class, int.class, boolean.class, boolean.class);
      scrollState = pager.getDeclaredField("T3");
      scrollState.setAccessible(true);
    }
    Method switchPage;
    try {
      switchPage = Reflect.findMethodExact(pager, "setCurrentItem", int.class, boolean.class);
    } catch (NoSuchMethodError missing) {
      // Verified in 26.15.0 smali and 26.15.1 dex: w(IZ) -> x(IIZZ) -> v(IIZZ).
      // The boolean controls native smooth scrolling; unknown mappings are not guessed.
      if (!NativeCallBridge.uses2615CallApi(version)) return;
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
                  && InteractionMotionHook.animationsEnabled()
                  && click.consume(SystemClock.uptimeMillis())) {
                clickedRoot.clear();
                Object[] args = chain.getArgs().toArray();
                args[1] = true;
                animatedPager = new WeakReference<>(view);
                long token = ++transitionGeneration;
                Vector.log("Tencha: native main tab slide requested");
                Object result = chain.proceed(args);
                handler.postDelayed(
                    () -> {
                      View active = animatedPager.get();
                      if (token == transitionGeneration
                          && active != null
                          && active.hasWindowFocus()) {
                        finishOwnedTransition("timeout");
                      }
                    },
                    1500L);
                return result;
              }
              // A later native/programmatic navigation supersedes our earlier transition.
              if (animatedPager.get() == view) {
                animatedPager.clear();
                transitionGeneration++;
              }
              return chain.proceed();
            });
    Vector.module
        .hook(Reflect.findMethodExact(View.class, "onTouchEvent", MotionEvent.class))
        .intercept(
            chain -> {
              View view = (View) chain.getThisObject();
              MotionEvent event = (MotionEvent) chain.getArg(0);
              if (Looper.myLooper() != Looper.getMainLooper()
                  || event.getActionMasked() != MotionEvent.ACTION_UP
                  || !isBottomTab(view)
                  || !view.isPressed()
                  || !InteractionMotionHook.animationsEnabled()
                  || !ValueAnimator.areAnimatorsEnabled()) return chain.proceed();
              View root = view.getRootView();
              long generation = click.begin(SystemClock.uptimeMillis());
              clickedRoot = new WeakReference<>(root);
              try {
                return chain.proceed();
              } finally {
                // View.onTouchEvent posts performClick on ACTION_UP. Programmatic performClick
                // (including LINE's activity restoration) must never arm this lease.
                // Allow callbacks already posted by LINE's click listener, but never leave
                // a flag that animates startup, activity restoration, or an unrelated pager.
                root.post(
                    () -> {
                      click.cancel(generation);
                    });
              }
            });
    Class<?> main = Reflect.findClass(LineVersion.get().main.mainActivity, param.classLoader);
    Vector.module
        .hook(Reflect.findMethodExact(main, "onPause"))
        .intercept(
            chain -> {
              click.cancel();
              clickedRoot.clear();
              finishOwnedTransition("pause");
              return chain.proceed();
            });
    Vector.module
        .hook(Reflect.findMethodExact(main, "onResume"))
        .intercept(
            chain -> {
              Object result = chain.proceed();
              finishOwnedTransition("resume");
              return result;
            });
  }

  private void finishOwnedTransition(String reason) {
    View view = animatedPager.get();
    if (view == null || snapToPage == null || scrollState == null) return;
    try {
      if (!view.isAttachedToWindow() || view.getWidth() == 0) return;
      if (scrollState.getInt(view) == 2) {
        int selected = (Integer) Reflect.callMethod(view, "getCurrentItem");
        // No adapter changes, no page selection event, no touch/drag interception.
        snapToPage.invoke(view, selected, 0, false, false);
        view.postInvalidateOnAnimation();
        Vector.log("Tencha: settled interrupted main tab transition (" + reason + ")");
      }
      animatedPager.clear();
      transitionGeneration++;
    } catch (Throwable error) {
      animatedPager.clear();
      transitionGeneration++;
      Vector.log("Tencha: main tab transition cleanup skipped", error);
    }
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
