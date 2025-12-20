package com.kiranaflow.app.ui.screens.transactions

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kiranaflow.app.data.local.KiranaDatabase
import com.kiranaflow.app.data.local.PartyEntity
import com.kiranaflow.app.data.local.TransactionEntity
import com.kiranaflow.app.data.local.TransactionItemEntity
import com.kiranaflow.app.data.repository.KiranaRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted

data class TransactionsExplorerState(
    val query: String = "",
    val productQuery: String = "",
    val partyType: String = "ALL", // ALL | CUSTOMER | VENDOR
    val paymentMode: String = "ALL", // ALL | CASH | UPI | CREDIT
    val dateRange: Pair<Long, Long>? = null,
    val results: List<TransactionRow> = emptyList(),
    val unsyncedTxIds: Set<Int> = emptySet()
)

data class TransactionRow(
    val tx: TransactionEntity,
    val party: PartyEntity?,
    val items: List<TransactionItemEntity>
)

class TransactionsViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = KiranaRepository(KiranaDatabase.getDatabase(application))

    private val _query = MutableStateFlow("")
    private val _productQuery = MutableStateFlow("")
    private val _partyType = MutableStateFlow("ALL")
    private val _paymentMode = MutableStateFlow("ALL")
    private val _dateRange = MutableStateFlow<Pair<Long, Long>?>(null)

    private data class Filters(
        val query: String,
        val productQuery: String,
        val partyType: String,
        val paymentMode: String,
        val dateRange: Pair<Long, Long>?
    )

    private val filtersFlow =
        combine(_query, _productQuery, _partyType, _paymentMode, _dateRange) { q, pq, pt, pm, dr ->
            Filters(query = q, productQuery = pq, partyType = pt, paymentMode = pm, dateRange = dr)
        }

    private data class Base(
        val txs: List<TransactionEntity>,
        val parties: List<PartyEntity>,
        val txItems: List<TransactionItemEntity>,
        val unsyncedTxIds: Set<Int>
    )

    private val baseFlow =
        combine(repo.allTransactions, repo.allParties, repo.allTransactionItems, repo.unsyncedTransactionIds) { txs, parties, txItems, unsynced ->
            Base(txs = txs, parties = parties, txItems = txItems, unsyncedTxIds = unsynced)
        }

    val state: StateFlow<TransactionsExplorerState> =
        combine(baseFlow, filtersFlow) { base, f ->
            val txs = base.txs
            val parties = base.parties
            val txItems = base.txItems
            val partyById = parties.associateBy { it.id }
            val itemsByTx = txItems.groupBy { it.transactionId }

            fun matchesSearch(row: TransactionRow, qRaw: String): Boolean {
                val qn = qRaw.trim()
                if (qn.isBlank()) return true
                val hay = buildString {
                    append(row.tx.title)
                    append(" ")
                    append(row.tx.type)
                    append(" ")
                    append(row.tx.paymentMode)
                    append(" ")
                    append(row.party?.name.orEmpty())
                    append(" ")
                    append(row.items.joinToString(" ") { it.itemNameSnapshot })
                }
                return hay.contains(qn, ignoreCase = true)
            }

            fun matchesProduct(row: TransactionRow, pqRaw: String): Boolean {
                val pqn = pqRaw.trim()
                if (pqn.isBlank()) return true
                return row.items.any { it.itemNameSnapshot.contains(pqn, ignoreCase = true) }
            }

            fun matchesPartyType(row: TransactionRow, partyType: String): Boolean {
                if (partyType == "ALL") return true
                return row.party?.type == partyType
            }

            fun matchesPaymentMode(row: TransactionRow, paymentMode: String): Boolean {
                if (paymentMode == "ALL") return true
                return row.tx.paymentMode == paymentMode
            }

            fun matchesDateRange(row: TransactionRow, range: Pair<Long, Long>?): Boolean {
                if (range == null) return true
                val (s, e) = range
                val start = minOf(s, e)
                val end = maxOf(s, e)
                return row.tx.date in start..end
            }

            val rows = txs
                .asSequence()
                .sortedByDescending { it.date }
                .map { tx ->
                    TransactionRow(
                        tx = tx,
                        party = tx.customerId?.let { partyById[it] } ?: tx.vendorId?.let { partyById[it] },
                        items = itemsByTx[tx.id].orEmpty()
                    )
                }
                .filter { matchesSearch(it, f.query) }
                .filter { matchesProduct(it, f.productQuery) }
                .filter { matchesPartyType(it, f.partyType) }
                .filter { matchesPaymentMode(it, f.paymentMode) }
                .filter { matchesDateRange(it, f.dateRange) }
                .toList()

            TransactionsExplorerState(
                query = f.query,
                productQuery = f.productQuery,
                partyType = f.partyType,
                paymentMode = f.paymentMode,
                dateRange = f.dateRange,
                results = rows,
                unsyncedTxIds = base.unsyncedTxIds
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = TransactionsExplorerState()
        )

    fun setQuery(v: String) = run { _query.value = v }
    fun setProductQuery(v: String) = run { _productQuery.value = v }
    fun setPartyType(v: String) = run { _partyType.value = v }
    fun setPaymentMode(v: String) = run { _paymentMode.value = v }
    fun setDateRange(range: Pair<Long, Long>?) = run { _dateRange.value = range }
    fun clearDateRange() = run { _dateRange.value = null }

    fun deleteTransactions(ids: Set<Int>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repo.deleteTransactionsByIds(ids.toList())
        }
    }
}


