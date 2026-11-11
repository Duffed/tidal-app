package io.github.yyan115.opentune

import io.github.yyan115.opentune.core.*
import io.github.yyan115.opentune.ui.*
import org.junit.Assert.*
import org.junit.Test

class ArtistCorrectionPlaybackTest {
    @Test fun visibleSectionsShareOneQueueInDisplayOrder() {
        val confirmed = Track("1", "Confirmed", "Artist")
        val possible = Track("2", "Possible", "Artist")
        val result = ArtistCorrectionResult(ArtistIdentity("a", "Artist"), listOf(confirmed), listOf(possible), emptyList())
        val state = ArtistCorrectionUi(result = result, showUnverified = true)
        assertEquals(listOf(confirmed, possible), state.playbackTracks())
        assertEquals(listOf(confirmed), state.copy(showUnverified = false).playbackTracks())
        assertEquals(listOf(possible), state.copy(result = result.copy(confirmed = emptyList())).playbackTracks())
    }
}
