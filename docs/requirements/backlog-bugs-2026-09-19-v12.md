# Backlog de bugs — Decimoquinta auditoría general (2026-09-19)

Ronda 15: misma premisa que la 14 (priorizar por cobertura real de SonarCloud, ahora medida bien), pero sobre la lógica testeable de mayor volumen sin cubrir: ViewModels, repositorios y casos de uso. 4 agentes en paralelo, **sin correr Gradle** (lección de la ronda 14: 4 agentes con builds simultáneos stallean por RAM); el gauntlet se corrió centralizado después. ~50 hallazgos corregidos y ~300 tests nuevos; suite completa: **1021 tests, 0 fallos**.

## Visor (`ViewerViewModel`, `FlattenAnnotationsPdfUseCase`)
- **Alta**: contraseña incorrecta mostraba el mensaje equivocado — `PdfReader(...)` de iText no valida la clave (lo hace `PdfDocument`), así que "verificar" nunca fallaba y el usuario agotaba los 3 intentos sin reintento. Ahora `isPasswordAccepted()` distingue `BadPasswordException`.
- **Alta**: PDF protegido dentro de `secure_preview/` perdía el modo solo lectura tras desbloquearse (se registraba en Recientes sin PIN y habilitaba anotar/favorito).
- Media: `isLoading` trabado si el URI `file://` no tenía `path`; carrera en la búsqueda (`searchInPdf("")`/`clearPdfSearch()` no cancelaban la búsqueda en vuelo); doble toque en desbloquear/compartir con anotaciones; `PdfReader` sin `.use{}` (fuga de descriptores); 11 logs con Throwable/`e.message` a Crashlytics; fuga del `FileOutputStream` en el aplanado si el PDF estaba corrupto.

## Biblioteca / Papelera / Home
- **Alta**: orden por fecha roto — se ordenaba por el texto "dd/MM/yyyy", así que "15/01/2026" quedaba antes que "14/09/2026". Ahora se ordena por timestamp real (`dedupeAndSortByRecency`).
- **Alta**: renombrar pisaba en silencio un archivo existente (`File.renameTo` reemplaza el destino); ahora cae al alias.
- **Alta**: la purga de papelera quedaba bloqueada tras reiniciar el dispositivo (`elapsedRealtime` se reinicia y la resta daba negativo); ahora se re-ancla.
- Media: una entrada mala frenaba toda la purga; purgas concurrentes (Mutex); `deleteAllForever` mandaba URIs SAF al pedido masivo de MediaStore (mismo `IllegalArgumentException` del crash del 2026-09-11); `moveToTrash` devolvía `false` con el documento ya movido; carga vieja pisando a la nueva y documento eliminado que "resucita" en Library/Home; `TrashViewModel`/`LibraryViewModel.loadTrashCount` sin `try/catch` (tumbaban la app ante una excepción de Room); `CancellationException` tragada.

## Herramientas PDF y Convertidor
- **Alta**: fotos verticales se convertían "acostadas" a JPG/PNG/WebP/BMP (EXIF ignorado) y las orientaciones EXIF 5 y 7 se ignoraban también en imagen→PDF. Lógica pura extraída (`exifTransformFor`) y verificada por los revisores contra la geometría estándar.
- **Alta**: cambiar de herramienta o resetear a mitad de un proceso dejaba la corrutina vieja viva, que terminaba escribiendo su resultado en la otra herramienta y gastaba un uso diario.
- Media: doble conteo del límite diario (`registerConversion()` dentro de `update{}`, que se reintenta); estados "Guardado en Descargas" que no se limpiaban al reconvertir; un archivo malo abortaba todo el lote; cancelación no detectada en `PdfToImage`/`ConvertImageToPdf` (bucles sin punto de suspensión → imágenes huérfanas); OOM con planos grandes (tope de 16 Mpx en `pdfToImageRenderSize`); `Bitmap.compress()` en false reportado como éxito; `PdfDocument`/bitmaps/páginas sin cerrar; archivos parciales huérfanos.

## Seguridad / Billing / Agenda / Estudio / Escáner
- **Alta**: `importFileToSecure` podía perder datos — si `openInputStream` devolvía null se saltaba la copia y se borraba igual el original. Ahora falla antes de tocarlo.
- **Alta (dinero real)**: `ITEM_ALREADY_OWNED` mostraba error a un usuario que ya había pagado; ahora dispara `restorePurchases()`. `restorePurchases()` ahora devuelve su resultado (antes `PremiumViewModel` lo pisaba según el orden de emisión). El throttle de 4 h quedaba bloqueado tras una restauración fallida.
- Media: `saveDraft`/`deleteEvent` de Agenda sin `try/catch` (crash y pérdida de lo escrito); una entrada dañada de `StudyReadingProgressStorage` borraba el progreso de todos los documentos; `NotesViewModel` con la pantalla vacía si fallaba la migración legada; `ScanSessionViewModel` crasheaba al renombrar/borrar un archivo ya inexistente; `DocuSmartAnalytics` enviaba rutas/URIs sin enmascarar a Firebase.

## Revisión adversarial (seguridad + correctitud)
Sin hallazgos Alta. Corregidos en esta ronda:
- **Media-Baja (correctitud)**: cancelar el desbloqueo no impedía que se publicara el PDF desbloqueado (el cuerpo del `withContext` es bloqueante) → `ensureActive()` antes de publicar. Además `loadDocument` ahora cancela `unlockJob`.
- **Media (seguridad)**: `sanitizeAnalyticsText` cortaba en el primer espacio y el resto de un nombre como "Mi Contrato Juan.pdf" viajaba a Firebase → regex con segmentos con espacios + test.

Evaluados y **no corregidos** (documentados, baja severidad):
- `TrashRepository`: carrera purga vs. restauración (`restoreFromTrash` no usa `purgeMutex`); cancelación entre `deleteDocument` y `trashDao.remove` deja fila huérfana invisible.
- `deleteAllForever` devuelve `Done` aunque los borrados SAF hayan fallado (proveedores sin `deleteDocument`, ej. WhatsApp).
- Copia desbloqueada `unlocked_<ts>.pdf` en la raíz de `cacheDir` (no en `secure_preview/`), que solo se borra en `onCleared`.
- `AgendaRepository.createEvent`: si `schedule()` lanza tras el `insert`, reabrir el borrador y reguardar duplica el evento.
- `TrashViewModel.load()` usa el string "no se pudo eliminar" para un fallo de lectura; `PdfToolsViewModel.saveToDownloads` no se cancela con `reset()`.
- `SecurityManager`: logs WARN con `dest.name`/`file.name` (nombre real en Carpeta Segura); otros `Timber.w/e` con `e.message` en `DocuSmartNavGraph`, `MediaDeletePermission`, `PdfToHtmlUseCase`, `PdfToWordUseCase` (preexistentes, fuera de los archivos de esta ronda).
- `copyStreamToSecureFile` no compara tamaño copiado vs. origen.
- Sin cobertura automática (no testeable sin Robolectric): caminos felices con `PdfRenderer`/`Bitmap`/`MediaStore`, `BillingManager` en sí (constructor arma un `BillingClient` real).

## Verificación
Gauntlet centralizado en verde: compile (main/test/androidTest), 1021 tests, detekt (baseline regenerado: +27/−10 líneas, mismas categorías ya aceptadas), lintDebug, ktlintCheck. Dispositivo real (Motorola Edge 30 Neo): arranque en frío, Inicio, Biblioteca (74 documentos, pestañas 64/10/3) y Papelera con datos reales, en horizontal; cero `FATAL EXCEPTION`/ANR.
