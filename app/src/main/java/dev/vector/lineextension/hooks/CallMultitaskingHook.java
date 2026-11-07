package dev.vector.lineextension.hooks;

import android.app.Activity;
import dev.vector.lineextension.LineVersion;
import dev.vector.lineextension.LoadParam;
import dev.vector.lineextension.Reflect;
import dev.vector.lineextension.SettingsStore;
import dev.vector.lineextension.Vector;
import dev.vector.lineextension.VectorConfig;
import java.lang.reflect.Method;

/** Removes verified UI-only LINE-call gates, not actual media ownership or call state. */
public final class CallMultitaskingHook implements BaseHook {
  @Override
  public void hook(VectorConfig options, LoadParam param) throws Throwable {
    LineVersion.Config version = LineVersion.get();
    if (version == null || version.callRestrictions.voicePresenterClass.isEmpty()) return;
    LineVersion.Config.CallRestrictions c = version.callRestrictions;
    ClassLoader loader = param.classLoader;
    // Resolve the complete surface before installing anything. All signatures are APK-verified.
    Method[] entries = {
      Reflect.findMethodExact(c.voicePresenterClass, loader, "p0", boolean.class),
      Reflect.findMethodExact(
          c.videoLauncherClass, loader, "a", Reflect.findClass(c.videoRequestClass, loader)),
      Reflect.findMethodExact(c.cameraGateClass, loader, "a", Activity.class)
    };
    Method[] checks = {
      Reflect.findMethodExact(c.chatCallStateClass, loader, c.methodChatCallState),
      Reflect.findMethodExact(version.camera.cameraModuleClass, loader, c.methodCameraCallState)
    };
    for (Method check : checks) {
      if (check.getReturnType() != boolean.class)
        throw new NoSuchMethodException("Media call guard is not boolean: " + check);
    }
    for (Method check : checks) {
      Vector.module
          .hook(check)
          .intercept(
              chain -> {
                Object result = chain.proceed();
                if (Boolean.TRUE.equals(result)
                    && CallRestrictionScope.active()
                    && enabled(options)) {
                  Vector.log(
                      "Tencha: bypassed media-entry call guard "
                          + check.getDeclaringClass().getName());
                  return false;
                }
                return result;
              });
    }
    for (Method entry : entries) {
      Vector.module
          .hook(entry)
          .intercept(
              chain -> {
                if (!enabled(options)) return chain.proceed();
                try (CallRestrictionScope ignored = CallRestrictionScope.enter()) {
                  return chain.proceed();
                }
              });
    }
    Vector.log("Tencha: call multitasking media-entry guards installed");
  }

  private static boolean enabled(VectorConfig options) {
    return SettingsStore.get(
        options.allowCallMultitasking.key, options.allowCallMultitasking.enabled);
  }
}
