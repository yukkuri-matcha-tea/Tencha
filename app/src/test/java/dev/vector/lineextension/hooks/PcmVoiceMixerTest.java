package dev.vector.lineextension.hooks;

import static org.junit.Assert.*;

import org.junit.Test;

public class PcmVoiceMixerTest {
  @Test
  public void tapsOverlapWithoutChangingTheSource() {
    PcmVoiceMixer mixer = new PcmVoiceMixer();
    short[] source = {100, 200, 300};
    mixer.enqueue(source);
    short[] frame = new short[1];
    mixer.render(frame);
    assertEquals(100, frame[0]);
    mixer.enqueue(source);
    mixer.render(frame);
    assertEquals(300, frame[0]);
    assertArrayEquals(new short[] {100, 200, 300}, source);
  }

  @Test
  public void repeatedTapsClampOnceAndFinish() {
    PcmVoiceMixer mixer = new PcmVoiceMixer();
    short[] source = {30000, -30000};
    for (int i = 0; i < 1000; i++) mixer.enqueue(source);
    short[] frame = new short[4];
    assertEquals(2, mixer.render(frame));
    assertArrayEquals(new short[] {32767, -32768, 0, 0}, frame);
    assertTrue(mixer.isEmpty());
  }

  @Test
  public void clearDoesNotReplayPendingSamples() {
    PcmVoiceMixer mixer = new PcmVoiceMixer();
    mixer.enqueue(new short[] {1, 2, 3});
    mixer.clear();
    short[] frame = {9, 9};
    assertEquals(0, mixer.render(frame));
    assertArrayEquals(new short[] {0, 0}, frame);
  }
}
