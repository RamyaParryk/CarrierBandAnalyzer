package com.ratolab.carrierbandanalyzer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import com.google.android.gms.ads.MobileAds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import androidx.activity.enableEdgeToEdge

class MainActivity : AppCompatActivity() {

    private lateinit var analyzer: BandAnalyzer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // AdMob SDKの初期化
        MobileAds.initialize(this) {}
        enableEdgeToEdge()
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
                    IconButton(onClick = {
                        context.startActivity(Intent(context, CapabilityReportActivity::class.java))
                    }) {
                        Icon(imageVector = Icons.Default.Assessment, contentDescription = "Report")
                    }
                    IconButton(onClick = {
                        context.startActivity(Intent(context, SettingsActivity::class.java))
                    }) {
                        Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        // 共通化したAdBannerを使用
        bottomBar = {
            Box(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface)
                    .navigationBarsPadding() // これでナビゲーションバーとの重なりを防ぎます
            ) {
                AdBanner()
            }
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

            BandSection(stringResource(R.string.band_now), uiState.nowBands, BandType.NOW)

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

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
                // 共通化した関数を使用
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
                    // 共通化したBandChipを使用
                    BandChip(band, type)
                }
            }
        }
    }
}