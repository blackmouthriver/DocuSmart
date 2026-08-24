package com.docsmart.features.viewer.presentation

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docsmart.core.data.FavoritesRepository
import com.docsmart.core.ui.components.DocumentType
import com.docsmart.core.ui.components.DocumentUiModel
import com.docsmart.features.viewer.domain.usecase.SearchPdfTextUseCase
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.ReaderProperties
import com.itextpdf.kernel.pdf.WriterProperties
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject

data class ViewerUiState(
    val document         : DocumentUiModel? = null,
    val currentPage      : Int     = 0,
    val totalPages       : Int     = 0,
    val isLoading        : Boolean = true,
    val error            : String? = null,
    val isFavorite       : Boolean = false,
    val showControls     : Boolean = true,
    val fileUri          : Uri?    = null,
    val mimeType         : String? = null,
    val requiresPassword : Boolean = false,
    val passwordError    : String? = null,
    val decryptedFile    : File?   = null,
    val pdfSearchMatches : List<Int> = emptyList(), // páginas (1-based) con coincidencias
    val pdfSearchIndex   : Int     = -1             // índice actual dentro de pdfSearchMatches
)

@HiltViewModel
class ViewerViewModel @Inject constructor(
    private val favoritesRepository: FavoritesRepository,
    private val searchPdfText: SearchPdfTextUseCase
) : ViewModel() {

    companion object {
        private const val TAG = "ViewerViewModel"
    }

    private val _uiState = MutableStateFlow(ViewerUiState())
    val uiState: StateFlow<ViewerUiState> = _uiState.asStateFlow()

    private var pendingDocumentId: String  = ""
    private var pendingContext   : Context? = null

    fun loadDocument(documentId: String, context: Context) {
        pendingDocumentId = documentId
        pendingContext    = context.applicationContext
        Timber.d("$TAG: loadDocument START → id=$documentId")
        viewModelScope.launch {
            _uiState.update { it.copy(
                isLoading        = true,
                error            = null,
                requiresPassword = false,
                passwordError    = null
            )}
            try {
                val isRealUri = documentId.startsWith("content://") ||
                        documentId.startsWith("file://")  ||
                        documentId.startsWith("content%3A") ||
                        documentId.startsWith("/")
                Timber.d("$TAG: isRealUri=$isRealUri")
                if (isRealUri) loadFromUri(documentId, context)
                else           loadFromMock(documentId)
            } catch (e: Exception) {
                Timber.e(e, "$TAG: error inesperado → ${e.javaClass.name}: ${e.message}")
                _uiState.update { it.copy(
                    isLoading = false,
                    error     = "Error al cargar el documento"
                )}
            }
        }
    }

    private suspend fun loadFromUri(documentId: String, context: Context) {
        withContext(Dispatchers.IO) {
            // uriString es el mismo id que usan Biblioteca/Home como clave de
            // favoritos/alias (FavoritesRepository) — para rutas absolutas debe
            // quedar SIN el prefijo "file://", si no los favoritos/alias
            // marcados desde el Visor no coinciden con los de Biblioteca/Home.
            val uriString = when {
                documentId.startsWith("content%3A") -> Uri.decode(documentId)
                else                                -> documentId
            }

            val uri = if (documentId.startsWith("/")) {
                Uri.fromFile(File(documentId))
            } else {
                Uri.parse(uriString)
            }

            val rawName  = resolveFileName(uriString, context)
            val fileName = favoritesRepository.getAlias(uriString) ?: rawName

            val mimeType: String = run {
                val fromResolver  = try {
                    context.contentResolver.getType(uri)
                } catch (e: Exception) { null }
                val fromExtension = resolveMimeTypeByExtension(fileName)
                    ?: resolveMimeType(uriString)
                when {
                    fromResolver == null                        -> fromExtension ?: "application/octet-stream"
                    fromResolver == "application/octet-stream" -> fromExtension ?: fromResolver
                    fromResolver.contains("*")                 -> fromExtension ?: fromResolver
                    else                                       -> fromResolver
                }
            }

            Timber.d("$TAG: uri=$uri mimeType=$mimeType fileName=$fileName")

            val isPdf = mimeType.contains("pdf") ||
                    fileName.endsWith(".pdf", ignoreCase = true)

            if (isPdf) {
                val isProtected = isPdfPasswordProtected(uri, context, documentId)
                Timber.d("$TAG: isPdf=$isPdf isProtected=$isProtected")

                if (isProtected) {
                    val isFavorite = favoritesRepository.isFavorite(uriString)
                    val document   = DocumentUiModel(
                        id         = uriString,
                        name       = fileName,
                        type       = DocumentType.PDF,
                        size       = "",
                        date       = "",
                        isFavorite = isFavorite
                    )
                    _uiState.update { state ->
                        state.copy(
                            document         = document,
                            fileUri          = uri,
                            mimeType         = mimeType,
                            isFavorite       = isFavorite,
                            isLoading        = false,
                            requiresPassword = true,
                            error            = null
                        )
                    }
                    return@withContext
                }
            }

            val documentType = detectDocumentType(mimeType)
            val isFavorite   = favoritesRepository.isFavorite(uriString)

            val document = DocumentUiModel(
                id         = uriString,
                name       = fileName,
                type       = documentType,
                size       = "",
                date       = "",
                isFavorite = isFavorite
            )

            _uiState.update { state ->
                state.copy(
                    document   = document,
                    fileUri    = uri,
                    mimeType   = mimeType,
                    isFavorite = isFavorite,
                    isLoading  = false,
                    error      = null
                )
            }
        }
    }

    private fun isPdfPasswordProtected(
        uri       : Uri,
        context   : Context,
        originalId: String = ""
    ): Boolean {
        val cacheFile = File(context.cacheDir, "temp_check_${System.currentTimeMillis()}.pdf")
        return try {
            val copied = when {
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
                val doc    = PdfDocument(reader)
                doc.close()
                isEncrypted = false
            } catch (e: Exception) {
                val msg = e.message?.lowercase() ?: ""
                isEncrypted = msg.contains("password") ||
                        msg.contains("encrypt")  ||
                        msg.contains("decrypt")  ||
                        msg.contains("bad user") ||
                        msg.contains("owner")    ||
                        msg.contains("pdf header")
                Timber.d("$TAG: PdfDocument excepción → clase=${e.javaClass.simpleName} msg=${e.message} isEncrypted=$isEncrypted")
            }
            Timber.d("$TAG: PDF isEncrypted=$isEncrypted")
            isEncrypted

        } catch (e: Exception) {
            val msg = e.message?.lowercase() ?: ""
            Timber.e("$TAG: isPdfPasswordProtected EXCEPCION → clase=${e.javaClass.name} msg=${e.message}")
            msg.contains("password") || msg.contains("encrypt") ||
                    msg.contains("decrypt")  || msg.contains("bad user") ||
                    msg.contains("owner")
        } finally {
            if (cacheFile.exists()) cacheFile.delete()
        }
    }

    // ── Desbloquear PDF con contraseña ────────────────────────────────────────
    fun unlockPdfWithPassword(password: String) {
        val context    = pendingContext ?: return
        val uri        = _uiState.value.fileUri ?: return
        val originalId = pendingDocumentId

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, passwordError = null) }

            withContext(Dispatchers.IO) {
                try {
                    val cacheIn = File(context.cacheDir, "temp_locked_${System.currentTimeMillis()}.pdf")

                    // ── Copiar archivo original al caché ──────────────────────
                    when {
                        originalId.startsWith("/") -> {
                            val src = File(originalId)
                            Timber.d("$TAG: unlock ruta absoluta path=$originalId existe=${src.exists()}")
                            if (!src.exists()) {
                                _uiState.update { it.copy(isLoading = false, passwordError = "No se pudo leer el archivo") }
                                return@withContext
                            }
                            src.copyTo(cacheIn, overwrite = true)
                        }
                        uri.scheme == "file" -> {
                            val path = uri.path ?: return@withContext
                            val src  = File(path)
                            Timber.d("$TAG: unlock file:// path=$path existe=${src.exists()}")
                            if (!src.exists()) {
                                _uiState.update { it.copy(isLoading = false, passwordError = "No se pudo leer el archivo") }
                                return@withContext
                            }
                            src.copyTo(cacheIn, overwrite = true)
                        }
                        else -> {
                            context.contentResolver.openInputStream(uri)?.use { input ->
                                cacheIn.outputStream().use { output -> input.copyTo(output) }
                            }
                        }
                    }

                    Timber.d("$TAG: cacheIn copiado → ${cacheIn.length()}b")

                    val userPass    = password.toByteArray()
                    val readerProps = ReaderProperties().setPassword(userPass)

                    // ── Verificar contraseña ──────────────────────────────────
                    val testReader = try {
                        val r = PdfReader(cacheIn.absolutePath, readerProps)
                        r.setUnethicalReading(true)
                        r
                    } catch (e: Exception) {
                        cacheIn.delete()
                        Timber.w("$TAG: contraseña incorrecta → ${e.message}")
                        _uiState.update { it.copy(
                            isLoading     = false,
                            passwordError = "Contraseña incorrecta. Intenta de nuevo."
                        )}
                        return@withContext
                    }
                    testReader.close()

                    val cacheOut = File(context.cacheDir, "unlocked_${System.currentTimeMillis()}.pdf")
                    var success  = false

                    // ── Intento 1: copyPagesTo (sin encriptación) ─────────────
                    try {
                        val r1 = PdfReader(cacheIn.absolutePath, readerProps)
                        r1.setUnethicalReading(true)
                        r1.setMemorySavingMode(true)
                        val srcDoc  = PdfDocument(r1)
                        val destDoc = PdfDocument(PdfWriter(cacheOut.absolutePath, WriterProperties()))
                        srcDoc.copyPagesTo(1, srcDoc.numberOfPages, destDoc)
                        srcDoc.close()
                        destDoc.close()
                        Timber.d("$TAG: Intento 1 copyPagesTo → ${cacheOut.length()}b")

                        // ── Verificar que el output NO está encriptado ────────
                        val isStillEncrypted = try {
                            val vr  = PdfReader(cacheOut.absolutePath)
                            val enc = vr.isEncrypted
                            PdfDocument(vr).close()
                            enc
                        } catch (ve: Exception) {
                            val msg = ve.message?.lowercase() ?: ""
                            Timber.e("$TAG: verificación output → ${ve.message}")
                            msg.contains("password") || msg.contains("encrypt")
                        }

                        Timber.d("$TAG: OUTPUT isStillEncrypted=$isStillEncrypted")
                        success = !isStillEncrypted

                    } catch (e1: Exception) {
                        Timber.w("$TAG: Intento 1 falló → ${e1.message}")
                    }

                    // ── Intento 2: PdfDocument directo si copyPagesTo falla ───
                    if (!success) {
                        try {
                            if (cacheOut.exists()) cacheOut.delete()
                            val r2  = PdfReader(cacheIn.absolutePath, readerProps)
                            r2.setUnethicalReading(true)
                            r2.setMemorySavingMode(true)
                            val doc = PdfDocument(r2, PdfWriter(cacheOut.absolutePath, WriterProperties()))
                            doc.close()
                            Timber.d("$TAG: Intento 2 PdfDocument directo → ${cacheOut.length()}b")

                            val isStillEncrypted2 = try {
                                val vr  = PdfReader(cacheOut.absolutePath)
                                val enc = vr.isEncrypted
                                PdfDocument(vr).close()
                                enc
                            } catch (ve: Exception) {
                                val msg = ve.message?.lowercase() ?: ""
                                msg.contains("password") || msg.contains("encrypt")
                            }

                            Timber.d("$TAG: OUTPUT2 isStillEncrypted=$isStillEncrypted2")
                            success = !isStillEncrypted2

                        } catch (e2: Exception) {
                            Timber.w("$TAG: Intento 2 falló → ${e2.message}")
                        }
                    }

                    // ── Intento 3: StampingProperties sin appendMode ──────────
                    if (!success) {
                        try {
                            if (cacheOut.exists()) cacheOut.delete()
                            val r3     = PdfReader(cacheIn.absolutePath, readerProps)
                            r3.setUnethicalReading(true)
                            val stamps = com.itextpdf.kernel.pdf.StampingProperties()
                            val doc3   = PdfDocument(r3, PdfWriter(cacheOut.absolutePath, WriterProperties()), stamps)
                            doc3.close()
                            Timber.d("$TAG: Intento 3 StampingProperties → ${cacheOut.length()}b")
                            success = cacheOut.exists() && cacheOut.length() > 100L
                        } catch (e3: Exception) {
                            Timber.w("$TAG: Intento 3 falló → ${e3.message}")
                        }
                    }

                    cacheIn.delete()

                    if (!success || !cacheOut.exists() || cacheOut.length() < 100L) {
                        Timber.e("$TAG: todos los intentos fallaron")
                        _uiState.update { it.copy(
                            isLoading     = false,
                            passwordError = "No se pudo desencriptar el PDF"
                        )}
                        return@withContext
                    }

                    Timber.d("$TAG: PDF desbloqueado exitosamente → ${cacheOut.length()}b")
                    _uiState.update { state ->
                        state.copy(
                            isLoading        = false,
                            requiresPassword = false,
                            passwordError    = null,
                            decryptedFile    = cacheOut,
                            fileUri          = Uri.fromFile(cacheOut),
                            mimeType         = "application/pdf",
                            error            = null,
                            document         = state.document?.copy()
                        )
                    }

                } catch (e: Exception) {
                    Timber.e(e, "$TAG: error desbloqueando PDF → ${e.message}")
                    _uiState.update { it.copy(
                        isLoading     = false,
                        passwordError = "No se pudo abrir el PDF: ${e.message}"
                    )}
                }
            }
        }
    }

    fun dismissPasswordDialog() {
        pendingDocumentId = ""
        pendingContext    = null
        _uiState.update { it.copy(
            requiresPassword = false,
            passwordError    = null,
            isLoading        = false
        )}
    }

    private fun detectDocumentType(mimeType: String): DocumentType = when {
        mimeType.contains("image")                                              -> DocumentType.IMAGE
        mimeType.contains("pdf")                                                -> DocumentType.PDF
        mimeType.contains("word")  || mimeType.contains("msword") ||
                mimeType.contains("wordprocessingml")                           -> DocumentType.WORD
        mimeType.contains("excel") || mimeType.contains("sheet") ||
                mimeType.contains("spreadsheet")                                -> DocumentType.EXCEL
        mimeType.contains("powerpoint") || mimeType.contains("presentation")   -> DocumentType.POWERPOINT
        mimeType.contains("text")                                               -> DocumentType.TEXT
        else                                                                    -> DocumentType.PDF
    }

    private fun loadFromMock(id: String) {
        val mockDocument = getMockDocument(id)
        val isFavorite   = mockDocument?.let {
            favoritesRepository.isFavorite(it.id)
        } ?: false

        _uiState.update { state ->
            state.copy(
                document   = mockDocument,
                isFavorite = isFavorite,
                isLoading  = false,
                error      = if (mockDocument == null) "Documento no encontrado" else null
            )
        }
    }

    private fun resolveFileName(uriString: String, context: Context): String {
        return try {
            val uri  = Uri.parse(uriString)
            var name = "Documento"
            context.contentResolver.query(
                uri,
                arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) name = cursor.getString(0) ?: "Documento"
            }
            if (name == "Documento" && uriString.startsWith("/")) {
                name = File(uriString).name
            }
            name
        } catch (e: Exception) {
            Timber.w("$TAG: no se pudo resolver nombre — ${e.message}")
            if (uriString.startsWith("/")) File(uriString).name
            else Uri.parse(uriString).lastPathSegment?.substringAfterLast("/") ?: "Documento"
        }
    }

    private fun resolveMimeType(uriString: String): String? = when {
        uriString.contains("image")                                -> "image/jpeg"
        uriString.endsWith(".pdf",  ignoreCase = true)             -> "application/pdf"
        uriString.endsWith(".docx", ignoreCase = true)             -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        uriString.endsWith(".doc",  ignoreCase = true)             -> "application/msword"
        uriString.endsWith(".xlsx", ignoreCase = true)             -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        uriString.endsWith(".xls",  ignoreCase = true)             -> "application/vnd.ms-excel"
        uriString.endsWith(".jpg",  ignoreCase = true) ||
                uriString.endsWith(".jpeg", ignoreCase = true)             -> "image/jpeg"
        uriString.endsWith(".png",  ignoreCase = true)             -> "image/png"
        uriString.endsWith(".txt",  ignoreCase = true)             -> "text/plain"
        else                                                       -> null
    }

    private fun resolveMimeTypeByExtension(fileName: String): String? {
        val ext = fileName.substringAfterLast(".", "").lowercase()
        return when (ext) {
            "pdf"        -> "application/pdf"
            "docx"       -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "doc"        -> "application/msword"
            "xlsx"       -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "xls"        -> "application/vnd.ms-excel"
            "pptx"       -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            "ppt"        -> "application/vnd.ms-powerpoint"
            "jpg","jpeg" -> "image/jpeg"
            "png"        -> "image/png"
            "webp"       -> "image/webp"
            "gif"        -> "image/gif"
            "txt"        -> "text/plain"
            "md"         -> "text/markdown"
            "csv"        -> "text/csv"
            else         -> null
        }
    }

    fun onPageChanged(page: Int, total: Int) {
        _uiState.update { it.copy(currentPage = page, totalPages = total) }
    }

    fun toggleFavorite() {
        val documentId = _uiState.value.document?.id ?: return
        viewModelScope.launch {
            val isNowFavorite = favoritesRepository.toggleFavorite(documentId)
            _uiState.update { state ->
                state.copy(
                    isFavorite = isNowFavorite,
                    document   = state.document?.copy(isFavorite = isNowFavorite)
                )
            }
            Timber.d("$TAG: toggleFavorite $documentId → $isNowFavorite")
        }
    }

    fun toggleControls() {
        _uiState.update { it.copy(showControls = !it.showControls) }
    }

    // ── Búsqueda dentro de PDF ─────────────────────────────────────────────────
    // Los PDF se muestran como bitmaps renderizados, así que no hay resaltado
    // inline como en Word/Excel/Texto: se buscan las páginas con coincidencias
    // y se navega entre ellas (ver SearchPdfTextUseCase).
    fun searchInPdf(query: String) {
        val uri = _uiState.value.fileUri
        if (uri == null || query.isBlank()) {
            _uiState.update { it.copy(pdfSearchMatches = emptyList(), pdfSearchIndex = -1) }
            return
        }
        viewModelScope.launch {
            val matches = searchPdfText(uri, query)
            _uiState.update {
                it.copy(
                    pdfSearchMatches = matches,
                    pdfSearchIndex   = if (matches.isEmpty()) -1 else 0
                )
            }
        }
    }

    fun nextPdfSearchResult() {
        _uiState.update { state ->
            if (state.pdfSearchMatches.isEmpty()) state
            else state.copy(pdfSearchIndex = (state.pdfSearchIndex + 1) % state.pdfSearchMatches.size)
        }
    }

    fun previousPdfSearchResult() {
        _uiState.update { state ->
            if (state.pdfSearchMatches.isEmpty()) state
            else state.copy(
                pdfSearchIndex = (state.pdfSearchIndex - 1 + state.pdfSearchMatches.size) %
                    state.pdfSearchMatches.size
            )
        }
    }

    fun clearPdfSearch() {
        _uiState.update { it.copy(pdfSearchMatches = emptyList(), pdfSearchIndex = -1) }
    }

    fun shareDocument(context: Context) {
        val state    = _uiState.value
        val document = state.document ?: return
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = state.mimeType ?: "application/pdf"
                state.fileUri?.let { uri ->
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                putExtra(Intent.EXTRA_SUBJECT, document.name)
            }
            context.startActivity(
                Intent.createChooser(shareIntent, "Compartir ${document.name}")
            )
        } catch (e: Exception) {
            Timber.e(e, "$TAG: error compartiendo documento")
            _uiState.update { it.copy(error = "No se pudo compartir el archivo") }
        }
    }

    override fun onCleared() {
        super.onCleared()
        _uiState.value.decryptedFile?.delete()
    }

    private fun getMockDocument(id: String): DocumentUiModel? {
        val mockDocs = mapOf(
            "1"  to DocumentUiModel("1",  "Contrato_Servicios_2024.pdf",  DocumentType.PDF,        "2.4 MB", "01/05/2026", true),
            "2"  to DocumentUiModel("2",  "Informe_Trimestral.docx",      DocumentType.WORD,       "1.1 MB", "30/04/2026"),
            "3"  to DocumentUiModel("3",  "Presupuesto_Q1.xlsx",          DocumentType.EXCEL,      "890 KB", "29/04/2026"),
            "4"  to DocumentUiModel("4",  "Presentacion_Clientes.pptx",   DocumentType.POWERPOINT, "5.2 MB", "28/04/2026"),
            "5"  to DocumentUiModel("5",  "Foto_Documento.jpg",           DocumentType.IMAGE,      "3.8 MB", "27/04/2026"),
            "6"  to DocumentUiModel("6",  "Manual_Usuario.pdf",           DocumentType.PDF,        "4.1 MB", "26/04/2026", true),
            "7"  to DocumentUiModel("7",  "Notas_Reunion.txt",            DocumentType.TEXT,       "12 KB",  "25/04/2026"),
            "8"  to DocumentUiModel("8",  "Backup_Documentos.zip",        DocumentType.ZIP,        "45 MB",  "24/04/2026"),
            "9"  to DocumentUiModel("9",  "Escaneo_Factura.pdf",          DocumentType.OCR,        "1.8 MB", "23/04/2026"),
            "10" to DocumentUiModel("10", "Reporte_Ventas.xlsx",          DocumentType.EXCEL,      "2.2 MB", "22/04/2026", true),
            "11" to DocumentUiModel("11", "Carta_Presentacion.docx",      DocumentType.WORD,       "340 KB", "21/04/2026"),
            "12" to DocumentUiModel("12", "Logo_Empresa.png",             DocumentType.IMAGE,      "890 KB", "20/04/2026")
        )
        return mockDocs[id]
    }
}