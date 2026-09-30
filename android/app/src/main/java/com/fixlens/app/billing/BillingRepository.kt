package com.fixlens.app.billing

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Persisted billing counters, decoupled from DataStore so tests can run with
 * an in-memory implementation.
 */
data class StoredBillingState(
    val scansThisMonth: Int = 0,
    val allowanceMonth: String = "",
    val creditBalance: Int = 0,
    /** RevenueCat transaction ids already credited (exactly-once grants). */
    val grantedTransactionIds: Set<String> = emptySet(),
)

/** Persistence boundary for the billing counters. */
interface BillingStorage {
    suspend fun load(): StoredBillingState
    suspend fun save(state: StoredBillingState)
}

/** Default DataStore-backed storage (device-local). */
class DataStoreBillingStorage(context: Context) : BillingStorage {

    private val dataStore = context.billingDataStore

    override suspend fun load(): StoredBillingState {
        // dataStore.data is an INFINITE flow, read with first(), never collect.
        val prefs = dataStore.data.first()
        return StoredBillingState(
            scansThisMonth = prefs[KEY_SCANS] ?: 0,
            allowanceMonth = prefs[KEY_MONTH] ?: "",
            creditBalance = prefs[KEY_CREDITS] ?: 0,
            grantedTransactionIds = prefs[KEY_GRANTED] ?: emptySet(),
        )
    }

    override suspend fun save(state: StoredBillingState) {
        dataStore.edit { prefs ->
            prefs[KEY_SCANS] = state.scansThisMonth
            prefs[KEY_MONTH] = state.allowanceMonth
            prefs[KEY_CREDITS] = state.creditBalance
            prefs[KEY_GRANTED] = state.grantedTransactionIds
        }
    }

    private companion object {
        val KEY_SCANS = intPreferencesKey("scans_this_month")
        val KEY_MONTH = stringPreferencesKey("allowance_month")
        val KEY_CREDITS = intPreferencesKey("repair_credits")
        val KEY_GRANTED = stringSetPreferencesKey("credited_transactions")
    }
}

private val Context.billingDataStore by preferencesDataStore(name = "fixlens_billing")

/**
 * Monetization state + policy application for the whole app (spec §12).
 *
 * - Entitlement (`fixlens_pro`): the REAL RevenueCat state, queried live and
 *   kept fresh via the SDK's customer-info listener. Never cached to disk,
 *   never faked, never inferred from purchases.
 * - Scan allowance: 3 per calendar month for free users (device-local
 *   counter, a courtesy gate for honest users, not an anti-fraud boundary).
 * - Repair credits: granted exactly once per completed one-time purchase,
 *   tracked by RevenueCat transaction id.
 *
 * The gate decides BEFORE the action; a scan consumes the allowance only
 * after the backend accepted the image (failed uploads never charge).
 */
