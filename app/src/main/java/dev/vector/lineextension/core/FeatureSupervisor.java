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
    String name = feature.getClass().getSimpleName();
    if (name.contains("ReadReceipt")) return "read_block";
    if (name.contains("Unsend")) return "unsend_retention";
    if (name.contains("Timestamp")) return "message_seconds";
    if (name.contains("Ad") || name.contains("HomeContents")) return "ad_removal";
    if (name.contains("Tab")) return "tab_customizer";
    if (name.contains("Fcm") || name.contains("Foreground")) return "fcm_fix";
    if (name.contains("Agent") || name.contains("AiIcon")) return "agenti_hider";
    if (name.contains("Search")) return "search_enhancement";
    if (name.contains("Font")) return "custom_font";
    if (name.contains("Image") || name.contains("Video")) return "media_quality";
    if (name.contains("Browser")) return "external_browser";
    if (name.contains("Menu") || name.contains("Header")) return "menu_customizer";
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
