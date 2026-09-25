package com.docsmart.features.study.presentation

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.core.content.IntentCompat
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.docsmart.R
import com.docsmart.core.data.db.NoteImageEntity
import com.docsmart.features.agenda.presentation.components.esString
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Vista de Notas: crear (título, texto, recordatorio, imagen, dictado), listar, editar, borrar,
 * eliminar todas, resaltados de Lectura, notificación de recordatorio abierta por id y exportar.
 * El repositorio es en memoria; selectores, dictado y chooser son grabadores (StudyTestSupport).
 */
class StudyNotesTest {
    @get:Rule(order = 0)
    val permissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(
            *if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                arrayOf(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                emptyArray()
            },
        )

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val env by lazy { StudyTestEnv(composeRule) }
    private val tempFiles = mutableListOf<File>()
    private var exportsBefore: Set<String> = emptySet()

    private fun exportsDir(): File = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "study_exports")

    @Before
    fun snapshotExports() {
        exportsBefore = exportsDir().list()?.toSet().orEmpty()
    }

    @After
    fun cleanUp() {
        tempFiles.forEach { it.delete() }
        // Solo los archivos que generó esta prueba (la carpeta real puede tener exportaciones del usuario).
        exportsDir().listFiles()?.filter { it.name !in exportsBefore }?.forEach { it.delete() }
    }

    private fun setNotes(
        fake: FakeNotes,
        openNoteId: String? = null,
    ) {
        env.setContent {
            StudyScreen(
                initialTab = STUDY_TAB_NOTES,
                openNoteId = openNoteId,
                viewModel = buildStudyViewModel(),
                notesViewModel = fake.buildViewModel(),
            )
        }
        composeRule.waitForTextExists(esString(R.string.study_new_note))
        syncNotes(fake)
    }

    private fun scrollNotesTo(matcher: SemanticsMatcher) {
        composeRule.onNode(hasScrollToIndexAction()).performScrollToNode(matcher)
    }

    private fun exists(text: String) = composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    private fun chip(text: String) {
        // Algunos tests montan NoteReminderSection directo (sin el LazyColumn de
        // NotesTab alrededor): performScrollTo() ahí lanza "no parent layout with a
        // Scroll SemanticsAction". runCatching lo hace tolerante a ambos casos.
        val node = composeRule.onNodeWithText(text)
        runCatching { node.performScrollTo() }
        node.performClick()
    }

    private fun typeNote(
        title: String,
        text: String,
    ) {
        composeRule.onNodeWithText(esString(R.string.study_note_title_label)).performTextInput(title)
        composeRule.onNodeWithText(esString(R.string.study_note_content_placeholder)).performTextInput(text)
    }

    private val reminderPrefix: SemanticsMatcher
        get() = hasText(esString(R.string.study_note_reminder_scheduled_desc, ""), substring = true)

    // Espera a que el ViewModel ya tenga la lista actual del repositorio en memoria.
    private fun syncNotes(fake: FakeNotes) {
        composeRule.waitUntil(timeoutMillis = 10_000) { fake.viewModel.uiState.value.notes == fake.notes.value }
    }

    // ── Crear ─────────────────────────────────────────────────────────────────
    @Test
    fun crearNota_conRecordatorioPreestablecido_loGuardaConSuFecha() {
        val fake = FakeNotes()
        setNotes(fake)
        composeRule.onNodeWithText(esString(R.string.study_save_note)).assertIsNotEnabled()

        typeNote("Titulo A", "Contenido A")
        composeRule.onNodeWithText(esString(R.string.study_save_note)).assertIsEnabled()

        chip(esString(R.string.study_note_reminder_tomorrow))
        composeRule.onNode(reminderPrefix).assertExists()
        chip(esString(R.string.study_note_reminder_1_week))
        composeRule.onNode(reminderPrefix).assertExists()
        chip(esString(R.string.agenda_reminder_none))
        composeRule.onNode(reminderPrefix).assertDoesNotExist()
        chip(esString(R.string.study_note_reminder_3_days))

        composeRule.onNodeWithText(esString(R.string.study_save_note)).performClick()

        composeRule.waitUntil(timeoutMillis = 10_000) { fake.created.isNotEmpty() }
        val created = fake.created.single()
        assertEquals("Titulo A", created.title)
        assertEquals("Contenido A", created.text)
        val reminder = created.reminderAt
        assertNotNull(reminder)
        assertTrue(reminder!! > System.currentTimeMillis())
        // El editor queda limpio.
        composeRule.onNodeWithText(esString(R.string.study_save_note)).assertIsNotEnabled()
    }

    @Test
    fun crearNota_sinTitulo_usaElTituloPorDefecto() {
        val fake = FakeNotes()
        setNotes(fake)

        composeRule.onNodeWithText(esString(R.string.study_note_content_placeholder)).performTextInput("Solo texto")
        composeRule.onNodeWithText(esString(R.string.study_save_note)).performClick()

        composeRule.waitUntil(timeoutMillis = 10_000) { fake.created.isNotEmpty() }
        assertEquals(esString(R.string.study_untitled_note), fake.created.single().title)
    }

    @Test
    fun recordatorioPersonalizado_abreElDialogoYAvisaSiYaPaso() {
        setNotes(FakeNotes())
        composeRule.onNodeWithText(esString(R.string.study_note_content_placeholder)).performTextInput("Con fecha")

        chip(esString(R.string.study_note_reminder_custom))
        composeRule.waitForTextExists(esString(R.string.general_accept))
        // Cancelar cierra sin elegir.
        composeRule.onNodeWithText(esString(R.string.general_cancel)).performClick()
        composeRule.onNodeWithText(esString(R.string.general_accept)).assertDoesNotExist()
        composeRule.onNode(reminderPrefix).assertDoesNotExist()

        // Aceptar con la fecha precargada ("ahora") deja un recordatorio ya vencido.
        chip(esString(R.string.study_note_reminder_custom))
        composeRule.waitForTextExists(esString(R.string.general_accept))
        composeRule.onNodeWithText(esString(R.string.general_accept)).performClick()
        composeRule.waitForTextExists(esString(R.string.study_note_reminder_already_past))
        composeRule.onNode(reminderPrefix).assertExists()
    }

    @Test
    fun dictadoPorVoz_agregaElTextoDictado() {
        setNotes(FakeNotes())
        val mic = esString(R.string.study_dictate_note)

        composeRule.onNodeWithContentDescription(mic).performClick()
        val launched = env.registry.launchedInputs.last() as Intent
        assertEquals(RecognizerIntent.ACTION_RECOGNIZE_SPEECH, launched.action)
        composeRule.waitForTextExists(esString(R.string.study_listening))

        env.registry.respond(
            Activity.RESULT_OK,
            Intent().putStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS, arrayListOf("hola dictado")),
        )
        composeRule.waitForTextExists("hola dictado")
        composeRule.onNodeWithText(esString(R.string.study_listening)).assertDoesNotExist()

        // Un segundo dictado se agrega al final.
        composeRule.onNodeWithContentDescription(mic).performClick()
        env.registry.respond(
            Activity.RESULT_OK,
            Intent().putStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS, arrayListOf("segundo")),
        )
        composeRule.waitForTextExists("hola dictado segundo")

        // Cancelar el dictado no cambia el texto pero apaga el indicador.
        composeRule.onNodeWithContentDescription(mic).performClick()
        env.registry.respond(Activity.RESULT_CANCELED, null)
        composeRule.onNodeWithText(esString(R.string.study_listening)).assertDoesNotExist()
        composeRule.onNodeWithText("hola dictado segundo").assertExists()
    }

    @Test
    fun adjuntarImagen_muestraMiniaturaSePuedeQuitarYSeGuardaConLaNota() {
        val png = createStudyTestPng().also { tempFiles += it }
        val fake = FakeNotes()
        setNotes(fake)
        val imageDesc = esString(R.string.study_note_image_desc)

        composeRule.onNodeWithText(esString(R.string.study_note_attach_image)).performClick()
        assertEquals("image/*", env.registry.launchedInputs.last())
        env.registry.respond(Activity.RESULT_OK, Intent().setData(Uri.fromFile(png)))
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithContentDescription(imageDesc).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithContentDescription(esString(R.string.study_note_remove_image)).performClick()
        composeRule.onNodeWithContentDescription(imageDesc).assertDoesNotExist()

        composeRule.onNodeWithText(esString(R.string.study_note_attach_image)).performClick()
        env.registry.respond(Activity.RESULT_OK, Intent().setData(Uri.fromFile(png)))
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithContentDescription(imageDesc).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(esString(R.string.study_note_content_placeholder)).performTextInput("Con imagen")
        composeRule.onNodeWithText(esString(R.string.study_save_note)).performClick()

        composeRule.waitUntil(timeoutMillis = 10_000) { fake.created.isNotEmpty() }
        val savedUris = fake.created.single().imageUris.map { it.toString() }
        assertEquals(listOf(Uri.fromFile(png).toString()), savedUris)
    }

    // ── Lista: borrar, eliminar todas, abrir por id ───────────────────────────
    @Test
    fun listaDeNotas_borrarUnaActualizaElContador() {
        val fake =
            FakeNotes(listOf(testNote("n1", "Primera nota", "Contenido uno"), testNote("n2", "Segunda nota", "Dos")))
        setNotes(fake)

        scrollNotesTo(hasText("Primera nota"))
        composeRule.onNodeWithText("Primera nota").assertExists()
        composeRule.onNodeWithText("Contenido uno").assertExists()
        composeRule.onAllNodesWithContentDescription(esString(R.string.study_delete_note_desc))[0].performClick()

        composeRule.waitUntil(timeoutMillis = 10_000) { fake.notes.value.size == 1 }
        assertEquals(listOf("n2"), fake.notes.value.map { it.note.id })
        syncNotes(fake)
        scrollNotesTo(hasText(esString(R.string.study_saved_notes_count, 1)))
    }

    @Test
    fun eliminarTodas_cancelarConservaYConfirmarVacia() {
        val fake = FakeNotes(listOf(testNote("n1", "Primera nota", "Uno"), testNote("n2", "Segunda nota", "Dos")))
        setNotes(fake)
        scrollNotesTo(hasText(esString(R.string.study_saved_notes_count, 2)))

        composeRule.onNodeWithText(esString(R.string.study_delete_all)).performClick()
        composeRule.waitForTextExists(esString(R.string.study_delete_all_notes_title))
        composeRule.onNodeWithText(esString(R.string.general_cancel)).performClick()
        composeRule.onNodeWithText(esString(R.string.study_delete_all_notes_title)).assertDoesNotExist()
        assertEquals(2, fake.notes.value.size)

        composeRule.onNodeWithText(esString(R.string.study_delete_all)).performClick()
        composeRule.waitForTextExists(esString(R.string.study_delete_all_notes_title))
        composeRule.onNodeWithText(esString(R.string.general_delete)).performClick()

        composeRule.waitForTextExists(esString(R.string.study_no_notes_yet))
        assertTrue(fake.notes.value.isEmpty())
    }

    @Test
    fun abrirNotaPorId_desplazaHastaLaNotaDelRecordatorio() {
        val notes = (1..4).map { testNote("n$it", "Nota numero $it", "Texto $it") }
        setNotes(FakeNotes(notes), openNoteId = "n4")

        composeRule.waitForTextExists("Nota numero 4")
        composeRule.onNodeWithText("Nota numero 4").assertExists()
    }

    // ── Editar ────────────────────────────────────────────────────────────────
    @Test
    fun editarNota_validaTextoVacioGuardaYCancela() {
        val reminder = System.currentTimeMillis() + 2 * 24 * 60 * 60 * 1000L
        val fake = FakeNotes(listOf(testNote("n1", "Titulo original", "Texto original", reminderAt = reminder)))
        setNotes(fake)
        scrollNotesTo(hasText("Titulo original"))
        val editDesc = esString(R.string.study_edit_note_desc)

        composeRule.onNodeWithContentDescription(editDesc).performClick()
        composeRule.waitForTextExists(esString(R.string.study_edit_note))
        // El recordatorio guardado aparece como "Personalizada" con su fecha.
        composeRule.onNode(reminderPrefix).assertExists()

        val titleField = hasSetTextAction() and hasText("Titulo original")
        val bodyField = hasSetTextAction() and hasText("Texto original")
        // Un texto en blanco deshabilita "Guardar".
        composeRule.onNode(bodyField).performTextReplacement(" ")
        composeRule.onNodeWithText(esString(R.string.general_save)).performScrollTo().assertIsNotEnabled()

        composeRule.onNode(hasSetTextAction() and hasText(" ")).performTextReplacement("Texto editado")
        composeRule.onNode(titleField).performTextReplacement("Titulo editado")
        composeRule.onNodeWithText(esString(R.string.general_save)).performScrollTo().performClick()

        composeRule.waitUntil(timeoutMillis = 10_000) { fake.updated.isNotEmpty() }
        val updated = fake.updated.single()
        assertEquals("n1", updated.noteId)
        assertEquals("Titulo editado", updated.title)
        assertEquals("Texto editado", updated.text)
        assertEquals(reminder, updated.reminderAt)
        composeRule.waitUntil(timeoutMillis = 10_000) { !exists(esString(R.string.study_edit_note)) }
        syncNotes(fake)
        scrollNotesTo(hasText("Titulo editado"))
        composeRule.onNodeWithText("Texto editado").assertExists()

        // Cancelar cierra el editor sin guardar nada más.
        composeRule.onNodeWithContentDescription(editDesc).performClick()
        composeRule.waitForTextExists(esString(R.string.study_edit_note))
        composeRule.onNodeWithText(esString(R.string.general_cancel)).performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) { !exists(esString(R.string.study_edit_note)) }
        assertEquals(1, fake.updated.size)
    }

    // ── NotesTab directo: resaltados de Lectura y borrador ────────────────────
    @Test
    fun parrafosResaltados_seListanYElBorradorSeReportaAlEditar() {
        val changes = mutableListOf<String>()
        val fake = FakeNotes()
        env.setContent {
            NotesTab(
                notes = "Borrador previo",
                onNotesChange = { changes += it },
                highlights = setOf(0, 2, 9),
                documentText = listOf("Parrafo cero resaltado", "Parrafo uno", "Parrafo dos resaltado"),
                viewModel = fake.buildViewModel(),
            )
        }

        composeRule.waitForTextExists(esString(R.string.study_highlighted_paragraphs_count, 3))
        composeRule.onNodeWithText("Parrafo cero resaltado").assertExists()
        composeRule.onNodeWithText("Parrafo dos resaltado").assertExists()
        // El índice 1 no está resaltado y el 9 no existe en el documento.
        composeRule.onNodeWithText("Parrafo uno").assertDoesNotExist()

        composeRule.onNode(hasSetTextAction() and hasText("Borrador previo")).performTextReplacement("Borrador nuevo")
        assertEquals("Borrador nuevo", changes.last())
    }

    // ── NoteReminderSection directo ───────────────────────────────────────────
    @Test
    fun seccionDeRecordatorio_sinPermisoPideActivarNotificaciones() {
        var granted by mutableStateOf(false)
        var selectedChip by mutableStateOf(NoteReminderChip.NONE)
        var reminderMillis by mutableStateOf<Long?>(null)
        var requested = 0
        env.setContent {
            NoteReminderSection(
                reminderChip = selectedChip,
                reminderAt = reminderMillis,
                onReminderChange = { newChip, millis ->
                    selectedChip = newChip
                    reminderMillis = millis
                },
                notificationsGranted = granted,
                onRequestNotifications = { requested++ },
            )
        }

        composeRule.onNodeWithText(esString(R.string.study_note_reminder_notifications_disabled)).assertExists()
        composeRule.onNodeWithText(esString(R.string.study_note_reminder_tomorrow)).assertDoesNotExist()
        composeRule.onNodeWithText(esString(R.string.study_note_reminder_enable_notifications)).performClick()
        assertEquals(1, requested)

        composeRule.runOnIdle { granted = true }
        chip(esString(R.string.study_note_reminder_tomorrow))
        composeRule.runOnIdle {
            assertEquals(NoteReminderChip.TOMORROW, selectedChip)
            assertNotNull(reminderMillis)
        }
        composeRule.onNode(reminderPrefix).assertExists()

        chip(esString(R.string.study_note_reminder_custom))
        composeRule.waitForTextExists(esString(R.string.general_accept))
        composeRule.onNodeWithText(esString(R.string.general_accept)).performClick()
        composeRule.waitForTextExists(esString(R.string.study_note_reminder_already_past))
        composeRule.runOnIdle { assertEquals(NoteReminderChip.CUSTOM, selectedChip) }

        // Una fecha futura ya no muestra el aviso de "pasado".
        composeRule.runOnIdle { reminderMillis = System.currentTimeMillis() + 24 * 60 * 60 * 1000L }
        composeRule.onNodeWithText(esString(R.string.study_note_reminder_already_past)).assertDoesNotExist()

        chip(esString(R.string.agenda_reminder_none))
        composeRule.runOnIdle {
            assertEquals(NoteReminderChip.NONE, selectedChip)
            assertNull(reminderMillis)
        }
    }

    // ── NoteEditDialog directo (imágenes) ─────────────────────────────────────
    @Test
    fun dialogoDeEdicion_quitaYAgregaImagenesYUsaTituloPorDefecto() {
        val original = createStudyTestPng().also { tempFiles += it }
        val added = createStudyTestPng().also { tempFiles += it }
        val image = NoteImageEntity(id = 1, noteId = "n1", filePath = original.absolutePath, position = 0)
        val note = testNote("n1", "Con imagen", "Texto base", images = listOf(image))
        var dismissed = 0
        var saved: List<Any?>? = null
        env.setContent {
            NoteEditDialog(
                noteWithImages = note,
                notificationsGranted = true,
                onRequestNotifications = {},
                onDismiss = { dismissed++ },
                onSave = { title, text, reminderAt, kept, removed, newUris ->
                    saved = listOf(title, text, reminderAt, kept, removed, newUris)
                },
            )
        }
        val imageDesc = esString(R.string.study_note_image_desc)
        composeRule.waitForTextExists(esString(R.string.study_edit_note))
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithContentDescription(imageDesc).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithContentDescription(esString(R.string.study_note_remove_image)).performClick()
        composeRule.onNodeWithContentDescription(imageDesc).assertDoesNotExist()

        composeRule.onNodeWithText(esString(R.string.study_note_attach_image)).performScrollTo().performClick()
        assertEquals("image/*", env.registry.launchedInputs.last())
        env.registry.respond(Activity.RESULT_OK, Intent().setData(Uri.fromFile(added)))
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithContentDescription(imageDesc).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNode(hasSetTextAction() and hasText("Con imagen")).performTextReplacement("")
        composeRule.onNodeWithText(esString(R.string.general_save)).performScrollTo().performClick()

        val args = saved
        assertNotNull(args)
        assertEquals(esString(R.string.study_untitled_note), args!![0])
        assertEquals("Texto base", args[1])
        assertNull(args[2])
        assertEquals(0, (args[3] as List<*>).size)
        assertEquals(listOf(image), args[4])
        assertEquals(listOf(Uri.fromFile(added)), args[5])

        composeRule.onNodeWithText(esString(R.string.general_cancel)).performScrollTo().performClick()
        assertEquals(1, dismissed)
    }

    // ── Exportar ──────────────────────────────────────────────────────────────
    private fun waitExportButtonIdle(description: String) {
        composeRule.waitUntil(timeoutMillis = 15_000) {
            runCatching { composeRule.onAllNodesWithContentDescription(description)[0].assertIsEnabled() }.isSuccess
        }
    }

    private fun assertChooserFor(
        index: Int,
        mimeType: String,
    ) {
        val chooser = env.startedIntents[index]
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        val send = IntentCompat.getParcelableExtra(chooser, Intent.EXTRA_INTENT, Intent::class.java)
        assertNotNull(send)
        assertEquals(mimeType, send!!.type)
    }

    @Test
    fun exportarTodas_comoTextoYComoPdf_lanzaElChooser() {
        val fake = FakeNotes(listOf(testNote("n1", "Primera nota", "Uno"), testNote("n2", "Segunda nota", "Dos")))
        setNotes(fake)
        scrollNotesTo(hasText(esString(R.string.study_saved_notes_count, 2)))
        val exportDesc = esString(R.string.study_export_notes)

        composeRule.onNodeWithContentDescription(exportDesc).performClick()
        composeRule.onNodeWithText(esString(R.string.study_export_as_text)).performClick()
        composeRule.waitUntil(timeoutMillis = 20_000) { env.startedIntents.size >= 1 }
        assertChooserFor(0, "text/plain")

        waitExportButtonIdle(exportDesc)
        composeRule.onNodeWithContentDescription(exportDesc).performClick()
        composeRule.onNodeWithText(esString(R.string.study_export_as_pdf)).performClick()
        composeRule.waitUntil(timeoutMillis = 20_000) { env.startedIntents.size >= 2 }
        assertChooserFor(1, "application/pdf")
    }

    @Test
    fun exportarUnaNota_comoPdfYComoWord_lanzaElChooser() {
        setNotes(FakeNotes(listOf(testNote("n1", "Unica nota", "Contenido"))))
        scrollNotesTo(hasText("Unica nota"))
        val exportDesc = esString(R.string.note_export_note_desc)

        composeRule.onNodeWithContentDescription(exportDesc).performClick()
        composeRule.onNodeWithText(esString(R.string.study_export_as_pdf)).performClick()
        composeRule.waitUntil(timeoutMillis = 20_000) { env.startedIntents.size >= 1 }
        assertChooserFor(0, "application/pdf")

        waitExportButtonIdle(exportDesc)
        composeRule.onNodeWithContentDescription(exportDesc).performClick()
        composeRule.onNodeWithText(esString(R.string.note_export_as_word)).performClick()
        composeRule.waitUntil(timeoutMillis = 40_000) { env.startedIntents.size >= 2 }
        assertChooserFor(1, "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
    }
}
