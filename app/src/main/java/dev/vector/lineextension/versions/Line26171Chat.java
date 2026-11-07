package dev.vector.lineextension.versions;

import dev.vector.lineextension.LineVersion;

/** Chat and privacy targets verified against LINE 26.17.1 (261710173). */
final class Line26171Chat {
  private Line26171Chat() {}

  static void apply(LineVersion.Config v) {
    // The operation registry uses x1 for NOTIFIED_READ_MESSAGE and h2 for reactions.
    v.unsend.notifiedReadMessageHandlerClass = "ls8.x1";
    v.unsend.notifiedSendReactionHandlerClass = "ls8.h2";
    v.unsend.notifiedDestroyMessageHandlerClass = "ls8.z0";
    v.unsend.unsendDestroyHandlerClass = "ls8.z0";
    v.unsend.operationClass = "lu8.ee";
    v.unsend.destroyResultClass = "ks8.a$a$c";
    // jr1.y.I0 receives ve1.h at index 3, with ve1.h.b() supplying the message metadata.
    v.unsend.chatMessageViewHolderClass = "jr1.y";
    v.chatTimestamp.displayTimeInterface = "ve1.f";
    v.res.idTimestamp = 0x7f0b0863;

    // pi3.e.d calls the W0 overload whose request writes sendChatChecked.
    v.readReceipt.readReceiptManagerClass = "pi3.e";
    v.thrift.v1 = "W0";
    v.unsend.chatServiceConfigClass = "dc5.u";
    v.unsend.appInfoProviderClass = "nr8.d";

    v.chat.headerController = "nl1.o0";
    v.chat.headerHelper = "tn8.g";
    v.chatHeader.fieldChatConfigChatId = "rg1.a";
    v.chatHeader.fieldChatConfigIsMuted = "pg1.a";
    v.chatHeader.fieldChatConfigType = "jh6.x";
    v.chatHeader.fieldAppInfoPkg = "tc1.a";
    v.chatHeader.fieldAppInfoId = "vw0.d";
    v.main.headerButtonTypeClass = "tn8.e";
    v.main.headerInterfaceA = "tn8.c";
    // ChatHistoryActivity.q still stores the request model with s() -> getChatId().
    v.chat.chatIdField = "q";

    v.chat.searchHeaderHelperClass = "wz1.r";
    v.chat.searchPresenterClass = "a02.t";
    v.chat.searchKeywordTypeClass = "wa1.c";
    v.chat.searchResultClass = "wa1.h";
    v.chat.searchResultWrapperClass = "wa1.i";
    v.chat.searchKeywordEventClass = "vz1.b";
    v.chat.searchResultTitleViewHolderClass = "d02.l";

    v.chatEditSelectAll.selectionProviderClass = "ne1.e";
    v.chatEditSelectAll.selectionStateClass = "ne1.f";
    v.chatEditSelectAll.methodGetSelectionState = "e0";
    v.chatEditSelectAll.methodGetItem = "k0";
    v.chatEditSelectAll.methodIsItemSelected = "n";

    v.announcementFix.formatterClass = "rt1.c";
    v.announcementFix.announcementEventClass = "m91.g$d0";
    v.messageContextMenu.dialogClass = "xm1.n1";
    v.messageContextMenu.showCoroutineClass = "xm1.q1";
    v.messageContextMenu.textParamsClass = "y81.a$e";
    v.messageContextMenu.fieldSelectedView = "c";
    v.messageEditHistory.editRequestClass = "pm8.h";
    v.messageEditHistory.menuListBuilderClass = "xm1.e2";
    v.messageEditHistory.menuItemEnumClass = "ld1.c";
    v.messageEditHistory.menuPresentationEnumClass = "xm1.d1";
    v.messageEditHistory.methodMenuLabel = "getContextMenuButtonText";
    v.messageEditHistory.methodMenuIcon = "getContextIconRes";
    v.messageEditHistory.methodMenuActionAccessor = "getButtonAction";
    v.messageEditHistory.menuActionLambdaClass = "kd1.f$b";
  }
}
