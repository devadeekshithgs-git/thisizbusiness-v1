package com.kiranaflow.app.ui.screens.vendors

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kiranaflow.app.data.local.ItemEntity
import com.kiranaflow.app.data.local.KiranaDatabase
import com.kiranaflow.app.data.local.PartyEntity
import com.kiranaflow.app.data.repository.KiranaRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class VendorKpiViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = KiranaRepository(KiranaDatabase.getDatabase(application))

    val lowStockItems: StateFlow<List<ItemEntity>> = repo.allItems
        .map { it.filter { item -> item.stock < item.reorderPoint }.sortedBy { item -> item.stock } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val vendorsWithPayables: StateFlow<List<PartyEntity>> = repo.vendors
        .map { it.filter { v -> v.balance < 0 }.sortedByDescending { v -> kotlin.math.abs(v.balance) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addReminder(type: String, refId: Int?, title: String, note: String?, dueAt: Long) {
        viewModelScope.launch {
            repo.addReminder(type = type, refId = refId, title = title, dueAt = dueAt, note = note)
        }
    }
}


