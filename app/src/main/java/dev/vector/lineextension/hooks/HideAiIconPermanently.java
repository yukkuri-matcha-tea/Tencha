package dev.vector.lineextension.hooks;

import dev.vector.lineextension.LineVersion;
import dev.vector.lineextension.LoadParam;
import dev.vector.lineextension.Main;
import dev.vector.lineextension.Reflect;
import dev.vector.lineextension.Vector;
import dev.vector.lineextension.VectorConfig;
import dev.vector.lineextension.core.RuntimeReporter;
import java.lang.reflect.Method;

public class HideAiIconPermanently implements BaseHook {

  @Override
  public void hook(VectorConfig config, LoadParam lpparam) throws Throwable {
    LineVersion.Config cfg = LineVersion.get();
    if (cfg.aiIcon.repoClass.isEmpty()) return;

    Method target =
        Reflect.findMethodExact(
            cfg.aiIcon.repoClass, lpparam.classLoader, cfg.aiIcon.methodGetShownAfterMillis);

    Vector.module
        .hook(target)
        .intercept(
            chain -> {
              Object result = chain.proceed();
              if (Main.options.hideAiIconPermanently.enabled) {
                RuntimeReporter.working("agenti_hider", "AgentI表示設定の上書きをRuntime確認");
                return Long.MAX_VALUE;
              }
              return result;
            });
  }
}
