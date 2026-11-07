package dev.vector.lineextension.hooks;

final class UnsendPolicy {
  private UnsendPolicy() {}

  static boolean shouldRetain(String type, String messageId, String notifiedType, String ownType) {
    return messageId != null
        && !messageId.isEmpty()
        && type != null
        && (type.equals(notifiedType) || type.equals(ownType));
  }
}
