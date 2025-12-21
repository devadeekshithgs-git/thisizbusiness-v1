package com.kiranaflow.app.ui.components.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.window.Dialog
import com.kiranaflow.app.ui.components.KiranaButton
import com.kiranaflow.app.ui.components.KiranaInput
import com.kiranaflow.app.ui.theme.*

@Composable
fun ShopSettingsDialog(
    shopName: String,
    ownerName: String,
    upiId: String,
    whatsappReminderMessage: String,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String) -> Unit
) {
    var shopNameState by remember { mutableStateOf(shopName) }
    var ownerNameState by remember { mutableStateOf(ownerName) }
    var upiIdState by remember { mutableStateOf(upiId) }
    var whatsappReminderState by remember { mutableStateOf(whatsappReminderMessage) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 720.dp)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = BgPrimary)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Shop Settings",
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

                // Shop Name
                KiranaInput(
                    value = shopNameState,
                    onValueChange = { shopNameState = it },
                    placeholder = "e.g. Bhanu Super Mart",
                    label = "SHOP NAME"
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Owner Name
                KiranaInput(
                    value = ownerNameState,
                    onValueChange = { ownerNameState = it },
                    placeholder = "e.g. Owner Ji",
                    label = "OWNER NAME"
                )

                Spacer(modifier = Modifier.height(16.dp))

                // UPI ID
                Column {
                    Text(
                        "@ UPI ID (FOR QR CODE)",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Purple600
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Required to show dynamic QR codes during billing.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    KiranaInput(
                        value = upiIdState,
                        onValueChange = { upiIdState = it },
                        placeholder = "e.g. 9876543210@upi"
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                KiranaInput(
                    value = whatsappReminderState,
                    onValueChange = { whatsappReminderState = it },
                    placeholder = "e.g. Namaste {name}, your due is ₹{due}. Please pay.",
                    label = "WHATSAPP REMINDER MESSAGE"
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Save Button
                KiranaButton(
                    text = "✓ Save Settings",
                    onClick = {
                        onSave(shopNameState, ownerNameState, upiIdState, whatsappReminderState)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Blue600,
                        contentColor = BgPrimary
                    )
                )
            }
        }
    }
}
