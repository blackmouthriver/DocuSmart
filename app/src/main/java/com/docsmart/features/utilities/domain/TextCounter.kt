package com.docsmart.features.utilities.domain

import java.text.BreakIterator
import java.util.Locale

data class TextStats(
    val characters: Int,
    val charactersNoSpaces: Int,
    val words: Int,
    val sentences: Int,
    val paragraphs: Int,
    val lines: Int,
    /** Minutos de lectura (200 palabras por minuto) y en voz alta (130). */
    val readingMinutes: Double,
    val speakingMinutes: Double,
)

data class WordCount(
    val word: String,
    val count: Int,
    val percent: Double,
)

/** Enlaces y correos: cada uno cuenta como una sola palabra (como en Word y Google Docs). */
object LinkDetector {
    private const val LEADING = "([\"'«“¿¡<"
    private const val TRAILING = ".,;:!?)]}\"'’»”>"
    private val EMAIL_PART = Regex("^[\\p{L}\\p{N}._%+-]+$")
    private val DOMAIN_PART = Regex("^[\\p{L}\\p{N}-]+$")
    private val TLD = Regex("^\\p{L}{2,}$")
    private const val WWW_LENGTH = 4

    /** ¿El trozo (ya sin puntuación alrededor) es un enlace o un correo? */
    fun isLink(token: String): Boolean {
        val lower = token.lowercase()
        val at = token.indexOf('@')
        return when {
            lower.startsWith("http://") || lower.startsWith("https://") -> lower.length > lower.indexOf("//") + 2
            lower.startsWith("www.") -> lower.length > WWW_LENGTH
            at <= 0 || at != token.lastIndexOf('@') -> false
            else -> isEmail(token, at)
        }
    }

    private fun isEmail(
        token: String,
        at: Int,
    ): Boolean {
        val domain = token.substring(at + 1).split('.')
        return EMAIL_PART.matches(token.substring(0, at)) &&
            domain.size >= 2 &&
            domain.all { DOMAIN_PART.matches(it) } &&
            TLD.matches(domain.last())
    }

    /** Posición de cada enlace o correo del texto, sin la puntuación que lo rodea. */
    fun spans(text: String): List<IntRange> {
        val spans = ArrayList<IntRange>()
        var i = 0
        while (i < text.length) {
            if (text[i].isWhitespace()) {
                i++
                continue
            }
            var end = i
            while (end < text.length && !text[end].isWhitespace()) end++
            var from = i
            var to = end
            while (from < to && text[from] in LEADING) from++
            while (to > from && text[to - 1] in TRAILING) to--
            if (to > from && isLink(text.substring(from, to))) spans.add(from until to)
            i = end
        }
        return spans
    }

    /** Reemplaza cada enlace o correo (la puntuación de alrededor se queda: un punto sigue cerrando la oración). */
    fun replace(
        text: String,
        by: String,
    ): String {
        val out = StringBuilder()
        var last = 0
        for (span in spans(text)) {
            out.append(text, last, span.first).append(by)
            last = span.last + 1
        }
        return out.append(text, last, text.length).toString()
    }
}

/**
 * Contador de palabras y caracteres (paridad con `text/core/stats.ts` de la web, sin el nivel de
 * lectura): cuenta como Word y Google Docs (un enlace, un correo y una palabra con guion son una
 * palabra; "Sr." no corta la oración) y separa bien las palabras también en japonés o chino.
 */
object TextCounter {
    const val READING_WPM = 200
    const val SPEAKING_WPM = 130
    private const val DEFAULT_TOP = 10
    private const val DEFAULT_MIN_LENGTH = 3
    private const val CJK_MIN_LENGTH = 2
    private val CJK_LANGUAGES = setOf("ja", "zh", "ko")
    private val HYPHENS = setOf('-', '‐', '‑')
    private val ABBREVIATION =
        Regex(
            "(?:^|\\s)(?:sr|sra|srta|dr|dra|ud|uds|etc|pág|págs|pp|vs|ej|núm|av|mr|mrs|ms|prof|vol|cap|art|ing|lic)" +
                "\\.$",
            RegexOption.IGNORE_CASE,
        )
    private val INITIAL = Regex("(?:^|\\s)\\p{Lu}\\.$")
    private val PARAGRAPH_BREAK = Regex("\\n\\s*\\n")
    private val LINE_BREAK = Regex("\\r\\n|\\r|\\n")

