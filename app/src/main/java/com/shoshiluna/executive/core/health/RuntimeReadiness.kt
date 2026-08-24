package com.shoshiluna.executive.core.health

import android.content.Context
import com.shoshiluna.executive.core.presentation.Avatar3DAssets
import com.shoshiluna.executive.core.security.ApiKeyManager
import com.shoshiluna.executive.core.hardening.CapabilityRegistry
import com.shoshiluna.executive.core.hardening.CapabilityStatus

data class ReadinessItem(val name: String, val ready: Boolean, val detail: String)

data class RuntimeReadinessReport(val items: List<ReadinessItem>) {
    val coreReady: Boolean get() = items.filterNot { it.name.startsWith("3D") || it.name.startsWith("AI") }.all { it.ready }
    val allReady: Boolean get() = items.all { it.ready }
}

object RuntimeReadiness {
    fun inspect(context: Context): RuntimeReadinessReport {
        val models = Avatar3DAssets.readiness(context)
        val aiState = CapabilityRegistry.get("ai")
        val aiVerified = aiState.status == CapabilityStatus.READY
        val configuredProviders = ApiKeyManager.configuredProviders(context)
        val hasCredential = configuredProviders.isNotEmpty()
        return RuntimeReadinessReport(
            listOf(
                ReadinessItem("Core Android runtime", true, "App shell, mission control and local memory are packaged"),
                ReadinessItem(
                    "AI provider",
                    aiVerified,
                    when {
                        aiVerified -> aiState.detail
                        hasCredential -> "Stored: ${configuredProviders.joinToString { it.name }}; live verification has not passed"
                        else -> "No AI provider credential configured"
                    }
                ),
                ReadinessItem("3D SHOSHI", models.shoshiModelPresent, if (models.shoshiModelPresent) "models/shoshi.glb present" else "models/shoshi.glb missing"),
                ReadinessItem("3D LUNA", models.lunaModelPresent, if (models.lunaModelPresent) "models/luna.glb present" else "models/luna.glb missing"),
            )
        )
    }
}
