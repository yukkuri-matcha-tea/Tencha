package dev.vector.lineextension.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;

public class FeatureSupervisorTest {
  @Test
  public void independentHooksDoNotShareSafeModeBuckets() {
    assertNotEquals(
        FeatureSupervisor.idForName("RemoveAds"),
        FeatureSupervisor.idForName("RemoveHomeContents"));
    assertNotEquals(
        FeatureSupervisor.idForName("ImageQuality"), FeatureSupervisor.idForName("LongVideoHook"));
    assertNotEquals(
        FeatureSupervisor.idForName("FcmFixHook"),
        FeatureSupervisor.idForName("LineForegroundKeepAliveHook"));
  }

  @Test
  public void runtimeReporterIdsMatchInstallBuckets() {
    assertEquals("media_quality", FeatureSupervisor.idForName("ImageQuality"));
    assertEquals("long_video", FeatureSupervisor.idForName("LongVideoHook"));
    assertEquals("search_enhancement", FeatureSupervisor.idForName("SearchMin1CharHook"));
    assertEquals("read_block", FeatureSupervisor.idForName("ReadReceiptHandler"));
  }
}
