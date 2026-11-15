package com.swara.player.data

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.database.Cursor
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Finds every song and video on the phone and SD card by itself (no folders to add),
 * keeps call recordings and other non-music audio out, and works out formats.
 */
object Library {
    private lateinit var app: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow(LibraryState())
    val state: StateFlow<LibraryState> = _state.asStateFlow()

    private val _formats = MutableStateFlow<Map<Long, AudioFormat>>(emptyMap())
    val formats: StateFlow<Map<Long, AudioFormat>> = _formats.asStateFlow()

    /** Everything found, before filters, so filter changes don't need a rescan. */
    private var allAudio: List<RawAudio> = emptyList()
    private var scanJob: Job? = null
    private var probeJob: Job? = null
    private var started = false

    fun init(context: Context) {
        app = context.applicationContext
    }

    /** Call once storage permission is granted. */
    fun start() {
        if (started) return
        started = true
        rescan()
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = rescan(debounce = true)
        }
        app.contentResolver.registerContentObserver(
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL), true, observer
        )
        app.contentResolver.registerContentObserver(
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL), true, observer
        )
        // Re-apply filters when the user changes them.
        scope.launch {
            Prefs.settings.map { it.filters to it.hiddenFolders }.distinctUntilChanged().drop(1)
                .collect { applyFilters(_state.value.videos) }
        }
    }

    fun rescan(debounce: Boolean = false) {
        scanJob?.cancel()
        scanJob = scope.launch {
            if (debounce) delay(1500)
            _state.value = _state.value.copy(scanning = true)
            val audio = withContext(Dispatchers.IO) { queryAudio() }
            val videos = withContext(Dispatchers.IO) { queryVideos() }
            allAudio = audio
            applyFilters(videos)
            probeFormats()
        }
    }

    private fun applyFilters(videos: List<VideoItem>) {
        val settings = Prefs.settings.value
        val visible = ArrayList<Track>(allAudio.size)
        val hidden = ArrayList<Pair<Track, HideReason>>()
        for (raw in allAudio) {
            val reason = classify(raw, settings)
            if (reason == null) visible += raw.track else hidden += raw.track to reason
        }
        visible.sortBy { it.title.lowercase() }
        val folders = allAudio.map { it.track.folder }.filter { it.isNotBlank() }.distinct().sorted()
        _state.value = LibraryState(
            scanning = false, tracks = visible, hidden = hidden, videos = videos, folders = folders
        )
    }

    // ---- Filtering -------------------------------------------------------------------------

    private val callPath = Regex("(?i)(^|/)(call|calls|callrec|call_rec|call recordings?|callrecordings?|record/call|phonerecord|call_recordings?)(/|$)")
    private val callPathLoose = Regex("(?i)(callrec|call_rec|call recording|callrecording|sound_recorder/call|recorder/call)")
    // "Call_..." style names, or a phone number (10+ digits, or +country code) in the file name.
    private val callName = Regex("(?i)^(call|rec_call|callrec|call_rec|record_call)[ _@-]|\\d{10,}|\\+\\d{1,3}[ -]?\\d{4,}[ -]?\\d{3,}")
    private val voicePath = Regex("(?i)(whatsapp|telegram|signal|messenger).*(voice|ptt|audio)|(^|/)voice notes?(/|$)")
    private val voiceName = Regex("(?i)^(ptt-|aud-\\d{8}-wa|voice_note|audio_\\d+@)")
    private val systemPath = Regex("(?i)(^|/)(ringtones?|notifications?|alarms?|ui|system/media)(/|$)")
    private val recorderPath = Regex("(?i)(^|/)(recordings?|sound_recorder|soundrecorder|voice recorder|voicerecorder|recorder|voice)(/|$)")

    private fun classify(raw: RawAudio, s: Settings): HideReason? {
        val t = raw.track
        val folder = t.folder
        if (s.hiddenFolders.any { folder.startsWith(it, ignoreCase = true) }) return HideReason.FOLDER
        val f = s.filters
        if (HideReason.CALL in f &&
            (callPath.containsMatchIn(folder) || callPathLoose.containsMatchIn(folder) || callName.containsMatchIn(t.fileName))
        ) return HideReason.CALL
        if (HideReason.VOICE_NOTE in f &&
            (voicePath.containsMatchIn(folder) || voiceName.containsMatchIn(t.fileName) || t.extension == "opus")
        ) return HideReason.VOICE_NOTE
        if (HideReason.SYSTEM in f &&
            (raw.isRingtone || raw.isAlarm || raw.isNotification || systemPath.containsMatchIn(folder))
        ) return HideReason.SYSTEM
        if (HideReason.RECORDER in f &&
            (raw.isRecording || recorderPath.containsMatchIn(folder) || t.extension in setOf("amr", "3gp", "3gpp", "awb"))
        ) return HideReason.RECORDER
        if (HideReason.SHORT in f && t.durationMs in 1 until 30_000) return HideReason.SHORT
        return null
    }

    // ---- MediaStore queries ----------------------------------------------------------------

    private class RawAudio(
        val track: Track,
        val isRingtone: Boolean,
        val isAlarm: Boolean,
        val isNotification: Boolean,
        val isRecording: Boolean,
    )

    private fun Cursor.str(name: String): String {
        val i = getColumnIndex(name)
        return if (i >= 0) getString(i) ?: "" else ""
    }

    private fun Cursor.long(name: String): Long {
        val i = getColumnIndex(name)
        return if (i >= 0) getLong(i) else 0L
    }

    private fun queryAudio(): List<RawAudio> {
        val uri = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val cols = mutableListOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.COMPOSER,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.RELATIVE_PATH,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.IS_RINGTONE,
            MediaStore.Audio.Media.IS_ALARM,
            MediaStore.Audio.Media.IS_NOTIFICATION,
        )
        if (Build.VERSION.SDK_INT >= 30) cols += "album_artist"
        if (Build.VERSION.SDK_INT >= 31) cols += "is_recording"

        val out = ArrayList<RawAudio>()
        app.contentResolver.query(uri, cols.toTypedArray(), null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val id = c.long(MediaStore.Audio.Media._ID)
                val fileName = c.str(MediaStore.Audio.Media.DISPLAY_NAME)
                val artist = c.str(MediaStore.Audio.Media.ARTIST).let { if (it == "<unknown>") "" else it }
                val track = Track(
                    id = id,
                    uri = ContentUris.withAppendedId(uri, id),
                    title = c.str(MediaStore.Audio.Media.TITLE).ifBlank { fileName.substringBeforeLast('.') },
                    artist = artist,
                    album = c.str(MediaStore.Audio.Media.ALBUM),
                    albumId = c.long(MediaStore.Audio.Media.ALBUM_ID),
                    albumArtist = c.str("album_artist"),
                    composer = c.str(MediaStore.Audio.Media.COMPOSER),
                    durationMs = c.long(MediaStore.Audio.Media.DURATION),
                    folder = c.str(MediaStore.Audio.Media.RELATIVE_PATH).trimEnd('/'),
                    fileName = fileName,
                    mime = c.str(MediaStore.Audio.Media.MIME_TYPE),
                    dateAdded = c.long(MediaStore.Audio.Media.DATE_ADDED),
                )
                out += RawAudio(
                    track,
                    isRingtone = c.long(MediaStore.Audio.Media.IS_RINGTONE) != 0L,
                    isAlarm = c.long(MediaStore.Audio.Media.IS_ALARM) != 0L,
                    isNotification = c.long(MediaStore.Audio.Media.IS_NOTIFICATION) != 0L,
                    isRecording = c.long("is_recording") != 0L,
                )
            }
        }
        return out
    }

    private fun queryVideos(): List<VideoItem> {
        val uri = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val cols = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.RELATIVE_PATH,
            MediaStore.Video.Media.DATE_ADDED,
        )
        val out = ArrayList<VideoItem>()
        app.contentResolver.query(uri, cols, null, null, "${MediaStore.Video.Media.DATE_ADDED} DESC")?.use { c ->
            while (c.moveToNext()) {
                val id = c.long(MediaStore.Video.Media._ID)
                out += VideoItem(
                    id = id,
                    uri = ContentUris.withAppendedId(uri, id),
                    title = c.str(MediaStore.Video.Media.DISPLAY_NAME),
                    durationMs = c.long(MediaStore.Video.Media.DURATION),
                    width = c.long(MediaStore.Video.Media.WIDTH).toInt(),
                    height = c.long(MediaStore.Video.Media.HEIGHT).toInt(),
                    sizeBytes = c.long(MediaStore.Video.Media.SIZE),
                    folder = c.str(MediaStore.Video.Media.RELATIVE_PATH).trimEnd('/'),
                    dateAdded = c.long(MediaStore.Video.Media.DATE_ADDED),
                )
            }
        }
        return out
    }

    // ---- Format detection (codec, bit depth, sample rate) ---------------------------------

    private fun probeFormats() {
        probeJob?.cancel()
        probeJob = scope.launch(Dispatchers.IO) {
            val tracks = _state.value.tracks
            val known = HashMap<Long, AudioFormat>()
            val missing = ArrayList<Track>()
            for (t in tracks) {
                val c = Prefs.cachedFormat(t)
                if (c != null) known[t.id] = c else missing += t
            }
            _formats.value = known.toMap()
            val batch = HashMap<Track, AudioFormat>()
            for ((i, t) in missing.withIndex()) {
                val f = runCatching { probe(t) }.getOrNull() ?: AudioFormat(t.extension.uppercase(), 0, 0)
                known[t.id] = f
                batch[t] = f
                if (i % 40 == 39) {
                    Prefs.cacheFormats(batch); batch.clear()
                    _formats.value = known.toMap()
                }
            }
            Prefs.cacheFormats(batch)
            _formats.value = known.toMap()
        }
    }

    fun probe(t: Track): AudioFormat {
        var mime = ""
        var rate = 0
        var bits = 0
        val ex = MediaExtractor()
        try {
            ex.setDataSource(app, t.uri, null)
            for (i in 0 until ex.trackCount) {
                val f = ex.getTrackFormat(i)
                val m = f.getString(MediaFormat.KEY_MIME) ?: continue
                if (!m.startsWith("audio/")) continue
                mime = m
                if (f.containsKey(MediaFormat.KEY_SAMPLE_RATE)) rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                if (f.containsKey("bits-per-sample")) bits = f.getInteger("bits-per-sample")
                if (bits == 0 && f.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                    bits = when (f.getInteger(MediaFormat.KEY_PCM_ENCODING)) {
                        android.media.AudioFormat.ENCODING_PCM_8BIT -> 8
                        android.media.AudioFormat.ENCODING_PCM_16BIT -> 16
                        android.media.AudioFormat.ENCODING_PCM_24BIT_PACKED -> 24
                        android.media.AudioFormat.ENCODING_PCM_32BIT,
                        android.media.AudioFormat.ENCODING_PCM_FLOAT -> 32
                        else -> 0
                    }
                }
                break
            }
        } catch (_: Exception) {
        } finally {
            ex.release()
        }
        if (Build.VERSION.SDK_INT >= 31 && (bits == 0 || rate == 0)) {
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(app, t.uri)
                if (bits == 0) bits = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE)?.toIntOrNull() ?: 0
                if (rate == 0) rate = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toIntOrNull() ?: 0
            } catch (_: Exception) {
            } finally {
                r.release()
            }
        }
        val ext = t.extension
        val codec = when {
            mime == "audio/flac" || ext == "flac" -> "FLAC"
            mime == "audio/alac" -> "ALAC"
            ext == "m4a" && (bits >= 16 || mime.isEmpty()) && mime != "audio/mp4a-latm" -> "ALAC"
            mime == "audio/mp4a-latm" -> "AAC"
            mime == "audio/mpeg" || ext == "mp3" -> "MP3"
            mime == "audio/raw" || ext == "wav" -> "WAV"
            ext == "aif" || ext == "aiff" -> "AIFF"
            ext == "dsf" || ext == "dff" -> "DSD"
            mime == "audio/opus" || ext == "opus" -> "OPUS"
            mime == "audio/vorbis" || ext == "ogg" -> "OGG"
            else -> ext.uppercase().ifEmpty { "AUDIO" }
        }
        // Lossy formats have no real bit depth; don't show one.
        if (codec in setOf("AAC", "MP3", "OPUS", "OGG")) bits = 0
        return AudioFormat(codec, bits, rate)
    }

    // ---- Grouping helpers -------------------------------------------------------------------

    fun groups(tracks: List<Track>, mode: GroupMode): List<Group> {
        val map = LinkedHashMap<String, MutableList<Track>>()
        for (t in tracks) for (k in mode.keysOf(t)) map.getOrPut(k) { mutableListOf() } += t
        return map.map { (k, v) -> Group(mode, k, v.sortedWith(compareBy({ it.album.lowercase() }, { it.title.lowercase() }))) }
            .sortedWith(compareBy({ it.name.startsWith("Unknown") }, { it.name.lowercase() }))
    }

    fun group(mode: GroupMode, name: String): Group? =
        groups(_state.value.tracks, mode).firstOrNull { it.name == name }
}
