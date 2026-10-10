package dev.vector.lineextension.hooks;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Context;
import android.graphics.Point;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import dev.vector.lineextension.utils.LineTheme;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Framework drag/drop avoids relying on LINE's obfuscated RecyclerView adapter classes. */
final class SoundboardManagerDialog {
  private final Context context;
  private final ScrollView scroll;
  private final LinearLayout list;
  private final List<SoundboardStore.Clip> clips = new ArrayList<>();
  private final Consumer<SoundboardStore.Clip> delete;
  private final Consumer<SoundboardStore.Clip> accessibleMove;
  private final Runnable changed;
  private AlertDialog dialog;
  private String draggingId;
  private float pointerY;
  private final Runnable autoScroll =
      new Runnable() {
        @Override
        public void run() {
          if (draggingId == null || !scroll.isAttachedToWindow()) return;
          int step =
              pointerY < dp(48) ? -dp(8) : pointerY > scroll.getHeight() - dp(48) ? dp(8) : 0;
          if (step != 0) {
            scroll.scrollBy(0, step);
            moveAtPointer();
          }
          scroll.postOnAnimation(this);
        }
      };

  static void show(
      Context context,
      Runnable add,
      Consumer<SoundboardStore.Clip> delete,
      Consumer<SoundboardStore.Clip> accessibleMove,
      Runnable changed) {
    new SoundboardManagerDialog(context, add, delete, accessibleMove, changed);
  }

  private SoundboardManagerDialog(
      Context context,
      Runnable add,
      Consumer<SoundboardStore.Clip> delete,
      Consumer<SoundboardStore.Clip> accessibleMove,
      Runnable changed) {
    this.context = context;
    this.delete = delete;
    this.accessibleMove = accessibleMove;
    this.changed = changed;
    scroll = new ScrollView(context);
    scroll.setFillViewport(false);
    list = new LinearLayout(context);
    list.setOrientation(LinearLayout.VERTICAL);
    list.setPadding(dp(16), 0, dp(16), 0);
    scroll.addView(list);
    reload();
    scroll.setOnDragListener(this::onDrag);
    dialog =
        new AlertDialog.Builder(context, LineTheme.dialogTheme(context))
            .setTitle("サウンドボード音声")
            .setView(scroll)
            .setNeutralButton("音声を追加", (d, button) -> add.run())
            .setPositiveButton("閉じる", null)
            .create();
    dialog.setOnDismissListener(
        d -> {
          scroll.removeCallbacks(autoScroll);
          if (draggingId != null) scroll.cancelDragAndDrop();
          draggingId = null;
        });
    dialog.show();
    LineTheme.applyDialogColors(dialog, context);
  }

  private int dp(int value) {
    return Math.round(value * context.getResources().getDisplayMetrics().density);
  }

  private void reload() {
    clips.clear();
    clips.addAll(SoundboardStore.load(context));
    list.removeAllViews();
    if (clips.isEmpty()) {
      TextView empty = new TextView(context);
      empty.setText("音声が登録されていません");
      empty.setTextColor(LineTheme.secondaryTextColor(context));
      empty.setPadding(dp(8), dp(24), dp(8), dp(24));
      list.addView(empty);
      return;
    }
    for (SoundboardStore.Clip clip : clips) {
      LinearLayout row = new LinearLayout(context);
      row.setGravity(Gravity.CENTER_VERTICAL);
      row.setBackgroundColor(LineTheme.backgroundColor(context));
      row.setTag(clip.id);
      TextView name = new TextView(context);
      name.setText(clip.name);
      name.setTextSize(16);
      name.setGravity(Gravity.CENTER_VERTICAL);
      name.setTextColor(LineTheme.primaryTextColor(context));
      name.setPadding(dp(8), 0, dp(8), 0);
      name.setSingleLine(true);
      name.setEllipsize(TextUtils.TruncateAt.END);
      name.setOnClickListener(
          v -> {
            dialog.dismiss();
            delete.accept(clip);
          });
      row.addView(name, new LinearLayout.LayoutParams(0, dp(56), 1));
      TextView handle = new TextView(context);
      handle.setText("≡");
      handle.setTypeface(Typeface.DEFAULT);
      handle.setTextSize(28);
      handle.setGravity(Gravity.CENTER);
      handle.setTextColor(LineTheme.secondaryTextColor(context));
      handle.setContentDescription(clip.name + "を並び替え");
      // Accessibility click offers an alternative to a drag gesture.
      handle.setOnClickListener(
          v -> {
            dialog.dismiss();
            accessibleMove.accept(clip);
          });
      handle.setOnTouchListener(
          (v, event) -> {
            if (event.getActionMasked() != MotionEvent.ACTION_DOWN) return true;
            if (draggingId != null) return true;
            if (row.startDragAndDrop(
                ClipData.newPlainText("Tencha soundboard", clip.id),
                new View.DragShadowBuilder(row) {
                  @Override
                  public void onProvideShadowMetrics(Point size, Point touch) {
                    super.onProvideShadowMetrics(size, touch);
                    touch.set(Math.max(0, row.getWidth() - dp(24)), row.getHeight() / 2);
                  }
                },
                this,
                0)) {
              draggingId = clip.id;
              pointerY = event.getRawY() - screenTop(scroll);
              row.setAlpha(0.4f);
              scroll.postOnAnimation(autoScroll);
            }
            return true;
          });
      row.addView(handle, new LinearLayout.LayoutParams(dp(48), dp(56)));
      list.addView(row);
    }
  }

  private static int screenTop(View view) {
    int[] location = new int[2];
    view.getLocationOnScreen(location);
    return location[1];
  }

  private boolean onDrag(View view, DragEvent event) {
    if (event.getLocalState() != this) return false;
    switch (event.getAction()) {
      case DragEvent.ACTION_DRAG_STARTED:
        return true;
      case DragEvent.ACTION_DRAG_ENTERED:
        scroll.removeCallbacks(autoScroll);
        scroll.postOnAnimation(autoScroll);
        return true;
      case DragEvent.ACTION_DRAG_EXITED:
        scroll.removeCallbacks(autoScroll);
        return true;
      case DragEvent.ACTION_DRAG_LOCATION:
        pointerY = event.getY();
        moveAtPointer();
        return true;
      case DragEvent.ACTION_DROP:
        pointerY = event.getY();
        moveAtPointer();
        List<String> ids = new ArrayList<>();
        for (SoundboardStore.Clip clip : clips) ids.add(clip.id);
        boolean saved = SoundboardStore.reorder(context, ids);
        if (!saved) Toast.makeText(context, "並び順を保存できません", Toast.LENGTH_LONG).show();
        else changed.run();
        return saved;
      case DragEvent.ACTION_DRAG_ENDED:
        scroll.removeCallbacks(autoScroll);
        draggingId = null;
        if (dialog.isShowing()) reload();
        return true;
      default:
        return true;
    }
  }

  private void moveAtPointer() {
    if (draggingId == null || clips.size() < 2) return;
    int from = -1;
    for (int i = 0; i < clips.size(); i++) if (draggingId.equals(clips.get(i).id)) from = i;
    if (from < 0) return;
    float contentY = pointerY + scroll.getScrollY() - list.getTop();
    int to = Math.max(0, Math.min(clips.size() - 1, (int) (contentY / dp(56))));
    if (from == to) return;
    View moving = list.getChildAt(from);
    SoundboardStore.Clip clip = clips.remove(from);
    clips.add(to, clip);
    list.removeViewAt(from);
    list.addView(moving, to);
  }
}
