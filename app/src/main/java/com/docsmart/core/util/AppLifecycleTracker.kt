package com.docsmart.core.util

import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Envoltorio inyectable sobre `ProcessLifecycleOwner.get().lifecycle` --
 * ese singleton de AndroidX solo se inicializa en un proceso Android real
 * (vía App Startup/ContentProvider), no en un test unitario plano sobre la
 * JVM, así que llamarlo directo desde el `init` de un ViewModel rompe
 * cualquier test que lo construya. Con esto de por medio, un test puede
 * pasar un mock/fake en vez del real (`AppLifecycleTracker` tiene
 * `@Inject constructor()` sin argumentos, así que Hilt lo provee solo, sin
 * necesitar un `@Module` nuevo -- mismo criterio que el resto del proyecto).
 */
@Singleton
class AppLifecycleTracker @Inject constructor() {
    fun addObserver(observer: LifecycleEventObserver) {
        ProcessLifecycleOwner.get().lifecycle.addObserver(observer)
    }

    fun removeObserver(observer: LifecycleEventObserver) {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(observer)
    }
}
