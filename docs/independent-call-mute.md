# Independent microphone mute

Implemented for verified LINE 26.14.0 / 26.15.0 recording-mixer paths. Native installation remains lazy (on mute or audio playback), not during VoIP engine construction.

The LINE settings screen exposes `チャット → 通話 → ミュート中も追加音声を送信` (`independent_call_mute`), default OFF. Enabling/disabling follows the existing LINE-restart workflow: the hook is registered only when enabled at process startup. A setting change during a call does not release an active privacy gate. With the option OFF after restart, the original LINE mute path is untouched. The description warns that local mute indication does not guarantee a remote mute indication; remote display remains unverified.

For LINE's mic toggle, `com.linecorp.andromeda.core.d.h1(boolean, boolean)` maintains the original UI/event state. The inner `SessionJNIImpl.o(int, long, boolean)` call has only the TX (`id=1`) mute argument replaced with false, and only if the physical-mic PCM gate is successfully installed. The original native TX call is executed, not skipped, to recover a previously muted stream. Mixer installation failure leaves the original LINE mute intact.

The recorder callback clears physical microphone PCM before acquiring the soundboard/TTS queue lock. A contended lock cannot let captured speech through. The mixer then adds only the requested soundboard/TTS audio; normal unmute restores microphone mixing. Android's own microphone-mute setter is not globally intercepted.

Mic state is atomic and independent of queue writes. It is not reset on hiding the controls, leaving the call UI, or an audio-device teardown; those are not proof that the call ended. It is released by an explicit unmute or successful release of the captured native TX session (`SessionJNIImpl.g0(long)` returning 0).

APK evidence: 26.15.0 `h1` calls TX mute synchronously, updates its mute state and emits `AudioControl.b`. `SessionJNIImpl.o` delegates to `nAudioStreamSetMute`; `g0` delegates to `nSessionRelease`. The native recording callback at virtual address `0x441f1c` in the supplied arm64 split has the expected four prologue words `d10183ff a9037bfd f90023f5 a9054ff4`. Runtime signature checking is retained.

Regression coverage includes TX-only replacement and fail-closed Java policy, plus native compile-time assertions for mic silence, soundboard/TTS while muted, normal mic mixing and positive/negative clipping. These checks do not prove remote audibility or actual mic privacy on the device; live mute/unmute, soundboard/TTS, route switching and call reconnection tests remain required. This change was not installed or published in this implementation run.

## 1.9.2 packaging

The subsequent install/publish request packages the feature and its opt-in setting as 1.9.2 (10902). APK declarations passed 44/44 checks; 34 Java unit tests, native compile-time assertions, `spotlessCheck`, `lintDebug`, and `assembleDebug` passed. ADB replacement installation succeeded, and the package manager reported 1.9.2 (10902). LINE was not force-stopped for this packaging request. Installation is not runtime audio verification; the behavior and remote mute indication remain unverified. README download links, setting instructions and warnings were updated for this version.
