# Módulo: Modo Estudio

> Parte de [`CONTEXT.md`](../../CONTEXT.md). Cubre lectura con voz (TTS),
> notas (texto y voz) y Pomodoro — las 3 pestañas de `StudyScreen.kt`.

**Estado:** 2 bugs reales corregidos, uno de ellos silenciosamente
corrompía el contenido de las notas del usuario en cada guardado. La nota de
`CONTEXT.md` que daba por "corregido" el guardado de notas resultó
**incorrecta al revisar el código con más detalle** — sí existe una lista de
notas guardadas (contrario a lo que indicaba la QA de mayo), pero su
serialización manual tenía dos bugs reales que nadie había detectado. 5 tests
nuevos.
**Código relacionado:** `features/study/presentation/StudyScreen.kt`,
`features/study/domain/StudyNotesStorage.kt` (nuevo).

---

## 1. Alcance

Tres pestañas dentro de una sola pantalla (`StudyScreen.kt`, sin ViewModel —
ver §3 nota de arquitectura):

1. **Lectura** — muestra el documento importado por párrafos, con
   texto-a-voz (TTS) para lectura en voz alta, resaltando el párrafo actual.
2. **Notas** — texto libre o dictado por voz, con lista de notas guardadas
   (título, texto, fecha), persistidas en `SharedPreferences`.
3. **Pomodoro** — temporizador de 25/5 minutos con contador de ciclos.

---

## 2. Requerimientos funcionales

- **RF-STU-01** El sistema debe permitir importar un documento (PDF, Word, PowerPoint, texto plano) y extraer su contenido en párrafos para lectura.
- **RF-STU-02** El sistema debe leer el contenido en voz alta (TTS), en el idioma configurado del dispositivo, resaltando el párrafo actual.
- **RF-STU-03** El sistema debe permitir dictar o escribir notas y guardarlas con título, texto y fecha/hora.
- **RF-STU-04** Las notas guardadas deben persistir entre sesiones — cerrar y reabrir la app no debe perderlas ni corromper su contenido (comillas, saltos de línea, etc.).
- **RF-STU-05** Las notas guardadas deben mostrarse en orden de más reciente a más antigua.
- **RF-STU-06** El sistema debe permitir eliminar una nota individual o todas a la vez (con confirmación).
- **RF-STU-07** El sistema debe ofrecer un temporizador Pomodoro (25 min de estudio / 5 min de descanso) con contador de ciclos completados.

### Backlog — no implementado
- **RF-STU-08** Exportar notas (compartir como texto o PDF).
- **RF-STU-09** Estadísticas de estudio (tiempo total leído, pomodoros completados por semana).
- **RF-STU-10** Temporizador Pomodoro que siga corriendo en segundo plano (hoy se detiene si se navega fuera de la pantalla — es un `LaunchedEffect` que se cancela al salir de la composición, no un servicio en segundo plano).

---

## 3. Requerimientos no funcionales

- **RNF-STU-01 (persistencia sin corrupción):** cualquier texto que el usuario escriba en una nota (comillas, saltos de línea, cualquier carácter) debe guardarse y recuperarse exactamente igual — sin reemplazos silenciosos de caracteres.
- **RNF-STU-02 (idioma de lectura en voz alta):** el TTS debe intentar primero el idioma configurado del dispositivo, igual que ya hace el reconocimiento de voz al dictar una nota — no debe forzar un idioma fijo sin importar la configuración del usuario.
- **RNF-STU-03 (nota de arquitectura):** este módulo es la única pantalla grande del proyecto sin `ViewModel` — todo el estado (documento, notas, Pomodoro, TTS) vive en `remember {}` dentro del Composable. Esto significa que no es testable con pruebas de ViewModel como el resto de módulos, y que el estado se pierde en cualquier recomposición que destruya la instancia (no en rotación, que Compose maneja, pero sí si el proceso muere). No se refactorizó en esta pasada — es un cambio de arquitectura más grande que un bug fix puntual.
- **RNF-STU-04 (el modo lectura es texto plano a propósito, no por descuido):** a diferencia del Visor (ver RF-VIS-10 en `visor-biblioteca.md`), Modo Estudio **no** intenta mostrar el documento con su aspecto visual original — la función central de esta pantalla es narrar el texto en voz alta y resaltar el párrafo que se está leyendo, algo que solo es viable sobre texto real extraído, no sobre una imagen renderizada del documento. El usuario confirmó explícitamente esta prioridad al elegir "solo mejorar la calidad del texto" frente a la opción de intentar una vista más visual. Lo que sí se corrigió es la CALIDAD de esa extracción de texto (ver HU-STU-04) — no su naturaleza de texto plano.

