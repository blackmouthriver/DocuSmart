# Módulo: Conversión de documentos

> Parte de [`CONTEXT.md`](../../CONTEXT.md). Cubre las 17 combinaciones de
> conversión ya declaradas en `ConversionType.kt` (imagen↔pdf/jpg/png/webp/bmp,
> pdf→img/texto/word/html, word→pdf/texto/html, excel→pdf/csv/html, ppt→pdf/texto).

**Estado:** un bug de configuración crítico corregido — la exclusión de
`org.apache.xmlbeans` en `app/build.gradle.kts` rompía en silencio **toda**
conversión que usara el modelo de objetos OOXML de Apache POI (`XWPFDocument`,
`WorkbookFactory`), incluyendo dos conversiones que ya estaban "implementadas"
(Word→PDF, Excel→PDF) y que en realidad fallaban con `NoClassDefFoundError`
al intentar leer un .docx/.xlsx real. Además, 3 opciones de conversión
declaradas en el menú estaban enrutadas al use case equivocado (entregaban
un formato de salida distinto al que el usuario eligió). 12 tests nuevos.
**Código relacionado:** `features/converter/**`, `app/build.gradle.kts`
(dependencias de Apache POI), `app/proguard-rules.pro`.

---

## 1. Alcance

17 tipos de conversión ya declarados y con opción visible en el menú
(`ConversionType.kt`), agrupados en 5 categorías: Imagen, PDF, Word, Excel,
PowerPoint. No hay combinaciones "faltantes" en el sentido de opciones no
mostradas — el problema real encontrado era que varias opciones mostradas
producían el archivo equivocado o fallaban al ejecutarse.

---

## 2. Requerimientos funcionales

- **RF-CONV-01** El sistema debe convertir imágenes entre JPG/PNG/WebP/BMP y a PDF (5 combinaciones, ya funcionando).
- **RF-CONV-02** El sistema debe convertir PDF a imagen, texto, Word y HTML (4 combinaciones).
- **RF-CONV-03** El sistema debe convertir Word (.docx) a PDF, texto y HTML.
- **RF-CONV-04** El sistema debe convertir Excel (.xlsx/.xls) a PDF, CSV y HTML.
- **RF-CONV-05** El sistema debe convertir PowerPoint (.pptx) a PDF y texto.
- **RF-CONV-06** Cada conversión debe producir un archivo con la extensión y el contenido correspondientes al formato de salida elegido — no el de otra conversión.

### Backlog — no implementado
- **RF-CONV-07** Soporte para `.doc` legado (formato binario OLE2, pre-2007) en las conversiones de Word — hoy solo `.docx` funciona porque `XWPFDocument` (Apache POI) es específico de OOXML; `.doc` requeriría el módulo `poi-scratchpad`/`HWPFDocument`, con un parser distinto.
- **RF-CONV-08** Conversión por lotes (varios archivos a la vez) — mencionado como mejora sugerida en `CONTEXT.md`.
- **RF-CONV-09** PDF → Word que preserve formato/diseño (hoy `PdfToWordUseCase` extrae solo texto plano, sin tablas ni estilos).

---

## 3. Requerimientos no funcionales

- **RNF-CONV-01 (BMP real):** `IMAGE_TO_BMP` genera un archivo `.bmp` cuyo contenido real es PNG (`Bitmap.compress` de Android no soporta el formato BMP verdadero) — decisión pragmática documentada, no un bug. La mayoría de lectores de imagen detectan el formato real por sus bytes de cabecera y lo abren igual, pero herramientas que validan la extensión estrictamente podrían rechazarlo.
- **RNF-CONV-02 (conversiones "a PDF" son texto plano, no facsímil visual):** Word→PDF, Excel→PDF y PowerPoint→PDF extraen el contenido textual y lo componen como un documento de texto simple con iText7 — no reproducen el diseño visual original (fuentes, colores, imágenes embebidas, layout de tablas complejo). Esto es consistente en las 3 herramientas, no una limitación de una sola.
- **RNF-CONV-03 (PPT→PDF no usa POI para renderizar):** Apache POI puede renderizar diapositivas a imagen (`XSLFSlide.draw(Graphics2D)`), pero esa ruta depende de `java.awt.Graphics2D`/`BufferedImage`, que no existen en el runtime de Android. Por eso `PptToPdfUseCase` reutiliza el parseo de texto por XML crudo de `PptToTextUseCase` en vez de intentar renderizar.
- **RNF-CONV-04 (dependencias de Apache POI):** `poi`, `poi-ooxml` y `poi-scratchpad` **deben** incluir `org.apache.xmlbeans` en el classpath de compilación y del APK — sin xmlbeans, cualquier uso del modelo de objetos OOXML de POI (`XWPFDocument`, `WorkbookFactory`, `XSSFWorkbook`) falla en tiempo de ejecución con `NoClassDefFoundError`, incluso para solo *leer* un documento existente. Ver bug corregido en §5.

