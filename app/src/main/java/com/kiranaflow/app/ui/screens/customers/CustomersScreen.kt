package com.kiranaflow.app.ui.screens.customers

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material.icons.filled.People
import com.kiranaflow.app.util.Formatters

@Composable
fun CustomersScreen(
    modifier: Modifier = Modifier,
    viewModel: CustomerViewModel = viewModel(),
    onAddCustomer: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onCustomerClick: (Int) -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    var selectedCustomerId by remember { mutableStateOf<Int?>(null) }

    val accent = tabCapsuleColor("customers")

    Box(modifier = modifier.fillMaxSize().background(GrayBg)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            // Header is part of the scroll (same interaction as Home screen)
            item {
                SolidTopBar(
                    title = "Customer Khata",
                    subtitle = "Manage udhaar & payments",
                    onSettings = onOpenSettings,
                    containerColor = accent
                )
            }

            if (state.customers.isEmpty() && !state.isLoading) {
                item {
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 24.dp)
                    )
                }
            } else {
                item {
                    Box(modifier = Modifier.padding(horizontal = 24.dp)) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = ProfitGreen),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Box(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
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
                    }
                }

                item {
                    Box(modifier = Modifier.padding(horizontal = 24.dp)) {
                        SearchField(
                            query = state.searchQuery,
                            onQueryChange = { viewModel.search(it) },
                            placeholder = "Search customers...",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                items(state.customers) { customer ->
                    Box(modifier = Modifier.padding(horizontal = 24.dp)) {
                        CustomerCard(
                            customer = customer,
                            onClick = { selectedCustomerId = customer.id }
                        )
                    }
                }
            }
        }

        // Bottom-right Add button (above bottom menu bar)
        AddFab(
            onClick = onAddCustomer,
            containerColor = accent,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 24.dp, bottom = 112.dp)
        )
    }

    // Customer Detail Sheet
    selectedCustomerId?.let { customerId ->
        val customer = state.customers.find { it.id == customerId }
        if (customer != null) {
            CustomerDetailSheet(
                customer = customer,
                transactions = state.customerTransactions,
                transactionItemsByTxId = state.transactionItemsByTxId,
                onDismiss = { selectedCustomerId = null },
                onSavePayment = { amount, paymentMethod ->
                    viewModel.recordPayment(customerId, amount, paymentMethod)
                }
            )
        }
    }
}
