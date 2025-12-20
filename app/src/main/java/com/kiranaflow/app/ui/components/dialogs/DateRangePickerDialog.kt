package com.kiranaflow.app.ui.components.dialogs

import androidx.compose.foundation.layout.*
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
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DateRangePickerDialog(
    onDismiss: () -> Unit,
    onApply: (Long, Long) -> Unit
) {
    var startDateText by remember { mutableStateOf("") }
    var endDateText by remember { mutableStateOf("") }
    
    val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
    var startDateMillis by remember { mutableStateOf<Long?>(null) }
    var endDateMillis by remember { mutableStateOf<Long?>(null) }

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
                        "Select Date Range",
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

                // Start Date
                KiranaInput(
                    value = startDateText,
                    onValueChange = { startDateText = it },
                    placeholder = "dd-mm-yyyy",
                    label = "START DATE"
                )

                Spacer(modifier = Modifier.height(16.dp))

                // End Date
                KiranaInput(
                    value = endDateText,
                    onValueChange = { endDateText = it },
                    placeholder = "dd-mm-yyyy",
                    label = "END DATE"
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Apply Button
                KiranaButton(
                    text = "Apply Range",
                    onClick = {
                        try {
                            val start = dateFormat.parse(startDateText)?.time
                            val end = dateFormat.parse(endDateText)?.time
                            if (start != null && end != null && end >= start) {
                                onApply(start, end)
                            }
                        } catch (e: Exception) {
                            // Handle parse error
                        }
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
