package dev.vector.lineextension.hooks;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/** Keep one PCM buffer per clip, not an eight-byte-per-sample expanded timeline. */
final class PcmVoiceMixer {
  private static final class Voice {
    final short[] pcm;
    int position;

    Voice(short[] pcm) {
      this.pcm = pcm;
    }
  }

  private final List<Voice> voices = new ArrayList<>();

  void enqueue(short[] pcm) {
    if (pcm != null && pcm.length > 0) voices.add(new Voice(pcm));
  }

  boolean isEmpty() {
    return voices.isEmpty();
  }

  void clear() {
    voices.clear();
  }

  int render(short[] frame) {
    Arrays.fill(frame, (short) 0);
    int count = 0;
    for (int i = 0; i < frame.length; i++) {
      long mixed = 0;
      boolean present = false;
      for (Voice voice : voices) {
        if (voice.position < voice.pcm.length) {
          mixed += voice.pcm[voice.position++];
          present = true;
        }
      }
      if (present) count = i + 1;
      frame[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, mixed));
    }
    for (Iterator<Voice> iterator = voices.iterator(); iterator.hasNext(); ) {
      Voice voice = iterator.next();
      if (voice.position >= voice.pcm.length) iterator.remove();
    }
    return count;
  }
}
