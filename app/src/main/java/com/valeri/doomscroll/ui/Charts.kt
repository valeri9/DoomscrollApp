package com.valeri.doomscroll.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.valeri.doomscroll.ui.theme.ChartPalette
import com.valeri.doomscroll.ui.theme.Palette

data class BarDatum(val label: String, val value: Float, val emphasised: Boolean = false)

/**
 * Daily totals as bars. One series, so no legend: the caption names it.
 *
 * Tapping a bar is the phone's equivalent of hover — it pins a readout above the chart
 * rather than putting a number on every bar.
 */
@Composable
fun DailyBarChart(
    data: List<BarDatum>,
    valueFormatter: (Float) -> String,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 150.dp,
) {
    if (data.isEmpty()) {
        EmptyChart("No data yet", modifier)
        return
    }

    var selected by remember(data.size) { mutableIntStateOf(-1) }
    val max = data.maxOf { it.value }.coerceAtLeast(1f)
    val selectedDatum = data.getOrNull(selected)

    Column(modifier) {
        Text(
            text = selectedDatum?.let { "${it.label} · ${valueFormatter(it.value)}" }
                ?: "Tap a bar for the exact figure",
            color = if (selectedDatum != null) Palette.TextPrimary else Palette.TextMuted,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .pointerInput(data) {
                    detectTapGestures { offset ->
                        val slot = size.width.toFloat() / data.size
                        val index = (offset.x / slot).toInt().coerceIn(0, data.lastIndex)
                        selected = if (selected == index) -1 else index
                    }
                }
        ) {
            // Recessive baseline only — no full grid competing with the bars.
            drawLine(
                color = ChartPalette.Grid,
                start = Offset(0f, size.height),
                end = Offset(size.width, size.height),
                strokeWidth = 2f,
            )

            val slot = size.width / data.size
            // A 2px surface gap keeps adjacent bars from reading as one block.
            val gap = 2.dp.toPx()
            val barWidth = (slot - gap).coerceAtLeast(2f)
            val radius = CornerRadius(4.dp.toPx(), 4.dp.toPx())

            data.forEachIndexed { index, datum ->
                val fraction = datum.value / max
                val barHeight = (size.height - 2f) * fraction
                val left = index * slot + gap / 2f
                val isSelected = index == selected

                val color = when {
                    datum.value == 0f -> ChartPalette.Track
                    isSelected -> ChartPalette.Teal
                    datum.emphasised -> ChartPalette.Teal
                    selected >= 0 -> ChartPalette.Teal.copy(alpha = 0.45f)
                    else -> ChartPalette.Teal.copy(alpha = 0.85f)
                }

                drawRoundRect(
                    color = color,
                    topLeft = Offset(left, size.height - barHeight),
                    size = Size(barWidth, barHeight.coerceAtLeast(2f)),
                    cornerRadius = radius,
                )
            }
        }

        // Only the ends and any emphasised day get a label; never one per bar.
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            Text(data.first().label, color = Palette.TextMuted, fontSize = 11.sp)
            Box(Modifier.weight(1f))
            Text(data.last().label, color = Palette.TextMuted, fontSize = 11.sp)
        }
    }
}

/** Horizontal magnitude bars for the per-app breakdown. */
@Composable
fun HorizontalBar(
    fraction: Float,
    color: Color = ChartPalette.Teal,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.fillMaxWidth().height(8.dp)) {
        val radius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
        drawRoundRect(color = ChartPalette.Track, size = size, cornerRadius = radius)
        val width = (size.width * fraction.coerceIn(0f, 1f)).coerceAtLeast(4f)
        drawRoundRect(color = color, size = Size(width, size.height), cornerRadius = radius)
    }
}

/**
 * Two-part split shown as one track. Both parts carry a direct label alongside, so the
 * split never rests on colour alone.
 */
@Composable
fun SplitBar(nightFraction: Float, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(10.dp)) {
        val radius = CornerRadius(5.dp.toPx(), 5.dp.toPx())
        val gap = 2.dp.toPx()
        drawRoundRect(color = ChartPalette.Track, size = size, cornerRadius = radius)

        val nightWidth = size.width * nightFraction.coerceIn(0f, 1f)
        if (nightWidth > 0f) {
            drawRoundRect(
                color = ChartPalette.Amber,
                size = Size(nightWidth, size.height),
                cornerRadius = radius,
            )
        }
        val dayWidth = size.width - nightWidth - gap
        if (dayWidth > 0f) {
            drawRoundRect(
                color = ChartPalette.Teal,
                topLeft = Offset(nightWidth + gap, 0f),
                size = Size(dayWidth, size.height),
                cornerRadius = radius,
            )
        }
    }
}

@Composable
fun EmptyChart(message: String, modifier: Modifier = Modifier) {
    Text(
        message,
        color = Palette.TextMuted,
        fontSize = 13.sp,
        fontWeight = FontWeight.Normal,
        modifier = modifier.padding(vertical = 20.dp),
    )
}
