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
import com.kiranaflow.app.ui.theme.*
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
            KiranaInput(
                value = query,
                onValueChange = { query = it },
                placeholder = "Search items...",
                icon = Icons.Default.Search
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
            KiranaInput(
                value = query,
                onValueChange = { query = it },
                placeholder = "Search vendors...",
                icon = Icons.Default.Search
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
}

@Composable
private fun PayableVendorRow(
    v: PartyEntity,
    lastDueNote: String?,
    onRemind: () -> Unit
    ,
    onPay: () -> Unit
) {
    val payable = kotlin.math.abs(v.balance)
    Card(
        colors = CardDefaults.cardColors(containerColor = BgPrimary),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
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
                        text = "Last due: $lastDueNote",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("₹${payable.toInt()}", fontWeight = FontWeight.Black, color = LossRed)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onPay, contentPadding = PaddingValues(0.dp)) {
                        Text("Pay", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    TextButton(onClick = onRemind, contentPadding = PaddingValues(0.dp)) {
                        Text("Remind", fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
    // Default: tomorrow 9 AM
    var dueAt by remember {
        val now = System.currentTimeMillis()
        mutableStateOf(now + TimeUnit.DAYS.toMillis(1))
    }

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = BgPrimary,
        dragHandle = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    modifier = Modifier.size(width = 40.dp, height = 4.dp),
                    shape = RoundedCornerShape(2.dp),
                    color = Gray100
                ) {}
                Spacer(modifier = Modifier.height(12.dp))
            }
        },
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Title
                Text(
                    text = title,
                    fontWeight = FontWeight.Black,
                    fontSize = 24.sp,
                    color = TextPrimary
                )
                
                Spacer(modifier = Modifier.height(8.dp))

                // Reminder Title Input
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Reminder Title",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
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

                // Note Input
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Note (optional)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
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

                // Due Date Display
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Set Due Date",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = TextSecondary
                    )
                    
                    Surface(
                        color = AlertOrange.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = java.text.SimpleDateFormat("EEEE, dd MMM yyyy • hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(dueAt)),
                                color = AlertOrange,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }

                    // Quick Date Selection Buttons - Responsive Row with FlowRow-like behavior
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
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
                                text = "Tomorrow",
                                isSelected = false,
                                onClick = { dueAt = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(1) },
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
            }

            // Bottom Action Buttons
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
                        text = "Save Reminder",
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


