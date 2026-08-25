package com.shoshiluna.executive.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.shoshiluna.executive.core.network.AiProviderId
import com.shoshiluna.executive.core.security.ApiKeyManager

@Composable
fun ApiKeyPoolDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val providers = remember {
        listOf(
            AiProviderId.OPENAI,
            AiProviderId.OPENROUTER,
            AiProviderId.GROQ,
            AiProviderId.GOOGLE_GEMINI,
            AiProviderId.ANTHROPIC
        )
    }
    var selected by remember { mutableStateOf(providers.first()) }
    var newKey by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }

    val keys = remember(selected, refresh) { ApiKeyManager.getProviderKeys(context, selected) }
    val active = remember(refresh) { ApiKeyManager.activeProvider(context) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ניהול מפתחות AI") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "המפתחות נשמרים מוצפנים במכשיר. האפליקציה מציגה רק גרסה ממוסכת שלהם.",
                    style = MaterialTheme.typography.bodySmall
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    providers.take(3).forEach { provider ->
                        FilterChip(
                            selected = selected == provider,
                            onClick = { selected = provider; message = null },
                            label = { Text(providerLabel(provider)) }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    providers.drop(3).forEach { provider ->
                        FilterChip(
                            selected = selected == provider,
                            onClick = { selected = provider; message = null },
                            label = { Text(providerLabel(provider)) }
                        )
                    }
                }

                if (selected == AiProviderId.OPENROUTER) {
                    Text(
                        "אותו pool משמש גם לפרופיל OpenRouter Uncensored.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                OutlinedTextField(
                    value = newKey,
                    onValueChange = { newKey = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("הוסף API key") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation()
                )
                Button(
                    enabled = newKey.isNotBlank(),
                    onClick = {
                        runCatching { ApiKeyManager.addProviderKey(context, selected, newKey) }
                            .onSuccess {
                                newKey = ""
                                message = "המפתח נוסף ל-${providerLabel(selected)}"
                                refresh++
                            }
                            .onFailure { message = it.message ?: "שמירת המפתח נכשלה" }
                    }
                ) { Text("+ הוסף מפתח") }

                if (keys.isEmpty()) {
                    Text("אין מפתחות שמורים לספק הזה.", style = MaterialTheme.typography.bodySmall)
                } else {
                    Text("${keys.size} מפתחות שמורים", style = MaterialTheme.typography.titleSmall)
                    LazyColumn(modifier = Modifier.height((keys.size.coerceAtMost(4) * 84).dp)) {
                        itemsIndexed(keys) { index, key ->
                            val enabled = ApiKeyManager.isKeyEnabled(context, selected, key)
                            ElevatedCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("#${index + 1}  ${ApiKeyManager.maskedKey(key)}")
                                        Text(if (enabled) "פעיל" else "מושבת", style = MaterialTheme.typography.bodySmall)
                                    }
                                    Switch(
                                        checked = enabled,
                                        onCheckedChange = {
                                            ApiKeyManager.setKeyEnabled(context, selected, key, it)
                                            refresh++
                                        }
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    TextButton(onClick = {
                                        ApiKeyManager.removeProviderKey(context, selected, key)
                                        message = "המפתח הוסר"
                                        refresh++
                                    }) { Text("מחק") }
                                }
                            }
                        }
                    }
                }

                OutlinedButton(
                    enabled = ApiKeyManager.hasProviderKey(context, selected),
                    onClick = {
                        runCatching { ApiKeyManager.setActiveProvider(context, selected) }
                            .onSuccess { message = "${providerLabel(selected)} הוגדר כספק הפעיל"; refresh++ }
                            .onFailure { message = it.message ?: "לא ניתן להפעיל את הספק" }
                    }
                ) {
                    Text(if (active == selected) "✓ ספק פעיל" else "הגדר כספק פעיל")
                }

                message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("סגור") } }
    )
}

private fun providerLabel(provider: AiProviderId): String = when (provider) {
    AiProviderId.OPENAI -> "OpenAI"
    AiProviderId.OPENROUTER -> "OpenRouter"
    AiProviderId.GROQ -> "Groq"
    AiProviderId.GOOGLE_GEMINI -> "Gemini"
    AiProviderId.ANTHROPIC -> "Anthropic"
    AiProviderId.OPENROUTER_UNCENSORED -> "OR Uncensored"
    AiProviderId.CUSTOM -> "Custom"
}
