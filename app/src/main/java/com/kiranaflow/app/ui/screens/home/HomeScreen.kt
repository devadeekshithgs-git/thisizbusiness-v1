package com.kiranaflow.app.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kiranaflow.app.ui.screens.home.DashboardViewModel
import com.kiranaflow.app.ui.components.*
import com.kiranaflow.app.ui.components.dialogs.DateRangePickerDialog
import com.kiranaflow.app.ui.theme.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kiranaflow.app.data.local.ShopSettingsStore
import com.kiranaflow.app.data.local.KiranaDatabase
import com.kiranaflow.app.data.local.AppPrefs
import com.kiranaflow.app.data.local.AppPrefsStore
import com.kiranaflow.app.data.repository.KiranaRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.kiranaflow.app.util.Formatters
import com.kiranaflow.app.util.BiometricAuth
import androidx.compose.foundation.text.selection.SelectionContainer

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun HomeScreen(
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = viewModel(),
    onSettingsClick: () -> Unit = {},
    onViewAllTransactions: () -> Unit = {},
    onOpenTransaction: (Int) -> Unit = {},
    onOpenGstReports: () -> Unit = {}
) {
    // #region agent log
    com.kiranaflow.app.util.DebugLogger.log("HomeScreen.kt:47", "HomeScreen composable entered", mapOf(), "H6")
    // #endregion
    
    val state by viewModel.state.collectAsState()
    // #region agent log
    com.kiranaflow.app.util.DebugLogger.log("HomeScreen.kt:51", "State collected", mapOf("revenue" to state.revenue, "expense" to state.expense), "H6")
    // #endregion
    val scrollState = rememberScrollState()
    var showDateRangePicker by remember { mutableStateOf(false) }
    var showRangeDropdown by remember { mutableStateOf(false) }
    var showAddReminder by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val repo = remember(context) { KiranaRepository(KiranaDatabase.getDatabase(context)) }
    val appPrefsStore = remember(context) { AppPrefsStore(context) }
    val appPrefs by appPrefsStore.prefs.collectAsState(initial = AppPrefs())
    // Track whether numbers are revealed (tap to toggle)
    var numbersRevealed by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    data class ExpandedPnl(val label: String, val amount: Double, val valueColor: Color)
    var expandedPnl by remember { mutableStateOf<ExpandedPnl?>(null) }

    @Composable
    fun PnlCard(
        label: String,
        amount: Double,
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        valueColor: Color,
        bg: Color,
        modifier: Modifier = Modifier
    ) {
        Card(
            modifier = modifier
                .height(86.dp)
                .clickable {
                    // Simple tap to toggle reveal, or open expanded view if already revealed
                    if (!numbersRevealed) {
                        numbersRevealed = true
                    } else {
                        expandedPnl = ExpandedPnl(label = label, amount = amount, valueColor = valueColor)
                    }
                },
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = bg),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.65f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = valueColor, modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(label, color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    val raw = Formatters.formatInrCurrency(amount, fractionDigits = 0, useAbsolute = true)
                    val display = if (numbersRevealed) raw else Formatters.maskDigits(raw)
                    Text(
                        text = display,
                        color = valueColor,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1
                    )
                }
            }
        }
    }

    // Full-screen expanded amount viewer (for very large values).
    expandedPnl?.let { exp ->
        val raw = Formatters.formatInrCurrency(exp.amount, fractionDigits = 0, useAbsolute = true)
        Dialog(
            onDismissRequest = { expandedPnl = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = BgPrimary
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 48.dp, start = 16.dp, end = 16.dp, bottom = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(exp.label, fontWeight = FontWeight.Black, fontSize = 20.sp, color = TextPrimary)
                        IconButton(onClick = { expandedPnl = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = White),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SelectionContainer {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = raw,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 34.sp,
                                        color = exp.valueColor,
                                        maxLines = 1
                                    )
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedButton(
                                    onClick = { clipboard.setText(AnnotatedString(raw)) },
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Copy", fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = { expandedPnl = null },
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Text("Close", fontWeight = FontWeight.Bold)
                                }
                            }
                            Text(
                                text = "Tip: tap Revenue/Expense/Profit cards to expand.",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgCard)
            .verticalScroll(scrollState)
            .padding(bottom = 100.dp) // Space for BottomNav
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 48.dp, start = 16.dp, end = 16.dp, bottom = 24.dp), 
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "${state.greeting},",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = TextSecondary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        fontSize = 12.sp
                    )
                )
                Text(
                    text = state.ownerName,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Black,
                        color = TextPrimary,
                        fontSize = 24.sp
                    )
                )
            }
            SettingsIconButton(onClick = onSettingsClick)
        }

        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "Profit & Loss",
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            fontSize = 18.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PnlCard(
                label = "Revenue",
                amount = state.revenue,
                icon = Icons.Default.AccountBalance,
                valueColor = Blue600,
                bg = BgPrimary,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "−",
                color = TextSecondary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black
            )
            PnlCard(
                label = "Expense",
                amount = state.expense,
                icon = Icons.Default.ArrowOutward,
                valueColor = LossRed,
                bg = BgPrimary,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        val isLoss = state.netProfit < 0
        PnlCard(
            label = if (isLoss) "Loss" else "Profit",
            amount = state.netProfit,
            icon = if (isLoss) Icons.Default.TrendingDown else Icons.Default.TrendingUp,
            valueColor = if (isLoss) LossRed else ProfitGreen,
            bg = BgPrimary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(14.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(containerColor = BgPrimary),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            onClick = onOpenGstReports
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = Blue600)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("GST Reports", fontWeight = FontWeight.Black, color = TextPrimary)
                        Text(
                            "Export GSTR-1 (JSON + Excel)",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            maxLines = 1
                        )
                    }
                }
                Text(
                    "Open",
                    fontWeight = FontWeight.Bold,
                    color = Blue600
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Reminders + Expiry alerts (C6)
        if (state.reminders.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Reminders", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 18.sp)
                TextButton(onClick = { showAddReminder = true }) { Text("Add", color = Blue600, fontWeight = FontWeight.Bold) }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                state.reminders.take(5).forEach { r ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = BgPrimary),
                        shape = RoundedCornerShape(14.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(r.title, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1)
                                val due = remember(r.dueAt) {
                                    SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(r.dueAt))
                                }
                                Text(due, color = TextSecondary, fontSize = 12.sp)
                                if (!r.note.isNullOrBlank()) {
                                    Text(r.note.orEmpty(), color = TextSecondary, fontSize = 12.sp, maxLines = 1)
                                }
                            }
                            Checkbox(
                                checked = false,
                                onCheckedChange = { viewModel.markReminderDone(r.id) }
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Reminders", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 18.sp)
                TextButton(onClick = { showAddReminder = true }) { Text("Add", color = Blue600, fontWeight = FontWeight.Bold) }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (state.expiringItems.isNotEmpty()) {
            Text(
                "Expiring soon",
                modifier = Modifier.padding(horizontal = 16.dp),
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                fontSize = 18.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                state.expiringItems.take(5).forEach { item ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = LossRedBg),
                        shape = RoundedCornerShape(14.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(item.name, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1)
                            val exp = item.expiryDateMillis ?: 0L
                            val expLabel = remember(exp) {
                                SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(exp))
                            }
                            Text("Expiry: $expLabel", color = LossRed, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        // Sales Trend Section
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = BgPrimary),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) { 
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp), 
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Sales Trend",
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontSize = 16.sp
                    )

                    val rangeLabel = when (state.selectedTimeRange) {
                        "TODAY" -> "Today"
                        "7D" -> "7D"
                        "1M" -> "1M"
                        "3M" -> "3M"
                        "6M" -> "6M"
                        "1Y" -> "1Y"
                        "CUSTOM" -> "Custom"
                        else -> "7D"
                    }
                    ExposedDropdownMenuBox(
                        expanded = showRangeDropdown,
                        onExpandedChange = { showRangeDropdown = !showRangeDropdown }
                    ) {
                        OutlinedTextField(
                            value = rangeLabel,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showRangeDropdown) },
                            modifier = Modifier
                                .widthIn(min = 120.dp)
                                .menuAnchor(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = White,
                                unfocusedContainerColor = White,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )
                        ExposedDropdownMenu(
                            expanded = showRangeDropdown,
                            onDismissRequest = { showRangeDropdown = false }
                        ) {
                            listOf(
                                "TODAY" to "Today",
                                "7D" to "7D",
                                "1M" to "1M",
                                "3M" to "3M",
                                "6M" to "6M",
                                "1Y" to "1Y",
                                "CUSTOM" to "Custom"
                            ).forEach { (key, label) ->
                                DropdownMenuItem(
                                    text = { Text(label, color = TextPrimary) },
                                    onClick = {
                                        showRangeDropdown = false
                                        if (key == "CUSTOM") {
                                            showDateRangePicker = true
                                        } else {
                                            viewModel.selectTimeRange(key)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
                SalesTrendChart(
                    data = state.chartData,
                    isPositiveTrend = state.netProfit >= 0
                )

                // Stock-style summary for the chart range
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val pct = state.salesChangePercent
                    val delta = state.salesChangeAmount
                    val isUp = state.salesChangeIsUp
                    val deltaColor = if (isUp) ProfitGreen else LossRed

                    val pctLabel = if (pct == null) {
                        if (delta == 0.0) "0.0%" else "New"
                    } else {
                        val v = kotlin.math.abs(pct)
                        (if (pct >= 0) "+" else "-") + String.format(Locale.getDefault(), "%.1f", v) + "%"
                    }
                    val deltaLabel = (if (delta >= 0.0) "+" else "-") + "₹" + kotlin.math.abs(delta).toInt()

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isUp) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                            contentDescription = null,
                            tint = deltaColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        val comparisonLabel = when (state.selectedTimeRange) {
                            "TODAY" -> "compared to yesterday"
                            "7D" -> "compared to the previous 7 days"
                            "1M" -> "compared to the previous 30 days"
                            "3M" -> "compared to the previous 90 days"
                            "6M" -> "compared to the previous 180 days"
                            "1Y" -> "compared to the previous 365 days"
                            "CUSTOM" -> "compared to the previous same number of days"
                            else -> "compared to the previous period"
                        }
                        Text(
                            "$pctLabel  $comparisonLabel",
                            color = deltaColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }

                    Text(deltaLabel, color = deltaColor, fontWeight = FontWeight.Black, fontSize = 12.sp)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Recent Transactions
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Recent Transactions",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                ),
                color = TextPrimary 
            )
            TextButton(onClick = onViewAllTransactions) {
                Text(
                    "View All",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Blue600
                ) 
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        
        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            state.recentTransactions.forEach { tx ->
                TransactionCard(
                    transaction = tx,
                    onClick = { onOpenTransaction(tx.id) }
                )
            }
        }
    }

    // Date Range Picker Dialog
    if (showDateRangePicker) {
        DateRangePickerDialog(
            onDismiss = { showDateRangePicker = false },
            onApply = { startDate, endDate ->
                viewModel.selectDateRange(startDate, endDate)
                showDateRangePicker = false
            }
        )
    }

    if (showAddReminder) {
        var title by remember { mutableStateOf("Reminder") }
        var note by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddReminder = false },
            title = { Text("Add reminder", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding()
                        .heightIn(max = 520.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Title") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = White,
                            unfocusedContainerColor = White,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("Note (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = White,
                            unfocusedContainerColor = White,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                    Text("This will be due tomorrow by default.", color = TextSecondary, fontSize = 12.sp)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val dueAt = System.currentTimeMillis() + (24L * 60L * 60L * 1000L)
                        val cleanTitle = title.trim()
                        if (cleanTitle.isNotBlank()) {
                            val cleanNote = note.trim().ifBlank { null }
                            scope.launch { repo.addReminder("GENERAL", null, cleanTitle, dueAt, cleanNote) }
                        }
                        showAddReminder = false
                    },
                    enabled = title.trim().isNotBlank()
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showAddReminder = false }) { Text("Cancel") } }
        )
    }
}
