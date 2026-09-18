package com.docsmart.architecture

import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.junit.jupiter.api.Test

/**
 * ArchUnit (pedido explícito del usuario, 2026-09-18): reglas de
 * arquitectura reales, verificadas contra el código actual antes de
 * escribirlas (no aspiracionales) -- el proyecto no sigue Clean
 * Architecture estricta en todas las features (varias, como `home`/
 * `settings`/`splash`, solo tienen `presentation`, sin `domain`/`data`),
 * así que las reglas acá cubren exactamente lo que SÍ es verdad hoy: la
 * capa de dominio y de datos nunca dependen hacia la UI.
 *
 * Escanea `app/build/tmp/kotlin-classes/debug` (clases ya compiladas, vía
 * el classpath normal de test) con `ClassFileImporter`, igual que
 * `jacocoTestReport`/Kover ya apuntan a esa misma carpeta para cobertura.
 */
class ArchitectureTest {

    private val importedClasses = ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .importPackages("com.docsmart")

    @Test
    fun `domain no depende de presentation`() {
        noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAPackage("..presentation..")
            .check(importedClasses)
    }

    @Test
    fun `data no depende de presentation`() {
        noClasses()
            .that().resideInAPackage("..data..")
            .should().dependOnClassesThat().resideInAPackage("..presentation..")
            .check(importedClasses)
    }

    // El compilador de Compose inyecta @androidx.compose.runtime.internal.
    // StabilityInferred en el bytecode de TODA clase del módulo (no solo
    // las de UI) cuando el plugin kotlin.compose está aplicado al módulo
    // entero, como acá -- confirmado en la primera corrida de este test:
    // 195 "violaciones" que eran en realidad esa única anotación en clases
    // completamente ajenas a Compose. Es un artefacto del compilador, no
    // una dependencia real escrita por el código, así que se excluye ese
    // paquete puntual en vez de debilitar la regla para paquetes reales de
    // Compose (`androidx.compose.ui..`, `androidx.compose.runtime.Composable`,
    // etc.), que sí importan si aparecen en domain/data.
    private val realComposeUsage = DescribedPredicate.describe<JavaClass>(
        "reside in a real (no generado por el compilador) androidx.compose.."
    ) { javaClass ->
        val pkg = javaClass.packageName
        pkg.startsWith("androidx.compose") && !pkg.startsWith("androidx.compose.runtime.internal")
    }

    @Test
    fun `domain y data no dependen de Jetpack Compose`() {
        // Única excepción real y ya documentada: VoicePersona.kt (domain de
        // Modo Estudio) usa androidx.compose.ui.graphics.Color como tipo de
        // valor puro (el color asociado a una persona de voz), sin ninguna
        // lógica de UI real -- mismo criterio que config/detekt/baseline.xml
        // usa para excepciones puntuales ya evaluadas, en vez de debilitar
        // la regla para todo el proyecto.
        noClasses()
            .that().resideInAPackage("..domain..")
            .or().resideInAPackage("..data..")
            .and().haveNameNotMatching(".*VoicePersona.*")
            .should().dependOnClassesThat(realComposeUsage)
            .check(importedClasses)
    }
}