---

## 4. Historias de usuario con criterios de aceptación

### HU-STU-01 — Guardar una nota sin perder su contenido
**Como** usuario que toma notas mientras estudio,
**quiero** que el texto se guarde exactamente como lo escribí,
**para** no perder información ni encontrar errores extraños al releerla.

- **AC1** Dado que escribo una nota con comillas dobles (`"así"`), cuando la guardo y vuelvo a abrir la app, entonces las comillas siguen siendo comillas dobles, no comillas simples.
- **AC2** Dado que escribo una nota con varios saltos de línea, cuando la guardo y la recargo, entonces los saltos de línea se conservan exactamente.

*(Corrige bug real: el serializador manual de notas hacía `texto.replace("\"", "'")` antes de guardar — cualquier comilla doble en una nota se convertía silenciosamente en comilla simple, en cada guardado, sin ningún aviso al usuario.)*

### HU-STU-02 — Ver las notas en el orden correcto
**Como** usuario con varias notas guardadas,
**quiero** ver la más reciente primero,
**para** encontrar rápido lo que acabo de escribir.

- **AC1** Dado que tengo notas guardadas de sesiones anteriores, cuando reabro la app y entro a Estudio → Notas, entonces la nota más reciente aparece primero en la lista.
- **AC2** Este orden debe ser el mismo tanto si acabo de guardar la nota en esta sesión como si la cargué desde una sesión anterior.

*(Corrige bug real: `loadNotes()` aplicaba un `.reversed()` de más sobre una lista que ya venía ordenada de más nueva a más vieja — al recargar la pantalla, el orden se invertía silenciosamente a más vieja primero, aunque dentro de la misma sesión (sin recargar) sí se veía correcto.)*

### HU-STU-03 — Leer en voz alta en el idioma del dispositivo
**Como** usuario que configuró la app en un idioma distinto al español,
**quiero** que la lectura en voz alta use ese idioma,
**para** entender lo que se lee.

- **AC1** Dado que mi dispositivo tiene un idioma configurado distinto al español y ese idioma tiene voz TTS disponible, cuando uso "Leer en voz alta", entonces se lee en ese idioma, no en español.
- **AC2** Dado que el idioma configurado no tiene voz TTS disponible en el dispositivo, cuando esto ocurre, entonces se usa español como respaldo, no falla en silencio.

*(Corrige bug real: la inicialización de TTS forzaba `Locale("es","ES")` incondicionalmente, sin importar el idioma configurado — mismo tipo de bug ya corregido antes para el reconocimiento de voz al dictar, pero que persistía en el lado de lectura en voz alta.)*

### HU-STU-04 — Los párrafos de lectura son párrafos reales
*(Implementado 2026-08-29 — ver §8. Reportado por el usuario en uso manual, agrupado con el mismo hallazgo del Visor — ver RNF-STU-04 para por qué la solución NO es la misma que en el Visor.)*

**Como** usuario que usa "leer este párrafo" sobre un PDF,
**quiero** que lea un párrafo real, no medio renglón cortado a mitad de
frase,
**para** que la narración tenga sentido y el resaltado marque bloques de
texto coherentes.

- **AC1** Dado que abro un PDF cuyo texto se ajusta en varias líneas
  visuales dentro del mismo párrafo, cuando lo veo en Modo Estudio,
  entonces esas líneas aparecen como UN párrafo — no uno por cada línea
  del PDF.
- **AC2** Dado que el PDF tiene un salto real entre párrafos (mayor
  espaciado vertical), cuando lo veo, entonces ese salto sí separa dos
  párrafos distintos.
- **AC3** Dado que abro un Word con un encabezado, cuando lo veo en Modo
  Estudio, entonces ese párrafo aparece en negrita y con color distintivo
  — igual que ya distingue el Visor de Word — para ubicarlo de un vistazo
  dentro de la narración.

*(Corrige bug real: `extractPdfText()` dividía por cada `\n` de
`PdfTextExtractor.getTextFromPage()` — una oración de una sola idea que el
PDF ajusta en 2-3 líneas se leía y resaltaba como 2-3 "párrafos"
distintos. `extractWordText()` tampoco distinguía encabezados en
absoluto, a diferencia del Visor de Word, que sí lo hacía desde antes.)*

