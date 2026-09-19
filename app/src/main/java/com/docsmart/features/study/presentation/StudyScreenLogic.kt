package com.docsmart.features.study.presentation

import java.time.LocalDate
import java.time.ZoneId

// Ronda 17: lógica pura extraída de StudyScreen.kt (sin Compose ni Android)
// para poder testearla en JVM. Comportamiento idéntico al que estaba inline.

private const val PDF_MIN_SCALE = 0.5f
private const val PDF_MAX_SCALE = 4f
private const val WEEK_BAR_MAX_HEIGHT_DP = 32
private const val WEEK_BAR_MIN_HEIGHT_DP = 4
private const val NOTE_REMINDER_PRESET_HOUR = 9

/** Resultado de comparar las imágenes de una nota antes/después de editarla. */
internal data class NoteImageDiff<U, I>(
    val keptImages: List<I>,
    val removedImages: List<I>,
    val newImageUris: List<U>,
)

/**
 * Separa las imágenes que sobreviven a la edición, las que se quitaron y las
 * nuevas (URIs que no estaban en la nota original).
 */
internal fun <U, I> diffNoteImages(
    currentUris: List<U>,
    originalImagesByUri: Map<U, I>,
): NoteImageDiff<U, I> {
    val kept = currentUris.mapNotNull { originalImagesByUri[it] }
    val removed = originalImagesByUri.values.filterNot { it in kept }
    val newUris = currentUris.filterNot { originalImagesByUri.containsKey(it) }
    return NoteImageDiff(kept, removed, newUris)
}

/** "MM:SS" con solo dígitos 0-9 (sin sensibilidad de locale). */
internal fun formatPomodoroClock(
    minutes: Int,
    seconds: Int,
): String = "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"

/** Altura de la barra de un día en el gráfico semanal (mínimo visible de 4 dp). */
internal fun weekBarHeightDp(
    count: Int,
    counts: IntArray,
): Int {
    val maxCount = (counts.maxOrNull() ?: 0).coerceAtLeast(1)
    return (WEEK_BAR_MAX_HEIGHT_DP * count / maxCount).coerceAtLeast(WEEK_BAR_MIN_HEIGHT_DP)
}

/** Una fecha de recordatorio ya pasada (o igual a "ahora") no dispara ninguna alarma. */
internal fun isReminderInPast(
    reminderAt: Long,
    nowMillis: Long,
): Boolean = reminderAt <= nowMillis

/** Fecha/hora (9:00 locales) de "hoy + N días" para los presets de recordatorio. */
internal fun noteReminderPresetMillis(
    daysFromNow: Long,
    today: LocalDate = LocalDate.now(),
    zone: ZoneId = ZoneId.systemDefault(),
): Long =
    today
        .plusDays(daysFromNow)
        .atTime(NOTE_REMINDER_PRESET_HOUR, 0)
        .atZone(zone)
        .toInstant()
        .toEpochMilli()

/**
 * Índice dentro de la LazyColumn de NotesTab al que hay que desplazarse para
 * mostrar la nota [noteIndex]; null si la nota no existe (índice negativo).
 * Los ítems fijos previos son el editor y la cabecera de la lista, más la
 * sección de párrafos resaltados (título + N + separador) si está presente.
 */
internal fun notesListScrollIndex(
    noteIndex: Int,
    highlightsCount: Int,
    documentParagraphCount: Int,
): Int? {
    if (noteIndex < 0) return null
    val highlightSectionCount = if (highlightsCount == 0 || documentParagraphCount == 0) 0 else highlightsCount + 2
    return highlightSectionCount + 1 + 1 + noteIndex
}

/** Índice 0-based de la página del visor a mostrar para la página 1-based [currentPage]. */
internal fun pdfViewerPageIndex(
    currentPage: Int,
    pageCount: Int,
): Int = (currentPage - 1).coerceIn(0, (pageCount - 1).coerceAtLeast(0))

/** Estado de zoom/desplazamiento del visor de PDF. */
internal data class PdfTransform(
    val scale: Float,
    val offsetX: Float,
    val offsetY: Float,
)

/**
 * Aplica un gesto de pellizco/arrastre limitando el zoom a 0.5x-4x y el
 * desplazamiento a los bordes del contenedor escalado.
 */
internal fun applyPdfGesture(
    current: PdfTransform,
    zoom: Float,
    panX: Float,
    panY: Float,
    containerWidth: Float,
    containerHeight: Float,
): PdfTransform {
    val newScale = (current.scale * zoom).coerceIn(PDF_MIN_SCALE, PDF_MAX_SCALE)
    val maxX = (containerWidth * (newScale - 1) / 2f).coerceAtLeast(0f)
    val maxY = (containerHeight * (newScale - 1) / 2f).coerceAtLeast(0f)
    return PdfTransform(
        scale = newScale,
        offsetX = (current.offsetX + panX).coerceIn(-maxX, maxX),
        offsetY = (current.offsetY + panY).coerceIn(-maxY, maxY),
    )
}

/** Extrae el índice de párrafo de un utteranceId con forma "prefijo_N"; null si no es válido. */
internal fun parseUtteranceIndex(utteranceId: String?): Int? = utteranceId?.substringAfterLast('_')?.toIntOrNull()
