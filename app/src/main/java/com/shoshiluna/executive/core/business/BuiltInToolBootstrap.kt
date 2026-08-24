package com.shoshiluna.executive.core.business

object BuiltInToolBootstrap {
    const val ARTIFACT_TOOL_ID = "local.artifact.create"

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
    }
}
