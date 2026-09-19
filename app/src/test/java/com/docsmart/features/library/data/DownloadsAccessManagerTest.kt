package com.docsmart.features.library.data

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.UriPermission
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Cubre el vínculo SAF a la carpeta Descargas (fila 22 del backlog UX) y los
 * hallazgos reales ya corregidos en rondas previas de esta sesión:
 * - M3 (auditoría 2026-09-17): `onFolderPicked()` debe devolver `false` (no
 *   solo loguear) si `takePersistableUriPermission()` lanza.
 * - Alta (auditoría 2026-09-18): `folderDisplayName()` no debe crashear si
 *   `DocumentFile.fromTreeUri()`/`.name` lanza (permiso SAF revocado o
 *   carpeta borrada).
 * - `loadLinkedFolderUri()` debe revalidar contra los permisos SAF
 *   realmente persistidos, no confiar ciegamente en SharedPreferences.
 */
class DownloadsAccessManagerTest {

    private lateinit var context: Context
    private lateinit var resolver: ContentResolver
    private lateinit var prefsBacking: MutableMap<String, String>

    @BeforeEach
    fun setUp() {
        prefsBacking = mutableMapOf()
        context = mockk()
        resolver = mockk()
        every { context.contentResolver } returns resolver
        every {
            context.getSharedPreferences("docusmart_downloads_access", Context.MODE_PRIVATE)
        } returns fakePrefs(prefsBacking)
        every { resolver.persistedUriPermissions } returns emptyList()
        mockkStatic(Uri::class)
        mockkStatic(DocumentsContract::class)
        mockkStatic(DocumentFile::class)
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(Uri::class)
        unmockkStatic(DocumentsContract::class)
        unmockkStatic(DocumentFile::class)
    }

    private fun newManager() = DownloadsAccessManager(context)

    @Test
    fun `sin carpeta vinculada previamente, linkedFolderUri arranca en null`() {
        val manager = newManager()

        assertNull(manager.linkedFolderUri.value)
    }

    @Test
    fun `onFolderPicked exitoso persiste el permiso y actualiza linkedFolderUri`() {
        val manager = newManager()
        val uri = mockk<Uri>()
        every { uri.toString() } returns "content://tree/primary:Download"
        every { resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } just Runs

        val picked = manager.onFolderPicked(uri)

        assertTrue(picked)
        assertEquals(uri, manager.linkedFolderUri.value)
        assertEquals("content://tree/primary:Download", prefsBacking["linked_folder_uri"])
    }

    @Test
    fun `onFolderPicked devuelve false si takePersistableUriPermission lanza SecurityException (hallazgo M3)`() {
        // Hallazgo real de la auditoría 2026-09-17 (M3): antes solo se
        // logueaba y la función no propagaba ningún resultado -- el usuario
        // tocaba "Vincular carpeta", elegía una, y no pasaba nada visible.
        val manager = newManager()
        val uri = mockk<Uri>()
        every {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } throws SecurityException("permission denied for content://tree/primary:Secretos")

        val picked = manager.onFolderPicked(uri)

        assertFalse(picked)
        assertNull(manager.linkedFolderUri.value)
        assertTrue(prefsBacking.isEmpty())
    }

    @Test
    fun `folderDisplayName devuelve el nombre real de la carpeta`() {
        val manager = newManager()
        val uri = mockk<Uri>()
        val documentFile = mockk<DocumentFile>()
        every { DocumentFile.fromTreeUri(context, uri) } returns documentFile
        every { documentFile.name } returns "DMSS"

        assertEquals("DMSS", manager.folderDisplayName(uri))
    }

    @Test
    fun `folderDisplayName no crashea si el permiso SAF fue revocado (hallazgo Alta 2026-09-18)`() {
        val manager = newManager()
        val uri = mockk<Uri>()
        every { DocumentFile.fromTreeUri(context, uri) } throws SecurityException("content://tree/primary:Secretos")

        assertNull(manager.folderDisplayName(uri))
    }

