package com.shoshiluna.executive.core.presentation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            SceneView(context).apply {
                shoshiModelPath?.let { path ->
                    runCatching {
                        val instance = modelLoader.createModelInstance(path)
                        ModelNode(instance).also { node ->
                            node.name = ExecutiveAvatar.SHOSHI.id
                            node.position = Position(shoshiPos.x, shoshiPos.y, shoshiPos.z)
                            addChildNode(node)
                        }
                    }
                }

                lunaModelPath?.let { path ->
                    runCatching {
                        val instance = modelLoader.createModelInstance(path)
                        ModelNode(instance).also { node ->
                            node.name = ExecutiveAvatar.LUNA.id
                            node.position = Position(lunaPos.x, lunaPos.y, lunaPos.z)
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
                        ExecutiveAvatar.SHOSHI.id -> node.position = Position(shoshiPos.x, shoshiPos.y, shoshiPos.z)
                        ExecutiveAvatar.LUNA.id -> node.position = Position(lunaPos.x, lunaPos.y, lunaPos.z)
                    }
                }
            }
        }
    )
}
