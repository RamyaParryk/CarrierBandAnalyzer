package com.ratolab.carrierbandanalyzer

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

class CapabilityReportActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val analyzer = BandAnalyzer(this)

        setContent {
            MaterialTheme {
                val observed = analyzer.getObservedBands()
                val coverage = analyzer.calculateCoverage()

                CapabilityReportScreen(
                    deviceName = Build.MODEL, // 端末名を自動取得 (例: Solana Seeker)
                    observedBands = observed,
                    coverage = coverage,
                    onBack = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CapabilityReportScreen(
    deviceName: String,
    observedBands: Set<String>,
    coverage: CoverageResult,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.report_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // 1. 端末情報セクション
            ReportInfoSection(stringResource(R.string.label_device_name), deviceName)

            // 2. LTE 推定対応 (Bから始まるもの)
            val lteBands = observedBands.filter { it.startsWith("B") }.sorted()
            ReportBandSection(stringResource(R.string.label_est_lte), lteBands, BandType.HISTORY)

            // 3. NR 推定対応 (nから始まるもの)
            val nrBands = observedBands.filter { it.startsWith("n") }.sorted()
            ReportBandSection(stringResource(R.string.label_est_nr), nrBands, BandType.NOW)

            // 4. 対応率セクション (ProgressBar)
            Column {
                Text(
                    text = stringResource(R.string.label_coverage_by, toJaCarrierName(coverage.carrier)),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))

                // 優先度①：ProgressBarの視覚化
                LinearProgressIndicator(
                    progress = { coverage.coveragePercent / 100f },
                    modifier = Modifier.fillMaxWidth().height(12.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                )
                Text(
                    text = "${coverage.coveragePercent}% - ${coverage.judgement}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
fun ReportInfoSection(label: String, value: String) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
        Text(text = value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReportBandSection(title: String, bands: List<String>, type: BandType) {
    Column {
        Text(text = title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(8.dp))
        if (bands.isEmpty()) {
            Text(text = stringResource(R.string.band_none), color = MaterialTheme.colorScheme.outline)
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                bands.forEach { BandChip(it, type) }
            }
        }
    }
}