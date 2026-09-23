#include <jni.h>

#include <android/log.h>
#include <dlfcn.h>
#include <cmath>
#include <cstdint>
#include <cstring>
#include <cstdio>

namespace {

constexpr const char* kTag = "TenchaCall";
constexpr int kAudioMediaKind = 1;

template <typename T>
T resolve(void* library, const char* name) {
  return reinterpret_cast<T>(dlsym(library, name));
}

struct Api {
  using GetJCall = void* (*)(void*);
  using GetStreamArray = void* (*)(void*, int);
  using ArrayCount = int (*)(void*);
  using ArrayGet = void* (*)(void*, int);
  using ArrayRelease = void (*)(void*);
  using StreamKind = int (*)(void*);
  using StreamDirection = int (*)(void*);
  using SetTalkerLevel = bool (*)(void*, const char*, const char*, bool, float,
                                  const char*, void*, void*);

  void* library = nullptr;
  GetJCall getJCall = nullptr;
  GetStreamArray getStreamArray = nullptr;
  ArrayCount arrayCount = nullptr;
  ArrayGet arrayGet = nullptr;
  ArrayRelease arrayRelease = nullptr;
  StreamKind streamKind = nullptr;
  StreamDirection streamDirection = nullptr;
  SetTalkerLevel setTalkerLevel = nullptr;

  bool load() {
    if (library != nullptr) return ready();
    library = dlopen("libandromeda.so", RTLD_NOW | RTLD_NOLOAD);
    if (library == nullptr) library = dlopen("libandromeda.so", RTLD_NOW | RTLD_LOCAL);
    if (library == nullptr) return false;
    getJCall = resolve<GetJCall>(library, "ampPlnGetJCall");
    getStreamArray = resolve<GetStreamArray>(library, "jup_call_get_stream_array");
    arrayCount = resolve<ArrayCount>(library, "ear_array_get_count");
    arrayGet = resolve<ArrayGet>(library, "ear_array_obj_get");
    arrayRelease = resolve<ArrayRelease>(library, "ear_array_release");
    streamKind = resolve<StreamKind>(library, "jup_stream_get_kind");
    streamDirection = resolve<StreamDirection>(library, "jup_stream_get_dir");
    setTalkerLevel =
        resolve<SetTalkerLevel>(library, "jup_stream_audio_rx_set_talker_level");
    return ready();
  }

  bool ready() const {
    return getJCall && getStreamArray && arrayCount && arrayGet && arrayRelease && streamKind &&
           streamDirection && setTalkerLevel;
  }
};

Api api;

bool alignedPointer(std::uintptr_t value) {
  return value > 0x10000 && (value & (alignof(void*) - 1)) == 0;
}

bool readableRange(std::uintptr_t value, std::size_t size) {
  if (!alignedPointer(value) || size == 0) return false;
  FILE* maps = std::fopen("/proc/self/maps", "r");
  if (maps == nullptr) return false;
  char line[512];
  bool readable = false;
  while (std::fgets(line, sizeof(line), maps) != nullptr) {
    unsigned long long start = 0;
    unsigned long long end = 0;
    char permissions[5] = {};
    if (std::sscanf(line, "%llx-%llx %4s", &start, &end, permissions) == 3 &&
        permissions[0] == 'r' && value >= start && value + size >= value &&
        value + size <= end) {
      readable = true;
      break;
    }
  }
  std::fclose(maps);
  return readable;
}

bool belongsToAndromeda(std::uintptr_t object) {
  if (!readableRange(object, sizeof(void*))) return false;
  const auto vtable = *reinterpret_cast<std::uintptr_t*>(object);
  if (!alignedPointer(vtable)) return false;
  Dl_info info{};
  return dladdr(reinterpret_cast<void*>(vtable), &info) != 0 && info.dli_fname != nullptr &&
         std::strstr(info.dli_fname, "libandromeda.so") != nullptr;
}

void* getAmpAudio(std::uintptr_t audioSessionStream) {
  // LINE 26.14.0 arm64: AudioSessionStream owns shared_ptr<AudioProtocolStream> at +0x30.
  // PlanetAudioInterface stores its AmpPlnAudio handle at +0x20. Java version-gates this bridge.
  if (!readableRange(audioSessionStream, 0x38)) return nullptr;
  auto* session = reinterpret_cast<std::uint8_t*>(audioSessionStream);
  auto protocolValue = *reinterpret_cast<std::uintptr_t*>(session + 0x30);
  if (!belongsToAndromeda(protocolValue) || !readableRange(protocolValue, 0x28)) return nullptr;
  auto* protocol = reinterpret_cast<std::uint8_t*>(protocolValue);
  auto ampValue = *reinterpret_cast<std::uintptr_t*>(protocol + 0x20);
  return readableRange(ampValue, 0x18) ? reinterpret_cast<void*>(ampValue) : nullptr;
}

float percentToDb(float multiplier) {
  if (multiplier <= 0.0001f) return -100.0f;
  return 20.0f * std::log10(multiplier);
}

}  // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_dev_vector_lineextension_hooks_NativeCallBridge_nativeSetParticipantVolume(
    JNIEnv* env, jclass, jlong audio_session_stream, jstring participant_id, jfloat multiplier) {
  if (participant_id == nullptr || multiplier < 0.0f || multiplier > 2.0f) {
    __android_log_print(ANDROID_LOG_ERROR, kTag, "Invalid JNI arguments");
    return JNI_FALSE;
  }
  if (!api.load()) {
    __android_log_print(ANDROID_LOG_ERROR, kTag, "Andromeda API resolution failed");
    return JNI_FALSE;
  }
  void* ampAudio = getAmpAudio(static_cast<std::uintptr_t>(audio_session_stream));
  if (ampAudio == nullptr) {
    __android_log_print(ANDROID_LOG_ERROR, kTag, "Amp audio lookup failed for stream=%p",
                        reinterpret_cast<void*>(audio_session_stream));
    return JNI_FALSE;
  }
  void* call = api.getJCall(ampAudio);
  if (call == nullptr) {
    __android_log_print(ANDROID_LOG_ERROR, kTag, "JCall lookup failed");
    return JNI_FALSE;
  }
  void* streams = api.getStreamArray(call, kAudioMediaKind);
  if (streams == nullptr) {
    __android_log_print(ANDROID_LOG_ERROR, kTag, "Audio stream array lookup failed");
    return JNI_FALSE;
  }

  const char* id = env->GetStringUTFChars(participant_id, nullptr);
  if (id == nullptr) {
    api.arrayRelease(streams);
    return JNI_FALSE;
  }
  const float db = percentToDb(multiplier);
  bool queued = false;
  const int count = api.arrayCount(streams);
  __android_log_print(ANDROID_LOG_INFO, kTag, "Trying participant volume on %d streams", count);
  for (int index = 0; index < count; ++index) {
    void* stream = api.arrayGet(streams, index);
    if (stream == nullptr || api.streamKind(stream) != kAudioMediaKind) continue;
    // The native function validates RX/mix streams. Supplying the participant ID lets LINE's
    // own user-to-SSRC callback resolve the current channel, including after reconnects.
    if (api.setTalkerLevel(stream, id, "", true, db, nullptr, nullptr, nullptr)) queued = true;
  }
  env->ReleaseStringUTFChars(participant_id, id);
  api.arrayRelease(streams);
  if (!queued) {
    __android_log_print(ANDROID_LOG_WARN, kTag, "No RX stream accepted participant volume");
  }
  return queued ? JNI_TRUE : JNI_FALSE;
}
