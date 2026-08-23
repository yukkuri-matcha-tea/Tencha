package dev.vector.lineextension.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GitHubUpdaterTest {
  @Test
  public void comparesSemanticReleaseVersions() {
    assertTrue(GitHubUpdater.compareVersions("1.5.11", "1.5.10") > 0);
    assertTrue(GitHubUpdater.compareVersions("2.0", "1.99.99") > 0);
    assertEquals(0, GitHubUpdater.compareVersions("v1.5.11", "1.5.11.0"));
  }
}
