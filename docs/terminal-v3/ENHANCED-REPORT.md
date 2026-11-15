# Vantra Terminal V3 — Stage 4 Enhanced

## Deliverable and status

Full project continued from the latest completed Stage 4 source (`Vantra-Terminal-V3-Final.zip`). This is an enhancement of its existing Kotlin/Compose/native Canvas architecture. No graphics/audio dependency, backend, permission or unrelated feature was added.

**70 JVM tests passed.** Signing suite 53/53 and restore Journal assertions 15/15 passed. Full Android/Compose compilation is blocked at Gradle distribution download; there is no APK and no Android-device acceptance or production-readiness claim. Earlier stage reports are preserved as history; this report describes the current changed behaviour.

## Full typing implementation

The previous renderer always drew complete code/lyric strings, then brightened a lyric-only prefix. `TerminalTypingTimeline.kt` replaces that presentation with a real range-reveal schedule over the existing document's original rows.

- Immutable primitive grapheme-boundary/cumulative-weight tables are built once per measured document. Cadence varies deterministically by row/character position, with pauses around punctuation, lower costs for indentation spaces and explicit line-break costs. BreakIterator keeps the same Unicode character-boundary approach as the existing document implementation.
- Each group includes **all** prefix rows, the declaration/call leading into the string, wrapped lyric segments, closing quotes/operators and postlude rows. Headers/comments and rear code planes also use reveal ranges. No text is rewritten, no alternate string frames are stored and no per-character substrings/objects are created by the drawing path.
- Prefix code can prepare before the real line timestamp (up to 900 ms at normal speed, bounded by available preceding time). Lyric-string characters start at that timestamp. If the song begins directly at its first line, a declaration bootstrap of at most 140 ms (also interval-bounded) is necessary to make its prefix type at all; the active lyric **line** still changes at its original timestamp.
- Main and suffix durations are capped inside the next real lyric interval. Slow/normal/fast settings change cadence, while fast or duplicate timestamps cannot leave unfinished old blocks accumulating behind playback. The last line uses a bounded default interval. Already finished groups stay complete; the entire document never restarts on a new line.
- Rendering clips the existing syntax spans at the current grapheme boundary and calls Canvas.drawText on the **original String with start/end indices**. It does not paint a dim complete future lyric underneath. Token colours remain the document's precomputed syntax colours; this is range-clipped highlighting rather than re-lexing/allocating a new prefix every character.
- Current time plus the existing lyric offset is authoritative. A seek, pause or repeated read at the same time produces exactly the same reveal state. Speed/preferences update without recreating a typing coroutine. Stage 4 still owns the single frame loop.
- The full-code caret comes from that same schedule; it eases/blinks using the existing renderer clock. Long wrapped-lyric viewport focus is mapped to the actually typed row, fixing a potential mismatch with the old lyric-only reveal speed. Folded code outside the readable viewport remains folded; it is not forcibly scrolled over the real active lyric.
- Typing OFF/system reduced motion draws complete strings immediately. Stage 1 typography/palette/gutters and Stage 2 depth/framing remain. With typing enabled, future code/lyrics now wait for their schedule, an intentional requested change from the old fully visible future context.

`ENHANCED-TYPING-PROBE.txt` is actual output from the production timeline, showing a real row progressing through `c`, `co`, `con`, `cons`, `const`, and the rest. The console probe uses readable test substrings only outside the app; production rendering does not allocate those frames. It is not a recording of Android native rendering.

## Bass/onset analysis and limitations

### Actual source path

The inherited Media3 `CrossmixClockSink` taps timestamped decoded input into the existing `AudioSpectrum` before effects/mixing. Terminal reads that spectrum at the current player's position, with its existing stream-offset alignment and stale-frame rejection. This preserves current 16-/24-/32-bit/float PCM support and avoids microphone capture. Offload/encoded bypass, unavailable/unsupported PCM and remote Cast do not invent onsets; they retain the explicitly labelled lyric/playback fallback. Output preferences are not overridden.

### Detector changes

The existing detector already had low-frequency smoothing, half-wave spectral flux, adaptive mean/deviation, warmup and refractory gating. Its flux also included upper low/mid bins: a test with steady bass and changing upper bins produced **19 false bass kicks** before this enhancement.

