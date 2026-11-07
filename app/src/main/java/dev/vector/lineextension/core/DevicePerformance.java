package dev.vector.lineextension.core;

import android.app.ActivityManager;
import android.content.ComponentCallbacks2;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Reads each host process's real limits. No model whitelist, root, governor or OS setting changes.
 */
public final class DevicePerformance {
  private static volatile DeviceProfile profile =
      new DeviceProfile(
          0, Runtime.getRuntime().maxMemory(), Runtime.getRuntime().availableProcessors(), false);
  private static final CopyOnWriteArrayList<Runnable> trimmers = new CopyOnWriteArrayList<>();
  private static boolean initialized;
  private static ThreadPoolExecutor io;

  private DevicePerformance() {}

  public static synchronized void initialize(Context context) {
    if (initialized || context == null) return;
    long ram = 0;
    boolean low = false;
    try {
      // The supplied ContextImpl is attached; getApplicationContext() can still be an
      // unattached Application while LINE's attachBaseContext interceptor is running.
      ActivityManager manager =
          (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
      if (manager != null) {
        ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo();
        manager.getMemoryInfo(memory);
        ram = memory.totalMem;
        low = manager.isLowRamDevice();
      }
    } catch (RuntimeException error) {
      Log.w(
          "TenchaPerformance",
          "Device memory query unavailable; using heap-limited budgets",
          error);
    }
    profile =
        new DeviceProfile(
            ram, Runtime.getRuntime().maxMemory(), Runtime.getRuntime().availableProcessors(), low);
    new Handler(Looper.getMainLooper()).post(() -> registerMemoryCallbacks(context));
    initialized = true;
    logProfile();
  }

  private static void registerMemoryCallbacks(Context context) {
    try {
      Context app = context.getApplicationContext();
      if (app == null) return;
      app.registerComponentCallbacks(
          new ComponentCallbacks2() {
            @Override
            public void onConfigurationChanged(Configuration config) {}

            @Override
            public void onLowMemory() {
              trimCaches();
            }

            @Override
            public void onTrimMemory(int level) {
              if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) trimCaches();
            }
          });
    } catch (RuntimeException error) {
      // Performance hints are optional. Never prevent a single LINE feature from loading.
      Log.w("TenchaPerformance", "Memory callback registration unavailable", error);
    }
  }

  private static void logProfile() {
    String soc =
        Build.VERSION.SDK_INT >= 31
            ? Build.SOC_MANUFACTURER + "/" + Build.SOC_MODEL
            : Build.HARDWARE;
    Log.i(
        "TenchaPerformance",
        "Device="
            + Build.MODEL
            + " SoC="
            + soc
            + " cores="
            + profile.cpuCores
            + " ramBytes="
            + profile.totalRamBytes
            + " heapBytes="
            + profile.heapLimitBytes
            + " lowRam="
            + profile.lowRam
            + " pcmCacheBytes="
            + profile.pcmCacheBytes()
            + " ioWorkers="
            + profile.ioWorkers());
  }

  public static DeviceProfile profile() {
    return profile;
  }

  public static void addCacheTrimmer(Runnable trimmer) {
    trimmers.add(trimmer);
  }

  private static void trimCaches() {
    for (Runnable trim : trimmers) {
      try {
        trim.run();
      } catch (RuntimeException error) {
        Log.w("TenchaPerformance", "Cache trim failed", error);
      }
    }
  }

  /**
   * Bounded optional background work. Rejection is explicit so callers can retain text-only
   * fallback.
   */
  public static synchronized ThreadPoolExecutor ioExecutor() {
    if (io == null) {
      int count = profile.ioWorkers();
      io =
          new ThreadPoolExecutor(
              count,
              count,
              30,
              TimeUnit.SECONDS,
              new ArrayBlockingQueue<>(64),
              task -> {
                Thread thread =
                    new Thread(
                        () -> {
                          android.os.Process.setThreadPriority(
                              android.os.Process.THREAD_PRIORITY_BACKGROUND);
                          task.run();
                        },
                        "TenchaIo");
                thread.setDaemon(true);
                return thread;
              });
      io.allowCoreThreadTimeOut(true);
    }
    return io;
  }
}
