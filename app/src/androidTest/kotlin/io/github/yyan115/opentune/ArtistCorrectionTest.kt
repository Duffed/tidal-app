package io.github.yyan115.opentune

import androidx.compose.ui.graphics.asAndroidBitmap
import java.io.File
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.media3.common.util.UnstableApi
import androidx.test.platform.app.InstrumentationRegistry
import io.github.yyan115.opentune.core.*
import io.github.yyan115.opentune.ui.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@UnstableApi
class ArtistCorrectionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun stageNameIsVisibleAlongsideTheCanonicalName() {
        val artist = ArtistIdentity("georgia", "Georgia Meek", country = "GB", aliases = listOf("MEEK"))
        var chosen: ArtistIdentity? = null
        compose.setContent { OpenTuneTheme("light") {
            ArtistIdentityDialog("Meek", ArtistCorrectionUi(candidates = listOf(artist), totalCandidates = 1, searched = true, query = "Meek"),
                {}, {}, { chosen = it }, {})
        } }
        compose.onNodeWithText("MEEK").assertIsDisplayed()
        compose.onNodeWithText("Georgia Meek").assertIsDisplayed()
        saveScreenshot("artist-search", compose.onNodeWithTag("artist-identity-dialog"))
        compose.onNodeWithText("MEEK").performClick()
        compose.runOnIdle { assertEquals(artist, chosen) }
    }

    private fun saveScreenshot(name: String, node: SemanticsNodeInteraction) {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        node.assertIsDisplayed()
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            ?: error("Screenshot capture failed")
        File(app.getExternalFilesDir(null), "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun choosingOneOfTwoIdenticalNamesUsesItsIdentity() {
        val first = ArtistIdentity("sweden", "Ghost", "Metal band", "SE")
        val second = ArtistIdentity("japan", "Ghost", "Psych rock", "JP")
        var chosen: ArtistIdentity? = null
        compose.setContent { MaterialTheme {
            ArtistIdentityDialog("Ghost", ArtistCorrectionUi(candidates = listOf(first, second), totalCandidates = 2, searched = true),
                {}, {}, { chosen = it }, {})
        } }
        compose.onNodeWithText("Psych rock · JP").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(second, chosen) }
    }

    @Test fun savedSelectionSurvivesReloadAndCanBeResetOffline() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as OpenTuneApp
        val id = "correction-test-${System.nanoTime()}"
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val controller = ArtistCorrections(app, scope)
        val result = ArtistCorrectionResult(ArtistIdentity("selected", "Same name", "The intended artist"),
            listOf(Track("right", "Right song", "Same name", isrc = "USAAA0000001")),
            listOf(Track("maybe", "Possible song", "Same name")), listOf(Track("unknown", "Unknown song", "Same name")))
        val prefs = app.getSharedPreferences("artist-corrections", android.content.Context.MODE_PRIVATE)
        try {
            prefs.edit().putString(controller.key(id), result.json()).commit()
            controller.load(id)
            assertEquals(result, controller.state.value[controller.key(id)]!!.result)
            compose.setContent {
                val states by controller.state.collectAsState()
                MaterialTheme { ArtistCorrectionControls(id, "Same name", states.getValue(controller.key(id)), controller, online = false) }
            }
            compose.onNodeWithText("Show unverified songs").assertDoesNotExist()
            compose.onNodeWithContentDescription("Expand artist identity").performClick()
            saveScreenshot("artist-correction", compose.onNodeWithTag("artist-correction-card"))
            compose.onNodeWithText("Show unverified songs").assertIsOff().performClick()
            compose.onNodeWithText("Show unverified songs").assertIsOn()
            compose.runOnIdle { assertTrue(controller.state.value[controller.key(id)]!!.showUnverified) }
            compose.onNodeWithText("Search TIDAL by song title").assertIsOff().performClick()
            val restored = ArtistCorrections(app, scope)
            restored.load(id)
            assertTrue(restored.state.value.getValue(restored.key(id)).showUnverified)
            assertTrue(restored.state.value.getValue(restored.key(id)).searchTitles)
            compose.onNodeWithContentDescription("Artist identity options").performClick()
            compose.onNodeWithText("Reset correction").performClick()
            compose.runOnIdle { assertNull(controller.state.value[controller.key(id)]!!.result) }
            val reloaded = ArtistCorrections(app, scope)
            reloaded.load(id)
            assertNull(reloaded.state.value[reloaded.key(id)]!!.result)
            assertTrue(reloaded.state.value.getValue(reloaded.key(id)).showUnverified)
            assertTrue(reloaded.state.value.getValue(reloaded.key(id)).searchTitles)
        } finally { prefs.edit().remove(controller.key(id)).remove("showUnverified:${controller.key(id)}").remove("searchTitles:${controller.key(id)}").commit(); scope.cancel() }
    }
}
