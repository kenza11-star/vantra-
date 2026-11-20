package com.vantra.music.ui.player.geometry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeometryGameTest {
    private fun run(world: GeometryWorld, until: Float, press: (GeometryWorld) -> Boolean): Boolean {
        var guard = 0
        while (world.x < until && world.alive && guard++ < 200_000) world.step(press(world))
        return world.alive
    }

    @Test fun aCubeThatNeverJumpsDiesOnASpike() {
        val world = GeometryWorld()
        world.obstacles.add(Obstacle(Kind.SPIKE, 8f, 0f, 1f, 1f))
        assertFalse(run(world, 14f) { false })
    }

    @Test fun aCubeThatJumpsAtTheTakeoffClearsASpike() {
        val world = GeometryWorld()
        world.obstacles.add(Obstacle(Kind.SPIKE, 8f, 0f, 1f, 1f))
        assertTrue(run(world, 14f) { 8.5f - it.x <= Gd.TakeoffBlocks + 0.1f })
    }

    @Test fun aJumpIsAboutTwoBlocksHighAndFourLong() {
        val world = GeometryWorld()
        var peak = 0f
        val startX = world.x
        world.step(true)
        var landedAt = 0f
        var guard = 0
        while (!world.grounded && guard++ < 10_000) {
            world.step(false)
            peak = maxOf(peak, world.y)
            landedAt = world.x
        }
        assertTrue("peak $peak", peak in 2.0f..2.3f)
        assertTrue("length ${landedAt - startX}", landedAt - startX in 3.8f..4.3f)
    }

    @Test fun aCubeLandsOnABlockAndRunsAlongItsTop() {
        val world = GeometryWorld()
        world.obstacles.add(Obstacle(Kind.BLOCK, 8f, 0f, 3f, 1f))
        assertTrue(run(world, 14f) { 8f - it.x <= 1.6f })
    }

    @Test fun runningIntoTheSideOfABlockKills() {
        val world = GeometryWorld()
        world.obstacles.add(Obstacle(Kind.BLOCK, 8f, 0f, 1f, 2f))
        assertFalse(run(world, 14f) { false })
    }

    @Test fun aHardSongCountsAtOnceAndACalmOneNeedsTwoWins() {
        val hard = GeometryRewards.win(true, 3, 0)
        assertEquals(4, hard.streak); assertTrue(hard.gained)
        val first = GeometryRewards.win(false, 3, 0)
        assertEquals(3, first.streak); assertEquals(1, first.easyWins); assertFalse(first.gained)
        val second = GeometryRewards.win(false, first.streak, first.easyWins)
        assertEquals(4, second.streak); assertEquals(0, second.easyWins); assertTrue(second.gained)
    }

    @Test fun phonkAndFastSongsAreHard() {
        assertTrue(GeometryRewards.isHard(100f, "Montagem Coral", "DJ", ""))
        assertTrue(GeometryRewards.isHard(0f, "Neon Blade", "Kordhell", "PHONK Vol. 2"))
        assertTrue(GeometryRewards.isHard(150f, "Song", "Artist", ""))
        assertFalse(GeometryRewards.isHard(90f, "Evening", "Someone", "Quiet"))
    }

    @Test fun theBeatGridLearnsTheTempoFromKicks() {
        val grid = BeatGrid()
        for (i in 0 until 24) grid.onKick(1000L + i * 469L)
        assertTrue(grid.confident)
        assertEquals(128f, grid.bpm, 2f)
        val next = grid.nextBeatAfter(10_000, 545f)
        assertTrue(next > 10_000)
        assertTrue((next - 1000) % 469L < 20 || 469 - (next - 1000) % 469L < 20)
    }

    @Test fun halfTimeKicksAreFoldedIntoOneBeat() {
        val grid = BeatGrid()
        // a kick only on every other beat of a 140 bpm song
        for (i in 0 until 20) grid.onKick(500L + i * 857L)
        assertTrue(grid.confident)
        assertTrue("bpm ${grid.bpm}", grid.bpm in 68f..72f || grid.bpm in 136f..144f)
    }

    @Test fun aSlowSongPlaysSlowerThanAFastOne() {
        val slow = TimeScale(); val fast = TimeScale()
        repeat(600) { slow.update(0.02f, 80f, 0.5f); fast.update(0.02f, 165f, 0.5f) }
        assertTrue(slow.value < 0.65f)
        assertTrue(fast.value > 1.1f)
    }

    @Test fun aBreakdownSlowsTheGameFurther() {
        val scale = TimeScale()
        repeat(800) { scale.update(0.02f, 140f, 0.8f) }
        val before = scale.value
        repeat(150) { scale.update(0.02f, 140f, 0.1f) }
        assertTrue(scale.calm)
        assertTrue(scale.value < before)
    }

    @Test fun aPlannedLevelKeepsTheBeatAndNeverRunsPatternsTogether() {
        val director = GeometryDirector("test-song")
        for (i in 0 until 60) director.onKick(600L + i * 428L)
        var now = 20_000L
        while (now < 60_000L) {
            director.plan(now, 120_000L, hard = true, calm = false, timeScale = 1f)
            now += 250
        }
        assertTrue("planned ${director.specs.size}", director.specs.size > 20)
        for (i in 1 until director.specs.size) {
            val gap = director.specs[i].arrivalMs - director.specs[i - 1].arrivalMs
            assertTrue("gap $gap", gap >= 400)
        }
        assertTrue(director.specs.first().arrivalMs >= GeometryDirector.IntroMs)
    }

    @Test fun aPadThrowsTheCubeOverATallBlockWithoutATap() {
        val world = GeometryWorld()
        world.obstacles.add(Obstacle(Kind.PAD, 8f, 0f, 1f, 0.25f))
        world.obstacles.add(Obstacle(Kind.BLOCK, 11f, 0f, 1f, 3f))
        assertTrue(run(world, 18f) { false })
    }

    @Test fun anOrbNeedsAFreshTapNotAHeldOne() {
        // spikes too wide for one jump: held from the ground the cube never takes the orb, tapped again it does
        val cells = Pattern.ORB_SPIKES4.cells.map { Obstacle(it.kind, 8f + it.dx, it.y, it.w, it.h) }
        val held = GeometryWorld(); held.obstacles.addAll(cells)
        assertFalse(run(held, 16f) { 8f - it.x <= 3f })
    }

    @Test fun everyPatternCanBePlayedOnItsOwn() {
        for (pattern in Pattern.entries.filter { !it.ship || it.isPortal }) {
            val cells = pattern.cells.map { Obstacle(it.kind, 8f + it.dx, it.y, it.w, it.h) }
            assertTrue("$pattern", Playability().tryAdd(cells, 8f + pattern.reach + 0.7f).also { })
        }
    }

    @Test fun aShipSectionCanBeFlownAndTheCubeComesBack() {
        val playable = Playability()
        var anchor = 8f
        val order = listOf(Pattern.PORTAL_IN, Pattern.GATE_LOW, Pattern.GATE_HIGH, Pattern.GATE_ZIG,
            Pattern.GATE_SQUEEZE, Pattern.GATE_SPIKES, Pattern.GATE_STEPS, Pattern.PORTAL_OUT, Pattern.SPIKE)
        for (pattern in order) {
            val cells = pattern.cells.map { Obstacle(it.kind, anchor + it.dx, it.y, it.w, it.h) }
            assertTrue("$pattern", playable.tryAdd(cells, anchor + pattern.reach + 0.7f))
            anchor += 8.9f
        }
    }

    @Test fun theShipRisesWhileHeldAndFallsWhenLetGo() {
        val world = GeometryWorld()
        world.ship = true
        repeat(120) { world.step(true) }
        val high = world.y
        assertTrue("risen to $high", high > 2f)
        repeat(240) { world.step(false) }
        assertTrue("fallen to ${world.y}", world.y < 0.1f)
    }

    @Test fun passingAPortalChangesTheMode() {
        val world = GeometryWorld()
        world.obstacles.add(Obstacle(Kind.PORTAL_SHIP, 4f, 0f, 1f, Gd.Ceiling))
        run(world, 8f) { false }
        assertTrue(world.ship)
    }

    @Test fun aCubeOnTheRoofPlaysTheMirrorImage() {
        val normal = GeometryWorld()
        normal.obstacles.add(Obstacle(Kind.SPIKE, 8f, 0f, 1f, 1f))
        val upsideDown = GeometryWorld()
        upsideDown.flipped = true
        upsideDown.y = Gd.Ceiling - 1f
        upsideDown.obstacles.add(Obstacle(Kind.SPIKE_DOWN, 8f, Gd.Ceiling - 1f, 1f, 1f))
        val press = { w: GeometryWorld -> 8.5f - w.x <= Gd.TakeoffBlocks + 0.1f }
        assertTrue(run(normal, 14f, press))
        assertTrue(run(upsideDown, 14f, press))
        assertEquals(normal.x, upsideDown.x, 0.05f)
        // and the same spike, not jumped, kills it there too
        val doomed = GeometryWorld()
        doomed.flipped = true
        doomed.y = Gd.Ceiling - 1f
        doomed.obstacles.add(Obstacle(Kind.SPIKE_DOWN, 8f, Gd.Ceiling - 1f, 1f, 1f))
        assertFalse(run(doomed, 14f) { false })
    }

    @Test fun aGravityPortalPutsTheCubeOnTheRoofAndAnotherBringsItBack() {
        val world = GeometryWorld()
        world.obstacles.add(Obstacle(Kind.PORTAL_FLIP, 4f, 0f, 1f, Gd.Ceiling))
        world.obstacles.add(Obstacle(Kind.PORTAL_UNFLIP, 14f, 0f, 1f, Gd.Ceiling))
        run(world, 10f) { false }
        assertTrue(world.flipped)
        assertEquals(Gd.Ceiling - 1f, world.y, 0.05f)
        run(world, 22f) { false }
        assertFalse(world.flipped)
        assertTrue(world.y < 0.1f)
    }

    @Test fun aFlippedSectionCanBePlayed() {
        val playable = Playability()
        var anchor = 8f
        val order = listOf(Pattern.PORTAL_FLIP, Pattern.SPIKE, Pattern.DOUBLE, Pattern.STAIR, Pattern.PORTAL_UNFLIP, Pattern.SPIKE)
        for ((index, pattern) in order.withIndex()) {
            val mirrored = index in 1..3
            val cells = pattern.cells.map {
                val kind = if (!mirrored) it.kind else when (it.kind) { Kind.SPIKE -> Kind.SPIKE_DOWN; else -> it.kind }
                Obstacle(kind, anchor + it.dx, if (mirrored) Gd.Ceiling - it.y - it.h else it.y, it.w, it.h)
            }
            assertTrue("$pattern", playable.tryAdd(cells, anchor + pattern.reach + 0.7f))
            anchor += 8.9f
        }
    }

    @Test fun theWaveClimbsAndDivesAtFortyFiveDegrees() {
        val world = GeometryWorld()
        world.wave = true
        world.y = 2f
        repeat(120) { world.step(true) }
        // half a second held: about five blocks up (cut by the roof), the same distance along
        assertTrue("y ${world.y}", world.y > 5.5f)
        repeat(240) { world.step(false) }
        assertTrue("y ${world.y}", world.y < 0.1f)
    }

    @Test fun aWaveTouchingABlockDies() {
        val world = GeometryWorld()
        world.wave = true
        world.y = 3f
        world.obstacles.add(Obstacle(Kind.BLOCK, 8f, 0f, 1f, 7f))
        assertFalse(run(world, 14f) { false })
    }

    @Test fun aWaveSectionCanBePlayed() {
        val playable = Playability()
        var anchor = 8f
        for (pattern in listOf(Pattern.PORTAL_WAVE, Pattern.GATE_SPIKES, Pattern.PORTAL_OUT, Pattern.SPIKE)) {
            val cells = pattern.cells.map { Obstacle(it.kind, anchor + it.dx, it.y, it.w, it.h) }
            assertTrue("$pattern", playable.tryAdd(cells, anchor + pattern.reach + 0.7f))
            anchor += 8.9f
        }
    }

    @Test fun bestPercentagesAreKeptPerSongAndOnlyRaised() {
        var raw = ""
        raw = GeometryBest.put(raw, "song-a", 40)
        raw = GeometryBest.put(raw, "song-b", 12)
        raw = GeometryBest.put(raw, "song-a", 25)
        assertEquals(40, GeometryBest.get(raw, "song-a"))
        raw = GeometryBest.put(raw, "song-a", 77)
        assertEquals(77, GeometryBest.get(raw, "song-a"))
        assertEquals(12, GeometryBest.get(raw, "song-b"))
        assertEquals(0, GeometryBest.get(raw, "unknown"))
    }

    @Test fun aBallChangesSidesOnAFreshTapAndIgnoresAHeldOne() {
        val world = GeometryWorld()
        world.ball = true
        world.step(true)
        assertTrue("flipped", world.flipped)
        repeat(240) { world.step(true) }
        // still held: no second change of sides, it stays on the roof
        assertTrue(world.flipped)
        assertEquals(Gd.Ceiling - 1f, world.y, 0.05f)
        world.step(false)
        world.step(true)
        assertFalse(world.flipped)
    }

    @Test fun aBallThatNeverTapsDiesOnAFloorSpike() {
        val world = GeometryWorld()
        world.ball = true
        world.obstacles.add(Obstacle(Kind.SPIKE, 8f, 0f, 1f, 1f))
        assertFalse(run(world, 14f) { false })
        val other = GeometryWorld()
        other.ball = true
        other.obstacles.add(Obstacle(Kind.SPIKE, 8f, 0f, 1f, 1f))
        var tapped = false
        assertTrue(run(other, 14f) { val tap = !tapped && it.x > 5f; if (tap) tapped = true; tap })
    }

    @Test fun aUfoHopsOnEveryTapWithoutTouchingTheGround() {
        val world = GeometryWorld()
        world.ufo = true
        world.y = 3f
        var peak = 0f
        repeat(40) { world.step(false) }
        val low = world.y
        world.step(true)
        repeat(30) { world.step(false); peak = maxOf(peak, world.y) }
        assertTrue("hopped from $low to $peak", peak > low + 0.5f)
    }

    @Test fun ballAndUfoSectionsCanBePlayed() {
        for (portal in listOf(Pattern.PORTAL_BALL, Pattern.PORTAL_UFO)) {
            val playable = Playability()
            var anchor = 8f
            val shapes = if (portal == Pattern.PORTAL_BALL)
                listOf(Pattern.BALL_LOW, Pattern.BALL_HIGH, Pattern.BALL_SPIKES_FLOOR, Pattern.BALL_SPIKES_ROOF, Pattern.BALL_ZIG)
            else listOf(Pattern.GATE_LOW, Pattern.GATE_SPIKES, Pattern.GATE_STEPS)
            for (pattern in listOf(portal) + shapes + listOf(Pattern.PORTAL_OUT, Pattern.SPIKE)) {
                val cells = pattern.cells.map { Obstacle(it.kind, anchor + it.dx, it.y, it.w, it.h) }
                assertTrue("$portal $pattern", playable.tryAdd(cells, anchor + pattern.reach + 0.7f))
                anchor += 8.9f
            }
        }
    }
}
