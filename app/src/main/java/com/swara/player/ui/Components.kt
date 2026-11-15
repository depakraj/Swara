package com.swara.player.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swara.player.data.AudioFormat
import com.swara.player.data.Library
import com.swara.player.data.Prefs
import com.swara.player.data.Track
import com.swara.player.data.formatDuration
import com.swara.player.player.PlayerController

/** The Swara wave logo. */
@Composable
fun SwaraLogo(size: Dp, circle: Color = C.White, wave: Color = C.Black) {
    Canvas(Modifier.size(size)) {
        val s = this.size.width / 64f
        drawCircle(circle, radius = this.size.width / 2f, center = Offset(this.size.width / 2f, this.size.height / 2f))
        val p = Path().apply {
            moveTo(10 * s, 32 * s)
            cubicTo(16 * s, 15 * s, 25 * s, 15 * s, 32 * s, 32 * s)
            cubicTo(39 * s, 49 * s, 48 * s, 49 * s, 54 * s, 32 * s)
        }
        drawPath(p, wave, style = Stroke(width = 5 * s, cap = StrokeCap.Round))
    }
}

@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, color = C.White, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp)
}

@Composable
fun SectionTitle(text: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text, color = C.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        if (action != null) {
            Text(
                action, color = C.Grey, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAction).padding(8.dp)
            )
        }
    }
}

@Composable
fun SmallLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(), modifier = modifier, color = C.Grey, fontSize = 13.sp,
        fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp
    )
}

@Composable
fun ChipRow(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { o ->
            val on = o == selected
            Box(
                Modifier.height(36.dp).clip(RoundedCornerShape(18.dp))
                    .background(if (on) C.White else C.Raised)
                    .clickable(role = Role.Tab) { onSelect(o) }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(o, color = if (on) C.Black else C.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** e.g. "FLAC 24/96". Hi-res gets a white outline, CD quality a grey one. */
@Composable
fun FormatTag(f: AudioFormat) {
    val c = if (f.isHiRes) C.White else C.Grey
    Text(
        f.short, color = c, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1,
        modifier = Modifier.border(1.dp, if (f.isHiRes) C.White else C.Off, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@Composable
fun SongRow(t: Track, format: AudioFormat?, isCurrent: Boolean, subtitle: String, onClick: () -> Unit) {
    val showFormat by Prefs.settings.collectAsState()
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ArtTile(t.album.ifBlank { t.title }, t, 48.dp, RoundedCornerShape(6.dp))
        Column(Modifier.weight(1f)) {
            Text(
                t.title, color = if (isCurrent) C.White else C.White, fontSize = 15.sp,
                fontWeight = if (isCurrent) FontWeight.ExtraBold else FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(subtitle, color = C.Grey, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(formatDuration(t.durationMs), color = C.Grey, fontSize = 12.sp)
            if (showFormat.showFormat && format != null && format.codec.isNotEmpty()) FormatTag(format)
        }
    }
}

@Composable
fun SwitchRow(title: String, subtitle: String, checked: Boolean, trailing: String? = null, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(role = Role.Switch) { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = C.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = C.Grey, fontSize = 12.sp)
        }
        if (trailing != null) Text(trailing, color = C.Grey, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Switch(
            checked = checked, onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = C.Black, checkedTrackColor = C.White,
                uncheckedThumbColor = C.White, uncheckedTrackColor = C.Off, uncheckedBorderColor = C.Off,
            )
        )
    }
}

@Composable
fun MiniPlayer(onOpen: () -> Unit) {
    val s by PlayerController.state.collectAsState()
    val t = s.current ?: return
    val autoplay by Prefs.settings.collectAsState()
    Box(
        Modifier.padding(horizontal = 8.dp).fillMaxWidth().height(60.dp)
            .shadow(16.dp, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp)).background(C.Raised)
            .clickable(onClick = onOpen)
    ) {
        Row(
            Modifier.padding(start = 8.dp, end = 4.dp).fillMaxWidth().height(58.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ArtTile(t.album.ifBlank { t.title }, t, 42.dp, RoundedCornerShape(6.dp))
            Column(Modifier.weight(1f)) {
                Text(t.title, color = C.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    t.director + if (autoplay.autoplay) " · Autoplay on" else "",
                    color = C.Light, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = { PlayerController.toggle() }) {
                Icon(
                    if (s.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (s.isPlaying) "Pause" else "Play", tint = C.White
                )
            }
            IconButton(onClick = { PlayerController.next() }) {
                Icon(Icons.Rounded.SkipNext, contentDescription = "Next song", tint = C.White)
            }
        }
        val frac = if (s.durationMs > 0) (s.positionMs.toFloat() / s.durationMs).coerceIn(0f, 1f) else 0f
        Box(Modifier.align(Alignment.BottomStart).padding(horizontal = 10.dp).fillMaxWidth().height(2.dp).background(C.Off))
        Box(Modifier.align(Alignment.BottomStart).padding(start = 10.dp).fillMaxWidth(frac * 0.95f).height(2.dp).background(C.White))
    }
}

enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    HOME("home", "Home", Icons.Rounded.Home),
    LIBRARY("library", "Library", Icons.Rounded.LibraryMusic),
    VIDEOS("videos", "Videos", Icons.Rounded.VideoLibrary),
    SETTINGS("settings", "Settings", Icons.Rounded.Settings),
}

@Composable
fun BottomBar(current: Tab?, onSelect: (Tab) -> Unit) {
    Column(Modifier.fillMaxWidth().background(C.Bar)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(C.Raised))
        Row(Modifier.fillMaxWidth().navigationBarsPadding().height(64.dp)) {
            Tab.entries.forEach { tab ->
                val on = tab == current
                Column(
                    Modifier.weight(1f).fillMaxWidth().height(64.dp).clickable(role = Role.Tab) { onSelect(tab) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(tab.icon, contentDescription = null, tint = if (on) C.White else C.Grey, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.height(3.dp))
                    Text(tab.label, color = if (on) C.White else C.Grey, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun EmptyNote(text: String) {
    Text(text, color = C.Grey, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp))
}

@Composable
fun formatsState() = Library.formats.collectAsState()

val Pill = RoundedCornerShape(50)
val Round = CircleShape
fun Modifier.w(d: Dp) = this.width(d)
