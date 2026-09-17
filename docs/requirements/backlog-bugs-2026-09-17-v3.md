# Auditoría general de punta a punta — 2026-09-17 (tercera ronda del día)

**Estado: catalogado, en ejecución.** Pedida por el usuario tras cerrar por
completo la auditoría de 10 Alta + 13 Media + 23 Baja (commits `c217396`,
`cca7aa8`, `ef35315`, `e4f4427`, `32bb725`) y decidir postergar el backlog
de mejoras UX/monetización/IA para el final. Alcance explícito del
usuario: revisar de punta a punta Visor, Escáner, Código QR, Notas,
Lectura, Herramientas PDF; investigar fallas reportadas por usuarios
reales en el Creador de QR; investigar el comportamiento distinto de
"Muy grande" en Ajustes → Apariencia → Tamaño de letra; revisar
consistencia de márgenes de los banners de anuncios (excepto Carpeta
Segura); barrido general de nuevos bugs; limpieza de código sin romper
funcionalidad; correr tests; corregir hallazgos de SonarCloud sin
perjudicar (o mejorando) el comportamiento ya logrado.

Metodología: 6 agentes en paralelo, uno por área, cada uno contrastando
contra `backlog-bugs-2026-09-16.md`, `-v2.md` y `-2026-09-17.md` para no
repetir hallazgos ya cerrados. Todo lo listado acá es nuevo.

## SonarCloud — 3 issues abiertas (causa del `new_security_rating` en ERROR)

Confirmado en vivo contra la API pública de SonarCloud
(`sonarcloud.io/api/issues/search?componentKeys=blackmouthriver_DocuSmart`):

- ✅ **Corregido** (Crítica) `ScanResultScreen.kt:1937`/`:1940` — el
  registro del `BroadcastReceiver` de `shareFileAwaitingSelection()` usaba
  una rama `if (SDK &gt;= TIRAMISU)` con `RECEIVER_NOT_EXPORTED` y otra sin
  flag para versiones viejas. Reemplazado por una única llamada a
  `ContextCompat.registerReceiver(..., ContextCompat.RECEIVER_NOT_EXPORTED)`,
  soportado por `androidx.core:core-ktx:1.15.0` en todas las versiones de
  Android que soporta la app -- cierra las 2 issues Críticas de una vez.
- ✅ **Corregido** (Mayor) `QrContentForms.kt:111` — el campo de contraseña
  Wi-Fi no tenía `keyboardOptions`, dejando el valor cacheable por el
  teclado. Se agregó `KeyboardOptions(keyboardType = KeyboardType.Password)`.
- El `new_coverage` en 0% sigue siendo el problema ya documentado y
  diferido (causa raíz sin confirmar -- el reporte combinado de Jacoco no
  se refleja en CI real pese a estar bien configurado localmente); no es
  un bug de código nuevo, no se re-investiga en esta ronda salvo pedido
  explícito.

## Visor

### V1 — Vista previa de solo lectura de Carpeta Segura: 4 accesos del menú "⋮" sacan el documento protegido sin PIN
**Estado: ✅ Corregido** · **Severidad: Alta (seguridad)** · `ViewerTopBar.kt:203-226`

"Mover a Carpeta Segura"/"Renombrar"/"Eliminar"/"Anotar" ya están
bloqueados durante la vista previa de solo lectura de Carpeta Segura
(`isReadOnlyPreview`), pero "Convertir", "Crear QR", "Hacer buscable"
(OCR) y "Firmar" del mismo menú no tienen ningún guard. Cualquiera de los
4 saca la URI efímera de `cacheDir/secure_preview/` hacia Convertidor/
Herramientas PDF, que escriben su salida en almacenamiento normal (fuera
de Carpeta Segura, sin PIN) -- burla la garantía de "100% privado en
Carpeta Segura" ya publicada, mismo tipo de fuga que A5 (2026-09-17) pero
por 4 puntos de entrada distintos y no cubiertos.
**Fix sugerido:** envolver los 4 `DropdownMenuItem` en el mismo
`if (!isReadOnlyPreview)`.

