package com.ratolab.carrierbandanalyzer.data

import android.content.Context
import java.io.File

/** Application-facing API; call disk/database operations on Dispatchers.IO. */
class BandRepository private constructor(context: Context) {
    private val app = context.applicationContext
    private val dao by lazy { BandLogDao(BandDatabase.get(app)) }

    fun observe(subId: Int, carrier: String, displayName: String, bands: Set<String>) =
        dao.observe(subId, carrier, displayName, bands)

    fun log(subId: Int, carrier: String, displayName: String, bands: Set<String>) =
        dao.recordSample(subId, carrier, displayName, bands)

    fun observed(subId: Int): Set<String> = dao.observedBands(subId)
    fun statistics(subId: Int, period: String): Map<String,Int> = dao.statistics(subId,period)
    fun savedSims(): List<SavedSimLog> = dao.savedSims()
    fun reset(subId: Int) = dao.reset(subId)

    fun exportCsv(subId: Int): File {
        // Old files named band_logs_*.csv are obsolete. Export uses a distinct filename.
        // Keep old share URIs valid briefly, but avoid unlimited exported-file buildup.
        val now = System.currentTimeMillis()
        app.filesDir.listFiles()?.forEach { file ->
            if (file.name.startsWith("band_export_") && file.name.endsWith(".csv") &&
                now - file.lastModified() > 24L * 60 * 60 * 1000) file.delete()
        }
        val output = File.createTempFile("band_export_${subId}_", ".csv", app.filesDir)
        return dao.exportCsv(subId, output)
    }

    companion object {
        @Volatile private var instance: BandRepository? = null
        fun get(context: Context): BandRepository = instance ?: synchronized(this) {
            instance ?: BandRepository(context.applicationContext).also { instance=it }
        }
    }
}
