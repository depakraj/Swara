package com.swara.player.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swara.player.data.HideReason
import com.swara.player.data.Library
import com.swara.player.data.Prefs
import com.swara.player.ui.BottomInset
import com.swara.player.ui.C
import com.swara.player.ui.Nav
import com.swara.player.ui.ScreenTitle
import com.swara.player.ui.SmallLabel
import com.swara.player.ui.SwitchRow

@Composable
fun FiltersScreen(nav: Nav) {
    val lib by Library.state.collectAsState()
    val s by Prefs.settings.collectAsState()
    var review by remember { mutableStateOf(false) }
    var pickFolder by remember { mutableStateOf(false) }
    val counts = lib.hidden.groupingBy { it.second }.eachCount()

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(bottom = BottomInset),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            IconButton(onClick = { nav.back() }, modifier = Modifier.padding(start = 4.dp, top = 4.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back to settings", tint = C.White)
            }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ScreenTitle("Kept out of your library")
                Text(
                    "Only real music shows up. These files stay on your phone, but Swara skips them automatically.",
                    color = C.Grey, fontSize = 14.sp
                )
            }
        }
        item {
            Card(onClick = { review = !review }) {
                Icon(Icons.Rounded.Shield, null, tint = C.White)
                Text("${lib.hidden.size} files hidden", color = C.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(
                    if (review) "Close" else "Review", color = C.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.border(1.dp, C.Off, RoundedCornerShape(18.dp)).padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }
        if (review) {
            items(lib.hidden.take(300), key = { it.first.id }) { (t, reason) ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(t.fileName, color = C.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${reason.title} · ${t.folder}", color = C.Grey, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        item {
            Column {
                HideReason.entries.filter { it != HideReason.FOLDER }.forEach { r ->
                    SwitchRow(r.title, r.subtitle, r in s.filters, trailing = (counts[r] ?: 0).toString()) { on ->
                        Prefs.update { it.copy(filters = if (on) it.filters + r else it.filters - r) }
                    }
                }
            }
        }
        if (s.hiddenFolders.isNotEmpty()) {
            item { SmallLabel("Folders you hid", Modifier.padding(start = 16.dp, top = 8.dp)) }
            items(s.hiddenFolders.sorted(), key = { "f_$it" }) { f ->
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(f, color = C.White, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    IconButton(onClick = { Prefs.update { it.copy(hiddenFolders = it.hiddenFolders - f) } }) {
                        Icon(Icons.Rounded.Close, "Show $f again", tint = C.Grey)
                    }
                }
            }
        }
        item {
            Row(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp))
                    .border(1.dp, C.Off, RoundedCornerShape(12.dp)).clickable { pickFolder = true },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Rounded.Add, null, tint = C.White)
                Text("Hide another folder", color = C.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }

    if (pickFolder) {
        val options = lib.folders.filter { f -> s.hiddenFolders.none { f.startsWith(it) } }
        AlertDialog(
            onDismissRequest = { pickFolder = false },
            confirmButton = { TextButton(onClick = { pickFolder = false }) { Text("Done", color = C.White) } },
            title = { Text("Hide a folder") },
            text = {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(options, key = { it }) { f ->
                        Text(
                            f, color = C.White, fontSize = 14.sp,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                .clickable { Prefs.update { it.copy(hiddenFolders = it.hiddenFolders + f) }; pickFolder = false }
                                .padding(vertical = 12.dp, horizontal = 4.dp)
                        )
                    }
                }
            },
            containerColor = C.Surface,
            titleContentColor = C.White,
        )
    }
}
