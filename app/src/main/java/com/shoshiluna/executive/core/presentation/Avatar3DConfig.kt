package com.shoshiluna.executive.core.presentation

import android.content.Context

/**
 * Single source of truth for the two executive digital humans.
 * Android Cloud / CI must place the final rigged GLB files at the exact asset paths below.
 */
enum class ExecutiveAvatar(
    val id: String,
    val displayName: String,
    val modelAssetPath: String,
    val masterVisualAsset: String,
    val spawn: SpatialPosition,
) {
    SHOSHI(
        id = "SHOSHI",
        displayName = "Shoshi",
        modelAssetPath = "models/shoshi.glb",
        masterVisualAsset = "drawable/shoshi_executive.png",
        spawn = SpatialPosition(-0.75f, 0f, 0f),
    ),
    LUNA(
        id = "LUNA",
        displayName = "Luna",
        modelAssetPath = "models/luna.glb",
        masterVisualAsset = "drawable/luna_executive.png",
        spawn = SpatialPosition(0.75f, 0f, 0f),
    )
}

enum class ExecutiveAnimation(val preferredClipNames: List<String>) {
    IDLE(listOf("Idle", "idle", "StandingIdle")),
    TALK(listOf("Talk", "talk", "Talking", "Speak")),
    WALK(listOf("Walk", "walk", "Walking")),
    POINT(listOf("Point", "point", "Pointing")),
    PRESENT(listOf("Present", "present", "Presentation", "GestureExecutive")),
    LISTEN(listOf("Listen", "listen", "Listening")),
    TURN_LEFT(listOf("TurnLeft", "turn_left")),
    TURN_RIGHT(listOf("TurnRight", "turn_right"));
}

data class Avatar3DReadiness(
    val shoshiModelPresent: Boolean,
    val lunaModelPresent: Boolean,
) {
    val fullyReady: Boolean get() = shoshiModelPresent && lunaModelPresent
}

object Avatar3DAssets {
    fun readiness(context: Context): Avatar3DReadiness = Avatar3DReadiness(
        shoshiModelPresent = exists(context, ExecutiveAvatar.SHOSHI.modelAssetPath),
        lunaModelPresent = exists(context, ExecutiveAvatar.LUNA.modelAssetPath),
    )

    private fun exists(context: Context, path: String): Boolean = runCatching {
        context.assets.open(path).use { true }
    }.getOrDefault(false)
}