---

## 5. Bugs de QA a corregir (trazabilidad)

| Bug (barrido de pruebas v1.0 / mejoras pendientes v1.0.1, y nota de `CONTEXT.md`) | HU que lo cubre | Estado |
|---|---|---|
| Documento de mayo: "faltaba guardado/lista de notas" — nota de `CONTEXT.md`: "ya tiene lista de notas guardadas, parece corregido" | — | **Parcialmente correcto, pero incompleto** — sí existe la lista de notas guardadas (`savedNotes`, con título/texto/fecha, guardar/eliminar), pero su serialización tenía 2 bugs reales no detectados hasta esta auditoría (HU-STU-01, HU-STU-02). El hallazgo de mayo de que "faltaba" ya no aplica; lo que sí aplicaba, y no se había visto, era la corrupción y el orden. |
| **Bug real encontrado hoy (no reportado en la QA):** comillas dobles en una nota se convertían en comillas simples en cada guardado. | HU-STU-01 | ✅ Corregido — reemplazado el serializador manual por `org.json` (parte del SDK de Android), que escapa correctamente cualquier carácter. |
| **Bug real encontrado hoy (no reportado en la QA):** el orden de las notas se invertía al recargar la pantalla (más vieja primero en vez de más nueva primero). | HU-STU-02 | ✅ Corregido — se quitó el `.reversed()` de más en `loadNotes()`. |
| **Bug real encontrado hoy (no reportado en la QA):** la lectura en voz alta forzaba español sin importar el idioma configurado del dispositivo — mismo bug ya corregido para el reconocimiento de voz, pero no para el TTS. | HU-STU-03 | ✅ Corregido — ahora intenta `Locale.getDefault()` primero, español como respaldo. |
| "Lectura se ve como texto plano — mejorar presentación visual" | HU-STU-04 | ✅ Parcialmente abordado 2026-08-29 — corregida la calidad real del texto extraído (párrafos reales de PDF, encabezados de Word), no la presentación visual como el documento original: el modo lectura sigue siendo texto plano **a propósito**, ver RNF-STU-04. |

---

## 6. Plan de pruebas unitarias

| # | Cobertura | Estado |
|---|---|---|
| 1 | `StudyNotesStorageTest` — comillas dobles sobreviven el round-trip, saltos de línea reales sobreviven el round-trip, el orden guardado (más nuevo primero) se preserva al recargar, sin notas guardadas devuelve lista vacía, JSON corrupto en preferencias devuelve lista vacía en vez de fallar. | ✅ 5 tests, en verde |
| 2 | `StudyTextExtractionTest` — agrupación de párrafos reales de PDF por espaciado vertical (líneas ajustadas quedan en el mismo párrafo, salto grande crea uno nuevo, párrafos muy cortos se descartan, sin fragmentos no hay párrafos), detección de encabezado de Word (incluyendo el bug real del identificador de estilo en español, `w:val="Ttulo1"`). | ✅ 7 tests, en verde |
| 3 | Resto de `StudyScreen.kt` (TTS, Pomodoro, resto de extracción PPT/texto plano) — no cubierto. Son funciones/composables sin ViewModel (ver RNF-STU-03); las funciones de extracción de PDF/Word de la fila 2 se expusieron como `internal` (mismo patrón que `StudyNotesStorage`) puntualmente para poder probarlas, sin refactorizar el resto del módulo. | Pendiente si se decide abordar la deuda de arquitectura completa. |

Se agregó `testImplementation("org.json:json:20231013")` en `app/build.gradle.kts`
— el stub de Android para unit tests deja `org.json.*` sin implementar
("not mocked"), así que se necesita la implementación real solo para pruebas
(no afecta el runtime de la app, que usa la versión de Android normalmente).

---

## 7. Preguntas abiertas

| Pregunta | Notas |
|---|---|
| ¿Vale la pena refactorizar `StudyScreen.kt` a una arquitectura con `ViewModel` (como el resto de módulos)? | Es la única pantalla grande sin esa estructura; permitiría testear TTS/Pomodoro/extracción y sería más consistente, pero es un refactor grande, no un bug fix. |
| ¿Prioridad de exportar notas (RF-STU-08) vs. estadísticas de estudio (RF-STU-09) vs. Pomodoro en segundo plano (RF-STU-10)? | Los 3 son mejoras sugeridas ya documentadas en `CONTEXT.md`, sin refinar todavía. |

