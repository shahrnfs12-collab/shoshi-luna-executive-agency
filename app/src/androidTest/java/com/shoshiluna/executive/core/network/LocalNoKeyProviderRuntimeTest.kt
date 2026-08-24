package com.shoshiluna.executive.core.network

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shoshiluna.executive.core.security.ApiKeyManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalNoKeyProviderRuntimeTest {
    @Test
    fun localOpenAiCompatibleEngineIsConfiguredWithoutFakeCredential() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        try {
            ApiKeyManager.saveLocalProvider(
                context,
                "http://10.0.2.2:11434/v1/chat/completions",
                "llama3.2"
            )

            assertTrue(ApiKeyManager.hasProviderAccess(context, AiProviderId.LOCAL_OPENAI))
            assertFalse(ApiKeyManager.hasProviderKey(context, AiProviderId.LOCAL_OPENAI))
            assertEquals("llama3.2", ApiKeyManager.localProtocol(context).defaultModel)
        } finally {
            ApiKeyManager.clearProviderKey(context, AiProviderId.LOCAL_OPENAI)
        }
    }
}
