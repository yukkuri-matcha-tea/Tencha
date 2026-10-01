package dev.vector.lineextension.hooks;

import android.app.AlertDialog;
import android.content.Context;
import android.text.InputType;
import android.widget.EditText;
import android.widget.Toast;
import dev.vector.lineextension.SettingsStore;
import dev.vector.lineextension.utils.LineTheme;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class CallTtsSettings {
  static final String KEY_TARGET = "call_tts_target";
  static final String KEY_OUTPUT = "call_tts_output";
  static final String KEY_SENDER_NAME = "call_tts_sender_name";
  static final String KEY_MAX_CHARS = "call_tts_max_chars";
  static final String KEY_SELECTED_MIDS = "call_tts_selected_mids";

  static final String TARGET_ALL = "all";
  static final String TARGET_SELF = "self";
  static final String TARGET_OTHERS = "others";
  static final String TARGET_SELECTED = "selected";
  static final String OUTPUT_LOCAL = "local";
  static final String OUTPUT_REMOTE = "remote";
  static final String OUTPUT_BOTH = "both";

  static final class Person {
    final String mid;
    final String name;

    Person(String mid, String name) {
      this.mid = mid;
      this.name = name;
    }
  }

  private CallTtsSettings() {}

  static String target() {
    return SettingsStore.getString(KEY_TARGET, TARGET_ALL);
  }

  static String output() {
    return SettingsStore.getString(KEY_OUTPUT, OUTPUT_BOTH);
  }

  static boolean includeSenderName() {
    return SettingsStore.get(KEY_SENDER_NAME, false);
  }

  static int maxChars() {
    try {
      return Math.max(
          20, Math.min(500, Integer.parseInt(SettingsStore.getString(KEY_MAX_CHARS, "200"))));
    } catch (Throwable ignored) {
      return 200;
    }
  }

  static Set<String> selectedMids() {
    LinkedHashSet<String> result = new LinkedHashSet<>();
    String raw = SettingsStore.getString(KEY_SELECTED_MIDS, "");
    if (!raw.isEmpty()) result.addAll(Arrays.asList(raw.split(",")));
    result.remove("");
    return result;
  }

  static void show(Context context, List<Person> availablePeople) {
    String[] rows = {
      "読み上げ対象　" + targetLabel(target()),
      "出力先　" + outputLabel(output()),
      "送信者名を読み上げる　" + (includeSenderName() ? "ON" : "OFF"),
      "最大読み上げ文字数　" + maxChars(),
      "個別に選択　" + selectedMids().size() + "人"
    };
    AlertDialog dialog =
        new AlertDialog.Builder(context, LineTheme.dialogTheme(context))
            .setTitle("TTS設定")
            .setItems(
                rows,
                (d, which) -> {
                  if (which == 0) showTargetPicker(context, availablePeople);
                  else if (which == 1) showOutputPicker(context, availablePeople);
                  else if (which == 2) {
                    SettingsStore.save(KEY_SENDER_NAME, !includeSenderName());
                    show(context, availablePeople);
                  } else if (which == 3) showMaxChars(context, availablePeople);
                  else showPeoplePicker(context, availablePeople);
                })
            .setNegativeButton("閉じる", null)
            .create();
    LineTheme.applyDialogColors(dialog, context);
    dialog.show();
  }

  private static void showTargetPicker(Context context, List<Person> people) {
    String[] labels = {"全員", "自分のみ", "自分以外", "個別に選択"};
    String[] values = {TARGET_ALL, TARGET_SELF, TARGET_OTHERS, TARGET_SELECTED};
    int checked = Math.max(0, Arrays.asList(values).indexOf(target()));
    AlertDialog dialog =
        new AlertDialog.Builder(context, LineTheme.dialogTheme(context))
            .setTitle("読み上げ対象")
            .setSingleChoiceItems(
                labels,
                checked,
                (d, which) -> {
                  SettingsStore.save(KEY_TARGET, values[which]);
                  d.dismiss();
                  if (TARGET_SELECTED.equals(values[which])) showPeoplePicker(context, people);
                  else show(context, people);
                })
            .setNegativeButton("キャンセル", null)
            .create();
    LineTheme.applyDialogColors(dialog, context);
    dialog.show();
  }

  private static void showOutputPicker(Context context, List<Person> people) {
    String[] labels = {"自分だけ", "通話相手だけ", "自分 + 通話相手"};
    String[] values = {OUTPUT_LOCAL, OUTPUT_REMOTE, OUTPUT_BOTH};
    int checked = Math.max(0, Arrays.asList(values).indexOf(output()));
    AlertDialog dialog =
        new AlertDialog.Builder(context, LineTheme.dialogTheme(context))
            .setTitle("TTS出力先")
            .setSingleChoiceItems(
                labels,
                checked,
                (d, which) -> {
                  SettingsStore.save(KEY_OUTPUT, values[which]);
                  d.dismiss();
                  show(context, people);
                })
            .setNegativeButton("キャンセル", null)
            .create();
    LineTheme.applyDialogColors(dialog, context);
    dialog.show();
  }

  private static void showMaxChars(Context context, List<Person> people) {
    EditText input = new EditText(context);
    input.setInputType(InputType.TYPE_CLASS_NUMBER);
    input.setText(String.valueOf(maxChars()));
    input.setSelectAllOnFocus(true);
    AlertDialog dialog =
        new AlertDialog.Builder(context, LineTheme.dialogTheme(context))
            .setTitle("最大読み上げ文字数")
            .setMessage("20〜500文字")
            .setView(input)
            .setPositiveButton(
                "保存",
                (d, which) -> {
                  try {
                    int value =
                        Math.max(20, Math.min(500, Integer.parseInt(input.getText().toString())));
                    SettingsStore.save(KEY_MAX_CHARS, String.valueOf(value));
                  } catch (Throwable ignored) {
                    Toast.makeText(context, "20〜500の数字を入力してください", Toast.LENGTH_SHORT).show();
                  }
                  show(context, people);
                })
            .setNegativeButton("キャンセル", null)
            .create();
    LineTheme.applyDialogColors(dialog, context);
    dialog.show();
  }

  private static void showPeoplePicker(Context context, List<Person> availablePeople) {
    List<Person> people =
        new ArrayList<>(availablePeople == null ? Collections.emptyList() : availablePeople);
    Set<String> stored = selectedMids();
    for (String mid : stored) {
      boolean present = false;
      for (Person person : people) if (mid.equals(person.mid)) present = true;
      if (!present) people.add(new Person(mid, mid));
    }
    if (people.isEmpty()) {
      Toast.makeText(context, "通話中に通話調整から参加者を選択できます", Toast.LENGTH_LONG).show();
      show(context, availablePeople);
      return;
    }
    String[] labels = new String[people.size()];
    boolean[] checked = new boolean[people.size()];
    for (int i = 0; i < people.size(); i++) {
      labels[i] = people.get(i).name;
      checked[i] = stored.contains(people.get(i).mid);
    }
    AlertDialog dialog =
        new AlertDialog.Builder(context, LineTheme.dialogTheme(context))
            .setTitle("読み上げるユーザー")
            .setMultiChoiceItems(
                labels,
                checked,
                (d, which, enabled) -> {
                  if (enabled) stored.add(people.get(which).mid);
                  else stored.remove(people.get(which).mid);
                })
            .setPositiveButton(
                "保存",
                (d, which) -> {
                  SettingsStore.save(KEY_SELECTED_MIDS, String.join(",", stored));
                  SettingsStore.save(KEY_TARGET, TARGET_SELECTED);
                  show(context, availablePeople);
                })
            .setNegativeButton("キャンセル", null)
            .create();
    LineTheme.applyDialogColors(dialog, context);
    dialog.show();
  }

  private static String targetLabel(String value) {
    if (TARGET_SELF.equals(value)) return "自分のみ";
    if (TARGET_OTHERS.equals(value)) return "自分以外";
    if (TARGET_SELECTED.equals(value)) return "個別に選択";
    return "全員";
  }

  private static String outputLabel(String value) {
    if (OUTPUT_LOCAL.equals(value)) return "自分だけ";
    if (OUTPUT_REMOTE.equals(value)) return "通話相手だけ";
    return "自分 + 通話相手";
  }
}
