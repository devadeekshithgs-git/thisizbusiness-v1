package com.kiranaflow.app.ui.screens.vendors

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kiranaflow.app.data.local.ItemEntity
import com.kiranaflow.app.data.local.PartyEntity
import com.kiranaflow.app.ui.components.KiranaInput
import com.kiranaflow.app.ui.components.SearchField
import com.kiranaflow.app.ui.components.dialogs.VendorDetailSheet
import com.kiranaflow.app.ui.theme.*
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ItemsToReorderScreen(
    onBack: () -> Unit,
    viewModel: VendorKpiViewModel = viewModel()
) {
    val items by viewModel.lowStockItems.collectAsState()
    val vendorsById by viewModel.vendorsById.collectAsState()
    var query by remember { mutableStateOf("") }
    var reminderForItem by remember { mutableStateOf<ItemEntity?>(null) }
    var showReminderDialog by remember { mutableStateOf(false) }

    val filtered = remember(items, query) {
        val q = query.trim()
        if (q.isBlank()) items
        else items.filter { it.name.contains(q, true) || it.category.contains(q, true) || (it.rackLocation?.contains(q, true) == true) }
    }

    Column(modifier = Modifier.fillMaxSize().background(GrayBg)) {
        CenterAlignedTopAppBar(
            title = { Text("Items to Reorder", fontWeight = FontWeight.Black) },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
            },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = BgPrimary)
        )

        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            SearchField(
                query = query,
                onQueryChange = { query = it },
                placeholder = "Search items..."
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(filtered, key = { it.id }) { item ->
                ReorderItemRow(
                    item = item,
                    vendorName = item.vendorId?.let { vendorsById[it]?.name } ?: "No vendor",
                    onRemind = {
                        reminderForItem = item
                        showReminderDialog = true
                    }
                )
            }
        }
    }

    if (showReminderDialog && reminderForItem != null) {
        QuickReminderDialog(
            title = "Set reminder",
            defaultTitle = "Reorder: ${reminderForItem!!.name}",
            onDismiss = { showReminderDialog = false; reminderForItem = null },
            onSave = { title, note, dueAt ->
                viewModel.addReminder(type = "ITEM", refId = reminderForItem!!.id, title = title, note = note, dueAt = dueAt)
                showReminderDialog = false
                reminderForItem = null
            }
        )
    }
}

