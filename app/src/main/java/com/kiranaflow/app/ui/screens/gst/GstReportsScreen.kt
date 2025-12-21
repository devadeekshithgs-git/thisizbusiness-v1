package com.kiranaflow.app.ui.screens.gst

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiranaflow.app.data.local.ShopSettings
import com.kiranaflow.app.data.local.ShopSettingsStore
import com.kiranaflow.app.ui.components.KiranaButton
import com.kiranaflow.app.ui.components.dialogs.DateRangePickerDialog
import com.kiranaflow.app.ui.theme.*
import com.kiranaflow.app.util.Formatters
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun GstReportsScreen(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenGstr1: (fromMillis: Long, toMillisExclusive: Long) -> Unit
) {
    val context = LocalContext.current
    val store = remember(context) { ShopSettingsStore(context) }
    val settings by store.settings.collectAsState(initial = ShopSettings())

    val dateFmt = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    val now = remember { System.currentTimeMillis() }

    // Default range: current month.
    var fromMillis by remember {
        val cal = Calendar.getInstance()
        cal.timeInMillis = now
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        mutableStateOf(cal.timeInMillis)
    }
    var toMillisInclusive by remember { mutableStateOf(now) }
    var showPicker by remember { mutableStateOf(false) }

    if (showPicker) {
        DateRangePickerDialog(
            onDismiss = { showPicker = false },
            onApply = { start, end ->
                fromMillis = start
                toMillisInclusive = end
                showPicker = false
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("GST Reports", fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = onOpenSettings) { Text("Settings") }
                }
            )
        },
        containerColor = GrayBg
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Business details card
            Card(
                colors = CardDefaults.cardColors(containerColor = BgPrimary),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Business Details", fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(modifier = Modifier.height(8.dp))

                    InfoRow(label = "GSTIN", value = settings.gstin.ifBlank { "Not set" }, isMissing = settings.gstin.isBlank())
                    InfoRow(label = "Legal Name", value = settings.legalName.ifBlank { "Not set" }, isMissing = settings.legalName.isBlank())
                    InfoRow(
                        label = "State Code",
                        value = if (settings.stateCode == 0) "Not set" else settings.stateCode.toString().padStart(2, '0'),
                        isMissing = settings.stateCode == 0
                    )
                    InfoRow(label = "Address", value = settings.address.ifBlank { "Not set" }, isMissing = settings.address.isBlank())

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Tip: Fill these once in Settings. GST export will highlight missing invoice fields before download.",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }

            // Period selector
            Card(
                colors = CardDefaults.cardColors(containerColor = BgPrimary),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Select Period", fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("From", fontSize = 12.sp, color = TextSecondary)
                            Text(dateFmt.format(Date(fromMillis)), fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("To", fontSize = 12.sp, color = TextSecondary)
                            Text(dateFmt.format(Date(toMillisInclusive)), fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        OutlinedButton(onClick = { showPicker = true }) {
                            Text("Change")
                        }
                    }
                }
            }

            // Form list (for now: GSTR-1 only)
            Card(
                colors = CardDefaults.cardColors(containerColor = BgPrimary),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = Blue600)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("GSTR-1", fontWeight = FontWeight.Black, color = TextPrimary)
                            Text(
                                "Outward supplies (sales/invoices), HSN summary",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Summary placeholders (populated in ViewModel in next todo)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        SummaryChip(label = "Invoices", value = "—")
                        SummaryChip(label = "Taxable", value = "—")
                        SummaryChip(label = "Tax", value = "—")
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    KiranaButton(
                        text = "Preview & Export",
                        onClick = {
                            // Convert inclusive picker end to exclusive bound at next day start.
                            val cal = Calendar.getInstance()
                            cal.timeInMillis = toMillisInclusive
                            cal.set(Calendar.HOUR_OF_DAY, 0)
                            cal.set(Calendar.MINUTE, 0)
                            cal.set(Calendar.SECOND, 0)
                            cal.set(Calendar.MILLISECOND, 0)
                            cal.add(Calendar.DAY_OF_MONTH, 1)
                            onOpenGstr1(fromMillis, cal.timeInMillis)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Blue600, contentColor = BgPrimary)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Exports will be generated in official JSON format for GST portal upload, and a spreadsheet for review.",
                fontSize = 12.sp,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, isMissing: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 12.sp, color = TextSecondary)
        Text(
            value,
            fontSize = 12.sp,
            color = if (isMissing) LossRed else TextPrimary,
            fontWeight = if (isMissing) FontWeight.Bold else FontWeight.SemiBold
        )
    }
}

@Composable
private fun SummaryChip(label: String, value: String) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = Gray100
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, fontSize = 11.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(value, fontSize = 12.sp, color = TextPrimary, fontWeight = FontWeight.Black)
        }
    }
}



