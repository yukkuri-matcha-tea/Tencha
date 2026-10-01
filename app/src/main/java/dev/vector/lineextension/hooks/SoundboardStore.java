package dev.vector.lineextension.hooks;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.provider.OpenableColumns;
import dev.vector.lineextension.Vector;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONObject;

/** Persistent LINE-side soundboard storage. Imported audio is normalized for LINE's WAV mixer. */
final class SoundboardStore {
  private static final String PREFS = "tencha_soundboard";
  private static final String KEY_CLIPS = "clips";
  private static final int TARGET_RATE = 16000;
  private static final int MAX_PCM_BYTES = 64 * 1024 * 1024;

  static final class Clip {
    final String id;
    final String name;
    final String path;

    Clip(String id, String name, String path) {
      this.id = id;
      this.name = name;
      this.path = path;
    }
  }

  private SoundboardStore() {}

  static List<Clip> load(Context context) {
    ArrayList<Clip> clips = new ArrayList<>();
    try {
      String raw =
          context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_CLIPS, "[]");
      JSONArray array = new JSONArray(raw);
      for (int i = 0; i < array.length(); i++) {
        JSONObject item = array.optJSONObject(i);
        if (item == null) continue;
        Clip clip = new Clip(item.optString("id"), item.optString("name"), item.optString("path"));
        if (!clip.id.isEmpty() && !clip.name.isEmpty() && new File(clip.path).isFile())
          clips.add(clip);
      }
    } catch (Throwable error) {
      Vector.log("Tencha: soundboard index read failed: " + error);
    }
    return clips;
  }

  static Clip importAudio(Context context, Uri uri) throws Exception {
    File directory = new File(context.getFilesDir(), "tencha_soundboard");
    if (!directory.isDirectory() && !directory.mkdirs())
      throw new IllegalStateException("保存先を作成できません");
    String id = UUID.randomUUID().toString();
    File output = new File(directory, id + ".wav");
    try {
      writeWav(output, decodeToPcm16(context, uri), TARGET_RATE);
      Clip clip = new Clip(id, displayName(context, uri), output.getAbsolutePath());
      List<Clip> clips = load(context);
      clips.add(clip);
      save(context, clips);
      return clip;
    } catch (Throwable error) {
      output.delete();
      if (error instanceof Exception) throw (Exception) error;
      throw new RuntimeException(error);
    }
  }

  static void remove(Context context, Clip clip) {
    List<Clip> clips = load(context);
    clips.removeIf(candidate -> candidate.id.equals(clip.id));
    save(context, clips);
    try {
      new File(clip.path).delete();
    } catch (Throwable ignored) {
    }
  }

  static long durationMs(Clip clip) {
    long pcmBytes = Math.max(0L, new File(clip.path).length() - 44L);
    return Math.max(250L, (pcmBytes * 1000L) / (TARGET_RATE * 2L));
  }

  static short[] readPcm16(Clip clip) throws Exception {
    File file = new File(clip.path);
    long pcmBytes = file.length() - 44L;
    if (pcmBytes <= 0L || pcmBytes > MAX_PCM_BYTES || pcmBytes > Integer.MAX_VALUE) {
      throw new IllegalArgumentException("音声データが不正です");
    }
    byte[] bytes = new byte[(int) pcmBytes];
    try (FileInputStream input = new FileInputStream(file)) {
      long skipped = 0L;
      while (skipped < 44L) {
        long count = input.skip(44L - skipped);
        if (count <= 0L) throw new IllegalArgumentException("WAVヘッダーが不正です");
        skipped += count;
      }
      int offset = 0;
      while (offset < bytes.length) {
        int count = input.read(bytes, offset, bytes.length - offset);
        if (count < 0) throw new IllegalArgumentException("音声データが途中で終了しました");
        offset += count;
      }
    }
    short[] pcm = new short[bytes.length / 2];
    ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(pcm);
    return pcm;
  }

  private static void save(Context context, List<Clip> clips) {
    JSONArray array = new JSONArray();
    for (Clip clip : clips) {
      JSONObject item = new JSONObject();
      try {
        item.put("id", clip.id);
        item.put("name", clip.name);
        item.put("path", clip.path);
        array.put(item);
      } catch (Throwable ignored) {
      }
    }
    SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    preferences.edit().putString(KEY_CLIPS, array.toString()).apply();
  }

  private static String displayName(Context context, Uri uri) {
    try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
      if (cursor != null && cursor.moveToFirst()) {
        int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
        if (index >= 0) {
          String name = cursor.getString(index);
          if (name != null && !name.trim().isEmpty()) return name;
        }
      }
    } catch (Throwable ignored) {
    }
    return "サウンド " + System.currentTimeMillis();
  }

  static short[] decodeToPcm16(Context context, Uri uri) throws Exception {
    MediaExtractor extractor = new MediaExtractor();
    MediaCodec codec = null;
    ByteArrayOutputStream pcm = new ByteArrayOutputStream();
    int sampleRate = 0;
    int channelCount = 0;
    int pcmEncoding = AudioFormat.ENCODING_PCM_16BIT;
    try {
      extractor.setDataSource(context, uri, null);
      int track = -1;
      MediaFormat inputFormat = null;
      for (int i = 0; i < extractor.getTrackCount(); i++) {
        MediaFormat candidate = extractor.getTrackFormat(i);
        String mime = candidate.getString(MediaFormat.KEY_MIME);
        if (mime != null && mime.startsWith("audio/")) {
          track = i;
          inputFormat = candidate;
          break;
        }
      }
      if (track < 0 || inputFormat == null) throw new IllegalArgumentException("音声トラックがありません");
      extractor.selectTrack(track);
      String mime = inputFormat.getString(MediaFormat.KEY_MIME);
      codec = MediaCodec.createDecoderByType(mime);
      codec.configure(inputFormat, null, null, 0);
      codec.start();

      MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
      boolean inputDone = false;
      boolean outputDone = false;
      while (!outputDone) {
        if (!inputDone) {
          int inputIndex = codec.dequeueInputBuffer(10000);
          if (inputIndex >= 0) {
            ByteBuffer input = codec.getInputBuffer(inputIndex);
            if (input == null) throw new IllegalStateException("デコーダー入力を取得できません");
            int size = extractor.readSampleData(input, 0);
            if (size < 0) {
              codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
              inputDone = true;
            } else {
              codec.queueInputBuffer(inputIndex, 0, size, extractor.getSampleTime(), 0);
              extractor.advance();
            }
          }
        }
        int outputIndex = codec.dequeueOutputBuffer(info, 10000);
        if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
          MediaFormat format = codec.getOutputFormat();
          sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
          channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
          if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
            pcmEncoding = format.getInteger(MediaFormat.KEY_PCM_ENCODING);
          }
        } else if (outputIndex >= 0) {
          ByteBuffer buffer = codec.getOutputBuffer(outputIndex);
          if (buffer != null && info.size > 0) {
            buffer.position(info.offset);
            buffer.limit(info.offset + info.size);
            byte[] bytes = new byte[info.size];
            buffer.get(bytes);
            pcm.write(bytes);
            if (pcm.size() > MAX_PCM_BYTES) throw new IllegalArgumentException("音声が長すぎます");
          }
          outputDone = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
          codec.releaseOutputBuffer(outputIndex, false);
        }
      }
    } finally {
      extractor.release();
      if (codec != null) {
        try {
          codec.stop();
        } catch (Throwable ignored) {
        }
        codec.release();
      }
    }
    if (sampleRate <= 0 || channelCount <= 0 || pcm.size() == 0)
      throw new IllegalArgumentException("音声を変換できません");
    short[] mono = toMono16(pcm.toByteArray(), channelCount, pcmEncoding);
    return resample(mono, sampleRate, TARGET_RATE);
  }

  private static short[] toMono16(byte[] source, int channels, int encoding) {
    if (encoding == AudioFormat.ENCODING_PCM_FLOAT) {
      int frames = source.length / (4 * channels);
      short[] result = new short[frames];
      ByteBuffer buffer = ByteBuffer.wrap(source).order(ByteOrder.LITTLE_ENDIAN);
      for (int frame = 0; frame < frames; frame++) {
        float sum = 0f;
        for (int channel = 0; channel < channels; channel++) sum += buffer.getFloat();
        float value = Math.max(-1f, Math.min(1f, sum / channels));
        result[frame] = (short) (value * 32767f);
      }
      return result;
    }
    int frames = source.length / (2 * channels);
    short[] result = new short[frames];
    ByteBuffer buffer = ByteBuffer.wrap(source).order(ByteOrder.LITTLE_ENDIAN);
    for (int frame = 0; frame < frames; frame++) {
      int sum = 0;
      for (int channel = 0; channel < channels; channel++) sum += buffer.getShort();
      result[frame] = (short) (sum / channels);
    }
    return result;
  }

  private static short[] resample(short[] source, int sourceRate, int targetRate) {
    if (sourceRate == targetRate) return source;
    int length = Math.max(1, (int) (((long) source.length * targetRate) / sourceRate));
    short[] result = new short[length];
    double step = (double) sourceRate / targetRate;
    for (int i = 0; i < length; i++) {
      double position = i * step;
      int lower = Math.min(source.length - 1, (int) position);
      int upper = Math.min(source.length - 1, lower + 1);
      double fraction = position - lower;
      result[i] = (short) Math.round(source[lower] * (1.0 - fraction) + source[upper] * fraction);
    }
    return result;
  }

  private static void writeWav(File output, short[] samples, int sampleRate) throws Exception {
    int dataLength = samples.length * 2;
    try (FileOutputStream stream = new FileOutputStream(output, false)) {
      ByteBuffer header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
      header.put(new byte[] {'R', 'I', 'F', 'F'});
      header.putInt(36 + dataLength);
      header.put(new byte[] {'W', 'A', 'V', 'E', 'f', 'm', 't', ' '});
      header.putInt(16);
      header.putShort((short) 1);
      header.putShort((short) 1);
      header.putInt(sampleRate);
      header.putInt(sampleRate * 2);
      header.putShort((short) 2);
      header.putShort((short) 16);
      header.put(new byte[] {'d', 'a', 't', 'a'});
      header.putInt(dataLength);
      stream.write(header.array());
      ByteBuffer data =
          ByteBuffer.allocate(Math.min(dataLength, 8192)).order(ByteOrder.LITTLE_ENDIAN);
      for (short sample : samples) {
        if (data.remaining() < 2) {
          stream.write(data.array(), 0, data.position());
          data.clear();
        }
        data.putShort(sample);
      }
      if (data.position() > 0) stream.write(data.array(), 0, data.position());
    }
  }
}
