package com.docsmart.core.media

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.docsmart.R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Efectos de sonido cortos (feedback de testers 2026-09-12) para escanear,
 * convertir y borrar. Tonos sintetizados propios (ver `res/raw/sound_*.wav`,
 * generados por código, no audio con licencia de terceros) -- mismo criterio
 * de privacidad/costo que ya rige el resto de la app ("100% local").
 *
 * `SoundPool` en vez de `MediaPlayer`: son sonidos de <150ms reproducidos
 * ante una acción del usuario, donde la latencia de arranque de
 * `MediaPlayer` (decodificación completa antes de reproducir) sería
 * perceptible; `SoundPool` precarga y reproduce con latencia mínima.
 */
@Singleton
class SoundEffectPlayer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _enabled = MutableStateFlow(loadEnabled())
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    // Bug real reportado por el usuario 2026-09-14: "no se escuchan los
    // sonidos" -- confirmado con `dumpsys audio` en dispositivo real:
    // USAGE_ASSISTANCE_SONIFICATION enruta al stream STREAM_SYSTEM, que en
    // Android sigue el modo silencio/vibración del timbre (ringer), no el
    // volumen de medios -- con el teléfono en silencio/vibración (un estado
    // cotidiano, no un caso raro), STREAM_SYSTEM queda muteado y ningún
    // sonido de esta clase se escucha nunca, sin importar el volumen de
    // medios. USAGE_MEDIA enruta a STREAM_MUSIC en su lugar, el mismo
    // volumen que ya gobierna el resto de audio de la app y el que el
    // usuario realmente sube/baja para "escuchar más o menos la app".
    private val soundPool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val scanSoundId    = soundPool.load(context, R.raw.sound_scan, 1)
    private val convertSoundId = soundPool.load(context, R.raw.sound_convert, 1)
    private val deleteSoundId  = soundPool.load(context, R.raw.sound_delete, 1)

    fun setEnabled(value: Boolean) {
        _enabled.value = value
        prefs.edit().putBoolean(KEY_ENABLED, value).apply()
        Timber.d("SoundEffectPlayer: efectos de sonido ${if (value) "activados" else "desactivados"}")
    }

    fun playScan()    = play(scanSoundId)
    fun playConvert() = play(convertSoundId)
    fun playDelete()  = play(deleteSoundId)

    private fun play(soundId: Int) {
        if (!_enabled.value) return
        soundPool.play(soundId, VOLUME, VOLUME, 1, 0, 1f)
    }

    private fun loadEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, true)

    private companion object {
        const val PREFS_NAME = "docusmart_sound"
        const val KEY_ENABLED = "sound_effects_enabled"
        const val VOLUME = 0.7f
    }
}
