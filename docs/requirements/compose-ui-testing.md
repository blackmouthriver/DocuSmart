# Compose UI Testing — cobertura de flujos de la app

## 1. Contexto y motivación

SonarCloud marca la condición `new_coverage` del Quality Gate en 0.0%
(umbral exigido: 80%) — ver [`deployment.md`](deployment.md) §7. Esto
**no es una regresión ni un descuido**: es un límite conocido de la
arquitectura del proyecto. `jacocoTestReport` (la tarea que alimenta a
Sonar, ver `.github/workflows/sonarcloud.yml`) solo corre
`testDebugUnitTest` — pruebas JVM puras (JUnit5 + MockK, en
`app/src/test/`). El código de Compose (`@Composable`, `ViewModel`s que
dependen de `Context`/Android framework, navegación) no se puede ejercer
de forma significativa ahí; requiere pruebas **instrumentadas**
(`app/src/androidTest/`, JUnit4 + `createAndroidComposeRule`, corren en un
emulador/dispositivo real).

Esta HU responde a la pregunta abierta: **¿conviene invertir en Compose UI
Testing para subir la cobertura de Sonar?** La respuesta corta es
**parcialmente**: da regresión automática real (valioso por sí solo,
independiente de Sonar), pero **no mueve la aguja de `new_coverage` sin
un cambio adicional de CI** — ver §4 "Advertencia técnica" antes de asumir
que esto cierra la condición del Quality Gate.

## 2. Estado actual (auditado 2026-08-30 — no asumir, verificar contra el código)

**Ya existe infraestructura real, esto no es un proyecto desde cero:**

- 5 pruebas instrumentadas ya escritas y en verde:
  `ViewerScreenTest` (abrir documento + favorito, ver
  [`visor-biblioteca.md`](visor-biblioteca.md) §9),
  `ConverterScreenTest` (ver [`conversion.md`](conversion.md) §7),
  `SecurityScreenTest` (PIN de Carpeta Segura),
  `LibraryScreenTest` y `TrashScreenTest` (nuevos 2026-08-31, ver §3
  filas 8-9 más abajo).
- Ya corren en CI: `.github/workflows/ci.yml`, job `instrumented-tests`
  ("Compose UI Testing (emulador)") — emulador API 34 x86_64 con KVM,
  `connectedDebugAndroidTest` sobre los flujos de arriba. Con
  `disable-animations: true` (evita el flakiness de Compose documentado en
  `conversion.md` §7).
- Dependencias y configuración de Gradle ya resueltas: `ui-test-junit4`,
  `mockk-android`, conflictos de "consistent resolution" de AGP, excludes
  de `META-INF/*.md` duplicados, detección de instrumentación para no
  disparar carga real de anuncios en los tests (ver `visor-biblioteca.md`
  §9 para el bug real que esto destapó).
- Patrón establecido: se pasa un ViewModel construido a mano con `mockk`
  al Composable (que ya acepta `viewModel` como parámetro con default
  `hiltViewModel()`) — **sin infraestructura de Hilt en los tests**, mismo
  patrón que los unit tests JVM.

Lo que falta es **ampliar la cobertura al resto de los flujos** — este
documento es el inventario y la priorización de eso.

## 3. RF-QA-01 — Cobertura de Compose UI Testing para los flujos críticos de cada módulo

**Como** equipo de desarrollo de DocuSmart,
**quiero** pruebas de UI automatizadas para el flujo principal de cada
módulo de la app,
**para** detectar regresiones de navegación/estado real (no solo lógica
pura) antes de que lleguen a producción, con el mismo nivel de confianza
que ya dan los ~150+ unit tests JVM para la lógica de negocio.

### Alcance — inventario de flujos (fuente: `NavRoutes.kt`, 18 rutas)

