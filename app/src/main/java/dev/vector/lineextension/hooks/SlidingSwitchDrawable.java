package dev.vector.lineextension.hooks;

import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.animation.PathInterpolator;
import android.widget.CheckedTextView;
import java.lang.ref.WeakReference;

/** Moves a single thumb, keeping LINE's native drawable at rest and when disabled. */
final class SlidingSwitchDrawable extends Drawable {
  private final Drawable nativeSwitch;
  private final Bitmap thumb;
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
  private final WeakReference<CheckedTextView> host;
  private final int offColor;
  private final int onColor;
  private float position;
  private boolean checked;
  private boolean moving;
  private ValueAnimator animator;

  SlidingSwitchDrawable(CheckedTextView view) {
    host = new WeakReference<>(view);
    nativeSwitch = view.getCheckMarkDrawable();
    checked = view.isChecked();
    position = checked ? 1f : 0f;
    Bitmap off = asset(view, "common_img_switch_off");
    Bitmap on = asset(view, "common_img_switch_on");
    offColor = off.getPixel((int) (off.getWidth() * .8f), off.getHeight() / 2);
    onColor = on.getPixel((int) (on.getWidth() * .2f), on.getHeight() / 2);
    // APK assets: 78x52 at xhdpi; white thumb centers differ by 26 pixels.
    thumb = extractThumb(off, offColor);
  }

  private static Bitmap extractThumb(Bitmap source, int trackColor) {
    int left = Math.round(source.getWidth() * 2f / 78f);
    int top = Math.round(source.getHeight() * 2f / 52f);
    int width = Math.round(source.getWidth() * 48f / 78f);
    int height = source.getHeight() - top;
    Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    float trackBrightness =
        (Color.red(trackColor) + Color.green(trackColor) + Color.blue(trackColor)) / 3f;
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        int color = source.getPixel(left + x, top + y);
        float px = (left + x + .5f) * 78f / source.getWidth();
        float py = (top + y + .5f) * 52f / source.getHeight();
        float dx = px - 26f;
        float dy = py - 26f;
        // Keep the complete native thumb and its antialiased rim. Outside it, remove
        // only the old track, recovering the black shadow composited over that track.
        if (dx * dx + dy * dy > 400f && py >= 12f && py <= 40f) {
          float brightness = (Color.red(color) + Color.green(color) + Color.blue(color)) / 3f;
          int alpha =
              Math.round(
                  Color.alpha(color)
                      * Math.max(0f, 1f - brightness / Math.max(1f, trackBrightness)));
          color = Color.argb(alpha, 0, 0, 0);
        }
        result.setPixel(x, y, color);
      }
    }
    return result;
  }

  private static Bitmap asset(CheckedTextView view, String name) {
    int id = view.getResources().getIdentifier(name, "drawable", "jp.naver.line.android");
    Drawable drawable = view.getContext().getDrawable(id);
    Bitmap bitmap =
        Bitmap.createBitmap(
            drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
    drawable.setBounds(0, 0, bitmap.getWidth(), bitmap.getHeight());
    drawable.draw(new Canvas(bitmap));
    return bitmap;
  }

  @Override
  protected boolean onStateChange(int[] state) {
    nativeSwitch.setState(state);
    boolean next = false;
    boolean enabled = false;
    for (int item : state) {
      if (item == android.R.attr.state_checked) next = true;
      if (item == android.R.attr.state_enabled) enabled = true;
    }
    CheckedTextView view = host.get();
    if (!enabled
        || !InteractionMotionHook.animationsEnabled()
        || view == null
        || !view.isShown()
        || !view.isAttachedToWindow()
        || !ValueAnimator.areAnimatorsEnabled()) {
      if (animator != null) animator.cancel();
      checked = next;
      position = next ? 1f : 0f;
      moving = false;
      invalidateSelf();
      return true;
    }
    if (checked == next) return true;
    checked = next;
    if (animator != null) animator.cancel();
    moving = true;
    animator = ValueAnimator.ofFloat(position, next ? 1f : 0f);
    animator.setDuration(220);
    animator.setInterpolator(new PathInterpolator(.2f, 0f, 0f, 1f));
    animator.addUpdateListener(
        a -> {
          position = (Float) a.getAnimatedValue();
          if (a.getAnimatedFraction() == 1f) moving = false;
          invalidateSelf();
        });
    animator.start();
    return true;
  }

  @Override
  public void draw(Canvas canvas) {
    if (!moving) {
      nativeSwitch.setBounds(getBounds());
      nativeSwitch.draw(canvas);
      return;
    }
    int save = canvas.save();
    canvas.translate(getBounds().left, getBounds().top);
    canvas.scale(getBounds().width() / 78f, getBounds().height() / 52f);
    paint.setColor(blend(offColor, onColor, position));
    canvas.drawRoundRect(new RectF(4, 12, 74, 40), 14, 14, paint);
    float left = 6 + 26 * position;
    // The extracted bitmap includes the native shadow beyond the circular thumb.
    // Do not clip to the circle: that was cutting off the shadow during motion.
    paint.setColor(Color.WHITE);
    canvas.drawBitmap(thumb, null, new RectF(left - 4, 2, left + 44, 52), paint);
    canvas.restoreToCount(save);
  }

  private static int blend(int a, int b, float t) {
    return Color.rgb(
        (int) (Color.red(a) + (Color.red(b) - Color.red(a)) * t),
        (int) (Color.green(a) + (Color.green(b) - Color.green(a)) * t),
        (int) (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t));
  }

  @Override
  public boolean isStateful() {
    return true;
  }

  @Override
  public int getIntrinsicWidth() {
    return nativeSwitch.getIntrinsicWidth();
  }

  @Override
  public int getIntrinsicHeight() {
    return nativeSwitch.getIntrinsicHeight();
  }

  @Override
  public void setAlpha(int alpha) {
    paint.setAlpha(alpha);
    nativeSwitch.setAlpha(alpha);
  }

  @Override
  public void setColorFilter(ColorFilter filter) {
    paint.setColorFilter(filter);
    nativeSwitch.setColorFilter(filter);
  }

  @Override
  public int getOpacity() {
    return PixelFormat.TRANSLUCENT;
  }
}
