package com.shoshiluna.executive.core.voice

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

enum class AudioPermissionState { REQUIRED, REQUESTING, DENIED, PERMANENTLY_DENIED, READY }

@Composable
fun rememberAudioPermissionFlow(onGranted: () -> Unit = {}): Pair<AudioPermissionState, () -> Unit> {
    val context = LocalContext.current
    val activity = context as? Activity
    fun current(): AudioPermissionState = if (
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    ) AudioPermissionState.READY else AudioPermissionState.REQUIRED

    var state by remember { mutableStateOf(current()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) { state = AudioPermissionState.READY; onGranted() }
        else {
            val rationale = activity?.let { ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.RECORD_AUDIO) } ?: false
            state = if (rationale) AudioPermissionState.DENIED else AudioPermissionState.PERMANENTLY_DENIED
        }
    }
    val request: () -> Unit = {
        if (state != AudioPermissionState.READY && state != AudioPermissionState.REQUESTING) {
            state = AudioPermissionState.REQUESTING
            launcher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    return state to request
}
