package com.docsmart.core.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FavoritesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // ── SharedPreferences para favoritos ──────────────────────────────────────
    private val prefsF by lazy {
        context.getSharedPreferences(PREFS_FAVORITES, Context.MODE_PRIVATE)
    }

    // ── SharedPreferences para aliases de nombres ─────────────────────────────
    private val prefsA by lazy {
        context.getSharedPreferences(PREFS_ALIASES, Context.MODE_PRIVATE)
    }

    private val favoriteIds: MutableSet<String> by lazy {
        prefsF.getStringSet(KEY_FAVORITES, emptySet())?.toMutableSet() ?: mutableSetOf()
    }

    // Mapa en memoria: documentId → nombre personalizado
    private val nameAliases: MutableMap<String, String> by lazy {
        prefsA.all
            .filterValues { it is String }
            .mapValues { it.value as String }
            .toMutableMap()
    }

    // ── Favoritos ─────────────────────────────────────────────────────────────

    fun isFavorite(documentId: String): Boolean = favoriteIds.contains(documentId)

    fun getAllFavoriteIds(): Set<String> = favoriteIds.toSet()

    suspend fun toggleFavorite(documentId: String): Boolean =
        withContext(Dispatchers.IO) {
            val isNowFavorite = if (favoriteIds.contains(documentId)) {
                favoriteIds.remove(documentId); false
            } else {
                favoriteIds.add(documentId); true
            }
            prefsF.edit().putStringSet(KEY_FAVORITES, favoriteIds.toSet()).apply()
            Timber.d("FavoritesRepository: $documentId → favorito=$isNowFavorite")
            isNowFavorite
        }

    // Hallazgo real de la revisión general 2026-09-16 (#50): a diferencia
    // de toggleFavorite() (que alterna), esto quita el favorito sin
    // importar el estado actual -- idempotente, para usar en limpieza tras
    // borrado definitivo/purga automática, donde no importa si estaba
    // marcado o no.
    suspend fun removeFavorite(documentId: String) =
        withContext(Dispatchers.IO) {
            if (favoriteIds.remove(documentId)) {
                prefsF.edit().putStringSet(KEY_FAVORITES, favoriteIds.toSet()).apply()
            }
        }

    // ── Aliases de nombres ────────────────────────────────────────────────────

    /** Retorna el nombre personalizado si existe, o null */
    fun getAlias(documentId: String): String? = nameAliases[documentId]

    /** Guarda un alias de nombre para un documento */
    suspend fun saveAlias(documentId: String, newName: String) =
        withContext(Dispatchers.IO) {
            nameAliases[documentId] = newName
            prefsA.edit().putString(documentId, newName).apply()
            Timber.d("FavoritesRepository: alias guardado $documentId → $newName")
        }

    /** Elimina el alias de un documento */
    suspend fun removeAlias(documentId: String) =
        withContext(Dispatchers.IO) {
            nameAliases.remove(documentId)
            prefsA.edit().remove(documentId).apply()
        }

    // Hallazgo real de la revisión general 2026-09-16 (#50): mover a/desde
    // Carpeta Segura o renombrar cambia el id (ruta) de un documento --
    // sin esto, un favorito/alias existente quedaba huérfano bajo el id
    // viejo (que ya no existe) en vez de seguir al documento a su id nuevo,
    // mismo criterio que ya se aplica a las anotaciones del Visor
    // (AnnotationDao.updateDocumentId, hallazgo #48).
    suspend fun migrateId(oldId: String, newId: String): Unit =
        withContext(Dispatchers.IO) {
            if (oldId == newId) return@withContext
            if (favoriteIds.remove(oldId)) {
                favoriteIds.add(newId)
                prefsF.edit().putStringSet(KEY_FAVORITES, favoriteIds.toSet()).apply()
            }
            val alias = nameAliases.remove(oldId)
            if (alias != null) {
                nameAliases[newId] = alias
                prefsA.edit().remove(oldId).putString(newId, alias).apply()
            }
        }

    companion object {
        private const val PREFS_FAVORITES = "docusmart_favorites"
        private const val PREFS_ALIASES   = "docusmart_name_aliases"
        private const val KEY_FAVORITES   = "favorite_ids"
    }
}