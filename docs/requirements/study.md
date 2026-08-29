# Módulo: Modo Estudio

> Parte de [`CONTEXT.md`](../../CONTEXT.md). Cubre lectura con voz (TTS),
> notas (texto y voz) y Pomodoro — las 3 pestañas de `StudyScreen.kt`.

**Estado:** 2 bugs reales corregidos, uno de ellos silenciosamente
corrompía el contenido de las notas del usuario en cada guardado. La nota de
`CONTEXT.md` que daba por "corregido" el guardado de notas resultó
**incorrecta al revisar el código con más detalle** — sí existe una lista de
notas guardadas (contrario a lo que indicaba la QA de mayo), pero su
serialización manual tenía dos bugs reales que nadie había detectado. 5 tests
nuevos. **RF-STU-08/09/10 implementados 2026-08-29, ver §9** — cierran por
completo el backlog original de este módulo: exportar notas (texto/PDF),
estadísticas de estudio (tiempo leído, pomodoros por semana), y Pomodoro en
segundo plano vía un servicio en primer plano (`PomodoroTimerService`) en
vez de un `LaunchedEffect` que se cancelaba al salir de la pantalla.
**Código relacionado:** `features/study/presentation/StudyScreen.kt`,
`features/study/domain/StudyNotesStorage.kt`,
`features/study/domain/StudyNotesExporter.kt` (nuevo),
`features/study/domain/StudyStatsStorage.kt` (nuevo),
`features/study/domain/PomodoroEngine.kt` (nuevo),
`features/study/domain/PomodoroTimerService.kt` (nuevo).

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

- **RF-STU-08** ✅ Exportar notas (compartir como texto o PDF). Implementado 2026-08-29, ver §9.
- **RF-STU-09** ✅ Estadísticas de estudio (tiempo total leído, pomodoros completados por semana). Implementado 2026-08-29, ver §9.
- **RF-STU-10** ✅ Temporizador Pomodoro que siga corriendo en segundo plano. Implementado 2026-08-29, ver §9.

### Backlog
*(vacío — las 3 funcionalidades de este backlog, RF-STU-08/09/10, están implementadas)*

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

### HU-STU-05 — Exportar mis notas
*(Implementado 2026-08-29 — ver §9.)*

**Como** usuario que tomó varias notas durante una sesión de estudio,
**quiero** exportarlas como texto o PDF,
**para** guardarlas o compartirlas fuera de la app.

- **AC1** Dado que tengo al menos una nota guardada, cuando toco el ícono de exportar en la lista de notas, entonces veo dos opciones: "Como texto" y "Como PDF".
- **AC2** Dado que elijo "Como texto", cuando se genera el archivo, entonces se abre el selector de compartir de Android con un `.txt` que incluye título, fecha y contenido de cada nota.
- **AC3** Dado que elijo "Como PDF", cuando se genera el archivo, entonces se abre el mismo selector con un `.pdf` con el mismo contenido, uno debajo del otro.
- **AC4** Dado que no tengo ninguna nota guardada, cuando veo la lista, entonces no hay ningún botón de exportar visible (nada que exportar).

### HU-STU-06 — Ver mis estadísticas de estudio
*(Implementado 2026-08-29 — ver §9.)*

**Como** usuario que usa Modo Estudio con frecuencia,
**quiero** ver cuánto tiempo he leído en voz alta y cuántos pomodoros completé esta semana,
**para** darme una idea de mi constancia sin llevar la cuenta yo mismo.

- **AC1** Dado que toco el ícono de estadísticas en la barra superior, cuando se abre el diálogo, entonces veo el tiempo total leído en voz alta (horas y minutos) y el total de pomodoros completados.
- **AC2** Dado que veo el diálogo, cuando reviso la sección semanal, entonces veo una barra por cada día de la semana calendario actual con la cantidad de pomodoros completados ese día.
- **AC3** Dado que nunca usé la lectura en voz alta ni completé un pomodoro, cuando abro el diálogo, entonces veo un mensaje de estado vacío en vez de ceros sin contexto.

### HU-STU-07 — El Pomodoro sigue corriendo si salgo de la pantalla
*(Implementado 2026-08-29 — ver §9.)*

**Como** usuario que inició un Pomodoro y quiere revisar un documento mientras estudia,
**quiero** que el conteo siga corriendo aunque salga de Modo Estudio,
**para** no perder el tiempo real transcurrido por navegar a otra pantalla.

