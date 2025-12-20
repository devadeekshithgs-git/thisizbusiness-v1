package com.kiranaflow.app.ui.components.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.kiranaflow.app.data.local.CustomerEntity
import com.kiranaflow.app.ui.components.KiranaButton
import com.kiranaflow.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompletePaymentModal(
    totalAmount: Double,
    customers: List<CustomerEntity>,
    selectedCustomerId: Int? = null,
    onDismiss: () -> Unit,
    onComplete: (Int?, String) -> Unit
) {
    var selectedPaymentMethod by remember { mutableStateOf("CASH") }
    var selectedCustomer by remember { mutableStateOf<CustomerEntity?>(null) }
    var showCustomerDropdown by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = BgPrimary)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Complete Payment",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        ),
                        color = TextPrimary
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Total Bill Amount
                Column {
                    Text(
                        "TOTAL BILL AMOUNT",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "₹${totalAmount.toInt()}",
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontWeight = FontWeight.Black,
                            fontSize = 32.sp,
                            color = TextPrimary
                        )
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Payment Mode Selection
                Text(
                    "SELECT PAYMENT MODE",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                )
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Cash
                    PaymentModeButton(
                        icon = Icons.Default.AttachMoney,
                        label = "Cash",
                        isSelected = selectedPaymentMethod == "CASH",
                        onClick = { selectedPaymentMethod = "CASH" },
                        modifier = Modifier.weight(1f),
                        backgroundColor = if (selectedPaymentMethod == "CASH") ProfitGreen else BgCard
                    )
                    
                    // UPI
                    PaymentModeButton(
                        icon = Icons.Default.PhoneAndroid,
                        label = "UPI",
                        isSelected = selectedPaymentMethod == "UPI",
                        onClick = { selectedPaymentMethod = "UPI" },
                        modifier = Modifier.weight(1f),
                        backgroundColor = if (selectedPaymentMethod == "UPI") ProfitGreen else BgCard
                    )
                    
                    // Credit
                    PaymentModeButton(
                        icon = Icons.Default.CreditCard,
                        label = "Credit",
                        isSelected = selectedPaymentMethod == "CREDIT",
                        onClick = { selectedPaymentMethod = "CREDIT" },
                        modifier = Modifier.weight(1f),
                        backgroundColor = if (selectedPaymentMethod == "CREDIT") ProfitGreen else BgCard
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Link Customer (Optional)
                Text(
                    "LINK CUSTOMER (OPTIONAL)",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                // Customer Dropdown
                ExposedDropdownMenuBox(
                    expanded = showCustomerDropdown,
                    onExpandedChange = { showCustomerDropdown = !showCustomerDropdown }
                ) {
                    OutlinedTextField(
                        value = selectedCustomer?.name ?: "",
                        onValueChange = { },
                        readOnly = true,
                        label = { Text("Select Customer...") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showCustomerDropdown) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = showCustomerDropdown,
                        onDismissRequest = { showCustomerDropdown = false }
                    ) {
                        customers.forEach { customer ->
                            DropdownMenuItem(
                                text = { Text(customer.name) },
                                onClick = {
                                    selectedCustomer = customer
                                    showCustomerDropdown = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Mark Paid & Close Button
                KiranaButton(
                    text = "✓ Mark Paid & Close",
                    onClick = {
                        onComplete(selectedCustomer?.id, selectedPaymentMethod)
                    },
                    icon = Icons.Default.Check,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ProfitGreen,
                        contentColor = BgPrimary
                    )
                )
            }
        }
    }
}

@Composable
fun PaymentModeButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: androidx.compose.ui.graphics.Color
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(80.dp),
        colors = ButtonDefaults.buttonColors(containerColor = backgroundColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (isSelected) BgPrimary else TextSecondary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    color = if (isSelected) BgPrimary else TextSecondary
                )
            )
        }
    }
}
