package dev.vector.lineextension.core;

/** Per-process budgets: device RAM must never be confused with this app's heap limit. */
public final class DeviceProfile {
  public final long totalRamBytes;
  public final long heapLimitBytes;
  public final int cpuCores;
  public final boolean lowRam;

  public DeviceProfile(long ram, long heap, int cores, boolean lowRamDevice) {
    totalRamBytes = Math.max(0, ram);
    heapLimitBytes = heap > 0 ? heap : 32L * 1024 * 1024;
    cpuCores = Math.max(1, cores);
    lowRam = lowRamDevice || (ram > 0 && ram <= 2L * 1024 * 1024 * 1024);
  }

  public long pcmCacheBytes() {
    long budget = Math.min(16L * 1024 * 1024, heapLimitBytes / (lowRam ? 64 : 32));
    return Math.max(1, budget);
  }

  public int ioWorkers() {
    return lowRam || cpuCores < 4 ? 1 : 2;
  }

  public int nameCacheEntries() {
    return lowRam ? 128 : 512;
  }
}
