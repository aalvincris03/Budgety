package com.budgety.myapp

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "budgety_v3.db"
        private const val DATABASE_VERSION = 2 // Binago ko sa 2 para mag-update ang database

        private const val TABLE_USERS = "users"
        private const val COLUMN_USER_ID = "id"
        private const val COLUMN_USER_NAME = "name"

        private const val TABLE_EXPENSES = "expenses"
        private const val COLUMN_EXPENSE_ID = "id"
        private const val COLUMN_EXPENSE_USER_ID = "user_id"
        private const val COLUMN_EXPENSE_TITLE = "title"
        private const val COLUMN_EXPENSE_AMOUNT = "amount"
        private const val COLUMN_EXPENSE_DATETIME = "datetime"

        private const val TABLE_INCOMES = "incomes"
        private const val COLUMN_INCOME_ID = "id"
        private const val COLUMN_INCOME_USER_ID = "user_id"
        private const val COLUMN_INCOME_TITLE = "title"
        private const val COLUMN_INCOME_AMOUNT = "amount"
        private const val COLUMN_INCOME_DATETIME = "datetime"

        // BAGONG TABLE PARA SA FULL HISTORY (AUDIT LOGS)
        private const val TABLE_AUDIT_LOGS = "audit_logs"
        private const val COLUMN_AUDIT_ID = "id"
        private const val COLUMN_AUDIT_USER_ID = "user_id"
        private const val COLUMN_AUDIT_ACTION = "action" // ADDED, EDITED, DELETED
        private const val COLUMN_AUDIT_DETAILS = "details"
        private const val COLUMN_AUDIT_DATE = "date"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createUsers = ("CREATE TABLE $TABLE_USERS ("
                + "$COLUMN_USER_ID INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "$COLUMN_USER_NAME TEXT UNIQUE)")

        val createExpenses = ("CREATE TABLE $TABLE_EXPENSES ("
                + "$COLUMN_EXPENSE_ID INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "$COLUMN_EXPENSE_USER_ID INTEGER, "
                + "$COLUMN_EXPENSE_TITLE TEXT, "
                + "$COLUMN_EXPENSE_AMOUNT REAL, "
                + "$COLUMN_EXPENSE_DATETIME TEXT)")

        val createIncomes = ("CREATE TABLE $TABLE_INCOMES ("
                + "$COLUMN_INCOME_ID INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "$COLUMN_INCOME_USER_ID INTEGER, "
                + "$COLUMN_INCOME_TITLE TEXT, "
                + "$COLUMN_INCOME_AMOUNT REAL, "
                + "$COLUMN_INCOME_DATETIME TEXT)")

        val createAuditLogs = ("CREATE TABLE $TABLE_AUDIT_LOGS ("
                + "$COLUMN_AUDIT_ID INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "$COLUMN_AUDIT_USER_ID INTEGER, "
                + "$COLUMN_AUDIT_ACTION TEXT, "
                + "$COLUMN_AUDIT_DETAILS TEXT, "
                + "$COLUMN_AUDIT_DATE TEXT)")

        db.execSQL(createUsers)
        db.execSQL(createExpenses)
        db.execSQL(createIncomes)
        db.execSQL(createAuditLogs)

        val values = ContentValues().apply { put(COLUMN_USER_NAME, "Main Account") }
        db.insert(TABLE_USERS, null, values)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_USERS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_EXPENSES")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_INCOMES")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_AUDIT_LOGS")
        onCreate(db)
    }

    // --- INTERNAL AUDIT LOG FUNCTION ---
    private fun addAuditLog(db: SQLiteDatabase, userId: Int, action: String, details: String) {
        val currentDate = SimpleDateFormat("yyyy-MM-dd hh:mm a", Locale.getDefault()).format(Date())
        val values = ContentValues().apply {
            put(COLUMN_AUDIT_USER_ID, userId)
            put(COLUMN_AUDIT_ACTION, action)
            put(COLUMN_AUDIT_DETAILS, details)
            put(COLUMN_AUDIT_DATE, currentDate)
        }
        db.insert(TABLE_AUDIT_LOGS, null, values)
    }

    // ==========================================
    //               USER METHODS
    // ==========================================
    fun addUser(name: String): Boolean {
        val db = this.writableDatabase
        val values = ContentValues().apply { put(COLUMN_USER_NAME, name) }
        return db.insert(TABLE_USERS, null, values) != -1L
    }

    // BAGO: Rename User Function
    fun updateUserName(id: Int, newName: String): Boolean {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_USER_NAME, newName)
        }
        return db.update(TABLE_USERS, values, "$COLUMN_USER_ID=?", arrayOf(id.toString())) > 0
    }

    val allUsers: List<User>
        get() {
            val list = ArrayList<User>()
            val db = this.readableDatabase
            val cursor = db.rawQuery("SELECT * FROM $TABLE_USERS ORDER BY $COLUMN_USER_ID ASC", null)
            if (cursor.moveToFirst()) {
                do {
                    val id = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_USER_ID))
                    val name = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_USER_NAME))
                    list.add(User(id, name))
                } while (cursor.moveToNext())
            }
            cursor.close()
            return list
        }

    // ==========================================
    //             EXPENSE METHODS
    // ==========================================
    fun addExpense(userId: Int, title: String, amount: Double, dateTime: String): Boolean {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_EXPENSE_USER_ID, userId)
            put(COLUMN_EXPENSE_TITLE, title)
            put(COLUMN_EXPENSE_AMOUNT, amount)
            put(COLUMN_EXPENSE_DATETIME, dateTime)
        }
        val success = db.insert(TABLE_EXPENSES, null, values) != -1L
        if (success) {
            addAuditLog(db, userId, "EXPENSE ADDED", "Added expense: $title (-₱$amount)")
        }
        return success
    }

    fun updateExpense(id: Int, title: String, amount: Double, dateTime: String): Boolean {
        val db = this.writableDatabase
        
        // Kunin muna ang User ID para sa Audit Log
        var userId = -1
        val cursor = db.rawQuery("SELECT $COLUMN_EXPENSE_USER_ID FROM $TABLE_EXPENSES WHERE $COLUMN_EXPENSE_ID=?", arrayOf(id.toString()))
        if (cursor.moveToFirst()) userId = cursor.getInt(0)
        cursor.close()

        val values = ContentValues().apply {
            put(COLUMN_EXPENSE_TITLE, title)
            put(COLUMN_EXPENSE_AMOUNT, amount)
            put(COLUMN_EXPENSE_DATETIME, dateTime)
        }
        val success = db.update(TABLE_EXPENSES, values, "$COLUMN_EXPENSE_ID=?", arrayOf(id.toString())) > 0
        if (success && userId != -1) {
            addAuditLog(db, userId, "EXPENSE EDITED", "Edited expense: $title (New amount: -₱$amount)")
        }
        return success
    }

    fun deleteExpense(id: Int): Boolean {
        val db = this.writableDatabase
        
        // Kunin muna ang detalye bago burahin para sa Audit Log
        var userId = -1
        var title = ""
        var amount = 0.0
        val cursor = db.rawQuery("SELECT * FROM $TABLE_EXPENSES WHERE $COLUMN_EXPENSE_ID=?", arrayOf(id.toString()))
        if (cursor.moveToFirst()) {
            userId = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_EXPENSE_USER_ID))
            title = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_EXPENSE_TITLE))
            amount = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_EXPENSE_AMOUNT))
        }
        cursor.close()

        val success = db.delete(TABLE_EXPENSES, "$COLUMN_EXPENSE_ID=?", arrayOf(id.toString())) > 0
        if (success && userId != -1) {
            addAuditLog(db, userId, "EXPENSE DELETED", "Deleted expense: $title (-₱$amount)")
        }
        return success
    }

    fun getExpensesByUser(userId: Int): List<Expense> {
        val list = ArrayList<Expense>()
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_EXPENSES WHERE $COLUMN_EXPENSE_USER_ID=? ORDER BY $COLUMN_EXPENSE_ID DESC", arrayOf(userId.toString()))
        if (cursor.moveToFirst()) {
            do {
                val id = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_EXPENSE_ID))
                val title = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_EXPENSE_TITLE))
                val amount = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_EXPENSE_AMOUNT))
                val dateTime = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_EXPENSE_DATETIME))
                list.add(Expense(id, userId, title, amount, dateTime))
            } while (cursor.moveToNext())
        }
        cursor.close()
        return list
    }

    fun getTotalExpensesByUser(userId: Int): Double {
        var total = 0.0
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT SUM($COLUMN_EXPENSE_AMOUNT) FROM $TABLE_EXPENSES WHERE $COLUMN_EXPENSE_USER_ID=?", arrayOf(userId.toString()))
        if (cursor.moveToFirst()) total = cursor.getDouble(0)
        cursor.close()
        return total
    }

    // ==========================================
    //              INCOME METHODS
    // ==========================================
    fun addIncome(userId: Int, title: String, amount: Double, dateTime: String): Boolean {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_INCOME_USER_ID, userId)
            put(COLUMN_INCOME_TITLE, title)
            put(COLUMN_INCOME_AMOUNT, amount)
            put(COLUMN_INCOME_DATETIME, dateTime)
        }
        val success = db.insert(TABLE_INCOMES, null, values) != -1L
        if (success) {
            addAuditLog(db, userId, "INCOME ADDED", "Added income: $title (+₱$amount)")
        }
        return success
    }

    fun updateIncome(id: Int, title: String, amount: Double, dateTime: String): Boolean {
        val db = this.writableDatabase
        
        var userId = -1
        val cursor = db.rawQuery("SELECT $COLUMN_INCOME_USER_ID FROM $TABLE_INCOMES WHERE $COLUMN_INCOME_ID=?", arrayOf(id.toString()))
        if (cursor.moveToFirst()) userId = cursor.getInt(0)
        cursor.close()

        val values = ContentValues().apply {
            put(COLUMN_INCOME_TITLE, title)
            put(COLUMN_INCOME_AMOUNT, amount)
            put(COLUMN_INCOME_DATETIME, dateTime)
        }
        val success = db.update(TABLE_INCOMES, values, "$COLUMN_INCOME_ID=?", arrayOf(id.toString())) > 0
        if (success && userId != -1) {
            addAuditLog(db, userId, "INCOME EDITED", "Edited income: $title (New amount: +₱$amount)")
        }
        return success
    }

    fun deleteIncome(id: Int): Boolean {
        val db = this.writableDatabase
        
        var userId = -1
        var title = ""
        var amount = 0.0
        val cursor = db.rawQuery("SELECT * FROM $TABLE_INCOMES WHERE $COLUMN_INCOME_ID=?", arrayOf(id.toString()))
        if (cursor.moveToFirst()) {
            userId = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_INCOME_USER_ID))
            title = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_INCOME_TITLE))
            amount = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_INCOME_AMOUNT))
        }
        cursor.close()

        val success = db.delete(TABLE_INCOMES, "$COLUMN_INCOME_ID=?", arrayOf(id.toString())) > 0
        if (success && userId != -1) {
            addAuditLog(db, userId, "INCOME DELETED", "Deleted income: $title (+₱$amount)")
        }
        return success
    }

    fun getIncomesByUser(userId: Int): List<Income> {
        val list = ArrayList<Income>()
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_INCOMES WHERE $COLUMN_INCOME_USER_ID=? ORDER BY $COLUMN_INCOME_ID DESC", arrayOf(userId.toString()))
        if (cursor.moveToFirst()) {
            do {
                val id = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_INCOME_ID))
                val title = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_INCOME_TITLE))
                val amount = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_INCOME_AMOUNT))
                val dateTime = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_INCOME_DATETIME))
                list.add(Income(id, userId, title, amount, dateTime))
            } while (cursor.moveToNext())
        }
        cursor.close()
        return list
    }

    fun getTotalIncomeByUser(userId: Int): Double {
        var total = 0.0
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT SUM($COLUMN_INCOME_AMOUNT) FROM $TABLE_INCOMES WHERE $COLUMN_INCOME_USER_ID=?", arrayOf(userId.toString()))
        if (cursor.moveToFirst()) total = cursor.getDouble(0)
        cursor.close()
        return total
    }

    // ==========================================
    //            TRANSACTIONS & HISTORY
    // ==========================================
    fun getAllTransactionsByUser(userId: Int, filterType: String = "ALL"): List<Transaction> {
        val list = ArrayList<Transaction>()
        val db = this.readableDatabase

        if (filterType == "ALL" || filterType == "INCOME") {
            val cursorIncome = db.rawQuery("SELECT * FROM $TABLE_INCOMES WHERE $COLUMN_INCOME_USER_ID=?", arrayOf(userId.toString()))
            if (cursorIncome.moveToFirst()) {
                do {
                    val id = cursorIncome.getInt(cursorIncome.getColumnIndexOrThrow(COLUMN_INCOME_ID))
                    val title = cursorIncome.getString(cursorIncome.getColumnIndexOrThrow(COLUMN_INCOME_TITLE))
                    val amount = cursorIncome.getDouble(cursorIncome.getColumnIndexOrThrow(COLUMN_INCOME_AMOUNT))
                    val dateTime = cursorIncome.getString(cursorIncome.getColumnIndexOrThrow(COLUMN_INCOME_DATETIME))
                    list.add(Transaction(id, userId, title, amount, dateTime, TransactionType.INCOME))
                } while (cursorIncome.moveToNext())
            }
            cursorIncome.close()
        }

        if (filterType == "ALL" || filterType == "EXPENSE") {
            val cursorExpense = db.rawQuery("SELECT * FROM $TABLE_EXPENSES WHERE $COLUMN_EXPENSE_USER_ID=?", arrayOf(userId.toString()))
            if (cursorExpense.moveToFirst()) {
                do {
                    val id = cursorExpense.getInt(cursorExpense.getColumnIndexOrThrow(COLUMN_EXPENSE_ID))
                    val title = cursorExpense.getString(cursorExpense.getColumnIndexOrThrow(COLUMN_EXPENSE_TITLE))
                    val amount = cursorExpense.getDouble(cursorExpense.getColumnIndexOrThrow(COLUMN_EXPENSE_AMOUNT))
                    val dateTime = cursorExpense.getString(cursorExpense.getColumnIndexOrThrow(COLUMN_EXPENSE_DATETIME))
                    list.add(Transaction(id, userId, title, amount, dateTime, TransactionType.EXPENSE))
                } while (cursorExpense.moveToNext())
            }
            cursorExpense.close()
        }

        list.sortByDescending { it.id }
        return list
    }

    // BAGO: Fetch Full Audit Logs
    fun getAuditLogsByUser(userId: Int): List<AuditLog> {
        val list = ArrayList<AuditLog>()
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_AUDIT_LOGS WHERE $COLUMN_AUDIT_USER_ID=? ORDER BY $COLUMN_AUDIT_ID DESC", arrayOf(userId.toString()))
        if (cursor.moveToFirst()) {
            do {
                val id = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_AUDIT_ID))
                val action = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_AUDIT_ACTION))
                val details = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_AUDIT_DETAILS))
                val date = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_AUDIT_DATE))
                list.add(AuditLog(id, userId, action, details, date))
            } while (cursor.moveToNext())
        }
        cursor.close()
        return list
    }
}