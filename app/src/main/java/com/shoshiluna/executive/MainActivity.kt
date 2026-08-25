package com.shoshiluna.executive

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import com.shoshiluna.executive.core.engine.TitanEngine

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        TitanEngine.initialize(this)
        WindowCompat.setDecorFitsSystemWindows(window, true)

        setContent {
            ShoshiLunaAppWithVideo()
        }
    }
}
