# Módulo: Herramientas PDF

> Parte de [`CONTEXT.md`](../../CONTEXT.md). Cubre: Unir, Dividir, Comprimir y
> Rotar PDF. Proteger/quitar contraseña de PDF ya está formalizado en
> [`security.md`](security.md) (RF-SEC-10/11/12, HU-SEC-07/08) — no se repite aquí.

**Estado:** bug de arquitectura crítico corregido (Unir y Rotar rasterizaban
cada página a imagen, destruyendo el texto seleccionable); "Dividir no
funciona" de la QA de mayo confirmado como **obsoleto** mediante tests reales
sobre PDFs generados con iText7; 8 tests unitarios nuevos, todos en verde.
Pendiente: i18n de las 4 pantallas (aún en español fijo, ver §5), y todas las
funcionalidades nuevas listadas en §2 como backlog.
**Código relacionado:** `features/pdftools/**`.

---

## 1. Alcance

Cuatro operaciones sobre PDF ya construidas, más un backlog amplio pedido en
el requerimiento #3 original que todavía no existe:

1. **Unir** — combinar 2+ PDFs en uno solo, en el orden seleccionado.
2. **Dividir** — extraer un rango de páginas a un PDF nuevo.
3. **Comprimir** — reducir el tamaño del archivo.
4. **Rotar** — rotar todas las páginas 90°/180°/270°.
5. *(Backlog, no implementado)* numerar páginas, marca de agua, reordenar/eliminar
   páginas individuales, recortar, editar contenido, firma digital, formularios,
   comparar dos PDFs, censurar contenido, OCR avanzado.

---

## 2. Requerimientos funcionales

### Implementados
- **RF-PDF-01** El sistema debe permitir unir 2 o más PDFs en uno solo, conservando el contenido original de cada página (texto/vectores, no una captura de pantalla de la página).
- **RF-PDF-02** El sistema debe permitir extraer un rango de páginas (`desde`–`hasta`) de un PDF a un archivo nuevo, sin alterar el PDF original.
- **RF-PDF-03** El sistema debe permitir comprimir un PDF con un nivel de calidad seleccionable (20–100), informando el tamaño antes/después y el porcentaje de reducción.
- **RF-PDF-04** El sistema debe permitir rotar todas las páginas de un PDF 90°, 180° o 270°, conservando el contenido original (texto/vectores).
- **RF-PDF-05** Tras cualquier operación exitosa, el sistema debe mostrar el nombre y tamaño del archivo resultante, y ofrecer guardarlo en Descargas o compartirlo directamente.

### Backlog — nuevas funcionalidades (mejoras sugeridas 2026-08-24, no implementadas)
- **RF-PDF-06** Numerar páginas (pie de página con número, formato configurable).
- **RF-PDF-07** Marca de agua de texto sobre todas las páginas.
- **RF-PDF-08** Reordenar y/o eliminar páginas individuales (vista de miniaturas arrastrable).
- **RF-PDF-09** Recortar (crop) márgenes de página.
- **RF-PDF-10** Edición básica de contenido (texto/imágenes existentes).
- **RF-PDF-11** Firma digital de PDF.
- **RF-PDF-12** Detección y relleno de formularios PDF.
- **RF-PDF-13** Comparar dos versiones de un PDF y resaltar diferencias.
- **RF-PDF-14** Censurar (redactar) contenido sensible de forma irreversible.
- **RF-PDF-15** OCR avanzado sobre PDFs escaneados (texto ya buscable vía Modo Estudio para imágenes sueltas; falta aplicado a PDF completo).

Prioridad sugerida dentro del backlog (esfuerzo vs. valor percibido):
**alta** → RF-PDF-06, RF-PDF-07, RF-PDF-08 · **media** → RF-PDF-13, RF-PDF-14 ·
**baja/futuro** → RF-PDF-09, RF-PDF-10, RF-PDF-11, RF-PDF-12, RF-PDF-15
(requieren más superficie de UI o licenciamiento adicional de iText7 para
firma/formularios avanzados).

---

## 3. Requerimientos no funcionales

- **RNF-PDF-01 (preservar contenido vectorial):** Unir, Dividir y Rotar deben operar sobre el PDF a nivel de página (iText7 `copyPagesTo`/`setRotation`), nunca rasterizando a imagen — el texto debe seguir siendo seleccionable y buscable en el resultado. **✅ Cumplido hoy** para las 3 (Unir y Rotar migrados desde un enfoque de bitmap que lo violaba). Comprimir es la única excepción deliberada: reducir tamaño de forma significativa requiere recodificar imágenes/rasterizar, así que se acepta perder texto seleccionable en esa operación específica.
- **RNF-PDF-02 (nombre de archivo consistente):** todo archivo generado por Herramientas PDF debe llevar el prefijo `DocuSmart_` seguido de un nombre descriptivo y timestamp. **✅ Cumplido hoy** en las 4 herramientas (antes solo Unir y Rotar lo tenían).
- **RNF-PDF-03 (no bloquear UI):** toda operación debe ejecutarse en `Dispatchers.IO`, nunca en el hilo principal. **✅ Ya cumplido.**
- **RNF-PDF-04 (mensajes de error):** los mensajes no deben filtrar rutas de archivo completas ni detalles internos de excepciones (mismo lineamiento que RNF-SEC-05).
- **RNF-PDF-05 (feedback tras operación exitosa):** nombre, tamaño y opciones de guardar/compartir deben mostrarse siempre, sin pasos adicionales. **✅ Ya cumplido** (`ToolSuccessCard`, compartido por las 4 herramientas).

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

