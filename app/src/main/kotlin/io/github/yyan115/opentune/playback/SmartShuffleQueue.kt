package io.github.yyan115.opentune.playback

import android.os.Bundle
import androidx.media3.common.MediaItem
import java.util.UUID

/** Private metadata survives reorder and distinguishes a recommendation from a manual duplicate. */
object SmartShuffleQueue {
    private const val UID = "opentune.entry"
    private const val SOURCE = "opentune.source"
    private const val RECOMMENDATION = "opentune.recommendation"
    fun uid(item: MediaItem): String = item.mediaMetadata.extras?.getString(UID).orEmpty()
    fun source(item: MediaItem): String = item.mediaMetadata.extras?.getString(SOURCE).orEmpty()
    fun contains(item: MediaItem): Boolean = item.mediaMetadata.extras?.getBoolean(RECOMMENDATION) == true
    fun mark(item: MediaItem, source: String = source(item), recommendation: Boolean = contains(item)): MediaItem =
        item.buildUpon().setMediaMetadata(item.mediaMetadata.buildUpon().setExtras(
            Bundle(item.mediaMetadata.extras ?: Bundle.EMPTY).apply {
                putString(UID, uid(item).ifBlank { UUID.randomUUID().toString() })
                putString(SOURCE, source); putBoolean(RECOMMENDATION, recommendation)
            }).build()).build()
}
