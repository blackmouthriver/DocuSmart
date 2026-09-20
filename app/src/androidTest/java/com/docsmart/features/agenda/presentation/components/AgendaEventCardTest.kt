package com.docsmart.features.agenda.presentation.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.docsmart.R
import com.docsmart.core.data.db.AgendaEventEntity
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Ronda 18: AgendaEventCard con sus tres estados (vencido/hoy/próximo), el
 * ícono de documento vinculado y el clic.
 */
class AgendaEventCardTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val dayMillis = 24 * 60 * 60 * 1_000L

    private fun event(
        id: String,
        title: String,
        dateTimeMillis: Long,
        documentId: String? = null,
    ) = AgendaEventEntity(
        id = id,
        title = title,
        dateTimeMillis = dateTimeMillis,
        documentId = documentId,
        createdAt = 0L,
    )

    @Test
    fun cadaEstado_muestraSuEtiqueta() {
        val now = System.currentTimeMillis()
        composeRule.setContentEs {
            Column {
                AgendaEventCard(event = event("1", "Evento pasado", now - 3 * dayMillis), onClick = {})
                AgendaEventCard(event = event("2", "Evento de hoy", now), onClick = {})
                AgendaEventCard(event = event("3", "Evento futuro", now + 3 * dayMillis), onClick = {})
            }
        }

        composeRule.onNodeWithText("Evento pasado").assertIsDisplayed()
        composeRule.onNodeWithText("Evento de hoy").assertIsDisplayed()
        composeRule.onNodeWithText("Evento futuro").assertIsDisplayed()
        composeRule.onNodeWithText(esString(R.string.agenda_status_overdue)).assertIsDisplayed()
        composeRule.onNodeWithText(esString(R.string.agenda_status_today)).assertIsDisplayed()
        composeRule.onNodeWithText(esString(R.string.agenda_status_upcoming)).assertIsDisplayed()
    }

    @Test
    fun conDocumentoVinculado_muestraElIcono() {
        composeRule.setContentEs {
            AgendaEventCard(
                event = event("1", "Con documento", System.currentTimeMillis() + dayMillis, documentId = "doc-1"),
                onClick = {},
            )
        }

        composeRule.onNodeWithContentDescription(esString(R.string.agenda_document_linked)).assertIsDisplayed()
    }

    @Test
    fun tocarLaTarjeta_invocaOnClick() {
        var clicks = 0
        composeRule.setContentEs {
            AgendaEventCard(
                event = event("1", "Tocable", System.currentTimeMillis() + dayMillis),
                onClick = { clicks++ },
            )
        }

        composeRule.onNodeWithText("Tocable").performClick()

        assertEquals(1, clicks)
    }
}
