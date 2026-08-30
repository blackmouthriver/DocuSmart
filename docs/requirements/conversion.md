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
**Verificación de hallazgos de QA pendientes, 2026-08-29:** el banner de
publicidad sí se muestra (solo tenía un retraso normal de carga de AdMob,
no reproducido como bug — ver §7 de `CONTEXT.md`/tabla de bugs abajo); "vista
en carrusel se ve vacía" **sí era un bug real** — `ImagePickerSection.kt`
(con la miniatura en carrusel) existía en el código pero nunca se usaba;
todas las conversiones, incluidas las de imagen, mostraban un picker
genérico solo de texto (`ConversionDetailCard`). Corregido agregando un
carrusel real de miniaturas para conversiones con origen Imagen y
eliminando el archivo muerto.
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
- **RF-CONV-07** ✅ El sistema debe soportar `.doc` legado (formato binario OLE2, pre-2007) en las 3 conversiones de Word (a PDF, texto y HTML), no solo `.docx` — ver §4 HU-CONV-05.
- **RF-CONV-08** ✅ El sistema debe permitir seleccionar varios archivos a la vez para una misma conversión y producir una salida independiente por cada uno ("N archivos → N salidas") — ver §4 HU-CONV-06.
- **RF-CONV-09** ✅ PDF → Word debe preservar negrita, cursiva, tamaño de fuente y párrafos reales (no solo texto plano línea por línea) — ver §4 HU-CONV-07.

### Backlog — no implementado
*(vacío — no quedan RF pendientes en este módulo)*

---

## 3. Requerimientos no funcionales

- **RNF-CONV-01 (BMP real):** `IMAGE_TO_BMP` genera un archivo `.bmp` cuyo contenido real es PNG (`Bitmap.compress` de Android no soporta el formato BMP verdadero) — decisión pragmática documentada, no un bug. La mayoría de lectores de imagen detectan el formato real por sus bytes de cabecera y lo abren igual, pero herramientas que validan la extensión estrictamente podrían rechazarlo.
- **RNF-CONV-02 (conversiones "a PDF" son texto plano, no facsímil visual):** Word→PDF, Excel→PDF y PowerPoint→PDF extraen el contenido textual y lo componen como un documento de texto simple con iText7 — no reproducen el diseño visual original (fuentes, colores, imágenes embebidas, layout de tablas complejo). Esto es consistente en las 3 herramientas, no una limitación de una sola.
- **RNF-CONV-03 (PPT→PDF no usa POI para renderizar):** Apache POI puede renderizar diapositivas a imagen (`XSLFSlide.draw(Graphics2D)`), pero esa ruta depende de `java.awt.Graphics2D`/`BufferedImage`, que no existen en el runtime de Android. Por eso `PptToPdfUseCase` reutiliza el parseo de texto por XML crudo de `PptToTextUseCase` en vez de intentar renderizar.
- **RNF-CONV-04 (dependencias de Apache POI):** `poi`, `poi-ooxml` y `poi-scratchpad` **deben** incluir `org.apache.xmlbeans` en el classpath de compilación y del APK — sin xmlbeans, cualquier uso del modelo de objetos OOXML de POI (`XWPFDocument`, `WorkbookFactory`, `XSSFWorkbook`) falla en tiempo de ejecución con `NoClassDefFoundError`, incluso para solo *leer* un documento existente. Ver bug corregido en §5.
- **RNF-CONV-05 (detección de encabezado en `.doc` depende del idioma del documento):** a diferencia de `.docx` (donde `w:styleId` es un identificador interno siempre en inglés, sin importar el idioma de la UI de Word), en `.doc` (HWPF) el nombre de estilo que expone la API pública de Apache POI (`StyleDescription.name`) es el nombre **visible**, guardado en el idioma con el que se creó el documento — un "Heading 1" creado con Word en español se llama "Título 1". El formato binario sí guarda un identificador numérico independiente del idioma (`sti`), pero POI no lo expone públicamente. `WordToHtmlUseCase` (que es la única de las 3 conversiones de `.doc` que distingue encabezados) reconoce los nombres de encabezado/título en los 5 idiomas que la app ya soporta (es/en/de/pt/ru — ver `isHeadingStyleName()` en `WordFormatDetection.kt`); un `.doc` creado con Word en otro idioma no tendrá sus encabezados detectados y esos párrafos se renderizan como texto normal — degradado, no roto.
- **RNF-CONV-06 (el lote cuenta contra el límite diario por archivo, no por lote):** convertir un lote de 3 archivos consume 3 conversiones del límite diario de usuarios free (`DailyLimitManager`, 5/día), no 1 — de lo contrario un "lote" sería una forma trivial de saltarse el límite. Si el límite se alcanza a mitad de un lote, los archivos restantes se marcan como `Error` (sin intentar convertirlos) en vez de detener todo el lote o dejarlo a medias sin explicación.
- **RNF-CONV-07 (el lote no aplica a IMAGE_TO_PDF):** IMAGE_TO_PDF con varios archivos sigue siendo "fusionar N imágenes en UN solo PDF" (comportamiento preexistente, muy usado) — el modo lote ("N archivos → N salidas") solo aplica al resto de conversiones cuando hay más de un archivo elegido. Mismo picker multi-selección para ambos casos; la diferencia es de comportamiento en `ConverterViewModel.convert()`, no de UI de selección.
- **RNF-CONV-08 (PDF→Word no reconstruye el layout visual, ni la detección de párrafos es exacta):** `PdfToWordUseCase` preserva negrita/cursiva/tamaño de fuente por fragmento de texto real y separa párrafos según el espaciado vertical entre líneas — pero sigue sin reproducir imágenes embebidas, tablas, columnas ni la posición exacta del texto (mismo alcance que RNF-CONV-02 para el resto del módulo, aplicado ahora con más fidelidad de estilo). La detección de párrafos es una heurística (gap vertical > 1.6x el tamaño de fuente = párrafo nuevo) verificada contra un PDF real exportado desde Word: acertó el corte entre una oración con negrita inline y un párrafo completo en cursiva, pero no siempre detecta el límite entre dos párrafos consecutivos con interlineado normal si el segundo usa una fuente mucho más grande (el gap real termina siendo menor que el umbral calculado con el tamaño de fuente más grande) — en ese caso el texto queda unido en el mismo párrafo de Word en vez de separado, degradado pero no roto.

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

