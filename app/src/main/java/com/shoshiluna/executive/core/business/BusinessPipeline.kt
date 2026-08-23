package com.shoshiluna.executive.core.business

import java.util.UUID

enum class PipelineStage {
    CAPABILITY_DISCOVERY,
    PRODUCTIZATION,
    MARKET_RESEARCH,
    OPPORTUNITY_DISCOVERY,
    LEAD_DISCOVERY,
    QUALIFICATION,
    OFFER_CREATION,
    HUMAN_APPROVAL,
    DELIVERY,
    PAYMENT_VERIFICATION,
    REVENUE_LEDGER,
    TREASURY_ALLOCATION
}

enum class PipelineStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    FAILED,
    WAITING_FOR_USER,
    SKIPPED
}

data class PipelineTask(
    val id: String = UUID.randomUUID().toString(),
    val stage: PipelineStage,
    val agentId: String,
    val description: String,
    val inputData: Map<String, Any> = emptyMap(),
    val outputData: Map<String, Any>? = null,
    val status: PipelineStatus = PipelineStatus.PENDING,
    val dependencies: List<String> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)

data class PipelineState(
    val id: String = UUID.randomUUID().toString(),
    val missionId: String,
    val currentStage: PipelineStage = PipelineStage.CAPABILITY_DISCOVERY,
    val tasks: List<PipelineTask> = emptyList(),
    val isHalted: Boolean = false
)
