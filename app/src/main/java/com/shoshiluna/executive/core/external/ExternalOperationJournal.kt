package com.shoshiluna.executive.core.external

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONObject

/** Durable fail-closed journal for external contact idempotency and crash windows. */
object ExternalOperationJournal {
    enum class State { EXECUTION_PENDING, VERIFIED, QUARANTINED, FAILED }
    data class Record(
        val idempotencyKey: String,
        val externalAgentId: String,
        val missionId: String,
        val state: State,
        val evidenceId: String? = null,
        val safeBody: String? = null,
        val updatedAt: Long = System.currentTimeMillis()
    )

    @Volatile private var context: Context? = null
    @Volatile private var testMode: Boolean = false
    private val testRecords = mutableMapOf<String, Record>()

    fun initialize(context: Context) {
        this.context = context.applicationContext
        testMode = false
    }

    @Synchronized
    fun get(key: String): Record? {
        if (testMode) return testRecords[key]
        val raw = prefs().getString(key, null) ?: return null
        return runCatching {
            val j = JSONObject(raw)
            Record(
                idempotencyKey = key,
                externalAgentId = j.getString("externalAgentId"),
                missionId = j.getString("missionId"),
                state = State.valueOf(j.getString("state")),
                evidenceId = j.optString("evidenceId").takeIf { it.isNotBlank() },
                safeBody = j.optString("safeBody").takeIf { it.isNotBlank() },
                updatedAt = j.optLong("updatedAt", 0L)
            )
        }.getOrNull()
    }

    @Synchronized
    fun put(record: Record) {
        require(record.idempotencyKey.isNotBlank())
        if (testMode) {
            testRecords[record.idempotencyKey] = record
            return
        }
        val j = JSONObject()
            .put("externalAgentId", record.externalAgentId)
            .put("missionId", record.missionId)
            .put("state", record.state.name)
            .put("evidenceId", record.evidenceId.orEmpty())
            .put("safeBody", record.safeBody.orEmpty())
            .put("updatedAt", record.updatedAt)
        prefs().edit().putString(record.idempotencyKey, j.toString()).apply()
    }

    @Synchronized
    fun resetForTest() {
        testRecords.clear()
        testMode = true
    }

    private fun prefs() = EncryptedSharedPreferences.create(
        requireNotNull(context) { "ExternalOperationJournal not initialized" },
        "external_operation_journal",
        MasterKey.Builder(requireNotNull(context)).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
}
