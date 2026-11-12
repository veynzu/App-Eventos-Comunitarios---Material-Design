package com.eventoscomunitarios.app

import android.app.Application
import com.eventoscomunitarios.app.core.util.initializeAppCheck
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ShareYouApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initializeAppCheck()
    }
}
