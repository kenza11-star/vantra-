package com.vantra.music.ui.player.terminal

import java.text.BreakIterator
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

internal class TerminalCodeCaret {
    var row=0
    var col=0
    var active=false

    /** End (exclusive) of the key being struck right now; equals [col] when no key is in flight. */
    var next=0

    /** How far that key is down, 0..1. The letter fades in and the caret rides along with it. */
    var frac=1f
}

/** Immutable reveal schedule over ORIGINAL strings, built once per measured document. No text
 * frames, timers or per-character runtime objects. Weighted grapheme boundaries include spaces,
 * syntax, comments, quote characters and row-break pauses, not just the lyric substring.
 */
internal class TerminalTypingTimeline(private val doc:TerminalDocument) {
    private val stops=Array(doc.rows.size) { IntArray(0) }
    private val costs=Array(doc.rows.size) { FloatArray(0) }
    private val start=FloatArray(doc.rows.size)
    private val end=FloatArray(doc.rows.size)
    private val prefix=FloatArray(doc.lines.size)
    private val body=FloatArray(doc.lines.size)
    private val total=FloatArray(doc.lines.size)
    private var headerTotal=0f
    private var headerCount=0

    init {
        val boundaries=BreakIterator.getCharacterInstance(Locale.ROOT)
        var group=Int.MIN_VALUE;var units=0f
        for(r in doc.rows.indices) {
            val row=doc.rows[r]
            if(row.line!=group) { units=0f;group=row.line }
            start[r]=units
            boundaries.setText(row.text)
            val positions=IntArray(row.text.length);val weights=FloatArray(row.text.length);var count=0
            var from=boundaries.first();var to=boundaries.next();var cumulative=0f
            while(to!=BreakIterator.DONE) {
                val ch=row.text[from]
                cumulative+=keystroke(r,from,ch,if(from>0) row.text[from-1] else ' ')
                positions[count]=to;weights[count]=cumulative;count++
                from=to;to=boundaries.next()
            }
            stops[r]=if(count==positions.size) positions else positions.copyOf(count)
            costs[r]=if(count==weights.size) weights else weights.copyOf(count)
            end[r]=units+cumulative
            units=end[r]+3f // a real line-break pause, even for an empty row
            if(row.line<0) { headerTotal=units;headerCount=r+1 }
        }
        for(line in doc.lines) {
            val last=line.groupFirst+line.groupCount-1
            total[line.index]=if(last in end.indices) end[last] else 0f
            if(line.lyricCount>0) {
                val first=line.lyricFirst;val tail=first+line.lyricCount-1
                prefix[line.index]=start[first]+costAt(first,doc.rows[first].lyricFrom)
                body[line.index]=start[tail]+costAt(tail,doc.rows[tail].lyricTo)
            } else { prefix[line.index]=0f;body[line.index]=total[line.index] }
        }
    }

    /** Delay before one key lands, in relative units. Hands speed up and slow down (a slow drift),
     * every key is a little different, a word start takes a beat, punctuation is followed by a
     * pause and now and then there is a small hesitation. Everything is derived from the row and
     * position, so a seek or a redraw types exactly the same keys.
     */
    private fun keystroke(row:Int,index:Int,ch:Char,before:Char):Float {
        val drift=1f+0.20f*sin(index*0.83f+row*1.7f)+0.10f*sin(index*2.1f+row*0.9f)
        val h=(row*73856093) xor (index*19349663)
        val jitter=0.90f+((h ushr 3) and 15)*0.0133f
        val hesitate=if((((h ushr 9) and 0x7FFFFFFF) % 19)==0) 1.7f else 0f
        val stroke=if(ch==' ') 0.85f else 1f
        val pause=when(before) { ',' -> 2.4f; ';' -> 2.8f; ':' -> 1.9f; '.','!','?' -> 3.0f
            '(',')','[',']','{','}' -> 1.25f; '"','\'' -> 1.2f; else -> 0f }
        val wordStart=if(before==' ' && ch!=' ') 0.22f else 0f
        return stroke*drift*jitter+pause+wordStart+hesitate
    }

    private fun costAt(row:Int,column:Int):Float {
        val i=java.util.Arrays.binarySearch(stops[row],column)
        return when { column<=0 -> 0f; i>=0 -> costs[row][i]
            else -> costs[row].getOrElse((-i-2).coerceAtLeast(0)) {0f} }
    }

    private fun interval(line:Int):Long =
        (doc.lines.getOrNull(line+1)?.timeMs?.minus(doc.lines[line].timeMs) ?: 6000L).coerceAtLeast(0L)

    private fun lead(line:Int,speed:Float):Float {
        val previous=if(line==0) doc.lines[line].timeMs.toFloat() else
            (doc.lines[line].timeMs-doc.lines[line-1].timeMs).coerceAtLeast(0L)*0.28f
        return min(1200f/speed,min(previous,doc.lines[line].timeMs.coerceAtLeast(0L).toFloat()))
    }

