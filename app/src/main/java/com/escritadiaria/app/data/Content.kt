package com.escritadiaria.app.data

import java.text.Normalizer
import java.time.LocalDate

/** Tema de um dia da semana, com as 5 perguntas do rodízio (uma por semana do ciclo). */
data class DayTheme(
    val short: String,
    val full: String,
    val theme: String,
    val sub: String,
    val defaults: List<String>,
)

/**
 * Perguntas padrão, escritas com palavras próprias a partir do método do artigo.
 * Todas podem ser substituídas pelo usuário na tela "Editar perguntas".
 */
object Content {
    val days = listOf(
        DayTheme(
            "Seg", "Segunda-feira", "Eu", "Mundo interior: o que você sente, teme e deseja.",
            listOf(
                "O que realmente importa para você hoje, sem filtro?",
                "Qual medo tem tomado decisões no seu lugar?",
                "Em que momentos você se sente mais vivo?",
                "Que insatisfação morna você vem tolerando?",
                "O que você está protegendo quando se protege?",
            ),
        ),
        DayTheme(
            "Ter", "Terça-feira", "Isto", "Ações e habilidades: o que você faz e sabe fazer.",
            listOf(
                "As ações de ontem levam para onde você quer ir?",
                "Que força sua passa despercebida até por você?",
                "O que você amava fazer quando criança?",
                "Qual hábito está te mantendo parado?",
                "Onde você gastou tempo sem nenhum impacto real?",
            ),
        ),
        DayTheme(
            "Qua", "Quarta-feira", "Nós", "Social e cultural: as lentes que você herdou.",
            listOf(
                "Como sua família define sucesso? Você concorda?",
                "Perto de quem você se sente maior ou menor?",
                "Que identidade você precisaria abandonar?",
                "O que você faz só para ser visto de certo jeito?",
                "Quem vive hoje um futuro que você não quer?",
            ),
        ),
        DayTheme(
            "Qui", "Quinta-feira", "Estes", "Contexto e oportunidades: o que está ao seu redor.",
            listOf(
                "Que oportunidade você deixou passar recentemente?",
                "Quais barreiras são reais e quais são desculpas?",
                "Que vantagem você tem e não usa?",
                "Quem controla como você paga as contas, e o que isso limita?",
                "Quantas horas de tela ontem, e o que elas anestesiaram?",
            ),
        ),
        DayTheme(
            "Sex", "Sexta-feira", "Antivisão", "A vida que você terá se nada mudar.",
            listOf(
                "Como é uma terça comum daqui a 5 anos se nada mudar?",
                "Qual é o motivo mais constrangedor para você não mudar?",
                "Que verdade desconfortável você evita encarar?",
                "Do que você reclama sem mudar nada?",
                "Quanto custa, de verdade, uma vida segura?",
            ),
        ),
        DayTheme(
            "Sáb", "Sábado", "Visão", "A vida que você quer construir.",
            listOf(
                "Como é uma terça comum daqui a 3 anos na vida que você quer?",
                "Que crenças fariam essa vida parecer natural?",
                "O que você faria esta semana se já fosse essa pessoa?",
                "O que precisa acontecer nos próximos 30 dias?",
                "O que você não aceita sacrificar?",
            ),
        ),
        DayTheme(
            "Dom", "Domingo", "Revisão", "Olhar a semana com honestidade.",
            listOf(
                "O que mudou nesta semana?",
                "Quais foram os momentos mais e menos vivos?",
                "Se alguém filmasse sua semana, o que concluiria que você quer?",
                "Que padrões se repetiram?",
                "Sua visão ou antivisão precisa ser reescrita?",
            ),
        ),
    )

    val themeNames = days.map { it.theme }

    /** Perguntas para responder mentalmente ao longo do dia. */
    val dayPromptDefaults = listOf(
        "O que estou evitando enquanto faço o que estou fazendo?",
        "Se alguém filmasse minhas últimas duas horas, o que concluiria?",
        "Estou indo para a vida que odeio ou para a que quero?",
        "O que importa e eu estou fingindo que não?",
        "O que fiz hoje para proteger minha identidade, e não por desejo real?",
        "Quando me senti mais vivo hoje? E mais morto?",
    )
    val defaultFixedTimes = listOf(11 * 60, 13 * 60 + 30, 15 * 60 + 15, 17 * 60, 19 * 60 + 30, 21 * 60)

    val b1Full = listOf(
        "Como acordei? Corpo, humor, primeiro pensamento.",
        "O que eu disse ontem que faria? Fiz?",
        "O que estou evitando agora?",
    )
    val b1Quick = listOf(b1Full[0], b1Full[2])
    val b3Full = listOf(
        "Uma ação pequena, com horário, que a pessoa que estou me tornando faria hoje.",
        "Uma coisa que não vou fazer hoje.",
        "Uma frase para carregar durante o dia.",
    )
    val b3Quick = listOf(b3Full[0])

    /** Duração de cada bloco, em segundos. */
    fun duration(block: String, quick: Boolean): Int = when {
        quick && block == "b1" -> 120
        quick && block == "b3" -> 180
        block == "b1" -> 180
        block == "b2" -> 480
        else -> 240
    }

    fun dayIndex(date: LocalDate): Int = date.dayOfWeek.value - 1

    val monthsShort = listOf("jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez")
    val monthsFull = listOf(
        "janeiro", "fevereiro", "março", "abril", "maio", "junho",
        "julho", "agosto", "setembro", "outubro", "novembro", "dezembro",
    )

    fun longDate(d: LocalDate): String =
        "${days[dayIndex(d)].full}, ${d.dayOfMonth} de ${monthsFull[d.monthValue - 1]}"
}

/** Minúsculas e sem acentos, para busca ("Não" encontra "nao"). Mantém o tamanho do texto NFC. */
fun normalizeForSearch(s: String): String =
    Normalizer.normalize(Normalizer.normalize(s, Normalizer.Form.NFC), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .lowercase()

fun formatMinutes(m: Int): String = "%02d:%02d".format(m / 60, m % 60)

fun mondayOf(d: LocalDate): LocalDate = d.minusDays((d.dayOfWeek.value - 1).toLong())
