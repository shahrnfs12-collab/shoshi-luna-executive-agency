package com.shoshiluna.executive.core.fabric

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ModelRegistryPersistenceRuntimeTest {
    @Test
    fun registryWritesItsCatalogToDurableAppStorage() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        ModelRegistry.initialize(context)
        val seed = requireNotNull(ModelRegistry.get("openai/gpt-5.6-luna"))

        ModelRegistry.upsert(seed)

        val stored = context.getSharedPreferences("ai_model_registry_v1", 0)
            .getString("models", null)
        assertTrue(!stored.isNullOrBlank())
        assertTrue(stored!!.contains("openai/gpt-5.6-luna"))
    }
}
