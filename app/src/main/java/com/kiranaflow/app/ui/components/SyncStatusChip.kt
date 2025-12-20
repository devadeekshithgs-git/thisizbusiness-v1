package com.kiranaflow.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiranaflow.app.ui.theme.AlertOrange
import com.kiranaflow.app.ui.theme.AlertOrangeBg
import com.kiranaflow.app.ui.theme.LossRed
import com.kiranaflow.app.ui.theme.LossRedBg
import com.kiranaflow.app.ui.theme.ProfitGreen
import com.kiranaflow.app.ui.theme.ProfitGreenBg
import com.kiranaflow.app.ui.theme.TextPrimary
import kotlinx.coroutines.flow.Flow

/**
 * Milestone D groundwork: connectivity + sync status indicator (stub).
 *
 * - Offline: red chip
 * - Online + pending>0: orange chip
 * - Online + pending==0: green chip ("Synced")
 */
@Composable
fun SyncStatusChip(
    isOnlineFlow: Flow<Boolean>,
    pendingCountFlow: Flow<Int>
) {
    val isOnline by isOnlineFlow.collectAsState(initial = true)
    val pending by pendingCountFlow.collectAsState(initial = 0)

    val (bg, fg, icon, text) = when {
        !isOnline -> Quad(LossRedBg, LossRed, Icons.Default.CloudOff, "Offline")
        pending > 0 -> Quad(AlertOrangeBg, AlertOrange, Icons.Default.CloudSync, "Pending $pending")
        else -> Quad(ProfitGreenBg, ProfitGreen, Icons.Default.CloudDone, "Synced")
    }

    // Always visible but small; gives consistent placement and avoids layout jumps.
    AnimatedVisibility(visible = true, enter = fadeIn(), exit = fadeOut()) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(bg)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = fg)
            Text(
                " $text",
                color = TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)





