package com.shoshiluna.executive.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shoshiluna.executive.core.external.*
import kotlinx.coroutines.launch

private val NetworkGold = Color(0xFFE2C06B)
private val NetworkPanel = Color(0xFF11141A)

@Composable
fun ExternalNetworkScreen() {
    val agents by TrustedExternalAgentRegistry.agents.collectAsState()
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var endpoint by remember { mutableStateOf("") }
    var protocol by remember { mutableStateOf(ExternalProtocol.GENERIC_HTTPS_JSON) }
    var status by remember { mutableStateOf<String?>(null) }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("EXTERNAL AGENT NETWORK", color = NetworkGold, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            Text("Trusted registry • handshake • approval firewall • quarantine • evidence", color = Color.Gray)
        }
        item {
            ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = NetworkPanel), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("REGISTER CANDIDATE", color = NetworkGold, fontWeight = FontWeight.Bold)
                    OutlinedTextField(name, { name = it }, label = { Text("Display name") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(endpoint, { endpoint = it }, label = { Text("HTTPS endpoint") }, modifier = Modifier.fillMaxWidth())
                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(onClick = { expanded = true }) { Text(protocol.name) }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            ExternalProtocol.entries.forEach { p ->
                                DropdownMenuItem(text = { Text(p.name) }, onClick = { protocol = p; expanded = false })
                            }
                        }
                    }
                    Button(
                        enabled = name.isNotBlank() && endpoint.isNotBlank(),
                        onClick = {
                            val result = runCatching {
                                TrustedExternalAgentRegistry.registerCandidate(
                                    ExternalAgentDescriptor(displayName = name.trim(), endpoint = endpoint.trim(), protocol = protocol)
                                ).getOrThrow()
                            }
                            status = result.fold({ "Candidate registered as PENDING: ${it.id.take(8)}" }, { "Rejected: ${it.message}" })
                            if (result.isSuccess) { name = ""; endpoint = "" }
                        }
                    ) { Text("Register candidate") }
                    status?.let { Text(it, color = Color.LightGray, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }

        if (agents.isEmpty()) {
            item { Text("No external agents registered.", color = Color.Gray) }
        }
        items(agents.values.toList(), key = { it.id }) { agent ->
            ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = NetworkPanel), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(agent.displayName, color = Color.White, fontWeight = FontWeight.Bold)
                    Text(agent.endpoint, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                    Text("${agent.protocol} • ${agent.trustStatus}", color = NetworkGold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (agent.trustStatus == ExternalTrustStatus.PENDING) {
                            OutlinedButton(onClick = { TrustedExternalAgentRegistry.markTrusted(agent.id, null) }) { Text("Trust") }
                        }
                        if (agent.trustStatus == ExternalTrustStatus.TRUSTED) {
                            OutlinedButton(onClick = {
                                scope.launch {
                                    val result = ExternalAgentGateway.handshake(agent.id)
                                    status = result.fold(
                                        { "Handshake accepted: ${it.remoteIdentity} • ${it.protocolVersion ?: "unknown version"}" },
                                        { "Handshake failed: ${it.message}" }
                                    )
                                }
                            }) { Text("Handshake") }
                            OutlinedButton(onClick = { TrustedExternalAgentRegistry.suspend(agent.id) }) { Text("Suspend") }
                        }
                        if (agent.trustStatus != ExternalTrustStatus.REVOKED) {
                            TextButton(onClick = { TrustedExternalAgentRegistry.revoke(agent.id) }) { Text("Revoke") }
                        }
                    }
                }
            }
        }
        item {
            Text(
                "No message crosses the network merely because an AI suggested it. Sending requires an authorized mission, COMMUNICATE_EXTERNAL permission and explicit Human Approval.",
                color = Color.Gray,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
