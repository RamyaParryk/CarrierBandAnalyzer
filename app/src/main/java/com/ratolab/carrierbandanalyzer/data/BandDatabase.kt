package com.ratolab.carrierbandanalyzer.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.File

/** One database for all SIMs; no legacy CSV or SharedPreferences import. */
class BandDatabase private constructor(private val app: Context) :
    SQLiteOpenHelper(app, "carrier_bands.db", null, 1) {

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)

    }

    init { setWriteAheadLoggingEnabled(true) }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE sim_profiles (
            sub_id INTEGER PRIMARY KEY, carrier TEXT NOT NULL DEFAULT '',
            display_name TEXT NOT NULL DEFAULT '', last_observed_ms INTEGER NOT NULL DEFAULT 0
        )""")
        db.execSQL("""CREATE TABLE observed_bands (
            sub_id INTEGER NOT NULL, band TEXT NOT NULL,
            PRIMARY KEY (sub_id, band)
        )""")
        db.execSQL("""CREATE TABLE band_samples (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            sub_id INTEGER NOT NULL, timestamp_ms INTEGER NOT NULL, carrier TEXT NOT NULL
        )""")
        db.execSQL("""CREATE TABLE sample_bands (
            sample_id INTEGER NOT NULL, band TEXT NOT NULL,
            PRIMARY KEY(sample_id, band),
            FOREIGN KEY (sample_id) REFERENCES band_samples(id) ON DELETE CASCADE
        )""")
        db.execSQL("CREATE INDEX idx_samples_sub_time ON band_samples(sub_id, timestamp_ms)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Future schema changes must use ALTER/migrations and preserve data.
        throw IllegalStateException("Database migration required: $oldVersion -> $newVersion")
    }

    /** Cleanup only files belonging to obsolete storage, after DB is open. Never remove exports. */
    private fun discardOldStorageOnce() {
        val marker = app.getSharedPreferences("sqlite_storage_state", Context.MODE_PRIVATE)
        if (marker.getBoolean("legacy_deleted_v1", false)) return
        var success = true
        val legacyCsv = Regex("""band_logs(?:_-?\d+)?\.csv(?:\.tmp|\.bak|\.new|\.migrating)?""")
        val otherLegacy = Regex("""band_logs\.csv\.(?:merged_to_-?\d+|migrating)""")
        app.filesDir.listFiles()?.forEach { f ->
            if (f.isFile && (legacyCsv.matches(f.name) || otherLegacy.matches(f.name))) {
                if (!f.delete()) success = false
            }
        }
        val prefsDir = File(app.applicationInfo.dataDir, "shared_prefs")
        val legacyPrefs = Regex("""band_analyzer_prefs(?:_-?\d+)?\.xml""")
        prefsDir.listFiles()?.filter { legacyPrefs.matches(it.name) }?.forEach {
            val prefsName = it.name.removeSuffix(".xml")
            if (!app.deleteSharedPreferences(prefsName) && it.exists()) success = false
        }
        if (success) marker.edit().putBoolean("legacy_deleted_v1", true).commit()
    }

    companion object {
        @Volatile private var INSTANCE: BandDatabase? = null
        fun get(context: Context): BandDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: BandDatabase(context.applicationContext).also { instance ->
                instance.writableDatabase // Verify SQLite storage is usable BEFORE deleting old data.
                instance.discardOldStorageOnce()
                INSTANCE = instance
            }
        }
    }
}
