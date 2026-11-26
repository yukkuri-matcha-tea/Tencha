package dev.vector.lineextension.versions;

import dev.vector.lineextension.LineVersion;

/** LINE 26.18.2 (261820153) DEX and resource mappings. */
public final class Version26182 {
  private Version26182() {}

  public static LineVersion.Config create() {
    LineVersion.Config v = Version26171.create();
    v.classAliases.clear();
    v.classAliases.put("com.linecorp.andromeda.core.d", "com.linecorp.andromeda.core.a");
    v.classAliases.put("ew7.i", "b18.i");
    v.classAliases.put("ip8.i0", "ku8.i0");
    v.classAliases.put("ps7.a$a$a", "cx7.a$a$a");
    v.classAliases.put("ps7.a$a$b", "cx7.a$a$b");
    v.classAliases.put("ps7.a$b$a", "cx7.a$b$a");
    v.classAliases.put("ps7.a$b$b", "cx7.a$b$b");
    v.classAliases.put("so7.a", "es7.a");
    v.classAliases.put("vw7.e", "s18.e");
    v.classAliases.put("vw7.j", "s18.i");
    v.classAliases.put("x30.b", "w30.b");

    v.agentIInChat.toggleComposableClass = "op1.j";

    v.announcementFix.announcementEventClass = "ia1.g$d0";
    v.announcementFix.formatterClass = "ou1.b";

    v.callRestrictions.cameraGateClass = "hz0.f0";
    v.callRestrictions.chatCallStateClass = "rj1.c";
    v.callRestrictions.videoLauncherClass = "up1.n";
    v.callRestrictions.videoRequestClass = "s91.a";
    v.callRestrictions.voicePresenterClass = "eo1.h2";

    v.callTts.messageClass = "iu8.pd";
    v.callTts.receiveProcessorClass = "is8.b3";
    v.callTts.sendProcessorClass = "is8.d4";

    v.camera.cameraModuleClass = "ah2.j";

    v.chat.headerController = "im1.u0";
    v.chat.headerHelper = "pn8.g";
    v.chat.methodGetChatId = "t";
    v.chat.searchControllerSearchBoxMethod = "v0";
    v.chat.searchHeaderHelperClass = "d12.c0";
    v.chat.searchKeywordEventClass = "c12.b";
    v.chat.searchKeywordTypeClass = "sb1.c";
    v.chat.searchPresenterClass = "h12.p";
    v.chat.searchResultClass = "sb1.h";
    v.chat.searchResultTitleViewHolderClass = "k12.h";
    v.chat.searchResultWrapperClass = "sb1.i";

    v.chatEditSelectAll.methodGetItem = "t0";
    v.chatEditSelectAll.methodGetSelectedIds = "e";
    v.chatEditSelectAll.methodGetSelectionState = "f0";
    v.chatEditSelectAll.selectionProviderClass = "if1.e";
    v.chatEditSelectAll.selectionStateClass = "if1.f";

    v.chatHeader.fieldAppInfoPkg = "od1.a";
    v.chatHeader.fieldChatConfigChatId = "mh1.a";
    v.chatHeader.fieldChatConfigIsMuted = "kh1.a";
    v.chatHeader.fieldChatConfigType = "im1.i0";

    v.chatListMoreMenu.clickListenerClass = "g62.a";

    v.chatTimestamp.displayTimeInterface = "qf1.f";

    v.compose.clickableClass = "u1.h0";
    v.compose.composerClass = "j3.r";
    v.compose.onGloballyPositionedClass = "z4.u1";

    v.home26NavIcon.rendererClass = "jp2.n";
    v.home26NavIcon.agentDrawableId = 0x7f080bb5;
    v.home26NavIcon.settingsDrawableId = 0x7f0812c5;

    v.homeTab.tabListProviderClass = "zh8.b";

    v.imageQuality.qualityProfileHighClass = "xr8.a$b$a";
    v.imageQuality.qualityProfileMediumClass = "xr8.a$b$b";

    v.main.headerButtonTypeClass = "pn8.e";
    v.main.headerInterfaceA = "pn8.c";

    v.media.droppedMediaPreprocessorClass = "b41.b";
    v.media.videoDurationCheckClass = "ah1.b";
    v.media.videoDurationSuccessClass = "bh1.a$c";

    v.messageContextMenu.dialogClass = "rn1.n1";
    v.messageContextMenu.showCoroutineClass = "rn1.p1";
    v.messageContextMenu.textParamsClass = "u91.a$e";

    v.messageEditHistory.editRequestClass = "lm8.h";
    v.messageEditHistory.menuActionLambdaClass = "fe1.f$b";
    v.messageEditHistory.menuItemEnumClass = "ge1.c";
    v.messageEditHistory.menuListBuilderClass = "rn1.d2";
    v.messageEditHistory.menuPresentationEnumClass = "rn1.e1";

    v.nightMode.darkThemeManagerClass = "jk6.i";
    v.nightMode.methodThemeMode = "A";
    v.nightMode.nightModeConfiguratorClass = "u60.a";

    v.notificationFix.firebaseAppClass = "tx.e";
    v.notificationFix.firebaseBindDeliveryClass = "hz.q1";
    v.notificationFix.firebaseCompletedTaskClass = "rt.m";
    v.notificationFix.firebaseDispatcherClass = "hz.n";
    v.notificationFix.firebaseQueueClass = "hz.a1";
    v.notificationFix.firebaseReceiverEnvelopeClass = "fr.a";
    v.notificationFix.firebaseRemoteMessageClass = "hz.v0";
    v.notificationFix.firebaseWakefulStartClass = "hz.l1";
    v.notificationFix.fisCertDigestClass = "rr.a";
    v.notificationFix.gmsAvailabilityClass = "gr.j";
    v.notificationFix.gmsSignatureCheckClass = "gr.k";
    v.notificationFix.legyDisconnectRunnableClass = "na0.h";
    v.notificationFix.legyStreamingLifecycleMethod = "g1";
    v.notificationFix.lineFcmServiceBaseClass = "hz.i";

    v.plusMenu.plusMenuCallbackClass = "bv8.a";
    v.plusMenu.plusMenuComponentClass = "z71.m";
    v.plusMenu.plusMenuComposerImplClass = "j3.c1";
    v.plusMenu.plusMenuOnClickItemClass = "bv8.l";

    v.profile.fieldH3 = "n4";
    v.profile.g50aClass = "mc0.a";
    v.profile.g50fClass = "mc0.f";
    v.profile.h13baClass = "hs3.b";

    v.profileViewer.analyticsClass = "zx6.d";
    v.profileViewer.baseOwnProfileField = "l";
    v.profileViewer.decoControllerClass = "ex6.k";
    v.profileViewer.decoEditorControllerClass = "ex6.q0";
    v.profileViewer.editorDeleteButtonField = "b";
    v.profileViewer.editorEditButtonField = "c";
    v.profileViewer.mediaValueClass = "ux6.y$e";
    v.profileViewer.methodGetDecorationView = "i";
    v.profileViewer.methodResetDecorations = "n7";
    v.profileViewer.methodSaveDecorations = "v7";
    v.profileViewer.methodSelectDecoration = "z7";
    v.profileViewer.methodSelectedDecoration = "p7";
    v.profileViewer.methodSetEditMode = "C7";
    v.profileViewer.popupClass = "nx6.b";
    v.profileViewer.saveDecorationsParamClass = "vn6.g";

    v.readReceipt.readReceiptManagerClass = "xj3.e";
    // Explicit Mark as Read actions in the chat list and chat sub-tabs.
    v.readReceipt.manualReadCallerClasses = new String[] {"a72.i", "a72.d", "t72.i", "t72.j"};

    v.res.idArrow = 0x7f0b2181;
    v.res.idDesc = 0x7f0b2199;
    v.res.idHeader = 0x7f0b10b7;
    v.res.idIcon = 0x7f0b21a7;
    v.res.idMark = 0x7f0b21b7;
    v.res.idNewMark = 0x7f0b187a;
    v.res.idNoticeDot = 0x7f0b18e7;
    v.res.idPersonalInfo = 0x7f153b57;
    v.res.idSeparator = 0x7f0b21e0;
    v.res.idSettingList = 0x7f0b21b3;
    v.res.idTimestamp = 0x7f0b0856;
    v.res.idTitle = 0x7f0b21e9;
    v.res.layoutCheckbox = 0x7f0e0519;
    v.res.layoutSectionHeader = 0x7f0e0525;
    v.res.layoutSettingsMain = 0x7f0e051f;
    v.res.typeRow = 0x7f0e0528;
    v.res.typeSection = 0x7f0e0525;

    v.searchBarAgentI.homeSearchBarClass = "qb5.h";
    v.searchBarAgentI.talkClickMethod = "t";
    v.searchBarAgentI.homeAiContainerId = 0x7f0b15c7;
    v.searchBarAgentI.homeGuidelineId = 0x7f0b15c9;

    v.settings.methodProxyGetItemType = "h";
    v.settings.settingsAdapterClass = "wj8.f";
    v.settings.settingsAdapterWrapperClass = "qe5.a";
    v.settings.settingsBaseAdapterClass = "wj8.f$b";
    v.settings.settingsHeaderItemClass = "re5.u";
    v.settings.settingsItemClass = "wj8.f$c";
    v.settings.settingsRowItemClass = "re5.y";
    v.settings.settingsSearchHelperClass = "rj5.b";

    v.talkTabHeader.chatTabHeaderStateClass = "a82.f";
    v.talkTabHeader.iconTypeClass = "x71.m";

    v.thrift.v1 = "X0";

    v.unsend.appInfoProviderClass = "kr8.d";
    v.unsend.chatMessageViewHolderClass = "gs1.z";
    v.unsend.chatServiceConfigClass = "ld5.p";
    v.unsend.destroyResultClass = "hs8.a$a$c";
    v.unsend.notifiedDestroyMessageHandlerClass = "is8.a1";
    v.unsend.notifiedReadMessageHandlerClass = "is8.a2";
    v.unsend.notifiedSendReactionHandlerClass = "is8.l2";
    v.unsend.operationClass = "iu8.ee";
    v.unsend.unsendDestroyHandlerClass = "is8.a1";
    return v;
  }
}
