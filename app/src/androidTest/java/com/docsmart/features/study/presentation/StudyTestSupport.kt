package com.docsmart.features.study.presentation

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.core.app.ActivityOptionsCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.docsmart.core.ads.AdManager
import com.docsmart.core.data.db.NoteEntity
import com.docsmart.core.data.db.NoteWithImages
import com.docsmart.core.ui.test.forceLocale
import com.docsmart.core.ui.test.testViewportDensity
import com.docsmart.core.ui.test.waitUntilOrDump
import com.docsmart.features.study.data.NoteRepository
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.AreaBreak
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.properties.AreaBreakType
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

// Utilidades compartidas por las pruebas instrumentadas de Modo Estudio: prefs en memoria,
// contexto que no lanza actividades reales, registro de resultados grabador (evita selectores,
// dictado y chooser reales), repositorio de notas en memoria y generadores de PDF/PNG.

internal const val STUDY_TEST_TAG = "StudyTest"

/** SharedPreferences en memoria (soporta todos los tipos, remove y clear). */
internal class InMemorySharedPreferences : SharedPreferences {
    private val store = HashMap<String, Any?>()

    private fun raw(key: String?): Any? = if (key == null) null else store[key]

    @Synchronized
    override fun getAll(): MutableMap<String, *> = HashMap(store)

    @Synchronized
    override fun getString(
        key: String?,
        defValue: String?,
    ): String? = raw(key) as? String ?: defValue

    @Synchronized
    override fun getStringSet(
        key: String?,
        defValues: MutableSet<String>?,
    ): MutableSet<String>? = (raw(key) as? Set<*>)?.filterIsInstance<String>()?.toMutableSet() ?: defValues

    @Synchronized
    override fun getInt(
        key: String?,
        defValue: Int,
    ): Int = raw(key) as? Int ?: defValue

    @Synchronized
    override fun getLong(
        key: String?,
        defValue: Long,
    ): Long = raw(key) as? Long ?: defValue

    @Synchronized
    override fun getFloat(
        key: String?,
        defValue: Float,
    ): Float = raw(key) as? Float ?: defValue

    @Synchronized
    override fun getBoolean(
        key: String?,
        defValue: Boolean,
    ): Boolean = raw(key) as? Boolean ?: defValue

    @Synchronized
    override fun contains(key: String?): Boolean = key != null && store.containsKey(key)

    override fun edit(): SharedPreferences.Editor = PrefsEditor()

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    private inner class PrefsEditor : SharedPreferences.Editor {
        private val puts = HashMap<String, Any?>()
        private val removals = HashSet<String>()
        private var clearAll = false

        private fun put(
            key: String?,
            value: Any?,
        ): SharedPreferences.Editor {
            if (key != null) puts[key] = value
            return this
        }

        override fun putString(
            key: String?,
            value: String?,
        ): SharedPreferences.Editor = put(key, value)

        override fun putStringSet(
            key: String?,
            values: MutableSet<String>?,
        ): SharedPreferences.Editor = put(key, values?.toSet())

        override fun putInt(
            key: String?,
            value: Int,
        ): SharedPreferences.Editor = put(key, value)

        override fun putLong(
            key: String?,
            value: Long,
        ): SharedPreferences.Editor = put(key, value)

        override fun putFloat(
            key: String?,
            value: Float,
        ): SharedPreferences.Editor = put(key, value)

        override fun putBoolean(
            key: String?,
            value: Boolean,
        ): SharedPreferences.Editor = put(key, value)

        override fun remove(key: String?): SharedPreferences.Editor {
            if (key != null) removals += key
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            clearAll = true
            return this
        }

        override fun commit(): Boolean {
            flush()
            return true
        }

        override fun apply() {
            flush()
        }

        private fun flush() {
            synchronized(this@InMemorySharedPreferences) {
                if (clearAll) store.clear()
                removals.forEach { store.remove(it) }
                store.putAll(puts)
            }
        }
    }
}

/**
 * Contexto que aísla las SharedPreferences (mapa compartido [prefs]) y que registra en
 * [startedIntents] las actividades que la UI intenta lanzar en vez de abrirlas de verdad.
 */
internal class StudyTestContext(
    base: Context,
    private val prefs: MutableMap<String, SharedPreferences>,
    val startedIntents: MutableList<Intent>,
) : ContextWrapper(base) {
    override fun getSharedPreferences(
        name: String?,
        mode: Int,
    ): SharedPreferences =
        synchronized(prefs) {
            prefs.getOrPut(name ?: "default") { InMemorySharedPreferences() }
        }

    override fun startActivity(intent: Intent) {
        startedIntents += intent
    }

    override fun startActivity(
        intent: Intent,
        options: Bundle?,
    ) {
        startedIntents += intent
    }
}

/** Registro de resultados que no lanza nada: anota el input y deja responder a mano con [respond]. */
internal class RecordingRegistry : ActivityResultRegistry() {
    val launchedInputs = CopyOnWriteArrayList<Any?>()

    @Volatile
    private var lastRequestCode = -1

    override fun <I, O> onLaunch(
        requestCode: Int,
        contract: ActivityResultContract<I, O>,
        input: I,
        options: ActivityOptionsCompat?,
    ) {
        launchedInputs += input
        lastRequestCode = requestCode
    }

    /** Entrega un resultado al último lanzador usado (en el hilo principal). */
    fun respond(
        resultCode: Int,
        data: Intent?,
    ) {
        val code = lastRequestCode
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            dispatchResult(code, resultCode, data)
        }
    }
}

