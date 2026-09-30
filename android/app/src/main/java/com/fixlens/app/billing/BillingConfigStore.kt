package com.fixlens.app.billing

import android.content.Context
import com.fixlens.app.data.BackendConfigStore

/**
 * Loads the RevenueCat configuration from the same developer-provided device
 * config file the backend URL uses (`fixlens.properties`), so ALL external
 * identifiers live in exactly one place and none are compiled into the app.
 *
 *     revenuecat.api_key=testn_...
 */
class BillingConfigStore(private val context: Context) {

    val config: BillingConfig by lazy { load() }

    private fun load(): BillingConfig {
        val file = BackendConfigStore.configFile(context)
        val values: Map<String, String> = if (file.exists()) {
            BackendConfigStore.parseProperties(file.readLines())
        } else {
            emptyMap()
        }
        return BillingConfig.fromProperties(values)
    }
}
