package dev.vector.lineextension.hooks;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Moves one stable clip ID to the selected clip's position without replacing the whole index. */
final class SoundboardOrder {
  private SoundboardOrder() {}

  static <T> List<T> merge(List<T> current, List<String> requested, Function<T, String> id) {
    Map<String, T> remaining = new LinkedHashMap<>();
    for (T clip : current) remaining.put(id.apply(clip), clip);
    List<T> result = new ArrayList<>();
    for (String key : requested) {
      T clip = remaining.remove(key);
      if (clip != null) result.add(clip);
    }
    result.addAll(remaining.values());
    return result;
  }

  static <T> boolean move(List<T> clips, String clipId, String targetId, Function<T, String> id) {
    if (clipId == null || targetId == null) return false;
    int from = -1;
    int to = -1;
    for (int i = 0; i < clips.size(); i++) {
      String candidate = id.apply(clips.get(i));
      if (clipId.equals(candidate)) from = i;
      if (targetId.equals(candidate)) to = i;
    }
    if (from < 0 || to < 0 || from == to) return false;
    T moving = clips.remove(from);
    clips.add(to, moving);
    return true;
  }
}
