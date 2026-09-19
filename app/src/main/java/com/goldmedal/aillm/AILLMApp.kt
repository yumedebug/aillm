package com.goldmedal.aillm

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class AILLMApp : Application() {
    override fun onCreate() {
        super.onCreate()
    }
}
