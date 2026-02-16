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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

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
                        Toast.makeText(this, "履歴を消去しました", Toast.LENGTH_SHORT).show()
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
                title = { Text("設定") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
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
            SettingsSectionTitle("バックグラウンド監視")
            ListItem(
                headlineContent = { Text("常駐監視サービス") },
                supportingContent = { Text(if (isServiceRunning) "実行中 (通知を表示中)" else "停止中") },
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

            SettingsSectionTitle("データ管理")
            SettingsItem(
                title = "観測履歴をリセット",
                description = "これまで記録したバンド情報をすべて消去します",
                onClick = onReset,
                isDestructive = true
            )
            HorizontalDivider()

            SettingsSectionTitle("システム設定")
            SettingsItem(
                title = "権限設定を開く",
                description = "位置情報や通知の権限を確認・変更します",
                onClick = onOpenPermissionSettings
            )
            HorizontalDivider()

            SettingsSectionTitle("サポート")
            ListItem(
                headlineContent = { Text("ヘルプ・使い方") },
                leadingContent = {
                    Icon(Icons.AutoMirrored.Filled.Help, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                },
                modifier = Modifier.clickable { showHelpDialog = true },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// ... (他のimportは既存のまま)

// ... (他の部分は変更なし)

// ... (package, imports は既存のまま)

@Composable
fun HelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ヘルプ・使い方") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // 1. 概要：可視化ツールとしての目的を強調
                HelpSection("1. アプリの概要",
                    "本アプリは、日本の携帯キャリア（ドコモ・au・SB・楽天）および、ahamo・povo・LINEMO等の各回線が、今どの周波数を使って通信しているかを**「可視化」**するツールです。\n\n" +
                            "アンテナの本数だけでは分からない、通信の混雑状況やエリアの真の品質を把握することを目的としています。")

                HelpSection("2. 使い方と色の意味",
                    "• 起動でスキャン開始。常駐監視で移動中も記録します。\n\n" +
                            "🟦 **濃い色**: 今まさに通信中のバンド (Active)\n" +
                            "⬜ **薄い色**: 既に観測した履歴 (History)\n" +
                            "⬛ **グレー**: 未検出の対応バンド (Missing)")

                // 3. 主力・高速バンド (4G/LTE)
                HelpSection("3. 主力・高速バンド (4G/LTE)", "速度の要となる、各社のメインバンドです。")
                BandInfoTable(
                    listOf(
                        Triple("B1 / B3", "2.1/1.7G", "各社の主力。高速だが障害物に並"),
                        Triple("B11/21", "1.5GHz", "補助用。B21はドコモ、B11はau/SB"),
                        Triple("B41", "2.5GHz", "SB(AXGP) / au(UQ等)。高速"),
                        Triple("B42", "3.5GHz", "4G最強バンド。非常に高速")
                    )
                )

                // 4. 高速通信バンド (5G)
                HelpSection("4. 高速通信バンド (5G)", "次世代の超高速周波数帯です。")
                BandInfoTable(
                    listOf(
                        Triple("n77/78", "Sub6", "5Gのメイン。n77は各社、n78はドコモ/au"),
                        Triple("n79", "Sub6", "ドコモ専用の高速5G帯域"),
                        Triple("n257", "ミリ波", "超爆速だが遮蔽物に極めて弱い")
                    )
                )

                // 5. プラチナバンド
                HelpSection("5. プラチナバンド", "「繋がりやすさ」を支える、建物内や地下に強い重要な電波です。")
                BandInfoTable(
                    listOf(
                        Triple("B8", "900MHz", "ソフトバンク / LINEMO"),
                        Triple("B18/26", "800MHz", "au / UQ / povo"),
                        Triple("B19", "800MHz", "docomo / ahamo"),
                        Triple("B28", "700MHz", "各社 (楽天モバイル含む)")
                    )
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("閉じる") }
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
            Text("バンド", Modifier.weight(1f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
            Text("周波数", Modifier.weight(1f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
            Text("特徴/キャリア", Modifier.weight(1.8f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
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