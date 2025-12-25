package com.kiranaflow.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiranaflow.app.ui.theme.*

@Composable
fun ValleyTopBar(
    title: String,
    subtitle: String,
    actionIcon: ImageVector,
    onAction: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = BgPrimary,
    actionColor: Color = BgPrimary,
    actionIconTint: Color = contentColorForBackground(actionColor),
    backgroundColor: Color = GrayBg,
    // If false, the action icon is shown in the top-right next to settings (Billing requirement).
    showCenterActionButton: Boolean = true,
    // Allow hiding the action icon entirely (e.g., Expense mode).
    showActionIcon: Boolean = true,
) {
    val headerContentColor = contentColorForBackground(containerColor)
    val headerSubtitleColor = headerContentColor.copy(alpha = 0.85f)
    val settingsBg =
        if (headerContentColor == Color.White) Color.White.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.06f)
    val settingsBorder = headerContentColor.copy(alpha = 0.22f)

    /**
     * Important: the "valley" and FAB overlap below the main bar.
     * Using `offset()` alone does NOT increase the layout's measured height, which causes
     * scrollable content below to render/scroll under the header and feel "stuck".
     *
     * We explicitly reserve extra height (26.dp) so content starts below the overlapped area.
     */
    Column(modifier = modifier.fillMaxWidth()) {
        // Main bar
        val barHeight = if (showCenterActionButton) 132.dp else 108.dp
        Box(modifier = Modifier.fillMaxWidth().height(barHeight)) {
            Surface(
                color = containerColor,
                shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 18.dp, start = 16.dp, end = 16.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left spacer to keep title centered relative to right actions
                    Box(modifier = Modifier.size(44.dp))

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(title, fontWeight = FontWeight.Black, fontSize = 18.sp, color = headerContentColor)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(subtitle, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = headerSubtitleColor)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (!showCenterActionButton && showActionIcon) {
                            // Action icon button next to settings (scan icon requirement)
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(settingsBg)
                                    .border(1.dp, settingsBorder, CircleShape)
                                    .clickable { onAction() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(actionIcon, contentDescription = null, tint = headerContentColor)
                            }
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

        if (showCenterActionButton && showActionIcon) {
            // Reserve the protruding space below the bar (so lists start below it)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(26.dp)
            ) {
                // Valley cutout (matches background behind)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        // 92dp circle with 26dp below the bar => overlap 66dp upward into the bar.
                        .offset(y = (-66).dp)
                        .size(92.dp)
                        .background(backgroundColor, CircleShape)
                )

                // Floating action button inside the valley
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        // 64dp fab with 10dp below the bar => overlap 54dp upward into the bar.
                        .offset(y = (-54).dp)
                        .size(64.dp)
                        .shadow(14.dp, CircleShape, spotColor = actionColor.copy(alpha = 0.35f))
                        .clip(CircleShape)
                        .background(actionColor)
                        .border(1.dp, Gray200, CircleShape)
                        .clickable { onAction() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(actionIcon, contentDescription = null, tint = actionIconTint, modifier = Modifier.size(28.dp))
                }
            }
        } else {
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}


