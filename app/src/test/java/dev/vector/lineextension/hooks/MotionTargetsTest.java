package dev.vector.lineextension.hooks;

import static org.junit.Assert.*;

import org.junit.Test;

public class MotionTargetsTest {
  @Test
  public void enhancedFeedbackDoesNotIncludeCameraOrParty() {
    assertTrue(MotionTargets.isMicOrSpeaker("bottom_action_mute"));
    assertTrue(MotionTargets.isMicOrSpeaker("bottom_action_speaker"));
    assertFalse(MotionTargets.isMicOrSpeaker("bottom_action_video"));
    assertFalse(MotionTargets.isMicOrSpeaker("bottom_action2"));
    assertFalse(MotionTargets.isMicOrSpeaker(null));
  }

  @Test
  public void callControlsHaveFeedback() {
    assertTrue(MotionTargets.isCallControl("bottom_action_mute"));
    assertTrue(MotionTargets.isCallControl("bottom_action_speaker"));
    assertTrue(MotionTargets.isCallControl("voipcall_mute_btn"));
    assertTrue(MotionTargets.isCallControl("voipcall_speaker_btn"));
    assertTrue(MotionTargets.isCallControl("bottom_action0"));
    assertTrue(MotionTargets.isCallControl("bottom_action3"));
  }

  @Test
  public void participantIndicatorsAndUnrelatedControlsAreExcluded() {
    assertFalse(MotionTargets.isCallControl("mute_icon"));
    assertFalse(MotionTargets.isCallControl("grid_user_mute"));
    assertFalse(MotionTargets.isCallControl("button_end"));
    assertFalse(MotionTargets.isCallControl("setting_radio"));
    assertFalse(MotionTargets.isCallControl(null));
  }
}
