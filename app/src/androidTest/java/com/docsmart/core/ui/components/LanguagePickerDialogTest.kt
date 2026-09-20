package com.docsmart.core.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.platform.app.InstrumentationRegistry
import com.docsmart.R
import com.docsmart.core.ui.AppLanguage
import com.docsmart.core.ui.test.forceLocale
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Ronda 18: selector de idioma (hoja inferior con grilla de tarjetas). Al ser
 * un ModalBottomSheet a media altura, las acciones se invocan por semántica
 * (OnClick) en vez de por coordenadas, que podrían quedar fuera de pantalla.
 */
class LanguagePickerDialogTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val current = mutableStateOf(AppLanguage.SPANISH)

    private fun string(resId: Int): String =
        forceLocale(InstrumentationRegistry.getInstrumentation().targetContext, "es-ES").getString(resId)

    private fun setPicker(
        onSelect: (AppLanguage) -> Unit = {},
        onDismiss: () -> Unit = {},
    ) {
        composeRule.setContent {
            val baseContext = LocalContext.current
            val localizedContext = remember(baseContext) { forceLocale(baseContext, "es-ES") }
            CompositionLocalProvider(LocalContext provides localizedContext, LocalResources provides localizedContext.resources) {
                MaterialTheme {
                    LanguagePickerDialog(
                        currentLanguage = current.value,
                        onSelect = onSelect,
                        onDismiss = onDismiss,
                    )
                }
            }
        }
    }

    private fun invokeClick(text: String) {
        composeRule.onNodeWithText(text).performSemanticsAction(SemanticsActions.OnClick)
    }

    @Test
    fun muestraTituloSubtituloYMarcaElIdiomaActual() {
        setPicker()

        composeRule.onNodeWithText(string(R.string.settings_select_language)).assertExists()
        composeRule.onNodeWithText(string(R.string.settings_select_language_subtitle)).assertExists()
        composeRule.onNodeWithText(AppLanguage.SPANISH.nativeLabel).assertIsSelected()
        composeRule.onNodeWithText(AppLanguage.ENGLISH.nativeLabel).assertIsNotSelected()
    }

    @Test
    fun tocarUnIdioma_invocaOnSelectConEseIdioma() {
        val selected = mutableListOf<AppLanguage>()
        setPicker(onSelect = { selected += it })

        invokeClick(AppLanguage.ENGLISH.nativeLabel)
        composeRule.waitForIdle()

        assertEquals(listOf(AppLanguage.ENGLISH), selected)
    }

    @Test
    fun cambiarElIdiomaActual_muevelaSeleccion() {
        setPicker()

        composeRule.runOnIdle { current.value = AppLanguage.ENGLISH }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(AppLanguage.ENGLISH.nativeLabel).assertIsSelected()
        composeRule.onNodeWithText(AppLanguage.SPANISH.nativeLabel).assertIsNotSelected()
    }

    @Test
    fun tocarCerrar_invocaOnDismiss() {
        var dismissed = false
        setPicker(onDismiss = { dismissed = true })

        invokeClick(string(R.string.settings_close))
        composeRule.waitForIdle()

        assertEquals(true, dismissed)
    }
}
