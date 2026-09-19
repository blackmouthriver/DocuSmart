package com.docsmart.core.ads

import android.content.Context
import com.docsmart.core.util.elapsedRealtimeMillisSafe
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gestiona los límites diarios de operaciones para usuarios free.
 * Persiste contadores en SharedPreferences, se resetean cada día.
 */
@Singleton
class DailyLimitManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        companion object {
            private const val PREFS_NAME = "docusmart_daily_limits"
            private const val KEY_DATE = "current_date"
            private const val KEY_CONVERSIONS = "count_conversions"
            private const val KEY_MERGE = "count_merge"
            private const val KEY_SPLIT = "count_split"
            private const val KEY_COMPRESS = "count_compress"
            private const val KEY_ROTATE = "count_rotate"
            private const val KEY_NUMBER_PAGES = "count_number_pages"
            private const val KEY_WATERMARK = "count_watermark"
            private const val KEY_REORDER_PAGES = "count_reorder_pages"
            private const val KEY_COMPARE = "count_compare"
            private const val KEY_REDACT = "count_redact"
            private const val KEY_CROP = "count_crop"
            private const val KEY_EDIT_TEXT = "count_edit_text"
            private const val KEY_SIGN = "count_sign"
            private const val KEY_FILL_FORM = "count_fill_form"
            private const val KEY_OCR = "count_ocr"
            private const val KEY_EXTRACT_IMAGES = "count_extract_images"
            private const val KEY_EXTRA_CONVERSIONS = "extra_conversions"
            private const val KEY_EXTRA_PDF_TOOLS = "extra_pdf_tools"

            // Backlog UX (pedido explícito del usuario 2026-09-06): "escaneos
            // guardados" es un contador propio, independiente del de
            // conversiones -- un usuario puede agotar sus 8 escaneos guardados
            // del día sin que eso afecte sus 5 conversiones del Convertidor, y
            // viceversa (dos límites distintos, cada uno con su propio anuncio
            // recompensado).
            private const val KEY_SCANS_SAVED = "count_scans_saved"
            private const val KEY_EXTRA_SCANS_SAVED = "extra_scans_saved"

            // Hallazgo real de la auditoría general 2026-09-17 (séptima
            // ronda, Media): el reseteo solo miraba la fecha de pared
            // (`SimpleDateFormat`, ajustable por el usuario) -- cambiar la
            // fecha/zona horaria del dispositivo y volver reseteaba los
            // contadores a 0 sin límite, neutralizando por completo el
            // propósito de negocio del límite diario. Se ancla con el mismo
            // tipo de mecanismo de "reloj confiable" ya usado para el bloqueo
            // de PIN (`SecurityManager.trustedNowMillis()`, sexta ronda): un
            // primer intento de este fix solo comparaba `elapsedRealtime()`
            // contra el último reseteo y permitía el reseteo sin más ante
            // cualquier reinicio detectado -- exactamente el mismo bypass de
            // 2 pasos (adelantar el reloj + reiniciar el dispositivo) que
            // rompió el primer intento del fix de PIN, hallado por la revisión
            // adversarial de esta ronda. `trustedNowMillis()` de abajo congela
            // el ancla ante un reinicio en vez de confiar en el reloj de pared
            // en ese momento, así que un reinicio ya no basta para saltarse la
            // espera.
            private const val KEY_ANCHOR_WALL = "reset_anchor_wall"
            private const val KEY_ANCHOR_ELAPSED = "reset_anchor_elapsed"
            private const val KEY_LAST_RESET_TRUSTED = "last_reset_trusted"
            private const val KEY_CLOCK_VERSION = "reset_clock_version"
            private const val CLOCK_VERSION = 2
            private const val MIN_REAL_MS_BETWEEN_RESETS = 20L * 60 * 60 * 1000

            // ── Límites diarios ───────────────────────────────────────────────────
            const val LIMIT_CONVERSIONS = 5
            const val LIMIT_PDF_TOOLS = 3
            const val LIMIT_SCANS_SAVED = 8

