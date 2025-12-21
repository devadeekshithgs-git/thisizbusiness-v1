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
import com.kiranaflow.app.ui.components.KiranaButton
import com.kiranaflow.app.ui.components.KiranaInput
import com.kiranaflow.app.ui.components.TransactionCard
import com.kiranaflow.app.ui.theme.*
import com.kiranaflow.app.util.InputFilters
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VendorDetailSheet(
    vendor: VendorEntity,
    transactions: List<TransactionEntity>,
    onDismiss: () -> Unit,
    onSavePayment: (Double, String) -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    var selectedPaymentMethod by remember { mutableStateOf("CASH") }
    var saving by remember { mutableStateOf(false) }
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
            items(transactions) { transaction ->
                TransactionCard(transaction = transaction)
            }
        }
    }
}
