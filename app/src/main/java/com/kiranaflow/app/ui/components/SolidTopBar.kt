package com.kiranaflow.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiranaflow.app.ui.theme.BgPrimary
import com.kiranaflow.app.ui.theme.GrayBg
import com.kiranaflow.app.ui.theme.contentColorForBackground

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
) {
    val headerContentColor = contentColorForBackground(containerColor)
    val headerSubtitleColor = headerContentColor.copy(alpha = 0.85f)
    val settingsBg =
        if (headerContentColor == Color.White) Color.White.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.06f)
    val settingsBorder = headerContentColor.copy(alpha = 0.22f)

    Box(modifier = modifier.fillMaxWidth()) {
        Surface(
            color = containerColor,
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
                // Left spacer to keep title centered relative to settings icon
                Box(modifier = Modifier.size(44.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(title, fontWeight = FontWeight.Black, fontSize = 18.sp, color = headerContentColor)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(subtitle, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = headerSubtitleColor)
                }

                SettingsIconButton(
                    onClick = onSettings,
                    containerColor = settingsBg,
                    contentColor = headerContentColor,
                    borderColor = settingsBorder
                )
            }
        }
    }
}


