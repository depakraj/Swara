package com.swara.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swara.player.data.GroupMode
import com.swara.player.data.Library
import com.swara.player.data.Prefs
import com.swara.player.player.PlayerController
import com.swara.player.ui.ArtTile
import com.swara.player.ui.BottomInset
import com.swara.player.ui.C
import com.swara.player.ui.EmptyNote
import com.swara.player.ui.Nav
import com.swara.player.ui.SongRow
import com.swara.player.ui.tracksFor

@Composable
fun GroupScreen(modeName: String, name: String, nav: Nav) {
    val lib by Library.state.collectAsState()
    val formats by Library.formats.collectAsState()
    val settings by Prefs.settings.collectAsState()
    val favs by Prefs.favourites.collectAsState()
    val player by PlayerController.state.collectAsState()
    val tracks = remember(lib.tracks, modeName, name, favs) { tracksFor(modeName, name, lib.tracks) }
    val mode = runCatching { GroupMode.valueOf(modeName) }.getOrNull()
    val label = mode?.label ?: "Playlist"
    val albums = tracks.map { it.albumId }.distinct().size
    val hiRes = tracks.count { formats[it.id]?.isHiRes == true }

    LazyColumn(Modifier.fillMaxSize().background(C.Black), contentPadding = PaddingValues(bottom = BottomInset)) {
        item {
            Column(
                Modifier.fillMaxWidth().background(C.Surface).statusBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                IconButton(onClick = { nav.back() }, modifier = Modifier.padding(top = 4.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = C.White)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ArtTile(
                        name, if (mode == GroupMode.ALBUM) tracks.firstOrNull() else null, 112.dp,
                        if (mode == GroupMode.ALBUM || mode == null) RoundedCornerShape(10.dp) else CircleShape,
                        fontSize = 38.sp
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(label.uppercase(), color = C.Light, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                        Text(name, color = C.White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 31.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        Text(
                            buildString {
                                append("${tracks.size} songs")
                                if (albums > 1) append(" · $albums albums")
                                if (hiRes > 0) append(" · $hiRes hi-res")
                            },
                            color = C.Light, fontSize = 13.sp
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BigButton("Play all", Icons.Rounded.PlayArrow, Modifier.weight(1f)) {
                        if (tracks.isNotEmpty()) { PlayerController.play(tracks, 0, name, mode); nav.openPlayer() }
                    }
                    BigButton("Shuffle", Icons.Rounded.Shuffle, Modifier.weight(1f)) {
                        if (tracks.isNotEmpty()) {
                            PlayerController.play(tracks, tracks.indices.random(), name, mode, shuffle = true); nav.openPlayer()
                        }
                    }
                }
                Row(
                    Modifier.clip(RoundedCornerShape(8.dp)).clickable { Prefs.update { it.copy(autoplay = !it.autoplay) } }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Rounded.SkipNext, null, tint = C.Light, modifier = Modifier.size(16.dp))
                    val cur = player.current
                    val idx = tracks.indexOfFirst { it.id == cur?.id }
                    val nextTitle = if (idx >= 0) tracks.getOrNull(idx + 1)?.title else null
                    Text(
                        when {
                            !settings.autoplay -> "Autoplay off · stops after each song (tap to turn on)"
                            nextTitle != null -> "Autoplay on · next: $nextTitle"
                            else -> "Autoplay on · plays the next song automatically"
                        },
                        color = C.Light, fontSize = 12.sp
                    )
                }
            }
        }
        if (tracks.isEmpty()) item { EmptyNote(if (name == "Favourites") "Tap the heart on Now Playing to add favourites." else "No songs here.") }
        item { Spacer(Modifier.height(6.dp)) }
        itemsIndexed(tracks, key = { _, t -> t.id }) { i, t ->
            SongRow(
                t, formats[t.id], player.current?.id == t.id,
                if (mode == GroupMode.ALBUM) t.artist.ifBlank { t.director } else t.album.ifBlank { t.artist }
            ) {
                PlayerController.play(tracks, i, name, mode)
                nav.openPlayer()
            }
        }
    }
}

@Composable
private fun BigButton(text: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.height(46.dp).clip(RoundedCornerShape(10.dp)).background(C.Raised).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = C.White, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, color = C.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}
