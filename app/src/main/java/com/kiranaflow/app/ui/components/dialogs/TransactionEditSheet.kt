package com.kiranaflow.app.ui.components.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kiranaflow.app.data.local.TransactionEntity
import com.kiranaflow.app.data.local.TransactionItemEntity
import com.kiranaflow.app.data.repository.KiranaRepository
import com.kiranaflow.app.util.InputFilters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionEditSheet(
    tx: TransactionEntity,
    items: List<TransactionItemEntity>,
    onDismiss: () -> Unit,
    onSave: (changes: KiranaRepository.TransactionEditChanges, reason: String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var paymentMode by remember(tx.id) { mutableStateOf(tx.paymentMode) }
    var note by remember(tx.id) { mutableStateOf("") }
    var reason by remember(tx.id) { mutableStateOf("") }

    val qtyByLineId = remember(tx.id) { mutableStateMapOf<Int, String>() }
    val priceByLineId = remember(tx.id) { mutableStateMapOf<Int, String>() }

    LaunchedEffect(tx.id, items) {
        items.forEach { li ->
            qtyByLineId[li.id] = li.qty.toString()
            priceByLineId[li.id] = li.price.toString()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Edit Transaction", fontWeight = FontWeight.Black)

            Text("Payment mode", fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = paymentMode,
                onValueChange = { paymentMode = it.trim().uppercase() },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Text("Notes", fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = false
            )

            Text("Items", fontWeight = FontWeight.Bold)
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(items, key = { it.id }) { li ->
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text(li.itemNameSnapshot, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = qtyByLineId[li.id].orEmpty(),
                                onValueChange = { qtyByLineId[li.id] = InputFilters.decimal(it) },
                                modifier = Modifier.weight(1f),
                                label = { Text("Qty") },
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = priceByLineId[li.id].orEmpty(),
                                onValueChange = { priceByLineId[li.id] = InputFilters.decimal(it) },
                                modifier = Modifier.weight(1f),
                                label = { Text("Unit price") },
                                singleLine = true
                            )
                        }
                    }
                }
            }

            Text("Reason (required)", fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = reason,
                onValueChange = { reason = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = false
            )

            Spacer(modifier = Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Button(onClick = {
                    val lineEdits = items.mapNotNull { li ->
                        val q = qtyByLineId[li.id]?.toDoubleOrNull()
                        val p = priceByLineId[li.id]?.toDoubleOrNull()
                        if (q == null && p == null) null
                        else KiranaRepository.TransactionLineEdit(
                            lineId = li.id,
                            newQty = q,
                            newUnitPrice = p
                        )
                    }
                    onSave(
                        KiranaRepository.TransactionEditChanges(
                            lineEdits = lineEdits,
                            paymentMode = paymentMode,
                            note = note.trim().ifBlank { null }
                        ),
                        reason
                    )
                }) { Text("Save") }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}



