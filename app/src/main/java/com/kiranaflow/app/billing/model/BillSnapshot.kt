package com.kiranaflow.app.billing.model

import java.util.Date

data class BillSnapshot(
    val storeInfo: StoreInfo,
    val customerInfo: CustomerInfo,
    val transactionInfo: TransactionInfo,
    val items: List<BillItem>,
    val gstSummary: GSTSummary,
    val totals: BillTotals,
    val paymentInfo: PaymentInfo
)

data class StoreInfo(
    val name: String,
    val address: String,
    val gstin: String,
    val fssaiLicense: String,
    val customerCarePhone: String,
    val placeOfSupply: String,
    val stateCode: String
)

data class CustomerInfo(
    val id: Int?,
    val name: String,
    val phone: String,
    val type: String = "URD" // Unregistered Customer
)

data class TransactionInfo(
    val billNo: String,
    val date: Date,
    val posNo: String,
    val storeId: String,
    val cashierId: String,
    val taxInvoiceNo: String,
    val paymentReferenceNo: String?
)

data class BillItem(
    val hsnCode: String,
    val name: String,
    val quantity: Double,
    val unitPrice: Double,
    val netPrice: Double,
    val discount: Double = 0.0,
    val taxableValue: Double,
    val gstRate: Double,
    val cgstAmount: Double,
    val sgstAmount: Double,
    val cessAmount: Double = 0.0,
    val totalAmount: Double,
    val isLoose: Boolean = false
)

data class GSTSummary(
    val totalTaxableAmount: Double,
    val totalCGST: Double,
    val totalSGST: Double,
    val totalCESS: Double,
    val totalGST: Double,
    val breakup: List<GSTBreakupItem>
)

data class GSTBreakupItem(
    val gstRate: Double,
    val taxableAmount: Double,
    val cgstAmount: Double,
    val sgstAmount: Double,
    val cessAmount: Double,
    val total: Double
)

data class BillTotals(
    val itemCount: Int,
    val grossSalesValue: Double,
    val totalDiscount: Double,
    val netSalesValue: Double, // Inclusive of GST
    val totalAmountPaid: Double,
    val roundingAdjustment: Double = 0.0
)

data class PaymentInfo(
    val mode: String, // CASH, UPI, CARD, CREDIT
    val amount: Double,
    val status: String, // PAID, PENDING, LATER
    val upiId: String? = null,
    val cardLast4: String? = null
)
