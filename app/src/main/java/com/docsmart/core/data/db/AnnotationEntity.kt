package com.docsmart.core.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.TypeConverter

// HU-46 (backlog UX 2026-09-10): resaltado o nota adhesiva anclada a una
// posición de una página PDF. `documentId` es el mismo `uriString`/ruta
// absoluta que usan Biblioteca/Home/Favoritos -- como el resto del
// proyecto, renombrar el documento no migra estas filas (mismo límite ya
// conocido y sin corregir de FavoritesRepository, ver
// DocumentRepository.renameDocument()). Coordenadas en puntos PDF (origen
// inferior-izquierda), mismo sistema que PdfMatchRect -- para NOTE,
// widthPts/heightPts quedan en 0 (es un punto de anclaje, no un área).
enum class AnnotationType { HIGHLIGHT, NOTE }

class AnnotationTypeConverter {
    @TypeConverter
    fun fromType(type: AnnotationType): String = type.name

    @TypeConverter
    fun toType(value: String): AnnotationType = AnnotationType.entries.firstOrNull { it.name == value } ?: AnnotationType.HIGHLIGHT
}

@Entity(tableName = "annotations", indices = [Index("documentId")])
data class AnnotationEntity(
    @androidx.room.PrimaryKey val id: String,
    val documentId: String,
    val type: AnnotationType,
    // 1-based, mismo criterio que pdfSearchHighlights
    val page: Int,
    val xPts: Float,
    val yPts: Float,
    val widthPts: Float,
    val heightPts: Float,
    // ARGB opaco -- la transparencia del resaltado se aplica al dibujar, no al guardar
    val color: Int,
    // vacío para HIGHLIGHT, contenido de la nota para NOTE
    val text: String,
    val createdAt: Long,
)
