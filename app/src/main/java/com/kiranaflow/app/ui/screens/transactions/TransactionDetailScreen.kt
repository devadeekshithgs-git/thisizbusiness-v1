package com.kiranaflow.app.ui.screens.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.kiranaflow.app.data.local.KiranaDatabase
import com.kiranaflow.app.data.local.TransactionItemEntity
import com.kiranaflow.app.data.repository.KiranaRepository
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

    val partyId = tx?.customerId ?: tx?.vendorId ?: 0
    val party by repo.partyById(partyId).collectAsState(initial = null)

    val df = remember { SimpleDateFormat("dd MMM yyyy • hh:mm a", Locale.getDefault()) }

    Column(modifier = Modifier.fillMaxSize().background(GrayBg)) {
        CenterAlignedTopAppBar(
            title = { Text("Transaction", fontWeight = FontWeight.Black) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
        }
    }
}

@Composable
private fun LineItemRow(line: TransactionItemEntity) {
    val subtotal = (line.price * line.qty)
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
                    Tag(text = "Qty ${line.qty}")
                    Tag(text = "₹${line.price.toInt()} each")
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





