package dev.vector.lineextension.hooks;

import static org.junit.Assert.*;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;

public class CallRestrictionScopeTest {
  @Test
  public void bypassIsOffOutsideMediaEntry() {
    assertFalse(CallRestrictionScope.active());
    try (CallRestrictionScope scope = CallRestrictionScope.enter()) {
      assertTrue(CallRestrictionScope.active());
    }
    assertFalse(CallRestrictionScope.active());
  }

  @Test
  public void nestedCallsKeepOuterScope() {
    try (CallRestrictionScope outer = CallRestrictionScope.enter()) {
      try (CallRestrictionScope inner = CallRestrictionScope.enter()) {
        assertTrue(CallRestrictionScope.active());
      }
      assertTrue(CallRestrictionScope.active());
    }
    assertFalse(CallRestrictionScope.active());
  }

  @Test
  public void failureDoesNotLeaveCallStateBypassed() {
    try {
      try (CallRestrictionScope scope = CallRestrictionScope.enter()) {
        throw new IllegalStateException("media startup failed");
      }
    } catch (IllegalStateException expected) {
      assertFalse(CallRestrictionScope.active());
    }
  }

  @Test
  public void audioThreadRetainsRealCallState() throws Exception {
    AtomicBoolean active = new AtomicBoolean(true);
    try (CallRestrictionScope scope = CallRestrictionScope.enter()) {
      Thread audio = new Thread(() -> active.set(CallRestrictionScope.active()));
      audio.start();
      audio.join();
      assertFalse(active.get());
    }
  }

  @Test
  public void duplicateCloseDoesNotEndOuterScope() {
    try (CallRestrictionScope outer = CallRestrictionScope.enter()) {
      CallRestrictionScope inner = CallRestrictionScope.enter();
      inner.close();
      inner.close();
      assertTrue(CallRestrictionScope.active());
    }
  }
}
