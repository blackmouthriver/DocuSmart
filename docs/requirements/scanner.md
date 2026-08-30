# Módulo: Escáner (documento + QR)

> Parte de [`CONTEXT.md`](../../CONTEXT.md). Cubre el escaneo de documentos
> (Google ML Kit Document Scanner) y el lector/creador de QR (`QrScreen.kt`,
> ya rediseñado en el módulo Seguridad — ver [`security.md`](security.md)
> RF-SEC-13/14/15 para el cifrado del QR protegido).

**Estado:** módulo con menos deuda real de lo que sugería la QA de mayo — la
mayoría de sus hallazgos resultaron obsoletos, no por haberse corregido en
esta sesión sino porque la implementación cambió de raíz (se adoptó Google
ML Kit Document Scanner en vez de una cámara/recorte propios) o porque el
lector de QR ya se reconstruyó por completo durante el módulo Seguridad. Se
encontraron y corrigieron 2 bugs reales menores: detección de esquema de QR
sensible a mayúsculas, y guardado de PDF escaneado en Descargas roto en
Android 8/9. 10 tests nuevos. **RF-SCAN-06/07 implementados 2026-08-29, ver
§8** — cierran el backlog completo del proyecto: brillo/contraste y
reescalado sobre la imagen ya escaneada, resueltos con un paso de edición
propio después del resultado de ML Kit (que no expone esos controles) y un
cambio de flujo necesario para que fuera alcanzable de verdad (ver §8).
**Código relacionado:** `features/scanner/**`.

---

## 1. Alcance

Dos capacidades independientes:

1. **Escanear documento/foto** — delega la captura, recorte, corrección de
   perspectiva y filtros a Google ML Kit Document Scanner (`GmsDocumentScanning`),
   no a una implementación propia. DocuSmart solo recibe el resultado final
   (imágenes o PDF) y lo lleva a `ScanResultScreen` para nombrarlo, guardarlo
   o compartirlo.
2. **Leer/crear QR** — cámara propia con CameraX + ML Kit Barcode Scanning
   para leer, clasificación de contenido (URL/imagen/documento/email/
   teléfono/texto) con acción específica por tipo, y creación de QR con
   contenido opcionalmente protegido por contraseña (AES-256/GCM, ver
   `security.md`).

---

## 2. Requerimientos funcionales

- **RF-SCAN-01** El sistema debe permitir escanear un documento con corrección automática de perspectiva y recorte, delegando la captura a Google ML Kit Document Scanner.
- **RF-SCAN-02** El resultado del escaneo (imágenes o PDF) debe poder nombrarse, guardarse en Descargas o compartirse directamente, en cualquier versión de Android soportada (API 26+).
- **RF-SCAN-03** El sistema debe leer códigos QR con la cámara y clasificar el contenido (URL, imagen, documento, email, teléfono, texto) para ofrecer la acción correspondiente (abrir en navegador, llamar, enviar correo, copiar, etc.).
- **RF-SCAN-04** La detección de tipo de contenido debe ser insensible a mayúsculas/minúsculas y a espacios en blanco alrededor del valor escaneado.
- **RF-SCAN-05** El sistema debe permitir crear un código QR con contenido de texto/URL, opcionalmente protegido con contraseña (ver `security.md` RF-SEC-13).

- **RF-SCAN-06** ✅ Ajuste de brillo/contraste sobre la imagen ya escaneada, antes de guardar. Implementado 2026-08-29, ver §8.
- **RF-SCAN-07** ✅ Reescalar la imagen escaneada (cambiar resolución/tamaño de salida). Implementado 2026-08-29, ver §8.

### Backlog
*(vacío — RF-SCAN-06/07, el backlog original de este módulo y el último pendiente de todo el proyecto, está implementado)*

---

## 3. Requerimientos no funcionales