            // Mapa en vez de `when` -- este dispatcher crece una entrada por cada
            // herramienta PDF nueva del backlog y ya había superado el umbral de
            // complejidad ciclomática de detekt (15) como `when` con 13 ramas.
            private val PDF_TOOL_KEYS =
                mapOf(
                    "MERGE" to KEY_MERGE,
                    "SPLIT" to KEY_SPLIT,
                    "COMPRESS" to KEY_COMPRESS,
                    "ROTATE" to KEY_ROTATE,
                    "NUMBER_PAGES" to KEY_NUMBER_PAGES,
                    "WATERMARK" to KEY_WATERMARK,
                    "REORDER_PAGES" to KEY_REORDER_PAGES,
                    "COMPARE" to KEY_COMPARE,
                    "REDACT" to KEY_REDACT,
                    "CROP" to KEY_CROP,
                    "EDIT_TEXT" to KEY_EDIT_TEXT,
                    "SIGN" to KEY_SIGN,
                    "FILL_FORM" to KEY_FILL_FORM,
                    "OCR" to KEY_OCR,
                    // Hallazgo real de la auditoría general 2026-09-17: faltaba acá
                    // -- getPdfToolKey() caía al ?: KEY_CONVERSIONS para cualquier
                    // clave no mapeada, así que "Extraer imágenes" consumía y
                    // revisaba el contador del Convertidor en vez del propio, mismo
                    // bug ya corregido dos veces antes para NUMBER_PAGES/WATERMARK.
                    "EXTRACT_IMAGES" to KEY_EXTRACT_IMAGES,
                )
        }

        private val prefs by lazy {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }

        // ── Verificar y resetear si cambió el día ─────────────────────────────────
        @Synchronized
        private fun checkAndResetIfNewDay() {
            // Hallazgo real de la auditoría general 2026-09-17 (B7): antes era
            // una instancia compartida a nivel de clase -- `SimpleDateFormat` no
            // es thread-safe (estado mutable interno), y este Singleton se
            // inyecta en varios ViewModels que pueden revisar el límite diario
            // en corrutinas concurrentes (Escáner/Convertidor/Herramientas PDF a
            // la vez). Una instancia nueva por llamada es igual de barata que el
            // resto de timestamps de la app (ver createOutputFile() en cada
            // UseCase) y elimina el problema de raíz.
            // El ancla del reloj de confianza debe avanzar en CADA uso, no solo al
            // cambiar de día: si no, tras un reinicio entre dos resets quedaba
            // días atrasada y bloqueaba el reseteo.
            val trustedNow = trustedNowMillis()
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val savedDate = prefs.getString(KEY_DATE, "")
            if (savedDate == today) return

            val lastResetTrusted = prefs.getLong(KEY_LAST_RESET_TRUSTED, 0L)
            // lastResetTrusted==0L cubre la primera vez que se llama (nunca
            // hubo un reset registrado) -- se permite el reset y queda fijado
            // como punto de partida. De ahí en más, trustedNow ya no puede
            // avanzar por un simple cambio del reloj de pared ni por un
            // reinicio del dispositivo (ver trustedNowMillis()), así que esta
            // resta sí refleja tiempo real transcurrido.
            val realTimeElapsedEnough =
                lastResetTrusted == 0L ||
                    trustedNow - lastResetTrusted >= MIN_REAL_MS_BETWEEN_RESETS
            if (!realTimeElapsedEnough) {
                Timber.w(
                    "DailyLimitManager: fecha cambió a $today pero no pasó suficiente tiempo real " +
                        "desde el último reseteo -- se ignora (posible manipulación del reloj)",
                )
                return
            }

            Timber.d("DailyLimitManager: nuevo día — reseteando contadores")
            prefs
                .edit()
                .putString(KEY_DATE, today)
                .putLong(KEY_LAST_RESET_TRUSTED, trustedNow)
                .putInt(KEY_CONVERSIONS, 0)
                .putInt(KEY_MERGE, 0)
                .putInt(KEY_SPLIT, 0)
                .putInt(KEY_COMPRESS, 0)
                .putInt(KEY_ROTATE, 0)
                .putInt(KEY_NUMBER_PAGES, 0)
                .putInt(KEY_WATERMARK, 0)
                .putInt(KEY_REORDER_PAGES, 0)
                .putInt(KEY_COMPARE, 0)
                .putInt(KEY_REDACT, 0)
                .putInt(KEY_CROP, 0)
                .putInt(KEY_EDIT_TEXT, 0)
                .putInt(KEY_SIGN, 0)
                .putInt(KEY_FILL_FORM, 0)
                .putInt(KEY_OCR, 0)
                // Hallazgo real de la ronda 16: faltaba acá -- el contador de
                // "Extraer imágenes" nunca se reseteaba al cambiar de día, así
                // que un usuario free lo agotaba para siempre.
                .putInt(KEY_EXTRACT_IMAGES, 0)
                .putInt(KEY_EXTRA_CONVERSIONS, 0)
                .putInt(KEY_EXTRA_PDF_TOOLS, 0)
                .putInt(KEY_SCANS_SAVED, 0)
                .putInt(KEY_EXTRA_SCANS_SAVED, 0)
                .apply()
        }

        // Reconstruye un "ahora" que no puede adelantarse solo por manipular el
        // reloj de pared ni por reiniciar el dispositivo -- mismo tipo de ancla
        // que `SecurityManager.trustedNowMillis()` (sexta ronda, bloqueo de
        // PIN), reimplementada acá mismo (en vez de extraerla y compartirla)
        // para no tocar ese código ya probado y en producción por un hallazgo
        // de severidad Media.
        //
        // Mientras no haya reinicio, avanza como `elapsedRealtime()` real
        // (inmune al reloj de pared). Si detecta un reinicio (elapsed actual
        // menor al ancla guardada), NO confía en el reloj de pared de ese
        // instante -- solo re-basa el punto de partida de `elapsedRealtime()`
        // para el nuevo arranque y sigue devolviendo el mismo valor de ancla
        // congelado hasta que pase tiempo real de verdad en este nuevo arranque.
        // `minOf(currentWall, reconstruido)` es la parte que de verdad resiste
        // el ataque: ante un reloj adelantado, siempre gana el valor más
        // conservador (el que diga que pasó MENOS tiempo), nunca el que el
        // usuario pueda inflar a su favor.
        //
        // Hallazgo real de la ronda 16: el ancla solo se persistía UNA vez (la
        // primera llamada) y nunca avanzaba. Tras cualquier reinicio normal del
        // teléfono, el "ahora de confianza" arrancaba de esa ancla vieja (días
        // atrás) + tiempo desde el arranque, quedando MUY por detrás de
        // `lastResetTrusted` (que se registró antes del reinicio): la resta
        // `trustedNow - lastResetTrusted` daba negativa y los contadores diarios
        // dejaban de resetearse hasta acumular tantas horas de uptime como
        // habían pasado desde la instalación. Ahora el ancla avanza y se
        // persiste en cada lectura (igual que SecurityManager), así que un
        // reinicio solo pierde el tramo desde la última lectura.
        private fun trustedNowMillis(): Long {
            val currentWall = System.currentTimeMillis()
            val currentElapsed = elapsedRealtimeMillisSafe()
            val raw =
                computeTrustedClock(
                    anchorWall = prefs.getLong(KEY_ANCHOR_WALL, 0L),
                    anchorElapsed = prefs.getLong(KEY_ANCHOR_ELAPSED, 0L),
                    currentWall = currentWall,
                    currentElapsed = currentElapsed,
                )
            // Migración única desde el ancla defectuosa: en instalaciones ya
            // desfasadas por un reinicio previo, el ancla queda como mínimo en
            // el último reseteo registrado (costo: una espera de hasta 20 h).
            val migrating = prefs.getInt(KEY_CLOCK_VERSION, 0) < CLOCK_VERSION
            val state =
                if (migrating) {
                    val base = maxOf(raw.anchorWall, prefs.getLong(KEY_LAST_RESET_TRUSTED, 0L))
                    TrustedClock(minOf(currentWall, base), base, currentElapsed)
                } else {
                    raw
                }
            if (migrating || state.anchorWall != prefs.getLong(KEY_ANCHOR_WALL, 0L) ||
                state.anchorElapsed != prefs.getLong(KEY_ANCHOR_ELAPSED, 0L)
            ) {
                prefs
                    .edit()
                    .putLong(KEY_ANCHOR_WALL, state.anchorWall)
                    .putLong(KEY_ANCHOR_ELAPSED, state.anchorElapsed)
                    .putInt(KEY_CLOCK_VERSION, CLOCK_VERSION)
                    .apply()
            }
            return state.trustedNow
        }

        // ── Verificar si puede realizar la operación ──────────────────────────────
        fun canConvert(): Boolean {
            checkAndResetIfNewDay()
            val count = prefs.getInt(KEY_CONVERSIONS, 0)
            val extras = prefs.getInt(KEY_EXTRA_CONVERSIONS, 0)
            val canDo = count < LIMIT_CONVERSIONS + extras
            Timber.d("DailyLimitManager: canConvert=$canDo ($count/${LIMIT_CONVERSIONS + extras})")
            return canDo
        }

        fun canSaveScan(): Boolean {
            checkAndResetIfNewDay()
            val count = prefs.getInt(KEY_SCANS_SAVED, 0)
            val extras = prefs.getInt(KEY_EXTRA_SCANS_SAVED, 0)
            val canDo = count < LIMIT_SCANS_SAVED + extras
            Timber.d("DailyLimitManager: canSaveScan=$canDo ($count/${LIMIT_SCANS_SAVED + extras})")
            return canDo
        }

        fun canUsePdfTool(toolKey: String): Boolean {
            checkAndResetIfNewDay()
            val key = getPdfToolKey(toolKey)
            val count = prefs.getInt(key, 0)
            val extras = prefs.getInt(KEY_EXTRA_PDF_TOOLS, 0)
            val canDo = count < LIMIT_PDF_TOOLS + extras
            Timber.d("DailyLimitManager: canUsePdfTool[$toolKey]=$canDo ($count/${LIMIT_PDF_TOOLS + extras})")
            return canDo
        }

        // ── Registrar uso ─────────────────────────────────────────────────────────
        @Synchronized
        fun registerConversion() {
            checkAndResetIfNewDay()
            val current = prefs.getInt(KEY_CONVERSIONS, 0)
            prefs.edit().putInt(KEY_CONVERSIONS, current + 1).apply()
            Timber.d("DailyLimitManager: conversión registrada → ${current + 1}")
        }

        @Synchronized
        fun registerScanSaved() {
            checkAndResetIfNewDay()
            val current = prefs.getInt(KEY_SCANS_SAVED, 0)
            prefs.edit().putInt(KEY_SCANS_SAVED, current + 1).apply()
            Timber.d("DailyLimitManager: escaneo guardado registrado → ${current + 1}")
        }

        @Synchronized
        fun registerPdfTool(toolKey: String) {
            checkAndResetIfNewDay()
            val key = getPdfToolKey(toolKey)
            val current = prefs.getInt(key, 0)
            prefs.edit().putInt(key, current + 1).apply()
            Timber.d("DailyLimitManager: pdfTool[$toolKey] registrado → ${current + 1}")
        }

        // ── Agregar conversión extra (reward por ver anuncio) ─────────────────────
        @Synchronized
        fun addRewardedConversion() {
            checkAndResetIfNewDay()
            val current = prefs.getInt(KEY_EXTRA_CONVERSIONS, 0)
            prefs.edit().putInt(KEY_EXTRA_CONVERSIONS, current + 1).apply()
            Timber.d("DailyLimitManager: +1 extra por rewarded → ${current + 1} extras")
        }

        // ── Agregar uso extra de herramienta PDF (reward por ver anuncio) ─────────
        @Synchronized
        fun addRewardedPdfTool() {
            checkAndResetIfNewDay()
            val current = prefs.getInt(KEY_EXTRA_PDF_TOOLS, 0)
            prefs.edit().putInt(KEY_EXTRA_PDF_TOOLS, current + 1).apply()
            Timber.d("DailyLimitManager: +1 extra de herramienta PDF por rewarded → ${current + 1} extras")
        }

        // ── Agregar escaneo guardado extra (reward por ver anuncio) ───────────────
        @Synchronized
        fun addRewardedScanSave() {
            checkAndResetIfNewDay()
            val current = prefs.getInt(KEY_EXTRA_SCANS_SAVED, 0)
            prefs.edit().putInt(KEY_EXTRA_SCANS_SAVED, current + 1).apply()
            Timber.d("DailyLimitManager: +1 escaneo guardado extra por rewarded → ${current + 1} extras")
        }

        // ── Obtener contadores para mostrar en UI ─────────────────────────────────
        fun getConversionCount(): Int {
            checkAndResetIfNewDay()
            return prefs.getInt(KEY_CONVERSIONS, 0)
        }

        fun getConversionLimit(): Int {
            checkAndResetIfNewDay()
            val extras = prefs.getInt(KEY_EXTRA_CONVERSIONS, 0)
            return LIMIT_CONVERSIONS + extras
        }

        fun getScanSavedCount(): Int {
            checkAndResetIfNewDay()
            return prefs.getInt(KEY_SCANS_SAVED, 0)
        }

        fun getScanSavedLimit(): Int {
            checkAndResetIfNewDay()
            val extras = prefs.getInt(KEY_EXTRA_SCANS_SAVED, 0)
            return LIMIT_SCANS_SAVED + extras
        }

        fun getPdfToolCount(toolKey: String): Int {
            checkAndResetIfNewDay()
            return prefs.getInt(getPdfToolKey(toolKey), 0)
        }

        fun getPdfToolLimit(): Int {
            checkAndResetIfNewDay()
            val extras = prefs.getInt(KEY_EXTRA_PDF_TOOLS, 0)
            return LIMIT_PDF_TOOLS + extras
        }

        private fun getPdfToolKey(toolKey: String): String = PDF_TOOL_KEYS[toolKey] ?: KEY_CONVERSIONS
    }

