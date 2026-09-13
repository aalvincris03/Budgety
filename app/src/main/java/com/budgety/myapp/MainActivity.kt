package com.budgety.myapp

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ListView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import android.widget.LinearLayout
import android.widget.CheckBox
import android.text.Editable
import android.text.TextWatcher
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.button.MaterialButtonToggleGroup
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent

import android.content.Context
import android.content.SharedPreferences

class MainActivity : AppCompatActivity() {
    companion object {
        const val ACTION_QUICK_ADD_EXPENSE = "com.budgety.myapp.action.QUICK_ADD_EXPENSE"
    }

    private lateinit var dbHelper: DatabaseHelper
    private lateinit var auditLogDatabase: AuditLogDatabaseHelper
    private lateinit var drawerLayout: DrawerLayout

    // Header Views
    private lateinit var tvActiveUserName: TextView

    // Navigation Views
    private lateinit var btnNavDashboard: TextView
    private lateinit var btnNavUser: TextView
    private lateinit var btnNavHistory: TextView
    private lateinit var btnNavChart: TextView
    private lateinit var btnNavDebt: TextView

    // Layout Containers
    private lateinit var viewDashboard: View
    private lateinit var viewUser: View
    private lateinit var viewHistory: View
    private lateinit var viewChart: View
    private lateinit var viewDebt: View

    // Dashboard Views
    private lateinit var tvTotalIncome: TextView
    private lateinit var tvTotalExpenses: TextView
    private lateinit var tvRemainingBalance: TextView
    private lateinit var listViewDashboardHistory: ListView

    // User Tab Views
    private lateinit var spinnerUsers: Spinner
    private lateinit var etNewUserName: EditText
    private lateinit var btnCreateUser: Button
    private lateinit var btnRenameUser: Button 
    private lateinit var btnBackToHome: Button // <-- 1. Idinagdag ang declaration dito

    // History Tab Views
    private lateinit var toggleHistoryFilter: MaterialButtonToggleGroup
    private lateinit var listViewFullHistory: ListView
    private lateinit var etTransactionSearch: EditText
    private lateinit var spinnerSort: Spinner
    private lateinit var btnStartDate: Button
    private lateinit var btnEndDate: Button
    private lateinit var tvReportSummary: TextView
    private lateinit var categoryBarChart: BarChartView

    private var userList: List<User> = ArrayList()
    private var activeUser: User? = null
    private var currentFilter = "ALL" // ALL, INCOME, EXPENSE
    private var startDate: Calendar? = null
    private var endDate: Calendar? = null
    private var amountsHidden = false
    private var lastDeleted: Transaction? = null

    private val dateTimeFormat = SimpleDateFormat("MMMM dd, yyyy - hh:mm a", Locale.ENGLISH)
	
