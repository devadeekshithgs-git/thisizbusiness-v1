package com.kiranaflow.app.ui.components.dialogs

import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kiranaflow.app.data.local.CustomerEntity
import com.kiranaflow.app.ui.components.KiranaButton
import com.kiranaflow.app.ui.theme.*
import com.kiranaflow.app.util.InputFilters
import com.kiranaflow.app.util.QrCodeUtil
import com.kiranaflow.app.util.WhatsAppHelper
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.launch

/**
 * Bill item data for WhatsApp bill sharing
 */
data class BillItemData(
    val name: String,
    val qty: Int,
    val isLoose: Boolean,
    val unitPrice: Double,
    val lineTotal: Double
)

private enum class PaymentModalScreen {
    PAYMENT,
    WHATSAPP_SHARE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompletePaymentModal(
    totalAmount: Double,
    customers: List<CustomerEntity>,
    selectedCustomerId: Int? = null,
    shopName: String,
    upiId: String,
    receiptTemplate: String = "",
    billItems: List<BillItemData> = emptyList(),
    onDismiss: () -> Unit,
    onComplete: (Int?, String) -> Unit,
    onAddCustomer: suspend (name: String, phone10: String) -> CustomerEntity?
) {
    var selectedPaymentMethod by remember { mutableStateOf("CASH") }
    var selectedCustomer by remember { mutableStateOf<CustomerEntity?>(null) }
    var showCustomerDropdown by remember { mutableStateOf(false) }
    var showAddCustomerDialog by remember { mutableStateOf(false) }
    var newCustomerName by remember { mutableStateOf("") }
    var newCustomerPhone by remember { mutableStateOf("") }
    var addCustomerPhoneError by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    
    // Screen state for WhatsApp share flow
    var currentScreen by remember { mutableStateOf(PaymentModalScreen.PAYMENT) }

    val cfg = LocalConfiguration.current
    val density = LocalDensity.current
    val maxModalWidthFraction = 0.96f
    val modalHorizontalPadding = 14.dp

    // Preselect if an id is provided
    LaunchedEffect(selectedCustomerId, customers) {
        if (selectedCustomer == null && selectedCustomerId != null) {
            selectedCustomer = customers.firstOrNull { it.id == selectedCustomerId }
        }
    }

    val showUpiQr = selectedPaymentMethod == "UPI" && upiId.isNotBlank()
    val upiLink = remember(showUpiQr, upiId, shopName, totalAmount) {
        if (!showUpiQr) null
        else WhatsAppHelper.buildUpiLink(
            upiId = upiId,
            payeeName = shopName.ifBlank { "thisizbusiness" },
            amountInr = totalAmount.toInt().coerceAtLeast(1)
        )
    }

    // Make QR big and scannable: size driven by screen width.
    val qrSizeDp = remember(cfg.screenWidthDp) {
        val w = cfg.screenWidthDp.dp
        // Roughly ~60% of screen width, capped for tablets.
        (w * 0.60f).coerceIn(220.dp, 320.dp)
    }
    val qrBitmap = remember(upiLink, qrSizeDp) {
        val link = upiLink ?: return@remember null
        // Generate at display resolution for sharpness.
        val sizePx = with(density) { qrSizeDp.roundToPx().coerceAtLeast(480) }
        QrCodeUtil.createQrBitmap(content = link, sizePx = sizePx)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = modalHorizontalPadding, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(maxModalWidthFraction)
                    .heightIn(min = 320.dp, max = 720.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = BgPrimary)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp)
                ) {
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
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Add, contentDescription = null, tint = Blue600)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Add new customer", color = Blue600, fontWeight = FontWeight.Bold)
                                }
                            },
                            onClick = {
                                showCustomerDropdown = false
                                showAddCustomerDialog = true
                            }
                        )
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

                Spacer(modifier = Modifier.height(12.dp))
                if (showUpiQr) {
                    Text(
                        "UPI QR",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (upiLink.isNullOrBlank() || qrBitmap == null) {
                        Text(
                            "UPI QR not available. Please set your UPI ID in Settings.",
                            color = LossRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    } else {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = White,
                            tonalElevation = 0.dp
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Image(
                                    bitmap = qrBitmap.asImageBitmap(),
                                    contentDescription = "UPI QR code",
                                    modifier = Modifier.size(qrSizeDp),
                                    contentScale = ContentScale.FillBounds
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Scan to pay ₹${totalAmount.toInt()} via UPI",
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "UPI ID: ${upiId.trim()}",
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                } else if (selectedPaymentMethod == "UPI" && upiId.isBlank()) {
                    Text(
                        "UPI ID not set. Add it in Settings to show a scannable QR code.",
                        color = LossRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Mark Paid & Close Button
                KiranaButton(
                    text = "Mark Paid & Close",
                    onClick = {
                        // Check if customer is linked and has valid phone for WhatsApp share
                        val customer = selectedCustomer
                        val hasValidPhone = customer != null && customer.phone.length >= 10
                        
                        if (hasValidPhone && billItems.isNotEmpty()) {
                            // Show WhatsApp share screen
                            currentScreen = PaymentModalScreen.WHATSAPP_SHARE
                        } else {
                            // No customer or no valid phone, just complete
                            onComplete(selectedCustomer?.id, selectedPaymentMethod)
                        }
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

    // WhatsApp Share Confirmation Screen
    if (currentScreen == PaymentModalScreen.WHATSAPP_SHARE && selectedCustomer != null) {
        Dialog(
            onDismissRequest = {
                // Allow dismissing, but complete the transaction
                onComplete(selectedCustomer?.id, selectedPaymentMethod)
            },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.96f)
                        .heightIn(min = 280.dp, max = 500.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = BgPrimary)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Success icon
                        Surface(
                            shape = CircleShape,
                            color = ProfitGreenBg,
                            modifier = Modifier.size(72.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = ProfitGreen,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            "Payment Recorded!",
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp
                            ),
                            color = TextPrimary
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            "₹${totalAmount.toInt()}",
                            style = MaterialTheme.typography.displayMedium.copy(
                                fontWeight = FontWeight.Black,
                                fontSize = 36.sp
                            ),
                            color = ProfitGreen
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        // Customer info
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = BgCard,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = null,
                                    tint = TextSecondary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        selectedCustomer?.name ?: "",
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    Text(
                                        "+91 ${selectedCustomer?.phone ?: ""}",
                                        fontSize = 14.sp,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Text(
                            "Send bill receipt on WhatsApp?",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        
                        Spacer(modifier = Modifier.height(20.dp))
                        
                        // WhatsApp send button
                        Button(
                            onClick = {
                                val customer = selectedCustomer ?: return@Button
                                val whatsappItems = billItems.map { item ->
                                    WhatsAppHelper.BillItem(
                                        name = item.name,
                                        qty = item.qty,
                                        isLoose = item.isLoose,
                                        unitPrice = item.unitPrice,
                                        lineTotal = item.lineTotal
                                    )
                                }
                                val message = WhatsAppHelper.buildBillMessage(
                                    items = whatsappItems,
                                    totalAmount = totalAmount,
                                    paymentMode = selectedPaymentMethod,
                                    shopName = shopName,
                                    customerName = customer.name,
                                    upiId = upiId,
                                    template = receiptTemplate
                                )
                                val phone = WhatsAppHelper.normalizeIndianPhone(customer.phone)
                                WhatsAppHelper.openWhatsApp(context, phone, message)
                                onComplete(selectedCustomer?.id, selectedPaymentMethod)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF25D366), // WhatsApp green
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(
                                Icons.Default.Send,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                "Send on WhatsApp",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Skip button
                        TextButton(
                            onClick = {
                                onComplete(selectedCustomer?.id, selectedPaymentMethod)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "Skip",
                                color = TextSecondary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddCustomerDialog) {
        AlertDialog(
            onDismissRequest = { showAddCustomerDialog = false },
            title = { Text("Add Customer", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = newCustomerName,
                        onValueChange = { newCustomerName = it },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newCustomerPhone,
                        onValueChange = { newCustomerPhone = InputFilters.digitsOnly(it, maxLen = 10) },
                        label = { Text("Phone (10 digits)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Phone)
                    )
                    if (addCustomerPhoneError) {
                        Text(
                            "Mobile number must be 10 digits",
                            color = LossRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            if (newCustomerPhone.length != 10) {
                                addCustomerPhoneError = true
                                return@launch
                            }
                            addCustomerPhoneError = false
                            val created = onAddCustomer(newCustomerName, newCustomerPhone)
                            if (created != null) {
                                selectedCustomer = created
                            }
                            showAddCustomerDialog = false
                            newCustomerName = ""
                            newCustomerPhone = ""
                        }
                    }
                ) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showAddCustomerDialog = false }) { Text("Cancel") }
            }
        )
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