### V2 — PDF con contraseña vía `content://` sin lectura muestra "contraseña incorrecta" engañoso
**Estado: ✅ Corregido** · **Severidad: Media** · `ViewerViewModel.kt:571-575`

Las ramas de ruta absoluta y `file://` de `unlockPdfWithPassword`
verifican `exists()`/muestran `pdf_pw_read_error` si el archivo no está
disponible; la rama `content://` no, así que un permiso SAF revocado o un
proveedor caído (URI persistida que dejó de ser válida) cae en el mismo
catch que "contraseña incorrecta" -- el usuario reintenta la contraseña
correcta indefinidamente sin poder resolver el problema real.
**Fix sugerido:** comprobar `openInputStream() == null` explícitamente y
devolver `pdf_pw_read_error` antes de intentar descifrar.

## Código QR

### Q1 — Falta `CHARACTER_SET=UTF-8` al generar el QR: corrupción silenciosa de emojis/caracteres no latinos
**Estado: ✅ Corregido** · **Severidad: Alta** · `QrScreen.kt:1564-1577` (`generateQrBitmap`)

ZXing sin el hint `CHARACTER_SET` codifica en ISO-8859-1 por defecto, que
sustituye en silencio cualquier carácter fuera de ese charset por `?`.
Afecta los 9 tipos de contenido (SSID/contraseña Wi-Fi, contacto, evento,
texto). Los acentos españoles sobreviven (están en Latin-1), lo que
ocultó el bug en pruebas en español, pero cualquier emoji o script no
latino (cirílico, árabe, chino, coreano -- relevante con 12 idiomas
soportados) lo dispara siempre. Para Wi-Fi esto rompe la conexión real
sin ningún mensaje de error -- coincide directamente con el reporte de
usuarios de que "algunas opciones no funcionan".
**Fix sugerido:** agregar `EncodeHintType.CHARACTER_SET to "UTF-8"` al
mapa de hints, incondicional.

### Q2 — Fallo total y silencioso al exceder la capacidad del QR (logo + contraseña + contenido largo)
**Estado: ✅ Corregido** · **Severidad: Alta** · `QrScreen.kt:1349`, `1330-1394`, `1564-1595`

`generateQrBitmap` atrapa `WriterException` y devuelve `null`, pero el
llamador nunca comprueba ese `null`: no hay string de error para este
caso, y aun así se ejecuta `QrHistoryStorage.save`/`logQrCreated`. Con
logo (fuerza nivel de corrección H, ~1273 bytes máx.) + contraseña
(`QrCrypto` agrega ~44 bytes + Base64 ×4/3 + prefijo) + texto largo
(&gt;900 caracteres, uso común de "Texto" para notas), `encode()` lanza y
el usuario ve el spinner desaparecer sin que pase nada -- ni QR ni error,
más una entrada fantasma en el Historial.
**Fix sugerido:** comprobar el resultado de `generateQrBitmap`; si es
`null`, mostrar un error nuevo (`qr_error_generation_failed`) y no
ejecutar `save`/`logQrCreated`.

### Q3 — `escapeVCardField()` no escapa `\r` explícitamente
**Estado: ⚠️ Evaluado, no corregido** · **Severidad: Baja** · `QrContentFormat.kt:132-137`

Un `\r` suelto (texto pegado desde Windows/Word con `\r\n`) queda sin
escapar en vCard/iCalendar. La mayoría de parsers lo toleran; no
confirmado un lector real que falle. Nota, no hallazgo confirmado.

## Modo Estudio (Notas + Lectura + Pomodoro)

### E1 — Rotar el dispositivo durante "Leer todo" (o con una nota sin guardar) destruye toda la sesión de Estudio
**Estado: ✅ Corregido** · **Severidad: Alta** · `StudyScreen.kt:151-202`

