package com.swara.player.ui.screens

import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swara.player.data.GroupMode
import com.swara.player.data.Library
import com.swara.player.data.Prefs
import com.swara.player.ui.BottomInset
import com.swara.player.ui.C
import com.swara.player.ui.Nav
import com.swara.player.ui.ScreenTitle
import com.swara.player.ui.SmallLabel
import com.swara.player.ui.SwitchRow

@Composable
fun SettingsScreen(nav: Nav) {
    val lib by Library.state.collectAsState()
    val s by Prefs.settings.collectAsState()
    val context = LocalContext.current
    val groups = Library.groups(lib.tracks, GroupMode.DIRECTOR).count { !it.name.startsWith("Unknown") }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(bottom = BottomInset),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { ScreenTitle("Settings", Modifier.padding(start = 16.dp, top = 12.dp)) }
        item {
            Card(onClick = { Library.rescan() }) {
                Icon(Icons.Rounded.Refresh, null, tint = C.White)
                Column(Modifier.weight(1f)) {
                    Text(
                        if (lib.scanning) "Scanning your phone…" else "Your whole phone is scanned automatically",
                        color = C.White, fontSize = 14.sp, fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${lib.tracks.size} songs · $groups music directors · ${lib.videos.size} videos. Tap to scan again.",
                        color = C.Grey, fontSize = 12.sp
                    )
                }
            }
        }
        item {
            Card(onClick = { nav.filters() }) {
                Icon(Icons.Rounded.Shield, null, tint = C.White)
                Column(Modifier.weight(1f)) {
                    Text("Kept out of your library", color = C.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("Call recordings, voice notes, ringtones · ${lib.hidden.size} files", color = C.Grey, fontSize = 12.sp)
                }
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = C.Off)
            }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallLabel("Group songs by")
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.Surface).padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    GroupMode.entries.forEach { m ->
                        val on = s.groupMode == m
                        Box(
                            Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(9.dp))
                                .background(if (on) C.White else C.Surface)
                                .clickable { Prefs.update { it.copy(groupMode = m) } },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(m.label, color = if (on) C.Black else C.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
        item { SmallLabel("Playback", Modifier.padding(start = 16.dp, top = 6.dp)) }
        item {
            Column {
                SwitchRow("Autoplay next song", "Keeps playing the list when a song ends", s.autoplay) { v -> Prefs.update { it.copy(autoplay = v) } }
                SwitchRow("Keep going when the list ends", "Plays more songs by the same music director", s.keepGoing) { v -> Prefs.update { it.copy(keepGoing = v) } }
                SwitchRow("Resume where I left off", "Remembers the song and position", s.resume) { v -> Prefs.update { it.copy(resume = v) } }
            }
        }
        item { SmallLabel("Hi-Res audio · FLAC and ALAC", Modifier.padding(start = 16.dp, top = 6.dp)) }
        item {
            SwitchRow("Show format and sample rate", "Badges like FLAC 24/96 on every song", s.showFormat) { v -> Prefs.update { it.copy(showFormat = v) } }
        }
        item {
            Card(onClick = {
                val wearable = context.packageManager.getLaunchIntentForPackage("com.samsung.android.app.watchmanager")
                context.startActivity(wearable ?: Intent(AndroidSettings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }) {
                Icon(Icons.Rounded.Bluetooth, null, tint = C.White)
                Column(Modifier.weight(1f)) {
                    Text("Best Bluetooth quality", color = C.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "For Galaxy Buds4 Pro, turn on Ultra High Quality audio (SSC UHQ, up to 24-bit / 96 kHz) in Galaxy Wearable. Tap to open it.",
                        color = C.Grey, fontSize = 12.sp
                    )
                }
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = C.Off)
            }
        }
        item {
            Text(
                "Swara plays music with the VLC engine (libVLC). Free and open source under the GPL.",
                color = C.Grey, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
fun Card(onClick: () -> Unit, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.Surface)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}
