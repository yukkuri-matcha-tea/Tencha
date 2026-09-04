package dev.vector.lineextension.hooks;

import android.graphics.Paint;
import android.graphics.Typeface;
import android.widget.EditText;
import android.widget.TextView;
import dev.vector.lineextension.LineVersion;
import dev.vector.lineextension.LoadParam;
import dev.vector.lineextension.Reflect;
import dev.vector.lineextension.SettingsStore;
import dev.vector.lineextension.Vector;
import dev.vector.lineextension.VectorConfig;
import io.github.libxposed.api.XposedInterface;
import java.io.File;
import java.util.List;

public class FontUnlockHook implements BaseHook {
  private static Typeface customTypeface = null;
  private static boolean overrideActive = false;

  @Override
  public void hook(VectorConfig config, LoadParam lpparam) throws Throwable {
    final LineVersion.Config cfg = LineVersion.get();
    if (cfg == null || cfg.font.fontConfigClass.isEmpty()) return;

    initTypeface();
    if (!overrideActive || customTypeface == null) return;

    Vector.log("Tencha: Initializing Font hooks");

    try {
      Vector.module
          .hook(Reflect.findMethodExact(TextView.class, "setIncludeFontPadding", boolean.class))
          .intercept(chain -> chain.proceed(new Object[] {false}));
    } catch (Throwable ignored) {
    }

    XposedInterface.Hooker globalHook =
        chain -> {
          chain.proceed();
          return customTypeface;
        };
    Vector.module
        .hook(Reflect.findMethodExact(Typeface.class, "create", String.class, int.class))
        .intercept(globalHook);
    Vector.module
        .hook(Reflect.findMethodExact(Typeface.class, "create", Typeface.class, int.class))
        .intercept(globalHook);
    Vector.module
        .hook(Reflect.findMethodExact(Typeface.class, "defaultFromStyle", int.class))
        .intercept(globalHook);

    // Compose and several newer LINE widgets assign their Typeface directly to Paint/
    // TextPaint without going through TextView#setTypeface or Typeface#create.
    try {
      Vector.module
          .hook(Reflect.findMethodExact(Paint.class, "setTypeface", Typeface.class))
          .intercept(
              chain -> {
                if (!overrideActive || customTypeface == null) return chain.proceed();
                return chain.proceed(new Object[] {customTypeface});
              });
    } catch (Throwable t) {
      Vector.log("Tencha: Paint font hook unavailable: " + t);
    }

    XposedInterface.Hooker textViewHook =
        chain -> {
          if (!overrideActive || customTypeface == null) return chain.proceed();
          Object[] args = chain.getArgs().toArray();
          boolean changed = false;
          if (args.length > 0 && args[0] instanceof Typeface) {
            args[0] = customTypeface;
            changed = true;
          }
          if (chain.getThisObject() instanceof TextView) {
            TextView tv = (TextView) chain.getThisObject();
            tv.setIncludeFontPadding(false);
            if (tv instanceof EditText) {
              tv.setPadding(tv.getPaddingLeft(), 0, tv.getPaddingRight(), 0);
            }
          }
          return changed ? chain.proceed(args) : chain.proceed();
        };

    try {
      Vector.module
          .hook(Reflect.findMethodExact(TextView.class, "setTypeface", Typeface.class, int.class))
          .intercept(textViewHook);
      Vector.module
          .hook(Reflect.findMethodExact(TextView.class, "setTypeface", Typeface.class))
          .intercept(textViewHook);
      XposedInterface.Hooker constructHook =
          chain -> {
            Object result = chain.proceed();
            if (overrideActive
                && customTypeface != null
                && chain.getThisObject() instanceof TextView) {
              TextView tv = (TextView) chain.getThisObject();
              int style = tv.getTypeface() == null ? Typeface.NORMAL : tv.getTypeface().getStyle();
              tv.setTypeface(customTypeface, style);
              tv.setIncludeFontPadding(false);
            }
            return result;
          };
      Vector.module
          .hook(
              Reflect.findConstructorExact(
                  TextView.class, android.content.Context.class, android.util.AttributeSet.class))
          .intercept(constructHook);
      Vector.module
          .hook(
              Reflect.findConstructorExact(
                  TextView.class,
                  android.content.Context.class,
                  android.util.AttributeSet.class,
                  int.class))
          .intercept(constructHook);
    } catch (Throwable ignored) {
    }

    XposedInterface.Hooker metricsHook =
        chain -> {
          Object result = chain.proceed();
          if (!overrideActive || customTypeface == null) return result;
          Paint paint = (Paint) chain.getThisObject();
          float textSize = paint.getTextSize();
          if (textSize <= 0) return result;
          List<Object> args = chain.getArgs();
          Object last = args.isEmpty() ? null : args.get(args.size() - 1);
          Object m =
              (last instanceof Paint.FontMetricsInt || last instanceof Paint.FontMetrics)
                  ? last
                  : result;
          if (m instanceof Paint.FontMetricsInt) {
            Paint.FontMetricsInt fmi = (Paint.FontMetricsInt) m;
            fmi.ascent = Math.round(-textSize * 0.95f);
            fmi.descent = Math.round(textSize * 0.20f);
            fmi.top = fmi.ascent;
            fmi.bottom = fmi.descent;
            fmi.leading = 0;
          } else if (m instanceof Paint.FontMetrics) {
            Paint.FontMetrics fm = (Paint.FontMetrics) m;
            fm.ascent = -textSize * 0.95f;
            fm.descent = textSize * 0.20f;
            fm.top = fm.ascent;
            fm.bottom = fm.descent;
            fm.leading = 0;
          } else if ("getFontSpacing".equals(chain.getExecutable().getName())) {
            return textSize * 1.15f;
          }
          return result;
        };

    try {
      Vector.module
          .hook(
              Reflect.findMethodExact(Paint.class, "getFontMetricsInt", Paint.FontMetricsInt.class))
          .intercept(metricsHook);
      Vector.module
          .hook(Reflect.findMethodExact(Paint.class, "getFontMetricsInt"))
          .intercept(metricsHook);
      Vector.module
          .hook(Reflect.findMethodExact(Paint.class, "getFontMetrics", Paint.FontMetrics.class))
          .intercept(metricsHook);
      Vector.module
          .hook(Reflect.findMethodExact(Paint.class, "getFontMetrics"))
          .intercept(metricsHook);
      Vector.module
          .hook(Reflect.findMethodExact(Paint.class, "getFontSpacing"))
          .intercept(metricsHook);
      try {
        Vector.module
            .hook(
                Reflect.findMethodExact(
                    Paint.class,
                    "getFontMetricsInt",
                    CharSequence.class,
                    int.class,
                    int.class,
                    int.class,
                    int.class,
                    boolean.class,
                    Paint.FontMetricsInt.class))
            .intercept(metricsHook);
      } catch (Throwable ignored) {
      }
    } catch (Throwable ignored) {
    }

    try {
      Vector.module
          .hook(Reflect.findMethodExact(TextView.class, "onMeasure", int.class, int.class))
          .intercept(
              chain -> {
                if (overrideActive && customTypeface != null) {
                  TextView tv = (TextView) chain.getThisObject();
                  tv.setIncludeFontPadding(false);
                  if (tv instanceof EditText)
                    tv.setPadding(tv.getPaddingLeft(), 0, tv.getPaddingRight(), 0);
                  try {
                    Reflect.callMethod(tv, "setFallbackLineSpacing", false);
                  } catch (Throwable ignored) {
                  }
                }
                return chain.proceed();
              });
    } catch (Throwable ignored) {
    }

    try {
      Vector.module
          .hook(Reflect.findMethodExact(Paint.class, "setElegantTextHeight", boolean.class))
          .intercept(
              chain -> {
                if (overrideActive && customTypeface != null) {
                  return chain.proceed(new Object[] {false});
                }
                return chain.proceed();
              });
    } catch (Throwable ignored) {
    }

    Vector.module
        .hook(
            Reflect.findMethodExact(
                cfg.font.fontConfigClass,
                lpparam.classLoader,
                cfg.font.methodGetFontConfig,
                android.content.Context.class,
                java.util.List.class,
                int.class,
                boolean.class,
                int.class,
                android.os.Handler.class,
                cfg.font.fontCallbackClass))
        .intercept(
            chain -> {
              Object callback = chain.getArg(6);
              if (callback != null)
                try {
                  Reflect.callMethod(callback, cfg.font.methodOnFontChanged, customTypeface);
                } catch (Throwable ignored) {
                }
              return customTypeface;
            });

    try {
      Vector.module
          .hook(
              Reflect.findMethodExact(
                  cfg.font.fontCallbackClass,
                  lpparam.classLoader,
                  cfg.font.methodOnFontChanged,
                  Typeface.class))
          .intercept(chain -> chain.proceed(new Object[] {customTypeface}));
    } catch (Throwable ignored) {
    }

    Vector.module
        .hook(
            Reflect.findMethodExact(
                cfg.font.fontManagerClass,
                lpparam.classLoader,
                "c",
                android.content.Context.class,
                java.util.List.class,
                int.class,
                cfg.font.fontRequestExecutorClass,
                cfg.font.fontCallbackWithHandlerClass))
        .intercept(chain -> customTypeface);
  }

  private void initTypeface() {
    if (!SettingsStore.get("use_custom_font", false)) {
      overrideActive = false;
      return;
    }
    String path = SettingsStore.getString("custom_font_path", "");
    if (!path.isEmpty()) {
      File f = new File(path);
      if (f.exists()) {
        try {
          customTypeface = Typeface.createFromFile(f);
          if (customTypeface != null) {
            overrideActive = true;
            Vector.log("Tencha: Custom font loaded: " + path);
            return;
          }
        } catch (Throwable t) {
          Vector.log("Tencha: Failed to load font: " + t.getMessage());
        }
      }
    }
    overrideActive = false;
  }
}
