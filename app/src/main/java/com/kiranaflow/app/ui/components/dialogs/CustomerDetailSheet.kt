package com.kiranaflow.app.ui.components.dialogs

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiranaflow.app.data.local.CustomerEntity
import com.kiranaflow.app.data.local.TransactionEntity
import com.kiranaflow.app.data.local.TransactionItemEntity
import com.kiranaflow.app.ui.components.KiranaButton
import com.kiranaflow.app.ui.components.KiranaInput
import com.kiranaflow.app.ui.theme.*
import com.kiranaflow.app.util.InputFilters
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

private enum class CustomerTxTypeFilter { ALL, SALE, PAYMENT }
private enum class CustomerTxSort { LATEST_FIRST, OLDEST_FIRST }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerDetailSheet(
    customer: CustomerEntity,
    transactions: List<TransactionEntity>,
    transactionItemsByTxId: Map<Int, List<TransactionItemEntity>> = emptyMap(),
    onDismiss: () -> Unit,
    onSavePayment: (Double, String) -> Unit,
    onOpenTransaction: (Int) -> Unit = {}
) {
    var showPaymentForm by remember { mutableStateOf(false) }
    var amountText by remember { mutableStateOf("") }
    var selectedPaymentMethod by remember { mutableStateOf("CASH") }
    var saving by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // In-sheet filters/search/sort
    var txSearchQuery by remember { mutableStateOf("") }
    var productQuery by remember { mutableStateOf("") }
    var typeFilter by remember { mutableStateOf(CustomerTxTypeFilter.ALL) }
    var sortOrder by remember { mutableStateOf(CustomerTxSort.LATEST_FIRST) }
    var showDateRangePicker by remember { mutableStateOf(false) }
    val dateRangePickerState = rememberDateRangePickerState()

    // Filter transactions for this customer (SALE + INCOME payment from customer).
    // (Previously had a logically unreachable branch that IDE correctly warned about.)
    val baseCustomerTransactions = transactions
        .asSequence()
        .filter { tx -> tx.customerId == customer.id && (tx.type == "SALE" || tx.type == "INCOME") }
        .toList()

    fun endOfDay(millis: Long): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        return cal.timeInMillis
    }

    val filteredSortedTransactions = remember(
        baseCustomerTransactions,
        transactionItemsByTxId,
        txSearchQuery,
        productQuery,
        typeFilter,
        sortOrder,
        dateRangePickerState.selectedStartDateMillis,
        dateRangePickerState.selectedEndDateMillis
    ) {
        val q = txSearchQuery.trim()
        val pq = productQuery.trim()

        val start = dateRangePickerState.selectedStartDateMillis
        val end = dateRangePickerState.selectedEndDateMillis?.let(::endOfDay)

        fun matchesQuery(tx: TransactionEntity): Boolean {
            if (q.isBlank()) return true
            val items = transactionItemsByTxId[tx.id].orEmpty()
            return tx.title.contains(q, ignoreCase = true) ||
                tx.paymentMode.contains(q, ignoreCase = true) ||
                tx.time.contains(q, ignoreCase = true) ||
                items.any { it.itemNameSnapshot.contains(q, ignoreCase = true) }
        }

        fun matchesProduct(tx: TransactionEntity): Boolean {
            if (pq.isBlank()) return true
            val items = transactionItemsByTxId[tx.id].orEmpty()
            return items.any { it.itemNameSnapshot.contains(pq, ignoreCase = true) }
        }

        fun matchesType(tx: TransactionEntity): Boolean {
            return when (typeFilter) {
                CustomerTxTypeFilter.ALL -> true
                CustomerTxTypeFilter.SALE -> tx.type == "SALE"
                CustomerTxTypeFilter.PAYMENT -> tx.type == "INCOME"
            }
        }

        fun matchesDate(tx: TransactionEntity): Boolean {
            if (start == null || end == null) return true
            return tx.date in start..end
        }

        baseCustomerTransactions
            .asSequence()
            .filter(::matchesType)
            .filter(::matchesDate)
            .filter(::matchesQuery)
            .filter(::matchesProduct)
            .sortedWith(
                when (sortOrder) {
                    CustomerTxSort.LATEST_FIRST -> compareByDescending<TransactionEntity> { it.date }
                    CustomerTxSort.OLDEST_FIRST -> compareBy<TransactionEntity> { it.date }
                }
            )
            .toList()
    }

    // Group transactions by date, preserving the order of the already-sorted list.
    val transactionsByDate = remember(filteredSortedTransactions) {
        val fmt = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
        val map = linkedMapOf<String, MutableList<TransactionEntity>>()
        for (tx in filteredSortedTransactions) {
            val cal = Calendar.getInstance().apply { timeInMillis = tx.date }
            val key = fmt.format(cal.time)
            map.getOrPut(key) { mutableListOf() }.add(tx)
        }
        map
    }

    if (showDateRangePicker) {
        DatePickerDialog(
            onDismissRequest = { showDateRangePicker = false },
            confirmButton = {
                TextButton(onClick = { showDateRangePicker = false }) { Text("Apply") }
            },
            dismissButton = {
                TextButton(onClick = { showDateRangePicker = false }) { Text("Cancel") }
            }
        ) {
            DateRangePicker(state = dateRangePickerState)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = BgPrimary,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        // Use single LazyColumn for entire content to support landscape scrolling
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(),
            contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 32.dp)
        ) {
            // Header: Name • Khata
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .padding(bottom = 16.dp)
                ) {
                    Text(
                        "${customer.name} • Khata",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp
                        ),
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    if (customer.balance > 0) {
                        Text(
                            "Due: ₹${customer.balance.toInt().toString().reversed().chunked(3).joinToString(",").reversed()}",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp
                            ),
                            color = LossRed
                        )
                    } else {
                        Text(
                            "No dues",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp
                            ),
                            color = ProfitGreen
                        )
                    }
                }
            }

            // Search + filters (in-sheet)
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                ) {
                    KiranaInput(
                        value = txSearchQuery,
                        onValueChange = { txSearchQuery = it },
                        placeholder = "Search transactions...",
                        label = "SEARCH"
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    KiranaInput(
                        value = productQuery,
                        onValueChange = { productQuery = it },
                        placeholder = "Filter by product bought (e.g. Sugar)",
                        label = "PRODUCT"
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AssistChip(
                            onClick = { showDateRangePicker = true },
                            label = {
                                val start = dateRangePickerState.selectedStartDateMillis
                                val end = dateRangePickerState.selectedEndDateMillis
                                if (start == null || end == null) Text("Any time")
                                else {
                                    val df = SimpleDateFormat("d MMM", Locale.getDefault())
                                    Text("${df.format(Date(start))} - ${df.format(Date(end))}")
                                }
                            },
                            leadingIcon = { Icon(Icons.Default.CalendarMonth, contentDescription = null) }
                        )

                        var sortMenuOpen by remember { mutableStateOf(false) }
                        Box {
                            AssistChip(
                                onClick = { sortMenuOpen = true },
                                label = {
                                    Text(
                                        when (sortOrder) {
                                            CustomerTxSort.LATEST_FIRST -> "Latest first"
                                            CustomerTxSort.OLDEST_FIRST -> "Oldest first"
                                        }
                                    )
                                },
                                leadingIcon = { Icon(Icons.Default.FilterList, contentDescription = null) }
                            )
                            DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("Latest to oldest") },
                                    onClick = { sortOrder = CustomerTxSort.LATEST_FIRST; sortMenuOpen = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("Oldest to latest") },
                                    onClick = { sortOrder = CustomerTxSort.OLDEST_FIRST; sortMenuOpen = false }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = typeFilter == CustomerTxTypeFilter.ALL,
                            onClick = { typeFilter = CustomerTxTypeFilter.ALL },
                            label = { Text("All") }
                        )
                        FilterChip(
                            selected = typeFilter == CustomerTxTypeFilter.SALE,
                            onClick = { typeFilter = CustomerTxTypeFilter.SALE },
                            label = { Text("Sales") }
                        )
                        FilterChip(
                            selected = typeFilter == CustomerTxTypeFilter.PAYMENT,
                            onClick = { typeFilter = CustomerTxTypeFilter.PAYMENT },
                            label = { Text("Payments") }
                        )
                    }
                }
            }

            // Payment form (expandable)
            if (showPaymentForm) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = BgCard)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "RECORD PAYMENT",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            KiranaInput(
                                value = amountText,
                                onValueChange = { amountText = InputFilters.decimal(it) },
                                placeholder = "₹ Amount",
                                label = "AMOUNT",
                                keyboardType = KeyboardType.Decimal
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { selectedPaymentMethod = "CASH" },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (selectedPaymentMethod == "CASH") ProfitGreen else BgPrimary
                                    )
                                ) {
                                    Text("Cash", color = if (selectedPaymentMethod == "CASH") BgPrimary else TextPrimary)
                                }
                                Button(
                                    onClick = { selectedPaymentMethod = "UPI" },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (selectedPaymentMethod == "UPI") ProfitGreen else BgPrimary
                                    )
                                ) {
                                    Text("UPI", color = if (selectedPaymentMethod == "UPI") BgPrimary else TextPrimary)
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            KiranaButton(
                                text = if (saving) "Saved" else "Save Payment",
                                onClick = {
                                    if (saving) return@KiranaButton
                                    val amount = amountText.toDoubleOrNull() ?: 0.0
                                    if (amount > 0) {
                                        saving = true
                                        onSavePayment(amount, selectedPaymentMethod)
                                        amountText = ""
                                        runCatching {
                                            Toast.makeText(context, "Payment recorded", Toast.LENGTH_SHORT).show()
                                        }
                                        scope.launch {
                                            delay(800)
                                            saving = false
                                            showPaymentForm = false
                                        }
                                    }
                                },
                                enabled = !saving,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ProfitGreen,
                                    contentColor = BgPrimary
                                )
                            )
                        }
                    }
                }
            }

            // Transaction History
            if (filteredSortedTransactions.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = BgCard)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("No transactions match your filters", fontWeight = FontWeight.Bold, color = TextPrimary)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Try clearing search, product filter, or date range.", color = TextSecondary, fontSize = 12.sp)
                        }
                    }
                }
            }

            transactionsByDate.forEach { (dateStr, txList) ->
                // Highlighted Date header with bright background
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(InteractiveCyan.copy(alpha = 0.15f))
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Text(
                            dateStr,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            ),
                            color = InteractiveCyan
                        )
                    }
                }

                // Transaction cards for this date
                items(txList) { tx ->
                    ExpandableTransactionCard(
                        transaction = tx,
                        items = transactionItemsByTxId[tx.id] ?: emptyList(),
                        onOpen = { onOpenTransaction(tx.id) }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }

            // Bottom buttons: Close and Record Payment
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            "Close",
                            color = Blue600,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp
                        )
                    }
                    TextButton(
                        onClick = { showPaymentForm = !showPaymentForm },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            "Record Payment",
                            color = Blue600,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpandableTransactionCard(
    transaction: TransactionEntity,
    items: List<TransactionItemEntity>,
    onOpen: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    
    val isSale = transaction.type == "SALE"
    val isPayment = transaction.type == "INCOME"
    val hasItems = isSale && items.isNotEmpty()
    
    // Determine display type
    val displayType = when {
        isSale -> "Sale"
        isPayment -> "Payment"
        else -> transaction.type
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                // Always open full transaction details on tap.
                onOpen()
            },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isExpanded && hasItems) Blue50 else BgPrimary
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isExpanded) 4.dp else 1.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Icon indicator for sale with items
                    if (hasItems) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(InteractiveCyan.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.ShoppingCart,
                                contentDescription = null,
                                tint = InteractiveCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    
                    Column {
                        // Type with item count hint
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                displayType,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 16.sp
                                ),
                                color = TextPrimary
                            )
                            if (hasItems) {
                                Text(
                                    "(${items.size} items)",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 12.sp
                                    ),
                                    color = InteractiveCyan
                                )
                            }
                        }
                        // Payment method • Time
                        Text(
                            "${transaction.paymentMode} • ${transaction.time}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp
                            ),
                            color = TextSecondary
                        )
                    }
                }
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.End) {
                        // Amount
                        Text(
                            "₹${transaction.amount.toInt().toString().reversed().chunked(3).joinToString(",").reversed()}",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            ),
                            color = if (isPayment) ProfitGreen else TextPrimary
                        )
                        // Status for sales
                        if (isSale) {
                            val isCredit = transaction.paymentMode == "CREDIT"
                            Text(
                                if (isCredit) "CREDIT" else "PAID",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                ),
                                color = if (isCredit) LossRed else ProfitGreen
                            )
                        }
                    }
                    
                    // Expand/collapse icon for transactions with items
                    if (hasItems) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isExpanded) "Collapse" else "Items available",
                            tint = InteractiveCyan,
                            modifier = Modifier
                                .size(24.dp)
                                .clickable { isExpanded = !isExpanded }
                        )
                    }
                }
            }
            
            // Tap hint for collapsed state
            if (hasItems && !isExpanded) {
                Text(
                    "Tap to view full bill",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.sp
                    ),
                    color = TextSecondary.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            // Expandable Item list for sales
            AnimatedVisibility(
                visible = isExpanded && hasItems,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    Divider(
                        color = InteractiveCyan.copy(alpha = 0.3f),
                        thickness = 1.dp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    Text(
                        "ITEMS PURCHASED",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            letterSpacing = 1.sp
                        ),
                        color = InteractiveCyan,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    items.forEachIndexed { index, item ->
                        fun formatKgShort(kg: Double): String {
                            if (kg <= 0.0) return "0kg"
                            val txt = if (kg % 1.0 == 0.0) kg.toInt().toString()
                            else String.format("%.3f", kg).trimEnd('0').trimEnd('.')
                            return "${txt}kg"
                        }

                        val unitUp = item.unit.trim().uppercase()
                        val qtyKg = when (unitUp) {
                            "KG", "KGS" -> item.qty
                            "GRAM", "G", "GM", "GMS", "GRAMS" -> item.qty / 1000.0
                            else -> item.qty
                        }
                        val displayQty = if (unitUp == "KG" || unitUp == "KGS") {
                            "× ${formatKgShort(item.qty)}"
                        } else if (unitUp in setOf("GRAM", "G", "GM", "GMS", "GRAMS")) {
                            "× ${formatKgShort(qtyKg)}"
                        } else {
                            "× ${item.qty.toInt()}"
                        }

                        val multiplier = if (unitUp == "KG" || unitUp == "KGS") item.qty else if (unitUp in setOf("GRAM", "G", "GM", "GMS", "GRAMS")) qtyKg else item.qty
                        val itemTotal = item.price * multiplier
                        
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = BgPrimary),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    // Item number badge
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(InteractiveCyan.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "${index + 1}",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp
                                            ),
                                            color = InteractiveCyan
                                        )
                                    }
                                    
                                    Column {
                                        Text(
                                            item.itemNameSnapshot,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.Medium,
                                                fontSize = 14.sp
                                            ),
                                            color = TextPrimary
                                        )
                                        Text(
                                            displayQty,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontSize = 12.sp
                                            ),
                                            color = TextSecondary
                                        )
                                    }
                                }
                                
                                Text(
                                    "₹${itemTotal.toInt()}",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp
                                    ),
                                    color = TextPrimary
                                )
                            }
                        }
                    }
                    
                    // Total row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, start = 4.dp, end = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Total",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            ),
                            color = TextPrimary
                        )
                        Text(
                            "₹${transaction.amount.toInt()}",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            ),
                            color = ProfitGreen
                        )
                    }
                }
            }
        }
    }
}
