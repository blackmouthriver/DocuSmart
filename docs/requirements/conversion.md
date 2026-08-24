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

## 7. Preguntas abiertas

| Pregunta | Notas |
|---|---|
| ¿Vale la pena soportar `.doc` legado (RF-CONV-07)? | Cada vez menos usuarios tienen archivos `.doc` reales (formato pre-2007); evaluar si el esfuerzo de agregar `HWPFDocument` se justifica. |
| ¿Conversión por lotes (RF-CONV-08) antes o después de ampliar el backlog de Herramientas PDF? | Depende de qué módulo prioriza el usuario. |
| Banner de anuncios y vista en carrusel — ¿siguen reproduciéndose en la versión actual? | Requieren prueba manual en dispositivo/emulador; no se pudieron confirmar ni descartar solo leyendo el código. |
