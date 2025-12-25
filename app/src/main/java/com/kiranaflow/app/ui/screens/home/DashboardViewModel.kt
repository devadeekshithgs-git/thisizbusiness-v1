package com.kiranaflow.app.ui.screens.home

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kiranaflow.app.data.local.ItemEntity
import com.kiranaflow.app.data.local.ReminderEntity
import com.kiranaflow.app.data.local.TransactionEntity
import com.kiranaflow.app.data.local.TransactionItemEntity
import com.kiranaflow.app.data.local.ShopSettingsStore
import com.kiranaflow.app.data.repository.KiranaRepository
import com.kiranaflow.app.data.local.KiranaDatabase
import com.kiranaflow.app.ui.components.ChartDataPoint
import com.kiranaflow.app.util.DebugLogger
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.time.Instant
import java.time.ZoneId
import java.time.LocalDate

data class DashboardState(
    val revenue: Double = 0.0,
    // Cost of goods sold (stock cost) computed from SALE line-items using item.costPrice.
    val cogs: Double = 0.0,
    val expense: Double = 0.0,
    val netProfit: Double = 0.0,
    val cashInHand: Double = 0.0,
    // Sales trend change vs previous period (for the chart section)
    val salesChangePercent: Double? = null,
    val salesChangeAmount: Double = 0.0,
    val salesChangeIsUp: Boolean = true,
    val reminders: List<ReminderEntity> = emptyList(),
    val expiringItems: List<ItemEntity> = emptyList(),
    val recentTransactions: List<TransactionEntity> = emptyList(),
    val chartData: List<ChartDataPoint> = emptyList(),
    val shopName: String = "",
    val ownerName: String = "",
    val greeting: String = "Good Morning",
    val selectedTimeRange: String = "7D"
)

class DashboardViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = KiranaRepository(KiranaDatabase.getDatabase(application))
    private val shopSettingsStore = ShopSettingsStore(application)

    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state.asStateFlow()

    private val selectedRange = MutableStateFlow("7D")
    private val customRangeMillis = MutableStateFlow<Pair<Long, Long>?>(null)

    init {
        // #region agent log
        try {
            DebugLogger.log("DashboardViewModel.kt:34", "DashboardViewModel init started", mapOf(), "H5")
        } catch (e: Exception) {
            Log.e("DashboardViewModel", "Log failed", e)
        }
        // #endregion
        
        try {
            // Subscribe to shop settings changes
            shopSettingsStore.settings.onEach { settings ->
                _state.value = _state.value.copy(
                    shopName = settings.shopName.ifBlank { "My Shop" },
                    ownerName = settings.ownerName.ifBlank { "Owner" }
                )
            }.launchIn(viewModelScope)

            // Clean up old completed reminders on init
            viewModelScope.launch {
                repository.cleanupOldCompletedReminders()
            }

            // Kotlin Flow `combine` overloads in this project support up to 5 typed flows.
            // Combine (range + customRange) first, then combine with the other 4 flows.
            val rangeAndCustomRange: Flow<Pair<String, Pair<Long, Long>?>> =
                combine(selectedRange, customRangeMillis) { range, customRange -> range to customRange }

            @Suppress("UNCHECKED_CAST")
            combine(
                listOf(
                    repository.allTransactions,
                    repository.allTransactionItems,
                    repository.getRemindersWithRecentCompleted(),
                    repository.allItems,
                    rangeAndCustomRange
                )
            ) { values ->
                val transactions = values[0] as List<TransactionEntity>
                val txItems = values[1] as List<TransactionItemEntity>
                val reminders = values[2] as List<ReminderEntity>
                val allItems = values[3] as List<ItemEntity>
                val rangeAndCustom = values[4] as Pair<String, Pair<Long, Long>?>

                val (range, customRange) = rangeAndCustom
                val now = System.currentTimeMillis()
                val dayMs = 24L * 60L * 60L * 1000L

                val (start, end) = when (range) {
                    "TODAY" -> {
                        val zone = ZoneId.systemDefault()
                        val startOfDay = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
                        Pair(startOfDay, now)
                    }
                    "7D" -> Pair(now - (7L * dayMs), now)
                    "1M" -> Pair(now - (30L * dayMs), now)
                    "3M" -> Pair(now - (90L * dayMs), now)
                    "6M" -> Pair(now - (180L * dayMs), now)
                    "1Y" -> Pair(now - (365L * dayMs), now)
                    "CUSTOM" -> customRange ?: Pair(0L, now)
                    else -> Pair(0L, now)
                }.let { (s, e) ->
                    // Ensure stable bounds even if caller passes swapped values.
                    val startBound = minOf(s, e)
                    val endBound = maxOf(s, e)
                    Pair(startBound, endBound)
                }

                val filtered = transactions.filter { it.date in start..end }

                val expiringSoon = allItems
                    .asSequence()
                    .filter { it.expiryDateMillis != null }
                    .filter { (it.expiryDateMillis ?: 0L) in now..(now + 30L * dayMs) }
                    .sortedBy { it.expiryDateMillis }
                    .take(10)
                    .toList()

                Triple(filtered, range, Pair(start, end)) to Triple(transactions, reminders, expiringSoon) to Pair(txItems, allItems)
            }.onEach { (bundlePair, itemsPair) ->
                val (bundle, allTriple) = bundlePair
                val (filtered, range, bounds) = bundle
                val (allTx, reminders, expiringItems) = allTriple
                val (allTxItems, allItems) = itemsPair
                try {
                    // #region agent log
                    DebugLogger.log(
                        "DashboardViewModel.kt:43",
                        "Processing transactions (filtered)",
                        mapOf("count" to filtered.size, "range" to range, "start" to bounds.first, "end" to bounds.second),
                        "H5"
                    )
                    // #endregion
                } catch (e: Exception) {
                    Log.e("DashboardViewModel", "Log failed", e)
                }

                // KPIs should feel "always alive": compute them across ALL TIME.
                // Important: exclude settlement payments from P&L, otherwise credit sales/vendor payments get double-counted.
                fun isSettlementPayment(tx: TransactionEntity): Boolean {
                    val looksLikePayment = tx.title.startsWith("Payment ")
                    val linkedParty = (tx.customerId != null || tx.vendorId != null)
                    return looksLikePayment && linkedParty
                }

                val revenue = allTx
                    .asSequence()
                    .filter { it.type == "SALE" || it.type == "INCOME" }
                    .filterNot { isSettlementPayment(it) }
                    .sumOf { it.amount }
                val expense = allTx
                    .asSequence()
                    .filter { it.type == "EXPENSE" }
                    .filterNot { isSettlementPayment(it) }
                    .sumOf { it.amount }

                // COGS (stock cost) = sum over SALE line-items of (costPrice × qty).
                // - For unit=PCS: qty is pieces, costPrice is per piece/packet.
                // - For unit=KG:  qty is kilograms, costPrice is per KG (loose items).
                // NOTE: costPrice is taken from current `items` table (not snapshot-at-sale).
                val txTypeById: Map<Int, String> = allTx.associate { it.id to it.type }
                val costByItemId: Map<Int, Double> = allItems.associate { it.id to it.costPrice }
                val cogs = allTxItems
                    .asSequence()
                    .filter { txTypeById[it.transactionId] == "SALE" }
                    .sumOf { line ->
                        val itemId = line.itemId
                        val unitCost = if (itemId != null) (costByItemId[itemId] ?: 0.0) else 0.0
                        // Backward-compat: if any legacy GRAM entries exist, treat qty as grams -> kg.
                        val multiplierQty = when (line.unit.uppercase()) {
                            "GRAM", "G", "GM", "GMS", "GRAMS" -> (line.qty / 1000.0)
                            else -> line.qty
                        }
                        unitCost * multiplierQty
                    }

                val daySpan = ((bounds.second - bounds.first).coerceAtLeast(0L) / (24L * 60L * 60L * 1000L)).toInt()
                val grouped = if (daySpan >= 62) {
                    // For longer ranges, group by month for readability/perf.
                    filtered
                        .filter { it.type == "SALE" }
                        .groupBy {
                            val d = Instant.ofEpochMilli(it.date).atZone(ZoneId.systemDefault()).toLocalDate()
                            d.withDayOfMonth(1)
                        }
                        .toSortedMap()
                        .map { (date, txns) ->
                            ChartDataPoint(
                                label = date.format(DateTimeFormatter.ofPattern("MMM")),
                                value = txns.sumOf { it.amount }.toFloat()
                            )
                        }
                } else {
                    filtered
                        .filter { it.type == "SALE" }
                        .groupBy {
                            Instant.ofEpochMilli(it.date).atZone(ZoneId.systemDefault()).toLocalDate()
                        }
                        .toSortedMap()
                        .map { (date, txns) ->
                            ChartDataPoint(
                                label = date.format(DateTimeFormatter.ofPattern("dd MMM")),
                                value = txns.sumOf { it.amount }.toFloat()
                            )
                        }
                }

                val cashIn = allTx
                    .asSequence()
                    .filter { it.paymentMode == "CASH" && (it.type == "INCOME" || it.type == "SALE") }
                    .sumOf { it.amount }
                val cashOut = allTx
                    .asSequence()
                    .filter { it.paymentMode == "CASH" && it.type == "EXPENSE" }
                    .sumOf { it.amount }
                val cashInHand = cashIn - cashOut

                // Sales trend % change vs previous equal-length period (based on SALE only).
                val duration = (bounds.second - bounds.first).coerceAtLeast(0L)
                val prevStart = (bounds.first - duration).coerceAtLeast(0L)
                val prevEnd = bounds.first
                val currentSales = filtered
                    .asSequence()
                    .filter { it.type == "SALE" }
                    .sumOf { it.amount }
                val prevSales = allTx
                    .asSequence()
                    .filter { it.type == "SALE" && it.date in prevStart..prevEnd }
                    .sumOf { it.amount }
                val delta = currentSales - prevSales
                val pct = if (prevSales > 0.0) (delta / prevSales) * 100.0 else null

                _state.value = _state.value.copy(
                    revenue = revenue,
                    cogs = cogs,
                    expense = expense,
                    // Profit includes stock cost: Profit = Revenue - COGS - Expense
                    netProfit = revenue - cogs - expense,
                    cashInHand = cashInHand,
                    salesChangePercent = pct,
                    salesChangeAmount = delta,
                    salesChangeIsUp = delta >= 0.0,
                    reminders = reminders,
                    expiringItems = expiringItems,
                    // Recent should always show the true latest activity.
                    recentTransactions = allTx.sortedByDescending { it.date }.take(5),
                    chartData = grouped,
                    selectedTimeRange = range
                )

                // #region agent log
                try {
                    DebugLogger.log(
                        "DashboardViewModel.kt:72",
                        "State updated",
                        mapOf("revenue" to revenue, "cogs" to cogs, "expense" to expense, "netProfit" to (revenue - cogs - expense), "range" to range),
                        "H5"
                    )
                } catch (e: Exception) {
                    Log.e("DashboardViewModel", "Log failed", e)
                }
                // #endregion
            }.launchIn(viewModelScope)

            updateGreeting()
        } catch (e: Exception) {
            // #region agent log
            try {
                DebugLogger.log("DashboardViewModel.kt:81", "Init error", mapOf("error" to (e.message ?: "unknown"), "stackTrace" to e.stackTraceToString()), "H5")
            } catch (logErr: Exception) {
                Log.e("DashboardViewModel", "Log failed", logErr)
            }
            // #endregion
            Log.e("DashboardViewModel", "Error in init", e)
        }
    }
    
    private fun updateGreeting() {
        val hour = java.time.LocalTime.now().hour
        val greeting = when {
            hour < 5 -> "Good Late Night"
            hour < 12 -> "Good Morning"
            hour < 17 -> "Good Afternoon"
            else -> "Good Evening"
        }
        _state.value = _state.value.copy(greeting = greeting)
    }
    
    fun selectTimeRange(range: String) {
        selectedRange.value = range
        if (range != "CUSTOM") {
            customRangeMillis.value = null
        }
    }
    
    fun selectDateRange(startDate: Long, endDate: Long) {
        selectedRange.value = "CUSTOM"
        customRangeMillis.value = Pair(startDate, endDate)
    }

    fun markReminderDone(id: Int) {
        viewModelScope.launch {
            repository.markReminderDone(id)
        }
    }

    fun dismissReminder(id: Int) {
        viewModelScope.launch {
            repository.dismissReminder(id)
        }
    }
}
