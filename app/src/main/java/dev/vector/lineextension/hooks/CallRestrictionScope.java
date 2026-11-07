package dev.vector.lineextension.hooks;

/** Bypass only an explicit media-entry guard, never the real session or an audio thread. */
final class CallRestrictionScope implements AutoCloseable {
  private static final ThreadLocal<Integer> depth = new ThreadLocal<>();
  private boolean closed;

  private CallRestrictionScope() {
    Integer current = depth.get();
    depth.set(current == null ? 1 : current + 1);
  }

  static CallRestrictionScope enter() {
    return new CallRestrictionScope();
  }

  static boolean active() {
    Integer current = depth.get();
    return current != null && current > 0;
  }

  @Override
  public void close() {
    if (closed) return;
    closed = true;
    Integer current = depth.get();
    if (current == null || current <= 1) depth.remove();
    else depth.set(current - 1);
  }
}
