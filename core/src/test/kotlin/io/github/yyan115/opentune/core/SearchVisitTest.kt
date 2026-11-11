package io.github.yyan115.opentune.core

import org.junit.Assert.*
import org.junit.Test

class SearchVisitTest {
    @Test fun missingTopHitPlaysTheChosenSongAndRetainsOtherResults() {
        val selected = Track("top", "Top", "Artist")
        val other = Track("other", "Other", "Artist")
        assertEquals(listOf(selected, other) to 0, searchPlaybackQueue(listOf(other), selected))
        assertEquals(listOf(other, selected) to 1, searchPlaybackQueue(listOf(other, selected), selected))
        assertEquals(listOf(selected) to 0, searchPlaybackQueue(emptyList(), selected))
    }

    @Test fun preservesTypesAndMovesReopenedResultToFront() {
        val track = SearchVisit(track = Track("1", "Song", "Artist"))
        val album = SearchVisit(release = Release("1", "Album", "Artist"))
        val playlist = SearchVisit(release = Release("2", "Playlist", "Owner", type = ReleaseType.PLAYLIST))
        val history = SearchVisit.prepend(listOf(playlist, track, album), album)
        assertEquals(listOf(album, playlist, track), history)
        assertEquals(history, SearchVisit.decode(SearchVisit.encode(history)))
    }
    @Test fun capsHistoryAndToleratesCorruptStorage() {
        var history = emptyList<SearchVisit>()
        repeat(30) { history = SearchVisit.prepend(history, SearchVisit(track = Track("$it", "Song", "Artist"))) }
        assertEquals(20, history.size)
        assertEquals("29", history.first().track!!.id)
        assertTrue(SearchVisit.decode("broken").isEmpty())
    }
}
