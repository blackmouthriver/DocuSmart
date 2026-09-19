# Backlog de bugs — Decimotercera auditoría general (2026-09-18)

Ronda 13: 4 agentes de investigación en paralelo sobre áreas no auditadas a fondo en rondas anteriores (resto de Herramientas PDF, consistencia de i18n en los 12 idiomas + Onboarding, integridad de la capa de persistencia Room, Notas/Modo Lectura en profundidad). Además, esta ronda incorporó al gauntlet ktlint, Spotless, Kover, Dependency Analysis Gradle Plugin, Kotest, ArchUnit y Snyk (pedido explícito del usuario, ver sección dedicada en `project_release_status.md`).

## Hallazgos — Resto de Herramientas PDF

- **H1 (Alta)** `MergePdfUseCase.kt`: unir PDFs aborta TODO el lote si un solo archivo está protegido/corrupto, en vez de saltearlo como ya hace con el caso "URI no copiable". **Estado: ✅ Corregido**
- **H2 (Alta)** `NumberPagesUseCase.kt`: numeración mal ubicada en PDFs rotados o con MediaBox desplazado (mismo bug ya corregido en `WatermarkPdfUseCase.kt`, no propagado acá). **Estado: ✅ Corregido**
- **H3 (Media)** 6 UseCase sin capturar `OutOfMemoryError`, dejando `outputFile` huérfano. **Estado: ✅ Corregido**
- **H4 (Media)** 6 UseCase sin relanzar `CancellationException` ni `ensureActive()` en bucles largos. **Estado: ✅ Corregido**
- **H5 (Media)** `WatermarkPdfUseCase.kt`: texto de marca de agua sin sanear contra el alfabeto de la fuente (Helvetica, solo Latin-1) — falla con caracteres CJK/cirílico/emoji. **Estado: ✅ Corregido**
- **H6 (Media)** `MergePdfUseCase.kt`: log con URI real reenviado a Crashlytics. **Estado: ✅ Corregido**
- **H7 (Media, accesibilidad)** `SplitPdfScreen.kt`: botones +/- de rango sin `contentDescription`. **Estado: ✅ Corregido**

## Hallazgos — i18n y Onboarding

- **H1 (Media)** Sin sistema `<plurals>` en toda la app — "1 documentos" en vez de "1 documento", peor en ruso (3-4 formas plurales). **Estado: ✅ Corregido (caso puntual `library_document_count`)** — el patrón es sistemático en más lugares, queda documentado como pendiente de una auditoría de i18n dedicada para el resto de casos.
- **H2 (Alta)** `DownloadsAccessManager.kt`/`OnboardingScreen.kt`: excepción no capturada al mostrar el nombre de carpeta vinculada, puede crashear la última slide del Onboarding. **Estado: ✅ Corregido**
- **H3 (Media, accesibilidad)** `OnboardingScreen.kt`: TalkBack no anuncia progreso en el carrusel ("página X de Y"). **Estado: ✅ Corregido**
- **H4 (Baja)** Persistencia del flag "onboarding completado" vía `apply()` asíncrono — edge case de baja frecuencia genérico de SharedPreferences. **Estado: no corregido, documentado** (no urgente per el propio agente de investigación).

**Verificación de integridad estructural de los 12 idiomas**: 0 claves faltantes, 0 placeholders rotos, 0 duplicados en los 5 archivos base + los agregados en rondas 11/12 — la cobertura de idiomas es sólida.

## Hallazgos — Integridad de la capa de persistencia (Room)

- **H1 (Alta)** `DocumentIdentityMaintenance.kt`: ninguna operación multi-tabla (6-7 escrituras secuenciales) está envuelta en una transacción Room real — un proceso muerto a mitad de camino deja la identidad del documento parcialmente migrada entre tablas. **Estado: ✅ Corregido** (`database.withTransaction { ... }`)
- **H2 (Alta)** `DocumentRepository.kt`: 3 logs con `documentId` real (ruta/nombre de archivo) reenviados a Crashlytics, mismo patrón ya corregido en `TrashRepository.kt` pero no propagado acá. **Estado: ✅ Corregido**
- **M1 (Media)** `NoteRepository.kt`: sin serialización de escrituras concurrentes sobre la misma nota (mismo riesgo ya corregido en `AgendaRepository.kt` con un `Mutex` por id, ronda 12). **Estado: ✅ Corregido**
- **M2 (Media)** `CancellationException` no relanzada en varios puntos de `NoteRepository.kt`/`TrashRepository.kt`/`DocumentRepository.kt`. **Estado: ✅ Corregido**
- **M3 (Media)** Salto de migración de esquema 1→2 sin `Migration` registrada (usa `fallbackToDestructiveMigration`). **Estado: evaluado, no corregido** — riesgo bajo hoy (ningún usuario real de la prueba cerrada debería tener una base en versión 1), pero tocar el esquema de una app ya publicada sin poder probar contra datos reales de producción es demasiado riesgo para esta ronda.
- **Baja** Sin tests de migración (`exportSchema=false`), sin índices en columnas de orden frecuente (`lastOpenedAt`, etc.). **Estado: no corregido, documentado** — mismo criterio de riesgo que M3.