### HU-CONV-05 — Soporte para `.doc` legado (RF-CONV-07)
**Como** usuario que aún conserva documentos Word antiguos (formato binario
pre-2007),
**quiero** poder convertirlos igual que un `.docx`,
**para** no depender de tener el archivo re-guardado en formato moderno.

- **AC1** Dado que selecciono "Word → PDF/Texto/HTML" y elijo un `.doc`
  real (formato binario OLE2, no `.docx` renombrado), cuando confirmo,
  entonces la conversión termina en éxito con el contenido real del
  documento (párrafos y texto de celdas de tabla), igual que con un `.docx`.
- **AC2** Dado que el formato real del archivo no coincide con su extensión
  (ni OOXML ni OLE2 reconocible), cuando confirmo, entonces veo un mensaje
  de error, no un crash.
- **AC3** (best-effort, ver RNF-CONV-05) Dado que el `.doc` fue creado con
  Word en español, inglés, alemán, portugués o ruso y usa el estilo
  "Heading"/"Título"/"Überschrift"/"Заголовок" (o "Title"/"Titel"/"Название"),
  cuando convierto a HTML, entonces ese párrafo se renderiza como encabezado
  (`<h2>`), no como párrafo normal.

**Decisión de diseño — detección de formato por firma binaria, no por
extensión:** `XWPFDocument` (OOXML/`.docx`) y `HWPFDocument` (OLE2/`.doc`,
módulo `poi-scratchpad`, ya declarado como dependencia) no comparten
interfaz común — a diferencia de `WorkbookFactory` para Excel, que sí
detecta y abstrae `.xls`/`.xlsx` automáticamente. `WordFormatDetection.kt`
usa `FileMagic.valueOf()` (mira los primeros bytes del archivo, no el
nombre) para decidir cuál API usar *antes* de leer — necesario porque
`XWPFDocument` lanza `NotOfficeXmlFileException` al recibir un `.doc` real.

