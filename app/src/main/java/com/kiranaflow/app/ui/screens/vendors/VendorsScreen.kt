package com.kiranaflow.app.ui.screens.vendors

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.TrendingUp
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
    onOpenSettings: () -> Unit = {},
    onPayNow: (Int) -> Unit = {}
) {
    // Reuse PartiesViewModel to show vendors
    val vendors by viewModel.vendors.collectAsState()
    val vendorTxById by viewModel.vendorTransactionsById.collectAsState()
    var selectedVendorId by remember { mutableStateOf<Int?>(null) }

    val accent = tabCapsuleColor("vendors")

    Box(modifier = modifier.fillMaxSize().background(GrayBg)) {
        Column(modifier = Modifier.fillMaxSize()) {
            SolidTopBar(
                title = "Vendor Khata",
                subtitle = "Track purchases & expenses",
                onSettings = onOpenSettings,
                containerColor = accent
            )

            if (vendors.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No Vendors", color = Gray500)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(top = 18.dp, bottom = 100.dp)
                ) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .background(White, RoundedCornerShape(12.dp))
                                .border(1.dp, Gray200, RoundedCornerShape(12.dp))
                        ) {
                            Text(
                                "Search vendors...",
                                modifier = Modifier.align(Alignment.CenterStart).padding(start = 16.dp),
                                color = Gray400
                            )
                        }
                    }

                    items(vendors, key = { it.id }) { vendor ->
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

        // Bottom-right Add button (above bottom menu bar)
        AddFab(
            onClick = onAddVendor,
            containerColor = accent,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 24.dp, bottom = 112.dp)
        )
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
