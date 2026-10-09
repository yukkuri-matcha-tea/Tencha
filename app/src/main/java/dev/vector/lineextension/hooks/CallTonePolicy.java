package dev.vector.lineextension.hooks;

/** Source selection without Android dependencies, shared by both ringback factories. */
final class CallTonePolicy {
  enum Source {
    CUSTOM,
    DEFAULT,
    ORIGINAL
  }

  static Source ringback(
      boolean customEnabled, boolean fileAvailable, boolean forceDefault, boolean remote) {
    if (customEnabled && fileAvailable) return Source.CUSTOM;
    if (forceDefault && remote) return Source.DEFAULT;
    return Source.ORIGINAL;
  }
}