    private fun budget(line:Int,timeMs:Long,speed:Float):Float {
        if(line<0) return headerTotal*(timeMs/(900f/speed)).coerceIn(0f,1f)
        val item=doc.lines[line];val gap=interval(line)
        if(timeMs>= (doc.lines.getOrNull(line+1)?.timeMs ?: Long.MAX_VALUE)) return total[line]
        if(gap==0L) return if(timeMs>=item.timeMs) total[line] else -1f
        val elapsed=(timeMs-item.timeMs).toFloat()
        val pre=lead(line,speed)
        // If a song starts directly at its first lyric, give the declaration at most 140ms.
        val boot=if(pre==0f && prefix[line]>0f) min(140f,gap*0.10f) else 0f
        if(elapsed < -pre) return -1f
        // The code in front of the words is typed during the lead-in, quicker at first, instead of
        // appearing in one piece.
        if(elapsed < boot && prefix[line]>0f) {
            val t=((elapsed+pre)/(pre+boot).coerceAtLeast(1f)).coerceIn(0f,1f)
            return prefix[line]*(1f-(1f-t)*(1f-t))
        }
        val bodyUnits=(body[line]-prefix[line]).coerceAtLeast(0f)
        // The words take a human pace (about 17 keys a second at Normal), stretched to fit when the
        // line has time and squeezed when it hasn't, and always done a little before the next line.
        val main=min(MaxTypingMs,min(max(180f,bodyUnits*PerUnitMs/speed),gap*0.86f)).coerceAtLeast(1f)
        if(elapsed<boot+main) {
            val t=((elapsed-boot)/main).coerceIn(0f,1f)
            return prefix[line]+bodyUnits*(t+EaseMix*(t*t*(3f-2f*t)-t))
        }
        val suffix=(total[line]-body[line]).coerceAtLeast(0f)
        val tail=min(max(35f,suffix*38f/speed),gap*0.12f).coerceAtLeast(1f)
        return body[line]+suffix*((elapsed-boot-main)/tail).coerceIn(0f,1f)
    }

    fun visibleChars(row:Int,timeMs:Long,speed:Float,enabled:Boolean):Int {
        val text=doc.rows[row].text
        if(!enabled) return text.length
        return visibleAt(row,budget(doc.rows[row].line,timeMs,speed.coerceIn(0.3f,3f)))
    }

    private fun visibleAt(row:Int,available:Float):Int {
        val a=costs[row];val value=available-start[row]
        var lo=0;var hi=a.size
        while(lo<hi) { val m=(lo+hi) ushr 1;if(a[m]<=value) lo=m+1 else hi=m }
        return if(lo==0) 0 else stops[row][lo-1]
    }

    /** Keep very long wrapped lyrics on the row actually being typed, rather than the legacy
     * lyric-only reveal speed. The authoritative line/timestamp is never changed.
     */
    fun focusCursor(cursor:TerminalCursor,caret:TerminalCodeCaret) {
        val line=doc.lines.getOrNull(cursor.line) ?: return
        if(cursor.idle || line.lyricCount<=0) return
        val r=caret.row.coerceIn(line.lyricFirst,line.lyricFirst+line.lyricCount-1)
        val row=doc.rows[r]
        val col=when {caret.row<r -> row.lyricFrom;caret.row>r -> row.lyricTo
            else -> caret.col.coerceIn(row.lyricFrom,row.lyricTo)}
        cursor.row=r;cursor.col=col
        cursor.typed=row.sliceStart+(col-row.lyricFrom)
    }

    fun locate(timeMs:Long,speed:Float,enabled:Boolean,out:TerminalCodeCaret) {
        if(doc.rows.isEmpty()) { out.row=-1;out.col=0;out.active=false;return }
        val rate=speed.coerceIn(0.3f,3f)
        var line=doc.lineAt(timeMs)
        val next=line+1
        if(next in doc.lines.indices && timeMs>=doc.lines[next].timeMs-lead(next,rate) &&
            (line<0 || budget(line,timeMs,rate)>=total[line])) line=next
        val first=if(line<0) 0 else doc.lines[line].groupFirst
        val count=if(line<0) headerCount else doc.lines[line].groupCount
        if(count<=0) { out.row=0;out.col=0;out.active=false;return }
        val last=(first+count-1).coerceAtMost(doc.rows.lastIndex)
        val b=if(enabled) budget(line,timeMs,rate) else Float.MAX_VALUE
        var lo=first;var hi=last
        while(lo<hi) { val m=(lo+hi) ushr 1;if(end[m]<=b) lo=m+1 else hi=m }
        out.row=lo
        out.col=if(enabled) visibleAt(lo,b) else doc.rows[lo].text.length
        out.active=enabled && b<end[last] && b>=start[first]
        if(enabled) strike(lo,b,out) else { out.next=out.col;out.frac=1f }
    }

    /** The key in flight in [row] at budget [available]: it is down over the last [StrokeUnits] of
     * its own delay, so a long pause before it does not fade it in slowly.
     */
    private fun strike(row:Int,available:Float,out:TerminalCodeCaret) {
        val a=costs[row];val value=available-start[row]
        var lo=0;var hi=a.size
        while(lo<hi) { val m=(lo+hi) ushr 1;if(a[m]<=value) lo=m+1 else hi=m }
        if(lo>=a.size || value<0f) { out.next=out.col;out.frac=1f;return }
        val before=if(lo==0) 0f else a[lo-1]
        val stroke=min(StrokeUnits,(a[lo]-before).coerceAtLeast(1e-3f))
        out.next=stops[row][lo]
        out.frac=((value-(a[lo]-stroke))/stroke).coerceIn(0f,1f)
    }

    private companion object {
        /** milliseconds per relative unit at Normal speed */
        const val PerUnitMs=48f
        const val MaxTypingMs=7000f
        /** how much of an ease-in/ease-out is blended into the otherwise even pace */
        const val EaseMix=0.25f
        const val StrokeUnits=1.5f
    }
}
