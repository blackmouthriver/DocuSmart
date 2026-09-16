# Backlog de bugs — repaso general 2026-09-16 (cuarta pasada)

**Estado: catalogado, en curso.** Cuarta pasada de revisión general de toda la
app (las tres anteriores: dos el 2026-09-14, ver `backlog-bugs-2026-09-14.md`,
y una el 2026-09-16, ver `backlog-bugs-2026-09-16.md` — las tres cerradas, con
sus 77 hallazgos corregidos y fusionados a `main`). Pedida explícitamente por
el usuario tras fusionar el commit `7d9ceae` (los 45 hallazgos Media/Baja del
backlog anterior + 9 hallazgos de una revisión adversarial de 2 agentes sobre
ese mismo lote).

Metodología: 7 agentes de revisión en paralelo (Escáner/QR, Visor,
Herramientas PDF, Convertidor, Biblioteca/Home/Seguridad,
Ajustes/Estudio/Monetización, y Seguridad transversal), cada uno leyendo el
código real (no especulación) y contrastando explícitamente contra las tres
rondas previas para no repetir hallazgos ya corregidos. Se les pidió prestar
atención especial al código del commit `7d9ceae` (el lote más reciente),
porque solo pasó por una revisión adversarial de 2 agentes, no por esta
revisión general de punta a punta.

