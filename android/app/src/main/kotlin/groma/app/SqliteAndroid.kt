package groma.app

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import groma.core.BaseSqlite

/**
 * android.database.sqlite adapter of the GeoPackage write protocol.
 * The journal mode of the file is left untouched (QField / GDAL decide it).
 */
class SqliteAndroid(private val db: SQLiteDatabase) : BaseSqlite {
    init {
        db.rawQuery("PRAGMA busy_timeout = 5000", null).use { it.moveToFirst() }
    }

    override fun executer(sql: String, args: List<Any?>) = db.execSQL(sql, args.map { it }.toTypedArray())

    override fun requete(sql: String, args: List<Any?>): List<Map<String, Any?>> =
        db.rawQuery(sql, args.map { it?.toString() }.toTypedArray()).use { c ->
            buildList {
                while (c.moveToNext()) add((0 until c.columnCount).associate { c.getColumnName(it) to valeur(c, it) })
            }
        }

    /** beginTransactionNonExclusive() issues BEGIN IMMEDIATE. */
    override fun <T> transactionImmediate(bloc: () -> T): T {
        db.beginTransactionNonExclusive()
        try {
            val r = bloc()
            db.setTransactionSuccessful()
            return r
        } finally {
            db.endTransaction()
        }
    }

    private fun valeur(c: Cursor, i: Int): Any? = when (c.getType(i)) {
        Cursor.FIELD_TYPE_NULL -> null
        Cursor.FIELD_TYPE_INTEGER -> c.getLong(i)
        Cursor.FIELD_TYPE_FLOAT -> c.getDouble(i)
        Cursor.FIELD_TYPE_BLOB -> c.getBlob(i)
        else -> c.getString(i)
    }
}