    private var selectedUserId: Int = 1
    
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val savedDarkMode = getSharedPreferences("BudgetAppPrefs", Context.MODE_PRIVATE)
            .getBoolean("DARK_MODE", false)
        AppCompatDelegate.setDefaultNightMode(
            if (savedDarkMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
        supportActionBar?.hide()
        setContentView(R.layout.activity_main)

        dbHelper = DatabaseHelper(this)
        auditLogDatabase = AuditLogDatabaseHelper(this)
        sharedPreferences = getSharedPreferences("BudgetAppPrefs", Context.MODE_PRIVATE)

        drawerLayout = findViewById(R.id.drawerLayout)
        val btnShowNav = findViewById<ImageButton>(R.id.btnShowNav)
        val btnHideNav = findViewById<ImageButton>(R.id.btnHideNav)

        tvActiveUserName = findViewById(R.id.tvActiveUserName)

        btnNavDashboard = findViewById(R.id.btnNavDashboard)
        btnNavUser = findViewById(R.id.btnNavUser)
        btnNavHistory = findViewById(R.id.btnNavHistory)
        btnNavChart = findViewById(R.id.btnNavChart)
        btnNavDebt = findViewById(R.id.btnNavDebt)

        viewDashboard = findViewById(R.id.viewDashboard)
        viewUser = findViewById(R.id.viewUser)
        viewHistory = findViewById(R.id.viewHistory)
        viewChart = findViewById(R.id.viewChart)
        viewDebt = findViewById(R.id.viewDebt)

        // Dashboard Elements
        tvTotalIncome = findViewById(R.id.tvTotalIncome)
        tvTotalExpenses = findViewById(R.id.tvTotalExpenses)
        tvRemainingBalance = findViewById(R.id.tvRemainingBalance)
        listViewDashboardHistory = findViewById(R.id.listViewDashboardHistory)
        val btnDashAddIncome = findViewById<Button>(R.id.btnDashAddIncome)
        val btnDashAddExpense = findViewById<Button>(R.id.btnDashAddExpense)

        // User Tab Elements
        spinnerUsers = findViewById(R.id.spinnerUsersTab)
        etNewUserName = findViewById(R.id.etNewUserName)
        btnCreateUser = findViewById(R.id.btnCreateUser)
        btnRenameUser = findViewById(R.id.btnRenameUser) 
        btnBackToHome = findViewById(R.id.btnBackToHome) // <-- 2. Idinagdag ang findViewById dito

        // History Tab Elements
        toggleHistoryFilter = findViewById(R.id.toggleHistoryFilter)
        listViewFullHistory = findViewById(R.id.listViewFullHistory)
        etTransactionSearch = findViewById(R.id.etTransactionSearch)
        spinnerSort = findViewById(R.id.spinnerSort)
        btnStartDate = findViewById(R.id.btnStartDate)
        btnEndDate = findViewById(R.id.btnEndDate)
        tvReportSummary = findViewById(R.id.tvReportSummary)
        categoryBarChart = findViewById(R.id.categoryBarChart)

        // Drawer Show / Hide Controls
        btnShowNav.setOnClickListener { drawerLayout.openDrawer(GravityCompat.START) }
        btnHideNav.setOnClickListener { drawerLayout.closeDrawer(GravityCompat.START) }

        // Drawer Menu Switcher
        btnNavDashboard.setOnClickListener { switchTab(viewDashboard) }
        btnNavUser.setOnClickListener { switchTab(viewUser) }
        btnNavHistory.setOnClickListener { switchTab(viewHistory) }
        btnNavChart.setOnClickListener { switchTab(viewChart) }
        btnNavDebt.setOnClickListener { switchTab(viewDebt) }

        // Quick Add buttons on Dashboard
        btnDashAddIncome.setOnClickListener { showIncomeDialog(null) }
        btnDashAddExpense.setOnClickListener { showExpenseDialog(null) }

        // User Buttons
        btnCreateUser.setOnClickListener { handleCreateUser() }
        btnRenameUser.setOnClickListener { showRenameDialog() } 
        btnBackToHome.setOnClickListener { switchTab(viewDashboard) } // <-- 3. Idinagdag ang OnClickListener dito
        amountsHidden = sharedPreferences.getBoolean("HIDE_AMOUNTS", false)
        val btnToggleAmounts = findViewById<ImageButton>(R.id.btnToggleAmounts)
        btnToggleAmounts.setOnClickListener {
            amountsHidden = !amountsHidden
            sharedPreferences.edit().putBoolean("HIDE_AMOUNTS", amountsHidden).apply()
            updateAmountVisibilityIcon(btnToggleAmounts)
            loadData()
        }
        updateAmountVisibilityIcon(btnToggleAmounts)

        val btnToggleDarkMode = findViewById<ImageButton>(R.id.btnToggleDarkMode)
        btnToggleDarkMode.setOnClickListener {
            val dark = sharedPreferences.getBoolean("DARK_MODE", false).not()
            sharedPreferences.edit().putBoolean("DARK_MODE", dark).apply()
            updateDarkModeIcon(btnToggleDarkMode, dark)
            AppCompatDelegate.setDefaultNightMode(
                if (dark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            )
        }
        updateDarkModeIcon(btnToggleDarkMode, sharedPreferences.getBoolean("DARK_MODE", false))

        val sortOptions = arrayOf("Newest", "Oldest", "Highest amount", "Lowest amount", "Category")
        spinnerSort.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, sortOptions)
        spinnerSort.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = loadData()
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        etTransactionSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = loadData()
            override fun afterTextChanged(s: Editable?) = Unit
        })
        btnStartDate.setOnClickListener { chooseDate(true) }
        btnEndDate.setOnClickListener { chooseDate(false) }
        findViewById<Button>(R.id.btnOpenDebtTracker).setOnClickListener { showDebtTracker() }

