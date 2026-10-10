package dev.vector.lineextension.hooks;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class SoundboardOrderTest {
  @Test
  public void dragOrderMergesConcurrentImportsAndDeletions() {
    List<String> current = new ArrayList<>(Arrays.asList("a", "c", "new"));
    assertEquals(
        Arrays.asList("c", "a", "new"),
        SoundboardOrder.merge(current, Arrays.asList("c", "deleted", "a", "c"), value -> value));
    assertEquals(Arrays.asList("a", "c", "new"), current);
  }

  @Test
  public void canceledOrEmptyDragOrderPreservesCurrentClips() {
    assertEquals(clips(), SoundboardOrder.merge(clips(), new ArrayList<String>(), value -> value));
  }

  private List<String> clips() {
    return new ArrayList<>(Arrays.asList("a", "b", "c", "d"));
  }

  @Test
  public void movesToFirstAndLast() {
    List<String> clips = clips();
    assertTrue(SoundboardOrder.move(clips, "d", "a", value -> value));
    assertEquals(Arrays.asList("d", "a", "b", "c"), clips);
    assertTrue(SoundboardOrder.move(clips, "d", "c", value -> value));
    assertEquals(Arrays.asList("a", "b", "c", "d"), clips);
  }

  @Test
  public void movesUpAndDownToTheSelectedSlot() {
    List<String> clips = clips();
    assertTrue(SoundboardOrder.move(clips, "a", "c", value -> value));
    assertEquals(Arrays.asList("b", "c", "a", "d"), clips);
    assertTrue(SoundboardOrder.move(clips, "a", "c", value -> value));
    assertEquals(Arrays.asList("b", "a", "c", "d"), clips);
  }

  @Test
  public void missingDeletedAndUnchangedTargetsDoNotMutate() {
    List<String> clips = clips();
    assertFalse(SoundboardOrder.move(clips, "deleted", "a", value -> value));
    assertFalse(SoundboardOrder.move(clips, "a", "deleted", value -> value));
    assertFalse(SoundboardOrder.move(clips, "a", "a", value -> value));
    assertFalse(SoundboardOrder.move(clips, null, "a", value -> value));
    assertEquals(Arrays.asList("a", "b", "c", "d"), clips);
  }

  @Test
  public void newlyAddedClipsArePreserved() {
    List<String> clips = clips();
    clips.add("new");
    assertTrue(SoundboardOrder.move(clips, "a", "d", value -> value));
    assertEquals(Arrays.asList("b", "c", "d", "a", "new"), clips);
  }

  @Test
  public void emptyAndSingleClipAreSafe() {
    assertFalse(SoundboardOrder.move(new ArrayList<String>(), "a", "b", value -> value));
    List<String> clips = new ArrayList<>(Arrays.asList("a"));
    assertFalse(SoundboardOrder.move(clips, "a", "a", value -> value));
    assertEquals(Arrays.asList("a"), clips);
  }
}
