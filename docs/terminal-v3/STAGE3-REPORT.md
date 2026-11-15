# Vantra Terminal V3 — Stage 3

## Result

The Stage 1 editor and Stage 2 perspective/depth camera remain. Stage 3 adds an explicitly gated audio-onset response, clearer timestamp-driven character reveal, an eased blinking caret and active-block crossfades. No other Vantra feature was rebuilt. No new app dependency, permission, microphone capture, backend or artificial beat schedule was added.

## Audio engine inspected and capability by source

The current service builds Media3 ExoPlayer instances. Each has its own `AudioSpectrum` in `MusicService.playerSpectra`, accessible via `spectrumOf(player)`. `createRenderersFactory` wraps the normal/exclusive router in `CrossmixClockSink`. That sink taps the timestamped input buffer before the equalizer, effects and Crossmix processors. `onStreamOffset` translates decoder timestamps to song positions; configure/flush clear spectrum history. This is the active song's decoded input, not a microphone recording or a measurement after speaker/device processing.

`AudioSpectrum` uses 2,048-sample Hann windows, 1,024-sample overlap hops, a 160-frame history and 40 logarithmic bands from 45 Hz toward 15 kHz (bounded by sample rate). FFT work is on the existing audio-buffer path, not the UI frame loop, and is demand-driven: the existing tap stops analysis after about 1.5 seconds without a reader. Terminal reuses it without allocating new audio processing buffers each frame.

| Playback route | Can provide real analysis? | Stage 3 behaviour |
| --- | --- | --- |
| Local file / downloaded or cached media decoded by ExoPlayer | Yes, when the sink input is supported raw PCM | Read spectrum at current playback position; warm the real onset detector; react only to its output |
| YouTube / resolved network URL / module HLS or other stream | Conditional on successful decoding to the same supported PCM sink path; not determined from the service name or file extension | Identical PCM gate; fall back whenever a fresh spectrum is unavailable |
| Audio Pass / exclusive output | Conditional; the tap is outside/before `PassRouterSink`, so supported raw PCM can be analysed before exclusive routing | Use verified current frames; no assumption that all exclusive drivers/formats work |
| Float or high-resolution raw PCM | Implemented for PCM 16-, 24-, 32-bit and float | Tested with generated PCM buffers; real hardware/output paths still require device tests |
| Encoded passthrough, compressed offload or a tunnel/bypass that does not supply supported PCM | No usable PCM at the tap in that case | No onset-driven response; preserve cinematic motion and timestamp-based lyrics |
| Cast receiver | Remote PCM is not exposed by this local player spectrum | Explicitly disable local FFT reactions and use `castPosition`/`castIsPlaying` from the existing Cast handler |
| Unsupported encoding, just configured/flushed, decode startup, seek or stale history | No valid sample until fresh frames arrive | Safe fallback; re-prime detector rather than manufacture a beat |

Playback routes were traced in source, not exercised on hardware. The runtime decision is a successful current-position `AudioSpectrum.read`, not a hardcoded claim that a provider always supports beats. Audio offload and playback output preferences are not overridden.

### Freshness defect fixed

The previous stale check used two signed distances joined with `&&`. If only a past frame existed, both selected neighbours pointed to that same frame: one distance was negative, so history never expired. The same issue existed for future-only frames. Stage 3 rejects history when the nearest frame is more than 250 ms from the requested position. Output remains zero on failure. This targeted shared-spectrum correction is necessary to prevent old data being presented as current beat analysis.

## Actual synchronization implementation

### One authoritative transport and frame owner

- Terminal now owns one frame invalidation clock and one lifecycle/visibility-owned animation loop. The chrome reads that clock instead of starting `rememberLivePosition`'s additional animated loop.
- The loop reads the current ExoPlayer position, playback speed and `isPlaying` after waiting for the frame. Lyric time is that position plus the existing per-song `lyricsOffsetMs`. It calls the existing deterministic `TerminalDocument.locate`; real lyrics/timestamps are not rewritten.
- While casting, the existing remote reported position and playing state own lyric timing. The Cast handler currently polls at 500 ms; Stage 3 does not invent a smoother remote timeline or claim sample-accurate remote synchronization.
- Actual Media3 `onPositionDiscontinuity` and `onMediaItemTransition` events advance an epoch. The listener is attached to the active service `playerFlow` instance and removed on disposal/player changes. This covers tiny seeks and replay of the same media ID, as well as normal skips and player replacement during transitions.
- `TerminalSynchronization.beginFrame` tracks media ID, player identity, event epoch, playing state and expected position advance at the real playback speed. Initial frames, seeks, pause/resume, buffering transitions, repeated tracks and track/player changes clear old audio state and restart detector warmup. A first boundary sample cannot generate a beat. Old pulses cannot leak into another track.
- Lyric data is checked against the actual current media ID, with a second frame-time guard. Until matching lyrics arrive, the renderer uses the existing `no synced lyrics` document instead of showing a previous track's words.
- Ordinary recomposition, typing/beat/intensity/quality preference changes and orientation changes do not recreate the animation loop or its retained controller. Preferences and current documents are read through `rememberUpdatedState`.

