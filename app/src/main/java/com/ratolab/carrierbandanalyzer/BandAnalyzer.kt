package com.ratolab.carrierbandanalyzer

import android.Manifest // 追加
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager // 追加
import android.os.Build
import android.telephony.CellIdentityNr
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat // 追加
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

class BandAnalyzer(context: Context) {

    private val appContext = context.applicationContext
    private val telephonyManager =
        appContext.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    private val prefs = appContext.getSharedPreferences("band_analyzer_prefs", Context.MODE_PRIVATE)

    // メモリ上のキャッシュ
    private val observedBands: MutableSet<String> = mutableSetOf()

    // 変更監視リスナー
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
        if (key == KEY_OBSERVED_BANDS) {
            reloadFromPrefs(sharedPreferences)
        }
    }

    init {
        reloadFromPrefs(prefs)
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
    }

    // ★追加: 許可チェック用の便利関数
    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun reloadFromPrefs(sharedPreferences: SharedPreferences) {
        val set = sharedPreferences.getStringSet(KEY_OBSERVED_BANDS, emptySet()) ?: emptySet()
        synchronized(observedBands) {
            observedBands.clear()
            observedBands.addAll(set)
        }
    }

    // キャリアごとのバンド定義
    private val carrierBands = mapOf(
        "DOCOMO" to setOf("B1","B3","B19","B21","B28","B42","n77","n78","n79"),
        "AU" to setOf("B1","B3","B18","B26","B28","B41","B42","n77","n78","n257"),
        "SOFTBANK" to setOf("B1","B3","B8","B11","B28","B41","B42","n77","n78","n257"),
        "RAKUTEN" to setOf("B3","B18","B26","n77"),
        "AHAMO" to setOf("B1","B3","B19","B21","B28","B42","n77","n78","n79"),
        "POVO" to setOf("B1","B3","B18","B26","B28","B41","B42","n77","n78","n257"),
        "UQ" to setOf("B1","B3","B18","B26","B28","B41","B42","n77","n78","n257"),
        "LINEMO" to setOf("B1","B3","B8","B11","B28","B41","B42","n77","n78","n257"),
        "Y!MOBILE" to setOf("B1","B3","B8","B11","B28","B41","B42","n77","n78","n257")
    )

    fun getCarrier(): String = detectCarrierLabel()

    fun getCarrierReferenceBands(): Set<String> {
        val label = detectCarrierLabel()
        return carrierBands[label].orEmpty()
    }

    fun getObservedBands(): Set<String> {
        synchronized(observedBands) {
            return observedBands.toSet()
        }
    }

    @SuppressLint("MissingPermission")
    fun scanNowBands(): Set<String> {
        // ★修正: 許可がない場合は空を返して終了（これで落ちなくなる！）
        if (!hasLocationPermission()) return emptySet()

        val now = mutableSetOf<String>()

        val cells = telephonyManager.allCellInfo ?: emptyList()
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

        now.addAll(getBandsFromPhysicalChannelConfigs())
        addObservedAndPersist(now)
        return now
    }

    @SuppressLint("MissingPermission")
    fun getCaChannelDebugList(): List<CaChannelDebug> {
        // ★修正: ここも許可がないと落ちる可能性があるためガード
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
        val percent = if (total == 0) 0 else ((covered.toDouble() / total) * 100.0).roundToInt()

        val judgement = when {
            total == 0 -> appContext.getString(R.string.judgement_unknown)
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

    fun resetObservedBands() {
        synchronized(observedBands) {
            observedBands.clear()
        }
        prefs.edit().remove(KEY_OBSERVED_BANDS).apply()
        // ログファイルも消去したい場合はここで File(appContext.filesDir, "band_logs.csv").delete()
    }

    /**
     * CSVログ保存機能 (Priority 4)
     */
    fun saveLog(bands: Set<String>) {
        if (bands.isEmpty()) return

        val time = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
        val carrier = detectCarrierLabel()
        val bandsStr = bands.sorted().joinToString("|")

        val logLine = "$time, $carrier, $bandsStr\n"

        try {
            val logFile = File(appContext.filesDir, "band_logs.csv")
            logFile.appendText(logLine)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * ログファイル取得用
     */
    fun getLogFile(): File = File(appContext.filesDir, "band_logs.csv")

    private fun addObservedAndPersist(bands: Set<String>) {
        var changed = false
        synchronized(observedBands) {
            for (b in bands) {
                if (observedBands.add(b)) changed = true
            }
        }
        if (changed) {
            val toSave = synchronized(observedBands) { observedBands.toSet() }
            prefs.edit().putStringSet(KEY_OBSERVED_BANDS, toSave).apply()
        }
    }

    @SuppressLint("MissingPermission")
    private fun detectCarrierLabel(): String {
        val tokens = mutableListOf<String>()
        fun add(s: String?) { if (!s.isNullOrBlank()) tokens += s }
        add(telephonyManager.simOperatorName)
        add(telephonyManager.networkOperatorName)
        add(getStringViaReflection(telephonyManager, "getSimCarrierIdName"))
        add(getStringViaReflection(telephonyManager, "getCarrierIdName"))
        val op = telephonyManager.simOperator.orEmpty()
        val hay = tokens.joinToString(" | ").lowercase()

        if (hay.contains("ahamo")) return "AHAMO"
        if (hay.contains("povo")) return "POVO"
        if (hay.contains("uq")) return "UQ"
        if (hay.contains("linemo")) return "LINEMO"
        if (hay.contains("ymobile") || hay.contains("y!mobile") || hay.contains("ワイモバ")) return "Y!MOBILE"
        if (hay.contains("docomo") || hay.contains("ドコモ")) return "DOCOMO"
        if (hay.contains("kddi") || hay.contains("au")) return "AU"
        if (hay.contains("softbank") || hay.contains("ソフトバンク")) return "SOFTBANK"
        if (hay.contains("rakuten") || hay.contains("楽天")) return "RAKUTEN"

        return when {
            op.startsWith("44010") -> "DOCOMO"
            op.startsWith("44011") -> "RAKUTEN"
            op.startsWith("44050") || op.startsWith("44051") -> "AU"
            op.startsWith("44020") || op.startsWith("44021") -> "SOFTBANK"
            else -> "UNKNOWN"
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
        in 1200..1949 -> "B3"
        in 6150..6449 -> "B19"
        in 6600..7399 -> "B21"
        in 9210..9659 -> "B28"
        in 41590..43589 -> "B42"
        else -> null
    }

    private fun convertNrArfcnToBand(nrarfcn: Int): String? = when (nrarfcn) {
        in 693334..733333 -> "n79"
        in 620000..653333 -> "n78"
        in 620000..680000 -> "n77"
        else -> null
    }

    companion object {
        private const val KEY_OBSERVED_BANDS = "observed_bands"
    }
}