- **RNF-SCAN-01 (sin control sobre la UI de captura):** al delegar la captura completa a Google ML Kit Document Scanner, DocuSmart no tiene forma de personalizar ni corregir su UI de recorte/filtros/miniaturas — cualquier problema ahí depende de una actualización de Play Services, no de código propio.
- **RNF-SCAN-02 (guardar/compartir debe funcionar en todo el rango de API soportado):** minSdk 26 — cualquier flujo de guardado debe tener una ruta pre-Android 10 (API < 29, sin `MediaStore.Downloads`), no solo la ruta moderna con `MediaStore`.

---

## 4. Historias de usuario con criterios de aceptación

### HU-SCAN-01 — Escanear y guardar un documento en cualquier versión de Android
**Como** usuario con un teléfono Android 8 o 9,
**quiero** poder guardar un documento que acabo de escanear,
**para** no depender de la versión de mi sistema operativo para usar la app.

- **AC1** Dado que escaneo un documento en modo "Documento" (ML Kit devuelve el PDF directamente), cuando toco "Guardar en Descargas" en cualquier versión de Android soportada, entonces el archivo se guarda correctamente.
- **AC2** Este comportamiento debe ser el mismo en Android 10+ (vía `MediaStore.Downloads`) y en Android 8-9 (vía el directorio público de Descargas).

*(Corrige bug real: `savePdfUriToDownloads()` no tenía ninguna implementación para API < 29 — devolvía `false` incondicionalmente. En Android 8/9, escanear en modo "Documento" y guardar producía un fallo silencioso sin explicación.)*

### HU-SCAN-02 — Detectar el tipo de contenido de un QR sin importar mayúsculas
**Como** usuario que escanea códigos QR generados por distintas herramientas,
**quiero** que la app reconozca una URL/email/teléfono sin importar cómo esté escrito el esquema,
**para** ver siempre la acción correcta (abrir en navegador, llamar, etc.).

- **AC1** Dado que escaneo un QR con el esquema en mayúsculas (`HTTPS://...`, `MAILTO:...`, `TEL:...`), cuando se procesa, entonces se clasifica igual que su equivalente en minúsculas.
- **AC2** Dado que el contenido escaneado tiene espacios al inicio o final, cuando se procesa, entonces se ignoran para la clasificación.

*(Corrige bug real: `detectQrContentType()` comparaba el esquema contra el valor sin normalizar — un QR con esquema en mayúsculas caía en la categoría TEXT en vez de URL/EMAIL/PHONE, y el usuario no veía el botón de acción correspondiente.)*

### HU-SCAN-03 — Ajustar brillo, contraste y tamaño antes de guardar
*(Implementado 2026-08-29 — ver §8.)*

**Como** usuario que escaneó una página con poca luz o quiere un archivo más liviano,
**quiero** ajustar brillo/contraste y reducir el tamaño de la imagen antes de generar el PDF,
**para** obtener un resultado más legible o más liviano sin salir de la app.

- **AC1** Dado que escaneo un documento, cuando veo la vista previa de cada página, entonces hay un botón de edición sobre la miniatura.
- **AC2** Dado que abro el editor de una página, cuando muevo los controles de brillo o contraste, entonces la vista previa se actualiza en vivo, antes de tocar "Aplicar".
- **AC3** Dado que elijo una escala menor a 100% (75/50/25%), cuando toco "Aplicar", entonces la imagen resultante tiene ese porcentaje de las dimensiones originales.
- **AC4** Dado que toco "Aplicar", cuando el ajuste termina, entonces la miniatura de esa página se actualiza con el resultado, y el PDF final generado usa la versión editada, no la original.
- **AC5** Dado que toco "Cancelar", cuando esto ocurre, entonces la página queda exactamente igual que antes de abrir el editor.

*(Ver §8 para el cambio de flujo que fue necesario para que esta HU fuera alcanzable: antes, la app siempre usaba el PDF que arma ML Kit internamente, y las páginas como imágenes nunca se usaban en la práctica.)*

