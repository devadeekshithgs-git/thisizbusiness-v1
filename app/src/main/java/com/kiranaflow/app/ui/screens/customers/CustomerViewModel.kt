package com.kiranaflow.app.ui.screens.customers

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kiranaflow.app.data.local.CustomerEntity
import com.kiranaflow.app.data.local.KiranaDatabase
import com.kiranaflow.app.data.local.TransactionEntity
import com.kiranaflow.app.data.repository.KiranaRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CustomerState(
    val customers: List<CustomerEntity> = emptyList(),
    val totalReceivables: Double = 0.0,
    val searchQuery: String = "",
    val isLoading: Boolean = true,
    val customerTransactions: List<TransactionEntity> = emptyList()
)

class CustomerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = KiranaRepository(KiranaDatabase.getDatabase(application))

    private val _searchQuery = MutableStateFlow("")
    private val _isLoading = MutableStateFlow(true)
    private val _customerTransactions = MutableStateFlow<List<TransactionEntity>>(emptyList())

    private val customersFlow: StateFlow<List<CustomerEntity>> =
        combine(repository.customers, _searchQuery) { customers, query ->
            if (query.isBlank()) customers
            else customers.filter {
                it.name.contains(query, ignoreCase = true) || it.phone.contains(query, ignoreCase = true)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val totalReceivablesFlow: StateFlow<Double> =
        customersFlow
            .map { list -> list.sumOf { if (it.balance > 0) it.balance else 0.0 } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val state: StateFlow<CustomerState> =
        combine(
            customersFlow,
            totalReceivablesFlow,
            _searchQuery,
            _isLoading,
            _customerTransactions
        ) { customers, totalReceivables, searchQuery, isLoading, txns ->
            CustomerState(
                customers = customers,
                totalReceivables = totalReceivables,
                searchQuery = searchQuery,
                isLoading = isLoading,
                customerTransactions = txns
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CustomerState())

    init {
        repository.allTransactions
            .onEach { txns ->
                _customerTransactions.value = txns
            }
            .launchIn(viewModelScope)

        _isLoading.value = false
    }

    fun search(query: String) {
        _searchQuery.value = query
    }

    fun recordPayment(customerId: Int, amount: Double, paymentMethod: String) {
        viewModelScope.launch {
            val customers = repository.customers.first()
            val customer = customers.firstOrNull { it.id == customerId } ?: return@launch
            repository.recordPayment(customer, amount, paymentMethod)
        }
    }
}