package com.docsmart.features.scanner.presentation

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * DocumentScannerLauncher.kt: `rememberDocumentScannerAction` sin Activity real. Sin
 * Activity el escáner de ML Kit (GMS) nunca se lanza: la acción es un no-op seguro y
 * no se invoca ningún callback.
 */
class DocumentScannerLauncherTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun accionSinActivity_noLanzaElEscanerNiInvocaCallbacks() {
        val pages = mutableListOf<List<Uri>>()
        val errors = mutableListOf<String>()
        composeRule.setContentEsFit(registryOwner = FakeResultRegistryOwner()) {
            val action =
                rememberDocumentScannerAction(
                    activity = null,
                    mode = ScannerMode.PHOTO,
                    pageLimit = 3,
                    onPagesScanned = { pages += it },
                    onScanError = { errors += it },
                )
            Button(onClick = action) { Text("Escanear") }
        }

        composeRule.onNodeWithText("Escanear").performClick()
        composeRule.waitForIdle()

        assertEquals(0, pages.size)
        assertEquals(0, errors.size)
    }

    @Test
    fun constanteDeLimiteDePaginas_esDiez() {
        assertEquals(10, SCAN_DEFAULT_PAGE_LIMIT)
    }
}
