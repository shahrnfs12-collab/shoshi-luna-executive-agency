package com.shoshiluna.executive.core.external

interface ExternalAgentAdapter {
    val protocol: ExternalProtocol
    suspend fun handshake(agent: ExternalAgentDescriptor): Result<ExternalHandshakeResult>
    suspend fun send(agent: ExternalAgentDescriptor, message: ExternalAgentMessage): Result<String>
}
