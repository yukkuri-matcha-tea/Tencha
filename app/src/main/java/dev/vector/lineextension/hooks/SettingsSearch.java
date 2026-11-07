package dev.vector.lineextension.hooks;

import java.util.Locale;

/** Search policy shared by ordinary rows and nested developer groups. */
final class SettingsSearch {
  private SettingsSearch() {}

  static String normalize(String text) {
    return text == null ? "" : text.toLowerCase(Locale.ROOT);
  }

  static boolean matches(String text, String query) {
    return normalize(text).contains(normalize(query));
  }

  static boolean showDeveloperGroup(boolean enabled, boolean searching, int matches) {
    return enabled && (!searching || matches > 0);
  }
}
