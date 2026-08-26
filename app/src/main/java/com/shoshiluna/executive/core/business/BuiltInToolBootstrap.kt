package com.shoshiluna.executive.core.business

import com.shoshiluna.executive.core.browser.ExternalBrowserToolAdapter
import com.shoshiluna.executive.core.external.ExternalAgentToolAdapter
import com.shoshiluna.executive.core.network.ConnectorRegistry
import com.shoshiluna.executive.core.video.VideoGenerationToolAdapter
import com.shoshiluna.executive.core.video.VideoWorkspace

object BuiltInToolBootstrap {
    const val ARTIFACT_TOOL_ID = "local.artifact.create"
    const val GITHUB_TOOL_ID = "github.repository"
    const val EXTERNAL_BROWSER_TOOL_ID = "external.browser.read"
    const val EXTERNAL_AGENT_TOOL_ID = "external.agent.contact"
    const val VIDEO_BUILD_TOOL_ID = "local.video.build"
    const val VIDEO_GENERATE_TOOL_ID = "external.video.generate"

    fun initialize() {
        ToolGateway.registerTool(
            ToolDefinition(ARTIFACT_TOOL_ID, "Create local auditable deliverable", ToolRiskClass.LOW_RISK_WRITE, false),
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
            ToolDefinition(GITHUB_TOOL_ID, "GitHub Repository & CI", ToolRiskClass.READ_ONLY, false),
            ToolGateway.ToolAdapter { input -> ConnectorRegistry.callConnector("github", input) }
        )

        ToolGateway.registerTool(
            ToolDefinition(EXTERNAL_BROWSER_TOOL_ID, "Read external HTTPS web page", ToolRiskClass.READ_ONLY, false, 35_000L),
            ExternalBrowserToolAdapter()
        )

        ToolGateway.registerTool(
            ToolDefinition(EXTERNAL_AGENT_TOOL_ID, "Contact trusted external AI agent", ToolRiskClass.EXTERNAL_COMMUNICATION, true, 45_000L),
            ExternalAgentToolAdapter()
        )

        ToolGateway.registerTool(
            ToolDefinition(
                id = VIDEO_BUILD_TOOL_ID,
                name = "Build local MP4 clip from uploaded video",
                riskClass = ToolRiskClass.LOW_RISK_WRITE,
                requiresUserApproval = false,
                timeoutMs = 120_000L
            ),
            ToolGateway.ToolAdapter { input ->
                val assetId = input["assetId"]?.takeIf { it.isNotBlank() }
                    ?: throw IllegalArgumentException("assetId is required")
                val startMs = input["startMs"]?.toLongOrNull() ?: 0L
                val endMs = input["endMs"]?.toLongOrNull() ?: 0L
                val result = VideoWorkspace.buildTrimmedMp4(assetId, startMs, endMs)
                "output=${result.outputPath};bytes=${result.bytes};sourceStartUs=${result.startedAtUs};sourceEndUs=${result.endedAtUs}"
            }
        )

        ToolGateway.registerTool(
            ToolDefinition(
                id = VIDEO_GENERATE_TOOL_ID,
                name = "Generate new video from text or image prompt",
                riskClass = ToolRiskClass.EXTERNAL_COMMUNICATION,
                requiresUserApproval = true,
                timeoutMs = 180_000L
            ),
            VideoGenerationToolAdapter()
        )
    }
}
