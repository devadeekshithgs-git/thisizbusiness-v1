package com.kiranaflow.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Enhanced SwipeToConfirmButton with loading, success, and failure states
@Composable
fun SwipeToConfirmButton(
    text: String = "CONFIRM ORDER",
    instructionText: String = "Swipe to confirm >",
    onSwipeComplete: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    resetTrigger: Boolean = false,
    isLoading: Boolean = false,
    isSuccess: Boolean = false,
    isError: Boolean = false,
    errorMessage: String = "Order failed. Please try again."
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableStateOf(0f) }
    var hasTriggered by remember { mutableStateOf(false) }
    var showResetAnimation by remember { mutableStateOf(false) }
    
    val density = LocalDensity.current
    val buttonWidth = 320.dp
    val buttonHeight = 56.dp
    val thumbSize = 48.dp
    val maxDrag = with(density) { (buttonWidth - thumbSize - 16.dp - 8.dp).toPx() }
    
    val scope = rememberCoroutineScope()
    
    // Reset state when resetTrigger changes (e.g., when dialog is dismissed)
    LaunchedEffect(resetTrigger) {
        if (resetTrigger) {
            hasTriggered = false
            dragOffset = 0f
            isDragging = false
            showResetAnimation = false
        }
    }
    
    // Handle success/failure states with auto-reset
    LaunchedEffect(isSuccess, isError) {
        if (isSuccess || isError) {
            scope.launch {
                delay(2000) // Reset after 2 seconds
                showResetAnimation = true
                delay(300) // Animation duration
                hasTriggered = false
                dragOffset = 0f
                isDragging = false
                showResetAnimation = false
            }
        }
    }
    
    val thumbOffset by animateFloatAsState(
        targetValue = if (showResetAnimation) 0f else dragOffset,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "thumb_offset"
    )
    
    val progress by animateFloatAsState(
        targetValue = if (isDragging) dragOffset / maxDrag.toFloat() else 0f,
        animationSpec = tween(300),
        label = "progress"
    )
    
    // Green trail width based on drag progress
    val trailWidth by animateDpAsState(
        targetValue = with(density) { (thumbOffset.toDp() + thumbSize) },
        animationSpec = tween(300),
        label = "trail_width"
    )
    
    // Text opacity based on drag progress
    val textOpacity by animateFloatAsState(
        targetValue = if (isSuccess || isError) 0f else 1f - progress,
        animationSpec = tween(300),
        label = "text_opacity"
    )
    
    // Button background color based on state
    val backgroundColor by animateColorAsState(
        targetValue = when {
            isSuccess -> Color(0xFF4CAF50) // Green for success
            isError -> Color(0xFFF44336) // Red for error
            isLoading -> Color(0xFF2196F3) // Blue for loading
            else -> Color.LightGray.copy(alpha = 0.3f)
        },
        animationSpec = tween(300),
        label = "background_color"
    )
    
    // Thumb color based on state
    val thumbColor by animateColorAsState(
        targetValue = when {
            !enabled -> Color.Gray
            isSuccess -> Color(0xFF4CAF50)
            isError -> Color(0xFFF44336)
            isLoading -> Color(0xFF2196F3)
            else -> Color(0xFF66BB6A)
        },
        animationSpec = tween(300),
        label = "thumb_color"
    )
    
    Box(
        modifier = modifier
            .width(buttonWidth)
            .height(buttonHeight)
            .clip(RoundedCornerShape(28.dp))
            .background(color = backgroundColor)
            .pointerInput(enabled && !hasTriggered && !isLoading && !isSuccess && !isError) {
                detectDragGestures(
                    onDragStart = { 
                        if (enabled && !hasTriggered && !isLoading && !isSuccess && !isError) {
                            isDragging = true
                        }
                    },
                    onDragEnd = {
                        isDragging = false
                        if (dragOffset >= maxDrag * 0.8f && !hasTriggered) {
                            hasTriggered = true
                            onSwipeComplete()
                        } else {
                            dragOffset = 0f
                        }
                    },
                    onDragCancel = {
                        isDragging = false
                        dragOffset = 0f
                    }
                ) { _, dragAmount ->
                    if (enabled && !hasTriggered && !isLoading && !isSuccess && !isError) {
                        val newOffset = (dragOffset + dragAmount.x).coerceIn(0f, maxDrag)
                        dragOffset = newOffset
                    }
                }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        // Green trail background (only show during normal state)
        if (!isLoading && !isSuccess && !isError) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(trailWidth.coerceAtMost(buttonWidth))
                    .background(
                        color = Color(0xFF4CAF50),
                        shape = RoundedCornerShape(28.dp)
                    )
            )
        }
        
        // Background text with fading effect
        if (!isLoading && !isSuccess && !isError) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = text,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.DarkGray.copy(alpha = textOpacity)
                )
                Text(
                    text = instructionText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.Gray.copy(alpha = textOpacity * 0.8f)
                )
            }
        }
        
        // Loading state
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = Color.White,
                strokeWidth = 3.dp
            )
        }
        
        // Success state
        if (isSuccess) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Success",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
        
        // Error state
        if (isError) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Error",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = errorMessage,
                    fontSize = 10.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        
        // Draggable thumb (only show during normal state)
        if (!isLoading && !isSuccess && !isError) {
            Box(
                modifier = Modifier
                    .offset(x = with(density) { thumbOffset.toDp() + 8.dp })
                    .size(thumbSize)
                    .clip(CircleShape)
                    .background(color = thumbColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = "Swipe",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        
        // Reset hasTriggered when component is recomposed with new enabled state
        LaunchedEffect(enabled) {
            if (!enabled) {
                hasTriggered = false
                dragOffset = 0f
                isDragging = false
                showResetAnimation = false
            }
        }
    }
}