/** Entorno de una prueba: prefs aisladas, registro grabador y contexto grabador ya en español. */
internal class StudyTestEnv(
    private val rule: AndroidComposeTestRule<*, ComponentActivity>,
) {
    val prefs = HashMap<String, SharedPreferences>()
    val registry = RecordingRegistry()
    val startedIntents = CopyOnWriteArrayList<Intent>()

    /** Contexto para sembrar/leer las mismas prefs que usa la composición (fuera de ella). */
    val seedContext: StudyTestContext =
        StudyTestContext(InstrumentationRegistry.getInstrumentation().targetContext, prefs, startedIntents)

    fun setContent(content: @Composable () -> Unit) {
        rule.setContent {
            val baseContext = LocalContext.current
            val testContext =
                remember(baseContext) { StudyTestContext(forceLocale(baseContext, "es-ES"), prefs, startedIntents) }
            val owner =
                remember {
                    object : ActivityResultRegistryOwner {
                        override val activityResultRegistry: ActivityResultRegistry = registry
                    }
                }
            CompositionLocalProvider(
                LocalContext provides testContext,
                LocalResources provides testContext.resources,
                LocalDensity provides testViewportDensity(),
                LocalActivityResultRegistryOwner provides owner,
                LocalOnBackPressedDispatcherOwner provides rule.activity,
            ) { content() }
        }
    }
}

internal fun ComposeTestRule.waitForTextExists(
    text: String,
    timeoutMillis: Long = 15_000,
) {
    waitUntilOrDump(STUDY_TEST_TAG, timeoutMillis) {
        onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }
}

/** ViewModel de Estudio con anuncios desactivados (Premium). */
internal fun buildStudyViewModel(): StudyViewModel {
    val adManager = mockk<AdManager>(relaxed = true)
    every { adManager.isPremium } returns MutableStateFlow(true)
    every { adManager.isInitialized } returns MutableStateFlow(false)
    return StudyViewModel(adManager = adManager)
}

internal const val FIXED_NOTE_CREATED_AT = 1_700_000_000_000L

internal fun testNote(
    id: String,
    title: String,
    text: String,
    reminderAt: Long? = null,
    images: List<com.docsmart.core.data.db.NoteImageEntity> = emptyList(),
): NoteWithImages =
    NoteWithImages(
        NoteEntity(id = id, title = title, text = text, createdAt = FIXED_NOTE_CREATED_AT, reminderAt = reminderAt),
        images,
    )

internal data class CreatedNote(
    val title: String,
    val text: String,
    val imageUris: List<Uri>,
    val reminderAt: Long?,
)

internal data class UpdatedNote(
    val noteId: String,
    val title: String,
    val text: String,
    val reminderAt: Long?,
)

/** Repositorio de notas en memoria (sin Room, sin alarmas) con los cambios grabados. */
internal class FakeNotes(
    initial: List<NoteWithImages> = emptyList(),
) {
    val notes = MutableStateFlow(initial)
    val created = CopyOnWriteArrayList<CreatedNote>()
    val updated = CopyOnWriteArrayList<UpdatedNote>()
    private val repository = mockk<NoteRepository>(relaxed = true)

    init {
        every { repository.observeAll() } returns notes
        coEvery { repository.createNote(any(), any(), any(), any(), any()) } coAnswers {
            val title = firstArg<String>()
            val text = secondArg<String>()
            val uris = arg<List<Uri>>(3)
            val reminder = arg<Long?>(4)
            created += CreatedNote(title, text, uris, reminder)
            notes.value = listOf(testNote("new${created.size}", title, text, reminder)) + notes.value
        }
        coEvery { repository.deleteNote(any()) } coAnswers {
            notes.value = notes.value - firstArg<NoteWithImages>()
        }
        coEvery { repository.deleteAll(any()) } coAnswers {
            notes.value = emptyList()
        }
        coEvery { repository.updateNote(any(), any(), any(), any(), any(), any(), any()) } coAnswers {
            val id = firstArg<String>()
            val title = secondArg<String>()
            val text = thirdArg<String>()
            val reminder = arg<Long?>(3)
            updated += UpdatedNote(id, title, text, reminder)
            notes.value =
                notes.value.map { item ->
                    if (item.note.id == id) {
                        item.copy(note = item.note.copy(title = title, text = text, reminderAt = reminder))
                    } else {
                        item
                    }
                }
        }
    }

    // Una sola instancia: las pruebas la consultan para esperar a que la UI ya vea los cambios.
    val viewModel: NotesViewModel by lazy { NotesViewModel(repository) }

    fun buildViewModel(): NotesViewModel = viewModel
}

private fun cacheDir(): File = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir

/** PDF de texto real (iText) con [pages] páginas y dos párrafos separados por página. */
internal fun createStudyTestPdf(
    pages: Int = 2,
    prefix: String = "study_test",
): File {
    val file = File(cacheDir(), "${prefix}_${System.nanoTime()}.pdf")
    val pdf = PdfDocument(PdfWriter(file))
    val document = Document(pdf)
    for (page in 1..pages) {
        if (page > 1) document.add(AreaBreak(AreaBreakType.NEXT_PAGE))
        document.add(Paragraph("Primer parrafo de la pagina $page con texto suficiente.").setMarginBottom(30f))
        document.add(Paragraph("Segundo parrafo de la pagina $page tambien con texto suficiente."))
    }
    document.close()
    return file
}

/** PNG pequeño en caché para adjuntarlo a una nota. */
internal fun createStudyTestPng(prefix: String = "study_test_img"): File {
    val file = File(cacheDir(), "${prefix}_${System.nanoTime()}.png")
    val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
    bitmap.eraseColor(Color.BLUE)
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    bitmap.recycle()
    return file
}
