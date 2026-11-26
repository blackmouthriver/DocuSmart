package com.docsmart.features.utilities.domain

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.InputStream
import java.security.MessageDigest

/** Algoritmos que ofrece la herramienta, en el orden de la web (el primero es el recomendado). */
enum class HashAlgorithm(
    val label: String,
    val javaName: String,
    val hexLength: Int,
) {
    SHA256("SHA-256", "SHA-256", 64),
    SHA512("SHA-512", "SHA-512", 128),
    SHA1("SHA-1", "SHA-1", 40),
    MD5("MD5", "MD5", 32),
}

data class HashResult(
    val name: String,
    val size: Long,
    val hash: String,
)

/**
 * Hash e integridad de archivos (paridad con `integrity/core/hash.ts` de la web). Todo se calcula
 * en el dispositivo y el archivo se lee por partes, así que no importa cuánto pese. MD5 y SHA-1
 * están solo por compatibilidad con sumas publicadas: no sirven como garantía de seguridad.
 */
object HashTool {
    private const val BUFFER_SIZE = 64 * 1024
    private val PREFIX = Regex("^(?:sha-?(?:1|256|512)|md5)[:=]", RegexOption.IGNORE_CASE)
    private val HEX = Regex("^[0-9a-f]+$")
    private val WHITESPACE = Regex("\\s+")

    /**
     * Lo que pegó el usuario, listo para comparar: sin espacios, en minúsculas y sin el nombre del
     * archivo que traen las líneas de sha256sum (`<hash>  archivo.pdf`) ni prefijos como `sha256:`.
     */
    fun normalize(input: String): String {
        val first = input.trim().split(WHITESPACE).firstOrNull().orEmpty()
        return first.replace(PREFIX, "").lowercase()
    }

    /** Algoritmo que corresponde al hash pegado según su largo, o null si no parece un hash. */
    fun detectAlgorithm(input: String): HashAlgorithm? {
        val hash = normalize(input)
        if (!HEX.matches(hash)) return null
        return HashAlgorithm.entries.firstOrNull { it.hexLength == hash.length }
    }

    /** ¿El hash calculado coincide con el pegado? (sin importar mayúsculas ni espacios). */
    fun matches(
        computed: String,
        expected: String,
    ): Boolean = normalize(computed) == normalize(expected)

    /** Lista en el formato de `sha256sum` / `md5sum` (`<hash>  <nombre>`), que también entiende `sha256sum -c`. */
    fun checksumText(results: List<HashResult>): String = results.joinToString("\n") { "${it.hash}  ${it.name}" } + "\n"

    /** Nombre sugerido para la lista: `hashes.sha256`, `hashes.md5`... */
    fun checksumFileName(algorithm: HashAlgorithm): String = "hashes.${algorithm.label.replace("-", "").lowercase()}"

    fun toHex(bytes: ByteArray): String =
        buildString(bytes.size * 2) {
            for (b in bytes) append(HEX_DIGITS[(b.toInt() shr 4) and 0xF]).append(HEX_DIGITS[b.toInt() and 0xF])
        }

    /** Hash en hexadecimal (minúsculas) del flujo, leído por partes; se puede cancelar entre partes. */
    suspend fun digest(
        input: InputStream,
        algorithm: HashAlgorithm,
        onProgress: (bytesRead: Long) -> Unit = {},
    ): String {
        val digest = MessageDigest.getInstance(algorithm.javaName)
        val buffer = ByteArray(BUFFER_SIZE)
        var total = 0L
        while (true) {
            currentCoroutineContext().ensureActive()
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
            total += read
            onProgress(total)
        }
        return toHex(digest.digest())
    }

    private const val HEX_DIGITS = "0123456789abcdef"
}
