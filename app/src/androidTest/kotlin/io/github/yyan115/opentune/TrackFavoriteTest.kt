package io.github.yyan115.opentune

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import io.github.yyan115.opentune.core.Track
import io.github.yyan115.opentune.ui.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TrackFavoriteTest {
    @get:Rule val compose = createComposeRule()
    @Test fun heartUpdatesWithoutStartingPlayback() {
        var plays = 0
        compose.setContent {
            var favorites by remember { mutableStateOf(emptySet<String>()) }
            CompositionLocalProvider(LocalFavoriteTrackIds provides favorites,
                LocalToggleTrackFavorite provides { track -> favorites = if (track.id in favorites) favorites - track.id else favorites + track.id }) {
                MaterialTheme { TrackRow(Track("song", "Song", "Artist"), onPlay = { plays++ }, onMenu = {}) }
            }
        }
        compose.onNodeWithTag("track-favorite-song").assertIsOff().performClick().assertIsOn()
        compose.onNodeWithTag("track-favorite-song").performClick().assertIsOff()
        assertEquals(0, plays)
    }
}
