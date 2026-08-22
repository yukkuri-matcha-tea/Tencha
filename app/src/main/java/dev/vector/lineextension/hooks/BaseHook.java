package dev.vector.lineextension.hooks;

import dev.vector.lineextension.LoadParam;
import dev.vector.lineextension.VectorConfig;

public interface BaseHook {
  void hook(VectorConfig config, LoadParam lpparam) throws Throwable;
}
