package com.budgety.myapp

enum class TransactionType {
    INCOME, EXPENSE
}

data class Transaction(
    val id: Int,
    val userId: Int,
    val title: String,
    val amount: Double,
    val dateTime: String,
    val type: TransactionType
)