**Decisión de alcance — sin pasada de tablas separada para `.doc`:** a
diferencia de la ruta `.docx` (que sí separa celdas de tabla con `" | "`
vía `XWPFDocument.tables`), la extracción de `.doc` no repite una segunda
pasada de tablas — el rango plano de `HWPFDocument` (`Range.getParagraph`)
ya incluye el texto de las celdas como párrafos normales en su posición
real del documento; agregar una iteración de tablas aparte duplicaría ese
contenido. El texto de una tabla en un `.doc` convertido queda legible como
líneas sueltas, sin separadores de columna — mismo nivel de fidelidad ya
aceptado para el resto del módulo (RNF-CONV-02).

**Fixture de prueba real, no sintético:** a diferencia de `.docx`
(`XWPFDocument()` crea un documento en blanco en memoria con una sola
línea de código), Apache POI no ofrece una API para crear un `.doc` desde
cero — `HWPFDocument` solo permite *leer* un binario OLE2 ya existente. Se
generó `app/src/test/resources/fixtures/legacy-sample.doc` con Microsoft
Word real vía automatización COM de PowerShell (`New-Object -ComObject
Word.Application`, `SaveAs` con `wdFormatDocument97`), en vez de un byte
array con solo la firma OLE2 — permite probar la extracción de contenido
real (párrafos, tabla, estilo de encabezado), no solo la detección de
formato. El mismo archivo se usó para la verificación en dispositivo.

**Verificado en dispositivo (2026-08-29):** las 3 conversiones probadas
manualmente con el `.doc` real (`legacy-sample.doc`, subido a
`Descargas`): "Word → PDF" y "Word → HTML" confirmadas end-to-end
extrayendo el archivo resultante del dispositivo y verificando su
contenido — título, párrafos y las 4 celdas de la tabla presentes en
ambos, y el encabezado correctamente renderizado como `<h2>` en el HTML
pese a que el estilo real en el archivo es "Título 1" (español), no
"Heading 1". "Word → Texto" no se repitió manualmente porque comparte
exactamente la misma función de extracción (`extractLegacyDocBlocks()`),
ya cubierta por test con el mismo fixture real.

**Fuera de alcance — inconsistencia de MIME type en el selector de
archivo:** `getMimeForType()` en `ConverterScreen.kt` usa
`"application/msword"` (MIME real de `.doc`) como filtro único para las 3
conversiones de Word, incluyendo cuando el usuario en realidad va a elegir
un `.docx` (cuyo MIME correcto es
`application/vnd.openxmlformats-officedocument.wordprocessingml.document`).
En la práctica el selector de Android (`DocumentsUI`) es permisivo y
sigue mostrando archivos `.docx` igual (verificado en dispositivo), así
que no es un bug bloqueante, pero es una inconsistencia real preexistente
que no se corrigió en esta HU por no ser parte de su alcance (soportar
`.doc`, no arreglar el filtro del picker).

### HU-CONV-06 — Conversión por lotes (RF-CONV-08)
**Como** usuario que necesita convertir varios documentos del mismo tipo,
**quiero** elegirlos todos de una vez,
**para** no repetir manualmente el mismo flujo de conversión archivo por
archivo.

- **AC1** Dado que elijo un tipo de conversión (que no sea Imagen→PDF) y
  selecciono más de un archivo, cuando confirmo, entonces obtengo una
  salida independiente por cada archivo, cada una con el nombre original
  del archivo de entrada (sin la extensión original) — no un solo archivo
  fusionado.
- **AC2** Dado que uno de los archivos del lote falla (formato inválido,
  sin texto extraíble, etc.), cuando termina la conversión, entonces veo
  el resultado de CADA archivo por separado (éxito o el mensaje de error
  puntual) — un archivo roto no oculta ni cancela el resultado de los
  demás.
- **AC3** Dado que elijo Imagen→PDF con varios archivos, cuando confirmo,
  entonces se sigue fusionando todo en un solo PDF (comportamiento
  existente, sin cambios) — el modo lote no aplica a este tipo.
