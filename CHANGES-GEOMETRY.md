# Geometry player style (stage 1)

A rhythm platformer built out of the song that is playing, picked from the player selector (last column).

- A cube runs by itself; a finger held down jumps (the whole screen is the button). Physics follow the genre's classic cube: ~2.2 blocks high, ~4 blocks long a jump, 10.4 blocks/s at normal speed.
- The level is planned from the beat (BeatGrid learns the tempo from the audio analysis' kicks): shapes land on the beats, a calm song gets one every other beat, a hard one nearly every beat. Playability checks every new shape against the game's own physics, so a level can always be finished.
- The level is kept per song, so the next attempt meets the same obstacles. A death restarts the song from the beginning.
- TimeScale: the whole game runs at song tempo / 140 (0.55x - 1.45x) and eases further in a breakdown, so slow songs play in slow motion.
- Lyrics are written on the wall behind the level. Colour and flashes follow the song.
- Winning (song finished alive): hard songs (>= 130 bpm, or phonk / montagem / funk ... in the tags) add a flame at once, calm songs every second win.
- Files: ui/player/geometry/GeometryGame.kt (engine), GeometryPlayerStyle.kt (screen); PlayerStyle.GEOMETRY, GeometryStreakKey, GeometryEasyWinsKey.

## Stage 2: jump pads and jump orbs

- Pad (yellow slab): throws the cube ~4.4 blocks up on touch, no tap needed. Orb (yellow ball): a fresh tap (not a held finger) while the cube is on it jumps again in mid-air.
- New patterns: PAD_SPIKES, PAD_BLOCK, PAD_WIDE (pads over spikes / a 3-high block), ORB_SPIKES4, ORB_SPIKES5 (spikes too wide for one jump; jump, then tap the orb). Pads are in calm songs too; orbs only in hard ones.
- Playability now also tracks whether a finger was already down, and tries taps in the air near an orb. A quick tap shorter than a frame still jumps.

## Stage 3: ship sections

- A pink portal turns the cube into a ship; a cyan one gives the cube back. As a ship, a held finger pushes it up and a lifted one lets it fall (floor and roof stop it without harm); it leans with its speed and leaves an exhaust trail. A roof slides in from the top while flying.
- Gates to fly through: GATE_LOW / GATE_HIGH (pillars from the floor / roof), GATE_STEPS, GATE_SPIKES (spikes on floor and roof), GATE_ZIG and GATE_SQUEEZE (hard songs only).
- The director opens a section after 6 (hard) or 9 (calm) cube patterns, 35% of the time, with 3 to 7 gates, and never in the last 14 seconds. Playability now tracks the ship state too, so every gate chain is checked against the same physics.
- Planning takes at most two beats per frame, and the ship is searched more coarsely, so the level builds up without stalling a frame.

## Stage 4: gravity portals

- An orange portal turns gravity upside down: the cube falls to the roof and runs along it, jumping downwards; a green portal puts it back. The roof slides in while upside down, the dust trail runs along it.
- Inside such a section the level is the cube patterns turned over (spikes hang from the roof, blocks stand on it; pads and orbs are left out). Physics run in a mirror, so a flipped cube plays exactly the mirror image of a normal one (tested).
- The director picks a ship or a gravity section (half and half) after 6 (hard) or 9 (calm) cube patterns, 35% of the time, 3 to 7 patterns long, never in the last 14 seconds. Playability tracks the flipped state as well.

## Stage 5: wave, and the best percentage per song

- A purple portal turns the cube into a wave: a held finger climbs at 45 degrees, a lifted one dives at 45, no inertia. Its hitbox is small (only its middle can be hurt), floor and roof are slid along, any block it touches kills. It leaves a bright trail and points along its way. Wave sections use the ship's gates; the playability check keeps only the ones that can be flown from where the wave is.
- Ship, wave and gravity sections are picked a third each after 6 (hard) / 9 (calm) cube patterns.
- "Best N%" is shown under the progress bar and kept per song across sessions (GeometryBestKey, at most 150 songs); a win counts as 100.

Next stages: ball and UFO modes, speed portals, practice mode with checkpoints, more effects.
