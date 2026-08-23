package com.shoshiluna.executive.core.agent

import android.content.Context
import com.shoshiluna.executive.core.business.EvidenceCategory
import com.shoshiluna.executive.core.data.HiveMemoryManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

enum class MemoryPartition {
    SHOSHI_PRIVATE,
    LUNA_PRIVATE,
    EXECUTIVE_SHARED,
    AGENT_WORKSPACE,
    VERIFIED_KNOWLEDGE,
    FAILURE_MEMORY,
    MISSION_SCOPED
}

data class SovereignMemory(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val partition: MemoryPartition,
    val agentId: String,
    val content: String,
    val missionId: String? = null,
    val importance: Float = 0.5f,
    val evidenceCategory: EvidenceCategory = EvidenceCategory.AI_INFERENCE
)

/**
 * ShoshiLuna Sovereign Memory Vault.
 * Partitioned and protected memory storage.
 */
object SovereignMemoryVault {
    private val _memories = MutableStateFlow<List<SovereignMemory>>(emptyList())
    val memories = _memories.asStateFlow()

    private var appContext: Context? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
        val saved = HiveMemoryManager.getObject(context, "executive_memories", Array<SovereignMemory>::class.java)
        if (saved != null) {
            _memories.value = saved.toList()
        }
    }

    private const val MAX_MEMORY_TOTAL = 1000

    fun record(memory: SovereignMemory) {
        val currentList = _memories.value
        
        // Rule: Memory Hygiene - Prevent speculation from overwriting facts
        if (memory.evidenceCategory == EvidenceCategory.FACT) {
            val existingSpeculation = currentList.find { 
                it.partition == memory.partition && 
                it.content == memory.content && 
                it.evidenceCategory != EvidenceCategory.FACT 
            }
            if (existingSpeculation != null) {
                // Fact overwrites speculation
                val next = (currentList.filter { it.id != existingSpeculation.id } + memory).takeLast(MAX_MEMORY_TOTAL)
                _memories.value = next
                persist(next)
                return
            }
        }

        val next = (currentList + memory).takeLast(MAX_MEMORY_TOTAL)
        _memories.value = next
        persist(next)
    }

    private fun persist(memories: List<SovereignMemory>) {
        appContext?.let { ctx ->
            HiveMemoryManager.saveObject(ctx, "executive_memories", memories.toTypedArray())
        }
    }

    /**
     * Explicit Memory Sharing.
     * Creates a copy of a private memory in the EXECUTIVE_SHARED partition.
     */
    fun shareMemory(originAgentId: String, targetAgentId: String, memoryId: String, missionId: String) {
        val original = _memories.value.find { it.id == memoryId && it.agentId == originAgentId } ?: return
        
        val sharedMemory = original.copy(
            id = UUID.randomUUID().toString(),
            partition = MemoryPartition.EXECUTIVE_SHARED,
            timestamp = System.currentTimeMillis(),
            missionId = missionId,
            content = "[SHARED FROM $originAgentId TO $targetAgentId]: ${original.content}"
        )
        
        record(sharedMemory)
    }

    /**
     * Promote a memory from ASSUMPTION/INFERENCE to FACT.
     * Rule: Silently promotion is forbidden. Evidence must be referenced.
     */
    fun promoteToFact(memoryId: String, evidenceRef: String, agentId: String) {
        require(evidenceRef.isNotBlank()) { "evidenceRef is required" }
        val next = _memories.value.map {
            if (it.id == memoryId && it.agentId == agentId &&
                (it.evidenceCategory == EvidenceCategory.ASSUMPTION || it.evidenceCategory == EvidenceCategory.AI_INFERENCE)) {
                it.copy(
                    evidenceCategory = EvidenceCategory.FACT,
                    content = "${it.content} [VERIFIED BY $evidenceRef]",
                    timestamp = System.currentTimeMillis()
                )
            } else it
        }
        _memories.value = next
        persist(next)
    }

    fun getPartition(partition: MemoryPartition, agentId: String): List<SovereignMemory> {
        return _memories.value.filter { it.partition == partition && (it.agentId == agentId || partition == MemoryPartition.EXECUTIVE_SHARED) }
    }
    
    fun getMissionMemory(missionId: String): List<SovereignMemory> {
        return _memories.value.filter { it.missionId == missionId }
    }
}
