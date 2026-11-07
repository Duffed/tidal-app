package io.github.yyan115.opentune.core

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin

/** Turns a short piece of audio into the levels of a few frequency bands, for a visualizer. */
object Spectrum {
    /** The number of samples that one analysis reads. It must be a power of two. */
    const val SIZE = 1024

    private val window = FloatArray(SIZE) { (0.5 - 0.5 * cos(2 * PI * it / (SIZE - 1))).toFloat() }
    private val cosines = FloatArray(SIZE / 2) { cos(2 * PI * it / SIZE).toFloat() }
    private val sines = FloatArray(SIZE / 2) { -sin(2 * PI * it / SIZE).toFloat() }

    /**
     * The level of each of [count] bands between [lowHz] and [highHz], spaced like musical pitch, from 0 for silence to 1 for a full scale tone.
     * [samples] holds [SIZE] samples of one channel and [sampleRate] is their rate.
     */
    fun bands(samples: FloatArray, sampleRate: Int, count: Int = 32, lowHz: Float = 40f, highHz: Float = 16_000f): FloatArray {
        require(samples.size >= SIZE) { "At least $SIZE samples are needed" }
        val re = FloatArray(SIZE) { samples[it] * window[it] }
        val im = FloatArray(SIZE)
        fft(re, im)
        val nyquist = sampleRate / 2f
        val top = minOf(highHz, nyquist)
        val result = FloatArray(count)
        val binWidth = sampleRate.toFloat() / SIZE
        for (band in 0 until count) {
            val from = lowHz * (top / lowHz).pow(band.toFloat() / count)
            val to = lowHz * (top / lowHz).pow((band + 1f) / count)
            val first = (from / binWidth).toInt().coerceIn(1, SIZE / 2 - 1)
            val last = (to / binWidth).toInt().coerceIn(first, SIZE / 2 - 1)
            var peak = 0f
            for (bin in first..last) peak = maxOf(peak, hypot(re[bin], im[bin]))
            // A full scale sine gives a magnitude of about SIZE / 4 with this window. The scale runs from -70 dB to 0 dB.
            val db = 20f * (ln((peak / (SIZE / 4f)).toDouble().coerceAtLeast(1e-7)) / ln(10.0)).toFloat()
            result[band] = ((db + 70f) / 70f).coerceIn(0f, 1f)
        }
        return result
    }

    private fun fft(re: FloatArray, im: FloatArray) {
        var j = 0
        for (i in 1 until SIZE) {
            var bit = SIZE shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) { val a = re[i]; re[i] = re[j]; re[j] = a; val b = im[i]; im[i] = im[j]; im[j] = b }
        }
        var length = 2
        while (length <= SIZE) {
            val step = SIZE / length
            for (start in 0 until SIZE step length) {
                for (k in 0 until length / 2) {
                    val c = cosines[k * step]; val s = sines[k * step]
                    val a = start + k; val b = a + length / 2
                    val tr = re[b] * c - im[b] * s; val ti = re[b] * s + im[b] * c
                    re[b] = re[a] - tr; im[b] = im[a] - ti
                    re[a] += tr; im[a] += ti
                }
            }
            length = length shl 1
        }
    }
}
