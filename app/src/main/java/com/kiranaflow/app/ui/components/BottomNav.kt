package com.kiranaflow.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.min
import com.kiranaflow.app.ui.theme.*

data class NavItem(val id: String, val label: String, val icon: ImageVector)

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun KiranaBottomNav(
    currentTab: String,
    onTabSelected: (String) -> Unit,
    onTabLongPress: (String) -> Unit = {},
) {
    // 1. Home, 2. Customers, 3. Bill, 4. Items, 5. Expenses
    val items = listOf(
        NavItem("home", "Home", Icons.Default.Home),
        NavItem("customers", "Customers", Icons.Default.People),
        NavItem("bill", "Bill", Icons.Default.ReceiptLong),
        NavItem("inventory", "Items", Icons.Default.Inventory2),
        NavItem("vendors", "Expenses", Icons.Default.Payments)
    )

    // Map currentTab to index
    val selectedIndex = when (currentTab) {
        "home" -> 0
        "customers" -> 1
        "bill" -> 2
        "inventory" -> 3
        "vendors" -> 4
        else -> 0
    }

    // Solid capsule colors for each tab
    fun capsuleColorForTab(tab: String): Color = when (tab) {
        "home" -> Color(0xFF301CA0)      // Deep purple-blue
        "customers" -> Color(0xFF043915) // Dark green
        "bill" -> Color(0xFF212121)      // Near black
        "inventory" -> Color(0xFFE67514) // Orange
        "vendors" -> Color(0xFFBF124D)   // Magenta/red
        else -> Color(0xFF212121)
    }
    val selectedCapsuleColor = capsuleColorForTab(items[selectedIndex].id)

    // Outer container stays full width, but the nav bar itself is a floating capsule with smaller width.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
            .padding(bottom = 10.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .height(78.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            val totalWidth = maxWidth
            val barPaddingH = 10.dp
            val innerWidth = totalWidth - (barPaddingH * 2)
            val tabWidth = innerWidth / items.size

            // --- New style (requested): dark glassmorphic capsule + sliding pill highlight ---
            val barShape = RoundedCornerShape(999.dp)
            val barHeight = 64.dp
            // Solid white background with shadow (per latest request)
            val barBg = Color.White
            val barBorder = Color.Black.copy(alpha = 0.08f)
            val iconTint = Color(0xFF111111)

            // Compute pill width based on label length (clamped), and animate its X offset.
            // Pill should never overlap other icons: keep it within the selected tab slot.
            // Rounded rectangle that fits WITHIN the bar with same radius style as menu bar
            val pillHeight = barHeight - 12.dp // smaller than bar to fit within with padding
            val pillWidth = (tabWidth - 8.dp).coerceAtLeast(0.dp)
            // Use half the pill height for perfectly rounded ends (same style as menu bar capsule)
            val pillCornerRadius = pillHeight / 2
            val pillOffsetX by animateDpAsState(
                targetValue = run {
                    // Align pill to selected slot (inside inner padded area).
                    val start = tabWidth * selectedIndex
                    start.coerceIn(0.dp, (innerWidth - pillWidth).coerceAtLeast(0.dp))
                },
                animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMedium),
                label = "pillOffset"
            )

            // Background capsule (glassmorphic-ish)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(barHeight)
                    .shadow(14.dp, barShape, spotColor = Color.Black.copy(alpha = 0.28f)),
                color = barBg,
                shape = barShape,
                border = BorderStroke(1.dp, barBorder)
            ) {}

            // Sliding pill (selected tab) - perfectly rounded capsule within the bar
            // Solid fill color based on selected tab
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(
                        x = pillOffsetX + barPaddingH + 4.dp,
                        y = 4.dp // Slightly lower than center
                    )
                    .height(pillHeight)
                    .width(pillWidth)
                    .clip(RoundedCornerShape(pillCornerRadius))
                    .background(selectedCapsuleColor),
                contentAlignment = Alignment.Center
            ) {
                // Intentionally empty: pill is a background highlight; icons are drawn above it and always visible.
            }

            // Icons Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(barHeight)
                    .padding(horizontal = barPaddingH),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEachIndexed { index, item ->
                    val isSelected = index == selectedIndex
                    // Icons must always remain visible. Only a subtle scale animation for the selected one.
                    val scale by animateFloatAsState(
                        targetValue = if (isSelected) 1.12f else 1.0f,
                        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium),
                        label = "iconScale"
                    )
                    // Animate icon color: white when selected, dark when not
                    val animatedIconTint by animateColorAsState(
                        targetValue = if (isSelected) Color.White else iconTint,
                        animationSpec = tween(durationMillis = 200),
                        label = "iconTint"
                    )
                    
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(barHeight)
                            .combinedClickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onTabSelected(item.id) },
                                onLongClick = { onTabLongPress(item.id) }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.label,
                            tint = animatedIconTint,
                            modifier = Modifier
                                .size(30.dp) // larger icon (per request)
                                .graphicsLayer(scaleX = scale, scaleY = scale)
                        )
                    }
                }
            }
        }
    }
}

