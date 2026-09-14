package com.docsmart.features.library.data

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Bug real reportado por Firebase Crashlytics (2026-09-14): `createBulkDeleteRequest()`
 * y su reintento (`retryWithTypedUris()`) solo atrapaban `IllegalArgumentException`,
 * pero `MediaStore.createDeleteRequest()` es una llamada Binder a `MediaProvider` --
 * `DatabaseUtils.readExceptionFromParcel()`, del otro lado del IPC, puede re-lanzar
 * cualquier `RuntimeException` que el proveedor haya lanzado, no solo ese subtipo.
 * Atrapar solo `IllegalArgumentException` dejaba escapar cualquier otro tipo como
 * crash real, pese a que el comentario del código prometía que el llamador nunca
 * vería una excepción (solo `null`).
 */
class MediaDeletePermissionTest {

    private lateinit var context: Context
    private lateinit var resolver: ContentResolver
    private lateinit var permission: MediaDeletePermission

    @BeforeEach
    fun setUp() {
        context = mockk()
        resolver = mockk()
        every { context.contentResolver } returns resolver
        permission = MediaDeletePermission(context)
        mockkStatic(MediaStore::class)
        mockkStatic(ContentUris::class)
        every { ContentUris.parseId(any()) } returns 1L
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(MediaStore::class)
        unmockkStatic(ContentUris::class)
    }

    @Test
    fun `createBulkDeleteRequest devuelve null si la lista de Uris esta vacia`() {
        val result = permission.createBulkDeleteRequest(emptyList())

        assertNull(result)
    }

    @Test
    fun `createBulkDeleteRequest no crashea si MediaStore lanza una excepcion distinta a IllegalArgumentException`() {
        val uri = mockk<Uri>()
        every { uri.authority } returns "com.android.externalstorage.documents"
        // IllegalStateException simula lo que puede re-lanzar
        // DatabaseUtils.readExceptionFromParcel() para un error del lado de
        // MediaProvider que no sea "Unknown URL" -- antes del fix, este tipo
        // no se atrapaba y se propagaba como crash real.
        every { MediaStore.createDeleteRequest(resolver, listOf(uri)) } throws
            IllegalStateException("boom")

        val result = permission.createBulkDeleteRequest(listOf(uri))

        assertNull(result)
    }

    @Test
    fun `createBulkDeleteRequest no crashea si el reintento con Uris tipados tambien falla`() {
        val uri = mockk<Uri>()
        every { uri.authority } returns MediaStore.AUTHORITY
        val cursor = mockk<android.database.Cursor>(relaxed = true)
        every { cursor.moveToFirst() } returns false
        every {
            resolver.query(uri, arrayOf(MediaStore.Files.FileColumns.MEDIA_TYPE), null, null, null)
        } returns cursor
        every { MediaStore.createDeleteRequest(resolver, any()) } throws
            IllegalArgumentException("All requested items must be Media items")

        val result = permission.createBulkDeleteRequest(listOf(uri))

        assertNull(result)
    }
}
