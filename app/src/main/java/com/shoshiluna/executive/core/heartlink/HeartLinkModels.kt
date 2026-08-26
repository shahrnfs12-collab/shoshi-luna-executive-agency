package com.shoshiluna.executive.core.heartlink

import java.util.UUID

enum class HeartLinkRisk { READ_ONLY, REVERSIBLE_WRITE, BUILD, RELEASE, DESTRUCTIVE }
enum class HeartLinkStatus { PROPOSED, APPROVED, RUNNING, VERIFIED, FAILED, ROLLED_BACK }

enum class HeartLinkTool(val risk: HeartLinkRisk) {
    INSPECT_PROJECT(HeartLinkRisk.READ_ONLY),
    SEARCH_CODE(HeartLinkRisk.READ_ONLY),
    READ_FILE(HeartLinkRisk.READ_ONLY),
    READ_RUNTIME_LOGS(HeartLinkRisk.READ_ONLY),
    CREATE_SNAPSHOT(HeartLinkRisk.READ_ONLY),
    PROPOSE_PATCH(HeartLinkRisk.READ_ONLY),
    APPLY_APPROVED_PATCH(HeartLinkRisk.REVERSIBLE_WRITE),
    RUN_TESTS(HeartLinkRisk.BUILD),
    RUN_SECURITY_AUDIT(HeartLinkRisk.BUILD),
    BUILD_APK(HeartLinkRisk.BUILD),
    VERIFY_APK(HeartLinkRisk.READ_ONLY),
    ROLLBACK(HeartLinkRisk.REVERSIBLE_WRITE),
    SIGN_RELEASE(HeartLinkRisk.RELEASE),
    PUBLISH_RELEASE(HeartLinkRisk.RELEASE)
}

data class HeartLinkOperation(
    val tool: HeartLinkTool,
    val target: String,
    val summary: String,
    val expectedSha256: String? = null
)

data class HeartLinkProposal(
    val id: String = UUID.randomUUID().toString(),
    val objective: String,
    val snapshotId: String,
    val operations: List<HeartLinkOperation>,
    val status: HeartLinkStatus = HeartLinkStatus.PROPOSED,
    val approvalRef: String? = null,
    val evidenceIds: List<String> = emptyList(),
    val error: String? = null
)

data class HeartLinkDecision(val allowed: Boolean, val reason: String)
