package dev.vector.lineextension.hooks;

import dev.vector.lineextension.LineVersion;
import dev.vector.lineextension.LoadParam;
import dev.vector.lineextension.Main;
import dev.vector.lineextension.Reflect;
import dev.vector.lineextension.Vector;
import dev.vector.lineextension.VectorConfig;

public class UseDefaultCameraHook implements BaseHook {

  @Override
  public void hook(VectorConfig config, LoadParam lpparam) throws Throwable {
    if (!config.useDefaultCamera.enabled) return;
    LineVersion.Config version = LineVersion.get();
    if (version == null || version.camera.cameraModuleClass.isEmpty()) return;

    Vector.module
        .hook(
            Reflect.findMethodExact(
                version.camera.cameraModuleClass,
                lpparam.classLoader,
                version.camera.methodUseExternalCamera))
        .intercept(chain -> Main.options.useDefaultCamera.enabled ? Boolean.TRUE : chain.proceed());
  }
}
