# Auditoría general de punta a punta — 2026-09-17/18 (décima ronda)

**Estado: ✅ 14/15 corregidos (1 evaluado y no corregido, ver C2),
pendiente de revisión adversarial y aprobación de fusión.** Pedida por
el usuario ("dale, seguí con una nueva ronda") tras la novena pasada
(verificación funcional en vivo, sin cambios de código). Cubre áreas sin
revisión dedicada hoy:
Herramientas PDF de punta a punta, Notas/Lectura de Modo Estudio,
Papelera de punta a punta, y fidelidad de formato específica del
Convertidor (Word/Excel/PowerPoint).

Metodología: 4 agentes en paralelo, uno por área, contrastando contra los
7 backlogs previos del día para no repetir hallazgos.

## Herramientas PDF

### H1 — Lienzo de Firmar sin accesibilidad TalkBack (bloquea la herramienta)
**Estado: ✅ Corregido** · **Severidad: Alta** · `features/pdftools/presentation/components/SignPdfScreen.kt:199-219`

El `Box` con `detectDragGestures` donde se dibuja la firma no tiene
`semantics{}`/`contentDescription`. No existe alternativa (no hay opción
de importar una imagen de firma), así que un usuario de TalkBack no
puede completar "Firmar" -- el botón de ejecutar queda deshabilitado
para siempre porque `hasSignature` nunca puede pasar a `true`.

### H2 — Lienzo de Censurar (Redact) sin accesibilidad TalkBack (bloquea la herramienta)
**Estado: ✅ Corregido** · **Severidad: Alta** · `features/pdftools/presentation/components/RedactPdfScreen.kt:222-253`

Mismo patrón que H1: la única forma de agregar una zona de censura es
el gesto de arrastre sobre un `Box` sin semántica. "Deshacer"/"Borrar
todo" sí son accesibles, pero crear una zona no. Mayor impacto que H1:
una censura mal hecha o inexistente expone datos sensibles.

### H3 — Reordenar páginas permite ejecutar sobre un PDF de 1 sola página (no-op que gasta cuota)
**Estado: ✅ Corregido** · **Severidad: Baja-Media** · `features/pdftools/presentation/PdfToolsViewModel.kt:262`,
`components/ReorderPagesScreen.kt:147`

Con 1 página no hay reorden posible, pero el botón "Reordenar" sigue
habilitado. El usuario gasta uno de sus 3 usos diarios y obtiene un
archivo idéntico al original con otro nombre.

### H4 — Mensaje de éxito de Censurar puede mentir sobre cuántas zonas se aplicaron
**Estado: ✅ Corregido** · **Severidad: Baja** · `features/pdftools/domain/usecase/RedactPdfUseCase.kt:96-104,112-117`

El mensaje usa `rects.size` (total original) en vez de `locations.size`
(las realmente aplicadas tras filtrar por página fuera de rango). No
alcanzable desde la UI actual, pero es un hueco latente.

## Convertidor

### C1 — Documentos de Office protegidos con contraseña reciben mensajes de error incorrectos
**Estado: ✅ Corregido** · **Severidad: Alta** · `ExcelToHtmlUseCase.kt:36-40`, `PptToPdfUseCase.kt:50-59`,
`PptToTextUseCase.kt:31-35`, `WordFormatDetection.kt`, `WordToTextUseCase.kt`/`WordToHtmlUseCase.kt`/`WordToPdfUseCase.kt`

Un `.docx`/`.xlsx`/`.pptx` protegido con contraseña de Office tiene la
misma firma binaria OLE2 que un archivo legado `.doc`/`.xls`/`.ppt`
real. `isLegacyOle2Uri()` los confunde: el usuario recibe "Guardalo como
.xlsx e intentá de nuevo" sobre un archivo que YA es `.xlsx`. Para Word,
termina lanzando una excepción cruda de Apache POI sin traducir. Ningún
use case tiene manejo dedicado a "documento con contraseña".

### C2 — Celdas combinadas en Excel→HTML rompen la alineación de la tabla
**Estado: ⚠️ Evaluado, no corregido (ver nota)** · **Severidad: Media** ·
`converter/domain/usecase/ExcelToHtmlUseCase.kt:71-91,120-128`

No lee `<mergeCells>` del worksheet ni genera `colspan`/`rowspan` -- una
hoja con celdas combinadas (típico en encabezados de reportes) se
renderiza desalineada frente a las filas normales.

**Nota de evaluación:** un fix correcto de verdad requiere que el parser
rastree el índice de columna REAL de cada celda (vía su atributo `r`,
ej. "B3") -- hoy no lo hace, agrega celdas secuencialmente asumiendo sin
huecos. Las celdas cubiertas por un merge (salvo la superior-izquierda)
suelen aparecer como `<c r="B2" s="1"/>` autocerrado (solo estilo, sin
valor), que el regex actual (`<c[^>]*>(.*?)</c>`, exige `</c>`) ni
siquiera captura -- ya hoy faltan de la lista de celdas, así que agregar
`colspan` sin resolver esa base dejaría el mismo desalineamiento de
fondo. Reescribir el núcleo del parser de Excel (compartido y ya
probado en producción) conlleva más riesgo del que amerita un hallazgo
Medio puramente cosmético -- queda para una pasada futura dedicada.

