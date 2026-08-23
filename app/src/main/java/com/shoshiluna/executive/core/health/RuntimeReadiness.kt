package com.shoshiluna.executive.core.health

import android.content.Context
import com.shoshiluna.executive.core.presentation.Avatar3DAssets
import com.shoshiluna.executive.core.security.ApiKeyManager

data class ReadinessItem(val name: String, val ready: Boolean, val detail: String)

data class RuntimeReadinessReport(val items: List<ReadinessItem>) {
    val coreReady: Boolean get() = items.filterNot { it.name.startsWith("3D") || it.name.startsWith("AI") }.all { it.ready }
    val allReady: Boolean get() = items.all { it.ready }
}

object RuntimeReadiness {
    fun inspect(context: Context): RuntimeReadinessReport {
        val models = Avatar3DAssets.readiness(context)
        return RuntimeReadinessReport(
            listOf(
                ReadinessItem("Core Android runtime", true, "App shell, mission control and local memory are packaged"),
                ReadinessItem("AI provider", ApiKeyManager.hasOpenAiKey(context), if (ApiKeyManager.hasOpenAiKey(context)) "Encrypted API key present" else "No OpenAI API key configured"),
                ReadinessItem("3D SHOSHI", models.shoshiModelPresent, if (models.shoshiModelPresent) "models/shoshi.glb present" else "models/shoshi.glb missing"),
                ReadinessItem("3D LUNA", models.lunaModelPresent, if (models.lunaModelPresent) "models/luna.glb present" else "models/luna.glb missing"),
            )
        )
    }
}
