package com.shoshiluna.executive.core.agent

import com.shoshiluna.executive.core.network.AiCallResult

interface WorkingCoreAiGateway {
    suspend fun send(agent: ExecutiveAgent, prompt: String): AiCallResult
}

class DefaultWorkingCoreAiGateway(
    private val provider: AiProvider = OpenAiProvider()
) : WorkingCoreAiGateway {
    override suspend fun send(agent: ExecutiveAgent, prompt: String): AiCallResult {
        val privatePartition = if (agent == ExecutiveAgent.SHOSHI) MemoryPartition.SHOSHI_PRIVATE else MemoryPartition.LUNA_PRIVATE
        val context = (
            SovereignMemoryVault.getPartition(privatePartition, agent.id).takeLast(12) +
                SovereignMemoryVault.getPartition(MemoryPartition.EXECUTIVE_SHARED, agent.id).takeLast(12) +
                SovereignMemoryVault.getPartition(MemoryPartition.VERIFIED_KNOWLEDGE, agent.id).takeLast(8)
            ).distinctBy { it.id }.sortedBy { it.timestamp }.takeLast(24)
        return provider.chatResult(
            prompt = prompt,
            context = context,
            missionId = null,
            agent = agent
        )
    }
}
