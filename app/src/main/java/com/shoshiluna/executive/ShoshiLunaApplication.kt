package com.shoshiluna.executive

import android.app.Application
import com.shoshiluna.executive.core.engine.TitanEngine

class ShoshiLunaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        TitanEngine.initialize(this)
    }
}
