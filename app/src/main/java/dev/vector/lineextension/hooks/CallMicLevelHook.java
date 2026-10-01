package dev.vector.lineextension.hooks;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import dev.vector.lineextension.LineVersion;
import dev.vector.lineextension.LoadParam;
import dev.vector.lineextension.Main;
import dev.vector.lineextension.Reflect;
import dev.vector.lineextension.Vector;
import dev.vector.lineextension.VectorConfig;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** Displays LINE's recording level and controls each remote participant's RX gain. */
public final class CallMicLevelHook implements BaseHook {
  private static final String CALL_ACTIVITY = "com.linecorp.voip2.service.VoIPServiceActivity";
  private static final String AUDIO_CONTROL = "com.linecorp.andromeda.core.d";
  private static final String NATIVE_SESSION = "com.linecorp.andromeda.jni.SessionJNIImpl";
  private static final String GROUP_FRAGMENT =
      "com.linecorp.voip2.service.groupcall.GroupCallFragment";
  private static final String FREE_FRAGMENT =
      "com.linecorp.voip2.service.freecall.FreeCallFragment";
  private static final String OA_FRAGMENT = "com.linecorp.voip2.service.oacall.OaCallFragment";
  private static final long UPDATE_INTERVAL_MS = 300L;

  private static volatile WeakReference<Object> audioControlRef = new WeakReference<>(null);
  private static final Map<Activity, Meter> meters = new WeakHashMap<>();
  private static final Handler UI_HANDLER = new Handler(Looper.getMainLooper());
  private static volatile Method setVolumeMethod;
  private static volatile Object callSettingsMenuItem;
  private static final ThreadLocal<Boolean> independentMuteCall = new ThreadLocal<>();
  private final Handler mainHandler = new Handler(Looper.getMainLooper());

  private static boolean isLine26150() {
    return "26.15.0".equals(LineVersion.getResolvedVersionName());
  }

  private static boolean micMeterEnabled() {
    return false; // Retired: only soundboard and TTS remain user-facing call controls.
  }

  private static boolean participantVolumeEnabled() {
    return false;
  }

  private static boolean anyCallControlEnabled() {
    return Main.options.soundboard.enabled
        || Main.options.callTts.enabled
        || micMeterEnabled()
        || participantVolumeEnabled();
  }

  private static final class Meter {
    final FrameLayout parent;
    final LinearLayout root;
    final ProgressBar bar;
    final TextView label;
    final ScrollView participantScroll;
    final LinearLayout participantList;
    final TextView participantStatus;
    final SeekBar fallbackVolumeBar;
    final TextView fallbackVolumeLabel;
    final LinearLayout soundboardList;
    final TextView soundboardStatus;
    final Runnable update;
    final Map<String, ParticipantRow> participantRows = new LinkedHashMap<>();
    final Map<String, TextView> soundboardButtons = new LinkedHashMap<>();
    final List<SoundboardPlayback> soundboardPlaybacks = new ArrayList<>();
    final Set<String> modifiedParticipants = new HashSet<>();
    long appliedStream;
    boolean fallbackVolumeApplied;
    boolean controlsOpen;

    Meter(
        FrameLayout parent,
        LinearLayout root,
        ProgressBar bar,
        TextView label,
        ScrollView participantScroll,
        LinearLayout participantList,
        TextView participantStatus,
        SeekBar fallbackVolumeBar,
        TextView fallbackVolumeLabel,
        LinearLayout soundboardList,
        TextView soundboardStatus,
        Runnable update) {
      this.parent = parent;
      this.root = root;
      this.bar = bar;
      this.label = label;
      this.participantScroll = participantScroll;
      this.participantList = participantList;
      this.participantStatus = participantStatus;
      this.fallbackVolumeBar = fallbackVolumeBar;
      this.fallbackVolumeLabel = fallbackVolumeLabel;
      this.soundboardList = soundboardList;
      this.soundboardStatus = soundboardStatus;
      this.update = update;
    }
  }

  private static final class SoundboardPlayback {
    final String clipId;

    SoundboardPlayback(String clipId) {
      this.clipId = clipId;
    }
  }

  private static final class Participant {
    final String id;
    final String name;

    Participant(String id, String name) {
      this.id = id;
      this.name = name;
    }
  }

  private static final class ParticipantRow {
    final Participant participant;
    final LinearLayout root;
    final TextView value;
    final SeekBar seekBar;

    ParticipantRow(Participant participant, LinearLayout root, TextView value, SeekBar seekBar) {
      this.participant = participant;
      this.root = root;
      this.value = value;
      this.seekBar = seekBar;
    }
  }

