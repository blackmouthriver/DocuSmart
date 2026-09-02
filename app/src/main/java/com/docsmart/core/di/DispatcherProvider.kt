package com.docsmart.core.di

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject

/**
 * Indirección sobre los Dispatchers de kotlinx.coroutines -- permite que las
 * pruebas de Compose UI Testing reemplacen `io`/`default` por uno que no
 * dependa de un thread pool real del SO (ver docs/requirements/deployment.md
 * §3, "Undécimo intento", 2026-09-02: en el emulador de CI, corrutinas reales
 * que saltan a Dispatchers.IO/Default nunca resumen -- probablemente por
 * contención de threads bajo 2 vCPU + renderizado por software). `open`
 * para poder subclasificar en tests sin necesitar un `@Module`/`@Binds`
 * (todo el resto del proyecto usa `@Inject constructor` directo, ver
 * DatabaseModule.kt).
 */
open class DispatcherProvider @Inject constructor() {
    open val io: CoroutineDispatcher get() = Dispatchers.IO
    open val default: CoroutineDispatcher get() = Dispatchers.Default
}
