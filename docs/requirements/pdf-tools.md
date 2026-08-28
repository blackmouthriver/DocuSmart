# Módulo: Herramientas PDF

> Parte de [`CONTEXT.md`](../../CONTEXT.md). Cubre: Unir, Dividir, Comprimir y
> Rotar PDF. Proteger/quitar contraseña de PDF ya está formalizado en
> [`security.md`](security.md) (RF-SEC-10/11/12, HU-SEC-07/08) — no se repite aquí.

**Estado:** bug de arquitectura crítico corregido (Unir y Rotar rasterizaban
cada página a imagen, destruyendo el texto seleccionable); "Dividir no
funciona" de la QA de mayo confirmado como **obsoleto** mediante tests reales
sobre PDFs generados con iText7; 8 tests unitarios nuevos, todos en verde.
**i18n de las 4 pantallas completado 2026-08-28 (ver §9)** — ya no queda
texto en español fijo en este módulo. **RF-PDF-06 (Numerar páginas)
implementado 2026-08-28, ver §10** — primera funcionalidad nueva del
backlog. **RF-PDF-07 (Marca de agua) implementado 2026-08-28, ver §11.**
**RF-PDF-08 (Reordenar/eliminar páginas) implementado 2026-08-28, ver §12**
— cierra las 3 funcionalidades de prioridad "alta" del backlog. **RF-PDF-13
(Comparar dos PDFs) implementado 2026-08-28, ver §13** — primera de las 2
funcionalidades de prioridad "media". **RF-PDF-14 (Censurar contenido)
implementado 2026-08-28, ver §14** — cierra las 2 funcionalidades de
prioridad "media". **RF-PDF-09 (Recortar márgenes) implementado
2026-08-28, ver §15** — primera funcionalidad de prioridad "baja/futuro".
Pendiente: RF-PDF-10 a RF-PDF-12 y RF-PDF-15, y selección de archivo desde
la Biblioteca de la app (ver §5).
**Código relacionado:** `features/pdftools/**`.

---

## 1. Alcance

Cuatro operaciones sobre PDF ya construidas, más un backlog amplio pedido en
el requerimiento #3 original que todavía no existe:

1. **Unir** — combinar 2+ PDFs en uno solo, en el orden seleccionado.
2. **Dividir** — extraer un rango de páginas a un PDF nuevo.
3. **Comprimir** — reducir el tamaño del archivo.
4. **Rotar** — rotar todas las páginas 90°/180°/270°.
5. **Numerar páginas** — pie de página con formato configurable (número solo,
   número/total, o "Página X de N").
6. **Marca de agua** — texto diagonal y semitransparente sobre todas las
   páginas.
7. **Reordenar páginas** — vista de miniaturas arrastrable para cambiar el
   orden y/o eliminar páginas individuales.
8. **Comparar PDFs** — compara el texto de cada página de dos PDFs y genera
   un reporte con las diferencias encontradas (páginas que solo existen en
   uno de los dos documentos, líneas de texto presentes en uno pero no en
   el otro).
9. **Censurar contenido** — el usuario dibuja rectángulos sobre las zonas
   sensibles de cada página; el contenido dentro de esas zonas se elimina
   de forma irreversible del PDF (no un rectángulo negro visual encima).
10. **Recortar páginas** — quita un margen uniforme (0–40%) de los 4 lados
    de cada página, con vista previa en vivo del resultado.
11. *(Backlog, no implementado)* editar contenido, firma digital,
    formularios, OCR avanzado.

---

## 2. Requerimientos funcionales

### Implementados
- **RF-PDF-01** El sistema debe permitir unir 2 o más PDFs en uno solo, conservando el contenido original de cada página (texto/vectores, no una captura de pantalla de la página).
- **RF-PDF-02** El sistema debe permitir extraer un rango de páginas (`desde`–`hasta`) de un PDF a un archivo nuevo, sin alterar el PDF original.
- **RF-PDF-03** El sistema debe permitir comprimir un PDF con un nivel de calidad seleccionable (20–100), informando el tamaño antes/después y el porcentaje de reducción.
- **RF-PDF-04** El sistema debe permitir rotar todas las páginas de un PDF 90°, 180° o 270°, conservando el contenido original (texto/vectores).
- **RF-PDF-05** Tras cualquier operación exitosa, el sistema debe mostrar el nombre y tamaño del archivo resultante, y ofrecer guardarlo en Descargas o compartirlo directamente.
- **RF-PDF-06** Numerar páginas (pie de página con número, formato configurable). **✅ Implementado 2026-08-28, ver §10.**
- **RF-PDF-07** Marca de agua de texto sobre todas las páginas. **✅ Implementado 2026-08-28, ver §11.**
- **RF-PDF-08** Reordenar y/o eliminar páginas individuales (vista de miniaturas arrastrable). **✅ Implementado 2026-08-28, ver §12.**
- **RF-PDF-13** Comparar dos versiones de un PDF y resaltar diferencias. **✅ Implementado 2026-08-28, ver §13.**
- **RF-PDF-14** Censurar (redactar) contenido sensible de forma irreversible. **✅ Implementado 2026-08-28, ver §14.**
- **RF-PDF-09** Recortar (crop) márgenes de página. **✅ Implementado 2026-08-28, ver §15.**

### Backlog — nuevas funcionalidades (mejoras sugeridas 2026-08-24, no implementadas)
- **RF-PDF-10** Edición básica de contenido (texto/imágenes existentes).
- **RF-PDF-11** Firma digital de PDF.
- **RF-PDF-12** Detección y relleno de formularios PDF.
- **RF-PDF-15** OCR avanzado sobre PDFs escaneados (texto ya buscable vía Modo Estudio para imágenes sueltas; falta aplicado a PDF completo).

Prioridad sugerida dentro del backlog restante (esfuerzo vs. valor percibido)
— **las 3 funcionalidades de prioridad "alta", las 2 de "media" y la
primera de "baja/futuro" ya están implementadas** (RF-PDF-06/07/08/13/14/09):
quedan RF-PDF-10, RF-PDF-11, RF-PDF-12, RF-PDF-15 (requieren más superficie
de UI o licenciamiento adicional de iText7 para firma/formularios
avanzados).

---

## 3. Requerimientos no funcionales

- **RNF-PDF-01 (preservar contenido vectorial):** Unir, Dividir, Rotar, Numerar páginas, Marca de agua y Reordenar páginas deben operar sobre el PDF a nivel de página (iText7 `copyPagesTo`/`setRotation`/`PdfCanvas`), nunca rasterizando a imagen — el texto debe seguir siendo seleccionable y buscable en el resultado. **✅ Cumplido** para las 6 (Unir y Rotar migrados desde un enfoque de bitmap que lo violaba; Numerar páginas y Marca de agua escriben su texto directamente sobre la página como texto real; Reordenar páginas reutiliza `copyPagesTo` igual que Unir, solo que página por página en el orden final deseado — las miniaturas que se ven en la UI sí son bitmaps vía `PdfRenderer`, pero eso es únicamente la vista previa, no el archivo generado). Comprimir es la única excepción deliberada: reducir tamaño de forma significativa requiere recodificar imágenes/rasterizar, así que se acepta perder texto seleccionable en esa operación específica. Comparar PDFs queda fuera del alcance de este RNF por naturaleza: no modifica ni copia contenido de los PDFs originales, solo lee su texto (`PdfTextExtractor`, igual que Modo Estudio/Buscar) y **genera un documento nuevo** (el reporte) — no hay "contenido vectorial que preservar" porque no hay página original que reescribir. Censurar contenido es un caso especial en sentido inverso: **debe destruir** deliberadamente el contenido vectorial dentro de las zonas marcadas (ver RF-PDF-14/RNF-PDF-06) — el resto de la página fuera de esas zonas sí conserva su texto/vectores intactos. Recortar páginas cumple este RNF igual que Rotar: solo ajusta `MediaBox`/`CropBox` (metadatos de tamaño de página), nunca toca el content stream — el texto sigue siendo el mismo texto real, solo cambia qué porción de la página es visible.
- **RNF-PDF-02 (nombre de archivo consistente):** todo archivo generado por Herramientas PDF debe llevar el prefijo `DocuSmart_` seguido de un nombre descriptivo y timestamp. **✅ Cumplido** en las 10 herramientas.
- **RNF-PDF-03 (no bloquear UI):** toda operación debe ejecutarse en `Dispatchers.IO`, nunca en el hilo principal. **✅ Ya cumplido.**
- **RNF-PDF-04 (mensajes de error):** los mensajes no deben filtrar rutas de archivo completas ni detalles internos de excepciones (mismo lineamiento que RNF-SEC-05).
- **RNF-PDF-05 (feedback tras operación exitosa):** nombre, tamaño y opciones de guardar/compartir deben mostrarse siempre, sin pasos adicionales. **✅ Ya cumplido** (`ToolSuccessCard`, compartido por las 4 herramientas).
- **RNF-PDF-06 (censura genuinamente irreversible):** el contenido dentro de una zona censurada debe quedar eliminado del content stream del PDF, no solo cubierto visualmente — un rectángulo negro dibujado encima de texto que sigue siendo seleccionable/extraíble **no** cumple este requerimiento. **✅ Cumplido** (ver §14) vía el módulo `pdfCleanup` de iText7 (`PdfCleaner.cleanUp`), verificado con `PdfTextExtractor` tanto en tests unitarios como en el dispositivo real: el texto dentro de la zona censurada deja de aparecer en la extracción, mientras el resto de la página se conserva intacto.

