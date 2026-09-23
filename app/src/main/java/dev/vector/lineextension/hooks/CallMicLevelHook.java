package dev.vector.lineextension.hooks;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
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
  private static volatile Method setVolumeMethod;
  private static volatile Object callSettingsMenuItem;
  private final Handler mainHandler = new Handler(Looper.getMainLooper());

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
    final Runnable update;
    final Map<String, ParticipantRow> participantRows = new LinkedHashMap<>();
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
      this.update = update;
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
    if (!(config.callMicMeter.enabled || config.participantVolume.enabled)) return;
    if (config.participantVolume.enabled) {
      setVolumeMethod =
          Reflect.findMethodExact(
              NATIVE_SESSION,
              lpparam.classLoader,
              "nAudioStreamSetVolume",
              long.class,
              int.class,
              float.class);
    }
    Class<?> audioControl = Reflect.findClass(AUDIO_CONTROL, lpparam.classLoader);
    Vector.hookAllCtors(
        audioControl,
        chain -> {
          Object result = chain.proceed();
          audioControlRef = new WeakReference<>(chain.getThisObject());
          return result;
        });
    Vector.module
        .hook(Reflect.findMethodExact(audioControl, "G"))
        .intercept(
            chain -> {
              Object result = chain.proceed();
              audioControlRef = new WeakReference<>(chain.getThisObject());
              return result;
            });

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
              hide((Activity) chain.getThisObject());
              return chain.proceed();
            });
    Vector.module
        .hook(Reflect.findMethodExact(callActivity, "onDestroy"))
        .intercept(
            chain -> {
              hide((Activity) chain.getThisObject());
              return chain.proceed();
            });
    installCallSettingsMenuItem(lpparam.classLoader);
  }

  private void show(Activity activity) {
    if (!(Main.options.callMicMeter.enabled || Main.options.participantVolume.enabled)
        || activity.isFinishing()
        || meters.containsKey(activity)) {
      return;
    }
    if (Main.options.participantVolume.enabled) NativeCallBridge.ensureLoaded(activity);
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

    TextView label = null;
    ProgressBar bar = null;
    if (Main.options.callMicMeter.enabled) {
      label = new TextView(activity);
      label.setText("マイク —");
      label.setTextColor(Color.WHITE);
      label.setTextSize(13);
      root.addView(label);

      bar = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
      bar.setMax(100);
      bar.setProgress(0);
      LinearLayout.LayoutParams barParams =
          new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(activity, 4));
      barParams.topMargin = dp(activity, 5);
      root.addView(bar, barParams);
    }

    TextView participantStatus = null;
    ScrollView participantScroll = null;
    LinearLayout participantList = null;
    TextView fallbackVolumeLabel = null;
    SeekBar fallbackVolumeBar = null;
    if (Main.options.participantVolume.enabled) {
      participantStatus = new TextView(activity);
      participantStatus.setText("参加者を取得中…");
      participantStatus.setTextColor(0xFFBDBDBD);
      participantStatus.setTextSize(13);
      if (label != null) {
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(-2, -2);
        labelParams.topMargin = dp(activity, 12);
        root.addView(participantStatus, labelParams);
      } else {
        root.addView(participantStatus);
      }

      participantList = new LinearLayout(activity);
      participantList.setOrientation(LinearLayout.VERTICAL);
      participantScroll = new ScrollView(activity);
      participantScroll.setFillViewport(false);
      participantScroll.setClipToPadding(false);
      participantScroll.addView(
          participantList,
          new ScrollView.LayoutParams(
              ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
      root.addView(
          participantScroll,
          new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(activity, 72)));

      fallbackVolumeLabel = new TextView(activity);
      fallbackVolumeLabel.setText("相手の音量 100%");
      fallbackVolumeLabel.setTextColor(Color.WHITE);
      fallbackVolumeLabel.setTextSize(13);
      fallbackVolumeLabel.setVisibility(View.GONE);
      root.addView(fallbackVolumeLabel);
      fallbackVolumeBar = new SeekBar(activity);
      fallbackVolumeBar.setMax(200);
      fallbackVolumeBar.setProgress(100);
      fallbackVolumeBar.setVisibility(View.GONE);
      root.addView(
          fallbackVolumeBar,
          new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, -2));
    }

    FrameLayout.LayoutParams params =
        new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
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
            if (!(Main.options.callMicMeter.enabled || Main.options.participantVolume.enabled)) {
              hide(activity);
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
              micLabel.setVisibility(Main.options.callMicMeter.enabled ? View.VISIBLE : View.GONE);
              micBar.setVisibility(Main.options.callMicMeter.enabled ? View.VISIBLE : View.GONE);
            }
            if (rosterStatus != null && rosterScroll != null && rosterList != null) {
              List<Participant> participants = readGroupParticipants(activity);
              boolean groupRosterAvailable = participants != null;
              if (groupRosterAvailable) {
                syncParticipantRows(activity, meter, participants);
                rosterStatus.setText(participants.isEmpty() ? "調整できる参加者はいません" : "参加者ごとの音量");
                rosterStatus.setVisibility(View.VISIBLE);
                rosterScroll.setVisibility(participants.isEmpty() ? View.GONE : View.VISIBLE);
                int height = Math.min(dp(activity, 336), dp(activity, 72) * participants.size());
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
                boolean eligible =
                    Main.options.participantVolume.enabled && isSingleRemoteCall(activity);
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
                Main.options.callMicMeter.enabled
                    || (rosterScroll != null && rosterScroll.getVisibility() == View.VISIBLE)
                    || (peerBar != null && peerBar.getVisibility() == View.VISIBLE);
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
            update);
    meters.put(activity, meter);
    if (fallbackVolumeBar != null && fallbackVolumeLabel != null) {
      fallbackVolumeBar.setOnSeekBarChangeListener(
          new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
              if (!fromUser) return;
              if (!Main.options.participantVolume.enabled || !isSingleRemoteCall(activity)) {
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

  private void hide(Activity activity) {
    Meter meter = meters.remove(activity);
    if (meter == null) return;
    mainHandler.removeCallbacks(meter.update);
    resetAllVolumes(meter);
    if (meter.root.getParent() == meter.parent) meter.parent.removeView(meter.root);
  }

  private void installCallSettingsMenuItem(ClassLoader classLoader) {
    try {
      Class<?> itemInterface = Reflect.findClass("xp7.e", classLoader);
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
                if ("d".equals(name)) return titleValue;
                if ("h".equals(name)) return enabledValue;
                if ("toString".equals(name)) return "TenchaCallSettingsMenuItem";
                if ("hashCode".equals(name)) return System.identityHashCode(proxy);
                if ("equals".equals(name)) return proxy == (args == null ? null : args[0]);
                return null;
              });

      Class<?> settingsProvider = Reflect.findClass("xp7.j", classLoader);
      Vector.module
          .hook(Reflect.findMethodExact(settingsProvider, "u"))
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
      Reflect.callMethod(liveData, "v", value);
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
      Object orderedRoster = getFieldAny(model, "F");
      if (orderedRoster == null) return null;
      Object entries = getFieldAny(orderedRoster, "f239928a", "a");
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
    Object control = audioControlRef.get();
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
    Object control = audioControlRef.get();
    if (control == null) return -1;
    try {
      Object level = Reflect.callMethod(control, "G");
      return level instanceof Number ? Math.max(0, ((Number) level).intValue()) : -1;
    } catch (Throwable ignored) {
      return -1;
    }
  }

  private static int dp(Activity activity, int value) {
    return (int) (value * activity.getResources().getDisplayMetrics().density + 0.5f);
  }
}
