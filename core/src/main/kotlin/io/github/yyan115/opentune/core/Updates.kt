package io.github.yyan115.opentune.core

import org.json.JSONObject

/** TIDAL's personal activity feed, including albums, EPs and singles. */
object UpdatesCodec {
    fun releases(json: JSONObject): List<Release> {
        val activities = json.optJSONArray("activities") ?: return emptyList()
        return (0 until activities.length()).mapNotNull { index ->
            val activity = activities.optJSONObject(index)?.optJSONObject("followableActivity") ?: return@mapNotNull null
            if (activity.optString("activityType") != "NEW_ALBUM_RELEASE") return@mapNotNull null
            val album = activity.optJSONObject("album") ?: return@mapNotNull null
            if (album.optString("id").isBlank() || album.isNull("id")) return@mapNotNull null
            JsonCodec.release(album, ReleaseType.ALBUM)
        }.distinctBy { it.id }
    }
}
