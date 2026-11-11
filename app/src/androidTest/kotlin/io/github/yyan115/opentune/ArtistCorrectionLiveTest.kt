package io.github.yyan115.opentune

import androidx.media3.common.util.UnstableApi
import androidx.test.platform.app.InstrumentationRegistry
import io.github.yyan115.opentune.core.discoverArtistTracks
import io.github.yyan115.opentune.core.ArtistMatch
import io.github.yyan115.opentune.core.ArtistRecordingMatcher
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Read-only, opt-in integration check; no library edits, playback or saved correction. */
@UnstableApi
class ArtistCorrectionLiveTest {
    @Test fun selectedMeekProfileProducesSixDistinctRecordings() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveArtistCorrection") == "true")
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as OpenTuneApp
        assumeTrue(app.tidal.signedIn && !app.settings.state.value.offline)
        val artist = app.musicBrainz.search("Meek").artists.first { it.id == "6982015a-b96e-4bcd-8875-6eecc12c16a8" }
        val recordings = app.musicBrainz.recordings(artist.id)
        assertEquals(6, recordings.size)
        val candidates = discoverArtistTracks(artist, recordings, search = { app.tidal.search(it).tracks })
            .map { app.tidal.trackDetails(it.id) }
        val selected = ArtistRecordingMatcher(recordings, artist).select(candidates)
        assertEquals("Exactly one TIDAL result per MusicBrainz recording", 6, selected.values.flatten().size)
        assertEquals(3, selected[ArtistMatch.Confirmed].orEmpty().size)
        assertEquals(3, selected[ArtistMatch.Possible].orEmpty().size)
        recordings.forEach { recording ->
            assertEquals("Missing or duplicate recording: ${recording.title}", 1,
                selected.values.flatten().count { ArtistRecordingMatcher(listOf(recording), artist).match(it) != ArtistMatch.Unverified })
        }
    }

    @Test fun stageNameMeekFindsGeorgiaAndHerRecording() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveArtistCorrection") == "true")
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as OpenTuneApp
        assumeTrue(!app.settings.state.value.offline)
        val artist = app.musicBrainz.search("Meek").artists.first { it.id == "28677645-3731-496d-99b0-ed7c247c8228" }
        assertEquals("MEEK", artist.displayName("Meek"))
        val recording = app.musicBrainz.recordings(artist.id).first { it.id == "7c386ad4-a394-45e9-bdbf-d400e8fb753b" }
        assertTrue("GB5KW2502205" in recording.isrcs)
        assumeTrue(app.tidal.signedIn)
        val tracks = discoverArtistTracks(artist, listOf(recording), search = { app.tidal.search(it).tracks })
        assertTrue("Title search should find MEEK outside the original artist page", tracks.isNotEmpty())
        val matcher = ArtistRecordingMatcher(listOf(recording))
        val details = tracks.map { app.tidal.trackDetails(it.id) }
        assertTrue("Title matches must remain visible as confirmed or possible results",
            details.any { matcher.match(it) != ArtistMatch.Unverified })
    }

    @Test fun musicBrainzIdentityMatchesAnActualTidalRecording() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveArtistCorrection") == "true")
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as OpenTuneApp
        assumeTrue(app.tidal.signedIn && !app.settings.state.value.offline)
        val candidates = app.musicBrainz.search("Ghost").artists
        val artist = candidates.first { it.name == "Ghost" && it.country == "SE" && it.description.contains("metal", ignoreCase = true) }
        assertTrue(candidates.any { it.name == "Ghost" && it.id != artist.id })
        val recordings = app.musicBrainz.recordings(artist.id)
        assertTrue(recordings.isNotEmpty())
        val tracks = app.tidal.search("Ghost Square Hammer").tracks.filter { it.artist.contains("Ghost") && it.title.contains("Square Hammer") }
        assertTrue("The TIDAL search must return the recording", tracks.isNotEmpty())
        val matcher = ArtistRecordingMatcher(recordings)
        val matches = tracks.take(3).map { app.tidal.trackDetails(it.id) }.map(matcher::match)
        assertTrue("A live TIDAL ISRC should match the selected MusicBrainz artist", ArtistMatch.Confirmed in matches)
    }
}
