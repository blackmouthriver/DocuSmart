package com.docsmart.core.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/**
 * Renderiza [DocuSmartTheme] en cada combinación de modo/acento/tamaño de letra
 * y verifica lo que el tema publica a través de MaterialTheme (sin depender del
 * idioma ni del tema real del dispositivo).
 */
class DocuSmartThemeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var dark by mutableStateOf(false)
    private var system by mutableStateOf(false)
    private var dynamic by mutableStateOf(false)
    private var accent by mutableStateOf(AccentColor.BLUE)
    private var fontScale by mutableStateOf(1f)

    private var primary: Color = Color.Unspecified
    private var onPrimary: Color = Color.Unspecified
    private var background: Color = Color.Unspecified
    private var error: Color = Color.Unspecified
    private var typography: Typography? = null
    private var shapes: Shapes? = null
    private var systemFontScale = 1f

    private fun render() {
        composeRule.setContent {
            DocuSmartTheme(
                darkTheme = dark,
                useSystemTheme = system,
                dynamicColor = dynamic,
                accentColor = accent,
                fontScale = fontScale,
            ) {
                primary = MaterialTheme.colorScheme.primary
                onPrimary = MaterialTheme.colorScheme.onPrimary
                background = MaterialTheme.colorScheme.background
                error = MaterialTheme.colorScheme.error
                typography = MaterialTheme.typography
                shapes = MaterialTheme.shapes
                systemFontScale = LocalDensity.current.fontScale
            }
        }
        composeRule.waitForIdle()
    }

    private fun change(block: () -> Unit) {
        composeRule.runOnIdle(block)
        composeRule.waitForIdle()
    }

    @Test
    fun cadaAcento_enClaroYOscuro_recoloreaElPrimario() {
        render()
        AccentColor.entries.forEach { entry ->
            change {
                accent = entry
                dark = false
            }
            assertEquals(entry.light.primary, primary)
            assertEquals(entry.light.onPrimary, onPrimary)
            change { dark = true }
            assertEquals(entry.dark.primary, primary)
            assertEquals(entry.dark.onPrimary, onPrimary)
        }
    }

    @Test
    fun claroOscuroYSistema_danFondosDistintos_yElErrorNoCambiaConElAcento() {
        render()
        change { dark = false }
        val fondoClaro = background
        val errorClaro = error
        change { accent = AccentColor.PURPLE }
        assertEquals(fondoClaro, background)
        assertEquals(errorClaro, error)
        change { dark = true }
        assertNotEquals(fondoClaro, background)
        change {
            dark = false
            system = true
        }
        assertTrue(background != Color.Unspecified)
        // El acento se aplica también sobre el esquema de sistema.
        assertEquals(AccentColor.PURPLE.light.primary, primary)
        change {
            dark = true
            system = true
        }
        assertEquals(AccentColor.PURPLE.dark.primary, primary)
    }

    @Test
    fun colorDinamico_noPisaElPrimarioConElAcento() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        render()
        change {
            dynamic = true
            accent = AccentColor.GREEN
            dark = false
        }
        // En API 31+ (el emulador es API 34) el color viene del sistema, no del acento.
        assertTrue(primary != Color.Unspecified)
        assertNotEquals(AccentColor.GREEN.light.primary, primary)
        change { dark = true }
        assertTrue(primary != Color.Unspecified)
    }

    @Test
    fun tamanoDeLetra_escalaLaTipografiaRespetandoElTope() {
        render()
        val base = DocuSmartTypography.bodyLarge.fontSize.value
        listOf(FontScale.NORMAL, FontScale.LARGE, FontScale.EXTRA_LARGE).forEach { entry ->
            change { fontScale = entry.scale }
            val esperado =
                if (systemFontScale > 0f) {
                    (1.8f / systemFontScale).coerceAtMost(entry.scale).coerceAtLeast(1f)
                } else {
                    entry.scale
                }
            assertEquals(base * esperado, typography!!.bodyLarge.fontSize.value, 0.05f)
            assertEquals(
                DocuSmartTypography.titleLarge.fontSize.value * esperado,
                typography!!.titleLarge.fontSize.value,
                0.05f,
            )
        }
    }

    @Test
    fun formasDelTema_sonLasDeDocuSmart() {
        render()
        assertEquals(DocuSmartShapes, shapes)
    }

    @Test
    fun scaledBy_conFactorUno_dejaLosTamanosIntactos() {
        val igual = DocuSmartTypography.scaledBy(1f)
        assertEquals(DocuSmartTypography.headlineSmall.fontSize.value, igual.headlineSmall.fontSize.value, 0.001f)
        val doble = DocuSmartTypography.scaledBy(2f)
        assertEquals(DocuSmartTypography.labelSmall.fontSize.value * 2f, doble.labelSmall.fontSize.value, 0.001f)
    }
}
