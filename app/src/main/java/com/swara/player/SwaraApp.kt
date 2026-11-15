package com.swara.player

import android.app.Application
import com.swara.player.data.Library
import com.swara.player.data.Prefs
import com.swara.player.player.PlayerController

class SwaraApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        Library.init(this)
        PlayerController.init(this)
    }
}