---

## 4. Historias de usuario con criterios de aceptación

### HU-CONV-01 — Word → Texto entrega un .txt real
**Como** usuario que solo necesita el contenido textual de un Word,
**quiero** convertirlo a un archivo de texto plano,
**para** copiarlo, buscarlo o abrirlo en cualquier editor simple.

- **AC1** Dado que selecciono "Word → Texto" con un .docx válido, cuando confirmo, entonces obtengo un archivo `.txt` con el texto de los párrafos y tablas del documento.
- **AC2** Dado que el documento no tiene texto extraíble, cuando confirmo, entonces veo un mensaje de error, no un archivo vacío.

*(Corrige bug real: "Word → Texto" estaba enrutado a `WordToPdfUseCase` — el usuario recibía un `.pdf`, no un `.txt`, sin ningún aviso de que el formato no coincidía con lo elegido.)*

### HU-CONV-02 — Excel → CSV entrega un .csv real
**Como** usuario que necesita importar datos de una hoja de cálculo a otra herramienta,
**quiero** convertirla a CSV,
**para** que sea compatible con cualquier programa que lea CSV estándar.

- **AC1** Dado que selecciono "Excel → CSV" con un .xlsx válido, cuando confirmo, entonces obtengo un archivo `.csv` con los datos de la primera hoja, separados por comas.
- **AC2** Dado que una celda contiene una coma, comilla o salto de línea, cuando se genera el CSV, entonces ese valor queda correctamente escapado entre comillas dobles (estándar CSV/RFC 4180).
- **AC3** Dado que la hoja no tiene datos, cuando confirmo, entonces veo un mensaje de error.

*(Corrige bug real: "Excel → CSV" estaba enrutado a `ExcelToPdfUseCase` — mismo problema que HU-CONV-01.)*

### HU-CONV-03 — PowerPoint → PDF funciona
**Como** usuario con una presentación,
**quiero** convertirla a PDF,
**para** compartirla con quien no tenga PowerPoint.

- **AC1** Dado que selecciono "PPT → PDF" con un .pptx válido, cuando confirmo, entonces obtengo un PDF con el texto de cada diapositiva en una página separada — la conversión **no falla**.
- **AC2** Dado que la presentación no tiene texto, cuando confirmo, entonces veo un mensaje de error.

*(Corrige bug real: "PPT → PDF" estaba enrutado a `WordToPdfUseCase`, que usa `XWPFDocument` — específico para .docx. Alimentarlo con un .pptx real lanzaba una excepción de formato inválido; la conversión siempre fallaba.)*

### HU-CONV-04 — Word/Excel a PDF no fallan al leer un documento real
*(Corrige un bug pre-existente, no reportado en la QA — ver §5.)*

**Como** usuario que convierte un Word o Excel real a PDF,
**quiero** que la conversión funcione,
**para** poder confiar en que las opciones del menú realmente sirven.

- **AC1** Dado que selecciono "Word → PDF" con un .docx real, cuando confirmo, entonces la conversión termina en éxito, no con `NoClassDefFoundError`.
- **AC2** Dado que selecciono "Excel → PDF" con un .xlsx real, cuando confirmo, entonces la conversión termina en éxito, no con `NoClassDefFoundError`.

