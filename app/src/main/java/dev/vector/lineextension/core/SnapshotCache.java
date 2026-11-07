package dev.vector.lineextension.core;

/** Single-flight immutable snapshots; warm reads do not lock, parse, inflate or perform IPC. */
public final class SnapshotCache<T> {
  public interface Loader<T> {
    T load() throws Throwable;
  }

  private volatile T value;

  public T get(Loader<T> loader) throws Throwable {
    T current = value;
    if (current != null) return current;
    synchronized (this) {
      if (value == null) value = loader.load();
      return value;
    }
  }

  public synchronized void set(T updated) {
    value = updated;
  }
}
