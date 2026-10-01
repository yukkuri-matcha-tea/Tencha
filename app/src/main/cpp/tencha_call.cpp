#include <jni.h>

#include <android/log.h>
#include <dlfcn.h>
#include <link.h>
#include <sys/mman.h>
#include <unistd.h>
#include <algorithm>
#include <cmath>
#include <cstdint>
#include <cstring>
#include <cstdio>
#include <mutex>
#include <string>
#include <vector>

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
  using StreamString = const char* (*)(void*);
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
  StreamString streamSourceUserId = nullptr;
  StreamString streamSourceServiceId = nullptr;
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
    streamSourceUserId = resolve<StreamString>(library, "jup_stream_get_src_userid");
    streamSourceServiceId = resolve<StreamString>(library, "jup_stream_get_src_svc_id");
    setTalkerLevel =
        resolve<SetTalkerLevel>(library, "jup_stream_audio_rx_set_talker_level");
    return ready();
  }

  bool ready() const {
    return getJCall && getStreamArray && arrayCount && arrayGet && arrayRelease && streamKind &&
           streamDirection && streamSourceUserId && streamSourceServiceId && setTalkerLevel;
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
  // AudioSessionStream calls the audio interface through the +0x10 secondary base. The verified
  // PlanetAudioInterface implementation then reads AmpPlnAudio at +0x20 from that adjusted this,
  // so the handle is AudioProtocolStream + 0x30 (not +0x20). Java version-gates this bridge.
  if (!readableRange(audioSessionStream, 0x38)) return nullptr;
  auto* session = reinterpret_cast<std::uint8_t*>(audioSessionStream);
  auto protocolValue = *reinterpret_cast<std::uintptr_t*>(session + 0x30);
  if (!belongsToAndromeda(protocolValue) || !readableRange(protocolValue, 0x38)) return nullptr;
  auto* protocol = reinterpret_cast<std::uint8_t*>(protocolValue);
  auto ampValue = *reinterpret_cast<std::uintptr_t*>(protocol + 0x30);
  return readableRange(ampValue, 0x18) ? reinterpret_cast<void*>(ampValue) : nullptr;
}

float percentToDb(float multiplier) {
  if (multiplier <= 0.0001f) return -100.0f;
  return 20.0f * std::log10(multiplier);
}

std::string userPart(const char* identity) {
  if (identity == nullptr) return {};
  const char* separator = std::strchr(identity, '@');
  return separator == nullptr ? std::string(identity)
                              : std::string(identity, static_cast<std::size_t>(separator - identity));
}

std::string servicePart(const char* identity) {
  if (identity == nullptr) return {};
  const char* separator = std::strchr(identity, '@');
  return separator == nullptr || separator[1] == '\0' ? std::string() : std::string(separator + 1);
}

bool sameUser(const char* left, const char* right) {
  const std::string leftUser = userPart(left);
  const std::string rightUser = userPart(right);
  return !leftUser.empty() && leftUser == rightUser;
}

void addUnique(std::vector<std::string>* values, const char* value) {
  if (value == nullptr || value[0] == '\0') return;
  for (const std::string& existing : *values) {
    if (existing == value) return;
  }
  values->emplace_back(value);
}

constexpr std::uintptr_t kRecordCallbackOffset = 0x441f1c;
constexpr int kSoundboardSampleRate = 16000;

std::mutex soundboardMutex;
std::vector<std::int64_t> soundboardTimeline;
double soundboardPosition = 0.0;
std::vector<std::int64_t> ttsTimeline;
double ttsPosition = 0.0;
bool physicalMicMuted = false;
bool soundboardFormatLogged = false;

using RecordCallback = void (*)(void*, void*);
RecordCallback originalRecordCallback = nullptr;
std::mutex hookMutex;

int findAndromedaBase(struct dl_phdr_info* info, size_t, void* data) {
  if (info->dlpi_name == nullptr ||
      std::strstr(info->dlpi_name, "libandromeda.so") == nullptr) {
    return 0;
  }
  *static_cast<std::uintptr_t*>(data) = static_cast<std::uintptr_t>(info->dlpi_addr);
  return 1;
}

