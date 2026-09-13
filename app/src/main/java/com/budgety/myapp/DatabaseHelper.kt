package com.budgety.myapp

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
    private val auditLogDatabase = AuditLogDatabaseHelper(context.applicationContext)

    companion object {
        private const val DATABASE_NAME = "budgety_v3.db"
        private const val DATABASE_VERSION = 3

        private const val TABLE_USERS = "users"
        private const val COLUMN_USER_ID = "id"
        private const val COLUMN_USER_NAME = "name"

        private const val TABLE_EXPENSES = "expenses"
        private const val COLUMN_EXPENSE_ID = "id"
        private const val COLUMN_EXPENSE_USER_ID = "user_id"
        private const val COLUMN_EXPENSE_TITLE = "title"
        private const val COLUMN_EXPENSE_AMOUNT = "amount"
        private const val COLUMN_EXPENSE_DATETIME = "datetime"
        private const val COLUMN_EXPENSE_CATEGORY = "category"

        private const val TABLE_INCOMES = "incomes"
        private const val COLUMN_INCOME_ID = "id"
        private const val COLUMN_INCOME_USER_ID = "user_id"
        private const val COLUMN_INCOME_TITLE = "title"
        private const val COLUMN_INCOME_AMOUNT = "amount"
        private const val COLUMN_INCOME_DATETIME = "datetime"
        private const val COLUMN_INCOME_CATEGORY = "category"

        private const val TABLE_DEBTS = "debts"
        private const val COLUMN_DEBT_ID = "id"
        private const val COLUMN_DEBT_USER_ID = "user_id"
        private const val COLUMN_DEBT_NAME = "name"
        private const val COLUMN_DEBT_AMOUNT = "amount"
        private const val COLUMN_DEBT_DUE_DATE = "due_date"
        private const val COLUMN_DEBT_PAID = "paid"

    }

    override fun onCreate(db: SQLiteDatabase) {
        ensureSchema(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        ensureSchema(db)
    }

    private fun ensureSchema(db: SQLiteDatabase) {
        val createUsers = ("CREATE TABLE IF NOT EXISTS $TABLE_USERS ("
                + "$COLUMN_USER_ID INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "$COLUMN_USER_NAME TEXT UNIQUE)")

        val createExpenses = ("CREATE TABLE IF NOT EXISTS $TABLE_EXPENSES ("
                + "$COLUMN_EXPENSE_ID INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "$COLUMN_EXPENSE_USER_ID INTEGER, "
                + "$COLUMN_EXPENSE_TITLE TEXT, "
                + "$COLUMN_EXPENSE_AMOUNT REAL, "
                + "$COLUMN_EXPENSE_DATETIME TEXT, "
                + "$COLUMN_EXPENSE_CATEGORY TEXT NOT NULL DEFAULT 'Other')")

        val createIncomes = ("CREATE TABLE IF NOT EXISTS $TABLE_INCOMES ("
                + "$COLUMN_INCOME_ID INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "$COLUMN_INCOME_USER_ID INTEGER, "
                + "$COLUMN_INCOME_TITLE TEXT, "
                + "$COLUMN_INCOME_AMOUNT REAL, "
                + "$COLUMN_INCOME_DATETIME TEXT, "
                + "$COLUMN_INCOME_CATEGORY TEXT NOT NULL DEFAULT 'Other')")

        val createDebts = ("CREATE TABLE IF NOT EXISTS $TABLE_DEBTS ("
                + "$COLUMN_DEBT_ID INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "$COLUMN_DEBT_USER_ID INTEGER NOT NULL, "
                + "$COLUMN_DEBT_NAME TEXT NOT NULL, "
                + "$COLUMN_DEBT_AMOUNT REAL NOT NULL, "
                + "$COLUMN_DEBT_DUE_DATE TEXT, "
                + "$COLUMN_DEBT_PAID INTEGER NOT NULL DEFAULT 0)")

        db.execSQL(createUsers)
        db.execSQL(createExpenses)
        db.execSQL(createIncomes)
        db.execSQL(createDebts)

        addColumnIfMissing(db, TABLE_EXPENSES, COLUMN_EXPENSE_CATEGORY, "TEXT NOT NULL DEFAULT 'Other'")
        addColumnIfMissing(db, TABLE_INCOMES, COLUMN_INCOME_CATEGORY, "TEXT NOT NULL DEFAULT 'Other'")

        val countCursor = db.rawQuery("SELECT COUNT(*) FROM $TABLE_USERS", null)
        try {
            if (countCursor.moveToFirst() && countCursor.getInt(0) == 0) {
                val values = ContentValues().apply { put(COLUMN_USER_NAME, "Main Account") }
                db.insert(TABLE_USERS, null, values)
            }
        } finally {
            countCursor.close()
        }
    }

    private fun addColumnIfMissing(db: SQLiteDatabase, tableName: String, columnName: String, columnDefinition: String) {
        val cursor = db.rawQuery("PRAGMA table_info($tableName)", null)
        try {
            while (cursor.moveToNext()) {
                val existingColumn = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                if (existingColumn == columnName) {
                    return
                }
            }
        } finally {
            cursor.close()
        }

        db.execSQL("ALTER TABLE $tableName ADD COLUMN $columnName $columnDefinition")
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
    fun addExpense(userId: Int, title: String, amount: Double, dateTime: String,
                   category: String = ExpenseCategory.OTHER.label): Boolean {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_EXPENSE_USER_ID, userId)
            put(COLUMN_EXPENSE_TITLE, title)
            put(COLUMN_EXPENSE_AMOUNT, amount)
            put(COLUMN_EXPENSE_DATETIME, dateTime)
            put(COLUMN_EXPENSE_CATEGORY, ExpenseCategory.fromStored(category))
        }
        val success = db.insert(TABLE_EXPENSES, null, values) != -1L
        if (success) {
            auditLogDatabase.addAuditLog(userId, "EXPENSE ADDED", "Added expense: $title (-₱$amount)")
        }
        return success
    }

    fun updateExpense(id: Int, title: String, amount: Double, dateTime: String,
                      category: String = ExpenseCategory.OTHER.label): Boolean {
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
            put(COLUMN_EXPENSE_CATEGORY, ExpenseCategory.fromStored(category))
        }
        val success = db.update(TABLE_EXPENSES, values, "$COLUMN_EXPENSE_ID=?", arrayOf(id.toString())) > 0
        if (success && userId != -1) {
            auditLogDatabase.addAuditLog(userId, "EXPENSE EDITED", "Edited expense: $title (New amount: -₱$amount)")
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
            auditLogDatabase.addAuditLog(userId, "EXPENSE DELETED", "Deleted expense: $title (-₱$amount)")
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
                val category = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_EXPENSE_CATEGORY))
                list.add(Expense(id, userId, title, amount, dateTime, ExpenseCategory.fromStored(category)))
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
    fun addIncome(userId: Int, title: String, amount: Double, dateTime: String,
                  category: String = ExpenseCategory.OTHER.label): Boolean {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_INCOME_USER_ID, userId)
            put(COLUMN_INCOME_TITLE, title)
            put(COLUMN_INCOME_AMOUNT, amount)
            put(COLUMN_INCOME_DATETIME, dateTime)
            put(COLUMN_INCOME_CATEGORY, ExpenseCategory.fromStored(category))
        }
        val success = db.insert(TABLE_INCOMES, null, values) != -1L
        if (success) {
            auditLogDatabase.addAuditLog(userId, "INCOME ADDED", "Added income: $title (+₱$amount)")
        }
        return success
    }

    fun updateIncome(id: Int, title: String, amount: Double, dateTime: String,
                     category: String = ExpenseCategory.OTHER.label): Boolean {
        val db = this.writableDatabase
        
        var userId = -1
        val cursor = db.rawQuery("SELECT $COLUMN_INCOME_USER_ID FROM $TABLE_INCOMES WHERE $COLUMN_INCOME_ID=?", arrayOf(id.toString()))
        if (cursor.moveToFirst()) userId = cursor.getInt(0)
        cursor.close()

        val values = ContentValues().apply {
            put(COLUMN_INCOME_TITLE, title)
            put(COLUMN_INCOME_AMOUNT, amount)
            put(COLUMN_INCOME_DATETIME, dateTime)
            put(COLUMN_INCOME_CATEGORY, ExpenseCategory.fromStored(category))
        }
        val success = db.update(TABLE_INCOMES, values, "$COLUMN_INCOME_ID=?", arrayOf(id.toString())) > 0
        if (success && userId != -1) {
            auditLogDatabase.addAuditLog(userId, "INCOME EDITED", "Edited income: $title (New amount: +₱$amount)")
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
            auditLogDatabase.addAuditLog(userId, "INCOME DELETED", "Deleted income: $title (+₱$amount)")
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
                val category = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_INCOME_CATEGORY))
                list.add(Income(id, userId, title, amount, dateTime, ExpenseCategory.fromStored(category)))
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
                    val category = cursorIncome.getString(cursorIncome.getColumnIndexOrThrow(COLUMN_INCOME_CATEGORY))
                    list.add(Transaction(id, userId, title, amount, dateTime, TransactionType.INCOME,
                        ExpenseCategory.fromStored(category)))
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
                    val category = cursorExpense.getString(cursorExpense.getColumnIndexOrThrow(COLUMN_EXPENSE_CATEGORY))
                    list.add(Transaction(id, userId, title, amount, dateTime, TransactionType.EXPENSE,
                        ExpenseCategory.fromStored(category)))
                } while (cursorExpense.moveToNext())
            }
            cursorExpense.close()
        }

        list.sortByDescending { it.id }
        return list
    }

    /** Restores a transaction deleted in the current session (used by Undo). */
    fun restoreTransaction(transaction: Transaction): Boolean {
        val db = writableDatabase
        val table = if (transaction.type == TransactionType.INCOME) TABLE_INCOMES else TABLE_EXPENSES
        val values = ContentValues().apply {
            put(if (transaction.type == TransactionType.INCOME) COLUMN_INCOME_ID else COLUMN_EXPENSE_ID, transaction.id)
            put(if (transaction.type == TransactionType.INCOME) COLUMN_INCOME_USER_ID else COLUMN_EXPENSE_USER_ID, transaction.userId)
            put(if (transaction.type == TransactionType.INCOME) COLUMN_INCOME_TITLE else COLUMN_EXPENSE_TITLE, transaction.title)
            put(if (transaction.type == TransactionType.INCOME) COLUMN_INCOME_AMOUNT else COLUMN_EXPENSE_AMOUNT, transaction.amount)
            put(if (transaction.type == TransactionType.INCOME) COLUMN_INCOME_DATETIME else COLUMN_EXPENSE_DATETIME, transaction.dateTime)
            put(if (transaction.type == TransactionType.INCOME) COLUMN_INCOME_CATEGORY else COLUMN_EXPENSE_CATEGORY, transaction.category)
        }
        return db.insertWithOnConflict(table, null, values, SQLiteDatabase.CONFLICT_IGNORE) != -1L
    }

    fun addDebt(userId: Int, name: String, amount: Double, dueDate: String?): Boolean {
        val values = ContentValues().apply {
            put(COLUMN_DEBT_USER_ID, userId)
            put(COLUMN_DEBT_NAME, name)
            put(COLUMN_DEBT_AMOUNT, amount)
            put(COLUMN_DEBT_DUE_DATE, dueDate)
        }
        return writableDatabase.insert(TABLE_DEBTS, null, values) != -1L
    }

    fun setDebtPaid(id: Int, paid: Boolean): Boolean {
        val values = ContentValues().apply { put(COLUMN_DEBT_PAID, if (paid) 1 else 0) }
        return writableDatabase.update(TABLE_DEBTS, values, "$COLUMN_DEBT_ID=?",
            arrayOf(id.toString())) > 0
    }

    fun deleteDebt(id: Int): Boolean =
        writableDatabase.delete(TABLE_DEBTS, "$COLUMN_DEBT_ID=?", arrayOf(id.toString())) > 0

    fun getDebtsByUser(userId: Int): List<Debt> {
        val result = ArrayList<Debt>()
        val cursor = readableDatabase.rawQuery(
            "SELECT * FROM $TABLE_DEBTS WHERE $COLUMN_DEBT_USER_ID=? ORDER BY $COLUMN_DEBT_PAID ASC, $COLUMN_DEBT_ID DESC",
            arrayOf(userId.toString())
        )
        cursor.use {
            while (it.moveToNext()) {
                result.add(Debt(
                    it.getInt(it.getColumnIndexOrThrow(COLUMN_DEBT_ID)),
                    userId,
                    it.getString(it.getColumnIndexOrThrow(COLUMN_DEBT_NAME)),
                    it.getDouble(it.getColumnIndexOrThrow(COLUMN_DEBT_AMOUNT)),
                    it.getString(it.getColumnIndexOrThrow(COLUMN_DEBT_DUE_DATE)),
                    it.getInt(it.getColumnIndexOrThrow(COLUMN_DEBT_PAID)) == 1
                ))
            }
        }
        return result
    }

}