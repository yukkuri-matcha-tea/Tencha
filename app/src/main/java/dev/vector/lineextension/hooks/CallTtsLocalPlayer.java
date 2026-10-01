package dev.vector.lineextension.hooks;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import dev.vector.lineextension.Vector;
import java.util.ArrayDeque;
import java.util.Deque;

/** Local TTS monitor kept separate from soundboard playback state. */
final class CallTtsLocalPlayer {
  private static final int SAMPLE_RATE = 16000;
  private static final Object LOCK = new Object();
  private static final Deque<short[]> queue = new ArrayDeque<>();
  private static Thread worker;
  private static boolean stopRequested;

  private CallTtsLocalPlayer() {}

  static void enqueue(short[] pcm) {
    synchronized (LOCK) {
      queue.addLast(pcm);
      stopRequested = false;
      if (worker == null || !worker.isAlive()) {
        worker = new Thread(CallTtsLocalPlayer::playQueue, "TenchaTtsLocal");
        worker.start();
      }
    }
  }

  static void clear() {
    synchronized (LOCK) {
      queue.clear();
      stopRequested = true;
    }
  }

  private static void playQueue() {
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
              .setBufferSizeInBytes(Math.max(minimum, 1280))
              .setTransferMode(AudioTrack.MODE_STREAM)
              .build();
      track.play();
      while (true) {
        short[] pcm;
        synchronized (LOCK) {
          if (stopRequested || queue.isEmpty()) break;
          pcm = queue.removeFirst();
        }
        int offset = 0;
        while (offset < pcm.length) {
          synchronized (LOCK) {
            if (stopRequested) return;
          }
          int count = Math.min(320, pcm.length - offset);
          int written = track.write(pcm, offset, count, AudioTrack.WRITE_BLOCKING);
          if (written <= 0) break;
          offset += written;
        }
      }
    } catch (Throwable error) {
      Vector.log("Tencha: local TTS playback failed", error);
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
        if (!queue.isEmpty() && !stopRequested) {
          worker = new Thread(CallTtsLocalPlayer::playQueue, "TenchaTtsLocal");
          worker.start();
        }
      }
    }
  }
}
