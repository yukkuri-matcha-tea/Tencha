package dev.vector.lineextension.versions;

import dev.vector.lineextension.LineVersion;

/** Profile and media targets in LINE 26.17.1 (261710173). */
final class Line26171ProfileMedia {
  private Line26171ProfileMedia() {}

  static void apply(LineVersion.Config v) {
    // UserProfileActivity stores ProfileBaseDataViewModel in r; its C LiveData drives edit mode.
    v.profileViewer.profileViewModelField = "r";
    v.profileViewer.decoControllerClass = "yv6.h";
    v.profileViewer.decoEditorControllerClass = "yv6.j0";
    v.profileViewer.popupClass = "hw6.b";
    v.profileViewer.analyticsClass = "tw6.d";
    v.profileViewer.methodSetEditMode = "K7";
    v.profileViewer.methodResetDecorations = "u7";
    v.profileViewer.methodSelectedDecoration = "w7";
    v.profileViewer.methodSelectDecoration = "G7";
    v.profileViewer.methodSaveDecorations = "C7";
    v.profileViewer.saveDecorationsParamClass = "nm6.h";
    v.profileViewer.mediaValueClass = "ow6.y$e";
    // yv6.j0 binds delete to fv6.u.b and edit to fv6.u.c.
    v.profileViewer.editorDeleteButtonField = "b";
    v.profileViewer.editorEditButtonField = "c";

    v.profile.g50fClass = "pc0.f";
    v.profile.g50aClass = "pc0.a";
    v.profile.h13baClass = "zq3.b";
    v.profile.fieldH3 = "vd";

    v.font.fontConfigClass = "i7.l";
    v.font.fontManagerClass = "i7.k";
    v.font.fontCallbackClass = "i7.l$c";
    v.font.fontRequestExecutorClass = "i7.n";
    v.font.fontCallbackWithHandlerClass = "i7.c";

    v.imageQuality.qualityProfileHighClass = "as8.a$b$a";
    v.imageQuality.qualityProfileMediumClass = "as8.a$b$b";
    v.imageQuality.imageUtilClass = "jp.naver.line.android.util.c1";

    v.media.videoDurationCheckClass = "fg1.b";
    v.media.videoDurationSuccessClass = "gg1.a$c";
    v.media.droppedMediaPreprocessorClass = "k31.b";
    // The old gallery field write cannot affect callers of the inlined 301000 int constant.
    v.media.galleryViewClass = "";
    v.media.fieldGalleryDurationLimit = "";
    v.media.fieldVideoProfileTrimmerLimit = "R0";
    v.media.keepNonDurationValidation = true;

    v.camera.cameraModuleClass = "tf2.f";
    v.camera.methodUseExternalCamera = "d";
  }
}
