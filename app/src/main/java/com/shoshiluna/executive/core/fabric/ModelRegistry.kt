package com.shoshiluna.executive.core.fabric

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object ModelRegistry {
    const val MAX_MODELS = 1_000
    private const val OPEN_POLICY_MAX_RATIO = 0.10
    private val _models = MutableStateFlow(seedModels().associateBy { it.id })
    val models = _models.asStateFlow()
    @Volatile private var appContext: Context? = null
    private val gson = Gson()

    @Synchronized
    fun initialize(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        val prefs = context.getSharedPreferences("ai_model_registry_v1", Context.MODE_PRIVATE)
        val raw = prefs.getString("models", null) ?: return
        val type = object : TypeToken<List<ModelDescriptor>>() {}.type
        val restored = runCatching { gson.fromJson<List<ModelDescriptor>>(raw, type) }
        if (restored.isFailure) {
            prefs.edit().putString("corrupt_models_backup", raw.take(200_000)).remove("models").commit()
            return
        }
        val merged = seedModels().associateBy { it.id }.toMutableMap()
        restored.getOrNull().orEmpty().take(MAX_MODELS).forEach { model ->
            runCatching { applyValidated(merged, model) }
        }
        _models.value = merged.entries.take(MAX_MODELS).associate { it.key to it.value }
    }

    @Synchronized
    fun upsert(model: ModelDescriptor) {
        val current = _models.value.toMutableMap()
        applyValidated(current, model)
        _models.value = current.toMap()
        persist()
    }

    @Synchronized
    fun upsertAll(models: Collection<ModelDescriptor>): Int {
        val current = _models.value.toMutableMap()
        var accepted = 0
        models.forEach { model ->
            runCatching { applyValidated(current, model) }.onSuccess { accepted++ }
        }
        _models.value = current.toMap()
        persist()
        return accepted
    }

    fun get(id: String) = _models.value[id]
    fun all() = _models.value.values.sortedBy { it.id }
    fun statusCounts() = all().groupingBy { it.status }.eachCount()
    fun verified() = all().filter { it.status == RegistryStatus.VERIFIED }

    fun transition(id: String, status: RegistryStatus, metrics: RuntimeMetrics? = null) {
        val existing = requireNotNull(get(id))
        val now = System.currentTimeMillis()
        upsert(existing.copy(
            status = status,
            metrics = metrics ?: existing.metrics,
            lastVerifiedAt = if (status == RegistryStatus.VERIFIED) now else existing.lastVerifiedAt
        ))
    }

    private fun applyValidated(current: MutableMap<String, ModelDescriptor>, model: ModelDescriptor) {
        require(model.id in current || current.size < MAX_MODELS) { "Registry capacity reached" }
        if (model.contentPolicy == ContentPolicyProfile.OPEN_POLICY && current[model.id]?.contentPolicy != ContentPolicyProfile.OPEN_POLICY) {
            val projectedOpen = current.values.count { it.contentPolicy == ContentPolicyProfile.OPEN_POLICY } + 1
            val projectedSize = if (model.id in current) current.size else current.size + 1
            require(projectedOpen.toDouble() / projectedSize <= OPEN_POLICY_MAX_RATIO) { "Open-policy pool may not exceed 10%" }
        }
        current[model.id] = model
    }

    private fun persist() {
        appContext?.getSharedPreferences("ai_model_registry_v1", Context.MODE_PRIVATE)?.edit()
            ?.putString("models", gson.toJson(_models.value.values.toList()))?.apply()
    }

    private fun seedModels(): List<ModelDescriptor> {
        val now = 1787569200000L // 2026-08-24 source verification snapshot
        fun evidence(url: String, claim: String) = listOf(EvidenceReference(url, "OFFICIAL_DOCUMENTATION", now, claim))
        fun proprietary(id: String, provider: String, name: String, context: Int?, caps: Set<FabricCapability>, input: Double?, output: Double?, url: String) =
            ModelDescriptor(id, provider, name, capabilities = caps, contextWindow = context,
                inputModalities = if (FabricCapability.VISION in caps) setOf(Modality.TEXT, Modality.IMAGE) else setOf(Modality.TEXT),
                toolCalling = FabricCapability.TOOL_CALLING in caps, structuredOutput = FabricCapability.STRUCTURED_OUTPUT in caps,
                cost = CostCard(input, output), apiCompatibility = provider.uppercase(), ownership = ModelOwnership.PROPRIETARY,
                status = RegistryStatus.DOCUMENTED, trustLevel = TrustLevel.T2_VERIFIED_PROVIDER,
                contentPolicy = ContentPolicyProfile.STANDARD, evidence = evidence(url, "Official model catalog entry"))
        val general = setOf(FabricCapability.GENERAL_LLM, FabricCapability.REASONING, FabricCapability.CODING, FabricCapability.VISION, FabricCapability.TOOL_CALLING, FabricCapability.STRUCTURED_OUTPUT, FabricCapability.LONG_CONTEXT)
        return listOf(
            proprietary("openai/gpt-5.6-sol", "openai", "GPT-5.6 Sol", 1_050_000, general, 4.0, 20.0, "https://developers.openai.com/api/docs/models/gpt-5.6-sol"),
            proprietary("openai/gpt-5.6-terra", "openai", "GPT-5.6 Terra", 1_050_000, general, 2.0, 12.0, "https://developers.openai.com/api/docs/models/gpt-5.6-terra"),
            proprietary("openai/gpt-5.6-luna", "openai", "GPT-5.6 Luna", 1_050_000, general, 0.2, 1.2, "https://developers.openai.com/api/docs/models/gpt-5.6-luna"),
            proprietary("anthropic/claude-opus-4-7", "anthropic", "Claude Opus 4.7", 1_000_000, general, 5.0, 25.0, "https://docs.anthropic.com/en/docs/about-claude/models/overview"),
            proprietary("anthropic/claude-sonnet-4-6", "anthropic", "Claude Sonnet 4.6", 1_000_000, general, 3.0, 15.0, "https://docs.anthropic.com/en/docs/about-claude/models/overview"),
            proprietary("anthropic/claude-haiku-4-5", "anthropic", "Claude Haiku 4.5", 200_000, general, 1.0, 5.0, "https://docs.anthropic.com/en/docs/about-claude/models/overview"),
            proprietary("google/gemini-3.7-flash", "google_gemini", "Gemini 3.7 Flash", 1_000_000, general + setOf(FabricCapability.COMPUTER_USE, FabricCapability.DOCUMENT_INTELLIGENCE), 0.75, 3.75, "https://ai.google.dev/gemini-api/docs/latest-model"),
            proprietary("google/gemini-3.5-flash", "google_gemini", "Gemini 3.5 Flash", 1_000_000, general, 1.5, 9.0, "https://ai.google.dev/gemini-api/docs/pricing"),
            proprietary("google/gemini-2.5-pro", "google_gemini", "Gemini 2.5 Pro", 1_000_000, general, 1.25, 10.0, "https://ai.google.dev/gemini-api/docs/pricing"),
            ModelDescriptor("local/custom-open-weight", "local", "User-supplied local open-weight model", capabilities = setOf(FabricCapability.GENERAL_LLM, FabricCapability.LOCAL_INFERENCE),
                apiCompatibility = "OPENAI_COMPATIBLE", ownership = ModelOwnership.OPEN_WEIGHT, license = "USER_MUST_VERIFY",
                contentPolicy = ContentPolicyProfile.OPEN_POLICY, status = RegistryStatus.DISCOVERED, trustLevel = TrustLevel.T1_PUBLIC_OPEN,
                evidence = evidence("local://user-configured", "Placeholder identity for an explicitly user-configured local endpoint"))
        )
    }
}
