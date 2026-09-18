package com.docsmart.core.util

import android.os.SystemClock

// SystemClock.elapsedRealtime() no está disponible sin Robolectric en los
// tests unitarios JVM de este proyecto (lanza RuntimeException, "not
// mocked") -- se degrada con gracia a currentTimeMillis() en ese caso. En
// un dispositivo real nunca lanza, así que esto no cambia el
// comportamiento en producción. Mismo patrón ya usado en
// SecurityManager.elapsedRealtimeMillis() (PIN), extraído acá para
// reutilizarlo sin duplicar el try/catch.
@Suppress("TooGenericExceptionCaught", "SwallowedException")
fun elapsedRealtimeMillisSafe(): Long =
    try {
        SystemClock.elapsedRealtime()
    } catch (e: RuntimeException) {
        System.currentTimeMillis()
    }
