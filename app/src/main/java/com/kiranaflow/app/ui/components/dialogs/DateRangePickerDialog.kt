package com.kiranaflow.app.ui.components.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.kiranaflow.app.ui.components.KiranaButton
import com.kiranaflow.app.ui.theme.*

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun DateRangePickerDialog(
    onDismiss: () -> Unit,
    onApply: (Long, Long) -> Unit
) {
    val pickerState = rememberDateRangePickerState()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            color = BgPrimary
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
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

                // DateRangePicker takes most of the space - it handles its own scrolling internally
                DateRangePicker(
                    state = pickerState,
                    showModeToggle = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    title = null,
                    headline = {
                        val startDate = pickerState.selectedStartDateMillis
                        val endDate = pickerState.selectedEndDateMillis
                        val startLabel = if (startDate != null) {
                            java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault())
                                .format(java.util.Date(startDate))
                        } else "Start Date"
                        val endLabel = if (endDate != null) {
                            java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault())
                                .format(java.util.Date(endDate))
                        } else "End Date"
                        Text(
                            text = "$startLabel  →  $endLabel",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                )

                // Bottom button row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BgPrimary)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Cancel", fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = {
                            val start = pickerState.selectedStartDateMillis
                            val end = pickerState.selectedEndDateMillis
                            if (start != null && end != null && end >= start) {
                                onApply(start, end)
                            }
                        },
                        enabled = pickerState.selectedStartDateMillis != null && pickerState.selectedEndDateMillis != null,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Blue600,
                            contentColor = BgPrimary
                        )
                    ) {
                        Text("Apply", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
