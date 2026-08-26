package com.shoshiluna.executive.core.github

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.shoshiluna.executive.core.network.ConnectorAdapter
import com.shoshiluna.executive.core.security.HumanApprovalGateway
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * Narrow GitHub REST adapter for the owner's configured repository.
 * Authentication is configured once and reused from encrypted device storage.
 * The adapter never returns or logs the credential.
 */
class GitHubConnectorAdapter : ConnectorAdapter {
    override val id: String = "github"
    override val name: String = "GitHub Repository & CI"

    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    override suspend fun execute(input: Map<String, String>): String {
        val token = GitHubCredentialStore.token()
            ?: return failure("GITHUB_NOT_CONFIGURED", "GitHub token is not configured on this device.")
        val repo = GitHubCredentialStore.repository()
            ?: return failure("GITHUB_NOT_CONFIGURED", "GitHub repository is not configured on this device.")

        return when (input["operation"]?.trim()?.lowercase()) {
            "repo" -> call("GET", "/repos/$repo", token)
            "read_file" -> {
                val path = requireInput(input, "path")
                val ref = input["ref"]?.takeIf { it.isNotBlank() }
                val suffix = if (ref != null) "?ref=${urlEncode(ref)}" else ""
                call("GET", "/repos/$repo/contents/${encodePath(path)}$suffix", token)
            }
            "get_branch" -> {
                val branch = requireInput(input, "branch")
                call("GET", "/repos/$repo/branches/${urlEncode(branch)}", token)
            }
            "workflow_runs" -> {
                val branch = input["branch"]?.takeIf { it.isNotBlank() }
                val suffix = branch?.let { "?branch=${urlEncode(it)}&per_page=20" } ?: "?per_page=20"
                call("GET", "/repos/$repo/actions/runs$suffix", token)
            }
            "run_artifacts" -> {
                val runId = requireInput(input, "run_id")
                call("GET", "/repos/$repo/actions/runs/${urlEncode(runId)}/artifacts", token)
            }
            "create_branch" -> {
                requireApproved(input, "create GitHub branch")
                val branch = requireInput(input, "branch")
                val sourceSha = requireInput(input, "source_sha")
                val body = gson.toJson(mapOf("ref" to "refs/heads/$branch", "sha" to sourceSha))
                call("POST", "/repos/$repo/git/refs", token, body)
            }
            "upsert_file" -> {
                requireApproved(input, "commit file to GitHub")
                val path = requireInput(input, "path")
                val message = requireInput(input, "message")
                val content = requireInput(input, "content")
                val branch = requireInput(input, "branch")
                val sha = input["sha"]?.takeIf { it.isNotBlank() }
                val payload = linkedMapOf<String, String>(
                    "message" to message,
                    "content" to Base64.getEncoder().encodeToString(content.toByteArray(Charsets.UTF_8)),
                    "branch" to branch
                )
                if (sha != null) payload["sha"] = sha
                call("PUT", "/repos/$repo/contents/${encodePath(path)}", token, gson.toJson(payload))
            }
            "create_pr" -> {
                requireApproved(input, "open GitHub pull request")
                val title = requireInput(input, "title")
                val head = requireInput(input, "head")
                val base = input["base"]?.takeIf { it.isNotBlank() } ?: "main"
                val bodyText = input["body"].orEmpty()
                val body = gson.toJson(mapOf("title" to title, "head" to head, "base" to base, "body" to bodyText))
                call("POST", "/repos/$repo/pulls", token, body)
            }
            "rerun_failed" -> {
                requireApproved(input, "re-run failed GitHub Actions jobs")
                val runId = requireInput(input, "run_id")
                call("POST", "/repos/$repo/actions/runs/${urlEncode(runId)}/rerun-failed-jobs", token, "{}")
            }
            else -> failure(
                "UNKNOWN_GITHUB_OPERATION",
                "Supported operations: repo, read_file, get_branch, workflow_runs, run_artifacts, create_branch, upsert_file, create_pr, rerun_failed."
            )
        }
    }

    private fun requireApproved(input: Map<String, String>, action: String) {
        val approvalId = input["approval_id"]?.takeIf { it.isNotBlank() }
            ?: throw SecurityException("Human approval is required to $action")
        check(HumanApprovalGateway.isApproved(approvalId)) { "Human approval is not granted for $action" }
    }

    private fun requireInput(input: Map<String, String>, key: String): String =
        input[key]?.takeIf { it.isNotBlank() } ?: throw IllegalArgumentException("Missing GitHub input: $key")

    private fun call(method: String, apiPath: String, token: String, body: String? = null): String {
        require(apiPath.startsWith("/repos/")) { "Only repository-scoped GitHub API paths are allowed" }
        val builder = Request.Builder()
            .url("https://api.github.com$apiPath")
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "ShoshiLuna-Executive-Agent")

        val requestBody = body?.toRequestBody(jsonMediaType)
        when (method) {
            "GET" -> builder.get()
            "POST" -> builder.post(requestBody ?: "{}".toRequestBody(jsonMediaType))
            "PUT" -> builder.put(requestBody ?: "{}".toRequestBody(jsonMediaType))
            else -> error("Unsupported GitHub method: $method")
        }

        try {
            client.newCall(builder.build()).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val safe = safeGitHubError(response.code, responseBody)
                    throw IllegalStateException(safe)
                }
                if (response.code == 204) return success("GitHub operation completed.")
                return responseBody.ifBlank { success("GitHub operation completed.") }
            }
        } catch (error: SocketTimeoutException) {
            throw IllegalStateException("GitHub connection timed out. Check internet access and try again.", error)
        } catch (error: IOException) {
            throw IllegalStateException("GitHub network connection failed. Check internet, DNS or VPN and try again.", error)
        }
    }

    private fun safeGitHubError(code: Int, rawBody: String): String {
        val message = runCatching {
            gson.fromJson(rawBody, JsonObject::class.java).get("message")?.asString
        }.getOrNull()?.takeIf { it.isNotBlank() }
        return when (code) {
            401 -> "GitHub rejected the token (HTTP 401). The token may be expired or invalid."
            403 -> "GitHub denied access (HTTP 403). Check the token repository permissions and Actions permissions."
            404 -> "GitHub repository was not found or the token has no access to it (HTTP 404). Check owner/name and repository access."
            429 -> "GitHub rate limit reached (HTTP 429). Try again later."
            in 500..599 -> "GitHub service error (HTTP $code). Try again later."
            else -> "GitHub request failed (HTTP $code)${message?.let { ": $it" }.orEmpty()}"
        }
    }

    private fun success(message: String): String = gson.toJson(mapOf("ok" to true, "message" to message))
    private fun failure(code: String, message: String): String = gson.toJson(mapOf("ok" to false, "code" to code, "message" to message))

    private fun encodePath(path: String): String = path.trim('/').split('/').joinToString("/") { urlEncode(it) }
    private fun urlEncode(value: String): String = java.net.URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
}
