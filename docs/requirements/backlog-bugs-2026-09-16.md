# Backlog de bugs — repaso general 2026-09-16

**Estado: los 61 hallazgos catalogados están corregidos, pendientes de
fusionar a `main`.** Los 16 de prioridad Alta se fusionaron el 2026-09-16
(commit `0c85c0d`); los 45 de Media/Baja restantes se corrigieron en un
segundo lote el mismo día, a la espera de la revisión adversarial, la
verificación en dispositivo real y la aprobación explícita del usuario antes
de fusionar. Tercera pasada de revisión general de toda la
app (las dos anteriores fueron el 2026-09-14, ver `backlog-bugs-2026-09-14.md`,
ya cerrado). Pedida explícitamente por el usuario tras fusionar HU-46
(anotaciones del Visor): "revisa nuevamente desde el dispositivo real y luego
realiza una nueva revisión general de la aplicación... y arma una nueva lista
si encuentras bugs".

Metodología: 6 agentes de revisión en paralelo (effort xhigh), uno por área
(Escáner/QR, Visor, Herramientas PDF + Convertidor — que a su vez delegó en 4
sub-revisiones internas por la escala del área—, Biblioteca/Home/Seguridad,
Ajustes/Estudio/Monetización, y una revisión de seguridad transversal a toda
la app), cada uno leyendo el código real (no especulación) y contrastando
contra las dos rondas previas para no repetir hallazgos ya corregidos.

## Cómo leer este documento

Mismo formato que `backlog-bugs-2026-09-14.md`: Prioridad (Alta/Media/Baja),
área, archivo:línea, descripción del escenario real que lo dispara, y Estado
(⬜ Pendiente / ✅ Corregido / ⚠️ Evaluado y aceptado).

---

## Seguridad transversal

