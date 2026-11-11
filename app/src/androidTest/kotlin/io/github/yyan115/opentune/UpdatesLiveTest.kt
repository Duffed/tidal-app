package io.github.yyan115.opentune

import android.os.Bundle
import androidx.media3.common.util.UnstableApi
import androidx.test.platform.app.InstrumentationRegistry
import io.github.yyan115.opentune.core.*
import io.github.yyan115.opentune.data.AppSettings
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.Assert.*

@UnstableApi
class UpdatesLiveTest {
    @Test fun personalFeedUsesRealActivitiesAndOpensAnAlbum() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveUpdates") == "true")
        val instrument = InstrumentationRegistry.getInstrumentation()
        val app = instrument.targetContext.applicationContext as OpenTuneApp
        assumeTrue(app.tidal.signedIn)
        val json = app.tidal.updatesJson()
        assertTrue("The personal feed must contain activities", json.has("activities"))
        val releases = UpdatesCodec.releases(json)
        releases.firstOrNull()?.let { release ->
            val page = app.tidal.page("pages/album", mapOf("albumId" to release.id))
            assertTrue(page.modules.any { it is AlbumHeader && it.album.id == release.id })
        }
        instrument.sendStatus(0, Bundle().apply { putString("stream", "Personal feed: ${json.getJSONArray("activities").length()} activities, ${releases.size} unique releases; first release opened.\n") })
    }

    @Test fun layoutAndTabPreferencesSurviveReloadAndBackup() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as OpenTuneApp
        val original = app.settings.state.value
        try {
            app.settings.update { it.copy(showUpdatesTab = false, albumsGrid = false, playlistsGrid = true) }
            val restored = AppSettings(app).state.value
            assertFalse(restored.showUpdatesTab)
            assertFalse(restored.albumsGrid)
            assertTrue(restored.playlistsGrid)
            val values = io.github.yyan115.opentune.data.BackupManager(app).toJson().getJSONObject("settings")
            assertFalse(values.getBoolean("showUpdatesTab"))
            assertFalse(values.getBoolean("albumsGrid"))
            assertTrue(values.getBoolean("playlistsGrid"))
        } finally { app.settings.update { original } }
    }
}
