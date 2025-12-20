package com.kiranaflow.app.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = KiranaGreen,
    secondary = KiranaGreenDark,
    tertiary = LossRed
)

private val LightColorScheme = lightColorScheme(
    primary = KiranaGreen,
    secondary = KiranaGreenDark,
    tertiary = LossRed,
    background = GrayBg,
    surface = White,
    onPrimary = White,
    onSecondary = White,
    onTertiary = White,
    onBackground = Gray900,
    onSurface = Gray900
)

@Composable
fun KiranaTheme(
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false, // We want to enforce OUR brand colors, so false
    content: @Composable () -> Unit
) {
    // Even if system is in dark mode, keep the app in light mode for consistent UX.
    // (We keep dynamic colors optional for future experiments, but default is false.)
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            dynamicLightColorScheme(context)
        }
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
