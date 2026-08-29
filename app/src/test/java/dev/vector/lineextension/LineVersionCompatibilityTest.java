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
