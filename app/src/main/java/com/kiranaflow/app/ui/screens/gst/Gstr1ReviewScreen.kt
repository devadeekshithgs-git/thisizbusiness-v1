package com.kiranaflow.app.ui.screens.gst

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiranaflow.app.ui.components.KiranaButton
import com.kiranaflow.app.ui.theme.*
import com.kiranaflow.app.util.Formatters
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun Gstr1ReviewScreen(
    state: Gstr1ReviewUiState,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onUpdateInvoiceNumber: (txId: Int, invoiceNumber: String) -> Unit,
    onUpdateRecipientName: (txId: Int, name: String) -> Unit,
    onUpdateRecipientGstin: (txId: Int, gstin: String) -> Unit,
    onUpdatePlaceOfSupply: (txId: Int, stateCode: Int) -> Unit,
    onUpdateLineHsn: (lineId: Int, hsn: String) -> Unit,
    onUpdateLineGstRate: (lineId: Int, gstRate: Double) -> Unit,
    onUpdateLineTaxableValue: (lineId: Int, taxable: Double) -> Unit,
    onExportJson: () -> Unit,
    onExportExcel: () -> Unit,
    onExportPdf: () -> Unit
) {
    val dateFmt = remember { SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()) }

    var tab by remember { mutableIntStateOf(0) } // 0=B2B, 1=B2C, 2=HSN

    val b2bInvoices = remember(state.invoices) { state.invoices.filter { it.isB2b } }
    val b2cInvoices = remember(state.invoices) { state.invoices.filter { !it.isB2b } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("GSTR-1 Review", fontWeight = FontWeight.Black) },
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
        bottomBar = {
            BottomActionBar(
                issueCount = state.issueCount,
                canExport = !state.isLoading && state.error == null && state.issueCount == 0,
                onExportJson = onExportJson,
                onExportExcel = onExportExcel,
                onExportPdf = onExportPdf
            )
        },
        containerColor = GrayBg
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Header summary + issues
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                colors = CardDefaults.cardColors(containerColor = BgPrimary),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Business GSTIN", fontSize = 12.sp, color = TextSecondary)
                    Text(
                        state.businessGstin.ifBlank { "Not set" },
                        fontWeight = FontWeight.Black,
                        color = if (state.businessGstin.isBlank()) LossRed else TextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (state.issueCount > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = LossRed)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "${state.issueCount} issues to fix before export",
                                fontWeight = FontWeight.Bold,
                                color = LossRed
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        val top = state.validationIssues.take(2)
                        top.forEach { issue ->
                            Text("• ${issue.message}", fontSize = 12.sp, color = TextSecondary)
                        }
                        if (state.validationIssues.size > 2) {
                            Text("• …", fontSize = 12.sp, color = TextSecondary)
                        }
                    } else {
                        Text(
                            "All required fields look good for export.",
                            fontSize = 12.sp,
                            color = ProfitGreen
                        )
                    }
                }
            }

            TabRow(
                selectedTabIndex = tab,
                modifier = Modifier.padding(horizontal = 16.dp),
                containerColor = BgPrimary,
                contentColor = Blue600
            ) {
                Tab(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    text = { Text("B2B (${b2bInvoices.size})") }
                )
                Tab(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    text = { Text("B2C (${b2cInvoices.size})") }
                )
                Tab(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    text = { Text("HSN Summary") }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            when {
                state.isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                state.error != null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(state.error, color = LossRed, fontWeight = FontWeight.Bold)
                    }
                }

                tab == 2 -> {
                    HsnSummaryList(
                        rows = state.hsnSummary,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp)
                    )
                }

                else -> {
                    val list = if (tab == 0) b2bInvoices else b2cInvoices
                    InvoiceList(
                        invoices = list,
                        dateFmt = dateFmt,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        onUpdateInvoiceNumber = onUpdateInvoiceNumber,
                        onUpdateRecipientName = onUpdateRecipientName,
                        onUpdateRecipientGstin = onUpdateRecipientGstin,
                        onUpdatePlaceOfSupply = onUpdatePlaceOfSupply,
                        onUpdateLineHsn = onUpdateLineHsn,
                        onUpdateLineGstRate = onUpdateLineGstRate,
                        onUpdateLineTaxableValue = onUpdateLineTaxableValue
                    )
                }
            }
        }
    }
}

