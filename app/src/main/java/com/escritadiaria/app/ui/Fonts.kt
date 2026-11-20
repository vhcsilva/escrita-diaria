package com.escritadiaria.app.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.escritadiaria.app.R

/** Fonte cursiva para mostrar o texto convertido da escrita à mão. [scale] compensa o tamanho visual de cada uma. */
data class ScriptFont(val name: String, val description: String, val resId: Int, val scale: Float) {
    val family: FontFamily = FontFamily(Font(resId))
}

object ScriptFonts {
    val all = listOf(
        ScriptFont("Dancing Script", "Cursiva clara, boa para ler", R.font.dancing_script, 1.1f),
        ScriptFont("Caveat", "Letra de mão, informal", R.font.caveat, 1.2f),
        ScriptFont("Great Vibes", "Caligrafia elegante", R.font.great_vibes, 1.3f),
    )

    fun get(i: Int): ScriptFont = all[i.coerceIn(0, all.lastIndex)]
}

val LocalScriptFont = compositionLocalOf { ScriptFonts.all[0] }

/** Texto convertido, mostrado na fonte cursiva escolhida em Ajustes. */
@Composable
fun ScriptText(
    text: String,
    modifier: Modifier = Modifier,
    size: TextUnit = 18.sp,
    color: Color = C.Ink,
    maxLines: Int = Int.MAX_VALUE,
    font: ScriptFont = LocalScriptFont.current,
) {
    Text(
        text,
        modifier = modifier,
        style = scriptStyle(size, color, font),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun ScriptText(text: AnnotatedString, modifier: Modifier = Modifier, size: TextUnit = 18.sp, maxLines: Int = Int.MAX_VALUE) {
    Text(text, modifier = modifier, style = scriptStyle(size, C.Ink, LocalScriptFont.current), maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}

fun scriptStyle(size: TextUnit, color: Color, font: ScriptFont): TextStyle =
    TextStyle(fontFamily = font.family, fontSize = size * font.scale, lineHeight = size * font.scale * 1.35f, color = color)
