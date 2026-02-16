package com.ratolab.carrierbandanalyzer

import android.app.ActivityManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.ContentCopy // コピーアイコン
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val analyzer = BandAnalyzer(this)

        setContent {
            MaterialTheme {
                SettingsScreen(
                    analyzer = analyzer,
                    onBack = { finish() },
                    onReset = {
                        analyzer.resetObservedBands()
                        val msg = getString(R.string.msg_history_cleared)
                        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                        finish()
                    },
                    onOpenPermissionSettings = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", packageName, null)
                        }
                        startActivity(intent)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    analyzer: BandAnalyzer,
    onBack: () -> Unit,
    onReset: () -> Unit,
    onOpenPermissionSettings: () -> Unit
) {
    val context = LocalContext.current
    var isServiceRunning by remember { mutableStateOf(isServiceRunning(context)) }
    var showHelpDialog by remember { mutableStateOf(false) }

    if (showHelpDialog) {
        HelpDialog(onDismiss = { showHelpDialog = false })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // === 監視状態 ===
            SettingsSectionTitle(stringResource(R.string.sec_monitoring))
            ListItem(
                headlineContent = { Text(stringResource(R.string.item_service)) },
                supportingContent = {
                    Text(if (isServiceRunning) stringResource(R.string.status_on) else stringResource(R.string.status_off))
                },
                trailingContent = {
                    Switch(
                        checked = isServiceRunning,
                        onCheckedChange = { check ->
                            if (check) {
                                startBandService(context)
                                isServiceRunning = true
                            } else {
                                stopBandService(context)
                                isServiceRunning = false
                            }
                        }
                    )
                }
            )
            HorizontalDivider()

            // === データ管理 (コピー＆共有) ===
            SettingsSectionTitle(stringResource(R.string.sec_data))

            // 1. レポートをコピー (新規追加：テキスト形式)
            ListItem(
                headlineContent = { Text(stringResource(R.string.action_share_report)) },
                supportingContent = { Text(stringResource(R.string.label_copy_to_clipboard)) },
                leadingContent = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                modifier = Modifier.clickable {
                    copyReportToClipboard(context, analyzer)
                }
            )

            // 2. CSV出力
            ListItem(
                headlineContent = { Text(stringResource(R.string.item_export_log)) },
                supportingContent = { Text("band_logs.csv") },
                leadingContent = { Icon(Icons.Default.Share, contentDescription = null) },
                modifier = Modifier.clickable {
                    exportLogFile(context, analyzer)
                }
            )

            // 3. リセット
            SettingsItem(
                title = stringResource(R.string.item_reset_title),
                description = stringResource(R.string.item_reset_desc),
                onClick = onReset,
                isDestructive = true
            )
            HorizontalDivider()

            // === システム設定 ===
            SettingsSectionTitle(stringResource(R.string.sec_system))
            SettingsItem(
                title = stringResource(R.string.item_perm_title),
                description = stringResource(R.string.item_perm_desc),
                onClick = onOpenPermissionSettings
            )
            HorizontalDivider()

            // === サポート ===
            SettingsSectionTitle(stringResource(R.string.sec_support))
            ListItem(
                headlineContent = { Text(stringResource(R.string.item_help)) },
                leadingContent = { Icon(Icons.AutoMirrored.Filled.Help, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                modifier = Modifier.clickable { showHelpDialog = true }
            )
            ListItem(
                headlineContent = { Text("YouTube (Rato Lab)") },
                supportingContent = { Text("@ramyaparryk") },
                leadingContent = { Icon(Icons.Default.PlayCircle, contentDescription = null, tint = Color(0xFFFF0000)) },
                modifier = Modifier.clickable {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/@ramyaparryk")))
                }
            )
            ListItem(
                headlineContent = { Text("Project Website") },
                supportingContent = { Text("GitHub Pages") },
                leadingContent = { Icon(Icons.Default.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                modifier = Modifier.clickable {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://ramyaparryk.github.io/CarrierBandAnalyzer/")))
                }
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/**
 * クリップボードにレポートテキストをコピーする関数
 */
private fun copyReportToClipboard(context: Context, analyzer: BandAnalyzer) {
    val observed = analyzer.getObservedBands()
    val coverage = analyzer.calculateCoverage()
    val deviceName = Build.MODEL

    val lteBands = observed.filter { it.startsWith("B") }.sorted().joinToString(", ")
    val nrBands = observed.filter { it.startsWith("n") }.sorted().joinToString(", ")
    val carrierName = toJaCarrierName(coverage.carrier)

    val reportText = """
        [${context.getString(R.string.report_title)}]
        Device: $deviceName
        LTE: ${lteBands.ifEmpty { "None" }}
        NR: ${nrBands.ifEmpty { "None" }}
        Coverage ($carrierName): ${coverage.coveragePercent}%
        ${context.getString(R.string.report_footer)}
    """.trimIndent()

    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Band Report", reportText)
    clipboard.setPrimaryClip(clip)

    Toast.makeText(context, context.getString(R.string.msg_copied), Toast.LENGTH_SHORT).show()
}

/**
 * CSVファイルを共有する関数
 */
private fun exportLogFile(context: Context, analyzer: BandAnalyzer) {
    val logFile = analyzer.getLogFile()
    if (!logFile.exists() || logFile.length() == 0L) {
        Toast.makeText(context, "Log file is empty", Toast.LENGTH_SHORT).show()
        return
    }
    try {
        val contentUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", logFile)
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

// ... (HelpDialog などのパーツは変更なし) ...

@Composable
fun HelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.help_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                HelpSection(stringResource(R.string.help_sec1_title), stringResource(R.string.help_sec1_desc))
                HelpSection(stringResource(R.string.help_sec2_title), stringResource(R.string.help_sec2_desc))
                HelpSection(stringResource(R.string.help_sec3_title), stringResource(R.string.help_sec3_desc))
                BandInfoTable(listOf(Triple("B1 / B3", "2.1/1.7G", stringResource(R.string.td_main_desc)), Triple("B11/21", "1.5GHz", stringResource(R.string.td_sub_desc)), Triple("B41", "2.5GHz", stringResource(R.string.td_high_desc)), Triple("B42", "3.5GHz", stringResource(R.string.td_high_desc))))
                HelpSection(stringResource(R.string.help_sec4_title), stringResource(R.string.help_sec4_desc))
                BandInfoTable(listOf(Triple("n77/78", "Sub6", stringResource(R.string.td_5g_main)), Triple("n79", "Sub6", stringResource(R.string.td_5g_docomo)), Triple("n257", "mmWave", stringResource(R.string.td_mmwave))))
                HelpSection(stringResource(R.string.help_sec5_title), stringResource(R.string.help_sec5_desc))
                BandInfoTable(listOf(Triple("B8", "900MHz", "SoftBank / LINEMO"), Triple("B18/26", "800MHz", "au / UQ / povo"), Triple("B19", "800MHz", "docomo / ahamo"), Triple("B28", "700MHz", "All Carriers")))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.help_close)) } }
    )
}

@Composable
fun BandInfoTable(data: List<Triple<String, String, String>>) {
    val outlineColor = MaterialTheme.colorScheme.outlineVariant
    Column(modifier = Modifier.fillMaxWidth().border(1.dp, outlineColor, RoundedCornerShape(8.dp))) {
        Row(modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)).padding(8.dp)) {
            Text(stringResource(R.string.th_band), Modifier.weight(1f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
            Text(stringResource(R.string.th_freq), Modifier.weight(1f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
            Text(stringResource(R.string.th_detail), Modifier.weight(1.8f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
        }
        data.forEach { (band, freq, detail) ->
            HorizontalDivider(color = outlineColor)
            Row(modifier = Modifier.padding(8.dp)) {
                Text(band, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                Text(freq, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                Text(detail, Modifier.weight(1.8f), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun HelpSection(title: String, content: String) {
    Column {
        Text(text = title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Text(text = content, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp), lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.2f)
    }
}

private fun startBandService(context: Context) {
    val intent = Intent(context, BandMonitorService::class.java)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
}

private fun stopBandService(context: Context) {
    context.stopService(Intent(context, BandMonitorService::class.java))
}

@Suppress("DEPRECATION")
private fun isServiceRunning(context: Context): Boolean {
    val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    for (service in manager.getRunningServices(Int.MAX_VALUE)) {
        if (BandMonitorService::class.java.name == service.service.className) return true
    }
    return false
}

@Composable
fun SettingsSectionTitle(title: String) {
    Text(text = title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp))
}

@Composable
fun SettingsItem(title: String, description: String, onClick: () -> Unit, isDestructive: Boolean = false) {
    ListItem(
        headlineContent = { Text(title, color = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface) },
        supportingContent = { Text(description) },
        modifier = Modifier.clickable { onClick() }
    )
}