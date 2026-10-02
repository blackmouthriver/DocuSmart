package com.docsmart.core.navegation

import android.net.Uri
import android.os.SystemClock
import androidx.navigation.NavHostController
import com.docsmart.core.ui.components.DocumentType
import com.docsmart.core.ui.components.DocumentUiModel
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * S4 (backlog-bugs-2026-09-17-v4.md): Convertir/Crear QR/Hacer buscable/
 * Firmar/Mover a Carpeta Segura no tenían ningún guard de doble-toque, a
 * diferencia de Favorito/Compartir/Renombrar/Eliminar -- son navegaciones
 * puras hacia el NavGraph, sin ViewModel propio. Cubre el debounce
 * compartido agregado en DocuSmartNavGraph.kt (navigateToConvert/
 * navigateToOcr/navigateToSign/navigateToSecureFolder).
 */
class QuickActionNavigateDebounceTest {
    private lateinit var navController: NavHostController
    private val document =
        DocumentUiModel(
            id = "content://com.docsmart.fileprovider/doc1.pdf",
            name = "doc1.pdf",
            type = DocumentType.PDF,
            size = "1 KB",
            date = "hoy",
        )

    @BeforeEach
    fun setUp() {
        resetQuickActionNavigateDebounceForTests()
        mockkStatic(Uri::class)
        mockkStatic(SystemClock::class)
        val fakeUri = mockk<Uri>(relaxed = true)
        every { Uri.parse(any()) } returns fakeUri
        every { fakeUri.toString() } returns document.id
        // NavRoutes.*.createRoute() codifica cada argumento con Uri.encode()
        // antes de armar la ruta -- sin este stub, "Method encode ... not
        // mocked" (android.jar real, sin cuerpo, ver mockkStatic de arriba).
        every { Uri.encode(any()) } answers { firstArg() }
        // Valor fijo: alcanza para probar el debounce (0ms transcurridos
        // entre 2 llamadas seguidas = se ignora la segunda) y su reset
        // (tras resetQuickActionNavigateDebounceForTests(), el "ultimo
        // toque" vuelve a 0L, así que hasta un reloj fijo > 500 ya deja
        // pasar la siguiente llamada).
        every { SystemClock.elapsedRealtime() } returns 10_000L
        navController = mockk(relaxed = true)
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(Uri::class)
        unmockkStatic(SystemClock::class)
    }

    @Test
    fun `un segundo toque a Convertir antes de 500ms se ignora`() {
        navController.navigateToConvert(document)
        navController.navigateToConvert(document)

        verify(exactly = 1) { navController.navigate(any<String>()) }
    }

    @Test
    fun `tras resetear el debounce, un nuevo toque si navega`() {
        navController.navigateToConvert(document)
        resetQuickActionNavigateDebounceForTests()
        navController.navigateToConvert(document)

        verify(exactly = 2) { navController.navigate(any<String>()) }
    }

    @Test
    fun `el debounce es compartido entre los 5 accesos directos, no solo por pantalla`() {
        navController.navigateToConvert(document)
        navController.navigateToOcr(document)
        navController.navigateToSign(document)
        navController.navigateToSecureFolder(document)

        verify(exactly = 1) { navController.navigate(any<String>()) }
    }
}
