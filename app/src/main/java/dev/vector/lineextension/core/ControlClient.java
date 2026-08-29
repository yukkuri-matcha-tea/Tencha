package dev.vector.lineextension.core;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import dev.vector.lineextension.SettingsStore;
import dev.vector.lineextension.VectorConfig;

public final class ControlClient {
  private ControlClient() {}

  public static boolean requestNextLaunchOff(Context context) {
    Bundle out = call(context, "requestNextLaunchOff", null, null);
    return out != null && out.getBoolean("ok", false);
  }

  public static boolean setNextLaunchOff(Context context, boolean enabled) {
    Bundle data = new Bundle();
    data.putBoolean("enabled", enabled);
    Bundle out = call(context, "setNextLaunchOff", null, data);
    return out != null && out.getBoolean("ok", false);
  }

  public static boolean consumeNextLaunchOff(Context context) {
    Bundle out = call(context, "consumeNextLaunchOff", null, null);
    return out != null && out.getBoolean("requested", false);
  }

  public static Bundle snapshot(Context context) {
    Bundle out = call(context, "snapshot", null, null);
    return out == null ? Bundle.EMPTY : out;
  }

  public static Bundle featureState(Context context, String id) {
    Bundle out = call(context, "featureState", id, null);
    return out == null ? Bundle.EMPTY : out;
  }

  public static void report(Context context, String id, FeatureStatus status, String detail) {
    Bundle data = new Bundle();
    data.putString("status", status.name());
    data.putString("detail", detail == null ? "" : detail);
    call(context, "reportFeature", id, data);
  }

  public static boolean clearSafeMode(Context context, String id) {
    Bundle out = call(context, "clearSafeMode", id, null);
    return out != null && out.getBoolean("ok", false);
  }

  public static boolean clearAllSafeModes(Context context) {
    Bundle out = call(context, "clearAllSafeModes", null, null);
    return out != null && out.getBoolean("ok", false);
  }

  public static boolean putSetting(Context context, String key, boolean value) {
    Bundle data = new Bundle();
    data.putString("type", "boolean");
    data.putBoolean("value", value);
    Bundle out = call(context, "putSetting", key, data);
    return out != null && out.getBoolean("ok", false);
  }

  public static boolean putSetting(Context context, String key, String value) {
    Bundle data = new Bundle();
    data.putString("type", "string");
    data.putString("value", value == null ? "" : value);
    Bundle out = call(context, "putSetting", key, data);
    return out != null && out.getBoolean("ok", false);
  }

  public static Bundle settingsSnapshot(Context context) {
    Bundle out = call(context, "settingsSnapshot", null, null);
    return out == null ? Bundle.EMPTY : out;
  }

  public static boolean resetSettings(Context context) {
    Bundle out = call(context, "resetSettings", null, null);
    return out != null && out.getBoolean("ok", false);
  }

  public static Uri storeUri(String fileName) {
    return URI_BUILDER.buildUpon().appendPath("store").appendPath(fileName).build();
  }

  public static boolean storeExists(Context context, String fileName) {
    Bundle out = call(context, "storeExists", fileName, null);
    return out != null && out.getBoolean("exists", false);
  }

  public static boolean commitLineBackup(Context context) {
    Bundle out = call(context, "commitLineBackup", null, null);
    return out != null && out.getBoolean("ok", false);
  }

  public static boolean commitLineRestore(Context context) {
    Bundle out = call(context, "commitLineRestore", null, null);
    return out != null && out.getBoolean("ok", false);
  }

  public static boolean finishLineRestore(Context context) {
    Bundle out = call(context, "finishLineRestore", null, null);
    return out != null && out.getBoolean("ok", false);
  }

  public static void syncSettings(Context context, VectorConfig config) {
    Bundle settings = settingsSnapshot(context);
    java.util.ArrayList<String> boolKeys = settings.getStringArrayList("booleanKeys");
    java.util.ArrayList<String> stringKeys = settings.getStringArrayList("stringKeys");
    java.util.HashSet<String> boolSet =
        boolKeys == null ? new java.util.HashSet<>() : new java.util.HashSet<>(boolKeys);
    java.util.HashSet<String> stringSet =
        stringKeys == null ? new java.util.HashSet<>() : new java.util.HashSet<>(stringKeys);
    for (VectorConfig.Item item : config.items) {
      if (boolSet.contains(item.key)) {
        item.enabled = settings.getBoolean("bool." + item.key, item.enabled);
        SettingsStore.setRuntimeSetting(item.key, item.enabled);
      }
      if (stringSet.contains(item.key)) {
        item.value = settings.getString("string." + item.key, item.value);
        SettingsStore.setRuntimeSetting(item.key, item.value);
      }
    }
  }

  public static void reportSession(
      Context context,
      String lineVersion,
      String processName,
      String loaderMode,
      String compatibilityState,
      String resolvedVersion,
      String compatibilityDetail) {
    Bundle data = new Bundle();
    data.putString("lineVersion", lineVersion == null ? "" : lineVersion);
    data.putString("process", processName == null ? "" : processName);
    data.putString("loaderMode", RuntimeEnvironment.sanitizeMode(loaderMode));
    data.putString(
        "compatibilityState", compatibilityState == null ? "unknown" : compatibilityState);
    data.putString("resolvedVersion", resolvedVersion == null ? "" : resolvedVersion);
    data.putString("compatibilityDetail", compatibilityDetail == null ? "" : compatibilityDetail);
    call(context, "reportSession", null, data);
  }

  private static Bundle call(Context context, String method, String arg, Bundle extras) {
    try {
      return context.getContentResolver().call(ControlProvider.URI, method, arg, extras);
    } catch (Throwable ignored) {
      return null;
    }
  }

  private static final Uri URI_BUILDER = ControlProvider.URI;
}
