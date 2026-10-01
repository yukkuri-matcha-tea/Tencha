package dev.vector.lineextension.hooks;

/** Only replace the verified TX mute invoked inside AudioControl's mic toggle. */
final class IndependentMutePolicy {
  private IndependentMutePolicy() {}

  static boolean keepTxAlive(int streamId, boolean requestedMute, boolean micGateReady) {
    // AudioStream direction enum: TX=1, RX=2, TXRX=3 in LINE 26.14/26.15.
    return streamId == 1 && requestedMute && micGateReady;
  }
}
