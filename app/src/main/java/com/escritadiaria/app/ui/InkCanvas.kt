package com.escritadiaria.app.ui

import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.escritadiaria.app.data.InkPoint
import com.escritadiaria.app.data.InkStroke
import com.escritadiaria.app.data.LINE_SPACING
import java.util.IdentityHashMap
import kotlin.math.min

fun composePath(pts: List<InkPoint>): Path {
    val p = Path()
    if (pts.isEmpty()) return p
    p.moveTo(pts[0].x, pts[0].y)
    if (pts.size == 1) {
        p.lineTo(pts[0].x + 0.1f, pts[0].y)
        return p
    }
    for (i in 1 until pts.size) {
        val a = pts[i - 1]
        val b = pts[i]
        p.quadraticBezierTo(a.x, a.y, (a.x + b.x) / 2f, (a.y + b.y) / 2f)
    }
    p.lineTo(pts.last().x, pts.last().y)
    return p
}

private fun DrawScope.drawRules(scale: Float, width: Float, height: Float) {
    val step = LINE_SPACING * scale
    var y = step
    while (y < height) {
        drawLine(C.Rule, Offset(0f, y), Offset(width, y), strokeWidth = 1f)
        y += step
    }
}

// Ações próprias da S Pen em aparelhos Samsung (botão lateral pressionado).
private const val SPEN_DOWN = 211
private const val SPEN_UP = 212
private const val SPEN_MOVE = 213
private const val ERASE_RADIUS = 14f

