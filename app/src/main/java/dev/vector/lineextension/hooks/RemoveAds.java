package dev.vector.lineextension.hooks;

import android.graphics.Canvas;
import android.view.View;
import android.view.ViewGroup;
import dev.vector.lineextension.LineVersion;
import dev.vector.lineextension.LoadParam;
import dev.vector.lineextension.Reflect;
import dev.vector.lineextension.Vector;
import dev.vector.lineextension.VectorConfig;
import dev.vector.lineextension.core.RuntimeReporter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RemoveAds implements BaseHook {

  private static final Map<String, Boolean> adClassCache = new ConcurrentHashMap<>();

  @Override
  public void hook(VectorConfig config, LoadParam lpparam) throws Throwable {
    LineVersion.Config cfg = LineVersion.get();

    hookLadAdView(lpparam, cfg.ads.ladAdView);
    hookLadAdView(lpparam, cfg.ads.ladAdViewV2);
    hookSmartChannel(lpparam, cfg);
    hookGenericAddView();
  }

  private void hookLadAdView(LoadParam lpparam, String className) {
    try {
      Class<?> ladCls = lpparam.classLoader.loadClass(className);
      Vector.module
          .hook(Reflect.findMethodExact(ladCls, "onAttachedToWindow"))
          .intercept(
              chain -> {
                hideAdWrapper((View) chain.getThisObject());
                return chain.proceed();
              });

      Vector.hookAll(
          ladCls,
          "setVisibility",
          chain -> {
            Object[] args = chain.getArgs().toArray();
            if ((int) args[0] == View.VISIBLE) {
              args[0] = View.GONE;
              return chain.proceed(args);
            }
            return chain.proceed();
          });
    } catch (Throwable ignored) {
    }
  }

  private void hookSmartChannel(LoadParam lpparam, LineVersion.Config cfg) {
    try {
      Class<?> smartCls = lpparam.classLoader.loadClass(cfg.ads.smartChannel);
      Vector.module
          .hook(Reflect.findMethodExact(smartCls, "dispatchDraw", Canvas.class))
          .intercept(
              chain -> {
                hideAdWrapper((View) chain.getThisObject());
                return chain.proceed();
              });
    } catch (Throwable ignored) {
    }
  }

  private void hookGenericAddView() {
    Vector.module
        .hook(
            Reflect.findMethodExact(
                ViewGroup.class, "addView", View.class, ViewGroup.LayoutParams.class))
        .intercept(
            chain -> {
              Object result = chain.proceed();
              View view = (View) chain.getArg(0);
              if (isAdComponent(view.getClass().getName())) hideAdWrapper(view);
              return result;
            });
  }

  private static void hideAdWrapper(View view) {
    if (view == null) return;
    try {
      collapse(view);
      RuntimeReporter.working("ad_removal", "広告Viewの除去をRuntime確認");

      View current = view;
      for (int depth = 0; depth < 5; depth++) {
        View parent = (View) current.getParent();
        if (!(parent instanceof ViewGroup) || !isEmptyAdWrapper((ViewGroup) parent, current)) break;
        collapse(parent);
        current = parent;
      }
    } catch (Throwable ignored) {
    }
  }

  private static void collapse(View view) {
    view.setVisibility(View.GONE);
    ViewGroup.LayoutParams lp = view.getLayoutParams();
    if (lp != null) {
      lp.height = 0;
      if (lp instanceof ViewGroup.MarginLayoutParams) {
        ((ViewGroup.MarginLayoutParams) lp).setMargins(0, 0, 0, 0);
      }
      view.setLayoutParams(lp);
    }
  }

  private static boolean isEmptyAdWrapper(ViewGroup parent, View adChild) {
    String name = parent.getClass().getSimpleName();
    if (name.contains("RecyclerView") || name.contains("ListView") || name.contains("ViewPager")) {
      return false;
    }
    if (parent.getPaddingTop() != 0 || parent.getPaddingBottom() != 0) return false;
    ViewGroup.LayoutParams lp = parent.getLayoutParams();
    if (lp != null && lp.height != ViewGroup.LayoutParams.WRAP_CONTENT) return false;
    for (int i = 0; i < parent.getChildCount(); i++) {
      View child = parent.getChildAt(i);
      if (child != adChild && child.getVisibility() != View.GONE) return false;
    }
    return true;
  }

  private static boolean isAdComponent(String className) {
    Boolean cached = adClassCache.get(className);
    if (cached != null) return cached;

    boolean result = performAdCheck(className);
    adClassCache.put(className, result);
    return result;
  }

  private static boolean performAdCheck(String className) {
    LineVersion.Config cfg = LineVersion.get();
    if (cfg != null) {
      if (className.startsWith(cfg.ads.classAdSdkBase)) return true;
      if (className.startsWith(cfg.ads.classAdMolinBase)) return true;
    }
    String simpleName = className.substring(className.lastIndexOf('.') + 1);
    String lower = simpleName.toLowerCase();
    return simpleName.contains("NativeAd")
        || simpleName.contains("AdCard")
        || simpleName.contains("AdCell")
        || simpleName.contains("AdItem")
        || simpleName.contains("AdUnit")
        || simpleName.contains("AdView")
        || simpleName.contains("AdBanner")
        || simpleName.contains("AdModule")
        || lower.contains("sponsored")
        || lower.contains("promoted");
  }
}
