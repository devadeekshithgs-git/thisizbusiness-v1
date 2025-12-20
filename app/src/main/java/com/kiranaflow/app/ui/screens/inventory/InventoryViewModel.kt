package com.kiranaflow.app.ui.screens.inventory

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kiranaflow.app.data.local.ItemEntity
import com.kiranaflow.app.data.local.KiranaDatabase
import com.kiranaflow.app.data.local.PartyEntity
import com.kiranaflow.app.data.remote.OffProductInfo
import com.kiranaflow.app.data.remote.OpenFoodFactsClient
import com.kiranaflow.app.data.repository.KiranaRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class InventoryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = KiranaRepository(KiranaDatabase.getDatabase(application))
    private val itemDao = KiranaDatabase.getDatabase(application).itemDao()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _items = repository.allItems // Flow<List>

    val vendors: StateFlow<List<PartyEntity>> = repository.vendors
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    val filteredItems: StateFlow<List<ItemEntity>> = combine(_items, _searchQuery) { items, query ->
        if (query.isEmpty()) items
        else items.filter { it.name.contains(query, ignoreCase = true) || it.category.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _scannedItem = MutableStateFlow<ItemEntity?>(null)
    val scannedItem: StateFlow<ItemEntity?> = _scannedItem.asStateFlow()

    private val _scannedBarcode = MutableStateFlow<String?>(null)
    val scannedBarcode: StateFlow<String?> = _scannedBarcode.asStateFlow()

    private val _offProduct = MutableStateFlow<OffProductInfo?>(null)
    val offProduct: StateFlow<OffProductInfo?> = _offProduct.asStateFlow()

    private val _offLoading = MutableStateFlow(false)
    val offLoading: StateFlow<Boolean> = _offLoading.asStateFlow()

    fun onSearchChange(query: String) {
        _searchQuery.value = query
    }

    fun onBarcodeScanned(barcode: String) {
        val raw = barcode.trim()
        if (raw.isBlank()) return
        viewModelScope.launch {
            _scannedBarcode.value = raw
            val found = itemDao.getItemByBarcode(raw) ?: itemDao.getItemByBarcode(raw.replace(" ", ""))
            _scannedItem.value = found
            Log.d("InventoryViewModel", "Barcode scanned='$raw' foundInDb=${found != null}")

            // If not found locally, attempt OFF lookup to prefill name/category.
            if (found == null) {
                _offLoading.value = true
                val off = withContext(Dispatchers.IO) { OpenFoodFactsClient.fetchProduct(raw) }
                _offProduct.value = off
                _offLoading.value = false
                Log.d("InventoryViewModel", "OFF lookup done barcode='$raw' foundInOff=${off != null}")
            } else {
                _offProduct.value = null
                _offLoading.value = false
            }
        }
    }

    fun clearScannedItem() {
        _scannedItem.value = null
    }

    fun clearOffProduct() {
        _offProduct.value = null
        _offLoading.value = false
    }

    fun saveItem(
        id: Int? = null,
        name: String,
        category: String,
        cost: Double,
        sell: Double,
        stock: Int,
        location: String?,
        barcode: String?,
        gst: Double?,
        reorder: Int?,
        imageUri: String?,
        vendorId: Int? = null,
        expiryDateMillis: Long? = null
    ) {
        viewModelScope.launch {
            val cleanBarcode = barcode?.trim()?.ifBlank { null }
            val margin = if (cost > 0) ((sell - cost) / cost * 100) else 0.0

            repository.updateItem(
                ItemEntity(
                    id = id ?: 0,
                    name = name.trim(),
                    category = category.trim().ifBlank { "General" },
                    costPrice = cost,
                    price = sell,
                    stock = stock,
                    rackLocation = location?.trim()?.ifBlank { null },
                    marginPercentage = margin,
                    barcode = cleanBarcode,
                    vendorId = vendorId,
                    gstPercentage = gst,
                    reorderPoint = reorder ?: 10,
                    imageUri = imageUri,
                    expiryDateMillis = expiryDateMillis
                )
            )
        }
    }
    
    fun deleteItem(item: ItemEntity) {
        viewModelScope.launch {
            repository.deleteItem(item)
        }
    }

    fun deleteItemsByIds(ids: Set<Int>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repository.deleteItemsByIds(ids.toList())
        }
    }
}
