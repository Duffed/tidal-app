package io.github.yyan115.opentune.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.text.Normalizer
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class ArtistIdentity(val id: String, val name: String, val description: String = "", val country: String = "", val lifeSpan: String = "", val aliases: List<String> = emptyList()) {
    fun json() = JSONObject().put("id", id).put("name", name).put("description", description).put("country", country).put("lifeSpan", lifeSpan).put("aliases", JSONArray(aliases))
    fun displayName(query: String): String = aliases.firstOrNull { it.equals(query.trim(), ignoreCase = true) } ?: name
    fun matchesName(query: String): Boolean = name.equals(query.trim(), ignoreCase = true) || aliases.any { it.equals(query.trim(), ignoreCase = true) }
    companion object {
        fun fromJson(value: JSONObject) = ArtistIdentity(value.getString("id"), value.getString("name"),
            value.optString("description"), value.optString("country"), value.optString("lifeSpan"),
            value.optJSONArray("aliases")?.let { list -> (0 until list.length()).map { list.getString(it) } }.orEmpty())
    }
}

data class ArtistSearch(val artists: List<ArtistIdentity>, val total: Int)
data class ArtistRecording(val id: String, val title: String, val durationMs: Long, val isrcs: Set<String>)

/** An isolated, unauthenticated client: no TIDAL tokens are sent to MusicBrainz. */
class MusicBrainzClient(
    private val http: OkHttpClient = OkHttpClient(),
    private val baseUrl: String = "https://musicbrainz.org/ws/2/",
    private val userAgent: String = "OpenTune/0.7 (https://github.com/yyan115/tidal-app)",
    private val intervalMs: Long = 1100,
) {
    private val gate = Mutex()
    private var lastRequest = 0L

    private suspend fun get(path: String, query: Map<String, String>): JSONObject = gate.withLock {
        val remaining = intervalMs - (System.nanoTime() / 1_000_000 - lastRequest)
        if (remaining > 0) delay(remaining)
        val url = baseUrl.toHttpUrl().newBuilder().addPathSegment(path).addQueryParameter("fmt", "json")
        query.forEach { (key, value) -> url.addQueryParameter(key, value) }
        val request = Request.Builder().url(url.build()).header("User-Agent", userAgent).header("Accept", "application/json").build()
        lastRequest = System.nanoTime() / 1_000_000
        val response = suspendCancellableCoroutine<Response> { continuation ->
            val call = http.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) { if (!continuation.isCancelled) continuation.resumeWithException(e) }
                override fun onResponse(call: Call, response: Response) {
                    continuation.resume(response) { _, value, _ -> value.close() }
                }
            })
        }
        response.use {
            withContext(Dispatchers.IO) {
                if (!it.isSuccessful) throw IOException("MusicBrainz HTTP ${it.code}")
                JSONObject(it.body.string())
            }
        }
    }

    suspend fun search(name: String, offset: Int = 0): ArtistSearch {
        // Literal phrase; user-supplied Lucene operators cannot change the query.
        val escaped = name.replace("\\", "\\\\").replace("\"", "\\\"")
        val json = get("artist", mapOf("query" to "(artist:\"$escaped\" OR alias:\"$escaped\")", "limit" to "25", "offset" to offset.toString()))
        val artists = json.optJSONArray("artists").objects().map { artist ->
            val life = artist.optJSONObject("life-span")
            ArtistIdentity(artist.getString("id"), artist.getString("name"),
                listOf(artist.clean("type"), artist.clean("disambiguation")).filter(String::isNotBlank).joinToString(" · "),
                artist.clean("country").ifBlank { artist.optJSONObject("area")?.clean("name").orEmpty() },
                listOf(life?.clean("begin").orEmpty(), life?.clean("end").orEmpty()).filter(String::isNotBlank).joinToString(" – "),
                artist.optJSONArray("aliases").objects().map { it.clean("name") }.filter(String::isNotBlank).distinct())
        }
        return ArtistSearch(artists.sortedByDescending { it.matchesName(name) }, json.optInt("count", artists.size))
    }

    /** Browse all pages, including collaborations; never infer that an absent recording belongs elsewhere. */
    suspend fun recordings(artistId: String, progress: (Int, Int) -> Unit = { _, _ -> }): List<ArtistRecording> {
        val result = mutableListOf<ArtistRecording>()
        var offset = 0
        do {
            val json = get("recording", mapOf("artist" to artistId, "inc" to "isrcs+artist-credits", "limit" to "100", "offset" to "$offset"))
            val rows = json.optJSONArray("recordings").objects()
            val total = json.getInt("recording-count")
            if (rows.isEmpty() && offset < total) throw IOException("Incomplete MusicBrainz catalogue")
            result += rows.filter { row -> row.optJSONArray("artist-credit").objects().any { it.optJSONObject("artist")?.optString("id") == artistId } }
                .map { row -> ArtistRecording(row.getString("id"), row.getString("title"), row.optLong("length"),
                    row.optJSONArray("isrcs")?.let { array -> (0 until array.length()).map { array.getString(it) }.toSet() }.orEmpty()) }
            offset += rows.size
            progress(offset, total)
        } while (offset < total)
        return result.distinctBy { it.id }
    }
}

