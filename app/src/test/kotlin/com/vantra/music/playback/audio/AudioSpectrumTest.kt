package com.vantra.music.playback.audio

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

class AudioSpectrumTest {
    private fun format(encoding: Int = C.ENCODING_PCM_16BIT, mime: String = MimeTypes.AUDIO_RAW) =
        Format.Builder().setSampleMimeType(mime).setSampleRate(48000).setChannelCount(1).setPcmEncoding(encoding).build()

    private fun tone(encoding: Int = C.ENCODING_PCM_16BIT): ByteBuffer {
        val bytes = if (encoding == C.ENCODING_PCM_16BIT) 2 else if (encoding == C.ENCODING_PCM_24BIT) 3 else 4
        val b = ByteBuffer.allocate(4096 * bytes).order(ByteOrder.LITTLE_ENDIAN)
        repeat(4096) { n ->
            val v = 0.7 * sin(2 * PI * 80 * n / 48000)
            when (encoding) {
                C.ENCODING_PCM_16BIT -> b.putShort((v * 32767).toInt().toShort())
                C.ENCODING_PCM_FLOAT -> b.putFloat(v.toFloat())
                C.ENCODING_PCM_32BIT -> b.putInt((v * 2147483647).toInt())
                else -> { val x = (v * 8388607).toInt(); b.put(x.toByte()); b.put((x shr 8).toByte()); b.put((x shr 16).toByte()) }
            }
        }
        return b.flip() as ByteBuffer
    }

    @Test fun oldSpectrumCannotMasqueradeAsCurrentAudio() {
        val s = AudioSpectrum(); val out = FloatArray(40)
        s.configure(format()); s.read(0, out); s.onBuffer(tone(), 0)
        assertTrue(s.read(40, out))
        assertFalse("one-sided old frames must expire", s.read(2000, out))
        assertTrue(out.all { it == 0f })
        assertFalse("one-sided future frames must be rejected", s.read(-2000, out))
    }
    @Test fun decodedPcmFormatsYieldRealLowFrequencyEnergyWithoutConsumingTheBuffer() {
        for (encoding in listOf(C.ENCODING_PCM_16BIT, C.ENCODING_PCM_24BIT, C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT)) {
            val s = AudioSpectrum(); val out = FloatArray(40); val b = tone(encoding)
            s.configure(format(encoding)); s.read(0, out); s.onBuffer(b, 0)
            assertEquals(0, b.position()); assertTrue(s.read(40, out))
            assertTrue("missing 80 Hz in encoding $encoding", out.take(8).max() > 0.3f)
        }
    }
    @Test fun streamOffsetAndFlushFollowSongTime() {
        val s = AudioSpectrum(); val out = FloatArray(40)
        s.configure(format()); s.onStreamOffset(5_000_000); s.read(0, out)
        s.onBuffer(tone(), 5_000_000)
        assertTrue(s.read(40, out)); assertFalse(s.read(5040, out))
        s.flush(); assertFalse(s.read(40, out))
    }
    @Test fun encodedOrUnsupportedAudioNeverProducesAnalysis() {
        val s = AudioSpectrum(); val out = FloatArray(40)
        s.configure(format(mime="audio/ac3")); s.read(0, out); s.onBuffer(tone(), 0)
        assertFalse(s.read(40, out))
        s.configure(format(encoding=C.ENCODING_INVALID)); s.onBuffer(tone(), 0)
        assertFalse(s.read(40, out))
    }
    @Test fun decodedPcmOnsetsReachTheRealDetectorButSteadyToneDoesNotPulse() {
        fun run(pulsed: Boolean): Int {
            val spectrum=AudioSpectrum(); spectrum.configure(format())
            val tracker=com.vantra.music.ui.player.terminal.BeatTracker()
            val hop=1024; val rate=48000
            repeat(280) { block ->
                val pos=(block.toLong()*hop*1000/rate-22).coerceAtLeast(0)
                spectrum.read(pos,tracker.bands) // subscribe before decode, just like the visible scene
                val b=ByteBuffer.allocate(hop*2).order(ByteOrder.LITTLE_ENDIAN)
                repeat(hop) { sample ->
                    val t=(block*hop+sample).toDouble()/rate
                    val amp=if(pulsed) 0.04+0.7*kotlin.math.exp(-(t%0.5)/0.065) else 0.4
                    b.putShort((amp*sin(2*PI*80*t)*32767).toInt().toShort())
                }
                b.flip()
                spectrum.onBuffer(b,block.toLong()*hop*1_000_000/rate)
                val heard=spectrum.read(pos,tracker.bands)
                tracker.update(hop.toFloat()/rate,heard)
            }
            return tracker.kickCount
        }
        assertEquals("a steady decoded tone is not a beat",0,run(false))
        val count=run(true)
        assertTrue("decoded test pulses detected: $count",count in 7..13)
    }
}
