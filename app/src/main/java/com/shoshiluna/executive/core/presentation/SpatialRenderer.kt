package com.shoshiluna.executive.core.presentation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import io.github.sceneview.SceneView
import io.github.sceneview.math.Position
import io.github.sceneview.node.ModelNode

/**
 * Shoshi + Luna real-time 3D renderer.
 *
 * Contract:
 * - Rigged GLB assets are loaded only from app/src/main/assets/models/.
 * - No 2D image is ever reported as a 3D model.
 * - The renderer is presentation-only and has no access to trading, treasury or financial gateways.
 * - Missing/corrupt models fail closed: the caller falls back to the verified photoreal 2D master visual.
 */
@Composable
fun SpatialRenderer(
    modifier: Modifier = Modifier,
    shoshiModelPath: String? = ExecutiveAvatar.SHOSHI.modelAssetPath,
    lunaModelPath: String? = ExecutiveAvatar.LUNA.modelAssetPath,
) {
    val shoshiPos by DigitalHumanController.shoshiPosition.collectAsState()
    val lunaPos by DigitalHumanController.lunaPosition.collectAsState()
    val shoshiAnim by DigitalHumanController.shoshiAnim.collectAsState()
    val lunaAnim by DigitalHumanController.lunaAnim.collectAsState()
    val activeClips = remember { mutableMapOf<String, String>() }
    val shoshiX by animateFloatAsState(shoshiPos.x, tween(1400), label = "shoshi-x")
    val shoshiY by animateFloatAsState(shoshiPos.y, tween(1400), label = "shoshi-y")
    val shoshiZ by animateFloatAsState(shoshiPos.z, tween(1400), label = "shoshi-z")
    val lunaX by animateFloatAsState(lunaPos.x, tween(1400), label = "luna-x")
    val lunaY by animateFloatAsState(lunaPos.y, tween(1400), label = "luna-y")
    val lunaZ by animateFloatAsState(lunaPos.z, tween(1400), label = "luna-z")

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            SceneView(context).apply {
                shoshiModelPath?.let { path ->
                    runCatching {
                        val instance = modelLoader.createModelInstance(path)
                        ModelNode(modelInstance = instance, scaleToUnits = 1.7f, autoAnimate = false).also { node ->
                            node.name = ExecutiveAvatar.SHOSHI.id
                            node.position = Position(shoshiX, shoshiY, shoshiZ)
                            node.isEditable = true
                            animationClip(shoshiAnim).let { clip -> node.playAnimation(clip, loop = true); activeClips[node.name] = clip }
                            addChildNode(node)
                        }
                    }
                }

                lunaModelPath?.let { path ->
                    runCatching {
                        val instance = modelLoader.createModelInstance(path)
                        ModelNode(modelInstance = instance, scaleToUnits = 1.7f, autoAnimate = false).also { node ->
                            node.name = ExecutiveAvatar.LUNA.id
                            node.position = Position(lunaX, lunaY, lunaZ)
                            node.isEditable = true
                            animationClip(lunaAnim).let { clip -> node.playAnimation(clip, loop = true); activeClips[node.name] = clip }
                            addChildNode(node)
                        }
                    }
                }
            }
        },
        update = { view ->
            view.childNodes.forEach { node ->
                if (node is ModelNode) {
                    when (node.name) {
                        ExecutiveAvatar.SHOSHI.id -> {
                            node.position = Position(shoshiX, shoshiY, shoshiZ)
                            switchClip(node, animationClip(shoshiAnim), activeClips)
                        }
                        ExecutiveAvatar.LUNA.id -> {
                            node.position = Position(lunaX, lunaY, lunaZ)
                            switchClip(node, animationClip(lunaAnim), activeClips)
                        }
                    }
                }
            }
        }
    )
}

private fun animationClip(state: AnimationState): String = when (state) {
    AnimationState.IDLE -> "Idle"
    AnimationState.WALK -> "Walk"
    AnimationState.TURN_LEFT -> "TurnLeft"
    AnimationState.TURN_RIGHT -> "TurnRight"
    AnimationState.SPEAK -> "Talk"
    AnimationState.LISTEN -> "Listen"
    AnimationState.GESTURE_EXECUTIVE -> "Present"
}

private fun switchClip(node: ModelNode, clip: String, active: MutableMap<String, String>) {
    if (active[node.name] == clip) return
    active[node.name]?.let(node::stopAnimation)
    node.playAnimation(clip, loop = true)
    active[node.name] = clip
}
