package dev.vector.lineextension.core;

public enum FeatureStatus {
  WORKING("動作中"),
  PARTIAL("一部非対応"),
  DISABLED("無効"),
  VERIFYING("確認中"),
  HOOK_FAILED("Hook失敗"),
  SAFE_MODE("Safe Mode");

  public final String label;

  FeatureStatus(String label) {
    this.label = label;
  }
}
