package com.docsmart.core.data.db

import androidx.room.Entity
import androidx.room.Index

/**
 * Marcador de página del Visor (backlog UX ítem #47) -- distinto de
 * "favorito" de documento completo (`FavoritesRepository`), esto marca una
 * página específica dentro de un documento para volver a ella rápido.
 * `documentId` es el mismo id que el resto de las tablas por documento
 * (Anotaciones/Historial/Favoritos). Clave primaria compuesta
 * (documentId, page): marcar la misma página dos veces es un no-op
 * (REPLACE), y "está marcada" se resuelve con una sola fila, sin necesitar
 * un id generado aparte. `page` es 0-based, mismo criterio que
 * `ViewerUiState.currentPage`.
 */
@Entity(tableName = "page_bookmarks", primaryKeys = ["documentId", "page"], indices = [Index("documentId")])
data class PageBookmarkEntity(
    val documentId: String,
    val page: Int,
    val createdAt: Long,
)
