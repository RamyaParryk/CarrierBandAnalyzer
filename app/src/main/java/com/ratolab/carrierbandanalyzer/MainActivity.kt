package com.ratolab.carrierbandanalyzer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private lateinit var analyzer: BandAnalyzer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        analyzer = BandAnalyzer(this)

        setContent {
            MaterialTheme {
                BandAnalyzerScreen(analyzer)
            }
        }

        checkPermissions()
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    private fun checkPermissions() {
        val perms = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= 33) perms.add(Manifest.permission.POST_NOTIFICATIONS)

        val notGranted = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (notGranted.isNotEmpty()) {
            requestPermissionLauncher.launch(notGranted.toTypedArray())
        }
    }
}

// 優先度②：ReferenceBands（回線対応）を保持できるように拡張
data class UiState(
    val carrier: String = "Loading...",
    val coveragePercent: Int = 0,
    val judgement: String = "-",
    val nowBands: List<String> = emptyList(),
    val observedBands: List<String> = emptyList(),
    val referenceBands: List<String> = emptyList(),
    val missingBands: List<String> = emptyList()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BandAnalyzerScreen(analyzer: BandAnalyzer) {
    val context = LocalContext.current
    var uiState by remember { mutableStateOf(UiState()) }

    LaunchedEffect(Unit) {
        while (isActive) {
            val result = withContext(Dispatchers.IO) {
                val now = analyzer.scanNowBands()
                val observed = analyzer.getObservedBands()
                val carrier = analyzer.getCarrier()
                val ref = analyzer.getCarrierReferenceBands()
                val missing = ref.minus(observed)
                val cov = analyzer.calculateCoverage()

                UiState(
                    carrier = carrier,
                    coveragePercent = cov.coveragePercent,
                    judgement = cov.judgement,
                    nowBands = now.sorted(),
                    observedBands = observed.sorted(),
                    referenceBands = ref.sorted(),
                    missingBands = missing.sorted()
                )
            }
            uiState = result
            delay(1500)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    // レポート画面へのボタン（新規追加）
                    IconButton(onClick = {
                        context.startActivity(Intent(context, CapabilityReportActivity::class.java))
                    }) {
                        Icon(
                            imageVector = Icons.Default.Assessment,
                            contentDescription = "Report",
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    // 設定画面へのボタン
                    IconButton(onClick = {
                        context.startActivity(Intent(context, SettingsActivity::class.java))
                    }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            modifier = Modifier.size(28.dp)
                        )
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
            DashboardCard(uiState)

            // 現在の接続（リアルタイム）
            BandSection(stringResource(R.string.band_now), uiState.nowBands, BandType.NOW)

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // 優先度②：端末対応・回線対応・不足の分離表示
            BandSection(stringResource(R.string.label_observed), uiState.observedBands, BandType.HISTORY)
            BandSection(stringResource(R.string.label_reference), uiState.referenceBands, BandType.REFERENCE)
            BandSection(stringResource(R.string.label_missing), uiState.missingBands, BandType.MISSING)

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun DashboardCard(state: UiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.SignalCellularAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = toJaCarrierName(state.carrier),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "${state.coveragePercent}%",
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = state.judgement,
                style = MaterialTheme.typography.titleLarge,
                color = if (state.coveragePercent > 50) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(16.dp))
            // 優先度①：対応率の視覚化
            LinearProgressIndicator(
                progress = { state.coveragePercent / 100f },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceDim,
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        }
    }
}

// 優先度②：REFERENCE（回線対応）を色のバリエーションに追加
enum class BandType { NOW, HISTORY, REFERENCE, MISSING }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BandSection(title: String, bands: List<String>, type: BandType) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
        )

        if (bands.isEmpty()) {
            Text(
                text = stringResource(R.string.band_none),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray,
                modifier = Modifier.padding(start = 8.dp)
            )
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                bands.forEach { band ->
                    BandChip(band, type)
                }
            }
        }
    }
}

@Composable
fun BandChip(text: String, type: BandType) {
    // 役割に応じてハッキリ色を分ける
    val containerColor = when (type) {
        BandType.NOW -> MaterialTheme.colorScheme.primary           // [濃い青/紫] 今まさに繋がっている！（最優先）
        BandType.HISTORY -> MaterialTheme.colorScheme.primaryContainer // [薄い青/紫] この端末で確認済み（実績）
        BandType.REFERENCE -> MaterialTheme.colorScheme.tertiaryContainer // [薄い緑/黄] この回線の仕様（目標・基準）
        BandType.MISSING -> MaterialTheme.colorScheme.surfaceVariant   // [グレー] まだ未確認（不足）
    }

    val contentColor = when (type) {
        BandType.NOW -> MaterialTheme.colorScheme.onPrimary
        BandType.HISTORY -> MaterialTheme.colorScheme.onPrimaryContainer
        BandType.REFERENCE -> MaterialTheme.colorScheme.onTertiaryContainer // 文字色もセットで変更
        BandType.MISSING -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    }

    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.height(32.dp),
        // REFERENCE（回線対応）だけ枠線を付けて「枠組み」感を出してもオシャレ
        border = if (type == BandType.REFERENCE) {
            androidx.compose.foundation.BorderStroke(1.dp, contentColor.copy(alpha = 0.3f))
        } else null
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 12.dp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

fun toJaCarrierName(label: String): String = when (label) {
    "DOCOMO" -> "docomo / AHAMO"
    "AU" -> "au / UQ / povo"
    "SOFTBANK" -> "SoftBank / Y!mobile"
    "RAKUTEN" -> "Rakuten Mobile"
    else -> label
}