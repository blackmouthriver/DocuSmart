package com.docsmart.core.media

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import coil.ImageLoader
import coil.request.Options
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * `PdfThumbnailFetcher.fetch()` en sí construye un `PdfRenderer` real (exige
 * runtime nativo de Android, no disponible en un test JVM puro sin
 * Robolectric). Lo que sí es lógica pura y testeable son las dos
 * `Fetcher.Factory` -- la decisión de "esto es un PDF, dame un Fetcher" vs.
 * "esto no es un PDF, dejá pasar" es exactamente el mecanismo descrito en el
 * comentario de la clase (Coil resuelve `file://` como `File` antes de
 * llegar al `Fetcher`, por eso hacen falta dos factories separadas).
 */
class PdfThumbnailFetcherTest {
    private val imageLoader = mockk<ImageLoader>()

    private fun optionsWithContext(context: Context): Options {
        val options = mockk<Options>()
        every { options.context } returns context
        return options
    }

    @Test
    fun `UriFactory crea un Fetcher para un content uri con mime type application-pdf`() {
        val uri = mockk<Uri>()
        every { uri.toString() } returns "content://com.docsmart.fileprovider/documento"
        val resolver = mockk<ContentResolver>()
        every { resolver.getType(uri) } returns "application/pdf"
        val context = mockk<Context>()
        every { context.contentResolver } returns resolver

        val fetcher = PdfThumbnailFetcher.UriFactory().create(uri, optionsWithContext(context), imageLoader)

        assertNotNull(fetcher)
        assertTrue(fetcher is PdfThumbnailFetcher)
    }

    @Test
    fun `UriFactory devuelve null si ni el mime type ni la uri sugieren un pdf`() {
        val uri = mockk<Uri>()
        every { uri.toString() } returns "content://media/external/images/media/42"
        val resolver = mockk<ContentResolver>()
        every { resolver.getType(uri) } returns "image/jpeg"
        val context = mockk<Context>()
        every { context.contentResolver } returns resolver

        val fetcher = PdfThumbnailFetcher.UriFactory().create(uri, optionsWithContext(context), imageLoader)

        assertNull(fetcher)
    }

    @Test
    fun `UriFactory reconoce un pdf por extension de la uri aunque el mime type sea nulo`() {
        val uri = mockk<Uri>()
        every { uri.toString() } returns "content://com.docsmart.fileprovider/reporte.pdf"
        val resolver = mockk<ContentResolver>()
        every { resolver.getType(uri) } returns null
        val context = mockk<Context>()
        every { context.contentResolver } returns resolver

        val fetcher = PdfThumbnailFetcher.UriFactory().create(uri, optionsWithContext(context), imageLoader)

        assertNotNull(fetcher)
    }

    @Test
    fun `FileFactory crea un Fetcher para un pdf generado por la app en cache`() {
        val file = File("/data/user/0/com.docsmart/cache/converted/documento.pdf")
        val context = mockk<Context>()

        val fetcher = PdfThumbnailFetcher.FileFactory().create(file, optionsWithContext(context), imageLoader)

        assertNotNull(fetcher)
        assertTrue(fetcher is PdfThumbnailFetcher)
    }

    @Test
    fun `FileFactory devuelve null para un archivo que no es pdf`() {
        val file = File("/data/user/0/com.docsmart/cache/converted/imagen.jpg")
        val context = mockk<Context>()

        val fetcher = PdfThumbnailFetcher.FileFactory().create(file, optionsWithContext(context), imageLoader)

        assertNull(fetcher)
    }
}
