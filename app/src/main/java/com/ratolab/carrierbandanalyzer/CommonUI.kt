package com.ratolab.carrierbandanalyzer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

// 共通の列挙型
enum class BandType { NOW, HISTORY, REFERENCE, MISSING }

/**
 * 共通バナー広告
 */
@Composable
fun AdBanner(modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { context ->
            AdView(context).apply {
                // local.propertiesから注入されたIDを使用
                setAdUnitId(BuildConfig.BANNER_ID)
                setAdSize(AdSize.BANNER)
                loadAd(AdRequest.Builder().build())
            }
        }
    )
}

/**
 * 共通のバンドチップデザイン
 */
@Composable
fun BandChip(text: String, type: BandType) {
    val containerColor = when (type) {
        BandType.NOW -> MaterialTheme.colorScheme.primary
        BandType.HISTORY -> MaterialTheme.colorScheme.primaryContainer
        BandType.REFERENCE -> MaterialTheme.colorScheme.tertiaryContainer
        BandType.MISSING -> MaterialTheme.colorScheme.surfaceVariant
    }

    val contentColor = when (type) {
        BandType.NOW -> MaterialTheme.colorScheme.onPrimary
        BandType.HISTORY -> MaterialTheme.colorScheme.onPrimaryContainer
        BandType.REFERENCE -> MaterialTheme.colorScheme.onTertiaryContainer
        BandType.MISSING -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    }

    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.height(32.dp),
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

/**
 * 共通のキャリア名変換
 */
fun toJaCarrierName(label: String): String = when (label) {
    "DOCOMO" -> "docomo / AHAMO"
    "AU" -> "au / UQ / povo"
    "SOFTBANK" -> "SoftBank / Y!mobile"
    "RAKUTEN" -> "Rakuten Mobile"
    else -> label
}