package com.docsmart.architecture

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Guardia contra un costo oculto de las pruebas instrumentadas (backlog: shard 6/8 del CI).
 *
 * `mockk<Activity>()` (o de `Context`, `Application`, `ContextWrapper`...) hace que MockK retransforme
 * `android.app.Activity` y toda su jerarquía hasta `Context`. Desde ese momento CADA llamada a
 * `getResources()` & co. del proceso de instrumentación pasa por `AndroidMockKDispatcher` y no se
 * deshace: medido en un emulador, la actividad de la siguiente prueba pasó de 1,3 s a 15 s y siguió así
 * hasta el final del proceso. Una sola prueba con ese mock dejó al shard 6/8 del CI en ~30 min frente a
 * ~10 min de los demás, además de llenar el heap de basura (hasta un `OutOfMemoryError`).
 *
 * En una prueba instrumentada se debe usar una instancia real (`Activity()` sin adjuntar sirve si la
 * rama bajo prueba no la usa) o la que da `ActivityScenario`/la regla de Compose.
 */
class AndroidTestMockkGuardTest {
    private val forbidden =
        Regex(
            """(mockk|spyk)\s*<\s*(android\.app\.|android\.content\.|androidx\.[\w.]+\.)?""" +
                """(Activity|FragmentActivity|ComponentActivity|AppCompatActivity|Context|ContextWrapper|""" +
                """ContextThemeWrapper|Application)\s*>""",
        )
    private val forbiddenByInference =
        Regex(
            """:\s*(android\.app\.|android\.content\.)?""" +
                """(Activity|FragmentActivity|ComponentActivity|AppCompatActivity|Context|ContextWrapper|""" +
                """ContextThemeWrapper|Application)\s*=\s*(mockk|spyk)\s*\(""",
        )

    private fun androidTestRoot(): File? =
        listOf("src/androidTest/java", "app/src/androidTest/java")
            .map(::File)
            .firstOrNull { it.isDirectory }

    @Test
    fun `las pruebas instrumentadas no mockean Activity ni Context con MockK`() {
        val root = androidTestRoot()
        assertTrue(root != null, "no se encontró src/androidTest/java desde ${File(".").absoluteFile}")

        val offenders =
            root!!
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .flatMap { file ->
                    file
                        .readLines()
                        .withIndex()
                        .filter { (_, line) ->
                            val code = line.substringBefore("//")
                            forbidden.containsMatchIn(code) || forbiddenByInference.containsMatchIn(code)
                        }.map { (index, line) -> "${file.relativeTo(root).path}:${index + 1}: ${line.trim()}" }
                }.toList()

        assertTrue(
            offenders.isEmpty(),
            "MockK sobre Activity/Context vuelve lenta toda la instrumentación (ver KDoc):\n" +
                offenders.joinToString("\n"),
        )
    }
}
