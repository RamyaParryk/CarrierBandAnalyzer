package com.ratolab.carrierbandanalyzer

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.CellIdentityNr
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.ratolab.carrierbandanalyzer.data.BandRepository
import kotlin.math.roundToInt

class BandAnalyzer(context: Context, val subscriptionId: Int = SubscriptionManager.INVALID_SUBSCRIPTION_ID) {

    private val appContext = context.applicationContext
    private val telephonyManager =
        (appContext.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager)
            .let { if (subscriptionId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) it.createForSubscriptionId(subscriptionId) else it }

    private val repository = BandRepository.get(appContext)

    // キャリアごとのバンド定義 (initより前に初期化する)
    private val carrierBands = mapOf(
        "DOCOMO" to setOf("B1","B3","B19","B21","B28","B42","n1","n28","n77","n78","n79","n257"),
        "AU" to setOf("B1","B3","B18","B26","B28","B41","B42","n1","n3","n28","n77","n78","n257"),
        "SOFTBANK" to setOf("B1","B3","B8","B11","B28","B41","B42","n1","n3","n28","n77","n78","n257"),
        "RAKUTEN" to setOf("B3","B18","B26","B28","n28","n77","n257"),
        "AHAMO" to setOf("B1","B3","B19","B21","B28","B42","n1","n28","n77","n78","n79","n257"),
        "POVO" to setOf("B1","B3","B18","B26","B28","B41","B42","n1","n3","n28","n77","n78","n257"),
        "UQ" to setOf("B1","B3","B18","B26","B28","B41","B42","n1","n3","n28","n77","n78","n257"),
        "LINEMO" to setOf("B1","B3","B8","B11","B28","B41","B42","n1","n3","n28","n77","n78","n257"),
        "Y!MOBILE" to setOf("B1","B3","B8","B11","B28","B41","B42","n1","n3","n28","n77","n78","n257")
    )

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun displayName(): String = SimRepository.active(appContext)
        .firstOrNull { it.subscriptionId == subscriptionId }?.displayName.orEmpty()

    fun getCarrier(): String = detectCarrierLabel()

    fun getCarrierReferenceBands(): Set<String> = carrierBands[detectCarrierLabel()].orEmpty()

    /** Call on Dispatchers.IO (especially from UI). */
    fun getObservedBands(): Set<String> = repository.observed(subscriptionId)

