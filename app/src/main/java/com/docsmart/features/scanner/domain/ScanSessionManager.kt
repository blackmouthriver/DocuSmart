package com.docsmart.features.scanner.domain

import android.content.Context
import com.docsmart.R
import com.docsmart.core.data.FavoritesRepository
import com.docsmart.core.ui.components.DocumentType
import com.docsmart.core.ui.components.DocumentUiModel
import com.docsmart.features.library.data.DocumentRepository
import com.docsmart.features.library.data.TrashRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Backlog UX #35 (pedido explícito del usuario 2026-09-06): cuando el
 * usuario guarda o comparte un documento escaneado, en vez de volver
 * directo a Inicio, la pantalla de resultado debe mostrar una lista tipo
 * Biblioteca de lo escaneado en esta sesión, con la opción de escanear
 * otro. `ScanResultScreen` usa `hiltViewModel()` con el scope por
 * defecto (la propia entrada del backstack de la ruta ScanResult), que se
 * recrea cada vez que se navega Scanner → ScanResult de nuevo -- así que
 * la lista necesita vivir en un `@Singleton` para sobrevivir esos
 * saltos, no en el ViewModel de la pantalla.
 *
 * Ampliado (mismo día, feedback del usuario tras probar la primera
 * versión): la fila de cada archivo solo tenía un botón de compartir --
 * ahora reutiliza `FavoritesRepository`/`DocumentRepository`/
 * `TrashRepository`, las mismas que ya usan Biblioteca/Recientes, para
 * que "favorito"/"renombrar"/"eliminar" se comporten igual (y de forma
 * consistente: un archivo renombrado o marcado favorito acá se ve igual
 * si luego aparece en Biblioteca).
 */
@Singleton
class ScanSessionManager @Inject constructor(
    private val favoritesRepository: FavoritesRepository,
    private val documentRepository: DocumentRepository,
    private val trashRepository: TrashRepository
) {

    private val _scannedFiles = MutableStateFlow<List<DocumentUiModel>>(emptyList())
    val scannedFiles: StateFlow<List<DocumentUiModel>> = _scannedFiles.asStateFlow()

    // H8 (undécima auditoría, 2026-09-18): `formatFileSize()` usaba el
    // Context inyectado (@ApplicationContext), que NUNCA lleva el
    // locale-override de idioma de la app -- solo
    // MainActivity.attachBaseContext() lo aplica -- así que un usuario con
    // un idioma distinto al del sistema veía el tamaño del archivo siempre
    // en el idioma del sistema. `context` ahora es un parámetro explícito;
    // los dos llamadores reales (ScanResultScreen.kt, vía
    // ScanSessionViewModel.addFile) pasan el Context localizado del
    // Composable (`LocalContext.current`).
    fun addFile(file: File, context: Context) {
        val id = file.absolutePath
        _scannedFiles.update { current ->
            if (current.any { it.id == id }) current else current + buildDocumentUiModel(file, context)
        }
    }

    fun clear() {
        _scannedFiles.value = emptyList()
    }

    suspend fun toggleFavorite(documentId: String) {
        val isNowFavorite = favoritesRepository.toggleFavorite(documentId)
        _scannedFiles.update { list ->
            list.map { if (it.id == documentId) it.copy(isFavorite = isNowFavorite) else it }
        }
    }

    suspend fun renameDocument(documentId: String, newName: String) {
        val newId = documentRepository.renameDocument(documentId, newName)
        _scannedFiles.update { list ->
            list.map { if (it.id == documentId) it.copy(id = newId, name = newName) else it }
        }
    }

    suspend fun deleteDocument(documentId: String): Boolean {
        val movedToTrash = trashRepository.moveToTrash(documentId)
        if (movedToTrash) {
            _scannedFiles.update { list -> list.filterNot { it.id == documentId } }
        }
        return movedToTrash
    }

    private fun buildDocumentUiModel(file: File, context: Context): DocumentUiModel = DocumentUiModel(
        id         = file.absolutePath,
        name       = file.name,
        type       = documentTypeForExtension(file.extension),
        size       = formatFileSize(file.length(), context),
        date       = "",
        isFavorite = favoritesRepository.isFavorite(file.absolutePath),
        sizeBytes  = file.length()
    )

    private fun documentTypeForExtension(extension: String): DocumentType = when (extension.lowercase()) {
        "pdf" -> DocumentType.PDF
        "jpg", "jpeg", "png", "webp" -> DocumentType.IMAGE
        else -> DocumentType.PDF
    }

    // Hallazgo real de la revisión general 2026-09-16 (cuarta pasada):
    // unidades "B"/"KB"/"MB" hardcodeadas sin stringResource, fuera del
    // sistema de 12 idiomas. Mismo patrón ya corregido para
    // PdfToolsScreen (hallazgo #30 del backlog anterior).
    private fun formatFileSize(bytes: Long, context: Context): String = when {
        bytes < 1024 -> context.getString(R.string.file_size_bytes, bytes)
        bytes < 1024 * 1024 -> context.getString(R.string.file_size_kb, bytes / 1024)
        else -> context.getString(
            R.string.file_size_mb,
            String.format(Locale.getDefault(), "%.1f", bytes / (1024.0 * 1024.0))
        )
    }
}
