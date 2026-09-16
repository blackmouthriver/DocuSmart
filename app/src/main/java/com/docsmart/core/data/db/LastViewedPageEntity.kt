package com.docsmart.core.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Última página vista de un documento del Visor (backlog UX ítem #48) --
 * `documentId` es el mismo id que Biblioteca/Home/Favoritos/Anotaciones
 * (Uri o ruta absoluta como String). Una sola fila por documento, mismo
 * patrón que `DocumentHistoryEntry`. `page` es 0-based, mismo criterio que
 * `ViewerUiState.currentPage`.
 */
@Entity(tableName = "last_viewed_page")
data class LastViewedPageEntity(
    @PrimaryKey val documentId: String,
    val page: Int,
    val updatedAt: Long
)
