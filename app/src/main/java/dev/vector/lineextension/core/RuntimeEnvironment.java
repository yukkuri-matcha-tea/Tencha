package dev.vector.lineextension.core;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import java.io.File;

/** Detects how the module was actually loaded without invoking su or showing a root prompt. */
public final class RuntimeEnvironment {
  public static final String MODE_ROOT = "root";
  public static final String MODE_LSPATCH = "lspatch";
  public static final String MODE_UNKNOWN = "unknown";

  private static final String LSPATCH_LOADER = "org.lsposed.lspatch.loader.LSPApplication";
  private static final String VECTOR_MANAGER_CATEGORY = "org.matrix.vector.manager.LAUNCH_MANAGER";
  private static final String[] ROOT_PATHS = {
    "/system/bin/su",
    "/system/xbin/su",
    "/sbin/su",
    "/su/bin/su",
    "/system/app/Superuser.apk",
    "/system/app/SuperSU.apk",
    "/debug_ramdisk/.magisk",
    "/data/adb/magisk"
  };

  private RuntimeEnvironment() {}

  /** Called only from a loaded module inside LINE. */
  public static String detectLoaderMode(ClassLoader targetClassLoader) {
    if (targetClassLoader == null) return MODE_UNKNOWN;
    try {
      Class.forName(LSPATCH_LOADER, false, targetClassLoader);
      return MODE_LSPATCH;
    } catch (ClassNotFoundException ignored) {
      return MODE_ROOT;
    } catch (Throwable ignored) {
      return MODE_UNKNOWN;
    }
  }

  /** Positive-only root evidence. This never executes su. */
  public static boolean hasRootEvidence(Context context) {
    if (context != null) {
      try {
        Intent vectorManager = new Intent(Intent.ACTION_MAIN).addCategory(VECTOR_MANAGER_CATEGORY);
        if (context.getPackageManager().resolveActivity(vectorManager, 0) != null) return true;
      } catch (Throwable ignored) {
        // Continue with filesystem evidence when the manager is hidden.
      }
      try {
        context.getPackageManager().getPackageInfo("org.lsposed.manager", 0);
        return true;
      } catch (PackageManager.NameNotFoundException ignored) {
        // LSPosed Manager is not installed or is hidden from this app.
      } catch (Throwable ignored) {
        // Continue with filesystem evidence.
      }
    }
    for (String path : ROOT_PATHS) {
      try {
        if (new File(path).exists()) return true;
      } catch (SecurityException ignored) {
        // Keep checking paths visible to the app sandbox.
      }
    }
    String path = System.getenv("PATH");
    if (path == null || path.isBlank()) return false;
    for (String directory : path.split(File.pathSeparator)) {
      if (directory.isBlank()) continue;
      try {
        if (new File(directory, "su").isFile()) return true;
      } catch (SecurityException ignored) {
        // An inaccessible path is not positive root evidence.
      }
    }
    return false;
  }

  public static String sanitizeMode(String mode) {
    if (MODE_ROOT.equals(mode) || MODE_LSPATCH.equals(mode)) return mode;
    return MODE_UNKNOWN;
  }
}
