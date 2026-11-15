# Terminal V3 Stage 3 — synchronization plan

Build on Stage 1/2 Canvas editor, safe projection, depth layers and camera paths.

1. Pin the observed spectrum freshness defect with real PCM tests, then fix nearest-frame staleness rejection without changing the audio path. Test supported PCM formats, unsupported encoded sources, flush and timestamp alignment.
2. Add a retained, Android-independent synchronization state: real media/player/discontinuity identity, position/speed/transport boundary detection, no-audio fallback, onset warmup and bounded beat envelope. Tests cover seek, pause/resume, skip, repeated tracks, buffering and no-audio cases.
3. Add an explicitly opt-in audio envelope to the Stage 2 camera. Only verified current-spectrum data can enable it; retain cinematic paths and angle bounds. Test no-source/off/reduced-motion immunity, short emphasis and frame-rate consistency.
4. Give Terminal one lifecycle/visibility-owned frame loop and one frame invalidation clock. Read current playback position plus stored lyric offset in that loop; use remote position while casting. Observe actual Media3 discontinuities, guard lyrics by media ID, retain state across ordinary recomposition/settings changes and detach listeners on disposal/player changes.
5. Preserve complete lyric strings while making the character-by-character bright reveal clear, ease the caret, and crossfade active-block emphasis. Keep timestamp-based document locate authoritative and typing frozen on pause; retain Stage 2 folding/scrolling/depth.
6. Compile and run Terminal + actual spectrum JVM tests, parse Compose source, attempt full Gradle Android checks, audit all archive members and package complete Stage 3 source with honest results and source capability/fallback table.

No fake beat timestamps, new permissions, new app dependencies, audio offload overrides or unrelated feature changes. Android rendering/performance still needs device verification.
