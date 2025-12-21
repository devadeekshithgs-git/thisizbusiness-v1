package com.kiranaflow.app.ui.screens.vendors

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kiranaflow.app.data.local.PartyEntity
import com.kiranaflow.app.ui.components.*
import com.kiranaflow.app.ui.components.dialogs.VendorDetailSheet
import com.kiranaflow.app.ui.screens.parties.PartiesViewModel
import com.kiranaflow.app.ui.screens.parties.PartyCard
import com.kiranaflow.app.ui.theme.*

@Composable
fun VendorsScreen(
    modifier: Modifier = Modifier,
    viewModel: PartiesViewModel = viewModel(),
    onAddVendor: () -> Unit = {},
    onVendorClick: (Int) -> Unit = {},
    onPayNow: (Int) -> Unit = {}
) {
    // Reuse PartiesViewModel to show vendors
    val vendors by viewModel.vendors.collectAsState()
    val vendorTxById by viewModel.vendorTransactionsById.collectAsState()
    var selectedVendorId by remember { mutableStateOf<Int?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(GrayBg)
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
                text = "Vendors",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Black,
                    fontSize = 28.sp,
                    color = Gray900
                )
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircleButton(
                    icon = Icons.Default.Add,
                    onClick = onAddVendor,
                    containerColor = Gray900,
                    contentColor = White
                )
                CircleButton(
                    icon = Icons.Outlined.Settings,
                    onClick = { /* TODO: Settings */ },
                    containerColor = White,
                    contentColor = Gray400
                )
            }
        }

        // Search Bar
        // Re-using KiranaInput or similar if available, or just a placeholder for now to fix build
        // Assume SearchField exists in CommonUi or replace with KiranaInput
        // For now, let's use a simple Box to mimic search bar if SearchField is missing
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(50.dp)
                .background(White, RoundedCornerShape(12.dp))
                .border(1.dp, Gray200, RoundedCornerShape(12.dp))
        ) {
            Text("Search vendors...", modifier = Modifier.align(Alignment.CenterStart).padding(start = 16.dp), color = Gray400)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Vendor List
        if (vendors.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No Vendors", color = Gray500)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(vendors) { vendor ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedVendorId = vendor.id }
                    ) {
                        PartyCard(party = vendor)
                    }
                }
            }
        }
    }

    selectedVendorId?.let { id ->
        val vendor = vendors.firstOrNull { it.id == id }
        if (vendor != null) {
            VendorDetailSheet(
                vendor = vendor,
                transactions = vendorTxById[id].orEmpty(),
                onDismiss = { selectedVendorId = null },
                onSavePayment = { amount, method ->
                    viewModel.recordVendorPayment(vendor, amount, method)
                }
            )
        }
    }
}
