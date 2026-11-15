# Vantra Terminal V3 — Stage 4 final integration report

## Status

Complete source deliverable based on Stage 3, with targeted final integration fixes. **Production readiness is not established.** Android/Compose compilation is blocked by the local build environment, and no Android device/emulator was exercised. No APK is included.

## Audit findings and changes

### Rotation and playback ownership

The inherited shared `StyleFrame` calls its content twice while `RotatingFrame` crossfades orientation-keyed layers. Terminal's scene state lived inside that content, so an orientation transition could temporarily own two renderers, frame loops and transport listeners, and replace the camera state.

`TerminalFrame.kt` now provides exactly one retained Terminal content tree, using the existing quarter-turn geometry, native swipe/double-tap gestures, carousel and turned-fling handling. A restrained rotation/fit transition transforms that one tree. It does not call a playback restart, stop, seek or player replacement during rotation. The existing shared wrapper and other styles were not edited.

The same renderer, camera springs, sync controller and frame invalidation clock survive Terminal's in-place portrait/landscape changes. Wrapping/scroll focus re-anchor to the same authoritative lyric position when column/font geometry changes. Width is now part of document construction's remember key so an initial zero-size/narrow layout cannot leave fallback-width wrapping cached after real font metrics arrive. Process/activity recreation is not claimed to preserve spring velocity; timing is reconstructed from the service player. The application retains its existing portrait window and renders Terminal's landscape view as a quarter turn, rather than requesting an Activity orientation restart.

### Lifecycle, sensors and fullscreen cleanup

The old orientation effect could return when AUTO was disabled without disabling the already enabled sensor; it also read Android Settings inside every orientation callback. The final implementation has:

- A separately registered/unregistered auto-rotate settings observer, with no Settings read in the sensor callback.
- Sensor ownership limited to enabled AUTO mode, system auto-rotate enabled, lifecycle RESUMED and style visible. All cancellation/disabled/disposal paths disable it.
- Fullscreen bar ownership limited to RESUMED/visible landscape. On release, the previous bar behaviour and status/navigation visibility are restored rather than always showing all bars. If root insets are unavailable on entry, the restoration conservatively assumes visible bars.
- Audio sync reset/status cleanup in the visible STARTED lifecycle block's `finally`, in addition to outer disposal cleanup. The existing demand-driven FFT reader stops receiving Terminal requests when hidden/stopped; its inherited analysis grace window remains about 1.5 seconds unless another visualizer is reading it.
- The actual player listener still detaches on active-player change/disposal. It is a small event listener, not an audio polling loop. No Context/Activity/window is stored in the renderer or sync controller.

### Insets and geometry

Terminal's inset calculation combines safe-drawing/cutout areas, mandatory system gestures and system bars ignoring visibility. It maps all four physical edges through the clockwise quarter turn and resolves RTL start/end. The native content can retain stable padding while transient bars appear. Controls/menu/header and the code scene receive those local insets. Landscape reserves a right control rail; very short landscape constraints use a bottom transport row with matching scene reservation. The header reservation is slightly denser in landscape.

The original scene projection, depth layers, camera bounds and active-lyric safe band remain. Portrait and landscape rendering/cutout behaviour still require device verification; geometry unit tests are not screenshots.

### Controls and accessibility

The old shared transport used raw pointer gestures on 28–40 dp circles without native action semantics. Changing that shared component would affect other styles. `TerminalControls.kt` instead provides Terminal-only Material IconButtons with 48 dp targets and labelled previous/play/pause/next/menu actions. Disabled skip actions reflect actual transport state. Ended playback replays using the same existing player behaviour.

The in-tree scrollable menu preserves lyrics, shuffle, repeat, other menu, rotation and edit-player actions. It remains in the rotated coordinate system rather than opening a separate unrotated popup. Clicking the scrim or Android Back dismisses it. No inactive/placeholder action was added.