---

## 5. Bugs de QA a corregir (trazabilidad)

| Bug (barrido de pruebas v1.0 / mejoras pendientes v1.0.1) | HU que lo cubre | Estado |
|---|---|---|
| "Muy pocas opciones de conversión por formato (2-3 cuando el requerimiento pide más)" | — | **Obsoleto en cuanto a cantidad** — hay 17 combinaciones ya declaradas y visibles en el menú (`ConversionType.kt`), no 2-3. El problema real no era la cantidad de opciones sino que 3 de ellas producían el formato equivocado y 2 más (ya "implementadas") fallaban al ejecutarse — ver bugs reales abajo. |
| **Bug real encontrado hoy (no reportado en la QA, más grave que lo buscado):** "Word → PDF" y "Excel → PDF" — ya marcadas como implementadas — fallaban con `NoClassDefFoundError: org/apache/xmlbeans/XmlException` al leer un documento real, porque `app/build.gradle.kts` excluía `org.apache.xmlbeans` de las 3 dependencias de Apache POI. Verificado con un test que lee un .docx real construido a mano: falla sin xmlbeans, funciona al quitar la exclusión. | HU-CONV-04 | ✅ Corregido — se quitó `exclude(group = "org.apache.xmlbeans")` de `poi`, `poi-ooxml` y `poi-scratchpad`. Se agregaron reglas ProGuard (`-keep`) para `org.apache.xmlbeans.**` y `org.openxmlformats.schemas.**` para que el build de release no las elimine por no detectar su uso (XmlBeans las carga por reflexión). Verificado que `assembleDebug` y `checkDebugDuplicateClasses` siguen en verde con xmlbeans incluido — la exclusión no evitaba ningún conflicto real. |
| "Word → Texto" entregaba un PDF (enrutado a `wordToPdf` en `ConverterViewModel.convert()`) | HU-CONV-01 | ✅ Corregido — nuevo `WordToTextUseCase`, enrutado correctamente. |
| "Excel → CSV" entregaba un PDF (enrutado a `excelToPdf`) | HU-CONV-02 | ✅ Corregido — nuevo `ExcelToCsvUseCase`, enrutado correctamente. |
| "PPT → PDF" fallaba siempre (enrutado a `wordToPdf`, que no puede leer .pptx) | HU-CONV-03 | ✅ Corregido — nuevo `PptToPdfUseCase`, enrutado correctamente. |
| "Banner de publicidad no se visualiza en esta pantalla" | — | No reproducido por lectura de código — el banner está conectado igual que en Home/Biblioteca/Herramientas PDF (mismo `AdConstants` de prueba, mismo componente `DocuSmartBannerAd`), que sí funcionan. Requiere verificación visual en dispositivo/emulador. |
| "Vista en carrusel se ve vacía — sugerido grilla/lista" | — | No verificado — requiere prueba visual, no se pudo confirmar ni descartar por lectura de código. |

---

## 6. Plan de pruebas unitarias

| # | Cobertura | Estado |
|---|---|---|
| 1 | `WordToTextUseCaseTest` — extrae párrafos a .txt, documento sin texto → Error, archivo no legible → Error. | ✅ 3 tests, en verde |
| 2 | `ExcelToCsvUseCaseTest` — filas/columnas a CSV, escape de comas/comillas (RFC 4180), hoja vacía → Error. | ✅ 3 tests, en verde |
| 3 | `PptToPdfUseCaseTest` — texto de cada diapositiva en el PDF resultante (verificado leyendo el PDF de vuelta con iText7), presentación sin texto → Error, archivo no legible → Error. Usa un .pptx construido a mano (ZIP + XML mínimo), no un mock del contenido. | ✅ 3 tests, en verde |

Los tests de Word/Excel generan documentos reales con Apache POI en memoria
(no mocks del contenido) — esto es precisamente lo que hizo evidente el bug
de xmlbeans: los tests fallaban con `NoClassDefFoundError` hasta corregir la
dependencia, igual que hubiera fallado la app real.

