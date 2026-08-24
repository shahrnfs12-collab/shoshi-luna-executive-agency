package com.shoshiluna.executive.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shoshiluna.executive.core.fabric.FabricAuditLedger
import com.shoshiluna.executive.core.fabric.ModelRegistry
import com.shoshiluna.executive.core.fabric.ProviderHealthMonitor
import com.shoshiluna.executive.core.fabric.PublicCatalogDiscovery
import com.shoshiluna.executive.core.network.AiProviderId
import com.shoshiluna.executive.core.security.ApiKeyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val FabricGold = Color(0xFFD8B45A)

@Composable
fun AiFabricDashboardScreen() {
    val context = LocalContext.current
    val models by ModelRegistry.models.collectAsState()
    val health by ProviderHealthMonitor.health.collectAsState()
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf("Live discovery has not run in this installation.") }
    val counts = models.values.groupingBy { it.status }.eachCount()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("AI Intelligence Fabric", color = FabricGold, fontWeight = FontWeight.Bold)
            Text("Registry ${models.size}/1000 • ${counts.entries.joinToString { "${it.key}:${it.value}" }}")
            Text("Status is evidence-based. CONNECTED is never presented as VERIFIED.", color = Color.Gray)
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Official catalog discovery", fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(enabled = !busy, onClick = {
                            busy = true
                            scope.launch {
                                val discovery = withContext(Dispatchers.IO) { PublicCatalogDiscovery.discoverHuggingFace() }
                                result = "${discovery.source}: observed ${discovery.discovered}, accepted ${discovery.accepted}, failed ${discovery.failed}${discovery.error?.let { " • $it" }.orEmpty()}"
                                busy = false
                            }
                        }) { Text("Hugging Face") }
                        OutlinedButton(enabled = !busy && ApiKeyManager.hasProviderKey(context, AiProviderId.OPENROUTER), onClick = {
                            busy = true
                            scope.launch {
                                val discovery = withContext(Dispatchers.IO) { PublicCatalogDiscovery.discoverOpenRouter(context) }
                                result = "${discovery.source}: observed ${discovery.discovered}, accepted ${discovery.accepted}, failed ${discovery.failed}${discovery.error?.let { " • $it" }.orEmpty()}"
                                busy = false
                            }
                        }) { Text("OpenRouter") }
                    }
                    if (busy) CircularProgressIndicator()
                    Text(result)
                }
            }
        }
        item { Text("Provider health", color = FabricGold, fontWeight = FontWeight.Bold) }
        if (health.isEmpty()) item { Text("No successful or failed live provider call has been observed yet.", color = Color.Gray) }
        items(health.values.sortedBy { it.providerId }) { row ->
            Text("${row.providerId}: ${row.status} • ${row.latencyP50Ms ?: "—"} ms • reliability ${row.reliability?.let { "%.2f".format(it) } ?: "—"}")
        }
        item {
            Text("Audit ledger", color = FabricGold, fontWeight = FontWeight.Bold)
            Text("Hash chain: ${if (FabricAuditLedger.verifyChain()) "VALID" else "INVALID"} • in-process records ${FabricAuditLedger.recent().size}")
        }
    }
}
