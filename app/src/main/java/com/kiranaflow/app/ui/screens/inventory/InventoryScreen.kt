package com.kiranaflow.app.ui.screens.inventory

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import com.kiranaflow.app.ui.components.ValleyTopBar
import com.kiranaflow.app.ui.theme.*

@Composable
fun InventoryScreen(
    navController: NavController,
    triggerAddItem: Boolean = false,
    onTriggerConsumed: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    viewModel: InventoryViewModel = viewModel()
) {
    val context = LocalContext.current
    val repo = remember(context) { KiranaRepository(KiranaDatabase.getDatabase(context)) }
    val items by viewModel.filteredItems.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    // Important: Navigation to scanner disposes this destination; keep modal state across navigation.
    var showAddModal by rememberSaveable { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<ItemEntity?>(null) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedItemIds by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var showBulkDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(triggerAddItem) {
        if (triggerAddItem) {
            showAddModal = true
            onTriggerConsumed()
        }
    }

    val newBarcode = navController.currentBackStackEntry?.savedStateHandle?.getLiveData<String>("barcode")?.observeAsState()
    val scannedBarcodeValue = newBarcode?.value
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

    Box(modifier = Modifier.fillMaxSize().background(GrayBg)) {
        Column(modifier = Modifier.fillMaxSize()) {
            ValleyTopBar(
                title = "Manage Inventory",
                subtitle = "Track stock & prices",
                actionIcon = Icons.Default.Add,
                onAction = {
                    editingItem = null
                    showAddModal = true
                },
                onSettings = onOpenSettings,
                actionColor = AlertOrange,
                actionIconTint = White
            )
            Spacer(modifier = Modifier.height(18.dp))

            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                // Search
                KiranaInput(
                    value = searchQuery,
                    onValueChange = viewModel::onSearchChange,
                    placeholder = "Search items...",
                    icon = Icons.Default.Search,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
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
                        TextButton(onClick = { selectionMode = true }) {
                            Icon(Icons.Default.Checklist, contentDescription = null, tint = TextSecondary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Select", fontWeight = FontWeight.Bold, color = TextSecondary)
                        }
                    }
                }

                // List
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 100.dp)
                ) {
                    items(items) { item ->
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
                            }
                        )
                    }
                }
            }
        }

        if (showAddModal) {
            AddItemDialog(
                onDismiss = {
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
                onSave = { name, cat, cost, sell, stock, loc, barcode, gst, reorder, imageUri, vendorId, expiryMillis ->
                    val id = editingItem?.id
                    viewModel.saveItem(
                        id = id,
                        name = name,
                        category = cat,
                        cost = cost,
                        sell = sell,
                        stock = stock,
                        location = loc,
                        barcode = barcode,
                        gst = gst,
                        reorder = reorder,
                        imageUri = imageUri,
                        vendorId = vendorId,
                        expiryDateMillis = expiryMillis
                    )
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
                onAddVendor = { name, phone, gst -> repo.addVendor(name, phone, gst) }
            )
        }
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
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun InventoryItemCardSelectable(
    item: ItemEntity,
    selectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
    ) {
        InventoryItemCard(item = item, onClick = onClick)
        if (selectionMode) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onClick() },
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
            )
        }
    }
}

@Composable
fun InventoryItemCard(
    item: ItemEntity,
    onClick: () -> Unit
) {
    KiranaCard(onClick = onClick) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(modifier = Modifier.weight(1f)) {
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
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Inventory2, contentDescription = null, tint = Gray400)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "${item.category} • ${item.rackLocation ?: "No Loc"}",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            val lowStock = item.stock < item.reorderPoint
                            Badge(
                                text = "${item.stock} in Stock",
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
                Text("₹${item.price.toInt()}", fontWeight = FontWeight.Black, fontSize = 18.sp, color = TextPrimary)
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
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = color)
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun AddItemDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, Double, Double, Int, String?, String?, Double?, Int?, String?, Int?, Long?) -> Unit,
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
    onAddVendor: suspend (String, String, String?) -> PartyEntity?
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

    // If this barcode already exists in DB, prefill the form to "edit-like" behavior.
    LaunchedEffect(existingItem?.id) {
        val item = existingItem ?: return@LaunchedEffect
        name = item.name
        category = item.category
        barcode = item.barcode ?: barcode
        costPrice = item.costPrice.toString()
        sellingPrice = item.price.toString()
        stock = item.stock.toString()
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
        if (categoryQuery.isBlank()) categories
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

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxSize().padding(16.dp).imePadding(),
            shape = RoundedCornerShape(24.dp),
            color = White
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
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
                            DropdownMenuItem(
                                text = {
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
                                },
                                onClick = {},
                                enabled = false
                            )
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
                            DropdownMenuItem(
                                text = {
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
                                },
                                onClick = {},
                                enabled = false
                            )
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
                        KiranaInput(value = costPrice, onValueChange = { costPrice = it }, placeholder = "0", label = "Cost Price (₹)", modifier = Modifier.weight(1f))
                        KiranaInput(value = sellingPrice, onValueChange = { sellingPrice = it }, placeholder = "0", label = "Selling Price (₹)", modifier = Modifier.weight(1f))
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        KiranaInput(value = stock, onValueChange = { stock = it }, placeholder = "0", label = "Stock", modifier = Modifier.weight(1f))
                        KiranaInput(value = gst, onValueChange = { gst = it }, placeholder = "e.g. 18", label = "GST % (Optional)", modifier = Modifier.weight(1f))
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        // Location input added
                        KiranaInput(value = location, onValueChange = { location = it }, placeholder = "Rack A1", label = "Location", modifier = Modifier.weight(1f))
                        KiranaInput(value = reorderPoint, onValueChange = { reorderPoint = it }, placeholder = "10", label = "Reorder Point", modifier = Modifier.weight(1f))
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
                            onSave(
                                name,
                                category,
                                costPrice.toDoubleOrNull() ?: 0.0,
                                sellingPrice.toDoubleOrNull() ?: 0.0,
                                stock.toIntOrNull() ?: 0,
                                location,
                                barcode,
                                gst.toDoubleOrNull(),
                                reorderPoint.toIntOrNull(),
                                imageUri,
                                selectedVendorId,
                                expiryDateMillis
                            )
                        },
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
            DatePicker(state = pickerState, showModeToggle = false)
        }
    }

    if (showAddVendorDialog) {
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
                        onValueChange = { newVendorPhone = it },
                        label = { Text("Phone") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
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
