package com.kiranaflow.app.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

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
    content: @Composable () -> Unit
) {
    // Dark mode is intentionally not supported: we enforce a single light theme for the app.
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        content = content
    )
}
