package io.github.yyan115.opentune

import android.os.Bundle
import androidx.media3.common.util.UnstableApi
import androidx.test.platform.app.InstrumentationRegistry
import io.github.yyan115.opentune.core.*
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.Assert.*

@UnstableApi
class RekaLiveTest {
    @Test fun albumAndCollaborations() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveReka") == "true")
        val instrument = InstrumentationRegistry.getInstrumentation()
        val app = instrument.targetContext.applicationContext as OpenTuneApp
        assumeTrue(app.tidal.signedIn)
        val artist = ArtistIdentity("1a0a7a17-a39a-4e36-b3b4-1b97d4f0ef27", "Reka")
        val recordings = app.musicBrainz.recordings(artist.id)
        val report = StringBuilder("recordings=${recordings.size}\n")
        val search = app.tidal.search("Todo Avaricia")
        val album = search.albums.first { it.id == "125927517" }
        val page = app.tidal.page("pages/album", mapOf("albumId" to album.id))
        assertTrue(page.modules.any { it is AlbumHeader && it.album.id == album.id })
        val tracks = app.tidal.allTracks(album)
        assertEquals(4, tracks.size)
        val selected = ArtistRecordingMatcher(recordings, artist).select(tracks).values.flatten()
        assertEquals("All four collaboration tracks must survive identity matching", tracks.map { it.id }.toSet(), selected.map { it.id }.toSet())
        val albums = app.tidal.artistAlbums(tracks.first().artistId)
        assertTrue("The catalogue scan must include the collaboration EP", albums.any { it.id == album.id })
        report.append("albumListed=true matched=${selected.size}\n")
        instrument.sendStatus(0, Bundle().apply { putString("stream", report.toString()) })
    }
}
