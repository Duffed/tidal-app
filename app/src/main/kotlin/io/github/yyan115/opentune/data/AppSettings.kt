package io.github.yyan115.opentune.data

import android.content.Context
import io.github.yyan115.opentune.core.AudioQuality
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Settings(
    val quality: AudioQuality = AudioQuality.LOSSLESS,
    val mobileQuality: AudioQuality = AudioQuality.LOSSLESS,
    val downloadOnWifi: Boolean = true,
    val offline: Boolean = false,
    /** The id of a theme, or "system". See `appThemes`. */
    val theme: String = "dark",
    /** Volume normalization from ReplayGain data: "off", "track" or "album". */
    val normalization: String = "off",
    val equalizer: Boolean = false,
    /** Band levels in millibels, in band order. Empty until the listener changes a band. */
    val equalizerLevels: List<Int> = emptyList(),
    val bitPerfect: Boolean = false,
    val keepFavoritesOffline: Boolean = false,
    val scrobble: Boolean = false,
    val scrobbleServer: String = "https://api.listenbrainz.org",
    /** Whether the first-launch screen was answered. */
    val welcomeDone: Boolean = false,
    /** Keeps playing similar music from TIDAL when the queue ends. */
    val autoplay: Boolean = true,
)

class AppSettings(context: Context) {
    private val preferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private fun quality(key: String, fallback: AudioQuality) = runCatching {
        AudioQuality.valueOf(preferences.getString(key, fallback.name)!!)
    }.getOrDefault(fallback)
    private val mutable = MutableStateFlow(Settings(
        quality("quality", AudioQuality.LOSSLESS), quality("mobileQuality", AudioQuality.LOSSLESS),
        preferences.getBoolean("downloadOnWifi", true), preferences.getBoolean("offline", false),
        io.github.yyan115.opentune.ui.migrateTheme(preferences.getString("theme", "dark") ?: "dark", preferences.getString("colorTheme", null)),
        preferences.getString("normalization", "off") ?: "off", preferences.getBoolean("equalizer", false),
        preferences.getString("equalizerLevels", "").orEmpty().split(',').mapNotNull { it.trim().toIntOrNull() },
        preferences.getBoolean("bitPerfect", false), preferences.getBoolean("keepFavoritesOffline", false),
        preferences.getBoolean("scrobble", false), preferences.getString("scrobbleServer", "https://api.listenbrainz.org") ?: "https://api.listenbrainz.org",
        preferences.getBoolean("welcomeDone", false), preferences.getBoolean("autoplay", true)))
    val state = mutable.asStateFlow()
    fun update(transform: (Settings) -> Settings) {
        val next = transform(mutable.value)
        preferences.edit().putString("quality", next.quality.name).putString("mobileQuality", next.mobileQuality.name)
            .putBoolean("downloadOnWifi", next.downloadOnWifi).putBoolean("offline", next.offline)
            .putString("theme", next.theme).remove("colorTheme")
            .putString("normalization", next.normalization).putBoolean("equalizer", next.equalizer)
            .putString("equalizerLevels", next.equalizerLevels.joinToString(",")).putBoolean("bitPerfect", next.bitPerfect)
            .putBoolean("keepFavoritesOffline", next.keepFavoritesOffline).putBoolean("scrobble", next.scrobble)
            .putString("scrobbleServer", next.scrobbleServer).putBoolean("welcomeDone", next.welcomeDone).putBoolean("autoplay", next.autoplay).apply()
        mutable.value = next
    }
}
