package io.github.yyan115.opentune.playback

import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import io.github.yyan115.opentune.OpenTuneApp
import io.github.yyan115.opentune.core.SmartShuffle
import kotlinx.coroutines.*

/** Runs in the playback service, including when the player screen is closed. */
@UnstableApi
internal class SmartShufflePlayback(private val app: OpenTuneApp, private val player: Player, private val scope: CoroutineScope) {
    private val engine = SmartShuffle()
    private var request: SmartShuffle.Request? = null
    private var job: Job? = null
    private var lastTimeline: androidx.media3.common.Timeline? = null
    private var lastInput: List<Any?>? = null
    private var transition = 0L
    fun transitioned() { transition++ }
    private fun snapshot(): SmartShuffle.Snapshot {
        val items = (0 until player.mediaItemCount).map(player::getMediaItemAt)
        val current = player.currentMediaItem
        val source = items.firstOrNull { !ManualQueue.contains(it) && !SmartShuffleQueue.contains(it) }
        return SmartShuffle.Snapshot(
            "${app.tidal.accountId}:${source?.let(SmartShuffleQueue::source)?.ifBlank { source.let(SmartShuffleQueue::uid) }.orEmpty()}",
            "${current?.let(SmartShuffleQueue::uid)}:$transition",
            items.mapIndexed { i, item -> SmartShuffle.Entry(SmartShuffleQueue.uid(item).ifBlank { "${item.mediaId}:$i" }, item.mediaId, SmartShuffleQueue.contains(item)) },
            player.currentMediaItemIndex, player.isPlaying,
            app.tidal.signedIn && !app.settings.state.value.offline && current != null &&
                !io.github.yyan115.opentune.core.isDeviceId(current.mediaId) && player.repeatMode != Player.REPEAT_MODE_ONE)
    }
    fun tick() {
        // Position-only ticks must not rebuild/scan a potentially thousands-long queue.
        // Timeline replacement still invalidates this when tracks are added, removed or shuffled.
        val timeline = player.currentTimeline
        val input = listOf(player.currentMediaItemIndex, player.isPlaying, player.repeatMode,
            transition, app.settings.state.value.smartShuffle, app.settings.state.value.offline,
            app.tidal.accountId)
        if (lastTimeline === timeline && lastInput == input) return
        lastTimeline = timeline
        lastInput = input
        val now = snapshot()
        val decision = engine.update(app.settings.state.value.smartShuffle, now)
        request?.let { pending ->
            if (!app.settings.state.value.smartShuffle || !now.eligible || pending.source != now.source || pending.transition != now.transition) {
                job?.cancel(); job = null; request = null
            }
        }
        // Descending indices keep every other queue occurrence intact.
        if (decision.remove.isNotEmpty()) (player.mediaItemCount - 1 downTo (player.currentMediaItemIndex + 1).coerceAtLeast(0)).forEach { i ->
            if (SmartShuffleQueue.uid(player.getMediaItemAt(i)) in decision.remove) player.removeMediaItem(i)
        }
        val next = decision.request ?: return
        job?.cancel(); request = next
        job = scope.launch {
            try {
                val candidates = app.tidal.smartShuffleRadio(next.seed)
                val latest = snapshot()
                // Re-check settings/account/source even if their change preceded the next service tick.
                if (!app.settings.state.value.smartShuffle || !latest.eligible) return@launch
                val track = engine.candidate(next, latest, candidates) ?: return@launch
                val item = SmartShuffleQueue.mark(app.sources.unresolved(track),
                    source = player.currentMediaItem?.let(SmartShuffleQueue::source).orEmpty(), recommendation = true)
                player.addMediaItem(ManualQueue.insertionIndex(player, next = false), item)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                // Skip this slot; normal playback and the next interval continue unchanged.
                android.util.Log.w("OpenTune", "Smart Shuffle recommendation unavailable (${error.javaClass.simpleName})")
            } finally { if (request == next) { request = null; job = null } }
        }
    }
}
