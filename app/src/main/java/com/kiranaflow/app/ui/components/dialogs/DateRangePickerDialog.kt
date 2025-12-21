package com.kiranaflow.app.ui.components.dialogs

import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .heightIn(max = 720.dp)
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .imePadding(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = BgPrimary)
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    // Landscape-friendly: if the available height is tight, allow scrolling.
                    .verticalScroll(rememberScrollState())
            ) {
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

                DateRangePicker(
                    state = pickerState,
                    showModeToggle = false,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Apply Button
                KiranaButton(
                    text = "Apply Range",
                    onClick = {
                        val start = pickerState.selectedStartDateMillis
                        val end = pickerState.selectedEndDateMillis
                        if (start != null && end != null && end >= start) {
                            onApply(start, end)
                        }
                    },
                    enabled = pickerState.selectedStartDateMillis != null && pickerState.selectedEndDateMillis != null,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Blue600,
                        contentColor = BgPrimary
                    )
                )
            }
        }
    }
}