/**
 * Página pautada para escrever com a S Pen.
 * - Pressão da caneta muda a espessura do traço.
 * - Botão lateral da S Pen (ou modo borracha) apaga o traço inteiro tocado.
 * - Depois que a caneta é detectada, toques com o dedo/palma são ignorados.
 * Coordenadas são guardadas em dp, independentes da densidade da tela.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun InkCanvas(
    strokes: SnapshotStateList<InkStroke>,
    eraser: Boolean,
    modifier: Modifier = Modifier,
    converted: Map<Int, String> = emptyMap(),
    showCursive: Boolean = false,
    onStrokeAdded: (InkStroke) -> Unit = {},
    onStrokesRemoved: (Set<Int>) -> Unit = {},
    onPageSize: (Float, Float) -> Unit = { _, _ -> },
) {
    val density = LocalDensity.current.density
    val font = LocalScriptFont.current
    val measurer = rememberTextMeasurer(cacheSize = 64)
    val onAdded by rememberUpdatedState(onStrokeAdded)
    val onRemoved by rememberUpdatedState(onStrokesRemoved)
    val live = remember { mutableStateListOf<InkPoint>() }
    var liveErasing by remember { mutableStateOf(false) }
    var penSeen by remember { mutableStateOf(false) }
    var t0 by remember { mutableLongStateOf(-1L) }
    val cache = remember { IdentityHashMap<InkStroke, Path>() }
    val eraserMode by rememberUpdatedState(eraser)

    fun point(x: Float, y: Float, p: Float, time: Long): InkPoint {
        if (t0 < 0) t0 = time
        return InkPoint(x / density, y / density, p.coerceIn(0f, 1f), time - t0)
    }

    fun eraseAt(pt: InkPoint) {
        val r2 = ERASE_RADIUS * ERASE_RADIUS
        val hit = strokes.filter { s ->
            s.points.any { q -> val dx = q.x - pt.x; val dy = q.y - pt.y; dx * dx + dy * dy <= r2 }
        }
        if (hit.isNotEmpty()) {
            strokes.removeAll(hit.toSet())
            onRemoved(hit.map { it.line }.toSet())
        }
    }

    fun commit() {
        if (!liveErasing && live.isNotEmpty()) {
            val s = InkStroke(live.toList())
            strokes.add(s)
            onAdded(s)
        }
        live.clear()
    }

    Canvas(
        modifier
            .semantics { contentDescription = "Página para escrita à mão" }
            .onSizeChanged { onPageSize(it.width / density, it.height / density) }
            .pointerInteropFilter { ev ->
                val tool = ev.getToolType(0)
                val isPen = tool == MotionEvent.TOOL_TYPE_STYLUS || tool == MotionEvent.TOOL_TYPE_ERASER
                if (isPen) penSeen = true
                if (tool == MotionEvent.TOOL_TYPE_FINGER && penSeen) return@pointerInteropFilter false
                val buttonErase = (ev.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY) != 0 ||
                    tool == MotionEvent.TOOL_TYPE_ERASER
                when (ev.actionMasked) {
                    MotionEvent.ACTION_DOWN, SPEN_DOWN -> {
                        live.clear()
                        liveErasing = eraserMode || buttonErase || ev.actionMasked == SPEN_DOWN
                        val p = point(ev.x, ev.y, ev.pressure, ev.eventTime)
                        if (liveErasing) eraseAt(p) else live.add(p)
                    }
                    MotionEvent.ACTION_MOVE, SPEN_MOVE -> {
                        for (h in 0 until ev.historySize) {
                            val p = point(ev.getHistoricalX(h), ev.getHistoricalY(h), ev.getHistoricalPressure(h), ev.getHistoricalEventTime(h))
                            if (liveErasing) eraseAt(p) else live.add(p)
                        }
                        val p = point(ev.x, ev.y, ev.pressure, ev.eventTime)
                        if (liveErasing) eraseAt(p) else live.add(p)
                    }
                    MotionEvent.ACTION_UP, SPEN_UP -> commit()
                    MotionEvent.ACTION_CANCEL -> live.clear()
                    else -> return@pointerInteropFilter false
                }
                true
            },
    ) {
        drawRules(density, size.width, size.height)
        val hidden = if (showCursive) converted.keys else emptySet()
        withTransform({ scale(density, density, Offset.Zero) }) {
            for (s in strokes) {
                if (s.line in hidden) continue
                val path = cache.getOrPut(s) { composePath(s.points) }
                drawPath(path, C.Stroke, style = Stroke(width = s.width, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            if (live.isNotEmpty() && !liveErasing) {
                val tmp = InkStroke(live.toList())
                drawPath(composePath(tmp.points), C.Stroke, style = Stroke(width = tmp.width, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
        if (showCursive) {
            // Linhas já convertidas: texto cursivo apoiado na linha da pauta, começando onde a escrita começou.
            val baseSize = (LINE_SPACING * 0.5f * font.scale).sp
            for ((line, text) in converted) {
                if (text.isBlank()) continue
                val startX = (strokes.filter { it.line == line }.minOfOrNull { it.minX } ?: 8f) * density
                val avail = (size.width - startX - 8f * density).coerceAtLeast(40f * density)
                var style = TextStyle(fontFamily = font.family, fontSize = baseSize, color = C.Stroke)
                var layout = measurer.measure(text, style, softWrap = false, maxLines = 1)
                if (layout.size.width > avail) {
                    style = style.copy(fontSize = baseSize * (avail / layout.size.width).coerceAtLeast(0.55f))
                    layout = measurer.measure(text, style, softWrap = false, maxLines = 1)
                }
                val ruleY = (line + 1) * LINE_SPACING * density
                drawText(layout, topLeft = Offset(startX, ruleY - 7f * density - layout.firstBaseline))
            }
        }
        if (cache.size > strokes.size + 50) cache.keys.retainAll(strokes.toSet())
    }
}

/** Mostra uma página já escrita, ajustada ao espaço disponível. */
@Composable
fun InkPreview(strokes: List<InkStroke>, pageW: Float, pageH: Float, modifier: Modifier = Modifier, rules: Boolean = true) {
    val paths = remember(strokes) { strokes.map { composePath(it.points) to it.width } }
    Canvas(modifier) {
        val pw = if (pageW > 0) pageW else 850f
        val ph = if (pageH > 0) pageH else 600f
        val sc = min(size.width / pw, size.height / ph)
        if (rules) drawRules(sc, pw * sc, ph * sc)
        withTransform({ scale(sc, sc, Offset.Zero) }) {
            paths.forEach { (p, w) ->
                drawPath(p, C.Stroke, style = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}