    @Test
    fun `folderDisplayName no crashea si la Uri del arbol ya es invalida (carpeta borrada)`() {
        val manager = newManager()
        val uri = mockk<Uri>()
        every { DocumentFile.fromTreeUri(context, uri) } throws IllegalArgumentException("invalid uri")

        assertNull(manager.folderDisplayName(uri))
    }

    @Test
    fun `unlink libera el permiso y limpia el estado y las preferencias`() {
        val manager = newManager()
        val uri = mockk<Uri>()
        every { uri.toString() } returns "content://tree/primary:Download"
        every { resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } just Runs
        manager.onFolderPicked(uri)
        every { resolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } just Runs

        manager.unlink()

        assertNull(manager.linkedFolderUri.value)
        assertTrue(prefsBacking.isEmpty())
    }

    @Test
    fun `unlink limpia el estado local aunque releasePersistableUriPermission lance SecurityException`() {
        val manager = newManager()
        val uri = mockk<Uri>()
        every { uri.toString() } returns "content://tree/primary:Download"
        every { resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } just Runs
        manager.onFolderPicked(uri)
        every {
            resolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } throws SecurityException("boom")

        manager.unlink()

        assertNull(manager.linkedFolderUri.value)
        assertTrue(prefsBacking.isEmpty())
    }

    @Test
    fun `unlink sin ninguna carpeta vinculada no crashea`() {
        val manager = newManager()

        manager.unlink()

        assertNull(manager.linkedFolderUri.value)
    }

    @Test
    fun `al construirse, revalida contra los permisos SAF persistidos y no confia ciegamente en SharedPreferences`() {
        // El usuario pudo revocar el permiso desde Ajustes del sistema sin
        // que la app se entere -- loadLinkedFolderUri() debe validar contra
        // context.contentResolver.persistedUriPermissions.
        val savedUri = mockk<Uri>()
        every { Uri.parse("content://tree/primary:Download") } returns savedUri
        prefsBacking["linked_folder_uri"] = "content://tree/primary:Download"
        every { resolver.persistedUriPermissions } returns emptyList()

        val manager = newManager()

        assertNull(
            manager.linkedFolderUri.value,
            "el permiso ya no esta en la lista real de SAF -> debe tratarse como no vinculado"
        )
        assertTrue(prefsBacking.isEmpty(), "debe limpiar la preferencia obsoleta")
    }

    @Test
    fun `al construirse, mantiene la carpeta vinculada si el permiso SAF sigue realmente concedido`() {
        val savedUri = mockk<Uri>()
        every { Uri.parse("content://tree/primary:Download") } returns savedUri
        prefsBacking["linked_folder_uri"] = "content://tree/primary:Download"
        val permission = mockk<UriPermission>()
        every { permission.uri } returns savedUri
        every { permission.isReadPermission } returns true
        every { resolver.persistedUriPermissions } returns listOf(permission)

        val manager = newManager()

        assertEquals(savedUri, manager.linkedFolderUri.value)
        assertEquals("content://tree/primary:Download", prefsBacking["linked_folder_uri"])
    }

    @Test
    fun `initialUriHint arma la Uri de la carpeta Download principal`() {
        val manager = newManager()
        val expected = mockk<Uri>()
        every {
            DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Download")
        } returns expected

        assertEquals(expected, manager.initialUriHint())
    }

    private fun fakePrefs(backing: MutableMap<String, String>): SharedPreferences {
        val editor = mockk<SharedPreferences.Editor>()
        every { editor.putString(any(), any()) } answers {
            backing[firstArg<String>()] = secondArg<String>()
            editor
        }
        every { editor.remove(any()) } answers {
            backing.remove(firstArg<String>())
            editor
        }
        every { editor.apply() } just Runs

        val prefs = mockk<SharedPreferences>()
        every { prefs.edit() } returns editor
        every { prefs.getString(any(), any()) } answers { backing[firstArg<String>()] ?: secondArg() }
        return prefs
    }
}