| # | Módulo / pantalla | Flujo a cubrir | Prioridad | Estado |
|---|---|---|---|---|
| 1 | Visor (`ViewerScreen`) | Abrir documento, ver nombre en barra superior, favorito | Alta | ✅ Cubierto (`ViewerScreenTest`) |
| 2 | Convertidor (`ConverterScreen`) | Seleccionar formato origen/destino, elegir archivo, convertir | Alta | ✅ Cubierto (`ConverterScreenTest`) |
| 3 | Seguridad (`SecurityScreen`) | Configurar PIN de Carpeta Segura | Alta | ✅ Cubierto (`SecurityScreenTest`) |
| 4 | Visor — búsqueda | Escribir término, navegar entre coincidencias resaltadas (RF-VIS-08) | Alta | ✅ Cubierto 2026-08-31 (`ViewerSearchTest`) |
| 5 | Visor — renombrar/eliminar | Renombrar documento, mover a papelera desde el Visor (RF-VIS-06) | Media | ⬜ Pendiente |
| 6 | Home (`HomeScreen`) | Ver recientes, favorito, accesos rápidos navegan a la ruta correcta | Alta | ✅ Cubierto 2026-08-31 (`HomeScreenTest`) |
| 7 | Home — eliminar | "Eliminar del historial" mueve a papelera y desaparece de la lista | Alta | ✅ Cubierto 2026-08-31 (`HomeScreenTest`) |
| 8 | Biblioteca (`LibraryScreen`) | Cambiar pestaña Dispositivo/Mis archivos, filtrar por tipo, buscar | Alta | ✅ Cubierto 2026-08-31 (`LibraryScreenTest`) |
| 9 | Papelera (`TrashScreen`) | Restaurar, eliminar uno, **"Borrar todo"** (§17 de `visor-biblioteca.md`, bug real recién corregido) | Alta | ✅ Cubierto 2026-08-31 (`TrashScreenTest`) |
| 10 | Herramientas PDF (`PdfToolsScreen`) | Elegir herramienta, ejecutar sobre un PDF real, ver `ToolSuccessCard` | Alta | ✅ Cubierto 2026-08-31 (`PdfToolsScreenTest`) |
| 11 | Contraseña PDF (`PdfPasswordScreen`) | Poner/quitar/cambiar contraseña | Media | ⬜ Pendiente |
| 12 | Escáner (`ScannerScreen`/`ScanResultScreen`) | Delegado a Google ML Kit — probar solo el resultado (guardar/compartir), no la captura en sí | Media | ⬜ Pendiente |
| 13 | QR (`QrReaderScreen`/`QrCreatorScreen`) | Crear QR con/sin contraseña, leer y navegar a URL | Media | ⬜ Pendiente |
| 14 | Estudio (`StudyScreen`) | Guardar/eliminar nota, orden de la lista, Pomodoro inicia/pausa | Media | ⬜ Pendiente |
| 15 | Ajustes (`SettingsScreen`) | Cambiar tema/idioma/acento, Restablecer configuración | Alta | ✅ Cubierto 2026-08-31 (`SettingsScreenTest`) |
| 16 | Premium (`PremiumScreen`) | Elegir plan, iniciar compra (mock de `BillingManager`), restaurar compras | Media | ⬜ Pendiente |
| 17 | Onboarding (`OnboardingScreen`) | Recorrer y completar, navega a Home | Baja | ⬜ Pendiente |
| 18 | Splash (`SplashMouthBlackScreen`/`SplashDocuSmartScreen`) | Transición automática a la siguiente pantalla | Baja | ⬜ Pendiente |

### Criterios de aceptación

- **AC1** Cada flujo de prioridad Alta pendiente (filas 4, 6-9, 10, 15)
  tiene al menos una prueba instrumentada en `app/src/androidTest/` que
  cubre su camino principal (golden path), siguiendo el patrón ya
  establecido (ViewModel mockeado a mano, sin Hilt en el test). ✅ Cumplido
  2026-08-31 -- las 7 filas Alta están cubiertas.
