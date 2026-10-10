package dev.vector.lineextension.versions;

import dev.vector.lineextension.LineVersion;

/** Exact mappings checked against LINE 26.15.1 (261510192), not a version-check bypass. */
public final class Version26151 {
  private Version26151() {}

  public static LineVersion.Config create() {
    LineVersion.Config v = Version26150.create();
    // Compose ABI shifted while the app-level renderer names and resource IDs stayed stable.
    v.compose.composerClass = "j3.q";
    v.plusMenu.plusMenuComposerImplClass = "j3.c1";
    // w1.a builds u1 -> x1; the callback receives LayoutCoordinates (z4.a0).
    v.compose.onGloballyPositionedClass = "z4.w1";
    v.compose.layoutCoordinatesClass = "z4.a0";
    // sp1.x is the gallery controller with the same 301000 ms static duration limit.
    v.media.galleryViewClass = "sp1.x";
    // classes13.dex: g owns the renderer; f is now a coroutine, not the controller.
    v.profileViewer.decoControllerClass = "rt6.g";
    v.profileViewer.decoEditorControllerClass = "rt6.d0";
    v.profileViewer.methodGetDecorationView = "v";
    // rt6.f1.b takes the MEDIA payload, not the PFRAME decoration drawable.
    v.profileViewer.mediaValueClass = "du6.y$e";
    // d0.D is ys6.u (delete/edit/resize); d0.F is a coroutine Job.
    v.profileViewer.editorSelectionFrameField = "D";
    // Glide n.R(ImageView); n.S accepts a Target, not an ImageView in this APK.
    v.profileViewer.methodLoadImageIntoView = "R";
    // classes10.dex: z0 owns ChatHistoryHeader; c1 is an icon function reference.
    v.chat.headerController = "il1.z0";
    v.chatHeader.fieldChatConfigType = "il1.m0";
    // ry1.v owns the native search box and references z0 via g, event bus via c.
    v.chat.searchHeaderHelperClass = "ry1.v";
    // n is lh1.a; d() returns HeaderSearchBoxView. g is the normal header, not search.
    v.chat.searchHeaderControllerField = "n";
    v.chat.searchControllerSearchBoxMethod = "d";
    // vy1.n's keyword handler reads/writes A; z is now a boolean.
    v.chat.searchPresenterKeywordSubjectField = "A";
    // SearchBoxView.b is a boolean; c is EditText, e is the search ImageView.
    v.chat.searchBoxEditTextField = "c";
    v.chat.searchBoxIconField = "e";
    // ChatRoomConfiguration: parser qg.b binds these two int getters to unsend limits.
    v.unsend.chatServiceConfigClass = "ta5.u";
    // Firebase/GMS types are verified from constructors and signatures in classes.dex/8/9.
    v.notificationFix.firebaseAppClass = "ux.e";
    v.notificationFix.lineFcmServiceBaseClass = "iz.i";
    v.notificationFix.firebaseRemoteMessageClass = "iz.s0";
    v.notificationFix.firebaseReceiverEnvelopeClass = "dr.a";
    v.notificationFix.firebaseDispatcherClass = "iz.n";
    // n.d is a bind connection, NOT the start-service queue. x0.a().d is that queue.
    v.notificationFix.firebaseQueueClass = "iz.x0";
    v.notificationFix.firebaseQueueGetInstanceMethod = "a";
    v.notificationFix.firebaseBindDeliveryClass = "iz.n1";
    v.notificationFix.firebaseWakefulStartClass = "iz.i1";
    v.notificationFix.firebaseCompletedTaskClass = "qt.n";
    v.notificationFix.gmsAvailabilityClass = "er.j";
    v.notificationFix.gmsAvailabilityMethod = "e";
    v.notificationFix.gmsSignatureCheckClass = "er.k";
    v.notificationFix.fisCertDigestClass = "pr.a";
    // h$d.e1 removes/posts this exact background-disconnect runnable.
    v.notificationFix.legyDisconnectRunnableClass = "ta0.j";
    return v;
  }
}
