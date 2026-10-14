package za.callguard.core

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** On-phone database: your block list, cached spam info, and the screened-call history. */
class Store private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "callguard.db", null, 1) {

    companion object {
        @Volatile private var instance: Store? = null
        fun get(context: Context): Store =
            instance ?: synchronized(this) { instance ?: Store(context).also { instance = it } }

        private const val CACHE_MAX_AGE_MS = 7L * 24 * 3600 * 1000
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE blocked(number TEXT PRIMARY KEY, label TEXT, at INTEGER NOT NULL)")
        db.execSQL(
            """CREATE TABLE spam_cache(
                number TEXT PRIMARY KEY, verdict TEXT NOT NULL, score INTEGER, reports INTEGER,
                not_spam INTEGER, category TEXT, label TEXT, business TEXT, carrier TEXT,
                location TEXT, line_type TEXT, updated_at INTEGER NOT NULL)"""
        )
        db.execSQL(
            """CREATE TABLE calls(
                id INTEGER PRIMARY KEY AUTOINCREMENT, number TEXT NOT NULL, title TEXT NOT NULL,
                verdict TEXT NOT NULL, action TEXT NOT NULL, at INTEGER NOT NULL)"""
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    // ---- block list
    fun isBlocked(number: String): Boolean =
        readableDatabase.rawQuery("SELECT 1 FROM blocked WHERE number=?", arrayOf(number))
            .use { it.moveToFirst() }

    fun block(number: String, label: String? = null) {
        writableDatabase.insertWithOnConflict("blocked", null, ContentValues().apply {
            put("number", number); put("label", label); put("at", System.currentTimeMillis())
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun unblock(number: String) {
        writableDatabase.delete("blocked", "number=?", arrayOf(number))
    }

    fun blockedList(): List<BlockedNumber> =
        readableDatabase.rawQuery("SELECT number, label, at FROM blocked ORDER BY at DESC", null).use { c ->
            buildList {
                while (c.moveToNext()) add(BlockedNumber(c.getString(0), c.getString(1), c.getLong(2)))
            }
        }

    // ---- spam cache
    fun cached(number: String): LookupResult? =
        readableDatabase.rawQuery(
            """SELECT verdict, score, reports, not_spam, category, label, business, carrier,
                      location, line_type, updated_at FROM spam_cache WHERE number=?""",
            arrayOf(number)
        ).use { c ->
            if (!c.moveToFirst()) return null
            if (System.currentTimeMillis() - c.getLong(10) > CACHE_MAX_AGE_MS) return null
            LookupResult(
                number = number, verdict = c.getString(0), score = c.getInt(1),
                reports = c.getInt(2), notSpam = c.getInt(3), category = c.getString(4),
                label = c.getString(5), businessName = c.getString(6), carrier = c.getString(7),
                location = c.getString(8), lineType = c.getString(9), source = "offline cache",
            )
        }

    fun putCache(results: List<LookupResult>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val now = System.currentTimeMillis()
            for (r in results) {
                db.insertWithOnConflict("spam_cache", null, ContentValues().apply {
                    put("number", r.number); put("verdict", r.verdict); put("score", r.score)
                    put("reports", r.reports); put("not_spam", r.notSpam); put("category", r.category)
                    put("label", r.label); put("business", r.businessName); put("carrier", r.carrier)
                    put("location", r.location); put("line_type", r.lineType); put("updated_at", now)
                }, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    // ---- call history
    fun logCall(number: String, title: String, verdict: String, action: String) {
        writableDatabase.insert("calls", null, ContentValues().apply {
            put("number", number); put("title", title); put("verdict", verdict)
            put("action", action); put("at", System.currentTimeMillis())
        })
        // keep the last 500
        writableDatabase.execSQL(
            "DELETE FROM calls WHERE id NOT IN (SELECT id FROM calls ORDER BY at DESC LIMIT 500)"
        )
    }

    fun recentCalls(limit: Int = 100): List<ScreenedCall> =
        readableDatabase.rawQuery(
            "SELECT id, number, title, verdict, action, at FROM calls ORDER BY at DESC LIMIT ${limit.coerceIn(1, 500)}",
            null
        ).use { c ->
            buildList {
                while (c.moveToNext()) add(
                    ScreenedCall(c.getLong(0), c.getString(1), c.getString(2), c.getString(3),
                        c.getString(4), c.getLong(5))
                )
            }
        }
}
