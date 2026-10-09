package dev.vector.lineextension.hooks;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class CallTonePolicyTest {
  @Test
  public void validCustomToneWinsForBothSources() {
    for (boolean remote : new boolean[] {false, true})
      for (boolean force : new boolean[] {false, true})
        assertEquals(
            CallTonePolicy.Source.CUSTOM, CallTonePolicy.ringback(true, true, force, remote));
  }

  @Test
  public void unavailableOrDisabledCustomToneUsesNormalFallback() {
    for (boolean remote : new boolean[] {false, true})
      for (boolean force : new boolean[] {false, true})
        for (boolean enabled : new boolean[] {false, true}) {
          assertEquals(
              force && remote ? CallTonePolicy.Source.DEFAULT : CallTonePolicy.Source.ORIGINAL,
              CallTonePolicy.ringback(enabled, false, force, remote));
          if (!enabled)
            assertEquals(
                force && remote ? CallTonePolicy.Source.DEFAULT : CallTonePolicy.Source.ORIGINAL,
                CallTonePolicy.ringback(false, true, force, remote));
        }
  }

  @Test
  public void defaultSourceNeverRedirectsToItself() {
    assertEquals(CallTonePolicy.Source.ORIGINAL, CallTonePolicy.ringback(true, false, true, false));
  }
}
