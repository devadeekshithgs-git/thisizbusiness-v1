package com.kiranaflow.app.ui.screens.billing

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kiranaflow.app.data.local.ItemEntity
import com.kiranaflow.app.data.repository.KiranaRepository
import com.kiranaflow.app.data.local.KiranaDatabase
import android.util.Log
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class BoxCartItem(val item: ItemEntity, val qty: Double)

data class BillSavedEvent(
    val txId: Int,
    val customerId: Int?,
    val paymentMode: String,
    val totalAmount: Double,
    val createdAtMillis: Long,
    val items: List<BillSavedLineItem>
)

data class BillSavedLineItem(
    val itemId: Int,
    val name: String,
    val qty: Double,
    val isLoose: Boolean,
    val unitPrice: Double,
    val unitLabel: String,
    val lineTotal: Double
)

sealed interface BillingScanResult {
    data class Added(val item: ItemEntity) : BillingScanResult
    data class NotFound(val barcode: String) : BillingScanResult
}

class BillingViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = KiranaRepository(KiranaDatabase.getDatabase(application))
    
    // Bill Items State (renamed from cart to billItems)
    private val _billItems = MutableStateFlow<List<BoxCartItem>>(emptyList())
    val billItems: StateFlow<List<BoxCartItem>> = _billItems.asStateFlow()
    
    // Keep cart as alias for backward compatibility
    val cart: StateFlow<List<BoxCartItem>> = _billItems.asStateFlow()

    // Search
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    
    // Keep billingSearch as alias for backward compatibility
    val billingSearch: StateFlow<String> = _searchQuery.asStateFlow()

    private val _items = repository.allItems
    val searchResults: StateFlow<List<ItemEntity>> = combine(_items, _searchQuery) { items, query ->
        if (query.isEmpty()) emptyList()
        else items.filter { it.name.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    // Keep searchResults as alias, add searchItems
    val searchItems: StateFlow<List<ItemEntity>> = searchResults

    private fun lineTotal(item: ItemEntity, qty: Double): Double {
        return if (item.isLoose) {
            item.pricePerKg * qty
        } else {
            item.price * qty
        }
    }

    val totalAmount: StateFlow<Double> = _billItems.map { list ->
        list.sumOf { lineTotal(it.item, it.qty) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)
    
    // Keep cartTotal as alias for backward compatibility
    val cartTotal: StateFlow<Double> = totalAmount

    fun onSearchChange(query: String) {
        _searchQuery.value = query
    }
    
    fun searchItems(query: String) {
        _searchQuery.value = query
    }

    fun addToCart(item: ItemEntity) {
        addItemToBill(item, 1.0)
    }
    
    fun addItemToBill(item: ItemEntity, quantity: Double = 1.0) {
        val currentList = _billItems.value.toMutableList()
        val existingIndex = currentList.indexOfFirst { it.item.id == item.id }
        val effectiveQty = if (item.isLoose && quantity == 1.0) 0.25 else quantity // default 0.25kg
        if (existingIndex != -1) {
            val existing = currentList[existingIndex]
            currentList[existingIndex] = existing.copy(qty = existing.qty + effectiveQty)
        } else {
            currentList.add(BoxCartItem(item, effectiveQty))
        }
        _billItems.value = currentList
        _searchQuery.value = "" // clear search
    }
    
    fun updateItemQuantity(itemId: Int, quantity: Double) {
        val currentList = _billItems.value.toMutableList()
        val index = currentList.indexOfFirst { it.item.id == itemId }
        if (index != -1) {
            if (quantity <= 0.0) {
                currentList.removeAt(index)
            } else {
                val existing = currentList[index]
                currentList[index] = existing.copy(qty = quantity)
            }
            _billItems.value = currentList
        }
    }
    
    fun removeItemFromBill(itemId: Int) {
        val currentList = _billItems.value.toMutableList()
        currentList.removeAll { it.item.id == itemId }
        _billItems.value = currentList
    }

    /**
     * Update the unit selling price for a cart line.
     * - If [persist] is true, also updates the inventory selling price (ItemEntity.price) in Room.
     * - If false, only applies to this bill (discount/override).
     */
    fun updateCartItemUnitPrice(itemId: Int, newUnitPrice: Double, persist: Boolean) {
        if (newUnitPrice <= 0.0) return
        val currentList = _billItems.value.toMutableList()
        val index = currentList.indexOfFirst { it.item.id == itemId }
        if (index == -1) return

        val existing = currentList[index]
        val updatedItem = if (existing.item.isLoose) {
            existing.item.copy(pricePerKg = newUnitPrice, price = newUnitPrice)
        } else {
            existing.item.copy(price = newUnitPrice)
        }
        currentList[index] = existing.copy(item = updatedItem)
        _billItems.value = currentList

        if (persist) {
            viewModelScope.launch {
                runCatching { repository.updateItem(updatedItem) }
                    .onFailure { Log.e("BillingViewModel", "Failed to persist unit price update itemId=$itemId", it) }
            }
        }
    }
    
    fun completeBill(paymentMode: String, customerId: Int? = null) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val cartSnapshot = _billItems.value
            val items = cartSnapshot.map { it.item to it.qty }
            val total = cartSnapshot.sumOf { lineTotal(it.item, it.qty) }
            val txId = repository.processSale(items, paymentMode, customerId, total)

            val receiptItems = cartSnapshot.map { (item, qty) ->
                val unitPrice = if (item.isLoose) item.pricePerKg else item.price
                BillSavedLineItem(
                    itemId = item.id,
                    name = item.name,
                    qty = qty,
                    isLoose = item.isLoose,
                    unitPrice = unitPrice,
                    unitLabel = if (item.isLoose) "KG" else "PCS",
                    lineTotal = lineTotal(item, qty)
                )
            }
            _billItems.value = emptyList()
            _billSavedEvents.tryEmit(
                BillSavedEvent(
                    txId = txId,
                    customerId = customerId,
                    paymentMode = paymentMode,
                    totalAmount = total,
                    createdAtMillis = now,
                    items = receiptItems
                )
            )
        }
    }

    fun addItemToCartByBarcode(barcode: String) {
        viewModelScope.launch {
            val raw = barcode.trim()
            if (raw.isBlank()) return@launch

            // Prefer DB lookup to avoid mismatch with stale/incomplete in-memory lists.
            val db = KiranaDatabase.getDatabase(getApplication())
            val found = db.itemDao().getItemByBarcode(raw)
                ?: db.itemDao().getItemByBarcode(raw.replace(" ", ""))

            if (found != null) {
                addItemToBill(found, 1.0)
                _scanResults.tryEmit(BillingScanResult.Added(found))
            } else {
                Log.w("BillingViewModel", "Barcode not found in DB: '$raw'")
                _scanResults.tryEmit(BillingScanResult.NotFound(raw))
            }
        }
    }

    private val _scanResults = MutableSharedFlow<BillingScanResult>(extraBufferCapacity = 8)
    val scanResults: SharedFlow<BillingScanResult> = _scanResults.asSharedFlow()

    private val _billSavedEvents = MutableSharedFlow<BillSavedEvent>(extraBufferCapacity = 1)
    val billSavedEvents: SharedFlow<BillSavedEvent> = _billSavedEvents.asSharedFlow()

    fun updateQty(itemId: Int, delta: Double) {
        val currentList = _billItems.value.toMutableList()
        val index = currentList.indexOfFirst { it.item.id == itemId }
        if (index != -1) {
            val existing = currentList[index]
            val newQty = existing.qty + delta
            if (newQty <= 0.0) {
                currentList.removeAt(index)
            } else {
                currentList[index] = existing.copy(qty = newQty)
            }
            _billItems.value = currentList
        }
    }
    
    fun removeFromCart(itemId: Int) {
        removeItemFromBill(itemId)
    }

    fun processCheckout(paymentMode: String) {
        completeBill(paymentMode, null)
    }
}