class BillingRepository(
    private val gateway: PurchasesGateway,
    private val config: BillingConfig,
    private val storage: BillingStorage,
    /** Injectable so tests run the init/sync coroutines on the test scheduler. */
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val clock: () -> Long = System::currentTimeMillis,
) {

    private val grantMutex = Mutex()

    data class State(
        val configured: Boolean = false,
        val isPro: Boolean = false,
        /** Scans used in the CURRENT calendar month (free allowance counter). */
        val scansThisMonth: Int = 0,
        val creditBalance: Int = 0,
        /** True while the first customer-state fetch is in flight. */
        val loading: Boolean = true,
    ) {
        val freeScansRemaining: Int
            get() = (BillingGate.FREE_SCANS_PER_MONTH - scansThisMonth).coerceAtLeast(0)
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    private var monthKey: String = BillingGate.monthKey(clock())

    init {
        scope.launch { loadLocalAndSyncRemote() }
    }

    /** Loads the persisted counters, then reconciles with RevenueCat. */
    private suspend fun loadLocalAndSyncRemote() {
        val stored = storage.load()
        val now = BillingGate.monthKey(clock())
        monthKey = now
        _state.value = _state.value.copy(
            scansThisMonth = if (stored.allowanceMonth == now) stored.scansThisMonth else 0,
            creditBalance = stored.creditBalance,
        )
        syncFromRevenueCat()
    }

    /**
     * Initializes the SDK (no-op without a configured key) and pulls the real
     * entitlement + any pending one-time purchase credits.
     */
    suspend fun syncFromRevenueCat() {
        if (config.apiKey.isBlank()) {
            _state.value = _state.value.copy(configured = false, loading = false)
            return
        }
        gateway.initialize(config)
        _state.value = _state.value.copy(configured = true)
        when (val result = gateway.customerState(config)) {
            is BillingResult.Success -> applySnapshot(result.info)
            is BillingResult.Error -> _state.value = _state.value.copy(loading = false)
        }
    }

    /** Applies real RevenueCat state: entitlement + exactly-once credit grants. */
    private suspend fun applySnapshot(snapshot: BillingSnapshot) {
        grantUncreditedPurchases(snapshot)
        _state.value = _state.value.copy(
            configured = true,
            loading = false,
            isPro = snapshot.entitlementActive,
        )
    }

    /**
     * Credits any one-time pack purchase that has no local credit record yet.
     * Keyed by RevenueCat transaction id: a purchase can never grant credits
     * twice, and a reinstall/restore grants missing credits exactly once.
     */
    private suspend fun grantUncreditedPurchases(snapshot: BillingSnapshot) {
        if (snapshot.nonSubscriptionTransactions.isEmpty()) return
        grantMutex.withLock {
            val stored = storage.load()
            val pending = snapshot.nonSubscriptionTransactions
                .filter { it.productId in setOf(config.productPack5, config.productPack10) }
                .filter { it.transactionId !in stored.grantedTransactionIds }
            if (pending.isEmpty()) return
            val newCredits = pending.sumOf { BillingGate.creditsForPack(it.productId, config) }
            val updatedScans =
                if (stored.allowanceMonth == monthKey) stored.scansThisMonth else 0
            storage.save(
                stored.copy(
                    creditBalance = stored.creditBalance + newCredits,
                    grantedTransactionIds = stored.grantedTransactionIds +
                        pending.map { it.transactionId }.toSet(),
                    scansThisMonth = updatedScans,
                    allowanceMonth = monthKey,
                ),
            )
            _state.value = _state.value.copy(creditBalance = _state.value.creditBalance + newCredits)
        }
    }

    /**
     * Whether the user may run a scan right now, per spec §12. Call BEFORE
     * the paywall decision; [recordScan] runs only after a successful upload.
     */
    fun evaluateScan(): BillingGate.Decision = BillingGate.evaluateScan(
        isPro = _state.value.isPro,
        scanCountThisMonth = effectiveScansThisMonth(),
        creditBalance = _state.value.creditBalance,
    )

    /** Whether a premium action (guided repair, assembly) may proceed. */
    fun evaluatePremium(): BillingGate.Decision = BillingGate.evaluatePremium(
        isPro = _state.value.isPro,
        scanCountThisMonth = effectiveScansThisMonth(),
        creditBalance = _state.value.creditBalance,
    )

    /** Counts a completed scan against the current month's allowance. */
    suspend fun recordScan() {
        val now = BillingGate.monthKey(clock())
        val stored = storage.load()
        val updated = if (now != stored.allowanceMonth) {
            stored.copy(scansThisMonth = 1, allowanceMonth = now)
        } else {
            stored.copy(scansThisMonth = stored.scansThisMonth + 1, allowanceMonth = now)
        }
        storage.save(updated)
        monthKey = now
        _state.value = _state.value.copy(scansThisMonth = updated.scansThisMonth)
    }

    /** Spends one credit (premium action completed on a free plan). */
    suspend fun consumeCredit() {
        val stored = storage.load()
        val updated = stored.copy(creditBalance = (stored.creditBalance - 1).coerceAtLeast(0))
        storage.save(updated)
        _state.value = _state.value.copy(creditBalance = updated.creditBalance)
    }

    /**
     * Non-suspending variant for UI callbacks that cannot be marked suspend:
     * moves the write onto the repository's scope.
     */
    fun consumeCreditSuspend() {
        scope.launch { consumeCredit() }
    }

    /** Paywall offerings; empty list means "not configured / nothing to sell". */
    suspend fun paywallProducts(): List<PaywallProduct> = gateway.offerings(config)

    /** The entitlement id this build checks (for diagnostics messages). */
    fun entitlementId(): String = config.entitlementId

    /** Launches the real purchase sheet (Test Store modal in development). */
    suspend fun purchase(activity: android.app.Activity, product: PaywallProduct): BillingResult =
        gateway.purchase(activity, product, config).also { result ->
            if (result is BillingResult.Success) applySnapshot(result.info)
        }

    /** Restores purchases; credits for restored packs are granted exactly once. */
    suspend fun restore(): BillingResult = gateway.restore(config).also { result ->
        if (result is BillingResult.Success) applySnapshot(result.info)
    }

    /** Subscribes to real-time RevenueCat customer-info updates. */
    fun startListening() {
        (gateway as? RevenueCatGateway)?.listenForUpdates(config) { snapshot ->
            scope.launch { applySnapshot(snapshot) }
        }
    }

    private fun effectiveScansThisMonth(): Int {
        val now = BillingGate.monthKey(clock())
        return if (now == monthKey) _state.value.scansThisMonth else 0
    }
}

private fun monthKeyOf(millis: Long): String =
    DateTimeFormatter.ofPattern("yyyy-MM").withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(millis))
