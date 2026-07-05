package com.guardia.app.core.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wraps Play Billing for the single monthly subscription. Degrades gracefully when the Play
 * product is not configured (the [status] reports it) and never blocks the rest of the app.
 */
@Singleton
class BillingManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val entitlements: EntitlementManager,
) : PurchasesUpdatedListener {

    enum class Status { Idle, Connecting, Ready, Unavailable }

    private val _status = MutableStateFlow(Status.Idle)
    val status: StateFlow<Status> = _status.asStateFlow()

    private val _price = MutableStateFlow<String?>(null)
    val price: StateFlow<String?> = _price.asStateFlow()

    /** Human-readable free-trial length (e.g. "7-day") when the offer includes one, else null. */
    private val _trial = MutableStateFlow<String?>(null)
    val trial: StateFlow<String?> = _trial.asStateFlow()

    private var productDetails: ProductDetails? = null

    private val client: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(
            com.android.billingclient.api.PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build(),
        )
        .build()

    fun connect() {
        if (_status.value == Status.Connecting || _status.value == Status.Ready) return
        _status.value = Status.Connecting
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryProduct()
                    queryPurchases()
                } else {
                    _status.value = Status.Unavailable
                }
            }

            override fun onBillingServiceDisconnected() {
                _status.value = Status.Idle
            }
        })
    }

    private fun queryProduct() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(SUB_ID)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build(),
                ),
            )
            .build()
        client.queryProductDetailsAsync(params) { result, details ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK && details.isNotEmpty()) {
                productDetails = details.first()
                val phases = productDetails
                    ?.subscriptionOfferDetails
                    ?.firstOrNull()
                    ?.pricingPhases
                    ?.pricingPhaseList
                    .orEmpty()
                _price.value = phases.firstOrNull { it.priceAmountMicros > 0 }?.formattedPrice
                    ?: phases.firstOrNull()?.formattedPrice
                _trial.value = phases.firstOrNull { it.priceAmountMicros == 0L }
                    ?.let { trialLabel(it.billingPeriod) }
                _status.value = Status.Ready
            } else {
                _status.value = Status.Unavailable
            }
        }
    }

    private fun queryPurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        client.queryPurchasesAsync(params) { _, purchases ->
            val active = purchases.any { it.purchaseState == Purchase.PurchaseState.PURCHASED }
            entitlements.setPremium(active)
            purchases.forEach { acknowledge(it) }
        }
    }

    fun launchPurchase(activity: Activity) {
        val details = productDetails ?: return
        val offerToken = details.subscriptionOfferDetails?.firstOrNull()?.offerToken ?: return
        val params = com.android.billingclient.api.BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    com.android.billingclient.api.BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .setOfferToken(offerToken)
                        .build(),
                ),
            )
            .build()
        client.launchBillingFlow(activity, params)
    }

    fun restore() = queryPurchases()

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            val active = purchases.any { it.purchaseState == Purchase.PurchaseState.PURCHASED }
            entitlements.setPremium(active)
            purchases.forEach { acknowledge(it) }
        }
    }

    private fun acknowledge(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED && !purchase.isAcknowledged) {
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            client.acknowledgePurchase(params) { Log.d(TAG, "ack: ${it.responseCode}") }
        }
    }

    /** Converts an ISO-8601 period like "P7D"/"P1W"/"P1M" into a short label such as "7-day". */
    private fun trialLabel(period: String?): String? {
        if (period.isNullOrBlank()) return null
        val match = Regex("P(\\d+)([DWMY])").find(period) ?: return "free"
        val (n, unit) = match.destructured
        val word = when (unit) { "D" -> "day"; "W" -> "week"; "M" -> "month"; else -> "year" }
        return "$n-$word"
    }

    companion object {
        private const val TAG = "BillingManager"
        const val SUB_ID = "guardia_premium_monthly"
    }
}
