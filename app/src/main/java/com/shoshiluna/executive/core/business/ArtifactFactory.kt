package com.shoshiluna.executive.core.business

import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

enum class ArtifactType {
    PROPOSAL,
    REPORT,
    BUSINESS_DOCUMENT,
    OFFER,
    RESEARCH_PACKAGE,
    CLIENT_DELIVERABLE,
    SUMMARY,
    PLAN,
    DATA_EXPORT
}

data class Artifact(
    val id: String = UUID.randomUUID().toString(),
    val type: ArtifactType,
    val creatorAgentId: String,
    val sourceTaskId: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val version: Int = 1,
    val hash: String,
    val status: String = "GENERATED"
)

/**
 * Auditable deliverable factory.
 * The integrity hash is a real SHA-256 digest of the canonical artifact payload.
 */
object ArtifactFactory {
    private val artifactStore = CopyOnWriteArrayList<Artifact>()
    @Volatile private var appContext: Context? = null
    private val gson = Gson()

    val artifacts: List<Artifact>
        get() = artifactStore.toList()

    @Synchronized
    fun initialize(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        val json = prefs().getString(KEY_ARTIFACTS, null) ?: return
        val type = object : TypeToken<List<Artifact>>() {}.type
        runCatching { gson.fromJson<List<Artifact>>(json, type) }.getOrNull()?.let {
            artifactStore.clear(); artifactStore.addAll(it)
        }
    }

    fun createArtifact(
        type: ArtifactType,
        agentId: String,
        taskId: String,
        content: String
    ): Artifact {
        require(agentId.isNotBlank()) { "creatorAgentId is required" }
        require(taskId.isNotBlank()) { "sourceTaskId is required" }
        require(content.isNotBlank()) { "Artifact content cannot be blank" }

        val id = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()
        val version = 1
        val canonicalPayload = listOf(
            id,
            type.name,
            agentId.trim(),
            taskId.trim(),
            content,
            timestamp.toString(),
            version.toString()
        ).joinToString("\u001F")

        val artifact = Artifact(
            id = id,
            type = type,
            creatorAgentId = agentId.trim(),
            sourceTaskId = taskId.trim(),
            content = content,
            timestamp = timestamp,
            version = version,
            hash = sha256(canonicalPayload)
        )
        artifactStore.add(artifact)
        persist()
        return artifact
    }

    fun verifyIntegrity(artifact: Artifact): Boolean {
        val canonicalPayload = listOf(
            artifact.id,
            artifact.type.name,
            artifact.creatorAgentId,
            artifact.sourceTaskId,
            artifact.content,
            artifact.timestamp.toString(),
            artifact.version.toString()
        ).joinToString("\u001F")
        return artifact.hash == sha256(canonicalPayload)
    }

    internal fun resetForTest() = artifactStore.clear()

    private fun persist() {
        val context = appContext ?: return
        check(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_ARTIFACTS, gson.toJson(artifactStore.toList())).commit()) {
            "Artifact persistence failed"
        }
    }

    private fun prefs() = requireNotNull(appContext).getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun sha256(value: String): String = MessageDigest
        .getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private const val PREFS_NAME = "agency_artifacts_v1"
    private const val KEY_ARTIFACTS = "artifacts"
}
