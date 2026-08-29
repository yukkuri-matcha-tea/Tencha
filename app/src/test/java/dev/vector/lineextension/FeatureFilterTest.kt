package dev.vector.lineextension

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureFilterTest {
  @Test
  fun compatibilityLabelsExplainAutomaticAndStoppedModes() {
    assertEquals("正式対応", compatibilityLabel("exact", "26.13.0"))
    assertEquals("自動互換（26.13.0 構成）", compatibilityLabel("automatic", "26.13.0"))
    assertEquals("互換性なし・全機能停止", compatibilityLabel("unsupported", ""))
  }

  @Test
  fun runtimeModeLabelSeparatesPendingAndFailedDetection() {
    assertEquals("Vector経由で動作中（root）", runtimeModeLabel("root", reported = true))
    assertEquals("LSPatch経由で動作中（非root）", runtimeModeLabel("lspatch", reported = true))
    assertEquals("LINE再起動後に実行方式を表示", runtimeModeLabel("unknown", reported = false))
    assertEquals("接続済み（実行方式を判定できません）", runtimeModeLabel("unknown", reported = true))
  }

  @Test
  fun rootlessSetupIsOnlyShownBeforeConnectionWithoutRootEvidence() {
    assertTrue(shouldShowRootlessSetup(hasConnected = false, hasRootEvidence = false))
    assertFalse(shouldShowRootlessSetup(hasConnected = true, hasRootEvidence = false))
    assertFalse(shouldShowRootlessSetup(hasConnected = false, hasRootEvidence = true))
    assertFalse(shouldShowRootlessSetup(hasConnected = true, hasRootEvidence = true))
  }

  @Test
  fun noFiltersIncludesEveryFeature() {
    assertTrue(matchesFeatureFilter("通知", "通知を改善します", "", false, false, false, false))
  }

  @Test
  fun enabledOnlyRequiresEnabledFeature() {
    assertTrue(matchesFeatureFilter("通知", "通知を改善します", "", true, false, true, false))
    assertFalse(matchesFeatureFilter("通知", "通知を改善します", "", false, true, true, false))
  }

  @Test
  fun experimentalOnlyRequiresExperimentalFeature() {
    assertTrue(matchesFeatureFilter("通知", "通知を改善します", "", false, true, false, true))
    assertFalse(matchesFeatureFilter("通知", "通知を改善します", "", true, false, false, true))
  }

  @Test
  fun combinedFiltersRequireEnabledExperimentalFeature() {
    assertTrue(matchesFeatureFilter("FCM実験", "通知を改善します", "", true, true, true, true))
    assertFalse(matchesFeatureFilter("FCM実験", "通知を改善します", "", false, true, true, true))
    assertFalse(matchesFeatureFilter("FCM実験", "通知を改善します", "", true, false, true, true))
  }

  @Test
  fun queryMatchesNameOrDescription() {
    assertTrue(matchesFeatureFilter("FCM実験", "通知を改善します", "fcm", true, true, true, true))
    assertTrue(matchesFeatureFilter("FCM実験", "通知を改善します", "改善", true, true, true, true))
    assertFalse(matchesFeatureFilter("FCM実験", "通知を改善します", "広告", true, true, true, true))
  }
}
