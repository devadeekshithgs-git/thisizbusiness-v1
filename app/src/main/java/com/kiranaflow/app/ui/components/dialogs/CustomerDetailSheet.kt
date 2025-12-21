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
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerDetailSheet(
    customer: CustomerEntity,
    transactions: List<TransactionEntity>,
    transactionItemsByTxId: Map<Int, List<TransactionItemEntity>> = emptyMap(),
    onDismiss: () -> Unit,
    onSavePayment: (Double, String) -> Unit
) {
    var showPaymentForm by remember { mutableStateOf(false) }
    var amountText by remember { mutableStateOf("") }
    var selectedPaymentMethod by remember { mutableStateOf("CASH") }
    var saving by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Filter transactions for this customer (SALE + INCOME payment from customer).
    // (Previously had a logically unreachable branch that IDE correctly warned about.)
    val customerTransactions = transactions
        .asSequence()
        .filter { tx -> tx.customerId == customer.id && (tx.type == "SALE" || tx.type == "INCOME") }
        .sortedByDescending { it.date }
        .toList()

    // Group transactions by date
    val transactionsByDate = customerTransactions.groupBy { tx ->
        val cal = Calendar.getInstance().apply { timeInMillis = tx.date }
        SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(cal.time)
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
                        items = transactionItemsByTxId[tx.id] ?: emptyList()
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
    items: List<TransactionItemEntity>
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
            .clickable(enabled = hasItems) { isExpanded = !isExpanded },
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
                            contentDescription = if (isExpanded) "Collapse" else "Tap to see items",
                            tint = InteractiveCyan,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
            
            // Tap hint for collapsed state
            if (hasItems && !isExpanded) {
                Text(
                    "Tap to see items purchased",
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
                        val displayQty = if (item.unit == "GRAM" && item.qty >= 1000) {
                            "${item.qty / 1000.0}kg"
                        } else if (item.unit == "GRAM") {
                            "${item.qty}g"
                        } else {
                            "× ${item.qty}"
                        }
                        
                        val multiplier = if (item.unit == "GRAM") item.qty / 1000.0 else item.qty.toDouble()
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
