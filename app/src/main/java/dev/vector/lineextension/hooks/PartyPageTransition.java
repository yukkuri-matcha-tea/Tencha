package dev.vector.lineextension.hooks;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;

/** Horizontal paging for the local party page without modifying LINE's service adapter. */
final class PartyPageTransition {
  private final ViewGroup container;
  private final View nativePage;
  private final View soundboardPage;
  private ValueAnimator animator;
  private float position;
  private boolean soundboardSelected;

  PartyPageTransition(ViewGroup container, View nativePage, View soundboardPage) {
    this.container = container;
    this.nativePage = nativePage;
    this.soundboardPage = soundboardPage;
    container.setClipChildren(true);
    container.addOnAttachStateChangeListener(
        new View.OnAttachStateChangeListener() {
          @Override
          public void onViewAttachedToWindow(View view) {}

          @Override
          public void onViewDetachedFromWindow(View view) {
            cancel();
            position = soundboardSelected ? 1f : 0f;
            settle();
          }
        });
    container.addOnLayoutChangeListener(
        (view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
          if (animator != null) applyPosition();
        });
  }

  void show(boolean soundboard) {
    if (soundboardSelected == soundboard) return;
    soundboardSelected = soundboard;
    cancel();
    float target = soundboard ? 1f : 0f;
    if (container.getWidth() == 0 || !ValueAnimator.areAnimatorsEnabled()) {
      position = target;
      settle();
      return;
    }
    nativePage.setVisibility(View.VISIBLE);
    soundboardPage.setVisibility(View.VISIBLE);
    applyPosition();
    ValueAnimator next = ValueAnimator.ofFloat(position, target);
    animator = next;
    // Match RecyclerView's horizontal page settling: travel at 25 ms/inch, then decelerate.
    int dpi = Math.max(1, container.getResources().getDisplayMetrics().densityDpi);
    double distance = container.getWidth() * Math.abs(target - position);
    next.setDuration((long) Math.ceil(Math.ceil(distance * 25.0 / dpi) / 0.3356));
    next.setInterpolator(new DecelerateInterpolator());
    next.addUpdateListener(
        value -> {
          position = (Float) value.getAnimatedValue();
          applyPosition();
        });
    next.addListener(
        new AnimatorListenerAdapter() {
          @Override
          public void onAnimationEnd(Animator animation) {
            if (animator != animation) return;
            animator = null;
            position = target;
            settle();
          }
        });
    next.start();
  }

  private void applyPosition() {
    int direction = container.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL ? -1 : 1;
    float width = container.getWidth() * direction;
    nativePage.setTranslationX(-width * position);
    soundboardPage.setTranslationX(width * (1f - position));
  }

  private void settle() {
    nativePage.setVisibility(soundboardSelected ? View.GONE : View.VISIBLE);
    soundboardPage.setVisibility(soundboardSelected ? View.VISIBLE : View.GONE);
    nativePage.setTranslationX(0f);
    soundboardPage.setTranslationX(0f);
  }

  private void cancel() {
    ValueAnimator previous = animator;
    animator = null;
    if (previous != null) previous.cancel();
  }
}
