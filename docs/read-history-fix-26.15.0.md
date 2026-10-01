# LINE 26.15.0 read-history notification fix

The previous `in8.y1` mapping was incorrect: its constructor subscribes to `NOTIFIED_PREMIUMBACKUP_STATE_CHANGED`. The actual read-receipt handler is `in8.a2`, whose constructor subscribes to `NOTIFIED_READ_MESSAGE`.

Verified in the supplied APK's smali:

- `in8.a2.b(hn8.u0, gp8.de, Continuation)` reads operation fields `g`, `h`, `i`, and `b` and forwards them to `gm8.b.c(long, String, String, long, gm8.b$b)`.
- Fields represent the chat, reader, last-read server message ID and operation timestamp used by the existing recorder. This observes notifications available to the client; it cannot recover every historical read time or information not delivered by LINE.
- Hook callback argument/type guards preserve the original handler result. Failures log only an exception class, not message text, chat IDs or reader IDs.
- Existing read-blocking hooks and stored history were not removed or reset.

Verification: APK declaration checker 41/41 passed; 32 unit tests with zero failures/errors; `lintDebug`, `assembleDebug` and `git diff --check` passed. A regression test asserts the read-notification handler and all consumed fields. Declaration checks alone are not semantic proof; the constructor's event constant and method body were inspected separately.

No device installation or actual receipt/history UI test was performed for this fix. The generated debug APK retains version 1.9.0; no release was published in this run.

## 1.9.1 packaging and installation

The subsequent installation/publishing request packages this fix as 1.9.1 (10901). `spotlessCheck`, all unit tests, `lintDebug` and `assembleDebug` passed again. The installed APK and new APK share certificate SHA-256 `fd54f8c2099c53528753a59262a5203801995eff926e1151ab69ffdf54355615`. ADB replacement installation succeeded and the package manager reported 1.9.1 (10901). LINE was not force-stopped, and receipt/history runtime behavior remains unverified; restart LINE to load the updated module.
