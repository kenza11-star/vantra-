package com.vantra.music.ui.player.terminal

import org.json.JSONObject

/** Track IDs are JSON keys, never filenames. Empty markers remove only that track's entry. */
internal object TerminalMarkerPreferences {
    fun text(raw:String,mediaId:String?):String = if(mediaId==null) "" else
        runCatching { JSONObject(raw).optString(mediaId,"") }.getOrDefault("")

    fun save(raw:String,mediaId:String,text:String):String {
        require(mediaId.isNotBlank()) { "No track selected" }
        require(TerminalEmphasis.parse(text).error==null) { "Invalid marker data" }
        // Do not destroy unrelated tracks if existing storage is malformed.
        val json=JSONObject(raw)
        if(text.isBlank()) json.remove(mediaId) else json.put(mediaId,text)
        return json.toString().also {require(it.length<=524288) {"Marker storage limit reached; remove unused markers"} }
    }
}
