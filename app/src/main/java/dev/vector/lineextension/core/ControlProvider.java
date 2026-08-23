package dev.vector.lineextension.core;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** A deliberately narrow cross-process bridge between the module UI and hooked LINE process. */
public final class ControlProvider extends ContentProvider {
  public static final String AUTHORITY = "dev.vector.lineextension.control";
  public static final Uri URI = Uri.parse("content://" + AUTHORITY);
  private static final String STORE_PATH = "store";
  private static final String LINE_PACKAGE = "jp.naver.line.android";
  private static final String PREFS = "vector_control_v1";
  private static final String NEXT_LAUNCH_OFF = "next_launch_off";
  private static final String PREFIX_STATUS = "status.";
  private static final String PREFIX_DETAIL = "detail.";
  private static final String PREFIX_FAILURES = "failures.";
  private static final String PREFIX_SUCCESS = "success.";
  private static final String PREFIX_FAILURE = "failure.";
  private static final String PREFIX_SETTING_BOOL = "setting.bool.";
  private static final String PREFIX_SETTING_STRING = "setting.string.";
  private static final String LAST_LINE_SEEN = "runtime.last_line_seen";
  private static final String LAST_LINE_VERSION = "runtime.line_version";
  private static final String LAST_LINE_PROCESS = "runtime.line_process";
  public static final String LINE_BACKUP_FILE = "line_chat_backup.tenchabak";
  public static final String LINE_BACKUP_TEMP_FILE = "line_chat_backup.tmp";
  public static final String LINE_RESTORE_FILE = "line_chat_restore.pending";
  public static final String LINE_RESTORE_TEMP_FILE = "line_chat_restore.tmp";

  private SharedPreferences prefs;

  @Override
  public boolean onCreate() {
    Context context = getContext();
    if (context == null) return false;
    prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    return true;
  }

  @Nullable
  @Override
  public Bundle call(@NonNull String method, @Nullable String arg, @Nullable Bundle extras) {
    if (!isAllowedCaller()) throw new SecurityException("Caller is not LINE or module process");
    Bundle out = new Bundle();
    switch (method) {
      case "requestNextLaunchOff":
        prefs.edit().putBoolean(NEXT_LAUNCH_OFF, true).apply();
        out.putBoolean("ok", true);
        return out;
      case "setNextLaunchOff":
        prefs
            .edit()
            .putBoolean(NEXT_LAUNCH_OFF, extras != null && extras.getBoolean("enabled", false))
            .apply();
        out.putBoolean("ok", true);
        return out;
      case "consumeNextLaunchOff":
        if (!isLineCaller()) throw new SecurityException("Only LINE may consume the flag");
        synchronized (ControlProvider.class) {
          boolean requested = prefs.getBoolean(NEXT_LAUNCH_OFF, false);
          if (requested) prefs.edit().putBoolean(NEXT_LAUNCH_OFF, false).commit();
          out.putBoolean("requested", requested);
        }
        return out;
      case "reportFeature":
        if (!isLineCaller()) throw new SecurityException("Only LINE may report runtime state");
        reportFeature(sanitizeId(arg), extras == null ? Bundle.EMPTY : extras);
        out.putBoolean("ok", true);
        return out;
      case "reportSession":
        if (!isLineCaller()) throw new SecurityException("Only LINE may report a session");
        Bundle session = extras == null ? Bundle.EMPTY : extras;
        SharedPreferences.Editor sessionEdit =
            prefs
                .edit()
                .putLong(LAST_LINE_SEEN, System.currentTimeMillis())
                .putString(
                    LAST_LINE_VERSION, sanitizeText(session.getString("lineVersion", ""), 64))
                .putString(LAST_LINE_PROCESS, sanitizeText(session.getString("process", ""), 128));
        for (String key : prefs.getAll().keySet()) {
          if (key.startsWith(PREFIX_STATUS)) {
            String id = key.substring(PREFIX_STATUS.length());
            if (prefs.getInt(PREFIX_FAILURES + id, 0) < 2) {
              sessionEdit.putString(key, FeatureStatus.DISABLED.name());
              sessionEdit.putString(PREFIX_DETAIL + id, "この起動では未適用");
            }
          }
        }
        sessionEdit.apply();
        out.putBoolean("ok", true);
        return out;
      case "featureState":
        return featureState(sanitizeId(arg));
      case "snapshot":
        return snapshot();
      case "clearSafeMode":
        clearSafeMode(sanitizeId(arg));
        out.putBoolean("ok", true);
        return out;
      case "clearAllSafeModes":
        clearAllSafeModes();
        out.putBoolean("ok", true);
        return out;
      case "putSetting":
        putSetting(sanitizeId(arg), extras == null ? Bundle.EMPTY : extras);
        out.putBoolean("ok", true);
        return out;
      case "settingsSnapshot":
        return settingsSnapshot();
      case "resetSettings":
        resetSettings();
        out.putBoolean("ok", true);
        return out;
      case "storeExists":
        out.putBoolean("exists", storeFile(arg).isFile());
        return out;
      case "commitLineBackup":
        if (!isLineCaller()) throw new SecurityException("Only LINE may commit a chat backup");
        out.putBoolean("ok", commitLineBackup());
        return out;
      case "commitLineRestore":
        if (!isLineCaller()) throw new SecurityException("Only LINE may stage a chat restore");
        out.putBoolean("ok", moveStoreFile(LINE_RESTORE_TEMP_FILE, LINE_RESTORE_FILE));
        return out;
      case "finishLineRestore":
        if (!isLineCaller()) throw new SecurityException("Only LINE may finish a chat restore");
        out.putBoolean(
            "ok", !storeFile(LINE_RESTORE_FILE).exists() || storeFile(LINE_RESTORE_FILE).delete());
        return out;
      default:
        throw new IllegalArgumentException("Unknown control method");
    }
  }

