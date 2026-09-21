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

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
