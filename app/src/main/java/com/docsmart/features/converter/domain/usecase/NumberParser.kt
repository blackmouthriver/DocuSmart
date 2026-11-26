package com.docsmart.features.converter.domain.usecase

/**
 * Convierte "1.500.000", "1,234.56", "$ 2.000", "45%" o "-3,5" en numero (mismas reglas que la web).
 * Con punto y coma a la vez, el ultimo es el decimal; con uno solo, grupos de 3 cifras indican miles
 * ("1.500" = 1500).
 */
internal object NumberParser {
    private const val MAX_LENGTH = 30
    private const val THOUSANDS_GROUP = 3
    private val CURRENCY_PREFIX = Regex("""^(\$|COP|USD|\u20AC)\s?""", RegexOption.IGNORE_CASE)
    private val NUMBER_SHAPE = Regex("""^\d[\d.,\s]*$""")
    private val PLAIN_NUMBER = Regex("""^\d+(\.\d+)?$""")

    private class Parts(
        val digits: String,
        val negative: Boolean,
        val currency: Boolean,
        val percent: Boolean,
    )

    fun parse(raw: String): Pair<Double, NumFormat>? =
        split(raw)?.let { parts ->
            normalize(parts.digits)?.let { toResult(parts, it) }
        }

    private fun split(raw: String): Parts? {
        val trimmed = raw.trim().replace(' ', ' ')
        var s = trimmed
        var negative = false
        if (s.length >= 2 && s.startsWith("(") && s.endsWith(")")) {
            negative = true
            s = s.substring(1, s.length - 1).trim()
        }
        if (s.startsWith("-") || s.startsWith("−")) {
            negative = !negative
            s = s.substring(1).trim()
        }
        val currency = CURRENCY_PREFIX.containsMatchIn(s)
        s = s.replace(CURRENCY_PREFIX, "").trim()
        val percent = s.endsWith("%")
        if (percent) s = s.dropLast(1).trim()
        val sizeOk = trimmed.isNotEmpty() && trimmed.length <= MAX_LENGTH
        val valid = sizeOk && NUMBER_SHAPE.matches(s) && s.last() !in ".,"
        return if (valid) Parts(s.replace(Regex("""\s"""), ""), negative, currency, percent) else null
    }

    private fun toResult(
        parts: Parts,
        normalized: String,
    ): Pair<Double, NumFormat> {
        val value = normalized.toDouble() * (if (parts.negative) -1 else 1)
        val decimals = normalized.contains('.')
        return when {
            parts.percent -> value / 100 to NumFormat.PERCENT
            parts.currency && decimals -> value to NumFormat.CURRENCY2
            parts.currency -> value to NumFormat.CURRENCY
            decimals -> value to NumFormat.DECIMAL
            else -> value to NumFormat.INTEGER
        }
    }

    private fun normalize(s: String): String? {
        val lastDot = s.lastIndexOf('.')
        val lastComma = s.lastIndexOf(',')
        val normalized =
            when {
                lastDot >= 0 && lastComma >= 0 -> {
                    val decimal = if (lastDot > lastComma) '.' else ','
                    val group = if (decimal == '.') ',' else '.'
                    s.replace(group.toString(), "").replace(decimal, '.')
                }
                lastDot >= 0 || lastComma >= 0 -> normalizeSingleSeparator(s, if (lastDot >= 0) '.' else ',')
                else -> s
            }
        return if (normalized.isNotEmpty() && PLAIN_NUMBER.matches(normalized)) normalized else null
    }

    private fun normalizeSingleSeparator(
        s: String,
        separator: Char,
    ): String {
        val groups = s.split(separator)
        val groupsOfThree = groups.drop(1).all { it.length == THOUSANDS_GROUP }
        val thousands = groups.size > 1 && groupsOfThree && groups[0].length <= THOUSANDS_GROUP
        return when {
            thousands -> groups.joinToString("")
            groups.size == 2 -> groups.joinToString(".")
            else -> ""
        }
    }
}
