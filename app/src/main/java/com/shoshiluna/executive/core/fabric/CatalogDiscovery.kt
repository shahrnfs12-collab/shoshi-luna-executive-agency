package com.shoshiluna.executive.core.fabric

import android.content.Context
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.shoshiluna.executive.core.network.AiProviderId
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class DiscoveryResult(val source: String, val discovered: Int, val accepted: Int, val failed: Int, val error: String? = null)

object PublicCatalogDiscovery {
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()

    fun discoverOpenRouter(context: Context): DiscoveryResult = runCatching {
        SecureCredentialBroker.withCredential(context, SecureCredentialBroker.reference(AiProviderId.OPENROUTER)) { key ->
            val body = execute("https://openrouter.ai/api/v1/models", "Bearer $key")
            val array = JsonParser.parseString(body).asJsonObject.getAsJsonArray("data")
            var accepted = 0; var failed = 0
            array.take(ModelRegistry.MAX_MODELS - ModelRegistry.all().size).forEach { element ->
                runCatching { ModelRegistry.upsert(openRouterModel(element.asJsonObject, body)); accepted++ }.onFailure { failed++ }
            }
            DiscoveryResult("OPENROUTER_OFFICIAL_CATALOG", array.size(), accepted, failed)
        }
    }.getOrElse { DiscoveryResult("OPENROUTER_OFFICIAL_CATALOG", 0, 0, 1, it.message) }

    fun discoverHuggingFace(): DiscoveryResult = runCatching {
        val remaining = (ModelRegistry.MAX_MODELS - ModelRegistry.all().size).coerceAtLeast(0)
        if (remaining == 0) return DiscoveryResult("HUGGING_FACE_PUBLIC_HUB", 0, 0, 0)
        val body = execute("https://huggingface.co/api/models?sort=downloads&direction=-1&limit=$remaining&full=true", null)
        val array = JsonParser.parseString(body).asJsonArray
        var accepted = 0; var failed = 0
        array.take(ModelRegistry.MAX_MODELS - ModelRegistry.all().size).forEach { element ->
            runCatching { ModelRegistry.upsert(huggingFaceModel(element.asJsonObject, body)); accepted++ }.onFailure { failed++ }
        }
        DiscoveryResult("HUGGING_FACE_PUBLIC_HUB", array.size(), accepted, failed)
    }.getOrElse { DiscoveryResult("HUGGING_FACE_PUBLIC_HUB", 0, 0, 1, it.message) }

    private fun execute(url: String, authorization: String?): String {
        val builder = Request.Builder().url(url).header("Accept", "application/json")
        authorization?.let { builder.header("Authorization", it) }
        return client.newCall(builder.build()).execute().use { response ->
            check(response.isSuccessful) { "Catalog HTTP ${response.code}" }
            response.body?.string()?.takeIf { it.isNotBlank() } ?: error("Empty catalog")
        }
    }