@Composable
private fun ReorderItemRow(
    item: ItemEntity,
    vendorName: String,
    onRemind: () -> Unit
) {
    val stockVal = if (item.isLoose) item.stockKg else item.stock.toDouble()
    val stockLabel = if (item.isLoose) "${String.format("%.2f", stockVal).trimEnd('0').trimEnd('.')} kg" else "${stockVal.toInt()} pcs"
    val low = stockVal < item.reorderPoint.toDouble()
    Card(
        colors = CardDefaults.cardColors(containerColor = BgPrimary),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = if (low) LossRedBg else Gray100,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Inventory2, contentDescription = null, tint = if (low) LossRed else TextSecondary)
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(item.name, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("${item.category} • ${item.rackLocation ?: "No loc"}", color = TextSecondary, fontSize = 12.sp)
                    Text("Vendor: $vendorName", color = TextSecondary, fontSize = 12.sp)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("$stockLabel left", fontWeight = FontWeight.Black, color = if (low) LossRed else TextPrimary)
                Text("Reorder @ ${item.reorderPoint}${if (item.isLoose) " kg" else ""}", color = TextSecondary, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(6.dp))
                TextButton(onClick = onRemind, contentPadding = PaddingValues(0.dp)) {
                    Text("Set by-when", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun TotalPayablesScreen(
    onBack: () -> Unit,
    viewModel: VendorKpiViewModel = viewModel()
) {
    val vendors by viewModel.vendorsWithPayables.collectAsState()
    val vendorTxById by viewModel.vendorTransactionsById.collectAsState()
    val txItemsByTxId by viewModel.transactionItemsByTxId.collectAsState()
    var query by remember { mutableStateOf("") }
    var reminderForVendor by remember { mutableStateOf<PartyEntity?>(null) }
    var showReminderDialog by remember { mutableStateOf(false) }
    var payVendor by remember { mutableStateOf<PartyEntity?>(null) }
    var detailVendor by remember { mutableStateOf<PartyEntity?>(null) }
    var payAmountText by remember { mutableStateOf("") }
    var payMode by remember { mutableStateOf("CASH") }
    var paying by remember { mutableStateOf(false) }

    val filtered = remember(vendors, query) {
        val q = query.trim()
        if (q.isBlank()) vendors
        else vendors.filter { it.name.contains(q, true) || it.phone.contains(q) || (it.gstNumber?.contains(q, true) == true) }
    }

    Column(modifier = Modifier.fillMaxSize().background(GrayBg)) {
        CenterAlignedTopAppBar(
            title = { Text("Total Payables", fontWeight = FontWeight.Black) },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
            },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = BgPrimary)
        )

        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            SearchField(
                query = query,
                onQueryChange = { query = it },
                placeholder = "Search vendors..."
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(filtered, key = { it.id }) { v ->
                PayableVendorRow(
                    v = v,
                    lastDueNote = run {
                        val recent = vendorTxById[v.id].orEmpty()
                        val lastDue = recent.firstOrNull { it.type == "EXPENSE" && it.paymentMode == "CREDIT" }
                        lastDue?.title
                    },
                    onOpenDetails = { detailVendor = v },
                    onRemind = {
                        reminderForVendor = v
                        showReminderDialog = true
                    },
                    onPay = {
                        payVendor = v
                        payAmountText = kotlin.math.abs(v.balance).toInt().toString()
                        payMode = "CASH"
                    }
                )
            }
        }
    }

    if (showReminderDialog && reminderForVendor != null) {
        QuickReminderDialog(
            title = "Set reminder",
            defaultTitle = "Pay vendor: ${reminderForVendor!!.name}",
            onDismiss = { showReminderDialog = false; reminderForVendor = null },
            onSave = { title, note, dueAt ->
                viewModel.addReminder(type = "VENDOR", refId = reminderForVendor!!.id, title = title, note = note, dueAt = dueAt)
                showReminderDialog = false
                reminderForVendor = null
            }
        )
    }

    // Payment sheet: partial/full settlement directly from Total Payables screen.
    if (payVendor != null) {
        val v = payVendor!!
        val duePurchases = remember(vendorTxById, v.id) {
            vendorTxById[v.id].orEmpty()
                .filter { it.type == "EXPENSE" && it.paymentMode == "CREDIT" }
                .sortedByDescending { it.date }
                .take(5)
        }
        AlertDialog(
            onDismissRequest = { if (!paying) payVendor = null },
            title = { Text("Settle Payable", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(v.name, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text("Current payable: ₹${kotlin.math.abs(v.balance).toInt()}", color = LossRed, fontWeight = FontWeight.Black)
                    OutlinedTextField(
                        value = payAmountText,
                        onValueChange = { payAmountText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Amount to pay (₹)") },
                        singleLine = true,
                        enabled = !paying,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = White,
                            unfocusedContainerColor = White,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilterChip(selected = payMode == "CASH", onClick = { payMode = "CASH" }, label = { Text("Cash") })
                        FilterChip(selected = payMode == "UPI", onClick = { payMode = "UPI" }, label = { Text("UPI") })
                    }
                    Text(
                        "Tip: enter partial amount to settle partially.",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )

                    if (duePurchases.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("What you owe (recent)", fontWeight = FontWeight.Black, color = TextPrimary)
                        duePurchases.forEach { tx ->
                            val lines = txItemsByTxId[tx.id].orEmpty()
                            Surface(color = Gray100, shape = RoundedCornerShape(12.dp)) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "₹${kotlin.math.abs(tx.amount).toInt()} • ${tx.time}",
                                        fontWeight = FontWeight.Bold,
                                        color = LossRed
                                    )
                                    if (lines.isNotEmpty()) {
                                        lines.take(4).forEach { li ->
                                            Text("• ${li.itemNameSnapshot} × ${li.qty}", color = TextSecondary, fontSize = 12.sp)
                                        }
                                        if (lines.size > 4) {
                                            Text("… +${lines.size - 4} more", color = TextSecondary, fontSize = 12.sp)
                                        }
                                    } else {
                                        Text(tx.title, color = TextSecondary, fontSize = 12.sp, maxLines = 2)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !paying && (payAmountText.toDoubleOrNull() ?: 0.0) > 0.0,
                    onClick = {
                        val amt = payAmountText.toDoubleOrNull() ?: 0.0
                        if (amt <= 0.0) return@TextButton
                        paying = true
                        viewModel.recordVendorPayment(v, amt, payMode)
                        paying = false
                        payVendor = null
                    }
                ) { Text(if (paying) "Saving..." else "Record Payment") }
            },
            dismissButton = {
                TextButton(enabled = !paying, onClick = { payVendor = null }) { Text("Cancel") }
            }
        )
    }

    // Vendor detail (what you owe for + full itemized history)
    if (detailVendor != null) {
        val v = detailVendor!!
        VendorDetailSheet(
            vendor = v,
            transactions = vendorTxById[v.id].orEmpty(),
            transactionItemsByTxId = txItemsByTxId,
            onDismiss = { detailVendor = null },
            onSavePayment = { amount, method ->
                viewModel.recordVendorPayment(v, amount, method)
            }
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun PayableVendorRow(
    v: PartyEntity,
    lastDueNote: String?,
    onOpenDetails: () -> Unit,
    onRemind: () -> Unit
    ,
    onPay: () -> Unit
) {
    val payable = kotlin.math.abs(v.balance)
    Card(
        colors = CardDefaults.cardColors(containerColor = BgPrimary),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
        onClick = onOpenDetails
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(v.name, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(v.phone, color = TextSecondary, fontSize = 12.sp)
                    if (!lastDueNote.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = lastDueNote,
                            color = TextSecondary,
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("PAYABLE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                    Text("₹${payable.toInt()}", fontWeight = FontWeight.Black, color = LossRed)
                }
            }

            // Bottom-center buttons (clearly visible)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Row(
                    modifier = Modifier.widthIn(max = 360.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onPay,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Blue600, contentColor = BgPrimary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Payable", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = onRemind,
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, Gray200),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary, containerColor = BgPrimary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Remind", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun QuickReminderDialog(
    title: String,
    defaultTitle: String,
    onDismiss: () -> Unit,
    onSave: (title: String, note: String?, dueAt: Long) -> Unit
) {
    var reminderTitle by remember { mutableStateOf(defaultTitle) }
    var note by remember { mutableStateOf("") }
    // Default: tomorrow 9 AM (local)
    var dueAt by remember {
        val now = System.currentTimeMillis()
        val tomorrowLocalDate = Instant.ofEpochMilli(now)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .plusDays(1)
        mutableStateOf(
            tomorrowLocalDate
                .atTime(9, 0)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        )
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = utcMidnightMillisForLocalDateFromEpochMillis(dueAt)
    )
    val initialTime = remember(dueAt) {
        val zdt = Instant.ofEpochMilli(dueAt).atZone(ZoneId.systemDefault())
        zdt.hour to zdt.minute
    }
    val timePickerState = rememberTimePickerState(
        initialHour = initialTime.first,
        initialMinute = initialTime.second,
        is24Hour = false
    )

    // Keep dueAt synced with picker selections.
    LaunchedEffect(datePickerState.selectedDateMillis, timePickerState.hour, timePickerState.minute) {
        val selectedDateUtcMillis = datePickerState.selectedDateMillis ?: return@LaunchedEffect
        dueAt = combineUtcDateMillisWithLocalTime(
            dateUtcMillis = selectedDateUtcMillis,
            hour = timePickerState.hour,
            minute = timePickerState.minute
        )
    }

    // Note: In the Compose Material3 version used by this project, DatePickerState/TimePickerState
    // selection fields are read-only (val). We keep `dueAt` as the source of truth and avoid
    // trying to programmatically force-sync picker state (it will sync via user interaction).

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = BgPrimary,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .navigationBarsPadding()
                .imePadding(),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header (match VendorDetailSheet style)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        fontWeight = FontWeight.Black,
                        fontSize = 20.sp,
                        color = TextPrimary
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                    }
                }
            }

            // Main highlight: Date + Time picker (always visible)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = AlertOrange.copy(alpha = 0.08f)),
                    border = BorderStroke(1.dp, AlertOrange.copy(alpha = 0.15f))
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "When should we remind you?",
                            fontWeight = FontWeight.Black,
                            color = TextPrimary
                        )

                        Surface(
                            color = AlertOrange.copy(alpha = 0.10f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = java.text.SimpleDateFormat(
                                    "EEEE, dd MMM yyyy • hh:mm a",
                                    java.util.Locale.getDefault()
                                ).format(java.util.Date(dueAt)),
                                color = AlertOrange,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                modifier = Modifier.padding(12.dp)
                            )
                        }

                        DatePicker(
                            state = datePickerState,
                            showModeToggle = false,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Divider(color = Gray100)

                        TimePicker(
                            state = timePickerState,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Quick presets (optional convenience)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Quick presets",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        QuickDateButton(
                            text = "In 2 hours",
                            isSelected = false,
                            onClick = { dueAt = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(2) },
                            modifier = Modifier.weight(1f)
                        )
                        QuickDateButton(
                            text = "Tomorrow 9am",
                            isSelected = false,
                            onClick = {
                                val now = System.currentTimeMillis()
                                val tomorrowLocalDate = Instant.ofEpochMilli(now)
                                    .atZone(ZoneId.systemDefault())
                                    .toLocalDate()
                                    .plusDays(1)
                                dueAt = tomorrowLocalDate
                                    .atTime(9, 0)
                                    .atZone(ZoneId.systemDefault())
                                    .toInstant()
                                    .toEpochMilli()
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        QuickDateButton(
                            text = "In 3 days",
                            isSelected = false,
                            onClick = { dueAt = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(3) },
                            modifier = Modifier.weight(1f)
                        )
                        QuickDateButton(
                            text = "In 1 week",
                            isSelected = false,
                            onClick = { dueAt = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(7) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Reminder Title Input
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Reminder title",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                    OutlinedTextField(
                        value = reminderTitle,
                        onValueChange = { reminderTitle = it },
                        placeholder = { Text("Enter reminder title") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = White,
                            unfocusedContainerColor = White,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = AlertOrange,
                            unfocusedBorderColor = Gray100
                        ),
                        textStyle = LocalTextStyle.current.copy(fontSize = 16.sp)
                    )
                }
            }

            // Note Input
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Note (optional)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        placeholder = { Text("Add a note...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 100.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = White,
                            unfocusedContainerColor = White,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = AlertOrange,
                            unfocusedBorderColor = Gray100
                        ),
                        textStyle = LocalTextStyle.current.copy(fontSize = 16.sp),
                        minLines = 3
                    )
                }
            }

            // Action buttons
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { onSave(reminderTitle, note.trim().ifBlank { null }, dueAt) },
                        enabled = reminderTitle.trim().isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AlertOrange,
                            contentColor = White,
                            disabledContainerColor = Gray100,
                            disabledContentColor = TextSecondary
                        )
                    ) {
                        Text(
                            text = "Save reminder",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }

                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = androidx.compose.ui.graphics.SolidColor(Gray100)
                        )
                    ) {
                        Text(
                            text = "Cancel",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = TextSecondary
                        )
                    }
                }
            }
        }
    }
}

private fun utcMidnightMillisForLocalDateFromEpochMillis(epochMillis: Long): Long {
    val localDate = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
    // DatePicker uses a UTC-millis representation for the selected day.
    return localDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
}

private fun combineUtcDateMillisWithLocalTime(dateUtcMillis: Long, hour: Int, minute: Int): Long {
    val selectedDate = Instant.ofEpochMilli(dateUtcMillis).atZone(ZoneOffset.UTC).toLocalDate()
    return selectedDate
        .atTime(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
        .atZone(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()
}

@Composable
private fun QuickDateButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) AlertOrange else White,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = if (isSelected) AlertOrange else Gray100
        )
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Text(
                text = text,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = if (isSelected) White else TextPrimary
            )
        }
    }
}


