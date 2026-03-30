package com.ratolab.carrierbandanalyzer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource // ★このインポートが足りていませんでした！
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class StatisticsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val analyzer = BandAnalyzer(this)

        setContent {
            MaterialTheme {
                StatisticsScreen(analyzer, onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(analyzer: BandAnalyzer, onBack: () -> Unit) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    // ★すべて stringResource に置き換え
    val tabs = listOf(
        stringResource(R.string.stat_tab_today),
        stringResource(R.string.stat_tab_week),
        stringResource(R.string.stat_tab_month),
        stringResource(R.string.stat_tab_all)
    )
    val periods = listOf(
        BandAnalyzer.StatPeriod.TODAY,
        BandAnalyzer.StatPeriod.WEEK,
        BandAnalyzer.StatPeriod.MONTH,
        BandAnalyzer.StatPeriod.ALL
    )

    var statsData by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }

    // タブが切り替わるたびに裏でCSVを集計
    LaunchedEffect(selectedTabIndex) {
        withContext(Dispatchers.IO) {
            statsData = analyzer.getBandStatistics(periods[selectedTabIndex])
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.stat_title)) }, // ★修正
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
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
        ) {
            TabRow(selectedTabIndex = selectedTabIndex) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title, fontWeight = FontWeight.Bold) }
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (statsData.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.stat_no_data), color = MaterialTheme.colorScheme.outline) // ★修正
                    }
                } else {
                    Text(stringResource(R.string.stat_ranking), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary) // ★修正
                    HorizontalBarChart(data = statsData)
                }
            }
        }
    }
}

@Composable
fun HorizontalBarChart(data: Map<String, Int>) {
    // 回数が多い順にソート
    val sortedData = data.toList().sortedByDescending { it.second }
    val maxCount = sortedData.maxOfOrNull { it.second } ?: 1

    // 全体の合計回数を計算（0割りを防ぐために最低値1を保証）
    val totalCount = sortedData.sumOf { it.second }.coerceAtLeast(1)

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        sortedData.forEach { (band, count) ->
            // アニメーション付きのバー幅計算
            val targetFraction = count.toFloat() / maxCount.toFloat()
            val animatedFraction by animateFloatAsState(
                targetValue = targetFraction,
                animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
                label = "barAnimation"
            )

            // 割合(%)と、カンマ区切りの回数を作成
            val percentage = (count.toFloat() / totalCount.toFloat()) * 100f
            // 小数点第1位まで表示（例: 25.4%）
            val formattedPercent = String.format(java.util.Locale.US, "%.1f%%", percentage)
            val formattedCount = java.text.NumberFormat.getNumberInstance().format(count)

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                // 左側: バンド名
                Text(
                    text = band,
                    modifier = Modifier.width(48.dp),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                // 中央: グラフのバー
                Box(modifier = Modifier.weight(1f).height(24.dp)) {
                    // 背景の薄いバー
                    Box(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant))
                    // 前景の色のついたバー（アニメーションで伸びる）
                    Box(modifier = Modifier.fillMaxWidth(animatedFraction).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.primary))
                }
                // 右側: 回数とパーセンテージ（1行表示）
                Text(
                    // ★ここで、言語ファイルに2つのデータ（回数と％）を同時に渡しています！
                    text = stringResource(R.string.stat_count_format, formattedCount, formattedPercent),
                    modifier = Modifier.padding(start = 12.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.End
                )
            }
        }
    }
}