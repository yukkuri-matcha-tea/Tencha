package dev.vector.lineextension.hooks;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import dev.vector.lineextension.Vector;
import java.util.Arrays;

/** A single local AudioTrack fed by an unbounded logical tap timeline. */
final class SoundboardLocalPlayer {
  private static final int SAMPLE_RATE = 16000;
  private static final int FRAME_SAMPLES = 320;
  private static final Object LOCK = new Object();
  private static long[] timeline = new long[FRAME_SAMPLES * 8];
  private static int readIndex;
  private static int validEnd;
  private static Thread worker;
  private static boolean stopRequested;

  private SoundboardLocalPlayer() {}

  static void enqueue(short[] pcm) {
    synchronized (LOCK) {
      compactIfUseful(pcm.length);
      ensureCapacity(readIndex + pcm.length);
      for (int i = 0; i < pcm.length; i++) timeline[readIndex + i] += pcm[i];
      validEnd = Math.max(validEnd, readIndex + pcm.length);
      stopRequested = false;
      if (worker == null || !worker.isAlive()) {
        worker = new Thread(SoundboardLocalPlayer::runMixer, "TenchaSoundboardLocal");
        worker.start();
      }
      LOCK.notifyAll();
    }
  }

  static void clear() {
    synchronized (LOCK) {
      Arrays.fill(timeline, 0, validEnd, 0L);
      readIndex = 0;
      validEnd = 0;
      stopRequested = true;
      LOCK.notifyAll();
    }
  }

  private static void runMixer() {
    AudioTrack track = null;
    try {
      int minimum =
          AudioTrack.getMinBufferSize(
              SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
      track =
          new AudioTrack.Builder()
              .setAudioAttributes(
                  new AudioAttributes.Builder()
                      .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                      .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                      .build())
              .setAudioFormat(
                  new AudioFormat.Builder()
                      .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                      .setSampleRate(SAMPLE_RATE)
                      .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                      .build())
              .setBufferSizeInBytes(Math.max(minimum, FRAME_SAMPLES * 4))
              .setTransferMode(AudioTrack.MODE_STREAM)
              .build();
      track.play();
      short[] frame = new short[FRAME_SAMPLES];
      while (true) {
        int count;
        synchronized (LOCK) {
          if (stopRequested || readIndex >= validEnd) break;
          count = Math.min(frame.length, validEnd - readIndex);
          for (int i = 0; i < count; i++) {
            long mixed = timeline[readIndex];
            timeline[readIndex++] = 0L;
            frame[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, mixed));
          }
          Arrays.fill(frame, count, frame.length, (short) 0);
          if (readIndex >= validEnd) {
            readIndex = 0;
            validEnd = 0;
          }
        }
        track.write(frame, 0, frame.length, AudioTrack.WRITE_BLOCKING);
      }
    } catch (Throwable error) {
      Vector.log("Tencha: local soundboard monitor failed", error);
    } finally {
      if (track != null) {
        try {
          track.stop();
        } catch (Throwable ignored) {
        }
        track.release();
      }
      synchronized (LOCK) {
        worker = null;
        if (validEnd > readIndex && !stopRequested) {
          worker = new Thread(SoundboardLocalPlayer::runMixer, "TenchaSoundboardLocal");
          worker.start();
        }
      }
    }
  }

  private static void compactIfUseful(int incoming) {
    if (readIndex == 0 || timeline.length - validEnd >= incoming) return;
    int remaining = validEnd - readIndex;
    System.arraycopy(timeline, readIndex, timeline, 0, remaining);
    Arrays.fill(timeline, remaining, validEnd, 0L);
    readIndex = 0;
    validEnd = remaining;
  }

  private static void ensureCapacity(int required) {
    if (required <= timeline.length) return;
    int capacity = timeline.length;
    while (capacity < required) capacity = Math.max(capacity * 2, required);
    timeline = Arrays.copyOf(timeline, capacity);
  }
}
