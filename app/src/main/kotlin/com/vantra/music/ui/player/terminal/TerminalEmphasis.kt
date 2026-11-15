package com.vantra.music.ui.player.terminal

internal enum class TerminalEmphasisKind { PUSH, POWER, GENTLE, STILL, CLIMAX }
internal data class TerminalEmphasisMarker(val atMs:Long,val kind:TerminalEmphasisKind,val amount:Float,val durationMs:Long)
internal class TerminalEmphasisFrame {
    var zoom=0f;var weight=0f;var stillness=0f;var climax=0f
    fun clear() { zoom=0f;weight=0f;stillness=0f;climax=0f }
}
internal object TerminalEmphasis {
    class ParseResult(val markers:List<TerminalEmphasisMarker>,val error:String?)
    /** at=focus peak on the SAME offset-adjusted lyric clock. Explicit human marks, never keywords. */
    fun parse(text:String):ParseResult {
        if(text.length>24000) return ParseResult(emptyList(),"Marker text is too long (max 24,000 characters)")
        val marks=ArrayList<TerminalEmphasisMarker>()
        for((index,line) in text.lineSequence().withIndex()) {
            if(line.isBlank()) continue
            if(marks.size>=128) return ParseResult(emptyList(),"Maximum 128 markers per track")
            try {
                val p=line.split('|').map {it.trim()}
                require(p.size==4)
                val t=p[0].split(':');require(t.size==2)
                val minutes=t[0].toLong();val seconds=t[1].toDouble()
                require(minutes in 0..1440 && seconds.isFinite() && seconds>=0 && seconds<60)
                val kind=TerminalEmphasisKind.valueOf(p[1].uppercase(java.util.Locale.ROOT))
                val amount=p[2].toFloat();val duration=p[3].toLong()
                require(amount.isFinite() && amount in 0f..1.5f && duration in 600..8000)
                marks.add(TerminalEmphasisMarker(minutes*60000+(seconds*1000).toLong(),kind,amount,duration))
            } catch(_:IllegalArgumentException) {
                return ParseResult(emptyList(),"Line ${index+1}: mm:ss.sss | push/power/gentle/still/climax | 0..1.5 | 600..8000 ms")
            }
        }
        return ParseResult(marks.sortedBy {it.atMs},null)
    }

    private fun ease(x:Float):Float { val t=x.coerceIn(0f,1f);return t*t*t*(t*(t*6f-15f)+10f) }

    fun sample(markers:List<TerminalEmphasisMarker>,timeMs:Long,gain:Float,out:TerminalEmphasisFrame) {
        out.clear()
        if(gain<=0f) return
        for(i in markers.indices) {
            val m=markers[i]
            if(m.atMs-600>timeMs) break
            if(timeMs>m.atMs+m.durationMs) continue
            val elapsed=timeMs-m.atMs
            // 200ms anticipation/stabilisation, 400ms push, 30% hold, quintic release.
            val hold=m.durationMs*0.30f
            val shape=when {elapsed < -400 -> 0f;elapsed < 0 -> ease((elapsed+400)/400f)
                elapsed<=hold -> 1f;else -> 1f-ease((elapsed-hold)/(m.durationMs-hold))}
            val pre=if(elapsed < -400) ease((elapsed+600)/200f)*0.25f else if(elapsed<0) 0.25f else 0f
            val w=(shape*m.amount*gain).coerceIn(0f,1f)
            out.weight=maxOf(out.weight,w,pre*m.amount*gain)
            if(m.kind==TerminalEmphasisKind.STILL) {out.stillness=maxOf(out.stillness,w);continue}
            val amplitude=when(m.kind) {TerminalEmphasisKind.PUSH->0.075f;TerminalEmphasisKind.POWER->0.11f
                TerminalEmphasisKind.GENTLE->0.045f;TerminalEmphasisKind.CLIMAX->0.10f;TerminalEmphasisKind.STILL->0f}
            out.zoom=maxOf(out.zoom,(amplitude*shape*m.amount*gain).coerceIn(0f,0.13f))
            if(m.kind==TerminalEmphasisKind.CLIMAX) out.climax=maxOf(out.climax,w)
        }
    }
}

/** One retained intent, composed in TerminalCamera; no independent animation owner. */
class TerminalCameraIntent {
    var focusZoom=0f;var focusWeight=0f;var stillness=0f;var climax=0f
    var bassGain=1f;var onset=0f;var reconstruct=false
}
