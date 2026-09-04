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
  public void latestVerifiedVersionUsesLiveSettingsTemplateMapping() {
    LineVersion.Config config = LineVersion.resolveVersion("26.14.0", name -> false);
    assertNotNull(config);
    assertEquals("exact", LineVersion.getCompatibilityState());
    assertEquals("y78.f", config.settings.settingsAdapterClass);
    assertEquals("ka5.b", config.settings.settingsSearchHelperClass);
    assertEquals("m55.s", config.settings.settingsHeaderItemClass);
    assertEquals("m55.v", config.settings.settingsRowItemClass);
    assertEquals("l55.a", config.settings.settingsAdapterWrapperClass);
    assertEquals(0x7f0b2275, config.res.idIcon);
    assertEquals(0x7f0b2267, config.res.idDesc);
    assertEquals(0x7f0b22b0, config.res.idSeparator);
    assertEquals(0x7f0b22b8, config.res.idTitle);
    assertEquals("n0", config.chat.searchControllerSearchBoxMethod);
    assertEquals("f7.l", config.font.fontConfigClass);
    assertEquals("f7.k", config.font.fontManagerClass);
    assertEquals("f7.l$c", config.font.fontCallbackClass);
    assertEquals("f7.n", config.font.fontRequestExecutorClass);
  }

  @Test
  public void unknownVersionFailsClosedEvenWhenOldAnchorsExist() {
    assertNull(LineVersion.resolveVersion("26.15.0", name -> true));
    assertEquals("unsupported", LineVersion.getCompatibilityState());
    assertEquals("", LineVersion.getResolvedVersionName());
  }

  @Test
  public void missingVersionNameFailsClosed() {
    assertNull(LineVersion.resolveVersion(null, name -> true));
    assertEquals("unsupported", LineVersion.getCompatibilityState());
    assertEquals("", LineVersion.getResolvedVersionName());
  }

  @Test
  public void unknownVersionFailsClosedWithoutEnoughAnchors() {
    assertNull(
        LineVersion.resolveVersion(
            "26.15.0", name -> "jp.naver.line.android.activity.main.MainActivity".equals(name)));
    assertEquals("unsupported", LineVersion.getCompatibilityState());
    assertEquals("", LineVersion.getResolvedVersionName());
  }
}