| # | Descripción | Prioridad | Archivo | Estado |
|---|---|---|---|---|
| 1 | Cualquier app instalada puede lanzar un `Intent VIEW` con un `file://` apuntando a `filesDir` de DocuSmart (incluida `secure/`, la Carpeta Segura) y el Visor lo abre sin pedir PIN/biometría — `isRealUri()`/`resolveUri()` aceptan cualquier `file://` sin validar que venga de un origen interno. Además `file_provider_paths.xml` expone TODO `filesDir` vía FileProvider, así que "Compartir" puede reenviar ese mismo archivo protegido a la app que originó el Intent | **Alta** | `MainActivity.kt:239-252`, `ViewerViewModel.kt:181-224,340-362,818-822`, `res/xml/file_provider_paths.xml` | ✅ Corregido (2026-09-16, fusionado en main, commit 0c85c0d) |
| 2 | Escanear un QR cuyo contenido es una URL de imagen dispara automáticamente una petición HTTP (`loadBitmapFromUrl`) sin confirmación del usuario — expone IP/momento del escaneo a un tercero sin consentimiento explícito, contradice la promesa "100% local" | Media | `QrScreen.kt:1393-1407` (llamado en `180-181, 359-361`) | ✅ Corregido (2026-09-16, pendiente de fusionar) |
| 59 | `cache-path` en `file_provider_paths.xml` sigue exponiendo TODO `cacheDir` (`path="."`) sin la misma lista blanca que se aplicó a `filesDir` en el hallazgo #1 — la mayoría de los use cases escriben directo a la raíz de `cacheDir` (no a subcarpetas), así que acotarlo requiere primero confirmar cuáles de esos archivos temporales se comparten de verdad vía `FileProvider.getUriForFile` | Baja | `res/xml/file_provider_paths.xml:14` | ✅ Corregido (2026-09-16, pendiente de fusionar) |
| 60 | Si `takePersistableUriPermission` falla al elegir Imagen/Documento para un QR (hallazgo #3, ya con `OpenDocument()`), el QR se genera igual sin avisar — vuelve a fallar más tarde exactamente como el bug original, solo que ahora es un caso borde en vez del camino normal | Baja/Media | `QrScreen.kt:721-723,740-742` | ✅ Corregido (2026-09-16, pendiente de fusionar) |
| 61 | `moveToSecure`/`moveFromSecure` escriben con `overwrite=true` a `File(destino, file.name)` sin chequear colisión de nombre — si dos documentos distintos comparten nombre y ambos se mueven a/desde Carpeta Segura, el segundo sobrescribe en silencio el contenido del primero, y (desde el fix del hallazgo #48) sus anotaciones quedan migradas al `documentId` que ahora apunta al contenido equivocado | Media | `SecurityManager.kt:198-220`, `SecurityViewModel.kt:209,416` | ✅ Corregido (2026-09-16, pendiente de fusionar) |

## Escáner / QR

| # | Descripción | Prioridad | Archivo | Estado |
|---|---|---|---|---|
| 3 | QR tipo Imagen/Documento codifica el `content://...` crudo del picker — inútil fuera del dispositivo (no es resoluble por nadie más) y falla incluso en el mismo dispositivo después porque `GetContent()` no persiste el permiso de lectura | **Alta** | `QrScreen.kt:1203-1204`, `QrScreen.kt:1409-1419` (`openDocumentExternally`) | ✅ Corregido (2026-09-16, fusionado en main, commit 0c85c0d) |
| 4 | La contraseña real de una red Wi-Fi (o teléfono/email de un Contacto) queda guardada en SharedPreferences y **mostrada en texto plano** en el Historial de QR si el usuario no activa la protección con contraseña aparte (caso normal: compartir el Wi-Fi de casa) | **Alta** | `QrHistoryStorage.kt` (persistencia), `QrHistoryScreen.kt:240-250` (enmascarado solo si `typeName=="PROTECTED"`) | ✅ Corregido (2026-09-16, fusionado en main, commit 0c85c0d) — nota: el fix inicial solo cubría QR *creados* en la app (`typeName` fijado a mano); un segundo pase (mismo día, revisión de seguridad del propio lote) lo extendió a detectar `WIFI:`/`BEGIN:VCARD` directamente sobre el contenido para cubrir también QR *escaneados*, que caían en `typeName="TEXT"` sin enmascarar nada |
| 5 | El Lector de QR propio de DocuSmart no reconoce los payloads Wi-Fi/Contacto/Evento que su propio Creador genera — caen en tipo TEXT y muestran el string crudo (`WIFI:T:WPA;S:...`) en vez de una acción útil | Media | `QrContentType.kt` (solo URL/IMAGE/DOCUMENT/EMAIL/PHONE/TEXT) | ✅ Corregido (2026-09-16, pendiente de fusionar) |
| 6 | Botón "Generar" del Creador de QR sin guard de doble-toque (`enabled = hasContent`, sin `!isGenerating`) — dos toques rápidos duplican la entrada en el Historial | Media | `QrScreen.kt:1174-1274` (línea 1261) | ✅ Corregido (2026-09-16, pendiente de fusionar) |
| 7 | Selector de color del QR (8 círculos) sin `contentDescription`/semántica — inaccesible para TalkBack | Media | `QrDesignSection.kt:92-109` | ✅ Corregido (2026-09-16, pendiente de fusionar) |
| 8 | Callejón sin salida si se deniega el permiso de cámara con "no volver a preguntar" — el botón "Permitir acceso" no lleva a Ajustes del sistema | Baja | `QrScreen.kt:291-296` | ✅ Corregido (2026-09-16, pendiente de fusionar) |
| 9 | 2 strings sin i18n en los selectores de Imagen/Documento del Creador de QR (`"JPG, PNG, WebP"` / `"PDF, Word, Excel, PPT, TXT"`) | Baja | `QrScreen.kt:897,946` | ✅ Corregido (2026-09-16, pendiente de fusionar) |

## Visor (incluye alcance general, no solo HU-46)

| # | Descripción | Prioridad | Archivo | Estado |
|---|---|---|---|---|
| 10 | Texto sin guardar se pierde al rotar el dispositivo en 3 diálogos del Visor (Nueva nota, Renombrar, Contraseña de PDF) — usan `remember` en vez de `rememberSaveable` | **Alta** | `ViewerAnnotationComponents.kt:119`, `ViewerDocumentDialogs.kt:31`, `ViewerScreen.kt:1854` | ✅ Corregido (2026-09-16, fusionado en main, commit 0c85c0d) |
| 11 | Rotar el dispositivo con el modo Anotar activo deja sin zoom/pan y sin ninguna barra visible para salir — `showAnnotationToolbar` (estado local, se resetea en rotación) se desincroniza de `uiState.annotationMode` (sobrevive en el ViewModel) | **Alta** | `ViewerScreen.kt:404` (`showAnnotationToolbar`) vs. `789` (`isAnnotating`) | ✅ Corregido (2026-09-16, fusionado en main, commit 0c85c0d) |
| 12 | Numerosos textos hardcodeados en español fuera del alcance de HU-46: búsqueda, mensajes de error de Word/Excel/PPT, diálogo de contraseña de PDF completo | **Alta** | `ViewerScreen.kt:565,1167,1181,1384,1407,1646,1659,1674,1797,1814,1869,1895,1920-1921,1964,1983-1984` | ✅ Corregido (2026-09-16, fusionado en main, commit 0c85c0d) |
| 13 | La búsqueda en el visor de Excel solo filtra la hoja activa, no todo el libro (a diferencia de PDF/Word/PowerPoint) | Media | `ViewerScreen.kt:1366-1368` | ✅ Corregido (2026-09-16, pendiente de fusionar) |
| 14 | Condición de carrera: un resaltado en curso de dibujarse puede cancelarse silenciosamente si Room emite justo a mitad del arrastre (`pointerInput` reinicia porque `pageAnnotations` cambió) | Media | `ViewerScreen.kt:909` | ✅ Corregido (2026-09-16, pendiente de fusionar) |
| 15 | Selector de color de resaltado (HU-46) sin accesibilidad para TalkBack | Media | `ViewerAnnotationComponents.kt:84-95` | ✅ Corregido (2026-09-16, pendiente de fusionar) |
| 16 | Traducción al euskera de "Anotar" (`viewer_annotate_content_desc`) es idéntica a "Nota" — ambigua para TalkBack | Media (i18n) | `values-eu/strings.xml:196` | ✅ Corregido (2026-09-16, pendiente de fusionar) |
| 17 | Eliminar una anotación no tiene confirmación (a diferencia de eliminar el documento completo, que sí la tiene) | Baja | `ViewerAnnotationComponents.kt:174-179` | ✅ Corregido (2026-09-16, pendiente de fusionar) |
| 18 | Sin feedback visible al llegar al límite de 2000 caracteres en una nota — el teclado "deja de responder" sin explicación | Baja | `ViewerAnnotationComponents.kt:127` | ✅ Corregido (2026-09-16, pendiente de fusionar) |

## Herramientas PDF

| # | Descripción | Prioridad | Archivo | Estado |
|---|---|---|---|---|
| 19 | Path traversal en el nombre de archivo de salida — el campo de texto libre no se sanea, `File(parent, "..#{name}")` puede escribir fuera de `filesDir/pdftools`, sobrescribiendo Room/SharedPreferences propios de la app. Afecta las 13 herramientas que generan salida | **Alta** | `OutputFileNameField.kt:26-29`, `PdfToolsViewModel.kt:368`, y el `createOutputFile()` de cada una de las 13 herramientas | ✅ Corregido (2026-09-16, fusionado en main, commit 0c85c0d) — nota: la revisión de seguridad del propio lote encontró la misma clase de vulnerabilidad en una tercera superficie que el fix inicial no cubría: `DocumentRepository.renameDocument()` (el diálogo "Renombrar" del Visor/Home/Biblioteca/Escáner) construía `File(parent, newName)` sin sanear. Corregido en el mismo pase con `sanitizeOutputFileName()`; de paso se corrigió un bug de orden en esa función (`FileNameSanitizer.kt`: filtraba caracteres DESPUÉS de colapsar "..", permitiendo reconstruir ".." si el filtro borraba el carácter que separaba los dos puntos) |
| 20 | "Limpiar firma" no borra el trazo dibujado en el lienzo — el botón solo limpia el estado del ViewModel, no el `Canvas`; el trazo viejo se reincorpora a la próxima firma o se mezcla entre documentos distintos si se cambia de PDF sin limpiar | **Alta** | `SignPdfScreen.kt:131,145,168-172,197-198` | ✅ Corregido (2026-09-16, fusionado en main, commit 0c85c0d) |
| 21 | Condición de carrera en detección de campos de formulario: un resultado tardío de un PDF anterior puede sobrescribir el del PDF seleccionado después (el `Job` no se cancela ni se etiqueta con el URI que lo originó) | Media | `FillFormScreen.kt:51-53`, `PdfToolsViewModel.kt:335-347` | ✅ Corregido (2026-09-16, pendiente de fusionar) |
| 22 | El stepper "Desde página" en Dividir permite superar "Hasta página" sin bloquear el botón de ejecutar (solo se pinta en rojo); el use case corrige el rango silenciosamente sin avisar | Media | `SplitPdfScreen.kt:114-118,142-143`, `PdfToolsViewModel.kt:223-225`, `SplitPdfUseCase.kt:68-69` | ✅ Corregido (2026-09-16, pendiente de fusionar) |
| 23 | `runTool()` sin protección ante `Throwable`/`OutOfMemoryError` (los use cases solo atrapan `Exception`) — cuelga la corrutina sin resetear `isProcessing` ni mostrar error | Media | `PdfToolsViewModel.kt:379` | ✅ Corregido (2026-09-16, pendiente de fusionar) |
| 24 | Nombre de archivo largo desborda visualmente `PdfSelectZone` (sin `maxLines`/`overflow`, caja de altura fija) — afecta 13 de 14 herramientas; `MergePdfScreen` sí lo maneja bien, confirma que es un descuido puntual | Media | `PdfToolCommon.kt:82,122-127` | ✅ Corregido (2026-09-16, pendiente de fusionar) |
| 25 | `CompressPdfUseCase` deja el archivo comprimido huérfano cuando comprimir no reduce el tamaño (camino feliz, no una excepción) — caso común con PDFs ya optimizados | Media | `CompressPdfUseCase.kt:77-106` | ✅ Corregido (2026-09-16, pendiente de fusionar) |
| 26 | `ComparePdfUseCase` deja `outputFile` huérfano si `writeReport()` lanza (ej. texto CJK/cirílico/emoji no soportado por la fuente Helvetica estándar) | Media | `ComparePdfUseCase.kt:105-106,121-127,148-180` | ✅ Corregido (2026-09-16, pendiente de fusionar) |
| 27 | `OcrPdfUseCase`: archivo huérfano en la rama `noPages` (patrón ya corregido en otras 5 herramientas, mas no en esta) + fuga de file descriptor si `PdfRenderer(fd)` lanza (PDF con solo contraseña de propietario) | Media | `OcrPdfUseCase.kt:149-152,154-155` | ✅ Corregido (2026-09-16, pendiente de fusionar) |
| 28 | `RotatePdfUseCase` sin validación de PDF de 0 páginas (única de las 8 herramientas comparables que no la tiene) — genera un falso `Success` | Media | `RotatePdfUseCase.kt:52-70` | ✅ Corregido (2026-09-16, pendiente de fusionar) |
| 29 | `FormatChip` de Numerar páginas define `maxLines=1` sin `overflow=Ellipsis` — recorte duro en vez de "..." con traducciones largas (de/fr/ru) | Baja | `NumberPagesScreen.kt:181-185` | ✅ Corregido (2026-09-16, pendiente de fusionar) |
| 30 | Unidad `"KB"` hardcodeada sin `stringResource` en la tarjeta de éxito, visible tras cualquiera de las 14 herramientas | Baja | `PdfToolsScreen.kt:817` | ✅ Corregido (2026-09-16, pendiente de fusionar) |

## Convertidor

| # | Descripción | Prioridad | Archivo | Estado |
|---|---|---|---|---|
| 31 | Condición de carrera real en el botón "Convertir" individual: sin `enabled=!isConverting` ni chequeo de re-entrada en `convert()` — un doble-toque hace que ambas corrutinas pasen `canConvert()` antes de que cualquiera registre, superando el límite diario en 1 conversión | **Alta** | `ConverterViewModel.kt:198-249`, `ConverterScreen.kt:651-658`, `DailyLimitManager.kt:113-120,142-147` | ✅ Corregido (2026-09-16, fusionado en main, commit 0c85c0d) |
| 32 | `OutOfMemoryError` no capturado en `ConvertImageToPdfUseCase` — una sola imagen muy grande en un lote crashea toda la conversión en vez de saltarse solo esa imagen | **Alta** | `ConvertImageToPdfUseCase.kt:146,177` | ✅ Corregido (2026-09-16, fusionado en main, commit 0c85c0d) |
| 33 | Fórmulas de Excel no evaluadas en `ExcelToCsvUseCase`/`ExcelToPdfUseCase` (`cell.toString()` en vez de `FormulaEvaluator`) — pérdida silenciosa de datos, el archivo de salida muestra el texto de la fórmula en vez del resultado calculado | **Alta** | `ExcelToCsvUseCase.kt:38`, `ExcelToPdfUseCase.kt:52` | ✅ Corregido (2026-09-16, fusionado en main, commit 0c85c0d) |
| 34 | `OutOfMemoryError` no capturado + fuga de `page`/orden de cierre incorrecto en `PdfToImageUseCase` — crash real en vez de `ConversionResult.Error` con páginas/afiches de alta resolución | **Alta** | `PdfToImageUseCase.kt:48,51-55,83` | ✅ Corregido (2026-09-16, fusionado en main, commit 0c85c0d) |
| 35 | Ruta de salida sin sanear (mismo patrón que el hallazgo #19 de Herramientas PDF) — afecta los 14 use cases del Conversor | Media | `ConverterViewModel.kt:216` + 14 use cases (ver informe completo) | ✅ Corregido (2026-09-16, pendiente de fusionar) |
| 36 | Sin límite de tamaño al leer entradas ZIP de `.xlsx`/`.docx`/`.pptx` (riesgo de "zip bomb") en 4 use cases | Media | `ExcelToHtmlUseCase.kt:34-35`, `WordToHtmlUseCase.kt:72`, `PptToPdfUseCase.kt:95,105`, `PptToTextUseCase.kt:70` | ✅ Corregido (2026-09-16, pendiente de fusionar) |
| 37 | `ExcelToHtmlUseCase` asume que `sheet1.xml` es siempre la primera hoja visible — no garantizado por la spec OOXML, puede convertir la hoja equivocada | Media | `ExcelToHtmlUseCase.kt:33-38` | ✅ Corregido (2026-09-16, pendiente de fusionar) |
| 38 | `.xls`/`.ppt` legado (OLE2) declarados como soportados en `ConversionType` pero las implementaciones solo parsean OOXML — fallan con mensaje engañoso ("hoja vacía"/"sin texto") en vez de "formato no soportado" | Media | `ExcelToHtmlUseCase.kt`, `PptToPdfUseCase.kt`, `PptToTextUseCase.kt` | ✅ Corregido (2026-09-16, pendiente de fusionar) |
| 39 | `PdfToTextUseCase`: el `cacheFile` (copia del PDF del usuario) nunca se borra — fuga de almacenamiento acumulativa + copia sin cifrar permanente fuera del flujo normal | Media | `PdfToTextUseCase.kt:34` (sin `finally`) | ✅ Corregido (2026-09-16, pendiente de fusionar) |
| 40 | Fuga de `PdfDocument`/`PdfReader` en `PdfToHtmlUseCase`/`PdfToWordUseCase` si la extracción falla a mitad del loop de páginas (el `.close()` solo se alcanza en el camino feliz) | Media | `PdfToHtmlUseCase.kt:39-43`, `PdfToWordUseCase.kt:89-94` | ✅ Corregido (2026-09-16, pendiente de fusionar) |
| 41 | `OutOfMemoryError` no capturado en `ImageFormatUseCase` con imagen de entrada muy grande | Media | `ImageFormatUseCase.kt:30-32,56` | ✅ Corregido (2026-09-16, pendiente de fusionar) |
| 42 | `convert()` sin protección ante `Throwable` — `isConverting` puede quedar colgado en `true` para siempre si un use case lanza un `Error` | Media | `ConverterViewModel.kt:221-248` | ✅ Corregido (2026-09-16, pendiente de fusionar) |
| 43 | `PdfToTextUseCase`: la rama de "PDF sin texto real" nunca se alcanza porque el encabezado `"=== Página N ==="` se agrega antes del chequeo `isBlank()` — un PDF escaneado sin OCR "convierte" con éxito a un .txt vacío de contenido real, sin avisar | Media | `PdfToTextUseCase.kt:44-49` | ✅ Corregido (2026-09-16, pendiente de fusionar) — la rama ahora sí se alcanza (`hasRealText`), con un test real que la cubre |
| 44 | String `"Conversiones hoy: X / Y"` hardcodeado sin `stringResource`, visible para todo usuario free | Media (i18n) | `ConverterScreen.kt:378` | ✅ Corregido (2026-09-16, pendiente de fusionar) |
| 45 | `extraFiles` de PDF→Imagen multi-página nunca se expone en la pantalla de éxito — el resto de las páginas generadas quedan en disco sin forma de verlas/guardarlas/compartirlas | Media | `ConversionSuccess.kt` (no referencia `result.extraFiles`) | ✅ Corregido (2026-09-16, pendiente de fusionar) |
| 46 | Texto duplicado (título y subtítulo idénticos) en `ConversionGridCard` — visible en ~17 tarjetas de la grilla | Baja | `ConverterScreen.kt:504-516` | ✅ Corregido (2026-09-16, pendiente de fusionar) |

## Biblioteca / Home / Seguridad

| # | Descripción | Prioridad | Archivo | Estado |
|---|---|---|---|---|
| 47 | "Mover a Carpeta Segura" desde el menú "⋮" global (Biblioteca/Home/Visor/Escáner) llama siempre a `importFileToSecure` (pensado para SAF/MediaStore), nunca a `importLocalFile` — para un archivo generado por la app (id = ruta absoluta) el borrado del original falla siempre (`DocumentsContract.deleteDocument` no aplica a `file://`), dejando una copia SIN protección visible en Biblioteca aunque el usuario crea haberla protegido | **Alta** | `DocuSmartNavGraph.kt:348-350`, `SecurityScreen.kt:245-266`, `SecurityViewModel.kt:234-275` | ✅ Corregido (2026-09-16, fusionado en main, commit 0c85c0d) |
| 48 | Las anotaciones del Visor (HU-46) se pierden silenciosamente (fila huérfana en Room, nunca migrada ni limpiada) cuando el documento cambia de id: mover/restaurar de Carpeta Segura, o un rename que mueve el archivo físico | **Alta** | `AnnotationDao.kt` (sin `deleteByDocument`/migración), `TrashRepository.kt:114-208`, `SecurityViewModel.kt:196-275,394-401`, `DocumentRepository.kt:263-280` | ✅ Corregido (2026-09-16, fusionado en main, commit 0c85c0d) |
| 49 | `HomeViewModel.renameDocument()` descarta el id nuevo que devuelve `DocumentRepository.renameDocument()` — la tarjeta de "Recientes" muestra el nombre nuevo pero conserva el id (ruta) viejo, que ya no existe; tocarla lleva a un documento roto | **Alta** | `HomeViewModel.kt:105-117` (comparar con `LibraryViewModel.kt:197-206`, que sí lo maneja bien) | ✅ Corregido (2026-09-16, fusionado en main, commit 0c85c0d) |
| 50 | Favoritos y alias huérfanos nunca se limpian tras borrado definitivo, purga automática a 30 días, o mover a Carpeta Segura — un archivo nuevo que reutilice la misma ruta que uno borrado/favorito puede aparecer "favorito" sin que el usuario lo haya marcado | Media | `TrashRepository.kt:114-183`, `FavoritesRepository.kt:39-53` | ✅ Corregido (2026-09-16, pendiente de fusionar) |
| 51 | Textos hardcodeados en español en Biblioteca (`DocumentListSection`/`FavoritesSection`), fuera de la limpieza de i18n del 2026-09-14 | Media (i18n) | `DocumentListSection.kt:54-55,66-68`, `FavoritesSection.kt:90` | ✅ Corregido (2026-09-16, pendiente de fusionar) |
| 52 | Biblioteca/Home no refrescan su lista al volver de otra pantalla (sin recarga ligada a `ON_RESUME`/`popBackStack`) — "documento fantasma" tras mover/borrar y volver atrás | Media | `LibraryScreen.kt:98-106` (y equivalente en Home) | ✅ Corregido (2026-09-16, pendiente de fusionar) |
| 53 | Carpeta Segura no permite previsualizar un archivo protegido sin restaurarlo primero (combinado con #48, restaurar para ver también pierde las anotaciones) | Baja | `SecurityScreen.kt:807-872` | ✅ Corregido (2026-09-16, pendiente de fusionar) |

## Ajustes / Modo Estudio / Monetización

| # | Descripción | Prioridad | Archivo | Estado |
|---|---|---|---|---|
| 54 | `study_exports/` (notas/resumen exportados desde Modo Estudio) queda completamente fuera de "Limpiar caché", "Restablecer configuración", el cálculo de Almacenamiento y la visibilidad en Biblioteca — mismo bug que ya se corrigió para `viewer_share/` en HU-46, pero no se extendió a esta carpeta | **Alta** | `StudyNotesExporter.kt:23,60`, `StudySummaryExporter.kt:16,30`, ausente en `SettingsScreen.kt:112-126,224-234` y `DocumentRepository.kt:473-483` | ✅ Corregido (2026-09-16, fusionado en main, commit 0c85c0d) |
| 55 | El diálogo de Almacenamiento cuenta `viewer_share` en el total pero no le da una fila propia — un usuario que solo compartió "con anotaciones" ve un Total mayor a cero sin ninguna fila que lo explique | Media | `SettingsScreen.kt:226-252` | ✅ Corregido (2026-09-16, pendiente de fusionar) |
| 56 | El texto de Pomodoro promete "descanso largo cada 4 pomodoros" pero esa lógica no existe en ningún lado del código — siempre son 5 minutos | Media | `values/strings.xml:937` vs. `PomodoroEngine.kt:25,37-53` | ✅ Corregido (2026-09-16, pendiente de fusionar) |
| 57 | Pestañas de Modo Estudio sin `overflow=Ellipsis` (recorte duro) — riesgo con `FontScale.EXTRA_LARGE` ("Muy grande"), el propio comentario del código ya advierte que el layout está al límite con 3 pestañas | Media | `StudyScreen.kt:535-556` | ✅ Corregido (2026-09-16, pendiente de fusionar) |
| 58 | `logStudySessionStarted()` se dispara en cada reanudación de Pomodoro, no solo al iniciar sesión — infla el conteo de Analytics (sin impacto de privacidad, el evento no lleva parámetros) | Baja | `PomodoroEngine.kt:104-106` | ✅ Corregido (2026-09-16, pendiente de fusionar) |

---

## Tests faltantes recomendados (consolidado, priorizados por los agentes)

- `ScanSessionManager.documentTypeForExtension()`/`formatFileSize()` — funciones puras sin test.
- Regresión de doble-toque en `QrCreatorScreen`/`QrHistoryScreen` (Generar/Guardar/Compartir).
- `hitTestAnnotation()` del Visor (privada, la lógica más crítica de HU-46 sin test — requiere extraerla a `internal`).
- `ViewerViewModel` completo — cero tests unitarios pese a ser la clase con más lógica de negocio del Visor.
- `extractExcelSheets()`/`extractPptSlides()` del Visor — sin tests.
- `ConvertImageToPdfUseCase`, `ExcelToPdfUseCase`, `ExcelToHtmlUseCase`, `ImageFormatUseCase`, `PdfToHtmlUseCase`, `PptToTextUseCase` — sin ningún test.
- Caso "PDF de 0 páginas" faltante en 6 use cases de Herramientas PDF que sí tienen test (`Crop`, `EditText`, `NumberPages`, `Redact`, `Sign`, `Split`, `Watermark`) — justo la rama que tuvo el fix de archivos huérfanos del 2026-09-14.
- `RotatePdfUseCaseTest`/`MergePdfUseCaseTest` — los más débiles del lote (solo camino feliz).
- `DailyLimitManagerTest` — sin cobertura del contador de "escaneos guardados" ni del reseteo diario real.
- `FavoritesRepositoryTest`, `LibraryViewModelTest`, `TrashViewModelTest`, `HomeViewModelTest` — no existen (0% de cobertura en las 4 clases).
- `TrashRepository.deleteAllForever()` — cero cobertura del borrado en lote (los 3 caminos: Done/NeedsPermission/PartialNeedsPermission).
- `DocumentRepository.loadAppGeneratedFiles()` — un test que enumere los directorios esperados habría detectado el hallazgo #54 de inmediato.

---

## Nota de alcance

Todos los hallazgos fueron verificados leyendo el código real por agentes de
revisión especializados con effort xhigh, contrastando explícitamente contra
las dos rondas previas (2026-09-14) para no repetir nada ya corregido. Este
documento es un catálogo de priorización — la decisión de qué corregir ahora
y qué diferir es del usuario.
