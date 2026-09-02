package com.docsmart.features.library.presentation

import android.Manifest
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.rule.GrantPermissionRule
import com.docsmart.core.ads.AdManager
import com.docsmart.core.data.FavoritesRepository
import com.docsmart.core.ui.components.DocumentType
import com.docsmart.core.ui.components.DocumentUiModel
import com.docsmart.core.ui.test.forceLocale
import com.docsmart.features.library.data.DocumentRepository
import com.docsmart.features.library.data.TrashRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test

/**
 * Flujo de prioridad Alta #8 de RF-QA-01 (ver compose-ui-testing.md):
 * Biblioteca -- cambiar pestaña Dispositivo/Mis archivos, filtrar por tipo,
 * buscar.
 *
 * `LibraryScreen` verifica un permiso real de almacenamiento con
 * `ContextCompat.checkSelfPermission()` antes de cargar nada (no es algo
 * mockeable desde el ViewModel) -- se usa `GrantPermissionRule` para
 * concederlo antes de que la Activity componga, en vez de conducir el
 * diálogo real del sistema (que Compose UI Testing no puede tocar).
 */
class LibraryScreenTest {

    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        *if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        else
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    )

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    // "Dispositivo" según isDeviceDocument() en LibraryViewModel: content://
    // de MediaStore. "Mis archivos" es cualquier otro id (ruta de archivo
    // propia de la app).
    private fun deviceDoc(name: String, type: DocumentType = DocumentType.IMAGE) = DocumentUiModel(
        id   = "content://media/external/images/media/${name.hashCode()}",
        name = name,
        type = type,
        size = "1.0 MB",
        date = "Hoy"
    )

    private fun appDoc(name: String, type: DocumentType = DocumentType.PDF) = DocumentUiModel(
        id   = "/data/data/com.docsmart/files/converted/$name",
        name = name,
        type = type,
        size = "1.0 MB",
        date = "Hoy"
    )

    private fun buildViewModel(documents: List<DocumentUiModel>): LibraryViewModel {
        val adManager = mockk<AdManager>(relaxed = true)
        every { adManager.isPremium } returns MutableStateFlow(true)
        every { adManager.isInitialized } returns MutableStateFlow(false)

        val repository = mockk<DocumentRepository>(relaxed = true)
        coEvery { repository.loadAllDocuments() } returns documents

        val trashRepository = mockk<TrashRepository>(relaxed = true)
        coEvery { trashRepository.loadTrashedDocuments() } returns emptyList()

        return LibraryViewModel(
            adManager           = adManager,
            repository          = repository,
            trashRepository     = trashRepository,
            favoritesRepository = mockk<FavoritesRepository>(relaxed = true)
        )
    }

    private fun setContentWithLocale(content: @Composable () -> Unit) {
        composeRule.setContent {
            // Fuerza español -- "Dispositivo"/"Mis archivos" vienen de
            // stringResource(), y el emulador de CI arranca en inglés por
            // defecto (ver com.docsmart.core.ui.test.forceLocale).
            val baseContext = LocalContext.current
            val localizedContext = remember(baseContext) { forceLocale(baseContext, "es-ES") }
            // LibraryScreen usa rememberLauncherForActivityResult() para el
            // permiso de almacenamiento -- mismo motivo que
            // ConverterScreenTest/SecurityScreenTest para re-proveer estos
            // dos apuntando a la Activity real.
            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalActivityResultRegistryOwner provides composeRule.activity,
                LocalOnBackPressedDispatcherOwner provides composeRule.activity
            ) { content() }
        }
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = 60_000) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun cambiarPestana_muestraSoloDocumentosDeEsaPestana() {
        val viewModel = buildViewModel(
            listOf(
                deviceDoc("FotoDispositivo.jpg"),
                appDoc("ArchivoApp.pdf")
            )
        )

        setContentWithLocale { LibraryScreen(viewModel = viewModel) }
        waitForText("FotoDispositivo.jpg")

        // Pestaña por defecto: Dispositivo -- solo se ve el doc de MediaStore.
        composeRule.onNodeWithText("FotoDispositivo.jpg").assertIsDisplayed()
        composeRule.onAllNodesWithText("ArchivoApp.pdf").assertCountEquals(0)

        composeRule.onNodeWithText("Mis archivos").performClick()
        waitForText("ArchivoApp.pdf")

        composeRule.onNodeWithText("ArchivoApp.pdf").assertIsDisplayed()
        composeRule.onAllNodesWithText("FotoDispositivo.jpg").assertCountEquals(0)
    }

    @Test
    fun filtrarPorCategoria_muestraSoloDocumentosDeEseTipo() {
        val viewModel = buildViewModel(
            listOf(
                deviceDoc("Foto.jpg", DocumentType.IMAGE),
                deviceDoc("Informe.docx", DocumentType.WORD)
            )
        )

        setContentWithLocale { LibraryScreen(viewModel = viewModel) }
        waitForText("Foto.jpg")

        // Se filtra por Imagen a propósito: el chip de categoría dice
        // "Imágenes" (plural, CategoryFilter.kt) y el badge de tipo del
        // documento dice "Imagen" (singular, DocumentType.IMAGE.label) --
        // textos distintos, sin ambigüedad para onNodeWithText(). Otras
        // categorías (PDF/Word/Excel/ZIP) comparten el mismo texto entre
        // chip y badge y sí serían ambiguas acá.
        composeRule.onNodeWithText("Imágenes").performClick()
        waitForText("Foto.jpg")

        composeRule.onNodeWithText("Foto.jpg").assertIsDisplayed()
        composeRule.onAllNodesWithText("Informe.docx").assertCountEquals(0)
    }

    @Test
    fun buscar_filtraDocumentosPorNombre() {
        val viewModel = buildViewModel(
            listOf(
                deviceDoc("Reporte_Enero.pdf", DocumentType.PDF),
                deviceDoc("Foto_Playa.jpg", DocumentType.IMAGE)
            )
        )

        setContentWithLocale { LibraryScreen(viewModel = viewModel) }
        waitForText("Reporte_Enero.pdf")

        composeRule.onNodeWithText("Buscar en biblioteca…").performTextInput("Reporte")
        waitForText("Reporte_Enero.pdf")

        composeRule.onNodeWithText("Reporte_Enero.pdf").assertIsDisplayed()
        composeRule.onAllNodesWithText("Foto_Playa.jpg").assertCountEquals(0)
    }
}