- **AC2** Todas las pruebas nuevas pasan con `disable-animations: true`
  (mismo mitigante de flakiness ya usado) y no dependen de temporizaciones
  fijas (`Thread.sleep`) sino de `waitUntil`/`onNodeWithText(...).assertExists()`
  de Compose Testing.
- **AC3** El job `instrumented-tests` de `ci.yml` sigue corriendo
  `connectedDebugAndroidTest` con todas las pruebas nuevas incluidas, sin
  aumentar el timeout más allá de lo necesario (hoy 40 min) — si una
  prueba nueva hace que el job exceda el timeout, se evalúa paralelizar
  antes de simplemente subir el límite.
- **AC4** Ninguna prueba nueva depende de red real, Play Billing real, ni
  MediaStore real con archivos que no controla el propio test (mismo
  criterio ya aplicado: `TrashRepositoryTest`/`DocumentRepositoryTest` no
  tocan MediaStore real, ver nota en `TrashRepositoryTest`).
- **AC5** Las prioridades Media/Baja (filas 5, 11-14, 16-18) quedan
  documentadas como backlog explícito en la tabla de arriba, no
  implementadas en el mismo lote que las de prioridad Alta — evita una
  sola HU/PR gigante.

### Implementado 2026-08-31 — filas 8 y 9 (Biblioteca y Papelera)

Primer lote de 2 flujos Alta (de un total de 7 pendientes), a propósito
más chico que "todos juntos" para verificar el patrón antes de seguir con
el resto (fila 4, 6, 7, 10, 15 quedan pendientes para lotes futuros).

