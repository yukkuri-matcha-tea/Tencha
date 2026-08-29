package dev.vector.lineextension.versions;

import dev.vector.lineextension.LineVersion;

/** Exact mappings verified against LINE 26.13.1 (versionCode 261310101). */
public final class Version26131 {
  private Version26131() {}

  public static LineVersion.Config create() {
    LineVersion.Config v = Version26130.create();

    v.settings.settingsAdapterClass = "d68.f";
    v.settings.settingsItemClass = "d68.f$c";
    v.settings.settingsBaseAdapterClass = "d68.f$b";
    v.settings.settingsSearchHelperClass = "g85.b";
    v.settings.settingsAdapterWrapperClass = "h35.a";
    v.settings.settingsHeaderItemClass = "i35.r";
    v.settings.settingsRowItemClass = "i35.t";
    v.settings.settingsHandlerBaseClass = "i35.w";

    // These IDs moved in 26.13.1 even though the surrounding settings layouts stayed stable.
    v.res.idIcon = 0x7f0b2296;
    v.res.idDesc = 0x7f0b2294;
    v.res.idSeparator = 0x7f0b2295;
    v.res.idTitle = 0x7f0b229c;

    return v;
  }
}
