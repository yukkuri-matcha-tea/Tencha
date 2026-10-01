package dev.vector.lineextension;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

public class VectorConfigTest {
  @Test
  public void retiredCallControlsAreNotRegisteredEvenForStoredLegacyKeys() {
    for (VectorConfig.Item item : new VectorConfig().items) {
      assertFalse("call_mic_meter".equals(item.key));
      assertFalse("single_peer_volume".equals(item.key));
    }
  }

  @Test
  public void independentCallMuteIsOptInAndWarnsAboutRemoteDisplay() {
    VectorConfig config = new VectorConfig();
    assertFalse(config.independentCallMute.enabled);
    assertTrue(config.independentCallMute.category == VectorConfig.Category.CHAT);
    assertTrue(config.independentCallMute.section.equals(config.callTts.section));
    assertTrue(config.independentCallMute.description.contains("相手からはミュートしていない"));
    assertTrue(config.independentCallMute.description.contains("再起動"));
    assertTrue(config.independentCallMute.description.contains("OFFではLINE本来"));
  }

  @Test
  public void featureKeysAreUniqueAndValidForProvider() {
    VectorConfig config = new VectorConfig();
    Set<String> keys = new HashSet<>();
    for (VectorConfig.Item item : config.items) {
      assertTrue(item.key.matches("[a-z0-9_]{1,64}"));
      assertTrue("duplicate feature key: " + item.key, keys.add(item.key));
    }
  }

  @Test
  public void behaviorChangingFeaturesAreOptIn() {
    VectorConfig config = new VectorConfig();
    assertFalse(config.preventMarkAsRead.enabled);
    assertFalse(config.preventUnsendMessage.enabled);
    assertFalse(config.experimentalFcmFix.enabled);
    assertFalse(config.spoofVersion.enabled);
    assertFalse(config.fixSignatureMismatch.enabled);
    assertFalse(config.longVideo.enabled);
  }

  @Test
  public void riskyFeaturesAreGroupedBehindDeveloperMode() {
    VectorConfig config = new VectorConfig();
    assertFalse(config.developerMode.enabled);
    assertTrue(config.developerMode.category == VectorConfig.Category.DEVELOPER);
    assertTrue(config.showProfileTimestamps.category == VectorConfig.Category.DEVELOPER);
    assertTrue(config.experimentalFcmFix.category == VectorConfig.Category.DEVELOPER);
    assertTrue(config.lineForegroundKeepAlive.category == VectorConfig.Category.DEVELOPER);
    assertTrue(config.spoofVersion.category == VectorConfig.Category.DEVELOPER);
    assertTrue(config.fixSignatureMismatch.category == VectorConfig.Category.DEVELOPER);
  }
}
