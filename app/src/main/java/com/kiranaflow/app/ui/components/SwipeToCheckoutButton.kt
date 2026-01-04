package com.kiranaflow.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.kiranaflow.app.R

/**
 * Simple Checkout Button styled to match the design reference
 * Shows item count and total amount with green background
 */
@Composable
fun SwipeToCheckoutButton(
    modifier: Modifier = Modifier,
    totalAmount: Double = 0.0,
    itemCount: Int = 0,
    enabled: Boolean = true,
    onSwipeComplete: () -> Unit
) {
    // State for loading/success/error
    var isLoading by remember { mutableStateOf(false) }
    var isSuccess by remember { mutableStateOf(false) }
    var isError by remember { mutableStateOf(false) }
    
    // Resources
    val buttonHeight = dimensionResource(id = R.dimen.checkout_swipe_height)
    val cornerRadius = dimensionResource(id = R.dimen.checkout_swipe_corner_radius)
    val animationDuration = 300
    val resetDelay = 2000
    
    // Colors matching the screenshot
    val backgroundColor = Color.Black // Black background
    val disabledBackgroundColor = Color(0xFFCCCCCC)
    val textColor = Color.White
    
    val scope = rememberCoroutineScope()
    
    // Handle success/failure states with auto-reset
    LaunchedEffect(isSuccess, isError) {
        if (isSuccess || isError) {
            scope.launch {
                delay(resetDelay.toLong())
                isLoading = false
                isSuccess = false
                isError = false
            }
        }
    }
    
    // Button background color based on state
    val backgroundColorState by animateColorAsState(
        targetValue = when {
            !enabled -> disabledBackgroundColor
            isError -> Color.Red
            else -> backgroundColor
        },
        animationSpec = tween(animationDuration),
        label = "background_color"
    )
    
    // Main button
    Row(
        modifier = modifier
            .wrapContentWidth() // Adaptive width based on content
            .height(buttonHeight)
            .clip(RoundedCornerShape(cornerRadius))
            .background(color = backgroundColorState)
            .clickable(enabled = enabled && !isLoading && !isSuccess && !isError) {
                if (enabled && !isLoading && !isSuccess && !isError) {
                    isLoading = true
                    try {
                        onSwipeComplete()
                        isLoading = false
                        isSuccess = true
                    } catch (e: Exception) {
                        isLoading = false
                        isError = true
                    }
                }
            }
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        when {
            isLoading -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = textColor,
                    strokeWidth = 3.dp
                )
            }
            isSuccess -> {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Order Confirmed",
                    tint = textColor,
                    modifier = Modifier.size(32.dp)
                )
            }
            isError -> {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Order Failed",
                    tint = textColor,
                    modifier = Modifier.size(32.dp)
                )
            }
            else -> {
                // Item count on left
                Text(
                    text = "${itemCount} item${if (itemCount != 1) "s" else ""}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = textColor
                )
                
                // Checkout text in center
                Text(
                    text = "Checkout ($itemCount)",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
                
                // Total amount on right
                Text(
                    text = "₹${totalAmount.toInt()}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = textColor
                )
            }
        }
    }
}
