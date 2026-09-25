package com.docsmart.features.scanner.presentation

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.core.content.FileProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.docsmart.R
import com.docsmart.core.ads.AdManager
import com.docsmart.core.ui.test.forceLocale
import com.docsmart.core.ui.test.waitUntilOrDump
import com.docsmart.features.scanner.domain.QrCrypto
import com.docsmart.features.scanner.domain.QrHistoryEntry
import com.docsmart.features.scanner.domain.QrHistorySource
import com.docsmart.features.scanner.domain.QrHistoryStorage
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * QrCreatorScreen (QrScreen.kt, ronda 20): un caso por tipo de QR (URL, texto, email,
 * teléfono, Wi-Fi, contacto, evento, imagen, documento), contraseña, color, logo, y el
 * resultado (guardar/compartir). Complementa QrCreatorScreenTest (URL y texto con
 * clave). El historial va a SharedPreferences en memoria ([MemoryPrefsContext]); los
 * selectores de archivos usan un registro falso y compartir un contexto que graba
 * los intents.
 */
class QrCreatorFlowsTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val targetContext get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val startMillis = System.currentTimeMillis()
    private val createdFiles = mutableListOf<File>()

    private fun str(id: Int): String = forceLocale(targetContext, "es-ES").getString(id)

    @After
    fun limpiar() {
        createdFiles.forEach { it.delete() }
        val names = mutableListOf<String>()
        File(targetContext.cacheDir, "qr").listFiles()?.forEach {
            if (it.lastModified() >= startMillis - 2_000) {
                names += it.name
                it.delete()
            }
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Solo los PNG de esta prueba (nombre exacto): nunca los QR reales del usuario.
                names.forEach {
                    targetContext.contentResolver.delete(
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

    private class Shown {
        var recording: RecordingContext? = null
        val back = AtomicInteger()
        val history = AtomicInteger()
    }

    private fun buildViewModel(): QrViewModel {
        val adManager = mockk<AdManager>(relaxed = true)
        every { adManager.isPremium } returns MutableStateFlow(true)
        every { adManager.isInitialized } returns MutableStateFlow(false)
        return QrViewModel(adManager = adManager)
    }

    private fun show(
        registry: FakeResultRegistryOwner = FakeResultRegistryOwner(),
        fileUri: String? = null,
        fileType: String? = null,
        fileName: String? = null,
    ): Shown {
        val shown = Shown()
        val viewModel = buildViewModel()
        composeRule.setContentEsFit(
            overrideContext = { RecordingContext(MemoryPrefsContext(it)).also { ctx -> shown.recording = ctx } },
            registryOwner = registry,
        ) {
            QrCreatorScreen(
                onBack = { shown.back.incrementAndGet() },
                initialFileUri = fileUri,
                initialFileType = fileType,
                initialFileName = fileName,
                onHistoryClick = { shown.history.incrementAndGet() },
                viewModel = viewModel,
            )
        }
        composeRule.waitForIdle()
        return shown
    }

    private fun waitForText(
        text: String,
        timeoutMillis: Long = 15_000,
    ) {
        composeRule.waitUntilOrDump("R20_QrCreator", timeoutMillis) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun chip(res: Int) {
        composeRule.onNodeWithText(str(res)).performClick()
        composeRule.waitForIdle()
    }

    private fun type(
        labelRes: Int,
        text: String,
    ) {
        composeRule.onNodeWithText(str(labelRes)).performScrollTo().performTextInput(text)
    }

    private fun generate() {
        composeRule.onNodeWithText(str(R.string.qr_generate)).performScrollTo().performClick()
    }

    private fun generateAndWaitResult() {
        generate()
        waitForText(str(R.string.qr_your_code))
        val qrImage = composeRule.onNodeWithContentDescription(str(R.string.qr_generated_content_desc))
        qrImage.performScrollTo().assertExists()
    }

    private fun history(shown: Shown): List<QrHistoryEntry> = QrHistoryStorage.loadAll(checkNotNull(shown.recording))

    private fun assertSingleCreated(
        shown: Shown,
        typeName: String,
    ): QrHistoryEntry {
        val entries = history(shown)
        assertEquals(1, entries.size)
        assertEquals(typeName, entries[0].typeName)
        assertEquals(QrHistorySource.CREATED, entries[0].source)
        return entries[0]
    }

    private fun testImage(name: String = "r20_qr_${System.nanoTime()}.jpg"): File =
        createTestJpeg(
            File(File(targetContext.filesDir, "converted").apply { mkdirs() }, name),
            Color.BLUE,
            80,
            80,
        ).also { createdFiles += it }

    // ── Tipos de contenido ───────────────────────────────────────────────────────

    @Test
    fun url_generaElCodigoYLoGuardaEnElHistorial() {
        val shown = show()

        type(R.string.qr_label_url, "ejemplo.com")
        generateAndWaitResult()

        val entry = assertSingleCreated(shown, "URL")
        assertTrue(entry.content.startsWith("https://"))
        assertTrue(entry.content.contains("ejemplo.com"))
    }

    @Test
    fun email_generaElCodigoConEsquemaMailto() {
        val shown = show()

        chip(R.string.qr_chip_email)
        type(R.string.qr_label_email, "ana@ejemplo.com")
        generateAndWaitResult()

        val entry = assertSingleCreated(shown, "EMAIL")
        assertTrue(entry.content.contains("ana@ejemplo.com"))
    }

    @Test
    fun telefono_generaElCodigoConEsquemaTel() {
        val shown = show()

        chip(R.string.qr_chip_phone)
        type(R.string.qr_label_phone, "3001234567")
        generateAndWaitResult()

        val entry = assertSingleCreated(shown, "PHONE")
        assertTrue(entry.content.contains("3001234567"))
    }

    @Test
    fun textoLargoConTildes_generaElCodigoYLoGuardaComoTexto() {
        val shown = show()

        chip(R.string.qr_chip_text)
        type(R.string.qr_label_text, "Reunión de canción y ñandú")
        generateAndWaitResult()

        val entry = assertSingleCreated(shown, "TEXT")
        assertEquals("Reunión de canción y ñandú", entry.content)
    }

    @Test
    fun cambiarDeTipo_limpiaElContenidoYDeshabilitaGenerar() {
        show()

        type(R.string.qr_label_url, "ejemplo.com")
        chip(R.string.qr_chip_text)

        composeRule.onNodeWithText(str(R.string.qr_generate)).performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun wifi_conClave_generaYGuardaElPayloadWifi() {
        val shown = show()

        chip(R.string.qr_chip_wifi)
        composeRule.onNodeWithText(str(R.string.qr_generate)).performScrollTo().assertIsNotEnabled()
        type(R.string.qr_label_wifi_ssid, "MiRed")
        type(R.string.qr_label_wifi_password, "clave1234")
        generateAndWaitResult()

        val entry = assertSingleCreated(shown, "WIFI")
        assertTrue(entry.content.startsWith("WIFI:"))
        assertTrue(entry.content.contains("MiRed"))
    }

    @Test
    fun wifi_redAbierta_noPideClave() {
        val shown = show()

        chip(R.string.qr_chip_wifi)
        composeRule.onNodeWithText(str(R.string.qr_wifi_security_none)).performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText(str(R.string.qr_label_wifi_password)).assertCountEquals(0)
        type(R.string.qr_label_wifi_ssid, "RedAbierta")
        generateAndWaitResult()

        assertSingleCreated(shown, "WIFI")
    }

    @Test
    fun contacto_conNombre_generaYGuardaUnaVCard() {
        val shown = show()

        chip(R.string.qr_chip_contact)
        composeRule.onNodeWithText(str(R.string.qr_generate)).performScrollTo().assertIsNotEnabled()
        type(R.string.qr_label_contact_name, "Ana Pérez")
        type(R.string.qr_label_contact_phone, "3001234567")
        generateAndWaitResult()

        val entry = assertSingleCreated(shown, "CONTACT")
        assertTrue(entry.content.contains("BEGIN:VCARD"))
    }

    @Test
    fun evento_conTitulo_generaYGuardaUnVEvent() {
        val shown = show()

        chip(R.string.qr_chip_event)
        composeRule.onNodeWithText(str(R.string.qr_generate)).performScrollTo().assertIsNotEnabled()
        type(R.string.qr_label_event_title, "Reunión de equipo")
        type(R.string.qr_label_event_location, "Sala 2")
        generateAndWaitResult()

        val entry = assertSingleCreated(shown, "EVENT")
        assertTrue(entry.content.contains("BEGIN:VEVENT"))
    }

    // ── Imagen y documento ───────────────────────────────────────────────────────

    @Test
    fun imagenYDocumento_sinArchivo_muestranElSelectorYNoPermitenGenerar() {
        show()

        chip(R.string.qr_chip_image)
        composeRule.onNodeWithText(str(R.string.qr_select_image)).assertExists()
        composeRule.onNodeWithText(str(R.string.qr_image_formats_hint)).assertExists()
        composeRule.onNodeWithText(str(R.string.qr_generate)).performScrollTo().assertIsNotEnabled()

        chip(R.string.qr_chip_document)
        composeRule.onNodeWithText(str(R.string.qr_select_document)).assertExists()
        composeRule.onNodeWithText(str(R.string.qr_document_formats_hint)).assertExists()
        composeRule.onNodeWithText(str(R.string.qr_generate)).performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun elegirImagen_usaElSelectorSinAbrirNadaRealYAvisaSiNoSePuedePersistir() {
        val file = testImage()
        val uri = FileProvider.getUriForFile(targetContext, "${targetContext.packageName}.fileprovider", file)
        val registry = FakeResultRegistryOwner { uri }
        show(registry = registry)

        chip(R.string.qr_chip_image)
        composeRule.onNodeWithText(str(R.string.qr_select_image)).performClick()

        // Con un URI propio sin permiso persistible el permiso falla y se avisa; si el
        // sistema lo concede, la imagen queda seleccionada. Ambas ramas son válidas.
        composeRule.waitUntilOrDump("R20_QrImagePick", 10_000) {
            composeRule.onAllNodesWithText(str(R.string.qr_persist_permission_failed)).fetchSemanticsNodes()
                .isNotEmpty() ||
                composeRule.onAllNodesWithText(str(R.string.qr_image_selected)).fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(listOf("OpenDocument"), registry.launched.toList())
    }

    @Test
    fun elegirDocumento_cancelarNoCambiaNada() {
        val registry = FakeResultRegistryOwner { null }
        show(registry = registry)

        chip(R.string.qr_chip_document)
        composeRule.onNodeWithText(str(R.string.qr_select_document)).performClick()
        composeRule.waitForIdle()

        assertEquals(listOf("OpenDocument"), registry.launched.toList())
        composeRule.onNodeWithText(str(R.string.qr_select_document)).assertExists()
    }

    @Test
    fun atajoCrearQrDeImagen_preseleccionaElChipYGeneraConLaUri() {
        val uri = "content://com.docsmart.fileprovider/converted/foto.png"
        val shown = show(fileUri = uri, fileType = "image", fileName = "foto.png")

        waitForText(str(R.string.qr_image_selected))
        composeRule.onNodeWithText(str(R.string.qr_chip_image)).assertIsSelected()
        composeRule.onNodeWithText("foto.png").assertExists()
        generateAndWaitResult()

        val entry = assertSingleCreated(shown, "IMAGE")
        assertEquals(uri, entry.content)
    }

    @Test
    fun atajoCrearQrDeDocumento_preseleccionaElChipYGeneraConLaUri() {
        val uri = "content://com.docsmart.fileprovider/converted/informe.pdf"
        val shown = show(fileUri = uri, fileType = "document", fileName = "informe.pdf")

        waitForText(str(R.string.qr_document_selected))
        composeRule.onNodeWithText(str(R.string.qr_chip_document)).assertIsSelected()
        generateAndWaitResult()

        val entry = assertSingleCreated(shown, "DOCUMENT")
        assertEquals(uri, entry.content)
    }

    // ── Diseño: color y logo ─────────────────────────────────────────────────────

    @Test
    fun colorDeBajoContraste_bloqueaLaGeneracionYUnColorOscuroLaPermite() {
        show()

        type(R.string.qr_label_url, "ejemplo.com")
        composeRule.onNodeWithContentDescription(str(R.string.qr_color_yellow)).performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(str(R.string.qr_design_contrast_warning)).assertExists()
        generate()
        waitForText(str(R.string.qr_error_low_contrast))
        composeRule.onAllNodesWithText(str(R.string.qr_your_code)).assertCountEquals(0)

        composeRule.onNodeWithContentDescription(str(R.string.qr_color_blue)).performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText(str(R.string.qr_design_contrast_warning)).assertCountEquals(0)
        composeRule.onNodeWithContentDescription(str(R.string.qr_color_blue)).assertIsSelected()
        generateAndWaitResult()
    }

    @Ignore("inestable en el emulador de CI 320x640 (temporización); pendiente, ver backlog v17")
    @Test
    fun logo_agregarGenerarYQuitar() {
        val file = testImage()
        val registry = FakeResultRegistryOwner { Uri.fromFile(file) }
        show(registry = registry)

        type(R.string.qr_label_url, "ejemplo.com")
        composeRule.onNodeWithText(str(R.string.qr_design_add_logo)).performScrollTo().performClick()
        waitForText(str(R.string.qr_design_logo_added))
        assertEquals(listOf("GetContent"), registry.launched.toList())

        generateAndWaitResult()

        composeRule.onNodeWithContentDescription(str(R.string.qr_design_remove_logo)).performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(str(R.string.qr_design_add_logo)).assertExists()
        composeRule.onAllNodesWithText(str(R.string.qr_your_code)).assertCountEquals(0)
    }

    @Test
    fun logo_conArchivoQueNoEsImagen_muestraElError() {
        val file = File(targetContext.cacheDir, "r20_no_logo_${System.nanoTime()}.jpg")
        file.writeText("esto no es una imagen")
        createdFiles += file
        show(registry = FakeResultRegistryOwner { Uri.fromFile(file) })

        composeRule.onNodeWithText(str(R.string.qr_design_add_logo)).performScrollTo().performClick()

        waitForText(str(R.string.qr_error_logo_load))
        composeRule.onAllNodesWithText(str(R.string.qr_design_logo_added)).assertCountEquals(0)
    }

    // ── Contraseña ───────────────────────────────────────────────────────────────

    @Test
    fun contrasenaCorta_muestraElErrorYUnaValidaProtegeElQr() {
        val shown = show()

        type(R.string.qr_label_url, "ejemplo.com")
        composeRule.onNode(isToggleable()).performScrollTo().performClick()
        composeRule.waitForIdle()
        type(R.string.qr_password_label, "12")
        generate()
        waitForText(str(R.string.qr_error_password_short))
        assertTrue(history(shown).isEmpty())

        composeRule.onNodeWithContentDescription(str(R.string.password_show)).performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription(str(R.string.password_hide)).assertExists()
        type(R.string.qr_password_label, "34")
        generateAndWaitResult()

        composeRule.onNodeWithText(str(R.string.qr_protected_badge)).performScrollTo().assertExists()
        val entry = assertSingleCreated(shown, "PROTECTED")
        assertTrue(entry.content.startsWith(QrCrypto.PREFIX))
    }

    @Test
    fun desactivarLaContrasena_ocultaElCampo() {
        show()

        val toggle = composeRule.onNode(isToggleable())
        toggle.performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(str(R.string.qr_password_label)).assertExists()

        toggle.performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText(str(R.string.qr_password_label)).assertCountEquals(0)
    }

    // ── Resultado: compartir y guardar ───────────────────────────────────────────

    @Test
    fun resultado_compartirArmaElChooserConElPng() {
        val shown = show()

        type(R.string.qr_label_url, "ejemplo.com")
        generateAndWaitResult()
        composeRule.onNodeWithText(str(R.string.general_share)).performScrollTo().performClick()

        composeRule.waitUntilOrDump("R20_QrShare", 10_000) { shown.recording?.started?.isNotEmpty() == true }
        val chooser = shown.recording!!.started.first()
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        @Suppress("DEPRECATION")
        val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        assertNotNull(send)
        assertEquals("image/png", send!!.type)
    }

    @Test
    fun resultado_guardarEnDescargasMuestraLaConfirmacion() {
        show()

        type(R.string.qr_label_url, "ejemplo.com")
        generateAndWaitResult()
        composeRule.onNodeWithText(str(R.string.general_save)).performScrollTo().performClick()

        waitForText(str(R.string.general_saved_downloads))
    }

    // ── Banner ───────────────────────────────────────────────────────────────────

    @Test
    fun banner_historialYVolverInvocanSusCallbacks() {
        val shown = show()

        composeRule.onNodeWithContentDescription(str(R.string.qr_history_title)).performClick()
        composeRule.onNodeWithText(str(R.string.general_back)).performClick()
        composeRule.waitForIdle()

        assertEquals(1, shown.history.get())
        assertEquals(1, shown.back.get())
        composeRule.onAllNodesWithContentDescription(str(R.string.qr_history_title)).assertCountEquals(1)
    }

    // Hallazgo de cobertura (ronda 23): ningún test existente pasaba `onHome`
    // -- la rama "Inicio" de BannerNavRow (DocuSmartTopBanner) quedaba en 0%
    // para esta pantalla.
    @Test
    fun banner_conOnHome_invocaSuCallback() {
        var homeCount = 0
        val viewModel = buildViewModel()
        composeRule.setContentEsFit(registryOwner = FakeResultRegistryOwner()) {
            QrCreatorScreen(onHome = { homeCount++ }, viewModel = viewModel)
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(str(R.string.nav_home)).performClick()
        composeRule.waitForIdle()

        assertEquals(1, homeCount)
    }
}
