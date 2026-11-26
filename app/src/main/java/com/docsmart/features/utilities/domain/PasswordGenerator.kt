package com.docsmart.features.utilities.domain

import java.security.SecureRandom
import kotlin.math.log2
import kotlin.math.roundToInt

/** `exclude`: caracteres que un sitio no acepta (igual que la web). */
data class PasswordOptions(
    val length: Int = PasswordGenerator.DEFAULT_LENGTH,
    val lower: Boolean = true,
    val upper: Boolean = true,
    val digits: Boolean = true,
    val symbols: Boolean = true,
    val avoidAmbiguous: Boolean = false,
    val exclude: String = "",
)

enum class PasswordStrength { WEAK, FAIR, STRONG, VERY_STRONG }

/**
 * Generador de contraseñas (paridad con `text/core/password.ts` de la web): `SecureRandom` sin sesgo
 * y al menos un carácter de cada tipo elegido. Nada se guarda ni se envía.
 */
object PasswordGenerator {
    const val MIN_LENGTH = 6
    const val MAX_LENGTH = 128
    const val DEFAULT_LENGTH = 16

    private const val WEAK_BELOW = 45
    private const val FAIR_BELOW = 65
    private const val STRONG_BELOW = 90
    private const val LOWER = "abcdefghijklmnopqrstuvwxyz"
    private const val UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private const val DIGITS = "0123456789"
    private const val SYMBOLS = "!@#$%&*-_=+?.:;,~^()[]{}"

    // Se confunden al leerlos o copiarlos a mano.
    private const val AMBIGUOUS = "Il1O0o"
    private val secureRandom = SecureRandom()

    fun clampLength(length: Int): Int = length.coerceIn(MIN_LENGTH, MAX_LENGTH)

    /** Los grupos de caracteres elegidos (vacío si no se eligió ninguno). */
    fun pools(options: PasswordOptions): List<String> {
        val skip = options.exclude.toSet()
        return listOf(
            options.lower to LOWER,
            options.upper to UPPER,
            options.digits to DIGITS,
            options.symbols to SYMBOLS,
        ).filter { it.first }
            .map { (_, set) ->
                set.filter { c -> c !in skip && !(options.avoidAmbiguous && c in AMBIGUOUS) }
            }.filter { it.isNotEmpty() }
    }

    /** `nextInt(bound)` devuelve un entero uniforme en `[0, bound)`; se inyecta para poder probarlo. */
    fun generate(
        options: PasswordOptions,
        nextInt: (Int) -> Int = secureRandom::nextInt,
    ): String {
        val groups = pools(options)
        if (groups.isEmpty()) return ""
        val length = clampLength(options.length)
        val all = groups.joinToString("")
        val chars = groups.mapTo(ArrayList(length)) { it[nextInt(it.length)] }
        while (chars.size < length) chars.add(all[nextInt(all.length)])
        // Mezcla (Fisher-Yates) para que los obligatorios no queden siempre al principio.
        for (i in chars.lastIndex downTo 1) {
            val j = nextInt(i + 1)
            val tmp = chars[i]
            chars[i] = chars[j]
            chars[j] = tmp
        }
        return chars.take(length).joinToString("")
    }

    /** Entropía en bits: largo por log2(tamaño del alfabeto). */
    fun entropyBits(options: PasswordOptions): Int {
        val size = pools(options).sumOf { it.length }
        return if (size == 0) 0 else (clampLength(options.length) * log2(size.toDouble())).roundToInt()
    }

    fun strength(bits: Int): PasswordStrength =
        when {
            bits < WEAK_BELOW -> PasswordStrength.WEAK
            bits < FAIR_BELOW -> PasswordStrength.FAIR
            bits < STRONG_BELOW -> PasswordStrength.STRONG
            else -> PasswordStrength.VERY_STRONG
        }
}
