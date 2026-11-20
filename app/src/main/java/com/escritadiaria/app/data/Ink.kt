package com.escritadiaria.app.data

/** Coordenadas em dp da página; p = pressão (0..1); t = ms desde o início da página. */
data class InkPoint(val x: Float, val y: Float, val p: Float, val t: Long)

class InkStroke(val points: List<InkPoint>) {
    val width: Float by lazy {
        val avg = if (points.isEmpty()) 0.5f else points.map { it.p }.average().toFloat()
        1.4f + avg.coerceIn(0f, 1f) * 2.4f
    }
}

/** Espaço entre as linhas da pauta, em dp. Também usado para separar linhas no reconhecimento. */
const val LINE_SPACING = 48f

object InkCodec {
    fun encode(strokes: List<InkStroke>): String = buildString {
        strokes.forEachIndexed { i, s ->
            if (i > 0) append('\n')
            s.points.forEachIndexed { j, pt ->
                if (j > 0) append(';')
                append(r(pt.x)).append(',').append(r(pt.y)).append(',').append(r(pt.p)).append(',').append(pt.t)
            }
        }
    }

    private fun r(f: Float): String = (Math.round(f * 10f) / 10f).toString()

    fun decode(s: String?): List<InkStroke> {
        if (s.isNullOrBlank()) return emptyList()
        return s.split('\n').mapNotNull { line ->
            val pts = line.split(';').mapNotNull { seg ->
                val f = seg.split(',')
                if (f.size < 4) null
                else runCatching { InkPoint(f[0].toFloat(), f[1].toFloat(), f[2].toFloat(), f[3].toLong()) }.getOrNull()
            }
            if (pts.isEmpty()) null else InkStroke(pts)
        }
    }
}
