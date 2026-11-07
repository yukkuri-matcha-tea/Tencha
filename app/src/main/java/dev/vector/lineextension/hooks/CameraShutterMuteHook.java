package dev.vector.lineextension.hooks;

import android.content.Context;
import android.hardware.Camera;
import android.media.SoundPool;
import dev.vector.lineextension.LoadParam;
import dev.vector.lineextension.Reflect;
import dev.vector.lineextension.Vector;
import dev.vector.lineextension.VectorConfig;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

public class CameraShutterMuteHook implements BaseHook {

  private static final Map<SoundPool, Set<Integer>> mutedSoundIds =
      Collections.synchronizedMap(new WeakHashMap<>());
  private static final Set<Integer> targetResIds = ConcurrentHashMap.newKeySet();
  private static volatile boolean isResIdsLoaded = false;

  private static final String[] TARGET_SOUND_NAMES = {
    "camera_shutter_0613",
    "video_start_0613",
    "video_end_0613",
    "voom_countdown",
    "voom_countdown_1sec",
    "timer_shutter_jp",
    "timer_shutter_en"
  };

  @Override
  public void hook(VectorConfig options, LoadParam lpparam) throws Throwable {
    if (!options.muteCameraShutter.enabled || options.useDefaultCamera.enabled) return;

    try {
      Vector.module
          .hook(
              Reflect.findMethodExact(SoundPool.class, "load", Context.class, int.class, int.class))
          .intercept(
              chain -> {
                if (!isResIdsLoaded && chain.getArg(0) != null) {
                  Context ctx = (Context) chain.getArg(0);
                  for (String name : TARGET_SOUND_NAMES) {
                    int id = ctx.getResources().getIdentifier(name, "raw", "jp.naver.line.android");
                    if (id != 0) targetResIds.add(id);
                  }
                  isResIdsLoaded = true;
                }

                Object result = chain.proceed();
                if (result instanceof Integer
                    && (Integer) result != 0
                    && targetResIds.contains((int) chain.getArg(1))) {
                  SoundPool pool = (SoundPool) chain.getThisObject();
                  synchronized (mutedSoundIds) {
                    mutedSoundIds
                        .computeIfAbsent(pool, unused -> ConcurrentHashMap.newKeySet())
                        .add((Integer) result);
                  }
                }
                return result;
              });

      Vector.module
          .hook(
              Reflect.findMethodExact(
                  SoundPool.class,
                  "play",
                  int.class,
                  float.class,
                  float.class,
                  int.class,
                  int.class,
                  float.class))
          .intercept(
              chain -> {
                Set<Integer> muted = mutedSoundIds.get((SoundPool) chain.getThisObject());
                return muted != null && muted.contains((int) chain.getArg(0)) ? 0 : chain.proceed();
              });

      Vector.module
          .hook(Reflect.findMethodExact(SoundPool.class, "unload", int.class))
          .intercept(
              chain -> {
                Object result = chain.proceed();
                if (Boolean.TRUE.equals(result)) {
                  Set<Integer> muted = mutedSoundIds.get((SoundPool) chain.getThisObject());
                  if (muted != null) muted.remove((int) chain.getArg(0));
                }
                return result;
              });

      Vector.module
          .hook(Reflect.findMethodExact(SoundPool.class, "release"))
          .intercept(
              chain -> {
                try {
                  return chain.proceed();
                } finally {
                  mutedSoundIds.remove((SoundPool) chain.getThisObject());
                }
              });

      Vector.module
          .hook(
              Reflect.findMethodExact(
                  Camera.class,
                  "takePicture",
                  Camera.ShutterCallback.class,
                  Camera.PictureCallback.class,
                  Camera.PictureCallback.class,
                  Camera.PictureCallback.class))
          .intercept(
              chain -> {
                Object[] args = chain.getArgs().toArray();
                args[0] = null;
                return chain.proceed(args);
              });

      Vector.log("Tencha: Camera Shutter Mute hooked");
    } catch (Throwable t) {
      Vector.log("Tencha: Failed to hook Camera Shutter Mute: " + t.getMessage());
    }
  }
}
