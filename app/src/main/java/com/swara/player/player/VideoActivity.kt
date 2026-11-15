package com.swara.player.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.swara.player.data.Prefs
import com.swara.player.data.VideoItem
import com.swara.player.data.formatDuration
import kotlinx.coroutines.delay
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

/** Full-screen video player using the VLC engine. Remembers where you stopped. */
class VideoActivity : ComponentActivity() {
    private lateinit var libVLC: LibVLC
    private lateinit var player: MediaPlayer
    private var fd: ParcelFileDescriptor? = null
    private var videoId: Long = -1

    private val playing = mutableStateOf(true)
    private val position = mutableLongStateOf(0L)
    private val length = mutableLongStateOf(0L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        PlayerController.pauseForVideo()

        val uri: Uri = intent.data ?: return finish()
        videoId = intent.getLongExtra(EXTRA_ID, -1)
        val title = intent.getStringExtra(EXTRA_TITLE) ?: ""

        libVLC = LibVLC(this, arrayListOf("--no-sub-autodetect-file"))
        player = MediaPlayer(libVLC)
        player.setEventListener { e ->
            when (e.type) {
                MediaPlayer.Event.Playing -> playing.value = true
                MediaPlayer.Event.Paused, MediaPlayer.Event.Stopped -> playing.value = false
                MediaPlayer.Event.TimeChanged -> position.longValue = e.timeChanged
                MediaPlayer.Event.LengthChanged -> length.longValue = e.lengthChanged
                MediaPlayer.Event.EndReached -> { Prefs.saveVideoPosition(videoId, 0, 1); finish() }
            }
        }

        setContent {
            var controls by remember { mutableStateOf(true) }
            var dragging by remember { mutableStateOf(false) }
            var drag by remember { mutableStateOf(0f) }
            LaunchedEffect(controls, playing.value) {
                if (controls && playing.value) { delay(3500); controls = false }
            }
            Box(
                Modifier.fillMaxSize().background(Color.Black).clickable(
                    interactionSource = remember { MutableInteractionSource() }, indication = null
                ) { controls = !controls }
            ) {
                AndroidView(
                    factory = { ctx ->
                        VLCVideoLayout(ctx).also { layout ->
                            player.attachViews(layout, null, true, false)
                            start(uri)
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                if (controls) {
                    Row(
                        Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.5f)).padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { finish() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Close video", tint = Color.White) }
                        Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(36.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { player.setTime((player.time - 10_000).coerceAtLeast(0)) }, modifier = Modifier.size(56.dp)) {
                            Icon(Icons.Rounded.Replay10, "Back 10 seconds", tint = Color.White, modifier = Modifier.size(40.dp))
                        }
                        IconButton(onClick = { if (player.isPlaying) player.pause() else player.play() }, modifier = Modifier.size(76.dp)) {
                            Icon(
                                if (playing.value) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                if (playing.value) "Pause" else "Play", tint = Color.White, modifier = Modifier.size(60.dp)
                            )
                        }
                        IconButton(onClick = { player.setTime(player.time + 10_000) }, modifier = Modifier.size(56.dp)) {
                            Icon(Icons.Rounded.Forward10, "Forward 10 seconds", tint = Color.White, modifier = Modifier.size(40.dp))
                        }
                    }
                    Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha = 0.5f)).padding(horizontal = 20.dp, vertical = 8.dp)) {
                        val len = length.longValue.coerceAtLeast(1)
                        val frac = if (dragging) drag else (position.longValue.toFloat() / len).coerceIn(0f, 1f)
                        Slider(
                            value = frac,
                            onValueChange = { dragging = true; drag = it },
                            onValueChangeFinished = { player.setTime((drag * len).toLong()); dragging = false },
                            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color.White.copy(alpha = 0.3f)),
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(formatDuration((frac * len).toLong()), color = Color.White, fontSize = 12.sp)
                            Text(formatDuration(length.longValue), color = Color.White, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }

    private fun start(uri: Uri) {
        try {
            val f = contentResolver.openFileDescriptor(uri, "r") ?: return finish()
            fd = f
            val media = Media(libVLC, f.fileDescriptor)
            player.media = media
            media.release()
            player.play()
            val resume = Prefs.videoPosition(videoId)
            if (resume > 0) player.setTime(resume)
        } catch (e: Exception) {
            finish()
        }
    }

    override fun onPause() {
        super.onPause()
        if (::player.isInitialized) {
            Prefs.saveVideoPosition(videoId, player.time, player.length)
            player.pause()
        }
    }

    override fun onDestroy() {
        if (::player.isInitialized) {
            player.stop()
            player.detachViews()
            player.release()
            libVLC.release()
        }
        fd?.close()
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_ID = "id"
        private const val EXTRA_TITLE = "title"
        fun start(context: Context, v: VideoItem) {
            context.startActivity(
                Intent(context, VideoActivity::class.java).setData(v.uri)
                    .putExtra(EXTRA_ID, v.id).putExtra(EXTRA_TITLE, v.title)
            )
        }
    }
}
