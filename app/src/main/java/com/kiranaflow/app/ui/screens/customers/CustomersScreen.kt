package com.kiranaflow.app.ui.screens.customers

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kiranaflow.app.ui.components.*
import com.kiranaflow.app.ui.components.dialogs.CustomerDetailSheet
import com.kiranaflow.app.ui.theme.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material.icons.filled.People
import com.kiranaflow.app.util.Formatters

@Composable
fun CustomersScreen(
    modifier: Modifier = Modifier,
    viewModel: CustomerViewModel = viewModel(),
    onAddCustomer: () -> Unit = {},
    onCustomerClick: (Int) -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    var selectedCustomerId by remember { mutableStateOf<Int?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgCard)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 48.dp, start = 16.dp, end = 16.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Customers",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Black,
                    fontSize = 28.sp,
                    color = TextPrimary
                )
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircleButton(
                    icon = Icons.Default.Add,
                    onClick = onAddCustomer,
                    containerColor = TextPrimary,
                    contentColor = BgPrimary
                )
                CircleButton(
                    icon = Icons.Outlined.Settings,
                    onClick = { /* TODO: Settings */ },
                    containerColor = BgCard,
                    contentColor = TextSecondary
                )
            }
        }

        // Total Receivables KPI Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = ProfitGreen),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize().padding(20.dp)) {
                Column {
                    Text(
                        "TOTAL RECEIVABLES",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = BgPrimary.copy(alpha = 0.9f),
                            fontSize = 12.sp,
                            letterSpacing = 1.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        Formatters.formatInrCurrency(state.totalReceivables, fractionDigits = 0, useAbsolute = true),
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontWeight = FontWeight.Black,
                            color = BgPrimary,
                            fontSize = 32.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Money pending from market",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = BgPrimary.copy(alpha = 0.8f),
                            fontSize = 12.sp
                        )
                    )
                }
                Icon(
                    Icons.Default.ArrowForward,
                    contentDescription = null,
                    tint = BgPrimary.copy(alpha = 0.5f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Search Bar
        SearchField(
            query = state.searchQuery,
            onQueryChange = { viewModel.search(it) },
            placeholder = "Search customers...",
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Customer List
        if (state.customers.isEmpty() && !state.isLoading) {
            EmptyState(
                title = "No Customers",
                message = "Add your first customer to get started",
                icon = {
                    Icon(
                        Icons.Default.People,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = TextSecondary
                    )
                },
                modifier = Modifier.weight(1f)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.customers) { customer ->
                    CustomerCard(
                        customer = customer,
                        onClick = { selectedCustomerId = customer.id }
                    )
                }
            }
        }
    }

    // Customer Detail Sheet
    selectedCustomerId?.let { customerId ->
        val customer = state.customers.find { it.id == customerId }
        if (customer != null) {
            CustomerDetailSheet(
                customer = customer,
                transactions = state.customerTransactions,
                onDismiss = { selectedCustomerId = null },
                onSavePayment = { amount, paymentMethod ->
                    viewModel.recordPayment(customerId, amount, paymentMethod)
                }
            )
        }
    }
}
