package dev.vector.lineextension.hooks;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
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
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public final class BackupRestoreHook {

  private static final String LOG_TAG = "TenchaSync";
  private static final ExecutorService syncExecutor = Executors.newSingleThreadExecutor();
  private static final Handler uiHandler = new Handler(Looper.getMainLooper());

  private static final List<String> DB_NAMES = Arrays.asList("naver_line", "contact", "square");
  private static final String[] DB_SUFFIXES = {"", "-wal"};
  private static final byte[] ZIP_MAGIC = {0x50, 0x4B, 0x03, 0x04};
  private static final String MARKER_ENTRY = "vector-backup-v1";
  private static final int COPY_BUFFER_SIZE = 64 * 1024;

  public static void runBackup(Context context) {
    runBackup(context, null);
  }

  public static void runBackup(Context context, Runnable onSuccess) {
    final ProgressDialog pd = createSyncProgress(context, ModuleStrings.RESTORE_PREPARING);
    pd.show();
    LineTheme.applyDialogColors(pd, context);

    syncExecutor.execute(
        () -> {
          final boolean result = executeVectorBackup(context);
          uiHandler.post(
              () -> {
                pd.dismiss();
                if (!result || onSuccess == null) {
                  notifySyncResult(
                      context, result, ModuleStrings.BACKUP_SUCCESS, ModuleStrings.BACKUP_ERROR);
                }
                if (result && onSuccess != null) onSuccess.run();
              });
        });
  }

  public static void exportInternalBackup(Context context, Uri destination) {
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
                pd.dismiss();
                notifySyncResult(
                    context,
                    finalResult,
                    ModuleStrings.BACKUP_EXPORT_SUCCESS,
                    ModuleStrings.BACKUP_EXPORT_ERROR);
              });
        });
  }

  public static void runRestore(Context context, File backupFile) {
    final ProgressDialog pd = createSyncProgress(context, ModuleStrings.RESTORE_PROCESSING);
    pd.show();
    LineTheme.applyDialogColors(pd, context);

    syncExecutor.execute(
        () -> {
          final boolean result = executeFullRestore(context, backupFile);
          uiHandler.post(
              () -> {
                pd.dismiss();
                if (result) {
                  LineTheme.applyDialogColors(
                      new AlertDialog.Builder(context, LineTheme.dialogTheme(context))
                          .setTitle(ModuleStrings.RESTORE_SUCCESS)
                          .setMessage(ModuleStrings.MANAGER_RESTART_REQUIRED)
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
                if (backupFile.getName().startsWith("vector_restore_")) {
                  backupFile.delete();
                }
              });
        });
  }

  public static boolean hasInternalBackup(Context context) {
    return ControlClient.storeExists(context, ControlProvider.LINE_BACKUP_FILE);
  }

  public static void runRestoreInternal(Context context) {
    final ProgressDialog pd = createSyncProgress(context, ModuleStrings.RESTORE_PROCESSING);
    pd.show();
    LineTheme.applyDialogColors(pd, context);

    syncExecutor.execute(
        () -> {
          File tempFile = null;
          boolean result = false;
          try {
            if (!hasInternalBackup(context)) throw new IOException("Internal backup not found");
            tempFile =
                File.createTempFile(
                    "vector_restore_internal_", ".tenchabak", context.getCacheDir());
            try (InputStream in =
                    context
                        .getContentResolver()
                        .openInputStream(ControlClient.storeUri(ControlProvider.LINE_BACKUP_FILE));
                OutputStream out = new FileOutputStream(tempFile, false)) {
              if (in == null) throw new IOException("Cannot open internal backup");
              copyStream(in, out);
            }
            result = executeFullRestore(context, tempFile);
          } catch (Exception e) {
            Log.e(LOG_TAG, "Internal restore failed: " + e.getMessage());
          }
          final boolean finalResult = result;
          final File finalTempFile = tempFile;
          uiHandler.post(
              () -> {
                pd.dismiss();
                if (finalResult) {
                  LineTheme.applyDialogColors(
                      new AlertDialog.Builder(context, LineTheme.dialogTheme(context))
                          .setTitle(ModuleStrings.RESTORE_SUCCESS)
                          .setMessage(ModuleStrings.MANAGER_RESTART_REQUIRED)
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
                if (finalTempFile != null) finalTempFile.delete();
              });
        });
  }

  private static boolean executeVectorBackup(Context context) {
    try {
      File mainDb = context.getDatabasePath("naver_line");
      if (!mainDb.exists()) {
        Log.e(LOG_TAG, "Source database not found");
        return false;
      }

      writeBackupZip(context, ControlClient.storeUri(ControlProvider.LINE_BACKUP_TEMP_FILE));
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
          for (String suffix : DB_SUFFIXES) {
            File f = new File(baseDb.getPath() + suffix);
            if (f.isFile()) {
              writeFileEntry(zip, dbName + ".db" + suffix, f);
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

  private static boolean restoreFromZip(Context context, File srcFile) {
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

      for (String dbName : dbsToReplace) wipeDbFamily(context.getDatabasePath(dbName));

      entries = zip.entries();
      while (entries.hasMoreElements()) {
        ZipEntry e = entries.nextElement();
        if (e.isDirectory() || MARKER_ENTRY.equals(e.getName())) continue;

        String dbName = matchedDbName(e.getName());
        if (dbName == null) {
          Log.w(LOG_TAG, "Skipping unknown entry: " + e.getName());
          continue;
        }

        File baseDb = context.getDatabasePath(dbName);
        File target = new File(baseDb.getPath() + e.getName().substring((dbName + ".db").length()));
        try (InputStream in = zip.getInputStream(e);
            OutputStream out = new FileOutputStream(target)) {
          copyStream(in, out);
        }
      }
      return true;
    } catch (Exception e) {
      Log.e(LOG_TAG, "Zip restore failed: " + e.getMessage());
      return false;
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

    File localDb = context.getDatabasePath("naver_line");
    try {
      wipeDbFamily(localDb);
      try (InputStream in = new FileInputStream(srcFile);
          OutputStream out = new FileOutputStream(localDb)) {
        copyStream(in, out);
      }
      return true;
    } catch (Exception e) {
      Log.e(LOG_TAG, "Legacy restore failed: " + e.getMessage());
      return false;
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

  private static ProgressDialog createSyncProgress(Context context, String text) {
    ProgressDialog pd = new ProgressDialog(context, LineTheme.dialogTheme(context));
    pd.setMessage(text);
    pd.setCancelable(false);
    return pd;
  }

  private static void notifySyncResult(Context context, boolean success, String sMsg, String eMsg) {
    Toast.makeText(context, success ? sMsg : eMsg, Toast.LENGTH_LONG).show();
  }
}
