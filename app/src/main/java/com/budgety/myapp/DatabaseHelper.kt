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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
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

    private lateinit var dbHelper: DatabaseHelper
    private lateinit var auditLogDatabase: AuditLogDatabaseHelper
    private lateinit var drawerLayout: DrawerLayout

    // Header Views
    private lateinit var tvActiveUserName: TextView

    // Navigation Views
    private lateinit var btnNavDashboard: TextView
    private lateinit var btnNavUser: TextView
    private lateinit var btnNavHistory: TextView

    // Layout Containers
    private lateinit var viewDashboard: View
    private lateinit var viewUser: View
    private lateinit var viewHistory: View

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

    private var userList: List<User> = ArrayList()
    private var activeUser: User? = null
    private var currentFilter = "ALL" // ALL, INCOME, EXPENSE

    private val dateTimeFormat = SimpleDateFormat("MMMM dd, yyyy - hh:mm a", Locale.ENGLISH)
	
    private var selectedUserId: Int = 1
    
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_main)

        dbHelper = DatabaseHelper(this)
        auditLogDatabase = AuditLogDatabaseHelper(this)

        drawerLayout = findViewById(R.id.drawerLayout)
        val btnShowNav = findViewById<ImageButton>(R.id.btnShowNav)
        val btnHideNav = findViewById<ImageButton>(R.id.btnHideNav)

        tvActiveUserName = findViewById(R.id.tvActiveUserName)

        btnNavDashboard = findViewById(R.id.btnNavDashboard)
        btnNavUser = findViewById(R.id.btnNavUser)
        btnNavHistory = findViewById(R.id.btnNavHistory)

        viewDashboard = findViewById(R.id.viewDashboard)
        viewUser = findViewById(R.id.viewUser)
        viewHistory = findViewById(R.id.viewHistory)

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

        // Drawer Show / Hide Controls
        btnShowNav.setOnClickListener { drawerLayout.openDrawer(GravityCompat.START) }
        btnHideNav.setOnClickListener { drawerLayout.closeDrawer(GravityCompat.START) }

        // Drawer Menu Switcher
        btnNavDashboard.setOnClickListener { switchTab(viewDashboard) }
        btnNavUser.setOnClickListener { switchTab(viewUser) }
        btnNavHistory.setOnClickListener { switchTab(viewHistory) }

        // Quick Add buttons on Dashboard
        btnDashAddIncome.setOnClickListener { showIncomeDialog(null) }
        btnDashAddExpense.setOnClickListener { showExpenseDialog(null) }

        // User Buttons
        btnCreateUser.setOnClickListener { handleCreateUser() }
        btnRenameUser.setOnClickListener { showRenameDialog() } 
        btnBackToHome.setOnClickListener { switchTab(viewDashboard) } // <-- 3. Idinagdag ang OnClickListener dito

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
		
        sharedPreferences = getSharedPreferences("BudgetAppPrefs", Context.MODE_PRIVATE)
		
        setupUserSpinner()
        switchTab(viewDashboard)
    }

    private fun switchTab(targetView: View) {
        viewDashboard.visibility = View.GONE
        viewUser.visibility = View.GONE
        viewHistory.visibility = View.GONE

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

        tvTotalIncome.text = String.format("₱%.2f", incomeTotal)
        tvTotalExpenses.text = String.format("₱%.2f", expenseTotal)
        tvRemainingBalance.text = String.format("₱%.2f", remainingBalance)

        // Dashboard Combined History
        val dashTransactions = dbHelper.getAllTransactionsByUser(user.id, "ALL")
        val dashAdapter = TransactionAdapter(
            this,
            dashTransactions,
            onEditClick = { transaction -> handleEditTransaction(transaction) },
            onDeleteClick = { transaction -> handleDeleteTransaction(transaction) }
        )
        listViewDashboardHistory.adapter = dashAdapter

        // Full History View
        val fullHistoryLogs = auditLogDatabase.getAuditLogsByUser(user.id, currentFilter)
        val historyAdapter = AuditLogAdapter(
            this,
            fullHistoryLogs
        )
        listViewFullHistory.adapter = historyAdapter
		updateWidget()
    }

    private fun handleEditTransaction(item: Transaction) {
        if (item.type == TransactionType.INCOME) {
            val income = Income(item.id, item.userId, item.title, item.amount, item.dateTime)
            showIncomeDialog(income)
        } else {
            val expense = Expense(item.id, item.userId, item.title, item.amount, item.dateTime)
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
                    Toast.makeText(this, "$titleType deleted.", Toast.LENGTH_SHORT).show()
                    loadData()
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

        val calendar = Calendar.getInstance()

        if (incomeToEdit != null) {
            etTitle.setText(incomeToEdit.title)
            etAmount.setText(incomeToEdit.amount.toString())
            etDateTime.setText(incomeToEdit.dateTime)
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
                dbHelper.addIncome(user.id, title, amount, dateTimeStr)
            } else {
                dbHelper.updateIncome(incomeToEdit.id, title, amount, dateTimeStr)
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

        val calendar = Calendar.getInstance()

        if (expenseToEdit != null) {
            etTitle.setText(expenseToEdit.title)
            etAmount.setText(expenseToEdit.amount.toString())
            etDateTime.setText(expenseToEdit.dateTime)
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
                dbHelper.addExpense(user.id, title, amount, dateTimeStr)
            } else {
                dbHelper.updateExpense(expenseToEdit.id, title, amount, dateTimeStr)
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
