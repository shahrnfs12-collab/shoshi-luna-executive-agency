package com.shoshiluna.executive.core.business

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Mission-scoped truth registry. Success claims must point to evidence when evidence is required. */
object EvidenceRegistry {
    private val _items = MutableStateFlow<Map<String, EvidenceItem>>(emptyMap())
    val items = _items.asStateFlow()

    @Synchronized fun record(category: EvidenceCategory, content: String, source: String, missionId: String? = null, correlationId: String? = null): String {
        require(content.isNotBlank()) { "Evidence content is required" }
        require(source.isNotBlank()) { "Evidence source is required" }
        val item = EvidenceItem(category = category, content = content.trim(), source = source.trim(), missionId = missionId, correlationId = correlationId)
        _items.value = _items.value + (item.id to item)
        return item.id
    }

    @Synchronized fun verify(id: String, evidenceRef: String) {
        require(evidenceRef.isNotBlank()) { "Verification reference is required" }
        val item = _items.value[id] ?: throw IllegalArgumentException("Unknown evidence id: $id")
        _items.value = _items.value + (id to item.copy(category = EvidenceCategory.VERIFIED_RESULT))
    }

    fun getItemsForMission(missionId: String): List<EvidenceItem> = _items.value.values.filter { it.missionId == missionId }
    fun get(id: String): EvidenceItem? = _items.value[id]
    fun exists(id: String): Boolean = _items.value.containsKey(id)
    fun isVerified(id: String): Boolean = _items.value[id]?.category == EvidenceCategory.VERIFIED_RESULT
    @Synchronized fun resetForTest() { _items.value = emptyMap() }
}