## Hallazgos — Notas y Modo Lectura (Modo Estudio, sin Pomodoro)

- **H1 (Media)** `NoteRepository.kt`: `CancellationException` no relanzada en `migrateLegacyNotesIfNeeded()` — encontrado independientemente también por el agente de Room (mismo hallazgo, corregido una sola vez). **Estado: ✅ Corregido** (ver M2 de Room)
- **H2 (Media)** `StudyScreen.kt`: fallback de idioma de TTS sin verificar si funcionó, filtro de voces desincronizado del idioma real. **Estado: ✅ Corregido**
- **H3 (Media)** `StudyScreen.kt`: sin aviso visible si el motor TTS no inicializa en absoluto — botón "Leer todo" deshabilitado para siempre sin explicación. **Estado: ✅ Corregido**
- **H4 (Media)** `StudyScreen.kt`: párrafos resaltados no se reinician al cargar un documento nuevo (índices numéricos sin atadura al documento). **Estado: ✅ Corregido**
- **H5 (Baja/Media)** `StudyScreen.kt`: `StudyPdfViewer` traga `CancellationException` al renderizar. **Estado: ✅ Corregido**
- **H6 (Alta)** `StudyScreen.kt`: `StudyPdfViewer` nunca recicla los bitmaps de página que renderiza — mismo riesgo de OOM ya corregido en el Visor (ronda 11), no propagado acá. **Estado: ✅ Corregido**

**Verificado sin bugs**: `TextSummarizer.kt` es 100% on-device (coherente con la promesa de privacidad ya publicada); exportación de notas/resúmenes usa nombres de archivo fijos con timestamp, sin riesgo de inconsistencia con `sanitizeOutputFileName()`.

## Revisión adversarial (2 agentes en paralelo: seguridad + correctitud)

Aplicada sobre los propios fixes de esta ronda, antes de pedir fusión — mismo patrón usado en rondas 11 y 12. Encontró 2 regresiones reales (Alta) y varias de severidad Media que los agentes de fix habían dejado pasar.

- **A1 (Alta)** `NumberPagesUseCase.kt`: el propio fix de H2 (arriba) tenía DOS bugs que se compensaban visualmente solo en páginas a 0°/180° — los anchors X de 90° y 270° estaban cruzados entre sí, y el signo del ángulo de rotación del texto estaba invertido. Confirmado por re-derivación matemática independiente de las fórmulas de transformación `/Rotate` de PDF; no existía ningún test con una página rotada. **Estado: ✅ Corregido** — anchors correctos, signo de ángulo corregido, 2 tests de regresión nuevos (`NumberPagesUseCaseTest.kt`) que parsean el operador `Tm` del content stream para verificar la posición X real del glifo en páginas a 90°/270°.
- **A2 (Alta)** `MergePdfUseCase.kt`: el fix de H1 (arriba) contaba dos veces un archivo corrupto que se copia bien a caché pero falla al abrirse como PDF válido — quedaba sumado tanto en "N archivos" (éxito) como en "N no se pudo incluir" (saltado). El único test previo de "salteo" usaba `openInputStream` devolviendo `null`, que falla ANTES del código con el bug, por lo que nunca lo ejercitó. **Estado: ✅ Corregido** — contador `mergedFileCount` dedicado, test de regresión nuevo con un archivo que copia bien pero no es PDF válido.
- **M1 (Media)** `NoteRepository.kt`: el `Mutex` por nota (M1 de la sección Room, arriba) solo protegía `updateNote()`/`linkDocument()`, no `deleteNote()`/`deleteAll()` — riesgo real de violación de FK con escrituras concurrentes. **Estado: ✅ Corregido**
- **M2 (Media)** `TrashRepository.kt`: quedaron 2 logs con `documentId` real sin redactar pese a que el mismo diff de esta ronda corrigió 3 sitios cercanos. **Estado: ✅ Corregido**
- **Evaluado y NO corregido (alcance desproporcionado para esta ronda, documentado explícitamente)**: la carrera cruzada entre `DocumentIdentityMaintenance` y `NoteRepository` — las escrituras masivas (`updateDocumentId()`/`unlinkDocument()`) no pasan por el mutex por-nota — necesitaría un esquema de locking tipo lector/escritor entre dos repositorios distintos; y la no-atomicidad de `FavoritesRepository` (SharedPreferences) dentro del nuevo `database.withTransaction{}` — brecha real pero acotada.