    @SuppressLint("MissingPermission")
    fun scanNowBands(): Set<String> {
        if (!hasLocationPermission()) return emptySet()

        val now = mutableSetOf<String>()

        // サブスクリプション（回線）ごとのTelephonyManagerでクエリを実行する（回線が特定できないPCCデータは混ぜない）。
        // 近隣セル（ネイバーセル）を除外するため、現在登録（接続）されているセルのみを接続中バンドとして扱う。
        val cells = try { telephonyManager.allCellInfo.orEmpty().filter { it.isRegistered } }
        catch (_: SecurityException) { emptyList() }
        catch (_: RuntimeException) { emptyList() }
        for (cell in cells) {
            if (cell is CellInfoLte) {
                val id = cell.cellIdentity
                val bands = getIntArrayViaReflection(id, "getBands")
                if (bands != null && bands.isNotEmpty()) {
                    for (b in bands) now.add("B$b")
                } else {
                    convertEarfcnToBand(id.earfcn)?.let { now.add(it) }
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && cell is CellInfoNr) {
                try {
                    val id = cell.cellIdentity as CellIdentityNr
                    val bands = getIntArrayViaReflection(id, "getBands")
                    if (bands != null && bands.isNotEmpty()) {
                        for (b in bands) now.add("n$b")
                    } else {
                        convertNrArfcnToBand(id.nrarfcn)?.let { now.add(it) }
                    }
                } catch (_: Throwable) {}
            }
        }

        // PhysicalChannelConfigのリフレクション取得は意図的に省略：
        // AndroidのメーカーやAPIの実装によっては、どのSIM回線に属するかの判定が保証されないため。
        if (now.isNotEmpty()) repository.observe(subscriptionId, detectCarrierLabel(), displayName(), now)
        return now
    }

    @SuppressLint("MissingPermission")
    fun getCaChannelDebugList(): List<CaChannelDebug> {
        if (!hasLocationPermission()) return emptyList()

        val out = mutableListOf<CaChannelDebug>()
        val listAny: List<Any> = getPhysicalChannelConfigListViaReflection()
        for (cfg in listAny) {
            val networkType = getIntViaReflection(cfg, "getNetworkType") ?: -1
            val channel = getIntViaReflection(cfg, "getChannelNumber") ?: -1
            val bandDirect = getIntViaReflection(cfg, "getBand")
            if (networkType == -1 && channel == -1 && bandDirect == null) continue
            out.add(
                CaChannelDebug(
                    networkType = networkType,
                    networkTypeName = networkTypeToName(networkType),
                    channelNumber = channel,
                    bandDirect = bandDirect
                )
            )
        }
        return out
    }

    fun calculateCoverage(): CoverageResult {
        val carrier = detectCarrierLabel()
        val ref = carrierBands[carrier].orEmpty()
        val currentObserved = getObservedBands()

        val covered = currentObserved.intersect(ref).size
        val total = ref.size
        val percent: Int? = if (total == 0 || currentObserved.isEmpty()) null else ((covered.toDouble() / total) * 100.0).roundToInt()

        val judgement = when {
            percent == null -> appContext.getString(if (total == 0) R.string.sim_no_reference else R.string.sim_waiting)
            percent >= 70 -> appContext.getString(R.string.judgement_excellent)
            percent >= 50 -> appContext.getString(R.string.judgement_good)
            percent >= 30 -> appContext.getString(R.string.judgement_fair)
            percent >= 15 -> appContext.getString(R.string.judgement_poor_slight)
            else -> appContext.getString(R.string.judgement_poor)
        }

        return CoverageResult(
            carrier = carrier,
            observedBands = currentObserved,
            carrierBands = ref,
            coveredCount = covered,
            totalCount = total,
            coveragePercent = percent,
            judgement = judgement
        )
    }

    /** Delete only the selected SIM's database history. */
    fun resetObservedBands() = repository.reset(subscriptionId)

    /** One sample per monitor tick; do not deduplicate counts across ticks. */
    fun saveLog(bands: Set<String>) {
        if (bands.isNotEmpty()) repository.log(subscriptionId, detectCarrierLabel(), displayName(), bands)
    }

    /** Generates the familiar 3-column CSV only on export. */
    fun exportLogCsv() = repository.exportCsv(subscriptionId)

    @SuppressLint("MissingPermission")
    private fun detectCarrierLabel(): String {
        val tokens = mutableListOf<String>()
        fun add(s: String?) { if (!s.isNullOrBlank()) tokens += s }
        // ① SIMや電波から名前をかき集める
        add(telephonyManager.simOperatorName)
        add(telephonyManager.networkOperatorName)
        add(getStringViaReflection(telephonyManager, "getSimCarrierIdName"))
        add(getStringViaReflection(telephonyManager, "getCarrierIdName"))
        val op = telephonyManager.simOperator.orEmpty()
        val hay = tokens.joinToString(" | ").lowercase()

        // ② 日本の主要キャリア・サブブランドを文字列から判定
        if (hay.contains("ahamo")) return "AHAMO"
        if (hay.contains("povo")) return "POVO"
        if (hay.contains("uq mobile")) return "UQ"
        if (hay.contains("linemo")) return "LINEMO"
        if (hay.contains("ymobile") || hay.contains("y!mobile") || hay.contains("ワイモバ")) return "Y!MOBILE"
        if (hay.contains("docomo") || hay.contains("ドコモ")) return "DOCOMO"
        if (hay.contains("kddi") || tokens.any { it.equals("au", ignoreCase = true) }) return "AU"
        if (hay.contains("softbank") || hay.contains("ソフトバンク")) return "SOFTBANK"
        if (hay.contains("rakuten") || hay.contains("楽天")) return "RAKUTEN"

        // かき集めた名前の中で一番最初の有効なものを、大文字にして取得（例：T-MOBILE）
        val rawCarrierName = tokens.firstOrNull()?.uppercase() ?: "UNKNOWN"

        // ③ PLMNコードで日本の主要キャリアを最終判定。それでもダメなら「生のキャリア名」を返す！
        return when {
            op == "44010" -> "DOCOMO"
            op == "44011" -> "RAKUTEN"
            op == "44050" || op == "44051" -> "AU"
            op == "44020" || op == "44021" -> "SOFTBANK"
            else -> rawCarrierName // "UNKNOWN" ではなく生のキャリア名を返す
        }
    }

    private fun getStringViaReflection(obj: Any, methodName: String): String? {
        return try {
            val m = obj.javaClass.getMethod(methodName)
            val v = m.invoke(obj)
            v as? CharSequence
        } catch (_: Throwable) { null }?.toString()
    }

    private fun getBandsFromPhysicalChannelConfigs(): Set<String> {
        val out = mutableSetOf<String>()
        for (ca in getCaChannelDebugList()) {
            val bd = ca.bandDirect
            if (bd != null && bd > 0) {
                when (ca.networkTypeName) {
                    "NR" -> out.add("n$bd")
                    "LTE", "LTE_CA" -> out.add("B$bd")
                }
                continue
            }
            val ch = ca.channelNumber
            if (ch <= 0) continue
            when (ca.networkTypeName) {
                "NR" -> convertNrArfcnToBand(ch)?.let { out.add(it) }
                "LTE", "LTE_CA" -> convertEarfcnToBand(ch)?.let { out.add(it) }
            }
        }
        return out
    }

    private fun getPhysicalChannelConfigListViaReflection(): List<Any> {
        return try {
            val m = telephonyManager.javaClass.getMethod("getPhysicalChannelConfigList")
            val v = m.invoke(telephonyManager)
            @Suppress("UNCHECKED_CAST")
            (v as? List<Any>) ?: emptyList()
        } catch (_: Throwable) { emptyList() }
    }

    private fun getIntViaReflection(obj: Any, methodName: String): Int? {
        return try {
            val m = obj.javaClass.getMethod(methodName)
            val v = m.invoke(obj)
            (v as? Int)
        } catch (_: Throwable) { null }
    }

    private fun getIntArrayViaReflection(obj: Any, methodName: String): IntArray? {
        return try {
            val m = obj.javaClass.getMethod(methodName)
            val v = m.invoke(obj)
            v as? IntArray
        } catch (_: Throwable) { null }
    }

    private fun networkTypeToName(type: Int): String = when (type) {
        13 -> "LTE"
        19 -> "LTE_CA"
        20 -> "NR"
        else -> "TYPE_$type"
    }

    private fun convertEarfcnToBand(earfcn: Int): String? = when (earfcn) {
        in 0..599 -> "B1"
        in 600..1199 -> "B2"
        in 1200..1949 -> "B3"
        in 1950..2399 -> "B4"
        in 2400..2649 -> "B5"
        in 2650..2749 -> "B6"
        in 2750..3449 -> "B7"
        in 3450..3799 -> "B8"
        in 3800..4149 -> "B9"
        in 4150..4749 -> "B10"
        in 4750..4949 -> "B11"
        in 5010..5179 -> "B12"
        in 5180..5279 -> "B13"
        in 5280..5379 -> "B14"
        in 5730..5849 -> "B17"
        in 5850..5999 -> "B18"
        in 6000..6149 -> "B19"
        in 6150..6449 -> "B20"
        in 6450..6599 -> "B21"
        in 6600..7399 -> "B22"
        in 7500..7699 -> "B23"
        in 7700..8039 -> "B24"
        in 8040..8689 -> "B25"
        in 8690..9039 -> "B26"
        in 9040..9209 -> "B27"
        in 9210..9659 -> "B28"
        in 9660..9769 -> "B29"
        in 9770..9869 -> "B30"
        in 9870..9919 -> "B31"
        in 9920..10359 -> "B32"
        in 36000..36199 -> "B33"
        in 36200..36349 -> "B34"
        in 36350..36949 -> "B35"
        in 36950..37549 -> "B36"
        in 37550..37749 -> "B37"
        in 37750..38249 -> "B38"
        in 38250..38649 -> "B39"
        in 38650..39649 -> "B40"
        in 39650..41589 -> "B41"
        in 41590..43589 -> "B42"
        in 43590..45589 -> "B43"
        in 46590..46789 -> "B45"
        in 46790..54539 -> "B46"
        in 55240..56739 -> "B48"
        in 56740..58239 -> "B49"
        in 58240..59089 -> "B50"
        in 59090..59139 -> "B51"
        in 59140..60139 -> "B52"
        in 60140..60254 -> "B53"
        in 65536..66435 -> "B65"
        in 66436..67335 -> "B66"
        in 67336..67535 -> "B67"
        in 67536..67835 -> "B68"
        in 67836..68335 -> "B69"
        in 68336..68585 -> "B70"
        in 68586..68935 -> "B71"
        in 68936..68985 -> "B72"
        in 68986..69035 -> "B73"
        in 69036..69465 -> "B74"
        in 69466..70315 -> "B75"
        in 70316..70365 -> "B76"
        in 70366..70545 -> "B85"
        in 70546..70595 -> "B87"
        in 70596..70645 -> "B88"
        else -> null
    }

    // ARFCNからのバンド特定はフォールバック（代替手段）として使用する。
    // 1つの周波数が複数の重複する3GPPバンドに属する場合があるため、その場合は無理に1つに決めつけずnullを返す。
    private fun convertNrArfcnToBand(nrarfcn: Int): String? {
        if (nrarfcn < 0) return null
        val mhz = when (nrarfcn) {
            in 0..599999 -> nrarfcn * 0.005
            in 600000..2016666 -> 3000.0 + (nrarfcn - 600000) * 0.015
            in 2016667..3279165 -> 24250.08 + (nrarfcn - 2016667) * 0.06
            else -> return null
        }
        val possible = listOf(
            Triple("n1", 2110.0, 2170.0), Triple("n2", 1930.0, 1990.0),
            Triple("n3", 1805.0, 1880.0), Triple("n5", 869.0, 894.0),
            Triple("n7", 2620.0, 2690.0), Triple("n8", 925.0, 960.0),
            Triple("n20", 791.0, 821.0), Triple("n28", 758.0, 803.0),
            Triple("n38", 2570.0, 2620.0), Triple("n40", 2300.0, 2400.0),
            Triple("n41", 2496.0, 2690.0), Triple("n66", 2110.0, 2200.0),
            Triple("n71", 617.0, 652.0), Triple("n77", 3300.0, 4200.0),
            Triple("n78", 3300.0, 3800.0), Triple("n79", 4400.0, 5000.0),
            Triple("n257", 26500.0, 29500.0), Triple("n258", 24250.0, 27500.0),
            Triple("n260", 37000.0, 40000.0), Triple("n261", 27500.0, 28350.0)
        ).filter { mhz >= it.second && mhz < it.third }
        return possible.singleOrNull()?.first
    }

    enum class StatPeriod { TODAY, WEEK, MONTH, ALL }

    fun getBandStatistics(period: StatPeriod): Map<String, Int> =
        repository.statistics(subscriptionId, period.name)
}
