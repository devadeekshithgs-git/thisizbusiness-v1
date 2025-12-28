package com.kiranaflow.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.kiranaflow.app.data.local.ChangeType
import com.kiranaflow.app.data.local.ScannedBillDraft
import com.kiranaflow.app.data.local.ScannedItemDraft
import com.kiranaflow.app.ui.theme.BgPrimary
import com.kiranaflow.app.ui.theme.GrayBg
import com.kiranaflow.app.ui.theme.LossRed
import com.kiranaflow.app.ui.theme.TextPrimary
import com.kiranaflow.app.ui.theme.TextSecondary

@Composable
fun BillReviewSection(
    draft: ScannedBillDraft,
    onUpdateItem: (ScannedItemDraft) -> Unit,
    onDone: () -> Unit,
    onDiscard: () -> Unit
) {
    var expanded by remember { mutableStateOf(true) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        colors = CardDefaults.cardColors(containerColor = GrayBg),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Scanned bill review", fontWeight = FontWeight.Black, color = TextPrimary)
                    val vendorName = draft.vendor.name?.trim().orEmpty().ifBlank { "Unknown vendor" }
                    Text(
                        "Vendor: $vendorName • Items: ${draft.items.size}",
                        color = TextSecondary
                    )
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        tint = TextSecondary
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val newItems = draft.items.filter { it.changeType == ChangeType.NEW }
                    val updatedItems = draft.items.filter { it.changeType != ChangeType.NEW }

                    if (newItems.isNotEmpty()) {
                        Text("New items", fontWeight = FontWeight.Bold, color = TextPrimary)
                        DraftItemsList(items = newItems, onUpdateItem = onUpdateItem)
                    }
                    if (updatedItems.isNotEmpty()) {
                        Text("Updates", fontWeight = FontWeight.Bold, color = TextPrimary)
                        DraftItemsList(items = updatedItems, onUpdateItem = onUpdateItem)
                    }

                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = onDiscard,
                            colors = ButtonDefaults.textButtonColors(contentColor = TextSecondary),
                            modifier = Modifier.weight(1f)
                        ) { Text("Discard") }

                        KiranaButton(
                            text = "Done",
                            onClick = onDone,
                            colors = ButtonDefaults.buttonColors(containerColor = TextPrimary, contentColor = BgPrimary),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DraftItemsList(
    items: List<ScannedItemDraft>,
    onUpdateItem: (ScannedItemDraft) -> Unit
) {
    // Small list: keep it simple. Inventory screen already scrolls.
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(items, key = { it.tempId }) { it ->
            DraftItemRow(item = it, onUpdateItem = onUpdateItem)
        }
    }
}

@Composable
private fun DraftItemRow(
    item: ScannedItemDraft,
    onUpdateItem: (ScannedItemDraft) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = BgPrimary),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.name, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 2)
                    Text(
                        when (item.changeType) {
                            ChangeType.NEW -> "New"
                            ChangeType.QTY_ONLY -> "Quantity updated"
                            ChangeType.PRICE_ONLY -> "Price updated"
                            ChangeType.QTY_AND_PRICE -> "Quantity + price updated"
                        },
                        color = TextSecondary
                    )
                    if (item.confidence in 0f..0.54f) {
                        Text("Low match confidence • please verify", color = LossRed)
                    }
                }
            }

            // Editable fields (review-first)
            OutlinedTextField(
                value = item.name,
                onValueChange = { s -> onUpdateItem(item.copy(name = s.trimStart())) },
                label = { Text("Item name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = item.qty.toString(),
                    onValueChange = { s ->
                        val q = s.filter { it.isDigit() }.take(5).toIntOrNull() ?: 0
                        onUpdateItem(item.copy(qty = q))
                    },
                    label = { Text("Qty") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = item.costPrice?.toString().orEmpty(),
                    onValueChange = { s ->
                        val v = s.trim().toDoubleOrNull()
                        onUpdateItem(item.copy(costPrice = v))
                    },
                    label = { Text("Cost price") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
            }

            OutlinedTextField(
                value = item.sellingPrice?.toString().orEmpty(),
                onValueChange = { s ->
                    val v = s.trim().toDoubleOrNull()
                    onUpdateItem(item.copy(sellingPrice = v))
                },
                label = { Text("Selling price (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = item.gstRate?.toString().orEmpty(),
                onValueChange = { s ->
                    val v = s.trim().toDoubleOrNull()
                    onUpdateItem(item.copy(gstRate = v))
                },
                label = { Text("GST % (optional)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = item.rackLocation.orEmpty(),
                onValueChange = { s -> onUpdateItem(item.copy(rackLocation = s.trim().ifBlank { null })) },
                label = { Text("Storage location (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}