@Composable
private fun InvoiceList(
    invoices: List<EditableGstr1Invoice>,
    dateFmt: SimpleDateFormat,
    modifier: Modifier,
    onUpdateInvoiceNumber: (txId: Int, invoiceNumber: String) -> Unit,
    onUpdateRecipientName: (txId: Int, name: String) -> Unit,
    onUpdateRecipientGstin: (txId: Int, gstin: String) -> Unit,
    onUpdatePlaceOfSupply: (txId: Int, stateCode: Int) -> Unit,
    onUpdateLineHsn: (lineId: Int, hsn: String) -> Unit,
    onUpdateLineGstRate: (lineId: Int, gstRate: Double) -> Unit,
    onUpdateLineTaxableValue: (lineId: Int, taxable: Double) -> Unit
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(items = invoices, key = { it.txId }) { inv ->
            InvoiceCard(
                invoice = inv,
                dateFmt = dateFmt,
                onUpdateInvoiceNumber = onUpdateInvoiceNumber,
                onUpdateRecipientName = onUpdateRecipientName,
                onUpdateRecipientGstin = onUpdateRecipientGstin,
                onUpdatePlaceOfSupply = onUpdatePlaceOfSupply,
                onUpdateLineHsn = onUpdateLineHsn,
                onUpdateLineGstRate = onUpdateLineGstRate,
                onUpdateLineTaxableValue = onUpdateLineTaxableValue
            )
        }
        item { Spacer(modifier = Modifier.height(12.dp)) }
    }
}

