package com.docsmart.features.study.domain

import android.content.Context

/**
 * Voz de Text-to-Speech elegida por el usuario para "Lectura en voz alta"
 * (pedido explícito de testers 2026-09-12: más opciones de voz, gratis y sin
 * salir del dispositivo). Se persiste solo el nombre técnico de la voz
 * (`android.speech.tts.Voice.name`, ej. "es-es-x-eef-local") -- alcanza para
 * volver a seleccionarla la próxima vez que el motor TTS del sistema
 * devuelva la misma lista de voces instaladas.
 */
object StudyVoicePreference {
    private const val PREFS_NAME = "study_voice"
    private const val KEY_VOICE_NAME = "voice_name"
    private const val KEY_SPEED = "reading_speed"
    private const val KEY_GENDER_PREFIX = "voice_gender_"

    fun save(
        context: Context,
        voiceName: String,
    ) {
        prefs(context).edit().putString(KEY_VOICE_NAME, voiceName).apply()
    }

    fun load(context: Context): String? = prefs(context).getString(KEY_VOICE_NAME, null)

    // Velocidad de lectura elegida (factor sobre la base; 1.0 = la de siempre).
    fun saveSpeed(
        context: Context,
        speed: Float,
    ) {
        prefs(context).edit().putFloat(KEY_SPEED, speed).apply()
    }

    fun loadSpeed(context: Context): Float = prefs(context).getFloat(KEY_SPEED, 1f)

    // Pedido explícito del usuario 2026-09-22: el género de cada voz se
    // detecta escuchándola (ver VoiceGenderProbe.kt, tarda unos segundos por
    // voz) -- se guarda una vez por voz técnica para no tener que repetir la
    // síntesis + análisis en cada apertura del selector.
    fun saveGender(
        context: Context,
        voiceName: String,
        isFeminine: Boolean,
    ) {
        prefs(context).edit().putBoolean(KEY_GENDER_PREFIX + voiceName, isFeminine).apply()
    }

    fun loadGenders(
        context: Context,
        voiceNames: List<String>,
    ): Map<String, Boolean> {
        val p = prefs(context)
        return voiceNames
            .filter { p.contains(KEY_GENDER_PREFIX + it) }
            .associateWith { p.getBoolean(KEY_GENDER_PREFIX + it, true) }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
