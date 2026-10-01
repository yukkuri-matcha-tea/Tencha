package dev.vector.lineextension.hooks;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.StatusBarNotification;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import dev.vector.lineextension.Vector;
import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Per-call Android TTS queue. Message text remains in memory and generated audio is temporary. */
final class CallTtsManager {
  private static final int MAX_QUEUE = 12;
  private static final long MAX_AGE_MS = 60_000L;
  private static final int MAX_SEEN_IDS = 512;
  private static final Object LOCK = new Object();
  private static final Handler MAIN = new Handler(Looper.getMainLooper());
  private static final ExecutorService AUDIO_WORKER = Executors.newSingleThreadExecutor();
  private static final Deque<Entry> queue = new ArrayDeque<>();
  private static final LinkedHashSet<String> seenIds = new LinkedHashSet<>();
  private static final LinkedHashMap<String, CallTtsSettings.Person> observedPeople =
      new LinkedHashMap<>();

  private static Context context;
  private static TextToSpeech tts;
  private static boolean ttsReady;
  private static boolean initStarted;
  private static boolean callActive;
  private static boolean sessionEnabled;
  private static boolean busy;
  private static boolean callUiDetached;
  private static int callStateCheckToken;
  private static int generation;
  private static File activeFile;
  private static String activeUtterance;
  private static String activeOutput;
  private static String selfDisplayName;

  private static final class Entry {
    final String text;
    final long queuedAt;

    Entry(String text) {
      this.text = text;
      this.queuedAt = System.currentTimeMillis();
    }
  }

  private CallTtsManager() {}

  static void startCall(Context value) {
    synchronized (LOCK) {
      context = value.getApplicationContext();
      callUiDetached = false;
      callStateCheckToken++;
      if (callActive) return;
      callActive = true;
      sessionEnabled = false;
      selfDisplayName = null;
      clearLocked(false);
      // Initialize the TTS engine only after the user enables it for this call. Creating a second
      // audio client during LINE 26.15's VoIP startup can race its AudioTrack connection state.
    }
  }

  static void endCall() {
    synchronized (LOCK) {
      endCallLocked();
    }
  }

  /**
   * The VoIP activity is also destroyed when the user returns to a chat while the call continues.
   * Keep TTS alive until Android's communication audio mode confirms that the actual call ended.
   */
  static void onCallUiDestroyed(Context value) {
    synchronized (LOCK) {
      context = value.getApplicationContext();
      callUiDetached = true;
      int token = ++callStateCheckToken;
      scheduleCallStateCheckLocked(token);
    }
  }

  private static void scheduleCallStateCheckLocked(int token) {
    MAIN.postDelayed(
        () -> {
          synchronized (LOCK) {
            if (token != callStateCheckToken || !callActive || !callUiDetached) return;
            if (hasOngoingCallNotificationLocked()) {
              scheduleCallStateCheckLocked(token);
              return;
            }
            Log.i("TenchaCall", "TTS call ended after ongoing notification disappeared");
            endCallLocked();
          }
        },
        1500L);
  }

