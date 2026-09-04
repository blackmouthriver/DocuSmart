package com.docsmart.core.analytics

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import timber.log.Timber

/**
 * Reenvía los `Timber.w`/`Timber.e` ya existentes en todo el proyecto (cientos
 * de call sites, muchos agregados esta sesión al corregir `SwallowedException`
 * de detekt) hacia Firebase Crashlytics, en vez de requerir agregar una llamada
 * manual a `recordException()` en cada catch. `DEBUG`/`INFO`/`VERBOSE` quedan
 * como breadcrumbs (`log()`) para dar contexto al reporte; `WARN`/`ERROR` con
 * una excepción real se registran como no fatales (`recordException()`), que
 * es lo que hace visible el error en la consola de Firebase sin tumbar la app.
 */
class CrashlyticsTree : Timber.Tree() {

    override fun isLoggable(tag: String?, priority: Int): Boolean = priority >= Log.INFO

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        val crashlytics = FirebaseCrashlytics.getInstance()
        crashlytics.log("${tag.orEmpty()}: $message")

        if (priority >= Log.WARN && t != null) {
            crashlytics.recordException(t)
        }
    }
}
