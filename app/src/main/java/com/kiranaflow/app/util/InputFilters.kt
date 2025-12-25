package com.kiranaflow.app.util

/**
 * Small, UI-safe input filters to enforce numeric-only entry without changing layouts.
 * We "reject alphabets" by stripping non-numeric characters in onValueChange.
 */
object InputFilters {
    fun digitsOnly(input: String, maxLen: Int? = null): String {
        val digits = input.filter { it.isDigit() }
        return if (maxLen != null) digits.take(maxLen) else digits
    }

    fun decimal(input: String, maxDecimals: Int = 2): String {
        // Keep only digits and at most one dot, and cap fractional digits.
        val cleaned = buildString(input.length) {
            var dotSeen = false
            for (ch in input) {
                when {
                    ch.isDigit() -> append(ch)
                    ch == '.' && !dotSeen -> {
                        dotSeen = true
                        append(ch)
                    }
                }
            }
        }
        val parts = cleaned.split('.', limit = 2)
        return when (parts.size) {
            1 -> parts[0]
            else -> {
                val intPart = parts[0]
                val frac = parts[1].take(maxDecimals.coerceAtLeast(0))
                if (frac.isEmpty()) "$intPart." else "$intPart.$frac"
            }
        }
    }
}