## Papelera

### P1 — Purga automática vulnerable a adelantar el reloj del sistema -- pérdida de datos permanente
**Estado: ✅ Corregido** · **Severidad: Alta** · `features/library/data/TrashRepository.kt:193` (`purgeExpiredTrash`)

A diferencia del PIN y el límite diario (ya blindados hoy con
`elapsedRealtime`), la purga automática de Papelera (30 días) usa solo
reloj de pared, y corre SIN ningún diálogo de confirmación (a diferencia
de "Eliminar definitivamente"/"Vaciar todo", que sí confirman).
Adelantar la fecha del sistema 31+ días y abrir Papelera borra todo de
forma irreversible y automática.

### P2 — "Eliminar definitivamente"/"Vaciar todo" sin guard de doble-toque
**Estado: ✅ Corregido** · **Severidad: Media** · `features/library/presentation/TrashScreen.kt:105-108`,
`TrashViewModel.kt:87,112`

Un doble-toque rápido en "Eliminar" puede llamar `deleteForever()` dos
veces: la segunda encuentra el archivo ya borrado y muestra "No se pudo
eliminar" después de un borrado exitoso (mensaje engañoso). En "Vaciar
todo", el `permissionLauncher.launch()` puede invocarse dos veces casi
simultáneas.

### P3 — Restaurar un documento cuyo archivo ya no existe falla en silencio
**Estado: ✅ Corregido** · **Severidad: Media** · `features/library/presentation/TrashViewModel.kt:80-85` (`restore`)

Solo borra la fila de `trash_entries` sin verificar que el archivo siga
en disco. Si se borró por fuera de la app, el documento desaparece sin
ningún aviso (a diferencia de `deleteForever`, que sí informa error).

### P4 — Reloj atrasado infla "días restantes" más allá de 30
**Estado: ✅ Corregido** · **Severidad: Baja** · `features/library/presentation/TrashViewModel.kt:71-73`

`daysRemaining = (30 - elapsedDays).coerceAtLeast(0)` sin
`coerceAtMost(30)` -- con `elapsedDays` negativo (reloj atrasado) muestra
p. ej. "35 días restantes". Cosmético.

## Notas y Lectura (Modo Estudio)

### N1 — Exportar Notas corre en el hilo principal sin guard de doble-toque (riesgo real de ANR)
**Estado: ✅ Corregido** · **Severidad: Alta** · `features/study/presentation/StudyScreen.kt:2686-2707` (`shareStudyNotes`)

A diferencia de `ViewerViewModel.shareWithAnnotations()` (ya usa
`viewModelScope.launch` + flag de carga), acá el I/O + decodificación de
imágenes con iText/POI corre síncrono en el hilo principal. Con varias
notas con imágenes ("Exportar todas") es riesgo real de ANR, y sin guard
un doble-toque puede lanzar 2 exportaciones (nombre con timestamp a
nivel de segundo, pueden colisionar).

### N2 — Doble-toque al editar una nota con imágenes nuevas duplica los adjuntos
**Estado: ✅ Corregido** · **Severidad: Media** · `StudyScreen.kt` (`NoteEditDialog`), `NotesViewModel.kt:97-110` (`updateNote`)

Mismo patrón que ya se corrigió para "Guardar" de Agenda (A3, ronda
sexta) pero nunca se replicó acá: `editingNoteId` recién se limpia
DESPUÉS de que `updateNote()` termine. Con imágenes nuevas adjuntas, un
doble-toque real las copia e inserta dos veces. El caso de nota NUEVA sí
está protegido; esto es específico de editar.

### N3 — Sin captura de `OutOfMemoryError` en la extracción de texto de Lectura
**Estado: ✅ Corregido** · **Severidad: Media** · `StudyScreen.kt:3444-3487` (`extractPdfText`/`extractPdfPages`)

El mismo patrón (P1, ronda quinta) ya se corrigió en 11 conversores del
Convertidor pero nunca se extendió a Lectura, que acumula todos los
párrafos de un PDF grande en memoria. Un libro/apunte muy largo puede
matar el proceso en vez de mostrar el error controlado que ya se ve con
documentos corruptos.

### N4 — Registros huérfanos de progreso de lectura si se borra/mueve el documento
**Estado: ✅ Corregido** · **Severidad: Media** · `StudyReadingProgressStorage.kt`, `StudyScreen.kt:3452-3466`

Nunca verifica que el URI siga existiendo. Si el documento se
elimina/mueve mientras hay progreso guardado, "Continuar leyendo"
muestra el texto de error de extracción COMO SI FUERA UN PÁRRAFO real
del documento, en vez de avisar que ya no existe, y la entrada queda
fantasma en el historial.

