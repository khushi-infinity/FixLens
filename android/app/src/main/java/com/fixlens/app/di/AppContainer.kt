package com.fixlens.app.di

import android.content.Context
import com.fixlens.app.billing.BillingConfigStore
import com.fixlens.app.billing.BillingRepository
import com.fixlens.app.billing.DataStoreBillingStorage
import com.fixlens.app.billing.RevenueCatGateway
import com.fixlens.app.data.BackendConfigStore
import com.fixlens.app.data.CaptureStore
import com.fixlens.app.network.ApiClient

/**
 * Phase 1 manual dependency container.
 *
 * Deliberately minimal: the Phase 1 architecture needs exactly three collaborators,
 * device backend configuration, the HTTP client abstraction, and the local capture
 * store. DI frameworks are intentionally not introduced.
 *
 * Phase 6 adds the billing collaborators: developer-provided RevenueCat config,
 * the gateway (the only SDK-speaking class), and the app-wide monetization
 * repository. No billing dependency touches existing camera/AI collaborators.
 */
class AppContainer(context: Context) {

    val backendConfigStore: BackendConfigStore =
        BackendConfigStore(context.applicationContext)

    val api: ApiClient = ApiClient.create(backendConfigStore.config.value)

    val captureStore: CaptureStore = CaptureStore(context.applicationContext)

    // --- Phase 6: monetization -------------------------------------------
    val billingConfigStore: BillingConfigStore = BillingConfigStore(context.applicationContext)

    val billingGateway: RevenueCatGateway = RevenueCatGateway(context.applicationContext)

    val billingRepository: BillingRepository = BillingRepository(
        gateway = billingGateway,
        config = billingConfigStore.config,
        storage = DataStoreBillingStorage(context.applicationContext),
    ).also { repo ->
        // Attaches the RevenueCat listener as soon as the SDK is configured;
        // a no-op (never a crash) on devices without a configured key.
        repo.startListening()
    }
}
