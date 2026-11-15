package com.swara.player.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Black and white palette, after Apple Music's dark mode. */
object C {
    val Black = Color(0xFF000000)
    val Surface = Color(0xFF1C1C1E)
    val Raised = Color(0xFF2C2C2E)
    val Elevated = Color(0xFF3A3A3C)
    val Separator = Color(0xFF38383A)
    val Off = Color(0xFF48484A)
    val Grey = Color(0xFF98989F)
    val Light = Color(0xFFD1D1D6)
    val White = Color(0xFFFFFFFF)
    val Bar = Color(0xE6161618)

    /** Default-thumbnail greys, picked from the name so a director always gets the same tile. */
    val ThumbGreys = listOf(
        Color(0xFF3A3A3C), Color(0xFF48484A), Color(0xFF2C2C2E), Color(0xFF545458),
        Color(0xFF636366), Color(0xFF3A3A3C), Color(0xFF48484A), Color(0xFF545458),
    )
}

@Composable
fun SwaraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = C.White,
            onPrimary = C.Black,
            secondary = C.Light,
            background = C.Black,
            onBackground = C.White,
            surface = C.Surface,
            onSurface = C.White,
            surfaceVariant = C.Raised,
            onSurfaceVariant = C.Grey,
            outline = C.Separator,
        ),
        content = content,
    )
}
