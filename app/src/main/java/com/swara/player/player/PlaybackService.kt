package com.swara.player.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.media.app.NotificationCompat.MediaStyle
import com.swara.player.MainActivity
import com.swara.player.R
import com.swara.player.ui.ArtLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Keeps music playing in the background, shows the notification and handles Buds/headset buttons. */
class PlaybackService : Service() {
    private lateinit var session: MediaSessionCompat
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var art: Bitmap? = null
    private var artFor: Long = -1
    private var inForeground = false

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // Buds taken out / disconnected, wired earphones unplugged.
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) PlayerController.pause()
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        session = MediaSessionCompat(this, "Swara").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() = PlayerController.resume()
                override fun onPause() = PlayerController.pause()
                override fun onSkipToNext() = PlayerController.next()
                override fun onSkipToPrevious() = PlayerController.previous()
                override fun onSeekTo(pos: Long) = PlayerController.seekTo(pos)
                override fun onStop() = PlayerController.pause()
            })
            setSessionActivity(openAppIntent())
            isActive = true
        }
        ContextCompat.registerReceiver(
            this, noisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        // Show the notification straight away to satisfy the foreground-service rules.
        goForeground(buildNotification(PlayerController.state.value))

        scope.launch {
            PlayerController.state
                .map { Triple(it.current?.id, it.isPlaying, it.positionMs / 1000) }
                .distinctUntilChanged()
                .collect { refresh() }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> PlayerController.toggle()
            ACTION_NEXT -> PlayerController.next()
            ACTION_PREV -> PlayerController.previous()
            Intent.ACTION_MEDIA_BUTTON -> androidx.media.session.MediaButtonReceiver.handleIntent(session, intent)
            else -> goForeground(buildNotification(PlayerController.state.value))
        }
        return START_NOT_STICKY
    }

    private suspend fun refresh() {
        val s = PlayerController.state.value
        val t = s.current
        if (t != null && t.id != artFor) {
            artFor = t.id
            art = withContext(Dispatchers.IO) { ArtLoader.loadAlbumArt(this@PlaybackService, t, 512) }
        }
        session.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, t?.title ?: "")
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, t?.let { it.artist.ifBlank { it.director } } ?: "")
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, t?.album ?: "")
                .putString(MediaMetadataCompat.METADATA_KEY_COMPOSER, t?.director ?: "")
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, s.durationMs)
                .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, art)
                .build()
        )
        session.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE or PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or PlaybackStateCompat.ACTION_SEEK_TO or
                        PlaybackStateCompat.ACTION_STOP
                )
                .setState(
                    if (s.isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                    s.positionMs, if (s.isPlaying) 1f else 0f
                )
                .build()
        )
        val n = buildNotification(s)
        if (s.isPlaying) {
            goForeground(n)
        } else {
            if (inForeground) {
                stopForeground(STOP_FOREGROUND_DETACH)
                inForeground = false
            }
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, n)
        }
    }

    private fun goForeground(n: Notification) {
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, n)
        }
        inForeground = true
    }

    private fun buildNotification(s: PlayerState): Notification {
        val t = s.current
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(t?.title ?: "Swara")
            .setContentText(t?.let { it.artist.ifBlank { it.director } } ?: "")
            .setSubText(s.source.ifBlank { null })
            .setLargeIcon(art)
            .setContentIntent(openAppIntent())
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setOngoing(s.isPlaying)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(android.R.drawable.ic_media_previous, "Previous", action(ACTION_PREV))
            .addAction(
                if (s.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (s.isPlaying) "Pause" else "Play", action(ACTION_TOGGLE)
            )
            .addAction(android.R.drawable.ic_media_next, "Next", action(ACTION_NEXT))
            .setStyle(MediaStyle().setMediaSession(session.sessionToken).setShowActionsInCompactView(0, 1, 2))
            .build()
    }

    private fun action(a: String): PendingIntent = PendingIntent.getService(
        this, a.hashCode(), Intent(this, PlaybackService::class.java).setAction(a),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Now playing", NotificationManager.IMPORTANCE_LOW).apply {
                    setShowBadge(false)
                }
            )
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!PlayerController.state.value.isPlaying) stopSelf()
    }

    override fun onDestroy() {
        unregisterReceiver(noisyReceiver)
        session.release()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL = "playback"
        private const val NOTIFICATION_ID = 7
        const val ACTION_TOGGLE = "com.swara.player.TOGGLE"
        const val ACTION_NEXT = "com.swara.player.NEXT"
        const val ACTION_PREV = "com.swara.player.PREV"
    }
}
