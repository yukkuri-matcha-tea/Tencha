package dev.vector.lineextension.hooks;

import android.content.res.Resources;
import android.text.SpannedString;
import dev.vector.lineextension.LoadParam;
import dev.vector.lineextension.Reflect;
import dev.vector.lineextension.Vector;
import dev.vector.lineextension.VectorConfig;

public class SafeResourceFix implements BaseHook {
  @Override
  public void hook(VectorConfig config, LoadParam lpparam) throws Throwable {
    if (!config.safeSettingsResources.enabled) return;

    try {
      Vector.module
          .hook(Reflect.findMethodExact(Resources.class, "getText", int.class))
          .intercept(
              chain -> {
                Object result = chain.proceed();
                if (result instanceof String) {
                  return new SpannedString((String) result);
                }
                return result;
              });
    } catch (Throwable t) {
      Vector.log("Tencha: Failed to hook SafeResourceFix: " + t.getMessage());
    }
  }
}
