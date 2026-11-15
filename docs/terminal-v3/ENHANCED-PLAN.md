# Stage 4 enhancement

Continue the final Stage 4 archive, preserving the single-scene rotation/lifecycle/accessibility fixes.

1. Build a deterministic, weighted, grapheme-aware full-code typing timeline from the existing document. Prefix scaffolding anticipates real lyric timestamps, lyric strings begin at their real timestamp, suffixes finish inside each interval. Draw only revealed original String ranges, including rear code planes; reconstruct at arbitrary seeks, no per-character frames or coroutines.
2. Tighten the real detector with independent adaptive low-band rise gating and attack/release energy proxies. Use detected events once for a bounded impulse, not a weak double-smoothed target; retain source gating and warmup.
3. Add validated per-track timestamp markers (push/power/gentle/still/climax) and a pure anticipation/hold/release timeline. No keyword inference or invented automatic emotion analysis.
4. Compose existing drift, marker focus and bass in the existing camera/frame owner: safe framing first, then markers, then bass, then drift. Bound total dolly/tilt; damp lower priorities during focus/stillness; use actual audio only for climax cooperation.
5. Add persistent typing speed, bass gain, emotional zoom and a current-track marker editor inside the existing Terminal options. Keep all existing keys/features and all other styles.
6. Test pure timelines, Unicode/seek/rate/pause behaviour, low-only detection, priority/bounds, marker validation and old regression suites. Attempt Android Gradle and report actual limits. Deliver full source and exact byte audit.
