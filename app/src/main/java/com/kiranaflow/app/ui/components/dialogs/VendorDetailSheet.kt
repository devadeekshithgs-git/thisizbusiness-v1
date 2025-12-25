package com.kiranaflow.app.ui.components.dialogs

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import com.kiranaflow.app.data.local.TransactionEntity
import com.kiranaflow.app.data.local.VendorEntity
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
import com.kiranaflow.app.util.UpiIntentHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VendorDetailSheet(
    vendor: VendorEntity,
    transactions: List<TransactionEntity>,
    transactionItemsByTxId: Map<Int, List<TransactionItemEntity>> = emptyMap(),
    onDismiss: () -> Unit,
    onSavePayment: (Double, String) -> Unit,
    onUpdateUpiId: (String) -> Unit = {},
    onOpenTransaction: (Int) -> Unit = {}
) {
    var amountText by remember { mutableStateOf("") }
    var selectedPaymentMethod by remember { mutableStateOf("CASH") }
    var saving by remember { mutableStateOf(false) }
    var upiIdText by remember(vendor.id) { mutableStateOf(vendor.upiId.orEmpty()) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = BgPrimary,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        // Use single LazyColumn for entire content to support landscape scrolling
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .navigationBarsPadding()
                .imePadding(),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            vendor.name,
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
                            ),
                            color = TextPrimary
                        )
                        Text(
                            vendor.phone,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 14.sp,
                                color = TextSecondary
                            )
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                    }
                }
            }

            // Current Balance Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = BgCard)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        val dueAmount = -vendor.balance
                        Text(
                            "CURRENT BALANCE",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            if (dueAmount > 0) "You owe ₹${dueAmount.toInt()}" else "No dues",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 24.sp,
                                color = if (dueAmount > 0) LossRed else TextPrimary
                            )
                        )
                    }
                }
            }

            // Record Payment Section
            item {
                Text(
                    "RECORD PAYMENT",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                )
            }

            // Vendor UPI (optional)
            item {
                val upi = vendor.upiId?.trim().orEmpty()
                if (upi.isNotBlank()) {
                    Text(
                        "UPI: $upi",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    )
                } else {
                    Text(
                        "UPI ID not set for this vendor (add it in Edit Vendor to enable UPI redirect).",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    )
                }
            }

            // Inline UPI ID edit (simple and optional)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    KiranaInput(
                        value = upiIdText,
                        onValueChange = { upiIdText = it },
                        placeholder = "Vendor UPI ID (e.g. vendor@upi)",
                        label = "UPI ID",
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            onUpdateUpiId(upiIdText)
                            runCatching {
                                Toast.makeText(context, "UPI ID saved", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BgCard, contentColor = TextPrimary)
                    ) {
                        Text("Save", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            
            // Amount Input
            item {
                KiranaInput(
                    value = amountText,
                    onValueChange = { amountText = InputFilters.decimal(it) },
                    placeholder = "₹ Amount",
                    label = "AMOUNT",
                    keyboardType = KeyboardType.Decimal
                )
            }

            // Payment Method Selection
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { selectedPaymentMethod = "CASH" },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedPaymentMethod == "CASH") ProfitGreen else BgCard
                        )
                    ) {
                        Text("Cash", color = if (selectedPaymentMethod == "CASH") BgPrimary else TextPrimary)
                    }
                    Button(
                        onClick = { selectedPaymentMethod = "UPI" },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedPaymentMethod == "UPI") ProfitGreen else BgCard
                        )
                    ) {
                        Text("UPI", color = if (selectedPaymentMethod == "UPI") BgPrimary else TextPrimary)
                    }
                }
            }

            // Save Transaction Button
            item {
                KiranaButton(
                    text = if (saving) "Saved" else "Save Transaction",
                    onClick = {
                        if (saving) return@KiranaButton
                        val amount = amountText.toDoubleOrNull() ?: 0.0
                        if (amount > 0) {
                            saving = true

                            // If paying via UPI, open UPI app (best-effort) before recording.
                            if (selectedPaymentMethod == "UPI") {
                                val vpa = vendor.upiId?.trim().orEmpty()
                                if (vpa.isNotBlank()) {
                                    UpiIntentHelper.openUpiPay(
                                        context = context,
                                        vpa = vpa,
                                        payeeName = vendor.name,
                                        amountInr = amount.toInt().coerceAtLeast(1)
                                    )
                                } else {
                                    runCatching {
                                        Toast.makeText(context, "Add vendor UPI ID to redirect to UPI app", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }

                            onSavePayment(amount, selectedPaymentMethod)
                            amountText = ""
                            runCatching {
                                Toast.makeText(context, "Payment recorded", Toast.LENGTH_SHORT).show()
                            }
                            scope.launch {
                                delay(800)
                                saving = false
                            }
                        }
                    },
                    enabled = !saving,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Blue600,
                        contentColor = BgPrimary
                    )
                )
            }

            // Transaction History Header
            item {
                Text(
                    "TRANSACTION HISTORY",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                )
            }

            // Transaction items
            items(transactions, key = { it.id }) { transaction ->
                val lines = transactionItemsByTxId[transaction.id].orEmpty()
                VendorTransactionCard(
                    transaction = transaction,
                    items = lines,
                    onOpenTransaction = { onOpenTransaction(transaction.id) }
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun VendorTransactionCard(
    transaction: TransactionEntity,
    items: List<TransactionItemEntity>,
    onOpenTransaction: () -> Unit
) {
    val isExpense = transaction.type == "EXPENSE"
    val isPayment = isExpense && transaction.title.startsWith("Payment to", ignoreCase = true)
    val amountColor = if (isExpense) LossRed else ProfitGreen

    Card(
        modifier = Modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = BgPrimary),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        onClick = onOpenTransaction
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isPayment) "Payment" else "Purchase",
                        fontWeight = FontWeight.Black,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${transaction.paymentMode} • ${transaction.time}",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
                Text(
                    text = "₹${kotlin.math.abs(transaction.amount).toInt()}",
                    fontWeight = FontWeight.Black,
                    color = amountColor
                )
            }

            // Itemized lines (when available)
            if (!isPayment && items.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items.take(6).forEach { li ->
                        val lineTotal = li.price * li.qty
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = li.itemNameSnapshot,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary,
                                    maxLines = 1
                                )
                                Text(
                                    text = "${li.qty} ${li.unit} × ₹${li.price.toInt()}",
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                            }
                            Text(
                                text = "₹${lineTotal.toInt()}",
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }
                    }
                    if (items.size > 6) {
                        Text(
                            text = "… +${items.size - 6} more items",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }
            } else if (transaction.title.isNotBlank()) {
                // Fallback: show transaction title for non-itemized purchases/expenses
                Text(
                    text = transaction.title,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 2
                )
            }

            Text(
                text = "Tap to open full details",
                color = TextSecondary.copy(alpha = 0.8f),
                fontSize = 11.sp
            )
        }
    }
}
