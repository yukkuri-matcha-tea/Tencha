package dev.vector.lineextension

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureFilterTest {
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