### Real onset reaction and clear fallback

The retained `BeatTracker` still computes half-wave-rectified low-band spectral flux from real spectrum samples, with a 300 ms warmup, adaptive running mean/deviation and 140 ms refractory period. No beat timestamps, BPM estimate or timer pulses are fabricated.

`TerminalSynchronization.react` accepts audio only when playback and reactions are enabled, the current spectrum read succeeded and the frame is not a transport boundary. Its decaying envelope is driven exclusively by detected onset strength. Missing audio immediately clears that envelope.

The Stage 2 camera has a separate opt-in `beatDolly` spring. The app passes `audioReactive=true` only for `BeatSource.AUDIO`. Its target is `0.014 × intensity × onsetEnvelope`, at most 0.0224 focal-distance units at maximum intensity, with critical damping (stiffness 196, damping 28). It adds a small optical-axis emphasis rather than yaw/roll kicks or shaking. Existing angle clamps, depth layers, safe lyric framing and instrumental pullback remain. Real slow section energy modestly broadens lateral motion; bass/lighting changes are smoothed and no white-screen flashes are added.

The existing status line distinguishes:

- `beat: audio onset` — current PCM spectrum is available; onset detector may react after warmup. It does not mean every frame contains a beat.
- `beat: none (lyric timing only)` — no current analysable audio; real lyric timestamps and the Stage 2 cinematic path still drive the scene.
- `beat: none (playback timing only)` — neither current audio analysis nor synced lyrics is available; no invented lyrics or beats.
- `beat: off` — paused/stopped or reactions/intensity/system motion are disabled. Real lyric selection continues independently.

All remaining sine-based cinematic paths are Stage 2 composition motion, never labelled as beat detection.

### Typing, cursor and code transitions

- Character counts remain a pure function of lyric position and original text. The existing measured wrapping and Unicode reveal boundaries remain unchanged. Advancing wall time while paused cannot type more characters; seek recomputes the correct reveal position.
- Stage 1 keeps complete real strings visible as dim context. The active string's untyped portion is now dimmer, and the already typed characters receive a brighter overlay, making character-by-character progression explicit without dropping or replacing words. Disabling typing shows the entire active lyric immediately.
- A single retained caret eases between measured glyph positions, blinks when not actively typing and resets horizontal position at a wrapped row instead of sweeping diagonally through the page. It uses the renderer's existing frame time, without a cursor coroutine.
- `TerminalBlockTransition` crossfades the old/new active lyric highlights and emphasis with a critically damped spring. This changes presentation only. Stage 2 scroll/folding continuity remains.
- Scene rendering and analysis stop when the style is covered/hidden or the lifecycle stops. On return, audio baselines are re-primed. Once paused motion settles, the same loop slows to the existing 265 ms blink polling interval; resume/seeks/settings changes are picked up on its next iteration.
- Intensity OFF disables camera/audio reaction while preserving the typing preference. System reduced motion suppresses camera/caret easing and typing reveal. LOW retains the Stage 2 reduced background layer count and approximately 30 Hz draw cap.

## Changed files

Modified, relative to the complete Stage 2 project:

1. `app/src/main/kotlin/com/vantra/music/playback/audio/AudioSpectrum.kt` — fix stale frame acceptance.
2. `app/src/main/kotlin/com/vantra/music/ui/player/terminal/BeatTracker.kt` — add explicit playback-only fallback enum/documentation; detection algorithm unchanged.
3. `app/src/main/kotlin/com/vantra/music/ui/player/terminal/TerminalCamera.kt` — gated, bounded audio dolly overlay; preserve Stage 2 default behaviour.
4. `app/src/main/kotlin/com/vantra/music/ui/player/terminal/TerminalRenderer.kt` — eased caret, typed-character emphasis, active-block crossfade.
5. `app/src/main/kotlin/com/vantra/music/ui/player/terminal/TerminalPlayerStyle.kt` — one frame owner, actual transport events, media identity guard, real audio/fallback integration and labels.

