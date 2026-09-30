package com.fixlens.app.billing

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Free-plan allowance + repair-credit rules (spec §12), as PURE logic.
 *
 * - Free: 3 scans per calendar month (basic diagnosis remains usable within
 *   the allowance; the allowance counts scans, not diagnoses rendered —
 *   every `/api/v1/diagnose` consumes one scan).
 * - Pro subscription (fixlens_pro entitlement): unlimited scans, guided
 *   repair, verification, advanced troubleshooting, complex assembly.
 * - Repair packs: consumable credits (5 or 10) that unlock the same premium
 *   features as Pro — one credit per premium action.
 *
 * No Android, no RevenueCat, no network imports: the repository owns the
 * RevenueCat state, this class owns the RULES. Same discipline as the
 * repair engine in Phase 4.
 */
object BillingGate {

    const val FREE_SCANS_PER_MONTH = 3

    /** What the user is allowed to do right now. */
    sealed class Decision {
        /** Proceed; the action is allowed. */
        data class Allow(val reason: Reason) : Decision()

        /** Blocked — show the paywall with this reason. */
        data class Paywall(val reason: Reason) : Decision()
    }

    enum class Reason {
        PRO_ENTITLED,
        FREE_SCAN_AVAILABLE,
        FREE_SCANS_EXHAUSTED,
        CREDIT_AVAILABLE,
        NO_CREDITS,
    }

    /** Whether `scanCountThisMonth` may proceed for the given entitlement/credit state. */
    fun evaluateScan(
        isPro: Boolean,
        scanCountThisMonth: Int,
        creditBalance: Int,
    ): Decision = when {
        isPro -> Decision.Allow(Reason.PRO_ENTITLED)
        scanCountThisMonth < FREE_SCANS_PER_MONTH -> Decision.Allow(Reason.FREE_SCAN_AVAILABLE)
        creditBalance > 0 -> Decision.Allow(Reason.CREDIT_AVAILABLE)
        else -> Decision.Paywall(Reason.FREE_SCANS_EXHAUSTED)
    }

    /**
     * Premium actions (guided repair, assembly). Spec §12: the paywall applies
     * to premium guided repair AFTER the free allowance — within the monthly
     * allowance, guided repair is part of basic guidance. Beyond it, Pro or a
     * credit unlocks the action.
     */
    fun evaluatePremium(
        isPro: Boolean,
        scanCountThisMonth: Int,
        creditBalance: Int,
    ): Decision = when {
        isPro -> Decision.Allow(Reason.PRO_ENTITLED)
        scanCountThisMonth < FREE_SCANS_PER_MONTH -> Decision.Allow(Reason.FREE_SCAN_AVAILABLE)
        creditBalance > 0 -> Decision.Allow(Reason.CREDIT_AVAILABLE)
        else -> Decision.Paywall(Reason.FREE_SCANS_EXHAUSTED)
    }

    /**
     * The calendar-month key ("2026-09") a scan at [epochMillis] counts
     * against. Device-local timezone — the allowance is a client-side
     * courtesy gate, not an anti-fraud boundary.
     */
    fun monthKey(epochMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): String =
        DateTimeFormatter.ofPattern("yyyy-MM")
            .withZone(zoneId)
            .format(Instant.ofEpochMilli(epochMillis))

    /** Deterministic credit identity: a purchase grants exactly its pack size. */
    fun creditsForPack(productId: String, config: BillingConfig): Int = when (productId) {
        config.productPack5 -> 5
        config.productPack10 -> 10
        else -> 0
    }
}
