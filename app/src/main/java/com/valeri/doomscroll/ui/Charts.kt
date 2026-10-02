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

/** One bar. In a stacked chart, part is the solid bottom segment of value (doomscroll time within app time). */
data class BarDatum(
    val label: String,
    val value: Float,
    val part: Float = 0f,
    val emphasised: Boolean = false,
)

/**
 * Bars over a sequence of days or hours. Tapping a bar is the phone's equivalent of hover — it
 * pins a readout above the chart rather than putting a number on every bar.
 *
 * stacked draws each bar as a solid part at the bottom with the rest of value as a lighter tint
 * above it, split by a 2px gap; the caller supplies a legend for the two.
 */
@Composable
fun DailyBarChart(
    data: List<BarDatum>,
    readout: (BarDatum) -> String,
    modifier: Modifier = Modifier,
    stacked: Boolean = false,
    height: androidx.compose.ui.unit.Dp = 150.dp,
) {
    if (data.isEmpty()) {
        EmptyChart("No data yet", modifier)
        return
    }

    var selected by remember(data.size) { mutableIntStateOf(-1) }
    val max = data.maxOf { maxOf(it.value, it.part) }.coerceAtLeast(1f)
    val selectedDatum = data.getOrNull(selected)

    Column(modifier) {
        Text(
            text = selectedDatum?.let(readout) ?: "Tap a bar for the exact figure",
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
            // A 2px surface gap keeps adjacent bars, and stacked segments, from reading as one block.
            val gap = 2.dp.toPx()
            val barWidth = (slot - gap).coerceAtLeast(2f)
            val radius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
            val usable = size.height - 2f

            data.forEachIndexed { index, datum ->
                val left = index * slot + gap / 2f
                val dimmed = selected >= 0 && index != selected

                if (stacked) {
                    val whole = maxOf(datum.value, datum.part)
                    val wholeHeight = usable * whole / max
                    val partHeight = usable * datum.part / max
                    if (whole == 0f) {
                        drawRoundRect(ChartPalette.Track, Offset(left, size.height - 2f), Size(barWidth, 2f), radius)
                        return@forEachIndexed
                    }
                    val restHeight = wholeHeight - partHeight - if (partHeight > 0f) gap else 0f
                    if (restHeight > 0f) {
                        drawRoundRect(
                            color = ChartPalette.Teal.copy(alpha = if (dimmed) 0.18f else 0.35f),
                            topLeft = Offset(left, size.height - wholeHeight),
                            size = Size(barWidth, restHeight),
                            cornerRadius = radius,
                        )
                    }
                    if (partHeight > 0f) {
                        drawRoundRect(
                            color = ChartPalette.Teal.copy(alpha = if (dimmed) 0.45f else 1f),
                            topLeft = Offset(left, size.height - partHeight),
                            size = Size(barWidth, partHeight.coerceAtLeast(2f)),
                            cornerRadius = radius,
                        )
                    }
                    return@forEachIndexed
                }

                val barHeight = usable * datum.value / max
                val color = when {
                    datum.value == 0f -> ChartPalette.Track
                    index == selected || (datum.emphasised && selected < 0) -> ChartPalette.Teal
                    dimmed -> ChartPalette.Teal.copy(alpha = 0.45f)
                    else -> ChartPalette.Teal.copy(alpha = 0.7f)
                }
                drawRoundRect(
                    color = color,
                    topLeft = Offset(left, size.height - barHeight.coerceAtLeast(2f)),
                    size = Size(barWidth, barHeight.coerceAtLeast(2f)),
                    cornerRadius = radius,
                )
            }
        }

        // Only the ends get a label; never one per bar.
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            Text(data.first().label, color = Palette.TextMuted, fontSize = 11.sp)
            Box(Modifier.weight(1f))
            Text(data.last().label, color = Palette.TextMuted, fontSize = 11.sp)
        }
    }
}

/** Horizontal magnitude bars. track = false draws only the fill, for layering one bar over another. */
@Composable
fun HorizontalBar(
    fraction: Float,
    color: Color = ChartPalette.Teal,
    modifier: Modifier = Modifier,
    track: Boolean = true,
) {
    Canvas(modifier.fillMaxWidth().height(8.dp)) {
        val radius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
        if (track) drawRoundRect(color = ChartPalette.Track, size = size, cornerRadius = radius)
        val width = (size.width * fraction.coerceIn(0f, 1f)).coerceAtLeast(4f)
        drawRoundRect(color = color, size = Size(width, size.height), cornerRadius = radius)
    }
}

/**
 * Two-part split shown as one track. Callers label both parts alongside, so the split never
 * rests on colour alone.
 */
@Composable
fun SplitBar(
    leftFraction: Float,
    leftColor: Color,
    rightColor: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.fillMaxWidth().height(10.dp)) {
        val radius = CornerRadius(5.dp.toPx(), 5.dp.toPx())
        val gap = 2.dp.toPx()
        drawRoundRect(color = ChartPalette.Track, size = size, cornerRadius = radius)

        val leftWidth = size.width * leftFraction.coerceIn(0f, 1f)
        if (leftWidth > 0f) {
            drawRoundRect(color = leftColor, size = Size(leftWidth, size.height), cornerRadius = radius)
        }
        val rightStart = if (leftWidth > 0f) leftWidth + gap else 0f
        val rightWidth = size.width - rightStart
        if (leftFraction < 1f && rightWidth > 0f) {
            drawRoundRect(
                color = rightColor,
                topLeft = Offset(rightStart, 0f),
                size = Size(rightWidth, size.height),
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
