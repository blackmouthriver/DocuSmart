package com.docsmart.features.security.presentation

// Lógica pura extraída de SecurityScreen/PdfPasswordScreen/SecurityViewModel
// (ronda 17) para poder testearla en JVM sin Compose: entrada del PIN,
// enrutado del archivo pendiente, habilitación de los formularios de
// contraseña PDF y formato de tamaños.

internal const val PIN_LENGTH = 4

/** Resultado de teclear un dígito en la pantalla de desbloqueo. */
internal data class UnlockPinStep(
    val pin: String,
    // No nulo solo cuando el PIN quedó completo: la pantalla debe enviarlo a verificar (y el campo se vacía).
    val submitted: String?,
)

internal fun unlockPinAppendDigit(
    current: String,
    digit: String,
    length: Int = PIN_LENGTH,
): UnlockPinStep {
    if (current.length >= length) return UnlockPinStep(current, null)
    val next = current + digit
    return if (next.length == length) UnlockPinStep("", next) else UnlockPinStep(next, null)
}

internal fun pinDeleteLast(current: String): String = current.dropLast(1)

/** Estado del asistente "Crear PIN" (crear y luego confirmar). */
internal data class SetupPinState(
    val pin: String = "",
    val confirmPin: String = "",
    val isConfirming: Boolean = false,
)

internal enum class SetupPinEvent { NONE, ADVANCED_TO_CONFIRM, MISMATCH, COMPLETED }

internal data class SetupPinStep(
    val state: SetupPinState,
    val event: SetupPinEvent,
)

internal fun setupPinAppendDigit(
    state: SetupPinState,
    digit: String,
    length: Int = PIN_LENGTH,
): SetupPinStep =
    if (state.isConfirming) {
        confirmPinAppendDigit(state, digit, length)
    } else if (state.pin.length >= length) {
        SetupPinStep(state, SetupPinEvent.NONE)
    } else {
        val next = state.pin + digit
        if (next.length == length) {
            SetupPinStep(state.copy(pin = next, isConfirming = true), SetupPinEvent.ADVANCED_TO_CONFIRM)
        } else {
            SetupPinStep(state.copy(pin = next), SetupPinEvent.NONE)
        }
    }

private fun confirmPinAppendDigit(
    state: SetupPinState,
    digit: String,
    length: Int,
): SetupPinStep {
    if (state.confirmPin.length >= length) return SetupPinStep(state, SetupPinEvent.NONE)
    val next = state.confirmPin + digit
    return when {
        next.length < length -> SetupPinStep(state.copy(confirmPin = next), SetupPinEvent.NONE)
        next == state.pin -> SetupPinStep(state.copy(confirmPin = next), SetupPinEvent.COMPLETED)
        else -> SetupPinStep(state.copy(confirmPin = ""), SetupPinEvent.MISMATCH)
    }
}

internal fun setupPinDeleteLast(state: SetupPinState): SetupPinState =
    if (state.isConfirming) {
        state.copy(confirmPin = pinDeleteLast(state.confirmPin))
    } else {
        state.copy(pin = pinDeleteLast(state.pin))
    }

/** Segundos (redondeados hacia arriba) que faltan de bloqueo por intentos fallidos de PIN. */
internal fun lockoutRemainingSeconds(lockoutMs: Long): Int = ((lockoutMs + 999) / 1000).toInt()

/** A dónde va un archivo pendiente de mover a Carpeta Segura tras desbloquear. */
internal sealed interface PendingImportRoute {
    data class LocalFile(
        val path: String,
    ) : PendingImportRoute

    data object ContentUri : PendingImportRoute

    // Esquema `file` sin ruta: no hay nada que mover.
    data object Ignore : PendingImportRoute
}

internal fun pendingImportRouteFor(
    scheme: String?,
    path: String?,
): PendingImportRoute =
    when {
        scheme != "file" -> PendingImportRoute.ContentUri
        path != null -> PendingImportRoute.LocalFile(path)
        else -> PendingImportRoute.Ignore
    }

/** Un documento de la Biblioteca cuyo id es un `content://` se importa por Uri; el resto es un archivo local. */
internal fun isContentDocumentId(documentId: String): Boolean = documentId.startsWith("content://")

internal fun canProtectPdf(
    hasFile: Boolean,
    password: String,
    confirmPassword: String,
    isProcessing: Boolean,
): Boolean = hasFile && password.isNotBlank() && password == confirmPassword && !isProcessing

internal fun canRemovePdfPassword(
    hasFile: Boolean,
    password: String,
    isProcessing: Boolean,
): Boolean = hasFile && password.isNotBlank() && !isProcessing

internal fun passwordsMismatch(
    password: String,
    confirmPassword: String,
): Boolean = confirmPassword.isNotEmpty() && password != confirmPassword

/** Tamaño de un archivo protegido, ya separado en unidad y valor para localizarlo en la UI. */
internal sealed interface SecureFileSize {
    data class Bytes(
        val value: Long,
    ) : SecureFileSize

    data class Kilobytes(
        val value: Long,
    ) : SecureFileSize

    data class Megabytes(
        val value: Double,
    ) : SecureFileSize
}

internal fun secureFileSizeOf(bytes: Long): SecureFileSize =
    when {
        bytes < 1024 -> SecureFileSize.Bytes(bytes)
        bytes < 1024 * 1024 -> SecureFileSize.Kilobytes(bytes / 1024)
        else -> SecureFileSize.Megabytes(bytes / (1024.0 * 1024.0))
    }
