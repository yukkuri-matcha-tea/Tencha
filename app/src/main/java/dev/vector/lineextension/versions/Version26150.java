package dev.vector.lineextension.versions;

import dev.vector.lineextension.LineVersion;

/** Exact mappings verified against LINE 26.15.0 (versionCode 261500177). */
public final class Version26150 {
  private Version26150() {}

  public static LineVersion.Config create() {
    LineVersion.Config v = Version26140.create();

    // y13.a obtains the account profile through this service key. cp3.a.toString()
    // labels b as mid and h as name; s70.g/dh3.b were unrelated/removed in 26.15.
    v.profile.g50fClass = "sc0.f";
    v.profile.h13baClass = "zo3.b";
    v.profile.fieldH3 = "sd";
    v.profile.g50aClass = "sc0.a";
    v.profile.fieldName = "h";

    // Message provider/state interfaces in the shipped chat UI. e0 returns the selection
    // state; h0(int) returns te1.h, d returns selected IDs, and g toggles one item.
    v.chatEditSelectAll.selectionProviderClass = "le1.c";
    v.chatEditSelectAll.selectionStateClass = "le1.d";
    v.chatEditSelectAll.methodGetSelectionState = "e0";
    v.chatEditSelectAll.methodGetItem = "h0";
    v.chatEditSelectAll.methodGetSelectedIds = "d";
    v.chatEditSelectAll.methodToggleItem = "g";
    v.camera.cameraModuleClass = "ie2.g";

    // dg1.b.c(Uri, Map, boolean) returns eg1.a; eg1.a$c.a is success.
    // sp1.u's static initializer sets Y to the 301000 ms gallery limit.
    v.media.videoDurationCheckClass = "dg1.b";
    v.media.videoDurationSuccessClass = "eg1.a$c";
    v.media.galleryViewClass = "sp1.u";

    // Home nav b(int, String, Function0, Modifier, ...) and Compose clickable ABI.
    v.home26NavIcon.rendererClass = "lm2.n";
    v.home26NavIcon.settingsDrawableId = 0x7f081298;
    v.home26NavIcon.agentDrawableId = 0x7f080b9f;
    v.compose.composerClass = "j3.r";
    v.compose.clickableClass = "u1.h0";
    v.compose.onGloballyPositionedClass = "z4.y1";
    v.compose.layoutCoordinatesClass = "z4.b0";
    v.compose.methodLocalToWindow = "k";
    // SearchBar lifecycle and AI chip Compose group (group key 891601255).
    v.searchBarAgentI.homeSearchBarClass = "y85.i";
    v.searchBarAgentI.homeAiContainerId = 0x7f0b164c;
    v.searchBarAgentI.homeGuidelineId = 0x7f0b164e;
    v.searchBarAgentI.commerceHeaderClass = "com.linecorp.line.commerce.impl.c";
    v.searchBarAgentI.commerceHeaderMethod = "d";
    v.agentIInChat.toggleComposableClass = "ho1.k";

    v.plusMenu.plusMenuComposerImplClass = "j3.b1";
    v.plusMenu.plusMenuCallbackClass = "aq8.a";
    v.plusMenu.plusMenuOnClickItemClass = "aq8.l";

    // LINE 26.15 replaced the settings adapter/model family. These mappings are taken from
    // LineUserSettingItemListFragment and its concrete adapter in the shipped 26.15.0 DEX.
    v.settings.settingsAdapterClass = "xe8.f";
    v.settings.settingsItemClass = "xe8.f$c";
    v.settings.settingsBaseAdapterClass = "xe8.f$b";
    v.settings.settingsSearchHelperClass = "xg5.b";
    v.settings.settingsAdapterWrapperClass = "yb5.a";
    v.settings.settingsHeaderItemClass = "zb5.q";
    v.settings.settingsRowItemClass = "zb5.u";
    v.settings.settingsHandlerBaseClass = "zb5.z";
    v.settings.methodProxyGetItemType = "f";

    // Resource IDs resolved from the 26.15.0 resources table and native settings layouts.
    v.res.idSettingList = 0x7f0b229e;
    v.res.idPersonalInfo = 0x7f1539ca;
    v.res.typeSection = 0x7f0e0543;
    v.res.typeRow = 0x7f0e0546;
    v.res.idIcon = 0x7f0b2290;
    v.res.idDesc = 0x7f0b2282;
    v.res.idMark = 0x7f0b22a2;
    v.res.idSeparator = 0x7f0b22cb;
    v.res.idArrow = 0x7f0b226a;
    v.res.idNewMark = 0x7f0b1916;
    v.res.idNoticeDot = 0x7f0b1983;
    v.res.idTitle = 0x7f0b22d4;
    v.res.layoutCheckbox = 0x7f0e0537;
    v.res.layoutSectionHeader = 0x7f0e0543;
    v.res.layoutSettingsMain = 0x7f0e053d;
    v.res.idHeader = 0x7f0b111c;
    // The 26.15 settings layout no longer has the old status-bar guide child.
    v.res.idStatusBarGuide = 0;
    v.res.idTimestamp = 0x7f0b0888;

    // Plaintext message stages verified from the 26.15.0 DEX. gp8.od keeps the same raw
    // thrift field layout: from(1), to(2), id(4), text(10), contentType(15).
    v.callTts.receiveProcessorClass = "in8.c3";
    v.callTts.receivePlaintextMethod = "g";
    v.callTts.sendProcessorClass = "in8.d4";
    v.callTts.sendPlaintextMethod = "p";
    v.callTts.messageClass = "gp8.od";
    v.callTts.messageFromField = "a";
    v.callTts.messageToField = "b";
    v.callTts.messageIdField = "d";
    v.callTts.messageTextField = "g";
    v.callTts.messageContentTypeField = "j";

    // Obfuscated packages that shifted as a unit in 26.15.0.
    v.readReceipt.readReceiptManagerClass = "oa3.e";
    // Constructor subscribes to NOTIFIED_READ_MESSAGE. b consumes g/h/i as
    // chat ID, reader MID and last-read server ID, and b as the event timestamp.
    // in8.y1 instead handles NOTIFIED_PREMIUMBACKUP_STATE_CHANGED.
    v.unsend.notifiedReadMessageHandlerClass = "in8.a2";
    v.unsend.notifiedSendReactionHandlerClass = "in8.j2";
    v.unsend.notifiedDestroyMessageHandlerClass = "in8.a1";
    v.unsend.unsendDestroyHandlerClass = "in8.a1";
    v.unsend.operationClass = "gp8.de";
    // wq1.p.I0 binds ChatMessageViewData (te1.h) at argument 3; b() returns te1.c,
    // whose d field is validServerMessageId. a0() returns the message item View.
    v.unsend.chatMessageViewHolderClass = "wq1.p";
    v.unsend.methodBind = "I0";
    v.unsend.methodBindIndex = 3;
    v.main.headerButtonTypeClass = "qi8.d";
    // c71.t.a renders the plus menu; c renders an item with icon at parameter 4.
    v.plusMenu.plusMenuComponentClass = "c71.t";
    v.profileViewer.decoControllerClass = "rt6.f";
    v.profileViewer.decoEditorControllerClass = "rt6.c0";
    v.profileViewer.popupClass = "wt6.b";
    v.profileViewer.analyticsClass = "iu6.c";

    // AndroidX downloadable-font implementation moved from f7 to j7 without an ABI change.
    v.font.fontConfigClass = "j7.l";
    v.font.fontManagerClass = "j7.k";
    v.font.fontCallbackClass = "j7.l$c";
    v.font.fontRequestExecutorClass = "j7.n";
    v.font.fontCallbackWithHandlerClass = "j7.c";

    // Chat message timestamp value family (identified by its Message/ScheduledMessage models).
    v.chatTimestamp.displayTimeInterface = "te1.f";

    // Message context menu was moved as a unit. Field layout and constructor shape are
    // unchanged; rm1.n1 is ContextMenuDialog$show$1 in the shipped DEX.
    v.messageContextMenu.dialogClass = "rm1.l1";
    v.messageContextMenu.showCoroutineClass = "rm1.n1";

    // EditMessageRequest and the chat context-menu bridge moved in 26.15. The Kotlin
    // accessors on the presentation enum are no longer obfuscated.
    v.messageEditHistory.editRequestClass = "mh8.h";
    v.messageEditHistory.menuListBuilderClass = "rm1.b2";
    v.messageEditHistory.menuItemEnumClass = "jd1.c";
    v.messageEditHistory.menuPresentationEnumClass = "rm1.c1";
    v.messageEditHistory.methodMenuLabel = "getContextMenuButtonText";
    v.messageEditHistory.methodMenuIcon = "getContextIconRes";
    v.messageEditHistory.methodMenuActionAccessor = "getButtonAction";
    v.messageEditHistory.menuActionLambdaClass = "id1.f$b";

    // Chat-tab header state and icon models identified from the 9000..9004 type map and
    // AI_FRIEND/ALBUM/CALENDAR/OPEN_CHAT enum constants in 26.15.
    v.talkTabHeader.chatTabHeaderStateClass = "m52.f";
    v.talkTabHeader.iconTypeClass = "a71.q";
    // These optional sub-device-only button implementations were removed/restructured.
    v.talkTabHeader.subDeviceOpenChatButtonClass = "";
    v.talkTabHeader.subDeviceAlbumButtonClass = "";

    // HeaderViewController.kt retained its 11-argument shape but all bridge models moved.
    v.chat.headerController = "il1.c1";
    v.chatHeader.fieldChatConfigChatId = "pg1.a";
    v.chatHeader.fieldChatConfigIsMuted = "ng1.a";
    v.chatHeader.fieldChatConfigType = "il1.p0";
    v.chatHeader.fieldAppInfoVersion =
        "com.linecorp.line.chat.ui.impl.officialaccount.OaChatStatusBarViewModel";
    v.chatHeader.fieldAppInfoPkg = "rc1.a";
    v.chatHeader.fieldAppInfoId = "vw0.d";

    v.imageQuality.qualityProfileHighClass = "xm8.a$b$a";
    v.imageQuality.qualityProfileMediumClass = "xm8.a$b$b";
    v.imageQuality.imageUtilClass = "jp.naver.line.android.util.y0";

    v.announcementFix.formatterClass = "dt1.c";
    v.announcementFix.announcementEventClass = "k91.g$d0";

    v.chat.searchHeaderHelperClass = "ry1.w";
    v.chat.searchHeaderControllerField = "g";
    v.chat.searchHeaderEventBusField = "c";
    v.chat.searchControllerSearchBoxMethod = "a";
    v.chat.searchPresenterClass = "vy1.n";
    v.chat.searchKeywordTypeClass = "ua1.c";
    v.chat.searchKeywordTypeMethod = "shouldTriggerSearch";
    v.chat.searchResultClass = "ua1.h";
    v.chat.searchResultWrapperClass = "ua1.i";
    v.chat.searchKeywordEventClass = "qy1.b";

    // Kotlin module suffix changed with the common-libs module rename.
    v.main.methodHeaderSetButtonVisibility =
        "setUpButtonVisibility$LINE_Android_migrant_common_libs";
    v.main.methodHeaderSetButtonListener =
        "setUpButtonOnClickListener$LINE_Android_migrant_common_libs";

    return v;
  }
}
