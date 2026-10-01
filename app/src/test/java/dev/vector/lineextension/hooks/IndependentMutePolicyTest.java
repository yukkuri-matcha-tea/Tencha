package dev.vector.lineextension.hooks;

import static org.junit.Assert.*;

import org.junit.Test;

public class IndependentMutePolicyTest {
  @Test
  public void onlyVerifiedMicToggleMayKeepTxAlive() {
    assertTrue(IndependentMutePolicy.keepTxAlive(1, true, true));
    assertFalse(IndependentMutePolicy.keepTxAlive(1, true, false));
    assertFalse(IndependentMutePolicy.keepTxAlive(1, false, true));
    assertFalse(IndependentMutePolicy.keepTxAlive(2, true, true));
    assertFalse(IndependentMutePolicy.keepTxAlive(3, true, true));
    assertFalse(IndependentMutePolicy.keepTxAlive(0, true, true));
  }
}
