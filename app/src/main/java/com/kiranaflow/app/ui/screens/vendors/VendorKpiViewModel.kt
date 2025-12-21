package com.kiranaflow.app.ui.screens.vendors

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kiranaflow.app.data.local.ItemEntity
import com.kiranaflow.app.data.local.KiranaDatabase
import com.kiranaflow.app.data.local.PartyEntity
import com.kiranaflow.app.data.local.TransactionEntity
import com.kiranaflow.app.data.local.TransactionItemEntity
import com.kiranaflow.app.data.repository.KiranaRepository
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class VendorKpiViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = KiranaRepository(KiranaDatabase.getDatabase(application))

    val lowStockItems: StateFlow<List<ItemEntity>> = repo.allItems
        .map { items ->
            fun stockFor(item: ItemEntity): Double = if (item.isLoose) item.stockKg else item.stock.toDouble()
            items
                .filter { item -> stockFor(item) < item.reorderPoint.toDouble() }
                .sortedBy { item -> stockFor(item) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val vendors: StateFlow<List<PartyEntity>> = repo.vendors
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val vendorsById: StateFlow<Map<Int, PartyEntity>> = vendors
        .map { it.associateBy { v -> v.id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val vendorsWithPayables: StateFlow<List<PartyEntity>> = repo.vendors
        .map { it.filter { v -> v.balance < 0 }.sortedByDescending { v -> kotlin.math.abs(v.balance) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val vendorTransactionsById: StateFlow<Map<Int, List<TransactionEntity>>> = repo.allTransactions
        .map { txs ->
            txs.filter { it.vendorId != null }
                .groupBy { it.vendorId!! }
                .mapValues { (_, list) -> list.sortedByDescending { it.date }.take(20) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val transactionItemsByTxId: StateFlow<Map<Int, List<TransactionItemEntity>>> = repo.allTransactionItems
        .map { items -> items.groupBy { it.transactionId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun recordVendorPayment(vendor: PartyEntity, amount: Double, mode: String) {
        viewModelScope.launch { repo.recordPayment(vendor, amount, mode) }
    }

    fun addReminder(type: String, refId: Int?, title: String, note: String?, dueAt: Long) {
        viewModelScope.launch {
            repo.addReminder(type = type, refId = refId, title = title, dueAt = dueAt, note = note)
        }
    }
}


