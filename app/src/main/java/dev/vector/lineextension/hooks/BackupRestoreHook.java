package dev.vector.lineextension.hooks;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.ContextWrapper;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;
import dev.vector.lineextension.core.ControlClient;
import dev.vector.lineextension.core.ControlProvider;
import dev.vector.lineextension.utils.LineTheme;
import dev.vector.lineextension.utils.ModuleStrings;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public final class BackupRestoreHook {

  private static final String LOG_TAG = "TenchaSync";
  private static final ExecutorService syncExecutor = Executors.newSingleThreadExecutor();
  private static final Handler uiHandler = new Handler(Looper.getMainLooper());
  private static final AtomicBoolean syncBusy = new AtomicBoolean(false);

  private static final List<String> DB_NAMES = Arrays.asList("naver_line", "contact", "square");
  private static final String[] DB_SUFFIXES = {"", "-wal"};
  private static final byte[] ZIP_MAGIC = {0x50, 0x4B, 0x03, 0x04};
  private static final String MARKER_ENTRY = "vector-backup-v1";
  private static final int COPY_BUFFER_SIZE = 64 * 1024;
  private static final long MAX_BACKUP_BYTES = 2L * 1024 * 1024 * 1024;

  public static void runBackup(Context context) {
    runBackup(context, null);
  }

  public static void runBackup(Context context, Runnable onSuccess) {
    if (!syncBusy.compareAndSet(false, true)) {
      Toast.makeText(context, "バックアップ処理中です。", Toast.LENGTH_SHORT).show();
      return;
    }
    final ProgressDialog pd = createSyncProgress(context, ModuleStrings.RESTORE_PREPARING);
    pd.show();
    LineTheme.applyDialogColors(pd, context);

    syncExecutor.execute(
        () -> {
          final boolean result = executeVectorBackup(context);
          uiHandler.post(
              () -> {
                syncBusy.set(false);
                safeDismiss(pd);
                if (!result || onSuccess == null) {
                  notifySyncResult(
                      context, result, ModuleStrings.BACKUP_SUCCESS, ModuleStrings.BACKUP_ERROR);
                }
                if (result && onSuccess != null && isContextUsable(context)) onSuccess.run();
              });
        });
  }

  public static void exportInternalBackup(Context context, Uri destination) {
    if (!syncBusy.compareAndSet(false, true)) {
      Toast.makeText(context, "バックアップ処理中です。", Toast.LENGTH_SHORT).show();
      return;
    }
    final ProgressDialog pd = createSyncProgress(context, ModuleStrings.BACKUP_EXPORTING);
    pd.show();
    LineTheme.applyDialogColors(pd, context);

    syncExecutor.execute(
        () -> {
          boolean result = false;
          try (InputStream in =
                  context
                      .getContentResolver()
                      .openInputStream(ControlClient.storeUri(ControlProvider.LINE_BACKUP_FILE));
              OutputStream out = context.getContentResolver().openOutputStream(destination, "wt")) {
            if (in == null || out == null) throw new IOException("Cannot open backup destination");
            copyStream(in, out);
            result = true;
          } catch (Exception e) {
            Log.e(LOG_TAG, "Backup export failed: " + e.getMessage());
          }
          final boolean finalResult = result;
          uiHandler.post(
              () -> {
                syncBusy.set(false);
                safeDismiss(pd);
                notifySyncResult(
                    context,
                    finalResult,
                    ModuleStrings.BACKUP_EXPORT_SUCCESS,
                    ModuleStrings.BACKUP_EXPORT_ERROR);
              });
        });
  }

  public static void runRestore(Context context, File backupFile) {
    if (backupFile == null) return;
    stageRestore(context, Uri.fromFile(backupFile), backupFile);
  }

  public static boolean hasInternalBackup(Context context) {
    return ControlClient.storeExists(context, ControlProvider.LINE_BACKUP_FILE);
  }

  public static void runRestoreInternal(Context context) {
    stageRestore(context, ControlClient.storeUri(ControlProvider.LINE_BACKUP_FILE), null);
  }

  public static void stageRestore(Context context, Uri source) {
    stageRestore(context, source, null);
  }

  private static void stageRestore(Context context, Uri source, File deleteAfter) {
    if (!syncBusy.compareAndSet(false, true)) {
      Toast.makeText(context, "復元準備中です。", Toast.LENGTH_SHORT).show();
      return;
    }
    final ProgressDialog pd = createSyncProgress(context, ModuleStrings.RESTORE_PROCESSING);
    pd.show();
    LineTheme.applyDialogColors(pd, context);

    syncExecutor.execute(
        () -> {
          boolean result = false;
          try {
            try (InputStream in = context.getContentResolver().openInputStream(source);
                OutputStream out =
                    context
                        .getContentResolver()
                        .openOutputStream(
                            ControlClient.storeUri(ControlProvider.LINE_RESTORE_TEMP_FILE), "wt")) {
              if (in == null || out == null) throw new IOException("Cannot open restore source");
              copyStream(in, out, MAX_BACKUP_BYTES);
            }
            File validation =
                File.createTempFile("tencha_restore_check_", ".bak", context.getCacheDir());
            try {
              try (InputStream in =
                      context
                          .getContentResolver()
                          .openInputStream(
                              ControlClient.storeUri(ControlProvider.LINE_RESTORE_TEMP_FILE));
                  OutputStream out = new FileOutputStream(validation)) {
                if (in == null) throw new IOException("Cannot verify staged restore");
                copyStream(in, out);
              }
              if (!validateBackup(context, validation))
                throw new IOException("Invalid Tencha backup");
            } finally {
              validation.delete();
            }
            result = ControlClient.commitLineRestore(context);
          } catch (Exception e) {
            Log.e(LOG_TAG, "Restore staging failed: " + e.getMessage());
          }
          final boolean finalResult = result;
          if (deleteAfter != null) deleteAfter.delete();
          uiHandler.post(
              () -> {
                syncBusy.set(false);
                safeDismiss(pd);
                if (finalResult) {
                  if (!isContextUsable(context)) {
                    Toast.makeText(
                            context.getApplicationContext(),
                            "復元準備が完了しました。LINEを再起動してください。",
                            Toast.LENGTH_LONG)
                        .show();
                    return;
                  }
                  LineTheme.applyDialogColors(
                      new AlertDialog.Builder(context, LineTheme.dialogTheme(context))
                          .setTitle("復元準備完了")
                          .setMessage("LINEを再起動して、安全にトーク履歴を復元します。")
                          .setPositiveButton(
                              ModuleStrings.RESTART_OK,
                              (d, w) -> android.os.Process.killProcess(android.os.Process.myPid()))
                          .setCancelable(false)
                          .show(),
                      context);
                } else {
                  notifySyncResult(
                      context, false, ModuleStrings.RESTORE_SUCCESS, ModuleStrings.RESTORE_ERROR);
                }
              });
        });
  }

  /**
   * Applies a validated staged restore during LINE's cold-start bootstrap, before feature hooks.
   */
  public static void applyPendingRestore(Context context) {
    if (!ControlClient.storeExists(context, ControlProvider.LINE_RESTORE_FILE)) return;
    File local = null;
    boolean restored = false;
    try {
      local = File.createTempFile("tencha_pending_restore_", ".bak", context.getCacheDir());
      try (InputStream in =
              context
                  .getContentResolver()
                  .openInputStream(ControlClient.storeUri(ControlProvider.LINE_RESTORE_FILE));
          OutputStream out = new FileOutputStream(local)) {
        if (in == null) throw new IOException("Cannot open pending restore");
        copyStream(in, out);
      }
      restored = validateBackup(context, local) && executeFullRestore(context, local);
      Log.i(LOG_TAG, restored ? "Pending restore applied" : "Pending restore rejected");
    } catch (Throwable t) {
      Log.e(LOG_TAG, "Pending restore failed: " + t.getMessage());
    } finally {
      if (local != null) local.delete();
      // Never boot-loop on a broken restore. The original internal/exported backup remains intact.
      ControlClient.finishLineRestore(context);
    }
  }

  private static boolean executeVectorBackup(Context context) {
    try {
      File mainDb = context.getDatabasePath("naver_line");
      if (!mainDb.exists()) {
        Log.e(LOG_TAG, "Source database not found");
        return false;
      }

      writeBackupZip(context, ControlClient.storeUri(ControlProvider.LINE_BACKUP_TEMP_FILE));
      File check = File.createTempFile("tencha_backup_check_", ".bak", context.getCacheDir());
      try {
        try (InputStream in =
                context
                    .getContentResolver()
                    .openInputStream(
                        ControlClient.storeUri(ControlProvider.LINE_BACKUP_TEMP_FILE));
            OutputStream out = new FileOutputStream(check)) {
          if (in == null) throw new IOException("Cannot verify backup");
          copyStream(in, out);
        }
        if (!validateBackup(context, check)) throw new IOException("Backup validation failed");
      } finally {
        check.delete();
      }
      if (!ControlClient.commitLineBackup(context)) {
        Log.e(LOG_TAG, "Could not commit internal backup");
        return false;
      }
      return true;
    } catch (Exception e) {
      Log.e(LOG_TAG, "Backup failed: " + e.getMessage());
      return false;
    }
  }

  private static void writeBackupZip(Context context, Uri dst) throws IOException {
    try (OutputStream raw = context.getContentResolver().openOutputStream(dst)) {
      if (raw == null) throw new IOException("Cannot open Tencha internal storage");
      try (ZipOutputStream zip = new ZipOutputStream(raw)) {

        zip.putNextEntry(new ZipEntry(MARKER_ENTRY));
        zip.closeEntry();

        for (String dbName : DB_NAMES) {
          File baseDb = context.getDatabasePath(dbName);
          if (!baseDb.isFile()) continue;
          try (SQLiteDatabase lock =
              SQLiteDatabase.openDatabase(
                  baseDb.getAbsolutePath(), null, SQLiteDatabase.OPEN_READWRITE)) {
            lock.beginTransactionNonExclusive();
            try {
              for (String suffix : DB_SUFFIXES) {
                File f = new File(baseDb.getPath() + suffix);
                if (f.isFile()) writeFileEntry(zip, dbName + ".db" + suffix, f);
              }
            } finally {
              lock.endTransaction();
            }
          }
        }
      }
    }
  }

  private static void writeFileEntry(ZipOutputStream zip, String name, File file)
      throws IOException {
    zip.putNextEntry(new ZipEntry(name));
    try (FileInputStream in = new FileInputStream(file)) {
      copyStream(in, zip);
    }
    zip.closeEntry();
  }

  private static boolean executeFullRestore(Context context, File srcFile) {
    return isZipFile(srcFile) ? restoreFromZip(context, srcFile) : restoreLegacy(context, srcFile);
  }

  private static boolean validateBackup(Context context, File file) {
    if (!isZipFile(file)) {
      try (SQLiteDatabase ignored =
          SQLiteDatabase.openDatabase(
              file.getAbsolutePath(),
              null,
              SQLiteDatabase.OPEN_READONLY | SQLiteDatabase.NO_LOCALIZED_COLLATORS)) {
        return true;
      } catch (Throwable ignored) {
        return false;
      }
    }
    File validationDir = new File(context.getCacheDir(), "tencha_validate_" + System.nanoTime());
    if (!validationDir.mkdirs()) return false;
    try (ZipFile zip = new ZipFile(file)) {
      if (zip.getEntry(MARKER_ENTRY) == null) return false;
      Set<String> bases = new HashSet<>();
      Enumeration<? extends ZipEntry> entries = zip.entries();
      while (entries.hasMoreElements()) {
        ZipEntry entry = entries.nextElement();
        if (!entry.isDirectory()
            && !MARKER_ENTRY.equals(entry.getName())
            && matchedDbName(entry.getName()) == null) return false;
        if (entry.isDirectory() || MARKER_ENTRY.equals(entry.getName())) continue;
        if (entry.getSize() > MAX_BACKUP_BYTES) return false;
        String dbName = matchedDbName(entry.getName());
        if (entry.getName().equals(dbName + ".db")) bases.add(dbName);
        try (InputStream in = zip.getInputStream(entry);
            OutputStream out = new FileOutputStream(new File(validationDir, entry.getName()))) {
          copyStream(in, out, MAX_BACKUP_BYTES);
        }
      }
      if (!bases.contains("naver_line")) return false;
      for (String dbName : bases) {
        if (!validateDatabaseFamily(new File(validationDir, dbName + ".db"))) return false;
      }
      return true;
    } catch (Throwable ignored) {
      return false;
    } finally {
      deleteDirectory(validationDir);
    }
  }

  private static boolean restoreFromZip(Context context, File srcFile) {
    File stageDir = new File(context.getCacheDir(), "tencha_restore_stage");
    deleteDirectory(stageDir);
    if (!stageDir.mkdirs()) return false;
    try (ZipFile zip = new ZipFile(srcFile)) {
      if (zip.getEntry(MARKER_ENTRY) == null) {
        Log.e(LOG_TAG, "Restore failed: not a Tencha backup");
        return false;
      }

      Set<String> dbsToReplace = new HashSet<>();
      Enumeration<? extends ZipEntry> entries = zip.entries();
      while (entries.hasMoreElements()) {
        String name = entries.nextElement().getName();
        String dbName = matchedDbName(name);
        if (dbName != null && name.equals(dbName + ".db")) {
          dbsToReplace.add(dbName);
        }
      }

      entries = zip.entries();
      while (entries.hasMoreElements()) {
        ZipEntry e = entries.nextElement();
        if (e.isDirectory() || MARKER_ENTRY.equals(e.getName())) continue;

        String dbName = matchedDbName(e.getName());
        if (dbName == null) {
          Log.w(LOG_TAG, "Skipping unknown entry: " + e.getName());
          continue;
        }

        File target = new File(stageDir, e.getName());
        try (InputStream in = zip.getInputStream(e);
            OutputStream out = new FileOutputStream(target)) {
          copyStream(in, out);
        }
      }

      if (!dbsToReplace.contains("naver_line")) return false;
      for (String dbName : dbsToReplace) {
        if (!validateDatabaseFamily(new File(stageDir, dbName + ".db"))) return false;
      }
      return replaceDatabaseFamilies(context, stageDir, dbsToReplace);
    } catch (Exception e) {
      Log.e(LOG_TAG, "Zip restore failed: " + e.getMessage());
      return false;
    } finally {
      deleteDirectory(stageDir);
    }
  }

  private static boolean restoreLegacy(Context context, File srcFile) {
    try (SQLiteDatabase check =
        SQLiteDatabase.openDatabase(
            srcFile.getAbsolutePath(),
            null,
            SQLiteDatabase.OPEN_READONLY | SQLiteDatabase.NO_LOCALIZED_COLLATORS)) {
    } catch (Exception e) {
      Log.e(LOG_TAG, "Restore failed: Invalid database file");
      return false;
    }

    File stageDir = new File(context.getCacheDir(), "tencha_restore_legacy");
    try {
      deleteDirectory(stageDir);
      if (!stageDir.mkdirs()) return false;
      File staged = new File(stageDir, "naver_line.db");
      try (InputStream in = new FileInputStream(srcFile);
          OutputStream out = new FileOutputStream(staged)) {
        copyStream(in, out);
      }
      if (!validateDatabaseFamily(staged)) return false;
      return replaceDatabaseFamilies(
          context, stageDir, java.util.Collections.singleton("naver_line"));
    } catch (Exception e) {
      Log.e(LOG_TAG, "Legacy restore failed: " + e.getMessage());
      return false;
    } finally {
      deleteDirectory(stageDir);
    }
  }

  private static String matchedDbName(String entryName) {
    for (String name : DB_NAMES) {
      for (String suffix : DB_SUFFIXES) {
        if (entryName.equals(name + ".db" + suffix)) return name;
      }
    }
    return null;
  }

  private static void wipeDbFamily(File baseDb) {
    baseDb.delete();
    new File(baseDb.getPath() + "-wal").delete();
    new File(baseDb.getPath() + "-shm").delete();
  }

  private static boolean isZipFile(File file) {
    if (file == null || !file.exists() || file.length() < ZIP_MAGIC.length) return false;
    try (FileInputStream in = new FileInputStream(file)) {
      byte[] header = new byte[ZIP_MAGIC.length];
      return in.read(header) == ZIP_MAGIC.length && Arrays.equals(header, ZIP_MAGIC);
    } catch (Throwable t) {
      return false;
    }
  }

  private static void copyStream(InputStream in, OutputStream out) throws IOException {
    byte[] buf = new byte[COPY_BUFFER_SIZE];
    int n;
    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
  }

  private static void copyStream(InputStream in, OutputStream out, long maxBytes)
      throws IOException {
    byte[] buf = new byte[COPY_BUFFER_SIZE];
    long total = 0L;
    int n;
    while ((n = in.read(buf)) > 0) {
      total += n;
      if (total > maxBytes) throw new IOException("Backup is too large");
      out.write(buf, 0, n);
    }
  }

  private static ProgressDialog createSyncProgress(Context context, String text) {
    ProgressDialog pd = new ProgressDialog(context, LineTheme.dialogTheme(context));
    pd.setMessage(text);
    pd.setCancelable(false);
    return pd;
  }

  private static boolean validateDatabaseFamily(File baseDb) {
    if (!baseDb.isFile() || baseDb.length() == 0L) return false;
    try (SQLiteDatabase db =
            SQLiteDatabase.openDatabase(
                baseDb.getAbsolutePath(),
                null,
                SQLiteDatabase.OPEN_READONLY | SQLiteDatabase.NO_LOCALIZED_COLLATORS);
        Cursor cursor = db.rawQuery("PRAGMA integrity_check", null)) {
      return cursor.moveToFirst() && "ok".equalsIgnoreCase(cursor.getString(0));
    } catch (Throwable t) {
      Log.e(LOG_TAG, "Database validation failed for " + baseDb.getName() + ": " + t.getMessage());
      return false;
    }
  }

  private static boolean replaceDatabaseFamilies(
      Context context, File stageDir, Set<String> dbNames) {
    Map<File, File> rollback = new LinkedHashMap<>();
    Set<File> placed = new HashSet<>();
    try {
      for (String dbName : dbNames) {
        File base = context.getDatabasePath(dbName);
        for (String suffix : new String[] {"", "-wal", "-shm"}) {
          File current = new File(base.getPath() + suffix);
          if (!current.exists()) continue;
          File saved = new File(base.getPath() + suffix + ".tencha-rollback");
          if (saved.exists() && !saved.delete())
            throw new IOException("Cannot clear rollback file");
          moveReplacing(current, saved);
          rollback.put(current, saved);
        }
      }
      for (String dbName : dbNames) {
        File base = context.getDatabasePath(dbName);
        for (String suffix : DB_SUFFIXES) {
          File staged = new File(stageDir, dbName + ".db" + suffix);
          if (staged.isFile()) {
            File target = new File(base.getPath() + suffix);
            moveReplacing(staged, target);
            placed.add(target);
          }
        }
      }
      for (File saved : rollback.values()) saved.delete();
      return true;
    } catch (Throwable t) {
      Log.e(LOG_TAG, "Database replacement failed, rolling back: " + t.getMessage());
      for (File target : placed) {
        if (target.exists()) target.delete();
      }
      for (Map.Entry<File, File> entry : rollback.entrySet()) {
        try {
          if (entry.getValue().exists()) moveReplacing(entry.getValue(), entry.getKey());
        } catch (Throwable rollbackFailure) {
          Log.e(LOG_TAG, "Rollback failed: " + rollbackFailure.getMessage());
        }
      }
      return false;
    }
  }

  private static void moveReplacing(File source, File target) throws IOException {
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

  private static void deleteDirectory(File dir) {
    if (dir == null || !dir.exists()) return;
    File[] children = dir.listFiles();
    if (children != null)
      for (File child : children) {
        if (child.isDirectory()) deleteDirectory(child);
        else child.delete();
      }
    dir.delete();
  }

  private static void safeDismiss(ProgressDialog dialog) {
    try {
      if (dialog != null && dialog.isShowing()) dialog.dismiss();
    } catch (Throwable ignored) {
    }
  }

  private static boolean isContextUsable(Context context) {
    Context current = context;
    while (current instanceof ContextWrapper && !(current instanceof Activity)) {
      Context base = ((ContextWrapper) current).getBaseContext();
      if (base == current) break;
      current = base;
    }
    if (!(current instanceof Activity)) return false;
    Activity activity = (Activity) current;
    return !activity.isFinishing() && !activity.isDestroyed();
  }

  private static void notifySyncResult(Context context, boolean success, String sMsg, String eMsg) {
    Toast.makeText(context, success ? sMsg : eMsg, Toast.LENGTH_LONG).show();
  }
}
