package com.docsmart.features.pdftools.presentation.components

// Lógica pura (sin Compose ni Android) de las pantallas de Herramientas PDF:
// cuándo se habilita "Ejecutar" en cada herramienta, el rango de Dividir y la
// navegación entre páginas. Extraída de los Composables para poder testearla en
// JVM; cada pantalla llama a estas funciones en vez de repetir la condición.
// /

/** Combinar exige al menos 2 PDFs. */
internal const val MERGE_MIN_PDFS = 2

internal fun canExecuteMerge(pdfCount: Int): Boolean = pdfCount >= MERGE_MIN_PDFS

/** Cantidad de páginas que abarca el rango de Dividir (0 si el rango está invertido). */
internal fun splitPageCount(
    fromPage: Int,
    toPage: Int,
): Int = (toPage - fromPage + 1).coerceAtLeast(0)

/** Un rango de una sola página (desde == hasta) es válido; solo el invertido (desde > hasta) no. */
internal fun isInvalidSplitRange(
    fromPage: Int,
    toPage: Int,
): Boolean = fromPage > toPage

internal fun canExecuteSplit(
    hasPdf: Boolean,
    fromPage: Int,
    toPage: Int,
): Boolean = hasPdf && !isInvalidSplitRange(fromPage, toPage)

/** "Desde" nunca baja de la página 1. */
internal fun canDecreaseSplitFrom(fromPage: Int): Boolean = fromPage > 1

/** "Hasta" nunca baja por debajo de "Desde". */
internal fun canDecreaseSplitTo(
    fromPage: Int,
    toPage: Int,
): Boolean = toPage > fromPage

/** Editar texto necesita PDF y un texto a buscar que no sea solo espacios. */
internal fun canExecuteEditText(
    hasPdf: Boolean,
    searchText: String,
): Boolean = hasPdf && searchText.isNotBlank()

/** Marca de agua necesita PDF y un texto que no sea solo espacios. */
internal fun canExecuteWatermark(
    hasPdf: Boolean,
    watermarkText: String,
): Boolean = hasPdf && watermarkText.isNotBlank()

/** Rellenar formulario necesita PDF y al menos un campo detectado. */
internal fun canExecuteFillForm(
    hasPdf: Boolean,
    fieldCount: Int,
): Boolean = hasPdf && fieldCount > 0

/** Censurar necesita PDF y al menos un rectángulo marcado. */
internal fun canExecuteRedact(
    hasPdf: Boolean,
    rectCount: Int,
): Boolean = hasPdf && rectCount > 0

/** Firmar necesita PDF y una firma ya capturada. */
internal fun canExecuteSign(
    hasPdf: Boolean,
    hasSignature: Boolean,
): Boolean = hasPdf && hasSignature

/** Reordenar con una sola página no cambia nada: no se ofrece (no gastar un uso diario en un archivo idéntico). */
internal fun canExecuteReorder(
    hasPdf: Boolean,
    pageCount: Int,
): Boolean = hasPdf && pageCount > 1

/** Comparar necesita los dos PDFs. */
internal fun canExecuteCompare(
    hasPdfA: Boolean,
    hasPdfB: Boolean,
): Boolean = hasPdfA && hasPdfB

/** "Quitar página" solo mientras quede más de una. */
internal fun canRemovePage(pageCount: Int): Boolean = pageCount > 1

internal fun canGoToPreviousPage(currentPage: Int): Boolean = currentPage > 1

internal fun canGoToNextPage(
    currentPage: Int,
    totalPages: Int,
): Boolean = currentPage < totalPages
