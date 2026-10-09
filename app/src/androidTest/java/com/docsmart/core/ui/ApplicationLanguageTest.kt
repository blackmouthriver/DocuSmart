package com.docsmart.core.ui

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.docsmart.R
import com.docsmart.core.ui.test.forceLocale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Los textos que se generan con `@ApplicationContext` (casos de uso del Convertidor, ViewModels de
 * Inicio/Biblioteca/Papelera, receptores de notificaciones) deben salir en el idioma elegido DENTRO
 * de la app, no en el del teléfono. Antes de este arreglo `MainActivity` aplicaba el idioma solo a su
 * propio contexto y los recursos de la `Application` seguían en el idioma del dispositivo.
 *
 * Estas pruebas cambian el idioma guardado de la app: se restaura en `@After`.
 */
@RunWith(AndroidJUnit4::class)
class ApplicationLanguageTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val prefs = app.getSharedPreferences("docusmart_language", Context.MODE_PRIVATE)
    private var savedLanguage: String? = null

    // Cadena traducida distinta en cada idioma, resuelta con un contexto forzado: no depende de
    // ningún estado global ni del idioma del dispositivo.
    private fun expected(tag: String) = forceLocale(app, tag).getString(R.string.general_delete_error)

    @Before
    fun guardarIdioma() {
        savedLanguage = prefs.getString("language", null)
    }

    @After
    fun restaurarIdioma() {
        prefs.edit().apply {
            if (savedLanguage == null) remove("language") else putString("language", savedLanguage)
        }.commit()
        // La configuración REAL del sistema, no la de los recursos de la app: otra prueba pudo
        // haberlos modificado y se contaminaría la siguiente.
        applyLanguageToApplicationResources(app, savedOrDeviceLanguageCode(app, Resources.getSystem().configuration))
    }

    @Test
    fun losTextosDelContextoDeAplicacionNoSonIgualesEnDosIdiomas() {
        // Si esto falla la prueba de abajo no demostraría nada (la misma cadena en es y en en).
        assertNotEquals(expected("es"), expected("en"))
    }

    @Test
    fun cambiarElIdiomaDeLaAppCambiaLosTextosDelContextoDeAplicacion() {
        val manager = LanguageManager(app)

        manager.setLanguage(AppLanguage.ENGLISH)
        assertEquals(expected("en"), app.getString(R.string.general_delete_error))

        manager.setLanguage(AppLanguage.SPANISH)
        assertEquals(expected("es"), app.getString(R.string.general_delete_error))

        manager.setLanguage(AppLanguage.FRENCH)
        assertEquals(expected("fr"), app.getString(R.string.general_delete_error))
    }

    @Test
    fun alCambiarLaConfiguracionDelSistemaSeVuelveAAplicarElIdiomaElegido() {
        prefs.edit().putString("language", "en").commit()
        // El sistema reescribe en la Application su propia configuración (aquí: idioma del teléfono
        // = español) en cada rotación / modo oscuro / cambio de idioma del sistema.
        val systemConfig = Configuration(app.resources.configuration).apply { setLocale(java.util.Locale("es")) }
        @Suppress("DEPRECATION")
        app.resources.updateConfiguration(systemConfig, app.resources.displayMetrics)
        assertEquals(expected("es"), app.getString(R.string.general_delete_error))

        app.onConfigurationChanged(systemConfig)

        assertEquals(expected("en"), app.getString(R.string.general_delete_error))
    }

    @Test
    fun sinIdiomaGuardadoSeUsaElDelDispositivoSiEsSoportadoYSiNoEspanol() {
        prefs.edit().remove("language").commit()
        val deviceEnglish = Configuration(app.resources.configuration).apply { setLocale(java.util.Locale("en")) }
        val deviceUnsupported = Configuration(app.resources.configuration).apply { setLocale(java.util.Locale("nl")) }

        assertEquals("en", savedOrDeviceLanguageCode(app, deviceEnglish))
        assertEquals("es", savedOrDeviceLanguageCode(app, deviceUnsupported))
    }
}
