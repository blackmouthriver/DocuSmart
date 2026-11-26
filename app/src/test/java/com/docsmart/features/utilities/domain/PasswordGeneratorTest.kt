package com.docsmart.features.utilities.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Random

class PasswordGeneratorTest {
    private val seeded = Random(42)

    private fun gen(options: PasswordOptions): String = PasswordGenerator.generate(options, seeded::nextInt)

    @Test
    fun `el largo pedido se respeta y se limita entre 6 y 128`() {
        assertEquals(16, gen(PasswordOptions()).length)
        assertEquals(6, gen(PasswordOptions(length = 1)).length)
        assertEquals(128, gen(PasswordOptions(length = 500)).length)
        assertEquals(24, gen(PasswordOptions(length = 24)).length)
    }

    @Test
    fun `lleva al menos un caracter de cada tipo elegido`() {
        repeat(200) {
            val password = gen(PasswordOptions(length = 8))
            assertTrue(password.any { it.isLowerCase() }, password)
            assertTrue(password.any { it.isUpperCase() }, password)
            assertTrue(password.any { it.isDigit() }, password)
            assertTrue(password.any { !it.isLetterOrDigit() }, password)
        }
    }

    @Test
    fun `solo usa los tipos elegidos`() {
        val onlyDigits = gen(PasswordOptions(lower = false, upper = false, symbols = false))
        assertTrue(onlyDigits.all { it.isDigit() })
        val noSymbols = gen(PasswordOptions(symbols = false, length = 64))
        assertTrue(noSymbols.all { it.isLetterOrDigit() })
    }

    @Test
    fun `sin ningun tipo elegido no genera nada`() {
        val none = PasswordOptions(lower = false, upper = false, digits = false, symbols = false)
        assertEquals("", gen(none))
        assertEquals(0, PasswordGenerator.entropyBits(none))
    }

    @Test
    fun `evitar ambiguos quita I l 1 O 0 o`() {
        repeat(100) {
            val password = gen(PasswordOptions(length = 64, avoidAmbiguous = true))
            assertTrue(password.none { it in "Il1O0o" }, password)
        }
    }

    @Test
    fun `los caracteres excluidos no aparecen`() {
        repeat(100) {
            val password = gen(PasswordOptions(length = 64, exclude = "aeiou!@#"))
            assertTrue(password.none { it in "aeiou!@#" }, password)
        }
    }

    @Test
    fun `si se excluye todo un grupo, ese grupo se omite`() {
        val pools = PasswordGenerator.pools(PasswordOptions(exclude = "0123456789"))
        assertEquals(3, pools.size)
        assertFalse(pools.any { p -> p.any { it.isDigit() } })
    }

    @Test
    fun `con SecureRandom real tambien genera y es distinta cada vez`() {
        val a = PasswordGenerator.generate(PasswordOptions(length = 32))
        val b = PasswordGenerator.generate(PasswordOptions(length = 32))
        assertEquals(32, a.length)
        assertTrue(a != b)
    }

    @Test
    fun `la entropia y la fuerza crecen con el largo y el alfabeto`() {
        val short = PasswordGenerator.entropyBits(PasswordOptions(length = 6, symbols = false, upper = false))
        val long = PasswordGenerator.entropyBits(PasswordOptions(length = 24))
        assertTrue(long > short)
        assertEquals(PasswordStrength.WEAK, PasswordGenerator.strength(30))
        assertEquals(PasswordStrength.FAIR, PasswordGenerator.strength(50))
        assertEquals(PasswordStrength.STRONG, PasswordGenerator.strength(70))
        assertEquals(PasswordStrength.VERY_STRONG, PasswordGenerator.strength(120))
        // 16 caracteres de 4 grupos (26+26+10+23 = 85): 16 * log2(85) ~ 103 bits
        assertEquals(103, PasswordGenerator.entropyBits(PasswordOptions()))
    }
}
