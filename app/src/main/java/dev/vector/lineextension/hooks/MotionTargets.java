package dev.vector.lineextension.hooks;

final class MotionTargets {
  private MotionTargets() {}

  static boolean isMicOrSpeaker(String resource) {
    return "bottom_action_mute".equals(resource)
        || "bottom_action_speaker".equals(resource)
        || "voipcall_mute_btn".equals(resource)
        || "voipcall_speaker_btn".equals(resource)
        || "preview_mute".equals(resource)
        || "button_mic".equals(resource);
  }

  static boolean isCallControl(String resource) {
    return "bottom_action_mute".equals(resource)
        || "bottom_action_speaker".equals(resource)
        || "bottom_action_video".equals(resource)
        || "bottom_action0".equals(resource)
        || "bottom_action1".equals(resource)
        || "bottom_action2".equals(resource)
        || "bottom_action3".equals(resource)
        || "voipcall_mute_btn".equals(resource)
        || "voipcall_speaker_btn".equals(resource)
        || "preview_mute".equals(resource)
        || "button_mic".equals(resource);
  }
}
