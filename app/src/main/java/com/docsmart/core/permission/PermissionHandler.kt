package com.docsmart.core.permission

import android.content.Context
import android.content.Intent
import android.net.Uri
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

// ── Fix Sentinel INFO: clase vacía implementada ───────
// Centraliza la lógica de permisos de URI que antes estaba
// dispersa en NavGraph y HomeScreen
@Singleton
class PermissionHandler @Inject constructor() {

    // ── Persistir permiso de lectura para una URI ─────
    fun takePersistableReadPermission(context: Context, uri: Uri): Boolean {
        return try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            Timber.d("PermissionHandler: permiso persistido para $uri")
            true
        } catch (e: Exception) {
            Timber.e(e, "PermissionHandler: error persistiendo permiso — ${e.message}")
            false
        }
    }

    // ── Verificar si ya tenemos permiso para una URI ──
    fun hasReadPermission(context: Context, uri: Uri): Boolean {
        return context.contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isReadPermission
        }
    }

    // ── Listar todos los permisos activos ─────────────
    fun logActivePermissions(context: Context) {
        val permisos = context.contentResolver.persistedUriPermissions
        Timber.d("PermissionHandler: ${permisos.size} permisos activos")
        permisos.forEach {
            Timber.d("  → ${it.uri} read=${it.isReadPermission}")
        }
    }

    // ── Revocar permiso de una URI específica ─────────
    fun revokePermission(context: Context, uri: Uri) {
        try {
            context.contentResolver.releasePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            Timber.d("PermissionHandler: permiso revocado para $uri")
        } catch (e: Exception) {
            Timber.e(e, "PermissionHandler: error revocando permiso — ${e.message}")
        }
    }
}