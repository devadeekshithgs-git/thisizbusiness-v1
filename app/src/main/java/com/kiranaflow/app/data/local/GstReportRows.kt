package com.kiranaflow.app.data.local

import androidx.room.ColumnInfo

/**
 * Flat row model for GST export (one row per transaction line item).
 * Built via SQL joins for performance and simplicity.
 */
data class GstSaleLineRow(
    @ColumnInfo(name = "txId")
    val txId: Int,

    @ColumnInfo(name = "txTitle")
    val txTitle: String,

    @ColumnInfo(name = "txAmount")
    val txAmount: Double,

    @ColumnInfo(name = "txDate")
    val txDate: Long,

    @ColumnInfo(name = "customerId")
    val customerId: Int?,

    @ColumnInfo(name = "customerName")
    val customerName: String?,

    @ColumnInfo(name = "customerGstin")
    val customerGstin: String?,

    @ColumnInfo(name = "customerStateCode")
    val customerStateCode: Int?,

    @ColumnInfo(name = "itemLineId")
    val itemLineId: Int,

    @ColumnInfo(name = "itemId")
    val itemId: Int?,

    @ColumnInfo(name = "itemNameSnapshot")
    val itemNameSnapshot: String,

    @ColumnInfo(name = "qty")
    val qty: Int,

    @ColumnInfo(name = "unit")
    val unit: String,

    @ColumnInfo(name = "price")
    val price: Double,

    @ColumnInfo(name = "hsnCodeSnapshot")
    val hsnCodeSnapshot: String?,

    @ColumnInfo(name = "gstRateSnapshot")
    val gstRateSnapshot: Double,

    @ColumnInfo(name = "taxableValueSnapshot")
    val taxableValueSnapshot: Double,

    @ColumnInfo(name = "cgstAmountSnapshot")
    val cgstAmountSnapshot: Double,

    @ColumnInfo(name = "sgstAmountSnapshot")
    val sgstAmountSnapshot: Double,

    @ColumnInfo(name = "igstAmountSnapshot")
    val igstAmountSnapshot: Double,

    @ColumnInfo(name = "itemHsnCode")
    val itemHsnCode: String?,

    @ColumnInfo(name = "itemGstPercentage")
    val itemGstPercentage: Double?
)



