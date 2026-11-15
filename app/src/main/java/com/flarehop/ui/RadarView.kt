package com.flarehop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
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
import kotlin.math.roundToInt
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

    Canvas(modifier.clipToBounds()) {
        val radius = size.minDimension / 2f * RADAR_FILL
        val rangeM = radarRangeM(blips)
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
            if (blips.isEmpty()) "You\nNo one nearby" else "You",
            TextStyle(color = colors.label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
        )
        drawText(you, topLeft = Offset(center.x - you.size.width / 2f, center.y + 10.dp.toPx()))

        blips.forEach { blip ->
            drawBlip(blip, rangeM, pxPerM, if (blip.isSos) colors.sos else colors.phone)
            drawBlipLabel(blip, rangeM, pxPerM, textMeasurer, colors.label)
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

private fun DrawScope.drawBlip(blip: RadarBlip, rangeM: Double, pxPerM: Float, color: Color) {
    val estimate = blip.estimate
    val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx()))
    val position = blipPosition(blip, rangeM, pxPerM)
    val dot = 8.dp.toPx()

    estimate.gps?.let { gps ->
        val gpsPoint =
            pointAt(center, onRadar(gps.distanceM, rangeM, pxPerM), angleOf(gps.bearingDeg))
        val half = 6.dp.toPx()
        drawRect(
            color,
            topLeft = Offset(gpsPoint.x - half, gpsPoint.y - half),
            size = Size(half * 2, half * 2),
            style = Stroke(2.dp.toPx()),
        )
        drawLine(color.copy(alpha = 0.6f), gpsPoint, position, 1.5.dp.toPx(), pathEffect = dash)
    }

    val bluetooth = estimate.bluetooth
    val bearing = estimate.bearingDeg
    if (bluetooth != null) {
        val r = onRadar(bluetooth.distanceM, rangeM, pxPerM)
        val ringStyle = Stroke(2.dp.toPx(), pathEffect = dash)
        // Signal strength alone has no direction: a whole ring when there is no bearing, and a
        // short arc around the bearing when GPS supplies one.
        if (bearing == null) {
            drawCircle(color.copy(alpha = 0.5f), r, center, style = ringStyle)
        } else {
            drawArc(
                color.copy(alpha = 0.7f),
                startAngle = angleOf(bearing) - BLUETOOTH_ARC_DEG / 2,
                sweepAngle = BLUETOOTH_ARC_DEG,
                useCenter = false,
                topLeft = Offset(center.x - r, center.y - r),
                size = Size(r * 2, r * 2),
                style = ringStyle,
            )
        }
    }

    if (bearing == null) {
        drawCircle(color, dot, position, style = Stroke(3.dp.toPx()))
    } else {
        val halo = (estimate.errorM * pxPerM).toFloat().coerceAtLeast(dot)
        drawCircle(color.copy(alpha = 0.18f), halo, position)
        drawCircle(color, dot, position)
    }
}

private fun DrawScope.drawBlipLabel(
    blip: RadarBlip,
    rangeM: Double,
    pxPerM: Float,
    textMeasurer: TextMeasurer,
    color: Color,
) {
    val position = blipPosition(blip, rangeM, pxPerM)
    val label = textMeasurer.measure(
        blipText(blip),
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

private fun blipText(blip: RadarBlip): String {
    val estimate = blip.estimate
    val source = when (estimate.source) {
        EstimateSource.GPS_AND_BLUETOOTH -> "GPS + Bluetooth"

        EstimateSource.GPS -> "GPS"

        EstimateSource.BLUETOOTH ->
            if (estimate.gps != null) "Bluetooth (GPS disagrees)" else "Bluetooth"
    }
    val direction = if (estimate.bearingDeg == null) ", direction unknown" else ""
    return "${blip.label}\n~${distanceText(estimate.distanceM)} " +
        "±${estimate.errorM.roundToInt()} m\n$source$direction"
}

private fun DrawScope.blipPosition(blip: RadarBlip, rangeM: Double, pxPerM: Float): Offset {
    val estimate = blip.estimate
    val angle = estimate.bearingDeg?.let(::angleOf) ?: estimatedAngle(blip.phoneId)
    return pointAt(center, onRadar(estimate.distanceM, rangeM, pxPerM), angle)
}

// Anything past the outer ring is pinned to it, so every phone heard stays on screen.
private fun onRadar(meters: Double, rangeM: Double, pxPerM: Float): Float =
    (meters.coerceAtMost(rangeM) * pxPerM).toFloat()

// The radar is north-up and Compose angles start at 3 o'clock, so north is -90.
private fun angleOf(bearingDeg: Double): Float = bearingDeg.toFloat() - 90f

// Phones with no direction get a fixed angle from their ID, so their dots do not jump around.
private fun estimatedAngle(phoneId: Int) = (phoneId * GOLDEN_ANGLE_DEG) % 360f

private fun radarRangeM(blips: List<RadarBlip>): Double {
    val farthest = blips.maxOfOrNull { it.estimate.distanceM } ?: 0.0
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
private const val BLUETOOTH_ARC_DEG = 40f
private const val GOLDEN_ANGLE_DEG = 137.508f
