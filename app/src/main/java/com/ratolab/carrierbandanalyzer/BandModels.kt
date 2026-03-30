package com.ratolab.carrierbandanalyzer

data class CoverageResult(
    val carrier: String,
    val observedBands: Set<String>,
    val carrierBands: Set<String>,
    val coveredCount: Int,
    val totalCount: Int,
    val coveragePercent: Int,
    val judgement: String
)
