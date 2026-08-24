package com.shoshiluna.executive

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {
    private var requestedTab by mutableIntStateOf(0)
    private var requestedMissionId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyNavigationIntent(intent)
        
        enableEdgeToEdge()
        setContent {
            ShoshiLunaApp(requestedTab, requestedMissionId)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyNavigationIntent(intent)
    }

    private fun applyNavigationIntent(intent: Intent?) {
        requestedTab = intent?.getIntExtra(EXTRA_OPEN_TAB, 0) ?: 0
        requestedMissionId = intent?.getStringExtra(EXTRA_MISSION_ID)
    }

    companion object {
        const val EXTRA_OPEN_TAB = "open_tab"
        const val EXTRA_MISSION_ID = "mission_id"
    }
}
