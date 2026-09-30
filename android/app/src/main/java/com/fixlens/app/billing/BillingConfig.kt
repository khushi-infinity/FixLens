package com.fixlens.app.billing

/**
 * RevenueCat configuration (spec §5, §12).
 *
 * The RevenueCat API key is a PUBLIC app-specific key, but like every other
 * configurable value in FixLens it is NEVER compiled into source. It is
 * developer-provided per device via the existing local config file
 * (`fixlens.properties`, written with run-as; see docs/DEVICE_SETUP.md):
 *
 *     revenuecat.api_key=testn_...        (Test Store key for development)
 *     # optional:
 *     revenuecat.entitlement=fixlens_pro  (default: fixlens_pro)
 *     revenuecat.product_monthly=fixlens_monthly
 *     revenuecat.product_annual=fixlens_annual
 *     revenuecat.product_pack5=fixlens_repair_pack_5
 *     revenuecat.product_pack10=fixlens_repair_pack_10
 *
 * Test Store API keys work out of the box in debug builds (the SDK presents
 * its simulate-purchase modal instead of a real store sheet; the Test Store
 * crashes release builds on purpose, so the key must be swapped before any
 * production build, enforced by RevenueCat itself, not by us).
 */
data class BillingConfig(
    val apiKey: String,
    val entitlementId: String = DEFAULT_ENTITLEMENT,
    val productMonthly: String = "fixlens_monthly",
    val productAnnual: String = "fixlens_annual",
    val productPack5: String = "fixlens_repair_pack_5",
    val productPack10: String = "fixlens_repair_pack_10",
) {
    /** Spec §12 product identifiers + fixlens_pro entitlement, as one bundle. */
    val productIds: List<String>
        get() = listOf(productMonthly, productAnnual, productPack5, productPack10)

    val isTestStoreKey: Boolean
        get() = apiKey.startsWith(TEST_KEY_PREFIX)

    companion object {
        const val DEFAULT_ENTITLEMENT = "fixlens_pro"
        const val TEST_KEY_PREFIX = "testn_"
        const val MISSING = ""

        /** Parses the developer config file lines into a [BillingConfig]. */
        fun fromProperties(values: Map<String, String>): BillingConfig {
            val key = values["revenuecat.api_key"].orEmpty().trim()
            return BillingConfig(
                apiKey = key,
                entitlementId = values["revenuecat.entitlement"]?.trim()
                    ?.takeIf { it.isNotEmpty() } ?: DEFAULT_ENTITLEMENT,
                productMonthly = values["revenuecat.product_monthly"]?.trim()
                    ?.takeIf { it.isNotEmpty() } ?: "fixlens_monthly",
                productAnnual = values["revenuecat.product_annual"]?.trim()
                    ?.takeIf { it.isNotEmpty() } ?: "fixlens_annual",
                productPack5 = values["revenuecat.product_pack5"]?.trim()
                    ?.takeIf { it.isNotEmpty() } ?: "fixlens_repair_pack_5",
                productPack10 = values["revenuecat.product_pack10"]?.trim()
                    ?.takeIf { it.isNotEmpty() } ?: "fixlens_repair_pack_10",
            )
        }
    }
}
