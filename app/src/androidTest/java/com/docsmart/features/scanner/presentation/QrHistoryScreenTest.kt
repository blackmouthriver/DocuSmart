package com.docsmart.features.scanner.presentation

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import com.docsmart.R
import com.docsmart.core.ui.test.forceLocale
import com.docsmart.core.ui.test.waitUntilOrDump
import com.docsmart.features.scanner.domain.QrCrypto
import com.docsmart.features.scanner.domain.QrHistoryEntry
import com.docsmart.features.scanner.domain.QrHistorySource
import com.docsmart.features.scanner.domain.QrHistoryStorage
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * QrHistoryScreen.kt (HU-44): lista, vacío, borrar una entrada, vaciar todo y
 * los diálogos de detalle (regenerar un QR creado, ver/copiar uno leído y
 * desbloquear uno protegido). Las SharedPreferences del historial se aíslan
 * con [MemoryPrefsContext]: jamás se toca el historial real del dispositivo.
 * No se pulsan "Guardar"/"Compartir" del diálogo de regenerar (escriben en
 * Descargas / abren el selector del sistema).
 */
class QrHistoryScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var ctx: Context
    private val startMillis = System.currentTimeMillis()

    // Limpia los PNG temporales de "Guardar"/"Compartir" del diálogo de
    // regenerar (mismo criterio de limpieza que QrCreatorFlowsTest): solo
    // los archivos creados durante ESTE test, nunca QR reales del usuario.
    @After
    fun limpiarArchivosDeQrRegenerado() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val names = mutableListOf<String>()
        File(target.cacheDir, "qr").listFiles()?.forEach {
            if (it.lastModified() >= startMillis - 2_000) {
                names += it.name
                it.delete()
            }
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                names.forEach {
                    target.contentResolver.delete(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        "${MediaStore.MediaColumns.DISPLAY_NAME} = ?",
                        arrayOf(it),
                    )
                }
            }
        } catch (e: Exception) {
            // Sin permiso o sin dueño: no hay nada más que limpiar.
        }
    }

    private fun str(
        id: Int,
        vararg args: Any,
    ): String {
        val target = forceLocale(InstrumentationRegistry.getInstrumentation().targetContext, "es-ES")
        return if (args.isEmpty()) target.getString(id) else target.getString(id, *args)
    }

    @Before
    fun setUp() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        ctx = MemoryPrefsContext(forceLocale(target, "es-ES"))
    }

    private fun seed(vararg entries: QrHistoryEntry) {
        // save() antepone: se guardan en orden inverso para que la primera quede arriba.
        entries.reversed().forEach { QrHistoryStorage.save(ctx, it) }
    }

    private fun entry(
        id: String,
        content: String,
        type: String,
        source: QrHistorySource,
    ) = QrHistoryEntry(id, content, type, source, createdAtMillis = 1_700_000_000_000L)

    private fun render(onBack: () -> Unit = {}) {
        composeRule.setContentEs(overrideContext = { ctx }) { QrHistoryScreen(onBack = onBack) }
        composeRule.waitForIdle()
    }

    private fun assertAbsent(text: String) {
        assertTrue(composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty())
    }

    private val created = entry("c1", "https://creado.com", "URL", QrHistorySource.CREATED)
    private val scanned = entry("s1", "https://leido.com", "URL", QrHistorySource.SCANNED)
    private val wifi = entry("w1", "WIFI:T:WPA;S:Casa;P:secreto;;", "TEXT", QrHistorySource.SCANNED)
    private val contact =
        entry("k1", "BEGIN:VCARD\nVERSION:3.0\nFN:Ana Perez\nTEL:555\nEND:VCARD", "CONTACT", QrHistorySource.CREATED)

    @Test
    fun sinEntradas_muestraElEstadoVacioYNoOfreceVaciar() {
        render()

        composeRule.onNodeWithText(str(R.string.qr_history_empty_title)).assertExists()
        composeRule.onNodeWithText(str(R.string.qr_history_empty_body)).assertExists()
        assertTrue(
            composeRule
                .onAllNodesWithContentDescription(str(R.string.qr_history_clear_all))
                .fetchSemanticsNodes()
                .isEmpty(),
        )
    }

    @Test
    fun conEntradas_muestraFilasYEnmascaraDatosSensibles() {
        seed(created, wifi, contact)
        render()

        composeRule.onNodeWithText("https://creado.com").assertExists()
        composeRule.onNodeWithText(str(R.string.qr_history_wifi_preview, "Casa")).assertExists()
        composeRule.onNodeWithText(str(R.string.qr_history_contact_preview, "Ana Perez")).assertExists()
        // Ni la clave Wi-Fi ni el teléfono del contacto aparecen en la fila.
        assertAbsent("secreto")
        assertAbsent("555")
        composeRule.onNodeWithText(str(R.string.qr_history_empty_title)).assertDoesNotExist()
    }

    @Test
    fun entradaProtegida_ocultaElContenido() {
        val protectedContent = QrCrypto.PREFIX + QrCrypto.encrypt("hola", "clave")
        seed(entry("p1", protectedContent, "PROTECTED", QrHistorySource.SCANNED))
        render()

        composeRule.onNodeWithText(str(R.string.qr_history_protected_content)).assertExists()
        assertAbsent(protectedContent)
    }

    @Test
    fun eliminarUnaEntrada_confirmarLaQuitaYCancelarLaConserva() {
        seed(created, scanned)
        render()
        val delete = str(R.string.general_delete)

        // Cancelar: la entrada sigue.
        composeRule.onAllNodesWithContentDescription(delete).onFirst().performClick()
        composeRule.onNodeWithText(str(R.string.qr_history_delete_confirm_title)).assertExists()
        composeRule.onNodeWithText(str(R.string.general_cancel)).performClick()
        composeRule.waitForIdle()
        assertEquals(2, QrHistoryStorage.loadAll(ctx).size)

        // Confirmar: se elimina solo la primera (la más reciente de la lista).
        composeRule.onAllNodesWithContentDescription(delete).onFirst().performClick()
        composeRule.onNodeWithText(delete).performClick()
        composeRule.waitForIdle()

        val remaining = QrHistoryStorage.loadAll(ctx)
        assertEquals(1, remaining.size)
        assertEquals("s1", remaining.first().id)
        assertAbsent("https://creado.com")
    }

    @Test
    fun vaciarTodo_confirmarDejaElEstadoVacio() {
        seed(created, scanned, wifi)
        render()
        val clearAll = str(R.string.qr_history_clear_all)

        // Cancelar primero: no borra nada.
        composeRule.onNodeWithContentDescription(clearAll).performClick()
        composeRule.onNodeWithText(str(R.string.qr_history_clear_all_confirm_title)).assertExists()
        composeRule.onNodeWithText(str(R.string.general_cancel)).performClick()
        composeRule.waitForIdle()
        assertEquals(3, QrHistoryStorage.loadAll(ctx).size)

        composeRule.onNodeWithContentDescription(clearAll).performClick()
        composeRule.onNodeWithText(str(R.string.general_delete)).performClick()
        composeRule.waitForIdle()

        assertTrue(QrHistoryStorage.loadAll(ctx).isEmpty())
        composeRule.onNodeWithText(str(R.string.qr_history_empty_title)).assertExists()
    }

    @Test
    fun entradaLeida_abreElDetalleYPermiteCopiar() {
        seed(scanned)
        render()

        composeRule.onNodeWithText("https://leido.com").performClick()
        composeRule.onNodeWithText(str(R.string.qr_type_url_detected)).assertExists()
        composeRule.onNodeWithText(str(R.string.qr_copy_url)).performClick()
        composeRule.onNodeWithText(str(R.string.qr_copied_clipboard)).assertExists()

        composeRule.onNodeWithText(str(R.string.general_close)).performClick()
        composeRule.waitForIdle()
        assertAbsent(str(R.string.qr_type_url_detected))
    }

    @Test
    fun entradaLeidaDeWifi_ofreceCopiarLaClaveEnElDetalle() {
        seed(wifi)
        render()

        composeRule
            .onNodeWithText(str(R.string.qr_history_wifi_preview, "Casa"))
            .performClick()
        composeRule.onNodeWithText(str(R.string.qr_type_wifi_detected)).assertExists()
        composeRule.onNodeWithText(str(R.string.qr_copy_wifi_password)).performClick()
        composeRule.onNodeWithText(str(R.string.qr_copied_clipboard)).assertExists()
    }

    @Test
    fun entradaCreada_regeneraElQrYSePuedeCerrar() {
        seed(created)
        render()

        composeRule.onNodeWithText("https://creado.com").performClick()
        composeRule.onNodeWithText(str(R.string.qr_your_code)).assertExists()
        composeRule.waitUntilOrDump("CI_HANG_QrHistoryScreenTest", timeoutMillis = 10_000) {
            composeRule
                .onAllNodesWithContentDescription(str(R.string.qr_generated_content_desc))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        composeRule.onNodeWithText(str(R.string.general_close)).performClick()
        composeRule.waitForIdle()
        assertAbsent(str(R.string.qr_your_code))
    }

    @Test
    fun entradaProtegida_claveIncorrectaMuestraError() {
        val protectedContent = QrCrypto.PREFIX + QrCrypto.encrypt("hola mundo", "clave-buena")
        seed(entry("p1", protectedContent, "PROTECTED", QrHistorySource.SCANNED))
        render()

        composeRule.onNodeWithText(str(R.string.qr_history_protected_content)).performClick()
        composeRule.onNodeWithText(str(R.string.qr_protected_title)).assertExists()

        composeRule.onNodeWithText(str(R.string.qr_password_label)).performTextInput("mala")
        composeRule.onNodeWithText(str(R.string.qr_unlock)).performClick()
        composeRule.onNodeWithText(str(R.string.pdf_pw_wrong_password)).assertExists()
    }

    @Test
    fun entradaProtegida_claveCorrectaMuestraElContenidoDescifrado() {
        val protectedContent = QrCrypto.PREFIX + QrCrypto.encrypt("hola mundo", "clave")
        seed(entry("p1", protectedContent, "PROTECTED", QrHistorySource.SCANNED))
        render()

        composeRule.onNodeWithText(str(R.string.qr_history_protected_content)).performClick()
        composeRule.onNodeWithText(str(R.string.qr_password_label)).performTextInput("clave")
        composeRule.onNodeWithText(str(R.string.qr_unlock)).performClick()

        composeRule.waitUntilOrDump("CI_HANG_QrHistoryScreenTest", timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("hola mundo").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(str(R.string.qr_type_text_detected)).assertExists()
    }

    @Test
    fun protegida_cancelarElDialogoDeClaveLoCierra() {
        val protectedContent = QrCrypto.PREFIX + QrCrypto.encrypt("hola", "clave")
        seed(entry("p1", protectedContent, "PROTECTED", QrHistorySource.SCANNED))
        render()

        composeRule.onNodeWithText(str(R.string.qr_history_protected_content)).performClick()
        composeRule.onNodeWithText(str(R.string.general_cancel)).performClick()
        composeRule.waitForIdle()

        assertAbsent(str(R.string.qr_protected_title))
    }

    // Hallazgo de cobertura (ronda 23): el diálogo de "regenerar" (entrada
    // CREATED) nunca pulsaba Guardar/Compartir -- solo se comprobaba que el
    // QR se mostraba y que "Cerrar" funcionaba. Los `onClick` reales de esos
    // dos botones (saveQrToFile + DownloadsSaver / shareQrImage) quedaban en
    // 0%. Mismo patrón ya probado en QrCreatorFlowsTest (Guardar real vía
    // MediaStore, limpiado en @After; Compartir grabado con RecordingContext
    // sin abrir ningún chooser real).
    @Test
    fun entradaCreada_guardarMuestraLaConfirmacion() {
        seed(created)
        render()

        composeRule.onNodeWithText("https://creado.com").performClick()
        composeRule.waitUntilOrDump("CI_HANG_QrHistoryScreenTest", timeoutMillis = 10_000) {
            composeRule
                .onAllNodesWithContentDescription(str(R.string.qr_generated_content_desc))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        composeRule.onNodeWithText(str(R.string.general_save)).performClick()
        composeRule.waitUntilOrDump("CI_HANG_QrHistoryScreenTest", timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText(str(R.string.general_saved_downloads)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun entradaCreada_compartirArmaElChooserConElPng() {
        seed(created)
        var recording: RecordingContext? = null
        composeRule.setContentEs(overrideContext = { RecordingContext(ctx).also { recording = it } }) {
            QrHistoryScreen(onBack = {})
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("https://creado.com").performClick()
        composeRule.waitUntilOrDump("CI_HANG_QrHistoryScreenTest", timeoutMillis = 10_000) {
            composeRule
                .onAllNodesWithContentDescription(str(R.string.qr_generated_content_desc))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithText(str(R.string.general_share)).performClick()
        composeRule.waitUntilOrDump("CI_HANG_QrHistoryScreenTest", timeoutMillis = 10_000) {
            recording?.started?.isNotEmpty() == true
        }

        val chooser = recording!!.started.first()
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        @Suppress("DEPRECATION")
        val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        assertNotNull(send)
        assertEquals("image/png", send!!.type)
    }

    // Hallazgo de cobertura (ronda 23): ningún test existente pasaba `onHome`
    // -- la rama "Inicio" de BannerNavRow (DocuSmartTopBanner) quedaba en 0%
    // para esta pantalla.
    @Test
    fun banner_conOnHome_invocaSuCallback() {
        var homeCount = 0
        composeRule.setContentEs(overrideContext = { ctx }) { QrHistoryScreen(onHome = { homeCount++ }) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(str(R.string.nav_home)).performClick()
        composeRule.waitForIdle()

        assertEquals(1, homeCount)
    }
}
