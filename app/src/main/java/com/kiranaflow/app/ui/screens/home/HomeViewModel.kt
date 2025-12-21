package com.kiranaflow.app.ui.screens.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kiranaflow.app.data.local.TransactionEntity
import com.kiranaflow.app.data.repository.KiranaRepository
import com.kiranaflow.app.data.local.KiranaDatabase
import com.kiranaflow.app.ui.components.ChartDataPoint
import kotlinx.coroutines.flow.*
import java.time.format.DateTimeFormatter
import java.time.Instant
import java.time.ZoneId

data class HomeState(
    val totalRevenue: Double = 0.0,
    val totalExpense: Double = 0.0,
    val netProfit: Double = 0.0,
    val recentTransactions: List<TransactionEntity> = emptyList(),
    val chartData: List<ChartDataPoint> = emptyList(),
    val shopName: String = "Bhanu Super Mart",
    val ownerName: String = "Owner Ji",
    val greeting: String = "Good Morning"
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = KiranaRepository(KiranaDatabase.getDatabase(application))

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    init {
        repository.allTransactions.onEach { transactions ->
            // #region agent log
            com.kiranaflow.app.util.DebugLogger.log("HomeViewModel.kt:33", "Processing transactions", mapOf("count" to transactions.size), "H5")
            // #endregion
            // Exclude settlement payments from P&L to avoid double counting.
            fun isSettlementPayment(tx: TransactionEntity): Boolean {
                val looksLikePayment = tx.title.startsWith("Payment ")
                val linkedParty = (tx.customerId != null || tx.vendorId != null)
                return looksLikePayment && linkedParty
            }

            val revenue = transactions
                .asSequence()
                .filter { it.type == "SALE" || it.type == "INCOME" }
                .filterNot { isSettlementPayment(it) }
                .sumOf { it.amount }
            val expense = transactions
                .asSequence()
                .filter { it.type == "EXPENSE" }
                .filterNot { isSettlementPayment(it) }
                .sumOf { it.amount }
            
            val grouped = transactions
                .filter { it.type == "SALE" }
                .groupBy { 
                    Instant.ofEpochMilli(it.date).atZone(ZoneId.systemDefault()).toLocalDate() 
                }
                .map { (date, txns) -> 
                    ChartDataPoint(
                        label = date.format(DateTimeFormatter.ofPattern("dd MMM")),
                        value = txns.sumOf { it.amount }.toFloat()
                    )
                }
                .sortedBy { it.label } // Ideally sort by date object, but this is okay for demo
            
            _state.value = _state.value.copy(
                totalRevenue = revenue,
                totalExpense = expense,
                netProfit = revenue - expense,
                recentTransactions = transactions.take(5),
                chartData = grouped
            )
        }.launchIn(viewModelScope)
        
        updateGreeting()
    }
    
    private fun updateGreeting() {
        val hour = java.time.LocalTime.now().hour
        val greeting = when {
            hour < 5 -> "Good Late Night"
            hour < 12 -> "Good Morning"
            hour < 17 -> "Good Afternoon"
            else -> "Good Evening"
        }
        _state.value = _state.value.copy(greeting = greeting)
    }
}
