package com.kiranaflow.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiranaflow.app.ui.theme.*
import kotlin.math.roundToInt

data class ChartDataPoint(val label: String, val value: Float)

@Composable
fun SalesTrendChart(
    data: List<ChartDataPoint>,
    modifier: Modifier = Modifier,
    isPositiveTrend: Boolean = true
) {
    if (data.isEmpty()) {
        Box(
            modifier = modifier.height(180.dp).fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Text("No data available", color = TextSecondary, fontSize = 12.sp)
        }
        return
    }

    val lineColor = if (isPositiveTrend) ChartUp else ChartDown
    val fillColorStart = if (isPositiveTrend) ChartUp.copy(alpha = 0.2f) else ChartDown.copy(alpha = 0.2f)
    val fillColorEnd = Color.Transparent

    var selectedIndex by remember(data) { mutableIntStateOf((data.size - 1).coerceAtLeast(0)) }

    Box(
        modifier = modifier
            .height(180.dp)
            .fillMaxWidth()
            .pointerInput(data) {
                if (data.isEmpty()) return@pointerInput
                detectTapGestures { pos ->
                    val w = size.width
                    val padding = 16.dp.toPx()
                    val chartWidth = (w - (padding * 2)).coerceAtLeast(1f)
                    val x = (pos.x - padding).coerceIn(0f, chartWidth)
                    val idx = ((x / chartWidth) * (data.size - 1)).roundToInt().coerceIn(0, data.size - 1)
                    selectedIndex = idx
                }
            }
            .pointerInput(data) {
                if (data.isEmpty()) return@pointerInput
                detectDragGestures { change, _ ->
                    val w = size.width
                    val padding = 16.dp.toPx()
                    val chartWidth = (w - (padding * 2)).coerceAtLeast(1f)
                    val x = (change.position.x - padding).coerceIn(0f, chartWidth)
                    val idx = ((x / chartWidth) * (data.size - 1)).roundToInt().coerceIn(0, data.size - 1)
                    selectedIndex = idx
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val padding = 16.dp.toPx()
            
            val chartWidth = width - (padding * 2)
            val chartHeight = height - (padding * 2)
            
            val maxVal = data.maxOfOrNull { it.value } ?: 1f
            val minVal = data.minOfOrNull { it.value } ?: 0f
            val valueRange = (maxVal - minVal).coerceAtLeast(1f)
            
            // Calculate points with smooth curve
            val points = data.mapIndexed { index, point ->
                val x = padding + (index.toFloat() / (data.size - 1).coerceAtLeast(1)) * chartWidth
                val normalizedValue = (point.value - minVal) / valueRange
                val y = padding + chartHeight - (normalizedValue * chartHeight)
                Offset(x, y)
            }

            // Create smooth path using cubic bezier
            val path = Path().apply {
                if (points.isNotEmpty()) {
                    moveTo(points.first().x, points.first().y)
                    
                    if (points.size > 1) {
                        for (i in 0 until points.size - 1) {
                            val current = points[i]
                            val next = points[i + 1]
                            
                            // Control points for smooth curve
                            val cp1x = current.x + (next.x - current.x) / 3
                            val cp1y = current.y
                            val cp2x = next.x - (next.x - current.x) / 3
                            val cp2y = next.y
                            
                            cubicTo(
                                x1 = cp1x,
                                y1 = cp1y,
                                x2 = cp2x,
                                y2 = cp2y,
                                x3 = next.x,
                                y3 = next.y
                            )
                        }
                    }
                }
            }
            
            // Create fill path
            val fillPath = Path().apply {
                addPath(path)
                if (points.isNotEmpty()) {
                    lineTo(points.last().x, height - padding)
                    lineTo(points.first().x, height - padding)
                }
                close()
            }

            // Draw fill gradient
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(fillColorStart, fillColorEnd),
                    startY = padding,
                    endY = height - padding
                )
            )

            // Draw line
            drawPath(
                path = path,
                color = lineColor,
                style = Stroke(width = 3.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
            )

            // Draw data points
            points.forEach { point ->
                drawCircle(
                    color = lineColor,
                    radius = 4.dp.toPx(),
                    center = point
                )
            }

            // Selection line + highlighted dot
            val idx = selectedIndex.coerceIn(0, (points.size - 1).coerceAtLeast(0))
            if (points.isNotEmpty()) {
                val p = points[idx]
                drawLine(
                    color = Gray300,
                    start = Offset(p.x, padding),
                    end = Offset(p.x, height - padding),
                    strokeWidth = 1.dp.toPx()
                )
                drawCircle(color = Color.White, radius = 7.dp.toPx(), center = p)
                drawCircle(color = lineColor, radius = 5.dp.toPx(), center = p)
            }
        }
        
        // Tooltip (label + value)
        if (data.isNotEmpty()) {
            val idx = selectedIndex.coerceIn(0, data.size - 1)
            val point = data[idx]
            val valueLabel = "₹${point.value.toInt()}"
            val tip = "${point.label} • $valueLabel"

            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Gray100)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(tip, fontSize = 11.sp, color = TextPrimary, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            }

            Text(
                text = point.label,
                fontSize = 10.sp,
                color = TextSecondary,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 4.dp)
            )
        }
    }
}

// Legacy function for backward compatibility
@Composable
@Suppress("UNUSED_PARAMETER")
fun SimpleAreaChart(
    data: List<ChartDataPoint>,
    modifier: Modifier = Modifier,
    lineColor: Color = ChartUp,
    fillColorStart: Color = ChartUp.copy(alpha = 0.2f),
    fillColorEnd: Color = Color.Transparent
) {
    SalesTrendChart(
        data = data,
        modifier = modifier,
        isPositiveTrend = lineColor == ChartUp
    )
}
