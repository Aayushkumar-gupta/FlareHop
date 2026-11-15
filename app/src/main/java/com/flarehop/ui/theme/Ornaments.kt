package com.flarehop.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.random.Random

private data class Star(val x: Float, val y: Float, val radius: Float, val alpha: Float)

// A still night sky: fixed stars over a sea that darkens toward the horizon. Nothing moves, so it
// costs no battery once drawn.
@Composable
fun NightSky(modifier: Modifier = Modifier) {
    val stars = remember {
        val random = Random(STAR_SEED)
        List(STAR_COUNT) {
            Star(
                x = random.nextFloat(),
                y = random.nextFloat() * SKY_HEIGHT,
                radius = 0.6f + random.nextFloat() * 1.2f,
                alpha = 0.2f + random.nextFloat() * 0.6f,
            )
        }
    }
    Canvas(modifier.fillMaxSize()) {
        drawRect(Brush.verticalGradient(listOf(Horizon, NightSea, DeepSea)))
        stars.forEach { star ->
            drawCircle(
                color = Parchment.copy(alpha = star.alpha),
                radius = star.radius.dp.toPx(),
                center = Offset(star.x * size.width, star.y * size.height),
            )
        }
    }
}

// The Greek key: a squared spiral repeated along a base line, between two thin rules.
@Composable
fun MeanderBorder(modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(MEANDER_HEIGHT.dp)) {
        val stroke = 1.5.dp.toPx()
        val top = stroke * 2
        val bottom = size.height - stroke * 2
        val h = bottom - top
        val unit = h * 1.5f
        drawLine(BronzeDim, Offset(0f, stroke / 2), Offset(size.width, stroke / 2), stroke)
        drawLine(
            BronzeDim,
            Offset(0f, size.height - stroke / 2),
            Offset(size.width, size.height - stroke / 2),
            stroke,
        )
        val path = Path()
        var x = 0f
        while (x + unit <= size.width) {
            path.moveTo(x, bottom)
            path.lineTo(x, top)
            path.lineTo(x + unit * 0.8f, top)
            path.lineTo(x + unit * 0.8f, top + h * 0.7f)
            path.lineTo(x + unit * 0.35f, top + h * 0.7f)
            path.lineTo(x + unit * 0.35f, top + h * 0.35f)
            path.lineTo(x + unit * 0.55f, top + h * 0.35f)
            path.moveTo(x, bottom)
            path.lineTo(x + unit, bottom)
            x += unit
        }
        drawPath(path, Bronze, style = Stroke(stroke))
    }
}

private const val STAR_SEED = 8
private const val STAR_COUNT = 90
private const val SKY_HEIGHT = 0.7f
private const val MEANDER_HEIGHT = 22
