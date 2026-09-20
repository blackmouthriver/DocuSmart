package com.docsmart.core.data

import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Ronda 18: `canonicalMediaUri` normaliza cualquier Uri de MediaStore a la
 * colección `Files`. Los métodos estáticos de `ContentUris`/`MediaStore.Files`
 * se mockean (mismo criterio que `ViewerViewModelTest` con `Uri`).
 */
class MediaUriUtilsTest {
    private val filesCollection = mockk<Uri>()

    @BeforeEach
    fun setUp() {
        mockkStatic(ContentUris::class)
        mockkStatic(MediaStore.Files::class)
        every { MediaStore.Files.getContentUri("external") } returns filesCollection
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(ContentUris::class)
        unmockkStatic(MediaStore.Files::class)
    }

    private fun mediaUri(): Uri = mockk<Uri>().also { every { it.authority } returns MediaStore.AUTHORITY }

    @Test
    fun `un Uri de otra autoridad se devuelve sin tocar`() {
        val uri = mockk<Uri>()
        every { uri.authority } returns "com.android.providers.downloads.documents"

        assertSame(uri, canonicalMediaUri(uri))
    }

    @Test
    fun `un Uri con autoridad nula se devuelve sin tocar`() {
        val uri = mockk<Uri>()
        every { uri.authority } returns null

        assertSame(uri, canonicalMediaUri(uri))
    }

    @Test
    fun `un Uri de MediaStore se normaliza a la coleccion Files con el mismo id`() {
        val uri = mediaUri()
        val canonical = mockk<Uri>()
        every { ContentUris.parseId(uri) } returns 42L
        every { ContentUris.withAppendedId(filesCollection, 42L) } returns canonical

        assertSame(canonical, canonicalMediaUri(uri))
    }

    @Test
    fun `un Uri jerarquico invalido se deja sin normalizar`() {
        val uri = mediaUri()
        every { ContentUris.parseId(uri) } throws UnsupportedOperationException()

        assertSame(uri, canonicalMediaUri(uri))
    }

    @Test
    fun `un Uri de coleccion sin id numerico se deja sin normalizar en vez de fallar`() {
        // Bug real ronda 18: parseId() lanza NumberFormatException con un
        // último segmento no numérico, y antes solo se atrapaba
        // UnsupportedOperationException.
        val uri = mediaUri()
        every { ContentUris.parseId(uri) } throws NumberFormatException()

        assertSame(uri, canonicalMediaUri(uri))
    }
}
