package com.shoshiluna.executive.core.browser

import com.shoshiluna.executive.core.business.ToolGateway
import com.shoshiluna.executive.core.external.EndpointPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Read-only external browser tool for SHOSHI/LUNA.
 * HTTPS only, no redirects, no cookies, no credential forwarding, no JS execution.
 */
class ExternalBrowserToolAdapter : ToolGateway.ToolAdapter {
    private val client = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    override suspend fun execute(input: Map<String, String>): String = withContext(Dispatchers.IO) {
        val url = input["url"]?.trim().orEmpty()
        require(url.isNotBlank()) { "url is required" }
        EndpointPolicy.validate(url).getOrThrow()

        val request = Request.Builder()
            .url(url)
            .get()
            .header("Accept", "text/html,text/plain,application/json;q=0.9,*/*;q=0.5")
            .header("User-Agent", "ShoshiLuna-ExternalBrowser/1.0")
            .build()

        client.newCall(request).execute().use { response ->
            if (response.code in 300..399) {
                val location = response.header("Location").orEmpty()
                error("Redirect blocked. Request the final HTTPS URL explicitly. Location=${location.take(200)}")
            }
            require(response.isSuccessful) { "HTTP ${response.code}" }
            val contentType = response.header("Content-Type").orEmpty().lowercase()
            require(
                contentType.isBlank() || contentType.startsWith("text/") ||
                    contentType.contains("application/json") || contentType.contains("application/xml")
            ) { "Binary/non-text content is not allowed" }
            val body = response.body?.string().orEmpty()
            require(body.isNotBlank()) { "Empty browser response" }
            body.take(200_000)
        }
    }
}
