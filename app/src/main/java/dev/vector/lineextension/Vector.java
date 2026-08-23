package dev.vector.lineextension;

import android.app.Application;
import android.util.Log;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedInterface.Hooker;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

public final class Vector {

  public static final String TAG = "Tencha";

  public static volatile XposedInterface module;
  public static volatile String processName;

  private Vector() {}

  public static void log(String msg) {
    XposedInterface m = module;
    if (m != null) {
      m.log(Log.INFO, TAG, msg);
    } else {
      Log.i(TAG, msg);
    }
  }

  public static void log(String msg, Throwable t) {
    XposedInterface m = module;
    if (m != null) {
      m.log(Log.ERROR, TAG, msg, t);
    } else {
      Log.e(TAG, msg, t);
    }
  }

  public static void hookAll(Class<?> clazz, String name, Hooker hooker) {
    for (Method m : clazz.getDeclaredMethods()) {
      if (m.getName().equals(name)) {
        m.setAccessible(true);
        module.hook(m).intercept(hooker);
      }
    }
  }

  public static void hookAllCtors(Class<?> clazz, Hooker hooker) {
    for (Constructor<?> c : clazz.getDeclaredConstructors()) {
      c.setAccessible(true);
      module.hook(c).intercept(hooker);
    }
  }

  public static Application currentApplication() {
    try {
      Class<?> at = Class.forName("android.app.ActivityThread");
      return (Application) at.getMethod("currentApplication").invoke(null);
    } catch (Throwable t) {
      return null;
    }
  }
}
