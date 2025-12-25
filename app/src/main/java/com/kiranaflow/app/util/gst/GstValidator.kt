package com.kiranaflow.app.util.gst

/**
 * Lightweight GST validations for export gating.
 * Keep these conservative (avoid false negatives) and user-friendly.
 */
object GstValidator {
    private const val GSTIN_LEN = 15
    private const val BASE = 36
    private const val CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ"

    /**
     * Validate GSTIN length + allowed charset + checksum.
     * Returns true only when checksum matches.
     */
    fun isValidGstin(gstinRaw: String): Boolean {
        val gstin = gstinRaw.trim().uppercase()
        if (gstin.length != GSTIN_LEN) return false
        if (!gstin.all { it in CHARS }) return false

        // Basic structure sanity: first 2 are digits (state code)
        if (!gstin[0].isDigit() || !gstin[1].isDigit()) return false

        val expected = computeCheckDigit(gstin.substring(0, GSTIN_LEN - 1))
        return expected == gstin.last()
    }

    /**
     * HSN/SAC: typically 4-8 digits for GST reporting.
     */
    fun isValidHsn(hsnRaw: String): Boolean {
        val hsn = hsnRaw.trim()
        if (hsn.length !in 4..8) return false
        return hsn.all { it.isDigit() }
    }

    fun normalizeStateCode(stateCode: Int): String? {
        if (stateCode <= 0) return null
        if (stateCode > 99) return null
        return stateCode.toString().padStart(2, '0')
    }

    private fun computeCheckDigit(input14: String): Char {
        var factor = 2
        var sum = 0
        // Process right-to-left
        for (i in input14.indices.reversed()) {
            val codePoint = CHARS.indexOf(input14[i])
            var addend = factor * codePoint
            factor = if (factor == 2) 1 else 2
            addend = (addend / BASE) + (addend % BASE)
            sum += addend
        }
        val remainder = sum % BASE
        val checkCodePoint = (BASE - remainder) % BASE
        return CHARS[checkCodePoint]
    }
}










