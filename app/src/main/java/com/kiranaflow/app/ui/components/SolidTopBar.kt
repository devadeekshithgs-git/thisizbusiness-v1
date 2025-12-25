package com.kiranaflow.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiranaflow.app.ui.theme.BgPrimary
import com.kiranaflow.app.ui.theme.contentColorForBackground

data class SolidTopBarActionColors(
    val containerColor: Color,
    val contentColor: Color,
    val borderColor: Color,
)

/**
 * Solid header (no valley/cutouts). Use this across screens for consistent alignment.
 */
@Composable
fun SolidTopBar(
    title: String,
    subtitle: String,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = BgPrimary,
    subtitleAlphaMultiplier: Float = 1f,
    tonalElevation: androidx.compose.ui.unit.Dp = 0.dp,
    actions: @Composable RowScope.(SolidTopBarActionColors) -> Unit = { colors ->
        SettingsIconButton(
            onClick = onSettings,
            containerColor = colors.containerColor,
            contentColor = colors.contentColor,
            borderColor = colors.borderColor
        )
    },
) {
    val headerContentColor = contentColorForBackground(containerColor)
    val headerSubtitleColor =
        headerContentColor.copy(alpha = 0.85f * subtitleAlphaMultiplier.coerceIn(0f, 1f))
    val settingsBg =
        if (headerContentColor == Color.White) Color.White.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.06f)
    val settingsBorder = headerContentColor.copy(alpha = 0.22f)
    val actionColors = remember(settingsBg, headerContentColor, settingsBorder) {
        SolidTopBarActionColors(
            containerColor = settingsBg,
            contentColor = headerContentColor,
            borderColor = settingsBorder
        )
    }

    val density = LocalDensity.current
    var actionsWidthPx by remember { mutableIntStateOf(with(density) { 44.dp.roundToPx() }) }
    val actionsWidthDp = with(density) { actionsWidthPx.toDp() }

    Box(modifier = modifier.fillMaxWidth()) {
        Surface(
            color = containerColor,
            tonalElevation = tonalElevation,
            // Keep it fully filled; no cutout shapes.
            shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp),
            modifier = Modifier.fillMaxWidth().height(132.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp, start = 16.dp, end = 16.dp),
                verticalAlignment = Alignment.Top
            ) {
                // Left spacer to keep title centered relative to right actions.
                Box(modifier = Modifier.width(actionsWidthDp).height(44.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(title, fontWeight = FontWeight.Black, fontSize = 18.sp, color = headerContentColor)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(subtitle, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = headerSubtitleColor)
                }

                Row(
                    modifier = Modifier.onSizeChanged { actionsWidthPx = it.width },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    actions(actionColors)
                }
            }
        }
    }
}



