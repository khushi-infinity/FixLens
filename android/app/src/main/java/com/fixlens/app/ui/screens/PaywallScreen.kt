package com.fixlens.app.ui.screens

import com.fixlens.app.ui.theme.paperSurface

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.fixlens.app.billing.BillingRepository
import com.fixlens.app.billing.BillingResult
import com.fixlens.app.billing.PaywallProduct
import com.fixlens.app.ui.theme.FixLensColors
import kotlinx.coroutines.launch

/**
 * Phase 6 paywall (spec §12): real RevenueCat offerings — Pro monthly vs
 * annual subscriptions and one-time Repair Packs — plus restore. Every
 * purchase goes through [BillingRepository.purchase] and reflects the REAL
 * RevenueCat result; there is no simulated entitlement anywhere.
 *
 * Shown when a free user hits the 3-scans/month allowance or tries a premium
 * feature without Pro or credits.
 */
@Composable
fun PaywallScreen(
    repository: BillingRepository,
    reason: PaywallReason,
    onDismiss: () -> Unit,
    onEntitled: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by repository.state.collectAsState()

    var products by remember { mutableStateOf<List<PaywallProduct>>(emptyList()) }
    var productsLoading by remember { mutableStateOf(true) }
    var purchasingId by remember { mutableStateOf<String?>(null) }
    var restoring by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var messageIsError by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        products = repository.paywallProducts()
        productsLoading = false
    }

    // Purchases can complete while the sheet is up (listener) — close with
    // success as soon as the real entitlement lands.
    LaunchedEffect(state.isPro) {
        if (state.isPro) onEntitled()
    }

    fun runPurchase(product: PaywallProduct) {
        if (purchasingId != null) return
        scope.launch {
            purchasingId = product.productId
            message = null
            val result = repository.purchase(context as android.app.Activity, product)
            purchasingId = null
            when (result) {
                is BillingResult.Success -> {
                    if (!result.info.entitlementActive && product.kind != PaywallProduct.Kind.SUBSCRIPTION_MONTHLY &&
                        product.kind != PaywallProduct.Kind.SUBSCRIPTION_ANNUAL
                    ) {
                        message = "Repair pack added — ${creditsFor(product)} credits."
                        messageIsError = false
                    }
                }
                is BillingResult.Error -> if (!result.userCancelled) {
                    message = result.message
                    messageIsError = true
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .paperSurface()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = FixLensColors.Ink,
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Icon(
            imageVector = Icons.Filled.WorkspacePremium,
            contentDescription = null,
            tint = FixLensColors.Terracotta,
            modifier = Modifier.size(56.dp),
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "FIXLENS PRO",
            style = MaterialTheme.typography.headlineSmall,
            color = FixLensColors.Ink,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = paywallHeadline(reason),
            style = MaterialTheme.typography.bodyMedium,
            color = FixLensColors.MutedInk,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(20.dp))
        Card(
            border = androidx.compose.foundation.BorderStroke(1.dp, com.fixlens.app.ui.theme.FixLensColors.Rule),
            colors = CardDefaults.cardColors(containerColor = FixLensColors.Cream),
            shape = RoundedCornerShape(14.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                ProFeature("Unlimited scans")
                ProFeature("Guided repair, step by step")
                ProFeature("Camera verification of every step")
                ProFeature("Advanced troubleshooting")
                ProFeature("Complex assembly")
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        when {
            !state.configured -> UnconfiguredState()
            productsLoading -> CircularProgressIndicator(color = FixLensColors.Terracotta)
            products.isEmpty() -> UnconfiguredState(
                detail = "No plans are available right now. Check your connection and try again later.",
            )
            else -> {
                val monthly = products.firstOrNull { it.kind == PaywallProduct.Kind.SUBSCRIPTION_MONTHLY }
                val annual = products.firstOrNull { it.kind == PaywallProduct.Kind.SUBSCRIPTION_ANNUAL }
                val pack5 = products.firstOrNull { it.kind == PaywallProduct.Kind.PACK_5 }
                val pack10 = products.firstOrNull { it.kind == PaywallProduct.Kind.PACK_10 }

                if (monthly != null && annual != null) {
                    Text(
                        text = "SUBSCRIPTIONS",
                        style = MaterialTheme.typography.labelLarge,
                        color = FixLensColors.Terracotta,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    PurchaseButton(
                        title = "Pro Annual — ${annual.price}",
                        subtitle = "Best value — same Pro features, annual pricing",
                        busy = purchasingId == annual.productId,
                        onClick = { runPurchase(annual) },
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    PurchaseButton(
                        title = "Pro Monthly — ${monthly.price}",
                        subtitle = "Cancel anytime",
                        busy = purchasingId == monthly.productId,
                        onClick = { runPurchase(monthly) },
                    )
                    if (pack5 != null || pack10 != null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = FixLensColors.Cream)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "ONE-TIME REPAIR PACKS",
                            style = MaterialTheme.typography.labelLarge,
                            color = FixLensColors.Terracotta,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        pack5?.let {
                            PurchaseButton(
                                title = "Repair Pack 5 — ${it.price}",
                                subtitle = "5 repair credits, one-time",
                                busy = purchasingId == it.productId,
                                onClick = { runPurchase(it) },
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        pack10?.let {
                            PurchaseButton(
                                title = "Repair Pack 10 — ${it.price}",
                                subtitle = "10 repair credits, one-time",
                                busy = purchasingId == it.productId,
                                onClick = { runPurchase(it) },
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                } else {
                    // Subscriptions absent: render whatever the offering has.
                    products.forEach { product ->
                        PurchaseButton(
                            title = "${product.title} — ${product.price}",
                            subtitle = product.periodLabel ?: product.description,
                            busy = purchasingId == product.productId,
                            onClick = { runPurchase(product) },
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }

        message?.let {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = if (messageIsError) FixLensColors.Danger else FixLensColors.Terracotta,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        OutlinedButton(
            onClick = {
                scope.launch {
                    restoring = true
                    message = null
                    val result = repository.restore()
                    restoring = false
                    message = when (result) {
                        is BillingResult.Success ->
                            if (result.info.entitlementActive) {
                                "Purchases restored — Pro is active."
                            } else {
                                "No active purchases found for this account."
                            }
                        is BillingResult.Error -> result.message
                    }
                    messageIsError = result is BillingResult.Error
                }
            },
            enabled = !restoring && state.configured,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
        ) {
            if (restoring) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = FixLensColors.Terracotta,
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text("Restore Purchases")
        }
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = onDismiss) {
            Text("Not now", color = FixLensColors.MutedInk)
        }
        Spacer(modifier = Modifier.navigationBarsPadding())
    }
}

private fun creditsFor(product: PaywallProduct): Int = when (product.kind) {
    PaywallProduct.Kind.PACK_5 -> 5
    PaywallProduct.Kind.PACK_10 -> 10
    else -> 0
}

private fun paywallHeadline(reason: PaywallReason): String = when (reason) {
    PaywallReason.FREE_SCANS_EXHAUSTED ->
        "You've used all ${com.fixlens.app.billing.BillingGate.FREE_SCANS_PER_MONTH} free scans this month. Unlock Pro for unlimited scans and guided fixes."
    PaywallReason.PREMIUM_FEATURE ->
        "Guided repair and verification are part of FixLens Pro. Unlock them to continue."
}

enum class PaywallReason { FREE_SCANS_EXHAUSTED, PREMIUM_FEATURE }

@Composable
private fun ProFeature(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = FixLensColors.Terracotta,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = FixLensColors.Ink)
    }
    Spacer(modifier = Modifier.height(6.dp))
}

@Composable
private fun PurchaseButton(
    title: String,
    subtitle: String,
    busy: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = !busy,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = FixLensColors.Terracotta,
            contentColor = FixLensColors.Cream,
        ),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = FixLensColors.Cream,
                )
            } else {
                Text(title, style = MaterialTheme.typography.labelLarge)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = FixLensColors.Cream,
                )
            }
        }
    }
}

@Composable
private fun UnconfiguredState(detail: String? = null) {
    Card(
        border = androidx.compose.foundation.BorderStroke(1.dp, com.fixlens.app.ui.theme.FixLensColors.Rule),
        colors = CardDefaults.cardColors(containerColor = FixLensColors.Cream),
        shape = RoundedCornerShape(14.dp),
    ) {
        Text(
            text = detail
                ?: "Purchases are not configured on this device yet. Add your RevenueCat key to the device config file (see docs/DEVICE_SETUP.md), then restart the app.",
            style = MaterialTheme.typography.bodyMedium,
            color = FixLensColors.Ink,
            modifier = Modifier.padding(16.dp),
        )
    }
}
