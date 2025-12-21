package com.kiranaflow.app.ui.screens.gst

/**
 * UI models for the interactive GSTR-1 review screen (editable before export).
 * ViewModel will populate these from Room + user overrides.
 */

data class Gstr1ReviewUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val fromMillis: Long = 0L,
    val toMillisExclusive: Long = 0L,
    val businessGstin: String = "",
    val businessLegalName: String = "",
    val businessStateCode: Int = 0,
    val invoices: List<EditableGstr1Invoice> = emptyList(),
    val hsnSummary: List<Gstr1HsnSummaryRow> = emptyList(),
    val validationIssues: List<Gstr1ValidationIssue> = emptyList()
) {
    val issueCount: Int get() = validationIssues.size
}

data class EditableGstr1Invoice(
    val txId: Int,
    val invoiceNumber: String,
    val invoiceDateMillis: Long,
    val invoiceTotalValue: Double,
    val recipientName: String = "",
    val recipientGstin: String = "",
    val placeOfSupplyStateCode: Int = 0,
    val isB2b: Boolean = false,
    val lineItems: List<EditableGstr1LineItem> = emptyList()
)

data class EditableGstr1LineItem(
    val lineId: Int,
    val itemName: String,
    val qty: Int,
    val unit: String,
    val hsnCode: String = "",
    val gstRate: Double = 0.0,
    val taxableValue: Double = 0.0,
    val cgstAmount: Double = 0.0,
    val sgstAmount: Double = 0.0,
    val igstAmount: Double = 0.0
)

data class Gstr1HsnSummaryRow(
    val hsnCode: String,
    val description: String = "",
    val qty: Double = 0.0,
    val taxableValue: Double = 0.0,
    val cgstAmount: Double = 0.0,
    val sgstAmount: Double = 0.0,
    val igstAmount: Double = 0.0
)

data class Gstr1ValidationIssue(
    val severity: Severity = Severity.Error,
    val message: String,
    val txId: Int? = null,
    val lineId: Int? = null
) {
    enum class Severity { Error, Warning }
}