  @Override
  public ParcelFileDescriptor openFile(@NonNull Uri uri, @NonNull String mode)
      throws FileNotFoundException {
    if (!isAllowedCaller()) throw new SecurityException("Caller is not LINE or module process");
    java.util.List<String> segments = uri.getPathSegments();
    if (segments.size() != 2 || !STORE_PATH.equals(segments.get(0))) {
      throw new FileNotFoundException("Unknown Tencha store URI");
    }
    File file = storeFile(segments.get(1));
    if (mode.startsWith("r") && !file.isFile()) throw new FileNotFoundException(file.getName());
    int flags =
        mode.startsWith("r") && !mode.contains("w")
            ? ParcelFileDescriptor.MODE_READ_ONLY
            : ParcelFileDescriptor.MODE_CREATE
                | ParcelFileDescriptor.MODE_TRUNCATE
                | ParcelFileDescriptor.MODE_WRITE_ONLY;
    return ParcelFileDescriptor.open(file, flags);
  }

  private File storeFile(String name) {
    if (name == null
        || !(name.equals("vector_settings.bin")
            || name.equals("vector_unsend_history.bin")
            || name.equals("vector_read_history.bin")
            || name.equals("vector_edit_history.bin")
            || name.equals(LINE_BACKUP_FILE)
            || name.equals(LINE_BACKUP_TEMP_FILE)
            || name.equals(LINE_RESTORE_FILE)
            || name.equals(LINE_RESTORE_TEMP_FILE))) {
      throw new IllegalArgumentException("Invalid Tencha store file");
    }
    File dir = new File(getContext().getFilesDir(), "tencha_store");
    if (!dir.isDirectory() && !dir.mkdirs()) {
      throw new IllegalStateException("Could not create Tencha store");
    }
    return new File(dir, name);
  }

  private boolean commitLineBackup() {
    return moveStoreFile(LINE_BACKUP_TEMP_FILE, LINE_BACKUP_FILE);
  }

  private boolean moveStoreFile(String sourceName, String destinationName) {
    File source = storeFile(sourceName);
    File destination = storeFile(destinationName);
    if (!source.isFile() || source.length() == 0L) return false;
    try {
      try {
        Files.move(
            source.toPath(),
            destination.toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING);
      } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
        Files.move(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
      }
      return destination.isFile() && destination.length() > 0L;
    } catch (IOException ignored) {
      return false;
    }
  }

  private void reportFeature(String id, Bundle extras) {
    String status = sanitizeText(extras.getString("status", FeatureStatus.VERIFYING.name()), 32);
    String detail = sanitizeText(extras.getString("detail", ""), 240);
    long now = System.currentTimeMillis();
    int failures = prefs.getInt(PREFIX_FAILURES + id, 0);
    SharedPreferences.Editor edit =
        prefs.edit().putString(PREFIX_STATUS + id, status).putString(PREFIX_DETAIL + id, detail);
    if (FeatureStatus.HOOK_FAILED.name().equals(status)) {
      edit.putInt(PREFIX_FAILURES + id, failures + 1).putLong(PREFIX_FAILURE + id, now);
    } else if (FeatureStatus.WORKING.name().equals(status)
        || FeatureStatus.VERIFYING.name().equals(status)) {
      edit.putInt(PREFIX_FAILURES + id, 0).putLong(PREFIX_SUCCESS + id, now);
    }
    edit.apply();
  }

  private Bundle featureState(String id) {
    Bundle out = new Bundle();
    int failures = prefs.getInt(PREFIX_FAILURES + id, 0);
    out.putString("status", prefs.getString(PREFIX_STATUS + id, FeatureStatus.DISABLED.name()));
    out.putString("detail", prefs.getString(PREFIX_DETAIL + id, ""));
    out.putInt("failures", failures);
    out.putLong("lastSuccess", prefs.getLong(PREFIX_SUCCESS + id, 0L));
    out.putLong("lastFailure", prefs.getLong(PREFIX_FAILURE + id, 0L));
    out.putBoolean("safeMode", failures >= 2);
    return out;
  }

