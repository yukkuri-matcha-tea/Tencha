package dev.vector.lineextension.core;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.json.JSONObject;

/** Exports and restores Tencha-private settings/history through a user-selected SAF document. */
public final class TenchaBackup {
  private static final String PREFS = "vector_control_v1";
  private static final String MANIFEST = "manifest.json";
  private static final String SETTINGS = "settings.json";
  private static final long MAX_SMALL_ENTRY_BYTES = 16L * 1024 * 1024;
  private static final long MAX_LINE_BACKUP_BYTES = 2L * 1024 * 1024 * 1024;
  private static final long MAX_TOTAL_BYTES = MAX_LINE_BACKUP_BYTES + 64L * 1024 * 1024;
  private static final Set<String> STORE_FILES =
      Collections.unmodifiableSet(
          new HashSet<>(
              Arrays.asList(
                  "vector_settings.bin",
                  "vector_unsend_history.bin",
                  "vector_read_history.bin",
                  "vector_edit_history.bin",
                  ControlProvider.LINE_BACKUP_FILE)));

  private TenchaBackup() {}

  public static void exportTo(Context context, Uri destination) throws Exception {
    try (OutputStream raw = context.getContentResolver().openOutputStream(destination, "wt")) {
      if (raw == null) throw new IllegalStateException("バックアップ先を開けません");
      try (ZipOutputStream zip = new ZipOutputStream(raw)) {
        JSONObject manifest = new JSONObject();
        manifest.put("format", "tencha-backup");
        manifest.put("version", 1);
        manifest.put("createdAt", System.currentTimeMillis());
        putBytes(zip, MANIFEST, manifest.toString().getBytes(StandardCharsets.UTF_8));
        putBytes(
            zip, SETTINGS, exportSettings(context).toString().getBytes(StandardCharsets.UTF_8));

        File store = new File(context.getFilesDir(), "tencha_store");
        for (String name : STORE_FILES) {
          File file = new File(store, name);
          if (!file.isFile()) continue;
          zip.putNextEntry(new ZipEntry("store/" + name));
          try (InputStream in = new FileInputStream(file)) {
            copy(in, zip, maxBytesFor(name));
          }
          zip.closeEntry();
        }
      }
    }
  }

  public static void restoreFrom(Context context, Uri source) throws Exception {
    JSONObject manifest = null;
    JSONObject settings = null;
    java.util.Map<String, File> stagedStore = new java.util.LinkedHashMap<>();
    Set<String> seen = new HashSet<>();
    long total = 0L;

    try (InputStream raw = context.getContentResolver().openInputStream(source)) {
      if (raw == null) throw new IllegalStateException("バックアップを開けません");
      try (ZipInputStream zip = new ZipInputStream(raw)) {
        ZipEntry entry;
        while ((entry = zip.getNextEntry()) != null) {
          if (entry.isDirectory()) {
            zip.closeEntry();
            continue;
          }
          if (!seen.add(entry.getName())) {
            throw new IllegalArgumentException("バックアップに重複項目があります");
          }
          if (MANIFEST.equals(entry.getName())) {
            byte[] bytes = readSmallEntry(zip);
            total += bytes.length;
            manifest = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
          } else if (SETTINGS.equals(entry.getName())) {
            byte[] bytes = readSmallEntry(zip);
            total += bytes.length;
            settings = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
          } else if (entry.getName().startsWith("store/")) {
            String name = entry.getName().substring("store/".length());
            if (STORE_FILES.contains(name)) {
              File store = ensureStoreDirectory(context);
              File temp = new File(store, name + ".restore");
              stagedStore.put(name, temp);
              try (OutputStream out = new FileOutputStream(temp, false)) {
                total += copy(zip, out, maxBytesFor(name));
              }
            } else {
              total += drain(zip, MAX_SMALL_ENTRY_BYTES);
            }
          } else {
            total += drain(zip, MAX_SMALL_ENTRY_BYTES);
          }
          if (total > MAX_TOTAL_BYTES) throw new IllegalArgumentException("バックアップが大きすぎます");
          zip.closeEntry();
        }
      }

      if (manifest == null
          || !"tencha-backup".equals(manifest.optString("format"))
          || manifest.optInt("version", -1) != 1
          || settings == null) {
        throw new IllegalArgumentException("Tenchaバックアップではありません");
      }

      restoreSettings(context, settings);
      File store = ensureStoreDirectory(context);
      for (Map.Entry<String, File> entry : stagedStore.entrySet()) {
        File target = new File(store, entry.getKey());
        moveReplacing(entry.getValue(), target);
      }
      stagedStore.clear();
    } finally {
      for (File temp : stagedStore.values()) {
        if (temp.isFile()) temp.delete();
      }
    }
  }