- **AC1** Dado que inicio un Pomodoro y navego a otra pantalla de la app, cuando vuelvo a Modo Estudio, entonces el tiempo restante refleja lo que realmente transcurrió, no se reinició ni se congeló.
- **AC2** Dado que un Pomodoro está corriendo y la app pasa a segundo plano, cuando reviso las notificaciones, entonces veo una notificación persistente con el tiempo restante (siempre que el permiso de notificaciones esté concedido).
- **AC3** Dado que un bloque de estudio o descanso termina, cuando esto ocurre, entonces el timer se pausa solo (no encadena el siguiente bloque automáticamente) — mismo comportamiento que antes de esta HU.
- **AC4** Dado que no concedo el permiso de notificaciones, cuando uso el Pomodoro, entonces el conteo funciona igual, solo sin notificación visible.

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
| 3 | Resto de `StudyScreen.kt` (TTS, resto de extracción PPT/texto plano) — no cubierto. Son funciones/composables sin ViewModel (ver RNF-STU-03); las funciones de extracción de PDF/Word de la fila 2 se expusieron como `internal` (mismo patrón que `StudyNotesStorage`) puntualmente para poder probarlas, sin refactorizar el resto del módulo. | Pendiente si se decide abordar la deuda de arquitectura completa. |
| 4 | `PomodoroEngineTest` (RF-STU-10, ver §9) — un tick normal solo descuenta un segundo, pasar de 0 segundos al minuto siguiente dan 59 segundos, terminar un bloque de estudio cuenta el pomodoro y pausa en descanso, terminar un descanso vuelve a estudio pausado sin sumar otro pomodoro, `tickCompletesStudyBlock` distingue el tick exacto que cierra un bloque de estudio. | ✅ 5 tests, en verde |
| 5 | `StudyStatsStorageTest` (RF-STU-09, ver §9) — `addReadingTime` acumula y descarta valores no positivos, `recordPomodoroCompletion` agrega al historial, `trimOldTimestamps` descarta lo más viejo que el período de retención, `pomodoroCountsByWeekday`/`pomodoroCountThisWeek` solo cuentan la semana calendario actual, `millisToHoursAndMinutes` convierte sin decimales. | ✅ 7 tests, en verde |
| 6 | `StudyNotesExporterTest` (RF-STU-08, ver §9) — solo cubre `buildPlainText` (lógica pura); `exportAsTextFile`/`exportAsPdfFile` escriben a disco y generan PDF con iText7, mismo límite ya documentado para otros use cases de conversión/PDF que tampoco tienen test unitario directo. | ✅ 3 tests, en verde |

Se agregó `testImplementation("org.json:json:20231013")` en `app/build.gradle.kts`
— el stub de Android para unit tests deja `org.json.*` sin implementar
("not mocked"), así que se necesita la implementación real solo para pruebas
(no afecta el runtime de la app, que usa la versión de Android normalmente).

---

## 7. Preguntas abiertas

| Pregunta | Notas |
|---|---|
| ¿Vale la pena refactorizar `StudyScreen.kt` a una arquitectura con `ViewModel` (como el resto de módulos)? | Es la única pantalla grande sin esa estructura; permitiría testear TTS/Pomodoro/extracción y sería más consistente, pero es un refactor grande, no un bug fix. |
| ¿Prioridad de exportar notas (RF-STU-08) vs. estadísticas de estudio (RF-STU-09) vs. Pomodoro en segundo plano (RF-STU-10)? | **Resuelto 2026-08-29** — se implementaron los 3 en la misma pasada, ver §9. |
| ¿El Pomodoro en segundo plano (RF-STU-10) debería sobrevivir si el usuario mata el proceso de la app, no solo si navega a otra pantalla? | No implementado en esta pasada — el `PomodoroEngine` vive mientras el proceso esté vivo (cubre el caso reportado: salir de Modo Estudio), pero no persiste un timestamp de fin en disco para recalcular tras un reinicio del proceso. Alcance deliberado, ver §9. |

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

---

## 9. RF-STU-08/09/10 — Exportar notas, estadísticas y Pomodoro en segundo plano (2026-08-29)

Cierra por completo el backlog original de este módulo, en la misma pasada
de UI/UX en la que el usuario pidió continuar con las HU pendientes de todo
el proyecto (ver también `visor-biblioteca.md` §15).

### RF-STU-08 — Exportar notas
`StudyNotesExporter.kt` (nuevo, `features/study/domain/`) — mismo patrón de
nombre de archivo y ubicación ya usado por Conversión/Herramientas PDF
(`filesDir/study_exports/DocuSmart_Notas_<timestamp>.<ext>`):
`buildPlainText()` (función pura, testeada) arma el texto plano de todas las
notas separadas por un separador visual; `exportAsTextFile()` lo escribe a
disco; `exportAsPdfFile()` genera un PDF con iText7 (`Document`/`Paragraph`,
mismo mecanismo ya usado en `WordToPdfUseCase`) con título en negrita, fecha
en gris y contenido por nota. Un ícono nuevo (`IosShare`) en el encabezado
de "Notas guardadas" (visible solo si hay al menos una nota) abre un
`DropdownMenu` con "Como texto"/"Como PDF"; al elegir uno, se comparte vía
`Intent.ACTION_SEND` + `FileProvider`, mismo patrón que el resto de la app.