---

## 4. Historias de usuario con criterios de aceptación

### HU-PDF-01 — Unir varios PDFs
**Como** usuario con varios documentos relacionados,
**quiero** combinarlos en un solo PDF,
**para** compartirlos u organizarlos como una sola unidad.

- **AC1** Dado que selecciono 2 o más PDFs, cuando confirmo unir, entonces obtengo un solo PDF cuyo total de páginas es la suma de las páginas de cada archivo de origen.
- **AC2** Dado que el PDF resultante se abre en cualquier lector, cuando reviso el contenido, entonces el texto original sigue siendo seleccionable (no es una imagen de la página).
- **AC3** Dado que selecciono menos de 2 PDFs, cuando intento unir, entonces veo "Selecciona al menos 2 PDFs para unir" y no se genera ningún archivo.

*(Corrige deuda técnica: la versión anterior rasterizaba cada página a bitmap, perdiendo todo el texto seleccionable del PDF resultante.)*

### HU-PDF-02 — Dividir un PDF por rango de páginas
**Como** usuario que solo necesita una parte de un PDF largo,
**quiero** extraer un rango de páginas,
**para** compartir solo lo relevante sin el documento completo.

- **AC1** Dado que selecciono un PDF y un rango "desde–hasta", cuando confirmo dividir, entonces obtengo un PDF nuevo que contiene **solo** esas páginas, no el documento completo.
- **AC2** Dado que el rango pedido excede el total de páginas del PDF, cuando confirmo, entonces el sistema ajusta el rango al máximo disponible en vez de fallar.
- **AC3** Dado que el archivo original tenía N páginas, cuando reviso el archivo original tras dividir, entonces sigue intacto con sus N páginas (dividir nunca modifica el original).

*(Verificado en 2026-08-24 con tests reales sobre PDFs multi-página: el hallazgo de QA "genera el mismo PDF sin dividir" no se reprodujo contra el código actual — se marca como obsoleto, ver §6.)*

### HU-PDF-03 — Comprimir un PDF
**Como** usuario con un PDF pesado,
**quiero** reducir su tamaño,
**para** poder compartirlo más fácilmente (por ejemplo, por correo o WhatsApp).

- **AC1** Dado que selecciono un PDF y un nivel de calidad, cuando confirmo comprimir, entonces veo el tamaño antes y después, y el porcentaje de reducción.
- **AC2** Dado que el resultado comprimido termina pesando más que el original (PDF ya optimizado), cuando esto ocurre, entonces el sistema informa "El PDF ya está optimizado" en vez de entregar un archivo más pesado como si fuera una mejora.
- **AC3** Dado que la compresión terminó, cuando reviso la pantalla, entonces veo las opciones de guardar en Descargas o compartir, igual que las otras 3 herramientas.

*(El hallazgo de QA "no indica dónde se guarda ni ofrece compartir" no se reprodujo: `ToolSuccessCard` ya cubre nombre, tamaño y ambas acciones para las 4 herramientas — se marca como obsoleto, ver §6.)*

### HU-PDF-04 — Rotar un PDF
**Como** usuario con un PDF escaneado en la orientación incorrecta,
**quiero** rotarlo,
**para** que se lea correctamente sin girar el dispositivo.

- **AC1** Dado que selecciono un PDF y un ángulo (90°/180°/270°), cuando confirmo rotar, entonces todas las páginas del resultado quedan rotadas exactamente ese ángulo.
- **AC2** Dado que el PDF ya tenía una rotación previa (por ejemplo, escaneado con metadato `/Rotate`), cuando aplico una nueva rotación, entonces el ángulo se acumula correctamente sobre el existente (ej. 180° + 270° = 90°, no 450°).
- **AC3** Dado que el resultado se abre en cualquier lector, cuando reviso el contenido, entonces el texto sigue siendo seleccionable (no es una imagen rotada).

*(Corrige deuda técnica: la versión anterior rasterizaba cada página y aplicaba la rotación con una matriz manual sobre el bitmap; ahora usa `PdfPage.setRotation()`, el metadato estándar de PDF que cualquier lector respeta de forma nativa.)*

### HU-PDF-05 — Numerar páginas
*(Implementado 2026-08-28 — ver §10.)*

**Como** usuario que va a imprimir o distribuir un PDF largo,
**quiero** agregar numeración automática,
**para** que sea fácil referenciar páginas específicas.

- **AC1** Dado que activo "Numerar páginas", cuando confirmo, entonces cada página del resultado muestra su número en el pie, en el formato "Página X de N".
- **AC2 (ampliado, no pedido explícitamente por la HU original pero implementado como "formato configurable" de RF-PDF-06)** Dado que el formato de numeración es configurable, cuando elijo "Número" o "Núm. / Total" en vez del formato por defecto, entonces el pie de página usa ese formato en las N páginas.

### HU-PDF-06 — Marca de agua
*(Implementado 2026-08-28 — ver §11.)*

**Como** usuario que comparte un borrador o documento confidencial,
**quiero** superponer un texto de marca de agua,
**para** dejar claro el estado o la propiedad del documento.

- **AC1** Dado que escribo un texto de marca de agua y confirmo, cuando se genera el resultado, entonces el texto aparece superpuesto (diagonal, semitransparente) en todas las páginas.
- **AC2 (implícito, mismo criterio que RF-PDF-04 AC3)** Dado que el resultado se abre en cualquier lector, cuando reviso el contenido, entonces el texto de la marca de agua y el contenido original siguen siendo texto real, no una imagen superpuesta.
- **AC3 (validación de entrada)** Dado que intento aplicar la marca de agua sin escribir texto, cuando confirmo, entonces el botón permanece deshabilitado (o, a nivel de use case, se devuelve un error) y no se genera ningún archivo.

### HU-PDF-07 — Reordenar y eliminar páginas
*(Implementado 2026-08-28 — ver §12.)*

**Como** usuario que necesita ajustar el orden de un PDF,
**quiero** ver miniaturas de las páginas y arrastrarlas o eliminarlas,
**para** corregir el documento sin herramientas externas.

- **AC1** Dado que abro la vista de miniaturas de un PDF, cuando arrastro una página a otra posición, entonces el PDF resultante refleja el nuevo orden.
- **AC2** Dado que marco una página para eliminar, cuando confirmo, entonces el resultado no la incluye.
- **AC3 (protección contra vaciar el PDF)** Dado que solo queda una página en la lista, cuando intento eliminarla, entonces el botón de eliminar queda deshabilitado — el resultado siempre debe conservar al menos una página.

### HU-PDF-08 — Comparar dos PDFs
*(Implementado 2026-08-28 — ver §13. HU redactada de nuevo, no existía en el
backlog original de HU-PDF-01 a 07 porque RF-PDF-13 se agregó después como
mejora sugerida — ver §2.)*

**Como** usuario que recibió una nueva versión de un documento,
**quiero** comparar dos PDFs y ver en qué páginas cambiaron,
**para** no tener que leer el documento completo de nuevo buscando qué se modificó.

- **AC1** Dado que selecciono un Documento A y un Documento B, cuando confirmo comparar, entonces obtengo un reporte en PDF que indica cuántas páginas tienen diferencias sobre el total.
- **AC2** Dado que una página tiene texto distinto entre A y B, cuando reviso el reporte, entonces veo, para esa página, qué líneas aparecen solo en A y cuáles solo en B.
- **AC3** Dado que uno de los dos documentos tiene más páginas que el otro, cuando reviso el reporte, entonces las páginas que no tienen contraparte se marcan explícitamente como existentes solo en A o solo en B, en vez de compararse contra "nada" silenciosamente.
- **AC4** Dado que los dos documentos son idénticos, cuando confirmo comparar, entonces el resultado indica "Los dos documentos son idénticos" en vez de un reporte vacío sin explicación.

### HU-PDF-09 — Censurar contenido sensible
*(Implementado 2026-08-28 — ver §14. HU redactada de nuevo, mismo motivo que
HU-PDF-08: RF-PDF-14 se agregó después como mejora sugerida — ver §2.)*

**Como** usuario que va a compartir un documento con información sensible,
**quiero** eliminar de forma permanente el texto o las imágenes de ciertas
zonas antes de compartirlo,
**para** evitar que esa información sea recuperable por quien reciba el archivo.

- **AC1** Dado que dibujo un rectángulo sobre una zona de una página, cuando confirmo censurar, entonces el contenido dentro de esa zona ya no aparece visualmente en el PDF resultante.
- **AC2 (la más importante — irreversibilidad real)** Dado que el PDF censurado se abre en cualquier lector, cuando intento seleccionar o extraer el texto de la zona censurada, entonces ese texto ya no existe en el documento — no es un rectángulo negro superpuesto a texto que sigue ahí debajo.
- **AC3** Dado que marco zonas en varias páginas de un mismo PDF, cuando confirmo censurar, entonces todas esas zonas quedan eliminadas en una sola operación sobre el archivo de salida.
- **AC4** Dado que no he marcado ninguna zona, cuando intento censurar, entonces el botón permanece deshabilitado (o, a nivel de use case, se devuelve un error) y no se genera ningún archivo.

