package com.kiranaflow.app.ui.screens.billing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun BillingTabsBar(
    sessions: List<BillingSession>,
    activeSessionId: String,
    onTabSelected: (String) -> Unit,
    onNewTab: () -> Unit,
    onCloseTab: (String) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        items(sessions.size) { index ->
            val session = sessions[index]
            val isActive = session.sessionId == activeSessionId
            FilterChip(
                selected = isActive,
                onClick = { onTabSelected(session.sessionId) },
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Bill ${index + 1} (${session.items.size})",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        IconButton(
                            onClick = { onCloseTab(session.sessionId) },
                            modifier = Modifier.size(18.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close tab",
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                },
                modifier = Modifier.height(36.dp)
            )
        }
        item {
            FilterChip(
                selected = false,
                onClick = onNewTab,
                label = { Text(text = "+", style = MaterialTheme.typography.labelLarge) },
                modifier = Modifier.height(36.dp)
            )
        }
    }
}
