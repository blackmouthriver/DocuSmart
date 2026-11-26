package com.docsmart.features.utilities.domain

enum class CaseMode { KEEP, LOWER, UPPER }

data class RenameOptions(
    /** Nombre nuevo que reemplaza todo el nombre original (la extensión se conserva); vacío = se parte del original. */
    val newName: String = "",
    /** Texto a buscar en el nombre (sin la extensión); vacío = no reemplaza. */
    val find: String = "",
    val replace: String = "",
    val prefix: String = "",
    val suffix: String = "",
    /** Agrega un número al final del nombre: "Foto 001". */
    val numbering: Boolean = false,
    val start: Int = 1,
    /** Cifras del número, con ceros a la izquierda (1 a 6). */
    val digits: Int = 3,
    val caseMode: CaseMode = CaseMode.KEEP,
)

/**
 * Renombrar varios archivos a la vez (paridad con `archive/core/renameBatch.ts` de la web): buscar y
 * reemplazar, texto al inicio o al final, numeración y mayúsculas o minúsculas. Lógica pura: calcula
 * los nombres nuevos. La extensión no se toca, los caracteres que los sistemas de archivos no aceptan
 * se reemplazan y los nombres repetidos se distinguen con " (2)", " (3)"...
 */
object RenameBatch {
    const val MAX_DIGITS = 6
    const val MAX_START = 999_999
    private const val MAX_BASE_LENGTH = 120
    private val FORBIDDEN = Regex("[\\\\/:*?\"<>|\\u0000-\\u001f]")
    private val SPACES = Regex("\\s+")
    private val EDGE_DOTS = Regex("^[\\s.]+|[\\s.]+$")

    /** Nombre y extensión (con el punto); un archivo oculto (".gitignore") no tiene extensión. */
    fun split(name: String): Pair<String, String> {
        val dot = name.lastIndexOf('.')
        return if (dot <= 0 || dot == name.lastIndex) name to "" else name.substring(0, dot) to name.substring(dot)
    }

    /** Sin caracteres que los sistemas de archivos no aceptan, sin espacios sobrantes y de largo razonable. */
    fun cleanBaseName(
        input: String,
        fallback: String,
    ): String {
        val clean =
            input
                .replace(FORBIDDEN, "-")
                .replace(SPACES, " ")
                .replace(EDGE_DOTS, "")
                .take(MAX_BASE_LENGTH)
        return clean.ifEmpty { fallback }
    }

    private fun applyCase(
        text: String,
        mode: CaseMode,
    ): String =
        when (mode) {
            CaseMode.LOWER -> text.lowercase()
            CaseMode.UPPER -> text.uppercase()
            CaseMode.KEEP -> text
        }

    /** Nombres nuevos, en el mismo orden que los originales. */
    fun renameAll(
        names: List<String>,
        options: RenameOptions,
        fallback: String = "archivo",
    ): List<String> {
        val digits = options.digits.coerceIn(1, MAX_DIGITS)
        val start = options.start.coerceIn(0, MAX_START)
        val taken = HashSet<String>()
        return names.mapIndexed { index, name ->
            val (base, ext) = split(name)
            val original = if (options.find.isNotEmpty()) base.replace(options.find, options.replace) else base
            var next = options.newName.ifEmpty { original }
            next = applyCase(options.prefix + next + options.suffix, options.caseMode)
            if (options.numbering) next = "$next ${(start + index).toString().padStart(digits, '0')}"
            val clean = cleanBaseName(next, fallback)
            var candidate = clean + ext
            var n = 2
            while (!taken.add(candidate.lowercase())) candidate = "$clean (${n++})$ext"
            candidate
        }
    }

    /** ¿Algún nombre cambia con estas opciones? */
    fun changesAnything(
        names: List<String>,
        options: RenameOptions,
    ): Boolean = renameAll(names, options).zip(names).any { (new, old) -> new != old }
}