    fun minWordLength(language: String): Int = if (language in CJK_LANGUAGES) CJK_MIN_LENGTH else DEFAULT_MIN_LENGTH

    private fun segments(
        text: String,
        iterator: BreakIterator,
    ): List<String> {
        iterator.setText(text)
        val out = ArrayList<String>()
        var start = iterator.first()
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            out.add(text.substring(start, end))
            start = end
            end = iterator.next()
        }
        return out
    }

    /** Palabras de un trozo sin enlaces; "bien-estar" (palabra, guion, palabra pegados) es una sola. */
    private fun plainWords(
        text: String,
        locale: Locale,
    ): List<String> {
        val out = ArrayList<String>()
        var joinNext = false
        var prevWord = false
        for (segment in segments(text, BreakIterator.getWordInstance(locale))) {
            if (segment.any { it.isLetterOrDigit() }) {
                if (joinNext && out.isNotEmpty()) out[out.lastIndex] = out.last() + "-" + segment else out.add(segment)
                joinNext = false
                prevWord = true
            } else {
                joinNext = prevWord && segment.length == 1 && segment[0] in HYPHENS
                prevWord = false
            }
        }
        return out
    }

    /** Las palabras del texto, en orden (sin signos ni espacios). */
    fun wordsOf(
        text: String,
        locale: Locale = Locale.getDefault(),
    ): List<String> {
        val out = ArrayList<String>()
        var last = 0
        for (span in LinkDetector.spans(text)) {
            out.addAll(plainWords(text.substring(last, span.first), locale))
            out.add(text.substring(span.first, span.last + 1))
            last = span.last + 1
        }
        out.addAll(plainWords(text.substring(last), locale))
        return out
    }

    private fun sentenceCount(
        text: String,
        locale: Locale,
    ): Int {
        // Los puntos de los enlaces y correos no cortan la oración.
        val plain = LinkDetector.replace(text, "x")
        val joined = ArrayList<String>()
        for (part in segments(plain, BreakIterator.getSentenceInstance(locale))) {
            val prev = joined.lastOrNull()?.trimEnd()
            // Una abreviatura ("Sr.", "Dra.") o una inicial ("J. García") no termina la oración.
            if (prev != null && (ABBREVIATION.containsMatchIn(prev) || INITIAL.containsMatchIn(prev))) {
                joined[joined.lastIndex] = joined.last() + part
            } else {
                joined.add(part)
            }
        }
        return joined.count { p -> p.any { it.isLetterOrDigit() } }
    }

    fun stats(
        text: String,
        locale: Locale = Locale.getDefault(),
    ): TextStats {
        val graphemes = segments(text, BreakIterator.getCharacterInstance(locale))
        val words = wordsOf(text, locale).size
        return TextStats(
            characters = graphemes.size,
            charactersNoSpaces = graphemes.count { g -> g.any { !it.isWhitespace() } },
            words = words,
            sentences = sentenceCount(text, locale),
            paragraphs = text.split(PARAGRAPH_BREAK).count { it.isNotBlank() },
            lines = if (text.isEmpty()) 0 else text.split(LINE_BREAK).size,
            readingMinutes = words.toDouble() / READING_WPM,
            speakingMinutes = words.toDouble() / SPEAKING_WPM,
        )
    }

    /** Las palabras que más se repiten (densidad de palabras clave); se omiten las muy cortas y los números. */
    fun topWords(
        text: String,
        locale: Locale = Locale.getDefault(),
        limit: Int = DEFAULT_TOP,
        minLength: Int = DEFAULT_MIN_LENGTH,
    ): List<WordCount> {
        val words = wordsOf(text, locale)
        if (words.isEmpty()) return emptyList()
        val counts = LinkedHashMap<String, Int>()
        for (word in words) {
            val key = word.lowercase(locale)
            val tooShort = key.codePointCount(0, key.length) < minLength
            val skip = tooShort || key.all { it.isDigit() } || LinkDetector.isLink(word)
            if (!skip) counts[key] = (counts[key] ?: 0) + 1
        }
        return counts.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(limit)
            .map { (word, count) ->
                WordCount(word, count, Math.round(count.toDouble() / words.size * PERCENT_SCALE) / 10.0)
            }
    }

    private const val PERCENT_SCALE = 1000.0
}
