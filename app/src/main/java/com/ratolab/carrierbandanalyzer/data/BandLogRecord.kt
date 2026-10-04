package com.ratolab.carrierbandanalyzer.data

/** subscriptionId is an Android-assigned identifier, not a permanent SIM identity. */
data class SavedSimLog(
    val subscriptionId: Int,
    val carrier: String,
    val displayName: String,
    val lastObservedMs: Long
)
