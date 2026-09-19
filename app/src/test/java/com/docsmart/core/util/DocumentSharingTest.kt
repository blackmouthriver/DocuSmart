package com.docsmart.core.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Cubre `resolveShareUri()` (extraída de `shareDocument()` para poder
 * testearla sin construir un `Intent` real -- el proyecto no usa
 * Robolectric en los tests unitarios, y un `Intent()` real revienta bajo el
 * stub de Android sin mockear).
 *
 * Hallazgo real corregido esta ronda (auditoría 2026-09-18, Favoritos/
 * Descargas): la versión heredada de los 3 sitios duplicados
 * (DocumentListSection/FavoritesSection/RecentDocuments) decidía content://
 * vs archivo real esperando que `Uri.parse(document.id)` lanzara una
 * excepción para el segundo caso -- pero `Uri.parse()` nunca lanza. Para
 * documentos generados localmente (Convertidor/Herramientas PDF/Escáner),
 * `document.id` es la ruta absoluta real del archivo (mismo estilo que
 * `TrashRepositoryTest`, que usa `file.absolutePath` como documentId), así
 * que el camino "feliz" nunca fallaba y la rama de FileProvider -- pensada
 * justo para ese caso -- nunca se ejecutaba: "Compartir" fallaba en
 * silencio para todos los documentos generados por la app.
 */
class DocumentSharingTest {
    private lateinit var context: Context

    @BeforeEach
    fun setUp() {
        context = mockk()
        every { context.packageName } returns "com.docsmart"
        mockkStatic(Uri::class)
        mockkStatic(FileProvider::class)
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(Uri::class)
        unmockkStatic(FileProvider::class)
    }

    @Test
    fun `resolveShareUri con una Uri content la devuelve tal cual, sin pasar por FileProvider`() {
        val documentId = "content://media/external/downloads/12345"
        val contentUri = mockk<Uri>()
        every { contentUri.scheme } returns "content"
        every { Uri.parse(documentId) } returns contentUri

        val result = resolveShareUri(context, documentId)

        assertSame(contentUri, result)
    }

    @Test
    fun `resolveShareUri con una ruta absoluta sin esquema la arma via FileProvider (regresion del bug real)`() {
        val path = "/data/user/0/com.docsmart/files/converted/documento.pdf"
        val rawUri = mockk<Uri>()
        every { rawUri.scheme } returns null
        every { Uri.parse(path) } returns rawUri
        val contentUri = mockk<Uri>()
        every {
            FileProvider.getUriForFile(context, "com.docsmart.fileprovider", File(path))
        } returns contentUri

        val result = resolveShareUri(context, path)

        assertSame(contentUri, result)
    }

    @Test
    fun `resolveShareUri con esquema file extrae la ruta real y la arma via FileProvider`() {
        // Exponer una Uri file:// cruda a otra app (sin pasar por
        // FileProvider) crashea con FileUriExposedException en apps con
        // targetSdk 24+ (bug real ya corregido en otro lugar del proyecto
        // por el mismo motivo).
        val path = "/data/user/0/com.docsmart/files/converted/documento.pdf"
        val documentId = "file://$path"
        val fileUri = mockk<Uri>()
        every { fileUri.scheme } returns "file"
        every { fileUri.path } returns path
        every { Uri.parse(documentId) } returns fileUri
        val contentUri = mockk<Uri>()
        every {
            FileProvider.getUriForFile(context, "com.docsmart.fileprovider", File(path))
        } returns contentUri

        val result = resolveShareUri(context, documentId)

        assertSame(contentUri, result)
    }

    @Test
    fun `resolveShareUri con esquema file sin path usa el documentId completo como fallback`() {
        val documentId = "file://"
        val fileUri = mockk<Uri>()
        every { fileUri.scheme } returns "file"
        every { fileUri.path } returns null
        every { Uri.parse(documentId) } returns fileUri
        val contentUri = mockk<Uri>()
        every {
            FileProvider.getUriForFile(context, "com.docsmart.fileprovider", File(documentId))
        } returns contentUri

        val result = resolveShareUri(context, documentId)

        assertSame(contentUri, result)
    }
}
