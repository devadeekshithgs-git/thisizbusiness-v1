package com.kiranaflow.app.util.gst

import com.google.gson.annotations.SerializedName

/**
 * Minimal GSTN Offline Tool-compatible GSTR-1 JSON models.
 * We use @SerializedName to match exact schema keys expected by validators.
 *
 * Note: GSTN schema evolves; keep this focused on the sections we actually export.
 */

data class Gstr1Json(
    @SerializedName("gstin")
    val gstin: String,

    /**
     * Filing period in MMYYYY (e.g., 072025).
     */
    @SerializedName("fp")
    val fp: String,

    /**
     * Gross turnover in the preceding FY (optional in many tools; include if known).
     */
    @SerializedName("gt")
    val gt: Double? = null,

    /**
     * Gross turnover in the current FY (optional).
     */
    @SerializedName("cur_gt")
    val curGt: Double? = null,

    /**
     * B2B supplies (registered recipients).
     */
    @SerializedName("b2b")
    val b2b: List<Gstr1B2bRecipient> = emptyList(),

    /**
     * B2C Small (rate-wise summary).
     */
    @SerializedName("b2cs")
    val b2cs: List<Gstr1B2csRow> = emptyList(),

    /**
     * B2C Large invoices (inter-state > 2.5L).
     * We keep the structure available but may export empty if not applicable.
     */
    @SerializedName("b2cl")
    val b2cl: List<Gstr1B2clRecipient> = emptyList(),

    /**
     * HSN summary.
     */
    @SerializedName("hsn")
    val hsn: Gstr1Hsn? = null,

    /**
     * Schema version string used by some tools.
     */
    @SerializedName("version")
    val version: String = "GST1.0"
)

data class Gstr1B2bRecipient(
    /**
     * Counterparty GSTIN (CTIN).
     */
    @SerializedName("ctin")
    val ctin: String,

    @SerializedName("inv")
    val inv: List<Gstr1Invoice>
)

data class Gstr1B2clRecipient(
    /**
     * Place of supply (2-digit state code as string, e.g., "29").
     */
    @SerializedName("pos")
    val pos: String,

    @SerializedName("inv")
    val inv: List<Gstr1Invoice>
)

data class Gstr1Invoice(
    /**
     * Invoice number.
     */
    @SerializedName("inum")
    val inum: String,

    /**
     * Invoice date in DD-MM-YYYY.
     */
    @SerializedName("idt")
    val idt: String,

    /**
     * Invoice value (total).
     */
    @SerializedName("val")
    val value: Double,

    /**
     * Place of supply (2-digit state code as string, e.g., "29").
     */
    @SerializedName("pos")
    val pos: String,

    /**
     * Reverse charge ("Y"/"N") - optional, default "N".
     */
    @SerializedName("rchrg")
    val reverseCharge: String = "N",

    /**
     * Supply type: "INTRA" or "INTER" (optional in some sections).
     */
    @SerializedName("sply_ty")
    val supplyType: String? = null,

    @SerializedName("itms")
    val itms: List<Gstr1Item>
)

data class Gstr1Item(
    /**
     * Item number (1-based).
     */
    @SerializedName("num")
    val num: Int,

    @SerializedName("itm_det")
    val itemDetail: Gstr1ItemDetail
)

data class Gstr1ItemDetail(
    /**
     * Taxable value.
     */
    @SerializedName("txval")
    val txval: Double,

    /**
     * GST rate (0/5/12/18/28 etc).
     */
    @SerializedName("rt")
    val rt: Double,

    /**
     * IGST amount.
     */
    @SerializedName("iamt")
    val iamt: Double? = null,

    /**
     * CGST amount.
     */
    @SerializedName("camt")
    val camt: Double? = null,

    /**
     * SGST amount.
     */
    @SerializedName("samt")
    val samt: Double? = null,

    /**
     * Cess amount.
     */
    @SerializedName("csamt")
    val csamt: Double? = null
)

data class Gstr1B2csRow(
    /**
     * Supply type: INTRA/INTER.
     */
    @SerializedName("sply_ty")
    val splyTy: String,

    /**
     * Place of supply (2-digit state code as string).
     */
    @SerializedName("pos")
    val pos: String,

    @SerializedName("rt")
    val rt: Double,

    @SerializedName("txval")
    val txval: Double,

    @SerializedName("iamt")
    val iamt: Double? = null,

    @SerializedName("camt")
    val camt: Double? = null,

    @SerializedName("samt")
    val samt: Double? = null,

    @SerializedName("csamt")
    val csamt: Double? = null
)

data class Gstr1Hsn(
    @SerializedName("data")
    val data: List<Gstr1HsnRow>
)

data class Gstr1HsnRow(
    /**
     * Serial number (1-based) for this row.
     */
    @SerializedName("num")
    val num: Int,

    /**
     * HSN/SAC code.
     */
    @SerializedName("hsn_sc")
    val hsnSc: String,

    /**
     * Description (optional).
     */
    @SerializedName("desc")
    val desc: String? = null,

    /**
     * Unit quantity code (optional).
     */
    @SerializedName("uqc")
    val uqc: String? = null,

    @SerializedName("qty")
    val qty: Double? = null,

    /**
     * Total value (optional).
     */
    @SerializedName("val")
    val value: Double? = null,

    @SerializedName("txval")
    val txval: Double,

    @SerializedName("iamt")
    val iamt: Double? = null,

    @SerializedName("camt")
    val camt: Double? = null,

    @SerializedName("samt")
    val samt: Double? = null,

    @SerializedName("csamt")
    val csamt: Double? = null
)



