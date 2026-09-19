package com.docsmart.core.analytics

import android.os.Bundle
import com.google.firebase.Firebase
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.analytics
import timber.log.Timber

/**
 * Helper centralizado de Firebase Analytics para DocuSmart.
 * Uso: DocuSmartAnalytics.logScreenView("Home")
 *      DocuSmartAnalytics.logConversion("IMAGE_TO_PDF")
 *
 * Cada evento pasa por [safely]: un fallo de analítica (Firebase sin
 * inicializar, `Bundle` no mockeado en un test unitario JVM puro, etc.)
 * nunca debe interrumpir el flujo real del usuario -- se registra con
 * Timber y se descarta, nunca se relanza.
 */
object DocuSmartAnalytics {
    private val analytics: FirebaseAnalytics by lazy { Firebase.analytics }

    // Genérico a propósito: un evento de analítica jamás debe tumbar al
    // llamador, sin importar la causa real (Firebase sin inicializar,
    // Bundle no mockeado en un test unitario JVM, etc.).
    @Suppress("TooGenericExceptionCaught")
    private inline fun safely(block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            // Solo el tipo: CrashlyticsTree reenvía todo >= WARN a Firebase y el
            // mensaje/stack de una falla de Firebase/Bundle no aporta nada más.
            Timber.w("DocuSmartAnalytics: no se pudo registrar el evento (${e.javaClass.simpleName})")
        }
    }

    // ── Vistas de pantalla ────────────────────────────────────────────────────
    fun logScreenView(screenName: String) =
        safely {
            val bundle =
                Bundle().apply {
                    putString(FirebaseAnalytics.Param.SCREEN_NAME, screenName)
                    putString(FirebaseAnalytics.Param.SCREEN_CLASS, screenName)
                }
            analytics.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, bundle)
            Timber.d("Analytics: screen_view → $screenName")
        }

    // ── Conversiones ──────────────────────────────────────────────────────────
    fun logConversion(conversionType: String) =
        safely {
            val bundle =
                Bundle().apply {
                    putString("conversion_type", conversionType)
                }
            analytics.logEvent("conversion_started", bundle)
            Timber.d("Analytics: conversion_started → $conversionType")
        }

    fun logConversionSuccess(
        conversionType: String,
        fileSizeKb: Int = 0,
    ) = safely {
        val bundle =
            Bundle().apply {
                putString("conversion_type", conversionType)
                putInt("file_size_kb", fileSizeKb)
            }
        analytics.logEvent("conversion_success", bundle)
        Timber.d("Analytics: conversion_success → $conversionType ($fileSizeKb KB)")
    }

    fun logConversionError(
        conversionType: String,
        error: String,
    ) = safely {
        val bundle =
            Bundle().apply {
                putString("conversion_type", conversionType)
                putString("error_message", sanitizeAnalyticsText(error))
            }
        analytics.logEvent("conversion_error", bundle)
        Timber.d("Analytics: conversion_error → $conversionType: $error")
    }

    // ── PDF Tools ─────────────────────────────────────────────────────────────
    fun logPdfTool(toolName: String) =
        safely {
            val bundle = Bundle().apply { putString("tool_name", toolName) }
            analytics.logEvent("pdf_tool_used", bundle)
            Timber.d("Analytics: pdf_tool_used → $toolName")
        }

    // ── Documentos ────────────────────────────────────────────────────────────
    fun logDocumentOpened(documentType: String) =
        safely {
            val bundle = Bundle().apply { putString("document_type", documentType) }
            analytics.logEvent("document_opened", bundle)
            Timber.d("Analytics: document_opened → $documentType")
        }

    fun logDocumentFavorited(documentType: String) =
        safely {
            val bundle = Bundle().apply { putString("document_type", documentType) }
            analytics.logEvent("document_favorited", bundle)
            Timber.d("Analytics: document_favorited → $documentType")
        }

    // ── Scanner ───────────────────────────────────────────────────────────────
    fun logScanCompleted(pageCount: Int) =
        safely {
            val bundle = Bundle().apply { putInt("page_count", pageCount) }
            analytics.logEvent("scan_completed", bundle)
            Timber.d("Analytics: scan_completed → $pageCount páginas")
        }

    fun logQrScanned(contentType: String) =
        safely {
            val bundle = Bundle().apply { putString("qr_content_type", contentType) }
            analytics.logEvent("qr_scanned", bundle)
            Timber.d("Analytics: qr_scanned → $contentType")
        }

    fun logQrCreated(
        contentType: String,
        hasPassword: Boolean,
    ) = safely {
        val bundle =
            Bundle().apply {
                putString("qr_content_type", contentType)
                putBoolean("has_password", hasPassword)
            }
        analytics.logEvent("qr_created", bundle)
        Timber.d("Analytics: qr_created → $contentType (pass=$hasPassword)")
    }

    // ── Estudio ───────────────────────────────────────────────────────────────
    fun logStudySessionStarted() =
        safely {
            analytics.logEvent("study_session_started", null)
            Timber.d("Analytics: study_session_started")
        }

    fun logNoteCreated() =
        safely {
            analytics.logEvent("note_created", null)
            Timber.d("Analytics: note_created")
        }

    fun logPomodoroCompleted(count: Int) =
        safely {
            val bundle = Bundle().apply { putInt("pomodoro_count", count) }
            analytics.logEvent("pomodoro_completed", bundle)
            Timber.d("Analytics: pomodoro_completed → $count")
        }

    // ── Premium ───────────────────────────────────────────────────────────────
    fun logPremiumScreenViewed() =
        safely {
            analytics.logEvent("premium_screen_viewed", null)
            Timber.d("Analytics: premium_screen_viewed")
        }

    fun logPremiumPurchaseAttempt(planName: String) =
        safely {
            val bundle = Bundle().apply { putString("plan_name", planName) }
            analytics.logEvent("premium_purchase_attempt", bundle)
            Timber.d("Analytics: premium_purchase_attempt → $planName")
        }

    // Hallazgo real de la auditoría general 2026-09-17 (octava ronda,
    // Alta -- G1): `logPremiumPurchaseAttempt()` medía intentos, pero
    // nunca existió un evento de conversión REAL -- imposible saber
    // cuántas compras se concretaban de verdad, ni distinguir plan
    // mensual/anual, el dato de negocio más importante de la
    // monetización. Deliberadamente separado de `logPremiumPurchaseAttempt`
    // (no se reutiliza ese evento) para que el embudo intento→conversión
    // se pueda medir en Firebase sin ambigüedad.
    fun logPremiumPurchaseSuccess(productId: String) =
        safely {
            val bundle = Bundle().apply { putString("product_id", productId) }
            analytics.logEvent("premium_purchase_success", bundle)
            Timber.d("Analytics: premium_purchase_success → $productId")
        }

    // ── Errores ───────────────────────────────────────────────────────────────
    fun logError(
        context: String,
        message: String,
    ) = safely {
        val bundle =
            Bundle().apply {
                putString("error_context", context)
                putString("error_message", sanitizeAnalyticsText(message))
            }
        analytics.logEvent("app_error", bundle)
        Timber.d("Analytics: app_error → $context: $message")
    }
}

// Firebase Analytics muestra estos textos a cualquiera con acceso a la consola
// y los mensajes de error pueden traer URIs o rutas con nombres de archivo del
// usuario: se enmascaran antes de enviar. Límite de 100 caracteres = tope de
// Firebase para el valor de un parámetro.
private val ANALYTICS_URI_REGEX = Regex("[A-Za-z][A-Za-z0-9+.-]*://\\S+")

// Los segmentos admiten espacios (nombres como "Mi Contrato Juan.pdf"): se enmascara
// hasta el próximo ":" o fin de línea. Prefiere enmascarar de más antes que filtrar un nombre.
private val ANALYTICS_PATH_REGEX = Regex("(?:[A-Za-z]:)?[\\\\/][^\\\\/\\n:]+(?:[\\\\/][^\\\\/\\n:]+)+")
private const val ANALYTICS_MAX_TEXT_LENGTH = 100

internal fun sanitizeAnalyticsText(text: String): String =
    text
        .replace(ANALYTICS_URI_REGEX, "<uri>")
        .replace(ANALYTICS_PATH_REGEX, "<path>")
        .take(ANALYTICS_MAX_TEXT_LENGTH)
