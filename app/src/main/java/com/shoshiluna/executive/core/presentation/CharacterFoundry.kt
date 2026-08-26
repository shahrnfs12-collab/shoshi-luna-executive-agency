package com.shoshiluna.executive.core.presentation

import java.io.File
import java.util.UUID

enum class FoundryStage { REQUESTED, GENERATED, STRUCTURE_VERIFIED, IDENTITY_APPROVED, DEVICE_VERIFIED, READY, REJECTED }

data class CharacterFoundryRequest(
    val id: String = UUID.randomUUID().toString(),
    val avatar: ExecutiveAvatar,
    val referenceAsset: String = avatar.masterVisualAsset,
    val requiredAnimations: List<String> = Glb3DValidator.REQUIRED_ANIMATIONS,
    val maxTriangles: Int = 80_000,
    val requireHumanoidRig: Boolean = true,
    val requireEmbeddedTextures: Boolean = true
)

data class CharacterFoundryReport(
    val request: CharacterFoundryRequest,
    val stage: FoundryStage,
    val structuralResult: GlbValidationResult? = null,
    val identityApproved: Boolean = false,
    val deviceRuntimeVerified: Boolean = false,
    val notes: List<String> = emptyList()
) {
    val ready: Boolean get() = stage == FoundryStage.READY &&
        structuralResult?.isValid == true && identityApproved && deviceRuntimeVerified
}

/** Strict promotion pipeline: a generated picture or unrigged mesh can never become Spatial 3D READY. */
class CharacterFoundry(private val validator: Glb3DValidator = Glb3DValidator()) {
    fun inspectCandidate(request: CharacterFoundryRequest, glb: File): CharacterFoundryReport {
        val result = validator.validate(glb)
        return if (result.isValid) CharacterFoundryReport(request, FoundryStage.STRUCTURE_VERIFIED, result)
        else CharacterFoundryReport(request, FoundryStage.REJECTED, result, notes = result.errors)
    }

    fun approveIdentity(report: CharacterFoundryReport, approved: Boolean, reviewerNote: String): CharacterFoundryReport {
        require(reviewerNote.isNotBlank()) { "Identity review note is required" }
        require(report.stage == FoundryStage.STRUCTURE_VERIFIED) { "Structure must pass before identity review" }
        return if (approved) report.copy(stage = FoundryStage.IDENTITY_APPROVED, identityApproved = true, notes = report.notes + reviewerNote)
        else report.copy(stage = FoundryStage.REJECTED, notes = report.notes + reviewerNote)
    }

    fun verifyOnDevice(report: CharacterFoundryReport, runtimeEvidence: String): CharacterFoundryReport {
        require(runtimeEvidence.isNotBlank()) { "Runtime evidence is required" }
        require(report.stage == FoundryStage.IDENTITY_APPROVED && report.identityApproved) { "Identity must be approved first" }
        return report.copy(stage = FoundryStage.READY, deviceRuntimeVerified = true, notes = report.notes + runtimeEvidence)
    }
}
