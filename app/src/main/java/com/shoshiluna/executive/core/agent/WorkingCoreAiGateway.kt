package com.shoshiluna.executive.core.agent

import com.shoshiluna.executive.core.network.AiCallResult

interface WorkingCoreAiGateway {
    suspend fun send(agent: ExecutiveAgent, prompt: String): AiCallResult

    /**
     * Mission-scoped variant. The default keeps compatibility with lightweight test gateways,
     * while the production gateway propagates the mission id into the provider system prompt.
     * That propagation is a truth boundary: no-API local continuity must never be mistaken for
     * provider-backed autonomous mission reasoning.
     */
    suspend fun sendMission(agent: ExecutiveAgent, prompt: String, missionId: String): AiCallResult =
        send(agent, prompt)
}

class DefaultWorkingCoreAiGateway(
    private val provider: AiProvider = OpenAiProvider()
) : WorkingCoreAiGateway {
    override suspend fun send(agent: ExecutiveAgent, prompt: String): AiCallResult =
        sendInternal(agent, prompt, missionId = null)

    override suspend fun sendMission(agent: ExecutiveAgent, prompt: String, missionId: String): AiCallResult =
        sendInternal(agent, prompt, missionId)

    private suspend fun sendInternal(agent: ExecutiveAgent, prompt: String, missionId: String?): AiCallResult {
        val privatePartition = if (agent == ExecutiveAgent.SHOSHI) MemoryPartition.SHOSHI_PRIVATE else MemoryPartition.LUNA_PRIVATE
        val context = (
            SovereignMemoryVault.getPartition(privatePartition, agent.id).takeLast(12) +
                SovereignMemoryVault.getPartition(MemoryPartition.EXECUTIVE_SHARED, agent.id).takeLast(12) +
                SovereignMemoryVault.getPartition(MemoryPartition.VERIFIED_KNOWLEDGE, agent.id).takeLast(8)
            ).distinctBy { it.id }.sortedBy { it.timestamp }.takeLast(24)
        return provider.chatResult(
            prompt = prompt,
            context = context,
            missionId = missionId,
            agent = agent
        )
    }
}
