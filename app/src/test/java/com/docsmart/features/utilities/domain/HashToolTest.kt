package com.docsmart.features.utilities.domain

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream

class HashToolTest {
    private suspend fun hashOf(
        text: String,
        algorithm: HashAlgorithm,
    ) = HashTool.digest(ByteArrayInputStream(text.toByteArray()), algorithm)

    @Test
    fun `los hashes de abc coinciden con los vectores publicados`() =
        runTest {
            assertEquals(
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                hashOf("abc", HashAlgorithm.SHA256),
            )
            assertEquals("a9993e364706816aba3e25717850c26c9cd0d89d", hashOf("abc", HashAlgorithm.SHA1))
            assertEquals("900150983cd24fb0d6963f7d28e17f72", hashOf("abc", HashAlgorithm.MD5))
            assertEquals(
                "ddaf35a193617abacc417349ae20413112e6fa4e89a97ea20a9eeee64b55d39a" +
                    "2192992a274fc1a836ba3c23a3feebbd454d4423643ce80e2a9ac94fa54ca49f",
                hashOf("abc", HashAlgorithm.SHA512),
            )
        }

    @Test
    fun `un archivo vacio tiene el hash conocido`() =
        runTest {
            assertEquals(
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                hashOf("", HashAlgorithm.SHA256),
            )
            assertEquals("d41d8cd98f00b204e9800998ecf8427e", hashOf("", HashAlgorithm.MD5))
        }

    @Test
    fun `un archivo mas grande que el buffer se lee por partes y reporta el progreso`() =
        runTest {
            val data = ByteArray(200_000) { (it % 251).toByte() }
            val reported = ArrayList<Long>()
            val streamed = HashTool.digest(ByteArrayInputStream(data), HashAlgorithm.SHA256) { reported.add(it) }
            val direct = HashTool.toHex(java.security.MessageDigest.getInstance("SHA-256").digest(data))
            assertEquals(direct, streamed)
            assertTrue(reported.size > 1)
            assertEquals(200_000L, reported.last())
        }

    @Test
    fun `normalize quita espacios, prefijos y el nombre del archivo`() {
        assertEquals("abcdef", HashTool.normalize("  ABCDEF  "))
        assertEquals("abcdef", HashTool.normalize("sha256:ABCDEF"))
        assertEquals("abcdef", HashTool.normalize("SHA-256=abcdef"))
        assertEquals("abcdef", HashTool.normalize("md5:abcdef  archivo.pdf"))
        assertEquals("", HashTool.normalize("   "))
    }

    @Test
    fun `detectAlgorithm reconoce el algoritmo por el largo`() {
        assertEquals(HashAlgorithm.MD5, HashTool.detectAlgorithm("0".repeat(32)))
        assertEquals(HashAlgorithm.SHA1, HashTool.detectAlgorithm("a".repeat(40)))
        assertEquals(HashAlgorithm.SHA256, HashTool.detectAlgorithm("sha256:" + "f".repeat(64)))
        assertEquals(HashAlgorithm.SHA512, HashTool.detectAlgorithm("1".repeat(128)))
        assertNull(HashTool.detectAlgorithm("no es un hash"))
        assertNull(HashTool.detectAlgorithm("abc"))
    }

    @Test
    fun `matches ignora mayusculas y espacios`() {
        assertTrue(HashTool.matches("ABCDEF12", " abcdef12 "))
        assertFalse(HashTool.matches("abcdef12", "abcdef13"))
    }

    @Test
    fun `la lista de sumas usa el formato de sha256sum`() {
        val text = HashTool.checksumText(listOf(HashResult("a.pdf", 1, "aa"), HashResult("b c.txt", 2, "bb")))
        assertEquals("aa  a.pdf\nbb  b c.txt\n", text)
        assertEquals("hashes.sha256", HashTool.checksumFileName(HashAlgorithm.SHA256))
        assertEquals("hashes.md5", HashTool.checksumFileName(HashAlgorithm.MD5))
        assertEquals("hashes.sha1", HashTool.checksumFileName(HashAlgorithm.SHA1))
    }

    @Test
    fun `toHex rellena con cero a la izquierda`() {
        assertEquals("000f10ff", HashTool.toHex(byteArrayOf(0, 15, 16, -1)))
    }
}
