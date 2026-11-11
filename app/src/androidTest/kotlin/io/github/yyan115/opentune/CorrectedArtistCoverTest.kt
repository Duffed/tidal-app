package io.github.yyan115.opentune

import android.graphics.Bitmap
import android.net.Uri
import java.io.File
import androidx.compose.ui.graphics.toPixelMap
import androidx.activity.compose.setContent
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.util.UnstableApi
import io.github.yyan115.opentune.core.*
import io.github.yyan115.opentune.ui.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@UnstableApi
class CorrectedArtistCoverTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun correctedSongsShowArtworkAndTheWholeListRemainsReachable() {
        val file = File(compose.activity.cacheDir, "artist-cover-test.png")
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.BLUE) }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        val tracks = (1..30).map { Track("cover-$it", "Song $it", "Artist", artwork = Uri.fromFile(file).toString()) }
        val original = PageData("artist", "Artist", listOf(ArtistHeader("header", "", Release("fixture", "Original artist", "", artwork = Uri.fromFile(file).toString(), type = ReleaseType.ARTIST), "Original biography", "TIDAL", "radio")))
        val correction = ArtistCorrectionUi(result = ArtistCorrectionResult(ArtistIdentity("fixture", "Artist"), tracks, emptyList(), emptyList()))
        compose.runOnUiThread {
            val vm = ViewModelProvider(compose.activity)[MainViewModel::class.java]
            val pages = ViewModelProvider(compose.activity)[PagesViewModel::class.java]
            compose.activity.setContent {
                val scope = rememberCoroutineScope()
                OpenTuneTheme("light") {
                    val page = correctedArtistPage(original, correction)
                    assertEquals(original.modules.first(), page.modules.first())
                    val context = PageContext(AppState(), vm, pages, scope, {}, correction = correction)
                    LazyColumn(Modifier.testTag("test-artist-list")) { pageContent(page, PageUi(), "fixture", context, false) }
                }
            }
        }
        compose.onNodeWithTag("test-artist-list").performScrollToNode(hasText("Song 1"))
        val cover = compose.onNodeWithTag("track-artwork-cover-1", useUnmergedTree = true)
        cover.assertExists()
        compose.waitUntil(5_000) {
            val pixels = cover.captureToImage().toPixelMap()
            val center = pixels[pixels.width / 2, pixels.height / 2]
            center.blue > .9f && center.red < .1f
        }
        compose.onNodeWithTag("test-artist-list").performScrollToNode(hasText("Song 30"))
        compose.onNodeWithTag("track-artwork-cover-30", useUnmergedTree = true).assertIsDisplayed()
    }
}
