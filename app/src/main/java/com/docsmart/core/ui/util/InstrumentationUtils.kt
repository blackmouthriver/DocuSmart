package com.docsmart.core.ui.util

// ActivityManager.isRunningInUserTestHarness() NO sirve para esto -- Google
// documenta explícitamente que es solo para Test Harness Mode (Firebase Test
// Lab), no para connectedAndroidTest normal. Se detecta en cambio si Espresso
// está en el classpath: solo ocurre cuando el APK de androidTest se mezcla al
// correr instrumentado.
fun isRunningUnderInstrumentation(): Boolean =
    try {
        Class.forName("androidx.test.espresso.Espresso")
        true
    } catch (_: ClassNotFoundException) {
        // Esperado en producción: Espresso solo está en el classpath cuando
        // el APK de androidTest se mezcla al correr instrumentado.
        false
    }
