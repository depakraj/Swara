package com.swara.player.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.swara.player.data.GroupMode
import com.swara.player.data.Library
import com.swara.player.data.Track
import com.swara.player.player.PlayerController
import com.swara.player.ui.screens.FiltersScreen
import com.swara.player.ui.screens.GroupScreen
import com.swara.player.ui.screens.HomeScreen
import com.swara.player.ui.screens.LibraryScreen
import com.swara.player.ui.screens.NowPlayingScreen
import com.swara.player.ui.screens.SettingsScreen
import com.swara.player.ui.screens.VideosScreen

/** Special lists that aren't a director, singer or album. */
object Lists {
    const val FAVOURITES = "Favourites"
    const val RECENT = "Recently added"
    const val MOST = "Most played"
    const val ALL = "All songs"
}

class Nav(private val nav: NavHostController, val openPlayer: () -> Unit) {
    fun group(mode: GroupMode, name: String) = nav.navigate("group/${mode.name}/${Uri.encode(name)}")
    fun list(name: String) = nav.navigate("group/LIST/${Uri.encode(name)}")
    fun tab(t: Tab) = nav.navigate(t.route) {
        popUpTo("home") { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
    fun filters() = nav.navigate("filters")
    fun back() = nav.popBackStack()
}

@Composable
fun SwaraUi() {
    val nav = rememberNavController()
    var playerOpen by remember { mutableStateOf(false) }
    val lib by Library.state.collectAsState()
    val actions = remember(nav) { Nav(nav) { playerOpen = true } }

    // Carry on where you left off.
    LaunchedEffect(lib.tracks.isNotEmpty()) {
        if (lib.tracks.isNotEmpty()) PlayerController.restoreResume(lib.tracks)
    }

    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "home"
    val tab = when {
        route.startsWith("library") || route.startsWith("group") -> Tab.LIBRARY
        route.startsWith("videos") -> Tab.VIDEOS
        route.startsWith("settings") || route.startsWith("filters") -> Tab.SETTINGS
        else -> Tab.HOME
    }

    Box(Modifier.fillMaxSize().background(C.Black)) {
        NavHost(nav, startDestination = "home", modifier = Modifier.fillMaxSize()) {
            composable("home") { HomeScreen(actions) }
            composable("library") { LibraryScreen(actions) }
            composable("group/{mode}/{name}") { e ->
                val mode = e.arguments?.getString("mode") ?: "DIRECTOR"
                val name = Uri.decode(e.arguments?.getString("name") ?: "")
                GroupScreen(mode, name, actions)
            }
            composable("videos") { VideosScreen() }
            composable("settings") { SettingsScreen(actions) }
            composable("filters") { FiltersScreen(actions) }
        }

        Column(Modifier.align(Alignment.BottomCenter)) {
            MiniPlayer(onOpen = { playerOpen = true })
            Box(Modifier.padding(top = 8.dp)) {
                BottomBar(tab) { actions.tab(it) }
            }
        }

        AnimatedVisibility(
            visible = playerOpen,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
        ) {
            BackHandler { playerOpen = false }
            NowPlayingScreen(onClose = { playerOpen = false }, onOpenGroup = { mode, name ->
                playerOpen = false
                actions.group(mode, name)
            })
        }
    }
}

/** Songs for a group route, including the special lists. */
fun tracksFor(mode: String, name: String, all: List<Track>): List<Track> = when (mode) {
    "LIST" -> when (name) {
        Lists.FAVOURITES -> {
            val fav = com.swara.player.data.Prefs.favourites.value
            all.filter { it.id in fav }
        }
        Lists.RECENT -> all.sortedByDescending { it.dateAdded }.take(100)
        Lists.MOST -> all.map { it to com.swara.player.data.Prefs.playCount(it.id) }
            .filter { it.second > 0 }.sortedByDescending { it.second }.take(100).map { it.first }
        else -> all
    }
    else -> {
        val gm = runCatching { GroupMode.valueOf(mode) }.getOrDefault(GroupMode.DIRECTOR)
        Library.groups(all, gm).firstOrNull { it.name == name }?.tracks ?: emptyList()
    }
}

/** Bottom space so lists scroll clear of the mini player and tab bar. */
val BottomInset = 170.dp
