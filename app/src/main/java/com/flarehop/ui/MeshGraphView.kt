package com.flarehop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

// Fixed slots, not positions: this phone in the middle and the others evenly round it in phone
// ID order, so nothing moves with signal noise and a phone only moves when one joins or leaves.
fun graphSlots(count: Int): List<Pair<Float, Float>> = List(count) { i ->
    val angle = -PI / 2 + 2 * PI * i / count
    cos(angle).toFloat() to sin(angle).toFloat()
}

@Composable
fun MeshGraphView(graph: MeshGraph, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val colors = GraphColors(
        me = MaterialTheme.colorScheme.primary,
        victim = MaterialTheme.colorScheme.error,
        carrier = CARRIER_ORANGE,
        rescuer = RESCUER_GREEN,
        phone = MaterialTheme.colorScheme.secondary,
        hears = MaterialTheme.colorScheme.outline,
        text = MaterialTheme.colorScheme.onSurface,
        onNode = Color.White,
    )
    Canvas(modifier.fillMaxWidth().aspectRatio(1f)) {
        val nodeR = NODE_RADIUS.dp.toPx()
        val ring = size.minDimension / 2f - nodeR - LABEL_SPACE.dp.toPx()
        val positions = HashMap<Int, Offset>()
        positions[graph.me.phoneId] = center
        graphSlots(graph.others.size).zip(graph.others).forEach { (slot, node) ->
            positions[node.phoneId] = center + Offset(slot.first * ring, slot.second * ring)
        }

        val pairs = graph.edges.map { it.from to it.to }.toSet()
        graph.edges.forEach { edge ->
            val from = positions[edge.from] ?: return@forEach
            val to = positions[edge.to] ?: return@forEach
            // Two arrows between the same phones run side by side instead of on top of each other.
            val shift = if ((edge.to to edge.from) in pairs) PARALLEL_GAP.dp.toPx() else 0f
            drawEdge(edge, from, to, nodeR, shift, colors)
            edge.rssi?.takeIf { edge.kind == EdgeKind.HEARS }?.let { rssi ->
                val mid = (from + to) / 2f
                val text = measurer.measure("$rssi dBm", TextStyle(fontSize = 11.sp))
                drawText(
                    text,
                    color = colors.hears,
                    topLeft = mid - Offset(text.size.width / 2f, text.size.height + 2f),
                )
            }
        }

        (listOf(graph.me) + graph.others).forEach { node ->
            val at = positions.getValue(node.phoneId)
            drawCircle(colors.of(node.kind), nodeR, at)
            val id = measurer.measure(
                phoneLabel(node.phoneId),
                TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold),
            )
            drawText(
                id,
                color = colors.onNode,
                topLeft = at - Offset(id.size.width / 2f, id.size.height / 2f),
            )
            val caption = measurer.measure(
                caption(node, isMe = node.phoneId == graph.me.phoneId),
                TextStyle(fontSize = 12.sp, textAlign = TextAlign.Center),
            )
            drawText(
                caption,
                color = colors.text,
                topLeft = at + Offset(-caption.size.width / 2f, nodeR + 4.dp.toPx()),
            )
        }
    }
}

private fun caption(node: GraphNode, isMe: Boolean): String {
    val part = when {
        isMe && node.kind == NodeKind.RESCUER -> "You, rescuer"

        isMe -> "You"

        else -> when (node.kind) {
            NodeKind.RESCUER -> "Rescuer"
            NodeKind.VICTIM -> "SOS"
            NodeKind.CARRIER -> "Carrier"
            else -> "Phone"
        }
    }
    return node.battery?.let { "$part\n$it%" } ?: part
}

private fun DrawScope.drawEdge(
    edge: GraphEdge,
    from: Offset,
    to: Offset,
    nodeR: Float,
    shift: Float,
    colors: GraphColors,
) {
    val length = hypot(to.x - from.x, to.y - from.y)
    if (length <= nodeR * 2) return
    val unit = Offset((to.x - from.x) / length, (to.y - from.y) / length)
    val side = Offset(-unit.y, unit.x) * shift
    val start = from + unit * nodeR + side
    val end = to - unit * (nodeR + 2.dp.toPx()) + side
    val color = when (edge.kind) {
        EdgeKind.HEARS -> colors.hears
        EdgeKind.CARRIES -> colors.carrier
        EdgeKind.RECEIPT -> colors.rescuer
    }
    val width = if (edge.kind == EdgeKind.HEARS) 2.dp.toPx() else 3.dp.toPx()
    val dash = if (edge.kind == EdgeKind.CARRIES) {
        PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 6.dp.toPx()))
    } else {
        null
    }
    drawLine(color, start, end, strokeWidth = width, pathEffect = dash)
    val head = ARROW_HEAD.dp.toPx()
    val back = end - unit * head
    val wing = Offset(-unit.y, unit.x) * (head * 0.55f)
    drawPath(
        Path().apply {
            moveTo(end.x, end.y)
            lineTo(back.x + wing.x, back.y + wing.y)
            lineTo(back.x - wing.x, back.y - wing.y)
            close()
        },
        color,
    )
}

private data class GraphColors(
    val me: Color,
    val victim: Color,
    val carrier: Color,
    val rescuer: Color,
    val phone: Color,
    val hears: Color,
    val text: Color,
    val onNode: Color,
) {
    fun of(kind: NodeKind) = when (kind) {
        NodeKind.ME -> me
        NodeKind.RESCUER -> rescuer
        NodeKind.VICTIM -> victim
        NodeKind.CARRIER -> carrier
        NodeKind.PHONE -> phone
    }
}

val CARRIER_ORANGE = Color(0xFFE65100)
val RESCUER_GREEN = Color(0xFF2E7D32)
private const val NODE_RADIUS = 26
private const val LABEL_SPACE = 34
private const val PARALLEL_GAP = 5
private const val ARROW_HEAD = 10
