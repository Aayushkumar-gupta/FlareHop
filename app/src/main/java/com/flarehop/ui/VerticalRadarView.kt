package com.flarehop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flarehop.R
import kotlin.math.roundToInt
import kotlin.math.sin

private data class Placed(
    val blip: RadarBlip,
    val flatM: Double,
    val toTheEast: Boolean,
    val height: HeightEstimate,
)

// Side view of a building: the rescuer stands at the bottom as the ground reference, and each
// survivor is drawn at the height worked out from GPS and Bluetooth. Only phones with both can
// be given a height; the rest are counted underneath.
@Composable
fun VerticalRadarView(
    blips: List<RadarBlip>,
    modifier: Modifier = Modifier,
    topInset: Dp = DEFAULT_TOP_INSET.dp,
) {
    val colors = SideColors(
        grid = MaterialTheme.colorScheme.outline,
        me = MaterialTheme.colorScheme.primary,
        sos = MaterialTheme.colorScheme.error,
        phone = MaterialTheme.colorScheme.tertiary,
        label = MaterialTheme.colorScheme.onSurface,
        building = MaterialTheme.colorScheme.onSurface,
    )
    val textMeasurer = rememberTextMeasurer()
    val building = ImageBitmap.imageResource(R.drawable.building)

    val placed = blips.mapNotNull(::place)
    val unplaced = blips.size - placed.size

    Canvas(modifier.clipToBounds()) {
        val caption = when {
            blips.isEmpty() -> "No one nearby"
            unplaced == 1 -> "1 phone has no GPS position, so its height is unknown"
            unplaced > 1 -> "$unplaced phones have no GPS position, so their height is unknown"
            else -> null
        }?.let { textMeasurer.measure(it, TextStyle(color = colors.label, fontSize = 13.sp)) }
        // The caption gets its own line above the building instead of covering the roof.
        val captionSpace = caption?.let { it.size.height + 8.dp.toPx() } ?: 0f
        caption?.let {
            drawText(it, topLeft = Offset(center.x - it.size.width / 2f, topInset.toPx()))
        }

        val groundY = size.height - BOTTOM_INSET.dp.toPx()
        val room = groundY - topInset.toPx() - captionSpace
        val fit = minOf(size.width * IMAGE_WIDTH_SHARE / building.width, room / building.height)
        val imageSize = IntSize(
            (building.width * fit).roundToInt(),
            (building.height * fit).roundToInt(),
        )
        val imageLeft = (center.x - imageSize.width / 2f)
        drawImage(
            building,
            dstOffset = IntOffset(
                imageLeft.roundToInt(),
                (groundY - imageSize.height).roundToInt(),
            ),
            dstSize = imageSize,
            alpha = BUILDING_ALPHA,
            colorFilter = ColorFilter.tint(colors.building),
        )

        // The picture's height stands for this many floors, enough to fit the highest survivor.
        val floors = maxOf(MIN_FLOORS, placed.maxOfOrNull { drawnFloors(it.height) } ?: 0)
        val pxPerM = imageSize.height / (floors * HeightEstimator.FLOOR_HEIGHT_M).toFloat()
        drawFloorScale(floors, groundY, pxPerM, imageLeft, colors.grid, textMeasurer)

        drawLine(colors.grid, Offset(0f, groundY), Offset(size.width, groundY), 2.dp.toPx())
        drawCircle(colors.me, 8.dp.toPx(), Offset(center.x, groundY))
        val you = textMeasurer.measure(
            "You, ground level",
            TextStyle(color = colors.label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
        )
        drawText(you, topLeft = Offset(center.x - you.size.width / 2f, groundY + 6.dp.toPx()))

        val rangeM = flatRangeM(placed)
        val halfWidth = imageSize.width / 2f
        placed.forEach { item ->
            val color = if (item.blip.isSos) colors.sos else colors.phone
            val side = if (item.toTheEast) 1f else -1f
            val x = center.x + side * (item.flatM / rangeM).toFloat().coerceIn(0f, 1f) * halfWidth
            drawSurvivor(item, x, groundY, pxPerM, color, colors.label, textMeasurer)
        }
    }
}

private data class SideColors(
    val grid: Color,
    val me: Color,
    val sos: Color,
    val phone: Color,
    val label: Color,
    val building: Color,
)

private fun place(blip: RadarBlip): Placed? {
    val estimate = blip.estimate
    val gps = estimate.gps ?: return null
    val height = HeightEstimator.estimate(gps, estimate.bluetooth) ?: return null
    return Placed(
        blip = blip,
        flatM = gps.distanceM,
        toTheEast = sin(Math.toRadians(gps.bearingDeg)) >= 0,
        height = height,
    )
}

private fun DrawScope.drawFloorScale(
    floors: Int,
    groundY: Float,
    pxPerM: Float,
    imageLeft: Float,
    color: Color,
    textMeasurer: TextMeasurer,
) {
    val floorPx = (HeightEstimator.FLOOR_HEIGHT_M * pxPerM).toFloat()
    // Skip labels when floors are packed tight, so they never overlap.
    val every = if (floorPx >= MIN_LABEL_GAP.dp.toPx()) 1 else 2
    val tickRight = imageLeft - 4.dp.toPx()
    (0..floors).forEach { level ->
        val y = groundY - level * floorPx
        drawLine(
            color.copy(alpha = 0.5f),
            Offset(tickRight - 10.dp.toPx(), y),
            Offset(tickRight, y),
            1.dp.toPx(),
        )
        if (level < floors && level % every == 0) {
            val text = textMeasurer.measure(
                if (level == 0) "G" else "$level",
                TextStyle(color = color, fontSize = 11.sp),
            )
            drawText(
                text,
                topLeft = Offset(
                    tickRight - 14.dp.toPx() - text.size.width,
                    y - floorPx / 2 - text.size.height / 2f,
                ),
            )
        }
    }
}

private fun DrawScope.drawSurvivor(
    item: Placed,
    x: Float,
    groundY: Float,
    pxPerM: Float,
    color: Color,
    labelColor: Color,
    textMeasurer: TextMeasurer,
) {
    val position = Offset(x, groundY - (item.height.heightM * pxPerM).toFloat())
    val dash = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 5.dp.toPx()))
    drawLine(color, Offset(x, groundY), position, 1.5.dp.toPx(), pathEffect = dash)

    val barX = x + 14.dp.toPx()
    val low = groundY - (item.height.minM * pxPerM).toFloat()
    val high = (groundY - (item.height.maxM * pxPerM).toFloat()).coerceAtLeast(0f)
    drawLine(color.copy(alpha = 0.6f), Offset(barX, low), Offset(barX, high), 4.dp.toPx())
    drawCircle(color, 9.dp.toPx(), position)

    val direction = if (item.toTheEast) "east" else "west"
    val text = textMeasurer.measure(
        "${item.blip.label}\n" +
            "~${distanceText(item.height.heightM)} up, ${distanceText(item.flatM)} $direction",
        TextStyle(color = labelColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
    )
    val gap = 24.dp.toPx()
    val left = if (x + gap + text.size.width > size.width) x - gap - text.size.width else x + gap
    val top = (position.y - text.size.height / 2f).coerceIn(0f, size.height - text.size.height)
    drawText(text, topLeft = Offset(left.coerceAtLeast(0f), top))
}

// One floor above the highest the survivor could be on, capped so a wild Bluetooth reading does
// not squash the building.
private fun drawnFloors(height: HeightEstimate) =
    (maxOf(height.floor, height.maxFloor) + 1).coerceIn(1, MAX_FLOORS)

private fun flatRangeM(placed: List<Placed>): Double {
    val farthest = placed.maxOfOrNull { it.flatM } ?: 0.0
    return RANGE_STEPS_M.firstOrNull { it >= farthest * RANGE_HEADROOM } ?: RANGE_STEPS_M.last()
}

private val RANGE_STEPS_M = listOf(10.0, 25.0, 50.0, 100.0, 200.0)
private const val RANGE_HEADROOM = 1.2
private const val DEFAULT_TOP_INSET = 150
private const val BOTTOM_INSET = 40
private const val IMAGE_WIDTH_SHARE = 0.7f
private const val BUILDING_ALPHA = 0.3f
private const val MIN_FLOORS = 5
private const val MAX_FLOORS = 20
private const val MIN_LABEL_GAP = 14
