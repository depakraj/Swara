package com.swara.player.player

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import androidx.core.content.ContextCompat
import com.swara.player.data.GroupMode
import com.swara.player.data.Library
import com.swara.player.data.Prefs
import com.swara.player.data.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer

enum class RepeatMode { OFF, ALL, ONE }

data class PlayerState(
    val queue: List<Track> = emptyList(),
    val index: Int = -1,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val repeat: RepeatMode = RepeatMode.OFF,
    /** Where the queue came from, e.g. "A. R. Rahman". */
    val source: String = "",
    val sourceMode: GroupMode? = null,
    val sleepEndsAt: Long = 0,
) {
    val current: Track? get() = queue.getOrNull(index)
    val next: Track? get() = queue.getOrNull(index + 1)
}

/** Audio playback through libVLC. One instance for the whole app. */
object PlayerController {
    private lateinit var app: Context
    private lateinit var libVLC: LibVLC
    private lateinit var player: MediaPlayer
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private var openFd: ParcelFileDescriptor? = null
    private var pendingSeek: Long = -1
    private var originalOrder: List<Track> = emptyList()
    private var sleepJob: Job? = null
    private var loaded = false
    private lateinit var audioManager: AudioManager
    private var focusRequest: AudioFocusRequest? = null
    private var resumeOnFocusGain = false

    fun init(context: Context) {
        app = context.applicationContext
        audioManager = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        libVLC = LibVLC(
            app,
            arrayListOf(
                "--no-video",
                "--no-sub-autodetect-file",
                "--audio-resampler=soxr",
                "--no-audio-time-stretch",
            )
        )
        player = MediaPlayer(libVLC)
        player.setEventListener { e ->
            when (e.type) {
                MediaPlayer.Event.Playing -> {
                    if (pendingSeek > 0) { player.setTime(pendingSeek); pendingSeek = -1 }
                    update { it.copy(isPlaying = true) }
                }
                MediaPlayer.Event.Paused, MediaPlayer.Event.Stopped -> update { it.copy(isPlaying = false) }
                MediaPlayer.Event.TimeChanged -> update { it.copy(positionMs = e.timeChanged) }
                MediaPlayer.Event.LengthChanged -> update { it.copy(durationMs = e.lengthChanged) }
                MediaPlayer.Event.EndReached -> main.post { onTrackEnded() }
                MediaPlayer.Event.EncounteredError -> main.post { skipBrokenTrack() }
            }
        }
        // Save the resume point every few seconds while playing.
        scope.launch {
            while (isActive) {
                delay(5000)
                if (_state.value.isPlaying) saveResume()
            }
        }
    }

    private inline fun update(crossinline f: (PlayerState) -> PlayerState) {
        main.post { _state.value = f(_state.value) }
    }

    // ---- Public controls ---------------------------------------------------------------------

    /** Play [tracks] starting at [start]. Autoplay carries on through the rest of the list. */
    fun play(tracks: List<Track>, start: Int, source: String, sourceMode: GroupMode? = null, shuffle: Boolean = false) {
        if (tracks.isEmpty()) return
        originalOrder = tracks
        var queue = tracks
        var index = start.coerceIn(0, tracks.lastIndex)
        if (shuffle) {
            val first = tracks[index]
            queue = listOf(first) + (tracks - first).shuffled()
            index = 0
        }
        _state.value = _state.value.copy(
            queue = queue, index = index, shuffle = shuffle, source = source, sourceMode = sourceMode,
            positionMs = 0, durationMs = queue[index].durationMs
        )
        load(queue[index], startAt = 0, autoStart = true)
    }

    fun toggle() {
        if (_state.value.current == null) return
        if (player.isPlaying) pause() else resume()
    }

    fun pause() {
        player.pause()
        saveResume()
    }

    fun resume() {
        val cur = _state.value.current ?: return
        if (!loaded) { load(cur, startAt = _state.value.positionMs, autoStart = true); return }
        if (!requestFocus()) return
        player.play()
        startService()
    }

    fun next() {
        val s = _state.value
        if (s.index + 1 < s.queue.size) jumpTo(s.index + 1)
        else if (s.repeat == RepeatMode.ALL && s.queue.isNotEmpty()) jumpTo(0)
        else if (!extendQueue()) return
        else jumpTo(_state.value.index + 1)
    }

    fun previous() {
        val s = _state.value
        if (s.positionMs > 3000 || s.index == 0) seekTo(0) else jumpTo(s.index - 1)
    }

    fun jumpTo(i: Int) {
        val s = _state.value
        val t = s.queue.getOrNull(i) ?: return
        _state.value = s.copy(index = i, positionMs = 0, durationMs = t.durationMs)
        load(t, startAt = 0, autoStart = true)
    }

    fun seekTo(ms: Long) {
        if (!loaded) { _state.value = _state.value.copy(positionMs = ms); return }
        player.setTime(ms)
        _state.value = _state.value.copy(positionMs = ms)
    }

    fun setShuffle(on: Boolean) {
        val s = _state.value
        val cur = s.current ?: return
        val queue = if (on) listOf(cur) + (s.queue - cur).shuffled() else originalOrder.ifEmpty { s.queue }
        _state.value = s.copy(queue = queue, index = queue.indexOf(cur).coerceAtLeast(0), shuffle = on)
    }

