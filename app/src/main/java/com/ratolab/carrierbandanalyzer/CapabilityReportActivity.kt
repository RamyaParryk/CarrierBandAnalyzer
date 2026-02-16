package com.ratolab.carrierbandanalyzer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider

class CapabilityReportActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val analyzer = BandAnalyzer(this)

        setContent {
            MaterialTheme {
                val observed = analyzer.getObservedBands()
                val coverage = analyzer.calculateCoverage()

                CapabilityReportScreen(
                    analyzer = analyzer,
                    deviceName = Build.MODEL,
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
    analyzer: BandAnalyzer,
    deviceName: String,
    observedBands: Set<String>,
    coverage: CoverageResult,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.report_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    // 1. CSVログ出力ボタン
                    IconButton(onClick = {
                        exportLogFile(context, analyzer)
                    }) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = stringResource(R.string.item_export_log)
                        )
                    }

                    // 2. テキストレポート共有ボタン
                    IconButton(onClick = {
                        shareReportText(context, deviceName, observedBands, coverage)
                    }) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = stringResource(R.string.action_share_report)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
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
            // 端末情報
            ReportInfoSection(stringResource(R.string.label_device_name), deviceName)

            // 推定LTE対応
            val lteBands = observedBands.filter { it.startsWith("B") }.sorted()
            ReportBandSection(stringResource(R.string.label_est_lte), lteBands, BandType.HISTORY)

            // 推定NR対応
            val nrBands = observedBands.filter { it.startsWith("n") }.sorted()
            ReportBandSection(stringResource(R.string.label_est_nr), nrBands, BandType.NOW)

            // 対応率 ProgressBar
            Column {
                val carrierName = toJaCarrierName(coverage.carrier)
                Text(
                    text = stringResource(R.string.label_coverage_by, carrierName),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))

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

/**
 * レポートをテキスト形式で共有する
 */
private fun shareReportText(
    context: Context,
    deviceName: String,
    observedBands: Set<String>,
    coverage: CoverageResult
) {
    val lteBands = observedBands.filter { it.startsWith("B") }.sorted().joinToString(", ")
    val nrBands = observedBands.filter { it.startsWith("n") }.sorted().joinToString(", ")
    val carrierName = toJaCarrierName(coverage.carrier)

    val reportText = """
        [${context.getString(R.string.report_title)}]
        ${context.getString(R.string.label_device_name)}: $deviceName
        
        ${context.getString(R.string.label_est_lte)}:
        ${lteBands.ifEmpty { "None" }}
        
        ${context.getString(R.string.label_est_nr)}:
        ${nrBands.ifEmpty { "None" }}
        
        ${context.getString(R.string.label_coverage_by, carrierName)}:
        ${coverage.coveragePercent}% (${coverage.judgement})
        
        ${context.getString(R.string.report_footer)}
    """.trimIndent()

    val sendIntent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_TEXT, reportText)
        type = "text/plain"
    }
    context.startActivity(Intent.createChooser(sendIntent, null))
}

/**
 * CSVファイルを共有する
 */
private fun exportLogFile(context: Context, analyzer: BandAnalyzer) {
    val logFile = analyzer.getLogFile()
    if (!logFile.exists() || logFile.length() == 0L) {
        Toast.makeText(context, "Log file is empty", Toast.LENGTH_SHORT).show()
        return
    }

    try {
        val contentUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            logFile
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.item_export_log)))
    } catch (e: Exception) {
        Toast.makeText(context, "Failed to export: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

// --- 共通UIパーツ ---

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