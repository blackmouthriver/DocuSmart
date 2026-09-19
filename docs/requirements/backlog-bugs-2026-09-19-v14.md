# Backlog de bugs — Decimoséptima auditoría general (2026-09-19)

Ronda 17: centrada en subir cobertura (SonarCloud 35.2% al inicio) auditando a la vez los archivos con más líneas sin cubrir. 4 agentes en paralelo sin Gradle (particiones: biblioteca/visor, PDF/convertidor, seguridad/billing/ads/Pomodoro, escáner/QR/Estudio/Agenda). Estrategia: extraer lógica pura de Composables/ViewModels/use cases a funciones `internal` y testearla en JVM (los Composables no se testean sin Robolectric).

## Bugs reales
- **Media (datos/privacidad)**: contraseña de QR con solo espacios (≥4) pasaba la validación y el QR salía **sin cifrar** aunque la UI lo mostrara protegido (`isQrPasswordInvalid`).
- **Media (privacidad)**: el Historial de QR podía mostrar parte de la contraseña Wi-Fi como SSID (regex sin anclar `S:`); ahora reutiliza `parseWifiPayload`/`parseVCardPayload` (también usa `N:` como respaldo del nombre).
- **Media (UX/ingresos)**: si la primera carga de un anuncio fallaba (sin red al arrancar) nada reintentaba y "Ver anuncio" quedaba deshabilitado hasta reiniciar; ahora reintenta a 15/30/60 s (revisión adversarial: el reintento solo carga si no hay un anuncio ya cargado).
- **Media (privacidad)**: ~25 logs ≥WARN enviaban a Crashlytics Throwable/`e.message`/URIs reales/nombres de archivo (Visor, Biblioteca, MediaDeletePermission, SearchPdfText, ScanResult, QrScreen, ConversionSuccess, PdfToHtml/Word...). Ahora solo `javaClass.simpleName`.
- Baja: barra de límite diario con NaN/Infinity o >1 (`dailyLimitProgress`); acceso directo con `initialTool="NONE"` seleccionaba una herramienta inexistente; `selectedUri!!` en PdfPasswordScreen.

## Cobertura
~370 tests nuevos (suite total > 1500). Lógica extraída: `DocumentTypeMapping`, `ViewerMimeTypes`, `SecurityLogic`, `PremiumLogic`, helpers de `BillingManager`/`AdManager`/`PomodoroTimerService`, `PdfToolsUiLogic`/`PdfToolsLogic`, `ConverterUiLogic`, `ConversionResultLogic`, funciones de Compress/Ocr/Split/ConvertImageToPdf, `StudyScreenLogic`, `AgendaCalendarLogic`, `QrHistoryLogic`, `ScanResultLogic`, `QrCreatorLogic`, `QrImageLogic`, y ampliaciones de PdfToolsViewModel/BillingManager/AdManager.

## Evaluado y no corregido
- ~30 `Timber.e(e, ...)` con Throwable (sin `e.message`) en use cases de pdftools/converter y algunas pantallas: se dejaron porque Crashlytics usa el Throwable para no-fatales; decisión de producto si se aplica la regla estricta (se perdería el stack trace).
- Logs restantes con nombres/URIs (`SecurityManager.kt` ~347/417/419, `PermissionHandler.kt`) y `error.message` de AdMob (`AdManager`, `DocuSmartBannerAd`): baja severidad, preexistentes.
- `QrDateTimeRow` con formato de fecha fijo y 24 h (a diferencia de Agenda).
- Los `loadDocumentsFrom*` de `DocumentRepository` (MediaStore), flujos de `BillingClient` real, `PdfRenderer`/ML Kit: sin cobertura JVM posible.
- Composables sin lógica extraíble (AgendaScreen, SecurityMenuScreen, PremiumPlanCards, etc.).
- Pendientes de rondas anteriores: QR Imagen/Documento por Wi-Fi local (diferido por el usuario), `MY_PACKAGE_REPLACED` para alarmas.

## Verificación
compile (main/test/androidTest), ktlint, detekt (baseline regenerado; 5 archivos de lógica con `@file:Suppress("MatchingDeclarationName")`), lintDebug y suite completa en verde. Dispositivo real (Motorola Edge 30 Neo): Inicio, Seguridad, Contraseña PDF, Herramientas PDF y Convertidor cargan; cero `FATAL EXCEPTION`/ANR. No probado en vivo: flujos de compra, reintento de anuncios sin red, TTS, escaneo de QR de terceros.
