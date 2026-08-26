package com.shoshiluna.executive.core.hardening

enum class CapabilityStatus { READY, DEGRADED, LOCKED, MISSING, SIMULATION, DEFERRED }

data class CapabilityState(
    val id: String,
    val status: CapabilityStatus,
    val detail: String,
    val evidenceRequired: Boolean = true
)

/** Single source of truth. UI and agents must not claim capability readiness independently. */
object CapabilityRegistry {
    private val states = linkedMapOf(
        "mission-control" to CapabilityState("mission-control", CapabilityStatus.READY, "Local mission lifecycle with transition policy"),
        "council" to CapabilityState("council", CapabilityStatus.READY, "Consensus engine present; runtime tests required"),
        "memory" to CapabilityState("memory", CapabilityStatus.DEGRADED, "Local memory exists; crash-safe persistence remains release-gated"),
        "ai" to CapabilityState("ai", CapabilityStatus.LOCKED, "Requires configured provider and live connectivity test"),
        "ai-transport-security" to CapabilityState("ai-transport-security", CapabilityStatus.DEGRADED, "Direct API key mode is for local/device testing; production release requires a server-side AI relay so provider secrets are never shipped in the app"),
        "external-tools" to CapabilityState("external-tools", CapabilityStatus.LOCKED, "Requires registered real adapter, permission and evidence"),
        "external-agents" to CapabilityState("external-agents", CapabilityStatus.LOCKED, "Trusted registry + approval + quarantine gateway implemented; requires a configured runtime adapter and verified remote agent"),
        "external-research" to CapabilityState("external-research", CapabilityStatus.LOCKED, "Provider-neutral research connector contract exists; no real provider configured"),
        "external-crm" to CapabilityState("external-crm", CapabilityStatus.LOCKED, "Provider-neutral CRM connector contract exists; no real provider configured"),
        "external-email" to CapabilityState("external-email", CapabilityStatus.LOCKED, "Provider-neutral email connector contract exists; no real provider configured"),
        "voice-tts" to CapabilityState("voice-tts", CapabilityStatus.DEGRADED, "Android TTS code path exists; device runtime verification required"),
        "voice-stt" to CapabilityState("voice-stt", CapabilityStatus.DEGRADED, "SpeechRecognizer + permission flow implemented; device runtime verification required"),
        "3d-shoshi" to CapabilityState("3d-shoshi", CapabilityStatus.MISSING, "shoshi.glb is not packaged; identity, rig, animation and device runtime gates remain open"),
        "3d-luna" to CapabilityState("3d-luna", CapabilityStatus.MISSING, "luna.glb is not packaged; identity, rig, animation and device runtime gates remain open"),
        "business-pipeline" to CapabilityState("business-pipeline", CapabilityStatus.SIMULATION, "Business logic is simulation/evidence mode until real connectors are proven"),
        "financial-actions" to CapabilityState("financial-actions", CapabilityStatus.LOCKED, "REAL execution requires verified biometric/Keystore approval plus configured external adapter"),
        "financial-ledger" to CapabilityState("financial-ledger", CapabilityStatus.DEGRADED, "Room double-entry/idempotency code present; Android Room integration test required"),
        "glb-validator" to CapabilityState("glb-validator", CapabilityStatus.DEGRADED, "Strict structural GLB gate present; real assets and renderer validation required"),
        "heartlink" to CapabilityState("heartlink", CapabilityStatus.LOCKED, "Private MCP tool contract present; tunnel identity and live bridge verification required"),
        "digital-heart-twin" to CapabilityState("digital-heart-twin", CapabilityStatus.READY, "Local capability/evidence topology snapshot available"),
        "3d-character-foundry" to CapabilityState("3d-character-foundry", CapabilityStatus.LOCKED, "Strict candidate pipeline present; requires a real 3D generator and approved GLB outputs")
    )

    @Synchronized fun snapshot(): List<CapabilityState> = states.values.toList()
    @Synchronized fun get(id: String): CapabilityState = states[id] ?: CapabilityState(id, CapabilityStatus.MISSING, "Unregistered capability")
    @Synchronized fun update(id: String, status: CapabilityStatus, detail: String) {
        require(detail.isNotBlank())
        val existing = states[id]
        states[id] = CapabilityState(id, status, detail.trim(), existing?.evidenceRequired ?: true)
    }
    fun canExecute(id: String): Boolean = get(id).status == CapabilityStatus.READY
}
