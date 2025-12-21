package com.kiranaflow.app.ui.screens.parties

import android.app.Application
import android.content.Intent
import android.provider.ContactsContract
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PermContactCalendar
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kiranaflow.app.data.local.KiranaDatabase
import com.kiranaflow.app.data.local.ItemEntity
import com.kiranaflow.app.data.local.PartyEntity
import com.kiranaflow.app.data.local.TransactionEntity
import com.kiranaflow.app.data.local.TransactionItemEntity
import com.kiranaflow.app.data.local.ShopSettings
import com.kiranaflow.app.data.local.ShopSettingsStore
import com.kiranaflow.app.data.repository.KiranaRepository
import com.kiranaflow.app.ui.components.*
import androidx.compose.material.icons.outlined.Settings
import com.kiranaflow.app.ui.theme.*
import com.kiranaflow.app.ui.components.dialogs.CustomerDetailSheet
import com.kiranaflow.app.ui.components.dialogs.VendorDetailSheet
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import com.kiranaflow.app.util.InputFilters
import com.kiranaflow.app.util.WhatsAppHelper
import java.text.SimpleDateFormat
import java.util.Date
import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.core.content.ContextCompat
import androidx.compose.ui.window.Dialog
import com.kiranaflow.app.util.Formatters

class PartiesViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = KiranaRepository(KiranaDatabase.getDatabase(application))

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    
    val customers: StateFlow<List<PartyEntity>> = repository.customers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        
    val vendors: StateFlow<List<PartyEntity>> = repository.vendors
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredCustomers: StateFlow<List<PartyEntity>> = combine(customers, searchQuery) { list, q ->
        val query = q.trim()
        if (query.isBlank()) list
        else list.filter { it.name.contains(query, true) || it.phone.contains(query) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredVendors: StateFlow<List<PartyEntity>> = combine(vendors, searchQuery) { list, q ->
        val query = q.trim()
        if (query.isBlank()) list
        else list.filter { it.name.contains(query, true) || it.phone.contains(query) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allItems: StateFlow<List<ItemEntity>> = repository.allItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val lowStockItems: StateFlow<List<ItemEntity>> = allItems
        .map { items ->
            fun stockFor(item: ItemEntity): Double = if (item.isLoose) item.stockKg else item.stock.toDouble()
            items
                .filter { stockFor(it) < it.reorderPoint.toDouble() }
                .sortedBy { stockFor(it) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val allTransactions: StateFlow<List<TransactionEntity>> = repository.allTransactions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val allTxItems: StateFlow<List<TransactionItemEntity>> = repository.allTransactionItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // transactionId -> line items
    val transactionItemsByTransactionId: StateFlow<Map<Int, List<TransactionItemEntity>>> = allTxItems
        .map { items -> items.groupBy { it.transactionId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // customerId -> recent transactions (sales + payments that reference customerId)
    val customerTransactionsById: StateFlow<Map<Int, List<TransactionEntity>>> = allTransactions
        .map { txs ->
            txs.filter { it.customerId != null }
                .groupBy { it.customerId!! }
                .mapValues { (_, list) -> list.sortedByDescending { it.date }.take(50) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // vendorId -> recent transactions (payments + sales/others that reference vendorId)
    val vendorTransactionsById: StateFlow<Map<Int, List<TransactionEntity>>> = allTransactions
        .map { txs ->
            txs.filter { it.vendorId != null }
                .groupBy { it.vendorId!! }
                .mapValues { (_, list) -> list.sortedByDescending { it.date }.take(10) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // vendorId -> latest transaction
    val lastVendorTransactionById: StateFlow<Map<Int, TransactionEntity>> = vendorTransactionsById
        .map { m -> m.mapNotNull { (k, v) -> v.firstOrNull()?.let { k to it } }.toMap() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun addParty(
        name: String,
        phone: String,
        type: String,
        gstNumber: String? = null,
        openingDue: Double = 0.0
    ) {
        viewModelScope.launch {
            // ID is auto-generated (Int)
            val due = if (type == "CUSTOMER") openingDue.coerceAtLeast(0.0) else 0.0
            repository.addParty(
                PartyEntity(
                    name = name,
                    phone = phone,
                    type = type,
                    gstNumber = gstNumber?.trim()?.ifBlank { null },
                    balance = due,
                    openingDue = due
                )
            )
        }
    }

    fun setSearchQuery(q: String) {
        _searchQuery.value = q
    }

    fun updateParty(party: PartyEntity, name: String, phone: String, gstNumber: String? = null) {
        viewModelScope.launch {
            repository.updateParty(
                party.copy(
                    name = name.trim(),
                    phone = phone.trim(),
                    gstNumber = gstNumber?.trim()?.ifBlank { null } ?: party.gstNumber
                )
            )
        }
    }

    fun deleteParty(party: PartyEntity) {
        viewModelScope.launch {
            repository.deleteParty(party)
        }
    }

    fun deletePartiesByIds(ids: Set<Int>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repository.deletePartiesByIds(ids.toList())
        }
    }

    suspend fun importCustomersFromContacts(customers: List<Pair<String, String>>): Pair<Int, Int> {
        return repository.addCustomersBulk(customers)
    }

    fun recordCustomerPayment(customer: PartyEntity, amount: Double, mode: String) {
        viewModelScope.launch {
            repository.recordPayment(customer, amount, mode)
        }
    }

    fun recordVendorPayment(vendor: PartyEntity, amount: Double, mode: String) {
        viewModelScope.launch {
            repository.recordPayment(vendor, amount, mode)
        }
    }

    fun recordVendorPurchaseDue(vendor: PartyEntity, amount: Double, mode: String, note: String?) {
        viewModelScope.launch {
            repository.recordVendorPurchase(vendor, amount, mode, note)
        }
    }
}

private data class PhoneContact(val name: String, val phone: String)

private fun normalizePhoneDigits(phone: String): String {
    val digits = phone.filter { it.isDigit() }
    if (digits.isBlank()) return ""
    return if (digits.length > 10) digits.takeLast(10) else digits
}

private suspend fun loadPhoneContacts(context: android.content.Context): List<PhoneContact> = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    val result = mutableListOf<PhoneContact>()
    val projection = arrayOf(
        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
        ContactsContract.CommonDataKinds.Phone.NUMBER
    )
    resolver.query(
        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
        projection,
        null,
        null,
        "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
    )?.use { cursor ->
        val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
        val numberIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
        while (cursor.moveToNext()) {
            val name = cursor.getString(nameIdx)?.trim().orEmpty()
            val phone = cursor.getString(numberIdx)?.trim().orEmpty()
            if (name.isNotBlank() && phone.isNotBlank()) result.add(PhoneContact(name, phone))
        }
    }
    result
        .asSequence()
        .map { PhoneContact(it.name, normalizePhoneDigits(it.phone)) }
        .filter { it.phone.isNotBlank() }
        .distinctBy { it.phone }
        .toList()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PartiesScreen(
    type: String = "CUSTOMER", // "CUSTOMER" or "VENDOR"
    triggerAdd: Boolean = false,
    onTriggerConsumed: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenPayables: () -> Unit = {},
    onOpenReorder: () -> Unit = {},
    onOpenVendorDetail: (Int) -> Unit = {},
    viewModel: PartiesViewModel = viewModel()
) {
    val context = LocalContext.current
    val shopSettingsStore = remember(context) { ShopSettingsStore(context) }
    val shopSettings by shopSettingsStore.settings.collectAsState(initial = ShopSettings("", "", "", ""))

    val list by if(type == "CUSTOMER") viewModel.filteredCustomers.collectAsState() else viewModel.filteredVendors.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val customers by viewModel.customers.collectAsState()
    val lowStockItems by viewModel.lowStockItems.collectAsState()
    val lastVendorTxById by viewModel.lastVendorTransactionById.collectAsState()
    val vendorTxById by viewModel.vendorTransactionsById.collectAsState()
    val customerTxById by viewModel.customerTransactionsById.collectAsState()
    val txItemsByTxId by viewModel.transactionItemsByTransactionId.collectAsState()
    var showAddModal by remember { mutableStateOf(false) }
    var editingParty by remember { mutableStateOf<PartyEntity?>(null) }
    var deletingParty by remember { mutableStateOf<PartyEntity?>(null) }
    var payingParty by remember { mutableStateOf<PartyEntity?>(null) }
    var showPayablesDialog by remember { mutableStateOf(false) }
    var showReorderDialog by remember { mutableStateOf(false) }
    var historyParty by remember { mutableStateOf<PartyEntity?>(null) }
    // Store IDs (not entity snapshots) so balance/fields update live while sheets are open.
    var customerDetailPartyId by remember { mutableStateOf<Int?>(null) }
    var vendorDetailPartyId by remember { mutableStateOf<Int?>(null) }
    var recordDueParty by remember { mutableStateOf<PartyEntity?>(null) }

    // Contacts import state (customers only)
    var showContactsImport by remember { mutableStateOf(false) }
    var contactsLoading by remember { mutableStateOf(false) }
    var contacts by remember { mutableStateOf<List<PhoneContact>>(emptyList()) }
    var contactsQuery by remember { mutableStateOf("") }
    var selectedPhones by remember { mutableStateOf<Set<String>>(emptySet()) }
    var importResult by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    var hasContactsPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        )
    }
    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted: Boolean -> hasContactsPermission = granted }
    )

    // Bulk select/delete (C3)
    var selectionMode by remember { mutableStateOf(false) }
    var selectedPartyIds by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var showBulkDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(triggerAdd) {
        if (triggerAdd) {
            showAddModal = true
            onTriggerConsumed()
        }
    }

    var newName by remember { mutableStateOf("") }
    var newPhone by remember { mutableStateOf("") }
    var newGst by remember { mutableStateOf("") }
    var newOpeningDue by remember { mutableStateOf("") }

    Box(modifier = Modifier.fillMaxSize().background(GrayBg)) {
        Column(modifier = Modifier.fillMaxSize()) {
            val title = if (type == "CUSTOMER") "Customer Khata" else "Expenses"
            val subtitle = if (type == "CUSTOMER") "Manage udhaar & payments" else "Track purchases & expenses"
            ValleyTopBar(
                title = title,
                subtitle = subtitle,
                actionIcon = Icons.Default.Add,
                onAction = { showAddModal = true },
                onSettings = onOpenSettings,
                actionColor = if (type == "CUSTOMER") Purple600 else InteractiveCyan,
                actionIconTint = White
            )
            Spacer(modifier = Modifier.height(18.dp))

            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                // Stats Card (Simplified for Customers)
                if (type == "CUSTOMER") {
                   val totalDue = list.filter { it.balance > 0 }.sumOf { it.balance }
                   Card(
                       modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                       colors = CardDefaults.cardColors(containerColor = KiranaGreen),
                       shape = RoundedCornerShape(24.dp)
                   ) {
                       Column(modifier = Modifier.padding(24.dp)) {
                           Text("TOTAL RECEIVABLES", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = KiranaGreenLight, letterSpacing = 1.sp)
                           val rawDue = Formatters.formatInrCurrency(totalDue, fractionDigits = 0, useAbsolute = true)
                           Text(
                               rawDue,
                               fontSize = 36.sp,
                               fontWeight = FontWeight.Black,
                               color = White
                           )
                           Text("Money pending from market", fontSize = 12.sp, color = KiranaGreenLight)
                       }
                   }

                    KiranaInput(
                        value = searchQuery,
                        onValueChange = viewModel::setSearchQuery,
                        placeholder = "Search customers...",
                        icon = Icons.Default.Search,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (!hasContactsPermission) {
                                    contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                                } else {
                                    showContactsImport = true
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = BgPrimary, contentColor = TextPrimary)
                        ) {
                            Icon(Icons.Default.PermContactCalendar, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Import from Contacts", fontWeight = FontWeight.Bold)
                        }
                    }

                    // Bulk selection controls
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (selectionMode) {
                            Text("${selectedPartyIds.size} selected", fontWeight = FontWeight.Bold, color = TextPrimary)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(
                                    onClick = { showBulkDeleteConfirm = true },
                                    enabled = selectedPartyIds.isNotEmpty()
                                ) { Text("Delete", color = LossRed, fontWeight = FontWeight.Bold) }
                                TextButton(
                                    onClick = {
                                        selectionMode = false
                                        selectedPartyIds = emptySet()
                                    }
                                ) { Text("Cancel", fontWeight = FontWeight.Bold) }
                            }
                        } else {
                            TextButton(onClick = { selectionMode = true }) {
                                Icon(Icons.Default.Checklist, contentDescription = null, tint = TextSecondary)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Select", fontWeight = FontWeight.Bold, color = TextSecondary)
                            }
                        }
                    }
                } else {
                    val totalPayables = list.filter { it.balance < 0 }.sumOf { kotlin.math.abs(it.balance) }
                    val itemsToReorder = lowStockItems.size

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 16.dp)) {
                        Card(
                            modifier = Modifier.weight(1f).clickable { onOpenPayables() },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = BgPrimary)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("TOTAL PAYABLES", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                                Spacer(modifier = Modifier.height(6.dp))
                                val rawPayables = Formatters.formatInrCurrency(totalPayables, fractionDigits = 0, useAbsolute = true)
                                Text(
                                    rawPayables,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Black,
                                    color = LossRed
                                )
                            }
                        }
                        Card(
                            modifier = Modifier.weight(1f).clickable { onOpenReorder() },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = BgPrimary)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("ITEMS TO REORDER", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(itemsToReorder.toString(), fontSize = 22.sp, fontWeight = FontWeight.Black, color = TextPrimary)
                            }
                        }
                    }

                    KiranaInput(
                        value = searchQuery,
                        onValueChange = viewModel::setSearchQuery,
                        placeholder = "Search vendors...",
                        icon = Icons.Default.Search,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    // Bulk selection controls
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (selectionMode) {
                            Text("${selectedPartyIds.size} selected", fontWeight = FontWeight.Bold, color = TextPrimary)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(
                                    onClick = { showBulkDeleteConfirm = true },
                                    enabled = selectedPartyIds.isNotEmpty()
                                ) { Text("Delete", color = LossRed, fontWeight = FontWeight.Bold) }
                                TextButton(
                                    onClick = {
                                        selectionMode = false
                                        selectedPartyIds = emptySet()
                                    }
                                ) { Text("Cancel", fontWeight = FontWeight.Bold) }
                            }
                        } else {
                            TextButton(onClick = { selectionMode = true }) {
                                Icon(Icons.Default.Checklist, contentDescription = null, tint = TextSecondary)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Select", fontWeight = FontWeight.Bold, color = TextSecondary)
                            }
                        }
                    }
                }

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 100.dp)
                ) {
                    items(list, key = { it.id }) { party ->
                        val isSelected = selectedPartyIds.contains(party.id)
                        val onToggle = {
                            selectedPartyIds = if (isSelected) selectedPartyIds - party.id else selectedPartyIds + party.id
                        }
                        if (type == "CUSTOMER") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (selectionMode) {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { onToggle() },
                                        modifier = Modifier.padding(start = 8.dp, end = 8.dp)
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .combinedClickable(
                                            onClick = {
                                                if (selectionMode) onToggle()
                                                else customerDetailPartyId = party.id
                                            },
                                            onLongClick = {
                                                if (!selectionMode) selectionMode = true
                                                selectedPartyIds = selectedPartyIds + party.id
                                            }
                                        )
                                ) {
                                    CustomerCard(
                                        customer = party,
                                        onRemind = { c ->
                                            // Only remind for due customers.
                                            if (c.balance <= 0) return@CustomerCard
                                            val phone = WhatsAppHelper.normalizeIndianPhone(c.phone)
                                            val amount = kotlin.math.abs(c.balance).toInt()
                                            val msg = WhatsAppHelper.buildReminderMessage(
                                                template = shopSettings.whatsappReminderMessage,
                                                customerName = c.name,
                                                dueAmountInr = amount,
                                                shopName = shopSettings.shopName,
                                                upiId = shopSettings.upiId
                                            )
                                            WhatsAppHelper.openWhatsApp(context, phone, msg)
                                        },
                                        onRecordPayment = { if (!selectionMode) payingParty = party },
                                        onEdit = { if (!selectionMode) editingParty = party },
                                        onDelete = { if (!selectionMode) deletingParty = party }
                                    )
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (selectionMode) {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { onToggle() },
                                        modifier = Modifier.padding(start = 8.dp, end = 8.dp)
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .combinedClickable(
                                            onClick = {
                                                if (selectionMode) onToggle()
                                                else vendorDetailPartyId = party.id
                                            },
                                            onLongClick = {
                                                if (!selectionMode) selectionMode = true
                                                selectedPartyIds = selectedPartyIds + party.id
                                            }
                                        )
                                ) {
                                    VendorCard(
                                        vendor = party,
                                        onPayNow = { if (!selectionMode) payingParty = party },
                                        onAddDue = { if (!selectionMode) recordDueParty = party },
                                        onEdit = { if (!selectionMode) editingParty = party },
                                        onDelete = { if (!selectionMode) deletingParty = party }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Customer detail: use the same bottom-sheet interaction as Vendor (swipe-down to dismiss).
    customerDetailPartyId?.let { id ->
        if (type == "CUSTOMER") {
            val c = customers.firstOrNull { it.id == id } ?: return@let
            CustomerDetailSheet(
                customer = c,
                transactions = customerTxById[c.id].orEmpty(),
                onDismiss = { customerDetailPartyId = null },
                onSavePayment = { amount, method ->
                    viewModel.recordCustomerPayment(c, amount, method)
                }
            )
        }
    }

    // Vendor detail sheet (transaction history + payment) - always resolve latest vendor from flow.
    vendorDetailPartyId?.let { id ->
        if (type == "VENDOR") {
            val v = list.firstOrNull { it.id == id } ?: return@let
            VendorDetailSheet(
                vendor = v,
                transactions = vendorTxById[v.id].orEmpty(),
                onDismiss = { vendorDetailPartyId = null },
                onSavePayment = { amount, method ->
                    viewModel.recordVendorPayment(v, amount, method)
                }
            )
        }
    }

    if (showContactsImport && type == "CUSTOMER") {
        LaunchedEffect(Unit) {
            contactsLoading = true
            contacts = runCatching { loadPhoneContacts(context) }.getOrDefault(emptyList())
            selectedPhones = emptySet()
            contactsQuery = ""
            contactsLoading = false
        }

        val existingPhones = remember(customers) {
            customers.map { normalizePhoneDigits(it.phone) }.filter { it.isNotBlank() }.toSet()
        }
        val filteredContacts = remember(contacts, contactsQuery) {
            val q = contactsQuery.trim()
            if (q.isBlank()) contacts
            else contacts.filter { it.name.contains(q, true) || it.phone.contains(q) }
        }

        Dialog(onDismissRequest = { showContactsImport = false }) {
            Surface(
                modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp),
                shape = RoundedCornerShape(18.dp),
                color = BgPrimary
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("Import Customers", fontWeight = FontWeight.Black, fontSize = 18.sp, modifier = Modifier.weight(1f))
                        IconButton(onClick = { showContactsImport = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = contactsQuery,
                        onValueChange = { contactsQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        placeholder = { Text("Search contacts...") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = White,
                            unfocusedContainerColor = White,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    if (contactsLoading) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        val scroll = rememberScrollState()
                        Column(
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .verticalScroll(scroll)
                        ) {
                            filteredContacts.forEach { c ->
                                val isDuplicate = existingPhones.contains(c.phone)
                                val isChecked = selectedPhones.contains(c.phone)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Gray50)
                                        .clickable(enabled = !isDuplicate) {
                                            selectedPhones =
                                                if (isChecked) selectedPhones - c.phone else selectedPhones + c.phone
                                        }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = isChecked,
                                        onCheckedChange = {
                                            if (!isDuplicate) {
                                                selectedPhones =
                                                    if (isChecked) selectedPhones - c.phone else selectedPhones + c.phone
                                            }
                                        },
                                        enabled = !isDuplicate
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(c.name, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(c.phone, color = TextSecondary, fontSize = 12.sp)
                                    }
                                    if (isDuplicate) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(999.dp))
                                                .background(Gray100)
                                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Text("Exists", color = TextSecondary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { selectedPhones = emptySet() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            border = null,
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = Gray100, contentColor = TextPrimary)
                        ) { Text("Clear", fontWeight = FontWeight.Bold) }
                        Button(
                            onClick = {
                                val toImport = contacts.filter { selectedPhones.contains(it.phone) }
                                    .map { it.name to it.phone }
                                viewModel.viewModelScope.launch {
                                    importResult = viewModel.importCustomersFromContacts(toImport)
                                    showContactsImport = false
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = selectedPhones.isNotEmpty(),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Purple600, contentColor = White)
                        ) {
                            Text("Import (${selectedPhones.size})", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    if (importResult != null && type == "CUSTOMER") {
        val (added, skipped) = importResult!!
        AlertDialog(
            onDismissRequest = { importResult = null },
            title = { Text("Import complete", fontWeight = FontWeight.Bold) },
            text = { Text("Added: $added\nSkipped: $skipped") },
            confirmButton = {
                TextButton(onClick = { importResult = null }) { Text("OK") }
            }
        )
    }

    if (showBulkDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showBulkDeleteConfirm = false },
            title = { Text("Delete selected?", fontWeight = FontWeight.Bold) },
            text = { Text("This will permanently delete ${selectedPartyIds.size} ${if (type == "CUSTOMER") "customer(s)" else "vendor(s)"} .") },
            confirmButton = {
                TextButton(onClick = {
                    showBulkDeleteConfirm = false
                    viewModel.deletePartiesByIds(selectedPartyIds)
                    selectionMode = false
                    selectedPartyIds = emptySet()
                }) { Text("Delete", color = LossRed) }
            },
            dismissButton = {
                TextButton(onClick = { showBulkDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }

    if (showAddModal) {
        var addPhoneError by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showAddModal = false },
            title = { Text(if (type == "CUSTOMER") "Add Customer" else "Add Vendor", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding()
                        .heightIn(max = 520.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newPhone,
                        onValueChange = { newPhone = InputFilters.digitsOnly(it, maxLen = 10) },
                        label = { Text("Phone") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Phone)
                    )
                    if (addPhoneError) {
                        Text(
                            text = "Mobile number must be 10 digits",
                            color = LossRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (type != "CUSTOMER") {
                        OutlinedTextField(
                            value = newGst,
                            onValueChange = { newGst = it },
                            label = { Text("GST Number (optional)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        OutlinedTextField(
                            value = newOpeningDue,
                            onValueChange = { newOpeningDue = InputFilters.decimal(it) },
                            label = { Text("Opening Due (optional)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newPhone.length != 10) {
                            addPhoneError = true
                            return@TextButton
                        }
                        addPhoneError = false
                        val openingDue = if (type == "CUSTOMER") (newOpeningDue.toDoubleOrNull() ?: 0.0) else 0.0
                        viewModel.addParty(
                            name = newName,
                            phone = newPhone,
                            type = type,
                            gstNumber = if (type == "CUSTOMER") null else newGst,
                            openingDue = openingDue
                        )
                        newName = ""
                        newPhone = ""
                        newGst = ""
                        newOpeningDue = ""
                        showAddModal = false
                    }
                ) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showAddModal = false }) { Text("Cancel") }
            }
        )
    }

    if (editingParty != null) {
        val party = editingParty!!
        var name by remember(party.id) { mutableStateOf(party.name) }
        var phone by remember(party.id) { mutableStateOf(party.phone) }
        var gst by remember(party.id) { mutableStateOf(party.gstNumber.orEmpty()) }
        var editPhoneError by remember(party.id) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { editingParty = null },
            title = { Text(if (type == "CUSTOMER") "Edit Customer" else "Edit Vendor", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding()
                        .heightIn(max = 520.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = InputFilters.digitsOnly(it, maxLen = 10) },
                        label = { Text("Phone") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Phone)
                    )
                    if (editPhoneError) {
                        Text(
                            text = "Mobile number must be 10 digits",
                            color = LossRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (type != "CUSTOMER") {
                        OutlinedTextField(value = gst, onValueChange = { gst = it }, label = { Text("GST Number (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (phone.length != 10) {
                        editPhoneError = true
                        return@TextButton
                    }
                    editPhoneError = false
                    viewModel.updateParty(party, name, phone, if (type == "CUSTOMER") null else gst)
                    editingParty = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editingParty = null }) { Text("Cancel") } }
        )
    }

    if (deletingParty != null) {
        val party = deletingParty!!
        AlertDialog(
            onDismissRequest = { deletingParty = null },
            title = { Text("Delete Customer?", fontWeight = FontWeight.Bold) },
            text = { Text("This will remove ${party.name} from your customers list.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteParty(party)
                    deletingParty = null
                }) { Text("Delete", color = LossRed) }
            },
            dismissButton = { TextButton(onClick = { deletingParty = null }) { Text("Cancel") } }
        )
    }

    if (payingParty != null) {
        val customer = payingParty!!
        var amountText by remember(customer.id) { mutableStateOf("") }
        var mode by remember(customer.id) { mutableStateOf("CASH") }
        AlertDialog(
            onDismissRequest = { payingParty = null },
            title = { Text(if (type == "CUSTOMER") "Record Payment" else "Pay Vendor", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding()
                        .heightIn(max = 560.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("${if (type == "CUSTOMER") "Customer" else "Vendor"}: ${customer.name}", color = TextSecondary, fontSize = 12.sp)
                    Text(
                        "${if (type == "CUSTOMER") "Due" else "You owe"}: ${Formatters.formatInrCurrency(customer.balance, fractionDigits = 0, useAbsolute = true)}",
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = InputFilters.decimal(it) },
                        label = { Text("Amount") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilterChip(
                            selected = mode == "CASH",
                            onClick = { mode = "CASH" },
                            label = { Text("Cash") }
                        )
                        FilterChip(
                            selected = mode == "UPI",
                            onClick = { mode = "UPI" },
                            label = { Text("UPI") }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val amt = amountText.toDoubleOrNull() ?: 0.0
                    if (amt > 0) {
                        if (type == "CUSTOMER") viewModel.recordCustomerPayment(customer, amt, mode)
                        else viewModel.recordVendorPayment(customer, amt, mode)
                    }
                    payingParty = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { payingParty = null }) { Text("Cancel") } }
        )
    }

    if (showPayablesDialog) {
        AlertDialog(
            onDismissRequest = { showPayablesDialog = false },
            title = { Text("Total Payables", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val payables = list.filter { it.balance < 0 }
                    if (payables.isEmpty()) {
                        Text("No payables 🎉", color = TextSecondary)
                    } else {
                        payables.forEach { v ->
                            val lastTx = lastVendorTxById[v.id]
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(v.name, fontWeight = FontWeight.Bold, color = TextPrimary)
                                    Text(v.phone, fontSize = 12.sp, color = TextSecondary)
                                    if (lastTx != null) {
                                        Text(
                                            "Last: ${lastTx.paymentMode} • ${lastTx.time}",
                                            fontSize = 11.sp,
                                            color = TextSecondary
                                        )
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        Formatters.formatInrCurrency(v.balance, fractionDigits = 0, useAbsolute = true),
                                        fontWeight = FontWeight.Bold,
                                        color = LossRed
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    TextButton(onClick = { payingParty = v; showPayablesDialog = false }) { Text("Pay") }
                                    TextButton(onClick = { historyParty = v; showPayablesDialog = false }) { Text("History") }
                                    TextButton(onClick = { recordDueParty = v; showPayablesDialog = false }) { Text("Add Due") }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showPayablesDialog = false }) { Text("Close") } }
        )
    }

    if (historyParty != null) {
        val v = historyParty!!
        val txns = vendorTxById[v.id].orEmpty()
        AlertDialog(
            onDismissRequest = { historyParty = null },
            title = { Text("${v.name} • History", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Payable: ${Formatters.formatInrCurrency(v.balance, fractionDigits = 0, useAbsolute = true)}",
                        fontWeight = FontWeight.Bold,
                        color = LossRed
                    )
                    if (txns.isEmpty()) {
                        Text("No transactions recorded yet.", color = TextSecondary)
                    } else {
                        txns.take(8).forEach { t ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(t.title, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                    Text("${t.paymentMode} • ${t.time}", fontSize = 12.sp, color = TextSecondary)
                                }
                                Text(
                                    Formatters.formatInrCurrency(t.amount, fractionDigits = 0, useAbsolute = true),
                                    fontWeight = FontWeight.Bold,
                                    color = if (t.type == "EXPENSE") LossRed else TextPrimary
                                )
                            }
                        }
                        if (txns.size > 8) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Showing 8 of ${txns.size}", fontSize = 12.sp, color = TextSecondary)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { payingParty = v; historyParty = null }) { Text("Pay") }
            },
            dismissButton = {
                TextButton(onClick = { historyParty = null }) { Text("Close") }
            }
        )
    }

    if (showReorderDialog) {
        AlertDialog(
            onDismissRequest = { showReorderDialog = false },
            title = { Text("Items to Reorder", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (lowStockItems.isEmpty()) {
                        Text("No items to reorder.", color = TextSecondary)
                    } else {
                        lowStockItems.take(12).forEach { it ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(it.name, fontWeight = FontWeight.Bold, color = TextPrimary)
                                    Text("Stock ${it.stock} • Reorder ${it.reorderPoint}", fontSize = 12.sp, color = TextSecondary)
                                }
                                if (it.vendorId != null) {
                                    val vName = list.firstOrNull { v -> v.id == it.vendorId }?.name
                                    if (!vName.isNullOrBlank()) {
                                        Text(vName, fontSize = 12.sp, color = TextSecondary)
                                    }
                                }
                            }
                        }
                        if (lowStockItems.size > 12) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Showing 12 of ${lowStockItems.size}", fontSize = 12.sp, color = TextSecondary)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showReorderDialog = false }) { Text("Close") } }
        )
    }

    if (recordDueParty != null) {
        val v = recordDueParty!!
        var amountText by remember(v.id) { mutableStateOf("") }
        var mode by remember(v.id) { mutableStateOf("CREDIT") } // Udhaar by default
        var note by remember(v.id) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { recordDueParty = null },
            title = { Text("Record Purchase / Due", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Vendor: ${v.name}", color = TextSecondary, fontSize = 12.sp)
                    Text(
                        "Current payable: ${Formatters.formatInrCurrency(v.balance, fractionDigits = 0, useAbsolute = true)}",
                        fontWeight = FontWeight.Bold,
                        color = LossRed
                    )
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = InputFilters.decimal(it) },
                        label = { Text("Amount") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("Note (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilterChip(
                            selected = mode == "CREDIT",
                            onClick = { mode = "CREDIT" },
                            label = { Text("Udhaar") }
                        )
                        FilterChip(
                            selected = mode == "CASH",
                            onClick = { mode = "CASH" },
                            label = { Text("Cash") }
                        )
                        FilterChip(
                            selected = mode == "UPI",
                            onClick = { mode = "UPI" },
                            label = { Text("UPI") }
                        )
                    }
                    Text(
                        if (mode == "CREDIT") "This will increase payables." else "This will not change payables (paid immediately).",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val amt = amountText.toDoubleOrNull() ?: 0.0
                    if (amt > 0) {
                        viewModel.recordVendorPurchaseDue(v, amt, mode, note)
                    }
                    recordDueParty = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { recordDueParty = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun CustomerCard(
    customer: PartyEntity,
    onRemind: (PartyEntity) -> Unit,
    onRecordPayment: (PartyEntity) -> Unit,
    onEdit: (PartyEntity) -> Unit,
    onDelete: (PartyEntity) -> Unit
) {
    val due = customer.balance > 0
    KiranaCard(borderColor = Color.Transparent) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (due) LossRedBg else KiranaGreenBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            customer.name.take(1),
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                            color = if (due) LossRed else KiranaGreen
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(customer.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Phone, null, modifier = Modifier.size(12.dp), tint = TextSecondary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(customer.phone, fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    val raw = Formatters.formatInrCurrency(customer.balance, fractionDigits = 0, useAbsolute = true)
                    Text(
                        text = if (customer.balance == 0.0) "Settled" else raw,
                        fontWeight = FontWeight.Black,
                        fontSize = 16.sp,
                        color = if (due) LossRed else TextSecondary
                    )
                    if (customer.balance != 0.0) {
                        Text(
                            if (due) "DUE" else "ADVANCE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (due) LossRed else KiranaGreen
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (due) {
                    OutlinedButton(
                        onClick = { onRemind(customer) },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = Gray100, contentColor = TextPrimary),
                        border = null
                    ) {
                        Text("Remind", fontWeight = FontWeight.Bold)
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { onRecordPayment(customer) },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = KiranaGreenBg, contentColor = KiranaGreen),
                        border = null,
                        enabled = customer.balance != 0.0
                    ) {
                        Text("Record", fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = { onEdit(customer) }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = TextPrimary)
                    }
                    IconButton(onClick = { onDelete(customer) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = LossRed)
                    }
                }
            }
        }
    }
}

@Composable
private fun VendorCard(
    vendor: PartyEntity,
    onPayNow: (PartyEntity) -> Unit,
    onAddDue: (PartyEntity) -> Unit,
    onEdit: (PartyEntity) -> Unit,
    onDelete: (PartyEntity) -> Unit
) {
    val payable = vendor.balance < 0
    KiranaCard(borderColor = Color.Transparent) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Gray100),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(vendor.name.take(1), fontWeight = FontWeight.Bold, fontSize = 22.sp, color = TextPrimary)
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(vendor.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
                        Text(vendor.phone, fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.SemiBold)
                        if (!vendor.gstNumber.isNullOrBlank()) {
                            Text("GST: ${vendor.gstNumber}", fontSize = 11.sp, color = TextSecondary)
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    val raw = Formatters.formatInrCurrency(vendor.balance, fractionDigits = 0, useAbsolute = true)
                    Text(
                        text = if (vendor.balance == 0.0) "Settled" else raw,
                        fontWeight = FontWeight.Black,
                        fontSize = 16.sp,
                        color = if (payable) LossRed else TextSecondary
                    )
                    if (vendor.balance != 0.0) {
                        Text(
                            if (payable) "YOU OWE" else "ADVANCE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (payable) LossRed else KiranaGreen
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { onPayNow(vendor) },
                        enabled = vendor.balance != 0.0,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = Gray100, contentColor = TextPrimary),
                        border = null
                    ) {
                        Text("Pay", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = { onAddDue(vendor) },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = BgCard, contentColor = TextPrimary),
                        border = null
                    ) {
                        Text("Add Due", fontWeight = FontWeight.Bold)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = { onEdit(vendor) }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = TextPrimary)
                    }
                    IconButton(onClick = { onDelete(vendor) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = LossRed)
                    }
                }
            }
        }
    }
}

@Composable
fun PartyCard(party: PartyEntity) {
    val isRed = party.balance > 0 && party.type == "CUSTOMER" // Owe us
    
    KiranaCard(borderColor = Color.Transparent) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(if(isRed) LossRedBg else KiranaGreenBg),
                    contentAlignment = Alignment.Center
                ) {
                    Text(party.name.take(1), fontWeight = FontWeight.Bold, fontSize = 24.sp, color = if(isRed) LossRed else KiranaGreen)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(party.name, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Gray900)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Phone, null, modifier = Modifier.size(12.dp), tint = Gray400)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(party.phone, fontSize = 12.sp, color = Gray400, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            
            Column(horizontalAlignment = Alignment.End) {
                val raw = Formatters.formatInrCurrency(party.balance, fractionDigits = 0, useAbsolute = true)
                Text(
                    text = if(party.balance == 0.0) "Settled" else raw,
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp,
                    color = if(isRed) LossRed else Gray400
                )
                if (party.balance != 0.0) {
                     Text(if(isRed) "DUE" else "ADVANCE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if(isRed) LossRed else KiranaGreen)
                }
            }
        }
    }
}
