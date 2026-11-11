package io.github.yyan115.opentune.core

import org.junit.Assert.*
import org.junit.Test

class SmartShuffleTest {
    private fun entry(id: String, recommendation: Boolean = false, uid: String = id) = SmartShuffle.Entry(uid, id, recommendation)
    private fun state(index: Int = 0, items: List<SmartShuffle.Entry> = listOf(entry("a"), entry("b"), entry("c"), entry("d")),
        source: String = "album", playing: Boolean = true, eligible: Boolean = true, transition: String = "${items.getOrNull(index)?.uid}") =
        SmartShuffle.Snapshot(source, transition, items, index, playing, eligible)
    private fun trigger(engine: SmartShuffle): SmartShuffle.Request {
        assertNull(engine.update(true, state(0)).request)
        assertNull(engine.update(true, state(1)).request)
        return requireNotNull(engine.update(true, state(2)).request)
    }
    @Test fun threeStartedSongsExcludeCollectionAndKeepRadioOrder() {
        val engine = SmartShuffle()
        val request = trigger(engine)
        val tracks = listOf(Track("a", "Existing", "Artist"), Track("new", "New", "Artist"), Track("later", "Later", "Artist"))
        assertEquals("new", engine.candidate(request, state(2), tracks)?.id)
        assertNull(engine.update(true, state(2)).request)
    }
    @Test fun pauseSeekAndDuplicateUpdatesDoNotAdvanceInterval() {
        val engine = SmartShuffle()
        engine.update(true, state())
        repeat(5) { engine.update(true, state(playing = false)); assertNull(engine.update(true, state()).request) }
        assertNull(engine.update(true, state(1)).request)
        assertNotNull(engine.update(true, state(2)).request)
    }
    @Test fun recommendationDoesNotCountAndWaitingRecommendationBlocksAnother() {
        val engine = SmartShuffle(); trigger(engine)
        val queue = listOf(entry("a"), entry("b"), entry("c"), entry("new", true), entry("d"), entry("e"), entry("f"))
        assertNull(engine.update(true, state(3, queue)).request)
        assertNull(engine.update(true, state(4, queue)).request)
        assertNull(engine.update(true, state(5, queue)).request)
        assertNotNull(engine.update(true, state(6, queue)).request)
        val moved = queue + entry("pending", true)
        assertNull(engine.update(true, state(0, moved, transition = "revisit")).request)
    }
    @Test fun disablingRemovesOnlyPendingRecommendationOccurrence() {
        val engine = SmartShuffle()
        val queue = listOf(entry("new", true, "playing"), entry("new", false, "manual"), entry("new", true, "pending"))
        assertEquals(listOf("pending"), engine.update(false, state(0, queue)).remove)
    }
    @Test fun staleRequestsAreRejectedAfterSkipSourceChangeDisableAndRepeatOne() {
        listOf<(SmartShuffle) -> Unit>(
            { it.update(true, state(3)) }, { it.update(true, state(2, source = "new album")) },
            { it.update(false, state(2)); it.update(true, state(2)) },
            { it.update(true, state(2, eligible = false)) }
        ).forEach { change ->
            val engine = SmartShuffle(); val request = trigger(engine); change(engine)
            assertNull(engine.candidate(request, state(2), listOf(Track("new", "New", "Artist"))))
        }
    }
    @Test fun failedSlotRetriesOnlyAfterAnotherFullInterval() {
        val engine = SmartShuffle(); val request = trigger(engine)
        assertNull(engine.candidate(request, state(2), emptyList()))
        assertNull(engine.update(true, state(3)).request)
        assertNull(engine.update(true, state(0, transition = "again-a")).request)
        assertNotNull(engine.update(true, state(1, transition = "again-b")).request)
    }
    @Test fun recentRecommendationsAreExcludedAfterChangingSource() {
        val engine = SmartShuffle(); trigger(engine)
        engine.update(true, state(0, listOf(entry("recent", true))))
        val queue = listOf(entry("x"), entry("y"), entry("z"))
        engine.update(true, state(0, queue, "new")); engine.update(true, state(1, queue, "new"))
        val request = requireNotNull(engine.update(true, state(2, queue, "new")).request)
        assertEquals("fresh", engine.candidate(request, state(2, queue, "new"),
            listOf(Track("recent", "Recent", "Artist"), Track("fresh", "Fresh", "Artist")))?.id)
    }
    @Test fun pausedNewTrackInvalidatesLateResultWithoutCountingUntilPlaybackStarts() {
        val engine = SmartShuffle(); val request = trigger(engine)
        engine.update(true, state(3, playing = false))
        assertNull(engine.candidate(request, state(3, playing = false), listOf(Track("new", "New", "Artist"))))
        assertNull(engine.update(true, state(3)).request)
    }
}
