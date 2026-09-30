package com.fixlens.app.data

import java.net.URI

/**
 * Where the Android app finds the FastAPI backend. Never hardcoded anywhere in
 * the app: the value comes from the developer's local device config file
 * (~/.fixlens/backend.properties), documented in docs/DEVICE_SETUP.md.
 *
 * [rawUrl] may omit the scheme; it is normalized to http:// for development use.
 */
data class BackendConfig(
    val rawUrl: String,
    val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
) {
    val normalizedUrl: String by lazy { normalize(rawUrl) }

    companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 20_000L

        /**
         * Accepts "8000", ":8000", "192.168.1.20:8000", "http://host:8000/".
         * Port-only input is shorthand for the loopback host used with adb reverse.
         */
        fun normalize(raw: String): String {
            val trimmed = raw.trim()
            require(trimmed.isNotEmpty()) { "Backend URL is empty" }
            require(!trimmed.contains("://") || trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                "Only http:// or https:// backend URLs are supported: $trimmed"
            }
            val candidate = when {
                trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
                // Bare port ("8000") would otherwise parse as a host name.
                trimmed.toLongOrNull() != null -> "http://127.0.0.1:$trimmed"
                trimmed.startsWith(":") -> "http://127.0.0.1$trimmed"
                else -> "http://$trimmed"
            }
            val uri = runCatching { URI(candidate) }.getOrNull()
                ?: throw IllegalArgumentException("Backend URL is not a valid URI: $trimmed")
            val host = uri.host
                ?: throw IllegalArgumentException("Backend URL is missing a host: $trimmed")
            val port = when {
                uri.port > 0 -> uri.port
                uri.scheme == "https" -> 443
                else -> 80
            }
            val path = uri.rawPath.orEmpty().trimEnd('/')
            return "${uri.scheme}://$host:$port$path"
        }
    }
}
