package dev.vector.lineextension.versions;

import dev.vector.lineextension.LineVersion;

/** Call targets from the LINE 26.17.1 (261710173) DEX files. */
final class Line26171Calls {
  private Line26171Calls() {}

  static void apply(LineVersion.Config v) {
    // All three entry points retain LINE's system-call and permission checks.
    v.callRestrictions.voicePresenterClass = "kn1.u1";
    v.callRestrictions.videoLauncherClass = "xo1.o";
    v.callRestrictions.videoRequestClass = "w81.a";
    v.callRestrictions.cameraGateClass = "sy0.b0";
    v.callRestrictions.chatCallStateClass = "wi1.b";
    v.callRestrictions.methodChatCallState = "c";
    v.callRestrictions.methodCameraCallState = "c";

    v.callTts.receiveProcessorClass = "ls8.w2";
    v.callTts.sendProcessorClass = "ls8.y3";
    v.callTts.messageClass = "lu8.pd";
    v.classAliases.put("ew7.i", "g18.i");

    v.classAliases.put("vw7.j", "x18.i");
    v.classAliases.put("vw7.e", "x18.e");
    v.classAliases.put("so7.a", "as7.a");

    v.classAliases.put("ps7.a$a$a", "hx7.a$a$a");
    v.classAliases.put("ps7.a$a$b", "hx7.a$a$b");
    v.classAliases.put("ps7.a$b$a", "hx7.a$b$a");
    v.classAliases.put("ps7.a$b$b", "hx7.a$b$b");
  }
}
