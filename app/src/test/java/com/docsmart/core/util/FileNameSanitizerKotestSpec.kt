package com.docsmart.core.util

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import io.kotest.property.Arb
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll

/**
 * Kotest (pedido explícito del usuario, 2026-09-18): `sanitizeOutputFileName()`
 * es la única defensa real contra path traversal en el campo "Nombre del
 * archivo" de Herramientas PDF/Convertidor (ver el comentario del propio
 * hallazgo en FileNameSanitizer.kt) y no tenía ningún test -- se cubre acá
 * en vez de en un archivo JUnit5 nuevo para dejar un ejemplo real de las dos
 * cosas que Kotest aporta sobre JUnit5+MockK (ya usados en el resto del
 * proyecto): specs más legibles (`FunSpec`) y testing basado en propiedades
 * (`checkAll`) para una función de saneo donde "para cualquier entrada
 * posible" es la garantía que realmente importa, no solo un puñado de casos
 * fijos.
 */
class FileNameSanitizerKotestSpec : FunSpec({

    test("descarta cualquier segmento de ruta y se queda solo con el nombre") {
        sanitizeOutputFileName("../../shared_prefs/docusmart_security") shouldBe "docusmart_security"
        sanitizeOutputFileName("C:\\Windows\\System32\\evil") shouldBe "evil"
    }

    test("preserva letras, dígitos, espacio, guion, guion bajo y punto") {
        sanitizeOutputFileName("Contrato Final_v2.pdf") shouldBe "Contrato Final_v2.pdf"
    }

    test("preserva acentos y Unicode de los 12 idiomas soportados") {
        sanitizeOutputFileName("Año_Übersicht_日本語") shouldBe "Año_Übersicht_日本語"
    }

    test("filtra caracteres fuera del alfabeto seguro") {
        sanitizeOutputFileName("nombre*con?símbolos<>|") shouldBe "nombreconsímbolos"
    }

    test("colapsa .. reconstruible tras filtrar un carácter separador") {
        // Hallazgo real de la ronda de seguridad 2026-09-16 (ver comentario
        // en FileNameSanitizer.kt): ".!." pierde el "!" en el filtro y
        // reconstruiría ".." si el colapso corriera antes del filtro.
        sanitizeOutputFileName(".!.") shouldNotContain ".."
    }

    test("el resultado nunca contiene '..' para ninguna entrada arbitraria") {
        checkAll(Arb.string(0..64)) { input ->
            sanitizeOutputFileName(input) shouldNotContain ".."
        }
    }

    test("el resultado nunca contiene separadores de ruta para ninguna entrada arbitraria") {
        checkAll(Arb.string(0..64)) { input ->
            val result = sanitizeOutputFileName(input)
            result shouldNotContain "/"
            result shouldNotContain "\\"
        }
    }
})
