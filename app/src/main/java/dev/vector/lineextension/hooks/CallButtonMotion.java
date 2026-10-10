package dev.vector.lineextension.hooks;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.view.animation.PathInterpolator;
import java.lang.ref.WeakReference;
import java.util.WeakHashMap;

/** One interruptible visual animation per control. Never touches click or audio routing. */
final class CallButtonMotion implements View.OnAttachStateChangeListener {
  private static final WeakHashMap<View, CallButtonMotion> states = new WeakHashMap<>();
  private final WeakReference<View> target;
  private final float baseX;
  private final float baseY;
  private ValueAnimator animator;
  private boolean held;

  private CallButtonMotion(View view) {
    target = new WeakReference<>(view);
    baseX = view.getScaleX();
    baseY = view.getScaleY();
    view.addOnAttachStateChangeListener(this);
  }

  static void setPressed(View view, boolean pressed) {
    CallButtonMotion state = states.get(view);
    if (state == null) {
      if (!pressed) return;
      state = new CallButtonMotion(view);
      states.put(view, state);
    }
    state.animate(pressed);
  }

  static void reset(View view) {
    CallButtonMotion state = states.remove(view);
    if (state != null) state.restore();
  }

  private void animate(boolean pressed) {
    if (held == pressed) return;
    held = pressed;
    View view = target.get();
    if (view == null) return;
    if (animator != null) animator.cancel();
    float startX = view.getScaleX();
    float startY = view.getScaleY();
    float endX = baseX * (pressed ? .86f : 1f);
    float endY = baseY * (pressed ? .86f : 1f);
    ValueAnimator next = ValueAnimator.ofFloat(0f, 1f);
    next.setDuration(pressed ? 100L : 300L);
    next.setInterpolator(
        pressed ? new PathInterpolator(.2f, 0f, 0f, 1f) : new OvershootInterpolator(1.4f));
    next.addUpdateListener(
        a -> {
          View current = target.get();
          if (current == null) return;
          float t = (Float) a.getAnimatedValue();
          current.setScaleX(startX + (endX - startX) * t);
          current.setScaleY(startY + (endY - startY) * t);
        });
    next.addListener(
        new AnimatorListenerAdapter() {
          @Override
          public void onAnimationEnd(Animator completed) {
            if (animator == completed) animator = null;
            completed.removeAllListeners();
            ((ValueAnimator) completed).removeAllUpdateListeners();
          }
        });
    animator = next;
    next.start();
  }

  private void restore() {
    if (animator != null) animator.cancel();
    View view = target.get();
    if (view != null) {
      view.setScaleX(baseX);
      view.setScaleY(baseY);
      view.removeOnAttachStateChangeListener(this);
    }
  }

  @Override
  public void onViewAttachedToWindow(View view) {}

  @Override
  public void onViewDetachedFromWindow(View view) {
    reset(view);
  }
}
