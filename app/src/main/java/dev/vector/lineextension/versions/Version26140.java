package dev.vector.lineextension.versions;

import dev.vector.lineextension.LineVersion;

/** Exact mappings verified against LINE 26.14.0 (versionCode 261400121). */
public final class Version26140 {
  private Version26140() {}

  public static LineVersion.Config create() {
    LineVersion.Config v = Version26130.create();

    // LINE settings was migrated to the i55/y78 adapter and m55 model family.
    v.settings.settingsAdapterClass = "y78.f";
    v.settings.settingsItemClass = "y78.f$c";
    v.settings.settingsBaseAdapterClass = "y78.f$b";
    v.settings.settingsSearchHelperClass = "ka5.b";
    v.settings.settingsAdapterWrapperClass = "l55.a";
    v.settings.settingsHeaderItemClass = "m55.s";
    v.settings.settingsRowItemClass = "m55.v";
    v.settings.settingsHandlerBaseClass = "m55.z";

    v.res.idSettingList = 0x7f0b2283;
    v.res.idPersonalInfo = 0x7f153993;
    v.res.typeSection = 0x7f0e053f;
    v.res.typeRow = 0x7f0e0542;
    v.res.idIcon = 0x7f0b2275;
    v.res.idDesc = 0x7f0b2267;
    v.res.idMark = 0x7f0b2287;
    v.res.idSeparator = 0x7f0b22b0;
    v.res.idArrow = 0x7f0b224f;
    v.res.idNewMark = 0x7f0b18fa;
    v.res.idNoticeDot = 0x7f0b1967;
    v.res.idTitle = 0x7f0b22b8;
    v.res.layoutCheckbox = 0x7f0e0533;
    v.res.layoutSectionHeader = 0x7f0e053f;
    v.res.layoutSettingsMain = 0x7f0e0539;
    v.res.idHeader = 0x7f0b110b;
    v.res.idStatusBarGuide = 0x7f0b24f9;
    v.res.idTimestamp = 0x7f0b0888;

    // Compose 1.9 ABI used by LINE 26.14.0.
    v.compose.composerClass = "h3.s";
    v.compose.clickableClass = "u1.l0";
    v.compose.methodLocalToWindow = "l";
    v.plusMenu.plusMenuComponentClass = "s11.o";
    v.plusMenu.plusMenuCallbackClass = "aj8.a";
    v.plusMenu.plusMenuOnClickItemClass = "aj8.l";

    // AndroidX downloadable-font classes shifted by one obfuscated symbol in 26.14.0.
    // Keeping the 26.13.x names makes the generic TextView hooks run, but aborts the
    // LINE/Compose font-provider hooks halfway through initialization.
    v.font.fontConfigClass = "f7.l";
    v.font.fontManagerClass = "f7.k";
    v.font.fontCallbackClass = "f7.l$c";
    v.font.fontRequestExecutorClass = "f7.n";
    v.font.fontCallbackWithHandlerClass = "f7.c";
    v.font.fontInjectedClass = "";

    // Chat header and read-receipt implementation.
    v.readReceipt.readReceiptManagerClass = "na3.e";
    v.unsend.notifiedReadMessageHandlerClass = "jg8.y1";
    v.unsend.notifiedSendReactionHandlerClass = "jg8.j2";
    v.chat.headerController = "ag1.t1";
    v.main.headerButtonTypeClass = "rb8.d";
    v.chatHeader.fieldChatConfigChatId = "ib1.a";
    v.chatHeader.fieldChatConfigIsMuted = "gb1.a";
    v.chatHeader.fieldChatConfigType = "ag1.e1";
    v.chatHeader.fieldAppInfoVersion = "fr1.n";
    v.chatHeader.fieldAppInfoPkg = "k71.a";
    v.chatHeader.fieldAppInfoId = "or0.d";

    // Talk operation and chat message view-data families.
    v.unsend.notifiedDestroyMessageHandlerClass = "jg8.a1";
    v.unsend.unsendDestroyHandlerClass = "jg8.a1";
    v.unsend.operationClass = "hi8.de";
    v.unsend.chatMessageViewHolderClass = "nl1.g";
    v.unsend.methodBind = "V";
    v.unsend.methodGetItemView = "a0";
    v.chatTimestamp.displayTimeInterface = "m91.f";

    // Media quality and video validation.
    v.imageQuality.qualityProfileHighClass = "yf8.a$b$a";
    v.imageQuality.qualityProfileMediumClass = "yf8.a$b$b";
    v.imageQuality.imageUtilClass = "jp.naver.line.android.util.g1";
    v.media.videoDurationCheckClass = "wa1.b";
    v.media.videoDurationSuccessClass = "xa1.a$c";
    v.media.galleryViewClass = "kk1.y";
    v.media.droppedMediaPreprocessorClass = "hy0.b";
    // The old single-argument selection validator no longer exists. The duration checker,
    // picker parameter, gallery limit and dropped-media paths above are the verified gates.
    v.media.selectionValidatorClass = "";
    v.media.selectionValidatorParamClass = "";

    // Search-in-chat model/presenter families.
    v.chat.searchHeaderHelperClass = "xs1.g";
    v.chat.searchControllerSearchBoxMethod = "n0";
    v.chat.searchPresenterClass = "bt1.t";
    v.chat.searchKeywordTypeClass = "l51.c";
    v.chat.searchKeywordTypeMethod = "d";
    v.chat.searchResultClass = "l51.h";
    v.chat.searchResultWrapperClass = "l51.i";
    v.chat.searchKeywordEventClass = "ws1.b";
    v.chat.searchResultTitleViewHolderClass = "et1.m";
    // The old FTS coroutine class was removed; result-model and local database counting remain.
    v.chat.searchFtsInChatQueryClass = "";
    v.chat.searchFtsQueryField = "";
    v.chat.searchFtsChatIdField = "";
    v.chat.searchFtsLimitField = "";

    // Edit-message request and context-menu families.
    v.messageEditHistory.editRequestClass = "na8.h";
    v.messageEditHistory.menuListBuilderClass = "kh1.y1";
    v.messageEditHistory.menuItemEnumClass = "c81.c";
    v.messageEditHistory.menuPresentationEnumClass = "kh1.w0";
    v.messageEditHistory.methodMenuLabel = "g";
    v.messageEditHistory.methodMenuIcon = "f";
    v.messageEditHistory.methodMenuActionAccessor = "d";
    v.messageEditHistory.menuActionLambdaClass = "b81.f$b";

    // Chat-tab header state and its button models.
    v.talkTabHeader.chatTabHeaderStateClass = "qz1.f";
    v.talkTabHeader.iconTypeClass = "q11.n";
    v.talkTabHeader.subDeviceOpenChatButtonClass = "wx1.c$f";
    v.talkTabHeader.subDeviceAlbumButtonClass = "wx1.c$b";

    v.home26NavIcon.agentDrawableId = 0x7f080b87;
    v.home26NavIcon.settingsDrawableId = 0x7f081278;
    v.home26NavIcon.rendererClass = "ng2.n";

    return v;
  }
}