### HU-PDF-05 — Numerar páginas *(backlog, no implementado)*
**Como** usuario que va a imprimir o distribuir un PDF largo,
**quiero** agregar numeración automática,
**para** que sea fácil referenciar páginas específicas.

- **AC1** Dado que activo "Numerar páginas", cuando confirmo, entonces cada página del resultado muestra su número en el pie, en el formato "Página X de N".

### HU-PDF-06 — Marca de agua *(backlog, no implementado)*
**Como** usuario que comparte un borrador o documento confidencial,
**quiero** superponer un texto de marca de agua,
**para** dejar claro el estado o la propiedad del documento.

- **AC1** Dado que escribo un texto de marca de agua y confirmo, cuando se genera el resultado, entonces el texto aparece superpuesto (diagonal, semitransparente) en todas las páginas.

### HU-PDF-07 — Reordenar y eliminar páginas *(backlog, no implementado)*
**Como** usuario que necesita ajustar el orden de un PDF,
**quiero** ver miniaturas de las páginas y arrastrarlas o eliminarlas,
**para** corregir el documento sin herramientas externas.

- **AC1** Dado que abro la vista de miniaturas de un PDF, cuando arrastro una página a otra posición, entonces el PDF resultante refleja el nuevo orden.
- **AC2** Dado que marco una página para eliminar, cuando confirmo, entonces el resultado no la incluye.

---

## 5. Deuda técnica y pendientes fuera de HU

- **i18n:** de las 4 pantallas de Herramientas PDF, solo `PdfToolsScreen.kt` tiene 2 strings conectados a `stringResource()` (título/subtítulo del banner); el resto (menú, las 4 sub-pantallas, `ToolSuccessCard`, y los mensajes de error/éxito de los 4 use cases) sigue en español fijo — mismo patrón que Seguridad/QR/Estudio antes de i18n. No se abordó en esta sesión por ser un trabajo mecánico grande sin bug funcional asociado; queda como siguiente paso natural cuando se retome este módulo.
- **Selector de archivo:** las 4 herramientas solo permiten elegir un PDF desde el selector del dispositivo (SAF), no desde la Biblioteca de la app — mismo gap que tenía Seguridad antes de corregirse (RF-SEC-04/HU-SEC-04 AC3).
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
| Faltan: contraseña, quitar contraseña, eliminar página, reordenar, firma, recorte, marca de agua, numeración, editar, formularios, comparar, censurar | Contraseña/quitar contraseña → `security.md` (ya implementado). El resto → RF-PDF-06 a RF-PDF-15 (backlog, §2). | Backlog documentado, no implementado. |

---

## 7. Plan de pruebas unitarias

| # | Cobertura | Estado |
|---|---|---|
| 1 | `SplitPdfUseCaseTest` — extrae rango correcto (no el documento completo), rango completo = mismo total de páginas, rango fuera de límites se ajusta, archivo no-PDF → Error. | ✅ 4 tests, en verde |
| 2 | `MergePdfUseCaseTest` — unir 2 PDFs suma páginas de ambos, menos de 2 PDFs → Error. | ✅ 2 tests, en verde |
| 3 | `RotatePdfUseCaseTest` — 90° se escribe en todas las páginas, rotación acumulada sobre una página ya rotada (180°+270°=90°). | ✅ 2 tests, en verde |
| 4 | `CompressPdfUseCase` — no cubierto (usa `android.graphics.pdf.PdfRenderer`, requiere Robolectric/instrumentación; mismo límite que ya aplicaba a Compress y a la vista previa de Rotate). | Pendiente |

Todos los tests generan PDFs reales en memoria con iText7 (mismo patrón que
`PdfPasswordUseCaseTest`), no mocks del contenido del PDF — el conteo de
páginas y la rotación se verifican leyendo el archivo de salida real.

---

## 8. Preguntas abiertas

| Pregunta | Notas |
|---|---|
| ¿Prioridad real dentro del backlog (§2)? | Se propuso alta/media/baja por esfuerzo-valor; confirmar con el usuario antes de implementar la siguiente tanda. |
| ¿Vale la pena un modo "comprimir conservando texto" (solo recomprimir imágenes embebidas)? | Depende de si el caso de uso principal son PDFs escaneados (ya son imagen, no pierden nada) o documentos de texto exportados (sí perderían selección de texto). |
| ¿Selección de archivo desde la Biblioteca de la app, no solo desde el dispositivo? | Mismo patrón ya resuelto para Seguridad (HU-SEC-04 AC3); replicar aquí cuando se aborde i18n/UI de este módulo. |
