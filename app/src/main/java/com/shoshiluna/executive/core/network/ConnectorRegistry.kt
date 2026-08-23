package com.shoshiluna.executive.core.network

import com.shoshiluna.executive.core.business.ToolDefinition
import java.util.UUID

interface ConnectorAdapter {
    val id: String
    val name: String
    suspend fun execute(input: Map<String, String>): String
}

/**
 * ShoshiLuna Connector Registry.
 * Modular adapter system for external tools.
 */
object ConnectorRegistry {
    
    private val adapters = mutableMapOf<String, ConnectorAdapter>()

    fun register(adapter: ConnectorAdapter) {
        adapters[adapter.id] = adapter
    }

    suspend fun callConnector(connectorId: String, input: Map<String, String>): String {
        val adapter = adapters[connectorId] ?: throw IllegalArgumentException("Unknown connector: $connectorId")
        return adapter.execute(input)
    }

    fun getAvailableConnectors(): List<String> = adapters.keys.toList()
}