The enhanced detector separately measures positive rise in the lowest eight bands (roughly 45–150 Hz in the inherited log spectrum). An onset now also requires low-band rise above a floor and its adaptive mean plus deviation. Changing upper bins alone cannot qualify. Full flux still uses its adaptive threshold and existing 300 ms warmup / 140 ms refractory interval.

A relative low-band power proxy is reconstructed from the spectrum's normalised dB scale and smoothed with 18 ms attack / 180 ms release. A slower local floor provides a bounded bass-drive value for medium depth motion. This is an **uncalibrated relative proxy**, not a physical RMS/SPL/LUFS measurement; the spectrum includes its existing band tilt.

### Camera response

For this enhanced app path, the **new detected onset event** kicks the retained bass spring once. It is not repeatedly kicked by an envelope on every frame. A critically damped response (stiffness 225, damping 30) releases the impulse; medium bass supplies only a small sustained depth target. Bass offset is bounded to 0.09 focal-distance units and has a small matching view-pitch component. Strong actual rises can therefore make a short impactful push; steady/soft passages do not generate arbitrary shakes. Settings and manual focus may reduce the available bass budget.

The legacy default camera call signature remains compatible for previous tests/callers; Terminal passes the new retained intent to enable the enhanced path. Old velocity/pulse state is cleared for real seeks/track/player changes; ordinary pause allows a controlled settle rather than snapping a large impulse away.

### Practical limits

- No fixed BPM grid, random shake, guessed beat timestamps or timer-based “beat” source was added. The tests use explicitly generated PCM/band inputs; the app does not generate those signals.
- Bass below the inherited approximately 45 Hz lower band edge is not directly represented. FFT window/hop size, interpolation and frame sampling limit temporal resolution. Warmup intentionally suppresses startup/seek transients.
- This remains a heuristic onset detector, not universal beat/downbeat/drop understanding. A drop can create a strong actual low-band onset and impulse; no separate claim of semantic “drop detection” is made.
- The tap is pre-effects/pre-mix. Crossfade-selected song data, output DSP, Bluetooth/device latency and the audible result can differ. No live song, Bluetooth/output or device-perceptual alignment was measured.
- Cast uses its existing approximately 500 ms reported position updates and no local PCM reaction. Marker/camera target changes ease across those reports rather than pretending to have a sample-accurate remote audio clock.

## Emotional emphasis and camera priority

There is no reliable automatic lyric-level emotional analyser in this implementation. No keyword list or network AI service guesses meaning. Humans supply explicit per-track markers.

`TerminalEmphasis.kt` validates/sorts up to 128 markers per song. It samples a deterministic absolute-time envelope into a reused frame: 200 ms anticipation/stabilisation, 400 ms quintic push toward the marked peak, a hold for 30% of the marker duration and a quintic release. Types are push, power, gentle, still and climax. Overlaps combine by bounded maximum focus/stillness, never by summing unlimited zooms.

Marker time uses the **same lyric-offset-adjusted clock** as the real words. Sampling is a pure function of time, so seeking reconstructs the appropriate marker phase and pausing cannot advance its envelope. Local seek reconstructs focus immediately. Normal timed curves pass through the camera without a second slow zoom filter that would miss the marked peak; a rate limit protects abrupt setting edits/coarse remote-clock changes. A regression test reproduced the former filtered peak arriving below its intended value at the marker and now verifies the correct peak.

One retained `TerminalCameraIntent` feeds the existing camera controller. Composition order:

1. Existing projected lyric safe-region correction/readability.
2. Manual focus (max 0.14 focal-distance units in the compositor).
3. Bass (only current analysed onsets/energy; budget reduced during focus).
4. Continuous Stage 2 drift, which is damped during focus and suppressed during stillness.
5. Background typing, which never drives a competing camera controller.

The total positive focal-distance offset is capped at 0.18; lower-priority drift gives way before focus. Still markers stabilise continuous motion and suppress bass. Climax markers allow a controlled extra share of the bass response **only when a real onset exists**; they do not create beats. Safety fitting can reduce visible zoom to keep text inside the viewport. There are no independent zoom coroutines fighting each other.

## Settings and manual marker guide

All controls live inside the existing Terminal OptionSheet:

| Control | Values/default |
| --- | --- |
| Typing animation | Existing switch/key, unchanged |
| Typing speed | Slow 0.65×, Normal 1× default, Fast 1.6×; interval caps still apply |
| Beat-reactive camera | Existing switch/key, unchanged |
| Bass response | Off / Subtle 0.55 / Normal 1 default / Strong 1.6 |
| Emotional zoom | Off / Subtle 0.55 / Normal 1 default / Strong 1.5 |
| Edit emphasis markers | Inline editor for the track selected when it was opened |

Example format (one marker per line):

```
00:12.500 | push | 1.0 | 2400
00:28.000 | power | 1.1 | 1800
00:42.250 | gentle | 0.7 | 3000
00:55.000 | still | 1.0 | 1600
01:08.500 | climax | 1.0 | 2200
```

These are examples only; **no sample markers are shipped as song data**. Time is the desired focus peak on the displayed lyric clock; duration is the post-peak hold/release interval. Allowed amounts 0..1.5, duration 600..8000 ms. Empty text removes only that track's markers. No markers means the normal cinematic/bass scene, with no automatic emotional zoom.

The editor validates before saving, binds the save target to the originally selected track even if playback skips, preserves other track entries, caps per-track text/marker count and bounds total stored JSON. It is inline so it stays inside the existing rotated settings tree rather than opening an unrotated dialog. Existing DataStore helpers persist new enum keys and a per-track marker map; all previous keys/values remain unchanged. Android keyboard behaviour and persistence across process restarts still require device tests.

## Verification actually performed

- **70 JVM JUnit tests passed**, zero final failures: all 55 prior-stage tests unchanged plus 15 enhancement tests. They cover full-row progression, strings/timestamps, grapheme boundaries, fast intervals, speed/caret, long wrapped viewport alignment, marker parsing/envelopes/defaults, false upper-band “bass”, single impulse/settling, composition limits, unsupported audio/stillness, portrait/landscape projected safe regions, 30/120 Hz response and manual peak timing.
- Real production FFT/PCM and existing detector tests still pass, including generated PCM encodings, decoded steady tone (no false beat), actual synthetic low-frequency pulses, stream offsets/flush and stale history.
- Kotlin 2.3.10 standalone compilation passed for the actual core, renderer, detector, timeline, emphasis and tests. Compile-time Android graphics stubs and minimal Media3 API stand-ins were used only in the local harness; none is added to the project. This is not full Android/Compose type-resolution compilation.
- Compiler PSI syntax parsing passed for updated Terminal UI/options/preferences/storage helpers. It does not prove Android/Compose API compatibility or UI execution.
- Existing signing suite: **53 passed, 0 failed**, with a throwaway key. No release signing key/config was modified.
- Existing real restore Journal scenarios: **15 assertions passed** (files/media/preferences/DataStore/rollback/interruption/undo).
- Actual full-build command `./gradlew :app:compileArm64FossDebugKotlin :app:testArm64FossDebugUnitTest --no-daemon` failed downloading the configured Gradle 9.3.1 distribution: `java.net.SocketException: Network is unreachable`. Full app compilation/unit suite and APK generation did not succeed.
- No Android device/emulator, native screenshots, real-song auditory sync, GPU/jank/GC/battery profiling or independent code review was performed. Portrait/landscape results are math/source checks, not device acceptance.

The raw verification file labels expected pre-fix failures separately from final passing output. Previous stage reports remain historical; their old reveal/weak-camera descriptions are superseded here.

## Preservation and known limitations

Only seven existing files change: Terminal renderer, camera, scene, detector, sync state, Terminal options and the Terminal additions to PreferenceKeys. The constants file's old content and Terminal options' old code were separately compared after removing the new additions; they match the baseline. Stage 4 frame/native controls, lifecycle/inset cleanup, document/lexer, all previous tests, shared styles, signing, manifest, Gradle/dependencies, playback/audio sink/spectrum, GitHub, backup and streak files remain byte-identical. Exact counts/list and CRC checks are in `ENHANCED-ARCHIVE-AUDIT.txt` and `ENHANCED-CHANGED-FILES.txt`.

Real device checks remain essential: native text/fallback fonts/RTL and large sizes, very small windows, controls/marker editor/IME in both orientations, pause/resume/seek/skip/rotation, marker persistence and wrong-track protection, source changes, offload/Cast fallback, real bass perception, style switching and performance. Unicode segmentation follows the platform character iterator and is not a new universal extended-emoji shaper. Cadence is compressed for fast songs, not an ASR-derived word timing system. Synthetic tests and code audits do not establish a production-ready APK.
