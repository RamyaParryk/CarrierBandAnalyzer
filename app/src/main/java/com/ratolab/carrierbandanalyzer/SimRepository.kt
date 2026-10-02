package com.ratolab.carrierbandanalyzer

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat

data class SimEntry(val subscriptionId: Int, val slotIndex: Int, val displayName: String)

object SimRepository {
    fun active(context: Context): List<SimEntry> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) return emptyList()
        return try {
            val manager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
            manager.activeSubscriptionInfoList.orEmpty()
                .sortedWith(compareBy({it.simSlotIndex}, {it.subscriptionId}))
                .map { SimEntry(it.subscriptionId, it.simSlotIndex, it.displayName?.toString().orEmpty()) }
        } catch (_: SecurityException) { emptyList() }
          catch (_: UnsupportedOperationException) { emptyList() }
    }
}
