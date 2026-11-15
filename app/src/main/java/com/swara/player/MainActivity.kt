package com.swara.player

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.swara.player.data.Library
import com.swara.player.ui.C
import com.swara.player.ui.SwaraLogo
import com.swara.player.ui.SwaraTheme
import com.swara.player.ui.SwaraUi

class MainActivity : ComponentActivity() {

    private val mediaPermissions: Array<String>
        get() = if (Build.VERSION.SDK_INT >= 33)
            arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.POST_NOTIFICATIONS)
        else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

    private fun hasMedia(): Boolean {
        val p = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        return ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            SwaraTheme {
                var granted by remember { mutableStateOf(hasMedia()) }
                val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
                    granted = hasMedia()
                }
                LaunchedEffect(Unit) { if (!granted) launcher.launch(mediaPermissions) }
                LaunchedEffect(granted) { if (granted) Library.start() }

                if (granted) {
                    SwaraUi()
                } else {
                    Box(Modifier.fillMaxSize().background(C.Black).padding(32.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            SwaraLogo(96.dp)
                            Text("Swara", color = C.White, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
                            Text(
                                "Allow access to music and videos so Swara can find your songs automatically. Nothing leaves your phone.",
                                color = C.Grey, fontSize = 15.sp, textAlign = TextAlign.Center
                            )
                            Text(
                                "Allow access", color = C.Black, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(C.White)
                                    .clickable { launcher.launch(mediaPermissions) }
                                    .padding(horizontal = 28.dp, vertical = 14.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
