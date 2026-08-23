package dev.vector.lineextension;

public final class LoadParam {
  public final ClassLoader classLoader;
  public final String packageName;
  public final String processName;

  public LoadParam(ClassLoader classLoader, String packageName, String processName) {
    this.classLoader = classLoader;
    this.packageName = packageName;
    this.processName = processName;
  }
}