class FluidNotchShape(
    private val offset: androidx.compose.ui.unit.Dp,
    private val density: Density,
    private val notchCurveWidth: androidx.compose.ui.unit.Dp = 96.dp
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val desiredNotchCenter = with(this.density) { offset.toPx() }
        
        // Measurements based on the image "Fluid Drop" style
        // The total width of the curve disturbance
        val curveWidth = with(this.density) { notchCurveWidth.toPx() }
        val halfCurve = curveWidth / 2
        
        // Depth of the dip
        val dipDepth = with(this.density) { 36.dp.toPx() }
        
        // Top rounding for the capsule bar
        val cornerRadius = size.height / 2.0f // Capsule ends

        // Allow the notch to reach Home/Expenses without "snapping" inward.
        // Keep curve inside the bar by clamping only to halfCurve (not cornerRadius).
        val minCenter = halfCurve
        val maxCenter = size.width - halfCurve
        val notchCenter = desiredNotchCenter.coerceIn(minCenter, maxCenter)

        return Outline.Generic(Path().apply {
            reset()
            // Start Top-Left
            moveTo(0f, cornerRadius)
            quadraticBezierTo(0f, 0f, cornerRadius, 0f) // Rounded Top-Left corner
            
            val startX = notchCenter - halfCurve
            val endX = notchCenter + halfCurve
            
            // Line to start of curve
            if (startX > cornerRadius) {
                lineTo(startX, 0f)
            } else {
                lineTo(cornerRadius, 0f)
            }
            
            // Draw the Fluid Dip
            // We use two cubic beziers to create the "S" shape down and "S" shape up
            // Control points for the "Go Down" part
            // 1. Anchor (startX, 0)
            // 2. Control Point 1: slightly inward, flat y=0
            // 3. Control Point 2: further inward, near bottom y=dipDepth
            // 4. Anchor (notchCenter, dipDepth)
            
            if (startX > -curveWidth && endX < size.width + curveWidth) {
                cubicTo(
                    startX + (halfCurve * 0.5f), 0f,          // CP1: Flat start
                    startX + (halfCurve * 0.25f), dipDepth,   // CP2: Steep drop
                    notchCenter, dipDepth                     // End: Bottom center
                )
                
                // Go Up part (Mirror)
                cubicTo(
                    endX - (halfCurve * 0.25f), dipDepth,     // CP1: Steep rise
                    endX - (halfCurve * 0.5f), 0f,            // CP2: Flat end
                    endX, 0f                                  // End
                )
            }

            // Line to Top-Right
            lineTo(size.width - cornerRadius, 0f)
            quadraticBezierTo(size.width, 0f, size.width, cornerRadius) // Rounded Top-Right
            
            // Right -> Bottom -> Left -> Close
            lineTo(size.width, size.height - cornerRadius)
            quadraticBezierTo(size.width, size.height, size.width - cornerRadius, size.height) // Bottom-Right
            lineTo(cornerRadius, size.height)
            quadraticBezierTo(0f, size.height, 0f, size.height - cornerRadius) // Bottom-Left
            
            close()
        })
    }
}
