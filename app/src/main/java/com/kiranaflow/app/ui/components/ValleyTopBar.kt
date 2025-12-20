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
    actionColor: Color = BgPrimary,
    actionIconTint: Color = TextPrimary,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        // Main bar
        Surface(
            color = BgPrimary,
            shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(132.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 18.dp, start = 16.dp, end = 16.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left spacer to keep title centered relative to settings icon
                Box(modifier = Modifier.size(44.dp))

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(title, fontWeight = FontWeight.Black, fontSize = 18.sp, color = TextPrimary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(subtitle, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = TextSecondary)
                }

                SettingsIconButton(onClick = onSettings)
            }
        }

        // Valley cutout (matches background behind)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = 26.dp)
                .size(92.dp)
                .background(GrayBg, CircleShape)
        )

        // Floating action button inside the valley
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = 10.dp)
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
}


