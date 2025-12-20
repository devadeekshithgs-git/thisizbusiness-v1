package com.kiranaflow.app.ui.screens.transactions

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kiranaflow.app.ui.components.dialogs.DateRangePickerDialog
import com.kiranaflow.app.ui.theme.*
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun TransactionsScreen(
    onBack: () -> Unit,
    onOpenTransaction: (Int) -> Unit = {},
    viewModel: TransactionsViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    var showDatePicker by remember { mutableStateOf(false) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var filtersExpanded by rememberSaveable { mutableStateOf(false) }

    val chipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = Blue50,
        selectedLabelColor = Blue600,
        selectedLeadingIconColor = Blue600
    )

    Column(modifier = Modifier.fillMaxSize().background(GrayBg)) {
        CenterAlignedTopAppBar(
            title = {
                if (selectionMode) {
                    Text("${selectedIds.size} selected", fontWeight = FontWeight.Black)
                } else {
                    Text("All Transactions", fontWeight = FontWeight.Black)
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                if (selectionMode) {
                    IconButton(
                        onClick = { showDeleteConfirm = true },
                        enabled = selectedIds.isNotEmpty()
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                    IconButton(
                        onClick = {
                            selectionMode = false
                            selectedIds = emptySet()
                        }
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close selection")
                    }
                } else {
                    IconButton(onClick = { selectionMode = true }) {
                        Icon(Icons.Default.Checklist, contentDescription = "Select")
                    }
                }
            },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = BgPrimary)
        )

        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                placeholder = { Text("Search by customer/vendor, product, title...") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = White,
                    unfocusedContainerColor = White,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "${state.results.size} result${if (state.results.size == 1) "" else "s"}",
                color = TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            if (state.unsyncedTxIds.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Sync pending: ${state.unsyncedTxIds.size}",
                    color = AlertOrange,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Filter strip
            Card(
                colors = CardDefaults.cardColors(containerColor = BgPrimary),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { filtersExpanded = !filtersExpanded },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("FILTERS", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (filtersExpanded) "Hide" else "Show",
                                color = Blue600,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = if (filtersExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = Blue600
                            )
                        }
                    }

                    AnimatedVisibility(visible = filtersExpanded) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val chipRow = rememberScrollState()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(chipRow),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FilterChip(
                            selected = state.partyType == "ALL",
                            onClick = { viewModel.setPartyType("ALL") },
                            label = { Text("All") },
                            colors = chipColors
                        )
                        FilterChip(
                            selected = state.partyType == "CUSTOMER",
                            onClick = { viewModel.setPartyType("CUSTOMER") },
                            label = { Text("Customers") },
                            colors = chipColors
                        )
                        FilterChip(
                            selected = state.partyType == "VENDOR",
                            onClick = { viewModel.setPartyType("VENDOR") },
                            label = { Text("Vendors") },
                            colors = chipColors
                        )
                    }

                    val chipRow2 = rememberScrollState()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(chipRow2),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FilterChip(
                            selected = state.paymentMode == "ALL",
                            onClick = { viewModel.setPaymentMode("ALL") },
                            label = { Text("Any") },
                            colors = chipColors
                        )
                        FilterChip(
                            selected = state.paymentMode == "CASH",
                            onClick = { viewModel.setPaymentMode("CASH") },
                            label = { Text("Cash") },
                            colors = chipColors
                        )
                        FilterChip(
                            selected = state.paymentMode == "UPI",
                            onClick = { viewModel.setPaymentMode("UPI") },
                            label = { Text("UPI") },
                            colors = chipColors
                        )
                        FilterChip(
                            selected = state.paymentMode == "CREDIT",
                            onClick = { viewModel.setPaymentMode("CREDIT") },
                            label = { Text("Udhaar") },
                            colors = chipColors
                        )
                    }

                    // Quick date range chips (in addition to Custom picker)
                    val dayMs = 24L * 60L * 60L * 1000L
                    val now = System.currentTimeMillis()
                    val startToday = remember {
                        val zone = ZoneId.systemDefault()
                        LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
                    }
                    val dateChipRow = rememberScrollState()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(dateChipRow),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FilterChip(
                            selected = state.dateRange?.first == startToday,
                            onClick = { viewModel.setDateRange(startToday to now) },
                            label = { Text("Today") },
                            colors = chipColors
                        )
                        FilterChip(
                            selected = state.dateRange?.first == (now - 7L * dayMs),
                            onClick = { viewModel.setDateRange((now - 7L * dayMs) to now) },
                            label = { Text("7D") },
                            colors = chipColors
                        )
                        FilterChip(
                            selected = state.dateRange?.first == (now - 30L * dayMs),
                            onClick = { viewModel.setDateRange((now - 30L * dayMs) to now) },
                            label = { Text("1M") },
                            colors = chipColors
                        )
                        FilterChip(
                            selected = state.dateRange?.first == (now - 90L * dayMs),
                            onClick = { viewModel.setDateRange((now - 90L * dayMs) to now) },
                            label = { Text("3M") },
                            colors = chipColors
                        )
                        FilterChip(
                            selected = state.dateRange?.first == (now - 180L * dayMs),
                            onClick = { viewModel.setDateRange((now - 180L * dayMs) to now) },
                            label = { Text("6M") },
                            colors = chipColors
                        )
                        FilterChip(
                            selected = state.dateRange?.first == (now - 365L * dayMs),
                            onClick = { viewModel.setDateRange((now - 365L * dayMs) to now) },
                            label = { Text("1Y") },
                            colors = chipColors
                        )
                        AssistChip(
                            onClick = { showDatePicker = true },
                            label = { Text("Custom") },
                            leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null) }
                        )
                    }

                    OutlinedTextField(
                        value = state.productQuery,
                        onValueChange = viewModel::setProductQuery,
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Inventory2, contentDescription = null) },
                        placeholder = { Text("Filter by product bought") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = White,
                            unfocusedContainerColor = White,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val df = remember { SimpleDateFormat("dd MMM yy", Locale.getDefault()) }
                        val dateLabel = if (state.dateRange == null) {
                            "Any date"
                        } else {
                            val (s, e) = state.dateRange!!
                            "${df.format(Date(minOf(s, e)))} → ${df.format(Date(maxOf(s, e)))}"
                        }
                        Text(dateLabel, color = if (state.dateRange == null) TextSecondary else TextPrimary, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { showDatePicker = true }) { Text("Pick") }
                            if (state.dateRange != null) {
                                TextButton(onClick = viewModel::clearDateRange) { Text("Clear") }
                            }
                        }
                    }

                    if (state.query.isNotBlank() || state.productQuery.isNotBlank() || state.partyType != "ALL" || state.paymentMode != "ALL" || state.dateRange != null) {
                        TextButton(
                            onClick = {
                                viewModel.setQuery("")
                                viewModel.setProductQuery("")
                                viewModel.setPartyType("ALL")
                                viewModel.setPaymentMode("ALL")
                                viewModel.clearDateRange()
                            },
                            contentPadding = PaddingValues(0.dp)
                        ) { Text("Clear all filters", color = Blue600, fontWeight = FontWeight.Bold) }
                    }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.results.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(56.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No transactions found", fontWeight = FontWeight.Bold, color = TextPrimary)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Try clearing filters or changing your search.", color = TextSecondary, fontSize = 12.sp)
                    }
                }
            }
            items(state.results, key = { it.tx.id }) { row ->
                val isSelected = selectedIds.contains(row.tx.id)
                TransactionExplorerCard(
                    row = row,
                    selectionMode = selectionMode,
                    isSelected = isSelected,
                    isUnsynced = state.unsyncedTxIds.contains(row.tx.id),
                    onClick = {
                        if (selectionMode) {
                            selectedIds = if (isSelected) selectedIds - row.tx.id else selectedIds + row.tx.id
                        } else {
                            onOpenTransaction(row.tx.id)
                        }
                    },
                    onLongPress = {
                        if (!selectionMode) selectionMode = true
                        selectedIds = selectedIds + row.tx.id
                    }
                )
            }
        }
    }

    if (showDatePicker) {
        DateRangePickerDialog(
            onDismiss = { showDatePicker = false },
            onApply = { start, end ->
                viewModel.setDateRange(start to end)
                showDatePicker = false
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete transactions?", fontWeight = FontWeight.Bold) },
            text = { Text("This will permanently delete ${selectedIds.size} transaction(s).") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        viewModel.deleteTransactions(selectedIds)
                        selectionMode = false
                        selectedIds = emptySet()
                    }
                ) { Text("Delete", color = LossRed) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } }
        )
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun TransactionExplorerCard(
    row: TransactionRow,
    selectionMode: Boolean,
    isSelected: Boolean,
    isUnsynced: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    val tx = row.tx
    val typeColor = when (tx.type) {
        "SALE" -> ProfitGreen
        "EXPENSE" -> LossRed
        "INCOME" -> Blue600
        else -> TextSecondary
    }
    val typeBg = when (tx.type) {
        "SALE" -> ProfitGreenBg
        "EXPENSE" -> LossRedBg
        "INCOME" -> Blue50
        else -> Gray100
    }
    val typeIcon = when (tx.type) {
        "SALE" -> Icons.Default.ReceiptLong
        "EXPENSE" -> Icons.Default.ArrowDownward
        "INCOME" -> Icons.Default.ArrowUpward
        else -> Icons.Default.SyncAlt
    }
    val partyIcon = when (row.party?.type) {
        "CUSTOMER" -> Icons.Default.Person
        "VENDOR" -> Icons.Default.Store
        else -> Icons.Default.Business
    }
    val partyColor = when (row.party?.type) {
        "CUSTOMER" -> Purple600
        "VENDOR" -> Blue600
        else -> TextSecondary
    }
    val sign = if (tx.type == "EXPENSE") "-" else "+"
    val amountColor = if (tx.type == "EXPENSE") LossRed else ProfitGreen

    Card(
        colors = CardDefaults.cardColors(containerColor = BgPrimary),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress
            )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(typeBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(typeIcon, contentDescription = null, tint = typeColor)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(tx.title, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TypePill(label = tx.type, bg = typeBg, fg = typeColor)
                        if (isUnsynced) {
                            TypePill(label = "Sync", bg = AlertOrangeBg, fg = AlertOrange)
                        }
                        if (row.party != null) {
                            PillWithIcon(icon = partyIcon, label = row.party.name, fg = partyColor)
                        }
                        TypePill(label = tx.paymentMode, bg = Gray100, fg = TextSecondary)
                    }
                }
                Text(
                    text = "$sign₹${kotlin.math.abs(tx.amount).toInt()}",
                    fontWeight = FontWeight.Black,
                    color = amountColor
                )
                if (selectionMode) {
                    Spacer(modifier = Modifier.width(10.dp))
                    Checkbox(checked = isSelected, onCheckedChange = { onClick() })
                }
            }

            val itemsLine = row.items.takeIf { it.isNotEmpty() }?.joinToString(", ") { "${it.itemNameSnapshot}×${it.qty}" }
            if (!itemsLine.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(itemsLine, color = TextSecondary, fontSize = 12.sp, maxLines = 2)
            }
        }
    }
}

@Composable
private fun TypePill(label: String, bg: androidx.compose.ui.graphics.Color, fg: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(label, color = fg, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PillWithIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, fg: androidx.compose.ui.graphics.Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Gray100)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}