internal data class TrustedClock(
    val trustedNow: Long,
    val anchorWall: Long,
    val anchorElapsed: Long,
)

// Lógica pura del "reloj de confianza" (ver DailyLimitManager.trustedNowMillis()),
// extraída para poder probarla sin SystemClock. Nunca retrocede el ancla y
// nunca confía en un reloj de pared adelantado: siempre gana el valor que
// indica que pasó MENOS tiempo.
internal fun computeTrustedClock(
    anchorWall: Long,
    anchorElapsed: Long,
    currentWall: Long,
    currentElapsed: Long,
): TrustedClock =
    when {
        anchorWall == 0L -> TrustedClock(currentWall, currentWall, currentElapsed)
        // Reinicio: elapsedRealtime volvió a empezar. Se congela en el ancla
        // (no en el reloj de pared, potencialmente manipulado) y se re-basa.
        currentElapsed < anchorElapsed -> TrustedClock(minOf(currentWall, anchorWall), anchorWall, currentElapsed)
        else -> {
            val trusted = minOf(currentWall, anchorWall + (currentElapsed - anchorElapsed))
            if (trusted > anchorWall) {
                TrustedClock(trusted, trusted, currentElapsed)
            } else {
                TrustedClock(trusted, anchorWall, anchorElapsed)
            }
        }
    }