- **AC4** Dado que dos archivos del lote comparten el mismo nombre original
  (ej. dos "informe.docx" de carpetas distintas), cuando se convierten,
  entonces el segundo no sobrescribe la salida del primero (se le agrega
  " (2)", " (3)", etc.).
- **AC5** Dado que el lote llega al límite diario de conversiones a mitad
  de proceso, cuando termina, entonces los archivos restantes aparecen
  marcados como error (límite alcanzado) sin haber intentado convertirlos
  — cada archivo del lote cuenta individualmente contra el límite, no el
  lote como una sola unidad (ver RNF-CONV-06).

**Decisión de diseño — mismo selector multi-archivo para todos los tipos:**
antes de esta HU, `ActivityResultContracts.GetMultipleContents()` (picker
que permite elegir varios) solo se usaba para IMAGE_TO_PDF; el resto de
conversiones usaba `GetContent()` (un solo archivo) — si el usuario elegía
varios archivos igual para otro tipo, los demás se perdían en silencio. Se
unificó a un solo `fileLauncher` multi-selección para las 17 conversiones
(el picker del sistema permite elegir uno solo igual), y `convert()`
decide entre "fusionar" (solo IMAGE_TO_PDF) o "lote" (todo lo demás, si
hay más de un archivo) según el tipo — ver `ConverterViewModel.kt`.

**Bug real encontrado durante la verificación en dispositivo de esta HU
(preexistente, no introducido por el lote):** `PdfToTextUseCase` llamaba
`pdfDoc.close()` y DESPUÉS volvía a leer `pdfDoc.numberOfPages` para
construir el `ConversionResult.Success` — iText7 invalida el documento al
cerrarlo, así que esa lectura lanzaba
`PdfException: Document was closed. It is impossible to execute action.`
**Esto significa que la conversión "PDF → Texto" fallaba siempre, en TODO
caso, no solo en lotes** — nadie lo había detectado porque no existía
`PdfToTextUseCaseTest.kt`. Corregido guardando `pageCount` en una variable
local antes de cerrar el documento. De paso, se cambió el nombre de
archivo de caché de `PdfToTextUseCase` (antes fijo: `"temp_text.pdf"`,
frágil ante llamadas repetidas dentro del mismo lote) a uno único por
llamada vía `File.createTempFile()`. Se agregó `PdfToTextUseCaseTest.kt`
(4 tests) con un PDF real generado con iText7, incluyendo un test que
verifica que dos llamadas seguidas no interfieren entre sí. **Hallazgo
adicional, fuera de alcance de esta HU y reportado por separado:** el
mensaje de error "el PDF no contiene texto extraíble" nunca se dispara en
la práctica porque el use case agrega un encabezado `"=== Página N ==="`
a cada página incondicionalmente antes de comprobar si el texto está en
blanco.

**Verificado en dispositivo (2026-08-29):** lote real de 2 PDFs
(`OcrTest.pdf`, `FormTest.pdf`) convertidos a Texto usando el selector
multi-archivo del sistema (mantener presionado + tocar para agregar a la
selección) — resultado "2 de 2 archivos convertidos", cada fila mostrando
`OcrTest.pdf → OcrTest.txt` / `FormTest.pdf → FormTest.txt` (nombre
original preservado), contador "Conversiones hoy: 2 / 5" (cada archivo
del lote contó individualmente), y "Guardar todos en Descargas" guardando
ambos `.txt` reales verificados por `adb pull`.

### HU-CONV-07 — PDF → Word preserva negrita, cursiva, tamaño y párrafos reales (RF-CONV-09)
**Como** usuario que convierte un PDF a Word para seguir editándolo,
**quiero** que conserve al menos el formato básico del texto (negrita,
cursiva, tamaño de fuente) y los párrafos reales del documento,
**para** no tener que re-aplicar el formato manualmente sobre un bloque de
texto plano.

- **AC1** Dado que convierto un PDF con una palabra en negrita en medio de
  una oración, cuando abro el `.docx` resultante, entonces esa palabra
  aparece en negrita — no todo el párrafo plano.
