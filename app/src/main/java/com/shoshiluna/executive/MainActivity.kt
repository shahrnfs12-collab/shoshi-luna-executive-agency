package com.shoshiluna.executive

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.shoshiluna.executive.core.engine.TitanEngine

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize ShoshiLuna Executive Engine
        TitanEngine.initialize(this)
        
        enableEdgeToEdge()
        setContent {
            ShoshiLunaApp()
        }
    }
}