**Bug real encontrado de paso, no reportado por el usuario:** al implementar
esto se necesitó la autoridad correcta del `FileProvider`
(`${packageName}.fileprovider`, la única registrada en el manifiesto) y se
descubrió que 3 archivos (`RecentDocuments.kt`, `FavoritesSection.kt`,
`DocumentListSection.kt`) usaban `${packageName}.provider` — una autoridad
que **no existe** — en su rama de respaldo para compartir un documento
generado por la app (se activa cuando el `content://` normal falla).
Cualquier intento de usar ese respaldo habría lanzado
`IllegalArgumentException: Failed to find configured root...` en vez de
compartir el archivo. Corregido en los 3 archivos a la autoridad real.

### RF-STU-09 — Estadísticas de estudio
`StudyStatsStorage.kt` (nuevo) — mismo patrón de persistencia que
`StudyNotesStorage` (SharedPreferences + JSON real vía `org.json`, sin
Room: este módulo no tiene ViewModel, ver RNF-STU-03, y el volumen de datos
es mínimo). Guarda `totalReadingMillis` (acumulado) y una lista de
timestamps de pomodoros completados (recortada a 90 días de retención —
`trimOldTimestamps()`, función pura). Dos funciones puras testeadas aparte:
`pomodoroCountsByWeekday()` (cuenta por día de la semana calendario actual,
usando `Calendar.DAY_OF_WEEK`/`firstDayOfWeek` para respetar el locale) y
`millisToHoursAndMinutes()`.

- **Tiempo de lectura real, no "tiempo con la pantalla abierta":**
  `StudyScreen` agrega un `LaunchedEffect(isSpeaking.value)` que, mientras
  `isSpeaking` es `true`, se suspende en `awaitCancellation()` — cuando
  `isSpeaking` vuelve a `false` (el usuario detiene la lectura, termina el
  TTS, o navega fuera), Compose cancela el efecto y el bloque `finally`
  calcula la duración real y la persiste. Cubre tanto "leer este párrafo"
  como "leer todo" porque ambos comparten el mismo `isSpeaking`.
- **UI:** un ícono nuevo (`QueryStats`, "Ver estadísticas") en
  `StudyTopBar` abre `StudyStatsDialog` — total de horas/minutos leídos,
  total de pomodoros, y una barra por día de la semana actual (con la
  inicial del día calculada vía `SimpleDateFormat("EEEEE", Locale...)` en
  vez de 7 strings nuevos por idioma, para no duplicar el trabajo de i18n
  ya hecho). Estado vacío dedicado si nunca hubo lectura ni pomodoros.
- El punto exacto en el que un pomodoro "cuenta" para las estadísticas es
  el mismo tick que cierra un bloque de ESTUDIO (no de descanso) — ver
  `tickCompletesStudyBlock()` más abajo.

### RF-STU-10 — Pomodoro en segundo plano
**Diagnóstico:** el Pomodoro vivía enteramente en `remember{}` dentro de
`StudyScreen` y un `LaunchedEffect(isRunning)` — Compose cancela ese efecto
en cuanto la composición se destruye, así que navegar a cualquier otra
pantalla de la app detenía el conteo en seco (no lo pausaba de forma
consciente, simplemente dejaba de avanzar hasta volver a Modo Estudio).

**Diseño — un motor fuera de la composición, más un servicio que lo hace
sobrevivir en segundo plano:**
- **`PomodoroEngine.kt`** (nuevo) — objeto singleton con su propio
  `CoroutineScope` (vive mientras el proceso esté vivo, no atado a ninguna
  pantalla) y un `MutableStateFlow<PomodoroState>`. `StudyScreen` ya no
  tiene ningún `remember` de minutos/segundos/etc. — solo
  `collectAsState()` sobre `PomodoroEngine.state`, y `onToggle`/`onReset`
  llaman a `PomodoroEngine.toggle(context)`/`reset(context)`.
  - `tickPomodoro(current): PomodoroState` y
    `tickCompletesStudyBlock(current): Boolean` son funciones puras
    extraídas del motor (sin `Context`, sin tocar `StudyStatsStorage`)
    específicamente para poder testearlas con estados fijos — mismo
    criterio ya usado para `isTrashEntryExpired`/
    `mergeHistoryWithDocuments` en `visor-biblioteca.md`.
  - **Mismo comportamiento de fin de ciclo que antes, no un cambio de
    conducta:** al completarse un bloque de estudio o descanso, el
    resultado ya trae `isRunning = false` — el usuario debe tocar
    "Iniciar" de nuevo para el siguiente bloque, igual que la versión
    anterior basada en `LaunchedEffect`.
