package com.kiranaflow.app.ui.components.dialogs

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

    // Filter transactions for this customer (SALE with customerId or INCOME payment from customer)
    val customerTransactions = transactions.filter { tx ->
        (tx.customerId == customer.id) || 
        (tx.type == "INCOME" && tx.customerId == customer.id)
    }.sortedByDescending { it.date }

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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(bottom = 32.dp)
        ) {
            // Header: Name • Khata
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(top = 8.dp, bottom = 16.dp)
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

            // Payment form (expandable)
            if (showPaymentForm) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
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

            // Transaction History
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                transactionsByDate.forEach { (dateStr, txList) ->
                    // Date header
                    item {
                        Text(
                            dateStr,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            ),
                            color = TextPrimary,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }

                    // Transaction cards for this date
                    items(txList) { tx ->
                        CustomerTransactionCard(
                            transaction = tx,
                            items = transactionItemsByTxId[tx.id] ?: emptyList()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }

            // Bottom buttons: Close and Record Payment
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
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

@Composable
private fun CustomerTransactionCard(
    transaction: TransactionEntity,
    items: List<TransactionItemEntity>
) {
    val isSale = transaction.type == "SALE"
    val isPayment = transaction.type == "INCOME"
    val isPaid = transaction.paymentMode != "CREDIT"
    
    // Determine display type
    val displayType = when {
        isSale -> "Sale"
        isPayment -> "Payment"
        else -> transaction.type
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = BgCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    // Type
                    Text(
                        displayType,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp
                        ),
                        color = TextPrimary
                    )
                    // Payment method • Time
                    Text(
                        "${transaction.paymentMode} • ${transaction.time}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp
                        ),
                        color = TextSecondary
                    )
                }
                
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
                    if (isSale && isPaid) {
                        Text(
                            "PAID",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            ),
                            color = ProfitGreen
                        )
                    }
                }
            }

            // Item list for sales
            if (isSale && items.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                items.forEach { item ->
                    val displayQty = if (item.unit == "GRAM" && item.qty >= 1000) {
                        "${item.qty / 1000.0}kg"
                    } else if (item.unit == "GRAM") {
                        "${item.qty}g"
                    } else {
                        "× ${item.qty}"
                    }
                    Text(
                        "• ${item.itemNameSnapshot} $displayQty",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 13.sp
                        ),
                        color = TextSecondary,
                        modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                    )
                }
            }
        }
    }
}
