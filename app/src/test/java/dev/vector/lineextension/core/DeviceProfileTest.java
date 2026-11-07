package dev.vector.lineextension.core;

import static org.junit.Assert.*;

import org.junit.Test;

public class DeviceProfileTest {
  private static final long M = 1024L * 1024;

  @Test
  public void largeDeviceRamDoesNotOverrideSmallAppHeap() {
    DeviceProfile p = new DeviceProfile(32 * 1024 * M, 64 * M, 16, false);
    assertEquals(2 * M, p.pcmCacheBytes());
  }

  @Test
  public void lowRamDevicesUseConservativeBudgets() {
    DeviceProfile p = new DeviceProfile(2 * 1024 * M, 128 * M, 8, false);
    assertTrue(p.lowRam);
    assertEquals(2 * M, p.pcmCacheBytes());
    assertEquals(1, p.ioWorkers());
    assertEquals(128, p.nameCacheEntries());
  }

  @Test
  public void highEndHeapStillHasAFiniteCacheCap() {
    DeviceProfile p = new DeviceProfile(16 * 1024 * M, 2048 * M, 8, false);
    assertEquals(16 * M, p.pcmCacheBytes());
    assertEquals(2, p.ioWorkers());
  }

  @Test
  public void unavailableValuesHaveSafeFallbacks() {
    DeviceProfile p = new DeviceProfile(-1, 0, 0, false);
    assertEquals(1, p.cpuCores);
    assertEquals(1, p.ioWorkers());
    assertTrue(p.pcmCacheBytes() <= p.heapLimitBytes / 32);
  }
}
