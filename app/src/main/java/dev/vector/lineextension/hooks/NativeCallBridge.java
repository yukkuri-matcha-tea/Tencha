package dev.vector.lineextension.hooks;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.util.Log;
import dev.vector.lineextension.Vector;

/** Runtime-validated native bridge for LINE's participant-to-SSRC receive mixer. */
final class NativeCallBridge {
  private static volatile boolean available;
  private static volatile boolean attempted;

  private NativeCallBridge() {}

  static synchronized boolean ensureLoaded(Context context) {
    if (available) return true;
    if (attempted) return false;
    attempted = true;
    try {
      System.loadLibrary("tencha_call");
      available = true;
    } catch (Throwable classLoaderError) {
      try {
        ApplicationInfo module =
            context.getPackageManager().getApplicationInfo("dev.vector.lineextension", 0);
        System.load(module.nativeLibraryDir + "/libtencha_call.so");
        available = true;
      } catch (Throwable absolutePathError) {
        Log.e("TenchaCall", "Native bridge load failed", absolutePathError);
        Vector.log("Tencha: participant volume native bridge unavailable", absolutePathError);
      }
    }
    if (available) Vector.log("Tencha: participant volume native bridge loaded");
    return available;
  }

  static boolean setParticipantVolume(long audioSessionStream, String participantId, int percent) {
    if (!available
        || audioSessionStream == 0L
        || participantId == null
        || participantId.isEmpty()) {
      Log.e(
          "TenchaCall",
          "Rejected before JNI: available="
              + available
              + " stream="
              + audioSessionStream
              + " participant="
              + (participantId == null ? "null" : participantId.length()));
      return false;
    }
    try {
      return nativeSetParticipantVolume(
          audioSessionStream, participantId, Math.max(0, Math.min(200, percent)) / 100f);
    } catch (Throwable error) {
      Log.e("TenchaCall", "Participant volume JNI call failed", error);
      Vector.log("Tencha: participant volume call failed", error);
      return false;
    }
  }

  static boolean enqueueSoundboard(Context context, short[] pcm) {
    if (pcm == null || pcm.length == 0 || !ensureLoaded(context)) return false;
    try {
      return nativeInstallSoundboardMixer() && nativeEnqueueSoundboard(pcm);
    } catch (Throwable error) {
      Log.e("TenchaCall", "Soundboard PCM enqueue failed", error);
      Vector.log("Tencha: soundboard PCM enqueue failed", error);
      return false;
    }
  }

  static boolean prepareSoundboard(Context context) {
    if (context == null || !ensureLoaded(context)) return false;
    try {
      return nativeInstallSoundboardMixer();
    } catch (Throwable error) {
      Log.e("TenchaCall", "Soundboard PCM mixer installation failed", error);
      return false;
    }
  }

  static void clearSoundboard() {
    if (!available) return;
    try {
      nativeClearSoundboard();
    } catch (Throwable error) {
      Log.e("TenchaCall", "Soundboard PCM clear failed", error);
    }
  }

  private static native boolean nativeSetParticipantVolume(
      long audioSessionStream, String participantId, float multiplier);

  private static native boolean nativeInstallSoundboardMixer();

  private static native boolean nativeEnqueueSoundboard(short[] pcm);

  private static native void nativeClearSoundboard();
}
