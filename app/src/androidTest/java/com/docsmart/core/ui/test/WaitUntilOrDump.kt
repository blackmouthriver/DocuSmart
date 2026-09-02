package com.docsmart.core.ui.test

import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToLog

/**
 * Diagnóstico temporal 2026-09-02 (ver docs/requirements/deployment.md §3,
 * "Undécimo intento"): un subconjunto fijo de pruebas (Converter, Home,
 * Library, Trash, QrCreator, Settings, ViewerRenameDelete) cuelga siempre
 * en `waitUntil()` en el emulador de CI -- confirmado que es un cuelgue
 * real (no falta de tiempo: subir el timeout de 20s a 60s no cambió nada,
 * cada corrida consume el presupuesto completo). Este wrapper vuelca el
 * árbol de semántica completo a logcat con el tag dado justo cuando el
 * timeout se cumple, para ver qué está realmente en pantalla en ese
 * momento en CI (¿la pantalla real de contenido con otro texto, un
 * estado de error, o directamente nada?) -- algo que un log de Gradle no
 * muestra. Quitar una vez encontrada la causa raíz.
 */
fun ComposeTestRule.waitUntilOrDump(tag: String, timeoutMillis: Long = 20_000, condition: () -> Boolean) {
    try {
        waitUntil(timeoutMillis = timeoutMillis, condition = condition)
    } catch (e: ComposeTimeoutException) {
        onRoot().printToLog(tag)
        throw e
    }
}
