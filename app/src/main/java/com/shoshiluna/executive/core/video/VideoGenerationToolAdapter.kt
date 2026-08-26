package com.shoshiluna.executive.core.video

import com.shoshiluna.executive.core.business.ToolGateway
import com.shoshiluna.executive.core.engine.TitanEngine
import java.io.File
import java.net.URI
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Provider-neutral text/image-to-video adapter.
 *
 * Contract for the configured HTTPS endpoint:
 * POST JSON: {prompt, durationSeconds, aspectRatio, imageUrl?}
 * Response may be either:
 *  - {"video_url":"https://...mp4"}
 *  - {"job_id":"...","status_url":"https://..."}
 * A status_url response must eventually expose video_url/result_url or a failed status.
 *
 * This adapter is deliberately fail-closed. It never fabricates a generated video.
 */
class VideoGenerationToolAdapter : ToolGateway.ToolAdapter {
    private val client = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    override suspend fun execute(input: Map<String, String>): String = withContext(Dispatchers.IO) {
        val endpoint = VideoGenerationCredentialStore.endpoint()
            ?: error("Video generation provider is not configured")
        val token = VideoGenerationCredentialStore.token()
            ?: error("Video generation provider credential is missing")
        val prompt = input["prompt"]?.trim()?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("prompt is required")
        val durationSeconds = input["durationSeconds"]?.toIntOrNull()?.coerceIn(1, 60) ?: 8
        val aspectRatio = input["aspectRatio"]?.trim()?.takeIf { it in setOf("9:16", "16:9", "1:1") } ?: "16:9"
        val imageUrl = input["imageUrl"]?.trim()?.takeIf { it.isNotBlank() }

        requirePublicHttps(endpoint)
        imageUrl?.let { requirePublicHttps(it) }

        val payload = JSONObject()
            .put("prompt", prompt)
            .put("durationSeconds", durationSeconds)
            .put("aspectRatio", aspectRatio)
        imageUrl?.let { payload.put("imageUrl", it) }

        val request = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        val initial = client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("Video provider rejected request: HTTP ${response.code}")
            JSONObject(body)
        }

        val videoUrl = initial.optString("video_url").takeIf { it.isNotBlank() }
            ?: pollForResult(initial, token)
        requirePublicHttps(videoUrl)

        val context = TitanEngine.applicationContext() ?: error("Application context unavailable")
        val outputDir = File(context.filesDir, "video-generated").apply { mkdirs() }
        val output = File(outputDir, "generated-${System.currentTimeMillis()}.mp4")

        val download = Request.Builder().url(videoUrl).get().build()
        client.newCall(download).execute().use { response ->
            if (!response.isSuccessful) error("Generated video download failed: HTTP ${response.code}")
            val body = response.body ?: error("Generated video response was empty")
            val contentType = body.contentType()?.toString().orEmpty()
            require(contentType.startsWith("video/") || videoUrl.lowercase().contains(".mp4")) {
                "Provider result is not a video"
            }
            output.outputStream().use { sink -> body.byteStream().copyTo(sink) }
        }

        require(output.length() > 0L) { "Generated video file is empty" }
        val sha256 = sha256(output)
        "output=${output.absolutePath};bytes=${output.length()};sha256=$sha256;prompt=${prompt.take(120)}"
    }

    private suspend fun pollForResult(initial: JSONObject, token: String): String {
        val statusUrl = initial.optString("status_url").takeIf { it.isNotBlank() }
            ?: error("Provider returned neither video_url nor status_url")
        requirePublicHttps(statusUrl)

        repeat(60) {
            val request = Request.Builder()
                .url(statusUrl)
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/json")
                .get()
                .build()
            val status = client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) error("Video provider status failed: HTTP ${response.code}")
                JSONObject(body)
            }
            status.optString("video_url").takeIf { it.isNotBlank() }?.let { return it }
            status.optString("result_url").takeIf { it.isNotBlank() }?.let { return it }
            val state = status.optString("status").lowercase()
            if (state in setOf("failed", "error", "cancelled", "canceled")) {
                error("Video generation failed: ${status.optString("error").ifBlank { state }}")
            }
            delay(2_000L)
        }
        error("Video generation timed out")
    }

    private fun requirePublicHttps(value: String) {
        val uri = runCatching { URI(value) }.getOrElse { throw IllegalArgumentException("Invalid HTTPS URL") }
        require(uri.scheme.equals("https", ignoreCase = true)) { "Only HTTPS URLs are allowed" }
        val host = uri.host?.lowercase() ?: throw IllegalArgumentException("URL host is missing")
        require(host != "localhost" && host != "127.0.0.1" && host != "::1") { "Local addresses are blocked" }
        require(!host.startsWith("10.") && !host.startsWith("192.168.") && !host.startsWith("169.254.")) {
            "Private network addresses are blocked"
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
