# Backlog de bugs — repaso general 2026-09-14

**Estado: catalogado, sin ejecutar.** Cola de prioridades resultante de dos
pasadas de revisión de punta a punta pedidas por el usuario el 2026-09-14
(4 agentes en paralelo por área + verificación en dispositivo real). La
primera pasada encontró y corrigió el bug de sonido silenciado (`AudioAttributes`
enrutando a `STREAM_SYSTEM`) y 6 bugs más (ver commits `490077e`/`786a713`).
La segunda pasada encontró 22 hallazgos adicionales; los 6 de Alta prioridad
ya se corrigieron (commit `adf336a`). Este documento cataloga los **16
restantes de prioridad Media/Baja**, sin tocar.

## Cómo leer este documento

Mismo formato que `backlog-mejoras-ux-2026-08-30.md`: Prioridad (Alta/Media/Baja),
Tipo (Bug), archivo:línea de referencia (puede haber corrido desde entonces),
y una descripción verificada leyendo el código real (no especulación).

## Herramientas PDF

| # | Descripción | Prioridad | Archivo |
|---|---|---|---|
| 1 | Archivos de salida huérfanos si falla temprano (antes de escribir contenido real): `PdfWriter(outputFile)` ya crea el archivo en disco apenas se abre. Falta `outputFile.delete()` en la rama de error temprano en 5 herramientas (ya corregido en NumberPages/Watermark) | Media | `RedactPdfUseCase.kt`, `CropPdfUseCase.kt`, `EditTextPdfUseCase.kt` (2 ramas: `noPages` y `noMatchesError` — esta última muy alcanzable, cualquier búsqueda sin resultados deja un archivo huérfano), `SignPdfUseCase.kt`, `FillFormUseCase.kt` (2 ramas) |
| 2 | `isProcessing` nunca se resetea si `runAdvancedTool` devuelve `null` en la rama `PdfTool.SIGN` (falta `signatureImageBytes`) — hoy no alcanzable porque el botón de Firmar ya está deshabilitado sin firma, pero es un hueco real en la máquina de estados si se afloja ese guard o se agrega otra herramienta con el mismo patrón | Media (latente) | `PdfToolsViewModel.kt:353-398, 504-517` |

## Conversor

| # | Descripción | Prioridad | Archivo |
|---|---|---|---|
| 3 | Fuga de `cacheFile` en `PdfPasswordUseCase.protect()` si una excepción ocurre entre abrir el reader y cerrar el documento (mismo patrón ya corregido en `removePassword()` el 2026-09-14, se le pasó por alto a `protect()`) | Media | `PdfPasswordUseCase.kt:47-112` |
| 4 | `ConvertImageToPdfUseCase`: si `loadBitmapFromUri()` devuelve `null` para todas las imágenes del lote, el resultado se reporta igual como `Success` con `pageCount = imageUris.size` (el conteo original, no el real) y un PDF de 0 páginas que pasa el chequeo `outputFile.length() == 0L` (el header/xref de un PDF vacío ya pesa &gt; 0 bytes) | Media | `ConvertImageToPdfUseCase.kt:77-102, 123-127` |
| 5 | ~14 use cases del Conversor (`WordToTextUseCase`, `WordToPdfUseCase`, `WordToHtmlUseCase`, `PdfToHtmlUseCase`, `PdfToTextUseCase`, `PdfToWordUseCase`, `PdfToImageUseCase`, `ExcelToPdfUseCase`, `ExcelToCsvUseCase`, `ExcelToHtmlUseCase`, `PptToPdfUseCase`, `PptToTextUseCase`, `ImageFormatUseCase`, `ConvertImageToPdfUseCase`) tienen sus `ConversionResult.Error(...)` hardcodeados en español, saltándose el sistema de 12 idiomas — se propagan directo al Snackbar de `ConverterViewModel` | Media (i18n) | `app/src/main/java/com/docsmart/features/converter/domain/usecase/*.kt` |

## Biblioteca

| # | Descripción | Prioridad | Archivo |
|---|---|---|---|
| 6 | `NoPermissionContent` (pantalla de "sin permiso" de acceso a archivos) hardcodeada en español: "Permiso denegado", "Acceso a archivos requerido", el texto de instrucciones y el botón "Permitir acceso" | Media (i18n) | `LibraryScreen.kt:501, 506-510, 517` |

