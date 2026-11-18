package com.example.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.example.data.model.OpenPosition
import com.example.data.model.TickPoint
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CoralLoss
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.EmeraldProfit
import com.example.ui.theme.MonoSmall

@Composable
fun TickChart(
    ticks: List<TickPoint>,
    openPosition: OpenPosition?,
    pipSize: Int,
    emaFastSeries: List<Double> = emptyList(),
    smaSlowSeries: List<Double> = emptyList(),
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "tickHalo")
    val haloScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 2.1f,
        animationSpec = infiniteRepeatable(tween(950), RepeatMode.Restart),
        label = "haloScale"
    )
    val haloAlpha by infiniteTransition.animateFloat(
        initialValue = 0.65f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(tween(950), RepeatMode.Restart),
        label = "haloAlpha"
    )

    var touchedIndex by remember { mutableStateOf<Int?>(null) }

    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
    val isUpward = if (ticks.size >= 2) ticks.last().quote >= ticks.first().quote else true
    val lineColor = if (isUpward) EmeraldProfit else CoralLoss

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(210.dp)
    ) {
        if (ticks.size < 2) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Streaming live 1s ticks from Deriv WebSocket...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Box
        }

        val visibleTicks = ticks.takeLast(60)
        val visibleCount = visibleTicks.size
        val visibleEma = emaFastSeries.takeLast(visibleCount)
        val visibleSma = smaSlowSeries.takeLast(visibleCount)
        val quotes = visibleTicks.map { it.quote }
        val entryQuote = openPosition?.entrySpot
        val allValues = quotes + visibleEma + visibleSma + listOfNotNull(entryQuote)
        val minVal = allValues.minOrNull() ?: 0.0
        val maxVal = allValues.maxOrNull() ?: 1.0
        val range = (maxVal - minVal).takeIf { it > 1e-9 } ?: 1.0
        val paddedMin = minVal - range * 0.12
        val paddedMax = maxVal + range * 0.12
        val paddedRange = (paddedMax - paddedMin).coerceAtLeast(1e-9)

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(visibleTicks.size) {
                    detectHorizontalDragGestures(
                        onDragEnd = { touchedIndex = null },
                        onDragCancel = { touchedIndex = null },
                        onHorizontalDrag = { change, _ ->
                            val fraction = (change.position.x / size.width).coerceIn(0f, 1f)
                            touchedIndex = (fraction * (visibleTicks.size - 1)).toInt()
                        }
                    )
                }
        ) {
            val w = size.width
            val h = size.height
            val topPad = 14.dp.toPx()
            val bottomPad = 14.dp.toPx()
            val usableH = (h - topPad - bottomPad).coerceAtLeast(10f)

            fun yFor(quote: Double): Float {
                val norm = ((quote - paddedMin) / paddedRange).toFloat()
                return topPad + usableH * (1f - norm)
            }

            // Subtle horizontal grid lines
            val gridSteps = 4
            for (i in 0..gridSteps) {
                val y = topPad + (usableH * i / gridSteps)
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(w, y),
                    strokeWidth = 1.dp.toPx()
                )
            }

            // Draw open position entry line
            if (entryQuote != null) {
                val entryY = yFor(entryQuote)
                drawLine(
                    color = AmberWarning,
                    start = Offset(0f, entryY),
                    end = Offset(w, entryY),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                )
            }

            val linePath = Path()
            val fillPath = Path()
            val stepX = w / (visibleTicks.size - 1).coerceAtLeast(1)

            visibleTicks.forEachIndexed { idx, pt ->
                val x = idx * stepX
                val y = yFor(pt.quote)
                if (idx == 0) {
                    linePath.moveTo(x, y)
                    fillPath.moveTo(x, h)
                    fillPath.lineTo(x, y)
                } else {
                    linePath.lineTo(x, y)
                    fillPath.lineTo(x, y)
                }
                if (idx == visibleTicks.lastIndex) {
                    fillPath.lineTo(x, h)
                    fillPath.close()
                }
            }

            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        lineColor.copy(alpha = 0.28f),
                        lineColor.copy(alpha = 0.01f)
                    ),
                    startY = topPad,
                    endY = h
                )
            )

            drawPath(
                path = linePath,
                color = lineColor,
                style = Stroke(
                    width = 2.4.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )

            // Draw SMA(15) slow moving average overlay (amber dashed)
            if (visibleSma.size == visibleCount && visibleCount >= 2) {
                val smaPath = Path()
                visibleSma.forEachIndexed { idx, v ->
                    val x = idx * stepX
                    val y = yFor(v)
                    if (idx == 0) smaPath.moveTo(x, y) else smaPath.lineTo(x, y)
                }
                drawPath(
                    path = smaPath,
                    color = AmberWarning.copy(alpha = 0.8f),
                    style = Stroke(
                        width = 1.4.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                    )
                )
            }

            // Draw EMA(5) fast moving average overlay (cyan smooth)
            if (visibleEma.size == visibleCount && visibleCount >= 2) {
                val emaPath = Path()
                visibleEma.forEachIndexed { idx, v ->
                    val x = idx * stepX
                    val y = yFor(v)
                    if (idx == 0) emaPath.moveTo(x, y) else emaPath.lineTo(x, y)
                }
                drawPath(
                    path = emaPath,
                    color = CyanPulse.copy(alpha = 0.85f),
                    style = Stroke(
                        width = 1.6.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }

            // Latest price pulsing dot
            val lastX = (visibleTicks.size - 1) * stepX
            val lastY = yFor(visibleTicks.last().quote)
            drawCircle(
                color = lineColor.copy(alpha = haloAlpha),
                radius = 6.dp.toPx() * haloScale,
                center = Offset(lastX, lastY)
            )
            drawCircle(
                color = lineColor,
                radius = 4.5.dp.toPx(),
                center = Offset(lastX, lastY)
            )
            drawCircle(
                color = Color.White,
                radius = 2.dp.toPx(),
                center = Offset(lastX, lastY)
            )

            // Interactive crosshair on drag
            touchedIndex?.let { idx ->
                val safeIdx = idx.coerceIn(0, visibleTicks.lastIndex)
                val cx = safeIdx * stepX
                val cy = yFor(visibleTicks[safeIdx].quote)
                drawLine(
                    color = CyanPulse.copy(alpha = 0.7f),
                    start = Offset(cx, 0f),
                    end = Offset(cx, h),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                )
                drawCircle(
                    color = CyanPulse,
                    radius = 5.dp.toPx(),
                    center = Offset(cx, cy)
                )
            }
        }

        // High / Low overlay labels
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "H ${formatQuote(maxVal, pipSize)}",
                    style = MonoSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                touchedIndex?.let { idx ->
                    val pt = visibleTicks.getOrNull(idx)
                    if (pt != null) {
                        Text(
                            text = "Tick ${formatQuote(pt.quote, pipSize)}",
                            style = MonoSmall,
                            color = CyanPulse,
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                    MaterialTheme.shapes.extraSmall
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            Text(
                text = "L ${formatQuote(minVal, pipSize)}",
                style = MonoSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
