package dev.vector.lineextension.hooks;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.HashSet;
import org.junit.Test;

public final class CallTtsPolicyTest {
  @Test
  public void ownRowsMustBePlainMessagesFromThisAccount() {
    assertTrue(CallTtsPolicy.isPlainSelfRow(1, 0, null, "me", "hello"));
    assertTrue(CallTtsPolicy.isPlainSelfRow(1, 0, "me", "me", "hello"));
    assertFalse(CallTtsPolicy.isPlainSelfRow(1, 0, "other", "me", "hello"));
    assertFalse(CallTtsPolicy.isPlainSelfRow(1, 0, "other", null, "hello"));
    assertFalse(CallTtsPolicy.isPlainSelfRow(null, 0, null, "me", "hello"));
    assertFalse(CallTtsPolicy.isPlainSelfRow(1, null, null, "me", "hello"));
    assertFalse(CallTtsPolicy.isPlainSelfRow(1, 1, null, "me", "photo"));
    assertFalse(CallTtsPolicy.isPlainSelfRow(7, 0, null, "me", "system"));
    assertFalse(CallTtsPolicy.isPlainSelfRow(1, 0, null, "me", "  "));
  }

  @Test
  public void sendingRequestedAndFailedRowsAreNotFixed() {
    for (int status : new int[] {1, 3, 7}) assertTrue(CallTtsPolicy.isFixedRowStatus(status));
    for (int status : new int[] {2, 4, 5, 6, 8, Integer.MIN_VALUE})
      assertFalse(CallTtsPolicy.isFixedRowStatus(status));
    assertTrue(CallTtsPolicy.isFailedRowStatus(4));
    assertTrue(CallTtsPolicy.isFailedRowStatus(6));
    assertFalse(CallTtsPolicy.isFailedRowStatus(3));
  }

  @Test
  public void targetModesAreIndependentOfOutput() {
    assertTrue(
        CallTtsPolicy.matchesTarget(
            CallTtsSettings.TARGET_ALL, "u1", false, Collections.emptySet()));
    assertTrue(
        CallTtsPolicy.matchesTarget(
            CallTtsSettings.TARGET_SELF, "me", true, Collections.emptySet()));
    assertFalse(
        CallTtsPolicy.matchesTarget(
            CallTtsSettings.TARGET_SELF, "u1", false, Collections.emptySet()));
    assertTrue(
        CallTtsPolicy.matchesTarget(
            CallTtsSettings.TARGET_OTHERS, "u1", false, Collections.emptySet()));
    assertFalse(
        CallTtsPolicy.matchesTarget(
            CallTtsSettings.TARGET_OTHERS, "me", true, Collections.emptySet()));
  }

  @Test
  public void selectedModeMatchesOnlyStoredMid() {
    HashSet<String> selected = new HashSet<>();
    selected.add("u-selected");
    assertTrue(
        CallTtsPolicy.matchesTarget(
            CallTtsSettings.TARGET_SELECTED, "u-selected", false, selected));
    assertFalse(
        CallTtsPolicy.matchesTarget(CallTtsSettings.TARGET_SELECTED, "u-other", false, selected));
  }

  @Test
  public void textIsTrimmedBoundedAndBlankIsIgnored() {
    assertNull(CallTtsPolicy.normalize("   ", 200));
    assertEquals("hello", CallTtsPolicy.normalize("  hello  ", 200));
    assertEquals(20, CallTtsPolicy.normalize("1234567890123456789012345", 1).length());
  }
}