- **AC2** Dado que convierto un PDF con un párrafo completo en cursiva,
  cuando abro el `.docx`, entonces ese párrafo aparece en cursiva.
- **AC3** Dado que convierto un PDF con texto en un tamaño de fuente
  distinto al resto (ej. un título más grande), cuando abro el `.docx`,
  entonces ese texto conserva un tamaño de fuente proporcionalmente mayor.
- **AC4** Dado que el PDF tiene líneas que se ajustan dentro del mismo
  párrafo (espaciado normal entre líneas) y luego un salto mayor hacia el
  siguiente párrafo, cuando se convierte, entonces las líneas ajustadas
  quedan en un solo párrafo de Word y el salto mayor genera un párrafo
  nuevo — no un párrafo por cada línea del PDF.

**Decisión de diseño — de texto plano a fragmentos con estilo real:** la
versión anterior extraía todo el texto de la página con
`PdfTextExtractor.getTextFromPage()` (una sola cadena, sin información de
estilo) y lo volcaba en un `.docx` mínimo escrito a mano por ZIP (un
`<w:p>` por línea, sin `<w:rPr>`). Se reemplazó por un recorrido con
`PdfCanvasProcessor` + un `IEventListener` propio que escucha
`EventType.RENDER_TEXT`: cada evento (`TextRenderInfo`) trae el fragmento
de texto tal como el PDF lo dibujó (ya separado por el propio documento
en los puntos donde cambia la fuente/estilo), su tamaño de fuente real y
la fuente (`PdfFont.fontProgram.fontNames`, con `isBold()`/`isItalic()`
basados en los flags `macStyle` de la fuente incrustada — con respaldo
adicional buscando "bold"/"italic"/"oblique" en el nombre de la fuente
para fuentes que no declaran esos flags correctamente). El `.docx` se
genera con `XWPFDocument` (modelo de objetos real de Apache POI, con
`XWPFRun.isBold/isItalic/fontSize` por fragmento) en vez del ZIP mínimo
anterior — más robusto además de necesario para soportar formato por
fragmento.

**Decisión de diseño — párrafos por espaciado vertical real, no por línea
de PDF:** un PDF no tiene el concepto de "párrafo" en su modelo de datos
—cada línea es solo texto posicionado en coordenadas X/Y—, así que separar
un párrafo por línea (como hacía la versión anterior) produce un párrafo
nuevo por cada línea visual, incluso dentro de una misma oración que
simplemente se ajustó al ancho de la página. Se implementó una heurística
basada en la coordenada Y de la línea base de cada fragmento: un salto
vertical mayor a 1.6x el tamaño de fuente del fragmento siguiente se
interpreta como fin de párrafo (interlineado extra / línea en blanco); un
salto menor es un simple ajuste de línea dentro del mismo párrafo lógico.
Detalle de la limitación de esta heurística en RNF-CONV-08.

**Verificado en dispositivo (2026-08-29) con un PDF real, no sintético:**
se generó un PDF exportando directamente desde Microsoft Word
(`formatted-sample.pdf`, vía automatización COM de PowerShell — mismo
mecanismo que `legacy-sample.doc` de RF-CONV-07) con una oración con una
palabra en negrita, un párrafo completo en cursiva y una línea en tamaño
20pt. Convertido a Word en la app y verificado extrayendo el
`word/document.xml` del `.docx` resultante: la palabra "negrita" es la
única marcada `<w:b w:val="on"/>` dentro de su oración (el resto en
`"off"`), el párrafo de cursiva completo tiene `<w:i w:val="on"/>` en
todos sus fragmentos, y el texto de tamaño 20pt tiene `<w:sz w:val="40"/>`
(unidades de medio punto) — formato real preservado por fragmento, no una
etiqueta global por documento.

---

## 5. Bugs de QA a corregir (trazabilidad)

