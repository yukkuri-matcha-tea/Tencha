package dev.vector.lineextension.hooks;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.Looper;
import android.view.View;
import android.view.animation.PathInterpolator;
import android.widget.CheckedTextView;
import dev.vector.lineextension.LoadParam;
import dev.vector.lineextension.Main;
import dev.vector.lineextension.Reflect;
import dev.vector.lineextension.SettingsStore;
import dev.vector.lineextension.Vector;
import dev.vector.lineextension.VectorConfig;

/** Visual feedback only: never intercept clicks, audio state, or settings persistence. */
public final class InteractionMotionHook implements BaseHook {

  @Override
  public void hook(VectorConfig config, LoadParam param) throws Throwable {
    Vector.module
        .hook(Reflect.findMethodExact(View.class, "setPressed", boolean.class))
        .intercept(
            chain -> {
              Object result = chain.proceed();
              View view = (View) chain.getThisObject();
              try {
                if (!animationsEnabled()) {
                  CallButtonMotion.reset(view);
                  return result;
                }
                if (isCallButton(view)) {
                  if (ready(view) && isMicOrSpeaker(view)) {
                    CallButtonMotion.setPressed(view, (Boolean) chain.getArg(0));
                  } else if (ready(view)) {
                    press(view, (Boolean) chain.getArg(0));
                  } else {
                    CallButtonMotion.reset(view);
                  }
                }
              } catch (Throwable error) {
                Vector.log("Tencha: button motion skipped", error);
              }
              return result;
            });
    Vector.module
        .hook(Reflect.findMethodExact(CheckedTextView.class, "setChecked", boolean.class))
        .intercept(
            chain -> {
              CheckedTextView view = (CheckedTextView) chain.getThisObject();
              try {
                if (ready(view)
                    && animationsEnabled()
                    && isSwitch(view)
                    && !(view.getCheckMarkDrawable() instanceof SlidingSwitchDrawable)) {
                  view.setCheckMarkDrawable(new SlidingSwitchDrawable(view));
                }
              } catch (Throwable error) {
                Vector.log("Tencha: sliding switch unavailable", error);
              }
              return chain.proceed();
            });
  }

  private static boolean ready(View view) {
    return Looper.myLooper() == Looper.getMainLooper()
        && view.isAttachedToWindow()
        && view.isShown()
        && view.isEnabled()
        && view.getWidth() > 0
        && view.getHeight() > 0
        && ValueAnimator.areAnimatorsEnabled();
  }

  static boolean animationsEnabled() {
    return SettingsStore.get(
        Main.options.enhanceAnimations.key, Main.options.enhanceAnimations.enabled);
  }

  private static String resource(View view) {
    if (view.getId() == View.NO_ID) return "";
    try {
      return view.getResources().getResourceEntryName(view.getId());
    } catch (RuntimeException ignored) {
      return "";
    }
  }

  private static boolean isSwitch(View view) {
    String id = resource(view);
    return "setting_switch".equals(id) || "setting_item_switch".equals(id);
  }

  static boolean isMicOrSpeaker(View view) {
    if (MotionTargets.isMicOrSpeaker(resource(view))) return true;
    CharSequence description = view.getContentDescription();
    if (description == null) return false;
    String[] names = {
      "call_previewscreen_button_mute",
      "call_previewscreen_button_unmute",
      "access_call_mute",
      "access_call_mic_on",
      "access_call_mute_on",
      "access_call_mute_off",
      "access_call_speaker_on",
      "access_call_speaker_off",
      "voip_audio_mute",
      "voip_audio_unmute",
      "voip_audio_speaker_on",
      "voip_audio_speaker_off"
    };
    for (String name : names) {
      int id = view.getResources().getIdentifier(name, "string", "jp.naver.line.android");
      if (id != 0 && description.toString().trim().equals(view.getContext().getString(id).trim()))
        return true;
    }
    return false;
  }

  static boolean isCallButton(View view) {
    Context context = view.getContext();
    for (int i = 0; i < 12 && context != null; i++) {
      if (context.getClass().getName().equals("com.linecorp.voip2.service.VoIPServiceActivity")) {
        return MotionTargets.isCallControl(resource(view));
      }
      if (!(context instanceof ContextWrapper)) break;
      Context next = ((ContextWrapper) context).getBaseContext();
      if (next == context) break;
      context = next;
    }
    return false;
  }

  private static void press(View view, boolean pressed) {
    // A legacy view animation is a render transform, not a layout or audio-state mutation.
    android.view.animation.ScaleAnimation animation =
        new android.view.animation.ScaleAnimation(
            pressed ? 1f : 0.90f,
            pressed ? 0.90f : 1f,
            pressed ? 1f : 0.90f,
            pressed ? 0.90f : 1f,
            android.view.animation.Animation.RELATIVE_TO_SELF,
            0.5f,
            android.view.animation.Animation.RELATIVE_TO_SELF,
            0.5f);
    animation.setDuration(pressed ? 90 : 180);
    animation.setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f));
    // Never leave a retained transform after a canceled gesture or detached call screen.
    animation.setFillAfter(false);
    view.startAnimation(animation);
  }
}
