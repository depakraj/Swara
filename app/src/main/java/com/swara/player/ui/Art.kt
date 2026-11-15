package com.swara.player.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swara.player.data.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ArtLoader {
    private val cache = LruCache<String, Bitmap>(80)
    private val missing = HashSet<String>()

    fun loadAlbumArt(context: Context, t: Track, px: Int): Bitmap? = load(context, t.uri, "a${t.albumId}_${t.id}_$px", px)

    fun load(context: Context, uri: Uri, key: String, px: Int): Bitmap? {
        cache.get(key)?.let { return it }
        synchronized(missing) { if (key in missing) return null }
        return try {
            context.contentResolver.loadThumbnail(uri, Size(px, px), null).also { cache.put(key, it) }
        } catch (_: Exception) {
            synchronized(missing) { missing += key }
            null
        }
    }
}

fun initials(name: String): String {
    val words = name.replace(".", " ").split(Regex("\\s+")).filter { it.isNotBlank() && it[0].isLetterOrDigit() }
    val a = words.getOrNull(0)?.first()?.uppercaseChar()
    val b = words.getOrNull(1)?.first()?.uppercaseChar()
    return listOfNotNull(a, b).joinToString("").ifEmpty { "♪" }
}

fun greyFor(name: String) = C.ThumbGreys[(name.fold(0) { h, c -> (h * 31 + c.code) % 997 }) % C.ThumbGreys.size]

@Composable
fun rememberBitmap(key: String, loader: suspend () -> Bitmap?): ImageBitmap? {
    val bmp by produceState<ImageBitmap?>(initialValue = null, key) {
        value = withContext(Dispatchers.IO) { loader() }?.asImageBitmap()
    }
    return bmp
}

/** Album art when the file has it, otherwise a grey tile with initials. */
@Composable
fun ArtTile(
    label: String,
    track: Track?,
    size: Dp,
    shape: Shape,
    fontSize: TextUnit = (size.value * 0.32f).sp,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val px = (size.value * 2.5f).toInt().coerceIn(96, 900)
    val art = if (track != null) rememberBitmap("${track.id}_$px") { ArtLoader.loadAlbumArt(context, track, px) } else null
    Box(
        modifier.size(size).clip(shape).background(greyFor(label)),
        contentAlignment = Alignment.Center,
    ) {
        if (art != null) {
            Image(art, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text(initials(label), color = C.White, fontSize = fontSize, fontWeight = FontWeight.Bold)
        }
    }
}

/** A group (director, singer, album) tile: art from its first song, else initials. */
@Composable
fun GroupTile(name: String, first: Track?, size: Dp, shape: Shape, useArt: Boolean) {
    ArtTile(label = name, track = if (useArt) first else null, size = size, shape = shape)
}

val Dp.half: Dp get() = (value / 2).dp
