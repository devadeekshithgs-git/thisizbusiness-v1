package com.kiranaflow.app.ui.screens.billing

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kiranaflow.app.data.local.BillingSessionDao
import com.kiranaflow.app.data.local.ItemEntity
import com.kiranaflow.app.data.repository.KiranaRepository
import com.kiranaflow.app.data.local.KiranaDatabase
import android.util.Log
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import com.kiranaflow.app.util.StockValidator
import com.kiranaflow.app.ui.screens.scanner.ScanMode

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
    data class OutOfStock(val message: String, val availableStock: Double) : BillingScanResult
    data class NotFound(val barcode: String) : BillingScanResult
}

sealed interface StockValidationEvent {
    data class Blocked(val message: String, val itemId: Int? = null) : StockValidationEvent
    data class CheckoutBlocked(val message: String, val offendingItemIds: Set<Int>) : StockValidationEvent
}

class BillingViewModel(application: Application) : AndroidViewModel(application) {
    private val db = KiranaDatabase.getDatabase(application)
    private val repository = KiranaRepository(db)
    private val billingSessionDao: BillingSessionDao = db.billingSessionDao()
 
    private var _scanMode by mutableStateOf(ScanMode.BARCODE)

    val scanMode: ScanMode
        get() = _scanMode
 
    fun setScanMode(mode: ScanMode) {
        _scanMode = mode
    }

    private val _sessions = MutableStateFlow<List<BillingSession>>(emptyList())
    val sessions: StateFlow<List<BillingSession>> = _sessions.asStateFlow()

    private val _activeSessionId = MutableStateFlow<String?>(null)
    val activeSessionId: StateFlow<String?> = _activeSessionId.asStateFlow()

    private var suppressPersist: Boolean = false

    private val currentSession: StateFlow<BillingSession?> = combine(_sessions, _activeSessionId) { list, id ->
        if (id == null) null else list.firstOrNull { it.sessionId == id }
    }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val billItems: StateFlow<List<BoxCartItem>> = currentSession
        .map { it?.items ?: emptyList() }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Keep cart as alias for backward compatibility
    val cart: StateFlow<List<BoxCartItem>> = billItems

    init {
        viewModelScope.launch {
            billingSessionDao.observeActiveSessions().collectLatest { entities ->
                suppressPersist = true
                try {
                    val restored = entities.map { it.toModel() }
                    if (restored.isEmpty()) {
                        val session = BillingSession()
                        _sessions.value = listOf(session)
                        _activeSessionId.value = session.sessionId
                        suppressPersist = false
                        persistSessions(_sessions.value)
                    } else {
                        val preferred = restored.firstOrNull { it.status == SessionStatus.ACTIVE } ?: restored.first()
                        val normalized = restored.map {
                            if (it.sessionId == preferred.sessionId) it.copy(status = SessionStatus.ACTIVE)
                            else it.copy(status = SessionStatus.ON_HOLD)
                        }
                        _sessions.value = normalized
                        _activeSessionId.value = preferred.sessionId
                    }
                } finally {
                    suppressPersist = false
                }
            }
        }
    }

    private fun updateSessions(transform: (List<BillingSession>) -> List<BillingSession>) {
        val next = transform(_sessions.value)
        _sessions.value = next
        if (!suppressPersist) persistSessions(next)
    }

    private fun persistSessions(sessions: List<BillingSession>) {
        viewModelScope.launch {
            runCatching {
                sessions.forEach { billingSessionDao.upsert(it.toEntity()) }
            }.onFailure { Log.e("BillingViewModel", "Failed to persist billing sessions", it) }
        }
    }

    fun createNewSession() {
        val newSession = BillingSession(status = SessionStatus.ACTIVE)
        updateSessions { list ->
            val held = list.map { it.copy(status = SessionStatus.ON_HOLD) }
            held + newSession
        }
        _activeSessionId.value = newSession.sessionId
    }

    fun switchSession(sessionId: String) {
        updateSessions { list ->
            list.map {
                if (it.sessionId == sessionId) it.copy(status = SessionStatus.ACTIVE)
                else it.copy(status = SessionStatus.ON_HOLD)
            }
        }
        _activeSessionId.value = sessionId
    }

    fun closeSession(sessionId: String) {
        val session = _sessions.value.firstOrNull { it.sessionId == sessionId } ?: return

        viewModelScope.launch {
            runCatching { billingSessionDao.deleteById(sessionId) }
                .onFailure { Log.e("BillingViewModel", "Failed to delete billing session sessionId=$sessionId", it) }
        }

        updateSessions { list -> list.filterNot { it.sessionId == sessionId } }
        val remaining = _sessions.value
        if (remaining.isEmpty()) {
            createNewSession()
        } else {
            val nextActive = remaining.first().sessionId
            switchSession(nextActive)
        }
    }

    // Search
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    
    // Keep billingSearch as alias for backward compatibility
    val billingSearch: StateFlow<String> = _searchQuery.asStateFlow()