---

## 5. Bugs de QA a corregir (trazabilidad)

| Bug (barrido de pruebas v1.0 / mejoras pendientes v1.0.1) | HU que lo cubre | Estado |
|---|---|---|
| "Miniatura de filtros no refleja la imagen real capturada" | — | **Obsoleto por cambio de arquitectura** — la captura ya no usa una cámara/recorte/filtros propios: se delega por completo a Google ML Kit Document Scanner, que no expone ninguna miniatura de filtro al código de DocuSmart. El hallazgo no aplica a la implementación actual. |
| "Falta lector de QR con navegación a URL" | — | **Obsoleto** — ya implementado (`detectQrContentType` + `openUrl`), con clasificación por tipo de contenido y botón de acción específico. No queda claro cuándo se agregó; probablemente durante la reconstrucción de `QrScreen.kt` en el módulo Seguridad. |
| "Falta QR con contraseña compartible" | — | **Obsoleto** — implementado en el módulo Seguridad (`QrCrypto.kt`, AES-256/GCM). Ver `security.md` RF-SEC-13/14/15. |
| Faltan: escalar imagen, ajuste de brillo/contraste | RF-SCAN-06, RF-SCAN-07 | ✅ Resuelto 2026-08-29 — editor propio por página (`ScanImageEditor`), ver §8. |
| **Bug real encontrado hoy (no reportado en la QA):** guardar un documento escaneado en modo "Documento" en Descargas fallaba en silencio en Android 8/9 (`savePdfUriToDownloads` sin implementación pre-Q). | HU-SCAN-01 | ✅ Corregido — agregada la ruta pre-Q (escritura directa al directorio público de Descargas), igual patrón que ya usaba `saveFileToDownloads` en el mismo archivo. |
| **Bug real encontrado hoy (no reportado en la QA):** detección de tipo de contenido de QR sensible a mayúsculas/espacios — un QR con esquema en mayúsculas se clasificaba como texto plano en vez de URL/email/teléfono. | HU-SCAN-02 | ✅ Corregido — normalización a minúsculas + `trim()` antes de clasificar. |

---

## 6. Plan de pruebas unitarias

| # | Cobertura | Estado |
|---|---|---|
| 1 | `QrContentTypeTest` — URL en minúsculas/mayúsculas, con espacios, con extensión de imagen/documento, mailto/tel en mayúsculas/minúsculas, `content://` con y sin extensión reconocida, texto plano sin esquema. | ✅ 10 tests, en verde |
| 2 | `QrCryptoTest` (ya existente, módulo Seguridad) — sigue en verde, sin cambios. | ✅ 6 tests, en verde |
| 3 | `ScanResultScreen` (guardar/compartir) — no cubierto con tests unitarios (funciones `private` a nivel de archivo Compose, requeriría refactor a una capa de datos separada para testear sin Robolectric/instrumentación). | Pendiente si se justifica el refactor. |
| 4 | `ScanImageEditorTest` (RF-SCAN-06/07, ver §8) — matriz de color identidad sin ajustes, contraste máximo/mínimo escala cada canal, brillo positivo/negativo desplaza sin tocar el factor, `scaledDimensions` al 100/50/25% (sin llegar a cero). `ScanImageEditor.applyAdjustments()` en sí no cubierto (usa `Bitmap`/`Canvas`/`ColorMatrixColorFilter` de `android.graphics`, mismo límite ya documentado para `CompressPdfUseCase`). | ✅ 8 tests, en verde |

`detectQrContentType`/`QrContentType` se cambiaron de `private` a `internal`
dentro de `QrScreen.kt` específicamente para poder testearlos desde un
archivo de test del mismo módulo, sin exponerlos como API pública de la app.

---

## 7. Preguntas abiertas

