package com.docsmart.features.agenda.presentation.components

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.test.platform.app.InstrumentationRegistry
import com.docsmart.core.ui.test.forceLocale

/**
 * Utilidades compartidas por las pruebas de Agenda/diálogos de vínculo:
 * fijan el idioma (el emulador de CI arranca en inglés, ver
 * [com.docsmart.core.ui.test.forceLocale]) y resuelven los textos esperados
 * desde los recursos reales en vez de literales.
 */
internal const val TEST_LANGUAGE_TAG = "es-ES"

internal fun localizedTargetContext(): Context = forceLocale(InstrumentationRegistry.getInstrumentation().targetContext, TEST_LANGUAGE_TAG)

internal fun esString(
    @StringRes id: Int,
    vararg args: Any,
): String = localizedTargetContext().getString(id, *args)

// Reprovee también el registro de resultados y el back dispatcher apuntando
// a la Activity real: AgendaScreen usa rememberLauncherForActivityResult().
internal fun AndroidComposeTestRule<*, ComponentActivity>.setContentEs(content: @Composable () -> Unit) {
    setContent {
        val baseContext = LocalContext.current
        val localizedContext = remember(baseContext) { forceLocale(baseContext, TEST_LANGUAGE_TAG) }
        CompositionLocalProvider(
            LocalContext provides localizedContext,
            LocalActivityResultRegistryOwner provides activity,
            LocalOnBackPressedDispatcherOwner provides activity,
        ) { content() }
    }
}