  private static boolean hasOngoingCallNotificationLocked() {
    if (context == null) return false;
    try {
      NotificationManager manager =
          (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
      if (manager == null) return false;
      for (StatusBarNotification item : manager.getActiveNotifications()) {
        Notification notification = item.getNotification();
        if (notification == null) continue;
        String channel = notification.getChannelId();
        if (Notification.CATEGORY_CALL.equals(notification.category)
            || (channel != null && channel.contains("VoIP"))) return true;
      }
    } catch (Throwable error) {
      Log.w("TenchaCall", "Unable to inspect ongoing call notification", error);
      // Do not disable an active TTS session merely because the OEM blocked this query.
      return true;
    }
    return false;
  }

  private static void endCallLocked() {
    callStateCheckToken++;
    callUiDetached = false;
    callActive = false;
    sessionEnabled = false;
    selfDisplayName = null;
    clearLocked(true);
  }

  static boolean isSessionEnabled() {
    synchronized (LOCK) {
      return sessionEnabled;
    }
  }

  static int enabledSessionToken() {
    synchronized (LOCK) {
      return callActive && sessionEnabled ? generation : -1;
    }
  }

  static void onCommittedSelfText(int token, String id, String mid, String name, String text) {
    synchronized (LOCK) {
      if (token != generation || !callActive || !sessionEnabled) return;
      onTextMessage(id, mid, name, text, true);
    }
  }

  static void setSelfDisplayName(String value) {
    if (value == null) return;
    String clean = value.trim();
    if (clean.isEmpty()) return;
    synchronized (LOCK) {
      selfDisplayName = clean;
      Log.i("TenchaCall", "TTS own display name captured");
    }
  }

  static void setSessionEnabled(Context value, boolean enabled) {
    synchronized (LOCK) {
      context = value.getApplicationContext();
      sessionEnabled = enabled && callActive;
      Log.i(
          "TenchaCall",
          "TTS session "
              + (sessionEnabled ? "enabled" : "disabled")
              + ", callActive="
              + callActive);
      if (sessionEnabled) prepareTtsLocked();
      else clearLocked(false);
    }
  }

  static void onTextMessage(
      String messageId, String senderMid, String senderName, String text, boolean fromSelf) {
    synchronized (LOCK) {
      if (!callActive) return;
      if (senderMid != null && !senderMid.isEmpty()) {
        String name = senderName;
        if (name == null || name.trim().isEmpty())
          name =
              fromSelf && selfDisplayName != null ? selfDisplayName : (fromSelf ? "自分" : senderMid);
        observedPeople.put(senderMid, new CallTtsSettings.Person(senderMid, name));
      }
      if (!sessionEnabled || text == null || text.trim().isEmpty()) return;
      if (messageId != null && !messageId.isEmpty()) {
        if (!seenIds.add(messageId)) return;
        while (seenIds.size() > MAX_SEEN_IDS) seenIds.remove(seenIds.iterator().next());
      }
      if (!CallTtsPolicy.matchesTarget(
          CallTtsSettings.target(), senderMid, fromSelf, CallTtsSettings.selectedMids())) return;
      String clean = CallTtsPolicy.normalize(text, CallTtsSettings.maxChars());
      if (clean == null) return;
      if (CallTtsSettings.includeSenderName()) {
        String name = senderName;
        if (name == null || name.trim().isEmpty())
          name = fromSelf && selfDisplayName != null ? selfDisplayName : (fromSelf ? "自分" : "送信者");
        clean = name + "、" + clean;
      }
      while (queue.size() >= MAX_QUEUE) queue.removeFirst();
      queue.addLast(new Entry(clean));
      Log.i(
          "TenchaCall",
          "TTS message queued: chars=" + clean.length() + ", pending=" + queue.size());
      drainLocked();
    }
  }

  static List<CallTtsSettings.Person> observedPeople() {
    synchronized (LOCK) {
      return new ArrayList<>(observedPeople.values());
    }
  }

  private static void prepareTtsLocked() {
    if (ttsReady || initStarted || context == null) return;
    initStarted = true;
    Context app = context;
    MAIN.post(
        () -> {
          try {
            TextToSpeech engine =
                new TextToSpeech(
                    app,
                    status -> {
                      // Some engines complete setup from inside their constructor. Defer handling
                      // until the constructor has returned and the engine has been assigned to
                      // {@link #tts}; otherwise a successful callback can be mistaken for failure.
                      MAIN.post(
                          () -> {
                            synchronized (LOCK) {
                              initStarted = false;
                              ttsReady = status == TextToSpeech.SUCCESS && tts != null;
                              if (!ttsReady) {
                                Log.e(
                                    "TenchaCall",
                                    "Android TTS initialization failed: status="
                                        + status
                                        + ", engine="
                                        + (tts != null));
                                queue.clear();
                                busy = false;
                              } else {
                                Log.i("TenchaCall", "Android TTS engine ready");
                                installListenerLocked();
                                drainLocked();
                              }
                            }
                          });
                    });
            synchronized (LOCK) {
              tts = engine;
            }
          } catch (Throwable error) {
            synchronized (LOCK) {
              initStarted = false;
              ttsReady = false;
              busy = false;
              queue.clear();
            }
            Vector.log("Tencha: Android TTS initialization failed", error);
          }
        });
  }

  private static void installListenerLocked() {
    tts.setOnUtteranceProgressListener(
        new UtteranceProgressListener() {
          @Override
          public void onStart(String utteranceId) {
            Log.i("TenchaCall", "TTS synthesis started");
          }

          @Override
          public void onDone(String utteranceId) {
            Log.i("TenchaCall", "TTS synthesis completed");
            handleDone(utteranceId);
          }

          @Override
          public void onError(String utteranceId) {
            Log.e("TenchaCall", "TTS synthesis failed");
            finishUtterance(utteranceId);
          }

          @Override
          public void onStop(String utteranceId, boolean interrupted) {
            finishUtterance(utteranceId);
          }
        });
  }

  private static void drainLocked() {
    if (busy || !callActive || !sessionEnabled) return;
    prepareTtsLocked();
    if (!ttsReady || tts == null) return;
    Entry entry;
    do {
      entry = queue.pollFirst();
    } while (entry != null && System.currentTimeMillis() - entry.queuedAt > MAX_AGE_MS);
    if (entry == null) return;

    busy = true;
    activeUtterance = "tencha-tts-" + UUID.randomUUID();
    activeOutput = CallTtsSettings.output();
    Bundle params = new Bundle();
    int result;
    if (CallTtsSettings.OUTPUT_LOCAL.equals(activeOutput)) {
      result = tts.speak(entry.text, TextToSpeech.QUEUE_FLUSH, params, activeUtterance);
    } else {
      File directory = new File(context.getCacheDir(), "tencha_tts");
      if (!directory.isDirectory()) directory.mkdirs();
      activeFile = new File(directory, activeUtterance + ".wav");
      result = tts.synthesizeToFile(entry.text, params, activeFile, activeUtterance);
    }
    Log.i("TenchaCall", "TTS request submitted: output=" + activeOutput + ", result=" + result);
    if (result == TextToSpeech.ERROR) finishUtterance(activeUtterance);
  }

  private static void handleDone(String utteranceId) {
    final File file;
    final String output;
    final Context app;
    final int token;
    synchronized (LOCK) {
      if (!utteranceId.equals(activeUtterance)) return;
      file = activeFile;
      output = activeOutput;
      app = context;
      token = generation;
      if (file == null || CallTtsSettings.OUTPUT_LOCAL.equals(output)) {
        finishUtterance(utteranceId);
        return;
      }
    }
    AUDIO_WORKER.execute(
        () -> {
          try {
            short[] pcm = SoundboardStore.decodeToPcm16(app, Uri.fromFile(file));
            synchronized (LOCK) {
              if (token != generation || !utteranceId.equals(activeUtterance)) return;
              if (CallTtsSettings.OUTPUT_REMOTE.equals(output)
                  || CallTtsSettings.OUTPUT_BOTH.equals(output)) {
                boolean queued = NativeCallBridge.enqueueTts(app, pcm);
                Log.i(
                    "TenchaCall",
                    "TTS call audio queued: samples=" + pcm.length + ", accepted=" + queued);
              }
              if (CallTtsSettings.OUTPUT_BOTH.equals(output)) CallTtsLocalPlayer.enqueue(pcm);
            }
          } catch (Throwable error) {
            Log.e("TenchaCall", "TTS audio conversion failed", error);
          } finally {
            finishUtterance(utteranceId);
          }
        });
  }

  private static void finishUtterance(String utteranceId) {
    synchronized (LOCK) {
      if (utteranceId == null || !utteranceId.equals(activeUtterance)) return;
      deleteActiveFileLocked();
      activeUtterance = null;
      activeOutput = null;
      busy = false;
      drainLocked();
    }
  }

  private static void clearLocked(boolean releaseEngine) {
    generation++;
    queue.clear();
    seenIds.clear();
    observedPeople.clear();
    busy = false;
    activeUtterance = null;
    activeOutput = null;
    deleteActiveFileLocked();
    NativeCallBridge.clearTts();
    CallTtsLocalPlayer.clear();
    if (tts != null) {
      try {
        tts.stop();
      } catch (Throwable ignored) {
      }
      if (releaseEngine) {
        try {
          tts.shutdown();
        } catch (Throwable ignored) {
        }
        tts = null;
        ttsReady = false;
        initStarted = false;
      }
    }
    File directory = context == null ? null : new File(context.getCacheDir(), "tencha_tts");
    File[] files = directory == null ? null : directory.listFiles();
    if (files != null) for (File file : files) file.delete();
  }

  private static void deleteActiveFileLocked() {
    if (activeFile != null) activeFile.delete();
    activeFile = null;
  }
}
