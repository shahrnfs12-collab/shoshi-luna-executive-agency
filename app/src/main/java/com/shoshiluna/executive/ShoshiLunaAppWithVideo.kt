package com.shoshiluna.executive

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.shoshiluna.executive.core.agent.ExecutiveAgent
import com.shoshiluna.executive.core.business.EvidenceCategory
import com.shoshiluna.executive.core.business.EvidenceRegistry
import com.shoshiluna.executive.core.video.VideoWorkspace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Adds a device-local video workspace over the existing executive app. */
@Composable
fun ShoshiLunaAppWithVideo() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var owner by remember { mutableStateOf(ExecutiveAgent.SHOSHI) }
    var asset by remember { mutableStateOf<VideoWorkspace.VideoAsset?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var building by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { VideoWorkspace.importVideo(uri.toString(), owner.id) }
            .onSuccess { imported ->
                asset = imported
                status = "וידאו נקלט: ${imported.displayName}"
            }
            .onFailure { error -> status = "קליטת וידאו נכשלה: ${error.message ?: error.javaClass.simpleName}" }
    }

    Box(Modifier.fillMaxSize()) {
        ShoshiLunaApp()

        SmallFloatingActionButton(
            onClick = { picker.launch(arrayOf("video/*")) },
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 58.dp, end = 10.dp),
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(Icons.Default.AttachFile, contentDescription = "העלאת וידאו")
        }

        asset?.let { current ->
            ElevatedCard(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 108.dp, start = 12.dp, end = 12.dp)
                    .widthIn(max = 420.dp)
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("VIDEO WORKSPACE", style = MaterialTheme.typography.titleSmall)
                    Text(current.displayName, style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(owner == ExecutiveAgent.SHOSHI, onClick = { owner = ExecutiveAgent.SHOSHI }, label = { Text("SHOSHI") })
                        FilterChip(owner == ExecutiveAgent.LUNA, onClick = { owner = ExecutiveAgent.LUNA }, label = { Text("LUNA") })
                    }
                    status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    Button(
                        enabled = !building,
                        onClick = {
                            building = true
                            status = "בונה MP4 מקומי…"
                            scope.launch {
                                runCatching {
                                    withContext(Dispatchers.IO) { VideoWorkspace.buildTrimmedMp4(current.id) }
                                }.onSuccess { result ->
                                    val evidenceId = EvidenceRegistry.record(
                                        EvidenceCategory.VERIFIED_RESULT,
                                        "Local MP4 build completed: ${result.outputPath}; bytes=${result.bytes}",
                                        "VideoWorkspace:MediaMuxer"
                                    )
                                    status = "MP4 נבנה בהצלחה • Evidence ${evidenceId.take(8)} • ${result.outputPath}"
                                }.onFailure { error ->
                                    status = "Build נכשל: ${error.message ?: error.javaClass.simpleName}"
                                }
                                building = false
                            }
                        }
                    ) {
                        Icon(Icons.Default.MovieCreation, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (building) "בונה…" else "בנה MP4")
                    }
                    TextButton(onClick = { asset = null; status = null }) { Text("סגור") }
                }
            }
        }
    }
}