| Pregunta | Notas |
|---|---|
| ¿Vale la pena construir un paso de edición propio (brillo/contraste/escala) después del resultado de ML Kit? | **Resuelto 2026-08-29** — sí, implementado. Ver §8. |
| ¿El modo "Foto" (`SCANNER_MODE_BASE`) necesita opciones adicionales respecto al modo "Documento" (`SCANNER_MODE_FULL`)? | No evaluado en esta pasada — ambos usan el mismo flujo de `ScanResultScreen` después de capturar, y desde RF-SCAN-06/07 (ver §8) ambos generan el PDF con el conversor propio en vez del de ML Kit. |
| El nombre por defecto del PDF generado ya no usa `scan_result_default_name_prefix` ("Escaneo_...") sino el de `ConverterViewModel` ("DocuSmart_..."), porque ambos modos pasan ahora por el conversor propio (ver §8). ¿Vale la pena unificarlo? | Inconsistencia cosmética preexistente al cambio de flujo (la ruta de imágenes ya usaba el nombre del conversor desde antes de esta sesión) — no afecta ninguna HU, señalada para una pasada de pulido futura. |

---

## 8. RF-SCAN-06/07/HU-SCAN-03 — Brillo, contraste y reescalado (2026-08-29)

Cierra el backlog completo de este módulo y el último RF pendiente de todo
el proyecto, en la misma pasada en la que el usuario pidió continuar con
las HU de todo el proyecto (ver también `visor-biblioteca.md` §15,
`study.md` §9 y `settings-premium.md` §12).

### Hallazgo real durante la implementación: la ruta de imágenes nunca se usaba
Antes de escribir el editor, se verificó en dispositivo que `ScannerScreen.kt`
pedía a ML Kit **tanto** `RESULT_FORMAT_JPEG` como `RESULT_FORMAT_PDF` en
los dos modos ("Documento" y "Foto"), y el listener del resultado
**siempre prefería el PDF cuando estaba disponible** — y como se pidió
`RESULT_FORMAT_PDF` incondicionalmente, ese PDF *siempre* está disponible,
sin importar el modo. Esto significa que la rama que usa las páginas como
imágenes individuales (la única donde tiene sentido editar brillo/
contraste/escala **antes** de generar el archivo final) era código muerto:
nunca se ejecutaba en la práctica. Implementar el editor sin corregir esto
habría dejado la funcionalidad completa, probada, pero inalcanzable desde
la UI real.

**Decisión (confirmada con el usuario):** dejar de pedir
`RESULT_FORMAT_PDF` por completo. ML Kit ahora solo hace lo que ninguna
otra pieza de la app puede hacer (corrección de perspectiva, recorte,
mejora automática de la captura) y siempre devuelve páginas como
imágenes; el PDF final lo arma el conversor propio de DocuSmart
(`ConvertImageToPdfUseCase`, ya usado por Conversión), que es justo el
paso donde ahora se puede editar cada página antes de generarlo. Esto
cambia el comportamiento de HU-SCAN-01 (antes usaba el PDF armado
internamente por ML Kit en modo "Documento"; ahora usa el del conversor
propio en ambos modos) — cambio deliberado y necesario, no un efecto
secundario no controlado.

### `ScanImageEditor.kt` (nuevo, `features/scanner/domain/`)
- `applyAdjustments(sourceUri, brightness, contrast, scalePercent): Uri?` —
  carga el bitmap original, reescala con `Bitmap.createScaledBitmap()` si
  `scalePercent < 100`, aplica brillo/contraste dibujando sobre un canvas
  con `Paint().colorFilter = ColorMatrixColorFilter(...)`, comprime a JPEG
  en `cacheDir/scanner_edits/` y devuelve la nueva `Uri` vía `FileProvider`
  (misma autoridad `${packageName}.fileprovider` ya usada en el resto de
  la app — ver el bug de autoridad incorrecta corregido en `study.md` §9).