  @Override
  public void hook(VectorConfig config, LoadParam lpparam) {
    if (!anyCallControlEnabled()) return;
    if (participantVolumeEnabled()) {
      setVolumeMethod =
          Reflect.findMethodExact(
              NATIVE_SESSION,
              lpparam.classLoader,
              "nAudioStreamSetVolume",
              long.class,
              int.class,
              float.class);
    }
    boolean line26150 = "26.15.0".equals(LineVersion.getResolvedVersionName());
    if (!line26150) {
      Class<?> audioControl = Reflect.findClass(AUDIO_CONTROL, lpparam.classLoader);
      Vector.hookAllCtors(
          audioControl,
          chain -> {
            Object result = chain.proceed();
            audioControlRef = new WeakReference<>(chain.getThisObject());
            if (Main.options.soundboard.enabled || Main.options.callTts.enabled) {
              NativeCallBridge.prepareSoundboard(Vector.currentApplication());
            }
            return result;
          });
      installIndependentMuteHook(lpparam.classLoader, audioControl);
      try {
        Vector.module
            .hook(Reflect.findMethodExact(audioControl, "G"))
            .intercept(
                chain -> {
                  Object result = chain.proceed();
                  audioControlRef = new WeakReference<>(chain.getThisObject());
                  return result;
                });
      } catch (Throwable ignored) {
        Vector.log("TenchaCall: optional AudioControl refresh callback is unavailable");
      }
    } else {
      Vector.log("Tencha: LINE 26.15 audio-engine hooks deferred during call isolation");
    }

    Class<?> callActivity = Reflect.findClass(CALL_ACTIVITY, lpparam.classLoader);
    Vector.module
        .hook(Reflect.findMethodExact(callActivity, "onResume"))
        .intercept(
            chain -> {
              Object result = chain.proceed();
              Activity activity = (Activity) chain.getThisObject();
              activity.runOnUiThread(() -> show(activity));
              return result;
            });
    Vector.module
        .hook(Reflect.findMethodExact(callActivity, "onPause"))
        .intercept(
            chain -> {
              hide((Activity) chain.getThisObject(), false);
              return chain.proceed();
            });
    Vector.module
        .hook(Reflect.findMethodExact(callActivity, "onDestroy"))
        .intercept(
            chain -> {
              Activity activity = (Activity) chain.getThisObject();
              hide(activity, false);
              if (Main.options.callTts.enabled) CallTtsManager.onCallUiDestroyed(activity);
              return chain.proceed();
            });
    installCallSettingsMenuItem(lpparam.classLoader);
  }

