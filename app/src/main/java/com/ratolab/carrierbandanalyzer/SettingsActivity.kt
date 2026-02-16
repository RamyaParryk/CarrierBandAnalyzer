package com.ratolab.carrierbandanalyzer

import android.app.ActivityManager
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Language

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val analyzer = BandAnalyzer(this)

        setContent {
            MaterialTheme {
                SettingsScreen(
                    onBack = { finish() },
                    onReset = {
                        analyzer.resetObservedBands()
                        // 文字リソースから取得
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
            // === バックグラウンド監視 ===
            SettingsSectionTitle(stringResource(R.string.sec_monitoring))
            ListItem(
                headlineContent = { Text(stringResource(R.string.item_service)) },
                supportingContent = {
                    val statusText = if (isServiceRunning) {
                        stringResource(R.string.service_running)
                    } else {
                        stringResource(R.string.service_stopped)
                    }
                    Text(statusText)
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
                },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
            )
            HorizontalDivider()

            // === データ管理 ===
            SettingsSectionTitle(stringResource(R.string.sec_data))
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

// === サポート (Support) ===
            SettingsSectionTitle(stringResource(R.string.sec_support))

            // ヘルプボタン
            ListItem(
                headlineContent = { Text(stringResource(R.string.item_help)) },
                leadingContent = {
                    Icon(Icons.AutoMirrored.Filled.Help, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                },
                modifier = Modifier.clickable { showHelpDialog = true },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
            )

            // YouTubeリンクボタン
            ListItem(
                headlineContent = { Text("YouTube (Rato Lab)") },
                supportingContent = { Text("@ramyaparryk") },
                leadingContent = {
                    Icon(Icons.Default.PlayCircle, contentDescription = null, tint = Color(0xFFFF0000)) // YouTube赤
                },
                modifier = Modifier.clickable {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/@ramyaparryk"))
                    context.startActivity(intent)
                },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
            )

            // プロジェクトWebサイト
            ListItem(
                headlineContent = { Text("Project Website") },
                supportingContent = { Text("GitHub Pages") },
                leadingContent = {
                    Icon(Icons.Default.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                },
                modifier = Modifier.clickable {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://ramyaparryk.github.io/CarrierBandAnalyzer/"))
                    context.startActivity(intent)
                },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun HelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.help_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // 1. 概要
                HelpSection(stringResource(R.string.help_sec1_title), stringResource(R.string.help_sec1_desc))

                // 2. 使い方
                HelpSection(stringResource(R.string.help_sec2_title), stringResource(R.string.help_sec2_desc))

                // 3. 主力・高速バンド (4G)
                HelpSection(stringResource(R.string.help_sec3_title), stringResource(R.string.help_sec3_desc))
                BandInfoTable(
                    listOf(
                        Triple("B1 / B3", "2.1/1.7G", stringResource(R.string.td_main_desc)),
                        Triple("B11/21", "1.5GHz", stringResource(R.string.td_sub_desc)),
                        Triple("B41", "2.5GHz", stringResource(R.string.td_high_desc)),
                        Triple("B42", "3.5GHz", stringResource(R.string.td_high_desc))
                    )
                )

                // 4. 高速通信バンド (5G)
                HelpSection(stringResource(R.string.help_sec4_title), stringResource(R.string.help_sec4_desc))
                BandInfoTable(
                    listOf(
                        Triple("n77/78", "Sub6", stringResource(R.string.td_5g_main)),
                        Triple("n79", "Sub6", stringResource(R.string.td_5g_docomo)),
                        Triple("n257", "mmWave", stringResource(R.string.td_mmwave))
                    )
                )

                // 5. プラチナバンド
                HelpSection(stringResource(R.string.help_sec5_title), stringResource(R.string.help_sec5_desc))
                BandInfoTable(
                    listOf(
                        Triple("B8", "900MHz", "SoftBank / LINEMO"),
                        Triple("B18/26", "800MHz", "au / UQ / povo"),
                        Triple("B19", "800MHz", "docomo / ahamo"),
                        Triple("B28", "700MHz", "All Carriers")
                    )
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.help_close)) }
        }
    )
}

/**
 * テーブル表示用パーツ
 */
@Composable
fun BandInfoTable(data: List<Triple<String, String, String>>) {
    val outlineColor = MaterialTheme.colorScheme.outlineVariant
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, outlineColor, RoundedCornerShape(8.dp))
    ) {
        Row(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                .padding(8.dp)
        ) {
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
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = content,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp),
            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.2f
        )
    }
}

// === 以下、ヘルパー関数 ===

private fun startBandService(context: Context) {
    val intent = Intent(context, BandMonitorService::class.java)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
    } else {
        context.startService(intent)
    }
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
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
    )
}

@Composable
fun SettingsItem(title: String, description: String, onClick: () -> Unit, isDestructive: Boolean = false) {
    ListItem(
        headlineContent = { Text(title, color = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface) },
        supportingContent = { Text(description) },
        modifier = Modifier.clickable { onClick() }
    )
}