package com.docsmart.features.agenda.presentation.components

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.docsmart.R
import com.docsmart.features.agenda.domain.ReminderPreset
import com.docsmart.features.agenda.domain.agendaDateFormatter
import com.docsmart.features.agenda.domain.agendaTimeFormatter
import com.docsmart.features.agenda.presentation.AgendaEventDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Ronda 18: AgendaEventEditorDialog -- modo nuevo/edición, guardado
 * habilitado solo con título, chips de recordatorio, aviso de recordatorio
 * ya vencido, vínculo/desvínculo de documento, confirmación de borrado y los
 * selectores de fecha y hora.
 */
class AgendaEventEditorDialogTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val fixedDateTime = LocalDateTime.of(2030, 1, 15, 9, 30)

    private fun millisOf(dateTime: LocalDateTime): Long = dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun newDraft(
        title: String = "",
        documentId: String? = null,
        dateTimeMillis: Long = millisOf(fixedDateTime),
        reminder: Int? = ReminderPreset.AT_TIME,
    ) = AgendaEventDraft(
        title = title,
        documentId = documentId,
        dateTimeMillis = dateTimeMillis,
        reminderMinutesBefore = reminder,
    )

    private fun editDraft(title: String = "Reunion") = newDraft(title = title).copy(id = "e1", createdAt = 1L)

    private fun setEditor(
        draft: AgendaEventDraft,
        onTitleChange: (String) -> Unit = {},
        onDescriptionChange: (String) -> Unit = {},
        onDateTimeChange: (Long) -> Unit = {},
        onReminderChange: (Int?) -> Unit = {},
        onLinkDocumentClick: () -> Unit = {},
        onUnlinkDocument: () -> Unit = {},
        onSave: () -> Unit = {},
        onDelete: () -> Unit = {},
        onDismiss: () -> Unit = {},
    ) {
        composeRule.setContentEs {
            AgendaEventEditorDialog(
                draft = draft,
                onTitleChange = onTitleChange,
                onDescriptionChange = onDescriptionChange,
                onDateTimeChange = onDateTimeChange,
                onReminderChange = onReminderChange,
                onLinkDocumentClick = onLinkDocumentClick,
                onUnlinkDocument = onUnlinkDocument,
                onSave = onSave,
                onDelete = onDelete,
                onDismiss = onDismiss,
            )
        }
    }

    // Botón dentro del AlertDialog de confirmación de borrado: el editor
    // también tiene "Eliminar"/"Cancelar", así que se distingue por estar en
    // la misma ventana que el título de la confirmación.
    private fun confirmDialogButton(label: String): SemanticsMatcher =
        hasText(label) and hasAnyAncestor(hasAnyDescendant(hasText(esString(R.string.agenda_delete_confirm_title))))

    @Test
    fun eventoNuevoSinTitulo_guardarDeshabilitadoYSinEliminar() {
        setEditor(newDraft())

        composeRule.onNodeWithText(esString(R.string.agenda_editor_title_new)).assertIsDisplayed()
        composeRule.onNodeWithText(esString(R.string.general_save)).assertIsNotEnabled()
        composeRule.onNodeWithText(esString(R.string.general_delete)).assertDoesNotExist()
        composeRule.onNodeWithText(esString(R.string.agenda_document_not_linked)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun escribirTituloYDescripcion_invocaLosCallbacks() {
        val titles = mutableListOf<String>()
        val descriptions = mutableListOf<String>()
        setEditor(newDraft(), onTitleChange = { titles.add(it) }, onDescriptionChange = { descriptions.add(it) })

        composeRule.onNodeWithText(esString(R.string.agenda_field_title)).performTextInput("Cita")
        composeRule.onNodeWithText(esString(R.string.agenda_field_description)).performTextInput("Detalle")

        // El borrador es fijo (no se re-escribe el estado), así que Compose puede
        // reportar además un valor vacío tras el primero: basta con el texto escrito.
        assertEquals("Cita", titles.first())
        assertEquals("Detalle", descriptions.first())
    }

    @Test
    fun conTituloEnEdicion_guardarHabilitadoEInvocaOnSave() {
        var saves = 0
        setEditor(editDraft("Reunion"), onSave = { saves++ })

        composeRule.onNodeWithText(esString(R.string.agenda_editor_title_edit)).assertIsDisplayed()
        composeRule.onNodeWithText(esString(R.string.general_save)).assertIsEnabled().performClick()

        assertEquals(1, saves)
    }

    @Test
    fun cancelar_invocaOnDismiss() {
        var dismissed = 0
        setEditor(newDraft(), onDismiss = { dismissed++ })

        composeRule.onNodeWithText(esString(R.string.general_cancel)).performClick()

        assertEquals(1, dismissed)
    }

    @Test
    fun chipsDeRecordatorio_invocanOnReminderChange() {
        val reminders = mutableListOf<Int?>()
        setEditor(newDraft(), onReminderChange = { reminders.add(it) })

        composeRule.onNodeWithText(esString(R.string.agenda_reminder_none)).performScrollTo().performClick()
        composeRule.onNodeWithText(esString(R.string.agenda_reminder_15_min)).performScrollTo().performClick()
        composeRule.onNodeWithText(esString(R.string.agenda_reminder_1_day)).performScrollTo().performClick()

        assertEquals(listOf(null, ReminderPreset.MINUTES_15, ReminderPreset.DAY_1), reminders)
        composeRule.onNodeWithText(esString(R.string.agenda_reminder_at_time)).assertExists()
        composeRule.onNodeWithText(esString(R.string.agenda_reminder_1_hour)).assertExists()
    }

    @Test
    fun recordatorioYaVencido_muestraElAviso() {
        val ayer = System.currentTimeMillis() - 24 * 60 * 60 * 1_000L
        setEditor(newDraft(dateTimeMillis = ayer, reminder = ReminderPreset.AT_TIME))

        composeRule
            .onNodeWithText(esString(R.string.agenda_reminder_already_past))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun recordatorioFuturoOSinRecordatorio_noMuestraElAviso() {
        setEditor(newDraft(reminder = ReminderPreset.HOUR_1))

        composeRule.onNodeWithText(esString(R.string.agenda_reminder_already_past)).assertDoesNotExist()
    }

    @Test
    fun sinDocumento_iconoDeVincularInvocaOnLinkDocumentClick() {
        var links = 0
        setEditor(newDraft(), onLinkDocumentClick = { links++ })

        composeRule
            .onNodeWithContentDescription(esString(R.string.agenda_link_document_title))
            .performScrollTo()
            .performClick()

        assertEquals(1, links)
    }

    @Test
    fun conDocumento_muestraVinculadoYDesvincula() {
        var unlinks = 0
        setEditor(newDraft(documentId = "doc-1"), onUnlinkDocument = { unlinks++ })

        composeRule.onNodeWithText(esString(R.string.agenda_document_linked)).performScrollTo().assertIsDisplayed()
        composeRule
            .onNodeWithContentDescription(esString(R.string.agenda_unlink_document))
            .performScrollTo()
            .performClick()

        assertEquals(1, unlinks)
    }

    @Test
    fun eliminarConfirmado_invocaOnDeleteYCierraLaConfirmacion() {
        var deletes = 0
        setEditor(editDraft("Reunion"), onDelete = { deletes++ })
        val confirmTitle = esString(R.string.agenda_delete_confirm_title)

        composeRule.onNodeWithText(esString(R.string.general_delete)).performScrollTo().performClick()
        composeRule.onNodeWithText(confirmTitle).assertIsDisplayed()
        composeRule.onNodeWithText(esString(R.string.agenda_delete_confirm_body, "Reunion")).assertExists()
        composeRule.onNode(confirmDialogButton(esString(R.string.general_delete)) and hasClickAction()).performClick()

        assertEquals(1, deletes)
        composeRule.onNodeWithText(confirmTitle).assertDoesNotExist()
    }

    @Test
    fun eliminarCancelado_noInvocaOnDelete() {
        var deletes = 0
        setEditor(editDraft("Reunion"), onDelete = { deletes++ })
        val confirmTitle = esString(R.string.agenda_delete_confirm_title)

        composeRule.onNodeWithText(esString(R.string.general_delete)).performScrollTo().performClick()
        composeRule.onNodeWithText(confirmTitle).assertIsDisplayed()
        composeRule.onNode(confirmDialogButton(esString(R.string.general_cancel)) and hasClickAction()).performClick()

        assertEquals(0, deletes)
        composeRule.onNodeWithText(confirmTitle).assertDoesNotExist()
    }

    @Test
    fun selectorDeFecha_aceptarEmiteLaFechaElegida() {
        val changes = mutableListOf<Long>()
        setEditor(newDraft(), onDateTimeChange = { changes.add(it) })
        val locale = composeRule.activity.resources.configuration.locales[0]
        val dateLabel = fixedDateTime.format(agendaDateFormatter(locale))

        composeRule.onNodeWithText(dateLabel).performScrollTo().performClick()
        composeRule.onNodeWithText(esString(R.string.general_accept)).performClick()

        assertEquals(1, changes.size)
        val emitted = changes.first().toAgendaLocalDateTime()
        assertEquals(LocalDate.of(2030, 1, 15), emitted.toLocalDate())
        assertEquals(fixedDateTime.toLocalTime(), emitted.toLocalTime())
    }

    @Test
    fun selectorDeHora_aceptarEmiteLaHoraActual() {
        val changes = mutableListOf<Long>()
        setEditor(newDraft(), onDateTimeChange = { changes.add(it) })
        val locale = composeRule.activity.resources.configuration.locales[0]
        // El formato 12/24h depende del ajuste del dispositivo: se acepta cualquiera de los dos.
        val label24 = fixedDateTime.format(agendaTimeFormatter(locale, true))
        val label12 = fixedDateTime.format(agendaTimeFormatter(locale, false))

        composeRule.onNode(hasText(label24) or hasText(label12)).performScrollTo().performClick()
        composeRule.onNodeWithText(esString(R.string.general_accept)).performClick()

        assertEquals(1, changes.size)
        val emitted = changes.first().toAgendaLocalDateTime()
        assertEquals(9, emitted.hour)
        assertEquals(30, emitted.minute)
        assertTrue(emitted.toLocalDate() == LocalDate.of(2030, 1, 15))
    }
}