  private void show(Activity activity) {
    if (!anyCallControlEnabled() || activity.isFinishing() || meters.containsKey(activity)) {
      return;
    }
    if (Main.options.callTts.enabled) CallTtsManager.startCall(activity);
    captureOwnCallName(activity);
    activity.getWindow().getDecorView().postDelayed(() -> captureOwnCallName(activity), 600L);
    activity.getWindow().getDecorView().postDelayed(() -> captureOwnCallName(activity), 1600L);
    View content = activity.findViewById(android.R.id.content);
    if (!(content instanceof FrameLayout)) return;
    FrameLayout parent = (FrameLayout) content;
    LinearLayout root = new LinearLayout(activity);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setPadding(dp(activity, 24), dp(activity, 18), dp(activity, 24), dp(activity, 28));
    GradientDrawable background = new GradientDrawable();
    background.setColor(0xFF202020);
    float topRadius = dp(activity, 28);
    background.setCornerRadii(
        new float[] {topRadius, topRadius, topRadius, topRadius, 0f, 0f, 0f, 0f});
    root.setBackground(background);
    root.setElevation(dp(activity, 5));

    LinearLayout header = new LinearLayout(activity);
    header.setOrientation(LinearLayout.HORIZONTAL);
    header.setGravity(Gravity.CENTER_VERTICAL);
    TextView title = new TextView(activity);
    title.setText("Tencha 通話調整");
    title.setTextColor(Color.WHITE);
    title.setTextSize(18);
    title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
    header.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
    TextView close = new TextView(activity);
    close.setText("×");
    close.setTextColor(0xFFFFFFFF);
    close.setTextSize(26);
    close.setGravity(Gravity.CENTER);
    close.setContentDescription("閉じる");
    close.setPadding(dp(activity, 12), 0, 0, 0);
    header.addView(close, new LinearLayout.LayoutParams(-2, -2));
    root.addView(header);

    ScrollView controlScroll = new ScrollView(activity);
    controlScroll.setFillViewport(false);
    controlScroll.setClipToPadding(false);
    controlScroll.setVerticalScrollBarEnabled(true);
    LinearLayout controls = new LinearLayout(activity);
    controls.setOrientation(LinearLayout.VERTICAL);
    controls.setPadding(0, dp(activity, 8), 0, dp(activity, 4));
    controlScroll.addView(
        controls,
        new ScrollView.LayoutParams(
            ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
    root.addView(controlScroll, new LinearLayout.LayoutParams(-1, 0, 1f));

    TextView label = null;
    ProgressBar bar = null;
    if (micMeterEnabled()) {
      label = new TextView(activity);
      label.setText("マイク —");
      label.setTextColor(Color.WHITE);
      label.setTextSize(13);
      controls.addView(label);

      bar = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
      bar.setMax(100);
      bar.setProgress(0);
      LinearLayout.LayoutParams barParams =
          new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(activity, 4));
      barParams.topMargin = dp(activity, 5);
      controls.addView(bar, barParams);
    }

    TextView participantStatus = null;
    ScrollView participantScroll = null;
    LinearLayout participantList = null;
    TextView fallbackVolumeLabel = null;
    SeekBar fallbackVolumeBar = null;
    if (participantVolumeEnabled()) {
      participantStatus = new TextView(activity);
      participantStatus.setText("参加者を取得中…");
      participantStatus.setTextColor(0xFFBDBDBD);
      participantStatus.setTextSize(13);
      if (label != null) {
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(-2, -2);
        labelParams.topMargin = dp(activity, 12);
        controls.addView(participantStatus, labelParams);
      } else {
        controls.addView(participantStatus);
      }

      participantList = new LinearLayout(activity);
      participantList.setOrientation(LinearLayout.VERTICAL);
      participantScroll = new ScrollView(activity);
      participantScroll.setFillViewport(false);
      participantScroll.setClipToPadding(false);
      participantScroll.setNestedScrollingEnabled(false);
      participantScroll.setVerticalScrollBarEnabled(false);
      participantScroll.addView(
          participantList,
          new ScrollView.LayoutParams(
              ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
      controls.addView(
          participantScroll,
          new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(activity, 72)));

      fallbackVolumeLabel = new TextView(activity);
      fallbackVolumeLabel.setText("相手の音量 100%");
      fallbackVolumeLabel.setTextColor(Color.WHITE);
      fallbackVolumeLabel.setTextSize(13);
      fallbackVolumeLabel.setVisibility(View.GONE);
      controls.addView(fallbackVolumeLabel);
      fallbackVolumeBar = new SeekBar(activity);
      fallbackVolumeBar.setMax(200);
      fallbackVolumeBar.setProgress(100);
      fallbackVolumeBar.setVisibility(View.GONE);
      controls.addView(
          fallbackVolumeBar,
          new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, -2));
    }

    TextView soundboardStatus = null;
    LinearLayout soundboardList = null;
    if (Main.options.soundboard.enabled) {
      soundboardStatus = new TextView(activity);
      soundboardStatus.setText("サウンドボード");
      soundboardStatus.setTextColor(Color.WHITE);
      soundboardStatus.setTextSize(14);
      LinearLayout.LayoutParams soundTitleParams = new LinearLayout.LayoutParams(-1, -2);
      soundTitleParams.topMargin = dp(activity, 12);
      controls.addView(soundboardStatus, soundTitleParams);

      soundboardList = new LinearLayout(activity);
      soundboardList.setOrientation(LinearLayout.VERTICAL);
      controls.addView(soundboardList, new LinearLayout.LayoutParams(-1, -2));
    }

    if (Main.options.callTts.enabled) {
      TextView ttsTitle = new TextView(activity);
      ttsTitle.setText("TTS");
      ttsTitle.setTextColor(Color.WHITE);
      ttsTitle.setTextSize(14);
      LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2);
      titleParams.topMargin = dp(activity, 14);
      controls.addView(ttsTitle, titleParams);

      LinearLayout ttsActions = new LinearLayout(activity);
      ttsActions.setOrientation(LinearLayout.HORIZONTAL);
      TextView ttsToggle = soundboardButton(activity, "TTS読み上げ  OFF");
      TextView ttsSettings = soundboardButton(activity, "設定");
      ttsActions.addView(ttsToggle, new LinearLayout.LayoutParams(0, -2, 1f));
      LinearLayout.LayoutParams settingsParams =
          new LinearLayout.LayoutParams(dp(activity, 88), -2);
      settingsParams.leftMargin = dp(activity, 8);
      ttsActions.addView(ttsSettings, settingsParams);
      controls.addView(ttsActions, new LinearLayout.LayoutParams(-1, -2));
      Runnable updateTtsButton =
          () -> {
            boolean enabled = CallTtsManager.isSessionEnabled();
            ttsToggle.setText("TTS読み上げ  " + (enabled ? "ON" : "OFF"));
            ttsToggle.setTextColor(enabled ? 0xFF9EE493 : Color.WHITE);
          };
      ttsToggle.setOnClickListener(
          view -> {
            CallTtsManager.setSessionEnabled(activity, !CallTtsManager.isSessionEnabled());
            updateTtsButton.run();
          });
      ttsSettings.setOnClickListener(
          view -> CallTtsSettings.show(activity, currentTtsPeople(activity)));
      updateTtsButton.run();
    }

    FrameLayout.LayoutParams params =
        new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            Math.round(activity.getResources().getDisplayMetrics().heightPixels * 0.82f));
    params.gravity = Gravity.BOTTOM;
    parent.addView(root, params);
    root.setVisibility(View.GONE);
    close.setOnClickListener(
        view -> {
          Meter meter = meters.get(activity);
          if (meter == null) {
            root.setVisibility(View.GONE);
            return;
          }
          root.animate()
              .translationY(root.getHeight())
              .setDuration(180L)
              .withEndAction(
                  () -> {
                    meter.controlsOpen = false;
                    root.setVisibility(View.GONE);
                    root.setTranslationY(0f);
                  })
              .start();
        });

