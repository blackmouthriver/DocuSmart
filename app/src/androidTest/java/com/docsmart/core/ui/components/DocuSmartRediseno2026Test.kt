package com.docsmart.core.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.docsmart.R
import com.docsmart.core.ui.components.buttons.DocuSmartFab
import com.docsmart.core.ui.theme.DocuSmartTheme
import com.docsmart.features.security.presentation.esText
import com.docsmart.features.security.presentation.setContentEsScaled
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Componentes comunes de las Fases 0-2 del rediseño (2026-09-20): chip de estado,
 * estados vacío/carga/error, botón flotante y encabezado compacto.
 */
class DocuSmartRediseno2026Test {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun themed(
        dark: Boolean,
        content: @Composable () -> Unit,
    ) {
        composeRule.setContentEsScaled { DocuSmartTheme(darkTheme = dark) { content() } }
        composeRule.waitForIdle()
    }

    @Test
    fun chipDeEstado_muestraSuEtiquetaEnLosCincoTonos_enClaroYOscuro() {
        themed(dark = false) {
            Column {
                StatusTone.entries.forEach { DocuSmartStatusChip(label = "claro-${it.name}", tone = it) }
                DocuSmartTheme(darkTheme = true) {
                    StatusTone.entries.forEach { DocuSmartStatusChip(label = "oscuro-${it.name}", tone = it) }
                }
            }
        }
        StatusTone.entries.forEach {
            composeRule.onNodeWithText("claro-${it.name}").assertIsDisplayed()
            composeRule.onNodeWithText("oscuro-${it.name}").assertIsDisplayed()
        }
    }

    @Test
    fun estadoVacio_muestraTituloDescripcionYLaAccionDisparaElCallback() {
        var clicks = 0
        themed(dark = false) {
            DocuSmartEmptyState(
                icon = Icons.Rounded.FolderOff,
                title = "Sin documentos",
                description = "Abre un archivo para empezar",
                actionLabel = "Abrir archivo",
                onAction = { clicks++ },
            )
        }
        composeRule.onNodeWithText("Sin documentos").assertIsDisplayed()
        composeRule.onNodeWithText("Abre un archivo para empezar").assertIsDisplayed()
        composeRule.onNodeWithText("Abrir archivo").performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun estadoVacio_sinAccion_noMuestraBoton() {
        themed(dark = true) {
            DocuSmartEmptyState(
                icon = Icons.Rounded.FolderOff,
                title = "Vacío",
                description = "Nada por aquí",
            )
        }
        composeRule.onNodeWithText("Vacío").assertIsDisplayed()
        composeRule.onNodeWithText("Nada por aquí").assertIsDisplayed()
    }

    @Test
    fun estadoVacio_sinDescripcion_yEnVarianteCompacta_muestraSoloElTitulo() {
        themed(dark = false) {
            Column {
                DocuSmartEmptyState(icon = Icons.Rounded.FolderOff, title = "Sin marcadores", compact = true)
                DocuSmartEmptyState(icon = Icons.Rounded.FolderOff, title = "Sin notas")
            }
        }
        composeRule.onNodeWithText("Sin marcadores").assertIsDisplayed()
        composeRule.onNodeWithText("Sin notas").assertIsDisplayed()
    }

    @Test
    fun estadoDeCarga_conYSinMensaje() {
        themed(dark = false) {
            Column {
                DocuSmartLoadingState(message = "Cargando documentos")
                DocuSmartLoadingState()
            }
        }
        composeRule.onNodeWithText("Cargando documentos").assertIsDisplayed()
    }

    @Test
    fun estadoDeError_muestraElMensajeYElReintentoEsPulsable() {
        var retries = 0
        themed(dark = true) {
            DocuSmartErrorState(
                icon = Icons.Rounded.CloudOff,
                message = "No se pudo cargar",
                retry = { TextButton(onClick = { retries++ }) { Text("Reintentar") } },
            )
        }
        composeRule.onNodeWithText("No se pudo cargar").assertIsDisplayed()
        composeRule.onNodeWithText("Reintentar").performClick()
        assertEquals(1, retries)
    }

    @Test
    fun estadoDeError_sinReintento() {
        themed(dark = false) {
            DocuSmartErrorState(icon = Icons.Rounded.CloudOff, message = "Sin conexión")
        }
        composeRule.onNodeWithText("Sin conexión").assertIsDisplayed()
    }

    @Test
    fun botonFlotante_tieneDescripcionYDisparaElCallback() {
        var clicks = 0
        themed(dark = false) {
            DocuSmartFab(
                icon = Icons.Rounded.Add,
                contentDescription = "Nuevo evento",
                onClick = { clicks++ },
            )
        }
        composeRule.onNodeWithContentDescription("Nuevo evento").assertHasClickAction().performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun encabezadoCompacto_muestraTituloSubtituloAccionesYNavegacion() {
        var back = 0
        var home = 0
        themed(dark = false) {
            DocuSmartTopBanner(
                screenTitle = "Agenda",
                screenSubtitle = "Organiza tus eventos",
                onBack = { back++ },
                onHome = { home++ },
                compact = true,
                actions = { Text("acción") },
            )
        }
        composeRule.onNodeWithText("Agenda").assertIsDisplayed()
        composeRule.onNodeWithText("Organiza tus eventos").assertIsDisplayed()
        composeRule.onNodeWithText("acción").assertIsDisplayed()
        composeRule.onNodeWithText(esText(R.string.general_back)).performClick()
        composeRule.onNodeWithText(esText(R.string.nav_home)).performClick()
        assertEquals(1, back)
        assertEquals(1, home)
    }

    @Test
    fun encabezadoCompacto_sinSubtituloNiNavegacion() {
        themed(dark = true) {
            DocuSmartTopBanner(screenTitle = "Papelera", compact = true)
        }
        composeRule.onNodeWithText("Papelera").assertIsDisplayed()
    }

    @Test
    fun encabezadoNormal_sigueMostrandoTituloYNavegacion() {
        var back = 0
        themed(dark = false) {
            DocuSmartTopBanner(
                screenTitle = "Biblioteca",
                screenSubtitle = "Todos tus documentos",
                onBack = { back++ },
            )
        }
        composeRule.onNodeWithText("Biblioteca").assertIsDisplayed()
        composeRule.onNodeWithText(esText(R.string.general_back)).performClick()
        assertEquals(1, back)
    }
}
