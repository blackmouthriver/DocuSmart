package com.docsmart.core.navegation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Contrato de paridad web <-> app (F1 del plan de paridad).
 *
 * `parity/web-tool-parity.json` es una COPIA de `src/shared/files/toolParity.json` del repo
 * `docusmart-web`, que declara por cada herramienta de la web si la app la tiene. Al cambiar el
 * archivo de la web hay que volver a copiarlo aquí (y regenerar `WebToolLinks`, ver su cabecera).
 *
 * Esta prueba falla si la app enlaza una herramienta que la copia no marca como `si`, o si la copia
 * marca `si` una herramienta que la app no enlaza: así la paridad no se desalinea en silencio.
 */
class ToolParityContractTest {
    private val parity: Map<String, String> = loadParity()

    private fun loadParity(): Map<String, String> {
        val text =
            checkNotNull(javaClass.classLoader?.getResourceAsStream("parity/web-tool-parity.json")) {
                "falta parity/web-tool-parity.json en los recursos de prueba"
            }.bufferedReader().use { it.readText() }
        val tools = text.substringAfter("\"herramientas\"")
        return Regex(""""([a-z0-9-]+)"\s*:\s*\{\s*"app"\s*:\s*"([a-z-]+)"""")
            .findAll(tools)
            .associate { it.groupValues[1] to it.groupValues[2] }
    }

    @Test
    fun `la copia del contrato tiene herramientas y solo estados conocidos`() {
        assertTrue(parity.size >= 40, "se esperaban al menos 40 herramientas, hay ${parity.size}")
        val known = setOf("si", "planeada", "evaluar", "solo-web")
        assertEquals(emptyMap<String, String>(), parity.filterValues { it !in known })
    }

    @Test
    fun `toda herramienta que la app enlaza esta declarada como si`() {
        val linked = WebToolLinks.ROUTES.keys
        val notDeclared = linked.filter { parity[it] != "si" }
        assertEquals(emptyList<String>(), notDeclared, "la app enlaza herramientas que el contrato no marca como si")
    }

    @Test
    fun `toda herramienta declarada como si esta enlazada en la app`() {
        val declared = parity.filterValues { it == "si" }.keys
        val missing = declared.filter { it !in WebToolLinks.ROUTES }
        assertEquals(emptyList<String>(), missing, "el contrato marca como si herramientas que la app no enlaza")
    }
}
