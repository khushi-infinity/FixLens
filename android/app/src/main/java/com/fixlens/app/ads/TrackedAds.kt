package com.fixlens.app.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.fixlens.app.billing.BillingConfig
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.admob.loadAndTrackRewardedAd

/**
 * Catvertising integration: Google AdMob rewarded ads loaded through
 * RevenueCat's AdTracker (`loadAndTrack*`), so every impression, click, and
 * impression-level revenue dollar lands on the SAME customer profile as the
 * subscription data. That unified view is the whole point of RevenueCat Ads:
 * realized LTV includes ad revenue from free users, not just subscriptions.
 *
 * Placement discipline (one rewarded placement, honestly framed):
 *  - `scan_unlock_rewarded`: a free user out of scans may watch a short ad to
 *    unlock one extra scan THIS MONTH. Opt-in, never interstitial, never
 *    shown to Pro users, never gating safety content (the diagnosis and its
 *    safety assessment stay free no matter what).
 *
 * Ad units: Google's PUBLIC TEST ad unit during development; swap for a real
 * AdMob unit id via device config before any store release. Test ads never
 * generate revenue, so sandbox events in the dashboard are safe to demo.
 */
object TrackedAds {

    /** Google's public rewarded test unit; replace via config for release. */
    const val TEST_REWARDED_AD_UNIT = "ca-app-pub-3940256099942544/5224354917"

    const val PLACEMENT_SCAN_UNLOCK = "scan_unlock_rewarded"

    @Volatile
    private var initialized = false

    /** Google Mobile Ads init; safe to call repeatedly, cheap after first. */
    fun ensureInitialized(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            MobileAds.initialize(context) {}
            initialized = true
        }
    }

    /** Loads a rewarded ad through RevenueCat's AdTracker (all events tracked). */
    fun loadRewarded(
        activity: Activity,
        adUnitId: String = TEST_REWARDED_AD_UNIT,
        onLoaded: (RewardedAd) -> Unit,
        onUnavailable: (String) -> Unit,
    ) {
        ensureInitialized(activity)
        Purchases.sharedInstance.adTracker.loadAndTrackRewardedAd(
            context = activity,
            adUnitId = adUnitId,
            adRequest = AdRequest.Builder().build(),
            placement = PLACEMENT_SCAN_UNLOCK,
            loadCallback = object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) = onLoaded(ad)
                override fun onAdFailedToLoad(error: LoadAdError) =
                    onUnavailable(error.message)
            },
        )
    }

    /**
     * Shows a loaded rewarded ad and reports the earned reward through
     * [onEarned]; dismissal always calls [onDone] exactly once.
     */
    fun showRewarded(
        activity: Activity,
        ad: RewardedAd,
        onEarned: () -> Unit,
        onDone: () -> Unit,
    ) {
        var settled = false
        fun settle() {
            if (!settled) {
                settled = true
                onDone()
            }
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() = settle()
            override fun onAdFailedToShowFullScreenContent(adError: AdError) = settle()
        }
        ad.show(activity) { _ -> onEarned() }
    }
}
