package com.kiranaflow.app.ui.components.dialogs

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight

@Composable
fun AdjustmentConfirmDialog(
    onCreateAdjustment: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Finalized invoice", fontWeight = FontWeight.Bold) },
        text = { Text("This invoice is finalized. Editing will create an adjustment entry.") },
        confirmButton = { TextButton(onClick = onCreateAdjustment) { Text("Create Adjustment") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } }
    )
}



