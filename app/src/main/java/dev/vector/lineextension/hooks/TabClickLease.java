package dev.vector.lineextension.hooks;

/** A delayed UI callback must not turn a later activity restoration into a tab click. */
final class TabClickLease {
  private long generation;
  private long startedAt;
  private boolean pending;

  long begin(long now) {
    startedAt = now;
    pending = true;
    return ++generation;
  }

  boolean consume(long now) {
    boolean valid = pending && now >= startedAt && now - startedAt <= 250L;
    pending = false;
    return valid;
  }

  void cancel(long token) {
    if (token == generation) pending = false;
  }

  void cancel() {
    pending = false;
    generation++;
  }
}
