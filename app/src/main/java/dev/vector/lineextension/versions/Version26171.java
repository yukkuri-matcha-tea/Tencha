package dev.vector.lineextension.versions;

import dev.vector.lineextension.LineVersion;

/** Mappings for LINE 26.17.1 (261710173), checked against the installed APK. */
public final class Version26171 {
  private Version26171() {}

  public static LineVersion.Config create() {
    LineVersion.Config v = Version26160.create();
    v.classAliases.clear();
    v.classAliases.put("ip8.i0", "nu8.i0");

    // ji5.b binds ak8.f$b, and id5.a wraps the concrete jd5 settings models.
    v.settings.settingsAdapterClass = "ak8.f";
    v.settings.settingsItemClass = "ak8.f$c";
    v.settings.settingsBaseAdapterClass = "ak8.f$b";
    v.settings.settingsSearchHelperClass = "ji5.b";
    v.settings.settingsAdapterWrapperClass = "id5.a";
    v.settings.settingsHeaderItemClass = "jd5.v";
    v.settings.settingsRowItemClass = "jd5.a0";
    v.settings.settingsHandlerBaseClass = "";
    v.settings.methodProxyGetItemType = "d";

    v.res.idSettingList = 0x7f0b2240;
    v.res.idPersonalInfo = 0x7f153ada;
    v.res.typeSection = 0x7f0e0526;
    v.res.typeRow = 0x7f0e0529;
    v.res.idIcon = 0x7f0b2234;
    v.res.idDesc = 0x7f0b2226;
    v.res.idMark = 0x7f0b2244;
    v.res.idSeparator = 0x7f0b226d;
    v.res.idArrow = 0x7f0b220e;
    v.res.idNewMark = 0x7f0b18c6;
    v.res.idNoticeDot = 0x7f0b1935;
    v.res.idTitle = 0x7f0b2276;
    v.res.layoutCheckbox = 0x7f0e051a;
    v.res.layoutSectionHeader = 0x7f0e0526;
    v.res.layoutSettingsMain = 0x7f0e0520;
    v.res.idHeader = 0x7f0b10f5;

    v.plusMenu.plusMenuComponentClass = "d71.t";
    v.plusMenu.plusMenuComposerImplClass = "j3.b1";
    v.plusMenu.plusMenuCallbackClass = "ev8.a";
    v.plusMenu.plusMenuOnClickItemClass = "ev8.l";
    // d71.t.a renders Edit Chats through the icon row c(), not the shortcut tile b().
    v.plusMenu.methodCreateMenu = "c";
    // ChatListFragment's popup attaches this listener after adding its native rows.
    v.chatListMoreMenu.clickListenerClass = "z42.a";
    v.home26NavIcon.rendererClass = "eo2.n";
    v.home26NavIcon.agentDrawableId = 0x7f080ba6;
    v.home26NavIcon.settingsDrawableId = 0x7f0812b2;
    v.compose.composerClass = "j3.q";
    v.compose.onGloballyPositionedClass = "z4.v1";
    v.compose.layoutCoordinatesClass = "z4.c0";

    v.talkTabHeader.chatTabHeaderStateClass = "t62.e";
    v.talkTabHeader.iconTypeClass = "b71.q";
    v.searchBarAgentI.homeSearchBarClass = "ia5.i";
    v.searchBarAgentI.talkClickMethod = "u";
    v.searchBarAgentI.homeAiContainerId = 0x7f0b160e;
    v.searchBarAgentI.homeGuidelineId = 0x7f0b1610;
    v.agentIInChat.toggleComposableClass = "ro1.i";
    // The old timestamp preference has no remaining reader in the current DEX.
    v.aiIcon.repoClass = "";
    v.aiIcon.rendererMethod = "b";
    v.homeTab.tabListProviderClass = "di8.f";
    v.nightMode.nightModeConfiguratorClass = "w60.a";
    v.nightMode.darkThemeManagerClass = "zi6.h";
    v.nightMode.methodThemeMode = "z";

    v.notificationFix.lineFcmServiceBaseClass = "ez.i";
    v.notificationFix.lineFcmDispatchMethod = "c";
    v.notificationFix.lineFcmOwnershipMethod = "f";
    v.notificationFix.lineFcmTokenMethod = "d";
    v.notificationFix.firebaseRemoteMessageClass = "ez.u0";
    v.notificationFix.firebaseReceiverEnvelopeClass = "ar.a";
    v.notificationFix.firebaseDispatcherClass = "ez.n";
    v.notificationFix.firebaseBindDeliveryClass = "ez.p1";
    v.notificationFix.firebaseMessagingHandleMethod = "b";
    v.notificationFix.firebaseWakefulStartClass = "ez.k1";
    v.notificationFix.firebaseCompletedTaskClass = "nt.m";
    v.notificationFix.firebaseAppClass = "qx.e";
    v.notificationFix.firebaseAppGetInstanceMethod = "d";
    v.notificationFix.firebaseMessagingTokenFreshMethod = "h";
    v.notificationFix.firebaseQueueClass = "ez.z0";
    v.notificationFix.fisCertDigestClass = "mr.a";
    v.notificationFix.gmsSignatureCheckClass = "br.k";
    v.notificationFix.gmsAvailabilityClass = "br.j";
    v.notificationFix.legyStreamingLifecycleMethod = "f1";
    v.notificationFix.legyDisconnectRunnableClass = "qa0.h";
    v.notificationFix.legyStateField = "r";
    v.notificationFix.legyTimeoutField = "t";
    v.notificationFix.legyRunnableField = "u";
    v.notificationFix.legyBackgroundWorkerFlagField = "v";

    Line26171Chat.apply(v);
    Line26171Calls.apply(v);
    Line26171ProfileMedia.apply(v);
    return v;
  }
}