---

## 8. HU-STU-04 — Párrafos reales en el modo lectura (2026-08-29)

Reportado por el usuario en uso manual junto con el mismo hallazgo del
Visor (ver §14 de `visor-biblioteca.md`): "cuando tomo un archivo para
usar para lectura presenta el mismo problema, muestra el texto, y la voz
narra el texto pero en un archivo plano". Investigado por separado porque
la causa y la solución correcta NO son las mismas que en el Visor — ver
RNF-STU-04 para por qué el modo lectura sigue siendo texto plano a
propósito (la narración por voz y el resaltado de palabras no son
viables sobre una imagen renderizada del documento).

**PDF — párrafos por espaciado vertical real, no por cada salto de
línea:** `extractPdfText()` dividía el texto de cada página con
`pageText.split("\n")` — cualquier oración que el PDF ajustara en 2-3
líneas visuales se guardaba y se leía en voz alta como 2-3 "párrafos"
distintos, cortados a mitad de frase. Reemplazado por un recorrido con
`PdfCanvasProcessor` + `StudyPdfLineListener` (mismo mecanismo que
`PdfToWordUseCase`, RF-CONV-09) que agrupa fragmentos de texto por la
coordenada Y real de su línea base: un salto vertical mayor a 1.6x el
tamaño de fuente del fragmento siguiente = párrafo nuevo; uno menor =
ajuste de línea dentro del mismo párrafo lógico (`groupPdfChunksIntoParagraphs()`).

**Word — encabezados detectados, igual que el Visor:**
`extractWordText()` convertía cada `<w:p>` en un salto de línea ANTES de
quitar las etiquetas, perdiendo la información de `<w:pPr>` necesaria
para saber si un párrafo es un encabezado — Estudio nunca distinguía
encabezados, ni siquiera antes de este hallazgo. Reescrito con el mismo
enfoque por bloque `<w:p>` que ya usaba `WordViewerContent`
(`parseWordParagraphsWithHeadings()`), incluyendo el mismo fix del
identificador de estilo real que escribe Word en español (`w:val="Ttulo1"`,
no `"Heading1"` — ver §14 de `visor-biblioteca.md` para el hallazgo
completo). Los encabezados detectados se resaltan en negrita/color
primario en la lista de lectura sin tocar la lógica existente de
resaltado manual (`highlights: Set<Int>`) ni el seguimiento de lectura en
voz alta (`currentSpeakingIndex`) — se agregó un `Set<Int>` paralelo
(`headingIndices`) puramente para estilo, sin cambiar el significado de
los índices ya usados por esas dos funcionalidades.

**Refactor de paso — código muerto eliminado:** `ParagraphItem`,
`paragraphCardColor`, `HighlightButton` y `SpeakButton` eran un composable
alternativo para renderizar un párrafo (tarjeta con número) que ninguna
función del archivo llamaba — `ReadingTab` ya dibujaba el párrafo como
texto fluido con un `Row` inline en su lugar. Encontrado al extraer el
render de cada párrafo a `ReadingParagraphRow` (por `LongMethod` de
detekt sobre `ReadingTab` tras agregar la lógica de encabezado) y
reemplazado en el mismo lugar en vez de dejarlo huérfano al lado.

**Verificado en dispositivo (2026-08-29):** `formatted-sample.pdf`
(mismo PDF real usado para verificar RF-CONV-09, con una oración ajustada
en varias líneas) mostró "2 párrafos" en Modo Estudio, no uno por línea;
`formatted-viewer-sample.docx` (mismo `.docx` real de §14 de
`visor-biblioteca.md`) mostró "4 párrafos" con "Titulo del documento" en
negrita/azul, igual que en el Visor.

**Tests nuevos:** `StudyTextExtractionTest` (7 tests) — agrupación de
párrafos por espaciado vertical (línea ajustada vs. párrafo nuevo,
párrafos cortos descartados, lista vacía), detección de encabezado
(incluyendo el bug real del identificador de estilo en español).
`testDebugUnitTest`/`detekt`/`lintDebug` en verde — el refactor de
`ReadingParagraphRow` fue necesario para que `ReadingTab` volviera a
pasar el umbral de `LongMethod` de detekt tras las nuevas líneas de
lógica de encabezado.
