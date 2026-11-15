package com.swara.player.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swara.player.data.Library
import com.swara.player.data.Prefs
import com.swara.player.data.VideoItem
import com.swara.player.data.formatDuration
import com.swara.player.player.VideoActivity
import com.swara.player.ui.ArtLoader
import com.swara.player.ui.BottomInset
import com.swara.player.ui.C
import com.swara.player.ui.EmptyNote
import com.swara.player.ui.ScreenTitle
import com.swara.player.ui.rememberBitmap

@Composable
fun VideosScreen() {
    val lib by Library.state.collectAsState()
    val context = LocalContext.current
    val watching = remember(lib.videos) {
        lib.videos.filter { Prefs.videoPosition(it.id) > 0 }.sortedByDescending { Prefs.videoWatchedAt(it.id) }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = BottomInset),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(span = { GridItemSpan(2) }) { ScreenTitle("Videos", Modifier.padding(top = 12.dp)) }
        if (watching.isNotEmpty()) {
            item(span = { GridItemSpan(2) }) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.padding(start = 0.dp)) { SectionTitleNoPad("Continue watching") }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(watching, key = { it.id }) { v -> VideoCard(v, 240.dp) { VideoActivity.start(context, v) } }
                    }
                }
            }
        }
        item(span = { GridItemSpan(2) }) { SectionTitleNoPad("All videos") }
        if (lib.videos.isEmpty()) item(span = { GridItemSpan(2) }) { EmptyNote(if (lib.scanning) "Looking for videos…" else "No videos found.") }
        items(lib.videos, key = { it.id }) { v -> VideoCard(v, null) { VideoActivity.start(context, v) } }
    }
}

@Composable
private fun SectionTitleNoPad(text: String) {
    Text(text, color = C.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
}

@Composable
fun VideoCard(v: VideoItem, width: Dp?, onClick: () -> Unit) {
    val context = LocalContext.current
    val thumb = rememberBitmap("v${v.id}") { ArtLoader.load(context, v.uri, "v${v.id}", 480) }
    val saved = remember(v.id) { Prefs.videoPosition(v.id) }
    Column(
        (if (width != null) Modifier.width(width) else Modifier.fillMaxWidth())
            .clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)).background(C.Raised), contentAlignment = Alignment.Center) {
            if (thumb != null) Image(thumb, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else Icon(Icons.Rounded.PlayArrow, null, tint = C.Grey)
            Text(
                formatDuration(v.durationMs), color = C.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).clip(RoundedCornerShape(4.dp))
                    .background(C.Black.copy(alpha = 0.75f)).padding(horizontal = 6.dp, vertical = 2.dp)
            )
            if (saved > 0 && v.durationMs > 0) {
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(C.White.copy(alpha = 0.25f)))
                Box(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth((saved.toFloat() / v.durationMs).coerceIn(0f, 1f))
                        .height(4.dp).background(C.White)
                )
            }
        }
        Text(v.title, color = C.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val meta = listOf(v.resolutionLabel, sizeLabel(v.sizeBytes)).filter { it.isNotEmpty() }.joinToString(" · ")
        if (meta.isNotEmpty()) Text(meta, color = C.Grey, fontSize = 12.sp)
    }
}

private fun sizeLabel(b: Long): String = when {
    b >= 1_000_000_000 -> String.format("%.1f GB", b / 1e9)
    b >= 1_000_000 -> "${b / 1_000_000} MB"
    b > 0 -> "${b / 1000} KB"
    else -> ""
}
