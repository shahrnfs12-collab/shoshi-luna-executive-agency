package com.shoshiluna.executive.core.hardening

import java.util.concurrent.ConcurrentHashMap

/** Process-local duplicate suppression. Persistent idempotency is still a release gate. */
object RequestDeduplicator {
    private val seen = ConcurrentHashMap<String, Long>()
    private const val MAX_AGE_MS = 24 * 60 * 60 * 1000L

    fun claim(key: String, now: Long = System.currentTimeMillis()): Boolean {
        require(key.isNotBlank())
        cleanup(now)
        return seen.putIfAbsent(key, now) == null
    }

    fun release(key: String) { seen.remove(key) }
    fun resetForTest() { seen.clear() }
    private fun cleanup(now: Long) { seen.entries.removeIf { now - it.value > MAX_AGE_MS } }
}
