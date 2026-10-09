package dev.vector.lineextension.hooks;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import dev.vector.lineextension.Main;
import dev.vector.lineextension.SettingsStore;
import java.io.File;
import java.util.UUID;

/** Separate local ringtone storage; imports do not add clips to the soundboard. */
final class CallToneStore {
  enum Kind {
    RINGTONE("call_ringtone", "tencha_ringtones", "着信音"),
    RINGBACK("call_ringback", "tencha_ringbacks", "呼出音");

    final String key;
    final String directory;
    final String label;

    Kind(String key, String directory, String label) {
      this.key = key;
      this.directory = directory;
      this.label = label;
    }
  }

  private CallToneStore() {}

  static File selectedFile(Context context, Kind kind) {
    try {
      String path =
          context
              .getSharedPreferences("tencha_call_tones", Context.MODE_PRIVATE)
              .getString(kind.key + "_file", SettingsStore.getString(kind.key + "_file", ""));
      if (path.isEmpty()) return null;
      File file = new File(path).getCanonicalFile();
      File directory = new File(context.getFilesDir(), kind.directory).getCanonicalFile();
      return directory.equals(file.getParentFile()) && file.isFile() && file.length() > 44
          ? file
          : null;
    } catch (Exception error) {
      return null;
    }
  }

  static String selectedName(Context context, Kind kind) {
    return context
        .getSharedPreferences("tencha_call_tones", Context.MODE_PRIVATE)
        .getString(kind.key + "_name", SettingsStore.getString(kind.key + "_name", "音声ファイルを選択"));
  }

  static synchronized String importAudio(Context context, Uri source, Kind kind) throws Exception {
    File directory = new File(context.getFilesDir(), kind.directory);
    if (!directory.isDirectory() && !directory.mkdirs())
      throw new IllegalStateException("保存先を作成できません");
    short[] pcm = SoundboardStore.decodeToPcm16(context, source);
    if (pcm.length == 0) throw new IllegalArgumentException("音声が空です");
    File previous = selectedFile(context, kind);
    File output = new File(directory, UUID.randomUUID() + ".wav");
    String name = "選択した音声";
    try (Cursor cursor =
        context
            .getContentResolver()
            .query(source, new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null)) {
      if (cursor != null && cursor.moveToFirst()) {
        String value = cursor.getString(0);
        if (value != null && !value.trim().isEmpty()) name = value;
      }
    } catch (Exception ignored) {
    }
    SharedPreferences preferences =
        context.getSharedPreferences("tencha_call_tones", Context.MODE_PRIVATE);
    String oldPath = preferences.getString(kind.key + "_file", null);
    String oldName = preferences.getString(kind.key + "_name", null);
    boolean committed = false;
    try {
      SoundboardStore.writeWav(output, pcm, 16000);
      // SettingsStore's runtime cache is not a durable-write acknowledgement.
      // Commit both fields atomically before retiring the previous imported file.
      if (!preferences
          .edit()
          .putString(kind.key + "_file", output.getAbsolutePath())
          .putString(kind.key + "_name", name)
          .commit()) {
        // commit() can update the in-memory preferences even when its disk write fails.
        preferences
            .edit()
            .putString(kind.key + "_file", oldPath)
            .putString(kind.key + "_name", oldName)
            .commit();
        throw new IllegalStateException("設定を保存できません");
      }
      committed = true;
      SettingsStore.save(kind.key + "_file", output.getAbsolutePath());
      (kind == Kind.RINGTONE ? Main.options.callRingtoneFile : Main.options.callRingbackFile)
              .value =
          output.getAbsolutePath();
      SettingsStore.save(kind.key + "_name", name);
      if (previous != null && !previous.equals(output)) previous.delete();
      return name;
    } catch (Exception error) {
      if (!committed) output.delete();
      throw error;
    }
  }
}
