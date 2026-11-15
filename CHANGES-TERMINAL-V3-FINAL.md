# Vantra Style Terminal V3 — final changelog

This complete project continues from `Vantra-Terminal-V3-Stage3.zip`.

## Stage 4

- Keep one Terminal composition/renderer/camera/frame loop through quarter-turn transitions. The shared two-layer rotation implementation and all other styles remain unchanged.
- Preserve playback ownership, real lyric position and retained camera springs during in-place orientation changes; rebuild wrapping when width/font metrics require it.
- Map stable system bars, display-cutout/safe-drawing and mandatory gesture insets into the rotated coordinate system, including RTL start/end mapping.
- Add Terminal-only native playback buttons with 48 dp targets, action labels and keyboard/accessibility semantics. Preserve previous/next/play/pause/end-of-track replay, lyrics, shuffle, repeat, other menu, rotation and edit-player actions. Keep the scrollable menu inside the rotated frame; Android Back dismisses it.
- Show controls on a tap/semantic action. Hide after 3.5 seconds or the accessibility-recommended longer timeout; keep them visible for pause, an open menu, touch exploration or keyboard focus. Adapt transport to a row in very short landscape viewports.
- Expose the current real lyric as Canvas accessibility text and hide fully faded chrome from accessibility navigation.
- Disable orientation sensors when inactive, hidden, paused or auto-rotate is off; observe auto-rotate settings separately instead of reading Settings on every sensor callback. Restore original system-bar visibility and behaviour when Terminal releases fullscreen ownership.
- Clear audio-analysis ownership/status when the visible lifecycle block stops, including background/foreground transitions.
- Reuse viewport selection across ordinary wrapped rows, cache measured monospace cell widths, and use arithmetic advances for printable ASCII. Preserve actual measurement for Unicode/fallback glyphs. Handle zero-size/font/orientation layout invalidation; ensure initial narrow-width documents rebuild with real font metrics.
- LOW no longer applies audio highlight glow. Existing AUTO/HIGH/LOW, intensity, typing, beat and orientation preference keys/read-write paths are unchanged.

## Preserved from Stages 1–3

Continuous dark code editor, syntax colours, measured wrapping, real lyric strings/timestamps, gutter/indent guides, active-row emphasis, Unicode reveal boundaries, perspective code depth, restrained cinematic paths, safe focus framing, current-PCM-only onset response, stale-spectrum rejection and clearly identified lyric/playback fallbacks.

Signing/update configuration, backup/restore, streak, GitHub integration, playback engine, manifest, dependencies, settings storage and every other style's source are preserved byte-for-byte against Stage 3.

## Verification status

- 55 Terminal/spectrum/presentation JUnit tests pass.
- Signing script suite: 53 passed, 0 failed, with a throwaway test key.
- Existing transfer/restore journal scenarios: 15 assertions pass against the real Journal class.
- Changed Compose files pass syntax parsing. Standalone Kotlin core/renderer compilation uses Android/Media3 API stand-ins, not a full Android build.
- Full Android/Compose build is blocked at Gradle distribution download (`Network is unreachable`). No APK was generated.
- No Android emulator/device validation or GPU/frame-time/memory/battery profiling was performed. This ZIP is not claimed production-ready.

See `docs/terminal-v3/FINAL-REPORT.md`, `FINAL-CHANGED-FILES.txt`, `FINAL-VERIFICATION.txt` and `FINAL-ARCHIVE-AUDIT.txt`.