    private val _items = repository.allItems
    private val _itemsById: StateFlow<Map<Int, ItemEntity>> = _items
        .map { list -> list.associateBy { it.id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())
    val searchResults: StateFlow<List<ItemEntity>> = combine(_items, _searchQuery) { items, query ->
        Log.d("BillingViewModel", "Search called with query: '$query', items count: ${items.size}")
        val results = if (query.isEmpty()) {
            Log.d("BillingViewModel", "Query is empty, returning empty list")
            emptyList()
        } else {
            val filtered = items.filter { it.name.contains(query, ignoreCase = true) }
            Log.d("BillingViewModel", "Filtered results count: ${filtered.size}")
            filtered
        }
        results
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

    val totalAmount: StateFlow<Double> = billItems.map { list ->
        list.sumOf { lineTotal(it.item, it.qty) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)
    
    // Keep cartTotal as alias for backward compatibility
    val cartTotal: StateFlow<Double> = totalAmount

    fun addItemToCartByQrPayload(payload: String) {
        viewModelScope.launch {
            val raw = payload.trim()
            if (raw.isBlank()) return@launch

            val candidates = LinkedHashSet<String>().apply {
                add(raw)
                add(raw.replace(" ", ""))
                val digitsOnly = raw.filter(Char::isDigit)
                if (digitsOnly.isNotBlank()) add(digitsOnly)
            }

            val db = KiranaDatabase.getDatabase(getApplication())

            val found = candidates.firstNotNullOfOrNull { cand ->
                db.itemDao().getItemByBarcode(cand)
            }

            if (found != null) {
                val currentQty = billItems.value.firstOrNull { it.item.id == found.id }?.qty ?: 0.0
                val stockItem = _itemsById.value[found.id] ?: found
                val check = StockValidator.canAddToBill(stockItem, currentQtyInBill = currentQty, qtyToAdd = 1.0)
                if (!check.canAdd) {
                    val msg = check.message ?: "Out of Stock"
                    _scanResults.tryEmit(BillingScanResult.OutOfStock(msg, check.availableStock))
                    return@launch
                }
                addItemToBill(found, 1.0)
                _scanResults.tryEmit(BillingScanResult.Added(found))
            } else {
                Log.w("BillingViewModel", "QR payload not found in DB: '$raw'")
                _scanResults.tryEmit(BillingScanResult.NotFound(raw))
            }
        }
    }

    fun onSearchChange(query: String) {
        Log.d("BillingViewModel", "onSearchChange called with: '$query'")
        _searchQuery.value = query
    }
    
    fun searchItems(query: String) {
        Log.d("BillingViewModel", "searchItems called with: '$query'")
        _searchQuery.value = query
    }

    fun addToCart(item: ItemEntity) {
        addItemToBill(item, 1.0)
    }

    fun addItemToBill(item: ItemEntity, quantity: Double = 1.0) {
        val sessionId = _activeSessionId.value ?: run {
            createNewSession()
            _activeSessionId.value ?: return
        }

        val currentList = billItems.value.toMutableList()
        val existingIndex = currentList.indexOfFirst { it.item.id == item.id }
        val effectiveQty = if (item.isLoose && quantity == 1.0) 0.25 else quantity // default 0.25kg
        val currentQty = if (existingIndex != -1) currentList[existingIndex].qty else 0.0

        val stockItem = _itemsById.value[item.id] ?: item
        val check = StockValidator.canAddToBill(stockItem, currentQtyInBill = currentQty, qtyToAdd = effectiveQty)
        if (!check.canAdd) {
            check.message?.let { _stockValidationEvents.tryEmit(StockValidationEvent.Blocked(it, itemId = item.id)) }
            return
        }

        if (existingIndex != -1) {
            val existing = currentList[existingIndex]
            currentList[existingIndex] = existing.copy(qty = existing.qty + effectiveQty)
        } else {
            currentList.add(BoxCartItem(item, effectiveQty))
        }

        updateSessions { list ->
            list.map {
                if (it.sessionId == sessionId) it.copy(items = currentList)
                else it
            }
        }

        _searchQuery.value = "" // clear search
    }

    fun updateItemQuantity(itemId: Int, quantity: Double) {
        val sessionId = _activeSessionId.value ?: return
        val currentList = billItems.value.toMutableList()
        val index = currentList.indexOfFirst { it.item.id == itemId }
        if (index == -1) return

        if (quantity <= 0.0) {
            currentList.removeAt(index)
        } else {
            val existing = currentList[index]
            val stockItem = _itemsById.value[itemId] ?: existing.item
            val check = StockValidator.canAddToBill(stockItem, currentQtyInBill = 0.0, qtyToAdd = quantity)
            if (!check.canAdd) {
                check.message?.let { _stockValidationEvents.tryEmit(StockValidationEvent.Blocked(it, itemId = itemId)) }
                return
            }
            currentList[index] = existing.copy(qty = quantity)
        }

        updateSessions { list ->
            list.map {
                if (it.sessionId == sessionId) it.copy(items = currentList)
                else it
            }
        }
    }

    fun removeItemFromBill(itemId: Int) {
        val sessionId = _activeSessionId.value ?: return
        val currentList = billItems.value.toMutableList()
        currentList.removeAll { it.item.id == itemId }
        updateSessions { list ->
            list.map {
                if (it.sessionId == sessionId) it.copy(items = currentList)
                else it
            }
        }
    }
    /**
     * Update the unit selling price for a cart line.
     * - If [persist] is true, also updates the inventory selling price (ItemEntity.price) in Room.
     * - If false, only applies to this bill (discount/override).
     */
    fun updateCartItemUnitPrice(itemId: Int, newUnitPrice: Double, persist: Boolean) {
        if (newUnitPrice <= 0.0) return
        val sessionId = _activeSessionId.value ?: return
        val currentList = billItems.value.toMutableList()
        val index = currentList.indexOfFirst { it.item.id == itemId }
        if (index == -1) return

        val existing = currentList[index]
        val updatedItem = if (existing.item.isLoose) {
            existing.item.copy(pricePerKg = newUnitPrice, price = newUnitPrice)
        } else {
            existing.item.copy(price = newUnitPrice)
        }
        currentList[index] = existing.copy(item = updatedItem)
        updateSessions { list ->
            list.map {
                if (it.sessionId == sessionId) it.copy(items = currentList)
                else it
            }
        }

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
            val sessionId = _activeSessionId.value
            val cartSnapshot = billItems.value
            val items = cartSnapshot.map { it.item to it.qty }
            val total = cartSnapshot.sumOf { lineTotal(it.item, it.qty) }
            val validation = StockValidator.validateCheckout(items, _itemsById.value)
            if (!validation.ok) {
                _stockValidationEvents.tryEmit(
                    StockValidationEvent.CheckoutBlocked(
                        message = validation.message ?: "Stock unavailable. Please review bill items.",
                        offendingItemIds = validation.offendingItemIds
                    )
                )
                return@launch
            }

            val result = repository.processSale(items, paymentMode, customerId, total)
            val txId = when (result) {
                is KiranaRepository.SaleResult.Success -> result.txId
                is KiranaRepository.SaleResult.StockConflict -> {
                    _stockValidationEvents.tryEmit(
                        StockValidationEvent.CheckoutBlocked(
                            message = "Stock unavailable. Please review bill items.",
                            offendingItemIds = result.offendingItemIds
                        )
                    )
                    return@launch
                }
            }

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

            if (sessionId != null) {
                runCatching { billingSessionDao.deleteById(sessionId) }
                    .onFailure { Log.e("BillingViewModel", "Failed to delete checked-out billing session sessionId=$sessionId", it) }
            }

            suppressPersist = true
            try {
                _sessions.value = _sessions.value.filterNot { it.sessionId == sessionId }
            } finally {
                suppressPersist = false
            }

            if (_sessions.value.isEmpty()) {
                createNewSession()
            } else {
                switchSession(_sessions.value.first().sessionId)
            }

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
                val currentQty = billItems.value.firstOrNull { it.item.id == found.id }?.qty ?: 0.0
                val stockItem = _itemsById.value[found.id] ?: found
                val check = StockValidator.canAddToBill(stockItem, currentQtyInBill = currentQty, qtyToAdd = 1.0)
                if (!check.canAdd) {
                    val msg = check.message ?: "Out of Stock"
                    _scanResults.tryEmit(BillingScanResult.OutOfStock(msg, check.availableStock))
                    return@launch
                }
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

    private val _stockValidationEvents = MutableSharedFlow<StockValidationEvent>(extraBufferCapacity = 16)
    val stockValidationEvents: SharedFlow<StockValidationEvent> = _stockValidationEvents.asSharedFlow()

    fun updateQty(itemId: Int, delta: Double) {
        val sessionId = _activeSessionId.value ?: return
        val currentList = billItems.value.toMutableList()
        val index = currentList.indexOfFirst { it.item.id == itemId }
        if (index != -1) {
            val existing = currentList[index]
            val newQty = existing.qty + delta
            if (newQty <= 0.0) {
                currentList.removeAt(index)
            } else {
                val stockItem = _itemsById.value[itemId] ?: existing.item
                val check = StockValidator.canAddToBill(stockItem, currentQtyInBill = 0.0, qtyToAdd = newQty)
                if (!check.canAdd) {
                    check.message?.let { _stockValidationEvents.tryEmit(StockValidationEvent.Blocked(it, itemId = itemId)) }
                    return
                }
                currentList[index] = existing.copy(qty = newQty)
            }
            updateSessions { list ->
                list.map {
                    if (it.sessionId == sessionId) it.copy(items = currentList)
                    else it
                }
            }
        }
    }
    
    fun removeFromCart(itemId: Int) {
        removeItemFromBill(itemId)
    }

    fun processCheckout(paymentMode: String) {
        completeBill(paymentMode, null)
    }
}
