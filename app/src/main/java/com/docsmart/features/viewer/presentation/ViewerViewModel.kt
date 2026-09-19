package com.docsmart.features.viewer.presentation

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docsmart.R
import com.docsmart.core.ads.AdManager
import com.docsmart.core.analytics.DocuSmartAnalytics
import com.docsmart.core.data.FavoritesRepository
import com.docsmart.core.data.db.AnnotationDao
import com.docsmart.core.data.db.AnnotationEntity
import com.docsmart.core.data.db.AnnotationType
import com.docsmart.core.data.db.DocumentHistoryDao
import com.docsmart.core.data.db.DocumentHistoryEntry
import com.docsmart.core.data.db.LastViewedPageDao
import com.docsmart.core.data.db.LastViewedPageEntity
import com.docsmart.core.data.db.NoteDao
import com.docsmart.core.data.db.NoteEntity
import com.docsmart.core.data.db.PageBookmarkDao
import com.docsmart.core.data.db.PageBookmarkEntity
import com.docsmart.core.ui.components.DocumentType
import com.docsmart.core.ui.components.DocumentUiModel
import com.docsmart.features.library.data.DocumentRepository
import com.docsmart.features.library.data.TrashRepository
import com.docsmart.features.viewer.domain.annotation.PdfRectPts
import com.docsmart.features.viewer.domain.usecase.FlattenAnnotationsPdfUseCase
import com.docsmart.features.viewer.domain.usecase.PdfMatchRect
import com.docsmart.features.viewer.domain.usecase.SearchPdfTextUseCase
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.ReaderProperties
import com.itextpdf.kernel.pdf.WriterProperties
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.util.UUID
import javax.inject.Inject

private const val MIME_PDF = "application/pdf"
private const val MIME_JPEG = "image/jpeg"

// HU-46: modo de anotación activo sobre el PDF -- NONE es el
// comportamiento normal de siempre (zoom/pan libres, tocar alterna los
// controles); HIGHLIGHT/NOTE deshabilitan el zoom/pan mientras están
// activos para no pelear con el gesto de dibujar/anclar (ver PdfViewerContent).
enum class AnnotationMode { NONE, HIGHLIGHT, NOTE }

// Paleta de colores para resaltar -- al menos 3 pedidos por RF1, con un
// cuarto para dar más variedad sin complicar la UI (misma cantidad de
// chips que ya usa el selector de "Modo de color" del Escáner).
val ANNOTATION_HIGHLIGHT_COLORS =
    listOf(
        // Amarillo (mismo tono que el resaltado de búsqueda)
        0xFFFFEB3B.toInt(),
        // Verde
        0xFF66BB6A.toInt(),
        // Rosa
        0xFFF06292.toInt(),
        // Celeste
        0xFF4FC3F7.toInt(),
    )
private const val ANNOTATION_NOTE_COLOR = 0xFFFF7043.toInt() // Naranja -- fijo, distingue notas de resaltados

// Hallazgo de la revisión de seguridad HU-46: sin límite, una nota de
// tamaño arbitrario queda persistida en Room y luego incrustada tal cual en
// el PDF aplanado al compartir -- acota el tamaño real de archivo/DB.
const val MAX_NOTE_LENGTH = 2000

data class ViewerUiState(
    val document: DocumentUiModel? = null,
    val currentPage: Int = 0,
    val totalPages: Int = 0,
    val isLoading: Boolean = true,
    val error: String? = null,
    val isFavorite: Boolean = false,
    val showControls: Boolean = true,
    val fileUri: Uri? = null,
    val mimeType: String? = null,
    val requiresPassword: Boolean = false,
    val passwordError: String? = null,
    val decryptedFile: File? = null,
    // páginas (1-based) con coincidencias
    val pdfSearchMatches: List<Int> = emptyList(),
    // índice actual dentro de pdfSearchMatches
    val pdfSearchIndex: Int = -1,
    // RF-VIS-08: posición real de cada coincidencia por página, para el
    // resaltado inline -- página (1-based) → rects en puntos PDF.
    val pdfSearchHighlights: Map<Int, List<PdfMatchRect>> = emptyMap(),
    // ── RF-VIS-06: renombrar/eliminar desde el Visor ──────────────────────
    val showRenameDialog: Boolean = false,
    val showDeleteConfirm: Boolean = false,
    val deleteError: String? = null,
    val documentDeleted: Boolean = false,
    // ── HU-46: anotaciones (resaltado + notas adhesivas) ──────────────────
    // Hallazgo real de la revisión general 2026-09-16: esta bandera vivía
    // como estado local de Compose (`remember`) en ViewerScreen -- al rotar
    // el dispositivo se reiniciaba a `false` (barra/ícono "Anotar" ya no se
    // ven activos) mientras `annotationMode` (acá, sobrevive rotación
    // porque el ViewModel sobrevive) seguía en HIGHLIGHT/NOTE, dejando el
    // zoom/pan deshabilitado sin ninguna barra visible para volver a NONE.
    // Vive acá para que ambos sobrevivan la rotación juntos, sincronizados.
    val showAnnotationToolbar: Boolean = false,
    val annotationMode: AnnotationMode = AnnotationMode.NONE,
    val selectedHighlightColor: Int = ANNOTATION_HIGHLIGHT_COLORS.first(),
    // página (1-based) → anotaciones
    val annotations: Map<Int, List<AnnotationEntity>> = emptyMap(),
    // punto tocado en modo NOTE, a la espera del texto
    val pendingNoteAnchor: PdfRectPts? = null,
    val pendingNotePage: Int? = null,
    // tap sobre una anotación ya existente
    val viewingAnnotation: AnnotationEntity? = null,
    // "con anotaciones" / "original", solo si hay >=1
    val showShareChoiceDialog: Boolean = false,
    val isFlatteningForShare: Boolean = false,
    // Hallazgo real de la revisión general 2026-09-16 (cuarta pasada): la
    // vista previa de Carpeta Segura (hallazgo #53) abre esta pantalla sobre
    // una copia efímera en cacheDir/secure_preview/, con TODA la
    // funcionalidad del Visor real -- eso permitía que "Eliminar" simulara
    // un borrado que nunca tocaba el archivo protegido real, que
    // anotaciones nuevas quedaran huérfanas bajo un id temporal, y que el
    // documento protegido quedara expuesto en Recientes/Biblioteca sin PIN
    // mientras la copia siguiera viva (ver recordHistoryOpen). Con esta
    // bandera, la pantalla pasa a modo genuinamente de solo lectura:
    // renombrar/eliminar/anotar se ocultan y el acceso no se registra en el
    // historial.
    val isReadOnlyPreview: Boolean = false,
    // Hallazgo de la revisión de correctitud HU-46: un fallo al compartir
    // usaba `error` (el mismo campo que reemplaza TODA la vista del Visor
    // por una pantalla de "documento roto", ver el `when` de nivel superior
    // en ViewerScreen) -- un aviso transitorio (mismo patrón que deleteError)
    // no debe expulsar al usuario del documento que sigue perfectamente
    // legible.
    val shareError: String? = null,
    // ── Backlog UX #47/#48: marcadores de página + última página vista ────
    // páginas (0-based) marcadas del documento actual
    val bookmarkedPages: Set<Int> = emptySet(),
    val showBookmarksSheet: Boolean = false,
    // Salto de página pendiente de consumir -- generaliza el mecanismo que
    // antes solo usaba la búsqueda in-PDF (ver targetPage en ViewerScreen):
    // se dispara al abrir un PDF con última página vista guardada, o al
    // tocar un marcador en la lista. PdfViewerContent lo consume (llama
    // onPageJumpConsumed) apenas termina de saltar, así que no vuelve a
    // dispararse en la siguiente recomposición.
    val pendingPageJump: Int? = null,
    // Backlog UX #50: notas de Modo Estudio vinculadas a este documento.
    val linkedNotes: List<NoteEntity> = emptyList(),
    val showLinkedNotesDialog: Boolean = false,
)

