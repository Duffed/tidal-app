package io.github.yyan115.opentune.ui

import io.github.yyan115.opentune.core.*

/** Preview only on overview pages with an actual way to open the complete list. */
internal fun PageData.showsAllTracks(module: TrackListModule, viewAll: Boolean): Boolean =
    viewAll || module.numbered || module.showAll || module.showMore == null ||
        modules.any { it is MixHeader || it is AlbumHeader }
