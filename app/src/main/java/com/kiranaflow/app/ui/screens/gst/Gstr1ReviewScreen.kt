package com.kiranaflow.app.ui.screens.gst

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiranaflow.app.ui.components.KiranaButton
import com.kiranaflow.app.ui.theme.*
import com.kiranaflow.app.util.Formatters
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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

    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showIssuesSheet by remember { mutableStateOf(false) }

    // Control expansion so we can programmatically "Fix -> open invoice".
    var expandedTxIds by remember { mutableStateOf(setOf<Int>()) }
    var highlight by remember { mutableStateOf<Pair<Int, Int?>?>(null) } // txId -> lineId?

    val listState = rememberLazyListState()

    fun invoiceTabFor(txId: Int): Int? {
        val inv = state.invoices.firstOrNull { it.txId == txId } ?: return null
        return if (inv.isB2b) 0 else 1
    }

    suspend fun scrollToInvoice(txId: Int) {
        // We render header (1 item) and sticky tabs (1 item), then invoices.
        val invoices = if (tab == 0) b2bInvoices else b2cInvoices
        val idx = invoices.indexOfFirst { it.txId == txId }
        if (idx >= 0) {
            // +1 accounts for the header card item; stickyHeader doesn't count as an item index.
            listState.animateScrollToItem(index = 1 + idx)
        }
    }

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
                canExport = !state.isLoading && state.error == null && state.issueCount == 0,
                onExportJson = onExportJson,
                onExportExcel = onExportExcel,
                onExportPdf = onExportPdf
            )
        },
        containerColor = GrayBg
    ) { innerPadding ->
        if (showIssuesSheet) {
            ModalBottomSheet(
                onDismissRequest = { showIssuesSheet = false },
                sheetState = sheetState,
                containerColor = BgPrimary
            ) {
                IssuesBreakdownSheet(
                    issues = state.validationIssues,
                    onOpenSettings = {
                        showIssuesSheet = false
                        onOpenSettings()
                    },
                    onFixInReview = { issue ->
                        val txId = issue.txId ?: return@IssuesBreakdownSheet
                        val targetTab = invoiceTabFor(txId) ?: return@IssuesBreakdownSheet
                        showIssuesSheet = false
                        tab = targetTab
                        expandedTxIds = expandedTxIds + txId
                        highlight = txId to issue.lineId
                        scope.launch { scrollToInvoice(txId) }
                    }
                )
            }
        }

        when {
            state.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            state.error != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(state.error, color = LossRed, fontWeight = FontWeight.Bold)
                }
            }

            else -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .imePadding(),
                    contentPadding = PaddingValues(bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        // Header summary + issues (scrolls away as you scroll invoices)
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
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable { showIssuesSheet = true }
                                            .padding(vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = LossRed)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            "${state.issueCount} issues to fix before export",
                                            fontWeight = FontWeight.Bold,
                                            color = LossRed,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Icon(Icons.Default.Info, contentDescription = null, tint = TextSecondary)
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    val top = state.validationIssues.take(2)
                                    top.forEach { issue ->
                                        Text("• ${issue.message}", fontSize = 12.sp, color = TextSecondary)
                                    }
                                    if (state.validationIssues.size > 2) {
                                        Text("• Tap above to see all", fontSize = 12.sp, color = TextSecondary)
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
                    }

                    stickyHeader {
                        Column(modifier = Modifier.background(GrayBg)) {
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
                        }
                    }

                    when (tab) {
                        2 -> {
                            if (state.hsnSummary.isEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 24.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("No HSN summary yet.", color = TextSecondary)
                                    }
                                }
                            } else {
                                items(state.hsnSummary, key = { it.hsnCode }) { row ->
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = BgPrimary),
                                        shape = RoundedCornerShape(18.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp)) {
                                            Text("HSN ${row.hsnCode}", fontWeight = FontWeight.Black, color = TextPrimary)
                                            if (row.description.isNotBlank()) {
                                                Text(row.description, fontSize = 12.sp, color = TextSecondary)
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                "Taxable: ${Formatters.formatInrCurrency(row.taxableValue, fractionDigits = 2, useAbsolute = false)}",
                                                fontSize = 12.sp,
                                                color = TextSecondary
                                            )
                                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                                Text(
                                                    "CGST: ${Formatters.formatInrCurrency(row.cgstAmount, fractionDigits = 2, useAbsolute = false)}",
                                                    fontSize = 12.sp,
                                                    color = TextSecondary
                                                )
                                                Text(
                                                    "SGST: ${Formatters.formatInrCurrency(row.sgstAmount, fractionDigits = 2, useAbsolute = false)}",
                                                    fontSize = 12.sp,
                                                    color = TextSecondary
                                                )
                                                Text(
                                                    "IGST: ${Formatters.formatInrCurrency(row.igstAmount, fractionDigits = 2, useAbsolute = false)}",
                                                    fontSize = 12.sp,
                                                    color = TextSecondary
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        else -> {
                            val list = if (tab == 0) b2bInvoices else b2cInvoices
                            items(items = list, key = { it.txId }) { inv ->
                                InvoiceCard(
                                    invoice = inv,
                                    dateFmt = dateFmt,
                                    isExpanded = expandedTxIds.contains(inv.txId),
                                    highlightLineId = if (highlight?.first == inv.txId) highlight?.second else null,
                                    onToggleExpanded = { txId ->
                                        expandedTxIds =
                                            if (expandedTxIds.contains(txId)) expandedTxIds - txId else expandedTxIds + txId
                                        if (expandedTxIds.contains(txId).not()) {
                                            if (highlight?.first == txId) highlight = null
                                        }
                                    },
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
                }
            }
        }
    }
}

@Composable
private fun InvoiceCard(
    invoice: EditableGstr1Invoice,
    dateFmt: SimpleDateFormat,
    isExpanded: Boolean,
    highlightLineId: Int?,
    onToggleExpanded: (txId: Int) -> Unit,
    onUpdateInvoiceNumber: (txId: Int, invoiceNumber: String) -> Unit,
    onUpdateRecipientName: (txId: Int, name: String) -> Unit,
    onUpdateRecipientGstin: (txId: Int, gstin: String) -> Unit,
    onUpdatePlaceOfSupply: (txId: Int, stateCode: Int) -> Unit,
    onUpdateLineHsn: (lineId: Int, hsn: String) -> Unit,
    onUpdateLineGstRate: (lineId: Int, gstRate: Double) -> Unit,
    onUpdateLineTaxableValue: (lineId: Int, taxable: Double) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = BgPrimary),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
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
                TextButton(onClick = { onToggleExpanded(invoice.txId) }) {
                    Text(if (isExpanded) "Hide" else "Edit")
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

            if (isExpanded) {
                Spacer(modifier = Modifier.height(12.dp))
                Text("Line Items", fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(modifier = Modifier.height(8.dp))

                invoice.lineItems.forEach { li ->
                    LineItemEditor(
                        line = li,
                        isHighlighted = highlightLineId != null && highlightLineId == li.lineId,
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
    isHighlighted: Boolean,
    onUpdateLineHsn: (lineId: Int, hsn: String) -> Unit,
    onUpdateLineGstRate: (lineId: Int, gstRate: Double) -> Unit,
    onUpdateLineTaxableValue: (lineId: Int, taxable: Double) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = if (isHighlighted) WarningYellow.copy(alpha = 0.25f) else Gray100),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(line.itemName, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            val unitUp = line.unit.trim().uppercase()
            val qtyTxt = if (unitUp == "KG" || unitUp == "KGS") {
                String.format("%.3f", line.qty).trimEnd('0').trimEnd('.')
            } else {
                line.qty.toInt().toString()
            }
            Text("$qtyTxt ${line.unit}", fontSize = 12.sp, color = TextSecondary)

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
private fun BottomActionBar(
    canExport: Boolean,
    onExportJson: () -> Unit,
    onExportExcel: () -> Unit,
    onExportPdf: () -> Unit
) {
    // Button-only bottom area: no background panel behind the export action.
    var menuOpen by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Column {
            KiranaButton(
                text = "Export",
                onClick = { menuOpen = true },
                enabled = canExport,
                colors = ButtonDefaults.buttonColors(containerColor = Blue600, contentColor = BgPrimary)
            )

            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false }
            ) {
                DropdownMenuItem(
                    text = { Text("JSON (.json)") },
                    onClick = { menuOpen = false; onExportJson() }
                )
                DropdownMenuItem(
                    text = { Text("Excel (.xlsx)") },
                    onClick = { menuOpen = false; onExportExcel() }
                )
                DropdownMenuItem(
                    text = { Text("PDF (.pdf)") },
                    onClick = { menuOpen = false; onExportPdf() }
                )
            }
        }
    }
}

private data class IssueGuide(
    val title: String,
    val why: String,
    val where: String,
    val primaryActionLabel: String,
    val action: Action
) {
    sealed class Action {
        data object OpenSettings : Action()
        data object FixInReview : Action()
    }
}

private fun issueGuide(issue: Gstr1ValidationIssue): IssueGuide {
    return when (issue.code) {
        Gstr1ValidationIssue.Code.BUSINESS_GSTIN_MISSING -> IssueGuide(
            title = "Missing Business GSTIN",
            why = "GSTR-1 export requires your GSTIN to identify the supplier on GST portal.",
            where = "In-app: Settings → GST/Business Details",
            primaryActionLabel = "Open Settings",
            action = IssueGuide.Action.OpenSettings
        )
        Gstr1ValidationIssue.Code.BUSINESS_GSTIN_INVALID -> IssueGuide(
            title = "Business GSTIN looks invalid",
            why = "Checksum mismatch means the GST portal may reject your upload.",
            where = "In-app: Settings → GST/Business Details (verify against GST certificate).",
            primaryActionLabel = "Open Settings",
            action = IssueGuide.Action.OpenSettings
        )
        Gstr1ValidationIssue.Code.BUSINESS_LEGAL_NAME_MISSING -> IssueGuide(
            title = "Missing Legal Name",
            why = "Some exports/attachments need supplier legal name for compliance.",
            where = "In-app: Settings → GST/Business Details",
            primaryActionLabel = "Open Settings",
            action = IssueGuide.Action.OpenSettings
        )
        Gstr1ValidationIssue.Code.BUSINESS_STATE_CODE_MISSING -> IssueGuide(
            title = "Missing State Code",
            why = "State code determines intra/inter-state tax classification and reporting fields.",
            where = "In-app: Settings → GST/Business Details",
            primaryActionLabel = "Open Settings",
            action = IssueGuide.Action.OpenSettings
        )
        Gstr1ValidationIssue.Code.INVOICE_NUMBER_MISSING -> IssueGuide(
            title = "Invoice number missing",
            why = "Invoice number is mandatory and must be unique for the financial year.",
            where = "In-app: GST Reports → GSTR-1 Review → Invoice edit",
            primaryActionLabel = "Fix in Review",
            action = IssueGuide.Action.FixInReview
        )
        Gstr1ValidationIssue.Code.INVOICE_NUMBER_DUPLICATE -> IssueGuide(
            title = "Duplicate invoice number",
            why = "GST export requires unique invoice numbers; duplicates can cause rejection.",
            where = "In-app: GST Reports → GSTR-1 Review → Invoice edit",
            primaryActionLabel = "Fix in Review",
            action = IssueGuide.Action.FixInReview
        )
        Gstr1ValidationIssue.Code.INVOICE_PLACE_OF_SUPPLY_MISSING -> IssueGuide(
            title = "Place of supply missing",
            why = "Needed to correctly classify IGST vs CGST/SGST and populate GSTR-1 fields.",
            where = "In-app: GST Reports → GSTR-1 Review → Invoice edit",
            primaryActionLabel = "Fix in Review",
            action = IssueGuide.Action.FixInReview
        )
        Gstr1ValidationIssue.Code.INVOICE_B2B_RECIPIENT_GSTIN_MISSING -> IssueGuide(
            title = "Recipient GSTIN missing (B2B)",
            why = "B2B invoices require recipient GSTIN for GST portal matching.",
            where = "In-app: GST Reports → GSTR-1 Review → Invoice edit",
            primaryActionLabel = "Fix in Review",
            action = IssueGuide.Action.FixInReview
        )
        Gstr1ValidationIssue.Code.INVOICE_B2B_RECIPIENT_GSTIN_INVALID -> IssueGuide(
            title = "Recipient GSTIN looks invalid",
            why = "Invalid GSTIN can cause portal validation errors.",
            where = "In-app: GST Reports → GSTR-1 Review → Invoice edit (verify with customer).",
            primaryActionLabel = "Fix in Review",
            action = IssueGuide.Action.FixInReview
        )
        Gstr1ValidationIssue.Code.LINE_HSN_MISSING -> IssueGuide(
            title = "HSN/SAC missing for a line item",
            why = "HSN is required for HSN summary and GST reporting.",
            where = "In-app: GST Reports → GSTR-1 Review → Expand invoice → Line items",
            primaryActionLabel = "Fix in Review",
            action = IssueGuide.Action.FixInReview
        )
        Gstr1ValidationIssue.Code.LINE_GST_RATE_INVALID -> IssueGuide(
            title = "Invalid GST %",
            why = "GST rate must be 0 or positive; invalid values can break tax calculations/export.",
            where = "In-app: GST Reports → GSTR-1 Review → Expand invoice → Line items",
            primaryActionLabel = "Fix in Review",
            action = IssueGuide.Action.FixInReview
        )
        Gstr1ValidationIssue.Code.LINE_TAXABLE_VALUE_MISSING -> IssueGuide(
            title = "Taxable value missing",
            why = "Taxable value is needed to compute GST amounts and populate export fields.",
            where = "In-app: GST Reports → GSTR-1 Review → Expand invoice → Line items",
            primaryActionLabel = "Fix in Review",
            action = IssueGuide.Action.FixInReview
        )
    }
}

@Composable
private fun IssuesBreakdownSheet(
    issues: List<Gstr1ValidationIssue>,
    onOpenSettings: () -> Unit,
    onFixInReview: (Gstr1ValidationIssue) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp)
    ) {
        Text("Issues & Fixes", fontWeight = FontWeight.Black, fontSize = 18.sp, color = TextPrimary)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            "Tap Fix to jump to the right place. Some fields need external verification (e.g., GSTIN from certificate).",
            fontSize = 12.sp,
            color = TextSecondary
        )
        Spacer(modifier = Modifier.height(12.dp))

        if (issues.isEmpty()) {
            Text("No issues found.", color = ProfitGreen, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(24.dp))
            return
        }

        issues.forEach { issue ->
            val guide = remember(issue.code) { issueGuide(issue) }
            Card(
                colors = CardDefaults.cardColors(containerColor = Gray100),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(guide.title, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(issue.message, fontSize = 12.sp, color = TextSecondary)
                    Spacer(modifier = Modifier.height(8.dp))

                    Text("Why this matters", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text(guide.why, fontSize = 12.sp, color = TextSecondary)
                    Spacer(modifier = Modifier.height(8.dp))

                    Text("Where to fix", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text(guide.where, fontSize = 12.sp, color = TextSecondary)

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = {
                                when (guide.action) {
                                    IssueGuide.Action.OpenSettings -> onOpenSettings()
                                    IssueGuide.Action.FixInReview -> onFixInReview(issue)
                                }
                            }
                        ) {
                            Text(guide.primaryActionLabel)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}