## Seguridad

| # | Descripción | Prioridad | Archivo |
|---|---|---|---|
| 7 | PIN de Carpeta Segura (4 dígitos, 10.000 combinaciones) sin límite de intentos ni backoff — a diferencia del desbloqueo biométrico (limitado por el propio SO), nada throttlea reintentos programáticos del PIN | Media (mejora) | `SecurityManager.kt:43-46`, `SecurityViewModel.kt:99-105` |
| 8 | Hash del PIN es SHA-256 de una sola pasada sin salt (no PBKDF2/BCrypt) — si el `SharedPreferences` `docusmart_security` se expone (backup, dispositivo rooteado), el espacio de 4 dígitos se prueba offline en microsegundos | Baja (mejora) | `SecurityManager.kt:62-66` |

## Visor

| # | Descripción | Prioridad | Archivo |
|---|---|---|---|
| 9 | `cacheOut` (archivo temporal del intento de desbloqueo) no se borra si los 3 intentos fallan — solo se limpia `cacheIn` | Media | `ViewerViewModel.kt` (función `unlockPdfWithPassword`, rama de fallo final) |
| 10 | Strings restantes hardcodeados en español en `ViewerViewModel.kt`: "Contraseña incorrecta. Intenta de nuevo.", "No se pudo desencriptar el PDF", "No se pudo abrir el PDF: ...", "Documento no encontrado" (en `loadFromMock`), título del selector de compartir | Media (i18n) | `ViewerViewModel.kt` (varias líneas) |
| 11 | Strings hardcodeados en la capa de UI del Visor: barra de resultados de búsqueda ("Sin resultados", "Coincidencia X de Y", content descriptions), indicador de página ("Página X de Y"), content descriptions de la barra superior ("Volver", "Buscar en documento", "Favorito", "Compartir") | Media (i18n) | `ViewerScreen.kt` (`PdfSearchResultBar`), `components/ViewerBottomBar.kt`, `components/ViewerTopBar.kt` |

## Escáner / QR

| # | Descripción | Prioridad | Archivo |
|---|---|---|---|
| 12 | `DisposableEffect` de `QrScreen` (fix del 2026-09-14) llama `executor.shutdown()` seguido de `scanner.close()` -- un frame ya encolado en el executor en el momento exacto de salir de la pantalla podría llamar `scanner.process()` justo después de `close()`. Ventana real pero rara (depende del framerate de análisis vs. el momento exacto de salida) | Media | `QrScreen.kt:129-134` |
| 13 | Botón "Guardar" en el resultado del escaneo no tiene guard de "ya en progreso" (a diferencia de "Compartir", que sí lo tiene con `isPreparingShare`) — un doble toque rápido puede lanzar dos guardados y contar dos veces el límite diario de "8 escaneos guardados/día" | Media | `ScanResultScreen.kt` (botón "Guardar", ~línea 1239-1263) |
| 14 | El switch de "Alta resolución" (Premium) revalida `isPremium` al togglear, pero `onGenerate` reenvía el valor ya guardado de `highResEnabled` sin volver a chequear `isPremium` en ese momento — si el estado Premium cambia entre togglear y generar, se puede generar en alta resolución sin ser Premium en ese instante | Media | `ScanResultScreen.kt` (switch ~línea 1166, `onGenerate` ~línea 275-281) |
| 15 | Placeholders del creador de QR hardcodeados en español/formato colombiano: "https://ejemplo.com", "correo@ejemplo.com", "+57 300 000 0000" — el resto de la pantalla sí usa `stringResource` | Media (i18n) | `QrScreen.kt:1014-1017` |
| 16 | `loadBitmapFromUrl` (QR que apunta a una imagen) no cierra el `InputStream` de la conexión HTTP — `BitmapFactory.decodeStream()` no lo cierra por su cuenta | Baja | `QrScreen.kt:1327-1338` |

## Nota de alcance

Todos estos hallazgos fueron verificados leyendo el código real (no
especulación) por agentes de revisión especializados, con effort xhigh,
antes de listarse acá. No se tocó ningún archivo de esta lista en la
sesión del 2026-09-14 más allá de lo ya corregido en los commits
`490077e`/`786a713`/`adf336a`.
