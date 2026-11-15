package com.swara.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swara.player.data.GroupMode
import com.swara.player.data.Library
import com.swara.player.data.Prefs
import com.swara.player.data.formatDuration
import com.swara.player.player.PlayerController
import com.swara.player.player.RepeatMode
import com.swara.player.ui.ArtTile
import com.swara.player.ui.C

@Composable
fun NowPlayingScreen(onClose: () -> Unit, onOpenGroup: (GroupMode, String) -> Unit) {
    val s by PlayerController.state.collectAsState()
    val formats by Library.formats.collectAsState()
    val favs by Prefs.favourites.collectAsState()
    val settings by Prefs.settings.collectAsState()
    var showQueue by remember { mutableStateOf(false) }
    var sleepMenu by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val t = s.current

    Column(
        Modifier.fillMaxSize().background(C.Surface).clickable(enabled = false) {}
            .statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose, modifier = Modifier.padding(start = 0.dp)) {
                Icon(Icons.Rounded.KeyboardArrowDown, "Close player", tint = C.White, modifier = Modifier.size(30.dp))
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "PLAYING FROM ${(s.sourceMode?.label ?: "list").uppercase()}", color = C.Light,
                    fontSize = 11.sp, letterSpacing = 0.8.sp
                )
                Text(
                    s.source.ifBlank { "Swara" }, color = C.White, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable(enabled = s.sourceMode != null) {
                        s.sourceMode?.let { onOpenGroup(it, s.source) }
                    }
                )
            }
            Box {
                IconButton(onClick = { sleepMenu = true }) {
                    Icon(
                        Icons.Rounded.Bedtime, "Sleep timer",
                        tint = if (s.sleepEndsAt > 0) C.White else C.Grey
                    )
                }
                DropdownMenu(expanded = sleepMenu, onDismissRequest = { sleepMenu = false }) {
                    listOf(0 to "Off", 15 to "15 minutes", 30 to "30 minutes", 45 to "45 minutes", 60 to "1 hour", 90 to "1.5 hours")
                        .forEach { (m, label) ->
                            DropdownMenuItem(text = { Text(if (m == 0) "Sleep timer off" else "Stop after $label") }, onClick = {
                                PlayerController.setSleepTimer(m); sleepMenu = false
                            })
                        }
                }
            }
        }

        if (t == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Pick a song to start listening.", color = C.Grey)
            }
            return@Column
        }

        if (showQueue) {
            QueueList(onPick = { PlayerController.jumpTo(it) })
        } else {
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                val side = if (maxWidth < maxHeight) maxWidth else maxHeight
                ArtTile(
                    t.album.ifBlank { t.title }, t, side, RoundedCornerShape(12.dp), fontSize = 96.sp,
                    modifier = Modifier.shadow(24.dp, RoundedCornerShape(12.dp))
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(t.title, color = C.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOf(t.director.takeIf { !it.startsWith("Unknown") }, t.artist.takeIf { it.isNotBlank() && it != t.director })
                        .filterNotNull().joinToString(" · ").ifEmpty { "Unknown artist" },
                    color = C.Light, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            val fav = t.id in favs
            IconButton(onClick = { Prefs.toggleFavourite(t.id) }) {
                Icon(
                    if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    if (fav) "Remove from favourites" else "Add to favourites", tint = C.White
                )
            }
        }

        val dur = s.durationMs.coerceAtLeast(1)
        val pos = if (dragging) dragValue else (s.positionMs.toFloat() / dur).coerceIn(0f, 1f)
        Slider(
            value = pos,
            onValueChange = { dragging = true; dragValue = it },
            onValueChangeFinished = { PlayerController.seekTo((dragValue * dur).toLong()); dragging = false },
            colors = SliderDefaults.colors(thumbColor = C.White, activeTrackColor = C.White, inactiveTrackColor = C.Off),
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(formatDuration((pos * dur).toLong()), color = C.Light, fontSize = 12.sp, modifier = Modifier.width(52.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                val f = formats[t.id]
                if (settings.showFormat && f != null) {
                    Row(
                        Modifier.clip(RoundedCornerShape(11.dp)).background(C.Raised).padding(start = 4.dp, end = 10.dp, top = 3.dp, bottom = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (f.isHiRes) {
                            Text(
                                "HI-RES", color = C.Black, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(C.White).padding(horizontal = 6.dp, vertical = 1.dp)
                            )
                        } else Spacer(Modifier.width(2.dp))
                        Text(f.long, color = C.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Text(
                formatDuration(s.durationMs), color = C.Light, fontSize = 12.sp,
                modifier = Modifier.width(52.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End
            )
        }

        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { PlayerController.setShuffle(!s.shuffle) }) {
                Icon(Icons.Rounded.Shuffle, if (s.shuffle) "Shuffle on" else "Shuffle off", tint = if (s.shuffle) C.White else C.Grey)
            }
            IconButton(onClick = { PlayerController.previous() }, modifier = Modifier.size(60.dp)) {
                Icon(Icons.Rounded.SkipPrevious, "Previous", tint = C.White, modifier = Modifier.size(44.dp))
            }
            IconButton(onClick = { PlayerController.toggle() }, modifier = Modifier.size(76.dp)) {
                Icon(
                    if (s.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    if (s.isPlaying) "Pause" else "Play", tint = C.White, modifier = Modifier.size(64.dp)
                )
            }
            IconButton(onClick = { PlayerController.next() }, modifier = Modifier.size(60.dp)) {
                Icon(Icons.Rounded.SkipNext, "Next", tint = C.White, modifier = Modifier.size(44.dp))
            }
            IconButton(onClick = { PlayerController.cycleRepeat() }) {
                Icon(
                    if (s.repeat == RepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                    when (s.repeat) { RepeatMode.OFF -> "Repeat off"; RepeatMode.ALL -> "Repeat all"; RepeatMode.ONE -> "Repeat one" },
                    tint = if (s.repeat == RepeatMode.OFF) C.Grey else C.White
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.Raised).clickable { showQueue = !showQueue }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val nxt = s.next
            if (nxt != null) ArtTile(nxt.album.ifBlank { nxt.title }, nxt, 36.dp, RoundedCornerShape(4.dp), fontSize = 13.sp)
            else Icon(Icons.AutoMirrored.Rounded.QueueMusic, null, tint = C.Grey)
            Column(Modifier.weight(1f)) {
                Text(
                    if (showQueue) "Hide queue" else "Up next: ${nxt?.title ?: if (settings.keepGoing) "more by ${t.director}" else "nothing"}",
                    color = C.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (settings.autoplay) "Plays automatically when this song ends" else "Playback stops after this song",
                    color = C.Light, fontSize = 12.sp
                )
            }
            Text(
                if (settings.autoplay) "Autoplay on" else "Autoplay off",
                color = if (settings.autoplay) C.Black else C.Light, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(16.dp))
                    .background(if (settings.autoplay) C.White else C.Off)
                    .clickable { Prefs.update { it.copy(autoplay = !it.autoplay) } }
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.QueueList(onPick: (Int) -> Unit) {
    val s by PlayerController.state.collectAsState()
    LazyColumn(Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(vertical = 8.dp)) {
        itemsIndexed(s.queue, key = { i, t -> "${t.id}_$i" }) { i, t ->
            val cur = i == s.index
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onPick(i) }
                    .background(if (cur) C.Raised else C.Surface).padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ArtTile(t.album.ifBlank { t.title }, t, 40.dp, RoundedCornerShape(4.dp), fontSize = 14.sp)
                Column(Modifier.weight(1f)) {
                    Text(t.title, color = C.White, fontSize = 14.sp, fontWeight = if (cur) FontWeight.ExtraBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(t.director, color = C.Grey, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (cur) Box(Modifier.size(8.dp).clip(CircleShape).background(C.White))
            }
        }
    }
}