## Verificación en dispositivo (Motorola Edge 30 Neo, `ZY22G7SB77`)

APK debug instalado y reiniciado en frío. Cero `FATAL EXCEPTION` y cero ANR en todo el logcat de la sesión. Confirmado visualmente sin crashes: Inicio, Herramientas PDF (listado completo), pantalla "Numerar páginas", Modo Estudio → pestaña Notas (formulario "Nueva nota" con recordatorios, adjuntar imagen, escanear, todo funcional).

**Verificación en vivo adicional, con archivos reales (pedida por el usuario tras el merge)**: se generaron 3 PDFs de prueba (2 normales + 1 con `/Rotate 90`), se pushearon al dispositivo y se ejercitaron los 2 hallazgos Alta de la revisión adversarial a través de la UI real, no solo por unit test:

- **Unir PDFs**: unión real de 2 archivos vía el selector del sistema → "PDFs unidos correctamente — 2 archivos, 2 páginas". Se extrajo el resultado del dispositivo y se confirmó con `pypdf` que ambas páginas están presentes con el contenido correcto de cada original (valida A2 con datos reales, no solo el test unitario).
- **Numerar páginas en un PDF rotado 90° real**: se corrió el flujo completo y se extrajo el resultado. Se verificó directamente el operador `Tm` del content stream: el número quedó anclado en x=592 de 612pt de ancho (cerca del borde derecho, como predice el fix) — confirma A1 con un caso end-to-end real, más allá de los 2 tests unitarios ya agregados.
- Modo Lectura (pestaña vacía) renderiza sin crash.
- Archivos de prueba borrados del dispositivo al terminar (no se tocaron datos reales del usuario).
- **Onboarding**: no se verificó en vivo (requeriría borrar los datos de la app en el dispositivo real del usuario) — queda cubierto solo por la revisión de código que encontró y corrigió el crash original (H2 de la sección i18n/Onboarding).

## Hallazgo adicional (mismo día, investigación pedida explícitamente por el usuario): Quality Gate de SonarCloud roto desde antes de esta sesión

El check "SonarCloud Code Analysis" venía fallando por "0% cobertura en código nuevo" en los últimos 3 merges (rondas 10, 11 y 12) — no era una regresión de esta ronda. Investigado a fondo y corregidos **2 bugs reales, independientes entre sí**, ambos confirmados con datos reales de la API de SonarCloud tras el fix (cobertura pasó de 0.0% a 28.7% total / 26.7% en código nuevo):

1. **`jacocoTestReport` comparaba bytecode incorrecto** (commit `2cf3b6e`): `classDirectories` apuntaba a la salida cruda de kotlinc (`build/tmp/kotlin-classes/debug`), pero tanto `testDebugUnitTest` como `connectedDebugAndroidTest` corren contra la salida ya transformada por el plugin compilador de Compose (`transformDebugClassesWithAsm`, inyección de `@StabilityInferred`). Confirmado con sha1sum: 3 versiones de bytecode distintas para la misma clase (6948 / 7002 / 7602 bytes). JaCoCo descartaba la cobertura ante el desajuste ("Classes in bundle 'app' do not match with execution data"). **Estado: ✅ Corregido**, apuntando `classDirectories` a la salida de `transformDebugClassesWithAsm`.
2. **La propiedad del reporte se evaluaba en el módulo equivocado de Sonar** (commit `5486269`): `rootProject.name = "DocuSmart"` hace que el build tenga dos módulos de Sonar ("DocuSmart" raíz + "app" subproyecto, donde vive todo el código real). `sonar.coverage.jacoco.xmlReportPaths` vivía en la raíz y se evaluaba en ambos módulos con la misma ruta relativa -- el módulo "app" no encontraba el archivo (ruta relativa mal resuelta), y el módulo raíz sí lo encontraba pero no tenía código real para emparejar ("None of the N files... could be matched to the analysed sources"). Esto explica también por qué el intento revertido del 2026-09-09 (tocar `sonar.sources`/`sonar.tests`) nunca funcionó -- atacaba el síntoma equivocado. **Estado: ✅ Corregido**, moviendo la propiedad al `sonar {}` de `app/build.gradle.kts` con la ruta relativa correcta.

El Quality Gate sigue en rojo (26.7% de cobertura en código nuevo vs. el umbral configurado de 80%), pero ahora es una señal real y accionable, no un bug de la tubería de medición. Diagnosticado con logs verbose de Sonar (`--info -Dsonar.verbose=true`, activado temporalmente en `.github/workflows/sonarcloud.yml` y retirado en el mismo commit del fix final).
