/* Smart Shuffle behavior adapted from Duffed/tidaluna-smart-shuffle.
 * Copyright (c) 2026 Duffed and contributors. This file is licensed under MS-PL;
 * see docs/licenses/MS-PL.txt and THIRD_PARTY_NOTICES.md.
 */
package io.github.yyan115.opentune.core

/** Pure decision engine. An entry identifies an occurrence, not just a song. */
class SmartShuffle {
    data class Entry(val uid: String, val id: String, val recommendation: Boolean = false)
    data class Snapshot(val source: String, val transition: String, val entries: List<Entry>, val index: Int,
        val playing: Boolean, val eligible: Boolean) {
        val current get() = entries.getOrNull(index)
        val pending get() = entries.drop(index + 1).filter { it.recommendation }
    }
    data class Request(val generation: Long, val source: String, val transition: String, val seed: String)
    data class Decision(val remove: List<String> = emptyList(), val request: Request? = null)
    private var enabled = false
    private var source = ""
    private var active = false
    private var lastTransition = ""
    private var observedTransition = ""
    private var generation = 0L
    private var count = 0
    private val recent = ArrayDeque<String>()
    private val collection = mutableSetOf<String>()

    fun update(on: Boolean, snapshot: Snapshot): Decision {
        val changedSource = source != snapshot.source
        if (enabled != on || changedSource || active != (on && snapshot.eligible)) {
            generation++; count = 0; lastTransition = ""; observedTransition = ""
            if (changedSource) { collection.clear() }
        }
        enabled = on; source = snapshot.source; active = on && snapshot.eligible
        collection += snapshot.entries.filterNot { it.recommendation }.map { it.id }
        if (observedTransition != snapshot.transition) { generation++; observedTransition = snapshot.transition }
        if (!active) return Decision(remove = snapshot.pending.map { it.uid })
        val current = snapshot.current ?: return Decision()
        if (!snapshot.playing || snapshot.transition == lastTransition) return Decision()
        lastTransition = snapshot.transition
        recent.addLast(current.id)
        while (recent.size > 50) recent.removeFirst()
        if (current.recommendation || snapshot.pending.isNotEmpty()) return Decision()
        count++
        if (count < 3) return Decision()
        count = 0
        return Decision(request = Request(generation, source, snapshot.transition, current.id))
    }

    fun candidate(request: Request, now: Snapshot, tracks: List<Track>): Track? {
        if (!enabled || !active || !now.eligible || request.generation != generation ||
            request.source != now.source || request.transition != now.transition || now.pending.isNotEmpty()) return null
        val excluded = collection + now.entries.map { it.id } + recent + request.seed
        return tracks.firstOrNull { !it.isFile && it.id.isNotBlank() && it.id !in excluded }
    }
}
