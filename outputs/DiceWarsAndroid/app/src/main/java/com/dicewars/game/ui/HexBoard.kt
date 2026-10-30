package com.dicewars.game.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dicewars.game.domain.GameState
import com.dicewars.game.domain.HexCell
import com.dicewars.game.domain.Territory
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

private val factionColors = listOf(
    Color(0xFF46D6A0), Color(0xFFFFC857), Color(0xFFFF7468), Color(0xFFB89AF7),
    Color(0xFF69B7FF), Color(0xFFE986C9), Color(0xFFA8D96B), Color(0xFFFF9E5B)
)

@androidx.compose.runtime.Composable
fun HexBoard(
    state: GameState,
    modifier: Modifier = Modifier,
    height: Dp = 340.dp,
    selectedId: String? = null,
    highlightedIds: Set<String> = emptySet(),
    onTerritoryTap: (String) -> Unit
) {
    val territories = state.territories
    Box(modifier = modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(22.dp)).background(Color(0xFF17231D))) {
        Canvas(
            Modifier.fillMaxWidth().height(height).pointerInput(state.mapId, territories, selectedId, highlightedIds) {
                detectTapGestures { tap ->
                    val geometry = boardGeometry(territories, size.width.toFloat(), size.height.toFloat())
                    if (geometry.radius <= 0f) return@detectTapGestures
                    val worldX = geometry.midX + (tap.x - size.width / 2f) / geometry.radius
                    val worldY = geometry.midY + (tap.y - size.height / 2f) / geometry.radius
                    val rFraction = (2.0 / 3.0 * worldY).toFloat()
                    val qFraction = (worldX / sqrt(3.0).toFloat() - rFraction / 2f)
                    val cell = roundAxial(qFraction, rFraction)
                    territories.firstOrNull { territory -> territory.hexes.any { it.q == cell.q && it.r == cell.r } }
                        ?.let { onTerritoryTap(it.id) }
                }
            }
        ) {
            val geometry = boardGeometry(territories, size.width, size.height)
            if (geometry.radius <= 0f) return@Canvas
            val density = drawContext.density
            territories.forEach { territory ->
                val faction = factionColors[territory.ownerId.mod(factionColors.size)]
                territory.hexes.forEach { cell ->
                    val center = screenPoint(cell.q.toFloat(), cell.r.toFloat(), geometry, size.width, size.height)
                    val path = hexPath(center.first, center.second, geometry.radius)
                    val fill = if (territory.id in highlightedIds) faction.copy(alpha = 0.96f) else faction.copy(alpha = 0.82f)
                    drawPath(path, fill)
                    drawPath(path, Color(0xFF18231D), style = Stroke(width = max(1.2f, 1.8f * density)))
                    if (territory.id == selectedId) {
                        drawPath(path, Color.White, style = Stroke(width = 3.3f * density))
                    } else if (territory.id in highlightedIds) {
                        drawPath(path, Color(0xFFFFF2C5), style = Stroke(width = 2.6f * density))
                    }
                }
                val centerQ = territory.hexes.map { it.q }.average().toFloat()
                val centerR = territory.hexes.map { it.r }.average().toFloat()
                val center = screenPoint(centerQ, centerR, geometry, size.width, size.height)
                val radius = max(11f * density, min(18f * density, geometry.radius * 0.40f))
                drawCircle(Color(0xE6121915), radius, androidx.compose.ui.geometry.Offset(center.first, center.second))
                drawIntoCanvas { canvas ->
                    val native = canvas.nativeCanvas
                    val dicePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.WHITE
                        textAlign = Paint.Align.CENTER
                        textSize = radius * 1.05f
                        typeface = android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD)
                    }
                    native.drawText(territory.dice.toString(), center.first, center.second - (dicePaint.ascent() + dicePaint.descent()) / 2f, dicePaint)
                    val marker = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(21, 29, 24) }
                    val markerRadius = max(6f * density, radius * 0.43f)
                    val mx = center.first + radius * 0.72f
                    val my = center.second - radius * 0.72f
                    native.drawCircle(mx, my, markerRadius, marker)
                    val ownerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.WHITE
                        textAlign = Paint.Align.CENTER
                        textSize = markerRadius * 1.15f
                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                    }
                    native.drawText(if (territory.ownerId == 0) "Я" else territory.ownerId.toString(),
                        mx, my - (ownerPaint.ascent() + ownerPaint.descent()) / 2f, ownerPaint)
                }
            }
        }
    }
}

private data class BoardGeometry(val radius: Float, val midX: Float, val midY: Float)

private fun boardGeometry(territories: List<Territory>, width: Float, height: Float): BoardGeometry {
    val cells = territories.flatMap { it.hexes }
    if (cells.isEmpty() || width <= 0f || height <= 0f) return BoardGeometry(0f, 0f, 0f)
    val xs = cells.map { sqrt(3.0).toFloat() * (it.q + it.r / 2f) }
    val ys = cells.map { 1.5f * it.r }
    val minX = xs.min(); val maxX = xs.max()
    val minY = ys.min(); val maxY = ys.max()
    val radius = min((width - 20f) / (maxX - minX + 2f), (height - 20f) / (maxY - minY + 2f)).coerceAtLeast(0f)
    return BoardGeometry(radius, (minX + maxX) / 2f, (minY + maxY) / 2f)
}

private fun screenPoint(q: Float, r: Float, geometry: BoardGeometry, width: Float, height: Float): Pair<Float, Float> {
    val worldX = sqrt(3.0).toFloat() * (q + r / 2f)
    val worldY = 1.5f * r
    return width / 2f + (worldX - geometry.midX) * geometry.radius to
        height / 2f + (worldY - geometry.midY) * geometry.radius
}

private fun hexPath(cx: Float, cy: Float, radius: Float): Path = Path().apply {
    for (index in 0 until 6) {
        val angle = (PI / 180.0 * (-90 + 60 * index)).toFloat()
        val x = cx + radius * kotlin.math.cos(angle)
        val y = cy + radius * kotlin.math.sin(angle)
        if (index == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

private fun roundAxial(q: Float, r: Float): HexCell {
    val s = -q - r
    var rq = q.roundToInt()
    var rr = r.roundToInt()
    var rs = s.roundToInt()
    val qDiff = abs(rq - q)
    val rDiff = abs(rr - r)
    val sDiff = abs(rs - s)
    if (qDiff > rDiff && qDiff > sDiff) rq = -rr - rs
    else if (rDiff > sDiff) rr = -rq - rs
    return HexCell(rq, rr)
}

