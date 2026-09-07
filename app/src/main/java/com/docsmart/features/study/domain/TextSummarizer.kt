package com.docsmart.features.study.domain

/**
 * Resumen automático 100% local -- pedido explícito del usuario 2026-09-08,
 * con una condición explícita: nada de esto sale del dispositivo (ni llamada
 * a ninguna API de IA en la nube), para no romper la promesa de la política
 * de privacidad ni sumar costo por uso. Es "extractivo", no "generativo": no
 * redacta un texto nuevo, elige las oraciones ya existentes en el documento
 * que parecen más relevantes (variante simple del algoritmo de Luhn --
 * puntúa cada oración por la frecuencia de sus palabras significativas en
 * todo el documento) y las devuelve en el orden original, para que se lean
 * como un resumen coherente y no como una lista desordenada.
 *
 * Limitación conocida, aceptada al elegir el enfoque local en vez de una
 * IA en la nube: la lista de palabras vacías (`STOPWORDS`) solo cubre
 * español e inglés -- en un documento en otro idioma el resumen igual
 * funciona (la puntuación por frecuencia sigue teniendo sentido), pero con
 * menos precisión al no poder descartar sus palabras funcionales. El
 * separador de oraciones (puntuación + mayúscula siguiente) tampoco
 * distingue abreviaturas ("Sr.", "EE.UU.") de fin de oración real --
 * aceptable para un resumen, no para una transcripción exacta.
 */
object TextSummarizer {

    private const val MIN_SENTENCE_LENGTH = 15
    private const val MIN_WORD_LENGTH = 3
    private const val TARGET_FRACTION = 0.12
    private const val MIN_SENTENCES = 5

    private val SENTENCE_BOUNDARY = Regex("(?<=[.!?])\\s+(?=[A-ZÁÉÍÓÚÑÜ¿¡])")
    private val WORD = Regex("\\p{L}+")

    private val STOPWORDS = setOf(
        // Español
        "de", "la", "que", "el", "en", "y", "a", "los", "del", "se", "las", "por", "un", "para",
        "con", "no", "una", "su", "al", "lo", "como", "más", "pero", "sus", "le", "ya", "o",
        "este", "sí", "porque", "esta", "entre", "cuando", "muy", "sin", "sobre", "también",
        "me", "hasta", "hay", "donde", "quien", "desde", "todo", "nos", "durante", "todos",
        "uno", "les", "ni", "contra", "otros", "ese", "eso", "ante", "ellos", "e", "esto", "mí",
        "antes", "algunos", "qué", "unos", "yo", "otro", "otras", "otra", "él", "tanto", "esa",
        "estos", "mucho", "quienes", "nada", "muchos", "cual", "poco", "ella", "estar", "estas",
        "algunas", "algo", "nosotros", "mi", "mis", "tú", "te", "ti", "tu", "tus", "ellas",
        "nosotras", "vosotros", "vosotras", "os", "es", "son", "fue", "era", "ser", "han", "ha",
        "había", "hemos", "habían", "está", "están", "soy", "eres", "somos",
        // Inglés
        "the", "and", "for", "with", "that", "this", "these", "those", "from", "have", "has",
        "had", "will", "would", "can", "could", "should", "does", "did", "his", "her", "their",
        "our", "your", "which", "whom", "what", "when", "where", "why", "how", "all", "each",
        "other", "some", "such", "nor", "only", "own", "same", "than", "too", "very", "just",
        "was", "were", "been", "being", "not", "are"
    )

    /**
     * Devuelve una lista de oraciones (subconjunto del texto original, en su
     * orden original) que forman el resumen. Si el documento ya es corto
     * (menos oraciones que las que pediría el resumen), devuelve todas tal
     * cual -- no tendría sentido "resumir" un párrafo de 3 oraciones.
     */
    fun summarize(paragraphs: List<String>, maxSentences: Int = 15): List<String> {
        val sentences = splitIntoSentences(paragraphs.joinToString(" "))

        // `upperBound` primero, `lowerBound` derivado de él (nunca al revés):
        // si `maxSentences` es menor que `MIN_SENTENCES` (ej. un límite bajo
        // pedido a propósito), `coerceIn` recibiría un rango inválido
        // (mínimo > máximo) y lanzaría `IllegalArgumentException` -- bug real
        // encontrado por el test con `maxSentences = 2`.
        val upperBound = minOf(maxSentences, sentences.size)
        val lowerBound = minOf(MIN_SENTENCES, upperBound)
        val targetCount = (sentences.size * TARGET_FRACTION).toInt().coerceIn(lowerBound, upperBound)

        return if (sentences.isEmpty() || sentences.size <= targetCount) {
            sentences
        } else {
            val tokenized = sentences.map(::significantWords)
            val frequency = mutableMapOf<String, Int>()
            tokenized.forEach { words -> words.forEach { frequency[it] = (frequency[it] ?: 0) + 1 } }

            val scoredIndices = sentences.indices.sortedByDescending { index ->
                val words = tokenized[index]
                if (words.isEmpty()) 0.0
                else words.sumOf { frequency[it]?.toDouble() ?: 0.0 } / words.size
            }

            scoredIndices.take(targetCount).sorted().map { sentences[it] }
        }
    }

    private fun splitIntoSentences(text: String): List<String> =
        text.split(SENTENCE_BOUNDARY)
            .map { it.trim() }
            .filter { it.length >= MIN_SENTENCE_LENGTH }

    private fun significantWords(sentence: String): List<String> =
        WORD.findAll(sentence.lowercase())
            .map { it.value }
            .filter { it.length >= MIN_WORD_LENGTH && it !in STOPWORDS }
            .toList()
}
