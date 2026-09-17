package com.docsmart.features.study.domain

import androidx.compose.ui.graphics.Color
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
    val avatarColor: Color
)

// HU-64 (backlog UX 2026-09-16, feedback real de testers de la prueba
// cerrada): android.speech.tts.Voice no expone el género de una voz --
// esta lista curada asigna nombre + color de avatar por VOZ TÉCNICA de
// forma determinística (ver personaForVoice), no un dato real leído del
// motor TTS. Los nombres quedan iguales en los 12 idiomas de la app a
// propósito (son identidades de personaje, no texto de interfaz -- mismo
// criterio que un nombre de asistente de voz, no se traduce).
private val VOICE_PERSONAS = listOf(
    VoicePersona("Sofía", isFeminine = true, avatarColor = DocuBlue),
    VoicePersona("Mateo", isFeminine = false, avatarColor = SuccessGreen),
    VoicePersona("Valentina", isFeminine = true, avatarColor = ColorPowerPoint),
    VoicePersona("Diego", isFeminine = false, avatarColor = IndigoAccent),
    VoicePersona("Camila", isFeminine = true, avatarColor = ErrorRed),
    VoicePersona("Sebastián", isFeminine = false, avatarColor = ColorOcr),
    VoicePersona("Isabella", isFeminine = true, avatarColor = PremiumGold),
    VoicePersona("Emilio", isFeminine = false, avatarColor = SmartBlue),
    VoicePersona("Lucía", isFeminine = true, avatarColor = ColorZip),
    VoicePersona("Nicolás", isFeminine = false, avatarColor = SlateGray)
)

// Determinístico por String.hashCode() (algoritmo estable, documentado
// por la especificación de Java) -- la misma voz técnica del dispositivo
// (ej. "es-es-x-eef-local") siempre cae en el mismo índice, así que
// muestra siempre el mismo personaje entre sesiones y entre reaperturas
// del selector, sin guardar ningún mapeo aparte.
fun personaForVoice(voiceTechnicalName: String): VoicePersona {
    val index = Math.floorMod(voiceTechnicalName.hashCode(), VOICE_PERSONAS.size)
    return VOICE_PERSONAS[index]
}
