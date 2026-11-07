package dev.vector.lineextension.hooks;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class UnsendPolicyTest {
  private boolean retain(String type, String id) {
    return UnsendPolicy.shouldRetain(type, id, "NOTIFIED_DESTROY_MESSAGE", "DESTROY_MESSAGE");
  }

  @Test
  public void retainsCancellationFromAnotherDeviceOrParticipant() {
    assertTrue(retain("NOTIFIED_DESTROY_MESSAGE", "123"));
    assertTrue(retain("DESTROY_MESSAGE", "123"));
  }

  @Test
  public void doesNotInterceptLocalDeletionOrOrdinaryOperations() {
    for (String type :
        new String[] {
          "REMOVE_MESSAGE", "NOTIFIED_READ_MESSAGE", "RECEIVE_MESSAGE", "SEND_MESSAGE", "DUMMY"
        }) {
      assertFalse(retain(type, "123"));
    }
  }

  @Test
  public void rejectsMissingIdentityAndMissingOperationType() {
    assertFalse(retain("NOTIFIED_DESTROY_MESSAGE", null));
    assertFalse(retain("NOTIFIED_DESTROY_MESSAGE", ""));
    assertFalse(retain(null, "123"));
  }
}
