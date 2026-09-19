package com.docsmart.core.permission

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.UriPermission
import android.net.Uri
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * `PermissionHandler` centraliza la persistencia de permisos de lectura
 * sobre `Uri` (SAF/`content://` elegidos por el usuario) -- no maneja
 * permisos runtime (CAMERA/READ_MEDIA_*), esos viven en otra capa fuera del
 * alcance de esta clase.
 */
class PermissionHandlerTest {

    private lateinit var context: Context
    private lateinit var resolver: ContentResolver
    private lateinit var handler: PermissionHandler
    private val uri = mockk<Uri>()

    @BeforeEach
    fun setUp() {
        resolver = mockk()
        context = mockk()
        every { context.contentResolver } returns resolver
        handler = PermissionHandler()
    }

    @Test
    fun `takePersistableReadPermission pide el flag de solo lectura y devuelve true si tiene exito`() {
        every {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } just Runs

        val result = handler.takePersistableReadPermission(context, uri)

        assertTrue(result)
        verify(exactly = 1) {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    @Test
    fun `takePersistableReadPermission devuelve false sin propagar la excepcion si el proveedor la rechaza`() {
        // Hallazgo real de la auditoría general 2026-09-18 (ronda 14): antes
        // de la redacción del log, este catch reenviaba `e` completa (con
        // su mensaje, que suele incluir la Uri real) a Crashlytics vía
        // Timber.e -- el comportamiento observable (no crashea, devuelve
        // false) es lo que cubre este test.
        every {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } throws SecurityException(
            "Permission Denial: taking Uri content://com.docsmart.fileprovider/contrato_confidencial.pdf"
        )

        val result = handler.takePersistableReadPermission(context, uri)

        assertFalse(result)
    }

    @Test
    fun `hasReadPermission es true si la uri esta persistida con permiso de lectura`() {
        val permission = mockk<UriPermission>()
        every { permission.uri } returns uri
        every { permission.isReadPermission } returns true
        every { resolver.persistedUriPermissions } returns listOf(permission)

        assertTrue(handler.hasReadPermission(context, uri))
    }

    @Test
    fun `hasReadPermission es false si la uri esta persistida solo con permiso de escritura`() {
        val permission = mockk<UriPermission>()
        every { permission.uri } returns uri
        every { permission.isReadPermission } returns false
        every { resolver.persistedUriPermissions } returns listOf(permission)

        assertFalse(handler.hasReadPermission(context, uri))
    }

    @Test
    fun `hasReadPermission es false si la uri no esta en la lista de permisos persistidos`() {
        val otraUri = mockk<Uri>()
        val permission = mockk<UriPermission>()
        every { permission.uri } returns otraUri
        every { permission.isReadPermission } returns true
        every { resolver.persistedUriPermissions } returns listOf(permission)

        assertFalse(handler.hasReadPermission(context, uri))
    }

    @Test
    fun `hasReadPermission es false si no hay ningun permiso persistido`() {
        every { resolver.persistedUriPermissions } returns emptyList()

        assertFalse(handler.hasReadPermission(context, uri))
    }

    @Test
    fun `revokePermission libera el permiso con el mismo flag usado al persistirlo`() {
        every {
            resolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } just Runs

        handler.revokePermission(context, uri)

        verify(exactly = 1) {
            resolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    @Test
    fun `revokePermission no lanza si el proveedor ya habia revocado el permiso`() {
        every {
            resolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } throws SecurityException("content://com.docsmart.fileprovider/contrato_confidencial.pdf")

        handler.revokePermission(context, uri) // no debe lanzar
    }

    @Test
    fun `logActivePermissions no lanza con una lista de permisos activos`() {
        val permission = mockk<UriPermission>()
        every { permission.uri } returns uri
        every { permission.isReadPermission } returns true
        every { resolver.persistedUriPermissions } returns listOf(permission)

        handler.logActivePermissions(context) // no debe lanzar
    }

    @Test
    fun `logActivePermissions no lanza con la lista vacia`() {
        every { resolver.persistedUriPermissions } returns emptyList()

        handler.logActivePermissions(context) // no debe lanzar
    }
}
