# Stage 4 Enhanced — changelog

Continues `Vantra-Terminal-V3-Final.zip`; retains Stage 4's single-scene rotation, native controls, lifecycle/inset cleanup, accessibility, quality options and previous integration fixes.

- Real full-code reveal: declarations, functions/arguments, comments, operators, quotes, lyric strings, braces, indentation and row breaks share a deterministic weighted typing schedule. Renderer paints only revealed ranges of original strings, including rear planes. No full future strings painted underneath.
- Natural deterministic cadence and punctuation pauses; prelude anticipates line timestamps, tail fits the interval, fast songs compress safely. Original lyric line timestamps still select the active lyric. Very long wrapped lyrics now follow the actual code caret rather than the old lyric-only typing speed.
- Seek/paused time reconstructs the same text immediately. No new typing loop or per-character frame/string objects. Primitive grapheme/cost tables built per measured document; binary searches during drawing. Typing OFF/reduced motion restores complete strings.
- Bass detector now requires independent adaptive low-band transient evidence, preventing changing upper bands over steady bass from being called bass beats. Added attack/release relative low-band power/drive. Current decoded-PCM gate, onset warmup and refractory period remain.
- Enhanced camera uses a detected onset once for a short bounded impulse, rather than feeding a slow filter with an already smoothed envelope. Adds controlled low-band depth and subtle perspective emphasis; no random shake or haptic changes.
- Manual per-track timestamp markers: push, power, gentle, still and climax. Smooth anticipation/push/hold/release, bounded edit transitions and immediate local-seek reconstruction. No keyword-based emotional inference or fabricated automatic analysis.
- One camera compositor prioritises safe lyric framing, manual focus, actual bass, continuous drift, then background typing. Total focal-distance offset capped; focus/stillness suppress lower-priority motion. Climax cooperation requires real onsets.
- Existing Terminal settings now include typing speed, bass intensity, emotional zoom intensity and an inline current-track marker editor. Existing switches/keys remain; new preferences use the existing DataStore architecture. Editor validation, per-track identity binding and bounded storage preserve unrelated entries.

Verification: 70 JVM tests pass; signing 53/53; real restore Journal scenarios 15/15. UI/preferences files pass syntax parsing. Android/Compose build remains blocked downloading Gradle (`Network is unreachable`); no APK or Android device/performance verification. No production-readiness claim.

See `docs/terminal-v3/ENHANCED-REPORT.md`, `ENHANCED-CHANGED-FILES.txt`, `ENHANCED-VERIFICATION.txt`, `ENHANCED-TYPING-PROBE.txt` and `ENHANCED-ARCHIVE-AUDIT.txt`. Earlier stage reports remain historical and are superseded by this enhancement report for changed behaviour.
