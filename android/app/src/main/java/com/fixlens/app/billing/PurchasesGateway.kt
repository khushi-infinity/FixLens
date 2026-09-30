package com.fixlens.app.billing

import android.app.Activity

/**
 * The minimal RevenueCat surface FixLens uses, as an interface so every
 * monetization rule is unit-testable against a fake (the same discipline as
 * the AI provider abstraction: call sites never name the SDK).
 *
 * Real implementation: [RevenueCatGateway]. All methods suspend and never
 * throw for expected outcomes, failures arrive as [BillingResult.Error].
 */
interface PurchasesGateway {
    /** Starts RevenueCat; cheap, idempotent, safe to call from any thread. */
    fun initialize(config: BillingConfig)

    /** One-shot entitlement/customer state snapshot. */
    suspend fun customerState(config: BillingConfig): BillingResult

    /** Offerings for the paywall (subscription packages + one-time packs). */
    suspend fun offerings(config: BillingConfig): List<PaywallProduct>

    /** Launches the native/Test-Store purchase sheet. [activity] must be current. */
    suspend fun purchase(
        activity: Activity,
        product: PaywallProduct,
        config: BillingConfig,
    ): BillingResult

    /** Restores previous purchases onto this device/identity. */
    suspend fun restore(config: BillingConfig): BillingResult
}

/** Result of any billing operation. Never a raw SDK type. */
sealed class BillingResult {
    data class Success(val info: BillingSnapshot) : BillingResult()
    data class Error(val message: String, val userCancelled: Boolean = false) : BillingResult()
}

/**
 * Normalized customer/purchase state the rest of the app consumes.
 * [entitlementActive] comes from the REAL RevenueCat entitlement map,
 * nothing in FixLens fakes or persists this value.
 */
data class BillingSnapshot(
    val entitlementActive: Boolean,
    val entitlementId: String,
    val productIdsOwned: Set<String> = emptySet(),
    val willRenew: Boolean = false,
    val expirationDateMillis: Long? = null,
    /** One-time (consumable) purchases visible on the customer, used for
     * exactly-once repair-credit grants keyed by transaction id. */
    val nonSubscriptionTransactions: List<TransactionRef> = emptyList(),
) {
    companion object {
        fun notEntitled(entitlementId: String) = BillingSnapshot(
            entitlementActive = false,
            entitlementId = entitlementId,
        )
    }
}

/** A one-time purchase reference (product + transaction id). */
data class TransactionRef(val productId: String, val transactionId: String)

/** A purchasable item rendered on the paywall (price strings from the store). */
data class PaywallProduct(
    val productId: String,
    val title: String,
    val description: String,
    /** Localized price as the store formats it, e.g. "$7.99". */
    val price: String,
    val kind: Kind,
    /** Subscription period label for subscriptions (e.g. "per month"), null for one-time. */
    val periodLabel: String? = null,
) {
    enum class Kind { SUBSCRIPTION_MONTHLY, SUBSCRIPTION_ANNUAL, LIFETIME, PACK_5, PACK_10 }
}
