package dev.vector.lineextension

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardStateTest {
  @Test
  fun connectionNeedsAReportForTheInstalledVersions() {
    assertTrue(currentConnectionReported(200L, "26.14.0", "26.14.0", 100L, 150L, "exact"))
    assertFalse(currentConnectionReported(0L, "26.14.0", "26.14.0", 100L, 150L, "exact"))
    assertFalse(currentConnectionReported(200L, "26.13.1", "26.14.0", 100L, 150L, "exact"))
    assertFalse(currentConnectionReported(200L, "26.14.0", null, 100L, 150L, "exact"))
  }

  @Test
  fun connectionRequiresARestartAfterEitherAppUpdates() {
    assertFalse(currentConnectionReported(200L, "26.14.0", "26.14.0", 201L, 150L, "exact"))
    assertFalse(currentConnectionReported(200L, "26.14.0", "26.14.0", 100L, 201L, "exact"))
    assertFalse(currentConnectionReported(200L, "26.14.0", "26.14.0", 100L, 150L, "unsupported"))
  }

  @Test
  fun runtimeModeDistinguishesRootAndLspatch() {
    assertEquals("Vector経由で動作中（root）", runtimeModeLabel("root", reported = true))
    assertEquals("LSPatch経由で動作中（非root）", runtimeModeLabel("lspatch", reported = true))
    assertEquals("LINE再起動後に実行方式を表示", runtimeModeLabel("unknown", reported = false))
  }
}
