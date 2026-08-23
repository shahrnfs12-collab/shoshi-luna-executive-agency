package com.shoshiluna.executive.core.external

/** Provider-neutral contracts. No provider is reported READY until a real implementation is registered and runtime-verified. */
interface ResearchConnector {
    suspend fun search(query: String, maxResults: Int = 10): Result<List<ResearchResult>>
}

data class ResearchResult(val title: String, val summary: String, val sourceUrl: String, val retrievedAt: Long = System.currentTimeMillis())

interface CrmConnector {
    suspend fun upsertLead(lead: CrmLead): Result<String>
    suspend fun updateStage(externalLeadId: String, stage: String): Result<Unit>
}

data class CrmLead(val localLeadId: String, val name: String, val company: String?, val contact: String?, val notes: String?)

interface EmailConnector {
    suspend fun send(message: EmailMessage): Result<String>
}

data class EmailMessage(val to: String, val subject: String, val body: String, val idempotencyKey: String)