### HU-PDF-10 — Recortar el margen de un PDF
*(Implementado 2026-08-28 — ver §15. HU redactada de nuevo, mismo motivo
que HU-PDF-08/09: RF-PDF-09 se agregó después como mejora sugerida —
ver §2.)*

**Como** usuario con un PDF escaneado con márgenes blancos excesivos,
**quiero** recortar un porcentaje uniforme de margen en cada página,
**para** aprovechar mejor la pantalla al leerlo y reducir espacio desperdiciado.

- **AC1** Dado que ajusto el control de margen y veo la vista previa, cuando el valor cambia, entonces la vista previa refleja en vivo cuánto se recortaría de la página.
- **AC2** Dado que confirmo recortar con un margen del N%, cuando reviso el PDF resultante, entonces cada página es más pequeña en esa proporción y el contenido de texto original se conserva (no se pierde ni se rasteriza).
- **AC3 (protección contra un margen inválido)** Dado que intento fijar un margen mayor al máximo permitido, cuando confirmo, entonces el sistema lo ajusta al máximo (40%) en vez de generar un rectángulo de página inválido o vacío.

---

## 5. Deuda técnica y pendientes fuera de HU

- **i18n:** ✅ Completado 2026-08-28, ver §9. Ya no queda español fijo en este módulo.
- **Selector de archivo:** las 10 herramientas solo permiten elegir un PDF desde el selector del dispositivo (SAF), no desde la Biblioteca de la app — mismo gap que tenía Seguridad antes de corregirse (RF-SEC-04/HU-SEC-04 AC3).
- **Compresión con pérdida de texto:** aceptado como trade-off deliberado (RNF-PDF-01) — una futura mejora de calidad/no indispensable sería ofrecer un modo "conservar texto" que solo recomprima imágenes embebidas en vez de rasterizar la página completa, pero requiere más trabajo con la API de iText7 y no está en el alcance de esta refinación.

---

## 6. Bugs de QA a corregir (trazabilidad)

| Bug (barrido de pruebas v1.0 / mejoras pendientes v1.0.1) | HU que lo cubre | Estado |
|---|---|---|
| Unir/Rotar producían un PDF válido pero con **todo el texto convertido a imagen** (no seleccionable, no buscable) — no reportado explícitamente en la QA de mayo, encontrado al auditar el código antes de escribir tests. | HU-PDF-01, HU-PDF-04 | ✅ Corregido — reescritos con iText7 (`copyPagesTo`, `setRotation`) en vez de `android.graphics.pdf.PdfRenderer`/bitmap. |
| "Dividir PDF no funciona — genera el mismo PDF sin dividir" | HU-PDF-02 | **Obsoleto** — 4 tests con PDFs reales de 3 y 5 páginas confirman que el rango extraído es correcto contra el código actual (`SplitPdfUseCase.kt`). No se reprodujo el bug; se documenta por si reaparece en un caso no cubierto por los tests. |
| "Comprimir no indica dónde queda guardado, no ofrece compartir/descargar" | HU-PDF-03 | **Obsoleto** — `ToolSuccessCard` ya muestra nombre, tamaño y ambas acciones para las 4 herramientas. |
| "Rotar: la vista previa no refleja la rotación real en grados" | HU-PDF-04 | Mitigado indirectamente — al migrar la rotación real a `setRotation()` (metadato estándar de PDF), cualquier discrepancia posible del cálculo manual de matriz de bitmap deja de existir en el archivo final. La vista previa de `RotatePdfScreen.kt` sigue usando su propio cálculo de bitmap con `Matrix().postRotate()` para mostrar el ángulo antes de procesar — consistente con el resultado real, pero no se migró a leer el PDF ya rotado por no ser indispensable para la corrección del archivo generado. |
| Nombre de archivo antepone "DocuSmart_" automáticamente (confirmar si es deseado) | RNF-PDF-02 | Resuelto como decisión de producto: se mantiene y se estandarizó en las 4 herramientas (antes solo 2 de 4 lo tenían) — es branding consistente, no un bug. |
| Faltan: contraseña, quitar contraseña, eliminar página, reordenar, firma, recorte, marca de agua, numeración, editar, formularios, comparar, censurar | Contraseña/quitar contraseña → `security.md` (ya implementado). Numeración → RF-PDF-06 (ya implementado, ver §10). Marca de agua → RF-PDF-07 (ya implementado, ver §11). Eliminar página/reordenar → RF-PDF-08 (ya implementado, ver §12). Comparar → RF-PDF-13 (ya implementado, ver §13). Censurar → RF-PDF-14 (ya implementado, ver §14). Recorte → RF-PDF-09 (ya implementado, ver §15). El resto → RF-PDF-10 a RF-PDF-12 y RF-PDF-15 (backlog, §2). | Parcialmente resuelto — resto documentado como backlog, no implementado. |

---

## 7. Plan de pruebas unitarias

| # | Cobertura | Estado |
|---|---|---|
| 1 | `SplitPdfUseCaseTest` — extrae rango correcto (no el documento completo), rango completo = mismo total de páginas, rango fuera de límites se ajusta, archivo no-PDF → Error. | ✅ 4 tests, en verde |
| 2 | `MergePdfUseCaseTest` — unir 2 PDFs suma páginas de ambos, menos de 2 PDFs → Error. | ✅ 2 tests, en verde |
| 3 | `RotatePdfUseCaseTest` — 90° se escribe en todas las páginas, rotación acumulada sobre una página ya rotada (180°+270°=90°). | ✅ 2 tests, en verde |
| 4 | `CompressPdfUseCase` — no cubierto (usa `android.graphics.pdf.PdfRenderer`, requiere Robolectric/instrumentación; mismo límite que ya aplicaba a Compress y a la vista previa de Rotate). | Pendiente |
| 5 | `NumberPagesUseCaseTest` — cada uno de los 3 formatos escribe el texto correcto en cada página (verificado extrayendo el texto real del PDF de salida con `PdfTextExtractor`, no solo el conteo de páginas), el total de páginas se conserva, archivo no-PDF → Error. | ✅ 5 tests, en verde |
| 6 | `WatermarkPdfUseCaseTest` — el texto de marca de agua queda escrito como texto real extraíble en cada página (pese a estar rotado/semitransparente), el total de páginas se conserva, texto vacío → Error sin tocar el archivo, texto largo no lanza excepción (se ajusta el tamaño de fuente), archivo no-PDF → Error. | ✅ 5 tests, en verde |
| 7 | `ReorderPagesUseCaseTest` — reordenar sin eliminar refleja el nuevo orden (verificado leyendo el **contenido** de cada página resultante, no solo el conteo — el PDF de prueba tiene una etiqueta de texto distinta por página), omitir una página de la lista la elimina, reordenar y eliminar a la vez produce el resultado combinado correcto, lista de orden vacía → Error, archivo no-PDF → Error. | ✅ 5 tests, en verde |
| 8 | `ComparePdfUseCaseTest` — documentos idénticos → mensaje "identical" sin diferencias, una línea distinta en una página compartida aparece en el reporte generado y se cuenta como página distinta, una página que solo existe en un documento se marca como tal (no se cuenta como idéntica ni se compara contra texto vacío), stream nulo al leer A → Error de lectura A, stream nulo al leer B → Error de lectura B. | ✅ 5 tests, en verde |
| 9 | `RedactPdfUseCaseTest` — censurar la franja donde está el texto "secreto" lo elimina de la extracción del PDF de salida mientras el texto "público" en otra zona de la misma página se conserva intacto (verificado con `PdfTextExtractor`, no solo el mensaje — la prueba que demuestra que la censura es real y no un rectángulo visual), sin zonas marcadas → Error sin tocar el archivo, una zona en una página fuera de rango se ignora sin fallar, el mensaje de éxito informa el número de zonas censuradas, archivo no-PDF → Error. | ✅ 5 tests, en verde |
| 10 | `CropPdfUseCaseTest` — recortar con margen del 10% reduce el tamaño de página proporcionalmente (verificado leyendo el `pageSize` real del PDF de salida, no solo el mensaje), el texto de la página sigue siendo extraíble tras recortar (no se toca el content stream), margen de 0% no cambia el tamaño de la página, un margen fuera de rango se ajusta al máximo permitido (40%) sin generar un rectángulo inválido, archivo no-PDF → Error. | ✅ 5 tests, en verde |

Todos los tests generan PDFs reales en memoria con iText7 (mismo patrón que
`PdfPasswordUseCaseTest`), no mocks del contenido del PDF — el conteo de
páginas y la rotación se verifican leyendo el archivo de salida real.

---

## 8. Preguntas abiertas

| Pregunta | Notas |
|---|---|
| ¿Prioridad real dentro del backlog (§2)? | Se propuso alta/media/baja por esfuerzo-valor; confirmar con el usuario antes de implementar la siguiente tanda. |
| ¿Vale la pena un modo "comprimir conservando texto" (solo recomprimir imágenes embebidas)? | Depende de si el caso de uso principal son PDFs escaneados (ya son imagen, no pierden nada) o documentos de texto exportados (sí perderían selección de texto). |
| ¿Selección de archivo desde la Biblioteca de la app, no solo desde el dispositivo? | Mismo patrón ya resuelto para Seguridad (HU-SEC-04 AC3); i18n ya no es un bloqueante (ver §9), queda pendiente como mejora de UI aparte. |

