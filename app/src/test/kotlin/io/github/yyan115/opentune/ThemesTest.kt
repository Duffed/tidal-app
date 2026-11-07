package io.github.yyan115.opentune

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import io.github.yyan115.opentune.ui.SYSTEM_THEME
import io.github.yyan115.opentune.ui.appThemes
import io.github.yyan115.opentune.ui.migrateTheme
import io.github.yyan115.opentune.ui.themeFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemesTest {
    /** The contrast ratio of two colours as WCAG defines it. Body text needs 4.5 and large text or icons need 3. */
    private fun contrast(a: Color, b: Color): Double {
        val high = maxOf(a.luminance(), b.luminance()).toDouble(); val low = minOf(a.luminance(), b.luminance()).toDouble()
        return (high + .05) / (low + .05)
    }

    @Test fun everyThemeHasReadableText() {
        val failures = mutableListOf<String>()
        for (theme in appThemes) {
            val scheme = theme.scheme
            fun check(name: String, foreground: Color, background: Color, minimum: Double) {
                val ratio = contrast(foreground, background)
                if (ratio < minimum) failures += "${theme.id}: $name is %.2f, below %.1f".format(ratio, minimum)
            }
            check("text on the background", scheme.onBackground, scheme.background, 7.0)
            check("secondary text on the background", scheme.onSurfaceVariant, scheme.background, 4.5)
            check("secondary text on a raised surface", scheme.onSurfaceVariant, scheme.surfaceContainerHigh, 4.5)
            check("text on a raised surface", scheme.onSurface, scheme.surfaceContainerHighest, 7.0)
            check("accent on the background", scheme.primary, scheme.background, 4.5)
            check("accent on a raised surface", scheme.primary, scheme.surfaceContainerHigh, 4.0)
            check("text on a button", scheme.onPrimary, scheme.primary, 4.5)
            check("borders on the background", scheme.outline, scheme.background, 3.0)
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test fun darkThemesAreDarkAndLightThemesAreLight() {
        for (theme in appThemes) assertEquals("${theme.id} says that it is ${if (theme.dark) "dark" else "light"}", theme.dark, theme.scheme.background.luminance() < .3f)
    }

    @Test fun idsAreUniqueAndTheNeutralOnesComeFirst() {
        assertEquals(appThemes.size, appThemes.map { it.id }.toSet().size)
        assertEquals(listOf("light", "dark"), appThemes.take(2).map { it.id })
        assertTrue("There are enough themes to choose from", appThemes.size >= 12)
        assertEquals("Each theme has its own accent", appThemes.size, appThemes.map { it.scheme.primary }.toSet().size - appThemes.count { it.id == "obsidian" }.let { 0 })
    }

    @Test fun theSystemThemeFollowsTheDevice() {
        assertEquals("light", themeFor(SYSTEM_THEME, systemDark = false).id)
        assertEquals("dark", themeFor(SYSTEM_THEME, systemDark = true).id)
        assertEquals("An unknown id falls back to Dark", "dark", themeFor("missing", systemDark = false).id)
    }

    @Test fun oldSettingsMapToTheNearestTheme() {
        assertEquals("gold", migrateTheme("dark", "gallery"))
        assertEquals("ocean", migrateTheme("dark", "cobalt"))
        assertEquals("ember", migrateTheme("dark", "copper"))
        assertEquals("dark", migrateTheme("dark", "studio"))
        assertEquals("paper", migrateTheme("light", "paper"))
        assertEquals("light", migrateTheme("light", "gallery"))
        assertEquals("system", migrateTheme("system", "cobalt"))
        assertEquals("A theme id that is already new stays", "cyberpunk", migrateTheme("cyberpunk", null))
        assertEquals("dark", migrateTheme("unknown", null))
    }
}
