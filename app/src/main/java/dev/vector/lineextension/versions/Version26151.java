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
    return v;
  }
}
