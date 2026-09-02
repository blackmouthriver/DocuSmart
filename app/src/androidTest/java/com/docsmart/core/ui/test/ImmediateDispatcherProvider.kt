package com.docsmart.core.ui.test

import com.docsmart.core.di.DispatcherProvider
import kotlinx.coroutines.Dispatchers

/**
 * DispatcherProvider de prueba que no usa un thread pool real -- ver
 * docs/requirements/deployment.md §3 ("Undécimo intento", 2026-09-02):
 * corrutinas reales que saltan a Dispatchers.IO/Default nunca resumen en
 * el emulador de CI (contención de threads bajo 2 vCPU + renderizado por
 * software). Al usar Dispatchers.Main.immediate para `io`/`default`, el
 * trabajo de la corrutina corre inline en el mismo thread de Compose
 * Testing en vez de esperar un thread de fondo real.
 */
class ImmediateDispatcherProvider : DispatcherProvider() {
    override val io = Dispatchers.Main.immediate
    override val default = Dispatchers.Main.immediate
}
