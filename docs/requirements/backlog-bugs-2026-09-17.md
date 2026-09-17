# Auditoría general de punta a punta — 2026-09-17

Pedida explícitamente por el usuario tras cerrar HU-49+HU-52 ("realiza una
auditoria de punta a punta de la aplicacion... si encuentras bugs arma una
lista de hallazgos y trabajar los bugs criticos y luego medios y bajos").

Metodología: 7 agentes de exploración en paralelo, cada uno cubriendo un
área de la app (Home/Biblioteca/Seguridad, Escáner/QR, Visor, Herramientas
PDF, Convertidor, Modo Estudio/Agenda, Ajustes/Premium/transversal),
auditando bugs de correctitud, seguridad, fugas de recursos, código
duplicado/espagueti, huecos de cobertura de tests, e inconsistencias de
UX/i18n. Cada agente leyó primero `backlog-bugs-2026-09-14.md`,
`backlog-bugs-2026-09-16.md` y `backlog-bugs-2026-09-16-v2.md` para no
repetir hallazgos ya corregidos en rondas anteriores -- todo lo listado
abajo es código real vigente hoy, no repetido de esos documentos.

Convención de estado: ⬜ Pendiente · 🔧 En progreso · ✅ Corregido y
verificado · ⚠️ Evaluado, no corregido (con motivo).

---

## Prioridad Alta (10 hallazgos)

### A1 — Carpeta Segura: "Eliminar" sin confirmación ni verificación de borrado
**Área:** Home/Biblioteca/Seguridad · **Archivo:** `SecurityManager.kt:278-287` (`deleteSecureFile`), `SecurityScreen.kt:892-899`
**Estado:** ✅ Corregido 2026-09-17 -- `deleteSecureFile()` ahora propaga el
resultado real de `File.delete()`; `SecurityViewModel.deleteFile()` solo
limpia metadata si el borrado fue real y expone el error vía `uiState.error`
(ya cableado a un Snackbar existente); se agregó `AlertDialog` de
confirmación antes de eliminar. Test de regresión nuevo en
`SecurityViewModelTest.kt`. Gauntlet en verde; no verificado en vivo (exige
el PIN real del usuario, fuera de lo que se puede automatizar sin
credenciales).

`deleteSecureFile()` descarta el booleano de `file.delete()` y siempre
retorna `true` salvo excepción. Además "Eliminar" en el menú "⋮" de un
archivo de Carpeta Segura llama `onDelete()` directo, sin ningún
`AlertDialog` de confirmación (a diferencia de la Papelera o "Restablecer
PIN").

**Escenario de falla:** Un toque accidental en "Eliminar" (justo debajo de
"Vista previa"/"Restaurar" en el mismo menú) borra un archivo protegido
sin confirmación y sin pasar por la Papelera -- irrecuperable. Si
`file.delete()` falla en silencio, la metadata (favorito/alias/anotaciones)
se limpia igual aunque el archivo siga en disco.

### A2 — Herramientas PDF: doble-toque sin guard síncrono en "Ejecutar"
**Área:** Herramientas PDF · **Archivo:** `PdfToolsViewModel.kt:372-446` (`execute()`)
**Estado:** ✅ Corregido 2026-09-17 -- guard `state.isProcessing` agregado
al inicio de `execute()`, antes de lanzar la corrutina. Gauntlet en verde.

No hay chequeo de `state.isProcessing` antes de lanzar la corrutina --
depende de que Compose recomponga a tiempo. `registerPdfTool()` solo se
llama después de terminar, así que dos ejecuciones concurrentes ven el
mismo contador sin incrementar.

**Escenario de falla:** Doble toque rápido en "Ejecutar": el límite diario
se salta en 1 uso, `createOutputFile()` con granularidad de 1 segundo
puede generar el mismo nombre para ambas corridas (una pisa a la otra a
mitad de escritura), y el resultado que termina último sobrescribe en
silencio el `uiState.result` del otro (archivo huérfano sin referencia).

### A3 — Historial de QR: enmascarado de Wi-Fi inconsistente por mayúsculas
**Área:** Escáner/QR · **Archivo:** `QrHistoryScreen.kt:295-306` (`historyContentPreview`) vs. `QrContentType.kt:38` (`detectQrContentType`)
**Estado:** ✅ Corregido 2026-09-17 -- `startsWith(..., ignoreCase = true)`
en ambas comparaciones (Wi-Fi y vCard), sincronizado con la clasificación
real. Gauntlet en verde.

`historyContentPreview` compara `startsWith("WIFI:")` sensible a
mayúsculas; la clasificación real (`detectQrContentType`) usa
`lower.startsWith("wifi:")`, insensible. Quedan desincronizadas.

**Escenario de falla:** Un QR de Wi-Fi con esquema en minúsculas
(`wifi:T:WPA;S:MiRed;P:clave;;`, válido y emitido por algunos
generadores) se clasifica bien como `WIFI` pero el Historial no lo
enmascara -- muestra la contraseña completa en texto plano en la lista,
justo el escenario que el hallazgo #4 de la ronda anterior decía haber
cerrado.

### A4 — Compartir desde el resultado del Escáner se cuelga si se cancela el selector
**Área:** Escáner/QR · **Archivo:** `ScanResultScreen.kt:1828-1870` (`shareFileAwaitingSelection`)
**Estado:** ✅ Corregido 2026-09-17 -- se agregó un `LifecycleEventObserver`
que resuelve la corrutina como "no compartido" en el primer `ON_RESUME`
real tras abrir el chooser, si el broadcast todavía no llegó (cancelar el
selector devuelve el foco de inmediato; compartir de verdad deja la
Activity en pausa). `cleanup()` centralizado evita fugas del receiver/
observer en los 3 caminos de resolución. Revisado adversarialmente sin
hallazgos de doble-resolución. Gauntlet en verde.

El fix que espera confirmación real de "compartido" usa
`suspendCancellableCoroutine` esperando el broadcast del
`PendingIntent`/`IntentSender` del chooser -- pero ese broadcast **solo**
se dispara si el usuario elige una app. Si cancela (back / tocar fuera),
nunca llega, no hay timeout.

**Escenario de falla:** Tocar "Compartir" y cerrar el selector sin elegir
nada deja `isPreparingShare=true` para siempre: el botón desaparece,
queda un `CircularProgressIndicator` infinito, y salir de la pantalla
dispara la limpieza de sesión (se pierde el documento generado).

### A5 — Visor: "Mover a Carpeta Segura" sigue activo durante una vista previa
**Área:** Visor · **Archivo:** `ViewerTopBar.kt:227-231`, `SecurityManager.kt:208-223` (`moveToSecure`)
**Estado:** ✅ Corregido 2026-09-17 -- el `DropdownMenuItem` ahora está
dentro del mismo guard `if (!isReadOnlyPreview)` que ya ocultaba Renombrar/
Eliminar. Gauntlet en verde; no verificado en vivo (exige abrir una vista
previa de Carpeta Segura, requiere el PIN real del usuario).

Es el único ítem del menú "⋮" que NO se oculta con `isReadOnlyPreview`
(Renombrar/Eliminar/Anotar sí lo hacen). Durante la vista previa,
`document.id` es la ruta efímera `cacheDir/secure_preview/<nombre>`; al
tocarlo, `moveToSecure()` copia esa copia temporal como documento nuevo y
**borra el archivo origen** (la copia que el Visor está mostrando).

**Escenario de falla:** Usuario ya autenticado abre la vista previa de un
documento YA protegido y toca "Mover a Carpeta Segura" por error: se crea
un duplicado confuso en Carpeta Segura y la copia temporal en uso se
borra en el acto, rompiendo la sesión de vista previa activa.

### A6 — Visor: fuga de `cacheDir` en cada apertura de PDF
**Área:** Visor · **Archivo:** `core/pdf/PdfPageBitmap.kt:24-30, 59-78`
**Estado:** ✅ Corregido 2026-09-17 -- `renderPdfPagesToBitmaps()` borra el
archivo temporal en un `finally` (éxito o error); `renderCachedPdfPages()`
cierra `PdfRenderer`/página/`FileDescriptor` en `finally` anidados,
de adentro hacia afuera. Gauntlet en verde.

El archivo temporal `cacheDir/preview_<timestamp>.pdf` creado para
renderizar nunca se borra (ni en camino feliz ni de error, sin
`try/finally`). "Limpiar caché" de Ajustes solo barre subcarpetas de
`filesDir`, nunca `cacheDir`.

**Escenario de falla:** Uso normal de la app (abrir/reabrir PDFs con
frecuencia) acumula una copia completa de cada PDF abierto en `cacheDir`
indefinidamente, sin forma de liberarlos desde la UI. Un PDF corrupto que
lance excepción a mitad del render deja además el `PdfRenderer`/FD nativo
sin cerrar.

### A7 — "Extraer imágenes" cuenta contra el límite diario equivocado
**Área:** Ajustes/Premium/transversal · **Archivo:** `core/ads/DailyLimitManager.kt:57-72,222`
**Estado:** ✅ Corregido 2026-09-17 -- se agregó `KEY_EXTRACT_IMAGES` a
`PDF_TOOL_KEYS`. Test de regresión nuevo en `DailyLimitManagerTest.kt`
(mismo patrón ya usado para `NUMBER_PAGES`/`WATERMARK`). Gauntlet en verde.

`PDF_TOOL_KEYS` no incluye `"EXTRACT_IMAGES"` (existe como valor real del
enum `PdfTool`). `getPdfToolKey()` cae a `KEY_CONVERSIONS` para cualquier
clave no mapeada -- mismo bug ya corregido dos veces antes
(`NUMBER_PAGES`, `WATERMARK`) y nunca extendido a esta herramienta.

**Escenario de falla:** Un usuario free usa "Extraer imágenes": consume y
revisa el contador de Conversiones del Convertidor (contra
`LIMIT_PDF_TOOLS`, no `LIMIT_CONVERSIONS`) -- ambos contadores quedan
cruzados y el contador que ve el usuario en Convertidor se infla sin
haber convertido nada.

### A8 — `AdView` de AdMob nunca se libera
**Área:** Ajustes/Premium/transversal · **Archivo:** `core/ads/DocuSmartBannerAd.kt:29-54`
**Estado:** ✅ Corregido 2026-09-17 -- `AndroidView` ahora recibe
`onRelease = { view -> (view as? AdView)?.destroy() }`. Verificado en
dispositivo real que los banners de AdMob siguen cargando y mostrándose
con normalidad tras el cambio (Convertidor/Notas). Gauntlet en verde.

El `AndroidView(factory = { AdView(context)... })` no provee `onRelease`,
así que `AdView.destroy()` nunca se llama al salir de composición --
Google documenta esto como fuga real de recursos nativos (incluida la
`WebView` interna del banner).

**Escenario de falla:** Navegar repetidamente entre pantallas que
comparten este banner (Ajustes/Convertidor/Escáner/Visor/Seguridad/QR)
durante una sesión larga acumula `AdView`s nunca liberados.

### A9 — Exportar nota (HU-51) no incluye imágenes adjuntas (HU-49)
**Área:** Modo Estudio/Agenda · **Archivo:** `StudyScreen.kt:2327-2358` (`StudyExportSingleNoteButton`), `StudyNotesExporter.kt:47-108`
**Estado:** ✅ Corregido y verificado en dispositivo real 2026-09-17 --
`exportAsPdfFile`/`exportAsWordFile` reciben `NoteWithImages` y agregan
cada imagen en el orden de `position` (iText7 `Image` para PDF, POI
`XWPFRun.addPicture()` para Word, con el tipo de imagen real detectado por
`BitmapFactory.Options.outMimeType`, no asumido por extensión -- hallazgo
de la propia revisión adversarial de este lote). Verificado en vivo: nota
con 2 imágenes reales de la galería exportada a Word, `.docx` inspeccionado
como ZIP -- ambas imágenes embebidas como JPEG válido (1600x1200,
confirmado con `file`), en el orden correcto. Gauntlet en verde.

El AC1 de HU-51 exige explícitamente "conserva el texto e imágenes
adjuntas en el mismo orden que la nota". `StudyNotesExporter` (reutilizado
de RF-STU-08, solo texto) nunca referencia ninguna imagen.

**Escenario de falla:** Nota con una foto de fórmula/diagrama adjunta
(HU-49), exportada a PDF/Word para entregar una tarea: la imagen
desaparece en silencio del archivo generado.

### A10 — Recordatorios (Agenda y Notas) se guardan con fecha ya pasada sin avisar
**Área:** Modo Estudio/Agenda · **Archivo:** `NoteReminderScheduler.kt:34-38`, `ReminderScheduler.kt:36-40`, `AgendaEventEditorDialog.kt:131-141`
**Estado:** ✅ Corregido y verificado en dispositivo real 2026-09-17 --
advertencia visual (ícono + texto ámbar) en ambos editores cuando el
disparo calculado ya quedó en el pasado, reutilizando `reminderTriggerMillis`
existente. Verificado en vivo en Notas: elegir "Personalizada" con la
fecha/hora precargada ("ahora") muestra exactamente "Con esta fecha y
hora, el recordatorio ya quedó en el pasado y no va a sonar." Gauntlet en
verde.

`schedule()` descarta en silencio (`return`) cualquier disparo con
`triggerAt <= now` -- correcto como defensa, pero ninguna UI valida eso
antes de guardar. La fila se guarda igual en Room y la UI sigue mostrando
"Recordatorio programado" aunque `AlarmManager` nunca programó nada.

**Escenario de falla:** Usuario crea un evento de Agenda "dentro de 3
horas" y elige el chip normal "1 día antes" -- el disparo calculado ya
quedó en el pasado, se guarda sin aviso, el recordatorio nunca suena.

---

## Prioridad Media (13 hallazgos)

Corregidos y fusionados en lote 2026-09-17. Cada fix pasó el gauntlet
completo (`compileDebugKotlin`+`detekt`+`lintDebug`+`testDebugUnitTest`+
`compileDebugAndroidTestKotlin`) y una revisión adversarial dedicada (agente
aparte, sin contexto de la implementación) que encontró y motivó 3
correcciones adicionales no listadas en el hallazgo original: M1 no
distinguía `success=false` de `originalDeleted=false` en `restoreFile()`
(mensaje engañoso si `moveFromSecure()` fallaba por completo); M3 solo se
había propagado a Biblioteca, faltaba en Ajustes y Onboarding (los otros 2
call sites reales de "Vincular carpeta"); M12 reutilizaba el string de
error de "compra" para un fallo de "restaurar", mensaje incorrecto aunque
ya localizado.

### M1 — Carpeta Segura: "Restaurar" no verifica el borrado ni distingue error total
**Área:** Home/Biblioteca/Seguridad · **Archivo:** `SecurityManager.kt:225-236` (`moveFromSecure`), `SecurityViewModel.kt:525-556`
**Estado:** ✅ Corregido 2026-09-17 -- `moveFromSecure()` ahora devuelve
`SecureMoveResult` (mismo shape que `moveToSecure()`), propagando si
`file.delete()` del original en `secure/` falló. `restoreFile()` gana un
param `restoreErrorMessage`; corrección tras revisión adversarial: la
primera versión solo miraba `originalDeleted`, no `success` -- si
`moveFromSecure()` fallaba por completo (`copyTo` lanza), igual mostraba
"Archivo restaurado, no se pudo borrar la copia" mintiendo sobre un
duplicado inexistente. Ahora usa el mismo criterio de 3 caminos que
`importLocalFile()`. Tests nuevos en `SecurityManagerTest.kt`/
`SecurityViewModelTest.kt` (incluye el caso de fallo total). No verificado
en vivo -- exige el PIN real de Carpeta Segura.

### M2 — Tamaños de archivo hardcodeados y división entera ("0 KB")
**Área:** Home/Biblioteca/Seguridad · **Archivo:** `DocumentRepository.kt`, `TrashScreen.kt`, `SecurityScreen.kt`
**Estado:** ✅ Corregido 2026-09-17 -- las 3 ubicaciones usan ahora
`stringResource(R.string.file_size_*)` (claves ya existentes, mismo patrón
que `ScanSessionManager`), corrigiendo también el bug de "0 KB" para
archivos &lt;1024 bytes. Ajuste post-implementación: el path de MB usaba
`String.format(Locale.getDefault(), ...)` dentro de un `@Composable`, lo
que dispara `NonObservableLocale` de Android Lint -- se cambió a
`LocalLocale.current.platformLocale`. Verificado en vivo en Papelera: "11,5
MB en la papelera" y tamaños individuales con coma decimal correcta.

### M3 — "Vincular carpeta" falla en silencio si falla el permiso SAF
**Área:** Home/Biblioteca/Seguridad · **Archivo:** `DownloadsAccessManager.kt:55-68`, `LibraryViewModel.kt`, `SettingsScreen.kt`, `OnboardingScreen.kt`
**Estado:** ✅ Corregido 2026-09-17 -- `onFolderPicked()` devuelve `Boolean`;
Biblioteca ya avisaba con un Toast. Corrección tras revisión adversarial:
el fix original no llegaba a los otros 2 call sites reales de "Vincular
carpeta" (`SettingsScreen.kt`/Ajustes y `OnboardingScreen.kt`/onboarding),
que seguían descartando el resultado -- se les agregó el mismo Toast
reutilizando `library_link_folder_error`.

### M4 — "Guardar en Descargas" de Herramientas PDF sin guard de re-entrada
**Área:** Herramientas PDF · **Archivo:** `PdfToolsViewModel.kt`, `PdfToolsScreen.kt`
**Estado:** ✅ Corregido 2026-09-17 -- `PdfToolsUiState.isSaving` +
guard síncrono en `saveToDownloads()` (mismo patrón ya usado en
Convertidor); `enabled = !isSaving` en el botón.

### M5 — Merge de PDFs: URI fallida se saltea sin avisar
**Área:** Herramientas PDF · **Archivo:** `MergePdfUseCase.kt`
**Estado:** ✅ Corregido 2026-09-17 -- `skippedCount` + aviso agregado al
mensaje de éxito (`pdf_merge_partial_warning`, 12 idiomas). Extraído
`buildSuccessMessage()` tras superar el límite de `CyclomaticComplexMethod`
de detekt. Test nuevo verificando merge parcial en `MergePdfUseCaseTest.kt`.

### M6 — Acciones desde un QR leído tragan la excepción en silencio
**Área:** Escáner/QR · **Archivo:** `QrResultDisplay.kt`, `QrScreen.kt`
**Estado:** ✅ Corregido 2026-09-17 -- Toast (`qr_action_no_app`, 12
idiomas) agregado a los 4 catches (`addContact`/`addCalendarEvent`/
`openDocumentExternally`/`openUrl`), además del `Timber.e` ya existente.

### M7 — Diálogos de límite diario del Escáner sin "Obtener Premium" cableado
**Área:** Escáner/QR · **Archivo:** `ScanResultScreen.kt`
**Estado:** ✅ Corregido 2026-09-17 -- `onPremiumClick` (ya recibido por
`ScanResultScreen`) se enhebra a través de `ScanResultSideEffects` hasta
`ScanDailyLimitDialog`/`ScanSaveLimitDialogHost`/`ScanSaveLimitDialog`.

### M8 — Apache POI: recursos cerrados fuera de try/finally en el Visor
**Área:** Visor · **Archivo:** `ViewerScreen.kt` (`extractExcelSheets`/`extractPptSlides`)
**Estado:** ✅ Corregido 2026-09-17 -- `workbook.close()`/`slideShow.close()`
movidos a un bloque `finally`. No se encontró un archivo Excel/PPT en el
dispositivo de prueba para verificar en vivo; confirmado por revisión de
código + gauntlet.

### M9 — `FlattenAnnotationsPdfUseCase`: archivo huérfano si falla a mitad de camino
**Área:** Visor · **Archivo:** `FlattenAnnotationsPdfUseCase.kt`
**Estado:** ✅ Corregido 2026-09-17 -- `outputFile` pasó de `val` local al
`try` a `var` visible también en el `catch`, que ahora lo borra si la
excepción ocurre después de crear el archivo físico (mismo patrón ya usado
para `cacheFile`).

### M10 — `Log.d` directo filtra `fileUri` también en release
**Área:** Visor · **Archivo:** `ViewerScreen.kt:259`
**Estado:** ✅ Corregido 2026-09-17 -- cambiado a `Timber.d`. Verificado
por código: `CrashlyticsTree.isLoggable()` filtra `priority < Log.INFO`, así
que en release (sin `DebugTree`) este log no llega ni a Logcat ni a
Crashlytics -- a diferencia de `Log.d` directo, que sí imprimía siempre.

### M11 — `restorePurchases()` sin guard de re-entrada
**Área:** Ajustes/Premium · **Archivo:** `PremiumViewModel.kt`
**Estado:** ✅ Corregido 2026-09-17 -- mismo guard `isPurchasing` que ya
tiene `purchase()`. Verificado en vivo: 3 toques rápidos en "Restaurar
compras" produjeron un solo Snackbar ("No se encontraron compras
anteriores"), sin crash ni duplicados.

### M12 — `restorePurchases()` no localiza el mensaje de error
**Área:** Ajustes/Premium · **Archivo:** `PremiumViewModel.kt`, `PremiumScreen.kt`, `BillingManager.kt`
**Estado:** ✅ Corregido 2026-09-17 -- `restorePurchases()` gana un param
`restoreErrorMessage` que fija `purchaseErrorMessage` antes de consultar
Play Billing, evitando el `debugMessage` crudo en inglés en
`PurchaseResult.Error`. Corrección tras revisión adversarial: la primera
versión reutilizaba `premium_purchase_error` ("No se pudo completar la
compra") también para un fallo de RESTAURAR -- mensaje localizado pero
incorrecto. Se agregó `premium_restore_error` dedicado (12 idiomas). Tests
nuevos en `PremiumViewModelTest.kt` (archivo creado en este lote, la clase
no tenía cobertura previa), incluyendo uno que reproduce el orden real de
ejecución (`emitResult()` despacha en una corrutina aparte del propio
`BillingManager`, no del llamador).

### M13 — Carrera read-modify-write en `PomodoroEngine.tick()`
**Área:** Estudio/Agenda · **Archivo:** `PomodoroEngine.kt`
**Estado:** ✅ Corregido 2026-09-17 -- `tick()` (corre en
`Dispatchers.Default`) usaba lectura+escritura no atómica sobre
`_state.value`, distinto del bug de doble-velocidad ya corregido antes: un
`pause()`/`reset()` desde el hilo principal, concurrente con un tick ya en
curso (más allá de su único punto de suspensión, donde `cancel()` ya no
tiene efecto), podía perderse -- el tick terminaba escribiendo `next`
(derivado de un `current` obsoleto) encima del `isRunning=false` recién
puesto, resucitando el cronómetro. Se cambió a
`_state.compareAndSet(current, next)`: si el estado cambió mientras tanto,
el tick se descarta entero sin duplicar sus side effects. Verificado en
vivo con un stress test real: iniciar, y con el timer corriendo, 4 toques
rápidos pausar/reanudar/pausar/reanudar -- quedó "en progreso" con el
tiempo avanzando a velocidad normal (24:59→24:48 en 11s reales), sin
duplicar velocidad ni quedar en estado inconsistente.

## Prioridad Baja / i18n (23 hallazgos)

Corregidos 2026-09-17: 13 de 23. Los 10 restantes quedan ⚠️ evaluados y no
corregidos a propósito -- son refactors/decisiones de alcance mayor
(reescribir un extractor completo, tocar una carrera de callbacks de TTS ya
delicada, agregar una función nueva de "editar nota") donde el riesgo de
regresión supera el beneficio de un hallazgo Baja, o directamente código
inalcanzable hoy que no se puede verificar sin reactivarlo antes.

### B1 — Saneo de nombre ad hoc en vez de `sanitizeOutputFileName()`
**Estado:** ✅ Corregido -- `PdfPasswordUseCase.protect()`/`removePassword()`
usan ahora la función centralizada (mismo patrón que ~27 sitios más).

### B2 — `contentDescription = null` en íconos de menú/contraseña
**Estado:** ✅ Corregido -- ícono "⋮" de `SecurityScreen.kt` reutiliza
`viewer_more_options`; mostrar/ocultar contraseña de `PdfPasswordScreen.kt`
(2 sitios) usa nuevos `password_show`/`password_hide` (12 idiomas),
invertidos correctamente según el ícono mostrado.

### B3 — Campo `rangeTooSmall` muerto en `SplitPdfUseCase`
**Estado:** ✅ Corregido -- eliminado el campo, su string
(`pdf_split_range_too_small`, 12 idiomas) y el call site en
`PdfToolsScreen.kt`. Confirmado con el propio código: el bug que motivó
este campo (rechazar extraer 1 sola página) ya se corrigió el 2026-09-08 y
dejó el campo sin ningún lector.

### B4/B5 — Word/PPT: orden de párrafos+tablas / falta workaround SAF
**Estado:** ⚠️ Evaluado, no corregido -- ambas funciones están ocultas tras
`HIDDEN_FROM_UI` (no hay usuario real expuesto hoy); una corrección real
implica reescribir el extractor Word con recorrido por `bodyElements` en
orden real de documento, un refactor de alcance mayor sin beneficio visible
para el hallazgo Baja que representa hoy.

### B6 — `PdfDocument.close()` fuera de `.use{}` en `ConvertImageToPdfUseCase`
**Estado:** ✅ Corregido -- todo el cuerpo relevante (incluidos los
`return@withContext` de error) queda dentro de un `try/finally` con
`pdfDocument.close()` único.

### B7 — `SimpleDateFormat` compartido sin sincronización
**Estado:** ✅ Corregido -- `DailyLimitManager.checkAndResetIfNewDay()` crea
una instancia local por llamada en vez de un campo de clase compartido
(mismo patrón ya usado en el resto de la app para timestamps).

### B8 — Código muerto: `onCategorySelected`/`getCategoryLabel`
**Estado:** ✅ Corregido -- eliminados `ConverterViewModel.onCategorySelected()`,
`ConverterUiState.selectedCategory`/`filteredTypes` y
`ConversionType.getCategoryLabel()` (duplicado byte a byte de
`ConverterScreen.getCategoryForUi()`, que es el mapeo real que usa la UI).
Confirmado con una búsqueda amplia en todo el proyecto que no quedan
referencias.

### B9 — `scanner_edits/` acumula archivos sin borrar los anteriores
**Estado:** ⚠️ Evaluado, no corregido -- una limpieza correcta exige
rastrear, por página, qué archivo de `cacheDir` reemplaza a cuál (el estado
de edición por página vive en `ScanResultScreen.kt`, no en
`ScanImageEditor`) -- borrar "todo lo demás" en el directorio arriesgaba
borrar el archivo de OTRA página todavía en uso. `cacheDir` además es
reclamable por el propio Android bajo presión de almacenamiento, así que no
es una fuga real de datos del usuario, solo una oportunidad de limpieza
proactiva perdida.

### B10 — Rama `isPdf=true` inalcanzable con bug latente
**Estado:** ⚠️ Evaluado, no corregido -- confirmado el bug real
(`onFinalized(File)` nunca se invoca en el camino de éxito de esta rama
porque `DownloadsSaver.saveUri()` no produce un `File` local, a diferencia
del otro camino), pero ML Kit ya no devuelve resultados PDF hoy -- no hay
forma de verificar en vivo una corrección de este código, y una corrección
mal probada podría introducir un bug nuevo en código que nadie ejerce.
Documentado acá para quien reactive este camino en el futuro.

### B11 — Prefijo `https://` sensible a mayúsculas
**Estado:** ✅ Corregido -- `QrScreen.kt` usa
`startsWith("http", ignoreCase = true)`.

### B12 — Contraseña de PDF en `rememberSaveable`
**Estado:** ⚠️ Evaluado, no corregido -- `rememberSaveable` en
`PdfPasswordDialog` es un fix deliberado de la revisión general 2026-09-16
para un bug real (la contraseña se perdía al rotar el dispositivo, `MainActivity`
no declara `configChanges`). Revertir a `remember` reintroduciría ese bug;
una solución que evite ambos problemas (estado en un ViewModel scopeado en
vez del Bundle de Activity) es un cambio de arquitectura mayor no
justificado para este hallazgo Baja.

### B13 — Duplicación de `*ViewerContent` (Word/Excel/PPT/Texto)
**Estado:** ⚠️ Evaluado, no corregido (extracción completa) / ✅ Corregido
(la inconsistencia concreta) -- unificar las 4 pantallas en un genérico es
un refactor de alcance mayor sobre el área más compleja de la app. Se
corrigió la única inconsistencia real y acotada que el hallazgo señalaba:
`PptViewerContent` ahora tiene su propio `hasError`, igual que
Word/Excel/Texto.

### B14 — "KB" hardcodeado en Almacenamiento
**Estado:** ✅ Corregido -- `StorageRow` y el Total del diálogo de
Almacenamiento usan `stringResource(R.string.file_size_kb, ...)`.
Verificado en vivo en un lote anterior que ese mismo patrón (M2) muestra
correctamente decimales/separadores por idioma.

### B15 — `shareApp()` hardcodeado en español
**Estado:** ✅ Corregido -- mensaje y título del chooser resueltos vía
`stringResource` (`settings_share_app_message`, 12 idiomas; el título
reutiliza `settings_share_app`).

### B16 — `sendSupportEmail()` solo es/no-es + versión hardcodeada
**Estado:** ✅ Corregido -- asunto/cuerpo del email ahora en los 12 idiomas
(`settings_support_email_subject`/`_body`), y `"App: 1.0.0"` reemplazado por
`BuildConfig.VERSION_NAME` (queda correcto en cada release sin editar a
mano).

### B17 — Ruta muerta `NavRoutes.Qr` + import duplicado
**Estado:** ✅ Corregido -- ambos eliminados, sin referencias huérfanas.

### B18 — Cambiar de mes no actualiza `selectedDate`
**Estado:** ✅ Corregido -- `AgendaViewModel.goToPreviousMonth()`/
`goToNextMonth()` ahora conservan el mismo día-del-mes si existe en el mes
nuevo, recortado al último día si no (`YearMonth.lengthOfMonth()`). 3 tests
nuevos en `AgendaViewModelTest.kt` (archivo creado, la clase no tenía
cobertura previa). **Verificado en vivo** en el Motorola Edge 30 Neo,
incluyendo el caso de recorte exacto: seleccioné el 30 de septiembre,
avancé a octubre (31 días, se conserva "30") y seguí avanzando hasta
febrero 2027 (28 días) -- quedó correctamente recortado a "28", con el
detalle de abajo sincronizado en cada paso ("Domingo 28 de febrero").

### B19 — Callbacks de TextToSpeech mutan State sin garantía de hilo
**Estado:** ✅ Corregido (parcial, acotado a lo verificable) / ⚠️ Evaluado
el resto -- `previewVoice()` (autoescucha de una voz en Ajustes de
lectura) ahora despacha sus callbacks `onDone`/`onError` a
`Handler(Looper.getMainLooper())` antes de volver a tocar `tts.voice`. El
handler mucho más grande de "Leer todo" (múltiples `tts.speak()` en bucle,
guardado de progreso, lógica de extracción incremental ya afinada en
varias rondas previas) se dejó sin tocar -- envolverlo en el mismo patrón
arriesgaba cambiar el orden de ejecución de una máquina de estados ya
delicada, para un hallazgo de robustez teórica (el snapshot system de
Compose ya soporta escrituras de estado desde cualquier hilo; el riesgo
real es la reentrada al motor TTS, no perceptible sin un motor OEM
específico que falle).

### B20 — Notas no refresca `POST_NOTIFICATIONS` al volver de Ajustes
**Estado:** ✅ Corregido -- `StudyScreen.kt` agrega `ReloadOnScreenResume`
para releer el permiso real al volver a la pantalla (mismo patrón ya usado
en `AgendaScreen.kt` para `SCHEDULE_EXACT_ALARM`).

### B21 — Duplicación `NoteLinkDocumentDialog`/`AgendaLinkDocumentDialog`
**Estado:** ✅ Corregido -- eran duplicados byte a byte salvo 3 strings.
Extraído `core/ui/components/LinkDocumentDialog.kt` parametrizado por esos
3 textos; ambos archivos originales quedan como wrappers delgados con la
misma firma pública de antes (ningún call site cambió).

### B22 — No existe edición de una nota ya guardada
**Estado:** ⚠️ Evaluado, no corregido -- es una funcionalidad nueva (no un
bug), fuera del alcance de una auditoría de corrección de bugs.

### B23 — `CalendarDayCell` sin `contentDescription`
**Estado:** ✅ Corregido -- cada celda expone ahora una descripción de
accesibilidad con fecha completa + "hoy"/"con eventos" cuando corresponde
(`agenda_calendar_day_today`/`_has_events`, 12 idiomas), en vez del número
suelto.

---

## Huecos de cobertura de tests más importantes (por área)

- **Home/Biblioteca/Seguridad:** `HomeViewModel`, `LibraryViewModel`, `TrashViewModel`, `FavoritesRepository` sin ningún test propio. Ningún test de `SecurityManagerTest`/`SecurityViewModelTest` cubre la rama `File.delete() == false` sin excepción (habría detectado A1).
- **Convertidor:** `ConvertImageToPdfUseCase` y `PdfToHtmlUseCase` (tipos activos) sin tests. `ConverterViewModel` solo testeado en modo lote, nunca archivo único (guard de doble-toque, recuperación tras OOM). Ningún test verifica orden párrafo/tabla en Word (habría detectado B4).
- **Herramientas PDF:** no existe `PdfToolsViewModelTest` (habría detectado A2). Ningún test de herramienta fuerza una excepción a mitad de procesamiento para verificar limpieza de archivo huérfano. `MergePdfUseCaseTest` sin caso de "una URI falla" (habría detectado M5).
- **Escáner/QR:** `historyContentPreview`/`extractWifiSsid` sin test (habría detectado A3 con un caso `"wifi:"` minúscula). `ScanSessionManager` con cobertura cero en su lógica de estado real (dedup, rename, delete).
- **Visor:** `ViewerViewModel` (1248 líneas) con cero tests unitarios -- ni desbloqueo de PDF, ni `isReadOnlyPreview` (habría detectado A5), ni debounce de última página, ni `shareDocument`. `extractExcelSheets`/`extractPptSlides` sin test (habría detectado M8). `renderPdfPagesToBitmaps` sin test (habría detectado A6).
- **Ajustes/Premium:** `PremiumViewModel` sin tests (habría detectado M11/M12 por simetría con `purchase()`). `ThemeManagerTest` no cubre `fontScale`/`animatedBackgroundEnabled`. `DailyLimitManagerTest` no ejercita una clave de herramienta no mapeada (habría detectado A7 -- ya detectó el mismo bug 2 veces antes).
- **Estudio/Agenda:** `NoteReminderScheduler`/`ReminderScheduler` sin tests (difícil por construir su propio `AlarmManager`, valdría un `Context` fake). `NoteRepositoryTest` no verifica que `createNote`/`deleteNote` llamen a `schedule()`/`cancel()` (a diferencia de `AgendaRepositoryTest`, que sí lo hace -- asimetría real). `PomodoroEngine` (el objeto real, no solo `tickPomodoro`) sin cobertura -- habría detectado M13. Sin tests de `AgendaViewModel`, `NotesViewModel`, `BootRescheduleReceiver`.

---

## Plan de trabajo

1. ✅ Corregir los 10 hallazgos de **Prioridad Alta** (A1-A10) -- hecho
   2026-09-17, con gauntlet completo + revisión adversarial (que encontró y
   corrigió un hallazgo adicional real en A9, tipo de imagen mal detectado
   en Word) + verificación en dispositivo real para A8/A9/A10 (A1/A5 no
   verificables en vivo sin el PIN real del usuario, quedan cubiertos por
   gauntlet + revisión de código). Fusionado (commit `c217396`).
2. ✅ Corregir los 13 hallazgos de **Prioridad Media** (M1-M13) -- hecho
   2026-09-17, con gauntlet completo + revisión adversarial (que encontró y
   corrigió 3 hallazgos adicionales reales: M1 no distinguía error total de
   "original no borrado", M3 no llegaba a Ajustes/Onboarding, M12 reutilizaba
   el mensaje de error equivocado) + verificación en dispositivo real para
   M2/M11/M13 (M1 no verificable sin el PIN real; M6/M7/M3 requieren forzar
   condiciones de fallo no prácticas en un dispositivo real; M8/M9/M10 no
   verificables en vivo por falta de un archivo Excel/PPT de prueba --
   cubiertos por gauntlet + revisión de código). Fusionado (commit `cca7aa8`).
3. ✅ Evaluar los 23 de **Prioridad Baja/i18n** -- hecho 2026-09-17: 13
   corregidos (B1, B2, B3, B6, B7, B8, B11, B14, B15, B16, B17, B18, B20,
   B21, B23 -- ver el detalle de cada uno arriba, incluye 1 fix acotado
   dentro de B13 y de B19), 10 documentados "⚠️ Evaluado, no corregido" por
   requerir una decisión de alcance mayor o ser código inalcanzable hoy (B4,
   B5, B9, B10, B12, el resto de B13, el resto de B19, B22). Gauntlet
   completo + revisión adversarial (sin hallazgos nuevos) + verificación en
   vivo de B18 (el fix de mayor riesgo del lote, incluyendo el caso de
   recorte de día exacto). Pendiente de aprobación para fusionar.
4. Sumar tests de regresión para los huecos de cobertura que hubieran detectado cada hallazgo Alta/Media, como parte de su propio fix (no como tarea aparte).