---

## 9. i18n de las 4 pantallas (2026-08-28)

Última pieza en español fijo del módulo (§5). Cubre: `PdfToolsMenu.kt` ya
estaba conectado a `stringResource()` desde antes — el resto no lo estaba.

- **`PdfToolsScreen.kt`** (menú/tarjeta de resultado), **`MergePdfScreen.kt`**,
  **`SplitPdfScreen.kt`**, **`CompressPdfScreen.kt`**, **`RotatePdfScreen.kt`**,
  **`OutputFileNameField.kt`** — todo el texto visible migrado a
  `stringResource()`. Los textos puramente numéricos sin significado
  lingüístico (`"$quality%"`, `"$X KB"`) se dejaron como estaban, mismo
  criterio ya usado en Seguridad.
- **`DailyLimitDialog.kt`** (compartido con Conversor, `core/ui/components/`)
  — estaba 100% en español fijo pese a renderizarse también dentro de
  Herramientas PDF; se localizó completo en el mismo cambio en vez de dejar
  el diálogo de límite diario como el único punto no traducido de la
  pantalla. `ConverterScreen.kt` también se actualizó (una línea,
  `itemLabelPlural`) para no dejar la mitad del mismo diálogo sin traducir.
- **Mensajes de los 4 use cases** (`MergePdfUseCase`, `SplitPdfUseCase`,
  `CompressPdfUseCase`, `RotatePdfUseCase`) — mismo patrón que
  `PdfPasswordMessages` (`security.md`): cada uno gana una `data class
  *Messages` con los textos de error/éxito, resueltos vía `stringResource()`
  en la capa de Compose y pasados al use case, en vez de que el use case
  llame `context.getString()` directamente. Se eligió este patrón (no el más
  directo de usar el `Context` ya inyectado en cada use case) por dos
  razones: consistencia con el precedente ya establecido en
  `PdfPasswordUseCase`, y porque mantiene los tests unitarios en JVM puro sin
  necesitar Robolectric para resolver recursos Android.
- `PdfToolsViewModel.execute()` ahora recibe un `PdfToolMessages` (bundle de
  los 4 anteriores); `shareResult()`/`saveToDownloads()` ahora reciben el
  título del selector de compartir y el mensaje de error como parámetros en
  vez de tenerlos hardcodeados.
- **Hallazgo real corregido en el camino:** los nuevos strings de reducción
  estimada de Comprimir (`pdf_compress_reduction_*`) se escribieron primero
  con `%%` (patrón copiado de `premium_savings_44`, que ya tenía el mismo
  problema sin detectar) pero se renderizan vía `stringResource(id)` **sin**
  argumentos de formato — Android solo colapsa `%%` a `%` cuando el string
  realmente pasa por `String.format()`. Sin argumentos, `%%` se mostraba
  literal en pantalla ("Reducción estimada: 30-50%%"). Detectado al
  verificar visualmente en el dispositivo real (no por los tests, que no
  cubren el layer de Compose) y corregido a `%` simple en los 5 idiomas. El
  `premium_savings_44` original con el mismo bug queda fuera de alcance de
  este documento — se dejó como tarea aparte para el módulo de Premium.
- **83 strings nuevos** en los 5 idiomas (`values`/-en/-de/-pt/-ru), paridad
  de claves verificada con `diff` tras cada edición.
- Los 3 tests unitarios existentes (`MergePdfUseCaseTest`,
  `SplitPdfUseCaseTest`, `RotatePdfUseCaseTest`) se actualizaron para pasar
  un objeto de mensajes de prueba (mismo patrón que `PdfPasswordUseCaseTest`)
  — sin cambios de cobertura, solo de firma.
