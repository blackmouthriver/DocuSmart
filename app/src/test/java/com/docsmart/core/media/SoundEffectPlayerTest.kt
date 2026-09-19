package com.docsmart.core.media

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.SoundPool
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.slot
import io.mockk.unmockkConstructor
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * `SoundPool`/`AudioAttributes` se construyen vía sus `Builder` reales
 * dentro del constructor de `SoundEffectPlayer` -- sin Robolectric en este
 * proyecto, esos `Builder.build()` reventarían con el stub de `android.jar`
 * si no se interceptan. Se usa `mockkConstructor` (mismo mecanismo, sobre
 * clases distintas, que `mockkStatic` ya usado en `MediaDeletePermissionTest`)
 * para poder ejercitar la lógica real de la clase: el gate de habilitado/
 * deshabilitado, el gate de "sonido aún no cargó" (bug real corregido
 * 2026-09-14: `SoundPool.load()` es async, `play()` antes de tiempo no hacía
 * nada) y, sobre todo, una prueba de regresión directa para el bug real ya
 * corregido 2026-09-14 de `USAGE_ASSISTANCE_SONIFICATION` (enrutaba al
 * stream del timbre, mudo en silencio/vibración) en vez de `USAGE_MEDIA`.
 */
class SoundEffectPlayerTest {

    private lateinit var context: Context
    private lateinit var prefsStore: MutableMap<String, Any?>
    private lateinit var soundPool: SoundPool
    private var loadCompleteListener: SoundPool.OnLoadCompleteListener? = null
    private val usageSlot = slot<Int>()

    @BeforeEach
    fun setUp() {
        prefsStore = mutableMapOf()
        val prefs = fakeSharedPreferences(prefsStore)
        context = mockk()
        every { context.getSharedPreferences(any(), any()) } returns prefs

        soundPool = mockk(relaxed = true)
        var nextId = 1
        every { soundPool.load(any<Context>(), any(), any()) } answers { nextId++ }
        every { soundPool.setOnLoadCompleteListener(any()) } answers {
            loadCompleteListener = firstArg()
        }

        mockkConstructor(SoundPool.Builder::class)
        every { anyConstructed<SoundPool.Builder>().setMaxStreams(any()) } answers { self as SoundPool.Builder }
        every { anyConstructed<SoundPool.Builder>().setAudioAttributes(any()) } answers { self as SoundPool.Builder }
        every { anyConstructed<SoundPool.Builder>().build() } returns soundPool

        mockkConstructor(AudioAttributes.Builder::class)
        every { anyConstructed<AudioAttributes.Builder>().setUsage(capture(usageSlot)) } answers {
            self as AudioAttributes.Builder
        }
        every { anyConstructed<AudioAttributes.Builder>().setContentType(any()) } answers {
            self as AudioAttributes.Builder
        }
        every { anyConstructed<AudioAttributes.Builder>().build() } returns mockk()
    }

    @AfterEach
    fun tearDown() {
        unmockkConstructor(SoundPool.Builder::class)
        unmockkConstructor(AudioAttributes.Builder::class)
    }

    private fun newPlayer(): SoundEffectPlayer = SoundEffectPlayer(context)

    /** Simula que SoundPool ya terminó de decodificar los 3 sonidos (status=0, éxito). */
    private fun markAllLoaded() {
        repeat(3) { i -> loadCompleteListener?.onLoadComplete(soundPool, i + 1, 0) }
    }

    @Test
    fun `construye SoundPool con USAGE_MEDIA y no con USAGE_ASSISTANCE_SONIFICATION`() {
        // Regresión directa del bug real reportado por el usuario
        // 2026-09-14: con USAGE_ASSISTANCE_SONIFICATION (stream del
        // timbre), ningún sonido se escuchaba con el teléfono en
        // silencio/vibración, sin importar el volumen de medios.
        newPlayer()

        assertEquals(AudioAttributes.USAGE_MEDIA, usageSlot.captured)
        assertFalse(usageSlot.captured == AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
    }

    @Test
    fun `los efectos de sonido estan habilitados por defecto`() {
        val player = newPlayer()

        assertTrue(player.enabled.value)
    }

    @Test
    fun `setEnabled false persiste la preferencia y actualiza el StateFlow`() {
        val player = newPlayer()

        player.setEnabled(false)

        assertFalse(player.enabled.value)
        assertEquals(false, prefsStore["sound_effects_enabled"])
    }

    @Test
    fun `un player nuevo respeta la preferencia ya guardada de una sesion anterior`() {
        prefsStore["sound_effects_enabled"] = false

        val player = newPlayer()

        assertFalse(player.enabled.value)
    }

    @Test
    fun `playScan no suena si los efectos estan deshabilitados`() {
        val player = newPlayer()
        markAllLoaded()
        player.setEnabled(false)

        player.playScan()

        verify(exactly = 0) { soundPool.play(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `playScan no suena si SoundPool todavia no termino de cargar ese sonido`() {
        // Bug real corregido 2026-09-14: SoundPool.load() es asíncrono --
        // sin este gate, play() antes de que termine de decodificar no
        // hacía nada pero tampoco avisaba, dejando la app "muda" en
        // dispositivos lentos.
        val player = newPlayer()
        // No se dispara markAllLoaded(): el sonido nunca "terminó" de cargar.

        player.playScan()

        verify(exactly = 0) { soundPool.play(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `playScan suena una vez que SoundPool confirma que el sonido cargo`() {
        val player = newPlayer()
        markAllLoaded()

        player.playScan()

        verify(exactly = 1) { soundPool.play(1, 0.7f, 0.7f, 1, 0, 1f) }
    }

    @Test
    fun `un sonido que fallo al cargar (status distinto de 0) nunca se reproduce`() {
        val player = newPlayer()
        // status=1 (AL_INVALID_OPERATION / falla genérica) para el sonido de scan (id 1).
        loadCompleteListener?.onLoadComplete(soundPool, 1, 1)
        loadCompleteListener?.onLoadComplete(soundPool, 2, 0)
        loadCompleteListener?.onLoadComplete(soundPool, 3, 0)

        player.playScan()
        player.playConvert()

        verify(exactly = 0) { soundPool.play(1, any(), any(), any(), any(), any()) }
        verify(exactly = 1) { soundPool.play(2, any(), any(), any(), any(), any()) }
    }

    private fun fakeSharedPreferences(store: MutableMap<String, Any?>): SharedPreferences {
        val editor = mockk<SharedPreferences.Editor>()
        every { editor.putBoolean(any(), any()) } answers {
            store[firstArg<String>()] = secondArg<Boolean>()
            editor
        }
        every { editor.apply() } just Runs

        val prefs = mockk<SharedPreferences>()
        every { prefs.edit() } returns editor
        every { prefs.getBoolean(any(), any()) } answers {
            (store[firstArg<String>()] as? Boolean) ?: secondArg()
        }
        return prefs
    }
}