| Bug (barrido de pruebas v1.0 / mejoras pendientes v1.0.1) | HU que lo cubre | Estado |
|---|---|---|
| "Muy pocas opciones de conversión por formato (2-3 cuando el requerimiento pide más)" | — | **Obsoleto en cuanto a cantidad** — hay 17 combinaciones ya declaradas y visibles en el menú (`ConversionType.kt`), no 2-3. El problema real no era la cantidad de opciones sino que 3 de ellas producían el formato equivocado y 2 más (ya "implementadas") fallaban al ejecutarse — ver bugs reales abajo. |
| **Bug real encontrado hoy (no reportado en la QA, más grave que lo buscado):** "Word → PDF" y "Excel → PDF" — ya marcadas como implementadas — fallaban con `NoClassDefFoundError: org/apache/xmlbeans/XmlException` al leer un documento real, porque `app/build.gradle.kts` excluía `org.apache.xmlbeans` de las 3 dependencias de Apache POI. Verificado con un test que lee un .docx real construido a mano: falla sin xmlbeans, funciona al quitar la exclusión. | HU-CONV-04 | ✅ Corregido — se quitó `exclude(group = "org.apache.xmlbeans")` de `poi`, `poi-ooxml` y `poi-scratchpad`. Se agregaron reglas ProGuard (`-keep`) para `org.apache.xmlbeans.**` y `org.openxmlformats.schemas.**` para que el build de release no las elimine por no detectar su uso (XmlBeans las carga por reflexión). Verificado que `assembleDebug` y `checkDebugDuplicateClasses` siguen en verde con xmlbeans incluido — la exclusión no evitaba ningún conflicto real. |
| "Word → Texto" entregaba un PDF (enrutado a `wordToPdf` en `ConverterViewModel.convert()`) | HU-CONV-01 | ✅ Corregido — nuevo `WordToTextUseCase`, enrutado correctamente. |
| "Excel → CSV" entregaba un PDF (enrutado a `excelToPdf`) | HU-CONV-02 | ✅ Corregido — nuevo `ExcelToCsvUseCase`, enrutado correctamente. |
| "PPT → PDF" fallaba siempre (enrutado a `wordToPdf`, que no puede leer .pptx) | HU-CONV-03 | ✅ Corregido — nuevo `PptToPdfUseCase`, enrutado correctamente. |
| "Banner de publicidad no se visualiza en esta pantalla" | — | **No reproducido** (verificado visualmente en dispositivo 2026-08-29) — el banner sí carga y se muestra; el hallazgo original probablemente capturó el estado justo antes de que AdMob terminara de cargar (mismo retraso de red que cualquier banner de AdMob, no específico de esta pantalla). |
| "Vista en carrusel se ve vacía — sugerido grilla/lista" | — | ✅ **Bug real confirmado y corregido 2026-08-29** — `ImagePickerSection.kt` (con el carrusel de miniaturas) existía en el código pero no se usaba en ningún lado; el picker real (`ConversionDetailCard`) solo mostraba "N archivo(s) seleccionado(s)" en texto, sin ninguna imagen. Se agregó `SelectedImagesCarousel` (miniaturas reales + botón eliminar) para conversiones con origen Imagen, y se eliminó el archivo muerto. |

---

## 6. Plan de pruebas unitarias

