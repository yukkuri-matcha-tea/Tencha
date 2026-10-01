#pragma once

#include <cstdint>
#include <limits>

namespace tencha {

constexpr std::int16_t mixCallSample(std::int16_t mic, bool muted,
                                     std::int64_t soundboard, std::int64_t tts) {
  const std::int64_t mixed = (muted ? 0 : static_cast<std::int64_t>(mic)) + soundboard + tts;
  return mixed > std::numeric_limits<std::int16_t>::max()
             ? std::numeric_limits<std::int16_t>::max()
             : mixed < std::numeric_limits<std::int16_t>::min()
                   ? std::numeric_limits<std::int16_t>::min()
                   : static_cast<std::int16_t>(mixed);
}

// Compile-time regression tests use the same function as the recorder callback.
static_assert(mixCallSample(12000, true, 0, 0) == 0, "Muted mic must be silent");
static_assert(mixCallSample(-12000, true, 1000, 2000) == 3000,
              "Mute must retain soundboard and TTS");
static_assert(mixCallSample(12000, false, 1000, 2000) == 15000,
              "Unmute must restore mic mixing");
static_assert(mixCallSample(32000, false, 1000, 2000) == 32767,
              "Positive clipping must saturate");
static_assert(mixCallSample(-32000, false, -1000, -2000) == -32768,
              "Negative clipping must saturate");

}  // namespace tencha
