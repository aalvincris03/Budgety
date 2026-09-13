package com.budgety.myapp

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AuditLogDatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "budgety_audit_logs.db"
        private const val DATABASE_VERSION = 1

        private const val TABLE_AUDIT_LOGS = "audit_logs"
        private const val COLUMN_ID = "id"
        private const val COLUMN_USER_ID = "user_id"
        private const val COLUMN_ACTION = "action"
        private const val COLUMN_DETAILS = "details"
        private const val COLUMN_DATE = "date"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE $TABLE_AUDIT_LOGS (" +
                "$COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "$COLUMN_USER_ID INTEGER NOT NULL, " +
                "$COLUMN_ACTION TEXT NOT NULL, " +
                "$COLUMN_DETAILS TEXT NOT NULL, " +
                "$COLUMN_DATE TEXT NOT NULL)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_AUDIT_LOGS")
        onCreate(db)
    }

    fun addAuditLog(userId: Int, action: String, details: String) {
        val values = ContentValues().apply {
            put(COLUMN_USER_ID, userId)
            put(COLUMN_ACTION, action)
            put(COLUMN_DETAILS, details)
            put(
                COLUMN_DATE,
                SimpleDateFormat("yyyy-MM-dd hh:mm a", Locale.getDefault()).format(Date())
            )
        }
        writableDatabase.insertOrThrow(TABLE_AUDIT_LOGS, null, values)
    }

    fun getAuditLogsByUser(userId: Int, filterType: String = "ALL"): List<AuditLog> {
        val logs = ArrayList<AuditLog>()
        val db = readableDatabase
        val selection = if (filterType == "ALL") {
            "$COLUMN_USER_ID=?"
        } else {
            "$COLUMN_USER_ID=? AND $COLUMN_ACTION LIKE ?"
        }
        val selectionArgs = if (filterType == "ALL") {
            arrayOf(userId.toString())
        } else {
            arrayOf(userId.toString(), "$filterType %")
        }
        val cursor = db.query(
            TABLE_AUDIT_LOGS,
            null,
            selection,
            selectionArgs,
            null,
            null,
            "$COLUMN_ID DESC"
        )

        cursor.use {
            while (it.moveToNext()) {
                logs.add(
                    AuditLog(
                        id = it.getInt(it.getColumnIndexOrThrow(COLUMN_ID)),
                        userId = it.getInt(it.getColumnIndexOrThrow(COLUMN_USER_ID)),
                        action = it.getString(it.getColumnIndexOrThrow(COLUMN_ACTION)),
                        details = it.getString(it.getColumnIndexOrThrow(COLUMN_DETAILS)),
                        date = it.getString(it.getColumnIndexOrThrow(COLUMN_DATE))
                    )
                )
            }
        }
        return logs
    }
}
