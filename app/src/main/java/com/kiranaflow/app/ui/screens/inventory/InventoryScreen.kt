package com.kiranaflow.app.ui.screens.inventory

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.BackHandler
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import java.io.File
import com.kiranaflow.app.data.local.ItemEntity
import com.kiranaflow.app.data.local.PartyEntity
import com.kiranaflow.app.data.local.KiranaDatabase
import com.kiranaflow.app.data.repository.KiranaRepository
import com.kiranaflow.app.ui.components.CircleButton
import com.kiranaflow.app.ui.components.KiranaButton
import com.kiranaflow.app.ui.components.KiranaCard
import com.kiranaflow.app.ui.components.KiranaInput
import com.kiranaflow.app.ui.components.AddFab
import com.kiranaflow.app.ui.components.SolidTopBar
import com.kiranaflow.app.ui.theme.*
import com.kiranaflow.app.util.InputFilters
import com.kiranaflow.app.util.OcrUtils
import com.kiranaflow.app.util.BillExtractionPipeline
import com.kiranaflow.app.util.BillOcrParser
import com.kiranaflow.app.util.InventorySheetParser
import androidx.compose.ui.text.input.KeyboardType
import android.widget.Toast

@Composable
fun InventoryScreen(
    navController: NavController,
    triggerAddItem: Boolean = false,
    onTriggerConsumed: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    viewModel: InventoryViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember(context) { KiranaRepository(KiranaDatabase.getDatabase(context)) }
    val items by viewModel.filteredItems.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    // Important: Navigation to scanner disposes this destination; keep modal state across navigation.
    var showAddModal by rememberSaveable { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<ItemEntity?>(null) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedItemIds by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var showBulkDeleteConfirm by remember { mutableStateOf(false) }
    var showBillScanner by remember { mutableStateOf(false) }
    var importBusy by remember { mutableStateOf(false) }
    var purchaseDraft by remember { mutableStateOf<PurchaseDraft?>(null) }

    LaunchedEffect(triggerAddItem) {
        if (triggerAddItem) {
            showAddModal = true
            onTriggerConsumed()
        }
    }

    val newBarcode = navController.currentBackStackEntry?.savedStateHandle?.getLiveData<String>("barcode")?.observeAsState()
    val scannedBarcodeValue = newBarcode?.value
    
    // Prefilled product name from billing search (when user clicks "Add as new product")
    val prefillProductName: String? =
        navController.currentBackStackEntry
            ?.savedStateHandle
            ?.getLiveData<String>("prefill_name")
            ?.observeAsState()
            ?.value
    val scannedItem by viewModel.scannedItem.collectAsState()
    val offProduct by viewModel.offProduct.collectAsState()
    val offLoading by viewModel.offLoading.collectAsState()
    val persistedBarcode by viewModel.scannedBarcode.collectAsState()
    val vendors by viewModel.vendors.collectAsState()

    // If inventory was opened from Billing "Add now to inventory", make BACK return to Billing.
    val returnToRoute: String? =
        navController.currentBackStackEntry
            ?.savedStateHandle
            ?.getLiveData<String>("return_to")
            ?.observeAsState()
            ?.value

    val returnToBilling = returnToRoute == "bill"
    if (returnToBilling) {
        BackHandler(enabled = true) {
            // Clear the flag so normal inventory navigation isn't affected later.
            Log.d("InventoryNav", "BackHandler: returning to $returnToRoute")
            navController.currentBackStackEntry?.savedStateHandle?.remove<String>("return_to")
            // Prefer popBackStack to the target if it exists; otherwise navigate there.
            val popped = navController.popBackStack("bill", false)
            if (!popped) {
                navController.navigate("bill")
            }
        }
    }

    LaunchedEffect(scannedBarcodeValue) {
        if (!scannedBarcodeValue.isNullOrBlank()) {
            viewModel.onBarcodeScanned(scannedBarcodeValue)
            // Re-open the Add Item dialog after returning from scanner.
            showAddModal = true
            // Consume the barcode so it doesn't retrigger on recomposition.
            navController.currentBackStackEntry?.savedStateHandle?.set("barcode", null)
        }
    }

    // Open add modal when prefill_name is set (from billing search "Add as new product")
    LaunchedEffect(prefillProductName) {
        if (!prefillProductName.isNullOrBlank()) {
            editingItem = null
            showAddModal = true
        }
    }

    val accent = tabCapsuleColor("inventory")

    Box(modifier = Modifier.fillMaxSize().background(GrayBg)) {
        // Bottom-right Add button (above bottom menu bar)
        AddFab(
            onClick = {
                editingItem = null
                showAddModal = true
            },
            containerColor = accent,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 24.dp, bottom = 112.dp)
                .zIndex(2f)
        )

        Column(modifier = Modifier.fillMaxSize()) {
            SolidTopBar(
                title = "Manage Inventory",
                subtitle = "Track stock & prices",
                onSettings = onOpenSettings,
                containerColor = accent
            )

            // Use single LazyColumn for entire content to support landscape scrolling
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(top = 18.dp, bottom = 100.dp)
            ) {
                // Search as first item
                item {
                    KiranaInput(
                        value = searchQuery,
                        onValueChange = viewModel::onSearchChange,
                        placeholder = "Search items...",
                        icon = Icons.Default.Search,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Selection controls
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (selectionMode) {
                            Text("${selectedItemIds.size} selected", fontWeight = FontWeight.Bold, color = TextPrimary)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(
                                    onClick = { showBulkDeleteConfirm = true },
                                    enabled = selectedItemIds.isNotEmpty()
                                ) { Text("Delete", color = LossRed, fontWeight = FontWeight.Bold) }
                                TextButton(
                                    onClick = {
                                        selectionMode = false
                                        selectedItemIds = emptySet()
                                    }
                                ) { Text("Cancel", fontWeight = FontWeight.Bold) }
                            }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { showBillScanner = true }) {
                                    Icon(Icons.Default.CameraAlt, contentDescription = null, tint = TextSecondary)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Scan Bill", fontWeight = FontWeight.Bold, color = TextSecondary)
                                }
                                TextButton(onClick = { selectionMode = true }) {
                                    Icon(Icons.Default.Checklist, contentDescription = null, tint = TextSecondary)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Select", fontWeight = FontWeight.Bold, color = TextSecondary)
                                }
                            }
                        }
                    }
                }

                // Inventory items list
                items(items, key = { it.id }) { item ->
                    val isSelected = selectedItemIds.contains(item.id)
                    InventoryItemCardSelectable(
                        item = item,
                        selectionMode = selectionMode,
                        isSelected = isSelected,
                        onClick = {
                            if (selectionMode) {
                                selectedItemIds = if (isSelected) selectedItemIds - item.id else selectedItemIds + item.id
                            } else {
                                editingItem = item
                                showAddModal = true
                            }
                        },
                        onLongPress = {
                            if (!selectionMode) selectionMode = true
                            selectedItemIds = selectedItemIds + item.id
                        },
                        onAdjustStock = { delta ->
                            viewModel.adjustStock(item, delta)
                        },
                        onAddReceivedStock = { qty ->
                            viewModel.addReceivedStock(item, qty)
                        }
                    )
                }
            }
        }

        if (showAddModal) {
            AddItemDialog(
                onDismiss = {
                    // Clear prefill_name so it doesn't retrigger
                    navController.currentBackStackEntry?.savedStateHandle?.remove<String>("prefill_name")
                    // When launched from Billing "Add now to inventory", X should return to Billing (wireframe expectation).
                    if (returnToBilling) {
                        Log.d("InventoryNav", "Dismiss AddItemDialog -> return to Billing")
                        navController.currentBackStackEntry?.savedStateHandle?.remove<String>("return_to")
                        val popped = navController.popBackStack("bill", false)
                        if (!popped) {
                            navController.navigate("bill") { launchSingleTop = true }
                        }
                    } else {
                        showAddModal = false
                    }
                },
                onSave = { name, cat, cost, sell, isLoose, pricePerKg, stockKg, stock, loc, barcode, gst, reorder, imageUri, vendorId, expiryMillis ->
                    val id = editingItem?.id
                    viewModel.saveItem(
                        id = id,
                        name = name,
                        category = cat,
                        cost = cost,
                        sell = sell,
                        isLoose = isLoose,
                        pricePerKg = pricePerKg,
                        stockKg = stockKg,
                        stock = stock,
                        location = loc,
                        barcode = barcode,
                        gst = gst,
                        reorder = reorder,
                        imageUri = imageUri,
                        vendorId = vendorId,
                        expiryDateMillis = expiryMillis
                    )
                    runCatching {
                        Toast.makeText(context, "Saved successfully", Toast.LENGTH_SHORT).show()
                    }
                    // Clear prefill_name so it doesn't retrigger
                    navController.currentBackStackEntry?.savedStateHandle?.remove<String>("prefill_name")
                    if (returnToBilling) {
                        Log.d("InventoryNav", "Saved item from Billing flow -> return to Billing")
                        navController.currentBackStackEntry?.savedStateHandle?.remove<String>("return_to")
                        val popped = navController.popBackStack("bill", false)
                        if (!popped) {
                            navController.navigate("bill") { launchSingleTop = true }
                        }
                    } else {
                        showAddModal = false
                    }
                    editingItem = null
                },
                onDelete = { itemToDelete ->
                    viewModel.deleteItem(itemToDelete)
                    // Clear prefill_name so it doesn't retrigger
                    navController.currentBackStackEntry?.savedStateHandle?.remove<String>("prefill_name")
                    if (returnToBilling) {
                        Log.d("InventoryNav", "Deleted item from Billing flow -> return to Billing")
                        navController.currentBackStackEntry?.savedStateHandle?.remove<String>("return_to")
                        val popped = navController.popBackStack("bill", false)
                        if (!popped) {
                            navController.navigate("bill") { launchSingleTop = true }
                        }
                    } else {
                        showAddModal = false
                    }
                    editingItem = null
                },
                onScanBarcode = {
                    navController.navigate("scanner/inventory")
                },
                scannedBarcode = persistedBarcode ?: scannedBarcodeValue,
                existingItem = editingItem ?: scannedItem,
                onConsumeExistingItem = {
                    if (editingItem != null) editingItem = null else viewModel.clearScannedItem()
                },
                offProduct = offProduct,
                offLoading = offLoading,
                onConsumeOff = { viewModel.clearOffProduct() },
                categories = buildList {
                    add("General")
                    addAll(items.map { it.category }.map { it.trim() }.filter { it.isNotBlank() }.distinct().sorted())
                },
                vendors = vendors,
                onAddVendor = { name, phone, gst -> repo.addVendor(name, phone, gst) },
                prefillName = prefillProductName
            )
        }
    }

    if (showBillScanner) {
        BillScannerScreen(
            onDismiss = { showBillScanner = false },
            onBillDocumentSelected = { uri ->
                showBillScanner = false
                val cr = context.contentResolver
                scope.launch {
                    if (importBusy) return@launch
                    importBusy = true
                    try {
                        val text = OcrUtils.ocrFromUri(cr, uri)
                        val parsed = BillExtractionPipeline.extract(context, text)
                        val res = repo.processVendorBill(parsed)
                        Toast.makeText(
                            context,
                            "Imported: ${res.added} added, ${res.updated} updated",
                            Toast.LENGTH_LONG
                        ).show()
                        // Offer to record this as a vendor purchase (so payables can show itemized dues).
                        purchaseDraft = PurchaseDraft(
                            parsed = parsed,
                            receiptUri = uri.toString(),
                            detectedVendorId = res.vendorId,
                            detectedVendorName = res.vendorName ?: parsed.vendor.name
                        )
                    } finally {
                        importBusy = false
                    }
                }
            },
            onInventoryFileSelected = { uri ->
                showBillScanner = false
                val cr = context.contentResolver
                scope.launch {
                    if (importBusy) return@launch
                    importBusy = true
                    try {
                        val rows = InventorySheetParser.parse(cr, uri)
                        val res = repo.processInventorySheetRows(rows)
                        Toast.makeText(
                            context,
                            "Imported: ${res.added} added, ${res.updated} updated",
                            Toast.LENGTH_LONG
                        ).show()
                    } finally {
                        importBusy = false
                    }
                }
            }
        )
    }

    if (showBulkDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showBulkDeleteConfirm = false },
            title = { Text("Delete selected items?", fontWeight = FontWeight.Bold) },
            text = { Text("This will remove ${selectedItemIds.size} item(s) from inventory.") },
            confirmButton = {
                TextButton(onClick = {
                    showBulkDeleteConfirm = false
                    viewModel.deleteItemsByIds(selectedItemIds)
                    selectionMode = false
                    selectedItemIds = emptySet()
                }) { Text("Delete", color = LossRed) }
            },
            dismissButton = {
                TextButton(onClick = { showBulkDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }

    purchaseDraft?.let { draft ->
        RecordVendorBillPurchaseDialog(
            draft = draft,
            vendors = vendors,
            onDismiss = { purchaseDraft = null },
            onConfirm = { vendor, mode, editedBill ->
                scope.launch {
                    runCatching {
                        repo.recordVendorBillPurchaseWithItems(
                            vendor = vendor,
                            parsed = editedBill,
                            mode = mode,
                            receiptImageUri = draft.receiptUri
                        )
                        Toast.makeText(context, "Purchase recorded", Toast.LENGTH_SHORT).show()
                    }.onFailure {
                        Toast.makeText(context, "Could not record purchase", Toast.LENGTH_SHORT).show()
                    }
                    purchaseDraft = null
                }
            }
        )
    }
}

private data class PurchaseDraft(
    val parsed: BillOcrParser.ParsedBill,
    val receiptUri: String?,
    val detectedVendorId: Int?,
    val detectedVendorName: String?
)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun RecordVendorBillPurchaseDialog(
    draft: PurchaseDraft,
    vendors: List<PartyEntity>,
    onDismiss: () -> Unit,
    onConfirm: (PartyEntity, String, BillOcrParser.ParsedBill) -> Unit
) {
    var mode by remember { mutableStateOf("CREDIT") } // default: Udhaar
    var selectedVendorId by remember(draft.detectedVendorId) { mutableStateOf(draft.detectedVendorId) }

    val selectedVendor = remember(vendors, selectedVendorId) { vendors.firstOrNull { it.id == selectedVendorId } }

    data class EditableLine(
        val id: Int,
        val name: String,
        val qty: String,
        val unitPrice: String,
        val unit: String,
        val rawLine: String
    )

    val initialLines = remember(draft.parsed) {
        draft.parsed.items.take(80).mapIndexed { idx, it ->
            val inferredUnitPrice = it.unitPrice ?: run {
                val t = it.total
                if (t != null && it.qty > 0) t / it.qty.toDouble() else null
            }
            EditableLine(
                id = idx,
                name = it.name,
                qty = it.qty.toString(),
                unitPrice = inferredUnitPrice?.takeIf { p -> p > 0.0 }?.toString().orEmpty(),
                unit = it.unit ?: "PCS",
                rawLine = it.rawLine
            )
        }
    }

    var lines by remember(draft.parsed) { mutableStateOf(initialLines) }

    val editedParsed = remember(draft.parsed.vendor, lines) {
        val editedItems = lines.mapNotNull { l ->
            val name = l.name.trim()
            if (name.isBlank()) return@mapNotNull null
            val qty = l.qty.trim().toIntOrNull()?.coerceAtLeast(1) ?: return@mapNotNull null
            val unitPrice = l.unitPrice.trim().toDoubleOrNull()?.takeIf { it > 0.0 } ?: return@mapNotNull null
            BillOcrParser.ParsedBillItem(
                name = name,
                qty = qty,
                qtyRaw = qty.toDouble(),
                unit = l.unit.trim().ifBlank { "PCS" },
                unitPrice = unitPrice,
                total = unitPrice * qty,
                rawLine = l.rawLine
            )
        }
        BillOcrParser.ParsedBill(vendor = draft.parsed.vendor, items = editedItems)
    }

    val computedTotal = remember(editedParsed) {
        editedParsed.items.sumOf { it.total ?: ((it.unitPrice ?: 0.0) * it.qty) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Review before recording", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = draft.detectedVendorName?.let { "Detected vendor: $it" } ?: "Vendor not detected",
                    color = TextSecondary,
                    fontSize = 12.sp
                )

                // Vendor picker (simple)
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                    OutlinedTextField(
                        value = selectedVendor?.name ?: "Select vendor…",
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = White,
                            unfocusedContainerColor = White,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        vendors.forEach { v ->
                            DropdownMenuItem(
                                text = { Text(v.name) },
                                onClick = {
                                    selectedVendorId = v.id
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                Text(
                    text = "Total (estimated): ₹${computedTotal.toInt()}",
                    fontWeight = FontWeight.Black,
                    color = LossRed
                )

                Text(
                    text = "Edit items before saving (wrong qty/price/name):",
                    color = TextSecondary,
                    fontSize = 12.sp
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = GrayBg,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 260.dp)
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        itemsIndexed(lines, key = { _, it -> it.id }) { idx, line ->
                            Surface(shape = RoundedCornerShape(12.dp), color = White) {
                                Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = line.name,
                                            onValueChange = { v ->
                                                lines = lines.toMutableList().also { it[idx] = it[idx].copy(name = v) }
                                            },
                                            modifier = Modifier.weight(1f),
                                            label = { Text("Item") },
                                            singleLine = true,
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedContainerColor = White,
                                                unfocusedContainerColor = White
                                            )
                                        )
                                        IconButton(onClick = { lines = lines.toMutableList().also { it.removeAt(idx) } }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Remove", tint = LossRed)
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        OutlinedTextField(
                                            value = line.qty,
                                            onValueChange = { v ->
                                                val filtered = v.filter { it.isDigit() }.take(4)
                                                lines = lines.toMutableList().also { it[idx] = it[idx].copy(qty = filtered) }
                                            },
                                            modifier = Modifier.weight(1f),
                                            label = { Text("Qty") },
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedContainerColor = White,
                                                unfocusedContainerColor = White
                                            )
                                        )
                                        OutlinedTextField(
                                            value = line.unitPrice,
                                            onValueChange = { v ->
                                                val filtered = v.filter { it.isDigit() || it == '.' }.take(10)
                                                lines = lines.toMutableList().also { it[idx] = it[idx].copy(unitPrice = filtered) }
                                            },
                                            modifier = Modifier.weight(1f),
                                            label = { Text("Unit price") },
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedContainerColor = White,
                                                unfocusedContainerColor = White
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilterChip(selected = mode == "CREDIT", onClick = { mode = "CREDIT" }, label = { Text("Udhaar") })
                    FilterChip(selected = mode == "CASH", onClick = { mode = "CASH" }, label = { Text("Cash") })
                    FilterChip(selected = mode == "UPI", onClick = { mode = "UPI" }, label = { Text("UPI") })
                }

                Text(
                    text = "This enables item-wise payables in the Total Payables screen.",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = selectedVendor != null && computedTotal > 0.0 && editedParsed.items.isNotEmpty(),
                onClick = { selectedVendor?.let { onConfirm(it, mode, editedParsed) } }
            ) { Text("Record") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Skip") } }
    )
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun InventoryItemCardSelectable(
    item: ItemEntity,
    selectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onAdjustStock: (Int) -> Unit = {},
    onAddReceivedStock: (Int) -> Unit = {}
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        if (selectionMode) {
            // Checkbox sits to the LEFT, outside the card (requested).
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onClick() },
                modifier = Modifier
                    .padding(end = 8.dp, top = 16.dp)
                    .size(24.dp)
            )
        }
        InventoryItemCardWithQuickStock(
            item = item,
            selectionMode = selectionMode,
            onAdjustStock = onAdjustStock,
            onAddReceivedStock = onAddReceivedStock,
            modifier = Modifier
                .weight(1f)
                .combinedClickable(onClick = onClick, onLongClick = onLongPress)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InventoryItemCard(
    item: ItemEntity,
    modifier: Modifier = Modifier
) {
    KiranaCard(modifier = modifier, onClick = null) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            // Product image thumbnail
            Surface(
                modifier = Modifier.size(56.dp),
                shape = RoundedCornerShape(16.dp),
                color = Gray50
            ) {
                if (!item.imageUri.isNullOrBlank()) {
                    AsyncImage(
                        model = item.imageUri,
                        contentDescription = item.name,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Inventory2, contentDescription = null, tint = Gray400)
                    }
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Main content - takes remaining space
            Column(modifier = Modifier.weight(1f)) {
                // Name and Price row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = item.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "₹${item.price.toInt()}",
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                        color = TextPrimary
                    )
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // Category and location
                Text(
                    text = "${item.category} • ${item.rackLocation ?: "No Loc"}",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(10.dp))
                
                // Badges row - wrap to next line if needed
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val lowStock = if (item.isLoose) item.stockKg < item.reorderPoint else item.stock < item.reorderPoint
                    val stockText = if (item.isLoose) "${item.stockKg} kg" else "${item.stock}"
                    Badge(
                        text = "$stockText in Stock",
                        color = if (lowStock) LossRed else KiranaGreen,
                        bg = if (lowStock) LossRedBg else KiranaGreenBg
                    )
                    Badge(
                        text = "${item.marginPercentage.toInt()}% Margin",
                        color = TextSecondary,
                        bg = Gray100
                    )
                }
            }
        }

    }
}

/**
 * Enhanced inventory card with quick stock adjustment controls.
 * Shows +/- buttons and a field to add received stock without opening the detail dialog.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InventoryItemCardWithQuickStock(
    item: ItemEntity,
    selectionMode: Boolean,
    onAdjustStock: (Int) -> Unit,
    onAddReceivedStock: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddStockField by remember { mutableStateOf(false) }
    var addStockValue by remember { mutableStateOf("") }
    
    KiranaCard(modifier = modifier, onClick = null) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                // Product image thumbnail
                Surface(
                    modifier = Modifier.size(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = Gray50
                ) {
                    if (!item.imageUri.isNullOrBlank()) {
                        AsyncImage(
                            model = item.imageUri,
                            contentDescription = item.name,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Inventory2, contentDescription = null, tint = Gray400)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                // Main content - takes remaining space
                Column(modifier = Modifier.weight(1f)) {
                    // Name and Price row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = item.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = TextPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (item.isLoose) "₹${item.pricePerKg.toInt()}/kg" else "₹${item.price.toInt()}",
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp,
                            color = TextPrimary
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    // Category and location
                    Text(
                        text = "${item.category} • ${item.rackLocation ?: "No Loc"}",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    // Badges row
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val lowStock = if (item.isLoose) item.stockKg < item.reorderPoint else item.stock < item.reorderPoint
                        val stockText = if (item.isLoose) "${item.stockKg} kg" else "${item.stock}"
                        Badge(
                            text = "$stockText in Stock",
                            color = if (lowStock) LossRed else KiranaGreen,
                            bg = if (lowStock) LossRedBg else KiranaGreenBg
                        )
                        Badge(
                            text = "${item.marginPercentage.toInt()}% Margin",
                            color = TextSecondary,
                            bg = Gray100
                        )
                    }
                }
            }
            
            // Quick Stock Adjustment Row (only show when not in selection mode)
            if (!selectionMode) {
                Spacer(modifier = Modifier.height(12.dp))
                Divider(color = Gray100, thickness = 1.dp)
                Spacer(modifier = Modifier.height(10.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Quick +/- controls
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Minus button
                        Surface(
                            modifier = Modifier
                                .size(36.dp)
                                .clickable { onAdjustStock(-1) },
                            shape = RoundedCornerShape(10.dp),
                            color = LossRedBg
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Text(
                                    text = "−",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 20.sp,
                                    color = LossRed
                                )
                            }
                        }
                        
                        // Current stock display
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Gray50,
                            modifier = Modifier.widthIn(min = 60.dp)
                        ) {
                            Text(
                                text = if (item.isLoose) "${item.stockKg} kg" else "${item.stock}",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = TextPrimary
                            )
                        }
                        
                        // Plus button
                        Surface(
                            modifier = Modifier
                                .size(36.dp)
                                .clickable { onAdjustStock(1) },
                            shape = RoundedCornerShape(10.dp),
                            color = KiranaGreenBg
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Text(
                                    text = "+",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 20.sp,
                                    color = KiranaGreen
                                )
                            }
                        }
                    }
                    
                    // Add Stock button/field
                    if (showAddStockField) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedTextField(
                                value = addStockValue,
                                onValueChange = { v ->
                                    addStockValue = v.filter { it.isDigit() }.take(5)
                                },
                                modifier = Modifier.width(80.dp).height(44.dp),
                                placeholder = { Text("Qty", fontSize = 12.sp) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = White,
                                    unfocusedContainerColor = White,
                                    focusedBorderColor = KiranaGreen,
                                    unfocusedBorderColor = Gray200
                                ),
                                shape = RoundedCornerShape(10.dp)
                            )
                            
                            // Confirm add
                            Surface(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clickable {
                                        val qty = addStockValue.toIntOrNull() ?: 0
                                        if (qty > 0) {
                                            onAddReceivedStock(qty)
                                            addStockValue = ""
                                            showAddStockField = false
                                        }
                                    },
                                shape = RoundedCornerShape(10.dp),
                                color = KiranaGreen
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = "Add stock",
                                        tint = White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            
                            // Cancel
                            Surface(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clickable {
                                        addStockValue = ""
                                        showAddStockField = false
                                    },
                                shape = RoundedCornerShape(10.dp),
                                color = Gray100
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Cancel",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        // "Add Stock" button
                        Surface(
                            modifier = Modifier.clickable { showAddStockField = true },
                            shape = RoundedCornerShape(10.dp),
                            color = Blue100
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = null,
                                    tint = Blue600,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Add Stock",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = Blue600
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Badge(text: String, color: Color, bg: Color) {
    Surface(
        color = bg,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = color),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun AddItemDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, Double, Double, Boolean, Double, Double, Int, String?, String?, Double?, Int?, String?, Int?, Long?) -> Unit,
    onDelete: (ItemEntity) -> Unit,
    onScanBarcode: () -> Unit,
    scannedBarcode: String?,
    existingItem: ItemEntity?,
    onConsumeExistingItem: () -> Unit,
    offProduct: com.kiranaflow.app.data.remote.OffProductInfo?,
    offLoading: Boolean,
    onConsumeOff: () -> Unit,
    categories: List<String>,
    vendors: List<PartyEntity>,
    onAddVendor: suspend (String, String, String?) -> PartyEntity?,
    prefillName: String? = null
) {
    val context = LocalContext.current
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("General") }
    var showCategoryDropdown by remember { mutableStateOf(false) }
    var categoryQuery by remember { mutableStateOf("") }
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var newCategory by remember { mutableStateOf("") }
    var barcode by remember { mutableStateOf("") }
    var costPrice by remember { mutableStateOf("") }
    var sellingPrice by remember { mutableStateOf("") }
    var isLoose by remember { mutableStateOf(false) }
    var stock by remember { mutableStateOf("") }
    var gst by remember { mutableStateOf("") }
    var selectedVendorId by remember { mutableStateOf<Int?>(null) }
    var showVendorDropdown by remember { mutableStateOf(false) }
    var vendorQuery by remember { mutableStateOf("") }
    var showAddVendorDialog by remember { mutableStateOf(false) }
    var newVendorName by remember { mutableStateOf("") }
    var newVendorPhone by remember { mutableStateOf("") }
    var newVendorGst by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    var reorderPoint by remember { mutableStateOf("10") }
    var location by remember { mutableStateOf("") }
    var imageUri by remember { mutableStateOf<String?>(null) }
    var expiryDateMillis by remember { mutableStateOf<Long?>(null) }
    var showExpiryPicker by remember { mutableStateOf(false) }
    var showNameError by remember { mutableStateOf(false) }

    var pendingCameraUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            imageUri = pendingCameraUri?.toString()
        }
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) imageUri = uri.toString()
    }

    LaunchedEffect(scannedBarcode) {
        if (scannedBarcode != null) {
            barcode = scannedBarcode
        }
    }

    // Prefill name from billing search (when user clicks "Add as new product")
    LaunchedEffect(prefillName) {
        if (!prefillName.isNullOrBlank() && name.isBlank()) {
            name = prefillName
        }
    }

    // If this barcode already exists in DB, prefill the form to "edit-like" behavior.
    LaunchedEffect(existingItem?.id) {
        val item = existingItem ?: return@LaunchedEffect
        name = item.name
        category = item.category
        barcode = item.barcode ?: barcode
        costPrice = item.costPrice.toString()
        isLoose = item.isLoose
        sellingPrice = if (item.isLoose) item.pricePerKg.toString() else item.price.toString()
        stock = if (item.isLoose) item.stockKg.toString() else item.stock.toString()
        gst = item.gstPercentage?.toString().orEmpty()
        reorderPoint = item.reorderPoint.toString()
        location = item.rackLocation.orEmpty()
        imageUri = item.imageUri
        expiryDateMillis = item.expiryDateMillis
        selectedVendorId = item.vendorId
        onConsumeExistingItem()
    }

    val normalizedCategories = remember(categories, category) {
        // Ensure currently-selected category is present in the list.
        buildList {
            addAll(categories.map { it.trim() }.filter { it.isNotBlank() })
            val current = category.trim()
            if (current.isNotBlank() && !contains(current)) add(current)
        }.distinct()
    }
    val filteredCategories = remember(normalizedCategories, categoryQuery) {
        if (categoryQuery.isBlank()) normalizedCategories
        else normalizedCategories.filter { it.contains(categoryQuery, true) }
    }

    val filteredVendors = remember(vendors, vendorQuery) {
        if (vendorQuery.isBlank()) vendors
        else vendors.filter { it.name.contains(vendorQuery, true) || it.phone.contains(vendorQuery) }
    }
    val selectedVendorName = remember(vendors, selectedVendorId) {
        vendors.firstOrNull { it.id == selectedVendorId }?.name.orEmpty()
    }

    // If OFF returns a product, prefill what we can (keep barcode as priority).
    LaunchedEffect(offProduct?.barcode) {
        val p = offProduct ?: return@LaunchedEffect
        if (name.isBlank() && !p.name.isNullOrBlank()) name = p.name
        if (category.isBlank() || category == "General") {
            // take first category token if present
            val cat = p.categories?.split(",")?.firstOrNull()?.trim()
            if (!cat.isNullOrBlank()) category = cat
        }
        onConsumeOff()
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = BgPrimary,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
        ) {
            val sheetHeight = maxHeight * 0.94f
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(sheetHeight)
                    .padding(16.dp),
                shape = RoundedCornerShape(24.dp),
                color = White
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (existingItem != null && existingItem.id != 0) "Edit Product" else "Add New Product",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null) }
                }

                Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    // Product image picker (Camera + Gallery)
                    Surface(
                        modifier = Modifier
                            .size(120.dp)
                            .align(Alignment.CenterHorizontally),
                        shape = RoundedCornerShape(16.dp),
                        color = Gray50
                    ) {
                        if (!imageUri.isNullOrBlank()) {
                            AsyncImage(
                                model = imageUri,
                                contentDescription = "Product photo",
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Image, contentDescription = null, tint = Gray200, modifier = Modifier.size(48.dp))
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                val photoFile = File.createTempFile("kirana_photo_", ".jpg", context.cacheDir)
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile)
                                pendingCameraUri = uri
                                cameraLauncher.launch(uri)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Camera")
                        }
                        OutlinedButton(
                            onClick = { galleryLauncher.launch("image/*") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Gallery")
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    KiranaInput(value = name, onValueChange = { name = it }, placeholder = "e.g. Basmati Rice 5kg", label = "Item Name")
                    if (showNameError && name.trim().isBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Item name is required",
                            color = LossRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    // Category dropdown (search + add new)
                    Text("CATEGORY", style = MaterialTheme.typography.labelSmall)
                    Spacer(modifier = Modifier.height(6.dp))
                    ExposedDropdownMenuBox(
                        expanded = showCategoryDropdown,
                        onExpandedChange = { showCategoryDropdown = !showCategoryDropdown }
                    ) {
                        OutlinedTextField(
                            value = category,
                            onValueChange = {},
                            readOnly = true,
                            placeholder = { Text("Select category...") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showCategoryDropdown) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = White,
                                unfocusedContainerColor = White,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )
                        ExposedDropdownMenu(
                            expanded = showCategoryDropdown,
                            onDismissRequest = { showCategoryDropdown = false }
                        ) {
                            // Search header (must be focusable)
                            Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                OutlinedTextField(
                                    value = categoryQuery,
                                    onValueChange = { categoryQuery = it },
                                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                    placeholder = { Text("Search category...") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = White,
                                        unfocusedContainerColor = White,
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary
                                    )
                                )
                            }
                            Divider(color = Gray200)
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Add, contentDescription = null, tint = Blue600)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("+ Add new category", color = Blue600, fontWeight = FontWeight.Bold)
                                    }
                                },
                                onClick = { showAddCategoryDialog = true }
                            )
                            filteredCategories.forEach { c ->
                                DropdownMenuItem(
                                    text = { Text(c) },
                                    onClick = {
                                        category = c
                                        showCategoryDropdown = false
                                    }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    // Vendor dropdown (search + add new)
                    Text("VENDOR (OPTIONAL)", style = MaterialTheme.typography.labelSmall)
                    Spacer(modifier = Modifier.height(6.dp))
                    ExposedDropdownMenuBox(
                        expanded = showVendorDropdown,
                        onExpandedChange = { showVendorDropdown = !showVendorDropdown }
                    ) {
                        OutlinedTextField(
                            value = selectedVendorName,
                            onValueChange = {},
                            readOnly = true,
                            placeholder = { Text("Select vendor...") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showVendorDropdown) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = White,
                                unfocusedContainerColor = White,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )
                        ExposedDropdownMenu(
                            expanded = showVendorDropdown,
                            onDismissRequest = { showVendorDropdown = false }
                        ) {
                            // Search header (must be focusable)
                            Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                OutlinedTextField(
                                    value = vendorQuery,
                                    onValueChange = { vendorQuery = it },
                                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                    placeholder = { Text("Search vendor...") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = White,
                                        unfocusedContainerColor = White,
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary
                                    )
                                )
                            }
                            Divider(color = Gray200)
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Add, contentDescription = null, tint = Blue600)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("+ Add new vendor", color = Blue600, fontWeight = FontWeight.Bold)
                                    }
                                },
                                onClick = { showAddVendorDialog = true }
                            )
                            filteredVendors.forEach { v ->
                                DropdownMenuItem(
                                    text = { Text(v.name) },
                                    onClick = {
                                        selectedVendorId = v.id
                                        showVendorDropdown = false
                                    }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Barcode Scanner Button
                    Text("BARCODE (OPTIONAL)", style = MaterialTheme.typography.labelSmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .border(1.dp, Gray200, RoundedCornerShape(16.dp))
                        .clickable { onScanBarcode() }
                        .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (barcode.isNotBlank()) {
                            Text(barcode, fontWeight = FontWeight.Bold)
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Search, null, tint = KiranaGreen)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Scan Barcode", color = KiranaGreen, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    if (offLoading) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Looking up product…", fontSize = 12.sp, color = Gray400)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        KiranaInput(
                            value = costPrice,
                            onValueChange = { costPrice = InputFilters.decimal(it) },
                            placeholder = "0",
                            label = if (isLoose) "Cost per KG (₹)" else "Cost Price (₹)",
                            modifier = Modifier.weight(1f),
                            keyboardType = KeyboardType.Decimal
                        )
                        KiranaInput(
                            value = sellingPrice,
                            onValueChange = { sellingPrice = InputFilters.decimal(it) },
                            placeholder = "0",
                            label = if (isLoose) "Price per KG (₹)" else "Selling Price (₹)",
                            modifier = Modifier.weight(1f),
                            keyboardType = KeyboardType.Decimal
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("LOOSE ITEM", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            Text("Sold by weight (Kg)", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                        Switch(
                            checked = isLoose,
                            onCheckedChange = { checked -> isLoose = checked }
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        KiranaInput(
                            value = stock,
                            onValueChange = {
                                stock = if (isLoose) InputFilters.decimal(it, maxDecimals = 3) else InputFilters.digitsOnly(it)
                            },
                            placeholder = "0",
                            label = if (isLoose) "Stock (kg)" else "Stock",
                            modifier = Modifier.weight(1f),
                            keyboardType = if (isLoose) KeyboardType.Decimal else KeyboardType.Number
                        )
                        KiranaInput(
                            value = gst,
                            onValueChange = { gst = InputFilters.decimal(it, maxDecimals = 2) },
                            placeholder = "e.g. 18",
                            label = "GST % (Optional)",
                            modifier = Modifier.weight(1f),
                            keyboardType = KeyboardType.Decimal
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        // Location input added
                        KiranaInput(value = location, onValueChange = { location = it }, placeholder = "Rack A1", label = "Location", modifier = Modifier.weight(1f))
                        KiranaInput(
                            value = reorderPoint,
                            onValueChange = { reorderPoint = InputFilters.digitsOnly(it) },
                            placeholder = "10",
                            label = "Reorder Point",
                            modifier = Modifier.weight(1f),
                            keyboardType = KeyboardType.Number
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("EXPIRY (OPTIONAL)", style = MaterialTheme.typography.labelSmall)
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = expiryDateMillis?.let {
                            java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date(it))
                        }.orEmpty(),
                        onValueChange = {},
                        readOnly = true,
                        placeholder = { Text("Select expiry date...") },
                        trailingIcon = {
                            IconButton(onClick = { showExpiryPicker = true }) {
                                Icon(Icons.Default.DateRange, contentDescription = null, tint = TextSecondary)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = White,
                            unfocusedContainerColor = White,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                    if (expiryDateMillis != null) {
                        TextButton(onClick = { expiryDateMillis = null }) { Text("Clear expiry") }
                    }
                    // (Vendor selection is above; remove old vendor text field)
                }

                Spacer(modifier = Modifier.height(12.dp))
                // Save + Delete (delete appears only when editing an existing item)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    if (existingItem != null && existingItem.id != 0) {
                        OutlinedButton(
                            onClick = { showDeleteConfirm = true },
                            modifier = Modifier.weight(1f).height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = LossRedBg, contentColor = LossRed),
                            border = null
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = LossRed)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Delete", fontWeight = FontWeight.Bold)
                        }
                    }
                    KiranaButton(
                        text = if (existingItem != null && existingItem.id != 0) "Save Changes" else "Save Product",
                        onClick = {
                            val cleanName = name.trim()
                            if (cleanName.isBlank()) {
                                showNameError = true
                                return@KiranaButton
                            }
                            showNameError = false
                            onSave(
                                cleanName,
                                category,
                                costPrice.toDoubleOrNull() ?: 0.0,
                                sellingPrice.toDoubleOrNull() ?: 0.0,
                                isLoose,
                                if (isLoose) (sellingPrice.toDoubleOrNull() ?: 0.0) else 0.0,
                                if (isLoose) (stock.toDoubleOrNull() ?: 0.0) else 0.0,
                                if (isLoose) 0 else (stock.toIntOrNull() ?: 0),
                                location,
                                barcode,
                                gst.toDoubleOrNull(),
                                reorderPoint.toIntOrNull(),
                                imageUri,
                                selectedVendorId,
                                expiryDateMillis
                            )
                        },
                        enabled = name.trim().isNotBlank(),
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = KiranaGreen, contentColor = White)
                    )
                }
            }
        }
    }

    if (showDeleteConfirm && existingItem != null && existingItem.id != 0) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete product?", fontWeight = FontWeight.Bold) },
            text = { Text("This will remove '${existingItem.name}' from your inventory.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete(existingItem)
                }) { Text("Delete", color = LossRed) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } }
        )
    }

    if (showExpiryPicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = expiryDateMillis)
        DatePickerDialog(
            onDismissRequest = { showExpiryPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    expiryDateMillis = pickerState.selectedDateMillis
                    showExpiryPicker = false
                }) { Text("Set") }
            },
            dismissButton = { TextButton(onClick = { showExpiryPicker = false }) { Text("Cancel") } }
        ) {
            DatePicker(
                state = pickerState,
                showModeToggle = false,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    if (showAddVendorDialog) {
        var addVendorPhoneError by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showAddVendorDialog = false },
            title = { Text("Add New Vendor", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = newVendorName,
                        onValueChange = { newVendorName = it },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newVendorPhone,
                        onValueChange = { newVendorPhone = InputFilters.digitsOnly(it, maxLen = 10) },
                        label = { Text("Phone") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Phone)
                    )
                    if (addVendorPhoneError) {
                        Text(
                            text = "Mobile number must be 10 digits",
                            color = LossRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    OutlinedTextField(
                        value = newVendorGst,
                        onValueChange = { newVendorGst = it },
                        label = { Text("GST Number (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            if (newVendorPhone.length != 10) {
                                addVendorPhoneError = true
                                return@launch
                            }
                            addVendorPhoneError = false
                            val created = onAddVendor(newVendorName, newVendorPhone, newVendorGst)
                            if (created != null) {
                                selectedVendorId = created.id
                                vendorQuery = ""
                                showVendorDropdown = false
                            }
                            showAddVendorDialog = false
                            newVendorName = ""
                            newVendorPhone = ""
                            newVendorGst = ""
                        }
                    }
                ) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showAddVendorDialog = false }) { Text("Cancel") } }
        )
    }

    if (showAddCategoryDialog) {
        AlertDialog(
            onDismissRequest = { showAddCategoryDialog = false },
            title = { Text("Add New Category", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newCategory,
                    onValueChange = { newCategory = it },
                    label = { Text("Category name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val c = newCategory.trim()
                        if (c.isNotBlank()) category = c
                        newCategory = ""
                        showAddCategoryDialog = false
                    }
                ) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showAddCategoryDialog = false }) { Text("Cancel") } }
        )
    }
}
}

