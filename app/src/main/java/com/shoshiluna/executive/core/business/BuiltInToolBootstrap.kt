package com.shoshiluna.executive.core.business

import com.shoshiluna.executive.core.browser.ExternalBrowserToolAdapter
import com.shoshiluna.executive.core.external.ExternalAgentToolAdapter
import com.shoshiluna.executive.core.network.ConnectorRegistry

object BuiltInToolBootstrap {
    const val ARTIFACT_TOOL_ID = "local.artifact.create"
    const val GITHUB_TOOL_ID = "github.repository"
    const val EXTERNAL_BROWSER_TOOL_ID = "external.browser.read"
    const val EXTERNAL_AGENT_TOOL_ID = "external.agent.contact"

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

        ToolGateway.registerTool(
            ToolDefinition(
                id = EXTERNAL_BROWSER_TOOL_ID,
                name = "Read external HTTPS web page",
                riskClass = ToolRiskClass.READ_ONLY,
                requiresUserApproval = false,
                timeoutMs = 35_000L
            ),
            ExternalBrowserToolAdapter()
        )

        ToolGateway.registerTool(
            ToolDefinition(
                id = EXTERNAL_AGENT_TOOL_ID,
                name = "Contact trusted external AI agent",
                riskClass = ToolRiskClass.EXTERNAL_COMMUNICATION,
                requiresUserApproval = true,
                timeoutMs = 45_000L
            ),
            ExternalAgentToolAdapter()
        )
    }
}
