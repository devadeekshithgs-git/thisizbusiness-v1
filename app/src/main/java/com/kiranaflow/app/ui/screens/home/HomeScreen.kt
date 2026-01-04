package com.kiranaflow.app.ui.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kiranaflow.app.ui.screens.home.DashboardViewModel
import com.kiranaflow.app.ui.components.*
import com.kiranaflow.app.ui.components.dialogs.DateRangePickerDialog
import com.kiranaflow.app.ui.theme.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kiranaflow.app.data.local.ShopSettingsStore
import com.kiranaflow.app.data.local.KiranaDatabase
import com.kiranaflow.app.data.local.AppPrefs
import com.kiranaflow.app.data.local.AppPrefsStore
import com.kiranaflow.app.data.local.ReminderEntity
import com.kiranaflow.app.data.repository.KiranaRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.kiranaflow.app.util.Formatters
import androidx.compose.foundation.text.selection.SelectionContainer

// Used for inline Profit & Loss info expansion UI.
private enum class PnlInfoKey { REVENUE, COGS, EXPENSE }
// P&L numbers privacy shutter: hidden by default, long-press to reveal/hide per card.
private enum class PnlPrivacyKey { REVENUE, COGS, EXPENSE, PROFIT }

@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
fun HomeScreen(
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = viewModel(),
    onSettingsClick: () -> Unit = {},
    onViewAllTransactions: () -> Unit = {},
    onOpenTransaction: (Int) -> Unit = {},
    onNavigateToReports: () -> Unit = {}
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
    var reminderToConfirm by remember { mutableStateOf<ReminderEntity?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val repo = remember(context) { KiranaRepository(KiranaDatabase.getDatabase(context)) }
    val appPrefsStore = remember(context) { AppPrefsStore(context) }
    val appPrefs by appPrefsStore.prefs.collectAsState(initial = AppPrefs())
    var revealedPnlKeys by remember { mutableStateOf(setOf<PnlPrivacyKey>()) }
    var expandedPnlInfo by remember { mutableStateOf<PnlInfoKey?>(null) }
    val clipboard = LocalClipboardManager.current

    data class ExpandedPnl(
        val key: PnlPrivacyKey,
        val label: String,
        val amount: Double,
        val valueColor: Color,
        val useAbsoluteAmount: Boolean
    )
    var expandedPnl by remember { mutableStateOf<ExpandedPnl?>(null) }

    @Composable
    fun PnlCard(
        privacyKey: PnlPrivacyKey,
        label: String,
        amount: Double,
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        valueColor: Color,
        bg: Color,
        useAbsoluteAmount: Boolean = true,
        showInfoIcon: Boolean = false,
        onInfoClick: (() -> Unit)? = null,
        modifier: Modifier = Modifier
    ) {
        val isRevealed = revealedPnlKeys.contains(privacyKey)
        Card(
            modifier = modifier
                .height(100.dp)
                .combinedClickable(
                    onClick = {
                        // Tap opens fullscreen ONLY if already revealed.
                        if (isRevealed) {
                        expandedPnl = ExpandedPnl(
                            key = privacyKey,
                            label = label,
                            amount = amount,
                            valueColor = valueColor,
                            useAbsoluteAmount = useAbsoluteAmount
                        )
                        }
                    },
                    onLongClick = {
                        // Long press toggles privacy shutter (reveal/hide).
                        val willReveal = !isRevealed
                        revealedPnlKeys = if (willReveal) {
                            revealedPnlKeys + privacyKey
                        } else {
                            revealedPnlKeys - privacyKey
                        }
                        // If user hides the card while fullscreen is open for it, close fullscreen.
                        if (!willReveal && expandedPnl?.key == privacyKey) {
                            expandedPnl = null
                        }
                    }
                ),
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = label,
                            color = TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            modifier = Modifier.weight(1f)
                        )
                        if (showInfoIcon && onInfoClick != null) {
                            IconButton(
                                onClick = onInfoClick,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Info",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    val raw = Formatters.formatInrCurrency(amount, fractionDigits = 0, useAbsolute = useAbsoluteAmount)
                    val display = if (isRevealed) raw else Formatters.maskDigits(raw)
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

    @Composable
    fun EquationSymbolRow(symbol: String) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = symbol,
                color = TextSecondary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black
            )
        }
    }

    @Composable
    fun PnlInfoInlineCard(text: String) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = White),
            shape = RoundedCornerShape(14.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Text(
                text = text,
                color = TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(12.dp)
            )
        }
    }

    // Full-screen expanded amount viewer (for very large values).
    expandedPnl?.let { exp ->
        val raw = Formatters.formatInrCurrency(exp.amount, fractionDigits = 0, useAbsolute = exp.useAbsoluteAmount)
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
                                text = "Tip: long-press to reveal. Tap to expand.",
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
        // Header Section (match global header dimensions + rounded corners used across other screens)
        Surface(
            color = tabCapsuleColor("home"),
            shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp),
            tonalElevation = 0.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(132.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 18.dp, start = 16.dp, end = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(
                        text = "${state.greeting},",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = Color.White.copy(alpha = 0.85f),
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            fontSize = 13.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = state.ownerName.ifBlank { "Owner" },
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            fontSize = 26.sp
                        )
                    )
                }

                IconButton(
                    onClick = onSettingsClick,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.2f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // =================== SECTION 1: REMINDERS ===================
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Reminders", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 18.sp)
            TextButton(onClick = { showAddReminder = true }) { Text("Add", color = Blue600, fontWeight = FontWeight.Bold) }
        }
        Spacer(modifier = Modifier.height(8.dp))
        
        if (state.reminders.isNotEmpty()) {
            Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                state.reminders.take(10).forEach { r ->
                    ReminderCard(
                        reminder = r,
                        onCompleteClick = { reminderToConfirm = r },
                        onDismissClick = { viewModel.dismissReminder(r.id) }
                    )
                }
            }
        } else {
            Card(
                colors = CardDefaults.cardColors(containerColor = BgPrimary),
                shape = RoundedCornerShape(14.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = ProfitGreen, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("No pending reminders", color = TextSecondary, fontWeight = FontWeight.Medium)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))

        // =================== SECTION 2: RECENT TRANSACTIONS ===================
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

        Spacer(modifier = Modifier.height(24.dp))

        // =================== SECTION 3: PROFIT & LOSS ===================
        Text(
            text = "Profit & Loss",
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            fontSize = 18.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(10.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PnlCard(
                privacyKey = PnlPrivacyKey.REVENUE,
                label = "Revenue",
                amount = state.revenue,
                icon = Icons.Default.AccountBalance,
                valueColor = Blue600,
                bg = BgPrimary,
                showInfoIcon = true,
                onInfoClick = {
                    expandedPnlInfo = if (expandedPnlInfo == PnlInfoKey.REVENUE) null else PnlInfoKey.REVENUE
                },
                modifier = Modifier.fillMaxWidth()
            )
            AnimatedVisibility(
                visible = expandedPnlInfo == PnlInfoKey.REVENUE,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                PnlInfoInlineCard(
                    text = "Revenue = money you earned.\n\nWe add up all Sales + Income transactions. We ignore customer/vendor \"Payment …\" entries so it doesn't get counted twice."
                )
            }

            EquationSymbolRow("−")

            PnlCard(
                privacyKey = PnlPrivacyKey.COGS,
                label = "Stock Cost (COGS)",
                amount = state.cogs,
                icon = Icons.Default.Inventory2,
                valueColor = AlertOrange,
                bg = BgPrimary,
                showInfoIcon = true,
                onInfoClick = {
                    expandedPnlInfo = if (expandedPnlInfo == PnlInfoKey.COGS) null else PnlInfoKey.COGS
                },
                modifier = Modifier.fillMaxWidth()
            )
            AnimatedVisibility(
                visible = expandedPnlInfo == PnlInfoKey.COGS,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                PnlInfoInlineCard(
                    text = "Stock Cost (COGS) = cost of items you sold.\n\nPackets/Pieces: cost price (per piece/packet) × pieces sold.\nLoose items: cost price per kg × kg sold.\n\nWeight is stored and calculated in kg across the app."
                )
            }

            EquationSymbolRow("−")

            PnlCard(
                privacyKey = PnlPrivacyKey.EXPENSE,
                label = "Expense",
                amount = state.expense,
                icon = Icons.Default.ArrowOutward,
                valueColor = LossRed,
                bg = BgPrimary,
                showInfoIcon = true,
                onInfoClick = {
                    expandedPnlInfo = if (expandedPnlInfo == PnlInfoKey.EXPENSE) null else PnlInfoKey.EXPENSE
                },
                modifier = Modifier.fillMaxWidth()
            )
            AnimatedVisibility(
                visible = expandedPnlInfo == PnlInfoKey.EXPENSE,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                PnlInfoInlineCard(
                    text = "Expense = money you spent to run the shop.\n\nWe add up all Expense transactions. We ignore customer/vendor \"Payment …\" entries so it doesn't get counted twice."
                )
            }

            EquationSymbolRow("=")

            val isLoss = state.netProfit < 0
            PnlCard(
                privacyKey = PnlPrivacyKey.PROFIT,
                label = if (isLoss) "Loss" else "Profit",
                amount = state.netProfit,
                icon = if (isLoss) Icons.Default.TrendingDown else Icons.Default.TrendingUp,
                valueColor = if (isLoss) LossRed else ProfitGreen,
                bg = BgPrimary,
                useAbsoluteAmount = false,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Expiring soon alerts
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

        // =================== SECTION 4: SALES TREND CHART ===================
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
                    // Chart color should follow the sales trend for the selected range,
                    // not the all-time P&L number.
                    isPositiveTrend = state.salesChangeIsUp
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

        // =================== SECTION 5: REPORTS ===================
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clickable { onNavigateToReports() },
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = BgPrimary),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Blue600.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = Blue600,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Reports",
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "Export business data & analytics",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                    
                    IconButton(
                        onClick = onNavigateToReports
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = "Go to Reports",
                            tint = TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

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

    // Confirmation dialog for completing a reminder
    reminderToConfirm?.let { reminder ->
        AlertDialog(
            onDismissRequest = { reminderToConfirm = null },
            title = { Text("Complete Task?", fontWeight = FontWeight.Bold, color = TextPrimary) },
            text = {
                Column {
                    Text(
                        "Mark this task as completed?",
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = BgCard),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(reminder.title, fontWeight = FontWeight.Bold, color = TextPrimary)
                            if (!reminder.note.isNullOrBlank()) {
                                Text(reminder.note.orEmpty(), color = TextSecondary, fontSize = 12.sp)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Completed tasks will be struck through and auto-dismiss after 24 hours.",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.markReminderDone(reminder.id)
                        reminderToConfirm = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ProfitGreen)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Yes, Complete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { reminderToConfirm = null }) {
                    Text("Cancel", color = TextSecondary, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = BgPrimary,
            shape = RoundedCornerShape(20.dp)
        )
    }
}

/**
 * Reminder card with strike-through animation for completed tasks.
 */
@Composable
private fun ReminderCard(
    reminder: ReminderEntity,
    onCompleteClick: () -> Unit,
    onDismissClick: () -> Unit
) {
    val isCompleted = reminder.isDone

    // Animated strike-through progress
    val strikeProgress by animateFloatAsState(
        targetValue = if (isCompleted) 1f else 0f,
        animationSpec = tween(durationMillis = 400),
        label = "strike"
    )

    // Animated opacity for completed tasks
    val cardAlpha by animateFloatAsState(
        targetValue = if (isCompleted) 0.7f else 1f,
        animationSpec = tween(durationMillis = 300),
        label = "alpha"
    )

    // Background color animation
    val bgColor by animateColorAsState(
        targetValue = if (isCompleted) ProfitGreenBg else BgPrimary,
        animationSpec = tween(durationMillis = 300),
        label = "bg"
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = bgColor),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = cardAlpha }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // Title with strike-through animation
                Text(
                    text = reminder.title,
                    fontWeight = FontWeight.Bold,
                    color = if (isCompleted) TextSecondary else TextPrimary,
                    maxLines = 1,
                    modifier = Modifier.drawWithContent {
                        drawContent()
                        if (strikeProgress > 0f) {
                            val textWidth = size.width * strikeProgress
                            val yCenter = size.height / 2
                            drawLine(
                                color = TextSecondary,
                                start = Offset(0f, yCenter),
                                end = Offset(textWidth, yCenter),
                                strokeWidth = 2.dp.toPx()
                            )
                        }
                    }
                )
                val due = remember(reminder.dueAt) {
                    SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(reminder.dueAt))
                }
                Text(
                    text = if (isCompleted) "Completed ✓" else due,
                    color = if (isCompleted) ProfitGreen else TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = if (isCompleted) FontWeight.Bold else FontWeight.Normal
                )
                if (!reminder.note.isNullOrBlank() && !isCompleted) {
                    Text(reminder.note.orEmpty(), color = TextSecondary, fontSize = 12.sp, maxLines = 1)
                }
            }

            Row(
                modifier = Modifier.size(36.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Show complete button (circle) for pending reminders
                if (!isCompleted) {
                    IconButton(
                        onClick = onCompleteClick,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.RadioButtonUnchecked,
                            contentDescription = "Mark as complete",
                            tint = Blue600,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                // Show dismiss/delete button for all reminders
                IconButton(
                    onClick = onDismissClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = if (isCompleted) "Dismiss" else "Delete",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
