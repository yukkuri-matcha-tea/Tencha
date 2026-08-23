package dev.vector.lineextension.hooks;

import dev.vector.lineextension.LineVersion;
import dev.vector.lineextension.LoadParam;
import dev.vector.lineextension.Reflect;
import dev.vector.lineextension.Vector;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.function.BooleanSupplier;

final class NightModePin {

  private NightModePin() {}

  static void install(LoadParam lpparam, BooleanSupplier active, String tag) {
    LineVersion.Config cfg = LineVersion.get();
    if (cfg == null) return;

    installDarkThemePredicates(cfg, lpparam, active, tag);

    if (cfg.nightMode.nightModeConfiguratorClass.isEmpty()
        || cfg.nightMode.methodApplyNightMode.isEmpty()) {
      return;
    }

    Class<?> configurator =
        Reflect.findClass(cfg.nightMode.nightModeConfiguratorClass, lpparam.classLoader);
    final Field systemDarkMode = booleanField(configurator, cfg.nightMode.fieldSystemDarkMode, tag);

    Vector.module
        .hook(
            Reflect.findMethodExact(
                configurator, cfg.nightMode.methodApplyNightMode, boolean.class))
        .intercept(
            chain -> {
              if (!active.getAsBoolean()) return chain.proceed();
              if (systemDarkMode == null) return chain.proceed(new Object[] {Boolean.TRUE});

              boolean systemIsDark = systemDarkMode.getBoolean(null);
              try {
                systemDarkMode.setBoolean(null, true);
                return chain.proceed(new Object[] {Boolean.TRUE});
              } finally {
                systemDarkMode.setBoolean(null, systemIsDark);
              }
            });

    Vector.log(
        tag
            + ": pinned night mode via "
            + cfg.nightMode.nightModeConfiguratorClass
            + "."
            + cfg.nightMode.methodApplyNightMode
            + (systemDarkMode == null ? " (only while the OS is in dark mode)" : ""));
  }

  private static void installDarkThemePredicates(
      LineVersion.Config cfg, LoadParam lpparam, BooleanSupplier active, String tag) {
    String className = cfg.nightMode.darkThemeManagerClass;
    if (className.isEmpty()) return;

    Class<?> themeManager;
    try {
      themeManager = Reflect.findClass(className, lpparam.classLoader);
    } catch (Throwable t) {
      Vector.log(tag + ": dark theme manager " + className + " not found: " + t);
      return;
    }

    if (!cfg.nightMode.methodIsDarkTheme.isEmpty()) {
      try {
        Method method = Reflect.findMethodExact(themeManager, cfg.nightMode.methodIsDarkTheme);
        if (method.getReturnType() == boolean.class) {
          Vector.module
              .hook(method)
              .intercept(
                  chain -> {
                    if (active.getAsBoolean()) return Boolean.TRUE;
                    return chain.proceed();
                  });
        }
      } catch (Throwable t) {
        Vector.log(tag + ": dark theme predicate unavailable on " + className + ": " + t);
      }
    }

    if (!cfg.nightMode.methodThemeMode.isEmpty()) {
      try {
        Method method = Reflect.findMethodExact(themeManager, cfg.nightMode.methodThemeMode);
        Object darkMode = enumConstant(method.getReturnType(), "DARK");
        if (darkMode != null) {
          Vector.module
              .hook(method)
              .intercept(
                  chain -> {
                    if (active.getAsBoolean()) return darkMode;
                    return chain.proceed();
                  });
        }
      } catch (Throwable t) {
        Vector.log(tag + ": theme mode predicate unavailable on " + className + ": " + t);
      }
    }
  }

  private static Object enumConstant(Class<?> type, String name) {
    if (type == null || !type.isEnum()) return null;
    Object[] constants = type.getEnumConstants();
    if (constants == null) return null;
    for (Object constant : constants) {
      if (constant instanceof Enum && name.equals(((Enum<?>) constant).name())) return constant;
    }
    return null;
  }

  private static Field booleanField(Class<?> owner, String name, String tag) {
    if (name == null || name.isEmpty()) return null;
    try {
      Field f = owner.getDeclaredField(name);
      if (f.getType() != boolean.class) return null;
      f.setAccessible(true);
      return f;
    } catch (Throwable t) {
      Vector.log(tag + ": dark mode flag " + name + " not found: " + t);
      return null;
    }
  }
}
