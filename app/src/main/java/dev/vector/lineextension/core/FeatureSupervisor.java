package dev.vector.lineextension.core;

import android.content.Context;
import dev.vector.lineextension.Vector;
import java.util.Locale;

/**
 * Install-time fault isolation. Runtime success remains VERIFYING until a feature reports
 * execution.
 */
public final class FeatureSupervisor {
  private final Context context;

  public FeatureSupervisor(Context context) {
    this.context = context.getApplicationContext();
  }

  public String idFor(Object feature) {
    return idForName(feature.getClass().getSimpleName());
  }

  static String idForName(String name) {
    if (name.contains("ReadReceipt")) return "read_block";
    if (name.contains("Unsend")) return "unsend_retention";
    if (name.contains("Timestamp")) return "message_seconds";
    if (name.equals("RemoveAds")) return "ad_removal";
    if (name.equals("RemoveHomeContents")) return "remove_home_contents";
    if (name.contains("Tab")) return "tab_customizer";
    if (name.contains("Fcm")) return "fcm_fix";
    if (name.contains("Foreground")) return "line_foreground_keep_alive";
    if (name.equals("HideAiIconPermanently")) return "agenti_hider";
    if (name.equals("RemoveTalkRoomAgentIToggle")) return "remove_talk_room_agenti_toggle";
    if (name.equals("SearchMin1CharHook")) return "search_enhancement";
    if (name.equals("SearchResultCountHook")) return "search_result_count";
    if (name.equals("SearchByMemberHook")) return "search_by_member";
    if (name.contains("Font")) return "custom_font";
    if (name.contains("Image")) return "media_quality";
    if (name.contains("Video")) return "long_video";
    if (name.contains("Browser")) return "external_browser";
    if (name.contains("Menu")) return "menu_customizer";
    if (name.contains("Header")) return "header_customizer";
    if (name.contains("Settings")) return "line_settings_ui";
    return name.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
  }

  public boolean isSafeMode(String id) {
    return ControlClient.featureState(context, id).getBoolean("safeMode", false);
  }

  public void installed(String id) {
    ControlClient.report(context, id, FeatureStatus.VERIFYING, "Hook登録済み・Runtime確認待ち");
  }

  public void failed(String id, Throwable failure) {
    String detail = failure.getClass().getSimpleName();
    String message = failure.getMessage();
    if (message != null && !message.isBlank()) detail += ": " + message;
    ControlClient.report(context, id, FeatureStatus.HOOK_FAILED, detail);
    Vector.log("Tencha: isolated feature failure " + id, failure);
  }

  public void safeMode(String id) {
    ControlClient.report(context, id, FeatureStatus.SAFE_MODE, "連続失敗により自動停止");
  }
}
