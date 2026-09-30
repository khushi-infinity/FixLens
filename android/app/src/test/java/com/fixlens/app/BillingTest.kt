package com.fixlens.app

import com.fixlens.app.billing.BillingConfig
import com.fixlens.app.billing.BillingGate
import com.fixlens.app.billing.BillingRepository
import com.fixlens.app.billing.BillingResult
import com.fixlens.app.billing.BillingSnapshot
import com.fixlens.app.billing.PaywallProduct
import com.fixlens.app.billing.PurchasesGateway
import com.fixlens.app.billing.TransactionRef
import android.app.Activity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Phase 6 tests: the monetization RULES (BillingGate — pure) and the
 * repository behavior against a FAKE gateway (real RevenueCat is exercised
 * on-device). The repository state must reflect the gateway exactly:
 * entitlements are never invented, credits never granted twice.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BillingTest {

    // -----------------------------------------------------------------------
    // BillingGate: the pure spec §12 rules
    // -----------------------------------------------------------------------

    @Test
    fun `free user under the allowance may scan`() {
        val d = BillingGate.evaluateScan(isPro = false, scanCountThisMonth = 2, creditBalance = 0)
        assertTrue(d is BillingGate.Decision.Allow)
        assertEquals(BillingGate.Reason.FREE_SCAN_AVAILABLE, (d as BillingGate.Decision.Allow).reason)
    }

    @Test
    fun `free user at the allowance is blocked without credits`() {
        val d = BillingGate.evaluateScan(isPro = false, scanCountThisMonth = 3, creditBalance = 0)
        assertTrue(d is BillingGate.Decision.Paywall)
        assertEquals(BillingGate.Reason.FREE_SCANS_EXHAUSTED, (d as BillingGate.Decision.Paywall).reason)
    }

    @Test
    fun `credit covers a scan beyond the free allowance`() {
        val d = BillingGate.evaluateScan(isPro = false, scanCountThisMonth = 3, creditBalance = 2)
        assertTrue(d is BillingGate.Decision.Allow)
        assertEquals(BillingGate.Reason.CREDIT_AVAILABLE, (d as BillingGate.Decision.Allow).reason)
    }

    @Test
    fun `pro bypasses the allowance entirely`() {
        val d = BillingGate.evaluateScan(isPro = true, scanCountThisMonth = 999, creditBalance = 0)
        assertTrue(d is BillingGate.Decision.Allow)
        assertEquals(BillingGate.Reason.PRO_ENTITLED, (d as BillingGate.Decision.Allow).reason)
    }

    @Test
    fun `premium feature allowed within the free allowance without pro`() {
        // Spec §12: paywall applies to premium guided repair AFTER the free
        // allowance. Within it, guided repair is part of basic guidance.
        val d = BillingGate.evaluatePremium(false, 1, 0)
        assertTrue(d is BillingGate.Decision.Allow)
        assertEquals(BillingGate.Reason.FREE_SCAN_AVAILABLE, (d as BillingGate.Decision.Allow).reason)
    }

    @Test
    fun `premium feature blocked beyond the allowance without pro or credits`() {
        assertTrue(BillingGate.evaluatePremium(false, 3, 0) is BillingGate.Decision.Paywall)
        val allow = BillingGate.evaluatePremium(false, 3, 1)
        assertEquals(BillingGate.Reason.CREDIT_AVAILABLE, (allow as BillingGate.Decision.Allow).reason)
        val pro = BillingGate.evaluatePremium(true, 999, 0)
        assertEquals(BillingGate.Reason.PRO_ENTITLED, (pro as BillingGate.Decision.Allow).reason)
    }

    @Test
    fun `exactly three free scans per month`() {
        assertEquals(3, BillingGate.FREE_SCANS_PER_MONTH)
    }

    @Test
    fun `month key is a calendar month`() {
        // 2026-09-29T00:00:00Z in UTC
        assertEquals("2026-09", BillingGate.monthKey(1790707200000L, java.time.ZoneId.of("UTC")))
        // 2026-10-01T00:00:00Z — next month
        assertEquals("2026-10", BillingGate.monthKey(1790966400000L, java.time.ZoneId.of("UTC")))
    }

    @Test
    fun `credits map from pack product ids only`() {
        val config = BillingConfig(apiKey = "testn_fake")
        assertEquals(5, BillingGate.creditsForPack("fixlens_repair_pack_5", config))
        assertEquals(10, BillingGate.creditsForPack("fixlens_repair_pack_10", config))
        assertEquals(0, BillingGate.creditsForPack("fixlens_monthly", config))
    }

    // -----------------------------------------------------------------------
    // Repository with a fake gateway
    // -----------------------------------------------------------------------

    private class FakeGateway(
        var snapshot: BillingSnapshot = BillingSnapshot.notEntitled("fixlens_pro"),
        var offeringsError: Boolean = false,
    ) : PurchasesGateway {
        var customerStateCalls = 0
        val purchasedIds = mutableListOf<String>()
        var restoreCalls = 0
        var nextPurchaseResult: BillingResult =
            BillingResult.Error("no purchase scripted")

        override fun initialize(config: BillingConfig) {}

        override suspend fun customerState(config: BillingConfig): BillingResult {
            customerStateCalls++
            return BillingResult.Success(snapshot)
        }

        override suspend fun offerings(config: BillingConfig): List<PaywallProduct> {
            if (offeringsError) return emptyList()
            return listOf(
                PaywallProduct(config.productMonthly, "Pro Monthly", "", "$7.99", PaywallProduct.Kind.SUBSCRIPTION_MONTHLY, "per month"),
                PaywallProduct(config.productAnnual, "Pro Annual", "", "$59.99", PaywallProduct.Kind.SUBSCRIPTION_ANNUAL, "per year"),
                PaywallProduct(config.productPack5, "Repair Pack 5", "", "$3.99", PaywallProduct.Kind.PACK_5),
                PaywallProduct(config.productPack10, "Repair Pack 10", "", "$6.99", PaywallProduct.Kind.PACK_10),
            )
        }

        override suspend fun purchase(
            activity: Activity,
            product: PaywallProduct,
            config: BillingConfig,
        ): BillingResult {
            purchasedIds.add(product.productId)
            return nextPurchaseResult
        }

        override suspend fun restore(config: BillingConfig): BillingResult {
            restoreCalls++
            return BillingResult.Success(snapshot)
        }
    }

    private lateinit var gateway: FakeGateway
    private lateinit var repo: BillingRepository
    private lateinit var storage: InMemoryBillingStorage
    private val dispatcher = StandardTestDispatcher()
    private val scope get() = kotlinx.coroutines.CoroutineScope(dispatcher + kotlinx.coroutines.SupervisorJob())

    /** In-memory stand-in for DataStoreBillingStorage. */
    private class InMemoryBillingStorage : com.fixlens.app.billing.BillingStorage {
        var state = com.fixlens.app.billing.StoredBillingState()
        var saveCalls = 0
        override suspend fun load(): com.fixlens.app.billing.StoredBillingState = state
        override suspend fun save(state: com.fixlens.app.billing.StoredBillingState) {
            saveCalls++
            this.state = state
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        gateway = FakeGateway()
        storage = InMemoryBillingStorage()
        repo = BillingRepository(
            gateway = gateway,
            config = BillingConfig(apiKey = "testn_fake_key"),
            storage = storage,
            scope = scope,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private suspend fun drain() {
        dispatcher.scheduler.advanceUntilIdle()
        kotlinx.coroutines.delay(10) // repository uses its own SupervisorJob scope
        dispatcher.scheduler.advanceUntilIdle()
    }

    @Test
    fun `starts free with zero scans and syncs the real entitlement`() = runTest(dispatcher) {
        gateway.snapshot = BillingSnapshot.notEntitled("fixlens_pro")
        repo.syncFromRevenueCat()
        drain()
        val s = repo.state.value
        assertTrue(s.configured)
        assertFalse(s.isPro)
        assertEquals(0, s.scansThisMonth)
        assertEquals(0, s.creditBalance)
        assertTrue(repo.evaluateScan() is BillingGate.Decision.Allow)
    }

    @Test
    fun `entitlement reflects exactly what revenuecat reports`() = runTest(dispatcher) {
        gateway.snapshot = BillingSnapshot(
            entitlementActive = true,
            entitlementId = "fixlens_pro",
        )
        repo.syncFromRevenueCat()
        drain()
        assertTrue(repo.state.value.isPro)
        assertTrue(repo.evaluatePremium() is BillingGate.Decision.Allow)
    }

    @Test
    fun `recordScan then evaluate hits the paywall at the limit`() = runTest(dispatcher) {
        repo.syncFromRevenueCat()
        drain()
        repo.recordScan()
        repo.recordScan()
        repo.recordScan()
        drain()
        val d = repo.evaluateScan()
        assertTrue(d is BillingGate.Decision.Paywall)
    }

    @Test
    fun `pack purchase grants credits exactly once per transaction`() = runTest(dispatcher) {
        repo.syncFromRevenueCat()
        drain()
        gateway.snapshot = BillingSnapshot.notEntitled("fixlens_pro").copy(
            nonSubscriptionTransactions = listOf(
                TransactionRef("fixlens_repair_pack_5", "txn_001"),
            ),
        )
        repo.syncFromRevenueCat() // reconciliation pass
        drain()
        assertEquals(5, repo.state.value.creditBalance)

        // Same transaction seen again (resync) must NOT double-grant.
        repo.syncFromRevenueCat()
        drain()
        assertEquals(5, repo.state.value.creditBalance)
    }

    @Test
    fun `restore grants missing credits and picks up entitlement`() = runTest(dispatcher) {
        repo.syncFromRevenueCat()
        drain()
        gateway.snapshot = BillingSnapshot(
            entitlementActive = true,
            entitlementId = "fixlens_pro",
            nonSubscriptionTransactions = listOf(
                TransactionRef("fixlens_repair_pack_10", "txn_777"),
            ),
        )
        val result = repo.restore()
        drain()
        assertTrue(result is BillingResult.Success)
        assertTrue(repo.state.value.isPro)
        assertEquals(10, repo.state.value.creditBalance)
        assertEquals(1, gateway.restoreCalls)
    }

    @Test
    fun `paywall products come from the gateway offerings`() = runTest(dispatcher) {
        val products = repo.paywallProducts()
        assertEquals(4, products.size)
        assertTrue(products.any { it.kind == PaywallProduct.Kind.SUBSCRIPTION_MONTHLY && it.price == "$7.99" })
        assertTrue(products.any { it.kind == PaywallProduct.Kind.SUBSCRIPTION_ANNUAL && it.price == "$59.99" })
        assertTrue(products.any { it.kind == PaywallProduct.Kind.PACK_5 })
        assertTrue(products.any { it.kind == PaywallProduct.Kind.PACK_10 })
    }

    @Test
    fun `unconfigured key means not configured and never pro`() = runTest(dispatcher) {
        val unconfiguredGateway = FakeGateway()
        val unconfigured = BillingRepository(
            gateway = unconfiguredGateway,
            config = BillingConfig(apiKey = ""),
            storage = InMemoryBillingStorage(),
            scope = scope,
        )
        unconfigured.syncFromRevenueCat()
        drain()
        val s = unconfigured.state.value
        assertFalse(s.configured)
        assertFalse(s.isPro)
        // The SDK must never be touched without a configured key.
        assertEquals(0, unconfiguredGateway.customerStateCalls)
    }

    @Test
    fun `consumeCredit never goes negative`() = runTest(dispatcher) {
        repo.syncFromRevenueCat()
        drain()
        repo.consumeCredit()
        drain()
        assertEquals(0, repo.state.value.creditBalance)
    }
}
