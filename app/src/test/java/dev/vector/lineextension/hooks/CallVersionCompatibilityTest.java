package dev.vector.lineextension.hooks;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CallVersionCompatibilityTest {
  @Test
  public void callApiOnlyAcceptsVerified2615Versions() {
    assertTrue(NativeCallBridge.uses2615CallApi("26.15.0"));
    assertTrue(NativeCallBridge.uses2615CallApi("26.15.1"));
    assertFalse(NativeCallBridge.uses2615CallApi("26.14.0"));
    assertFalse(NativeCallBridge.uses2615CallApi("26.15.2"));
    assertFalse(NativeCallBridge.uses2615CallApi(null));
  }
}
