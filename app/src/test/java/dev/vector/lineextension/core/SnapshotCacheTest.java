package dev.vector.lineextension.core;

import static org.junit.Assert.*;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public class SnapshotCacheTest {
  @Test
  public void twentyThousandWarmReadsOnlyLoadOnce() throws Throwable {
    SnapshotCache<String> cache = new SnapshotCache<>();
    AtomicInteger loads = new AtomicInteger();
    for (int i = 0; i < 20_000; i++)
      assertEquals(
          "stored",
          cache.get(
              () -> {
                loads.incrementAndGet();
                return "stored";
              }));
    assertEquals(1, loads.get());
  }

  @Test
  public void settingNewSnapshotIsImmediatelyVisible() throws Throwable {
    SnapshotCache<String> cache = new SnapshotCache<>();
    cache.set("old");
    cache.set("new");
    assertEquals(
        "new",
        cache.get(
            () -> {
              throw new AssertionError("Unexpected disk read");
            }));
  }

  @Test
  public void missingOrFailedStorageIsNotCachedForever() throws Throwable {
    SnapshotCache<String> cache = new SnapshotCache<>();
    assertNull(cache.get(() -> null));
    try {
      cache.get(
          () -> {
            throw new IllegalStateException("provider unavailable");
          });
    } catch (IllegalStateException expected) {
      /* The next access can retry. */
    }
    assertEquals("available", cache.get(() -> "available"));
  }

  @Test
  public void concurrentInitialReadsShareOneLoad() throws Exception {
    SnapshotCache<String> cache = new SnapshotCache<>();
    AtomicInteger loads = new AtomicInteger();
    AtomicInteger failures = new AtomicInteger();
    Thread[] threads = new Thread[8];
    for (int i = 0; i < threads.length; i++) {
      threads[i] =
          new Thread(
              () -> {
                try {
                  if (!"ok"
                      .equals(
                          cache.get(
                              () -> {
                                loads.incrementAndGet();
                                return "ok";
                              }))) failures.incrementAndGet();
                } catch (Throwable error) {
                  failures.incrementAndGet();
                }
              });
      threads[i].start();
    }
    for (Thread thread : threads) thread.join();
    assertEquals(0, failures.get());
    assertEquals(1, loads.get());
  }
}
