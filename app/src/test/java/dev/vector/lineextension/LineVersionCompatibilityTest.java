package dev.vector.lineextension;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class LineVersionCompatibilityTest {
  @Before
  public void setUp() {
    LineVersion.resetResolutionForTests();
  }

  @After
  public void tearDown() {
    LineVersion.resetResolutionForTests();
  }

  @Test
  public void versionIsNormalizedBeforeExactLookup() {
    assertEquals("26.13.0", LineVersion.normalizeVersion("LINE/26.13.0 (261300096)"));
    assertNotNull(LineVersion.resolveVersion("LINE/26.13.0 (261300096)", name -> false));
    assertEquals("exact", LineVersion.getCompatibilityState());
    assertEquals("26.13.0", LineVersion.getResolvedVersionName());
  }

  @Test
  public void latestVerifiedVersionUsesNativeSettingsModelMapping() {
    LineVersion.Config config = LineVersion.resolveVersion("26.13.0", name -> false);
    assertNotNull(config);
    assertEquals("exact", LineVersion.getCompatibilityState());
    assertEquals("k35.s", config.settings.settingsHeaderItemClass);
    assertEquals("k35.v", config.settings.settingsRowItemClass);
    assertEquals("kh8.p", config.settings.settingsSuspendFunction2Class);
    assertEquals("kh8.l", config.settings.settingsFunction1Class);
    assertEquals("k35.v0$a", config.settings.settingsDefaultNavigationClass);
  }

  @Test
  public void unknownVersionUsesNewestStructurallyCompatibleConfig() {
    assertNotNull(LineVersion.resolveVersion("26.14.0", name -> true));
    assertEquals("automatic", LineVersion.getCompatibilityState());
    assertEquals("26.13.0", LineVersion.getResolvedVersionName());
  }

  @Test
  public void missingVersionNameCanStillUseStructuralCompatibility() {
    assertNotNull(LineVersion.resolveVersion(null, name -> true));
    assertEquals("automatic", LineVersion.getCompatibilityState());
    assertEquals("26.13.0", LineVersion.getResolvedVersionName());
  }

  @Test
  public void unknownVersionFailsClosedWithoutEnoughAnchors() {
    assertNull(
        LineVersion.resolveVersion(
            "26.14.0", name -> "jp.naver.line.android.activity.main.MainActivity".equals(name)));
    assertEquals("unsupported", LineVersion.getCompatibilityState());
    assertEquals("", LineVersion.getResolvedVersionName());
  }
}
