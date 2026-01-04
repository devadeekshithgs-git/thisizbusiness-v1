package com.kiranaflow.app.ui.screens.reports

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class Report(
    val id: String,
    val title: String,
    val description: String,
    val icon: String = "📊"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    onBackClick: () -> Unit,
    onReportClick: (String) -> Unit
) {
    val reports = remember {
        listOf(
            Report("sales", "Sales Report", "Daily, weekly, and monthly sales analysis"),
            Report("inventory", "Inventory Report", "Stock levels and movement tracking"),
            Report("financial", "Financial Report", "Revenue, expenses, and profit analysis"),
            Report("customer", "Customer Report", "Customer behavior and purchase patterns"),
            Report("vendor", "Vendor Report", "Supplier performance and payment tracking"),
            Report("gst", "GST Report", "Tax compliance and filing reports"),
            Report("profit_loss", "Profit & Loss", "Comprehensive P&L statement"),
            Report("cash_flow", "Cash Flow", "Cash inflow and outflow analysis")
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reports") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(reports) { report ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth(),
                    onClick = { onReportClick(report.id) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = report.icon,
                            style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier.padding(end = 16.dp)
                        )
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = report.title,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = report.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}