package com.guardia.app.ui.screens.settings

import android.app.Activity
import androidx.lifecycle.ViewModel
import com.guardia.app.core.billing.BillingManager
import com.guardia.app.core.billing.EntitlementManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val billing: BillingManager,
    entitlements: EntitlementManager,
) : ViewModel() {

    val premium: StateFlow<Boolean> = entitlements.premium
    val status: StateFlow<BillingManager.Status> = billing.status
    val price: StateFlow<String?> = billing.price
    val trial: StateFlow<String?> = billing.trial

    fun connect() = billing.connect()
    fun purchase(activity: Activity) = billing.launchPurchase(activity)
    fun restore() = billing.restore()
}
