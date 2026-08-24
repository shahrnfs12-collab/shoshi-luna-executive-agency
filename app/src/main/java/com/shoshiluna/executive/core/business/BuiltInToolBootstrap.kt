package com.shoshiluna.executive.core.business

import com.shoshiluna.executive.core.network.ConnectorRegistry

object BuiltInToolBootstrap {
    const val ARTIFACT_TOOL_ID = "local.artifact.create"
    const val GITHUB_TOOL_ID = "github.repository"

    fun initialize() {
        ToolGateway.registerTool(
            ToolDefinition(
                id = ARTIFACT_TOOL_ID,
                name = "Create local auditable deliverable",
                riskClass = ToolRiskClass.LOW_RISK_WRITE,
                requiresUserApproval = false
            ),
            ToolGateway.ToolAdapter { input ->
                val type = input["type"]?.let { runCatching { ArtifactType.valueOf(it) }.getOrNull() } ?: ArtifactType.REPORT
                val artifact = ArtifactFactory.createArtifact(
                    type = type,
                    agentId = input["agentId"].orEmpty(),
                    taskId = input["taskId"].orEmpty(),
                    content = input["content"].orEmpty()
                )
                "artifactId=${artifact.id};sha256=${artifact.hash}"
            }
        )

        // The GitHub connector is exposed as a real runtime tool. Read operations can
        // execute directly; write operations remain approval-gated inside the adapter.
        ToolGateway.registerTool(
            ToolDefinition(
                id = GITHUB_TOOL_ID,
                name = "GitHub Repository & CI",
                riskClass = ToolRiskClass.READ_ONLY,
                requiresUserApproval = false
            ),
            ToolGateway.ToolAdapter { input ->
                ConnectorRegistry.callConnector("github", input)
            }
        )
    }
}
