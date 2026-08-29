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

    // The cloned Account row still uses the generic setting_* views in 26.13.1.
    // Do not use the similarly named setting_item_* IDs: those belong to a different layout.
    v.res.idIcon = 0x7f0b2290;
    v.res.idDesc = 0x7f0b2282;
    v.res.idSeparator = 0x7f0b22cb;
    v.res.idTitle = 0x7f0b22d3;

    return v;
  }
}
