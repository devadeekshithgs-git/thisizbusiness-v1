package com.kiranaflow.app.ui.screens.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import coil.compose.AsyncImage
import com.kiranaflow.app.data.local.KiranaDatabase
import com.kiranaflow.app.data.local.TransactionItemEntity
import com.kiranaflow.app.data.repository.KiranaRepository
import com.kiranaflow.app.ui.components.dialogs.AdjustmentConfirmDialog
import com.kiranaflow.app.ui.components.dialogs.ReasonInputDialog
import com.kiranaflow.app.ui.components.dialogs.TransactionEditSheet
import com.kiranaflow.app.ui.theme.BgPrimary
import com.kiranaflow.app.ui.theme.Gray100
import com.kiranaflow.app.ui.theme.GrayBg
import com.kiranaflow.app.ui.theme.LossRed
import com.kiranaflow.app.ui.theme.ProfitGreen
import com.kiranaflow.app.ui.theme.TextPrimary
import com.kiranaflow.app.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun TransactionDetailScreen(
    transactionId: Int,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val repo = remember(context) { KiranaRepository(KiranaDatabase.getDatabase(context)) }

    val tx by repo.transactionById(transactionId).collectAsState(initial = null)
    val items by repo.transactionItemsFor(transactionId).collectAsState(initial = emptyList())
    val unsyncedIds by repo.unsyncedTransactionIds.collectAsState(initial = emptySet())

    val partyId = tx?.customerId ?: tx?.vendorId ?: 0
    val party by repo.partyById(partyId).collectAsState(initial = null)

    val df = remember { SimpleDateFormat("dd MMM yyyy • hh:mm a", Locale.getDefault()) }
    var showReceiptFull by remember { mutableStateOf(false) }
    var showEditSheet by remember { mutableStateOf(false) }
    var showFinalizedConfirm by remember { mutableStateOf(false) }
    var showFinalizeConfirm by remember { mutableStateOf(false) }
    var editMode by remember { mutableStateOf("DIRECT") } // DIRECT | ADJUSTMENT

    Column(modifier = Modifier.fillMaxSize().background(GrayBg)) {
        CenterAlignedTopAppBar(
            title = { Text("Transaction", fontWeight = FontWeight.Black) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                val t = tx
                if (t != null) {
                    if (t.status.uppercase() in setOf("DRAFT", "POSTED", "FINALIZED")) {
                        IconButton(onClick = {
                            if (t.status.uppercase() == "FINALIZED") {
                                showFinalizedConfirm = true
                            } else {
                                editMode = "DIRECT"
                                showEditSheet = true
                            }
                        }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit")
                        }
                    }
                    if (t.status.uppercase() == "POSTED") {
                        IconButton(onClick = { showFinalizeConfirm = true }) {
                            Icon(Icons.Default.Done, contentDescription = "Finalize")
                        }
                    }
                }
            },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = BgPrimary)
        )

        if (tx == null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Transaction not found", fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(modifier = Modifier.height(6.dp))
                Text("It may have been deleted.", color = TextSecondary, fontSize = 12.sp)
            }
            return
        }

        val t = tx!!
        val isExpense = t.type == "EXPENSE"
        val sign = if (isExpense) "-" else "+"
        val amountColor = if (isExpense) LossRed else ProfitGreen
        val adjustments by repo.adjustmentsForTransaction(t.id).collectAsState(initial = emptyList())

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = BgPrimary),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(t.title, fontWeight = FontWeight.Black, color = TextPrimary, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)

                        Text(df.format(Date(t.date)), color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)

                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Column {
                                Text("TYPE", fontSize = 10.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
                                Text(t.type, color = TextPrimary, fontWeight = FontWeight.Bold)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("PAYMENT", fontSize = 10.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
                                Text(if (t.paymentMode == "CREDIT") "UDHAAR" else t.paymentMode, color = TextPrimary, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Column {
                                Text("STATUS", fontSize = 10.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
                                Text(t.status, color = TextPrimary, fontWeight = FontWeight.Bold)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("SYNC", fontSize = 10.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
                                Text(if (unsyncedIds.contains(t.id)) "PENDING" else "OK", color = TextPrimary, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (party != null) {
                            val p = party!!
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (p.type == "VENDOR") Icons.Default.Store else Icons.Default.Person,
                                    contentDescription = null,
                                    tint = TextSecondary,
                                    modifier = Modifier.width(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "${if (p.type == "VENDOR") "Vendor" else "Customer"}: ${p.name}",
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("TOTAL", fontWeight = FontWeight.Black, color = TextPrimary)
                            Text(
                                "$sign₹${abs(t.amount).toInt()}",
                                fontWeight = FontWeight.Black,
                                color = amountColor,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }

            // Receipt proof (for expenses/purchases where a photo was attached)
            if (!t.receiptImageUri.isNullOrBlank()) {
                item {
                    Text("Receipt proof", fontWeight = FontWeight.Black, color = TextPrimary, fontSize = 16.sp)
                }
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = BgPrimary),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = TextSecondary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Receipt attached", fontWeight = FontWeight.Bold, color = TextPrimary)
                                    Text("Tap to view fullscreen", color = TextSecondary, fontSize = 12.sp)
                                }
                            }
                            Divider(color = Gray100)
                            AsyncImage(
                                model = t.receiptImageUri,
                                contentDescription = "Receipt proof",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { showReceiptFull = true }
                            )
                        }
                    }
                }
            }

            if (items.isNotEmpty()) {
                item {
                    Text("Items", fontWeight = FontWeight.Black, color = TextPrimary, fontSize = 16.sp)
                }

                items(items, key = { it.id }) { line ->
                    LineItemRow(line = line)
                }
            } else {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = BgPrimary),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Inventory2, contentDescription = null, tint = TextSecondary)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("No line items saved", fontWeight = FontWeight.Bold, color = TextPrimary)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("This transaction doesn’t have item-level details.", color = TextSecondary, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            if (t.status.uppercase() == "ADJUSTED" && adjustments.isNotEmpty()) {
                item {
                    Text("Adjustments", fontWeight = FontWeight.Black, color = TextPrimary, fontSize = 16.sp)
                }
                items(adjustments, key = { it.id }) { adj ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = BgPrimary),
                        shape = RoundedCornerShape(14.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("${adj.adjustmentType} • ${adj.gstType ?: "ADJUSTMENT"}", fontWeight = FontWeight.Bold, color = TextPrimary)
                            Text(adj.reason, color = TextSecondary, fontSize = 12.sp)
                            Text(df.format(Date(adj.createdAt)), color = TextSecondary, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }

    // Fullscreen receipt viewer
    if (showReceiptFull && !tx?.receiptImageUri.isNullOrBlank()) {
        Dialog(onDismissRequest = { showReceiptFull = false }) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.92f)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = tx?.receiptImageUri,
                        contentDescription = "Receipt proof fullscreen",
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.Center)
                            .padding(16.dp)
                            .clip(RoundedCornerShape(16.dp))
                    )
                    IconButton(
                        onClick = { showReceiptFull = false },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp)
                            .clip(CircleShape)
                            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.35f))
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = androidx.compose.ui.graphics.Color.White)
                    }
                }
            }
        }
    }

    if (showFinalizedConfirm) {
        AdjustmentConfirmDialog(
            onCreateAdjustment = {
                showFinalizedConfirm = false
                editMode = "ADJUSTMENT"
                showEditSheet = true
            },
            onCancel = { showFinalizedConfirm = false }
        )
    }

    if (showFinalizeConfirm && tx != null) {
        ReasonInputDialog(
            title = "Finalize transaction?",
            onConfirm = {
                showFinalizeConfirm = false
                CoroutineScope(Dispatchers.Main).launch {
                    val ok = repo.finalizeTransaction(tx!!.id, null)
                    Toast.makeText(context, if (ok) "Finalized" else "Cannot finalize", Toast.LENGTH_SHORT).show()
                }
            },
            onCancel = { showFinalizeConfirm = false }
        )
    }

    if (showEditSheet && tx != null) {
        TransactionEditSheet(
            tx = tx!!,
            items = items,
            onDismiss = { showEditSheet = false },
            onSave = { changes, reason ->
                showEditSheet = false
                CoroutineScope(Dispatchers.Main).launch {
                    if (editMode == "DIRECT") {
                        when (val r = repo.editTransaction(tx!!.id, changes, reason, null)) {
                            is KiranaRepository.EditResult.Success -> Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
                            is KiranaRepository.EditResult.StockConflict -> Toast.makeText(context, "Stock insufficient", Toast.LENGTH_SHORT).show()
                            is KiranaRepository.EditResult.NotAllowed -> Toast.makeText(context, r.reason, Toast.LENGTH_SHORT).show()
                            is KiranaRepository.EditResult.InvalidInput -> Toast.makeText(context, r.reason, Toast.LENGTH_SHORT).show()
                            else -> Toast.makeText(context, "Edit failed", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        val original = tx!!
                        val byId = items.associateBy { it.id }
                        val deltas = changes.lineEdits.mapNotNull { e ->
                            val li = byId[e.lineId] ?: return@mapNotNull null
                            val newQty = e.newQty ?: li.qty
                            val newPrice = e.newUnitPrice ?: li.price
                            val qtyDelta = newQty - li.qty
                            val priceDelta = newPrice - li.price
                            if (qtyDelta == 0.0 && priceDelta == 0.0) return@mapNotNull null
                            KiranaRepository.ItemAdjustment(
                                itemId = li.itemId,
                                itemNameSnapshot = li.itemNameSnapshot,
                                quantityDelta = qtyDelta,
                                priceDelta = priceDelta,
                                taxDelta = 0.0
                            )
                        }
                        when (val ar = repo.createAdjustment(original.id, deltas, reason, null)) {
                            is KiranaRepository.AdjustmentResult.Success -> Toast.makeText(context, "Adjustment created", Toast.LENGTH_SHORT).show()
                            is KiranaRepository.AdjustmentResult.StockConflict -> Toast.makeText(context, "Stock insufficient", Toast.LENGTH_SHORT).show()
                            is KiranaRepository.AdjustmentResult.NotAllowed -> Toast.makeText(context, ar.reason, Toast.LENGTH_SHORT).show()
                            is KiranaRepository.AdjustmentResult.InvalidInput -> Toast.makeText(context, ar.reason, Toast.LENGTH_SHORT).show()
                            else -> Toast.makeText(context, "Adjustment failed", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        )
    }
}

@Composable
private fun LineItemRow(line: TransactionItemEntity) {
    val unit = line.unit.uppercase()
    val isGram = unit == "GRAM" || unit == "G"
    val isKg = unit == "KG"

    val multiplier = when {
        isGram -> line.qty / 1000.0 // backward-compat only
        else -> line.qty
    }
    val subtotal = (line.price * multiplier)

    fun formatKgShort(kg: Double): String {
        if (kg <= 0.0) return "0kg"
        val txt = if (kg % 1.0 == 0.0) kg.toInt().toString()
        else String.format("%.3f", kg).trimEnd('0').trimEnd('.')
        return "${txt}kg"
    }

    val qtyLabel = when {
        isKg -> formatKgShort(line.qty)
        isGram -> formatKgShort(line.qty / 1000.0) // backward-compat only
        else -> "Qty ${line.qty.toInt()}"
    }

    val priceLabel = when {
        isGram || isKg -> "₹${line.price.toInt()}/kg"
        else -> "₹${line.price.toInt()} each"
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = BgPrimary),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    line.itemNameSnapshot,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Tag(text = qtyLabel)
                    Tag(text = priceLabel)
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text("₹${subtotal.toInt()}", fontWeight = FontWeight.Black, color = TextPrimary)
        }
    }
}

@Composable
private fun Tag(text: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Gray100),
        shape = RoundedCornerShape(999.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            color = TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}





