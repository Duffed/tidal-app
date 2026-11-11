package io.github.yyan115.opentune.core

import org.json.JSONArray
import org.json.JSONObject

/** An opened search result, separate from the query that found it. */
data class SearchVisit(val track: Track? = null, val release: Release? = null) {
    init { require((track != null) != (release != null)) }
    val key: String get() = track?.let { "track:${it.id}" } ?: "${release!!.type}:${release.id}"
    companion object {
        fun prepend(history: List<SearchVisit>, visit: SearchVisit) = (listOf(visit) + history.filterNot { it.key == visit.key }).take(20)
        fun encode(history: List<SearchVisit>): String = JSONArray().apply {
            history.forEach { visit -> put(JSONObject().apply {
                visit.track?.let { put("track", JsonCodec.encodeTrack(it)) }
                visit.release?.let { put("release", JsonCodec.encodeRelease(it)) }
            }) }
        }.toString()
        fun decode(value: String?): List<SearchVisit> = runCatching {
            val array = JSONArray(value ?: "[]")
            (0 until array.length()).mapNotNull { index -> runCatching {
                val entry = array.getJSONObject(index)
                entry.optJSONObject("track")?.let { SearchVisit(track = JsonCodec.decodeTrack(it)) }
                    ?: SearchVisit(release = JsonCodec.decodeRelease(entry.getJSONObject("release")))
            }.getOrNull() }.distinctBy { it.key }.take(20)
        }.getOrDefault(emptyList())
    }
}

/** Top hits may be absent from the ordinary search results. Always include the selected song. */
fun searchPlaybackQueue(results: List<Track>, selected: Track): Pair<List<Track>, Int> {
    val index = results.indexOfFirst { it.id == selected.id }
    return if (index >= 0) results to index else (listOf(selected) + results) to 0
}
