package com.shoshiluna.executive.core.data

import android.content.Context
import com.shoshiluna.executive.core.agent.SovereignMemory
import com.shoshiluna.executive.core.agent.SovereignMemoryVault
import com.google.gson.Gson

/** Local crash-recovery snapshot for non-secret agency memory. */
object BackupManager {
    private const val KEY = "agency_backup_snapshot_v1"
    private val gson = Gson()

    fun createBackup(context: Context): Boolean = runCatching {
        val snapshot = BackupSnapshot(
            createdAt = System.currentTimeMillis(),
            memories = SovereignMemoryVault.memories.value
        )
        HiveMemoryManager.saveString(context, KEY, gson.toJson(snapshot))
        true
    }.getOrDefault(false)

    fun readBackup(context: Context): BackupSnapshot? = runCatching {
        val json = HiveMemoryManager.getString(context, KEY, "")
        if (json.isBlank()) null else gson.fromJson(json, BackupSnapshot::class.java)
    }.getOrNull()

    fun clearBackup(context: Context) {
        HiveMemoryManager.saveString(context, KEY, "")
    }
}

data class BackupSnapshot(
    val createdAt: Long,
    val memories: List<SovereignMemory>
)
