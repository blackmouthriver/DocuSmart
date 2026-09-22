package com.docsmart.features.study.domain

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import com.docsmart.R
import com.docsmart.core.ui.theme.ColorOcr
import com.docsmart.core.ui.theme.ColorPowerPoint
import com.docsmart.core.ui.theme.ColorZip
import com.docsmart.core.ui.theme.DocuBlue
import com.docsmart.core.ui.theme.ErrorRed
import com.docsmart.core.ui.theme.IndigoAccent
import com.docsmart.core.ui.theme.PremiumGold
import com.docsmart.core.ui.theme.SlateGray
import com.docsmart.core.ui.theme.SmartBlue
import com.docsmart.core.ui.theme.SuccessGreen

data class VoicePersona(
    val name: String,
    val isFeminine: Boolean,
    val avatarColor: Color,
    // Backlog UX 2026-09-16/17 (seguimiento #40): reemplaza el círculo de
    // color + ícono genérico por un personaje ilustrado propio -- imágenes
    // generadas por el usuario (Gemini), no fotos de bancos de imágenes ni
    // de personas reales. avatarColor se conserva como anillo de color
    // alrededor del avatar, mismo criterio de identidad por color que ya
    // tenía cada persona.
    @DrawableRes val avatarDrawableRes: Int,
)

// HU-64 (backlog UX 2026-09-16, feedback real de testers de la prueba
// cerrada): android.speech.tts.Voice no expone el género de una voz --
// esta lista curada asigna nombre + color de avatar por VOZ TÉCNICA de
// forma determinística (ver personaForVoice), no un dato real leído del
// motor TTS. Los nombres quedan iguales en los 12 idiomas de la app a
// propósito (son identidades de personaje, no texto de interfaz -- mismo
// criterio que un nombre de asistente de voz, no se traduce).
private val VOICE_PERSONAS =
    listOf(
        VoicePersona(
            "Sofía",
            isFeminine = true,
            avatarColor = DocuBlue,
            avatarDrawableRes = R.drawable.voice_avatar_sofia,
        ),
        VoicePersona(
            "Mateo",
            isFeminine = false,
            avatarColor = SuccessGreen,
            avatarDrawableRes = R.drawable.voice_avatar_mateo,
        ),
        VoicePersona(
            "Valentina",
            isFeminine = true,
            avatarColor = ColorPowerPoint,
            avatarDrawableRes = R.drawable.voice_avatar_valentina,
        ),
        VoicePersona(
            "Diego",
            isFeminine = false,
            avatarColor = IndigoAccent,
            avatarDrawableRes = R.drawable.voice_avatar_diego,
        ),
        VoicePersona(
            "Camila",
            isFeminine = true,
            avatarColor = ErrorRed,
            avatarDrawableRes = R.drawable.voice_avatar_camila,
        ),
        VoicePersona(
            "Sebastián",
            isFeminine = false,
            avatarColor = ColorOcr,
            avatarDrawableRes = R.drawable.voice_avatar_sebastian,
        ),
        VoicePersona(
            "Isabella",
            isFeminine = true,
            avatarColor = PremiumGold,
            avatarDrawableRes = R.drawable.voice_avatar_isabella,
        ),
        VoicePersona(
            "Emilio",
            isFeminine = false,
            avatarColor = SmartBlue,
            avatarDrawableRes = R.drawable.voice_avatar_emilio,
        ),
        VoicePersona(
            "Lucía",
            isFeminine = true,
            avatarColor = ColorZip,
            avatarDrawableRes = R.drawable.voice_avatar_lucia,
        ),
        VoicePersona(
            "Nicolás",
            isFeminine = false,
            avatarColor = SlateGray,
            avatarDrawableRes = R.drawable.voice_avatar_nicolas,
        ),
    )

// Determinístico por String.hashCode() (algoritmo estable, documentado
// por la especificación de Java) -- la misma voz técnica del dispositivo
// (ej. "es-es-x-eef-local") siempre cae en el mismo índice, así que
// muestra siempre el mismo personaje entre sesiones y entre reaperturas
// del selector, sin guardar ningún mapeo aparte.
//
// Solo para cuando no se tiene la lista completa de voces a mano (fallback
// de `personasForVoices`, más abajo) -- usada sola, dos voces técnicas
// distintas pueden caer en el mismo personaje si el dispositivo tiene más
// voces instaladas que personajes en la lista (frecuente: Google TTS expone
// varias variantes de "es" por región y calidad).
fun personaForVoice(voiceTechnicalName: String): VoicePersona {
    val index = Math.floorMod(voiceTechnicalName.hashCode(), VOICE_PERSONAS.size)
    return VOICE_PERSONAS[index]
}

// Pedido explícito del usuario 2026-09-22 ("hay varios personajes que repiten
// nombre... la idea es que cada uno sea diferente"): un personaje ÚNICO por
// voz DENTRO de la lista que se muestra junta en el selector -- a diferencia
// de `personaForVoice`, que resuelve cada voz sola (con hash % 10, así que
// con más de 10 voces instaladas, dos técnicas distintas repetían personaje).
// Acá cada voz recibe una posición fija dentro de la lista ordenada por su
// nombre técnico (determinístico: mismas voces instaladas -> mismo orden ->
// mismos personajes, sesión tras sesión) y esa posición indexa directo la
// lista de personajes -- sin colisiones mientras la cantidad de voces no
// supere la de personajes; si la supera, recién ahí se repite (imposible
// evitarlo con solo 10 personajes curados, pero ya no antes de eso).
fun personasForVoices(voiceTechnicalNames: List<String>): Map<String, VoicePersona> =
    voiceTechnicalNames
        .distinct()
        .sorted()
        .mapIndexed { index, name -> name to VOICE_PERSONAS[index % VOICE_PERSONAS.size] }
        .toMap()
