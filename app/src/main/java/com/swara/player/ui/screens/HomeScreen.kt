package com.swara.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swara.player.data.GroupMode
import com.swara.player.data.Library
import com.swara.player.data.Prefs
import com.swara.player.data.Track
import com.swara.player.player.PlayerController
import com.swara.player.player.VideoActivity
import com.swara.player.ui.ArtTile
import com.swara.player.ui.BottomInset
import com.swara.player.ui.C
import com.swara.player.ui.ChipRow
import com.swara.player.ui.EmptyNote
import com.swara.player.ui.Lists
import com.swara.player.ui.Nav
import com.swara.player.ui.ScreenTitle
import com.swara.player.ui.SectionTitle
import com.swara.player.ui.SwaraLogo
import com.swara.player.ui.Tab
import java.util.Calendar

@Composable
fun HomeScreen(nav: Nav) {
    val lib by Library.state.collectAsState()
    val settings by Prefs.settings.collectAsState()
    val favs by Prefs.favourites.collectAsState()
    var chip by rememberSaveable { mutableStateOf("All") }
    val context = LocalContext.current

    val mode = settings.groupMode
    val groups = remember(lib.tracks, mode) {
        Library.groups(lib.tracks, mode).filter { !it.name.startsWith("Unknown") }.sortedByDescending { it.tracks.size }
    }
    val recent = remember(lib.tracks) { lib.tracks.sortedByDescending { it.dateAdded }.take(12) }
    val watching = remember(lib.videos) {
        lib.videos.filter { Prefs.videoPosition(it.id) > 0 }.sortedByDescending { Prefs.videoWatchedAt(it.id) }.take(8)
    }
    val greeting = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(bottom = BottomInset),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SwaraLogo(34.dp)
                Spacer(Modifier.width(10.dp))
                ScreenTitle(greeting, Modifier.weight(1f))
                IconButton(onClick = { nav.tab(Tab.LIBRARY) }) { Icon(Icons.Rounded.Search, "Search", tint = C.White) }
                IconButton(onClick = { nav.tab(Tab.SETTINGS) }) { Icon(Icons.Rounded.Settings, "Settings", tint = C.White) }
            }
        }
        item { ChipRow(listOf("All", "Songs", mode.plural, "Videos"), chip) { chip = it } }

        if (lib.scanning && lib.tracks.isEmpty()) {
            item { EmptyNote("Finding music on your phone…") }
        } else if (lib.tracks.isEmpty() && chip != "Videos") {
            item { EmptyNote("No songs found yet. Copy music to your phone or SD card and it appears here automatically.") }
        }

        if (chip == "All" || chip == "Songs") {
            item {
                val quick = buildList {
                    add(Quick(Lists.FAVOURITES, null) { nav.list(Lists.FAVOURITES) })
                    add(Quick(Lists.RECENT, recent.firstOrNull()) { nav.list(Lists.RECENT) })
                    add(Quick(Lists.MOST, null) { nav.list(Lists.MOST) })
                    groups.take(3).forEach { g -> add(Quick(g.name, null) { nav.group(mode, g.name) }) }
                }
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    quick.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { q ->
                                Row(
                                    Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(6.dp)).background(C.Surface)
                                        .clickable(onClick = q.onClick),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    ArtTile(q.title, q.art, 56.dp, RoundedCornerShape(0.dp), fontSize = 18.sp)
                                    Text(
                                        q.title, color = C.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                        maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 10.dp)
                                    )
                                }
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        if ((chip == "All" || chip == mode.plural) && groups.isNotEmpty()) {
            item { SectionTitle(mode.plural, "Show all") { nav.tab(Tab.LIBRARY) } }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(groups.take(15), key = { it.name }) { g ->
                        Column(
                            Modifier.width(84.dp).clip(RoundedCornerShape(8.dp)).clickable { nav.group(mode, g.name) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ArtTile(g.name, if (mode == GroupMode.ALBUM) g.tracks.firstOrNull() else null, 80.dp, CircleShape)
                            Text(
                                g.name, color = C.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        if ((chip == "All" || chip == "Songs") && recent.isNotEmpty()) {
            item { SectionTitle("Recently added", "Show all") { nav.list(Lists.RECENT) } }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    itemsIndexed(recent, key = { _, t -> t.id }) { i, t ->
                        AlbumCard(t) { PlayerController.play(recent, i, Lists.RECENT); nav.openPlayer() }
                    }
                }
            }
        }

        if ((chip == "All" || chip == "Videos")) {
            val vids = if (watching.isNotEmpty()) watching else lib.videos.take(8)
            if (vids.isNotEmpty()) {
                item { SectionTitle(if (watching.isNotEmpty()) "Continue watching" else "Recent videos", "Show all") { nav.tab(Tab.VIDEOS) } }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(vids, key = { it.id }) { v ->
                            VideoCard(v, width = 220.dp) { VideoActivity.start(context, v) }
                        }
                    }
                }
            } else if (chip == "Videos") {
                item { EmptyNote("No videos found.") }
            }
        }
    }
}

private class Quick(val title: String, val art: Track?, val onClick: () -> Unit)

@Composable
fun AlbumCard(t: Track, onClick: () -> Unit) {
    Column(
        Modifier.width(150.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ArtTile(t.album.ifBlank { t.title }, t, 150.dp, RoundedCornerShape(8.dp))
        Box {
            Column {
                Text(t.title, color = C.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(t.director, color = C.Grey, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.size(0.dp))
    }
}
