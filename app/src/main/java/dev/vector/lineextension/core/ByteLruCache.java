package dev.vector.lineextension.core;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.ToLongFunction;

/** A byte-bounded cache. Never recycle values that may still be in use by a player. */
public final class ByteLruCache<K, V> {
  private final LinkedHashMap<K, V> values = new LinkedHashMap<>(16, .75f, true);
  private final ToLongFunction<V> weigh;
  private long limit;
  private long bytes;

  public ByteLruCache(long limit, ToLongFunction<V> weigh) {
    this.limit = Math.max(0, limit);
    this.weigh = weigh;
  }

  public synchronized V get(K key) {
    return values.get(key);
  }

  public synchronized void put(K key, V value) {
    V previous = values.remove(key);
    if (previous != null) bytes -= weigh.applyAsLong(previous);
    long weight = weigh.applyAsLong(value);
    if (weight <= 0 || weight > limit) return;
    while (bytes > limit - weight && !values.isEmpty()) {
      Map.Entry<K, V> eldest = values.entrySet().iterator().next();
      bytes -= weigh.applyAsLong(eldest.getValue());
      values.remove(eldest.getKey());
    }
    values.put(key, value);
    bytes += weight;
    trim();
  }

  public synchronized void resize(long size) {
    limit = Math.max(0, size);
    trim();
  }

  private void trim() {
    while (bytes > limit && !values.isEmpty()) {
      Map.Entry<K, V> eldest = values.entrySet().iterator().next();
      bytes -= weigh.applyAsLong(eldest.getValue());
      values.remove(eldest.getKey());
    }
  }

  public synchronized void clear() {
    values.clear();
    bytes = 0;
  }

  public synchronized long bytes() {
    return bytes;
  }
}
