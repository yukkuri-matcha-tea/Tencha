package dev.vector.lineextension.hooks;

import dev.vector.lineextension.LoadParam;
import dev.vector.lineextension.Main;
import dev.vector.lineextension.VectorConfig;

public class ForceDarkModeUiHook implements BaseHook {

  @Override
  public void hook(VectorConfig config, LoadParam lpparam) throws Throwable {
    if (!config.forceDarkModeUi.enabled) return;
    NightModePin.install(
        lpparam, () -> Main.options.forceDarkModeUi.enabled, "Tencha: ForceDarkModeUi");
  }
}