@Composable
private fun InvoiceCard(
    invoice: EditableGstr1Invoice,
    dateFmt: SimpleDateFormat,
    onUpdateInvoiceNumber: (txId: Int, invoiceNumber: String) -> Unit,
    onUpdateRecipientName: (txId: Int, name: String) -> Unit,
    onUpdateRecipientGstin: (txId: Int, gstin: String) -> Unit,
    onUpdatePlaceOfSupply: (txId: Int, stateCode: Int) -> Unit,
    onUpdateLineHsn: (lineId: Int, hsn: String) -> Unit,
    onUpdateLineGstRate: (lineId: Int, gstRate: Double) -> Unit,
    onUpdateLineTaxableValue: (lineId: Int, taxable: Double) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = BgPrimary),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Invoice • ${Formatters.formatInrCurrency(invoice.invoiceTotalValue, useAbsolute = false)}",
                        fontWeight = FontWeight.Black,
                        color = TextPrimary
                    )
                    Text(
                        dateFmt.format(Date(invoice.invoiceDateMillis)),
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Hide" else "Edit")
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = invoice.invoiceNumber,
                onValueChange = { onUpdateInvoiceNumber(invoice.txId, it) },
                label = { Text("Invoice Number (unique FY)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = invoice.recipientName,
                onValueChange = { onUpdateRecipientName(invoice.txId, it) },
                label = { Text("Recipient Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = invoice.recipientGstin,
                onValueChange = { onUpdateRecipientGstin(invoice.txId, it) },
                label = { Text(if (invoice.isB2b) "Recipient GSTIN" else "Recipient GSTIN (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = if (invoice.placeOfSupplyStateCode == 0) "" else invoice.placeOfSupplyStateCode.toString(),
                onValueChange = {
                    val v = it.filter { ch -> ch.isDigit() }.take(2)
                    onUpdatePlaceOfSupply(invoice.txId, v.toIntOrNull() ?: 0)
                },
                label = { Text("Place of Supply (State Code)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            if (expanded) {
                Spacer(modifier = Modifier.height(12.dp))
                Text("Line Items", fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(modifier = Modifier.height(8.dp))

                invoice.lineItems.forEach { li ->
                    LineItemEditor(
                        line = li,
                        onUpdateLineHsn = onUpdateLineHsn,
                        onUpdateLineGstRate = onUpdateLineGstRate,
                        onUpdateLineTaxableValue = onUpdateLineTaxableValue
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }
    }
}

@Composable
private fun LineItemEditor(
    line: EditableGstr1LineItem,
    onUpdateLineHsn: (lineId: Int, hsn: String) -> Unit,
    onUpdateLineGstRate: (lineId: Int, gstRate: Double) -> Unit,
    onUpdateLineTaxableValue: (lineId: Int, taxable: Double) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Gray100),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(line.itemName, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Text("${line.qty} ${line.unit}", fontSize = 12.sp, color = TextSecondary)

            Spacer(modifier = Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = line.hsnCode,
                    onValueChange = { onUpdateLineHsn(line.lineId, it.filter(Char::isDigit).take(8)) },
                    label = { Text("HSN/SAC") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = if (line.gstRate == 0.0) "" else line.gstRate.toString(),
                    onValueChange = { onUpdateLineGstRate(line.lineId, it.toDoubleOrNull() ?: 0.0) },
                    label = { Text("GST %") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = if (line.taxableValue == 0.0) "" else line.taxableValue.toString(),
                onValueChange = { onUpdateLineTaxableValue(line.lineId, it.toDoubleOrNull() ?: 0.0) },
                label = { Text("Taxable Value") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("CGST: ${Formatters.formatInrCurrency(line.cgstAmount, fractionDigits = 2, useAbsolute = false)}", fontSize = 12.sp, color = TextSecondary)
                Text("SGST: ${Formatters.formatInrCurrency(line.sgstAmount, fractionDigits = 2, useAbsolute = false)}", fontSize = 12.sp, color = TextSecondary)
                Text("IGST: ${Formatters.formatInrCurrency(line.igstAmount, fractionDigits = 2, useAbsolute = false)}", fontSize = 12.sp, color = TextSecondary)
            }
        }
    }
}

@Composable
private fun HsnSummaryList(rows: List<Gstr1HsnSummaryRow>, modifier: Modifier) {
    if (rows.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No HSN summary yet.", color = TextSecondary)
        }
        return
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(rows, key = { it.hsnCode }) { row ->
            Card(colors = CardDefaults.cardColors(containerColor = BgPrimary), shape = RoundedCornerShape(18.dp)) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("HSN ${row.hsnCode}", fontWeight = FontWeight.Black, color = TextPrimary)
                    if (row.description.isNotBlank()) {
                        Text(row.description, fontSize = 12.sp, color = TextSecondary)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Taxable: ${Formatters.formatInrCurrency(row.taxableValue, fractionDigits = 2, useAbsolute = false)}", fontSize = 12.sp, color = TextSecondary)
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("CGST: ${Formatters.formatInrCurrency(row.cgstAmount, fractionDigits = 2, useAbsolute = false)}", fontSize = 12.sp, color = TextSecondary)
                        Text("SGST: ${Formatters.formatInrCurrency(row.sgstAmount, fractionDigits = 2, useAbsolute = false)}", fontSize = 12.sp, color = TextSecondary)
                        Text("IGST: ${Formatters.formatInrCurrency(row.igstAmount, fractionDigits = 2, useAbsolute = false)}", fontSize = 12.sp, color = TextSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomActionBar(
    issueCount: Int,
    canExport: Boolean,
    onExportJson: () -> Unit,
    onExportExcel: () -> Unit,
    onExportPdf: () -> Unit
) {
    Surface(color = BgPrimary, tonalElevation = 4.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (issueCount > 0) {
                Text(
                    "$issueCount issues",
                    color = LossRed,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
            } else {
                Text(
                    "Ready to export",
                    color = ProfitGreen,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
            }

            KiranaButton(
                text = "Export JSON",
                onClick = onExportJson,
                enabled = canExport,
                colors = ButtonDefaults.buttonColors(containerColor = Blue600, contentColor = BgPrimary)
            )
            OutlinedButton(onClick = onExportExcel, enabled = canExport) {
                Text("Export Excel")
            }
            OutlinedButton(onClick = onExportPdf, enabled = canExport) {
                Text("Export PDF")
            }
        }
    }
}


