package dev.vector.lineextension.hooks;

import static org.junit.Assert.*;

import java.util.Locale;
import org.junit.Test;

public class SettingsSearchTest {
  @Test
  public void unrelatedDeveloperRowsDoNotKeepTheirGroupVisible() {
    assertFalse(SettingsSearch.matches("プロフィール内部情報", "TTS"));
    assertFalse(SettingsSearch.matches("FCM Fix", "TTS"));
    assertFalse(SettingsSearch.showDeveloperGroup(true, true, 0));
    assertTrue(SettingsSearch.showDeveloperGroup(true, true, 1));
  }

  @Test
  public void clearingSearchNeverExposesDisabledDeveloperOptions() {
    assertFalse(SettingsSearch.showDeveloperGroup(false, false, 12));
    assertFalse(SettingsSearch.showDeveloperGroup(false, true, 12));
    assertTrue(SettingsSearch.showDeveloperGroup(true, false, 0));
  }

  @Test
  public void matchingDoesNotDependOnDeviceLocale() {
    Locale before = Locale.getDefault();
    try {
      Locale.setDefault(Locale.forLanguageTag("tr-TR"));
      assertTrue(SettingsSearch.matches("TTS SETTINGS", "settings"));
    } finally {
      Locale.setDefault(before);
    }
  }
}
