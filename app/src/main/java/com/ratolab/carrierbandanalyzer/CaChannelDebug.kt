package com.ratolab.carrierbandanalyzer

data class CaChannelDebug(
    val networkType: Int,
    val networkTypeName: String,
    val channelNumber: Int,
    val bandDirect: Int?
)