@HiltViewModel
class ViewerViewModel
    @Inject
    constructor(
        private val favoritesRepository: FavoritesRepository,
        private val searchPdfText: SearchPdfTextUseCase,
        private val documentHistoryDao: DocumentHistoryDao,
        private val documentRepository: DocumentRepository,
        private val trashRepository: TrashRepository,
        // Backlog UX 2026-08-30 (HU-UX-07): banner de anuncios consistente en
        // pantallas de contenido, faltaba en el Visor.
        val adManager: AdManager,
        // HU-46: anotaciones (resaltado + notas adhesivas) sobre el PDF.
        private val annotationDao: AnnotationDao,
        private val flattenAnnotationsPdfUseCase: FlattenAnnotationsPdfUseCase,
        // Backlog UX #47/#48: marcadores de página + última página vista.
        private val pageBookmarkDao: PageBookmarkDao,
        private val lastViewedPageDao: LastViewedPageDao,
        // Backlog UX #50: notas de Modo Estudio vinculadas a este documento.
        private val noteDao: NoteDao,
    ) : ViewModel() {
        companion object {
            private const val TAG = "ViewerViewModel"

            // Backlog UX #48: onPageChanged() se dispara por cada página que
            // entra en composición durante el scroll (LazyColumn), no solo al
            // asentarse -- escribir en Room en cada llamada sería demasiado
            // frecuente en un scroll rápido por un documento largo. Se debounce
            // (cancelar + reprogramar) para persistir solo la página en la que
            // el usuario realmente se detuvo.
            private const val SAVE_LAST_PAGE_DEBOUNCE_MS = 600L
        }

        private val _uiState = MutableStateFlow(ViewerUiState())
        val uiState: StateFlow<ViewerUiState> = _uiState.asStateFlow()

        private var pendingDocumentId: String = ""
        private var annotationsJob: Job? = null
        private var bookmarksJob: Job? = null
        private var saveLastPageJob: Job? = null
        private var linkedNotesJob: Job? = null
        private var searchJob: Job? = null

        // HU-46: se re-suscribe cada vez que cambia el documento cargado --
        // Flow, no una sola carga, para que altas/bajas hechas en esta misma
        // sesión (agregar resaltado, borrar nota) se reflejen sin recargar la
        // pantalla. Mismo `documentId` (uriString) que usan favoritos/historial.
        private fun observeAnnotations(documentId: String) {
            annotationsJob?.cancel()
            annotationsJob =
                viewModelScope.launch {
                    annotationDao.observeByDocument(documentId).collect { entries ->
                        _uiState.update { it.copy(annotations = entries.groupBy { entry -> entry.page }) }
                    }
                }
        }

        // Backlog UX #47: mismo criterio que observeAnnotations() -- Flow, no
        // una sola carga, para que marcar/desmarcar en esta misma sesión se
        // refleje sin recargar la pantalla.
        private fun observeBookmarks(documentId: String) {
            bookmarksJob?.cancel()
            bookmarksJob =
                viewModelScope.launch {
                    pageBookmarkDao.observeByDocument(documentId).collect { entries ->
                        _uiState.update { it.copy(bookmarkedPages = entries.map { entry -> entry.page }.toSet()) }
                    }
                }
        }

        // Backlog UX #48: se dispara una sola vez al abrir el documento -- si
        // hay una página guardada, PdfViewerContent salta ahí apenas termina de
        // renderizar (ver pendingPageJump/onPageJumpConsumed). page=0 (primera
        // página) no dispara salto: es el mismo lugar donde ya abriría por
        // defecto, no hace falta un salto "de vuelta al principio".
        private fun loadLastViewedPage(documentId: String) {
            viewModelScope.launch {
                val saved = lastViewedPageDao.getByDocument(documentId)
                if (saved != null && saved.page > 0) {
                    _uiState.update { it.copy(pendingPageJump = saved.page) }
                }
            }
        }

        // Backlog UX #50: mismo criterio reactivo que observeBookmarks() -- se
        // refleja solo al vincular/desvincular una nota desde Modo Estudio,
        // sin recargar el Visor.
        private fun observeLinkedNotes(documentId: String) {
            linkedNotesJob?.cancel()
            linkedNotesJob =
                viewModelScope.launch {
                    noteDao.observeByDocument(documentId).collect { notes ->
                        _uiState.update { it.copy(linkedNotes = notes.map { entry -> entry.note }) }
                    }
                }
        }

        fun showLinkedNotesDialog() {
            _uiState.update { it.copy(showLinkedNotesDialog = true) }
        }

        fun dismissLinkedNotesDialog() {
            _uiState.update { it.copy(showLinkedNotesDialog = false) }
        }

        // Se guarda applicationContext (no la Activity), por eso no hay fuga real
        // pese a lo que reporta el detector StaticFieldLeak de lint.
        @SuppressLint("StaticFieldLeak")
        private var pendingContext: Context? = null

        fun loadDocument(
            documentId: String,
            context: Context,
        ) {
            pendingDocumentId = documentId
            pendingContext = context.applicationContext
            Timber.d("$TAG: loadDocument START → id=$documentId")
            viewModelScope.launch {
                _uiState.update {
                    it.copy(
                        isLoading = true,
                        error = null,
                        requiresPassword = false,
                        passwordError = null,
                    )
                }
                try {
                    loadDocumentOrMock(documentId, context)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "$TAG: error inesperado → ${e.javaClass.name}: ${e.message}")
                    // Bug real encontrado 2026-09-14: este mensaje estaba
                    // hardcodeado en español, saltándose el sistema de 12
                    // idiomas -- reusa viewer_error, el mismo texto que la UI ya
                    // mostraba como fallback cuando error era null.
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = context.getString(R.string.viewer_error),
                        )
                    }
                }
            }
        }

        private fun isRealUri(documentId: String) =
            documentId.startsWith("content://") ||
                documentId.startsWith("file://") ||
                documentId.startsWith("content%3A") ||
                documentId.startsWith("/")

        private suspend fun loadDocumentOrMock(
            documentId: String,
            context: Context,
        ) {
            val isReal = isRealUri(documentId)
            Timber.d("$TAG: isRealUri=$isReal")
            if (isReal) loadFromUri(documentId, context) else loadFromMock(documentId, context)
        }

        private suspend fun loadFromUri(
            documentId: String,
            context: Context,
        ) {
            withContext(Dispatchers.IO) {
                // uriString es el mismo id que usan Biblioteca/Home como clave de
                // favoritos/alias (FavoritesRepository) — para rutas absolutas debe
                // quedar SIN el prefijo "file://", si no los favoritos/alias
                // marcados desde el Visor no coinciden con los de Biblioteca/Home.
                val uriString = resolveUriString(documentId)
                val uri = resolveUri(documentId, uriString)
                val rawName = resolveFileName(uriString, context)
                val fileName = favoritesRepository.getAlias(uriString) ?: rawName
                val mimeType = resolveDisplayMimeType(uri, fileName, uriString, context)

                Timber.d("$TAG: uri=$uri mimeType=$mimeType fileName=$fileName")

                val isPdf = mimeType.contains("pdf") || fileName.endsWith(".pdf", ignoreCase = true)
                if (isPdf && isPdfPasswordProtected(uri, context, documentId)) {
                    Timber.d("$TAG: isPdf=$isPdf isProtected=true")
                    publishPasswordProtectedPdf(uriString, uri, mimeType, fileName)
                    return@withContext
                }

                publishLoadedDocument(uriString, uri, mimeType, fileName)
            }
        }

        private fun resolveUriString(documentId: String) =
            when {
                documentId.startsWith("content%3A") -> Uri.decode(documentId)
                else -> documentId
            }

        private fun resolveUri(
            documentId: String,
            uriString: String,
        ) = if (documentId.startsWith("/")) Uri.fromFile(File(documentId)) else Uri.parse(uriString)

        private fun resolveDisplayMimeType(
            uri: Uri,
            fileName: String,
            uriString: String,
            context: Context,
        ): String {
            val fromResolver =
                try {
                    context.contentResolver.getType(uri)
                } catch (e: Exception) {
                    null
                }
            val fromExtension = resolveMimeTypeByExtension(fileName) ?: resolveMimeType(uriString)
            return when {
                fromResolver == null -> fromExtension ?: "application/octet-stream"
                fromResolver == "application/octet-stream" -> fromExtension ?: fromResolver
                fromResolver.contains("*") -> fromExtension ?: fromResolver
                else -> fromResolver
            }
        }

        private fun publishPasswordProtectedPdf(
            uriString: String,
            uri: Uri,
            mimeType: String,
            fileName: String,
        ) {
            val isFavorite = favoritesRepository.isFavorite(uriString)
            val document =
                DocumentUiModel(
                    id = uriString,
                    name = fileName,
                    type = DocumentType.PDF,
                    size = "",
                    date = "",
                    isFavorite = isFavorite,
                )
            _uiState.update { state ->
                state.copy(
                    document = document,
                    fileUri = uri,
                    mimeType = mimeType,
                    isFavorite = isFavorite,
                    isLoading = false,
                    requiresPassword = true,
                    error = null,
                )
            }
        }

        private suspend fun publishLoadedDocument(
            uriString: String,
            uri: Uri,
            mimeType: String,
            fileName: String,
        ) {
            val documentType = detectDocumentType(mimeType)
            val isFavorite = favoritesRepository.isFavorite(uriString)
            val isReadOnlyPreview = isPreviewCacheDocument(uriString)
            val document =
                DocumentUiModel(
                    id = uriString,
                    name = fileName,
                    type = documentType,
                    size = "",
                    date = "",
                    isFavorite = isFavorite,
                )
            _uiState.update { state ->
                state.copy(
                    document = document,
                    fileUri = uri,
                    mimeType = mimeType,
                    isFavorite = isFavorite,
                    isLoading = false,
                    error = null,
                    isReadOnlyPreview = isReadOnlyPreview,
                )
            }
            // Hallazgo real de la revisión general 2026-09-16 (cuarta pasada):
            // registrar la copia efímera de vista previa en el historial la
            // hacía aparecer en Recientes/Biblioteca (que solo exigen que el
            // archivo exista en disco) con su nombre real, sin pedir PIN,
            // mientras la copia siguiera viva.
            if (!isReadOnlyPreview) recordHistoryOpen(uriString)
            observeAnnotations(uriString)
            // Backlog UX #47/#48: mismo criterio de solo-lectura que el resto
            // de la vista previa de Carpeta Segura -- no observar marcadores ni
            // saltar a una "última página" de la copia efímera (que además
            // nunca tendría ninguna guardada, al no registrarse el historial).
            if (!isReadOnlyPreview) {
                observeBookmarks(uriString)
                loadLastViewedPage(uriString)
                observeLinkedNotes(uriString)
            }
            DocuSmartAnalytics.logDocumentOpened(documentType.name)
        }

        // Hallazgo real de la revisión general 2026-09-16 (cuarta pasada): la
        // vista previa de Carpeta Segura (SecurityManager.copyForPreview) copia
        // siempre a `cacheDir/secure_preview/<nombre>` -- detectar ese path por
        // el nombre de su carpeta padre (no por un flag explícito que habría
        // que enhebrar por toda la navegación) alcanza para identificar el
        // documentId sin acoplar el Visor a SecurityManager.
        private fun isPreviewCacheDocument(documentId: String) = File(documentId).parentFile?.name == "secure_preview"

        // RF-VIS/HOME: registra el acceso real para que "recientes" en Home
        // refleje uso, no solo la fecha de modificación del archivo.
        private suspend fun recordHistoryOpen(documentId: String) {
            documentHistoryDao.recordOpen(DocumentHistoryEntry(documentId, System.currentTimeMillis()))
        }

        // Intento 1 de unlockPdfWithPassword (copyPagesTo, sin encriptación) --
        // extraído a función propia (además de mantener la función principal
        // corta, como tryStampingUnlock) para no acumular su `throw` de
        // CancellationException en el conteo de unlockPdfWithPassword
        // (ThrowsCount de detekt).
        @Suppress("TooGenericExceptionCaught")
        private fun tryDirectCopyUnlock(
            cacheIn: File,
            cacheOut: File,
            readerProps: ReaderProperties,
        ): Boolean =
            try {
                val r1 = PdfReader(cacheIn.absolutePath, readerProps)
                r1.setUnethicalReading(true)
                r1.setMemorySavingMode(true)
                val srcDoc = PdfDocument(r1)
                val destDoc = PdfDocument(PdfWriter(cacheOut.absolutePath, WriterProperties()))
                srcDoc.copyPagesTo(1, srcDoc.numberOfPages, destDoc)
                srcDoc.close()
                destDoc.close()
                Timber.d("$TAG: Intento 1 copyPagesTo → ${cacheOut.length()}b")

                // ── Verificar que el output NO está encriptado ────────
                val stillEncrypted1 = isStillEncrypted(cacheOut)
                Timber.d("$TAG: OUTPUT isStillEncrypted=$stillEncrypted1")
                !stillEncrypted1
            } catch (e1: CancellationException) {
                throw e1
            } catch (e1: Exception) {
                Timber.w("$TAG: Intento 1 falló → ${e1.message}")
                false
            }

        // Intento 2 de unlockPdfWithPassword (PdfDocument directo si copyPagesTo
        // falla) -- mismo motivo de extracción que tryDirectCopyUnlock.
        @Suppress("TooGenericExceptionCaught")
        private fun tryDirectDocumentUnlock(
            cacheIn: File,
            cacheOut: File,
            readerProps: ReaderProperties,
        ): Boolean {
            if (cacheOut.exists()) cacheOut.delete()
            return try {
                val r2 = PdfReader(cacheIn.absolutePath, readerProps)
                r2.setUnethicalReading(true)
                r2.setMemorySavingMode(true)
                val doc = PdfDocument(r2, PdfWriter(cacheOut.absolutePath, WriterProperties()))
                doc.close()
                Timber.d("$TAG: Intento 2 PdfDocument directo → ${cacheOut.length()}b")

                val stillEncrypted2 = isStillEncrypted(cacheOut)
                Timber.d("$TAG: OUTPUT2 isStillEncrypted=$stillEncrypted2")
                !stillEncrypted2
            } catch (e2: CancellationException) {
                throw e2
            } catch (e2: Exception) {
                Timber.w("$TAG: Intento 2 falló → ${e2.message}")
                false
            }
        }

        // Último recurso de unlockPdfWithPassword si copyPagesTo y PdfDocument
        // directo fallan — extraído para mantener la función principal corta.
        private fun tryStampingUnlock(
            cacheIn: File,
            cacheOut: File,
            readerProps: ReaderProperties,
        ): Boolean =
            try {
                if (cacheOut.exists()) cacheOut.delete()
                val r3 = PdfReader(cacheIn.absolutePath, readerProps)
                r3.setUnethicalReading(true)
                val stamps =
                    com.itextpdf.kernel.pdf
                        .StampingProperties()
                val doc3 = PdfDocument(r3, PdfWriter(cacheOut.absolutePath, WriterProperties()), stamps)
                doc3.close()
                Timber.d("$TAG: Intento 3 StampingProperties → ${cacheOut.length()}b")
                // Bug real encontrado 2026-09-14 (repaso general): a diferencia
                // de los Intentos 1 y 2, este nunca verificaba que el archivo
                // resultante ya no estuviera encriptado -- StampingProperties
                // "estampa" sobre el PDF original en vez de reescribirlo desde
                // cero, así que puede arrastrar la encriptación de origen al
                // archivo de salida. Sin esta verificación, un PDF que en
                // realidad seguía encriptado podía marcarse como desbloqueado
                // con éxito (requiresPassword = false), y el Visor fallaba en
                // silencio más adelante al intentar renderizarlo.
                cacheOut.exists() && cacheOut.length() > 100L && !isStillEncrypted(cacheOut)
            } catch (e3: Exception) {
                Timber.w("$TAG: Intento 3 falló → ${e3.message}")
                false
            }

        // Compartido entre los 3 intentos de unlockPdfWithPassword -- confirma
        // que el archivo de salida realmente ya no requiere contraseña.
        private fun isStillEncrypted(file: File): Boolean =
            try {
                val vr = PdfReader(file.absolutePath)
                val enc = vr.isEncrypted
                PdfDocument(vr).close()
                enc
            } catch (ve: Exception) {
                val msg = ve.message?.lowercase() ?: ""
                Timber.e("$TAG: verificación output → ${ve.message}")
                msg.contains("password") || msg.contains("encrypt")
            }

        private fun isPdfPasswordProtected(
            uri: Uri,
            context: Context,
            originalId: String = "",
        ): Boolean {
            val cacheFile = File(context.cacheDir, "temp_check_${System.currentTimeMillis()}.pdf")
            return try {
                val copied =
                    when {
                        originalId.startsWith("/") -> {
                            val sourceFile = File(originalId)
                            Timber.d("$TAG: isPdf ruta absoluta path=$originalId existe=${sourceFile.exists()} size=${sourceFile.length()}")
                            if (!sourceFile.exists()) return false
                            sourceFile.copyTo(cacheFile, overwrite = true)
                            true
                        }
                        uri.scheme == "file" -> {
                            val path = uri.path ?: return false
                            val sourceFile = File(path)
                            Timber.d("$TAG: isPdf file:// path=$path existe=${sourceFile.exists()} size=${sourceFile.length()}")
                            if (!sourceFile.exists()) return false
                            sourceFile.copyTo(cacheFile, overwrite = true)
                            true
                        }
                        else -> {
                            context.contentResolver.openInputStream(uri)?.use { input ->
                                cacheFile.outputStream().use { output -> input.copyTo(output) }
                                true
                            } ?: false
                        }
                    }

                if (!copied || !cacheFile.exists() || cacheFile.length() == 0L) {
                    Timber.w("$TAG: no se pudo copiar el PDF al caché")
                    return false
                }

                Timber.d("$TAG: cacheFile size=${cacheFile.length()} — abriendo con PdfReader")
                var isEncrypted = false
                try {
                    val reader = PdfReader(cacheFile.absolutePath)
                    val doc = PdfDocument(reader)
                    doc.close()
                    isEncrypted = false
                } catch (e: Exception) {
                    val msg = e.message?.lowercase() ?: ""
                    isEncrypted = msg.contains("password") ||
                        msg.contains("encrypt") ||
                        msg.contains("decrypt") ||
                        msg.contains("bad user") ||
                        msg.contains("owner") ||
                        msg.contains("pdf header")
                    Timber.d("$TAG: PdfDocument excepción → clase=${e.javaClass.simpleName} msg=${e.message} isEncrypted=$isEncrypted")
                }
                Timber.d("$TAG: PDF isEncrypted=$isEncrypted")
                isEncrypted
            } catch (e: Exception) {
                val msg = e.message?.lowercase() ?: ""
                Timber.e("$TAG: isPdfPasswordProtected EXCEPCION → clase=${e.javaClass.name} msg=${e.message}")
                msg.contains("password") ||
                    msg.contains("encrypt") ||
                    msg.contains("decrypt") ||
                    msg.contains("bad user") ||
                    msg.contains("owner")
            } finally {
                if (cacheFile.exists()) cacheFile.delete()
            }
        }

        // ── Desbloquear PDF con contraseña ────────────────────────────────────────
        fun unlockPdfWithPassword(password: String) {
            val context = pendingContext ?: return
            val uri = _uiState.value.fileUri ?: return
            val originalId = pendingDocumentId

            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true, passwordError = null) }

                withContext(Dispatchers.IO) {
                    // Revisión adversarial de correctitud (ronda 11): cacheIn/
                    // cacheOut declarados fuera del try -- si la corrutina se
                    // cancela (el usuario navega fuera del Visor mientras el
                    // desbloqueo, potencialmente lento para un PDF grande, sigue
                    // en curso), el finally de abajo los borra igual que un
                    // catch de Exception, en vez de quedar huérfanos para
                    // siempre en cacheDir. `committed` protege el único caso
                    // donde cacheOut SÍ debe sobrevivir: cuando ya quedó
                    // publicado en el estado como el PDF desbloqueado en uso.
                    var cacheIn: File? = null
                    var cacheOut: File? = null
                    var committed = false
                    try {
                        val localCacheIn = File(context.cacheDir, "temp_locked_${System.currentTimeMillis()}.pdf")
                        cacheIn = localCacheIn

                        // ── Copiar archivo original al caché ──────────────────────
                        when {
                            originalId.startsWith("/") -> {
                                val src = File(originalId)
                                Timber.d("$TAG: unlock ruta absoluta path=$originalId existe=${src.exists()}")
                                if (!src.exists()) {
                                    // Bug real encontrado 2026-09-14: hardcodeado
                                    // en español -- reusa pdf_pw_read_error
                                    // (mismo mensaje que ya usa PdfPasswordScreen
                                    // para este mismo escenario).
                                    val readError = context.getString(R.string.pdf_pw_read_error)
                                    _uiState.update { it.copy(isLoading = false, passwordError = readError) }
                                    return@withContext
                                }
                                src.copyTo(localCacheIn, overwrite = true)
                            }
                            uri.scheme == "file" -> {
                                val path = uri.path ?: return@withContext
                                val src = File(path)
                                Timber.d("$TAG: unlock file:// path=$path existe=${src.exists()}")
                                if (!src.exists()) {
                                    // Bug real encontrado 2026-09-14: hardcodeado
                                    // en español -- reusa pdf_pw_read_error
                                    // (mismo mensaje que ya usa PdfPasswordScreen
                                    // para este mismo escenario).
                                    val readError = context.getString(R.string.pdf_pw_read_error)
                                    _uiState.update { it.copy(isLoading = false, passwordError = readError) }
                                    return@withContext
                                }
                                src.copyTo(localCacheIn, overwrite = true)
                            }
                            else -> {
                                val opened = context.contentResolver.openInputStream(uri)
                                if (opened == null) {
                                    // Hallazgo real de la auditoría general
                                    // 2026-09-17 (quinta pasada): a diferencia de
                                    // las otras 2 ramas, esta no comprobaba si el
                                    // archivo era legible -- un permiso SAF
                                    // revocado o un proveedor caído hacía que
                                    // openInputStream() devolviera null, cacheIn
                                    // quedaba vacío/inexistente, y el intento de
                                    // PdfReader de más abajo fallaba con la misma
                                    // excepción que "contraseña incorrecta" --
                                    // el usuario reintentaba la contraseña
                                    // correcta sin poder resolver el problema
                                    // real.
                                    val readError = context.getString(R.string.pdf_pw_read_error)
                                    _uiState.update { it.copy(isLoading = false, passwordError = readError) }
                                    return@withContext
                                }
                                opened.use { input ->
                                    localCacheIn.outputStream().use { output -> input.copyTo(output) }
                                }
                            }
                        }

                        Timber.d("$TAG: cacheIn copiado → ${localCacheIn.length()}b")

                        val userPass = password.toByteArray()
                        val readerProps = ReaderProperties().setPassword(userPass)

                        // ── Verificar contraseña ──────────────────────────────────
                        val testReader =
                            try {
                                val r = PdfReader(localCacheIn.absolutePath, readerProps)
                                r.setUnethicalReading(true)
                                r
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                localCacheIn.delete()
                                Timber.w("$TAG: contraseña incorrecta → ${e.message}")
                                // Bug real encontrado 2026-09-14 (repaso general):
                                // hardcodeado en español -- reusa
                                // pdf_pw_wrong_password_retry (mismo texto que ya
                                // usa PdfPasswordScreen para este mismo escenario).
                                _uiState.update {
                                    it.copy(
                                        isLoading = false,
                                        passwordError = context.getString(R.string.pdf_pw_wrong_password_retry),
                                    )
                                }
                                return@withContext
                            }
                        testReader.close()

                        val localCacheOut = File(context.cacheDir, "unlocked_${System.currentTimeMillis()}.pdf")
                        cacheOut = localCacheOut

                        // ── Intento 1: copyPagesTo (sin encriptación) ─────────────
                        var success = tryDirectCopyUnlock(localCacheIn, localCacheOut, readerProps)

                        // ── Intento 2: PdfDocument directo si copyPagesTo falla ───
                        if (!success) success = tryDirectDocumentUnlock(localCacheIn, localCacheOut, readerProps)

                        // ── Intento 3: StampingProperties sin appendMode ──────────
                        if (!success) success = tryStampingUnlock(localCacheIn, localCacheOut, readerProps)

                        localCacheIn.delete()

                        if (!success || !localCacheOut.exists() || localCacheOut.length() < 100L) {
                            // Bug real encontrado 2026-09-14 (repaso general): si
                            // los 3 intentos fallan, cacheOut (creado/reescrito
                            // por cada intento) quedaba huérfano en cacheDir para
                            // siempre -- nada barre ese directorio.
                            if (localCacheOut.exists()) localCacheOut.delete()
                            Timber.e("$TAG: todos los intentos fallaron")
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    passwordError = context.getString(R.string.viewer_decrypt_failed),
                                )
                            }
                            return@withContext
                        }

                        Timber.d("$TAG: PDF desbloqueado exitosamente → ${localCacheOut.length()}b")
                        // Id ya publicado en el estado, no originalId crudo (pueden diferir)
                        val unlockedDocumentId = _uiState.value.document?.id ?: originalId
                        recordHistoryOpen(unlockedDocumentId)
                        // Bug real encontrado por la revisión de seguridad HU-46: para un PDF
                        // protegido con contraseña, observeAnnotations() nunca se llamaba (solo
                        // publishLoadedDocument() la dispara, y ese camino no se usa para PDFs
                        // que piden contraseña) -- las anotaciones creadas tras desbloquear
                        // quedaban invisibles y shareDocument() tomaba siempre el camino "sin
                        // anotaciones" en silencio, aunque el usuario ya hubiera resaltado/anotado.
                        observeAnnotations(unlockedDocumentId)
                        // Marcado ANTES de publicar el estado: MutableStateFlow.update
                        // es una operación atómica, así que si se cancela justo acá,
                        // o ya se aplicó por completo (cacheOut correctamente en uso,
                        // no debe borrarse) o no se aplicó en absoluto (y el catch de
                        // CancellationException de abajo relanza sin publicar nada) --
                        // no hay estado intermedio a proteger.
                        committed = true
                        _uiState.update { state ->
                            state.copy(
                                isLoading = false,
                                requiresPassword = false,
                                passwordError = null,
                                decryptedFile = localCacheOut,
                                fileUri = Uri.fromFile(localCacheOut),
                                mimeType = MIME_PDF,
                                error = null,
                                document = state.document?.copy(),
                            )
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Timber.e(e, "$TAG: error desbloqueando PDF → ${e.message}")
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                passwordError =
                                    String.format(
                                        context.getString(R.string.viewer_open_error_format),
                                        e.message ?: "",
                                    ),
                            )
                        }
                    } finally {
                        // Revisión adversarial de correctitud (ronda 11): si la
                        // corrutina se cancela mientras el desbloqueo (potencialmente
                        // lento) está en curso, la CancellationException relanzada
                        // arriba salta directo acá sin pasar por ningún catch que
                        // limpie -- antes dejaba cacheIn/cacheOut huérfanos en
                        // cacheDir para siempre. `committed` protege el único caso
                        // donde cacheOut debe sobrevivir (ya en uso como el PDF
                        // desbloqueado).
                        if (!committed) {
                            cacheIn?.let { if (it.exists()) it.delete() }
                            cacheOut?.let { if (it.exists()) it.delete() }
                        }
                    }
                }
            }
        }

        fun dismissPasswordDialog() {
            pendingDocumentId = ""
            pendingContext = null
            _uiState.update {
                it.copy(
                    requiresPassword = false,
                    passwordError = null,
                    isLoading = false,
                )
            }
        }

        private fun detectDocumentType(mimeType: String): DocumentType =
            when {
                mimeType.contains("image") -> DocumentType.IMAGE
                mimeType.contains("pdf") -> DocumentType.PDF
                mimeType.contains("word") ||
                    mimeType.contains("msword") ||
                    mimeType.contains("wordprocessingml") -> DocumentType.WORD
                mimeType.contains("excel") ||
                    mimeType.contains("sheet") ||
                    mimeType.contains("spreadsheet") -> DocumentType.EXCEL
                mimeType.contains("powerpoint") || mimeType.contains("presentation") -> DocumentType.POWERPOINT
                mimeType.contains("text") -> DocumentType.TEXT
                else -> DocumentType.PDF
            }

        private fun loadFromMock(
            id: String,
            context: Context,
        ) {
            val mockDocument = getMockDocument(id)
            val isFavorite =
                mockDocument?.let {
                    favoritesRepository.isFavorite(it.id)
                } ?: false

            _uiState.update { state ->
                state.copy(
                    document = mockDocument,
                    isFavorite = isFavorite,
                    isLoading = false,
                    // Bug real encontrado 2026-09-14 (repaso general):
                    // hardcodeado en español, saltándose el sistema de idiomas.
                    error =
                        if (mockDocument == null) {
                            context.getString(R.string.viewer_document_not_found)
                        } else {
                            null
                        },
                )
            }
        }

        private fun resolveFileName(
            uriString: String,
            context: Context,
        ): String =
            try {
                val uri = Uri.parse(uriString)
                var name = "Documento"
                context.contentResolver
                    .query(
                        uri,
                        arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                        null,
                        null,
                        null,
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) name = cursor.getString(0) ?: "Documento"
                    }
                if (name == "Documento" && uriString.startsWith("/")) {
                    name = File(uriString).name
                }
                name
            } catch (e: Exception) {
                Timber.w("$TAG: no se pudo resolver nombre — ${e.message}")
                if (uriString.startsWith("/")) {
                    File(uriString).name
                } else {
                    Uri.parse(uriString).lastPathSegment?.substringAfterLast("/") ?: "Documento"
                }
            }

        private fun resolveMimeType(uriString: String): String? =
            when {
                uriString.contains("image") -> MIME_JPEG
                uriString.endsWith(".pdf", ignoreCase = true) -> MIME_PDF
                uriString.endsWith(".docx", ignoreCase = true) -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                uriString.endsWith(".doc", ignoreCase = true) -> "application/msword"
                uriString.endsWith(".xlsx", ignoreCase = true) -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                uriString.endsWith(".xls", ignoreCase = true) -> "application/vnd.ms-excel"
                uriString.endsWith(".jpg", ignoreCase = true) ||
                    uriString.endsWith(".jpeg", ignoreCase = true) -> MIME_JPEG
                uriString.endsWith(".png", ignoreCase = true) -> "image/png"
                uriString.endsWith(".txt", ignoreCase = true) -> "text/plain"
                else -> null
            }

        private fun resolveMimeTypeByExtension(fileName: String): String? {
            val ext = fileName.substringAfterLast(".", "").lowercase()
            return when (ext) {
                "pdf" -> MIME_PDF
                "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                "doc" -> "application/msword"
                "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                "xls" -> "application/vnd.ms-excel"
                "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                "ppt" -> "application/vnd.ms-powerpoint"
                "jpg", "jpeg" -> MIME_JPEG
                "png" -> "image/png"
                "webp" -> "image/webp"
                "gif" -> "image/gif"
                "txt" -> "text/plain"
                "md" -> "text/markdown"
                "csv" -> "text/csv"
                else -> null
            }
        }

        fun onPageChanged(
            page: Int,
            total: Int,
        ) {
            _uiState.update { it.copy(currentPage = page, totalPages = total) }
            scheduleSaveLastViewedPage(page)
        }

        private fun scheduleSaveLastViewedPage(page: Int) {
            if (_uiState.value.isReadOnlyPreview) return
            val documentId = _uiState.value.document?.id ?: return
            saveLastPageJob?.cancel()
            saveLastPageJob =
                viewModelScope.launch {
                    delay(SAVE_LAST_PAGE_DEBOUNCE_MS)
                    lastViewedPageDao.save(LastViewedPageEntity(documentId, page, System.currentTimeMillis()))
                }
        }

        // ── Backlog UX #47: marcadores de página ──────────────────────────────
        fun toggleBookmarkCurrentPage() {
            if (_uiState.value.isReadOnlyPreview) return
            val documentId = _uiState.value.document?.id ?: return
            val page = _uiState.value.currentPage
            val alreadyBookmarked = page in _uiState.value.bookmarkedPages
            viewModelScope.launch {
                if (alreadyBookmarked) {
                    pageBookmarkDao.delete(documentId, page)
                } else {
                    pageBookmarkDao.insert(PageBookmarkEntity(documentId, page, System.currentTimeMillis()))
                }
            }
        }

        fun removeBookmark(page: Int) {
            val documentId = _uiState.value.document?.id ?: return
            viewModelScope.launch { pageBookmarkDao.delete(documentId, page) }
        }

        fun showBookmarksSheet() {
            _uiState.update { it.copy(showBookmarksSheet = true) }
        }

        fun dismissBookmarksSheet() {
            _uiState.update { it.copy(showBookmarksSheet = false) }
        }

        fun navigateToBookmark(page: Int) {
            _uiState.update { it.copy(showBookmarksSheet = false, pendingPageJump = page) }
        }

        // PdfViewerContent lo llama apenas termina de saltar -- evita que el
        // mismo salto se repita en la siguiente recomposición (ej. al rotar la
        // pantalla, que recompone con el mismo pendingPageJump si no se limpia).
        fun onPageJumpConsumed() {
            _uiState.update { it.copy(pendingPageJump = null) }
        }

        fun toggleFavorite() {
            // Hallazgo real de la revisión adversarial de seguridad sobre V1
            // (auditoría general 2026-09-17, quinta pasada): a diferencia de
            // renombrar/eliminar/anotar, este acceso nunca chequeaba
            // isReadOnlyPreview -- durante la vista previa de Carpeta Segura,
            // `document.id` es la ruta efímera de `secure_preview/`, que
            // quedaría persistida como favorito en `FavoritesRepository` mucho
            // después de que el archivo temporal se borre. Defensa en
            // profundidad, mismo criterio que los demás accesos ya protegidos.
            if (_uiState.value.isReadOnlyPreview) return
            val document = _uiState.value.document ?: return
            viewModelScope.launch {
                val isNowFavorite = favoritesRepository.toggleFavorite(document.id)
                if (isNowFavorite) DocuSmartAnalytics.logDocumentFavorited(document.type.name)
                _uiState.update { state ->
                    state.copy(
                        isFavorite = isNowFavorite,
                        document = state.document?.copy(isFavorite = isNowFavorite),
                    )
                }
                Timber.d("$TAG: toggleFavorite ${document.id} → $isNowFavorite")
            }
        }

        fun toggleControls() {
            _uiState.update { it.copy(showControls = !it.showControls) }
        }

        // ── RF-VIS-06: renombrar/eliminar desde el Visor ──────────────────────────
        fun onRenameClick() {
            _uiState.update { it.copy(showRenameDialog = true) }
        }

        fun dismissRenameDialog() {
            _uiState.update { it.copy(showRenameDialog = false) }
        }

        fun renameDocument(newName: String) {
            val document = _uiState.value.document ?: return
            val trimmed = newName.trim()
            // Defensa en profundidad, mismo criterio que toggleAnnotationToolbar:
            // renombrar durante una vista previa de Carpeta Segura solo tocaría
            // la copia efímera de caché, no el archivo protegido real.
            if (_uiState.value.isReadOnlyPreview || trimmed.isBlank() || trimmed == document.name) {
                _uiState.update { it.copy(showRenameDialog = false) }
                return
            }
            viewModelScope.launch {
                val newId = documentRepository.renameDocument(document.id, trimmed)
                val newUri = if (newId != document.id) Uri.fromFile(File(newId)) else _uiState.value.fileUri
                _uiState.update { state ->
                    state.copy(
                        showRenameDialog = false,
                        document = state.document?.copy(id = newId, name = trimmed),
                        fileUri = newUri,
                    )
                }
                // Hallazgo real de la revisión general 2026-09-16 (cuarta
                // pasada): DocumentRepository.renameDocument() ya migra las
                // anotaciones al id nuevo (hallazgo #48), pero acá nunca se
                // volvía a suscribir con ese id -- la suscripción activa
                // seguía filtrando por el id VIEJO (sin filas), así que Room
                // emitía lista vacía y los resaltados/notas desaparecían de la
                // pantalla hasta reabrir el documento (los datos seguían
                // intactos bajo el id nuevo). Mismo criterio para marcadores de
                // página (#47) -- la última página vista (#48) no necesita
                // re-suscripción porque solo se lee una vez al abrir.
                if (newId != document.id) {
                    observeAnnotations(newId)
                    observeBookmarks(newId)
                    observeLinkedNotes(newId)
                }
                Timber.d("$TAG: renameDocument ${document.id} → $newId ($trimmed)")
            }
        }

        fun onDeleteClick() {
            _uiState.update { it.copy(showDeleteConfirm = true) }
        }

        fun dismissDeleteConfirm() {
            _uiState.update { it.copy(showDeleteConfirm = false) }
        }

        fun dismissDeleteError() {
            _uiState.update { it.copy(deleteError = null) }
        }

        // RF-VIS-07: "eliminar" mueve a la papelera, no borra de inmediato -- ver
        // DocumentRepository.moveToTrash().
        fun confirmDelete(context: Context) {
            // Hallazgo real de la revisión general 2026-09-16 (cuarta pasada):
            // "Eliminar" durante una vista previa de Carpeta Segura llamaba
            // trashRepository.moveToTrash() con el id de la copia efímera de
            // caché -- eso solo insertaba una fila de papelera huérfana, sin
            // tocar el archivo protegido real, mientras la UI mostraba el
            // borrado como exitoso.
            if (_uiState.value.isReadOnlyPreview) {
                _uiState.update { it.copy(showDeleteConfirm = false) }
                return
            }
            val documentId = _uiState.value.document?.id ?: return
            viewModelScope.launch {
                val movedToTrash = trashRepository.moveToTrash(documentId)
                if (movedToTrash) {
                    _uiState.update { it.copy(showDeleteConfirm = false, documentDeleted = true) }
                } else {
                    // Bug real encontrado 2026-09-14: hardcodeado en español --
                    // reusa general_delete_error (mismo mensaje que Library/Trash/
                    // Home para este mismo escenario).
                    _uiState.update {
                        it.copy(showDeleteConfirm = false, deleteError = context.getString(R.string.general_delete_error))
                    }
                }
                Timber.d("$TAG: confirmDelete $documentId → $movedToTrash")
            }
        }

        // ── Búsqueda dentro de PDF ─────────────────────────────────────────────────
        // RF-VIS-08: además de saltar entre páginas con coincidencias, se guarda
        // la posición real de cada una (`pdfSearchHighlights`) para que
        // `PdfViewerContent` dibuje el resaltado directamente sobre el bitmap ya
        // renderizado (ver SearchPdfTextUseCase).
        fun searchInPdf(query: String) {
            val uri = _uiState.value.fileUri
            if (uri == null || query.isBlank()) {
                _uiState.update {
                    it.copy(pdfSearchMatches = emptyList(), pdfSearchIndex = -1, pdfSearchHighlights = emptyMap())
                }
                return
            }
            searchJob?.cancel()
            searchJob =
                viewModelScope.launch {
                    val results = searchPdfText(uri, query)
                    _uiState.update {
                        it.copy(
                            pdfSearchMatches = results.map { r -> r.pageNumber },
                            pdfSearchIndex = if (results.isEmpty()) -1 else 0,
                            pdfSearchHighlights = results.associate { r -> r.pageNumber to r.rects },
                        )
                    }
                }
        }

        fun nextPdfSearchResult() {
            _uiState.update { state ->
                if (state.pdfSearchMatches.isEmpty()) {
                    state
                } else {
                    state.copy(pdfSearchIndex = (state.pdfSearchIndex + 1) % state.pdfSearchMatches.size)
                }
            }
        }

        fun previousPdfSearchResult() {
            _uiState.update { state ->
                if (state.pdfSearchMatches.isEmpty()) {
                    state
                } else {
                    state.copy(
                        pdfSearchIndex =
                            (state.pdfSearchIndex - 1 + state.pdfSearchMatches.size) %
                                state.pdfSearchMatches.size,
                    )
                }
            }
        }

        fun clearPdfSearch() {
            _uiState.update {
                it.copy(pdfSearchMatches = emptyList(), pdfSearchIndex = -1, pdfSearchHighlights = emptyMap())
            }
        }

        // Bug real reportado por el usuario 2026-09-03: compartir un documento
        // generado por la app (id = ruta absoluta, ver resolveUri()) dejó de
        // funcionar porque fileUri queda como Uri.fromFile(...) -- un file://
        // real. Desde Android 7 (API 24) exponer un file:// a otra app vía
        // Intent lanza FileUriExposedException (hereda de SecurityException),
        // que el catch genérico de abajo atrapaba silenciosamente como "No se
        // pudo compartir". Biblioteca/Home ya resolvían esto con FileProvider
        // (ver DocumentListSection.kt/FavoritesSection.kt) -- el Visor nunca lo
        // hizo. Se envuelve acá con el mismo FileProvider/authority ya
        // declarado en el manifest.
        private fun shareableUri(
            context: Context,
            uri: Uri,
        ): Uri {
            val path = uri.path
            if (uri.scheme != "file" || path == null) return uri
            return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(path))
        }

        // HU-46 (RNF2): un documento SIN anotaciones comparte exactamente igual
        // que siempre (AC2, sin diálogo de por medio) -- el diálogo "con
        // anotaciones/original" solo aparece si hay algo que aplanar.
        fun shareDocument(context: Context) {
            // Hallazgo real de la revisión adversarial de seguridad sobre V1
            // (auditoría general 2026-09-17, quinta pasada): "Compartir" no
            // tenía el mismo guard que renombrar/eliminar/anotar. Hoy no es
            // explotable (file_provider_paths.xml no declara `secure_preview/`,
            // así que FileProvider rechaza la URI), pero esa protección vive
            // en un XML no relacionado -- defensa en profundidad acá, igual
            // que en toggleFavorite().
            if (_uiState.value.isReadOnlyPreview) return
            val hasAnnotations =
                _uiState.value.annotations.values
                    .any { it.isNotEmpty() }
            if (hasAnnotations) {
                _uiState.update { it.copy(showShareChoiceDialog = true) }
            } else {
                shareOriginal(context)
            }
        }

        fun dismissShareChoiceDialog() {
            _uiState.update { it.copy(showShareChoiceDialog = false) }
        }

        fun shareOriginal(context: Context) {
            _uiState.update { it.copy(showShareChoiceDialog = false) }
            val state = _uiState.value
            // Se llama también directo desde el diálogo "Original/Con
            // anotaciones" (ViewerScreen.kt), sin pasar por shareDocument() --
            // mismo guard acá, ver el comentario de shareDocument().
            if (state.isReadOnlyPreview) return
            val document = state.document ?: return
            try {
                val shareIntent =
                    Intent(Intent.ACTION_SEND).apply {
                        type = state.mimeType ?: MIME_PDF
                        state.fileUri?.let { uri ->
                            putExtra(Intent.EXTRA_STREAM, shareableUri(context, uri))
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        putExtra(Intent.EXTRA_SUBJECT, document.name)
                    }
                // Bug real encontrado 2026-09-14 (repaso general): hardcodeado
                // en español, saltándose el sistema de idiomas.
                val chooserTitle =
                    String.format(
                        context.getString(R.string.viewer_share_chooser_title),
                        document.name,
                    )
                context.startActivity(Intent.createChooser(shareIntent, chooserTitle))
            } catch (e: Exception) {
                Timber.e(e, "$TAG: error compartiendo documento")
                // Bug real encontrado 2026-09-14: hardcodeado en español --
                // reusa pdf_tools_share_error (mismo mensaje que Herramientas PDF
                // para este mismo escenario).
                _uiState.update { it.copy(shareError = context.getString(R.string.pdf_tools_share_error)) }
            }
        }

        fun dismissShareError() {
            _uiState.update { it.copy(shareError = null) }
        }

        // HU-46 (RNF1): aplana sobre una COPIA en caché (FlattenAnnotationsPdfUseCase)
        // y comparte esa copia -- el archivo original (state.fileUri) nunca se
        // toca ni se sobrescribe.
        fun shareWithAnnotations(context: Context) {
            val state = _uiState.value
            // Mismo guard que shareOriginal()/shareDocument() -- se llama
            // directo desde el diálogo "Original/Con anotaciones".
            if (state.isReadOnlyPreview) {
                _uiState.update { it.copy(showShareChoiceDialog = false) }
                return
            }
            val document = state.document
            val sourceUri = state.fileUri
            if (document == null || sourceUri == null) {
                // Hallazgo de la revisión de seguridad HU-46: los `?: return`
                // tempranos dejaban isFlatteningForShare/showShareChoiceDialog
                // sin resetear si document/fileUri fueran null en este punto.
                _uiState.update { it.copy(showShareChoiceDialog = false) }
                return
            }
            _uiState.update { it.copy(showShareChoiceDialog = false, isFlatteningForShare = true) }
            val allAnnotations = state.annotations.values.flatten()
            viewModelScope.launch {
                val flattened = flattenAnnotationsPdfUseCase(sourceUri, allAnnotations)
                _uiState.update { it.copy(isFlatteningForShare = false) }
                if (flattened == null) {
                    _uiState.update { it.copy(shareError = context.getString(R.string.pdf_tools_share_error)) }
                    return@launch
                }
                try {
                    val shareIntent =
                        Intent(Intent.ACTION_SEND).apply {
                            type = MIME_PDF
                            putExtra(Intent.EXTRA_STREAM, shareableUri(context, Uri.fromFile(flattened)))
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            putExtra(Intent.EXTRA_SUBJECT, document.name)
                        }
                    val chooserTitle =
                        String.format(
                            context.getString(R.string.viewer_share_chooser_title),
                            document.name,
                        )
                    context.startActivity(Intent.createChooser(shareIntent, chooserTitle))
                } catch (e: Exception) {
                    Timber.e(e, "$TAG: error compartiendo documento anotado")
                    _uiState.update { it.copy(shareError = context.getString(R.string.pdf_tools_share_error)) }
                }
            }
        }

        // ── HU-46: anotaciones (resaltado + notas adhesivas) ──────────────────────
        fun setAnnotationMode(mode: AnnotationMode) {
            _uiState.update { it.copy(annotationMode = mode, pendingNoteAnchor = null, pendingNotePage = null) }
        }

        // Hallazgo real de la revisión general 2026-09-16: showAnnotationToolbar
        // y annotationMode deben cambiar siempre juntos -- ver el comentario en
        // ViewerUiState. toggleAnnotationToolbar() es el único punto que los
        // toca a los dos, evitando que queden desincronizados otra vez.
        fun toggleAnnotationToolbar() {
            // Defensa en profundidad: la UI ya oculta el botón "Anotar" en modo
            // de solo lectura (ver ViewerUiState.isReadOnlyPreview), pero este
            // es el único punto de entrada real a anotar -- guardarlo acá
            // también cierra el hueco aunque algo llegue a invocarlo igual.
            if (_uiState.value.isReadOnlyPreview) return
            val opening = !_uiState.value.showAnnotationToolbar
            _uiState.update {
                it.copy(
                    showAnnotationToolbar = opening,
                    annotationMode = if (opening) it.annotationMode else AnnotationMode.NONE,
                )
            }
        }

        fun closeAnnotationToolbar() {
            _uiState.update { it.copy(showAnnotationToolbar = false, annotationMode = AnnotationMode.NONE) }
        }

        fun setHighlightColor(colorArgb: Int) {
            _uiState.update { it.copy(selectedHighlightColor = colorArgb) }
        }

        fun addHighlight(
            page: Int,
            rect: PdfRectPts,
        ) {
            val documentId = _uiState.value.document?.id ?: return
            val color = _uiState.value.selectedHighlightColor
            viewModelScope.launch {
                annotationDao.insert(
                    AnnotationEntity(
                        id = UUID.randomUUID().toString(),
                        documentId = documentId,
                        type = AnnotationType.HIGHLIGHT,
                        page = page,
                        xPts = rect.xPts,
                        yPts = rect.yPts,
                        widthPts = rect.widthPts,
                        heightPts = rect.heightPts,
                        color = color,
                        text = "",
                        createdAt = System.currentTimeMillis(),
                    ),
                )
            }
        }

        // RF2: tocar un punto en modo NOTE no guarda de inmediato -- deja el
        // anclaje pendiente hasta que el usuario escribe el texto (o cancela).
        fun requestAddNote(
            page: Int,
            anchor: PdfRectPts,
        ) {
            _uiState.update { it.copy(pendingNoteAnchor = anchor, pendingNotePage = page) }
        }

        fun cancelPendingNote() {
            _uiState.update { it.copy(pendingNoteAnchor = null, pendingNotePage = null) }
        }

        fun confirmNote(text: String) {
            if (text.isBlank()) {
                cancelPendingNote()
                return
            }
            val state = _uiState.value
            val documentId = state.document?.id
            val anchor = state.pendingNoteAnchor
            val page = state.pendingNotePage
            if (documentId == null || anchor == null || page == null) return
            viewModelScope.launch {
                annotationDao.insert(
                    AnnotationEntity(
                        id = UUID.randomUUID().toString(),
                        documentId = documentId,
                        type = AnnotationType.NOTE,
                        page = page,
                        xPts = anchor.xPts,
                        yPts = anchor.yPts,
                        widthPts = 0f,
                        heightPts = 0f,
                        color = ANNOTATION_NOTE_COLOR,
                        text = text.trim().take(MAX_NOTE_LENGTH),
                        createdAt = System.currentTimeMillis(),
                    ),
                )
                _uiState.update { it.copy(pendingNoteAnchor = null, pendingNotePage = null) }
            }
        }

        fun viewAnnotation(annotation: AnnotationEntity) {
            _uiState.update { it.copy(viewingAnnotation = annotation) }
        }

        fun dismissAnnotationDetail() {
            _uiState.update { it.copy(viewingAnnotation = null) }
        }

        fun deleteAnnotation(id: String) {
            viewModelScope.launch {
                annotationDao.delete(id)
                _uiState.update { it.copy(viewingAnnotation = null) }
            }
        }

        override fun onCleared() {
            super.onCleared()
            _uiState.value.decryptedFile?.delete()
        }

        private fun getMockDocument(id: String): DocumentUiModel? {
            val mockDocs =
                mapOf(
                    "1" to DocumentUiModel("1", "Contrato_Servicios_2024.pdf", DocumentType.PDF, "2.4 MB", "01/05/2026", true),
                    "2" to DocumentUiModel("2", "Informe_Trimestral.docx", DocumentType.WORD, "1.1 MB", "30/04/2026"),
                    "3" to DocumentUiModel("3", "Presupuesto_Q1.xlsx", DocumentType.EXCEL, "890 KB", "29/04/2026"),
                    "4" to DocumentUiModel("4", "Presentacion_Clientes.pptx", DocumentType.POWERPOINT, "5.2 MB", "28/04/2026"),
                    "5" to DocumentUiModel("5", "Foto_Documento.jpg", DocumentType.IMAGE, "3.8 MB", "27/04/2026"),
                    "6" to DocumentUiModel("6", "Manual_Usuario.pdf", DocumentType.PDF, "4.1 MB", "26/04/2026", true),
                    "7" to DocumentUiModel("7", "Notas_Reunion.txt", DocumentType.TEXT, "12 KB", "25/04/2026"),
                    "8" to DocumentUiModel("8", "Backup_Documentos.zip", DocumentType.ZIP, "45 MB", "24/04/2026"),
                    "9" to DocumentUiModel("9", "Escaneo_Factura.pdf", DocumentType.OCR, "1.8 MB", "23/04/2026"),
                    "10" to DocumentUiModel("10", "Reporte_Ventas.xlsx", DocumentType.EXCEL, "2.2 MB", "22/04/2026", true),
                    "11" to DocumentUiModel("11", "Carta_Presentacion.docx", DocumentType.WORD, "340 KB", "21/04/2026"),
                    "12" to DocumentUiModel("12", "Logo_Empresa.png", DocumentType.IMAGE, "890 KB", "20/04/2026"),
                )
            return mockDocs[id]
        }
    }
