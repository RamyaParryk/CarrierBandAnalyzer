package com.ratolab.carrierbandanalyzer.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** SQL access. All write operations use a transaction; queries use indexed timestamps. */
class BandLogDao(private val database: BandDatabase) {
    private fun profile(db: SQLiteDatabase, subId: Int, carrier: String, name: String, time: Long) {
        db.execSQL("INSERT OR IGNORE INTO sim_profiles(sub_id,carrier,display_name,last_observed_ms) VALUES(?,?,?,?)", arrayOf(subId, carrier, name, time))
        db.execSQL("""UPDATE sim_profiles SET carrier=?,
            display_name=CASE WHEN ?<>'' THEN ? ELSE display_name END,
            last_observed_ms=? WHERE sub_id=?""", arrayOf(carrier, name, name, time, subId))
    }

    fun observe(subId: Int, carrier: String, displayName: String, bands: Set<String>) {
        if (bands.isEmpty() || subId < 0) return
        val db = database.writableDatabase
        db.beginTransaction()
        try {
            profile(db, subId, carrier, displayName, System.currentTimeMillis())
            val statement = db.compileStatement("INSERT OR IGNORE INTO observed_bands(sub_id,band) VALUES(?,?)")
            for (band in bands) {
                statement.clearBindings()
                statement.bindLong(1, subId.toLong())
                statement.bindString(2, band)
                statement.executeInsert()
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun recordSample(subId: Int, carrier: String, displayName: String, bands: Set<String>) {
        if (bands.isEmpty() || subId < 0) return
        val db = database.writableDatabase
        db.beginTransaction()
        try {
            val now = System.currentTimeMillis()
            profile(db, subId, carrier, displayName, now)
            val entry = ContentValues().apply {
                put("sub_id", subId); put("timestamp_ms", now); put("carrier", carrier)
            }
            val sampleId = db.insertOrThrow("band_samples", null, entry)
            val insertBand = db.compileStatement("INSERT INTO sample_bands(sample_id,band) VALUES(?,?)")
            val insertObserved = db.compileStatement("INSERT OR IGNORE INTO observed_bands(sub_id,band) VALUES(?,?)")
            for (band in bands) {
                insertBand.clearBindings()
                insertBand.bindLong(1, sampleId)
                insertBand.bindString(2, band)
                insertBand.executeInsert()
                insertObserved.clearBindings()
                insertObserved.bindLong(1, subId.toLong())
                insertObserved.bindString(2, band)
                insertObserved.executeInsert()
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun observedBands(subId: Int): Set<String> {
        val result = linkedSetOf<String>()
        database.readableDatabase.rawQuery(
            "SELECT band FROM observed_bands WHERE sub_id=?", arrayOf(subId.toString())
        ).use { c -> while (c.moveToNext()) result.add(c.getString(0)) }
        return result
    }

    /** Periods: TODAY, WEEK (rolling seven days), MONTH (calendar month), ALL. */
    fun statistics(subId: Int, period: String): Map<String, Int> {
        val cal = Calendar.getInstance()
        val lower = when (period) {
            "TODAY" -> { cal.set(Calendar.HOUR_OF_DAY,0); cal.set(Calendar.MINUTE,0); cal.set(Calendar.SECOND,0); cal.set(Calendar.MILLISECOND,0); cal.timeInMillis }
            "WEEK" -> System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
            "MONTH" -> { cal.set(Calendar.DAY_OF_MONTH,1); cal.set(Calendar.HOUR_OF_DAY,0); cal.set(Calendar.MINUTE,0); cal.set(Calendar.SECOND,0); cal.set(Calendar.MILLISECOND,0); cal.timeInMillis }
            else -> null
        }
        val sql = """SELECT b.band, COUNT(*) FROM band_samples s
            JOIN sample_bands b ON b.sample_id=s.id
            WHERE s.sub_id=?""" + (if (lower != null) " AND s.timestamp_ms>=? AND s.timestamp_ms<=?" else "") +
            " GROUP BY b.band ORDER BY COUNT(*) DESC"
        val args = if (lower == null) arrayOf(subId.toString()) else
            arrayOf(subId.toString(), lower.toString(), System.currentTimeMillis().toString())
        val result = linkedMapOf<String,Int>()
        database.readableDatabase.rawQuery(sql,args).use { c ->
            while (c.moveToNext()) result[c.getString(0)] = c.getInt(1)
        }
        return result
    }

    fun savedSims(): List<SavedSimLog> {
        val list = mutableListOf<SavedSimLog>()
        database.readableDatabase.rawQuery("""SELECT sub_id,carrier,display_name,last_observed_ms
            FROM sim_profiles WHERE EXISTS(SELECT 1 FROM observed_bands o WHERE o.sub_id=sim_profiles.sub_id)
            OR EXISTS(SELECT 1 FROM band_samples s WHERE s.sub_id=sim_profiles.sub_id)
            ORDER BY last_observed_ms DESC""", null).use { c ->
            while (c.moveToNext()) list.add(SavedSimLog(c.getInt(0),c.getString(1),c.getString(2),c.getLong(3)))
        }
        return list
    }

    fun reset(subId: Int) {
        val db=database.writableDatabase
        db.beginTransaction()
        try {
            db.delete("band_samples", "sub_id=?", arrayOf(subId.toString()))
            db.delete("observed_bands", "sub_id=?", arrayOf(subId.toString()))
            db.delete("sim_profiles", "sub_id=?", arrayOf(subId.toString()))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    /** Preserves the old 3-column CSV format, one line per sample. Never stores CSV permanently. */
    fun exportCsv(subId: Int, destination: File): File {
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val db = database.readableDatabase
        destination.outputStream().bufferedWriter(Charsets.UTF_8).use { writer ->
            db.rawQuery("""SELECT s.id,s.timestamp_ms,s.carrier,b.band FROM band_samples s
                JOIN sample_bands b ON b.sample_id=s.id
                WHERE s.sub_id=? ORDER BY s.id,b.band""", arrayOf(subId.toString())).use { cursor ->
                var currentId = -1L
                var time = 0L
                var carrier = ""
                val bands = mutableListOf<String>()
                fun flush() {
                    if (currentId < 0L) return
                    writer.append(formatter.format(Date(time))).append(", ")
                        .append(carrier).append(", ").append(bands.joinToString("|"))
                    writer.newLine()
                }
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    if (id != currentId) {
                        flush()
                        currentId=id; time=cursor.getLong(1); carrier=cursor.getString(2); bands.clear()
                    }
                    bands.add(cursor.getString(3))
                }
                flush()
            }
        }
        return destination
    }
}
