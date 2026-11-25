package com.vantra.music.ui.player.liquid

import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import com.vantra.music.ui.component.agslRenderEffect
import com.vantra.music.ui.component.createAgslShader
import com.vantra.music.ui.component.setAgslUniform

/**
 * The liquid glass of the Liquid player style: the picture under it (the song's video, or its
 * cover) is blurred and then pushed around by a slow, wavy displacement, so it moves like oil on
 * water instead of just sitting soft. Android 13+ gets both; Android 12 the blur alone; older
 * phones neither (the style then shows the picture darker, drifting).
 *
 * Every Android class is named only inside the guarded functions below, so nothing here is
 * resolved on a phone that doesn't have it.
 */
internal class LiquidFx {
    private var shader: Any? = null

    /** the effect for this moment, or null where the phone can't blur; [timeSec] loops every 100 s */
    fun build(widthPx: Float, heightPx: Float, timeSec: Float, blurPx: Float): RenderEffect? {
        if (Build.VERSION.SDK_INT < 31 || widthPx < 1f || heightPx < 1f) return null
        return buildGuarded(widthPx, heightPx, timeSec, blurPx.coerceAtLeast(0.5f))
    }

    @RequiresApi(31)
    private fun buildGuarded(widthPx: Float, heightPx: Float, timeSec: Float, blurPx: Float): RenderEffect {
        val blur = android.graphics.RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP)
        if (Build.VERSION.SDK_INT < 33) return blur.asComposeRenderEffect()
        val warp = warpGuarded(widthPx, heightPx, timeSec)
        // the blur first, then the waves pushed through the blurred picture
        return android.graphics.RenderEffect.createChainEffect(warp, blur).asComposeRenderEffect()
    }

    @RequiresApi(33)
    private fun warpGuarded(widthPx: Float, heightPx: Float, timeSec: Float): android.graphics.RenderEffect {
        val s = shader ?: createAgslShader(WarpSource).also { shader = it }
        s.setAgslUniform("resolution", widthPx, heightPx)
        s.setAgslUniform("time", timeSec)
        return agslRenderEffect(s, "image")
    }
}

// every wave turns a whole number of times in the 100 s loop, so the wrap never shows
private const val WarpSource = """
uniform shader image;
uniform float2 resolution;
uniform float time;

half4 main(float2 p) {
    float2 uv = p / resolution;
    float w = 6.2831853 / 100.0;
    float t = time;
    float2 d = float2(
        sin(uv.y * 5.0 + t * w * 8.0) + 0.5 * sin(uv.y * 12.0 - t * w * 5.0 + uv.x * 3.0),
        cos(uv.x * 4.0 + t * w * 6.0) + 0.5 * cos(uv.x * 10.0 + t * w * 7.0 - uv.y * 2.0)
    );
    float amp = resolution.y * 0.05;
    return image.eval(p + d * amp);
}
"""
