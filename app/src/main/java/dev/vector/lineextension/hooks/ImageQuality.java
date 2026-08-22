package dev.vector.lineextension.hooks;

import dev.vector.lineextension.LineVersion;
import dev.vector.lineextension.LoadParam;
import dev.vector.lineextension.Reflect;
import dev.vector.lineextension.Vector;
import dev.vector.lineextension.VectorConfig;
import dev.vector.lineextension.core.RuntimeReporter;
import io.github.libxposed.api.XposedInterface;

public class ImageQuality implements BaseHook {

  @Override
  public void hook(VectorConfig options, LoadParam lpparam) throws Throwable {
    if (!options.highQualityPhoto.enabled) return;

    LineVersion.Config v = LineVersion.get();
    if (v == null) return;

    hookQualityProfiles(lpparam.classLoader, v);
    hookImageUtil(lpparam.classLoader, v);
  }

  private void hookQualityProfiles(ClassLoader cl, LineVersion.Config v) {
    if (v.imageQuality.qualityProfileHighClass.isEmpty()) return;

    try {
      XposedInterface.Hooker maxDimensionHook =
          chain -> {
            RuntimeReporter.working("media_quality", "画像サイズ上限の上書きをRuntime確認");
            return 99999;
          };
      XposedInterface.Hooker qualityHook =
          chain -> {
            RuntimeReporter.working("media_quality", "JPEG品質の上書きをRuntime確認");
            return 100;
          };

      Class<?> highClass = Reflect.findClass(v.imageQuality.qualityProfileHighClass, cl);
      Vector.module
          .hook(Reflect.findMethodExact(highClass, v.imageQuality.methodGetMaxDimension))
          .intercept(maxDimensionHook);
      Vector.module
          .hook(Reflect.findMethodExact(highClass, v.imageQuality.methodGetQuality))
          .intercept(qualityHook);

      if (!v.imageQuality.qualityProfileMediumClass.isEmpty()) {
        Class<?> mediumClass = Reflect.findClass(v.imageQuality.qualityProfileMediumClass, cl);
        Vector.module
            .hook(Reflect.findMethodExact(mediumClass, v.imageQuality.methodGetMaxDimension))
            .intercept(maxDimensionHook);
        Vector.module
            .hook(Reflect.findMethodExact(mediumClass, v.imageQuality.methodGetQuality))
            .intercept(qualityHook);
      }

      Vector.log("Tencha: Image quality profiles hooked");
    } catch (Throwable t) {
      Vector.log("Tencha: Failed to hook image quality profiles: " + t.getMessage());
    }
  }

  private void hookImageUtil(ClassLoader cl, LineVersion.Config v) {
    if (v.imageQuality.imageUtilClass.isEmpty()) return;

    try {
      Vector.module
          .hook(
              Reflect.findMethodExact(
                  android.graphics.Bitmap.class,
                  "compress",
                  android.graphics.Bitmap.CompressFormat.class,
                  int.class,
                  java.io.OutputStream.class))
          .intercept(
              chain -> {
                android.graphics.Bitmap.CompressFormat format =
                    (android.graphics.Bitmap.CompressFormat) chain.getArg(0);
                int quality = (int) chain.getArg(1);

                if (format == android.graphics.Bitmap.CompressFormat.JPEG && quality < 100) {
                  RuntimeReporter.working("media_quality", "JPEG再圧縮品質の上書きをRuntime確認");
                  Object[] args = chain.getArgs().toArray();
                  args[1] = 100;
                  return chain.proceed(args);
                }
                return chain.proceed();
              });

      Vector.log("Tencha: Bitmap.compress hooked");
    } catch (Throwable t) {
      Vector.log("Tencha: Failed to hook Bitmap.compress: " + t.getMessage());
    }
  }
}
