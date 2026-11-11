package io.github.yyan115.opentune.ui

import io.github.yyan115.opentune.OpenTuneApp
import io.github.yyan115.opentune.R
import io.github.yyan115.opentune.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject

data class ArtistCorrectionResult(
    val artist: ArtistIdentity,
    val confirmed: List<Track>,
    val possible: List<Track>,
    val unverified: List<Track>,
) {
    fun json() = JSONObject().put("artist", artist.json()).put("confirmed", JsonCodec.encodeTracks(confirmed))
        .put("possible", JsonCodec.encodeTracks(possible)).put("unverified", JsonCodec.encodeTracks(unverified)).toString()
    companion object {
        fun parse(text: String): ArtistCorrectionResult = JSONObject(text).let {
            ArtistCorrectionResult(ArtistIdentity.fromJson(it.getJSONObject("artist")), JsonCodec.decodeTracks(it.getString("confirmed")),
                JsonCodec.decodeTracks(it.getString("possible")), JsonCodec.decodeTracks(it.getString("unverified")))
        }
    }
}

data class ArtistCorrectionUi(
    val loaded: Boolean = false, val result: ArtistCorrectionResult? = null,
    val searching: Boolean = false, val candidates: List<ArtistIdentity> = emptyList(), val totalCandidates: Int = 0,
    val query: String = "", val searched: Boolean = false,
    val scanning: Boolean = false, val progress: String = "", val error: String? = null,
    val showUnverified: Boolean = false, val searchTitles: Boolean = false,
)

/** The visible corrected sections form one playback queue, in display order. */
internal fun ArtistCorrectionUi.playbackTracks(): List<Track> = result?.let {
    (it.confirmed + if (showUnverified) it.possible + it.unverified else emptyList()).distinctBy { track -> track.id }
}.orEmpty()

/** Account-scoped cache. A failed or cancelled scan never replaces a previous successful selection. */
@androidx.media3.common.util.UnstableApi
class ArtistCorrections(private val app: OpenTuneApp, private val scope: CoroutineScope) {
    private val mutable = MutableStateFlow<Map<String, ArtistCorrectionUi>>(emptyMap())
    val state = mutable.asStateFlow()
    private val jobs = mutableMapOf<String, Job>()
    private val searches = mutableMapOf<String, Job>()
    private val generations = mutableMapOf<String, Int>()
    private val cache = app.getSharedPreferences("artist-corrections", android.content.Context.MODE_PRIVATE)
    fun key(id: String) = "${app.tidal.accountId}:$id"
    private fun update(key: String, transform: (ArtistCorrectionUi) -> ArtistCorrectionUi) =
        mutable.update { it + (key to transform(it[key] ?: ArtistCorrectionUi())) }

    fun load(id: String) {
        val key = key(id)
        if (mutable.value[key]?.loaded == true) return
        val result = cache.getString(key, null)?.let { runCatching { ArtistCorrectionResult.parse(it) }.getOrNull() }
        update(key) { it.copy(loaded = true, result = result,
            showUnverified = cache.getBoolean("showUnverified:$key", false),
            searchTitles = cache.getBoolean("searchTitles:$key", false)) }
    }

    fun search(id: String, name: String, more: Boolean = false) {
        val key = key(id)
        searches.remove(key)?.cancel()
        val old = mutable.value[key] ?: ArtistCorrectionUi()
        val previous = if (more && old.query == name) old.candidates else emptyList()
        if (name.isBlank()) return
        update(key) { it.copy(searching = true, query = name, candidates = previous, error = null) }
        searches[key] = scope.launch {
            try {
                val result = app.musicBrainz.search(name, previous.size)
                update(key) { it.copy(searching = false, searched = true, candidates = previous + result.artists, totalCandidates = result.total) }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                update(key) { it.copy(searching = false, error = app.pageError(error)) }
            }
        }
    }

    fun stopSearch(id: String) { val key = key(id); searches.remove(key)?.cancel(); update(key) { it.copy(searching = false) } }
    fun showUnverified(id: String, show: Boolean) {
        load(id)
        cache.edit().putBoolean("showUnverified:${key(id)}", show).apply()
        update(key(id)) { it.copy(showUnverified = show) }
    }
    fun searchTitles(id: String, enabled: Boolean) {
        load(id)
        cancel(id)
        cache.edit().putBoolean("searchTitles:${key(id)}", enabled).apply()
        update(key(id)) { it.copy(searchTitles = enabled) }
    }

    fun cancel(id: String) {
        val key = key(id)
        generations[key] = (generations[key] ?: 0) + 1
        jobs.remove(key)?.cancel()
        update(key) { it.copy(scanning = false, progress = "") }
    }

    fun reset(id: String) {
        cancel(id)
        cache.edit().remove(key(id)).apply()
        update(key(id)) { it.copy(result = null, error = null) }
    }

    fun choose(id: String, artist: ArtistIdentity) {
        load(id)
        cancel(id)
        val key = key(id)
        val generation = generations[key]
        val searchTitles = mutable.value.getValue(key).searchTitles
        update(key) { it.copy(scanning = true, error = null, progress = app.getString(R.string.artist_correction_loading_catalogue)) }
        jobs[key] = scope.launch {
            try {
                val recordings = app.musicBrainz.recordings(artist.id) { done, total ->
                    update(key) { it.copy(progress = app.getString(R.string.artist_correction_recordings, done, total)) }
                }
                val matcher = ArtistRecordingMatcher(recordings, artist)
                val tracks = linkedMapOf<String, Track>()
                // Top tracks include singles/collaborations that may be absent from the album listing.
                app.tidal.allTracks(Release(id, artist.name, artist.name, type = ReleaseType.ARTIST)).forEach { tracks[it.id] = it }
                val albums = app.tidal.artistAlbums(id).distinctBy { it.id }
                albums.forEachIndexed { index, album ->
                    ensureActive()
                    update(key) { it.copy(progress = app.getString(R.string.artist_correction_albums, (index + 1).toInt(), albums.size)) }
                    app.tidal.allTracks(album).forEach { tracks[it.id] = it }
                }
                if (searchTitles) discoverArtistTracks(artist, recordings, search = { query -> app.tidal.search(query).tracks }) { done, total ->
                    update(key) { it.copy(progress = app.getString(R.string.artist_correction_searching_titles, done, total)) }
                }.forEach { tracks.putIfAbsent(it.id, it) }
                val detailedTracks = mutableListOf<Track>()
                tracks.values.forEachIndexed { index, original ->
                    ensureActive()
                    update(key) { it.copy(progress = app.getString(R.string.artist_correction_tracks, (index + 1).toInt(), tracks.size)) }
                    // The listing often omits ISRC, while the track detail response includes it.
                    val track = if (original.isrc.isBlank()) {
                        try { original.copy(isrc = app.tidal.trackDetails(original.id).isrc) }
                        catch (error: ServiceException) {
                            if (error.status != 404) throw error
                            original
                        }
                    } else original
                    detailedTracks += track
                }
                ensureActive()
                if (key(id) != key || generations[key] != generation) return@launch
                val groups = matcher.select(detailedTracks)
                val result = ArtistCorrectionResult(artist, groups[ArtistMatch.Confirmed].orEmpty(), groups[ArtistMatch.Possible].orEmpty(), groups[ArtistMatch.Unverified].orEmpty())
                cache.edit().putString(key, result.json()).apply()
                update(key) { it.copy(loaded = true, scanning = false, progress = "", result = result) }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                update(key) { it.copy(scanning = false, progress = "", error = app.pageError(error)) }
            }
        }
    }
}
