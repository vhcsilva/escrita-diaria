package com.escritadiaria.app.data

/**
 * Separa um texto colado (por exemplo, um trecho copiado do artigo) em itens:
 * uma pergunta por linha, sem marcadores, numeração ou horários no começo.
 * Quando há perguntas (linhas com "?"), usa só elas; senão, usa as linhas com texto.
 */
object PasteParser {
    private val leading = Regex("""^\s*(?:[-–—•*·▪●◦>]+|\(?\d{1,2}[.)]|\d{1,2}[:h]\d{0,2}\s*[-–—:]?|[a-eA-E][.)])\s*""")

    fun clean(line: String): String {
        var s = line.trim()
        repeat(3) { s = s.replace(leading, "").trim() }
        return s.trim('"', '“', '”', ' ')
    }

    fun parse(text: String, count: Int): List<String> {
        // Uma linha pode trazer várias perguntas seguidas; quebra depois de cada "?".
        val lines = text.lines()
            .flatMap { l -> l.split(Regex("""(?<=\?)\s+(?=\S)""")) }
            .map { clean(it) }
            .filter { l -> l.any { it.isLetter() } }
        val questions = lines.filter { it.contains('?') }
        val pick = if (questions.size >= count) questions else lines
        return pick.take(count)
    }
}