    final TextView micLabel = label;
    final ProgressBar micBar = bar;
    final TextView rosterStatus = participantStatus;
    final ScrollView rosterScroll = participantScroll;
    final LinearLayout rosterList = participantList;
    final TextView peerLabel = fallbackVolumeLabel;
    final SeekBar peerBar = fallbackVolumeBar;
    Runnable update =
        new Runnable() {
          @Override
          public void run() {
            if (!anyCallControlEnabled()) {
              hide(activity, true);
              return;
            }
            Meter meter = meters.get(activity);
            if (meter == null) return;
            if (micLabel != null && micBar != null) {
              int level = readRecordingLevel();
              if (level < 0) {
                micLabel.setText("マイク —");
                micBar.setProgress(0);
              } else {
                micLabel.setText("マイクレベル " + level);
                micBar.setProgress(Math.min(100, level));
              }
              micLabel.setVisibility(micMeterEnabled() ? View.VISIBLE : View.GONE);
              micBar.setVisibility(micMeterEnabled() ? View.VISIBLE : View.GONE);
            }
            if (rosterStatus != null && rosterScroll != null && rosterList != null) {
              List<Participant> participants = readGroupParticipants(activity);
              boolean groupRosterAvailable = participants != null;
              if (groupRosterAvailable) {
                syncParticipantRows(activity, meter, participants);
                rosterStatus.setText(participants.isEmpty() ? "調整できる参加者はいません" : "参加者ごとの音量");
                rosterStatus.setVisibility(View.VISIBLE);
                rosterScroll.setVisibility(participants.isEmpty() ? View.GONE : View.VISIBLE);
                int height = dp(activity, 72) * participants.size();
                LinearLayout.LayoutParams scrollParams =
                    (LinearLayout.LayoutParams) rosterScroll.getLayoutParams();
                if (scrollParams.height != height && height > 0) {
                  scrollParams.height = height;
                  rosterScroll.setLayoutParams(scrollParams);
                }
                if (peerLabel != null) peerLabel.setVisibility(View.GONE);
                if (peerBar != null) peerBar.setVisibility(View.GONE);
                resetFallbackVolume(meter);
              } else {
                clearParticipantRows(meter, true);
                boolean eligible = participantVolumeEnabled() && isSingleRemoteCall(activity);
                rosterStatus.setVisibility(eligible ? View.GONE : View.VISIBLE);
                rosterStatus.setText("参加者情報を取得できません");
                rosterScroll.setVisibility(View.GONE);
                if (peerLabel != null) peerLabel.setVisibility(eligible ? View.VISIBLE : View.GONE);
                if (peerBar != null) {
                  peerBar.setVisibility(eligible ? View.VISIBLE : View.GONE);
                  if (!eligible) peerBar.setProgress(100);
                }
                if (!eligible) resetFallbackVolume(meter);
              }
            }
            boolean hasVisibleControl =
                micMeterEnabled()
                    || (rosterScroll != null && rosterScroll.getVisibility() == View.VISIBLE)
                    || (peerBar != null && peerBar.getVisibility() == View.VISIBLE)
                    || Main.options.soundboard.enabled
                    || Main.options.callTts.enabled;
            root.setVisibility(meter.controlsOpen && hasVisibleControl ? View.VISIBLE : View.GONE);
            mainHandler.postDelayed(this, UPDATE_INTERVAL_MS);
          }
        };
    Meter meter =
        new Meter(
            parent,
            root,
            bar,
            label,
            participantScroll,
            participantList,
            participantStatus,
            fallbackVolumeBar,
            fallbackVolumeLabel,
            soundboardList,
            soundboardStatus,
            update);
    meters.put(activity, meter);
    if (soundboardList != null && soundboardStatus != null) {
      populateSoundboard(activity, meter);
    }
    if (fallbackVolumeBar != null && fallbackVolumeLabel != null) {
      fallbackVolumeBar.setOnSeekBarChangeListener(
          new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
              if (!fromUser) return;
              if (!participantVolumeEnabled() || !isSingleRemoteCall(activity)) {
                seekBar.setProgress(100);
                return;
              }
              if (setFallbackRxVolume(progress, meter)) {
                peerLabel.setText("相手の音量 " + progress + "%");
              } else {
                peerLabel.setText("相手の音量を変更できません");
                seekBar.setProgress(100);
              }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
          });
    }
    mainHandler.post(update);
  }

  private static void captureOwnCallName(Activity activity) {
    if (activity == null || activity.isFinishing()) return;
    CallTtsManager.setSelfDisplayName(dev.vector.lineextension.utils.LineDBUtils.getMyName());
  }

  private void hide(Activity activity, boolean callEnded) {
    Meter meter = meters.remove(activity);
    if (callEnded) {
      CallTtsManager.endCall();
      if (!isLine26150()) NativeCallBridge.setPhysicalMicMuted(activity, false);
    }
    if (meter == null) return;
    mainHandler.removeCallbacks(meter.update);
    stopAllSoundboardPlaybacks(meter);
    resetAllVolumes(meter);
    if (meter.root.getParent() == meter.parent) meter.parent.removeView(meter.root);
  }

  private static void populateSoundboard(Activity activity, Meter meter) {
    meter.soundboardList.removeAllViews();
    meter.soundboardButtons.clear();
    List<SoundboardStore.Clip> clips = SoundboardStore.load(activity);
    if (clips.isEmpty()) {
      meter.soundboardStatus.setText("サウンドボード — 音声未登録");
      TextView help = new TextView(activity);
      help.setText("LINE設定 → Tencha → チャット → サウンドボード音声から追加");
      help.setTextColor(0xFFBDBDBD);
      help.setTextSize(12);
      help.setPadding(0, dp(activity, 6), 0, 0);
      meter.soundboardList.addView(help);
      return;
    }
    meter.soundboardStatus.setText("サウンドボード");
    for (SoundboardStore.Clip clip : clips) {
      TextView button = soundboardButton(activity, "▶  " + clip.name);
      button.setOnClickListener(view -> playSoundboard(activity, meter, clip));
      meter.soundboardButtons.put(clip.id, button);
      meter.soundboardList.addView(button);
    }
  }

  private static TextView soundboardButton(Activity activity, String text) {
    TextView button = new TextView(activity);
    button.setText(text);
    button.setTextColor(Color.WHITE);
    button.setTextSize(14);
    button.setGravity(Gravity.CENTER_VERTICAL);
    button.setSingleLine(true);
    button.setEllipsize(android.text.TextUtils.TruncateAt.END);
    button.setPadding(dp(activity, 14), 0, dp(activity, 14), 0);
    GradientDrawable background = new GradientDrawable();
    background.setColor(0xFF333333);
    background.setCornerRadius(dp(activity, 12));
    button.setBackground(background);
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(activity, 46));
    params.topMargin = dp(activity, 7);
    button.setLayoutParams(params);
    return button;
  }

  private static void playSoundboard(Activity activity, Meter meter, SoundboardStore.Clip clip) {
    try {
      short[] pcm = SoundboardStore.readPcm16(clip);
      if (!NativeCallBridge.enqueueSoundboard(activity, pcm)) {
        Toast.makeText(activity, "音声を送信できません", Toast.LENGTH_SHORT).show();
        return;
      }
      SoundboardLocalPlayer.enqueue(pcm);
      SoundboardPlayback playback = new SoundboardPlayback(clip.id);
      meter.soundboardPlaybacks.add(playback);
      updateSoundboardButtons(meter);
      UI_HANDLER.postDelayed(
          () -> stopSoundboardPlayback(meter, playback), SoundboardStore.durationMs(clip) + 150L);
    } catch (Throwable error) {
      Vector.log("Tencha: soundboard playback failed", error);
      Toast.makeText(activity, "音声を送信できません", Toast.LENGTH_SHORT).show();
    }
  }

  private static void stopSoundboardPlayback(Meter meter, SoundboardPlayback playback) {
    if (!meter.soundboardPlaybacks.remove(playback)) return;
    updateSoundboardButtons(meter);
  }

  private static void stopAllSoundboardPlaybacks(Meter meter) {
    meter.soundboardPlaybacks.clear();
    NativeCallBridge.clearSoundboard();
    SoundboardLocalPlayer.clear();
    updateSoundboardButtons(meter);
  }

  private static void updateSoundboardButtons(Meter meter) {
    for (Map.Entry<String, TextView> entry : meter.soundboardButtons.entrySet()) {
      int activeCount = 0;
      for (SoundboardPlayback playback : meter.soundboardPlaybacks) {
        if (entry.getKey().equals(playback.clipId)) activeCount++;
      }
      boolean active = activeCount > 0;
      TextView button = entry.getValue();
      Object original = button.getTag();
      String name =
          original instanceof String
              ? (String) original
              : button.getText().toString().replace("▶  ", "");
      if (!(original instanceof String)) button.setTag(name);
      button.setText(active ? "▶  送信中 ×" + activeCount + "・" + name : "▶  " + name);
      GradientDrawable background = new GradientDrawable();
      background.setColor(active ? 0xFF06C755 : 0xFF333333);
      background.setCornerRadius(dp(button.getContext(), 12));
      if (active) background.setStroke(dp(button.getContext(), 2), 0xFFFFFFFF);
      button.setBackground(background);
    }
  }

  private static void installIndependentMuteHook(ClassLoader loader, Class<?> audioControl) {
    if (!(Main.options.soundboard.enabled || Main.options.callTts.enabled)) return;
    try {
      Vector.module
          .hook(Reflect.findMethodExact(audioControl, "h1", boolean.class, boolean.class))
          .intercept(
              chain -> {
                boolean muted = (Boolean) chain.getArg(0);
                Context context = Vector.currentApplication();
                boolean mixerReady = NativeCallBridge.setPhysicalMicMuted(context, muted);
                independentMuteCall.set(muted && mixerReady);
                try {
                  return chain.proceed();
                } finally {
                  independentMuteCall.remove();
                }
              });
      Class<?> session = Reflect.findClass(NATIVE_SESSION, loader);
      Vector.module
          .hook(Reflect.findMethodExact(session, "o", int.class, long.class, boolean.class))
          .intercept(
              chain -> {
                boolean muted = (Boolean) chain.getArg(2);
                if (muted && Boolean.TRUE.equals(independentMuteCall.get())) {
                  // Keep LINE's TX stream alive. The recorder hook replaces only physical mic PCM
                  // with silence before independently adding TTS and soundboard PCM.
                  return null;
                }
                return chain.proceed();
              });
    } catch (Throwable error) {
      Vector.log("Tencha: independent microphone mute hook unavailable", error);
    }
  }

  private static List<CallTtsSettings.Person> currentTtsPeople(Activity activity) {
    LinkedHashMap<String, CallTtsSettings.Person> people = new LinkedHashMap<>();
    String self = dev.vector.lineextension.utils.LineDBUtils.getMyMid();
    if (self != null && !self.isEmpty()) {
      String selfName = dev.vector.lineextension.utils.LineDBUtils.resolveMemberName(self);
      people.put(self, new CallTtsSettings.Person(self, selfName == null ? "自分" : selfName));
    }
    List<Participant> participants = readGroupParticipants(activity);
    if (participants != null) {
      for (Participant participant : participants)
        people.put(participant.id, new CallTtsSettings.Person(participant.id, participant.name));
    }
    for (CallTtsSettings.Person person : CallTtsManager.observedPeople())
      people.put(person.mid, person);
    return new ArrayList<>(people.values());
  }

  private void installCallSettingsMenuItem(ClassLoader classLoader) {
    try {
      boolean line26150 = "26.15.0".equals(LineVersion.getResolvedVersionName());
      String itemInterfaceName = line26150 ? "vw7.e" : "xp7.e";
      String settingsProviderName = line26150 ? "vw7.j" : "xp7.j";
      String settingsItemsMethod = line26150 ? "v" : "u";
      Class<?> itemInterface = Reflect.findClass(itemInterfaceName, classLoader);
      Class<?> mutableLiveData = Reflect.findClass("androidx.lifecycle.h1", classLoader);
      // Resolve every value before exposing the proxy to LINE. If LINE changes this API, menu
      // injection fails closed during startup instead of crashing RecyclerView while it binds.
      Object enabledValue = newConstantLiveData(mutableLiveData, Boolean.TRUE);
      Object iconValue =
          newConstantLiveData(mutableLiveData, Integer.valueOf(android.R.drawable.ic_menu_manage));
      Object titleValue = newConstantLiveData(mutableLiveData, "Tencha 通話調整");
      callSettingsMenuItem =
          Proxy.newProxyInstance(
              classLoader,
              new Class<?>[] {itemInterface},
              (proxy, method, args) -> {
                String name = method.getName();
                if ("a".equals(name)) {
                  showCallControls();
                  return null;
                }
                if ("b".equals(name)) return iconValue;
                if ("c".equals(name)) return null;
                if ((line26150 ? "g" : "d").equals(name)) return titleValue;
                if ((line26150 ? "j" : "h").equals(name)) return enabledValue;
                if ("toString".equals(name)) return "TenchaCallSettingsMenuItem";
                if ("hashCode".equals(name)) return System.identityHashCode(proxy);
                if ("equals".equals(name)) return proxy == (args == null ? null : args[0]);
                return null;
              });

      Class<?> settingsProvider = Reflect.findClass(settingsProviderName, classLoader);
      Vector.module
          .hook(Reflect.findMethodExact(settingsProvider, settingsItemsMethod))
          .intercept(
              chain -> {
                Object result = chain.proceed();
                if (!(result instanceof List) || callSettingsMenuItem == null) return result;
                List<Object> items = new ArrayList<>((List<?>) result);
                items.add(callSettingsMenuItem);
                return items;
              });
    } catch (Throwable error) {
      Vector.log("Tencha: call settings menu injection unavailable", error);
    }
  }

  private static Object newConstantLiveData(Class<?> liveDataClass, Object value) {
    try {
      return Reflect.newInstance(liveDataClass, value);
    } catch (Throwable noValueConstructor) {
      Object liveData = Reflect.newInstance(liveDataClass);
      try {
        Reflect.callMethod(liveData, "v", value);
      } catch (Throwable oldSetterUnavailable) {
        // AndroidX bundled in LINE 26.15 names MutableLiveData#setValue as x().
        Reflect.callMethod(liveData, "x", value);
      }
      return liveData;
    }
  }

  private static void showCallControls() {
    for (Map.Entry<Activity, Meter> entry : meters.entrySet()) {
      Activity activity = entry.getKey();
      Meter meter = entry.getValue();
      if (activity == null || meter == null || activity.isFinishing()) continue;
      int closeId =
          activity.getResources().getIdentifier("action_x", "id", activity.getPackageName());
      View closeMenu = closeId == 0 ? null : activity.findViewById(closeId);
      if (closeMenu != null && closeMenu.isShown()) closeMenu.performClick();
      meter.controlsOpen = true;
      meter.root.setVisibility(View.VISIBLE);
      meter.root.bringToFront();
      meter.root.post(
          () -> {
            meter.root.animate().cancel();
            meter.root.setTranslationY(meter.root.getHeight());
            meter.root.animate().translationY(0f).setDuration(200L).start();
          });
      return;
    }
  }

  private static boolean isSingleRemoteCall(Activity activity) {
    try {
      Object fragment = findPrimaryCallFragment(activity);
      if (fragment == null) return false;
      return isClassOrSuperclass(fragment, FREE_FRAGMENT)
          || isClassOrSuperclass(fragment, OA_FRAGMENT);
    } catch (Throwable ignored) {
      return false;
    }
  }

  private static boolean isClassOrSuperclass(Object instance, String name) {
    for (Class<?> type = instance.getClass(); type != null; type = type.getSuperclass()) {
      if (name.equals(type.getName())) return true;
    }
    return false;
  }

  private static List<Participant> readGroupParticipants(Activity activity) {
    try {
      Object fragment = findPrimaryCallFragment(activity);
      if (fragment != null && isClassOrSuperclass(fragment, GROUP_FRAGMENT)) {
        return readGroupParticipantsFromFragment(fragment);
      }
    } catch (Throwable ignored) {
      // The group call screen may still be attaching. The next periodic update retries.
    }
    return null;
  }

  private static Object findPrimaryCallFragment(Activity activity) {
    Object manager = Reflect.callMethod(activity, "getSupportFragmentManager");
    int mainContentId =
        activity.getResources().getIdentifier("main_content", "id", activity.getPackageName());
    if (mainContentId == 0) return null;
    // LINE 26.14.0 bundles a minified AndroidX build. FragmentManager#getFragments no longer
    // exists by that name; LINE itself resolves the active call Fragment through r0.I(int).
    return Reflect.callMethod(manager, "I", mainContentId);
  }

  private static List<Participant> readGroupParticipantsFromFragment(Object fragment) {
    try {
      // Prefer actual dex names. JADX's short aliases (d/l/o/a) are display names only and are
      // not valid reflection targets in the installed APK.
      Object session = getFieldAny(fragment, "f98442d", "d");
      if (session == null) return null;
      Object model = getFieldAny(session, "f151256l", "l");
      if (model == null) return null;
      String selfId = String.valueOf(getFieldAny(model, "f239916o", "o"));
      // f239925x is the StateFlow consumed by LINE's in-call voice overlay. Its current value is
      // the participants actually present in this call. Model.F is the wider group roster and can
      // contain users who never joined or already left.
      Object activeParticipants = getFieldAny(model, "f239925x", "x");
      if (activeParticipants == null) return null;
      Object entries = Reflect.callMethod(activeParticipants, "getValue");
      if (!(entries instanceof List)) return null;
      LinkedHashMap<String, Participant> participants = new LinkedHashMap<>();
      for (Object entry : (List<?>) entries) {
        if (entry == null) continue;
        Object idValue = Reflect.callMethod(entry, "getId");
        Object nameValue = Reflect.callMethod(entry, "getName");
        if (!(idValue instanceof String)) continue;
        String id = (String) idValue;
        if (id.isEmpty() || id.equals(selfId)) continue;
        String name = nameValue instanceof String ? (String) nameValue : "";
        if (name.isEmpty()) name = "名前なし";
        participants.put(id, new Participant(id, name));
      }
      return new ArrayList<>(participants.values());
    } catch (Throwable error) {
      Vector.log("Tencha: group participant roster unavailable", error);
      return null;
    }
  }

  private static Object getFieldAny(Object instance, String... names) {
    Throwable last = null;
    for (String name : names) {
      try {
        return Reflect.getObjectField(instance, name);
      } catch (Throwable error) {
        last = error;
      }
    }
    throw new RuntimeException(last);
  }

  private static void syncParticipantRows(
      Activity activity, Meter meter, List<Participant> participants) {
    Set<String> activeIds = new HashSet<>();
    boolean rebuildOrder = meter.participantRows.size() != participants.size();
    for (Participant participant : participants) {
      activeIds.add(participant.id);
      ParticipantRow old = meter.participantRows.get(participant.id);
      if (old == null || !old.participant.name.equals(participant.name)) {
        if (old != null) resetParticipantVolume(meter, participant.id);
        meter.participantRows.put(
            participant.id,
            createParticipantRow(activity, meter, participant, participants.size() == 1));
        rebuildOrder = true;
      }
    }
    for (String id : new ArrayList<>(meter.participantRows.keySet())) {
      if (!activeIds.contains(id)) {
        resetParticipantVolume(meter, id);
        meter.participantRows.remove(id);
        rebuildOrder = true;
      }
    }
    if (!rebuildOrder) return;
    meter.participantList.removeAllViews();
    LinkedHashMap<String, ParticipantRow> ordered = new LinkedHashMap<>();
    for (Participant participant : participants) {
      ParticipantRow row = meter.participantRows.get(participant.id);
      if (row != null) {
        ordered.put(participant.id, row);
        meter.participantList.addView(row.root);
      }
    }
    meter.participantRows.clear();
    meter.participantRows.putAll(ordered);
  }

  private static ParticipantRow createParticipantRow(
      Activity activity, Meter meter, Participant participant, boolean allowGlobalFallback) {
    LinearLayout row = new LinearLayout(activity);
    row.setOrientation(LinearLayout.VERTICAL);
    row.setPadding(0, dp(activity, 5), 0, dp(activity, 4));

    LinearLayout header = new LinearLayout(activity);
    header.setGravity(Gravity.CENTER_VERTICAL);
    TextView name = new TextView(activity);
    name.setText(participant.name);
    name.setTextColor(Color.WHITE);
    name.setTextSize(14);
    name.setSingleLine(true);
    header.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));
    TextView value = new TextView(activity);
    value.setText("100%");
    value.setTextColor(0xFFBDBDBD);
    value.setTextSize(13);
    header.addView(value);
    row.addView(header);

    SeekBar seekBar = new SeekBar(activity);
    seekBar.setMax(200);
    seekBar.setProgress(100);
    row.addView(
        seekBar,
        new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(activity, 40)));
    ParticipantRow participantRow = new ParticipantRow(participant, row, value, seekBar);
    seekBar.setOnSeekBarChangeListener(
        new SeekBar.OnSeekBarChangeListener() {
          @Override
          public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
            if (!fromUser) return;
            long stream = audioStreamPointer();
            // In a two-person group call there is only one remote RX source, so LINE's proven
            // stream-wide RX control is exactly equivalent and is a safe fallback. Three or more
            // participants continue to require the per-talker native path.
            boolean changed =
                allowGlobalFallback
                    ? setFallbackRxVolume(progress, meter)
                    : stream != 0L
                        && NativeCallBridge.setParticipantVolume(stream, participant.id, progress);
            if (!changed) {
              value.setText("変更できません");
              bar.setProgress(100);
              return;
            }
            meter.appliedStream = stream;
            if (!meter.fallbackVolumeApplied) {
              if (progress == 100) {
                meter.modifiedParticipants.remove(participant.id);
              } else {
                meter.modifiedParticipants.add(participant.id);
              }
            }
            value.setText(progress + "%");
          }

          @Override
          public void onStartTrackingTouch(SeekBar seekBar) {}

          @Override
          public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    return participantRow;
  }

  private static void resetParticipantVolume(Meter meter, String participantId) {
    if (!meter.modifiedParticipants.remove(participantId)) return;
    long stream = audioStreamPointer();
    if (stream != 0L && stream == meter.appliedStream) {
      NativeCallBridge.setParticipantVolume(stream, participantId, 100);
    }
  }

  private static void clearParticipantRows(Meter meter, boolean reset) {
    if (reset) {
      for (String id : new ArrayList<>(meter.modifiedParticipants)) {
        resetParticipantVolume(meter, id);
      }
    } else {
      meter.modifiedParticipants.clear();
    }
    meter.participantRows.clear();
    if (meter.participantList != null) meter.participantList.removeAllViews();
  }

  private static long audioStreamPointer() {
    Object control = resolveAudioControl();
    if (control == null) return 0L;
    try {
      Object session = Reflect.callMethod(control, "b1");
      Object stream = getFieldAny(session, "f59351b", "b");
      Object nativeStream = getFieldAny(stream, "f59370a", "a");
      return getLongFieldAny(nativeStream, "f400958a", "a");
    } catch (Throwable error) {
      Log.e("TenchaCall", "Audio stream pointer lookup failed", error);
      Vector.log("Tencha: audio stream pointer unavailable", error);
      return 0L;
    }
  }

  private static long getLongFieldAny(Object instance, String... names) {
    Throwable last = null;
    for (String name : names) {
      try {
        return Reflect.getLongField(instance, name);
      } catch (Throwable error) {
        last = error;
      }
    }
    throw new RuntimeException(last);
  }

  private static boolean setFallbackRxVolume(int percent, Meter meter) {
    long stream = audioStreamPointer();
    if (stream == 0L || setVolumeMethod == null) return false;
    try {
      setVolumeMethod.invoke(null, stream, 2, percent / 100f);
      meter.appliedStream = stream;
      meter.fallbackVolumeApplied = percent != 100;
      return true;
    } catch (Throwable ignored) {
      return false;
    }
  }

  private static void resetFallbackVolume(Meter meter) {
    if (!meter.fallbackVolumeApplied) return;
    try {
      long stream = audioStreamPointer();
      if (stream != 0L && stream == meter.appliedStream && setVolumeMethod != null) {
        setVolumeMethod.invoke(null, stream, 2, 1f);
      }
    } catch (Throwable ignored) {
      // Session may already have been destroyed; never call a stale native pointer.
    } finally {
      meter.fallbackVolumeApplied = false;
      if (meter.fallbackVolumeLabel != null) meter.fallbackVolumeLabel.setText("相手の音量 100%");
      if (meter.fallbackVolumeBar != null) meter.fallbackVolumeBar.setProgress(100);
    }
  }

  private static void resetAllVolumes(Meter meter) {
    resetFallbackVolume(meter);
    clearParticipantRows(meter, true);
    meter.appliedStream = 0L;
  }

  private static int readRecordingLevel() {
    Object control = resolveAudioControl();
    if (control == null) return -1;
    try {
      if ("26.15.0".equals(LineVersion.getResolvedVersionName())) {
        Object pcmLevel = Reflect.callMethod(control, "u");
        return pcmLevel == null ? -1 : Math.max(0, Reflect.getIntField(pcmLevel, "b"));
      }
      Object level = Reflect.callMethod(control, "G");
      return level instanceof Number ? Math.max(0, ((Number) level).intValue()) : -1;
    } catch (Throwable ignored) {
      return -1;
    }
  }

  private static Object resolveAudioControl() {
    Object cached = audioControlRef.get();
    if (cached != null) return cached;
    if (!"26.15.0".equals(LineVersion.getResolvedVersionName())) return null;
    try {
      ClassLoader loader = Vector.currentApplication().getClassLoader();
      Class<?> universeClass =
          Reflect.findClass("com.linecorp.andromeda.core.UniverseCore", loader);
      Object universe = Reflect.getStaticObjectField(universeClass, "e");
      Object holder = getFieldAny(universe, "a");
      Object values = getFieldAny(holder, "a");
      if (!(values instanceof SparseArray)) return null;
      SparseArray<?> calls = (SparseArray<?>) values;
      Object connecting = null;
      for (int index = 0; index < calls.size(); index++) {
        Object control = calls.valueAt(index);
        if (control == null) continue;
        String state = String.valueOf(Reflect.callMethod(control, "getState"));
        if ("CONNECTED".equals(state)) {
          audioControlRef = new WeakReference<>(control);
          return control;
        }
        if ("CONNECTING".equals(state)) connecting = control;
      }
      if (connecting != null) audioControlRef = new WeakReference<>(connecting);
      return connecting;
    } catch (Throwable error) {
      Log.e("TenchaCall", "Safe AudioControl lookup failed", error);
      return null;
    }
  }

  private static int dp(android.content.Context context, int value) {
    return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
  }
}
