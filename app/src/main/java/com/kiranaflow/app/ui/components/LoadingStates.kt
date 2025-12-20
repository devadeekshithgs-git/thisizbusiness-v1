package com.kiranaflow.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiranaflow.app.ui.theme.*

@Composable
fun LoadingIndicator(
    modifier: Modifier = Modifier,
    message: String = "Loading..."
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BgCard),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(
                color = ProfitGreen,
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = message,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = TextSecondary
            )
        }
    }
}

@Composable
fun EmptyState(
    modifier: Modifier = Modifier,
    title: String = "No Data",
    message: String = "There's nothing here yet.",
    icon: @Composable () -> Unit = {}
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            icon()
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = message,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = TextSecondary
            )
        }
    }
}
