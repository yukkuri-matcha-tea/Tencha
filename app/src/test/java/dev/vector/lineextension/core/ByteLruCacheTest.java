package dev.vector.lineextension.core;

import static org.junit.Assert.*;

import org.junit.Test;

public class ByteLruCacheTest {
  @Test
  public void recentlyUsedValueSurvivesEviction() {
    ByteLruCache<String, byte[]> cache = new ByteLruCache<>(4, value -> value.length);
    byte[] first = {1, 2};
    cache.put("a", first);
    cache.put("b", new byte[2]);
    assertSame(first, cache.get("a"));
    cache.put("c", new byte[2]);
    assertNull(cache.get("b"));
    assertSame(first, cache.get("a"));
    assertEquals(4, cache.bytes());
  }

  @Test
  public void oversizedClipIsNotRetainedOrTruncated() {
    ByteLruCache<String, short[]> cache = new ByteLruCache<>(4, value -> value.length * 2L);
    short[] playing = new short[100];
    cache.put("clip", playing);
    assertNull(cache.get("clip"));
    assertEquals(100, playing.length);
    assertEquals(0, cache.bytes());
  }

  @Test
  public void trimAndClearDoNotMutatePlayingPcm() {
    ByteLruCache<String, short[]> cache = new ByteLruCache<>(8, value -> value.length * 2L);
    short[] playing = {3, 4};
    cache.put("a", playing);
    cache.resize(0);
    assertNull(cache.get("a"));
    cache.clear();
    assertArrayEquals(new short[] {3, 4}, playing);
    assertEquals(0, cache.bytes());
  }

  @Test
  public void replacingValueUpdatesItsWeight() {
    ByteLruCache<String, byte[]> cache = new ByteLruCache<>(8, value -> value.length);
    cache.put("a", new byte[8]);
    cache.put("a", new byte[2]);
    assertEquals(2, cache.bytes());
  }

  @Test
  public void sizeAccountingCannotOverflow() {
    ByteLruCache<String, Long> cache = new ByteLruCache<>(Long.MAX_VALUE, value -> value);
    cache.put("a", Long.MAX_VALUE - 1);
    cache.put("b", 4L);
    assertNull(cache.get("a"));
    assertEquals(4, cache.bytes());
  }
}
