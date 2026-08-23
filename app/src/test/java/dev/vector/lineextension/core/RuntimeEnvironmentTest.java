package dev.vector.lineextension.core;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class RuntimeEnvironmentTest {
  @Test
  public void nullClassLoaderIsUnknown() {
    assertEquals(RuntimeEnvironment.MODE_UNKNOWN, RuntimeEnvironment.detectLoaderMode(null));
  }

  @Test
  public void classLoaderWithoutLspatchIsRootMode() {
    assertEquals(
        RuntimeEnvironment.MODE_ROOT,
        RuntimeEnvironment.detectLoaderMode(new ClassLoader(null) {}));
  }

  @Test
  public void lspatchLoaderClassIsDetected() {
    assertEquals(
        RuntimeEnvironment.MODE_LSPATCH,
        RuntimeEnvironment.detectLoaderMode(RuntimeEnvironmentTest.class.getClassLoader()));
  }

  @Test
  public void modeSanitizerRejectsUnexpectedValues() {
    assertEquals(RuntimeEnvironment.MODE_ROOT, RuntimeEnvironment.sanitizeMode("root"));
    assertEquals(RuntimeEnvironment.MODE_LSPATCH, RuntimeEnvironment.sanitizeMode("lspatch"));
    assertEquals(RuntimeEnvironment.MODE_UNKNOWN, RuntimeEnvironment.sanitizeMode("other"));
  }
}