---

## 7. Compose UI Testing — flujo #2: conversión (2026-08-25)

Segunda prueba de Compose UI del proyecto (ver también
[`visor-biblioteca.md` §9](visor-biblioteca.md#9-compose-ui-testing--flujo-1-abrir-documento-2026-08-25),
flujo #1 "abrir documento"). Misma infraestructura: JUnit4/`createAndroidComposeRule`,
instrumentada contra dispositivo real, sin Hilt (ViewModel construido a mano con `mockk`).

- **`ConverterScreenTest`** cubre el flujo #2 del manual de marca ("abrir o
  convertir un documento en menos de 3 toques"): elegir Imagen→WebP, tocar
  "Convertir a WebP" y ver el mensaje de éxito. A propósito **no mockea**
  `ImageFormatUseCase` — usa la instancia real, para dar protección de
  regresión de verdad sobre el crash de WEBP_LOSSLESS en Android 8-10
  corregido antes en esta sesión (mockearlo habría probado el ViewModel,
  pero no el bug real).
- El selector de archivos del sistema es un proceso externo, no conducible
  por Compose UI Testing: se simula llamando a `onFilesSelected()` directo
  con la URI de una imagen real de prueba (mismo principio que
  `ViewerScreenTest` resolviendo `documentId` sin `ContentResolver` real).
  El resto del flujo (elegir tipo, tocar "Convertir", ver el resultado) sí
  se conduce por la UI real.
- **Hallazgo real al construir la prueba:** `onTypeSelected()` limpia
  `selectedFiles` a propósito (coincide con el flujo real: primero se
  elige el formato, después se abre el picker) — el test debe llamar
  `onTypeSelected()` antes de `onFilesSelected()`, no al revés.
- **Hallazgo de i18n — corregido (2026-08-26):** tanto `ConversionType.label`
  (ej. `"Imagen → WebP"`) como todo el texto de `ConversionSuccess.kt`
  estaban en español fijo, sin pasar por `stringResource()` — mismo patrón
  de bug que ya se corrigió en las 7 pantallas listadas en `CONTEXT.md` §2
  "i18n", pero estos dos casos habían quedado fuera de esa limpieza porque
  están en un modelo de dominio y un componente compartido, no en una
  pantalla completa. Detalle completo del fix en la sección "i18n del
  Convertidor" más abajo. El test sigue usando el literal español porque
  `ConverterScreenTest` fuerza `es-ES` con `forceLocale()` (ver
  `security.md` §7), así que el texto renderizado sigue siendo el mismo.
- **Bug de entorno de prueba encontrado y corregido (no del código de la
  app):** al correr la suite completa de `connectedDebugAndroidTest` (los
  dos flujos juntos, no cada uno filtrado por separado), ambas pruebas de
  Compose fallaban de forma intermitente con
  `IllegalStateException: No compose hierarchies found in the app`. Causa:
  las animaciones del dispositivo real (`window_animation_scale`,
  `transition_animation_scale`, `animator_duration_scale`) estaban en
  `1.0`; Google documenta esto como causa conocida de inestabilidad al
  correr varias pruebas de Compose/Espresso seguidas en un dispositivo
  físico. Corregido con `adb shell settings put global <escala> 0` en las
  tres. Suite completa (4 pruebas) verificada en verde varias veces
  seguidas tras el cambio.

---

## 8. i18n del Convertidor (2026-08-26)

Dos hallazgos de i18n encontrados durante Compose UI Testing (§7), ambos
corregidos.

- **`ConversionType.label`** — campo del enum con 17 valores hardcodeados
  en español (`"Imagen → WebP"`, `"PDF → Word"`, etc.), usado en 2
  pantallas (`ConverterScreen.kt`). Un enum de dominio no puede llamar
  `stringResource()` en su constructor (no hay `@Composable` scope al
  cargar la clase), así que en vez de guardar el texto final se guardan
  claves internas fijas (`fromFormat`/`toFormat`, ej. `"Imagen"`, `"PDF"`,
  `"WebP"`) — las mismas que ya usaba `getFormatStyle()` para elegir
  color/ícono, sin tocar. El campo `label` se eliminó del todo (quedaba
  muerto en cuanto se localizó `fromFormat`/`toFormat` en el punto de uso).
  De esas claves, **solo `"Imagen"` es una palabra real que necesita
  traducción** — el resto (`PDF`, `Word`, `Excel`, `PowerPoint`, `WebP`,
  `JPG`, `PNG`, `BMP`, `TXT`, `HTML`, `CSV`) son nombres propios/
  abreviaturas de formato, iguales en los 5 idiomas del proyecto. Nuevo
  recurso `format_name_image` + helpers `@Composable` en
  `ConverterScreen.kt` (`localizedFromFormat()`/`localizedToFormat()`/
  `localizedLabel()`) que traducen solo esa palabra y dejan todo lo demás
  igual. También corregidos 2 sitios que ya llamaban `stringResource()`
  pero pasándole el valor crudo sin localizar
  (`converter_select_files`/`converter_to_format` recibiendo
  `type.fromFormat`/`type.toFormat` directo — "Convertir a Imagen" nunca
  se traducía, aunque la plantilla sí).
- **`ConversionSuccess.kt`** — 6 strings en español fijo (título, conteo de
  páginas/tamaño, confirmación de guardado, 3 botones). Nuevos recursos:
  `converter_success_title`, `converter_success_page_count_size`,
  `converter_saved_to_downloads`, `converter_share`,
  `converter_convert_another`; `converter_save` ya existía y solo hacía
  falta usarlo ahí (estaba duplicado como literal).
  **Bug funcional encontrado de paso, más grave que el de i18n:**
  `ConversionSuccess` es el componente de éxito de **las 17** conversiones
  (no solo PDF/imagen — se ve desde `ConverterScreen.kt:126`, un solo
  punto de uso para todos los `ConversionResult.Success`), pero el ícono
  (`Icons.Rounded.PictureAsPdf` fijo), el MIME type al compartir
  (`"application/pdf"` fijo) y el texto de los botones ("Compartir PDF",
  "Convertir otra imagen") asumían siempre PDF/imagen — mostrando ícono y
  MIME equivocados, y compartiendo con el MIME type incorrecto, para las
  ~12 conversiones que no producen PDF ni imagen (Excel→CSV, Word→HTML,
  PPT→Texto, etc.). Corregido derivando ícono/color y MIME type de la
  extensión real de `result.outputFile` (`formatIconForExtension()` +
  `MimeTypeMap`), y generalizando el texto de los botones
  ("Compartir archivo"/"Convertir otro archivo", ya no específico a
  PDF/imagen).
- Paridad de claves verificada tras el cambio: las 5 versiones de
  `strings.xml` (es/en/de/pt/ru) siguen teniendo exactamente el mismo
  conjunto de nombres de recurso.
- **Sin verificar en el dispositivo real** — el cable USB se desconectó a
  mitad de sesión y no volvió a reconectar; verificado solo con
  `testDebugUnitTest`/`detekt`/`lintDebug`/`compileDebugAndroidTestKotlin`
  en verde (compila limpio, incluyendo el source set `androidTest`).
  Pendiente correr `connectedDebugAndroidTest` en el dispositivo real (o
  esperar al próximo push a `main`, que ya lo corre en CI) antes de dar
  esto por completamente cerrado.

---

## 9. Preguntas abiertas

| Pregunta | Notas |
|---|---|
| ¿Vale la pena soportar `.doc` legado (RF-CONV-07)? | Cada vez menos usuarios tienen archivos `.doc` reales (formato pre-2007); evaluar si el esfuerzo de agregar `HWPFDocument` se justifica. |
| ¿Conversión por lotes (RF-CONV-08) antes o después de ampliar el backlog de Herramientas PDF? | Depende de qué módulo prioriza el usuario. |
| Banner de anuncios y vista en carrusel — ¿siguen reproduciéndose en la versión actual? | Requieren prueba manual en dispositivo/emulador; no se pudieron confirmar ni descartar solo leyendo el código. |
