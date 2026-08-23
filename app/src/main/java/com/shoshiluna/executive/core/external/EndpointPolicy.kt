package com.shoshiluna.executive.core.external

import java.net.InetAddress
import java.net.URI

object EndpointPolicy {
    fun validate(endpoint: String): Result<URI> = runCatching {
        val uri = URI(endpoint.trim())
        require(uri.scheme.equals("https", ignoreCase = true)) { "Only HTTPS endpoints are allowed" }
        require(!uri.host.isNullOrBlank()) { "Endpoint host is required" }
        require(uri.userInfo == null) { "Credentials in endpoint URL are forbidden" }
        require(uri.fragment == null) { "Endpoint fragments are forbidden" }
        require(!isLocalHostName(uri.host)) { "Local/private endpoints are forbidden" }
        if (isIpLiteral(uri.host)) {
            val address = InetAddress.getByName(uri.host)
            require(!address.isAnyLocalAddress && !address.isLoopbackAddress && !address.isLinkLocalAddress && !address.isSiteLocalAddress) {
                "Private/local IP endpoints are forbidden"
            }
        }
        uri
    }

    private fun isLocalHostName(host: String): Boolean {
        val h = host.lowercase()
        return h == "localhost" || h.endsWith(".localhost") || h.endsWith(".local")
    }

    private fun isIpLiteral(host: String): Boolean =
        host.matches(Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")) || host.contains(":")
}