    fun cycleRepeat() {
        val r = when (_state.value.repeat) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        _state.value = _state.value.copy(repeat = r)
    }

    fun playNext(t: Track) {
        val s = _state.value
        if (s.current == null) { play(listOf(t), 0, "Queue"); return }
        val q = s.queue.toMutableList()
        q.remove(t)
        val at = (q.indexOf(s.current) + 1).coerceAtMost(q.size)
        q.add(at, t)
        _state.value = s.copy(queue = q, index = q.indexOf(s.current))
    }

    fun setSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        if (minutes <= 0) { _state.value = _state.value.copy(sleepEndsAt = 0); return }
        val end = System.currentTimeMillis() + minutes * 60_000L
        _state.value = _state.value.copy(sleepEndsAt = end)
        sleepJob = scope.launch {
            delay(minutes * 60_000L)
            pause()
            _state.value = _state.value.copy(sleepEndsAt = 0)
        }
    }

    /** Load the last song and position, paused, so tapping play carries on where you left off. */
    fun restoreResume(all: List<Track>) {
        if (_state.value.current != null || !Prefs.settings.value.resume) return
        val id = Prefs.resumeTrack()
        if (id < 0) return
        val byId = all.associateBy { it.id }
        val cur = byId[id] ?: return
        val queue = Prefs.resumeQueue().mapNotNull { byId[it] }.ifEmpty { listOf(cur) }
        val idx = queue.indexOf(cur).coerceAtLeast(0)
        originalOrder = queue
        _state.value = PlayerState(
            queue = queue, index = idx, positionMs = Prefs.resumePosition(), durationMs = cur.durationMs,
            source = Prefs.resumeSource(),
        )
    }

    // ---- Internals ---------------------------------------------------------------------------

    private fun load(t: Track, startAt: Long, autoStart: Boolean) {
        try {
            val fd = app.contentResolver.openFileDescriptor(t.uri, "r") ?: return skipBrokenTrack()
            val media = Media(libVLC, fd.fileDescriptor)
            media.addOption(":no-video")
            player.media = media
            media.release()
            openFd?.close()
            openFd = fd
            loaded = true
            pendingSeek = startAt
            Prefs.addPlay(t.id)
            if (autoStart && requestFocus()) {
                player.play()
                startService()
            }
        } catch (e: Exception) {
            skipBrokenTrack()
        }
    }

    private fun onTrackEnded() {
        val s = _state.value
        val settings = Prefs.settings.value
        when {
            s.repeat == RepeatMode.ONE -> jumpTo(s.index)
            !settings.autoplay -> { _state.value = s.copy(isPlaying = false, positionMs = 0); loaded = false }
            s.index + 1 < s.queue.size -> jumpTo(s.index + 1)
            s.repeat == RepeatMode.ALL -> jumpTo(0)
            settings.keepGoing && extendQueue() -> jumpTo(_state.value.index + 1)
            else -> { _state.value = s.copy(isPlaying = false, positionMs = 0); loaded = false; saveResume() }
        }
    }

    /** When the list ends, add more songs by the same music director (or singer/album). */
    private fun extendQueue(): Boolean {
        val s = _state.value
        val cur = s.current ?: return false
        val mode = s.sourceMode ?: Prefs.settings.value.groupMode
        val keys = mode.keysOf(cur).toSet()
        val inQueue = s.queue.map { it.id }.toSet()
        val all = Library.state.value.tracks
        var more = all.filter { t -> t.id !in inQueue && mode.keysOf(t).any { it in keys } }
        if (more.isEmpty()) more = all.filter { it.id !in inQueue }
        if (more.isEmpty()) return false
        val add = more.shuffled().take(25)
        _state.value = s.copy(queue = s.queue + add)
        return true
    }

    private fun skipBrokenTrack() {
        val s = _state.value
        if (s.index + 1 < s.queue.size) jumpTo(s.index + 1)
        else _state.value = s.copy(isPlaying = false)
    }

    private fun saveResume() {
        val s = _state.value
        val cur = s.current ?: return
        Prefs.saveResume(cur.id, s.positionMs, s.queue.map { it.id }, s.source)
    }

    private fun startService() {
        try {
            ContextCompat.startForegroundService(app, Intent(app, PlaybackService::class.java))
        } catch (_: Exception) {
            // Android can refuse to start a foreground service from the background; the running
            // service picks up the new state on its own.
        }
    }

    // ---- Audio focus (pause for calls, other apps, etc.) -------------------------------------

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> { resumeOnFocusGain = false; pause() }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                if (player.isPlaying) { resumeOnFocusGain = true; player.pause() }
            }
            AudioManager.AUDIOFOCUS_GAIN -> if (resumeOnFocusGain) { resumeOnFocusGain = false; player.play() }
        }
    }

    private fun requestFocus(): Boolean {
        val req = focusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setOnAudioFocusChangeListener(focusListener)
            .build().also { focusRequest = it }
        return audioManager.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    /** Video playback pauses the music. */
    fun pauseForVideo() { if (player.isPlaying) pause() }
}