**Hallazgo destacado de este pase**: 3 agentes distintos (Biblioteca/Home/
Seguridad, Visor, Seguridad transversal), trabajando en paralelo y sin verse
entre sí, llegaron de forma independiente a la misma raíz: el flujo nuevo de
"Vista previa" de Carpeta Segura (hallazgo #53 del backlog anterior) abre el
Visor con funcionalidad completa sobre una copia temporal, sin ningún modo de
"solo lectura" — eso filtra el documento protegido a Recientes/Biblioteca sin
PIN, pierde anotaciones, y permite un "Eliminar" que no borra nada real. Se
fusionan esos hallazgos en los ítems #1-#3 de la tabla de Seguridad
transversal.

## Cómo leer este documento

Mismo formato que los backlogs previos: Prioridad (Alta/Media/Baja), área,
archivo:línea, descripción del escenario real que lo dispara, y Estado
(⬜ Pendiente / ✅ Corregido / ⚠️ Evaluado y aceptado).

---

## Seguridad transversal / Carpeta Segura

| # | Descripción | Prioridad | Archivo | Estado |
|---|---|---|---|---|
| 1 | **La vista previa de Carpeta Segura (hallazgo #53) filtra el documento protegido a "Recientes"/Biblioteca sin pedir PIN.** `ViewerViewModel.publishLoadedDocument()` llama siempre a `recordHistoryOpen(uriString)`, sin distinguir si `uriString` es la copia efímera de `cacheDir/secure_preview/`. Esa fila de historial nunca se limpia junto con `clearPreviewCache()` (que solo borra archivos, no el historial), y `DocumentRepository.documentFromHistoryFile()` solo exige `file.exists()` para listar el documento con su nombre real en Home/Biblioteca — abrible con un toque, sin PIN ni biometría, mientras la copia en caché siga viva (hasta el próximo backgrounding o la próxima vista previa). El fix del hallazgo #52 (recarga automática de Home/Biblioteca) agrava esto: el documento aparece solo con volver a esas pantallas | **Alta** | `ViewerViewModel.kt:291` (`recordHistoryOpen`), `SecurityManager.kt:288-305` (`copyForPreview`/`clearPreviewCache`), `DocumentRepository.kt:546-609` (`loadDocumentsFromHistory`/`documentFromHistoryFile`) | ✅ Corregido (2026-09-16) — `ViewerViewModel` ahora detecta documentos de `secure_preview/` (`isReadOnlyPreview`) y no los registra en el historial |
| 2 | **Las anotaciones agregadas durante una vista previa de Carpeta Segura se pierden en silencio y pueden filtrarse entre documentos distintos con el mismo nombre.** Se guardan en `AnnotationDao` con `documentId` = la ruta de la copia efímera (`cacheDir/secure_preview/<nombre>`, siempre la misma para un mismo nombre de archivo). Nada migra esas anotaciones al `documentId` real del archivo protegido, y `annotationDao.deleteByDocument()` (existe, pero nunca se invoca desde este flujo) no se llama al limpiar la caché — quedan huérfanas en Room para siempre. Como el `documentId` de caché depende solo del nombre de archivo, si se previsualizan dos documentos protegidos distintos que comparten nombre (ej. dos "Contrato.pdf"), las anotaciones del primero aparecen superpuestas sobre el segundo | **Alta** | `SecurityManager.kt:295-299` (`copyForPreview`), `ViewerViewModel.kt:274,292` (`document.id`/`observeAnnotations`), `AnnotationDao.kt:24` (`deleteByDocument`, sin uso) | ✅ Corregido (2026-09-16) — se ocultó la posibilidad de anotar durante una vista previa (`isReadOnlyPreview`, botón "Anotar" oculto + guard en `toggleAnnotationToolbar`), en vez de migrar/limpiar anotaciones efímeras |
| 3 | **"Eliminar" desde el Visor durante una vista previa de Carpeta Segura simula un borrado que nunca toca el archivo protegido real.** `confirmDelete()` llama `trashRepository.moveToTrash(documentId)` con el `documentId` = ruta temporal de caché; eso solo inserta una fila de papelera huérfana (no toca ningún archivo real), y el Visor se cierra como si el borrado hubiera funcionado. Al volver a Carpeta Segura, el archivo "eliminado" sigue ahí intacto — el usuario puede creer que borró un documento sensible cuando en realidad sigue completo en la app | **Alta** | `ViewerViewModel.kt:750-766` (`confirmDelete`), `TrashRepository.kt:78-87` (`moveToTrash`) | ✅ Corregido (2026-09-16) — "Eliminar" se oculta del menú y `confirmDelete()` tiene guard cuando `isReadOnlyPreview` |
| 4 | `deleteFile()` en Carpeta Segura (botón "Eliminar" real, sobre el archivo protegido, no la vista previa) no limpia favorito/alias/anotación del `documentId` borrado — a diferencia de TODAS las demás vías de borrado definitivo del proyecto (`TrashRepository`, ya corregidas por el hallazgo #50). Si otro documento distinto luego se protege y su nombre saneado colisiona con el del primero, hereda en silencio el favorito/las notas del documento ya borrado | Media | `SecurityViewModel.kt:470-476` (`deleteFile`) | ✅ Corregido (2026-09-16) — `deleteFile()` ahora también limpia favorito/alias/anotación del `documentId` borrado |
| 5 | **`CrashlyticsTree` (activo en `release`) puede subir a la nube de Google la ruta real de un archivo de Carpeta Segura.** Varios `catch (e: Exception)` de `SecurityManager` (mover/restaurar/eliminar/previsualizar) solo loguean un mensaje genérico pero pasan la excepción real a `Timber.e(e, ...)`, que en producción se reenvía sin más filtro a `FirebaseCrashlytics.recordException()`. Una excepción estándar de E/S (disco lleno, permiso denegado, colisión de nombre) trae en su `.message` la ruta absoluta completa, incluida `secure/<nombre_real>.pdf` — contradice la promesa "100% local" sin ningún aviso ni consentimiento específico (solo hay consentimiento UMP de anuncios, no de diagnóstico) | **Alta** | `app/build.gradle.kts:83-91`, `CrashlyticsTree.kt:16-27`, `SecurityManager.kt:219-222,232-235,269-272,301-304` | ✅ Corregido (2026-09-16) — `redactedForLog()` nuevo en `SecurityManager`/`PdfPasswordUseCase`: se registra un `Throwable` genérico (tipo, sin `.message` real) en vez del original, cerrando el mismo hueco en los 6 catches señalados |
| 6 | El campo de texto libre "Nombre del archivo" del Resultado del Escaneo nunca pasa por `sanitizeOutputFileName()` antes de construir rutas de salida (`copyUriToCache()` al Compartir, `DownloadsSaver.saveUri()` al Guardar en Descargas) — en `DownloadsSaver.saveUri()`, la rama pre-Android 10 usa `File(dir, displayName)` sin sanear. Con un nombre tipo `../../../qr/evil`, se puede escribir fuera de `cacheDir/scanner/`; en un dispositivo API 26-28 (dentro del `minSdk=26` declarado), puede escapar de la carpeta Descargas hacia otras rutas del almacenamiento externo | Media (Alta en API 26-28) | `ScanResultScreen.kt:141,1381-1392,1734-1752`, `DownloadsSaver.kt:96-106` | ✅ Corregido (2026-09-16) — tanto "Guardar" como "Compartir" ahora sanean `state.fileName` con `sanitizeOutputFileName()` antes de construir la ruta de salida |

## Escáner / QR

| # | Descripción | Prioridad | Archivo | Estado |
|---|---|---|---|---|
| 7 | **"Crear QR" desde un documento ya existente generado por la propia app codifica un `file://` crudo hacia almacenamiento interno**, sin pasar por el mismo saneamiento que sí tiene el selector de archivo del Creador (no persiste permiso, no valida esquema). Al escanear ese QR y tocar "Abrir documento"/"Abrir imagen", se lanza un `Intent.ACTION_VIEW` con ese `file://` sin FileProvider — con `targetSdk 36` dispara `FileUriExposedException`, atrapada en silencio (sin aviso al usuario, ni siquiera en el mismo dispositivo que generó el QR). El path interno de la app además queda expuesto en texto plano dentro del QR y persistido sin cifrar en el Historial salvo que se active contraseña. Mismo patrón que el hallazgo #3 ya cerrado, resurge por un camino de código distinto que ese fix no cubrió | **Alta** | `QrScreen.kt:719-726,1537-1547,1670-1673`, `DocuSmartDocumentItem.kt:52-53` (`toContentUri`), `DocuSmartNavGraph.kt:329-337` | ✅ Corregido (2026-09-16) — `navigateToQrCreator()` ahora arma la Uri vía `FileProvider.getUriForFile()` para documentos propios (id = ruta absoluta), con aviso al usuario (`qr_document_not_shareable`) si la ruta no está en `file_provider_paths.xml` en vez de generar un QR roto |
| 8 | Cancelar el selector nativo de "Compartir" del Escáner (o cerrarlo sin elegir app) igual cuenta como "escaneo guardado" del límite diario y cierra la vista del documento — `onFinalized(state.savedFile)` se llama inmediatamente después de lanzar el chooser, sin esperar a que el usuario complete el compartir. El botón "Guardar" no tiene este problema | Media | `ScanResultScreen.kt:1414-1433,1481-1497,1755-1776` | ✅ Corregido (2026-09-16) — nuevo `shareFileAwaitingSelection()` (PendingIntent + BroadcastReceiver) espera a que el usuario elija una app antes de invocar `onFinalized` |
| 9 | En el editor de página del Escáner, tocar un chip de **modo de color** (Color/B&N/Escala de grises/Resaltar texto) lo aplica de inmediato al documento, a diferencia de brillo/contraste/escala (estado local, solo se propaga con "Aplicar"). Tocar "Cancelar" después de probar un modo de color no lo descarta, rompiendo la expectativa de simetría con el resto del diálogo | Media | `ScanResultScreen.kt:513-534,1706-1711` | ✅ Corregido (2026-09-16) — el modo de color ahora usa estado local (`pendingColorMode`) y solo se propaga al tocar "Aplicar" |
| 10 | Los botones "Agregar contacto"/"Agregar al calendario" del Lector de QR no hacen nada, sin aviso, cuando el payload es real pero el parser propio no lo reconoce completamente — caso alcanzable: un evento de calendario "de todo el día" (`DTSTART;VALUE=DATE:...`, sin hora), formato habitual en invitaciones reales, que `parseIcalDateTime` no entiende. `WifiActionButtons` sí tiene un fallback razonable; los otros dos no, confirmando que es un descuido puntual | Media | `QrResultDisplay.kt:236-282`, `QrContentFormat.kt:210-214` | ✅ Corregido (2026-09-16) — `parseIcalDateTime()` ahora reconoce eventos de todo el día (`VALUE=DATE`) y los botones se deshabilitan cuando el parser no reconoce el payload |
| 11 | Unidades de tamaño `"B"`/`"KB"`/`"MB"` hardcodeadas sin `stringResource` en "Archivos escaneados en esta sesión". Mismo patrón que el hallazgo #30 ya corregido, en un archivo distinto | Baja (i18n) | `ScanSessionManager.kt:95-99` (`formatFileSize`) | ✅ Corregido (2026-09-16) — usa `context.getString()` con las nuevas claves `file_size_bytes/kb/mb` |

## Visor

| # | Descripción | Prioridad | Archivo | Estado |
|---|---|---|---|---|
| 12 | Renombrar desde el Visor **durante una vista previa de Carpeta Segura** solo renombra la copia efímera de caché, no el archivo real protegido — el cambio se pierde junto con el resto de la copia de vista previa, y el archivo protegido conserva su nombre original | Media | `ViewerViewModel.kt:715-734`, `DocumentRepository.kt:264-304` | ✅ Corregido (2026-09-16) — `renameDocument()` tiene guard cuando `isReadOnlyPreview` (mismo mecanismo que #1-#3), la UI oculta la opción |
| 13 | Renombrar cualquier documento desde el Visor deja las anotaciones invisibles hasta reabrir el documento: `DocumentRepository.renameDocument()` sí migra las filas de `AnnotationEntity` al id nuevo, pero `ViewerViewModel.renameDocument()` nunca vuelve a llamar `observeAnnotations(newId)` — la suscripción sigue filtrando por el id viejo (sin filas), así que Room emite lista vacía y los resaltados/notas "desaparecen" (los datos siguen intactos, reaparecen al reabrir) | Media | `ViewerViewModel.kt:715-734` (no reinvoca `observeAnnotations`) | ✅ Corregido (2026-09-16) — `renameDocument()` ahora llama `observeAnnotations(newId)` cuando el id cambia |
| 14 | "Abrir con otra app" para un documento generado por la app (`file://` crudo) falla en silencio en dispositivos con `targetSdk 36` (`FileUriExposedException`, atrapada por un catch genérico sin Toast) — mismo problema de raíz que ya se corrigió para "Compartir" (que sí usa FileProvider), nunca aplicado a este segundo camino | Media | `ViewerScreen.kt:2054-2065` | ✅ Corregido (2026-09-16) — envuelve la Uri vía `FileProvider.getUriForFile()`, con Toast si falla |
| 15 | `contentDescription` de cada página del PDF hardcodeado en español ("Página $pageNumber"), sin pasar por el sistema de 12 idiomas — TalkBack en cualquier idioma anuncia "Página 3" en español | Media (i18n/TalkBack) | `ViewerScreen.kt:846` | ✅ Corregido (2026-09-16) — usa `stringResource(R.string.viewer_page_content_desc, pageNumber)` |
| 16 | El aplanado de anotaciones para "Compartir con anotaciones" no tiene en cuenta la rotación `/Rotate` de la página — en un PDF escaneado con páginas rotadas 90°/270°, el resaltado se ve bien en la app pero puede quedar desplazado en el PDF compartido. Limitación ya documentada en un comentario del propio código, nunca corregida | Baja/Media | `FlattenAnnotationsPdfUseCase.kt:34-45,93-131` | ⚠️ Evaluado, no corregido (2026-09-16) — requiere una transformación geométrica de coordenadas que no puede verificarse sin probar en dispositivo con un PDF real rotado; se prefirió no arriesgar un fix no verificable que podría desplazar las anotaciones de otra forma distinta |

## Herramientas PDF

| # | Descripción | Prioridad | Archivo | Estado |
|---|---|---|---|---|
| 17 | `outputFile` queda huérfano en `filesDir/pdftools/` si el `catch` general se dispara después de que `PdfWriter`/`PdfDocument` ya creó el archivo, en 11 de las 14 herramientas (el fix de este patrón para Compress/Compare/OCR — hallazgos #25-27 — nunca se replicó al resto). En `RotatePdfUseCase` el `outputFile` ni siquiera está en scope del `catch` (es un `val` dentro del `try`). Disparador real: contenido malformado (content stream corrupto, campo de formulario dañado, fuente no soportada) a mitad del procesamiento | Media | `RotatePdfUseCase.kt:55,80-82`, y el mismo patrón en `CropPdfUseCase.kt`, `EditTextPdfUseCase.kt`, `FillFormUseCase.kt`, `SignPdfUseCase.kt`, `RedactPdfUseCase.kt`, `NumberPagesUseCase.kt`, `WatermarkPdfUseCase.kt`, `SplitPdfUseCase.kt`, `MergePdfUseCase.kt`, `ReorderPagesUseCase.kt` | ✅ Corregido (2026-09-16) — `outputFile` hoisteado a `var File? = null` antes del `try` (visible al `catch`) en las 11 herramientas, con `outputFile?.delete()` en cada catch |
| 18 | Las 5 pantallas que generan una miniatura/vista previa del PDF (Firmar, Censurar, Reordenar, Recortar, Rotar) copian a un archivo temporal en `cacheDir` y abren `ParcelFileDescriptor`/`PdfRenderer` a mano, cerrando y borrando **solo en el camino feliz** (sin `.use{}`/`try-finally`). Un PDF con contraseña de propietario (que iText sí abre pero `PdfRenderer` no) o de 0 páginas (`renderer.openPage(0)` sin chequear `pageCount`) deja el descriptor, el renderer y el archivo temporal huérfanos en cada intento — se acumulan con cada intento fallido | Media | `SignPdfScreen.kt:265-280`, `RedactPdfScreen.kt:322-354`, `ReorderPagesScreen.kt:157-190`, `CropPdfScreen.kt:244-265`, `RotatePdfScreen.kt:57-104` | ✅ Corregido (2026-09-16) — las 5 pantallas ahora anidan `.use{}` (fd/renderer/page) + `finally { file.delete() }`; `RedactPdfScreen`/`CropPdfScreen` también chequean `pageCount == 0` antes de `coerceIn` |

## Convertidor

| # | Descripción | Prioridad | Archivo | Estado |
|---|---|---|---|---|
| 19 | `saveToDownloads()`/`saveAllToDownloads()` usan `Iterable.all { ... }`, que corta en cortocircuito en el primer `false` — si el primer archivo de un lote falla al guardarse, el resto **ni se intenta**. En PDF→Imagen multi-página, si falla la página 1, las páginas 2..N (que hubieran funcionado) nunca se copian a Descargas | Media | `ConverterViewModel.kt:406-408,463-465` | ✅ Corregido (2026-09-16) — cambiado a `.map { ... }.all { it }` para intentar todos los archivos sin cortocircuito |
| 20 | El botón "Guardar en Descargas" del Convertidor no tiene guard de re-entrada (a diferencia de "Convertir", ya corregido por el hallazgo #31) — un doble-toque rápido dispara `saveToDownloads()`/`saveAllToDownloads()` dos veces en paralelo, duplicando el archivo en Descargas ("doc.pdf" y "doc (1).pdf") | Media | `ConverterViewModel.kt:400-416,454-480`, `ConversionSuccess.kt:223-242`, `BatchConversionSuccess.kt:109-125` | ✅ Corregido (2026-09-16) — nuevo `isSaving` en `ConverterUiState`, guard de re-entrada + `enabled = !isSaving` en ambos botones |
| 21 | `PdfToImageUseCase`: si falla a mitad del loop de páginas (`OutOfMemoryError` u otra excepción), las páginas ya escritas antes del fallo quedan huérfanas en `filesDir/converted` — la función devuelve `Error` sin limpiar los archivos ya generados | Media | `PdfToImageUseCase.kt:49-53,68-86` | ✅ Corregido (2026-09-16) — nuevo `cleanupOrphanPages()` borra todas las páginas ya escritas antes de devolver el Error |
| 22 | `WordToPdfUseCase`/`ExcelToPdfUseCase`/`PptToPdfUseCase`: mismo patrón de `outputFile` huérfano en el `catch` general que Herramientas PDF (#17) — **latente**, estos 3 tipos están ocultos de la grilla hoy (`HIDDEN_FROM_UI`), pero el hueco resurge en cuanto se reactiven | Media (latente) | `WordToPdfUseCase.kt:35-64`, `ExcelToPdfUseCase.kt:32-86`, `PptToPdfUseCase.kt:37-90` | ✅ Corregido (2026-09-16) — mismo patrón `var outputFile: File? = null` + `outputFile?.delete()` que #17 |
| 23 | Imagen→JPG con transparencia (PNG/WebP con alpha): nunca se compone el bitmap sobre un fondo opaco antes de comprimir a JPEG (que no soporta alfa) — las zonas transparentes se ven **negras** en el resultado en vez de blancas | Media | `ImageFormatUseCase.kt:37-46` | ✅ Corregido (2026-09-16) — nuevo `flattenOnWhite()`, aplicado cuando el destino necesita fondo blanco y el bitmap tiene alfa |
| 24 | Imagen→BMP en realidad escribe bytes PNG con extensión `.bmp` (Android no tiene codificador BMP real) — una herramienta externa que valide el formato por contenido real (no por extensión) falla al abrirlo | Baja/Media | `ImageFormatUseCase.kt:41` | ✅ Corregido (2026-09-16) — nuevo codificador BMP 24 bits real (`bitmapToBmp`/`argbPixelsToBmp`), con tests unitarios de los bytes de cabecera y datos de píxel |
| 25 | El MIME pasado al picker del sistema para Word→PDF/TXT/HTML es `"application/msword"` (solo `.doc` legado), excluyendo `.docx` (el formato Word más común) del selector en muchos proveedores — **latente**, `WORD_TO_*` está oculto hoy pero resurge al reactivarse | Baja (latente) | `ConverterScreen.kt:830-832` | ✅ Corregido (2026-09-16) — cambiado a `"*/*"`, igual que el patrón ya usado por Excel/PPT |
| 26 | Contenido generado (no solo mensajes de error, sino el HTML/TXT real que el usuario recibe) con textos hardcodeados en español pese al idioma configurado: `lang="es"`, título "Documento PDF", "Página N" en `PdfToHtmlUseCase`; "=== Página N ===" en `PdfToTextUseCase` — ambos tipos visibles y activos hoy. Mismo problema latente en 4 use cases hoy ocultos | Media | `PdfToHtmlUseCase.kt:84,86,97`, `PdfToTextUseCase.kt:68` (latente: `WordToHtmlUseCase.kt`, `ExcelToHtmlUseCase.kt`, `PptToPdfUseCase.kt`, `PptToTextUseCase.kt`) | ✅ Corregido (2026-09-16) — los 6 use cases (activos y latentes) ahora usan `context.getString()`/`Locale.getDefault().language` en vez de texto hardcodeado en español |

## Biblioteca / Home / Seguridad

(Ver también los ítems #1-#4 de Seguridad transversal, todos de esta área.)

Sin hallazgos adicionales nuevos — el resto del código de `LibraryScreen`/
`HomeScreen`/`DocumentRepository`/`TrashRepository`/`FavoritesRepository`
revisado línea por línea, correcciones de #48/#49/#50/#52/#59/#61 confirmadas
sólidas.

## Ajustes / Modo Estudio / Monetización

| # | Descripción | Prioridad | Archivo | Estado |
|---|---|---|---|---|
| 27 | El texto informativo de Pomodoro (`study_pomodoro_technique_info`) sigue diciendo "...con 5 min de descanso" en los 12 idiomas, desactualizado por el propio fix del hallazgo #56 (commit `7d9ceae`) que introdujo el descanso largo de 15 min cada 4 pomodoros — solo se corrigió la otra frase (`study_pomodoros_hint`). Regresión textual directa de este mismo lote | Media | `values*/strings.xml` (`study_pomodoro_technique_info`), mostrado en `StudyScreen.kt:2100` | ✅ Corregido (2026-09-16) — el texto de los 12 idiomas ahora menciona el descanso largo |
| 28 | Modo Estudio (extracción de PDF para lectura en voz alta): `cacheFile` solo se borra en el camino feliz — un PDF protegido/corrupto elegido en "Abrir documento" de la pestaña Lectura deja el archivo temporal huérfano en cada intento. Mismo patrón que el hallazgo #39 ya corregido, en un archivo distinto que ese fix no tocó | Media | `StudyScreen.kt:2416-2447` | ✅ Corregido (2026-09-16) — `cacheFile` hoisteado antes del `try`, borrado en `finally` |
| 29 | El botón de compra/prueba de Premium no tiene guard de re-entrada (`PremiumViewModel.purchase()` no chequea `isPurchasing`, el `Button` no se deshabilita hasta la próxima recomposición) — un doble-toque rápido puede lanzar dos flujos de Play Billing superpuestos. Mismo patrón ya corregido en Convertidor/QR, nunca aplicado al botón de compra real | Media | `PremiumViewModel.kt:164-176`, `PremiumScreen.kt:205-246` | ✅ Corregido (2026-09-16) — `purchase()` ahora chequea `isPurchasing` al entrar (la UI ya ocultaba el botón durante la compra, pero quedaba la ventana de carrera antes de la recomposición) |
| 30 | `sendSupportEmail()` (arma un mailto con datos del dispositivo) está completamente sin usar — ningún botón/fila en Ajustes ni en el diálogo de Ayuda la invoca. Código listo pero inalcanzable desde la UI | Baja | `SettingsScreen.kt:1053-1065` | ✅ Corregido (2026-09-16) — nuevo botón "Contactar soporte" en el `dismissButton` del diálogo de Ayuda |

---

## Nota de alcance

Los 30 hallazgos fueron verificados leyendo el código real por 7 agentes de
revisión en paralelo, contrastando explícitamente contra las tres rondas
previas (77 hallazgos ya corregidos) para no repetir nada. Este documento es
un catálogo de priorización — la decisión de qué corregir ahora y qué diferir
es del usuario.
