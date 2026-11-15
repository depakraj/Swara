package com.swara.player.data

import android.net.Uri

data class Track(
    val id: Long,
    val uri: Uri,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val albumArtist: String,
    val composer: String,
    val durationMs: Long,
    val folder: String,
    val fileName: String,
    val mime: String,
    val dateAdded: Long,
) {
    /** The music director: composer tag, or album artist when it differs from the singer. */
    val director: String
        get() {
            val c = composer.trim()
            if (c.isNotEmpty()) return c
            val aa = albumArtist.trim()
            if (aa.isNotEmpty() && !aa.equals(artist.trim(), ignoreCase = true) &&
                !aa.equals("Various Artists", ignoreCase = true)
            ) return aa
            return UNKNOWN_DIRECTOR
        }

    val extension: String get() = fileName.substringAfterLast('.', "").lowercase()

    companion object {
        const val UNKNOWN_DIRECTOR = "Unknown director"
    }
}

data class AudioFormat(
    val codec: String,
    val bitsPerSample: Int,
    val sampleRate: Int,
) {
    val isHiRes: Boolean get() = bitsPerSample >= 24 || sampleRate > 48_000

    /** e.g. "FLAC 24/96" */
    val short: String
        get() {
            val parts = mutableListOf<String>()
            if (bitsPerSample > 0) parts += bitsPerSample.toString()
            if (sampleRate > 0) parts += khz(sampleRate)
            return if (parts.isEmpty()) codec else "$codec ${parts.joinToString("/")}"
        }

    /** e.g. "FLAC · 24-bit / 96 kHz" */
    val long: String
        get() {
            val bits = if (bitsPerSample > 0) "$bitsPerSample-bit" else null
            val rate = if (sampleRate > 0) "${khz(sampleRate)} kHz" else null
            val detail = listOfNotNull(bits, rate).joinToString(" / ")
            return if (detail.isEmpty()) codec else "$codec · $detail"
        }

    companion object {
        fun khz(rate: Int): String {
            val k = rate / 1000.0
            return if (k % 1.0 == 0.0) k.toInt().toString() else String.format("%.1f", k)
        }
    }
}

enum class HideReason(val title: String, val subtitle: String) {
    CALL("Call recordings", "Call recorder folders and files named like call recordings"),
    RECORDER("Voice recorder notes", "Recordings from the sound recorder app"),
    VOICE_NOTE("WhatsApp and Telegram voice notes", "Voice messages and audio sent in chats"),
    SYSTEM("Ringtones, alarms and notification sounds", "Phone system sounds and ringtone folders"),
    SHORT("Clips under 30 seconds", "Short sound effects and audio snippets"),
    FOLDER("Folders you hid", "Folders you chose to keep out of the library"),
}

enum class GroupMode(val label: String, val plural: String) {
    DIRECTOR("Music director", "Music directors"),
    SINGER("Singer", "Singers"),
    ALBUM("Album", "Albums");

    fun keysOf(t: Track): List<String> = when (this) {
        DIRECTOR -> splitNames(t.director, Track.UNKNOWN_DIRECTOR)
        SINGER -> splitNames(t.artist, "Unknown singer")
        ALBUM -> listOf(t.album.ifBlank { "Unknown album" })
    }

    companion object {
        private val separators = Regex("\\s*(,|;|/| & | and |\\|)\\s*")
        fun splitNames(raw: String, fallback: String): List<String> {
            val v = raw.trim()
            if (v.isEmpty() || v.equals("<unknown>", ignoreCase = true)) return listOf(fallback)
            return v.split(separators).map { it.trim() }.filter { it.isNotEmpty() }.distinct()
                .ifEmpty { listOf(fallback) }
        }
    }
}

data class Group(val mode: GroupMode, val name: String, val tracks: List<Track>) {
    val albumCount: Int get() = tracks.map { it.albumId }.distinct().size
}

data class VideoItem(
    val id: Long,
    val uri: Uri,
    val title: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    val folder: String,
    val dateAdded: Long,
) {
    val resolutionLabel: String
        get() {
            val h = minOf(width, height)
            return when {
                h >= 2000 -> "4K"
                h >= 1000 -> "1080p"
                h >= 700 -> "720p"
                h > 0 -> "${h}p"
                else -> ""
            }
        }
}

data class LibraryState(
    val scanning: Boolean = true,
    val tracks: List<Track> = emptyList(),
    val hidden: List<Pair<Track, HideReason>> = emptyList(),
    val videos: List<VideoItem> = emptyList(),
    val folders: List<String> = emptyList(),
)

fun formatDuration(ms: Long): String {
    if (ms <= 0) return "0:00"
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%d:%02d", m, s)
}
