package com.kiranaflow.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Single source of truth for the bottom-tab "capsule" (accent) colors.
 * Keep this in sync with bottom navigation + tab headers.
 */
fun tabCapsuleColor(tabId: String): Color = when (tabId) {
    "home" -> Color(0xFF301CA0)      // Deep purple-blue
    "customers" -> Color(0xFF06923E) // Green
    "bill" -> Color(0xFF212121)      // Near black
    "inventory" -> Color(0xFFE67514) // Orange
    "vendors" -> Color(0xFFBF124D)   // Magenta/red
    else -> Color(0xFF212121)
}

/**
 * Simple contrast helper for colored surfaces.
 */
fun contentColorForBackground(background: Color): Color {
    // If it's a dark background, use white. Otherwise use our primary text color.
    return if (background.luminance() < 0.5f) Color.White else TextPrimary
}













