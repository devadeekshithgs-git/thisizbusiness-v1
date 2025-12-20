package com.kiranaflow.app.ui.screens.vendors

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
    onRemind: () -> Unit
) {
    val low = item.stock < item.reorderPoint
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
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${item.stock} left", fontWeight = FontWeight.Black, color = if (low) LossRed else TextPrimary)
                Text("Reorder @ ${item.reorderPoint}", color = TextSecondary, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(6.dp))
                TextButton(onClick = onRemind, contentPadding = PaddingValues(0.dp)) {
                    Text("Remind", fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
    var query by remember { mutableStateOf("") }
    var reminderForVendor by remember { mutableStateOf<PartyEntity?>(null) }
    var showReminderDialog by remember { mutableStateOf(false) }

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
                    onRemind = {
                        reminderForVendor = v
                        showReminderDialog = true
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
}

@Composable
private fun PayableVendorRow(
    v: PartyEntity,
    onRemind: () -> Unit
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
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("₹${payable.toInt()}", fontWeight = FontWeight.Black, color = LossRed)
                Spacer(modifier = Modifier.height(6.dp))
                TextButton(onClick = onRemind, contentPadding = PaddingValues(0.dp)) {
                    Text("Remind", fontWeight = FontWeight.Bold, fontSize = 12.sp)
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = reminderTitle,
                    onValueChange = { reminderTitle = it },
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = White,
                        unfocusedContainerColor = White,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = White,
                        unfocusedContainerColor = White,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
                Text(
                    "Due: ${java.text.SimpleDateFormat("dd MMM, hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(dueAt))}",
                    color = TextSecondary,
                    fontWeight = FontWeight.Medium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { dueAt = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(2) },
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("In 2h") }
                    OutlinedButton(
                        onClick = { dueAt = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(1) },
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Tomorrow") }
                    OutlinedButton(
                        onClick = { dueAt = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(7) },
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("1 week") }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(reminderTitle, note.trim().ifBlank { null }, dueAt) },
                enabled = reminderTitle.trim().isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}