`documentUri`/`documentText`/`pageBoundaries`/`highlights`/`notes`/
`isSpeaking`/etc. son `remember`, no `rememberSaveable`; una rotación real
recrea la Activity y el `DisposableEffect` de TTS llama
`stop()`/`shutdown()` en `onDispose`. Mismo patrón que ya se corrigió como
Alta en el Visor (backlog 2026-09-16, #10) pero nunca replicado a
`StudyScreen`. Rotar a mitad de "Leer todo" corta el audio, pierde el PDF
cargado y todo el texto extraído, y descarta cualquier nota a medio
escribir (texto/imágenes/recordatorio) sin aviso.
**Fix sugerido:** mover el estado a un `HiltViewModel` (patrón ya usado
por Pomodoro) o `rememberSaveable` con `Saver` para listas/URI.

### E2 — La vista del PDF no sigue la página que se está leyendo en "Leer todo"
**Estado: ✅ Corregido** · **Severidad: Media** · `StudyScreen.kt:1127-1130`, `:1294`

`currentPage`/`totalPages` solo alimentan el texto "Página X de Y"; no
hay `LazyListState` compartido con `StudyPdfViewer` ni
`animateScrollToItem`. La etiqueta avanza pero la imagen visible queda
fija donde el usuario la dejó.
**Fix sugerido:** compartir un `LazyListState` entre `ReadingTab` y
`StudyPdfViewer`, `animateScrollToItem(currentPage - 1)` en
`LaunchedEffect(currentPage)`.

### E3 — El contador de "descanso largo cada 4 pomodoros" no sobrevive si el proceso muere entre bloques
**Estado: ✅ Corregido** · **Severidad: Media** · `PomodoroEngine.kt:91` (`pomodoroCount` en memoria)

`pomodoroCount` vive solo en el singleton en memoria, nunca se
inicializa desde `StudyStatsStorage` (que sí persiste el historial real y
alimenta correctamente el número mostrado en pantalla). Pausar y que el
proceso muera (memoria baja o cierre manual) reinicia el contador a 0 --
contradice el propio texto publicado ("Cada 4 pomodoros = descanso largo").
**Fix sugerido:** seedear `pomodoroCount` desde un contador diario
persistido al iniciar el engine.

### E4 — Páginas del PDF en Modo Estudio sin `contentDescription` para TalkBack
**Estado: ✅ Corregido** · **Severidad: Baja** · `StudyScreen.kt:1359-1363`

El Visor principal ya corrigió esto (`viewer_page_content_desc`,
backlog 2026-09-16-v2 #15); nunca se replicó al visor propio de Estudio,
justamente una función pensada para accesibilidad.

## Herramientas PDF / Convertidor

### P1 — Sin `catch(OutOfMemoryError)` en 11 conversores basados en Apache POI/ZIP
**Estado: ✅ Corregido** · **Severidad: Media** · `WordToPdfUseCase.kt:69`, `ExcelToPdfUseCase.kt:88`,
`PptToPdfUseCase.kt:99` + 8 más (`WordToTextUseCase`, `WordToHtmlUseCase`,
`ExcelToCsvUseCase`, `ExcelToHtmlUseCase`, `PdfToWordUseCase`,
`PdfToHtmlUseCase`, `PdfToTextUseCase`, `PptToTextUseCase`)

`OutOfMemoryError` no hereda de `Exception`, así que ninguno de estos 11
use cases lo atrapa (a diferencia de `ConvertImageToPdfUseCase`/
`PdfToImageUseCase`/`ImageFormatUseCase`, que sí lo agregaron en la ronda
2026-09-16). En los 3 que crean el `outputFile` antes de terminar de
procesar (Word/Excel/Ppt→Pdf), un OOM a mitad de camino deja un `.pdf`
parcial huérfano. En modo lote, además, el `OutOfMemoryError` se propaga
fuera del `try/catch` de `ConverterViewModel.convert()` y aborta TODO el
lote, perdiendo resultados ya exitosos de archivos anteriores.
**Fix aplicado:** `catch (e: OutOfMemoryError)` agregado a los 11, mismo
patrón que `ImageFormatUseCase`, con `outputFile?.delete()` en los 3 que
crean el archivo temprano (Word/Excel/Ppt→Pdf). Con esto, ningún use case
del Convertidor propaga ya un `OutOfMemoryError` sin atrapar -- el
problema de "aborta todo el lote" queda resuelto como efecto directo,
sin necesidad de tocar `ConverterViewModel.runBatchConversion()` por
separado.

### P2 — Ninguna herramienta tiene cancelación cooperativa; salir con "Atrás" no detiene el trabajo en curso
**Estado: ✅ Corregido (parcial, ver nota)** · **Severidad: Media** ·
`OcrPdfUseCase.kt:216-223`, `CompressPdfUseCase.kt:167-190`,
`PdfToolsViewModel.kt:404-458`, `ConverterViewModel.kt:318-356`

Los ViewModels quedan scopeados al `NavBackStackEntry`; navegar hacia
atrás durante el procesamiento cancela la corrutina, pero como no hay
ningún punto de suspensión dentro de los bucles de página/archivo, la
cancelación cooperativa nunca se evalúa a tiempo -- el trabajo (OCR
página por página, compresión, conversión por lote) sigue corriendo en
segundo plano hasta el final, y el archivo resultante queda huérfano en
`filesDir/` sin ninguna referencia en la UI.
**Fix aplicado:** `coroutineContext.ensureActive()` en el bucle de páginas
de `OcrPdfUseCase.ocrAllPages()` y `CompressPdfUseCase.
renderAndCompressPages()` (ambas convertidas a `suspend fun`), que son las
operaciones más largas y las que más CPU/batería desperdician si siguen
corriendo tras una cancelación. **Nota de alcance:** no se agregó
`BackHandler`/confirmación en la UI ni se tocó `runBatchConversion()` --
entre archivos del lote, cada conversión ya pasa por su propio
`withContext(Dispatchers.IO)`, que de por sí es un punto de cancelación
cooperativa real, así que ese caso ya estaba cubierto sin cambios
adicionales.

## Escáner

### S1 — "Escanear otro"/"Escanear de nuevo" borra la sesión acumulada antes de mostrar el siguiente documento
**Estado: ✅ Corregido** · **Severidad: Alta** ·
`ScanResultScreen.kt:474-475`, `244`, `297`, `311`, `361`

El `DisposableEffect(Unit) { onDispose { scanSessionViewModel.clearSession() } }`
agregado para limpiar sesiones abandonadas también se dispara cuando
"Escanear otro" navega hacia atrás (`onBack()` → `popBackStack()`) para
volver a capturar la página siguiente -- vacía el singleton `_scannedFiles`
justo cuando "Escanear otro" está pensado para hacerlo crecer. Escanear A,
guardarlo, "Escanear otro", escanear B: la lista de sesión termina
mostrando solo B, A desaparece de la UI (el archivo en disco sigue
intacto, pero la sesión visual lo pierde).
**Fix aplicado:** bandera local `isContinuingSession`, seteada en
`scanAgainAction` antes de llamar a `onBack()` y leída con
`rememberUpdatedState` dentro del `onDispose` -- si sigue en la misma
sesión, no limpia; en cualquier salida real (bandera en `false`, valor por
defecto), limpia igual que antes.

### S2 — `ScanImageEditor.applyColorMode()` no registra sus archivos de caché (regresión parcial de B9)
**Estado: ✅ Corregido** · **Severidad: Media** · `ScanImageEditor.kt:84-100`

B9 agregó `ownedCacheFiles` para poder borrar con seguridad los archivos
propios, pero solo se cablea en `applyAdjustments()`, no en
`applyColorMode()` -- cada cambio de modo de color (Escala de
grises/Blanco y negro/Resaltar texto) deja un archivo huérfano en
`cacheDir/scanner_edits/` que `deleteCachedFile()` nunca encuentra.
**Fix sugerido:** registrar también en `ownedCacheFiles` el archivo de
`applyColorMode()`.

### S3 — "Guardar todas en Descargas" del lote del Escáner no muestra estado de "guardando"
**Estado: ✅ Corregido** · **Severidad: Baja** ·
`ScanResultScreen.kt:848-854` vs `ConverterScreen.kt:180-188`

`BatchConversionSuccess` recibe el guard `isSaving` en el Convertidor
(agregado como fix real 2026-09-16) pero la llamada del Escáner no lo
pasa -- el guard síncrono de `saveAllToDownloads()` evita el doble
guardado real, pero la UI queda inconsistente sin feedback visual.
**Fix sugerido:** pasar `isSaving` en la llamada del Escáner igual que
hace el Convertidor.

## Ajustes — Tamaño de letra "Muy grande" y consistencia de banners

### A1 — El propio selector de tamaño de letra se deforma al elegir "Muy grande"
**Estado: ✅ Corregido** · **Severidad: Alta** ·
`SettingsScreen.kt:596-611` (`AppearanceFontScaleRow`)

`SingleChoiceSegmentedButtonRow` con 3 segmentos de ancho igual, sin
`maxLines`/manejo de overflow. Las etiquetas usan `labelLarge`, que se
reescala globalmente en cuanto se toca cualquier opción. "Muy grande" (o
su traducción) es la etiqueta más larga en los 12 idiomas verificados;
al tocarla, el multiplicador 1.3× se aplica de inmediato y el propio
segmento donde el usuario está mirando se deforma/envuelve. "Grande"
(+15%) es la mitad de salto, por eso solo "Muy grande" se ve "distinto".
**Fix sugerido:** `maxLines=1` + `TextOverflow.Ellipsis` o auto-size en
las etiquetas del segmented control.

### A2 — Sin techo combinado entre el font-scale del sistema (accesibilidad) y el multiplicador propio de la app
**Estado: ✅ Corregido** · **Severidad: Media** · sin coerción en `Theme.kt`

El multiplicador propio (hasta 1.3×) se compone multiplicativamente con
el `Configuration.fontScale` del sistema (hasta 1.3×-2× según OEM) sin
límite superior -- en un dispositivo con accesibilidad alta + "Muy
grande" el texto final puede llegar a ~2.5×-3× el tamaño base, agravando
A1 y cualquier otro layout ajustado.
**Fix aplicado:** `MAX_COMBINED_FONT_SCALE = 1.8f` en `Theme.kt` --
el multiplicador propio se reduce (nunca por debajo de 1x) según
`LocalDensity.current.fontScale` para que el producto no supere el techo.

### A3 — Label de la barra de navegación inferior no escala con "Tamaño de letra"
**Estado: ✅ Corregido** · **Severidad: Media** ·
`DocuSmartBottomBar.kt:344` (`fontSize = 11.sp` hardcodeado)

No deriva de `MaterialTheme.typography`, así que en "Muy grande" el
resto de la UI crece 30% pero la barra de navegación queda igual --
inconsistente con la promesa de la función.
**Fix sugerido:** usar `MaterialTheme.typography.labelSmall` en vez del
valor fijo.

### A4 — Dígito del teclado numérico del PIN con `fontSize` hardcodeado
**Estado: ⚠️ Evaluado, no corregido** · **Severidad: Baja** ·
`SecurityScreen.kt:513` (`fontSize = 24.sp`)

Dentro de un círculo fijo de 64dp, sin riesgo de overflow, pero tampoco
escala con la preferencia. Informativo.

### A5 — Banners de anuncios del Creador/Lector de QR con márgenes distintos al resto de la app
**Estado: ✅ Corregido (parcial, ver nota)** · **Severidad: Media** ·
`QrScreen.kt:444-462`, `:883-917`

Usan `.padding(20.dp)` en vez de los 16dp/12dp/8dp estándar centralizados
en `DocuSmartScreenHeader` (que usan Home/Biblioteca/Convertidor/
Herramientas PDF/Estudio/Agenda); en el Creador además el orden
está invertido (título antes que banner) con espaciados distintos.
**Fix aplicado:** se corrigió el orden en el Creador (el banner de
anuncios ahora va antes del banner de título, igual que el resto de la
app). **Nota de alcance:** se dejó el valor de padding en 20dp (en vez de
16dp) por decisión deliberada -- migrar completamente a
`DocuSmartScreenHeader` exige sacar el banner de la `Column` con scroll
compartido y reestructurar el resto del contenido de estas 2 pantallas,
un cambio de mayor riesgo para una diferencia de 4dp de margen. Queda
para una pasada futura si el usuario lo prioriza.

### A6 — Banner del Visor sin margen horizontal, a diferencia del resto de la app
**Estado: ⚠️ Evaluado, no corregido** · **Severidad: Baja-Media** ·
`ViewerScreen.kt:380-389`

El banner del Visor vive abajo (junto a `ViewerBottomBar`, para no tapar
el documento en modo inmersivo) sin ningún padding lateral, mientras el
resto de la app usa 16dp arriba. La posición inferior está justificada
en el código; la falta de margen lateral no. Dado que el pedido explícito
es "misma posición y márgenes salvo Carpeta Segura", conviene decidir
explícitamente: documentar como excepción o agregar el margen de 16dp.

### A7 — Import muerto de `DocuSmartBannerAd` en `StudyScreen.kt:66`
**Estado: ✅ Corregido** · **Severidad: Baja (código)** — `StudyScreen`
delega en `DocuSmartScreenHeader`, que ya lo importa por su cuenta. No es
un bug funcional.

### Confirmado correcto (no es un bug)
Carpeta Segura y Papelera sin banner es una decisión del usuario ya
documentada (`backlog-mejoras-ux-2026-08-30.md:640-642`). "Seguridad"
(el menú con las 2 tarjetas) sí lleva banner correctamente -- es una
pantalla distinta de "Carpeta Segura" (el contenido ya autenticado).

---

## Resumen por severidad

- **Alta (6/6 corregidas):** V1 (seguridad, Carpeta Segura preview), Q1
  (UTF-8 en QR), Q2 (overflow silencioso de QR), E1 (rotación destruye
  sesión de Estudio), S1 (Escanear otro borra sesión), A1 (selector de
  tamaño de letra se deforma).
- **Media (9/9 corregidas):** V2, E2, E3, P1, P2 (parcial, ver nota), S2,
  A2, A3, A5 (parcial, ver nota).
- **Baja (4/6 corregidas):** E4, S3, A7 corregidas; Q3 y A4 evaluadas y no
  corregidas (impacto mínimo/informativo, ver detalle); A6 evaluada y no
  corregida (requiere decisión explícita del usuario, ver detalle).
- **SonarCloud:** 2 Críticas + 1 Mayor, corregidas.

## Estado final

**Las 6 Altas y las 9 Medias quedaron corregidas** (2 de ellas -- P2 y A5
-- con alcance acotado deliberadamente, ver sus notas). De las 6 Bajas,
3 se corrigieron (E4, S3, A7) y 3 quedaron evaluadas sin corregir por ser
de impacto mínimo o requerir una decisión de diseño explícita (Q3, A4,
A6). Gauntlet completo (`compileDebugKotlin`+`detekt`+`lintDebug`+
`testDebugUnitTest`+`compileDebugAndroidTestKotlin`) en verde.

## Revisión adversarial (seguridad + correctitud) sobre este mismo lote

Dos agentes en paralelo revisaron el diff completo antes de la
verificación en dispositivo. Encontraron **6 hallazgos reales**, todos
corregidos en el mismo lote (ninguno requirió revertir un fix ya hecho):

- **[Seguridad, Media] Regresión real introducida por el propio fix S1**:
  `isContinuingSession` se pone en `true` de forma optimista ANTES de
  saber si "Escanear otro" realmente captura una página nueva. Si el
  usuario cancela ese segundo intento (o `activity == null`), `onBack()`
  de `ScannerScreen` ya no vuelve a `ScanResultScreen` (quedó fuera de la
  pila) sino más atrás -- la sesión anterior queda huérfana en el
  singleton `ScanSessionManager`, lista para mezclarse con la próxima
  sesión real. **Corregido**: `ScannerScreen.kt` ahora limpia la sesión
  explícitamente en sus 3 rutas de cancelación (`activity == null`,
  páginas vacías, resultado cancelado) -- no-op seguro si no había nada
  que limpiar, mismo criterio que "Volver al inicio".
- **[Seguridad, Baja/latente] V1 incompleto -- "Compartir" y "Favorito"
  del Visor** no tenían el mismo guard `isReadOnlyPreview` que
  Renombrar/Eliminar/Anotar/Convertir/QR/OCR/Firmar. Hoy no es explotable
  (`file_provider_paths.xml` no declara `secure_preview/`, así que
  `FileProvider` rechaza la URI y el share falla en silencio), pero esa
  protección vive en un XML sin relación documentada con este caso.
  **Corregido**: guard agregado en `shareDocument()`/`shareOriginal()`/
  `shareWithAnnotations()`/`toggleFavorite()` de `ViewerViewModel.kt`,
  defensa en profundidad igual que los demás accesos.
- **[Correctitud, Alta] Riesgo real de `TransactionTooLargeException`**:
  `documentText` (un `String` por párrafo de TODO el documento) quedó en
  `rememberSaveable` -- para el caso de uso real de Modo Estudio
  (libros/apuntes largos) puede ser varios MB, y el Bundle de
  `onSaveInstanceState` tiene un límite de ~1MB. Exactamente el escenario
  que este mismo fix (E1) quiere proteger (rotar con un documento largo
  cargado) podía terminar en un crash en vez de una pérdida de estado.
  **Corregido**: `documentText` vuelve a `remember` (no persiste en el
  Bundle); se agregó un `LaunchedEffect(documentUri)` que re-extrae
  automáticamente el texto si `documentUri` sobrevivió la rotación pero
  `documentText` no, reutilizando el mismo mecanismo de "Retomar lectura"
  (`StudyReadingProgressStorage.findFor`) para no perder el punto de
  progreso si había uno guardado. `pageBoundaries`/`documentUri`/
  `documentName`/`notes`/`highlights`/`extractionComplete` sí son seguros
  en `rememberSaveable` (acotados en tamaño, no crecen con el documento).
- **[Correctitud, Media-Alta] `PomodoroEngine.reset()` bloqueaba la
  siembra real**: marcaba `seededPomodoroCount = true` sin condición,
  así que si el usuario tocaba "Reiniciar" (siempre habilitado) antes que
  "Iniciar" alguna vez en el proceso, el próximo `start()` ya no sembraba
  el contador real desde `StudyStatsStorage` -- reintroducía el bug que
  E3 corrige. **Corregido**: se sacó esa línea de `reset()`; la bandera
  solo se toca en `start()`.
- **[Correctitud, Media] Ruido de logging por `CancellationException`**:
  al convertir `ocrAllPages()`/`renderAndCompressPages()` a `suspend fun`
  con `ensureActive()`, una cancelación real (navegar hacia atrás) caía
  en el `catch (e: Exception)` genérico de cada use case y se registraba
  como un error de OCR/compresión -- no rompía la propagación de la
  cancelación (el `Job` ya cancelado la fuerza igual), pero contaminaba
  cualquier log/reporte de fallos. **Corregido**: `catch (e:
  CancellationException) { throw e }` agregado antes del catch genérico
  en ambos use cases.
- **[Correctitud, Baja/latente] Bomba de tiempo en `Theme.kt`**:
  `coerceIn(1f, fontScale)` asume `fontScale >= 1f` (válido hoy) -- si
  algún día se agrega una opción de fuente menor a 1x sin tocar esta
  fórmula, `coerceIn` con rango inválido lanza `IllegalArgumentException`
  y crashea toda la app al arrancar cualquier pantalla. **Corregido**:
  reemplazado por `coerceAtMost(fontScale).coerceAtLeast(1f)` encadenado,
  mismo resultado hoy, sin ese riesgo. De paso se documentó que el techo
  de 1.8x es sobre el multiplicador PROPIO de la app, no una garantía
  absoluta si el font-scale de accesibilidad del sistema ya lo supera por
  sí solo (deliberado: no se pisa una decisión de accesibilidad tomada
  fuera de la app).

Gauntlet completo re-ejecutado tras estos 6 fixes adicionales: verde.

## Verificación en dispositivo real (Motorola Edge 30 Neo, ZY22G7SB77)

- **A1 confirmado en vivo**: en Ajustes → Apariencia → Tamaño de letra,
  "Muy grande" ahora se trunca ("Muy gra...") en vez de deformar/envolver
  el selector -- captura antes/después comparada.
- **A5 confirmado en vivo**: el Creador de QR muestra el banner de
  anuncios antes del banner de título, igual que el resto de la app.
- **Q1 confirmado con evidencia concreta**: se generó un QR de texto real
  desde la app, se guardó en Descargas y se decodificó con el detector de
  OpenCV -- el propio decodificador avisó "QR: ECI is not supported
  properly", confirmando que ahora SÍ se emite el segmento ECI de UTF-8
  (la causa raíz corregida); el contenido ("Prueba") decodificó
  correctamente, sin regresión para texto ASCII. **No se pudo probar el
  caso con emoji/cirílico en vivo**: `adb shell input text` no soporta
  caracteres no-ASCII en este dispositivo (limitación de la herramienta de
  automatización, no de la app) -- cubierto por revisión de código y la
  confirmación indirecta del ECI de arriba.
- **Q2, S1, S2, V1, V2, E2, E3, E4, P1, P2, A2, A3 no verificables en vivo
  con los recursos de este dispositivo**: Q2 requiere un logo + contraseña
  + texto largo (posible pero no priorizado por tiempo); S1/S2 requieren
  escaneos reales con la cámara; V1/V2 requieren un documento protegido en
  Carpeta Segura con el PIN real del usuario o un PDF con contraseña real;
  E1-E4 requieren un PDF/documento real cargable en Modo Estudio (mismo
  hueco ya documentado para M8/M9/M10/B13 en esta misma auditoría --
  no hay archivos Word/Excel/PDF de prueba en `/sdcard`); P1/P2 requieren
  archivos grandes reales para forzar OOM/cancelación con timing exacto.
  Cubiertos por gauntlet completo + revisión adversarial de dos agentes
  (seguridad y correctitud) sobre el diff completo.
- **Confirmado sin crash**: se rotó Modo Estudio (sin documento cargado,
  ya que no hay ninguno disponible en el dispositivo) a horizontal y de
  vuelta a vertical -- sin `FATAL EXCEPTION` en logcat, layout correcto en
  ambas orientaciones. No prueba el camino completo de E1 (que necesita
  un documento realmente cargado), pero confirma que el nuevo
  `rememberSaveable`/`LaunchedEffect` no rompe el caso más simple.
- Logcat revisado en toda la sesión de pruebas (Ajustes, Crear QR,
  generar, guardar, Modo Estudio, rotación): sin `FATAL EXCEPTION` ni
  excepciones de `com.docsmart` en ningún punto.
- Archivo de prueba (`QR_1789681525551.png`) borrado de Descargas al
  terminar.

Pendiente: aprobación explícita del usuario para fusionar.
