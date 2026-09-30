package com.fixlens.app.billing

import android.app.Activity
import android.content.Context
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.Offerings
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import com.revenuecat.purchases.PurchasesException
import com.revenuecat.purchases.PurchasesTransactionException
import com.revenuecat.purchases.awaitCustomerInfo
import com.revenuecat.purchases.awaitOfferings
import com.revenuecat.purchases.awaitPurchase
import com.revenuecat.purchases.awaitRestore
import com.revenuecat.purchases.interfaces.UpdatedCustomerInfoListener
import com.revenuecat.purchases.models.StoreProduct

/**
 * The one class in FixLens that speaks RevenueCat. Everything else depends on
 * [PurchasesGateway]; monetization rules stay unit-testable and no call site
 * imports the SDK (same boundary discipline as the AI provider layer).
 *
 * Uses the documented await* coroutine extensions. Purchase state is ALWAYS
 * the real SDK state, FixLens never fakes, caches to disk, or persists
 * entitlements. StoreProduct handles stay inside this class: paywall UI works
 * with plain [PaywallProduct] values, and purchases look the product back up
 * here (re-fetching offerings if needed after a process restart).
 */
class RevenueCatGateway(private val appContext: Context) : PurchasesGateway {

    @Volatile
    private var configured: Boolean = false

    /** StoreProducts from the last offerings fetch, keyed by product id. */
    private val productCache = HashMap<String, StoreProduct>()

    /** Listener registration deferred until the SDK is configured. */
    private var pendingListener: (() -> Unit)? = null

    override fun initialize(config: BillingConfig) {
        if (config.apiKey.isBlank() || configured) return
        Purchases.configure(
            PurchasesConfiguration.Builder(appContext, config.apiKey).build(),
        )
        configured = true
        // Deferred listener attach: the singleton exists only after configure.
        pendingListener?.invoke()
    }

    override suspend fun customerState(config: BillingConfig): BillingResult =
        try {
            BillingResult.Success(Purchases.sharedInstance.awaitCustomerInfo().toSnapshot(config))
        } catch (e: PurchasesException) {
            BillingResult.Error(friendlyError(e))
        } catch (t: Throwable) {
            BillingResult.Error("Could not load purchase state: ${t.message ?: "unknown error"}")
        }

    override suspend fun offerings(config: BillingConfig): List<PaywallProduct> = try {
        val offerings: Offerings = Purchases.sharedInstance.awaitOfferings()
        val current = offerings.current ?: return emptyList()
        current.availablePackages.mapNotNull { pkg ->
            val product = pkg.product
            productCache[product.id] = product
            when (product.id) {
                config.productMonthly -> product.toPaywallProduct(PaywallProduct.Kind.SUBSCRIPTION_MONTHLY, "per month")
                config.productAnnual -> product.toPaywallProduct(PaywallProduct.Kind.SUBSCRIPTION_ANNUAL, "per year")
                config.productPack5 -> product.toPaywallProduct(PaywallProduct.Kind.PACK_5, null)
                config.productPack10 -> product.toPaywallProduct(PaywallProduct.Kind.PACK_10, null)
                else -> null // only spec §12 products are ever rendered
            }
        }
    } catch (e: PurchasesException) {
        emptyList() // honest empty: the paywall shows an unconfigured state
    } catch (t: Throwable) {
        emptyList()
    }

    override suspend fun purchase(
        activity: Activity,
        product: PaywallProduct,
        config: BillingConfig,
    ): BillingResult = try {
        val storeProduct = productCache[product.productId]
            ?: refreshProducts(config).firstOrNull { it.id == product.productId }
            ?: return BillingResult.Error(
                "This product is not available yet. Check your connection and try again.",
            )
        val params = PurchaseParams.Builder(activity, storeProduct).build()
        BillingResult.Success(
            Purchases.sharedInstance.awaitPurchase(params).customerInfo.toSnapshot(config),
        )
    } catch (e: PurchasesTransactionException) {
        if (e.userCancelled) {
            BillingResult.Error("Purchase cancelled.", userCancelled = true)
        } else {
            BillingResult.Error(friendlyCode(e.error.code.name, e.error.message))
        }
    } catch (e: PurchasesException) {
        BillingResult.Error(friendlyError(e))
    } catch (t: Throwable) {
        BillingResult.Error("Purchase failed: ${t.message ?: "unknown error"}")
    }

    override suspend fun restore(config: BillingConfig): BillingResult = try {
        BillingResult.Success(Purchases.sharedInstance.awaitRestore().toSnapshot(config))
    } catch (e: PurchasesException) {
        BillingResult.Error(friendlyError(e))
    } catch (t: Throwable) {
        BillingResult.Error("Restore failed: ${t.message ?: "unknown error"}")
    }

    /**
     * Real-time entitlement updates (purchase completion, renewal, expiry).
     * No-op until the SDK has been configured with a key, accessing the
     * singleton before configure() throws and previously crashed the app at
     * startup on unconfigured devices (caught by E2E).
     */
    fun listenForUpdates(config: BillingConfig, onUpdate: (BillingSnapshot) -> Unit) {
        if (!Purchases.isConfigured) {
            // Defer: attach as soon as initialize() configures the SDK.
            pendingListener = { listenForUpdates(config, onUpdate) }
            return
        }
        Purchases.sharedInstance.updatedCustomerInfoListener =
            UpdatedCustomerInfoListener { info -> onUpdate(info.toSnapshot(config)) }
    }

    private suspend fun refreshProducts(config: BillingConfig): List<StoreProduct> {
        offerings(config)
        return productCache.values.toList()
    }

    private fun CustomerInfo.toSnapshot(config: BillingConfig): BillingSnapshot = BillingSnapshot(
        entitlementActive = entitlements.active.containsKey(config.entitlementId),
        entitlementId = config.entitlementId,
        productIdsOwned = entitlements.active.values.map { it.productIdentifier }.toSet(),
        willRenew = entitlements.active[config.entitlementId]?.willRenew ?: false,
        expirationDateMillis = entitlements.active[config.entitlementId]?.expirationDate?.time,
        nonSubscriptionTransactions = nonSubscriptionTransactions.map {
            TransactionRef(productId = it.productId, transactionId = it.transactionIdentifier)
        },
    )

    private fun StoreProduct.toPaywallProduct(kind: PaywallProduct.Kind, periodLabel: String?): PaywallProduct =                PaywallProduct(
                    productId = id,
                    title = title,
            description = description,
            price = price.formatted,
            kind = kind,
            periodLabel = periodLabel,
        )

    private fun friendlyError(e: PurchasesException): String =
        friendlyCode(e.code.name, e.message)

    private fun friendlyCode(code: String, message: String?): String = when {
        code.contains("NetworkError", ignoreCase = true) ->
            "Could not reach the purchase service. Check your connection and try again."
        code.contains("StoreProblem", ignoreCase = true) ->
            "The purchase store is temporarily unavailable. Try again in a moment."
        code.contains("PaymentPending", ignoreCase = true) ->
            "Your payment is awaiting confirmation. Purchases unlock automatically once approved."
        code.contains("ConfigurationError", ignoreCase = true) ->
            "Purchases are not configured on this device yet. See docs/DEVICE_SETUP.md."
        else -> message?.takeIf { it.isNotBlank() } ?: "Purchase could not be completed."
    }
}