Added:

6. `app/src/main/kotlin/com/vantra/music/ui/player/terminal/TerminalSynchronization.kt`
7. `app/src/test/kotlin/com/vantra/music/playback/audio/AudioSpectrumTest.kt`
8. `app/src/test/kotlin/com/vantra/music/ui/player/terminal/TerminalSynchronizationTest.kt`
9. `docs/terminal-v3/STAGE3-PLAN.md`
10. `docs/terminal-v3/STAGE3-REPORT.md`
11. `docs/terminal-v3/STAGE3-VERIFICATION.txt`
12. `docs/terminal-v3/STAGE3-ARCHIVE-AUDIT.txt`

## Actual verification and limits

- PASS: **49 JUnit tests**, zero failures: all 37 Stage 1/2 Terminal tests plus 12 new tests (5 real-spectrum tests and 7 synchronization/camera/transition tests). Existing Stage 2 tests were not edited.
- PASS: real production `AudioSpectrum`, `BeatTracker` and controller code exercised with generated PCM. Tests cover all four supported PCM encodings, buffer-position preservation, stream-offset alignment, flush, encoded/unsupported fallback, expired past/future data, and end-to-end PCM → FFT → onset detection. A steady decoded tone generated zero onsets; the synthetic pulsed test signal met the asserted detection range. These signals are test inputs, not a timer-based detector in the app.
- PASS: media/player/event identity boundaries, repeated IDs, pause/resume, speed-aware normal advance, seek reset, no-audio/no-lyrics fallback, audio-loss pulse clearing, onset gating, bounded camera emphasis/settling, frame-rate-independent block transitions and frozen-position typing. Stage 2 frame-rate/projection/readability tests still pass.
- Regression proof: before the freshness fix, the new stale-history and timestamp-offset tests failed against Stage 2. The new synchronization and audio-overlay tests initially could not compile before their classes/parameters existed; all pass after implementation.
- PASS: Kotlin 2.3.10 standalone compilation of the real Terminal core, synchronization, renderer, AudioSpectrum and tests. Android graphics API stubs and minimal Media3 `C`/`Format`/`MimeTypes`/annotation stand-ins were used only for this isolated JVM check. The actual FFT, PCM handling, detector and synchronization implementations ran; no production code was replaced by a mock and no stand-in was added to the app ZIP. This does not establish full Android API/Compose integration compatibility.
- PASS: compiler PSI syntax parsing of `TerminalPlayerStyle.kt` and `TerminalRenderer.kt`. Syntax parsing does not resolve Compose/Media3 symbols or compile the complete app.
- BLOCKED: actual command `./gradlew :app:compileArm64FossDebugKotlin :app:testArm64FossDebugUnitTest --no-daemon` failed during the configured Gradle 9.3.1 download with `java.net.SocketException: Network is unreachable`. The full Android/Compose build and complete application unit suite have not run successfully. No APK was produced.
- The inherited GitHub Terminal verification workflow is unchanged; it compiles Android integration and runs application tests, including these new tests. It has not been run remotely in this session.
- Final ZIP CRC, duplicate-member detection and byte audit are recorded in `STAGE3-ARCHIVE-AUDIT.txt`. Only the five source files listed above changed; all original files remain. Stage 1/2 document generation, lexer, viewport-motion helper, tests, documentation, settings keys, manifest, signing/release configuration, playback engine/output configuration and other player styles are preserved byte-for-byte.

No Android device, emulator, Cast receiver, exclusive output or offload hardware was exercised. Native appearance, real-song onset quality, audible/device latency, GPU memory, frame time and battery performance remain unmeasured. Input spectrum is before audio effects/mixing; it is not a guarantee of alignment after Bluetooth, Cast, EQ or speaker latency. Before release, run the inherited CI workflow and test local/network/module PCM, Cast fallback, offload/passthrough, long/Unicode lyrics, rapid skips/repeat, pause/resume/buffering, seeks in either direction, background/foreground, style changes and all motion/quality options on Android.

Stop at Stage 3. No Stage 4 work was started.