A tap reveals controls and restarts the timeout. The default timeout is 3.5 seconds, extended by Compose's accessibility-recommended timeout. Automatic hiding is disabled while paused, in portrait/preview, with an open menu, during touch exploration or while a control has keyboard focus. Native button focus is observed by the controls group. Fading chrome is hidden from accessibility only after fully faded, and removed controls have no invisible hit targets. The scene offers a semantic reveal-controls action outside preview and exposes the current real lyric text to screen readers without publishing every syntax token or every typing frame as a live announcement.

Text remains monospace with original Unicode-aware wrapping/reveal and full original lyric strings. Existing application/system reduced-motion settings still stop camera/typing easing; the single-tree orientation transition uses the application's motion-duration scale. Intensity OFF retains the independent typing preference. No flashing beat overlay was introduced.

### Performance and memory audit

- The renderer reuses Paint, matrices, spring state, projector, framing results and scratch arrays. No per-frame bitmaps, GPU buffer/texture uploads, blur effects or projection-result objects were added. Terminal no longer incurs the shared rotation wrapper's two scenes and per-frame transition blur objects.
- Ordinary wrapped lyric rows now share a stable viewport-selection key. Moving the typing caret to another wrapped row does not rebuild TreeSets/arrays if the selected visible rows are unchanged; oversized lyrics still advance the viewport when needed. Viewport construction remains an allocation on actual selection/document changes, not an allocation-free API claim.
- Monospace code/lyric cell widths are cached during layout. Printable ASCII spans use arithmetic advances with the correct separate code and lyric font sizes; Unicode/fallback text still uses Android measurement. This reduces repeated native measurement calls without changing the real strings.
- Zero-size layout prevents stale-size rendering. Font/orientation layout invalidates caret geometry. Drawing and background traversal stay bounded by the viewport; background code is not rebuilt every frame.
- LOW retains one rear plane, suppresses highlight glow and keeps the inherited approximate 30 Hz drawing cap. HIGH retains the full restrained scene. AUTO uses the inherited low-memory-device/slow-frame downgrade. No app dependency or graphics engine was changed.
- Camera/beat/typing stay in the one owned frame loop. Paused, settled scenes slow to the inherited 265 ms blink poll. The actual FFT remains on the audio path, never moved onto the UI thread.

These are source-level optimizations. Android allocation counts, GC pressure, GPU memory, FPS/jank, ANR risk, device temperature and battery savings were **not measured**. No numeric performance improvement is claimed.

## Settings and preservation audit

The five Terminal preference keys and existing enum names/defaults are unchanged. The existing DataStore preference helpers still read cached stored values and persist writes using the same keys. System animation scale still comes from the unmodified `AppMotion` observer. Read/write paths were inspected; process-restart/device persistence was not tested.

A byte audit against every original Stage 3 archive file permits only three modified files: `TerminalPlayerStyle.kt`, `TerminalRenderer.kt` and `TerminalDocument.kt`. New implementation is confined to Terminal frame/controls/presentation helpers and their tests. All other player styles, shared StyleKit/rotation/controls, Stage 3 camera/onset/spectrum corrections, signing/update scripts and configuration, manifest, backup/restore, streak, GitHub integration, settings storage, Gradle files and dependencies remain identical. This preservation check does not claim that an untested original feature has no runtime bug.

## Actual verification