private fun JSONObject.clean(key: String) = if (isNull(key)) "" else optString(key)
private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).mapNotNull(::optJSONObject)

enum class ArtistMatch { Confirmed, Possible, Unverified }

/** Titles/durations alone can collide, so they are suggestions rather than confirmed identities. */
class ArtistRecordingMatcher(private val recordings: List<ArtistRecording>, private val artist: ArtistIdentity? = null) {
    private val isrcs = recordings.flatMap { it.isrcs }.map(::normalizeIsrc).filter(String::isNotBlank).toSet()
    private val titles = recordings.groupBy { normalizeTitle(it.title) }
    fun match(track: Track): ArtistMatch {
        if (track.isrc.isNotBlank() && normalizeIsrc(track.isrc) in isrcs) return ArtistMatch.Confirmed
        // Compare actual individual credits; splitting display text would corrupt names containing commas.
        if (artist != null && !(track.artistNames.ifEmpty { listOf(track.artist) }).any(artist::matchesName)) return ArtistMatch.Unverified
        val candidates = titles[normalizeTitle(track.title)].orEmpty()
        return if (track.durationMs > 0 && candidates.any { it.durationMs > 0 && kotlin.math.abs(it.durationMs - track.durationMs) <= 2000 })
            ArtistMatch.Possible else ArtistMatch.Unverified
    }
    /** One representative release per recording; unrelated catalogue tracks are not results. */
    fun select(tracks: List<Track>): Map<ArtistMatch, List<Track>> {
        val used = mutableSetOf<String>()
        val selected = mutableListOf<Pair<ArtistMatch, Track>>()
        recordings.forEach { recording ->
            val matcher = ArtistRecordingMatcher(listOf(recording), artist)
            val candidate = tracks.asSequence().filter { it.id !in used }
                .map { matcher.match(it) to it }.filter { it.first != ArtistMatch.Unverified }
                .sortedWith(compareBy<Pair<ArtistMatch, Track>> { it.first.ordinal }
                    .thenBy { kotlin.math.abs(it.second.durationMs - recording.durationMs) }
                    .thenBy { it.second.id }).firstOrNull()
            if (candidate != null) { used += candidate.second.id; selected += candidate }
        }
        return selected.groupBy({ it.first }, { it.second })
    }

    private fun normalizeIsrc(value: String) = value.replace("-", "").trim().uppercase(Locale.ROOT)
    // Do not strip version qualifiers such as live, remix or acoustic.
    private fun normalizeTitle(value: String) = Normalizer.normalize(value, Normalizer.Form.NFKC).lowercase(Locale.ROOT).trim().replace(Regex("\\s+"), " ")
}

/** Searches beyond the original (potentially merged or incomplete) TIDAL artist page. */
suspend fun discoverArtistTracks(
    artist: ArtistIdentity,
    recordings: List<ArtistRecording>,
    search: suspend (String) -> List<Track>,
    progress: (Int, Int) -> Unit = { _, _ -> },
): List<Track> {
    val matcher = ArtistRecordingMatcher(recordings, artist)
    val names = (listOf(artist.name) + artist.aliases).filter(String::isNotBlank).distinctBy { it.lowercase(Locale.ROOT) }
    val queries = recordings.map { it.title }.filter(String::isNotBlank).distinct().flatMap { title ->
        listOf(title) + names.map { name -> "$title $name" }
    }.distinct()
    val found = linkedMapOf<String, Track>()
    queries.forEachIndexed { index, query ->
        search(query).filter { matcher.match(it) != ArtistMatch.Unverified }.forEach { found[it.id] = it }
        progress(index + 1, queries.size)
    }
    return found.values.toList()
}