### N5 — Título/texto de notas de Estudio sin límite de longitud
**Estado: ✅ Corregido** · **Severidad: Baja** · `StudyScreen.kt` (creación/edición de notas)

A diferencia del campo "Nota" del Visor (límite de 2000 caracteres ya
aplicado), el título/texto de Notas de Estudio no tiene ningún límite.
Inconsistencia menor.

## Confirmado correcto (no son bugs, sin cambios)

- Guard de doble-toque, `OutOfMemoryError`, saneo de nombre y registro
  del límite diario: centralizados en `PdfToolsViewModel.execute()`,
  aplican igual a las 15 herramientas PDF.
- `DailyLimitManager.PDF_TOOL_KEYS` completo, las 15 claves correctas.
- Comparar con 2 PDFs idénticos, Dividir con 1 página, Redactar sin
  marcar zonas: todos manejados correctamente.
- `PdfToImageUseCase`: guardado incremental correcto (no acumula en
  memoria), sin riesgo de OOM pese a no tener límite de páginas.
- Codificación UTF-8 explícita en los 4 conversores a texto/CSV.
- Saneo de nombre de archivo consistente en los 14 use cases del
  Convertidor.
- Recordatorios de repaso de notas: idempotentes, no duplicables por
  doble-toque.
- Accesibilidad de Lectura/Notas (fuera de N/A): controles con
  `contentDescription` adecuado.
- Papelera: sin selección múltiple (no aplica ese caso borde); doble-
  restaurar no es un problema real (`TrashDao.remove()` idempotente).
- Profundización sobre A5 (huérfanos SAF, ronda quinta): confirma que
  sigue siendo razonable no corregirlo ahora (requeriría migración de
  esquema para guardar nombre/tamaño, más riesgo del que vale la pena).

## Revisión adversarial (2 agentes en paralelo, seguridad + correctitud)

Encontró 4 hallazgos reales sobre el propio lote, todos corregidos:

1. **`SignPdfScreen.kt` -- firma escrita, 2 bugs de integridad (Media)**:
   (a) `isBlank()` no cubre caracteres Unicode de formato (espacio de
   ancho cero, categoría `Cf`) -- un texto compuesto solo por esos
   caracteres generaba una firma visualmente en blanco pero
   `hasSignature=true`. (b) "Firma fantasma": al vaciar el campo,
   `hasSignature`/`signatureImageBytes` nunca se reseteaban en el
   ViewModel -- el PDF se firmaba igual con el último texto válido
   tecleado aunque el campo se viera vacío. Corregido filtrando
   caracteres de formato antes de decidir si hay contenido real, y
   llamando a `onClearSignature()` cuando no lo hay.
2. **`TrashRepository.kt` -- candidato de purga huérfano tras borrado
   definitivo (Media, privacidad)**: `deleteForever()`/
   `finalizeDeleteForever()` (ambas sobrecargas)/`deleteAllForever()` no
   limpiaban la entrada de `purgeCandidatePrefs` (P1) -- el `documentId`
   (ruta/URI real, potencialmente sensible) quedaba en SharedPreferences
   para siempre después de un borrado que se supone completo. Corregido
   agregando `clearPurgeCandidate()` a los 3 caminos.
3. **`StudyScreen.kt` -- `CancellationException` tragada en
   `shareStudyNotes()` (Media)**: si el usuario navega fuera de la
   pantalla mientras se exporta, el `catch (e: Exception)` genérico
   atrapaba también la cancelación de la corrutina, rompiendo la
   cancelación estructurada. Corregido relanzándola siempre (mismo
   patrón ya usado en `OcrPdfUseCase`/`CompressPdfUseCase`).
4. **`TrashRepository.kt` -- `documentId` embebido en logs nuevos
   enviados a Crashlytics (Baja-Media)**: mismo patrón que la fuga ya
   corregida para `DownloadsAccessManager` (octava ronda) -- se omitió
   el identificador de los 3 mensajes de log nuevos de esta ronda.

## Resumen por severidad

- **Alta (5):** H1 (firma sin TalkBack), H2 (censura sin TalkBack), C1
  (Office con contraseña), P1 (purga automática vulnerable al reloj),
  N1 (exportar notas en hilo principal).
- **Media (6):** C2 (celdas combinadas Excel), P2 (doble-toque Papelera),
  P3 (restaurar archivo inexistente), N2 (doble-toque editar nota), N3
  (OOM en Lectura), N4 (progreso huérfano).
- **Baja/Baja-Media (4):** H3 (reordenar 1 página), H4 (mensaje censura),
  P4 (días restantes), N5 (límite de longitud de notas).

## Plan de trabajo

Corregir por severidad (Alta → Media → Baja), gauntlet completo +
revisión adversarial antes de pedir aprobación de fusión, mismo criterio
que las rondas anteriores.
