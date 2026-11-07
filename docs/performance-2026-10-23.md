# Runtime performance budgets and bootstrap recovery (2026-10-23)

## Scope

Tencha reads RAM, low-RAM status, the host process's heap limit and available
CPU count inside Android. Model and SoC are diagnostic labels, not model-specific
tuning rules. No ADB-derived constants, root configuration, CPU clock changes or
system-wide tuning are used by the implementation.

The process-local profile sizes PCM and image caches and bounds optional
background image work. Settings snapshots avoid repeated provider reads;
short-lived member-name caching avoids repeated database opens. Memory-pressure
callbacks discard cache ownership without modifying buffers still being played.
Soundboard tap rate and existing clip-size limits are unchanged.

## Bootstrap regression and correction

The first implementation registered component callbacks through the Application
while LINE was still inside attachBaseContext. Its base context was not attached,
causing a NullPointerException and stopping subsequent module hook registration.

Memory queries now use the attached supplied context. Callback registration is
posted to the main looper and catches runtime failures. Main also isolates the
optional performance initialization so its failure cannot abort feature hooks.

## Verification

- Corrected debug APK built, installed with settings preserved, LINE restarted.
- LINE 26.15.1 process 16500 logged settings-button, unsend indicator, image,
  TTS message and custom-font hook registration after performance initialization.
- That process reported 11,661,799,424 bytes RAM, 536,870,912-byte heap,
  8 CPU cores, 16 MiB PCM cache and 2 optional I/O workers. These values were
  obtained by app code in LINE, not injected from the connected-device baseline.
- 84 host unit tests passed, including cache bounds/eviction, snapshot
  single-flight loading and low-memory device profiles.
- Formatting/build checks and lintDebug passed; git diff --check passed.

Hook registration and successful launch are not proof of every hooked feature's
interactive behavior. No call, message or reaction was initiated for this check.
No end-to-end speedup percentage, black-screen cure or long-call audio result is
claimed. Large audio clips still require their existing full PCM allocation.