- **`buildColorMatrix(brightness, contrast): FloatArray`** (función pura,
  sin ninguna clase de `android.graphics`) — matriz 4x5 en el mismo
  formato que `android.graphics.ColorMatrix` **y**
  `androidx.compose.ui.graphics.ColorMatrix`, reutilizada tal cual para la
  vista previa en vivo del diálogo de edición y para el "bake" final sobre
  el bitmap real — lo que el usuario ve en el slider es exactamente lo que
  queda guardado. Contraste (-100..100) es un factor de escala anclado al
  gris medio (128); brillo (-100..100) es un desplazamiento aditivo por
  canal.
- **`scaledDimensions(width, height, scalePercent): Pair<Int, Int>`**
  (función pura) — nuevas dimensiones sin llegar nunca a 0px.

### UI (`ScanResultScreen.kt`, modificado)
- Lista de páginas ahora es `editableUris` (estado local, no el parámetro
  `scannedUris` original) — cada página editada reemplaza su URI por la
  del archivo ya ajustado, sin tocar las demás.
- Cada miniatura (`ScanPageThumbnail`, extraído por `LongMethod` de
  detekt) tiene un ícono de edición (`Tune`) superpuesto en la esquina.
- `ScanImageEditorDialog` (nuevo) — diálogo de pantalla completa con la
  imagen (vista previa en vivo vía `ColorFilter.colorMatrix`), 2
  `Slider` (brillo/contraste, -100..100) y 4 `FilterChip` de escala
  (100/75/50/25%). "Aplicar" llama a `ScanImageEditorViewModel`
  (nuevo, envuelve `ScanImageEditor` solo para poder obtenerlo con
  `hiltViewModel()` desde el Composable) y reemplaza la URI de esa página
  en `editableUris`; "Cancelar" descarta los cambios sin tocar nada.
- **detekt:** `ScanResultScreen` superó `LongMethod` (150 líneas) al
  agregar el editor — extraído en 4 composables (`ScanPreviewSection`,
  `ScanFilenameField`, `ScanResultActions` + `ScanResultActionsState`,
  `ScanPageThumbnail`) en vez de baselinear el hallazgo. `AccentColor`-like
  agrupación de parámetros no hizo falta aquí (los estados nuevos ya se
  agruparon desde el diseño inicial).

### `ScannerScreen.kt` (modificado)
Ver la sección de "Hallazgo real" arriba — se quitó
`RESULT_FORMAT_PDF` de `GmsDocumentScannerOptions` y se simplificó el
listener del resultado para solo manejar páginas (ya no hay rama de PDF
directo).

**8 tests nuevos** (`ScanImageEditorTest`) — ver fila 4 de §6.

**Verificado end-to-end en el dispositivo real (app en español), con una
página real fotografiada durante la sesión de pruebas (no un fixture
sintético):** escaneado un documento en modo "Documento" → confirmado que
`ScanResultScreen` mostró "Generar PDF"/"Escanear de nuevo" (la ruta de
imágenes, no la de PDF directo — confirma que el cambio de flujo
funcionó) → tocado el ícono de edición de la única página → el diálogo
abrió con Brillo/Contraste en 0 y Escala en 100% → movidos ambos
sliders a valores altos (64/64) y confirmada la actualización en vivo de
la vista previa (imagen visiblemente más clara/lavada) → ajustados a
valores moderados (-12/-12) con escala 50% → tocado "Aplicar" → la
miniatura en `ScanResultScreen` se actualizó con el resultado ajustado →
tocado "Generar PDF" → conversión exitosa (botones cambiaron a "Guardar
en Descargas"/"Compartir PDF", confirma AC4) → tocado "Guardar en
Descargas" → confirmado "Guardado en Descargas" en pantalla y el archivo
real (`DocuSmart_20260829_194224.pdf`) presente en
`/sdcard/Download/` vía `adb shell find`.
- Verificado también: `testDebugUnitTest`/`detekt`/`lintDebug` en verde.
