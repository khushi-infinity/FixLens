package com.fixlens.app.data

import android.content.Context
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Loads the developer's device-local backend configuration.
 *
 * Phase 1 policy: the backend URL is NEVER hardcoded in source or Gradle. Each
 * developer device/AVD gets its own config file, written by a helper command:
 *
 *     echo "backend.url=http://127.0.0.1:8000" | \
 *       adb shell "run-as com.fixlens.app sh -c 'cat > files/fixlens.properties'"
 *
 * (Requires a debug build, the app launched once so files/ exists, and
 * `adb reverse tcp:8000 tcp:8000`, see docs/DEVICE_SETUP.md step 7.)
 *
 * The app must be (re)started after the file changes.
 */
class BackendConfigStore(private val context: Context) {

    private val _config = MutableStateFlow(loadConfig())

    /** Observable current config; recomputed on demand, not watched for file changes. */
    val config: StateFlow<BackendConfig> = _config

    @Suppress("unused")
    fun reload() {
        _config.value = loadConfig()
    }

    private fun loadConfig(): BackendConfig {
        val file = configFile(context)
        val rawUrl = if (file.exists()) {
            file.readLines()
                .firstOrNull { it.trim().startsWith("backend.url=") }
                ?.substringAfter('=')
                ?.trim()
                .orEmpty()
        } else {
            ""
        }
        return BackendConfig(rawUrl = rawUrl)
    }

    companion object {
        const val CONFIG_FILE_NAME = "fixlens.properties"

        fun configFile(context: Context): File =
            File(context.filesDir, CONFIG_FILE_NAME)

        fun parseProperties(lines: List<String>): Map<String, String> =
            lines
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .mapNotNull { line ->
                    val eq = line.indexOf('=')
                    // Missing separator or an empty key is malformed.
                    if (eq <= 0) return@mapNotNull null
                    val key = line.substring(0, eq).trim()
                    val value = line.substring(eq + 1).trim()
                    if (key.isEmpty()) null else key to value
                }
                .toMap()
    }
}
