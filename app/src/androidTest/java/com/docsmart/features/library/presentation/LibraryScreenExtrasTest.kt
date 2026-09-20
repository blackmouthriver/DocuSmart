package com.docsmart.features.library.presentation

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.docsmart.R
import com.docsmart.core.ads.AdManager
import com.docsmart.core.data.FavoritesRepository
import com.docsmart.core.ui.components.DocumentType
import com.docsmart.core.ui.components.DocumentUiModel
import com.docsmart.core.ui.test.forceLocale
import com.docsmart.core.ui.test.testViewportDensity
import com.docsmart.core.ui.test.waitUntilOrDump
import com.docsmart.features.library.data.DocumentRepository
import com.docsmart.features.library.data.DownloadsAccessManager
import com.docsmart.features.library.data.TrashRepository
import com.docsmart.features.library.data.TrashedDocumentUiModel
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Biblioteca: papelera, carpeta vinculada por SAF (atajo y tarjeta de vincular) y descripción de pestaña. El
 * atajo a la carpeta usa contextos que registran o rechazan el Intent (nunca se abre un explorador real).
 */
class LibraryScreenExtrasTest {
    @get:Rule
    val permissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(
            *if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
            } else {
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            },
        )

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val target: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val strings: Context get() = forceLocale(target, "es-ES")
    private val folderUri = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3ADocs")

    private fun text(id: Int): String = strings.getString(id)

    private fun trashed(name: String) =
        TrashedDocumentUiModel(
            DocumentUiModel("/app/$name", name, DocumentType.PDF, "1 KB", "Hoy"),
            deletedAt = 1L,
        )

    private fun buildViewModel(
        linkedFolder: Uri? = null,
        trashCount: Int = 0,
    ): LibraryViewModel {
        val adManager = mockk<AdManager>(relaxed = true)
        every { adManager.isPremium } returns MutableStateFlow(true)
        every { adManager.isInitialized } returns MutableStateFlow(false)
        val repository = mockk<DocumentRepository>(relaxed = true)
        coEvery { repository.loadAllDocuments() } returns
            listOf(DocumentUiModel("/app/a.pdf", "A.pdf", DocumentType.PDF, "1 KB", "Hoy"))
        val trashRepository = mockk<TrashRepository>(relaxed = true)
        coEvery { trashRepository.loadTrashedDocuments() } returns (1..trashCount).map { trashed("t$it.pdf") }
        val downloadsAccessManager = mockk<DownloadsAccessManager>(relaxed = true)
        every { downloadsAccessManager.linkedFolderUri } returns MutableStateFlow(linkedFolder)
        every { downloadsAccessManager.folderDisplayName(any()) } returns "MisDocs"
        return LibraryViewModel(
            adManager = adManager,
            repository = repository,
            trashRepository = trashRepository,
            favoritesRepository = mockk<FavoritesRepository>(relaxed = true),
            downloadsAccessManager = downloadsAccessManager,
            soundEffectPlayer = mockk(relaxed = true),
            context = target,
        )
    }

    private fun setScreen(
        override: Context? = null,
        content: @Composable () -> Unit,
    ) {
        composeRule.setContent {
            val base = LocalContext.current
            val localized = remember(base) { override ?: forceLocale(base, "es-ES") }
            CompositionLocalProvider(
                LocalContext provides localized,
                LocalResources provides localized.resources,
                LocalDensity provides testViewportDensity(),
                LocalActivityResultRegistryOwner provides composeRule.activity,
                LocalOnBackPressedDispatcherOwner provides composeRule.activity,
            ) { content() }
        }
    }

    private fun waitForText(value: String) {
        composeRule.waitUntilOrDump("CI_HANG_LibraryScreenExtrasTest") {
            composeRule.onAllNodesWithText(value).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private class RecordingContext(
        base: Context,
    ) : ContextWrapper(base) {
        val intents = mutableListOf<Intent>()

        override fun startActivity(intent: Intent) {
            intents += intent
        }
    }

    private class NoAppContext(
        base: Context,
    ) : ContextWrapper(base) {
        var attempts = 0

        override fun startActivity(intent: Intent) {
            attempts++
            throw ActivityNotFoundException("sin explorador")
        }
    }

    @Test
    fun laPapeleraMuestraSuContadorYAlTocarlaNavega() {
        var trashClicks = 0
        val viewModel = buildViewModel(trashCount = 3)

        setScreen { LibraryScreen(onTrashClick = { trashClicks++ }, viewModel = viewModel) }
        waitForText(text(R.string.library_trash))

        composeRule.onNodeWithText(text(R.string.library_trash)).performClick()

        assertEquals(1, trashClicks)
        assertEquals(3, viewModel.uiState.value.trashCount)
    }

    @Test
    fun laDescripcionDeLaPestanaCambiaConLaSeleccion() {
        val viewModel = buildViewModel()

        setScreen { LibraryScreen(viewModel = viewModel) }
        waitForText(text(R.string.library_tab_device_description))

        composeRule.onNodeWithText(text(R.string.library_tab_app_files)).performClick()

        waitForText(text(R.string.library_tab_app_files_description))
        composeRule.onAllNodesWithText(text(R.string.library_tab_device_description)).assertCountEquals(0)
    }

    @Test
    fun sinCarpetaVinculadaSeOfreceVincularlaSoloEnLaPestanaDispositivo() {
        val viewModel = buildViewModel(linkedFolder = null)

        setScreen { LibraryScreen(viewModel = viewModel) }
        waitForText(text(R.string.library_link_downloads_title))

        composeRule.onNodeWithText(text(R.string.library_tab_app_files)).performClick()

        composeRule.waitUntilOrDump("CI_HANG_LibraryScreenExtrasTest") {
            composeRule.onAllNodesWithText(text(R.string.library_link_downloads_title)).fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun conCarpetaVinculadaSeMuestraElAtajoConSuNombreYSeAbreConUnIntentView() {
        val recording = RecordingContext(strings)
        val viewModel = buildViewModel(linkedFolder = folderUri)

        setScreen(override = recording) { LibraryScreen(viewModel = viewModel) }
        waitForText("MisDocs")

        composeRule.onAllNodesWithText(text(R.string.library_link_downloads_title)).assertCountEquals(0)
        composeRule.onNodeWithText("MisDocs").performClick()

        assertEquals(1, recording.intents.size)
        val intent = recording.intents.single()
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(folderUri, intent.data)
    }

    @Test
    fun siNoHayExploradorDeArchivosElAtajoNoCierraLaApp() {
        val noApp = NoAppContext(strings)
        val viewModel = buildViewModel(linkedFolder = folderUri)

        setScreen(override = noApp) { LibraryScreen(viewModel = viewModel) }
        waitForText("MisDocs")

        composeRule.onNodeWithText("MisDocs").performClick()
        composeRule.waitForIdle()

        assertTrue(noApp.attempts >= 1)
        composeRule.onNodeWithText("MisDocs").assertExists()
    }
}
