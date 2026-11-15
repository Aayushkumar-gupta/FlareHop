package com.flarehop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

data class RadarBlip(
    val phoneId: Int,
    val label: String,
    val estimate: LocationEstimate,
    val isSos: Boolean,
)

@Composable
fun RadarView(blips: List<RadarBlip>, modifier: Modifier = Modifier) {
    val colors = RadarColors(
        grid = MaterialTheme.colorScheme.outline,
        me = MaterialTheme.colorScheme.primary,
        sos = MaterialTheme.colorScheme.error,
        phone = MaterialTheme.colorScheme.tertiary,
        label = MaterialTheme.colorScheme.onSurface,
    )
    val textMeasurer = rememberTextMeasurer()
    // Only GPS coordinates are drawn; a phone without a fix stays off the radar.
    val placed = blips.filter { it.estimate.gps != null }

    Canvas(modifier.clipToBounds()) {
        val radius = size.minDimension / 2f * RADAR_FILL
        val rangeM = radarRangeM(placed)
        val pxPerM = (radius / rangeM).toFloat()
        val line = 1.dp.toPx()

        (1..RINGS).forEach { ring ->
            val r = radius * ring / RINGS
            drawCircle(colors.grid, r, center, style = Stroke(line))
            val text = textMeasurer.measure(
                distanceText(rangeM * ring / RINGS),
                TextStyle(color = colors.grid, fontSize = 12.sp),
            )
            drawText(text, topLeft = pointAt(center, r, RING_LABEL_ANGLE))
        }
        repeat(SPOKES) { i ->
            drawLine(colors.grid, center, pointAt(center, radius, i * 360f / SPOKES), line)
        }

        drawCircle(colors.me, 7.dp.toPx(), center)
        val you = textMeasurer.measure(
            when {
                blips.isEmpty() -> "You\nNo one nearby"
                placed.isEmpty() -> "You\nNo GPS position yet"
                else -> "You"
            },
            TextStyle(color = colors.label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
        )
        drawText(you, topLeft = Offset(center.x - you.size.width / 2f, center.y + 10.dp.toPx()))

        placed.forEach { blip ->
            val gps = blip.estimate.gps ?: return@forEach
            drawBlip(gps, rangeM, pxPerM, if (blip.isSos) colors.sos else colors.phone)
            drawBlipLabel(blip, gps, rangeM, pxPerM, textMeasurer, colors.label)
        }
    }
}

private data class RadarColors(
    val grid: Color,
    val me: Color,
    val sos: Color,
    val phone: Color,
    val label: Color,
)

private fun DrawScope.drawBlip(gps: GpsReference, rangeM: Double, pxPerM: Float, color: Color) {
    val point = gpsPoint(gps, rangeM, pxPerM)
    val half = 6.dp.toPx()
    drawRect(
        color,
        topLeft = Offset(point.x - half, point.y - half),
        size = Size(half * 2, half * 2),
        style = Stroke(2.dp.toPx()),
    )
}

private fun DrawScope.drawBlipLabel(
    blip: RadarBlip,
    gps: GpsReference,
    rangeM: Double,
    pxPerM: Float,
    textMeasurer: TextMeasurer,
    color: Color,
) {
    val position = gpsPoint(gps, rangeM, pxPerM)
    val label = textMeasurer.measure(
        "${blip.label}\n${distanceText(gps.distanceM)}",
        TextStyle(color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
    )
    val gap = 12.dp.toPx()
    val x = if (position.x + gap + label.size.width > size.width) {
        position.x - gap - label.size.width
    } else {
        position.x + gap
    }
    val y = (position.y - label.size.height / 2f).coerceIn(0f, size.height - label.size.height)
    drawText(label, topLeft = Offset(x.coerceAtLeast(0f), y))
}

private fun DrawScope.gpsPoint(gps: GpsReference, rangeM: Double, pxPerM: Float): Offset =
    pointAt(center, onRadar(gps.distanceM, rangeM, pxPerM), angleOf(gps.bearingDeg))

// Anything past the outer ring is pinned to it, so every phone heard stays on screen.
private fun onRadar(meters: Double, rangeM: Double, pxPerM: Float): Float =
    (meters.coerceAtMost(rangeM) * pxPerM).toFloat()

// The radar is north-up and Compose angles start at 3 o'clock, so north is -90.
private fun angleOf(bearingDeg: Double): Float = bearingDeg.toFloat() - 90f

private fun radarRangeM(blips: List<RadarBlip>): Double {
    val farthest = blips.maxOfOrNull { it.estimate.gps?.distanceM ?: 0.0 } ?: 0.0
    return RANGE_STEPS_M.firstOrNull { it >= farthest * RANGE_HEADROOM } ?: RANGE_STEPS_M.last()
}

private fun pointAt(center: Offset, distance: Float, angleDeg: Float): Offset {
    val rad = Math.toRadians(angleDeg.toDouble())
    return Offset(
        center.x + distance * cos(rad).toFloat(),
        center.y + distance * sin(rad).toFloat(),
    )
}

private val RANGE_STEPS_M = listOf(10.0, 25.0, 50.0, 100.0, 200.0)
private const val RANGE_HEADROOM = 1.15
private const val RADAR_FILL = 0.9f
private const val SPOKES = 12
private const val RINGS = 4
private const val RING_LABEL_ANGLE = 45f