| # | Cobertura | Estado |
|---|---|---|
| 1 | `WordToTextUseCaseTest` — extrae párrafos a .txt, documento sin texto → Error, archivo no legible → Error. | ✅ 3 tests, en verde |
| 2 | `ExcelToCsvUseCaseTest` — filas/columnas a CSV, escape de comas/comillas (RFC 4180), hoja vacía → Error. | ✅ 3 tests, en verde |
| 3 | `PptToPdfUseCaseTest` — texto de cada diapositiva en el PDF resultante (verificado leyendo el PDF de vuelta con iText7), presentación sin texto → Error, archivo no legible → Error. Usa un .pptx construido a mano (ZIP + XML mínimo), no un mock del contenido. | ✅ 3 tests, en verde |
| 4 | `WordFormatDetectionTest` — detecta OOXML/OLE2/UNKNOWN por firma binaria; extrae párrafos y detecta encabezado de un `.doc` legado **real** (`legacy-sample.doc`); extrae texto de tabla sin duplicarlo. | ✅ 5 tests, en verde |
| 5 | `WordToPdfUseCaseTest`, `WordToTextUseCaseTest`, `WordToHtmlUseCaseTest` — cada una agrega un caso con el `.doc` real, verificando que el contenido (título, párrafos, celdas) llega al archivo de salida sin romper el camino `.docx` existente. | ✅ en verde |
| 6 | `PdfToTextUseCaseTest` (nuevo, no existía) — extrae texto real de un PDF generado con iText7, cuenta páginas correctamente, dos llamadas seguidas no interfieren entre sí, archivo no legible → Error. Cubre el bug real corregido en HU-CONV-06 (documento cerrado antes de leer `numberOfPages`). | ✅ 4 tests, en verde |
| 7 | `ConverterViewModelBatchTest` (nuevo) — el lote produce un resultado por archivo (no fusión), IMAGE_TO_PDF con varios archivos sigue fusionando (regresión), nombres duplicados se desambiguan, corte por límite diario a mitad de lote. | ✅ 4 tests, en verde |
| 8 | `PdfToWordUseCaseTest` (nuevo, no existía) — líneas con poco espacio quedan en el mismo párrafo y un salto grande crea uno nuevo (PDF armado con coordenadas Y controladas), negrita/cursiva/tamaño de fuente preservados por fragmento real, PDF sin texto → Error, archivo no legible → Error. | ✅ 4 tests, en verde |

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
- Verificado en verde: `testDebugUnitTest`/`detekt`/`lintDebug` y
  `connectedDebugAndroidTest` (6 pruebas) en el dispositivo real, una vez
  reconectado el cable USB.

---

## 9. Carrusel de miniaturas para conversiones de imagen (2026-08-29)

Cierra el hallazgo de QA "vista en carrusel se ve vacía", verificado como
bug real al retomar la lista de hallazgos pendientes de todo el proyecto.

- **Causa raíz:** `ImagePickerSection.kt` (`features/converter/presentation/components/`)
  ya tenía una implementación completa de carrusel de miniaturas (`LazyRow`
  + `AsyncImage` + botón eliminar por imagen), pero **nunca se usaba en
  ningún lugar del código** — quedó como código muerto desde que se
  escribió. El picker realmente activo para todas las conversiones,
  incluidas las de imagen, es `ConversionDetailCard`, que solo muestra un
  ícono de carpeta + texto ("N archivo(s) seleccionado(s)") sin ninguna
  vista previa visual.
- **Corregido** agregando `SelectedImagesCarousel` (un `LazyRow` de
  miniaturas de 84dp con botón "✕" de eliminar por imagen, reutilizando
  `onRemoveImage` que `ConverterViewModel` ya exponía) dentro de
  `ConversionDetailCard`, visible solo cuando `type.fromFormat == "Imagen"`
  y hay al menos un archivo seleccionado — Word/Excel/PDF/PowerPoint siguen
  mostrando solo el ícono genérico, donde una miniatura de imagen no
  aportaría nada (no son archivos de imagen).
- `ImagePickerSection.kt` se eliminó por completo (código muerto real, no
  una funcionalidad a futuro) en vez de dejarlo sin usar al lado del
  carrusel nuevo.
- **Verificado en dispositivo real:** seleccionadas 2 imágenes para
  "Imagen → PDF" → confirmado que las miniaturas reales aparecen debajo
  del picker (antes solo se veía el texto "2 archivo(s)
  seleccionado(s)") → confirmado el botón "✕" visible por miniatura.
- Sin tests nuevos: cambio de UI puro (un `LazyRow` con `AsyncImage`, sin
  lógica de negocio nueva) — mismo criterio ya aplicado a otros cambios
  puramente visuales del proyecto.
- Verificado también: `testDebugUnitTest`/`detekt`/`lintDebug` en verde.

---

## 10. Preguntas abiertas

| Pregunta | Notas |
|---|---|
| Banner de anuncios y vista en carrusel — ¿siguen reproduciéndose en la versión actual? | **Resuelto 2026-08-29** — banner no reproducido (solo retraso de carga normal); carrusel sí era un bug real, corregido. Ver tabla de bugs arriba. |
