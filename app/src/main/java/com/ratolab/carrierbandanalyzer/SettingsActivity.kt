package com.ratolab.carrierbandanalyzer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.activity.enableEdgeToEdge

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val analyzer = BandAnalyzer(this, intent.getIntExtra("subscription_id", android.telephony.SubscriptionManager.INVALID_SUBSCRIPTION_ID))

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
    var isServiceActive by remember { mutableStateOf(isServiceRunning(context)) }

    var showUsageDialog by remember { mutableStateOf(false) }
    var showFaqDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }

    if (showUsageDialog) UsageDialog(onDismiss = { showUsageDialog = false })
    if (showFaqDialog) FaqDialog(onDismiss = { showFaqDialog = false })
    if (showLanguageDialog) LanguageSelectionDialog(onDismiss = { showLanguageDialog = false })

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
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface)
                    .navigationBarsPadding()
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
        ) {
            // === 監視状態 ===
            SettingsSectionTitle(stringResource(R.string.sec_monitoring))
            SettingsCard {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.item_service)) },
                    supportingContent = { Text(if (isServiceActive) stringResource(R.string.status_on) else stringResource(R.string.status_off)) },
                    leadingContent = { Icon(Icons.Default.Visibility, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    trailingContent = {
                        Switch(
                            checked = isServiceActive,
                            onCheckedChange = { check ->
                                if (check) {
                                    startBandService(context)
                                    isServiceActive = true
                                } else {
                                    stopBandService(context)
                                    isServiceActive = false
                                }
                            }
                        )
                    }
                )
            }

            // === データ管理 ===
            SettingsSectionTitle(stringResource(R.string.sec_data))
            SettingsCard {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.action_share_report)) },
                    supportingContent = { Text(stringResource(R.string.label_copy_to_clipboard)) },
                    leadingContent = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                    modifier = Modifier.clickable { copyReportToClipboard(context, analyzer) }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                ListItem(
                    headlineContent = { Text(stringResource(R.string.item_export_log)) },
                    supportingContent = { Text("band_logs.csv") },
                    leadingContent = { Icon(Icons.Default.Share, contentDescription = null) },
                    modifier = Modifier.clickable { shareLogFile(context, analyzer.getLogFile()) }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                ListItem(
                    headlineContent = { Text(stringResource(R.string.item_reset_title), color = MaterialTheme.colorScheme.error) },
                    supportingContent = { Text(stringResource(R.string.item_reset_desc)) },
                    leadingContent = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    modifier = Modifier.clickable { onReset() }
                )
            }

            // === システム設定 ===
            SettingsSectionTitle(stringResource(R.string.sec_system))
            SettingsCard {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.item_language_title)) },
                    supportingContent = { Text(stringResource(R.string.item_language_desc)) },
                    leadingContent = { Icon(Icons.Default.Language, contentDescription = null) },
                    modifier = Modifier.clickable { showLanguageDialog = true }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                ListItem(
                    headlineContent = { Text(stringResource(R.string.item_perm_title)) },
                    supportingContent = { Text(stringResource(R.string.item_perm_desc)) },
                    leadingContent = { Icon(Icons.Default.Security, contentDescription = null) },
                    modifier = Modifier.clickable { onOpenPermissionSettings() }
                )
            }

            // === サポート ===
            SettingsSectionTitle(stringResource(R.string.sec_support))
            SettingsCard {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.item_usage)) },
                    leadingContent = { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable { showUsageDialog = true }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                ListItem(
                    headlineContent = { Text(stringResource(R.string.item_faq)) },
                    leadingContent = { Icon(Icons.Default.QuestionAnswer, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable { showFaqDialog = true }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                ListItem(
                    headlineContent = { Text("Project Website") },
                    supportingContent = { Text("GitHub Pages") },
                    leadingContent = { Icon(Icons.Default.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://ramyaparryk.github.io/CarrierBandAnalyzer/")))
                    }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = "Ver ${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(content = content)
    }
}

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
        Coverage ($carrierName): ${coverage.coveragePercent?.let { "$it%" } ?: "N/A"}
        ${context.getString(R.string.report_footer)}
    """.trimIndent()

    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Band Report", reportText)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, context.getString(R.string.msg_copied), Toast.LENGTH_SHORT).show()
}

@Composable
fun UsageDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.item_usage)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                HelpSection(stringResource(R.string.help_sec1_title), stringResource(R.string.help_sec1_desc))
                HelpSection(stringResource(R.string.help_sec2_title), stringResource(R.string.help_sec2_desc))
                HelpSection(stringResource(R.string.help_sec_share_title), stringResource(R.string.help_sec_share_desc))
                HelpSection(stringResource(R.string.help_sec_global_title), stringResource(R.string.help_sec_global_desc))
                HelpSection(stringResource(R.string.help_sec_graph_title), stringResource(R.string.help_sec_graph_desc))
                HelpSection(stringResource(R.string.help_sec3_title), stringResource(R.string.help_sec3_desc))
                BandInfoTable(
                    listOf(
                        Triple("B1 / B3", "2.1/1.7G", stringResource(R.string.td_main_desc)),
                        Triple("B11/21", "1.5GHz", stringResource(R.string.td_sub_desc)),
                        Triple("B41", "2.5GHz", stringResource(R.string.td_high_desc)),
                        Triple("B42", "3.5GHz", stringResource(R.string.td_high_desc))
                    )
                )
                HelpSection(stringResource(R.string.help_sec4_title), stringResource(R.string.help_sec4_desc))
                BandInfoTable(
                    listOf(
                        Triple("n77/78", "Sub6", stringResource(R.string.td_5g_main)),
                        Triple("n79", "Sub6", stringResource(R.string.td_5g_docomo)),
                        Triple("n257", "mmWave", stringResource(R.string.td_mmwave))
                    )
                )
                HelpSection(stringResource(R.string.help_sec5_title), stringResource(R.string.help_sec5_desc))
                BandInfoTable(
                    listOf(
                        Triple("B8", "900MHz", "SoftBank / LINEMO"),
                        Triple("B18/26", "800MHz", "au / UQ / povo"),
                        Triple("B19", "800MHz", "docomo / ahamo"),
                        Triple("B28", "700MHz", "All Carriers"),
                    )
                )
                HelpSection(stringResource(R.string.help_sec6_title), stringResource(R.string.help_sec6_desc))
                BandInfoTable(
                    listOf(
                        Triple("n1 / n3", "2.1/1.7G", stringResource(R.string.td_5g_diverted)),
                        Triple("n28", "700MHz", stringResource(R.string.td_5g_diverted))
                    )
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.help_close)) } }
    )
}

@Composable
fun FaqDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.faq_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                HelpSection(stringResource(R.string.faq_q1), stringResource(R.string.faq_a1))
                HelpSection(stringResource(R.string.faq_q2), stringResource(R.string.faq_a2))
                HelpSection(stringResource(R.string.faq_q3), stringResource(R.string.faq_a3))
                HelpSection(stringResource(R.string.faq_q4), stringResource(R.string.faq_a4))
                HelpSection(stringResource(R.string.faq_q5), stringResource(R.string.faq_a5))
                HelpSection(stringResource(R.string.faq_q6), stringResource(R.string.faq_a6))
                HelpSection(stringResource(R.string.faq_q7), stringResource(R.string.faq_a7))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.help_close)) } }
    )
}

@Composable
fun LanguageSelectionDialog(onDismiss: () -> Unit) {
    val languages = listOf(
        "ja" to "日本語",
        "en" to "English",
        "es" to "Español",
        "de" to "Deutsch",
        "ru" to "Русский",
        "zh" to "中文",
        "ko" to "한국어",
        "hi" to "हिन्दी",
        "fr" to "Français",
        "vi" to "Tiếng Việt", // ベトナム語
        "th" to "ไทย", // タイ語
        "ar" to "العربية", // アラビア語
        "fa" to "فارسی", // ペルシャ語
        "tr" to "Türkçe" // トルコ語
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.item_language_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                languages.forEach { (tag, name) ->
                    TextButton(
                        onClick = {
                            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(text = name, style = MaterialTheme.typography.bodyLarge) }
                }
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

@Composable
fun SettingsSectionTitle(title: String) {
    Text(text = title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 24.dp, top = 24.dp, bottom = 8.dp))
}