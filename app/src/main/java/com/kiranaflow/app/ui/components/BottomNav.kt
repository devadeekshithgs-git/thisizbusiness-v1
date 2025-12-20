package com.kiranaflow.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    fun accentForTab(tab: String): Color = when (tab) {
        "home" -> Blue600
        "customers" -> Purple600
        "bill" -> KiranaGreen
        "inventory" -> AlertOrange
        "vendors" -> InteractiveCyan
        else -> KiranaGreen
    }
    val selectedAccent = accentForTab(items[selectedIndex].id)

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
            val density = LocalDensity.current
            val totalWidth = maxWidth
            val tabWidth = totalWidth / items.size

            val circleSize = 52.dp
            val circleRadius = circleSize / 2
            // Keep notch behavior consistent at the edges (Home/Vendors) by sharing the same curve width
            // for both the background notch and the indicator positioning.
            // Width of the "valley" dip. Keep this comfortably wider than the floating circle diameter
            // so the circle sits inside the notch without clipping.
            val notchCurveWidth = 96.dp
            val halfCurve = notchCurveWidth / 2
        
        // Center of item i
        val indicatorOffset by animateDpAsState(
            targetValue = run {
                val center = (tabWidth * selectedIndex) + (tabWidth / 2)
                // Clamp the notch center so it can reach the first/last tab without visually "jumping"
                // due to capsule corner constraints.
                val clampedCenter = center.coerceIn(halfCurve, totalWidth - halfCurve)
                val raw = clampedCenter - circleRadius
                val maxOffset = (totalWidth - circleSize).coerceAtLeast(0.dp)
                raw.coerceIn(0.dp, maxOffset)
            },
            animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium),
            label = "indicator"
        )

            // Custom Shape Background
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp) // Slightly smaller floating bar
                    .shadow(
                        elevation = 10.dp,
                        shape = FluidNotchShape(indicatorOffset + circleRadius, density, notchCurveWidth = notchCurveWidth),
                        spotColor = Color(0x15000000)
                    )
                    .background(Color.White, shape = FluidNotchShape(indicatorOffset + circleRadius, density, notchCurveWidth = notchCurveWidth))
            )

            // Icons Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEachIndexed { index, item ->
                    val isSelected = index == selectedIndex
                    // Animate opacity: Hide icon when selected (it moves to circle)
                    val alpha by animateFloatAsState(if (isSelected) 0f else 1f, animationSpec = tween(300), label = "alpha")
                    
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(64.dp)
                            .combinedClickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onTabSelected(item.id) },
                                onLongClick = { onTabLongPress(item.id) }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (alpha > 0f) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.label,
                                    tint = Gray400,
                                    modifier = Modifier.size(24.dp).graphicsLayer(alpha = alpha)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(item.label, fontSize = 10.sp, color = Gray400, fontWeight = FontWeight.Medium, modifier = Modifier.graphicsLayer(alpha = alpha))
                            }
                        }
                    }
                }
            }

            // Floating Selected Icon (The Circle)
            // Positioned to float in the "pocket"
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = indicatorOffset, y = (-34).dp)
                    .size(circleSize)
                    .shadow(12.dp, CircleShape, spotColor = selectedAccent.copy(alpha = 0.4f))
                    .clip(CircleShape)
                    .background(selectedAccent)
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onTabSelected(items[selectedIndex].id) },
                        onLongClick = { onTabLongPress(items[selectedIndex].id) }
                    ),
                contentAlignment = Alignment.Center
            ) {
                val selectedItem = items.getOrNull(selectedIndex) ?: items[0]
                Icon(
                    imageVector = selectedItem.icon,
                    contentDescription = selectedItem.label,
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
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
