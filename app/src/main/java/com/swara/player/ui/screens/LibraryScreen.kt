package com.swara.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swara.player.data.Group
import com.swara.player.data.GroupMode
import com.swara.player.data.Library
import com.swara.player.data.Prefs
import com.swara.player.player.PlayerController
import com.swara.player.ui.ArtTile
import com.swara.player.ui.BottomInset
import com.swara.player.ui.C
import com.swara.player.ui.ChipRow
import com.swara.player.ui.EmptyNote
import com.swara.player.ui.Lists
import com.swara.player.ui.Nav
import com.swara.player.ui.ScreenTitle
import com.swara.player.ui.SongRow

private const val SONGS = "Songs"

@Composable
fun LibraryScreen(nav: Nav) {
    val lib by Library.state.collectAsState()
    val formats by Library.formats.collectAsState()
    val settings by Prefs.settings.collectAsState()
    val player by PlayerController.state.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var chip by rememberSaveable { mutableStateOf(settings.groupMode.plural) }
    val modes = listOf(settings.groupMode) + (GroupMode.entries - settings.groupMode)

    val q = query.trim().lowercase()
    val songs = remember(lib.tracks, q) {
        if (q.isEmpty()) lib.tracks
        else lib.tracks.filter {
            it.title.lowercase().contains(q) || it.artist.lowercase().contains(q) ||
                it.album.lowercase().contains(q) || it.director.lowercase().contains(q)
        }
    }
    val mode = modes.firstOrNull { it.plural == chip }
    val groups: List<Group> = remember(songs, mode) { if (mode == null) emptyList() else Library.groups(songs, mode) }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(bottom = BottomInset),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                ScreenTitle("Library", Modifier.weight(1f))
                IconButton(onClick = { Library.rescan() }) { Icon(Icons.Rounded.Refresh, "Scan again", tint = C.White) }
            }
        }
        item {
            TextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                placeholder = { Text("Songs, directors, singers, albums", color = C.Grey) },
                leadingIcon = { Icon(Icons.Rounded.Search, null, tint = C.Grey) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Clear search", tint = C.Grey) }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = C.Surface, unfocusedContainerColor = C.Surface,
                    focusedIndicatorColor = C.Surface, unfocusedIndicatorColor = C.Surface,
                    focusedTextColor = C.White, unfocusedTextColor = C.White, cursorColor = C.White,
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
        item { ChipRow(modes.map { it.plural } + SONGS, chip) { chip = it } }
        item {
            Row(
                Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.Surface)
                    .clickable { nav.filters() }.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (lib.scanning) "Scanning your phone…" else "${lib.tracks.size} songs found on your phone",
                        color = C.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "${lib.hidden.size} call recordings, voice notes and ringtones hidden",
                        color = C.Grey, fontSize = 12.sp
                    )
                }
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = C.Grey)
            }
        }

        if (chip == SONGS) {
            if (songs.isEmpty()) item { EmptyNote(if (q.isEmpty()) "No songs yet." else "Nothing matches “$query”.") }
            itemsIndexed(songs, key = { _, t -> t.id }) { i, t ->
                SongRow(
                    t, formats[t.id], player.current?.id == t.id,
                    listOf(t.artist, t.album).filter { it.isNotBlank() }.joinToString(" · ")
                ) {
                    PlayerController.play(songs, i, if (q.isEmpty()) Lists.ALL else "Search results")
                    nav.openPlayer()
                }
            }
        } else if (mode != null) {
            if (groups.isEmpty()) item { EmptyNote("Nothing here yet.") }
            items(groups, key = { it.name }) { g ->
                GroupRow(g) { nav.group(g.mode, g.name) }
            }
        }
    }
}

@Composable
fun GroupRow(g: Group, onClick: () -> Unit) {
    val album = g.mode == GroupMode.ALBUM
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ArtTile(
            g.name, if (album) g.tracks.firstOrNull() else null, 56.dp,
            if (album) RoundedCornerShape(6.dp) else CircleShape, fontSize = 18.sp
        )
        Column(Modifier.weight(1f)) {
            Text(g.name, color = C.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = if (g.name.startsWith("Unknown")) "${g.tracks.size} songs · no director in tags"
            else if (album) "${g.tracks.size} songs · ${g.tracks.first().director}"
            else "${g.tracks.size} songs · ${g.albumCount} albums"
            Text(sub, color = C.Grey, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = C.Off)
    }
}