- **`PomodoroTimerService.kt`** (nuevo, primer `Service` del proyecto) —
  servicio "tonto": no tiene timer propio, solo observa
  `PomodoroEngine.state` y refleja el minuto:segundo restante en una
  notificación en primer plano (canal `pomodoro_timer`, importancia baja,
  ícono monocromo nuevo `ic_notification_pomodoro.xml`); se detiene solo
  (`stopSelf()`) en cuanto `state.isRunning` es `false`. `PomodoroEngine`
  lo arranca con `startForegroundService()` al iniciar el timer y lo
  detiene con `stopService()` al pausar/reiniciar/completar un ciclo.
- **Manifiesto** — 3 permisos nuevos
  (`POST_NOTIFICATIONS`/`FOREGROUND_SERVICE`/
  `FOREGROUND_SERVICE_SPECIAL_USE`) y la declaración del servicio con
  `android:foregroundServiceType="specialUse"` +
  `PROPERTY_SPECIAL_USE_FGS_SUBTYPE="pomodoro_study_timer"` — un timer de
  estudio no encaja en ningún tipo de FGS estándar (no es
  `dataSync`/`mediaPlayback`/`location`/etc.), y Android 14+ exige declarar
  un tipo explícito con motivo para cualquier servicio en primer plano.
- **Permiso de notificaciones (Android 13+):** `StudyScreen` lo pide
  (`ActivityResultContracts.RequestPermission()`) al entrar a la pestaña
  Pomodoro, mismo patrón ya usado para la cámara en `QrScreen.kt`. El
  timer funciona igual sin el permiso — solo no se ve la notificación
  mientras la app está en segundo plano (AC4 de HU-STU-07).
- **Alcance deliberado, no cubierto:** el motor sobrevive a navegar a otra
  pantalla o a que la app pase a segundo plano (el caso reportado), pero
  no persiste un timestamp de fin en disco para recalcular el tiempo
  restante si el usuario mata el proceso por completo (deslizar la app
  fuera de "recientes", o que el sistema lo mate por memoria) — al volver
  a abrir la app en ese escenario, el Pomodoro se reinicia. Se documenta
  como pregunta abierta en §7 en vez de resolverla en esta pasada: hacerlo
  bien requeriría guardar `endTimestamp`/`pausedRemaining` en
  SharedPreferences y recalcular al arrancar, más alcance del que pedía el
  hallazgo original ("se detiene si se navega fuera de la pantalla").

**5 tests nuevos** (`PomodoroEngineTest`) sobre `tickPomodoro`/
`tickCompletesStudyBlock` — ver fila 4 de §6. **7 tests nuevos**
(`StudyStatsStorageTest`) y **3 tests nuevos** (`StudyNotesExporterTest`) —
ver filas 5 y 6 de §6.

**detekt:** un hallazgo real corregido durante el desarrollo (no
baselineado) — `createNotificationChannelIfNeeded()` con 3
`return`tempranos superaba el límite de `ReturnCount`; reescrito con un
solo `if` anidado en vez de suprimir el hallazgo.

**Verificado end-to-end en el dispositivo real (app en español):**
- **Pomodoro en segundo plano (AC1/AC2 de HU-STU-07):** iniciado el timer
  en 25:00, confirmado el permiso de notificaciones solicitado al entrar a
  la pestaña, concedido, y `adb shell dumpsys activity services` confirmó
  `PomodoroTimerService` con `isForeground=true` y la notificación real
  (`channel=pomodoro_timer`) mientras la app estaba completamente cerrada
  (tecla atrás + `am start` de nuevo, no solo minimizada). Al volver a
  abrir Modo Estudio → Pomodoro, el reloj mostraba 22:20 "en progreso" —
  el conteo avanzó de verdad mientras la app no estaba en primer plano, no
  se reinició ni se congeló.
- **Fin de ciclo pausado, no encadenado (AC3):** cubierto por el test
  unitario (`tickPomodoro` con minutos/segundos en 0 devuelve
  `isRunning = false`); no repetido en dispositivo por ser una espera de
  25/5 minutos reales.
- **Exportar notas (AC1-AC3 de HU-STU-05):** creada una nota de prueba,
  tocado el ícono de exportar → menú con "Como texto"/"Como PDF" → elegido
  "Como PDF" → se abrió el selector de compartir de Android mostrando
  `DocuSmart_Notas_20260829_183935.pdf` listo para compartir — confirma
  que el archivo se generó y el `FileProvider` (ya corregido) lo resolvió
  correctamente.
- **Estadísticas (AC1/AC3 de HU-STU-06):** tocado el ícono de
  estadísticas antes de generar ningún dato → diálogo mostró el mensaje de
  estado vacío correctamente, no ceros sin contexto.
- Verificado también: `testDebugUnitTest`/`detekt`/`lintDebug` en verde.
