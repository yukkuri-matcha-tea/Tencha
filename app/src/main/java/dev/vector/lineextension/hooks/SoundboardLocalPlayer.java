package dev.vector.lineextension.hooks;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import dev.vector.lineextension.Vector;

/** A single local AudioTrack fed by an unbounded logical tap timeline. */
final class SoundboardLocalPlayer {
  private static final int SAMPLE_RATE = 16000;
  private static final int FRAME_SAMPLES = 320;
  private static final Object LOCK = new Object();
  private static final PcmVoiceMixer mixer = new PcmVoiceMixer();
  private static Thread worker;
  private static boolean stopRequested;

  private SoundboardLocalPlayer() {}

  static void enqueue(short[] pcm) {
    synchronized (LOCK) {
      mixer.enqueue(pcm);
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
      mixer.clear();
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
          if (stopRequested || mixer.isEmpty()) break;
          count = mixer.render(frame);
        }
        track.write(frame, 0, count, AudioTrack.WRITE_BLOCKING);
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
        if (!mixer.isEmpty() && !stopRequested) {
          worker = new Thread(SoundboardLocalPlayer::runMixer, "TenchaSoundboardLocal");
          worker.start();
        }
      }
    }
  }
}
