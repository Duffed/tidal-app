package io.github.yyan115.opentune

import androidx.compose.ui.geometry.Rect
import io.github.yyan115.opentune.ui.playerMorphRect
import org.junit.Assert.*
import org.junit.Test

class PlayerMorphTest {
    @Test fun surfaceAndCoverReachTheirExactEndpointsWithoutOvershoot() {
        val mini = Rect(16f, 700f, 344f, 760f)
        val full = Rect(0f, 0f, 360f, 800f)
        assertEquals(mini, playerMorphRect(mini, full, -1f))
        assertEquals(full, playerMorphRect(mini, full, 2f))
        var previous = mini
        for (step in 1..100) {
            val next = playerMorphRect(mini, full, step / 100f)
            assertTrue(next.width >= previous.width)
            assertTrue(next.height >= previous.height)
            assertTrue(next.top <= previous.top)
            previous = next
        }
        assertEquals(Rect(8f, 350f, 352f, 780f), playerMorphRect(mini, full, .5f))
    }
}
