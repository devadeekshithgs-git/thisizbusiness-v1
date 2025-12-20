package com.kiranaflow.app.ui.screens.vendors

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiranaflow.app.data.local.KiranaDatabase
import com.kiranaflow.app.ui.theme.BgCard
import com.kiranaflow.app.ui.theme.BgPrimary
import com.kiranaflow.app.ui.theme.TextPrimary
import com.kiranaflow.app.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun VendorDetailScreen(
    vendorId: Int,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val db = remember(context) { KiranaDatabase.getDatabase(context) }
    val vendor by db.partyDao().getPartyById(vendorId).collectAsState(initial = null)
    val txs by db.transactionDao().getTransactionsForParty(vendorId).collectAsState(initial = emptyList())
    val fmt = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgCard)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 48.dp, start = 12.dp, end = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
            }
            Column(modifier = Modifier.padding(start = 6.dp)) {
                Text(
                    text = vendor?.name ?: "Vendor",
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp,
                    color = TextPrimary
                )
                Text(
                    text = "Transactions",
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { Spacer(modifier = Modifier.height(2.dp)) }

            items(txs) { t ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = BgPrimary),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(t.title, color = TextPrimary, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "₹${t.amount.toInt()}",
                                color = TextPrimary,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                fmt.format(Date(t.date)),
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

