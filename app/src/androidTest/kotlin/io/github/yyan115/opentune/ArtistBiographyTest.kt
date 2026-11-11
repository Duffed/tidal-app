package io.github.yyan115.opentune

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import io.github.yyan115.opentune.ui.ArtistBiography
import org.junit.Rule
import org.junit.Test

class ArtistBiographyTest {
    @get:Rule val compose = createComposeRule()

    @Test fun readMoreOpensScrollableBiographyAndCloseReturnsToPreview() {
        val biography = (1..30).joinToString("\n\n") { "Paragraph $it about the artist and their recordings." }
        compose.setContent { MaterialTheme { ArtistBiography("Example Artist", biography, "Example source") } }
        compose.onNodeWithTag("artist-biography-dialog").assertDoesNotExist()
        compose.onNodeWithText("Read more").performClick()
        compose.onNodeWithTag("artist-biography-dialog").assertIsDisplayed()
        compose.onNode(hasText(biography) and hasAnyAncestor(isDialog())).assertExists()
        compose.onNode(hasText("Example source", substring = true) and hasAnyAncestor(isDialog())).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithTag("artist-biography-dialog").assertDoesNotExist()
        compose.onNodeWithText("Read more").assertIsDisplayed()
    }

    @Test fun emptyBiographyDoesNotOfferReadMore() {
        compose.setContent { MaterialTheme { ArtistBiography("Artist", "", "") } }
        compose.onNodeWithText("Read more").assertDoesNotExist()
    }
}
