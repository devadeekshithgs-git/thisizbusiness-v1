package com.kiranaflow.app.ui.screens.billing

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
fun BillingTabsBar(
    sessions: List<BillingSession>,
    activeSessionId: String,
    onTabSelected: (String) -> Unit,
    onNewTab: () -> Unit,
    onCloseTab: (String) -> Unit
) {
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val hapticFeedback = LocalHapticFeedback.current
    
    // Auto-scroll to active tab when it changes
    LaunchedEffect(activeSessionId) {
        val activeIndex = sessions.indexOfFirst { it.sessionId == activeSessionId }
        if (activeIndex >= 0) {
            listState.animateScrollToItem(
                index = activeIndex,
                scrollOffset = -50 // Keep some padding on the left
            )
        }
    }

    LazyRow(
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        itemsIndexed(sessions) { index, session ->
            val isActive = session.sessionId == activeSessionId
            val previousActiveIndex = remember { mutableStateOf(sessions.indexOfFirst { it.sessionId == activeSessionId }) }
            
            // Track when this tab becomes active/inactive for animations
            val isBecomingActive = remember(activeSessionId, session.sessionId) {
                isActive && previousActiveIndex.value != index
            }
            val isBecomingInactive = remember(activeSessionId, session.sessionId) {
                !isActive && previousActiveIndex.value == index
            }
            
            LaunchedEffect(activeSessionId) {
                previousActiveIndex.value = sessions.indexOfFirst { it.sessionId == activeSessionId }
            }
            
            AnimatedTabChip(
                isActive = isActive,
                isBecomingActive = isBecomingActive,
                isBecomingInactive = isBecomingInactive,
                tabText = "Bill ${index + 1} (${session.items.size})",
                sessionId = session.sessionId,
                onSelect = { 
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onTabSelected(session.sessionId) 
                },
                onClose = { 
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    onCloseTab(session.sessionId) 
                }
            )
        }
        
        item {
            AnimatedAddTabButton(
                onAdd = { 
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    onNewTab() 
                }
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun AnimatedTabChip(
    isActive: Boolean,
    isBecomingActive: Boolean,
    isBecomingInactive: Boolean,
    tabText: String,
    sessionId: String,
    onSelect: () -> Unit,
    onClose: () -> Unit
) {
    val hapticFeedback = LocalHapticFeedback.current
    var scale by remember { mutableFloatStateOf(1f) }
    var elevation by remember { mutableFloatStateOf(0f) }
    
    // Animate scale and elevation when tab becomes active
    LaunchedEffect(isBecomingActive) {
        if (isBecomingActive) {
            scale = 1.05f
            elevation = 8f
            animate(
                initialValue = 1.05f,
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            ) { value, _ -> scale = value }
            animate(
                initialValue = 8f,
                targetValue = 4f,
                animationSpec = tween(300, easing = EaseOutQuart)
            ) { value, _ -> elevation = value }
        }
    }
    
    // Animate when tab becomes inactive
    LaunchedEffect(isBecomingInactive) {
        if (isBecomingInactive) {
            animate(
                initialValue = 1f,
                targetValue = 0.95f,
                animationSpec = tween(150, easing = EaseInQuart)
            ) { value, _ -> scale = value }
            animate(
                initialValue = 4f,
                targetValue = 0f,
                animationSpec = tween(150, easing = EaseInQuart)
            ) { value, _ -> elevation = value }
            delay(150)
            animate(
                initialValue = 0.95f,
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            ) { value, _ -> scale = value }
        }
    }
    
    Card(
        modifier = Modifier
            .height(40.dp)
            .scale(scale)
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
            .pointerInput(sessionId) {
                detectTapGestures(
                    onTap = { 
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelect() 
                    },
                    onLongPress = { 
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        onClose() 
                    }
                )
            },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
            contentColor = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = elevation.dp
        )
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AnimatedVisibility(
                visible = isActive,
                enter = fadeIn(animationSpec = tween(200)) + scaleIn(
                    initialScale = 0.8f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                ),
                exit = fadeOut(animationSpec = tween(150)) + scaleOut(
                    targetScale = 0.8f,
                    animationSpec = tween(150)
                )
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(
                            color = MaterialTheme.colorScheme.onPrimary,
                            shape = RoundedCornerShape(3.dp)
                        )
                )
            }
            
            Text(
                text = tabText,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun AnimatedAddTabButton(
    onAdd: () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    val scope = rememberCoroutineScope()
    
    Card(
        modifier = Modifier
            .height(40.dp)
            .scale(scale)
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        onClick = {
            scale = 0.9f
            onAdd()
            // Reset scale after a short delay
            scope.launch {
                delay(100)
                scale = 1f
            }
        }
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "+",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(animationSpec = tween(300)) + slideInHorizontally(
                    initialOffsetX = { it / 4 },
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                ),
                exit = fadeOut(animationSpec = tween(150))
            ) {
                Text(
                    text = "New",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
