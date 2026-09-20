package com.docsmart.core.navegation

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.test.platform.app.InstrumentationRegistry
import com.docsmart.core.ui.components.DocumentType
import com.docsmart.core.ui.components.DocumentUiModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Ronda 20: atajos de navegación del grafo (Convertir, Crear QR, OCR, Firmar,
 * Carpeta Segura) sobre un NavHost real con destinos vacíos -- sin ViewModels
 * ni Hilt -- y `safeShareableUriOrNull` con el FileProvider real de la app.
 */
class NavGraphHelpersTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var controller: NavHostController

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun stringArgs(vararg names: String) =
        names.map {
            navArgument(it) {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            }
        }

    @Composable
    private fun TestGraph() {
        controller = rememberNavController()
        NavHost(navController = controller, startDestination = NavRoutes.Home.route) {
            composable(NavRoutes.Home.route) {}
            composable(
                NavRoutes.Converter.route,
                arguments = stringArgs("initialType", "initialFileUri", "initialFileCategory"),
            ) {}
            composable(
                NavRoutes.QrCreator.route,
                arguments = stringArgs("initialFileUri", "initialFileType", "initialFileName"),
            ) {}
            composable(NavRoutes.PdfTools.route, arguments = stringArgs("initialTool", "initialFileUri")) {}
            composable(NavRoutes.SecureFolder.route, arguments = stringArgs("pendingFileUri")) {}
        }
    }

    private fun start() {
        composeRule.setContent { TestGraph() }
        composeRule.waitForIdle()
    }

    private fun navigateWith(block: NavHostController.() -> Unit) {
        composeRule.runOnUiThread { controller.block() }
        composeRule.waitForIdle()
    }

    private fun arg(name: String): String? = controller.currentBackStackEntry?.arguments?.getString(name)

    private fun doc(
        id: String,
        type: DocumentType,
        name: String = "Doc",
    ) = DocumentUiModel(id = id, name = name, type = type, size = "1 KB", date = "Hoy")

    @Test
    fun convertir_pdfLocal_llevaLaUriFileYLaCategoria() {
        start()
        navigateWith { navigateToConvert(doc("/data/x/contrato.pdf", DocumentType.PDF)) }

        assertEquals(NavRoutes.Converter.route, controller.currentDestination?.route)
        assertEquals("file:///data/x/contrato.pdf", arg("initialFileUri"))
        assertEquals("PDF", arg("initialFileCategory"))
    }

    @Test
    fun convertir_contentUriDeImagen_conservaLaUriYUsaCategoriaImagen() {
        start()
        navigateWith { navigateToConvert(doc("content://media/external/images/media/7", DocumentType.IMAGE)) }

        assertEquals("content://media/external/images/media/7", arg("initialFileUri"))
        assertEquals("Imagen", arg("initialFileCategory"))
    }

    @Test
    fun convertir_tipoSinConversion_noLlevaCategoria() {
        start()
        navigateWith { navigateToConvert(doc("/data/x/notas.txt", DocumentType.TEXT)) }

        assertEquals(NavRoutes.Converter.route, controller.currentDestination?.route)
        assertNull(arg("initialFileCategory"))
    }

    @Test
    fun crearQr_contentUri_navegaConTipoDocumentoONombre() {
        start()
        navigateWith {
            navigateToQrCreator(context, doc("content://docs/1", DocumentType.PDF, name = "Contrato.pdf"))
        }

        assertEquals(NavRoutes.QrCreator.route, controller.currentDestination?.route)
        assertEquals("content://docs/1", arg("initialFileUri"))
        assertEquals("document", arg("initialFileType"))
        assertEquals("Contrato.pdf", arg("initialFileName"))
    }

    @Test
    fun crearQr_imagenLocalEnCarpetaCompartible_usaElFileProvider() {
        start()
        val path = File(context.filesDir, "converted/r20_no_existe.png").absolutePath
        navigateWith { navigateToQrCreator(context, doc(path, DocumentType.IMAGE, name = "Foto.png")) }

        assertEquals(NavRoutes.QrCreator.route, controller.currentDestination?.route)
        assertEquals("image", arg("initialFileType"))
        val uri = arg("initialFileUri")
        assertNotNull(uri)
        assertTrue(uri!!.startsWith("content://${context.packageName}.fileprovider/"))
    }

    @Test
    fun crearQr_archivoDeCarpetaSegura_noNavegaYAvisa() {
        start()
        val path = File(context.filesDir, "secure/r20_no_existe.pdf").absolutePath
        navigateWith { navigateToQrCreator(context, doc(path, DocumentType.PDF)) }

        assertEquals(NavRoutes.Home.route, controller.currentDestination?.route)
    }

    @Test
    fun safeShareableUri_distingueContentCompartibleYNoCompartible() {
        assertEquals(
            "content://docs/9",
            safeShareableUriOrNull(context, doc("content://docs/9", DocumentType.PDF)).toString(),
        )
        assertNull(safeShareableUriOrNull(context, doc("/data/local/tmp/r20.pdf", DocumentType.PDF)))
    }

    @Test
    fun ocrYFirmar_abrenHerramientasPdfConLaHerramientaCorrecta() {
        start()
        navigateWith { navigateToOcr(doc("/data/x/a.pdf", DocumentType.PDF)) }
        assertEquals(NavRoutes.PdfTools.route, controller.currentDestination?.route)
        assertEquals("OCR", arg("initialTool"))
        assertEquals("file:///data/x/a.pdf", arg("initialFileUri"))

        navigateWith { navigateToSign(doc("/data/x/b.pdf", DocumentType.PDF)) }
        assertEquals("SIGN", arg("initialTool"))
        assertEquals("file:///data/x/b.pdf", arg("initialFileUri"))
    }

    @Test
    fun moverACarpetaSegura_pasaElArchivoPendiente() {
        start()
        navigateWith { navigateToSecureFolder(doc("/data/x/c.pdf", DocumentType.PDF)) }

        assertEquals(NavRoutes.SecureFolder.route, controller.currentDestination?.route)
        assertEquals("file:///data/x/c.pdf", arg("pendingFileUri"))
    }
}
