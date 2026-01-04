package com.kiranaflow.app.billing.factory

import com.kiranaflow.app.billing.model.*
import com.kiranaflow.app.ui.screens.billing.BillSavedEvent
import com.kiranaflow.app.ui.screens.billing.BillSavedLineItem
import com.kiranaflow.app.data.local.ShopSettings
import com.kiranaflow.app.data.local.CustomerEntity
import java.util.*

object BillSnapshotFactory {
    
    /**
     * Creates a BillSnapshot from billing event data
     */
    fun createFromBillingEvent(
        event: BillSavedEvent,
        shopSettings: ShopSettings,
        customer: CustomerEntity?
    ): BillSnapshot {
        val storeInfo = createStoreInfo(shopSettings)
        val customerInfo = createCustomerInfo(customer)
        val transactionInfo = createTransactionInfo(event)
        val items = createBillItems(event.items)
        val gstSummary = calculateGSTSummary(items)
        val totals = calculateTotals(items, event.totalAmount)
        val paymentInfo = createPaymentInfo(event)
        
        return BillSnapshot(
            storeInfo = storeInfo,
            customerInfo = customerInfo,
            transactionInfo = transactionInfo,
            items = items,
            gstSummary = gstSummary,
            totals = totals,
            paymentInfo = paymentInfo
        )
    }
    
    private fun createStoreInfo(settings: ShopSettings): StoreInfo {
        return StoreInfo(
            name = settings.shopName.ifBlank { "Kirana Store" },
            address = settings.address.ifBlank { "Store Address\nCity, State - PIN" },
            gstin = settings.gstin.ifBlank { "" },
            fssaiLicense = "", // No FSSAI field in ShopSettings
            customerCarePhone = settings.shopPhone.ifBlank { "" },
            placeOfSupply = "Karnataka", // TODO: Make this configurable
            stateCode = "29"
        )
    }
    
    private fun createCustomerInfo(customer: CustomerEntity?): CustomerInfo {
        return CustomerInfo(
            id = customer?.id,
            name = customer?.name ?: "Walk-in Customer",
            phone = customer?.phone ?: "",
            type = "URD" // Unregistered Customer
        )
    }
    
    private fun createTransactionInfo(event: BillSavedEvent): TransactionInfo {
        val date = Date(event.createdAtMillis)
        return TransactionInfo(
            billNo = event.txId.toString(),
            date = date,
            posNo = "POS-01", // TODO: Make this configurable
            storeId = "STORE-001", // TODO: Make this configurable
            cashierId = "CASHIER-001", // TODO: Make this configurable
            taxInvoiceNo = "TXN-${event.txId}", // TODO: Generate proper tax invoice number
            paymentReferenceNo = if (event.paymentMode == "UPI") generateUPIReference() else null
        )
    }
    
    private fun createBillItems(lineItems: List<BillSavedLineItem>): List<BillItem> {
        return lineItems.map { item ->
            // Calculate GST amounts (assuming 5% GST for all items - this should come from product data)
            val gstRate = 5.0
            val netPrice = item.unitPrice
            val taxableValue = item.lineTotal / 1.05 // Remove GST to get taxable value
            val gstAmount = taxableValue * (gstRate / 100)
            val cgstAmount = gstAmount / 2
            val sgstAmount = gstAmount / 2
            
            BillItem(
                hsnCode = "999999", // Default HSN for retail items - should come from product data
                name = item.name,
                quantity = item.qty,
                unitPrice = netPrice,
                netPrice = netPrice,
                taxableValue = taxableValue,
                gstRate = gstRate,
                cgstAmount = cgstAmount,
                sgstAmount = sgstAmount,
                totalAmount = item.lineTotal,
                isLoose = item.isLoose
            )
        }
    }
    
    private fun calculateGSTSummary(items: List<BillItem>): GSTSummary {
        // Group by GST rate
        val gstBreakup = items
            .groupBy { it.gstRate }
            .map { (rate, rateItems) ->
                GSTBreakupItem(
                    gstRate = rate,
                    taxableAmount = rateItems.sumOf { it.taxableValue },
                    cgstAmount = rateItems.sumOf { it.cgstAmount },
                    sgstAmount = rateItems.sumOf { it.sgstAmount },
                    cessAmount = rateItems.sumOf { it.cessAmount },
                    total = rateItems.sumOf { it.cgstAmount + it.sgstAmount + it.cessAmount }
                )
            }
            .sortedBy { it.gstRate }
        
        return GSTSummary(
            totalTaxableAmount = gstBreakup.sumOf { it.taxableAmount },
            totalCGST = gstBreakup.sumOf { it.cgstAmount },
            totalSGST = gstBreakup.sumOf { it.sgstAmount },
            totalCESS = gstBreakup.sumOf { it.cessAmount },
            totalGST = gstBreakup.sumOf { it.total },
            breakup = gstBreakup
        )
    }
    
    private fun calculateTotals(items: List<BillItem>, totalAmount: Double): BillTotals {
        val grossSalesValue = items.sumOf { it.taxableValue }
        val totalGST = items.sumOf { it.cgstAmount + it.sgstAmount + it.cessAmount }
        val netSalesValue = grossSalesValue + totalGST
        
        return BillTotals(
            itemCount = items.sumOf { it.quantity.toInt() },
            grossSalesValue = grossSalesValue,
            totalDiscount = 0.0, // TODO: Calculate from discount data
            netSalesValue = netSalesValue,
            totalAmountPaid = totalAmount,
            roundingAdjustment = totalAmount - netSalesValue // Handle rounding differences
        )
    }
    
    private fun createPaymentInfo(event: BillSavedEvent): PaymentInfo {
        return PaymentInfo(
            mode = event.paymentMode,
            amount = event.totalAmount,
            status = when (event.paymentMode) {
                "CREDIT" -> "PENDING"
                else -> "PAID"
            },
            upiId = if (event.paymentMode == "UPI") "merchant@upi" else null, // TODO: Get actual UPI ID
            cardLast4 = null // TODO: Get card details if payment mode is CARD
        )
    }
    
    private fun generateUPIReference(): String {
        return "UPI${System.currentTimeMillis()}"
    }
}
