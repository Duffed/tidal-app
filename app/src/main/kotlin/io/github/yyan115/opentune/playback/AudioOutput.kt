package io.github.yyan115.opentune.playback

import android.media.audiofx.Equalizer

/** The bands, range and presets of the built-in equalizer, or null when the device has none. */
data class EqualizerInfo(val minLevel: Int, val maxLevel: Int, val centerHz: List<Int>, val presets: List<Preset>) {
    data class Preset(val name: String, val levels: List<Int>)
    val bands get() = centerHz.size

    companion object {
        fun read(): EqualizerInfo? = runCatching {
            val effect = Equalizer(0, 0)
            try {
                val bands = effect.numberOfBands.toInt()
                val presets = (0 until effect.numberOfPresets).map { index ->
                    effect.usePreset(index.toShort())
                    Preset(effect.getPresetName(index.toShort()), (0 until bands).map { effect.getBandLevel(it.toShort()).toInt() })
                }
                EqualizerInfo(effect.bandLevelRange[0].toInt(), effect.bandLevelRange[1].toInt(),
                    (0 until bands).map { effect.getCenterFreq(it.toShort()) / 1000 }, presets)
            } finally { effect.release() }
        }.getOrNull()
    }
}