void mixSoundboardIntoRecording(void* recorder) {
  if (recorder == nullptr || !soundboardMutex.try_lock()) return;
  if (soundboardTimeline.empty() && ttsTimeline.empty() && !physicalMicMuted) {
    soundboardMutex.unlock();
    return;
  }
  auto* bytes = reinterpret_cast<std::uint8_t*>(recorder);
  const int channelCount = *reinterpret_cast<int*>(bytes + 0x38);
  const int byteCount = *reinterpret_cast<int*>(bytes + 0x3c);
  auto* pcm = *reinterpret_cast<std::int16_t**>(bytes + 0x40);
  const int sampleRate = *reinterpret_cast<int*>(bytes + 0x48);
  if (!soundboardFormatLogged) {
    __android_log_print(ANDROID_LOG_INFO, kTag,
                        "Soundboard recorder format channels=%d rate=%d bytes=%d buffer=%p",
                        channelCount, sampleRate, byteCount, pcm);
    soundboardFormatLogged = true;
  }
  if (channelCount != 1 || sampleRate < 8000 || sampleRate > 192000 || byteCount <= 0 ||
      byteCount > 1024 * 1024 || pcm == nullptr) {
    soundboardMutex.unlock();
    return;
  }

  const std::size_t sampleCount = static_cast<std::size_t>(byteCount) / sizeof(std::int16_t);
  const double step = static_cast<double>(kSoundboardSampleRate) / sampleRate;
  for (std::size_t outputIndex = 0; outputIndex < sampleCount; ++outputIndex) {
    const std::size_t sourceIndex = static_cast<std::size_t>(soundboardPosition);
    const std::size_t ttsIndex = static_cast<std::size_t>(ttsPosition);
    std::int64_t mixed = physicalMicMuted ? 0 : static_cast<std::int64_t>(pcm[outputIndex]);
    if (sourceIndex < soundboardTimeline.size()) mixed += soundboardTimeline[sourceIndex];
    if (ttsIndex < ttsTimeline.size()) mixed += ttsTimeline[ttsIndex];
    mixed = std::max<std::int64_t>(INT16_MIN, std::min<std::int64_t>(INT16_MAX, mixed));
    pcm[outputIndex] = static_cast<std::int16_t>(mixed);
    if (!soundboardTimeline.empty()) soundboardPosition += step;
    if (!ttsTimeline.empty()) ttsPosition += step;
  }
  if (ttsPosition >= static_cast<double>(ttsTimeline.size())) {
    ttsTimeline.clear();
    ttsPosition = 0.0;
  }
  if (soundboardPosition >= static_cast<double>(soundboardTimeline.size())) {
    soundboardTimeline.clear();
    soundboardPosition = 0.0;
  }
  soundboardMutex.unlock();
}

void hookedRecordCallback(void* queue, void* recorder) {
  mixSoundboardIntoRecording(recorder);
  if (originalRecordCallback != nullptr) originalRecordCallback(queue, recorder);
}

void writeAbsoluteJump(void* destination, const void* target) {
  const std::uint32_t instructions[] = {0x58000051U, 0xD61F0220U};  // ldr x17, #8; br x17
  std::memcpy(destination, instructions, sizeof(instructions));
  const std::uintptr_t address = reinterpret_cast<std::uintptr_t>(target);
  std::memcpy(reinterpret_cast<std::uint8_t*>(destination) + sizeof(instructions), &address,
              sizeof(address));
}

