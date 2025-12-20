package com.kiranaflow.app.ui.theme

import androidx.compose.ui.graphics.Color

// PRD Color Palette - Profit/Success Colors
val ProfitGreen = Color(0xFF2E7D32) // Primary Green (RGB: 46, 125, 50)
val ProfitGreenBg = Color(0xFFE0F7F4) // Light Green BG (RGB: 224, 247, 244)

// PRD Color Palette - Loss/Error Colors
val LossRed = Color(0xFFB71C1C) // Primary Red (RGB: 183, 28, 28)
val LossRedBg = Color(0xFFFFEBEE) // Light Red BG (RGB: 255, 235, 238)
val LowStockRed = Color(0xFFFFE0E6) // Low Stock Red BG (RGB: 255, 224, 230)

// PRD Color Palette - Interactive/Selection
val InteractiveCyan = Color(0xFF00BCD4) // Cyan/Teal (RGB: 0, 188, 212)

// PRD Color Palette - Backgrounds
val BgPrimary = Color(0xFFFFFFFF) // Primary BG (RGB: 255, 255, 255)
val BgCard = Color(0xFFF7F7F7) // Card BG (RGB: 247, 247, 247)

// PRD Color Palette - Text Colors
val TextPrimary = Color(0xFF1A1A1A) // Primary Text (RGB: 26, 26, 26)
val TextSecondary = Color(0xFF808080) // Secondary Text (RGB: 128, 128, 128)

// PRD Color Palette - Chart Colors
val ChartUp = Color(0xFF10B981) // Uptrend Line (RGB: 16, 185, 129)
val ChartDown = Color(0xFFEF4444) // Downtrend Line (RGB: 239, 68, 68)

// Legacy colors - keeping for backward compatibility
val KiranaGreen = ProfitGreen
val KiranaGreenDark = Color(0xFF006048)
val KiranaGreenLight = Color(0xFFD1FAE5)
val KiranaGreenBg = ProfitGreenBg

val LossRedDark = Color(0xFFDC2626)
val LossRedBorder = Color(0xFFFECACA)

val AlertOrange = Color(0xFFEA580C)
val AlertOrangeBg = Color(0xFFFFF7ED)
val AlertOrangeBorder = Color(0xFFFED7AA)

// Grays
val Gray50 = Color(0xFFF9FAFB)
val GrayBg = BgCard // Main background
val Gray100 = Color(0xFFF3F4F6)
val Gray200 = Color(0xFFE5E7EB)
val Gray300 = Color(0xFFD1D5DB)
val Gray400 = Color(0xFF9CA3AF)
val Gray500 = Color(0xFF6B7280)
val Gray600 = Color(0xFF4B5563)
val Gray700 = Color(0xFF374151)
val Gray800 = Color(0xFF1F2937)
val Gray900 = TextPrimary
val Black = Color(0xFF000000)
val White = BgPrimary

// Blue/Purple for specific accents
val Blue50 = Color(0xFFEFF6FF)
val Blue600 = Color(0xFF2563EB)
val Purple50 = Color(0xFFFAF5FF)
val Purple600 = Color(0xFF9333EA)

// Additional colors from screenshots
val KiranaGreenStack = Color(0xFFECFDF5)

// ============================================
// DARK MODE COLORS
// ============================================

// Dark Mode - Backgrounds
val DarkBgPrimary = Color(0xFF121212)       // Main dark background
val DarkBgCard = Color(0xFF1E1E1E)          // Card/Surface dark
val DarkBgElevated = Color(0xFF2D2D2D)      // Elevated surfaces

// Dark Mode - Text Colors
val DarkTextPrimary = Color(0xFFE1E1E1)     // Primary text on dark
val DarkTextSecondary = Color(0xFFA0A0A0)   // Secondary text on dark

// Dark Mode - Profit/Success (slightly brighter for visibility)
val DarkProfitGreen = Color(0xFF4CAF50)     // Brighter green for dark mode
val DarkProfitGreenBg = Color(0xFF1B3D1B)   // Dark green background

// Dark Mode - Loss/Error (slightly brighter for visibility)
val DarkLossRed = Color(0xFFEF5350)         // Brighter red for dark mode
val DarkLossRedBg = Color(0xFF3D1B1B)       // Dark red background

// Dark Mode - Interactive/Accent
val DarkInteractiveCyan = Color(0xFF26C6DA) // Brighter cyan for dark
val DarkBlue600 = Color(0xFF42A5F5)         // Brighter blue for dark
val DarkPurple600 = Color(0xFFAB47BC)       // Brighter purple for dark
val DarkAlertOrange = Color(0xFFFF7043)     // Brighter orange for dark

// Dark Mode - Gray scale
val DarkGray50 = Color(0xFF2D2D2D)
val DarkGray100 = Color(0xFF3D3D3D)
val DarkGray200 = Color(0xFF4D4D4D)
val DarkGray300 = Color(0xFF5D5D5D)
val DarkGray400 = Color(0xFF757575)
val DarkGray500 = Color(0xFF9E9E9E)

// Dark Mode - Chart Colors
val DarkChartUp = Color(0xFF26A69A)         // Teal for uptrend
val DarkChartDown = Color(0xFFEF5350)       // Red for downtrend
