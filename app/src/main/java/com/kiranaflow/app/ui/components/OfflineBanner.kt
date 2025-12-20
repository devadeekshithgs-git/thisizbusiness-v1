package com.kiranaflow.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiranaflow.app.ui.theme.LossRed
import com.kiranaflow.app.ui.theme.LossRedBg
import com.kiranaflow.app.ui.theme.TextPrimary
import kotlinx.coroutines.flow.Flow

@Composable
fun OfflineBanner(
    isOnlineFlow: Flow<Boolean>,
    onReconnect: () -> Unit = {}
) {
    val isOnline by isOnlineFlow.collectAsState(initial = true)
    val interactionSource = remember { MutableInteractionSource() }
    AnimatedVisibility(
        visible = !isOnline,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(LossRedBg)
                // No ripple/visual change; just enables tapping to trigger reconnect attempts.
                .clickable(
                    enabled = !isOnline,
                    interactionSource = interactionSource,
                    indication = null
                ) { onReconnect() }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.CloudOff, contentDescription = null, tint = LossRed)
            Text(
                " Offline",
                color = TextPrimary,
                fontSize = 12.sp
            )
        }
    }
}










