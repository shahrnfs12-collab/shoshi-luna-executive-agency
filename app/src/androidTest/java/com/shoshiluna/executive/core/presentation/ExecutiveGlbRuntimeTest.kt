package com.shoshiluna.executive.core.presentation

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.sceneview.SceneView
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExecutiveGlbRuntimeTest {
    private val required = Glb3DValidator.REQUIRED_ANIMATIONS.toSet()

    @Test
    fun bothExecutiveAssetsPassStructuralGateAndLoadInSceneView() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        listOf("models/shoshi.glb", "models/luna.glb").forEach { assetPath ->
            val local = File(context.cacheDir, assetPath.substringAfterLast('/'))
            context.assets.open(assetPath).use { input -> local.outputStream().use(input::copyTo) }

            val validation = Glb3DValidator().validate(local)
            assertTrue("$assetPath: ${validation.errors}", validation.isValid)
            assertEquals(required, validation.animationNames.toSet())
            assertTrue(validation.uniqueJointCount >= 15)

            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val sceneView = SceneView(context)
                assertNotNull("SceneView could not load $assetPath", sceneView.modelLoader.createModelInstance(assetPath))
                sceneView.destroy()
            }
        }
    }
}
