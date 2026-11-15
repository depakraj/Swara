package com.swara.player.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Settings(
    val groupMode: GroupMode = GroupMode.DIRECTOR,
    val autoplay: Boolean = true,
    val keepGoing: Boolean = true,
    val resume: Boolean = true,
    val showFormat: Boolean = true,
    val filters: Set<HideReason> = HideReason.entries.toSet(),
    val hiddenFolders: Set<String> = emptySet(),
)

/** Small key-value store for settings, favourites, play counts and resume points. */
object Prefs {
    private lateinit var sp: SharedPreferences
    private lateinit var formats: SharedPreferences
    private lateinit var videoPos: SharedPreferences

    private val _settings = MutableStateFlow(Settings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    private val _favourites = MutableStateFlow<Set<Long>>(emptySet())
    val favourites: StateFlow<Set<Long>> = _favourites.asStateFlow()

    fun init(context: Context) {
        sp = context.getSharedPreferences("swara", Context.MODE_PRIVATE)
        formats = context.getSharedPreferences("swara_formats", Context.MODE_PRIVATE)
        videoPos = context.getSharedPreferences("swara_video_positions", Context.MODE_PRIVATE)
        _settings.value = Settings(
            groupMode = runCatching { GroupMode.valueOf(sp.getString("groupMode", null) ?: "") }
                .getOrDefault(GroupMode.DIRECTOR),
            autoplay = sp.getBoolean("autoplay", true),
            keepGoing = sp.getBoolean("keepGoing", true),
            resume = sp.getBoolean("resume", true),
            showFormat = sp.getBoolean("showFormat", true),
            filters = sp.getStringSet("filters", null)
                ?.mapNotNull { runCatching { HideReason.valueOf(it) }.getOrNull() }?.toSet()
                ?: HideReason.entries.toSet(),
            hiddenFolders = sp.getStringSet("hiddenFolders", emptySet())!!.toSet(),
        )
        _favourites.value = sp.getStringSet("favourites", emptySet())!!
            .mapNotNull { it.toLongOrNull() }.toSet()
    }

    fun update(block: (Settings) -> Settings) {
        val s = block(_settings.value)
        _settings.value = s
        sp.edit()
            .putString("groupMode", s.groupMode.name)
            .putBoolean("autoplay", s.autoplay)
            .putBoolean("keepGoing", s.keepGoing)
            .putBoolean("resume", s.resume)
            .putBoolean("showFormat", s.showFormat)
            .putStringSet("filters", s.filters.map { it.name }.toSet())
            .putStringSet("hiddenFolders", s.hiddenFolders)
            .apply()
    }

    fun toggleFavourite(id: Long) {
        val f = _favourites.value.toMutableSet()
        if (!f.add(id)) f.remove(id)
        _favourites.value = f
        sp.edit().putStringSet("favourites", f.map { it.toString() }.toSet()).apply()
    }

    // Play counts
    fun playCount(id: Long): Int = sp.getInt("plays_$id", 0)
    fun addPlay(id: Long) {
        sp.edit().putInt("plays_$id", playCount(id) + 1).putLong("lastPlayed_$id", System.currentTimeMillis()).apply()
    }
    fun lastPlayed(id: Long): Long = sp.getLong("lastPlayed_$id", 0)

    // Resume point
    fun saveResume(trackId: Long, positionMs: Long, queueIds: List<Long>, source: String) {
        sp.edit()
            .putLong("resumeTrack", trackId)
            .putLong("resumePos", positionMs)
            .putString("resumeQueue", queueIds.take(500).joinToString(","))
            .putString("resumeSource", source)
            .apply()
    }
    fun resumeTrack(): Long = sp.getLong("resumeTrack", -1)
    fun resumePosition(): Long = sp.getLong("resumePos", 0)
    fun resumeQueue(): List<Long> =
        (sp.getString("resumeQueue", "") ?: "").split(",").mapNotNull { it.toLongOrNull() }
    fun resumeSource(): String = sp.getString("resumeSource", "") ?: ""

    // Cached audio formats, keyed by track id + date added so edits invalidate them.
    fun cachedFormat(t: Track): AudioFormat? {
        val v = formats.getString("${t.id}_${t.dateAdded}", null) ?: return null
        val p = v.split("|")
        if (p.size != 3) return null
        return AudioFormat(p[0], p[1].toIntOrNull() ?: 0, p[2].toIntOrNull() ?: 0)
    }
    fun cacheFormats(items: Map<Track, AudioFormat>) {
        val e = formats.edit()
        items.forEach { (t, f) -> e.putString("${t.id}_${t.dateAdded}", "${f.codec}|${f.bitsPerSample}|${f.sampleRate}") }
        e.apply()
    }

    // Video resume positions
    fun videoPosition(id: Long): Long = videoPos.getLong(id.toString(), 0)
    fun saveVideoPosition(id: Long, pos: Long, duration: Long) {
        val e = videoPos.edit()
        // Forget the position once the video is basically finished.
        if (duration > 0 && pos > duration - 15_000) e.remove(id.toString()) else e.putLong(id.toString(), pos)
        e.putLong("t_$id", System.currentTimeMillis()).apply()
    }
    fun videoWatchedAt(id: Long): Long = videoPos.getLong("t_$id", 0)
}
