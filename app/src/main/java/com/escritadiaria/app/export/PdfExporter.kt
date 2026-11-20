package com.escritadiaria.app.export

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.escritadiaria.app.data.Content
import com.escritadiaria.app.data.InkCodec
import com.escritadiaria.app.data.InkPoint
import com.escritadiaria.app.data.LINE_SPACING
import com.escritadiaria.app.data.Ritual
import java.time.LocalDate
import kotlin.math.min

/** Exporta rituais para PDF A4: uma página por bloco, com a escrita original e o texto reconhecido. */
object PdfExporter {
    private const val W = 595
    private const val H = 842
    private const val M = 40f

    fun androidPath(pts: List<InkPoint>): Path {
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
            p.quadTo(a.x, a.y, (a.x + b.x) / 2f, (a.y + b.y) / 2f)
        }
        p.lineTo(pts.last().x, pts.last().y)
        return p
    }

    private fun label(block: String, r: Ritual): String = when (block) {
        "b1" -> "Bloco 1 · Onde estou"
        "b2" -> "Bloco 2 · ${r.question}"
        else -> "Bloco 3 · Direção do dia"
    }

    fun write(context: Context, uri: Uri, rituals: List<Ritual>): Boolean {
        val doc = PdfDocument()
        var n = 1
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 18f; typeface = Typeface.DEFAULT_BOLD; color = Color.rgb(22, 27, 38) }
        val sub = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f; color = Color.rgb(90, 97, 112) }
        val rule = Paint().apply { color = Color.rgb(230, 233, 242); strokeWidth = 0.5f }
        val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; color = Color.rgb(26, 36, 64)
            strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }
        val body = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f; color = Color.rgb(22, 27, 38) }

        for (r in rituals) {
            val date = LocalDate.ofEpochDay(r.epochDay)
            for (b in r.blocks()) {
                val strokes = InkCodec.decode(r.ink(b))
                val page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, n++).create())
                val c = page.canvas
                c.drawText("${Content.longDate(date)} de ${date.year}", M, M + 14f, title)
                c.drawText("${r.theme} · ${if (r.quick) "versão rápida" else "ritual completo"}", M, M + 32f, sub)
                val header = label(b, r)
                val headerLayout = StaticLayout.Builder.obtain(header, 0, header.length, TextPaint(sub), (W - 2 * M).toInt())
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL).build()
                c.save(); c.translate(M, M + 42f); headerLayout.draw(c); c.restore()

                val top = M + 50f + headerLayout.height
                val pw = if (r.pageW > 0) r.pageW else 850f
                val ph = if (r.pageH > 0) r.pageH else 600f
                val maxH = H * 0.55f
                val scale = min((W - 2 * M) / pw, maxH / ph)
                var y = LINE_SPACING * scale
                while (y < ph * scale) {
                    c.drawLine(M, top + y, M + pw * scale, top + y, rule)
                    y += LINE_SPACING * scale
                }
                c.save()
                c.translate(M, top)
                c.scale(scale, scale)
                strokes.forEach { s ->
                    ink.strokeWidth = s.width
                    c.drawPath(androidPath(s.points), ink)
                }
                c.restore()

                val text = r.text(b)
                if (text.isNotBlank()) {
                    val textTop = top + ph * scale + 20f
                    c.drawText("Texto reconhecido", M, textTop, sub)
                    val layout = StaticLayout.Builder.obtain(text, 0, text.length, body, (W - 2 * M).toInt())
                        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                        .setMaxLines(((H - textTop - M) / 14f).toInt().coerceAtLeast(1))
                        .build()
                    c.save(); c.translate(M, textTop + 8f); layout.draw(c); c.restore()
                }
                doc.finishPage(page)
            }
        }
        if (n == 1) {
            doc.close()
            return false
        }
        return try {
            context.contentResolver.openOutputStream(uri)?.use { doc.writeTo(it) }
            true
        } catch (e: Exception) {
            false
        } finally {
            doc.close()
        }
    }
}
