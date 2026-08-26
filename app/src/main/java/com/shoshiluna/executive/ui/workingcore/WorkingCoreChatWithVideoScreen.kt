package com.shoshiluna.executive.ui.workingcore

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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shoshiluna.executive.core.business.EvidenceCategory
import com.shoshiluna.executive.core.business.EvidenceRegistry
import com.shoshiluna.executive.core.video.VideoWorkspace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Chat host that adds device-local video import/build controls without changing
 * the existing AI/chat request semantics.
 */
@Composable
fun WorkingCoreChatWithVideoScreen(vm: WorkingCoreViewModel = viewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ui by vm.uiState.collectAsState()
    var selectedAsset by remember { mutableStateOf<VideoWorkspace.VideoAsset?>(null) }
    var videoStatus by remember { mutableStateOf<String?>(null) }
    var isBuilding by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching {
            VideoWorkspace.importVideo(uri.toString(), ui.selectedAgent.id)
        }.onSuccess { asset ->
            selectedAsset = asset
            val sizeMb = asset.sizeBytes / (1024.0 * 1024.0)
            videoStatus = "וידאו נקלט: ${asset.displayName} • %.1f MB".format(sizeMb)
            vm.send(
                "צירפתי וידאו לעבודה: ${asset.displayName}. מזהה הנכס המקומי הוא ${asset.id}. " +
                    "הווידאו נשאר במכשיר ולא הועלה לשירות חיצוני. המתיני להוראות העריכה שלי.",
                speakResponse = false
            )
        }.onFailure { error ->
            videoStatus = "קליטת הווידאו נכשלה: ${error.message ?: error.javaClass.simpleName}"
        }
    }

    Box(Modifier.fillMaxSize()) {
        WorkingCoreChatScreen(vm)

        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 92.dp, end = 10.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SmallFloatingActionButton(
                onClick = { picker.launch(arrayOf("video/*")) },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.AttachFile, contentDescription = "העלאת וידאו")
            }

            selectedAsset?.let { asset ->
                ElevatedCard(modifier = Modifier.widthIn(max = 330.dp)) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("VIDEO WORKSPACE", style = MaterialTheme.typography.labelLarge)
                        Text(asset.displayName, style = MaterialTheme.typography.bodySmall)
                        Text(
                            videoStatus.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Button(
                            enabled = !isBuilding,
                            onClick = {
                                isBuilding = true
                                videoStatus = "בונה MP4 מקומי אמיתי…"
                                scope.launch {
                                    val outcome = runCatching {
                                        withContext(Dispatchers.IO) {
                                            VideoWorkspace.buildTrimmedMp4(asset.id)
                                        }
                                    }
                                    outcome.onSuccess { result ->
                                        val evidenceId = EvidenceRegistry.record(
                                            category = EvidenceCategory.VERIFIED_RESULT,
                                            content = "Local MP4 build completed: ${result.outputPath}; bytes=${result.bytes}",
                                            source = "VideoWorkspace:MediaMuxer"
                                        )
                                        videoStatus = "הבנייה הושלמה • ${(result.bytes / (1024.0 * 1024.0)).let { "%.1f".format(it) }} MB • Evidence ${evidenceId.take(8)}"
                                        vm.send(
                                            "בניית הווידאו המקומית הסתיימה בהצלחה. קובץ MP4 אמיתי נוצר ב-${result.outputPath}. " +
                                                "Evidence ID: $evidenceId. התייחסי לזה כתוצאת כלי מאומתת.",
                                            speakResponse = false
                                        )
                                    }.onFailure { error ->
                                        videoStatus = "בניית הווידאו נכשלה: ${error.message ?: error.javaClass.simpleName}"
                                    }
                                    isBuilding = false
                                }
                            }
                        ) {
                            Icon(Icons.Default.MovieCreation, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(if (isBuilding) "בונה…" else "בנה MP4")
                        }
                        TextButton(onClick = { selectedAsset = null; videoStatus = null }) {
                            Text("סגור")
                        }
                    }
                }
            }
        }
    }
}
