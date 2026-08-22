package dev.vector.lineextension;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import dev.vector.lineextension.versions.Version26130;
import org.junit.Test;

public class Version26130Test {
  @Test
  public void criticalReadAndUnsendMappingsStayPinned() {
    LineVersion.Config config = Version26130.create();
    assertEquals("u83.e", config.readReceipt.readReceiptManagerClass);
    assertEquals("d", config.readReceipt.methodSendReadReceipt);
    assertEquals("Y0", config.thrift.v1);
    assertEquals("te8.y1", config.unsend.notifiedReadMessageHandlerClass);
    assertEquals("te8.b1", config.unsend.notifiedDestroyMessageHandlerClass);
    assertEquals("rg8.de", config.unsend.operationClass);
  }

  @Test
  public void criticalUiAndComposeMappingsArePresent() {
    LineVersion.Config config = Version26130.create();
    assertFalse(config.settings.mainSettingsFragmentClass.isEmpty());
    assertEquals("h3.r", config.compose.composerClass);
    assertEquals("u1.k0", config.compose.clickableClass);
    assertFalse(config.iab.inAppBrowserActivityClass.isEmpty());
    assertFalse(config.chatTimestamp.displayTimeInterface.isEmpty());
  }
}