  private Bundle snapshot() {
    Bundle out = new Bundle();
    out.putBoolean("nextLaunchOff", prefs.getBoolean(NEXT_LAUNCH_OFF, false));
    out.putLong("lastLineSeen", prefs.getLong(LAST_LINE_SEEN, 0L));
    out.putString("lineVersion", prefs.getString(LAST_LINE_VERSION, ""));
    out.putString("lineProcess", prefs.getString(LAST_LINE_PROCESS, ""));
    Set<String> ids = new HashSet<>();
    for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
      if (entry.getKey().startsWith(PREFIX_STATUS)) {
        ids.add(entry.getKey().substring(PREFIX_STATUS.length()));
      }
    }
    out.putStringArrayList("featureIds", new java.util.ArrayList<>(ids));
    for (String id : ids) {
      Bundle state = featureState(id);
      out.putBundle("feature." + id, state);
    }
    return out;
  }

  private void putSetting(String key, Bundle extras) {
    String type = extras.getString("type", "");
    SharedPreferences.Editor edit = prefs.edit();
    if ("boolean".equals(type)) {
      edit.remove(PREFIX_SETTING_STRING + key);
      edit.putBoolean(PREFIX_SETTING_BOOL + key, extras.getBoolean("value", false));
    } else if ("string".equals(type)) {
      edit.remove(PREFIX_SETTING_BOOL + key);
      edit.putString(
          PREFIX_SETTING_STRING + key, sanitizeText(extras.getString("value", ""), 4096));
    } else {
      throw new IllegalArgumentException("Invalid setting type");
    }
    edit.apply();
  }

  private Bundle settingsSnapshot() {
    Bundle out = new Bundle();
    java.util.ArrayList<String> boolKeys = new java.util.ArrayList<>();
    java.util.ArrayList<String> stringKeys = new java.util.ArrayList<>();
    for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
      String storedKey = entry.getKey();
      if (storedKey.startsWith(PREFIX_SETTING_BOOL) && entry.getValue() instanceof Boolean) {
        String key = storedKey.substring(PREFIX_SETTING_BOOL.length());
        boolKeys.add(key);
        out.putBoolean("bool." + key, (Boolean) entry.getValue());
      } else if (storedKey.startsWith(PREFIX_SETTING_STRING)
          && entry.getValue() instanceof String) {
        String key = storedKey.substring(PREFIX_SETTING_STRING.length());
        stringKeys.add(key);
        out.putString("string." + key, (String) entry.getValue());
      }
    }
    out.putStringArrayList("booleanKeys", boolKeys);
    out.putStringArrayList("stringKeys", stringKeys);
    return out;
  }

  private void resetSettings() {
    SharedPreferences.Editor edit = prefs.edit();
    for (String key : prefs.getAll().keySet()) {
      if (key.startsWith(PREFIX_SETTING_BOOL) || key.startsWith(PREFIX_SETTING_STRING)) {
        edit.remove(key);
      }
    }
    edit.apply();
  }

  private void clearAllSafeModes() {
    SharedPreferences.Editor edit = prefs.edit();
    for (String key : prefs.getAll().keySet()) {
      if (key.startsWith(PREFIX_FAILURES)) edit.remove(key);
      if (key.startsWith(PREFIX_STATUS)) edit.putString(key, FeatureStatus.DISABLED.name());
    }
    edit.apply();
  }

  private void clearSafeMode(String id) {
    prefs
        .edit()
        .putInt(PREFIX_FAILURES + id, 0)
        .putString(PREFIX_STATUS + id, FeatureStatus.DISABLED.name())
        .apply();
  }

  private boolean isAllowedCaller() {
    int uid = Binder.getCallingUid();
    if (uid == Process.myUid()) return true;
    String[] packages = getContext().getPackageManager().getPackagesForUid(uid);
    if (packages == null) return false;
    for (String packageName : packages) if (LINE_PACKAGE.equals(packageName)) return true;
    return false;
  }

  private boolean isLineCaller() {
    int uid = Binder.getCallingUid();
    String[] packages = getContext().getPackageManager().getPackagesForUid(uid);
    if (packages == null) return false;
    for (String packageName : packages) if (LINE_PACKAGE.equals(packageName)) return true;
    return false;
  }

  private static String sanitizeId(String value) {
    if (value == null || !value.matches("[a-z0-9_]{1,64}")) {
      throw new IllegalArgumentException("Invalid feature id");
    }
    return value;
  }

  private static String sanitizeText(String value, int maxLength) {
    if (value == null) return "";
    return value.substring(0, Math.min(value.length(), maxLength));
  }

  @Nullable
  @Override
  public Cursor query(
      @NonNull Uri uri,
      @Nullable String[] p,
      @Nullable String s,
      @Nullable String[] a,
      @Nullable String o) {
    return null;
  }

  @Nullable
  @Override
  public String getType(@NonNull Uri uri) {
    return null;
  }

  @Nullable
  @Override
  public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int delete(@NonNull Uri uri, @Nullable String s, @Nullable String[] a) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int update(
      @NonNull Uri uri, @Nullable ContentValues v, @Nullable String s, @Nullable String[] a) {
    throw new UnsupportedOperationException();
  }
}