        // History Filter Toggle
        toggleHistoryFilter.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                currentFilter = when (checkedId) {
                    R.id.btnFilterIncome -> "INCOME"
                    R.id.btnFilterExpense -> "EXPENSE"
                    else -> "ALL"
                }
                loadData()
            }
        }
		
        setupUserSpinner()
        switchTab(viewDashboard)
        if (intent?.action == ACTION_QUICK_ADD_EXPENSE) showExpenseDialog(null)
    }

    private fun switchTab(targetView: View) {
        viewDashboard.visibility = View.GONE
        viewUser.visibility = View.GONE
        viewHistory.visibility = View.GONE
        viewChart.visibility = View.GONE
        viewDebt.visibility = View.GONE

        targetView.visibility = View.VISIBLE
        drawerLayout.closeDrawer(GravityCompat.START)
        loadData()
    }

	private fun setupUserSpinner() {
        userList = dbHelper.allUsers
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, userList)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerUsers.adapter = adapter

        val lastSavedUserId = sharedPreferences.getInt("LAST_USER_ID", -1)
    
        var selectedIndex = 0 
        if (lastSavedUserId != -1) {
            val index = userList.indexOfFirst { it.id == lastSavedUserId }
            if (index >= 0) {
                selectedIndex = index
            }
        }

        if (userList.isNotEmpty()) {
            spinnerUsers.setSelection(selectedIndex) 
            activeUser = userList[selectedIndex]     
        }

        spinnerUsers.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                activeUser = userList[position]
                tvActiveUserName.text = "Active Account: ${activeUser?.name}"
                
                activeUser?.let {
                    sharedPreferences.edit().putInt("LAST_USER_ID", it.id).apply()
                }
                
                loadData()
            }
            
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun handleCreateUser() {
        val name = etNewUserName.text.toString().trim()
        if (name.isEmpty()) {
            etNewUserName.error = "User name is required!"
            return
        }

        if (dbHelper.addUser(name)) {
            Toast.makeText(this, "User added successfully!", Toast.LENGTH_SHORT).show()
            etNewUserName.text.clear()
            setupUserSpinner()
        } else {
            etNewUserName.error = "User name already exists!"
        }
    }

    private fun loadData() {
        val user = activeUser ?: return
        tvActiveUserName.text = "Active Account: ${user.name}"

        val incomeTotal = dbHelper.getTotalIncomeByUser(user.id)
        val expenseTotal = dbHelper.getTotalExpensesByUser(user.id)
        val remainingBalance = incomeTotal - expenseTotal

        tvTotalIncome.text = formatAmount(incomeTotal)
        tvTotalExpenses.text = formatAmount(expenseTotal)
        tvRemainingBalance.text = formatAmount(remainingBalance)

        // Dashboard Combined History
        val allTransactions = dbHelper.getAllTransactionsByUser(user.id, "ALL")
        val search = etTransactionSearch.text.toString().trim().toLowerCase(Locale.getDefault())
        val dashTransactions = sortTransactions(allTransactions
            .filter { search.isEmpty() || it.title.toLowerCase(Locale.getDefault()).contains(search) ||
                    it.category.toLowerCase(Locale.getDefault()).contains(search) }
            .filter { isInSelectedDateRange(it) })
        val dashAdapter = TransactionAdapter(
            this,
            dashTransactions,
            onEditClick = { transaction -> handleEditTransaction(transaction) },
            onDeleteClick = { transaction -> handleDeleteTransaction(transaction) },
            amountsHidden = amountsHidden
        )
        listViewDashboardHistory.adapter = dashAdapter
        updateReportSummary(allTransactions)
        categoryBarChart.setData(dashTransactions.filter { it.type == TransactionType.EXPENSE }
            .groupBy { it.category }
            .map { it.key to it.value.sumByDouble { transaction -> transaction.amount } }
            .sortedByDescending { it.second })

        // Full History View
        val fullHistoryLogs = auditLogDatabase.getAuditLogsByUser(user.id, currentFilter)
        val historyAdapter = AuditLogAdapter(
            this,
            fullHistoryLogs
        )
        listViewFullHistory.adapter = historyAdapter
		updateWidget()
    }

    private fun formatAmount(amount: Double): String =
        if (amountsHidden) "••••" else String.format(Locale.ENGLISH, "₱%.2f", amount)

    private fun sortTransactions(items: List<Transaction>): List<Transaction> =
        when (spinnerSort.selectedItemPosition) {
            1 -> items.sortedBy { parseDate(it.dateTime)?.time ?: 0L }
            2 -> items.sortedByDescending { it.amount }
            3 -> items.sortedBy { it.amount }
            4 -> items.sortedWith(compareBy<Transaction> { it.category }.thenByDescending { it.id })
            else -> items.sortedWith(compareByDescending<Transaction> { parseDate(it.dateTime)?.time ?: 0L }
                .thenByDescending { it.id })
        }

    private fun parseDate(value: String): Date? = try { dateTimeFormat.parse(value) } catch (_: Exception) { null }

    private fun isInSelectedDateRange(transaction: Transaction): Boolean {
        val date = parseDate(transaction.dateTime)?.time ?: return true
        return date >= (startDate?.timeInMillis ?: Long.MIN_VALUE) &&
                date <= (endDate?.timeInMillis ?: Long.MAX_VALUE)
    }

    private fun updateAmountVisibilityIcon(button: ImageButton) {
        button.setImageResource(
            if (amountsHidden) R.drawable.ic_eye_closed
            else R.drawable.ic_eye_open
        )
        button.contentDescription = if (amountsHidden) "Show amounts" else "Hide amounts"
    }

    private fun updateDarkModeIcon(button: ImageButton, darkMode: Boolean) {
        button.setImageResource(
            if (darkMode) R.drawable.ic_sun else R.drawable.ic_moon
        )
        button.contentDescription = if (darkMode) "Switch to light mode" else "Switch to dark mode"
    }

    private fun chooseDate(isStart: Boolean) {
        val existing = if (isStart) startDate else endDate
        val calendar = existing?.clone() as? Calendar ?: Calendar.getInstance()
        DatePickerDialog(this, { _, year, month, day ->
            calendar.set(year, month, day)
            if (isStart) {
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                startDate = calendar
                btnStartDate.text = dateTimeFormat.format(calendar.time).substringBefore(" -")
            } else {
                calendar.set(Calendar.HOUR_OF_DAY, 23); calendar.set(Calendar.MINUTE, 59)
                endDate = calendar
                btnEndDate.text = dateTimeFormat.format(calendar.time).substringBefore(" -")
            }
            loadData()
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun updateReportSummary(transactions: List<Transaction>) {
        val now = Calendar.getInstance()
        val week = now.clone() as Calendar
        week.add(Calendar.DAY_OF_YEAR, -6)
        val month = now.clone() as Calendar
        month.set(Calendar.DAY_OF_MONTH, 1)
        fun totalSince(from: Calendar) = transactions.filter {
            it.type == TransactionType.EXPENSE && (parseDate(it.dateTime)?.time ?: 0L) >= from.timeInMillis
        }.sumByDouble { it.amount }
        tvReportSummary.text = "This week: ${formatAmount(totalSince(week))} spent  •  This month: ${formatAmount(totalSince(month))} spent"
    }

    private fun handleEditTransaction(item: Transaction) {
        if (item.type == TransactionType.INCOME) {
            val income = Income(item.id, item.userId, item.title, item.amount, item.dateTime, item.category)
            showIncomeDialog(income)
        } else {
            val expense = Expense(item.id, item.userId, item.title, item.amount, item.dateTime, item.category)
            showExpenseDialog(expense)
        }
    }

    private fun handleDeleteTransaction(item: Transaction) {
        val titleType = if (item.type == TransactionType.INCOME) "Income" else "Expense"
        AlertDialog.Builder(this)
            .setTitle("Delete $titleType")
            .setMessage("Are you sure you want to delete \"${item.title}\"?")
            .setPositiveButton("Delete") { _, _ ->
                val success = if (item.type == TransactionType.INCOME) {
                    dbHelper.deleteIncome(item.id)
                } else {
                    dbHelper.deleteExpense(item.id)
                }
                if (success) {
                    lastDeleted = item
                    loadData()
                    Snackbar.make(findViewById(R.id.drawerLayout), "$titleType deleted",
                        Snackbar.LENGTH_LONG).setAction("UNDO") {
                        lastDeleted?.let {
                            if (dbHelper.restoreTransaction(it)) loadData()
                        }
                        lastDeleted = null
                    }.show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showIncomeDialog(incomeToEdit: Income?) {
        val user = activeUser ?: return
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_add_income, null)

        val etTitle = view.findViewById<EditText>(R.id.etTitle)
        val etAmount = view.findViewById<EditText>(R.id.etAmount)
        val etDateTime = view.findViewById<EditText>(R.id.etDateTime)
        val spinnerCategory = view.findViewById<Spinner>(R.id.spinnerCategory)
        val categories = ExpenseCategory.values().map { it.label }
        spinnerCategory.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, categories)

        val calendar = Calendar.getInstance()

        if (incomeToEdit != null) {
            etTitle.setText(incomeToEdit.title)
            etAmount.setText(incomeToEdit.amount.toString())
            etDateTime.setText(incomeToEdit.dateTime)
            spinnerCategory.setSelection(categories.indexOf(incomeToEdit.category).coerceAtLeast(0))
        } else {
            etDateTime.setText(dateTimeFormat.format(Date()))
        }

        etDateTime.setOnClickListener {
            DatePickerDialog(
                this,
                { _, year, month, dayOfMonth ->
                    calendar.set(Calendar.YEAR, year)
                    calendar.set(Calendar.MONTH, month)
                    calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)

                    TimePickerDialog(
                        this,
                        { _, hourOfDay, minute ->
                            calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                            calendar.set(Calendar.MINUTE, minute)
                            etDateTime.setText(dateTimeFormat.format(calendar.time))
                        },
                        calendar.get(Calendar.HOUR_OF_DAY),
                        calendar.get(Calendar.MINUTE),
                        false
                    ).show()
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        val dialogTitle = if (incomeToEdit == null) "Add Budget / Income" else "Edit Income"

        val dialog = AlertDialog.Builder(this)
            .setTitle(dialogTitle)
            .setView(view)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val title = etTitle.text.toString().trim()
            val amountStr = etAmount.text.toString().trim()
            val dateTimeStr = etDateTime.text.toString().trim()
            val category = spinnerCategory.selectedItem?.toString() ?: ExpenseCategory.OTHER.label

            var isValid = true

            if (title.isEmpty()) {
                etTitle.error = "Title is required!"
                isValid = false
            }

            if (amountStr.isEmpty()) {
                etAmount.error = "Amount is required!"
                isValid = false
            } else {
                val valAmount = amountStr.toDoubleOrNull()
                if (valAmount == null || valAmount <= 0) {
                    etAmount.error = "Please enter a valid amount!"
                    isValid = false
                }
            }

            if (!isValid) return@setOnClickListener

            val amount = amountStr.toDouble()
            val success = if (incomeToEdit == null) {
                dbHelper.addIncome(user.id, title, amount, dateTimeStr, category)
            } else {
                dbHelper.updateIncome(incomeToEdit.id, title, amount, dateTimeStr, category)
            }

            if (success) {
                Toast.makeText(this, "Income saved successfully!", Toast.LENGTH_SHORT).show()
                loadData()
                dialog.dismiss()
            } else {
                Toast.makeText(this, "Failed to save income.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showExpenseDialog(expenseToEdit: Expense?) {
        val user = activeUser ?: return
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_add_expense, null)

        val etTitle = view.findViewById<EditText>(R.id.etTitle)
        val etAmount = view.findViewById<EditText>(R.id.etAmount)
        val etDateTime = view.findViewById<EditText>(R.id.etDateTime)
        val spinnerCategory = view.findViewById<Spinner>(R.id.spinnerCategory)
        val categories = ExpenseCategory.values().map { it.label }
        spinnerCategory.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, categories)

        val calendar = Calendar.getInstance()

        if (expenseToEdit != null) {
            etTitle.setText(expenseToEdit.title)
            etAmount.setText(expenseToEdit.amount.toString())
            etDateTime.setText(expenseToEdit.dateTime)
            spinnerCategory.setSelection(categories.indexOf(expenseToEdit.category).coerceAtLeast(0))
        } else {
            etDateTime.setText(dateTimeFormat.format(Date()))
        }

        etDateTime.setOnClickListener {
            DatePickerDialog(
                this,
                { _, year, month, dayOfMonth ->
                    calendar.set(Calendar.YEAR, year)
                    calendar.set(Calendar.MONTH, month)
                    calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)

                    TimePickerDialog(
                        this,
                        { _, hourOfDay, minute ->
                            calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                            calendar.set(Calendar.MINUTE, minute)
                            etDateTime.setText(dateTimeFormat.format(calendar.time))
                        },
                        calendar.get(Calendar.HOUR_OF_DAY),
                        calendar.get(Calendar.MINUTE),
                        false
                    ).show()
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        val dialogTitle = if (expenseToEdit == null) "Add Expense" else "Edit Expense"

        val dialog = AlertDialog.Builder(this)
            .setTitle(dialogTitle)
            .setView(view)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val title = etTitle.text.toString().trim()
            val amountStr = etAmount.text.toString().trim()
            val dateTimeStr = etDateTime.text.toString().trim()
            val category = spinnerCategory.selectedItem?.toString() ?: ExpenseCategory.OTHER.label

            var isValid = true

            if (title.isEmpty()) {
                etTitle.error = "Title is required!"
                isValid = false
            }

            if (amountStr.isEmpty()) {
                etAmount.error = "Amount is required!"
                isValid = false
            } else {
                val valAmount = amountStr.toDoubleOrNull()
                if (valAmount == null || valAmount <= 0) {
                    etAmount.error = "Please enter a valid amount!"
                    isValid = false
                }
            }

            if (!isValid) return@setOnClickListener

            val amount = amountStr.toDouble()
            val success = if (expenseToEdit == null) {
                dbHelper.addExpense(user.id, title, amount, dateTimeStr, category)
            } else {
                dbHelper.updateExpense(expenseToEdit.id, title, amount, dateTimeStr, category)
            }

            if (success) {
                Toast.makeText(this, "Expense saved successfully!", Toast.LENGTH_SHORT).show()
                loadData()
                dialog.dismiss()
            } else {
                Toast.makeText(this, "Failed to save expense.", Toast.LENGTH_SHORT).show()
  
          }
        }
    }

    private fun updateWidget() {
        val intent = Intent(this, BudgetWidgetProvider::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
        }
        val ids = AppWidgetManager.getInstance(application).getAppWidgetIds(
            ComponentName(application, BudgetWidgetProvider::class.java)
        )
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        sendBroadcast(intent)
    }

    private fun showDebtTracker() {
        val user = activeUser ?: return
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 8, 32, 8)
        }
        val name = EditText(this).apply { hint = "Debt name" }
        val amount = EditText(this).apply {
            hint = "Amount (₱)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val dueDate = EditText(this).apply { hint = "Due date (optional, e.g. 30 Sep 2026)" }
        container.addView(name); container.addView(amount); container.addView(dueDate)
        val debtRows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        container.addView(TextView(this).apply {
            text = "Outstanding debts"
            textSize = 16f
            setPadding(0, 20, 0, 8)
        })
        container.addView(debtRows)

        fun refreshRows() {
            debtRows.removeAllViews()
            dbHelper.getDebtsByUser(user.id).forEach { debt ->
                val row = CheckBox(this).apply {
                    text = "${debt.name}  ${if (amountsHidden) "••••" else String.format("₱%.2f", debt.amount)}" +
                            (if (debt.dueDate.isNullOrBlank()) "" else " • due ${debt.dueDate}")
                    isChecked = debt.paid
                    setOnCheckedChangeListener { _, checked -> dbHelper.setDebtPaid(debt.id, checked) }
                }
                debtRows.addView(row)
            }
            if (debtRows.childCount == 0) debtRows.addView(TextView(this).apply { text = "No debts recorded." })
        }
        refreshRows()
        val dialog = AlertDialog.Builder(this)
            .setTitle("Debt tracker")
            .setView(container)
            .setPositiveButton("Add debt", null)
            .setNegativeButton("Close", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val value = amount.text.toString().toDoubleOrNull()
                if (name.text.toString().trim().isEmpty() || value == null || value <= 0) {
                    Toast.makeText(this, "Enter a debt name and a positive amount.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (dbHelper.addDebt(user.id, name.text.toString().trim(), value, dueDate.text.toString().trim())) {
                    name.text.clear(); amount.text.clear(); dueDate.text.clear(); refreshRows()
                    Toast.makeText(this, "Debt added.", Toast.LENGTH_SHORT).show()
                }
            }
        }
        dialog.show()
    }

    // ==========================================
    //            RENAME USER FUNCTIONS
    // ==========================================
    
    private fun showRenameDialog() {
        val user = activeUser ?: return

        val etNewName = EditText(this).apply {
            hint = "Enter new name"
            setText(user.name)
        }

        AlertDialog.Builder(this)
            .setTitle("Rename Account")
            .setView(etNewName)
            .setPositiveButton("Save") { _, _ ->
                val newName = etNewName.text.toString().trim()
                if (newName.isNotEmpty() && newName != user.name) {
                    renameUser(user.id, newName)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun renameUser(userId: Int, newName: String) {
        val lastRenameTime = sharedPreferences.getLong("LAST_RENAME_$userId", 0L)
        val currentTime = System.currentTimeMillis()
        val oneDayInMillis = 24 * 60 * 60 * 1000

        if (currentTime - lastRenameTime >= oneDayInMillis) {
            val success = dbHelper.updateUserName(userId, newName)
            
            if (success) {
                sharedPreferences.edit().putLong("LAST_RENAME_$userId", currentTime).apply()
                Toast.makeText(this, "User renamed successfully!", Toast.LENGTH_SHORT).show()
                setupUserSpinner() 
            } else {
                Toast.makeText(this, "Failed to rename user.", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "You can only rename a user once every 24 hours.", Toast.LENGTH_LONG).show()
        }
    }
}