- **Verificado end-to-end en el dispositivo real, con la app en ruso** (para
  confirmar un idioma no romance, no solo revisar que compile): las 4
  pantallas del menú y sus formularios, más una operación real completa de
  Rotar (PDF de prueba subido vía `adb push`, seleccionado desde el selector
  del sistema, rotado 90°) — el mensaje de éxito, el contador de usos
  diarios, y los 3 botones de la tarjeta de resultado (incluyendo "Guardado
  en Descargas" tras confirmar el guardado) se mostraron correctamente
  formateados en ruso, confirmando que el nuevo flujo
  ViewModel → use case → `String.format()` funciona en producción y no solo
  en los tests unitarios (que usan plantillas de prueba, no el texto real de
  `strings.xml`).
- Verificado también: `testDebugUnitTest`/`detekt`/`lintDebug` en verde. El
  primer intento falló en lint (`LocalContextGetResourceValueCall`) por
  resolver los mensajes con `context.getString()` dentro de un bloque
  `remember { }` — corregido resolviendo los `stringResource()` fuera del
  `remember` (no puede llamarse un `@Composable` dentro de
  `@DisallowComposableCalls`) y empaquetando los valores ya resueltos.

---

## 10. RF-PDF-06/HU-PDF-05 — Numerar páginas (2026-08-28)

Primera funcionalidad nueva del backlog implementada (prioridad "alta"
sugerida en §2). Sigue exactamente el patrón arquitectónico ya establecido
por las 4 herramientas existentes — nueva entrada en el menú, nuevo use
case con su `data class *Messages`, nueva pantalla, todo en español/i18n
desde el día uno (no se repitió la deuda de i18n que tuvieron las 4
herramientas originales).

- **`NumberPagesUseCase.kt`** (nuevo) — escribe el número de página en el
  pie de cada página vía iText7 (`PdfCanvas`/`layout.Canvas.showTextAligned`,
  fuente Helvetica, centrado), **no rasteriza** (RNF-PDF-01): el contenido
  original de cada página queda intacto y el número queda como texto real,
  seleccionable y buscable. Formato configurable vía `PageNumberFormat`
  (enum `NUMBER_ONLY`/`NUMBER_OF_TOTAL`/`PAGE_OF_TOTAL`, por defecto
  `PAGE_OF_TOTAL` = "Página X de N" según AC1). El texto real de
  "Página X de N" respeta el idioma de la app (`pageOfTotalTemplate` en
  `NumberPagesMessages`, resuelto vía `stringResource()`) — un PDF numerado
  con la app en ruso lleva "Страница X из N" en el pie, no español fijo.
- **`NumberPagesScreen.kt`** (nuevo) — selector de PDF, tarjeta de formato
  con 3 chips (`FilterChip`, mismo patrón que los ángulos de Rotar) y texto
  de ejemplo que cambia según el formato elegido. Sin vista previa en vivo
  (a diferencia de Rotar): requeriría duplicar la lógica de renderizado del
  use case solo para previsualizar, y no lo pide el AC — se prefirió una
  tarjeta de ejemplo estática, consistente con la simplicidad de
  Dividir/Comprimir.
- **`PdfTool.NUMBER_PAGES`** (nuevo valor de enum), wireado en
  `PdfToolsViewModel` (`pageNumberFormat` en `PdfToolsUiState`,
  `onPageNumberFormatChange()`, rama nueva en `execute()`) y
  `PdfToolsScreen.kt` (nueva entrada de menú con ícono
  `Icons.Rounded.FormatListNumbered` y color `IndigoAccent`, quinto color
  distinto de los 4 ya usados por las otras herramientas).
- **Hallazgo real encontrado y corregido antes de shippear:**
  `DailyLimitManager.getPdfToolKey()` mapea el nombre de cada herramienta a
  su propia clave de contador en `SharedPreferences`; sin agregar un `case`
  para `"NUMBER_PAGES"`, habría caído en la rama `else -> KEY_CONVERSIONS`
  — usar "Numerar páginas" habría consumido el límite diario de
  conversiones del Conversor en vez de tener su propio contador
  independiente (mismo tipo de bug de "cae en el `else` equivocado" que
  otras herramientas PDF ya tienen resuelto con su propio `KEY_*`). Se
  agregó `KEY_NUMBER_PAGES` y su `case`, más un test de regresión en
  `DailyLimitManagerTest` que fija que "Numerar páginas" no comparte
  contador con conversiones ni con otra herramienta PDF.
- **Hallazgo de UI encontrado en el dispositivo real:** el chip de formato
  "Página X de N" se veía cortado ("Página X") en la captura del ícono de
  check al quedar seleccionado — el ícono le resta ancho disponible al
  texto dentro del mismo tercio de fila que los otros dos chips más
  cortos. Corregido acortando la etiqueta del chip a "Página X" en los 5
  idiomas (el texto completo con el total sigue apareciendo en la línea de
  ejemplo debajo, "Ejemplo: Página 3 de 10") y reduciendo el estilo
  tipográfico del chip a `labelSmall` como margen de seguridad adicional
  para los idiomas más largos (ruso).
- **5 tests unitarios nuevos** (`NumberPagesUseCaseTest`) — verifican el
  texto real escrito en cada página con `PdfTextExtractor` para los 3
  formatos (no solo que el resultado sea `Success`), que el total de
  páginas se conserva, y que un archivo no-PDF devuelve `Error`.
- **detekt:** el archivo nuevo introdujo hallazgos reales
  (`LongMethod` en la pantalla, `WildcardImport` x3) que si se corrigieron
  de verdad (se extrajeron `NumberPagesSelectZone`/`NumberPagesFormatCard`
  como composables privados, imports explícitos en vez de `*`) — y
  hallazgos de boilerplate ya aceptado en las 4 herramientas hermanas
  (`copyUriToCache` con `NestedBlockDepth`/`ReturnCount`, `catch (e:
  Exception)` genérico) que se añadieron al baseline (`config/detekt/
  baseline.xml`) a mano, con las 3 líneas nuevas exactas — **no** con
  `./gradlew detektBaseline` (que regenera el archivo completo y arrastra
  drift de sesiones anteriores no relacionado con este cambio).
- **Verificado end-to-end en el dispositivo real (app en español):** PDF de
  3 páginas real subido vía `adb push` → seleccionado desde el selector del
  sistema → las 3 páginas numeradas con el formato por defecto → mensaje de
  éxito "PDF numerado correctamente — 3 páginas" → guardado en Descargas →
  archivo descargado y verificado directamente: cada una de las 3 páginas
  lleva "Página 1 de 3"/"Página 2 de 3"/"Página 3 de 3" en el pie, con el
  contenido original de cada página ("Page one"/"Page two"/"Page three")
  intacto.
- Verificado también: `testDebugUnitTest`/`detekt`/`lintDebug` en verde.

---

## 11. RF-PDF-07/HU-PDF-06 — Marca de agua (2026-08-28)

Segunda funcionalidad nueva del backlog (prioridad "alta" sugerida en §2,
junto con Numerar páginas). Mismo patrón arquitectónico que las 5
herramientas existentes.

- **`WatermarkPdfUseCase.kt`** (nuevo) — superpone el texto en diagonal
  (45°) y semitransparente (opacidad 0.15, `PdfExtGState.setFillOpacity()`)
  sobre cada página, escrito directamente con `PdfCanvas`/`setTextMatrix()`
  (matriz de rotación manual, ya que a diferencia de Numerar páginas esto
  no tiene un helper de alto nivel equivalente a
  `Canvas.showTextAligned()` que soporte rotación+opacidad juntas de forma
  simple) — **no rasteriza** (RNF-PDF-01), el contenido original y el
  texto de la marca quedan ambos como texto real seleccionable/buscable
  (verificado con `PdfTextExtractor` en los tests, pese a la rotación).
  Centrado en la página vía el ancho del texto (`font.getWidth()`) y
  trigonometría básica (`cos`/`sin` de 45°) para las coordenadas de
  inicio.
  - **Auto-ajuste de tamaño de fuente:** el texto de marca de agua es
    libre (no un formato fijo como Numerar páginas), así que puede ser
    arbitrariamente largo. Se parte de 40pt y, si el texto a ese tamaño
    superaría 1.3× el ancho de la página, se reduce proporcionalmente
    (mínimo 8pt) para evitar que quede completamente ilegible o corte de
    forma extrema — sin este ajuste, un texto largo con fuente fija
    podría desbordar mucho más allá de la página.
- **`WatermarkPdfScreen.kt`** (nuevo) — selector de PDF, campo de texto
  libre (`OutlinedTextField`, mismo estilo visual que
  `OutputFileNameField`) para el texto de la marca de agua, y una nota de
  ayuda explicando el comportamiento (diagonal, semitransparente) en vez
  de una vista previa en vivo — mismo criterio que Numerar páginas: una
  vista previa real requeriría duplicar la lógica de renderizado del use
  case. El botón de ejecutar queda deshabilitado tanto sin PDF
  seleccionado como con el texto vacío (`watermarkText.isNotBlank()`),
  igual que el use case también valida y devuelve `Error` si igual se
  invoca con texto en blanco — doble validación (UI + dominio), mismo
  patrón que "Selecciona al menos 2 PDFs" en Unir.
  - **Escrito con imports explícitos y composables separados desde el
    inicio** (`WatermarkSelectZone`, `WatermarkTextCard`) — a diferencia
    de `NumberPagesScreen.kt` (§10), que necesitó una segunda pasada tras
    fallar detekt por `WildcardImport`/`LongMethod`, esta pantalla se
    escribió ya con la lección aplicada y pasó detekt a la primera.
- **`PdfTool.WATERMARK`** (nuevo valor de enum), wireado en
  `PdfToolsViewModel` (`watermarkText` en `PdfToolsUiState`,
  `onWatermarkTextChange()`, rama nueva en `execute()`) y
  `PdfToolsScreen.kt` (nueva entrada de menú con ícono
  `Icons.Rounded.BrandingWatermark` y color `ColorImage`, sexto color
  distinto de los 5 ya usados).
- **Bug de `DailyLimitManager` evitado de entrada, no encontrado después:**
  tras el hallazgo real de Numerar páginas (§10, `NUMBER_PAGES` caía en el
  `else -> KEY_CONVERSIONS` por faltar su `case`), se agregó
  `KEY_WATERMARK` y su `case` en `getPdfToolKey()` **antes** de escribir
  el resto del feature, no después — con su propio test de regresión en
  `DailyLimitManagerTest` (mismo patrón que el de `NUMBER_PAGES`).
- **5 tests unitarios nuevos** (`WatermarkPdfUseCaseTest`) — verifican con
  `PdfTextExtractor` que el texto de marca de agua es extraíble en cada
  página pese a la rotación/opacidad, que el total de páginas se
  conserva, que un texto vacío/en blanco devuelve `Error` sin tocar el
  sistema de archivos, que un texto largo no lanza excepción (ejercita el
  ajuste de tamaño de fuente), y que un archivo no-PDF devuelve `Error`.
- **detekt:** mismos 4 hallazgos de boilerplate ya vistos en las 5
  herramientas hermanas (`copyUriToCache` con
  `NestedBlockDepth`/`ReturnCount`, `catch (e: Exception)` genérico x2 que
  colapsan en 1 entrada de baseline) — agregados a mano al baseline en las
  posiciones alfabéticas correctas, mismo procedimiento que §10 (no
  `./gradlew detektBaseline`). La pantalla, a diferencia de la de Numerar
  páginas, no aportó hallazgos nuevos (ver nota arriba).
- **Verificado end-to-end en el dispositivo real (app en español):** PDF
  de 3 páginas real subido vía `adb push` → seleccionado desde el
  selector del sistema → texto "CONFIDENCIAL" escrito en el campo → marca
  de agua aplicada → mensaje de éxito "Marca de agua aplicada
  correctamente — 3 páginas" → guardado en Descargas → archivo descargado
  y verificado directamente: las 3 páginas muestran "CONFIDENCIAL" en
  diagonal, semitransparente y centrado, con el contenido original de
  cada página ("Page one"/"Page two"/"Page three") intacto — coincide
  exactamente con HU-PDF-06 AC1.
- Verificado también: `testDebugUnitTest`/`detekt`/`lintDebug` en verde.

---

## 12. RF-PDF-08/HU-PDF-07 — Reordenar y eliminar páginas (2026-08-28)

Tercera y última funcionalidad de prioridad "alta" del backlog, y la más
compleja de UI del módulo — la única que requiere miniaturas reales
renderizadas del PDF y una interacción de arrastre en vivo, no solo un
formulario con campos/chips como las 3 anteriores.

- **`ReorderPagesUseCase.kt`** (nuevo) — recibe **una sola lista**
  `pageOrder: List<Int>` con los números de página 1-based del PDF
  *original*, ya en el orden final deseado; una página del original que no
  aparezca en la lista queda eliminada del resultado. Este diseño resuelve
  reordenar (AC1) y eliminar (AC2) en una sola operación/parámetro en vez
  de necesitar dos conceptos separados — más simple de razonar y de testear
  que mantener "orden" y "páginas a eliminar" como dos listas
  independientes que podrían desincronizarse. Usa `copyPagesTo` página por
  página (mismo principio que Unir), **no rasteriza** el archivo final
  (RNF-PDF-01) aunque sí rasteriza las miniaturas de la vista previa (ver
  abajo) — son cosas distintas: la miniatura es solo para mostrar en
  pantalla, el archivo que genera el use case nunca pasa por un bitmap.
- **`ReorderPagesScreen.kt`** (nuevo, la pieza más grande de las 3
  funcionalidades nuevas) —
  - **Miniaturas:** al seleccionar un PDF, un `LaunchedEffect` copia el
    `Uri` a caché y usa `android.graphics.pdf.PdfRenderer` para generar un
    `Bitmap` reducido (220px de ancho, escalado proporcional) por cada
    página — mismo mecanismo que ya usaba la vista previa de Rotar
    (`RotatePdfScreen.kt`), extendido de 1 página a todas. Vive en la capa
    de Compose, no en el use case, por la misma razón que la vista previa
    de Rotar: `PdfRenderer`/`Bitmap` son clases de framework Android no
    testeables en JVM puro sin Robolectric.
  - **Arrastre en vivo:** cada fila tiene un ícono de arrastre
    (`Icons.Rounded.DragHandle`) con su propio `Modifier.pointerInput` +
    `detectDragGestures` — deliberadamente **sin** long-press previo
    (`detectDragGestures`, no `...AfterLongPress`): al ser un ícono
    dedicado y pequeño, no compite con el scroll vertical de la lista como
    lo haría si el gesto viviera en la fila completa, así que no hace
    falta el paso extra de mantener presionado para "armar" el arrastre.
    El desplazamiento vertical acumulado se compara contra una altura de
    fila fija (88dp→px) para decidir cuándo cruzar el umbral y disparar un
    swap con la página vecina, reponiendo el offset visual para que el
    dedo y el ítem no se desincronicen durante arrastres largos.
    **Detalle de Compose no evidente:** `pointerInput(key)` no reinicia su
    corrutina mientras `key` no cambie, así que durante un arrastre
    continuo el cierre (`closure`) que lee `pageOrder` seguía siendo el de
    *antes* de empezar a arrastrar si no se corrige — un arrastre de varios
    pasos habría operado sobre una lista desactualizada a partir del
    segundo swap. Se resuelve con `rememberUpdatedState(pageOrder)`, el
    mecanismo estándar de Compose para este problema exacto.
  - **Eliminar:** ícono de papelera por fila, deshabilitado cuando solo
    queda 1 página (AC3, ver §4) — mismo guardarraíl que ya tiene el use
    case (`emptyOrderError`), así que la protección existe en dos capas.
  - Sin vista previa de "cómo queda el PDF final" más allá de las propias
    miniaturas reordenables — no hace falta una vista previa aparte, la
    lista de miniaturas *es* la vista previa en este caso, a diferencia de
    Numerar páginas/Marca de agua donde el resultado no es visualmente
    obvio antes de generarlo.
- **`PdfTool.REORDER_PAGES`** (nuevo valor de enum), con estado propio en
  `PdfToolsUiState.pageOrder: List<Int>` (vacío hasta que se cargan las
  miniaturas, inicializado a `1..totalPages` vía `onPagesLoaded()`,
  mutado por `onReorderPage(from, to)`/`onRemovePage(pageNumber)`) y nueva
  entrada de menú con ícono `Icons.Rounded.Reorder` y color `SlateGray`
  (séptimo color distinto de los 6 ya usados).
- **`DailyLimitManager`:** mismo procedimiento preventivo que Marca de agua
  (§11) — se agregó `KEY_REORDER_PAGES` y su `case` **antes** de escribir
  el resto del feature, con su test de regresión correspondiente.
- **Refactor real motivado por detekt, no boilerplate:**
  `PdfToolsViewModel.execute()` superó el umbral de complejidad ciclomática
  (15) al agregar la séptima rama del `when` de despacho por herramienta —
  a diferencia de los hallazgos de `copyUriToCache` (boilerplate idéntico
  ya aceptado en 6 archivos hermanos, ver abajo), este **sí** se corrigió
  de verdad: se extrajo el `when` completo a una función privada nueva
  `runTool(state, customName, messages): PdfToolResult?`, dejando
  `execute()` solo con las validaciones previas (PDF seleccionado, límite
  diario) y el manejo de `uiState` — su complejidad baja considerablemente
  y `runTool()` queda con la complejidad inherente de despachar por tipo de
  herramienta (~8), bien por debajo del umbral. Este dispatcher va a seguir
  creciendo con cada herramienta nueva del backlog restante, así que vale
  la pena mantenerlo separado desde ahora.
- **5 tests unitarios nuevos** (`ReorderPagesUseCaseTest`) — el PDF de
  prueba lleva una etiqueta de texto distinta por página ("PAGINA_1",
  "PAGINA_2"...) para poder verificar no solo el conteo de páginas del
  resultado sino que el **contenido correcto** terminó en cada posición
  tras reordenar y/o eliminar (con `PdfTextExtractor`, mismo enfoque que
  Numerar páginas/Marca de agua) — cubre reordenar solo, eliminar solo,
  ambos combinados, lista vacía → Error, y archivo no-PDF → Error.
- **detekt:** los 4 hallazgos de boilerplate ya vistos en las 6
  herramientas hermanas (`copyUriToCache` con
  `NestedBlockDepth`/`ReturnCount`, `catch (e: Exception)` genérico) más un
  `PrintStackTrace` real en la carga de miniaturas (`e.printStackTrace()`
  en vez de `Timber.e()`, único hallazgo de este tipo en las 3
  funcionalidades nuevas) — el `PrintStackTrace` y el
  `CyclomaticComplexMethod` de `execute()` (ver arriba) se corrigieron de
  verdad; los 4 de boilerplate se añadieron al baseline a mano, mismo
  procedimiento que §10/§11.
- **Verificado end-to-end en el dispositivo real (app en español),
  incluyendo el gesto de arrastre real, no solo taps:** PDF de 3 páginas
  real subido vía `adb push` → seleccionado → las 3 miniaturas cargaron
  correctamente (contenido visible en cada una) → `adb shell input swipe`
  simulando mantener presionado el ícono de arrastre de la página 1 y
  moverlo verticalmente hasta pasar las páginas 2 y 3 → el orden en
  pantalla cambió en vivo a Página 2, Página 3, Página 1 (confirmando que
  el swap en vivo y `rememberUpdatedState` funcionan correctamente en un
  arrastre de varios pasos, no solo de uno) → se eliminó la página 3 desde
  su ícono de papelera → se ejecutó la operación → mensaje de éxito "PDF
  actualizado correctamente — 2 páginas" → guardado en Descargas →
  archivo descargado y verificado directamente: 2 páginas, "Page two"
  primero y "Page one" segundo, "Page three" ausente — coincide
  exactamente con la reordenación y eliminación realizadas en pantalla.
- Verificado también: `testDebugUnitTest`/`detekt`/`lintDebug` en verde.

---

## 13. RF-PDF-13/HU-PDF-08 — Comparar dos PDFs (2026-08-28)

Primera funcionalidad de prioridad "media" del backlog, y la primera que
recibe **dos** PDFs de entrada en vez de uno — mismo patrón de dos
selectores que ya usa Unir para múltiples archivos, pero acá son
exactamente dos slots con roles fijos (Documento A / Documento B), no una
lista abierta. También es la primera herramienta cuyo archivo de salida
**no es una versión modificada de un PDF de entrada**, sino un documento
nuevo (un reporte) sintetizado a partir de lo que se encontró al comparar
— ver la nota de RNF-PDF-01 en §3 sobre por qué esta herramienta queda
fuera de ese requerimiento.

- **Decisión de diseño — comparación de texto, no píxel a píxel:** "resaltar
  diferencias" se interpretó como comparación de **contenido de texto** por
  página (`PdfTextExtractor`, el mismo mecanismo que ya usan Modo
  Estudio/Buscar), no como diff visual de imágenes renderizadas. Se eligió
  así por tres razones: (1) no exige que ambos PDFs tengan el mismo
  tamaño/DPI de página para poder compararse, (2) el resultado (el reporte)
  puede ser texto real, no una imagen superpuesta con highlights — más
  liviano y compartible, (3) evita añadir una dependencia de
  procesamiento de imágenes solo para esta herramienta. La desventaja
  aceptada: cambios puramente visuales sin cambio de texto (una imagen
  reemplazada por otra del mismo tamaño, por ejemplo) no se detectan — se
  documenta como limitación conocida, no como bug.
- **`ComparePdfUseCase.kt`** (nuevo) —
  - Extrae el texto de cada página de ambos PDFs (`PdfTextExtractor`) hasta
    el máximo de páginas de los dos documentos; una página que no existe en
    uno de los dos (`totalPages` distintos) se marca como "solo existe en
    el Documento A/B" en vez de compararse contra texto vacío.
  - Para una página presente en ambos, separa el texto en líneas
    (`trim()` + filtra vacías) y calcula, vía diferencia de conjuntos
    (`Set.filter { it !in otro }`), qué líneas aparecen solo en A y cuáles
    solo en B — un diff por conjunto, no un diff ordenado tipo
    Myers/LCS: no detecta líneas *movidas* de posición, solo líneas
    *presentes en uno y ausentes en el otro*, decisión deliberada para
    mantener el algoritmo simple y testeable dado el alcance de la
    funcionalidad (un reporte de diferencias, no un editor de diff visual
    línea por línea).
  - Genera el reporte con la API de alto nivel `com.itextpdf.layout.Document`
    /`Paragraph` — primera vez que este módulo la usa (las 7 herramientas
    anteriores escriben con `PdfCanvas`, de bajo nivel, porque dibujan
    sobre páginas *existentes*; acá no hay página existente sobre la cual
    dibujar, se sintetiza un documento nuevo de cero, y `layout.Document`
    da paginación/wrap automático gratis, algo que no hacía falta antes).
  - Mensaje de resultado: "Los dos documentos son idénticos" si 0 páginas
    difieren, o "N de M páginas tienen diferencias" en caso contrario — AC4
    de HU-PDF-08.
- **`ComparePdfScreen.kt`** (nuevo) — dos `CompareSelectZone` (Documento A /
  Documento B), cada una con su propio callback de selección; el botón de
  ejecutar solo se habilita cuando ambos están seleccionados. Escrito con
  imports explícitos y sub-composables desde el inicio (lección ya aplicada
  en Marca de agua, §11).
- **`PdfTool.COMPARE`** (nuevo valor de enum), con estado propio en
  `PdfToolsUiState` (`comparePdfA: Uri?`, `comparePdfB: Uri?` — campos
  separados de `selectedPdfs`, no reutilizados, porque las otras 7
  herramientas tratan `selectedPdfs` como una lista de un solo rol; forzar
  el mismo campo a guardar "posición 0 = A, posición 1 = B" habría sido más
  frágil que dos campos con nombre propio) y dos acciones nuevas en el
  ViewModel (`onComparePdfASelected`/`onComparePdfBSelected`). `execute()`
  ajustó su guardarraíl inicial (antes solo `selectedPdfs.isEmpty()`) para
  reconocer que Comparar valida sus dos campos propios en vez de
  `selectedPdfs`. Nueva entrada de menú con ícono
  `Icons.Rounded.CompareArrows` y color `ColorPowerPoint` (octavo color
  distinto de los 7 ya usados).
- **`DailyLimitManager`:** mismo procedimiento preventivo que las 3
  funcionalidades anteriores (§10/§11/§12) — se agregó `KEY_COMPARE` y su
  `case` **antes** de escribir el resto del feature, con su test de
  regresión correspondiente.
- **5 tests unitarios nuevos** (`ComparePdfUseCaseTest`) — documentos
  idénticos → mensaje "identical"; una línea distinta en una página
  compartida aparece en el reporte generado (verificado extrayendo el
  texto real del PDF de salida con `PdfTextExtractor`, no solo el mensaje)
  y se cuenta en el total de páginas distintas; una página que solo existe
  en un documento se marca como tal en el reporte y se cuenta como
  distinta (no como idéntica); stream nulo al leer A/B (`openInputStream`
  devuelve `null`) → `Error` con el mensaje específico de lectura A/B —
  cubre los 4 casos límite de la HU (AC1–AC4).
- **detekt:** los mismos 3 hallazgos de boilerplate ya vistos en las 7
  herramientas hermanas (`copyUriToCache` con
  `NestedBlockDepth`/`ReturnCount`, `catch (e: Exception)` genérico —
  detekt deduplica por firma de clase, así que un solo `<ID>` cubre los 2
  `catch (e: Exception)` de este archivo, uno en `invoke()` y otro en
  `copyUriToCache()`) más 2 hallazgos genuinamente nuevos que se
  corrigieron de verdad en vez de baselinearse: `ReturnCount` en
  `buildPageDiff` (3 returns tempranos → reescrito como una sola expresión
  `when`) y `MaxLineLength` en 4 líneas (extracción de texto por página y
  la propia definición de `buildPageDiff`, envueltas en bloques `if`
  multilínea). También se corrigieron 2 `MaxLineLength` reales en
  `PdfToolsViewModel.kt` (`onComparePdfASelected`/`onComparePdfBSelected`).
- **Detalle de compilación no evidente:** el primer intento de la rama
  `PdfTool.COMPARE` dentro de `runTool()` usaba
  `state.comparePdfA ?: return null` como argumento — pero `runTool()` es
  una función de **cuerpo de expresión** (`= when (...) { ... }`), y Kotlin
  prohíbe `return` dentro de una función de cuerpo de expresión ("Returns
  are prohibited in functions with expression body"). Se corrigió
  reestructurando la rama como un bloque que calcula `pdfA`/`pdfB` en
  variables locales y usa un `if/else` como última expresión del bloque
  (valor `null` en el `else`) en vez de un `return` temprano — sigue siendo
  una función de expresión, sin convertirla a cuerpo de bloque solo por
  esta rama.
- **Verificado end-to-end en el dispositivo real (app en español):** dos
  PDFs de prueba generados con contenido conocido (Documento A: 3 páginas
  — "LINEA COMPARTIDA", "TEXTO SOLO EN A", "PAGINA EXTRA SOLO EN A";
  Documento B: 2 páginas — "LINEA COMPARTIDA", "TEXTO SOLO EN B") subidos
  vía `adb push` → seleccionados como Documento A/B en la pantalla →
  ejecutado → mensaje de resultado "2 de 3 páginas tienen diferencias"
  (coincide exactamente con lo esperado: página 1 idéntica no cuenta,
  página 2 difiere en texto, página 3 solo existe en A) → guardado en
  Descargas → archivo descargado y leído directamente: el reporte generado
  contiene "Página 2 / Solo en A: TEXTO SOLO EN A / Solo en B: TEXTO SOLO
  EN B" y "Página 3 / Esta página solo existe en el Documento A", con la
  página 1 (idéntica) correctamente omitida del reporte — coincide
  exactamente con el contenido real de los dos PDFs de prueba.
- Verificado también: `testDebugUnitTest`/`detekt`/`lintDebug` en verde.

---

## 14. RF-PDF-14/HU-PDF-09 — Censurar contenido (2026-08-28)

Segunda y última funcionalidad de prioridad "media" del backlog — cierra
esa categoría junto con Comparar (§13). Es la única herramienta del módulo
cuyo objetivo explícito es **destruir** contenido en vez de preservarlo
(ver la nota añadida a RNF-PDF-01 en §3), y la que introduce la primera
dependencia nueva de iText7 desde que el módulo existe.

- **Decisión de diseño — dependencia nueva, no un rectángulo dibujado a
  mano:** dibujar un rectángulo negro con `PdfCanvas` (el mismo mecanismo
  ya usado por Marca de agua/Numerar páginas) habría sido mucho más simple,
  pero **no cumple lo que pide el RF** ("de forma irreversible") — el texto
  original seguiría existiendo en el content stream, debajo del rectángulo,
  perfectamente extraíble con cualquier herramienta que ignore el color de
  relleno (o simplemente copiando el texto en un lector de PDF). Se agregó
  el módulo `pdfCleanup` de iText7 (`com.itextpdf:cleanup:3.0.2`,
  exactamente la versión declarada compatible con `itext7-core:7.2.5` ya
  presente en el proyecto — verificado contra el POM publicado en Maven
  Central antes de fijar la versión) — su clase `PdfCleaner.cleanUp()`
  elimina de verdad el contenido del content stream dentro de cada región
  antes de rellenarla, no solo lo cubre visualmente.
- **`RedactPdfUseCase.kt`** (nuevo) — recibe una lista de `RedactionRect`
  (página + rectángulo en **fracciones** 0..1 del ancho/alto de la página
  *tal como se ve en pantalla*, no en puntos PDF ni en píxeles del bitmap
  de vista previa). Por cada rectángulo, convierte la fracción a
  coordenadas PDF reales usando el tamaño real de esa página
  (`PdfPage.getPageSize()`), invirtiendo el eje Y (pantalla: origen
  arriba-izquierda: PDF: origen abajo-izquierda) — guardar en fracciones
  en vez de píxeles absolutos es lo que permite que la censura sea exacta
  sin importar a qué resolución se renderizó la vista previa en pantalla.
  Construye una `PdfCleanUpLocation` por rectángulo y llama
  `PdfCleaner.cleanUp(pdfDocument, locations)` sobre un
  `PdfDocument(PdfReader, PdfWriter)` combinado (mismo patrón que Rotar).
  Rectángulos en una página fuera de rango del PDF real se ignoran en vez
  de fallar toda la operación (protege contra el caso de que el usuario
  marque zonas contra una vista previa desincronizada).
- **`RedactPdfScreen.kt`** (nuevo) — la segunda pantalla más compleja del
  módulo después de Reordenar páginas (§12), y la primera con un editor de
  dibujo libre en vez de gestos predefinidos:
  - **Navegador de páginas:** "Página X de N" con flechas prev/next; cada
    cambio de página carga bajo demanda (no todas a la vez, para no
    consumir memoria en documentos largos) un nuevo bitmap de esa página
    vía `PdfRenderer` (mismo mecanismo que Rotar/Reordenar), a mayor
    resolución que las miniaturas de Reordenar (900px de ancho objetivo
    vs. 220px) porque acá el usuario necesita precisión para dibujar sobre
    texto pequeño.
  - **Dibujo del rectángulo:** un `Box` con `Modifier.pointerInput` +
    `detectDragGestures` sobre la imagen de la página, con un `Canvas`
    superpuesto (`Modifier.matchParentSize()`, miembro de `BoxScope` — no
    requiere import propio) que dibuja en vivo el rectángulo mientras se
    arrastra y los ya confirmados de esa página en negro semitransparente.
    El `Box` que contiene la imagen fija su `aspectRatio` al de la página
    PDF real y la imagen usa `ContentScale.FillBounds` — esto asegura que
    el 100% del área del `Box` sea la página (sin bandas de letterbox),
    así la conversión de posición del gesto a fracción (0..1) es directa,
    sin tener que descontar offsets de recorte de `ContentScale.Fit`.
  - **Deshacer/Borrar todo:** en vez de hit-testing individual por
    rectángulo para poder eliminar uno específico (bastante más complejo
    de implementar de forma confiable con gestos), se ofrece "Deshacer"
    (quita el último rectángulo agregado, sin importar en qué página) y
    "Borrar todo" — más simple y predecible, suficiente para el alcance de
    esta primera versión.
- **`PdfTool.REDACT`** (nuevo valor de enum), con estado propio en
  `PdfToolsUiState` (`redactionRects: List<RedactionRect>`,
  `redactionCurrentPage`, `redactionTotalPages`) y 5 acciones nuevas en el
  ViewModel. Reutiliza `selectedPdfs`/`singlePdfLauncher` igual que
  Rotar/Marca de agua/Numerar páginas/Reordenar (un solo archivo de
  entrada), a diferencia de Comparar que necesitó campos separados. Nueva
  entrada de menú con ícono `Icons.Rounded.VisibilityOff` y color
  `ErrorRed` (noveno color distinto de los 8 ya usados — semánticamente
  apropiado para una operación irreversible/de riesgo).
- **`DailyLimitManager`:** mismo procedimiento preventivo que las 4
  funcionalidades anteriores (§10/§11/§12/§13) — se agregó `KEY_REDACT` y
  su `case` **antes** de escribir el resto del feature, con su test de
  regresión correspondiente.
- **5 tests unitarios nuevos** (`RedactPdfUseCaseTest`) — el PDF de prueba
  tiene "SECRETO" cerca del borde superior de la página y "PUBLICO" cerca
  del borde inferior; censurar solo la franja superior (yFrac=0,
  hFrac=0.3) verifica con `PdfTextExtractor` que "SECRETO" **deja de
  existir** en la extracción del PDF resultante mientras "PUBLICO" se
  conserva — esta es la prueba que demuestra que la censura es real, no
  solo un mensaje de éxito. Cubre además: sin zonas marcadas → Error, una
  zona en una página fuera de rango se ignora sin fallar, el mensaje de
  éxito informa el número exacto de zonas censuradas, archivo no-PDF →
  Error.
- **detekt:** los mismos 3 hallazgos de boilerplate ya vistos en las 8
  herramientas hermanas (`copyUriToCache` con
  `NestedBlockDepth`/`ReturnCount`, `catch (e: Exception)` genérico) más un
  `TooGenericExceptionCaught` en la carga de vista previa de
  `RedactPdfScreen.kt` (mismo patrón exacto ya baselineado para
  `ReorderPagesScreen.kt$e: Exception`) y 2 `MaxLineLength` reales en los
  `IconButton` de navegación de página, corregidos de verdad envolviendo el
  `Icon` en un bloque multilínea.
- **Detalle de Compose no evidente:** `Modifier.matchParentSize()` es un
  miembro de extensión de `BoxScope`, no una función de nivel de paquete —
  el primer intento agregó `import androidx.compose.foundation.layout.matchParentSize`,
  que no existe como símbolo importable y causó "Unresolved reference" en
  la línea del import mismo. Se corrigió eliminando el import: la función
  ya está disponible automáticamente dentro de cualquier lambda `Box { }`
  por resolución de miembro de receptor implícito.
- **Verificado end-to-end en el dispositivo real (app en español):** PDF
  de una página con "SECRETO CONFIDENCIAL" arriba y "PUBLICO VISIBLE" abajo
  subido vía `adb push` → seleccionado → simulado un gesto de arrastre real
  (`adb shell input swipe`, no solo taps) sobre la vista previa cubriendo
  la línea completa de "SECRETO CONFIDENCIAL" → "1 zonas marcadas" en
  pantalla, con el rectángulo visible tapando el texto → ejecutado →
  mensaje de éxito "1 zonas censuradas correctamente" → guardado en
  Descargas → archivo descargado y leído directamente: "SECRETO
  CONFIDENCIAL" **no aparece en absoluto** en el texto extraído del PDF
  resultante, mientras "PUBLICO VISIBLE" se conserva intacto — coincide
  exactamente con la zona marcada en pantalla y confirma en el dispositivo
  real el mismo comportamiento validado por los tests unitarios.
- Verificado también: `testDebugUnitTest`/`detekt`/`lintDebug` en verde.

---

## 15. RF-PDF-09/HU-PDF-10 — Recortar páginas (2026-08-28)

Primera funcionalidad de prioridad "baja/futuro" del backlog, y la más
simple de las últimas 4 (Comparar, Censurar, Recortar) — no requiere
dependencias nuevas, comparación de texto entre documentos, ni un editor
de dibujo libre, solo ajustar el tamaño declarado de cada página.

- **`CropPdfUseCase.kt`** (nuevo) — recibe `marginPercent: Int` (0–40,
  ajustado con `coerceIn` si llega fuera de rango) y aplica el mismo
  porcentaje a los 4 lados de cada página. Por cada página, calcula el
  nuevo rectángulo a partir de `PdfPage.getPageSize()` real (no asume un
  tamaño fijo tipo LETTER/A4 — funciona igual con cualquier tamaño de
  página de origen) y llama `page.setMediaBox(rect)` **y**
  `page.setCropBox(rect)` — fijar ambos es necesario porque algunos
  lectores priorizan `CropBox` sobre `MediaBox` cuando ambos existen; si
  solo se hubiera cambiado uno de los dos, el recorte podría no verse
  reflejado en todos los visores. Mismo principio que RF-PDF-04 (Rotar):
  no se reescribe el content stream, solo un metadato de tamaño de página,
  así que el texto/vectores originales quedan completamente intactos
  (RNF-PDF-01, ver nota añadida en §3).
- **`CropPdfScreen.kt`** (nuevo) — reutiliza el patrón de `Slider` ya
  establecido por Comprimir (`CompressPdfScreen.kt`, calidad 20–100) en
  vez de introducir un control nuevo. La vista previa es interactiva de
  verdad, no solo un mensaje de "esto se va a recortar": al cambiar el
  selector de PDF se carga **una vez** el bitmap completo de la primera
  página vía `PdfRenderer` (mismo mecanismo que Rotar/Recortar/Censurar),
  y cada movimiento del slider recorta ese mismo bitmap en memoria
  (`Bitmap.createBitmap(original, x, y, width, height)`, `remember(bitmap,
  marginPercent)`) sin volver a tocar el archivo ni relanzar I/O — el
  usuario ve el efecto del margen en tiempo real mientras arrastra.
  Escrito con imports explícitos y sub-composables (`CropSelectZone`,
  `CropPreviewCard`) desde el inicio, lección ya aplicada en Marca de
  agua/Comparar/Censurar — aun así el primer intento excedió el límite de
  `LongMethod` de detekt (238 líneas en un solo composable) por no haber
  extraído los sub-composables *de entrada*; se corrigió extrayéndolos
  antes de continuar, no baselineando el hallazgo.
- **`PdfTool.CROP`** (nuevo valor de enum), con un único campo de estado
  nuevo en `PdfToolsUiState` (`cropMarginPercent: Int = 10`) — no necesita
  reset en `onPdfsSelected()` porque no depende del PDF seleccionado,
  mismo criterio ya aplicado a `rotationDegrees`/`compressionQuality`.
  Reutiliza `selectedPdfs`/`singlePdfLauncher` (un solo archivo de
  entrada). Nueva entrada de menú con ícono `Icons.Rounded.Crop` y color
  `PremiumGold` (décimo color distinto de los 9 ya usados).
- **`DailyLimitManager`:** mismo procedimiento preventivo que las 5
  funcionalidades anteriores (§10/§11/§12/§13/§14) — se agregó `KEY_CROP`
  y su `case` **antes** de escribir el resto del feature, con su test de
  regresión correspondiente.
- **5 tests unitarios nuevos** (`CropPdfUseCaseTest`) — un PDF de prueba en
  tamaño LETTER fijado explícitamente (`PageSize.LETTER`, no el tamaño por
  defecto de iText7 — el primer intento asumió LETTER implícito y falló
  porque la versión de iText7 usada por el proyecto por defecto genera A4)
  verifica que un margen del 10% reduce el ancho/alto de página en
  exactamente 20% (10% de cada lado), que el texto de la página sigue
  siendo extraíble tras recortar, que 0% de margen no altera el tamaño de
  página, que un margen de 90% se ajusta a 40% sin producir un rectángulo
  de ancho/alto negativo, y que un archivo no-PDF devuelve `Error`.
- **detekt:** los mismos 3 hallazgos de boilerplate ya vistos en las 9
  herramientas hermanas (`copyUriToCache` con
  `NestedBlockDepth`/`ReturnCount`, `catch (e: Exception)` genérico en
  `CropPdfUseCase.kt` y en la carga de vista previa de `CropPdfScreen.kt`,
  mismo patrón exacto ya baselineado para
  `ReorderPagesScreen.kt`/`RedactPdfScreen.kt$e: Exception`) más un
  `LongMethod` real (ver arriba), corregido de verdad extrayendo
  sub-composables en vez de baselinearse.
- **Verificado end-to-end en el dispositivo real (app en español):**
  reutilizado el mismo PDF de prueba de la verificación de Censurar
  (`RedactTest.pdf`, "SECRETO CONFIDENCIAL" cerca del borde superior,
  "PUBLICO VISIBLE" cerca del borde inferior) → seleccionado en la
  pantalla de Recortar → la vista previa mostró en vivo el recorte
  aplicándose sobre el bitmap conforme se movía el slider (confirmando que
  el recorte de vista previa en memoria funciona sin recargar el PDF) →
  ajustado a 19% de margen → ejecutado → mensaje de éxito "PDF recortado
  correctamente — 19% de margen" → guardado en Descargas → archivo
  descargado: "SECRETO CONFIDENCIAL", que estaba muy cerca del borde
  superior recortado, ya no aparece en el contenido visible del PDF
  resultante — coincide con el efecto esperado del recorte aplicado en
  pantalla.
- Verificado también: `testDebugUnitTest`/`detekt`/`lintDebug` en verde.
