package com.personal.hydra.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

data class BarValue(val fraction: Float, val completed: Boolean)

/** Lightweight bar chart drawn with Canvas — no external dependency. */
@Composable
fun BarChart(
    bars: List<BarValue>,
    modifier: Modifier = Modifier,
    completedColor: Color = MaterialTheme.colorScheme.primary,
    incompleteColor: Color = MaterialTheme.colorScheme.surfaceVariant,
) {
    Canvas(
        modifier
            .fillMaxWidth()
            .height(120.dp),
    ) {
        if (bars.isEmpty()) return@Canvas
        val n = bars.size
        val gap = 3.dp.toPx()
        val barWidth = ((size.width - gap * (n - 1)) / n).coerceAtLeast(1f)
        bars.forEachIndexed { i, b ->
            val h = b.fraction.coerceIn(0f, 1f) * size.height
            val x = i * (barWidth + gap)
            val radius = CornerRadius(barWidth / 3f, barWidth / 3f)
            // track
            drawRoundRect(
                color = incompleteColor.copy(alpha = 0.4f),
                topLeft = Offset(x, 0f),
                size = Size(barWidth, size.height),
                cornerRadius = radius,
            )
            // value
            drawRoundRect(
                color = if (b.completed) completedColor else incompleteColor,
                topLeft = Offset(x, size.height - h),
                size = Size(barWidth, h),
                cornerRadius = radius,
            )
        }
    }
}