bool installSoundboardHook() {
  std::lock_guard<std::mutex> guard(hookMutex);
  if (originalRecordCallback != nullptr) return true;

  std::uintptr_t andromedaBase = 0;
  dl_iterate_phdr(findAndromedaBase, &andromedaBase);
  if (andromedaBase == 0) {
    __android_log_print(ANDROID_LOG_ERROR, kTag, "libandromeda mapping not found");
    return false;
  }
  auto* target = reinterpret_cast<void*>(andromedaBase + kRecordCallbackOffset);
  // Verified against LINE 26.14.0 arm64. Fail closed instead of patching another build.
  const std::uint32_t expected[] = {0xD10183FFU, 0xA9037BFDU, 0xF90023F5U, 0xA9054FF4U};
  if (std::memcmp(target, expected, sizeof(expected)) != 0) {
    __android_log_print(ANDROID_LOG_ERROR, kTag, "Recording callback signature changed");
    return false;
  }

  void* trampoline =
      mmap(nullptr, 32, PROT_READ | PROT_WRITE | PROT_EXEC, MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
  if (trampoline == MAP_FAILED) return false;
  std::memcpy(trampoline, target, sizeof(expected));
  writeAbsoluteJump(reinterpret_cast<std::uint8_t*>(trampoline) + sizeof(expected),
                    reinterpret_cast<std::uint8_t*>(target) + sizeof(expected));
  __builtin___clear_cache(reinterpret_cast<char*>(trampoline),
                          reinterpret_cast<char*>(trampoline) + 32);

  const long pageSize = sysconf(_SC_PAGESIZE);
  const std::uintptr_t page =
      reinterpret_cast<std::uintptr_t>(target) & ~(static_cast<std::uintptr_t>(pageSize) - 1U);
  if (mprotect(reinterpret_cast<void*>(page), static_cast<std::size_t>(pageSize),
               PROT_READ | PROT_WRITE | PROT_EXEC) != 0) {
    munmap(trampoline, 32);
    return false;
  }
  originalRecordCallback = reinterpret_cast<RecordCallback>(trampoline);
  writeAbsoluteJump(target, reinterpret_cast<void*>(&hookedRecordCallback));
  __builtin___clear_cache(reinterpret_cast<char*>(target), reinterpret_cast<char*>(target) + 16);
  mprotect(reinterpret_cast<void*>(page), static_cast<std::size_t>(pageSize),
           PROT_READ | PROT_EXEC);
  __android_log_print(ANDROID_LOG_INFO, kTag, "Soundboard single TX mixer installed");
  return true;
}

}  // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_dev_vector_lineextension_hooks_NativeCallBridge_nativeInstallSoundboardMixer(JNIEnv*, jclass) {
  return installSoundboardHook() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_dev_vector_lineextension_hooks_NativeCallBridge_nativeEnqueueSoundboard(JNIEnv* env, jclass,
                                                                              jshortArray pcm) {
  if (pcm == nullptr) return JNI_FALSE;
  const jsize length = env->GetArrayLength(pcm);
  if (length <= 0) return JNI_FALSE;
  std::vector<std::int16_t> samples(static_cast<std::size_t>(length));
  env->GetShortArrayRegion(pcm, 0, length, reinterpret_cast<jshort*>(samples.data()));
  if (env->ExceptionCheck()) return JNI_FALSE;

  std::lock_guard<std::mutex> guard(soundboardMutex);
  std::size_t start = static_cast<std::size_t>(std::ceil(soundboardPosition));
  if (start > kSoundboardSampleRate && start > soundboardTimeline.size() / 2U) {
    const std::size_t consumed = static_cast<std::size_t>(soundboardPosition);
    soundboardTimeline.erase(soundboardTimeline.begin(), soundboardTimeline.begin() + consumed);
    soundboardPosition -= static_cast<double>(consumed);
    start = static_cast<std::size_t>(std::ceil(soundboardPosition));
  }
  if (soundboardTimeline.size() < start + samples.size()) {
    soundboardTimeline.resize(start + samples.size(), 0);
  }
  for (std::size_t i = 0; i < samples.size(); ++i) {
    soundboardTimeline[start + i] += samples[i];
  }
  return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_dev_vector_lineextension_hooks_NativeCallBridge_nativeEnqueueTts(JNIEnv* env, jclass,
                                                                       jshortArray pcm) {
  if (pcm == nullptr) return JNI_FALSE;
  const jsize length = env->GetArrayLength(pcm);
  if (length <= 0) return JNI_FALSE;
  std::vector<std::int16_t> samples(static_cast<std::size_t>(length));
  env->GetShortArrayRegion(pcm, 0, length, reinterpret_cast<jshort*>(samples.data()));
  if (env->ExceptionCheck()) return JNI_FALSE;
  std::lock_guard<std::mutex> guard(soundboardMutex);
  const std::size_t start = std::max(static_cast<std::size_t>(std::ceil(ttsPosition)),
                                     ttsTimeline.size());
  if (ttsTimeline.size() < start + samples.size()) ttsTimeline.resize(start + samples.size(), 0);
  for (std::size_t i = 0; i < samples.size(); ++i) ttsTimeline[start + i] += samples[i];
  return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_dev_vector_lineextension_hooks_NativeCallBridge_nativeClearSoundboard(JNIEnv*, jclass) {
  std::lock_guard<std::mutex> guard(soundboardMutex);
  soundboardTimeline.clear();
  soundboardPosition = 0.0;
}

extern "C" JNIEXPORT void JNICALL
Java_dev_vector_lineextension_hooks_NativeCallBridge_nativeClearTts(JNIEnv*, jclass) {
  std::lock_guard<std::mutex> guard(soundboardMutex);
  ttsTimeline.clear();
  ttsPosition = 0.0;
}

extern "C" JNIEXPORT void JNICALL
Java_dev_vector_lineextension_hooks_NativeCallBridge_nativeSetPhysicalMicMuted(JNIEnv*, jclass,
                                                                                jboolean muted) {
  std::lock_guard<std::mutex> guard(soundboardMutex);
  physicalMicMuted = muted == JNI_TRUE;
}

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
  const std::string targetUser = userPart(id);
  const std::string targetService = servicePart(id);
  bool queued = false;
  const int count = api.arrayCount(streams);
  std::vector<void*> audioStreams;
  std::vector<std::string> serviceIds;
  std::vector<void*> matchingStreams;
  audioStreams.reserve(count > 0 ? static_cast<std::size_t>(count) : 0U);
  __android_log_print(ANDROID_LOG_INFO, kTag, "Resolving participant on %d streams", count);
  for (int index = 0; index < count; ++index) {
    void* stream = api.arrayGet(streams, index);
    if (stream == nullptr || api.streamKind(stream) != kAudioMediaKind) continue;
    audioStreams.push_back(stream);
    const char* sourceUserId = api.streamSourceUserId(stream);
    const char* sourceServiceId = api.streamSourceServiceId(stream);
    addUnique(&serviceIds, sourceServiceId);
    const std::string embeddedService = servicePart(sourceUserId);
    addUnique(&serviceIds, embeddedService.c_str());
    if (sameUser(sourceUserId, id)) matchingStreams.push_back(stream);
    __android_log_print(
        ANDROID_LOG_DEBUG, kTag, "stream[%d] dir=%d sourceUser=%s sourceService=%s match=%d",
        index, api.streamDirection(stream), sourceUserId != nullptr ? sourceUserId : "<none>",
        sourceServiceId != nullptr ? sourceServiceId : "<none>", sameUser(sourceUserId, id));
  }
  addUnique(&serviceIds, targetService.c_str());

  // A group participant is identified by LINE as userId + serviceId. Prefer the exact receive
  // stream metadata, then try the service IDs advertised by the other audio streams. The latter
  // is needed for server-mixed group calls where the mixer stream itself has no source user.
  for (void* stream : matchingStreams) {
    const char* sourceUserId = api.streamSourceUserId(stream);
    const char* sourceServiceId = api.streamSourceServiceId(stream);
    const std::string sourceUser = userPart(sourceUserId);
    std::string sourceService =
        sourceServiceId != nullptr ? std::string(sourceServiceId) : std::string();
    if (sourceService.empty()) sourceService = servicePart(sourceUserId);
    if (!sourceUser.empty() && !sourceService.empty() &&
        api.setTalkerLevel(stream, sourceUser.c_str(), sourceService.c_str(), true, db, nullptr, nullptr,
                           nullptr)) {
      queued = true;
    }
  }
  for (void* stream : audioStreams) {
    for (const std::string& serviceId : serviceIds) {
      if (api.setTalkerLevel(stream, targetUser.c_str(), serviceId.c_str(), true, db, nullptr, nullptr,
                             nullptr)) {
        queued = true;
      }
    }
  }

  // Keep a compatibility fallback for call types whose stream metadata omits a service ID.
  if (serviceIds.empty()) {
    for (void* stream : audioStreams) {
      if (api.setTalkerLevel(stream, targetUser.c_str(), "", true, db, nullptr, nullptr, nullptr)) {
        queued = true;
      }
    }
  }
  env->ReleaseStringUTFChars(participant_id, id);
  api.arrayRelease(streams);
  if (!queued) {
    __android_log_print(ANDROID_LOG_WARN, kTag,
                        "No RX stream accepted participant volume (audio=%zu matching=%zu services=%zu)",
                        audioStreams.size(), matchingStreams.size(), serviceIds.size());
  } else {
    __android_log_print(ANDROID_LOG_INFO, kTag,
                        "Participant volume queued (audio=%zu matching=%zu services=%zu)",
                        audioStreams.size(), matchingStreams.size(), serviceIds.size());
  }
  return queued ? JNI_TRUE : JNI_FALSE;
}