    private fun openRouterModel(json: JsonObject, raw: String): ModelDescriptor {
        val id = json.get("id").asString; val architecture = json.getAsJsonObject("architecture")
        val inputs = architecture?.getAsJsonArray("input_modalities")?.mapNotNull { modality(it.asString) }?.toSet().orEmpty().ifEmpty { setOf(Modality.TEXT) }
        val outputs = architecture?.getAsJsonArray("output_modalities")?.mapNotNull { modality(it.asString) }?.toSet().orEmpty().ifEmpty { setOf(Modality.TEXT) }
        val supported = json.getAsJsonArray("supported_parameters")?.map { it.asString }.orEmpty()
        val pricing = json.getAsJsonObject("pricing")
        fun price(field: String) = pricing?.get(field)?.asString?.toDoubleOrNull()?.times(1_000_000)
        val caps = buildSet {
            add(FabricCapability.GENERAL_LLM)
            if (Modality.IMAGE in inputs) add(FabricCapability.VISION)
            if ("tools" in supported || "tool_choice" in supported) add(FabricCapability.TOOL_CALLING)
            if ("response_format" in supported || "structured_outputs" in supported) add(FabricCapability.STRUCTURED_OUTPUT)
            if ((json.get("context_length")?.asInt ?: 0) >= 128_000) add(FabricCapability.LONG_CONTEXT)
        }
        val now = System.currentTimeMillis()
        return ModelDescriptor(id, "openrouter", json.get("name")?.asString ?: id, capabilities = caps,
            contextWindow = json.get("context_length")?.asInt, inputModalities = inputs, outputModalities = outputs,
            toolCalling = FabricCapability.TOOL_CALLING in caps, structuredOutput = FabricCapability.STRUCTURED_OUTPUT in caps,
            cost = CostCard(price("prompt"), price("completion")), apiCompatibility = "OPENAI_CHAT_COMPLETIONS",
            ownership = ModelOwnership.UNKNOWN, status = RegistryStatus.DISCOVERED, trustLevel = TrustLevel.T1_PUBLIC_OPEN,
            evidence = listOf(EvidenceReference("https://openrouter.ai/api/v1/models", "LIVE_OFFICIAL_CATALOG", now, "Observed model record", sha256(raw))))
    }

    private fun huggingFaceModel(json: JsonObject, raw: String): ModelDescriptor {
        val id = json.get("id")?.asString ?: json.get("modelId").asString
        val pipeline = json.get("pipeline_tag")?.asString.orEmpty()
        val caps = when (pipeline) {
            "text-generation" -> setOf(FabricCapability.GENERAL_LLM, FabricCapability.LOCAL_INFERENCE)
            "image-text-to-text", "visual-question-answering" -> setOf(FabricCapability.VISION, FabricCapability.LOCAL_INFERENCE)
            "text-to-image" -> setOf(FabricCapability.IMAGE_GENERATION, FabricCapability.LOCAL_INFERENCE)
            "text-to-video" -> setOf(FabricCapability.VIDEO_GENERATION, FabricCapability.LOCAL_INFERENCE)
            "automatic-speech-recognition" -> setOf(FabricCapability.SPEECH_TO_TEXT, FabricCapability.LOCAL_INFERENCE)
            "text-to-speech" -> setOf(FabricCapability.TEXT_TO_SPEECH, FabricCapability.LOCAL_INFERENCE)
            "feature-extraction" -> setOf(FabricCapability.EMBEDDINGS, FabricCapability.LOCAL_INFERENCE)
            else -> setOf(FabricCapability.LOCAL_INFERENCE)
        }
        val modalities = buildSet { add(Modality.TEXT); if (FabricCapability.VISION in caps || FabricCapability.IMAGE_GENERATION in caps) add(Modality.IMAGE); if (FabricCapability.SPEECH_TO_TEXT in caps || FabricCapability.TEXT_TO_SPEECH in caps) add(Modality.AUDIO) }
        val license = json.getAsJsonObject("cardData")?.get("license")?.asString
        val now = System.currentTimeMillis()
        return ModelDescriptor("hf/$id", "huggingface", id, capabilities = caps, inputModalities = modalities,
            apiCompatibility = "HUGGING_FACE_HUB_OR_LOCAL", ownership = ModelOwnership.OPEN_WEIGHT, license = license,
            status = RegistryStatus.DISCOVERED, trustLevel = TrustLevel.T1_PUBLIC_OPEN, contentPolicy = ContentPolicyProfile.UNKNOWN,
            evidence = listOf(EvidenceReference("https://huggingface.co/api/models", "LIVE_PUBLIC_CATALOG", now, "Observed Hub model metadata", sha256(raw))))
    }

    private fun modality(value: String): Modality? = when (value.lowercase()) { "text" -> Modality.TEXT; "image" -> Modality.IMAGE; "audio" -> Modality.AUDIO; "video" -> Modality.VIDEO; "file" -> Modality.FILE; "embeddings" -> Modality.EMBEDDING; else -> null }
    private fun sha256(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}
