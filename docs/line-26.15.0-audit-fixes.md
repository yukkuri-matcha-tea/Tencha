# LINE 26.15.0 audit fixes (2026-10-01)

Target: LINE 26.15.0, versionCode 261500177. Mappings were derived from the supplied APK's DEX declarations, smali bodies, resource IDs and call relationships; class existence alone was not used as evidence.

## Changes

- Replaced inherited account-profile, selection, camera, video, Compose navigation, Agent I and plus-menu mappings with the 26.15.0 implementations. Corrected navigation drawable/container IDs and the plus-menu icon argument index.
- Resolve this account's display name from its profile, not the first displayed participant. Clear the previous call's self name on session transitions.
- Observe successful own-message inserts without altering the original SQLite result. Read back committed text rows through an independent read-only connection and require a fixed status and server ID before TTS. Exclude failed, media and system rows, deduplicate pending row checks, and reject callbacks from an ended call session.
- Stop exposing the retired individual-volume and microphone-meter settings; retain soundboard and TTS.
- Permit retry after clickable-hook registration failure. Bind ordinary ConstraintLayout settings rows through their child resource IDs instead of calling absent custom setters.

## Verification

Run the read-only declaration checker against extracted DEX files:

```text
python tools/inspect-line-dex.py <dex-directory> --verify-json docs/line-26.15.0-audit-mappings.json
```

Result: 35/35 declarations passed. Literal-search hits from the helper are candidates only and require inspection of smali before use.

```text
gradlew.bat spotlessApply testDebugUnitTest lintDebug assembleDebug --console=plain
```

Result: build succeeded; 31 unit tests, zero failures/errors; lintDebug completed. This does not claim that all lint warnings were eliminated. `git diff --check` passed.

Output: `app/build/outputs/apk/debug/app-debug.apk`, existing version 1.9.0 (10900). SHA-256: `9EEBBCB6596FB1DD5C03D6C4BE2D9C06F5B30CE124025D92F49F1E48CF2DEDF9`.

No installation, LINE restart, message sending or call initiation was performed for this audit-fix run. Real-device UI, TTS playback and call regression checks remain unverified. In particular, own-message TTS now waits for the committed fixed row; offline/pending sends are not spoken until confirmation, and checks expire after 60 seconds.
