package com.vantra.music.ui.player.liquid

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import com.vantra.music.ui.component.agslRenderEffect
import com.vantra.music.ui.component.createAgslShader
import com.vantra.music.ui.component.setAgslUniform
import kotlin.math.sin

/**
 * The liquid of the Liquid player style. The picture under it (the song's video, or its cover)
 * stays sharp and itself; a few thick patches of liquid drift over it like drops on glass. Inside
 * a patch the picture is smeared soft, bent a little at the rim (a lens), lifted with frost and
 * edged with a thin light; outside it nothing is touched. The patches are metaballs, so two that
 * meet flow into one and part again.
 *
 * Android 13+ gets all of it (one AGSL pass; the smear is read straight from the sharp picture, so
 * there is no second copy of a video). Older phones get no effect on the picture: the style draws
 * plain frosted patches over it instead (see FrostPatches in LiquidPlayerStyle).
 *
 * Every Android class is named only inside the guarded functions below, so nothing here is
 * resolved on a phone that doesn't have it.
 */
internal class LiquidFx {
    private var shader: Any? = null
    private val blob = FloatArray(3)

    /**
     * the effect for this moment, or null where the phone can't run it.
     * [timeSec] loops every 100 s; [melt] is 1 while a song's cover is still melting in (the whole
     * picture is smeared) and 0 once only the patches are left.
     */
    fun build(widthPx: Float, heightPx: Float, timeSec: Float, blurPx: Float, melt: Float): RenderEffect? {
        if (Build.VERSION.SDK_INT < 33 || widthPx < 1f || heightPx < 1f) return null
        return buildGuarded(widthPx, heightPx, timeSec, blurPx.coerceAtLeast(1f), melt.coerceIn(0f, 1f))
    }

    @RequiresApi(33)
    private fun buildGuarded(widthPx: Float, heightPx: Float, timeSec: Float, blurPx: Float, melt: Float): RenderEffect {
        val s = shader ?: createAgslShader(LiquidSource).also { shader = it }
        s.setAgslUniform("resolution", widthPx, heightPx)
        s.setAgslUniform("warp", timeSec * Wave * 3f, timeSec * Wave * 2f)
        s.setAgslUniform("blurPx", blurPx)
        s.setAgslUniform("melt", melt)
        val aspect = heightPx / widthPx
        for (i in 0 until LiquidBlobCount) {
            liquidBlob(i, timeSec, aspect, blob)
            s.setAgslUniform("b$i", blob[0], blob[1], blob[2])
        }
        return agslRenderEffect(s, "image").asComposeRenderEffect()
    }
}

internal const val LiquidBlobCount = 3

private const val Wave = 6.2831855f / 100f

/**
 * Where patch [i] is at [t] seconds, in units of the screen's width: x, y and its radius into
 * [out]. [aspect] is height / width. Every number turns a whole number of times in the 100 s
 * loop, so the wrap never shows; a patch takes 33 to 100 s for one round, slow and thick.
 */
internal fun liquidBlob(i: Int, t: Float, aspect: Float, out: FloatArray) {
    val hx = 1 + i % 3
    val hy = 1 + (i + 1) % 3
    val hr = 1 + i % 2
    val ph = i * 1.7f
    // three heavy drops, slow like syrup; each keeps to its own part of the screen so they meet only now and then
    out[0] = 0.5f + 0.30f * sin(t * Wave * hx + ph)
    out[1] = aspect * (0.5f + 0.32f * sin(t * Wave * hy + ph * 1.3f + 1f))
    out[2] = 0.15f + 0.03f * sin(t * Wave * hr + ph * 2.1f)
}

private const val LiquidSource = """
uniform shader image;
uniform float2 resolution;
uniform float2 warp;
uniform float blurPx;
uniform float melt;
uniform float3 b0;
uniform float3 b1;
uniform float3 b2;

float ball(float2 q, float3 b) {
    float2 d = q - b.xy;
    return b.z * b.z / (dot(d, d) + 0.0008);
}

// the thickness of the liquid: patches merging into one another, edges bent so none is a circle
float field(float2 q) {
    q += 0.028 * float2(sin(q.y * 5.0 + warp.x), cos(q.x * 4.5 - warp.y));
    return ball(q, b0) + ball(q, b1) + ball(q, b2);
}

float cover(float f) {
    return smoothstep(1.0, 2.6, f);
}

half4 main(float2 p) {
    float s = resolution.x;
    float2 q = p / s;
    float f0 = cover(field(q));
    float m = max(f0, melt);
    if (m < 0.003) return image.eval(p);

    // the slope of the liquid, per pixel: it bends the picture at the rim like a lens
    float e = 3.0;
    float gx = (cover(field(q + float2(e / s, 0.0))) - f0) / e;
    float gy = (cover(field(q + float2(0.0, e / s))) - f0) / e;
    float2 g = float2(gx, gy);
    float2 pc = p - g * 520.0 * (1.0 - melt);

    // a thick smear: a disc of taps, turned a different way at every pixel so it reads as grain, not as ghosts
    float radius = blurPx * m * (1.0 + 0.7 * melt);
    float turn = 6.2831853 * fract(52.9829189 * fract(dot(p, float2(0.06711056, 0.00583715))));
    half4 acc = half4(0.0);
    for (int i = 0; i < 20; i++) {
        float fi = float(i) + 0.5;
        float rr = sqrt(fi / 20.0) * radius;
        float th = fi * 2.3999632 + turn;
        acc += image.eval(pc + rr * float2(cos(th), sin(th)));
    }
    half4 c = acc / 20.0;

    // frost inside, and a thin light where the liquid is steepest
    float rim = clamp(length(g) * 45.0, 0.0, 1.0) * (1.0 - melt);
    float3 col = mix(float3(c.rgb), float3(1.0), 0.03 * m);
    col += float3(1.0) * rim * 0.05;
    return half4(half3(col), c.a);
}
"""