  private static File ensureStoreDirectory(Context context) {
    File store = new File(context.getFilesDir(), "tencha_store");
    if (!store.isDirectory() && !store.mkdirs()) throw new IllegalStateException("保存領域を作れません");
    return store;
  }

  private static void moveReplacing(File source, File target) throws Exception {
    try {
      java.nio.file.Files.move(
          source.toPath(),
          target.toPath(),
          java.nio.file.StandardCopyOption.ATOMIC_MOVE,
          java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
      java.nio.file.Files.move(
          source.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private static byte[] readSmallEntry(InputStream in) throws Exception {
    ByteArrayOutputStream data = new ByteArrayOutputStream();
    copy(in, data, MAX_SMALL_ENTRY_BYTES);
    return data.toByteArray();
  }

  private static long drain(InputStream in, long maxBytes) throws Exception {
    return copy(
        in,
        new OutputStream() {
          @Override
          public void write(int value) {}

          @Override
          public void write(byte[] data, int offset, int length) {}
        },
        maxBytes);
  }

  private static long maxBytesFor(String name) {
    return ControlProvider.LINE_BACKUP_FILE.equals(name)
        ? MAX_LINE_BACKUP_BYTES
        : MAX_SMALL_ENTRY_BYTES;
  }

  private static JSONObject exportSettings(Context context) throws Exception {
    JSONObject json = new JSONObject();
    for (Map.Entry<String, ?> entry :
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getAll().entrySet()) {
      if (!entry.getKey().startsWith("setting.bool.")
          && !entry.getKey().startsWith("setting.string.")) continue;
      Object value = entry.getValue();
      if (value instanceof Boolean || value instanceof String) json.put(entry.getKey(), value);
    }
    return json;
  }

  private static void restoreSettings(Context context, JSONObject json) throws Exception {
    SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    SharedPreferences.Editor edit = prefs.edit();
    for (String key : prefs.getAll().keySet()) {
      if (key.startsWith("setting.bool.") || key.startsWith("setting.string.")) edit.remove(key);
    }
    java.util.Iterator<String> keys = json.keys();
    while (keys.hasNext()) {
      String key = keys.next();
      if (!key.matches("setting\\.(bool|string)\\.[a-z0-9_]{1,64}")) continue;
      Object value = json.get(key);
      if (key.startsWith("setting.bool.") && value instanceof Boolean) {
        edit.putBoolean(key, (Boolean) value);
      } else if (key.startsWith("setting.string.") && value instanceof String) {
        edit.putString(
            key, ((String) value).substring(0, Math.min(4096, ((String) value).length())));
      }
    }
    if (!edit.commit()) throw new IllegalStateException("設定を復元できません");
  }

  private static void putBytes(ZipOutputStream zip, String name, byte[] data) throws Exception {
    zip.putNextEntry(new ZipEntry(name));
    zip.write(data);
    zip.closeEntry();
  }

  private static long copy(InputStream in, OutputStream out, long maxBytes) throws Exception {
    byte[] buffer = new byte[8192];
    long total = 0L;
    int read;
    while ((read = in.read(buffer)) != -1) {
      total += read;
      if (total > maxBytes) throw new IllegalArgumentException("バックアップ項目が大きすぎます");
      out.write(buffer, 0, read);
    }
    return total;
  }
}