| Check | Actual result |
| --- | --- |
| Terminal/core/spectrum/presentation JUnit | **55 passed**, including all 49 Stage 1–3 tests and six new polish tests; no existing test was edited |
| Standalone Kotlin compilation | Passed for real document/lexer/camera/sync/presentation/renderer/spectrum/test sources, Kotlin 2.3.10 |
| Compile-time stand-ins | Old Android graphics API stub jar and minimal Media3 C/Format/MimeTypes/annotation stand-ins only in the local JVM harness; no stand-ins added to the project |
| New tests | Stable ordinary wrapping selection, oversized-line viewport advance, all-edge clockwise/RTL inset mapping, accessibility/menu/focus/pause control policy, separate-font ASCII advances and control-character measured fallback |
| Regression sequence | New helper/API tests initially failed to compile before implementation; final suite passes. They are policy/math tests, not an instrumented proof of lifecycle cleanup or touch UI |
| Signing regression suite | **53 passed, 0 failed**, using its existing throwaway keystore; no release key modified |
| Transfer/restore Journal | Existing real Journal class compiled and tested against all scenario assertions: **15 passed**, including files, media, preferences, DataStore, interruption and repeated undo |
| Compose syntax | Compiler PSI parsing passed for TerminalPlayerStyle, TerminalFrame and TerminalControls; not type-resolution/integration compilation |
| Full Android command | `./gradlew :app:compileArm64FossDebugKotlin :app:testArm64FossDebugUnitTest --no-daemon` stopped during configured Gradle 9.3.1 download: `java.net.SocketException: Network is unreachable` |
| Android SDK/device | Standard SDK locations checked were absent; adb/emulator not available in the checked environment; no device execution |
| Full app compile/unit suite | **Not completed**. No assertion that all Compose integration/compilation errors are absent |
| APK | Not generated, because Android compilation did not succeed |
| Archive integrity | CRC, duplicate entries, unchanged original bytes and exact changed-file scope checked; see FINAL-ARCHIVE-AUDIT.txt |

Full raw outputs are in `FINAL-VERIFICATION.txt`. The existing Terminal GitHub verification workflow is preserved and would compile Android integration/run app unit tests in an Android-equipped environment. It was not run remotely in this session. Independent code review and Android instrumentation were not performed.

## Visual acceptance and remaining device work

| Acceptance area | Evidence now | Still required |
| --- | --- | --- |
| Active lyric readability | Existing projection/safe-region, timestamp and wrapping tests pass; active typed text stays bright | Native font fallback, high font scale, RTL/emoji, tiny windows and camera extremes on device |
| Coherent syntax/depth | Existing lexer/document tests pass; Stage 1 palette and Stage 2 depth retained | Visual judgement on an actual rendered screen |
| Portrait/landscape | One retained scene, mapped cutout/gesture insets, adaptive control arrangement | Real rotation, display cutouts, gesture/3-button navigation, split screen and large-font touch layout |
| Smooth camera/no fake beats | Existing spring/frame-rate and real PCM/onset tests pass; audio implementation unchanged | Real-song perceptual sync, Bluetooth/device latency and frame-time measurements |
| Empty space/clipping/overlap | Denser landscape reservation, protected control rail, scrollable in-tree menu | Native screenshots/video at actual sizes; no claim of zero clipping on every device |
| Playback continuity/styles | No rotation/style-disposal stop/restart calls; shared styles and service untouched | Actual playback during rapid rotations/style switches/background/foreground |
| Other feature regression | Byte preservation plus passing signing and restore suites | Device signing/update, backup, streak and GitHub workflows end-to-end |

Known limits retained: no lyrics produces the real `no synced lyrics` status document; unsupported audio/encoded bypass/Cast uses the identified timestamp/playback fallback, never fake beats. Cast timing is limited by the existing 500 ms remote position updates. The auto sensor follows the original counter-clockwise device quarter-turn direction; manual portrait/landscape is still available. Very small viewports and unusually large text can force the minimum-column font fit/projection shrink; no universal minimum post-projection font size is promised. Android speech output, Switch Access/keyboard traversal and full device accessibility acceptance remain unverified.

This is the final source integration deliverable for Stage 4, with the above verification boundaries. It is not certified production-ready.

## API references consulted for implementation review

- https://developer.android.com/reference/kotlin/androidx/compose/foundation/layout/package-summary
- https://developer.android.com/reference/kotlin/androidx/compose/ui/platform/AccessibilityManager
- https://developer.android.com/reference/kotlin/androidx/compose/ui/semantics/package-summary

These references informed API review; they do not replace compilation against this project's dependencies.
