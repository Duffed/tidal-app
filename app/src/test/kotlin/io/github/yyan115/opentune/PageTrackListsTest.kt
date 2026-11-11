package io.github.yyan115.opentune

import io.github.yyan115.opentune.core.*
import io.github.yyan115.opentune.ui.showsAllTracks
import org.junit.Assert.*
import org.junit.Test

class PageTrackListsTest {
    private val songs = TrackListModule("songs", "Songs", (1..30).map { Track("$it", "Song $it", "Artist") },
        showMore = "pages/more", dataPath = "mixes/daily/items", total = 100)
    @Test fun mixWithHeaderShowsEveryLoadedSong() {
        val page = PageData("mix", "Daily discovery", listOf(MixHeader("header", "", Release("daily", "Daily discovery", "", type = ReleaseType.MIX), "", ""), songs))
        assertTrue(page.showsAllTracks(songs, false))
    }
    @Test fun overviewPreviewRequiresAnAccessibleFullList() {
        val page = PageData("home", "Home", listOf(songs))
        assertFalse(page.showsAllTracks(songs, false))
        assertTrue(page.showsAllTracks(songs.copy(showMore = null), false))
        assertTrue(page.showsAllTracks(songs, true))
    }
    @Test fun numberedAlbumsAndCorrectedArtistListsAreComplete() {
        val page = PageData("page", "", listOf(songs))
        assertTrue(page.showsAllTracks(songs.copy(numbered = true), false))
        assertTrue(page.showsAllTracks(songs.copy(showAll = true), false))
    }
}