- **`TrashScreenTest`** (3 pruebas: restaurar, eliminar uno, "Borrar
  todo"): `TrashRepository` se mockea completo -- `TrashViewModel` solo
  depende de él, no de sus 5 sub-dependencias. Los resultados de
  `deleteForever()`/`deleteAllForever()` se stubean como ya confirmados
  (`Deleted`/`Done`) para no depender del diálogo real de
  `MediaStore.createDeleteRequest()` (proceso externo, ver AC4).
- **`LibraryScreenTest`** (3 pruebas: cambiar pestaña, filtrar por
  categoría, buscar): `LibraryScreen` verifica un permiso real de
  almacenamiento (`ContextCompat.checkSelfPermission`) antes de cargar
  nada -- no es mockeable desde el ViewModel, así que se agregó
  `androidx.test:rules:1.6.1` (nueva dependencia `androidTestImplementation`)
  para usar `GrantPermissionRule`, que concede el permiso antes de que la
  Activity componga, sin depender del diálogo real del sistema.
- **Hallazgo real de ambigüedad de texto** (no asumido, encontrado al
  correr la prueba): filtrar por la categoría "PDF" falló porque el chip
  de filtro y el badge de tipo de cada documento (`DocuSmartDocumentItem`)
  muestran el mismo texto ("PDF") -- `onNodeWithText` encontró 2 nodos en
  vez de 1. Se resolvió eligiendo una categoría donde el label del chip y
  el del badge difieren a propósito (Imagen: chip "Imágenes" plural vs.
  badge "Imagen" singular) en vez de forzar un matcher más complejo --
  PDF/Word/Excel/ZIP comparten el mismo texto entre chip y badge y
  tendrían el mismo problema si se cubren en un lote futuro.
- Gauntlet completo verde: `compileDebugAndroidTestKotlin`, `detekt`
  (no analiza `androidTest/`, sin cambios ahí), `lintDebug`,
  `testDebugUnitTest`, `connectedDebugAndroidTest` (12/12, incluidas las 2
  pruebas previamente flagueadas como flaky -- pasaron limpio en esta
  corrida, consistente con la hipótesis de carga transitoria del
  dispositivo tras varias corridas seguidas, no un bug de código).

### Implementado 2026-08-31 — filas 6 y 7 (Home)

Segundo lote: las dos filas de Home juntas (misma pantalla, mismo
ViewModel) en vez de combinarlas con otro módulo distinto.

- **`HomeScreenTest`** (4 pruebas: ver recientes, tocar favorito, tocar un
  acceso rápido, eliminar mueve a la papelera y desaparece de la lista).
  `HomeViewModel` (el real, en el paquete `home.presentation`) se
  construye con `DocumentRepository`/`TrashRepository`/
  `FavoritesRepository`/`AdManager` mockeados, mismo patrón ya usado en
  `LibraryScreenTest`.
- **Nota de alcance sobre "accesos rápidos navegan a la ruta correcta"**:
  `HomeScreen` recibe los callbacks de navegación (`onSecurity`, `onScan`,
  etc.) ya resueltos desde afuera -- la navegación real la hace
  `DocuSmartNavGraph.kt`, no `HomeScreen`. La prueba verifica que tocar el
  acceso invoca el callback correcto (`onSecurity`), no que ocurra una
  navegación real -- eso requeriría un `NavController` real, un alcance
  distinto (integración de navegación, no UI de una pantalla aislada).
- **Hallazgo real, no de código sino de organización del proyecto**: existe
  un segundo `HomeViewModel` huérfano en el paquete `converter.presentation`
  (datos mock hardcodeados, sin ninguna referencia real en el código,
  confirmado con `grep`) -- no es el que usa `HomeScreen` (mismo paquete
  `home.presentation`, resuelto sin import). Se flagueó aparte para
  eliminarlo, no se tocó en este lote.
- Gauntlet completo verde: `compileDebugAndroidTestKotlin`, `detekt`,
  `lintDebug`, `testDebugUnitTest`, `connectedDebugAndroidTest` (16/16)
  en verde, sin `FATAL EXCEPTION` en logcat.

### Implementado 2026-08-31 — filas 4, 10 y 15 (Visor-búsqueda, Herramientas PDF, Ajustes)

Tercer y último lote de prioridad Alta pendiente -- con esto quedan
cubiertas las 7 filas Alta de la tabla (1-4, 6-10, 15). Las prioridades
Media/Baja (filas 5, 11-14, 16-18) quedan sin implementar, per AC5.

- **`ViewerSearchTest`** (1 prueba: escribir término, ver "Coincidencia X
  de Y", navegar siguiente/anterior con vuelta de módulo). A diferencia de
  `ViewerScreenTest` (que usa un `documentId` mock sin `fileUri` real, por
  lo que `searchInPdf()` corta temprano), acá se genera un PDF real de 2
  páginas con iText7 (mismo patrón de `StudyNotesExporter`) y se pasa su
  ruta absoluta como `documentId` para forzar la resolución real de
  `fileUri`. `SearchPdfTextUseCase` se usa real (no mock), mismo criterio
  ya aplicado a `ImageFormatUseCase` en `ConverterScreenTest`.
- **`SettingsScreenTest`** (2 pruebas: cambiar tema/acento/idioma y ver el
  subtítulo actualizado; restablecer configuración vuelve todo a
  default). `ThemeManager`/`LanguageManager` reales, envueltos en un
  `ContextWrapper` propio (`IsolatedPrefsContext`) que aísla cualquier
  `SharedPreferences` pedida por nombre del estado real del dispositivo --
  generaliza el patrón ya usado en `SecurityScreenTest` para un solo
  namespace, a varios (`docusmart_theme`/`docusmart_language`).
- **`PdfToolsScreenTest`** (1 prueba: elegir "Rotar PDF", ejecutar sobre un
  PDF real de iText7, ver el mensaje de éxito). De las 14 herramientas del
  dispatcher solo se ejercita "Rotar PDF" (la más simple) con
  `RotatePdfUseCase` real; las otras 13 quedan mockeadas relajadas, nunca
  invocadas en este camino.
- **Hallazgo real (bug de test, no de producción) -- clic sintético fuera
  del viewport visible**: el botón final "Rotar PDF 90°" quedó totalmente
  sin efecto al hacer `performClick()` -- sin excepción, sin logs de
  `PdfToolsViewModel`/`RotatePdfUseCase` (se instrumentó temporalmente con
  `Timber.d` en la primera línea de `execute()` para confirmarlo), sin
  cambio de estado. Diagnosticado con `composeRule.onRoot().printToLog(...)`
  volcado a logcat: el nodo del botón existía en el árbol de semántica con
  su acción `OnClick` presente (`t=3020px, b=3157px`), pero la pantalla
  física del dispositivo mide 2400px de alto y el viewport visible de la
  `LazyColumn` llegaba solo hasta `b=2274px` -- el nodo SÍ está compuesto
  (a diferencia del caso de `SettingsScreenTest` más abajo, donde el nodo
  ni existía) porque la `LazyColumn` mide el `item` completo aunque exceda
  el viewport, pero `performClick()` dispara un toque sintético en las
  coordenadas reales del nodo, y esas coordenadas caen fuera de lo visible
  -- el toque no llega a nada y no se lanza ningún error. Corregido con
  `.performScrollTo().performClick()` en vez de `.performClick()` directo.
  Distinto del hallazgo de "Restablecer configuración" en `SettingsScreenTest`
  (mismo síntoma superficial -- timeout esperando texto -- pero causa
  distinta: ahí el nodo no estaba compuesto todavía, acá sí lo estaba pero
  era inalcanzable por toque).
- Gauntlet completo verde: `connectedDebugAndroidTest` (20/20), `detekt`,
  `lintDebug`, `testDebugUnitTest`.

## 4. Advertencia técnica — esto no cierra por sí solo la condición de Sonar

`jacocoTestReport` (`app/build.gradle.kts`, tarea usada por
`sonarcloud.yml`) solo agrega los reportes de `testDebugUnitTest`. Las
pruebas de `androidTest/` corren en un **proceso distinto, dentro del
emulador**, vía `connectedDebugAndroidTest` — Jacoco puede instrumentar
también ese proceso, pero requiere:

1. Habilitar `testCoverageEnabled = true` (o el mecanismo equivalente en
   AGP moderno) en el build type de test, para que
   `connectedDebugAndroidTest` genere su propio reporte Jacoco.
2. Fusionar ese reporte con el de `testDebugUnitTest` antes de pasárselo a
   Sonar (`sonar.coverage.jacoco.xmlReportPaths` acepta una lista de
   archivos).
3. Mover (o duplicar) el job `instrumented-tests` — con emulador, ~15-20
   min más de CI — al workflow de `sonarcloud.yml`, hoy sin emulador. Esto
   aumenta el tiempo y el costo de cómputo de cada análisis de Sonar en
   `main`/PRs.

Ninguno de estos 3 pasos está hecho hoy. **Decisión pendiente del
usuario:** si vale la pena ese costo de CI para que Sonar refleje la
cobertura real, o si el valor de estas pruebas (regresión real, atrapar
bugs como el de Papelera de §17) es suficiente sin perseguir el número de
Sonar — en cuyo caso la alternativa más simple es bajar/ajustar el umbral
de `new_coverage` en la configuración del Quality Gate de SonarCloud
(decisión de configuración, no de código).

## 5. Preguntas abiertas

- Con las 7 filas Alta ya cubiertas (2026-08-31), ¿se evalúa ahora la
  integración con Sonar (§4), se aborda el backlog Media/Baja (filas 5,
  11-14, 16-18), o ambos quedan en pausa hasta nueva indicación?
- ¿Vale la pena el costo de CI adicional (emulador en cada análisis de
  Sonar) para que `new_coverage` refleje pruebas instrumentadas, o se
  prefiere ajustar el umbral del Quality Gate y dejar Compose UI Testing
  como iniciativa de calidad independiente?
- Fila 12 (Escáner): dado que la captura en sí es 100% Google ML Kit (sin
  código propio que testear ahí), ¿se limita el test a lo que sí es código
  propio (guardar/compartir el resultado), o se considera fuera de alcance
  por completo?
