package io.github.yyan115.opentune

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import io.github.yyan115.opentune.ui.CatalogReportDialog
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CatalogReportTest {
    @get:Rule val compose = createComposeRule()
    @Test fun reportCanBeEditedBeforeOpeningOfficialSupport() {
        val opened = mutableListOf<String>()
        compose.setContent {
            CompositionLocalProvider(LocalUriHandler provides object : UriHandler {
                override fun openUri(uri: String) { opened += uri }
            }) {
                MaterialTheme { CatalogReportDialog("https://tidal.com/browse/track/42", "Track 42") {} }
            }
        }
        assertTrue(opened.isEmpty())
        compose.onNode(hasSetTextAction()).performTextReplacement("Correct artist: MEEK")
        compose.onNodeWithText("Copy and open support").performClick()
        compose.onNodeWithText("Copied. Nothing has been submitted yet.").assertExists()
        assertEquals(listOf("https://support.tidal.com/hc/en-us/requests/new?ticket_form_id=360000036038"), opened)
        compose.onNode(hasSetTextAction()).assertTextContains("Correct artist: MEEK")
    }
}
