package dev.vector.lineextension.hooks;

import java.util.Set;

final class CallTtsPolicy {
  private CallTtsPolicy() {}

  // ek8.a: type=MESSAGE(1), attachement_type=NONE(0). FIXED status is 3 with
  // backward-compatible values 1/7; SENDING=2/5, REQUESTED=8, FAILED=4/6.
  static boolean isPlainSelfRow(
      Integer type, Integer attachmentType, String senderMid, String myMid, String content) {
    return type != null
        && type == 1
        && attachmentType != null
        && attachmentType == 0
        && (senderMid == null || senderMid.isEmpty() || senderMid.equals(myMid))
        && content != null
        && !content.trim().isEmpty();
  }

  static boolean isFixedRowStatus(int status) {
    return status == 3 || status == 1 || status == 7;
  }

  static boolean isFailedRowStatus(int status) {
    return status == 4 || status == 6;
  }

  static boolean matchesTarget(
      String target, String senderMid, boolean fromSelf, Set<String> selectedMids) {
    if (CallTtsSettings.TARGET_SELF.equals(target)) return fromSelf;
    if (CallTtsSettings.TARGET_OTHERS.equals(target)) return !fromSelf;
    if (CallTtsSettings.TARGET_SELECTED.equals(target))
      return senderMid != null && selectedMids.contains(senderMid);
    return true;
  }

  static String normalize(String text, int maxChars) {
    if (text == null) return null;
    String clean = text.trim();
    if (clean.isEmpty()) return null;
    int max = Math.max(20, Math.min(500, maxChars));
    return clean.length() > max ? clean.substring(0, max) : clean;
  }
}
