# Módulo: Visor + Biblioteca (+ Home)

> Parte de [`CONTEXT.md`](../../CONTEXT.md). Cubre el Visor de documentos, la
> Biblioteca, y la sección "Recientes" de Home (comparten `DocumentRepository`
> y `FavoritesRepository`, así que sus bugs suelen cruzarse entre pantallas).

**Estado:** 3 bugs reales corregidos (favoritos/alias no coincidían entre
Visor y Biblioteca/Home por un formato de id inconsistente; búsqueda en PDF
no hacía nada; "eliminar" no borraba el archivo real, solo lo ocultaba de la
lista hasta el siguiente refresh). 4 hallazgos de la QA de mayo confirmados
**obsoletos** (favoritos de Biblioteca/Home, tab dispositivo/app, botón
"Abrir", accesos rápidos de Home) — ya estaban corregidos antes de esta
sesión, sin registro de cuándo. **RF-VIS-09 agregado 2026-08-25:** primera
tabla Room del proyecto (`document_history`) — "Recientes" en Home ahora
refleja cuándo se abrió realmente cada documento, no su fecha de
modificación en disco. Incluye la **primera prueba de integración del
proyecto** contra SQLite real (§8.1), que encontró un bug real en el uso de
`@Upsert`, y la **primera prueba de Compose UI** (§9), que encontró que
`AdManager.initialize()` cargaba anuncios (incluido video recompensado) sin
condición alguna al arrancar la app, crasheando el proceso bajo
instrumentación — corregido solo para el caso de test, sin tocar el
comportamiento real de anuncios. 21 tests nuevos (10 + 5 + 6, más 2 pruebas
instrumentadas aparte de este conteo). **RF-VIS-06 implementado 2026-08-29,
ver §11** — renombrar y eliminar desde el Visor, con la lógica de rename
extraída de Biblioteca/Home a `DocumentRepository` para reutilizarla en un
tercer lugar sin triplicar código.
**Código relacionado:** `features/viewer/**`, `features/library/**`,
`features/home/**`, `core/data/FavoritesRepository.kt`, `core/data/db/**`
(nuevo).

---

## 1. Alcance

Tres pantallas que comparten el mismo repositorio de documentos:

1. **Visor** — abre y muestra PDF/imagen/Word/Excel/PowerPoint/texto; favorito,
   compartir, búsqueda dentro del documento.
2. **Biblioteca** — lista navegable con pestañas dispositivo/app, filtro por
   tipo, buscador, favoritos, renombrar, eliminar.
3. **Home → Recientes** — mismas acciones que Biblioteca sobre los 5
   documentos más recientes, más accesos rápidos a otros módulos.

---

## 2. Requerimientos funcionales

- **RF-VIS-01** El sistema debe permitir marcar/desmarcar un documento como favorito desde el Visor, Biblioteca o Home, y el estado debe ser el mismo sin importar desde dónde se marcó.
- **RF-VIS-02** El sistema debe permitir buscar texto dentro de un documento abierto en el Visor. Para PDF, la búsqueda debe indicar cuántas páginas tienen coincidencias y permitir saltar entre ellas.
- **RF-VIS-03** El sistema debe permitir renombrar un documento desde Biblioteca/Home o desde el Visor (ver RF-VIS-06).
- **RF-VIS-04** El sistema debe permitir eliminar un documento desde Biblioteca/Home, y la eliminación debe ser real (el archivo deja de existir), no solo ocultarlo de la lista.
- **RF-VIS-05** La Biblioteca debe separar los documentos del dispositivo de los generados por la app en pestañas distintas.
- **RF-VIS-09** ✅ "Recientes" en Home debe reflejar los documentos que el usuario realmente abrió más recientemente en el Visor, no solo la fecha de modificación del archivo en disco. Resuelto 2026-08-25 con una tabla Room (`document_history`) — ver §8.
- **RF-VIS-06** ✅ El sistema debe permitir renombrar y eliminar un documento directamente desde el Visor. Implementado 2026-08-29, ver §11.

### Backlog — no implementado
- **RF-VIS-07** Papelera de reciclaje: recuperar un documento eliminado dentro de un plazo antes del borrado definitivo.
- **RF-VIS-08** Resaltado inline de coincidencias de búsqueda dentro del PDF (hoy solo salta de página en página — ver RNF-VIS-01 sobre por qué no hay resaltado real).

---

## 3. Requerimientos no funcionales

- **RNF-VIS-01 (búsqueda en PDF sin resaltado inline):** los PDF se muestran como bitmaps renderizados (`android.graphics.pdf.PdfRenderer`), no como texto con posición — no hay forma de dibujar un resaltado sobre una palabra específica sin reescribir el renderer completo. La búsqueda en PDF por tanto identifica páginas con coincidencias (vía extracción de texto con iText7) y navega entre ellas, no resalta la palabra en pantalla. Word/Excel/PowerPoint/Texto sí resaltan inline porque se renderizan como texto real, no como imagen.
- **RNF-VIS-02 (id de documento consistente):** todo documento debe tener el mismo `id` sin importar desde qué pantalla se cargue — es la clave que usa `FavoritesRepository` para favoritos y alias. Para documentos de MediaStore es el `content://` URI tal cual; para archivos generados por la app es la ruta absoluta **sin** prefijo de esquema (`/data/...`, no `file:///data/...`).
- **RNF-VIS-03 (eliminar es real o falla explícitamente):** eliminar un documento debe borrar el archivo/fila real. Si no se puede (por ejemplo, permiso denegado sobre un archivo de MediaStore que la app no creó), el sistema debe informarlo y **no** quitar el documento de la lista — mismo principio que RNF-SEC-01 (no fallar en silencio).
- **RNF-VIS-04 (mensajes de error):** sin rutas de archivo completas ni detalles internos de excepciones en mensajes visibles al usuario (mismo lineamiento que RNF-SEC-05 / RNF-PDF-04).

---

## 4. Historias de usuario con criterios de aceptación

### HU-VIS-01 — Favorito consistente entre pantallas
**Como** usuario que marca un documento como favorito,
**quiero** que ese estado se vea igual sin importar si lo abro desde el Visor, Biblioteca o Home,
**para** no confundirme sobre qué documentos marqué.

- **AC1** Dado que marco un documento como favorito desde Biblioteca, cuando lo abro después en el Visor, entonces aparece marcado como favorito ahí también.
- **AC2** Dado que marco un documento como favorito desde el Visor, cuando vuelvo a Biblioteca u Home, entonces aparece marcado ahí también.
- **AC3** Esto aplica igual a documentos del dispositivo (`content://`) y a documentos generados por la app (ruta absoluta).

*(Corrige bug real: el Visor calculaba el id de favorito anteponiendo `"file://"` a las rutas absolutas, mientras que Biblioteca/Home usaban la ruta sin prefijo — el mismo documento tenía dos claves distintas según desde dónde se favoriteaba.)*

### HU-VIS-02 — Buscar dentro de un PDF
**Como** usuario leyendo un PDF largo,
**quiero** buscar una palabra y saltar entre las páginas donde aparece,
**para** encontrar la información sin desplazarme manualmente por todo el documento.

- **AC1** Dado que escribo una palabra en el buscador del Visor con un PDF abierto, cuando termino de escribir, entonces veo cuántas páginas tienen coincidencias.
- **AC2** Dado que hay coincidencias, cuando toco "siguiente" o "anterior", entonces el visor se desplaza a la página correspondiente.
- **AC3** Dado que no hay coincidencias, cuando termino de escribir, entonces veo "Sin resultados" en vez de que el buscador no haga nada.
- **AC4** La búsqueda no distingue mayúsculas/minúsculas.

*(Corrige bug real: el botón de búsqueda aparecía habilitado para PDF — `isTextBased` lo incluía — pero `PdfViewerContent` no recibía ningún `searchQuery`; escribir en el buscador no tenía ningún efecto. Word/Excel/PowerPoint/Texto sí funcionaban correctamente.)*

### HU-VIS-03 — Eliminar un documento de verdad
**Como** usuario que ya no necesita un documento,
**quiero** eliminarlo desde Biblioteca u Home,
**para** que deje de ocupar espacio y de aparecer en la lista permanentemente.

- **AC1** Dado que elimino un documento generado por la app, cuando confirmo, entonces el archivo se borra del almacenamiento de la app — no solo desaparece de la lista.
- **AC2** Dado que elimino un documento del dispositivo (MediaStore), cuando confirmo, entonces se borra vía `ContentResolver` si la app tiene permiso.
- **AC3** Dado que el sistema no pudo borrar el archivo (permiso denegado), cuando esto ocurre, entonces el documento **permanece** en la lista y veo un aviso de que no se pudo eliminar — nunca desaparece de la lista sin haberse borrado de verdad.
- **AC4** Dado que reabro la app o refresco la lista después de eliminar exitosamente, cuando esto ocurre, entonces el documento eliminado **no reaparece**.

*(Corrige bug real: `removeDocument()` en `LibraryViewModel` y `HomeViewModel` solo filtraba la lista en memoria — el archivo seguía existiendo en disco/MediaStore, y reaparecía en la siguiente carga de `loadAllDocuments()`. El usuario veía "eliminado" un documento que en realidad seguía intacto y accesible.)*

### HU-VIS-04 — Pestañas dispositivo / app en Biblioteca
*(Ya implementado — HU documentada como base de tests de regresión.)*

**Como** usuario con muchos documentos,
**quiero** distinguir los que creó la app de los que ya tenía en el dispositivo,
**para** encontrar más rápido lo que busco.

- **AC1** Dado que entro a Biblioteca, cuando veo la lista, entonces hay dos pestañas: "Dispositivo" y "Archivos de la app".
- **AC2** Dado que cambio de pestaña, cuando lo hago, entonces se resetean el filtro por tipo y la búsqueda activa.

---

### HU-VIS-05 — Recientes según uso real, no fecha de archivo

**Como** usuario que abre documentos con frecuencia,
**quiero** que "Recientes" en Home muestre lo que abrí hace poco,
**para** no tener que buscarlo si no lo edité recientemente.

- **AC1** Dado que abro un documento en el Visor, cuando vuelvo a Home, entonces aparece primero en "Recientes", sin importar su fecha de modificación en disco.
- **AC2** Dado que desbloqueo un PDF protegido con contraseña, cuando el desbloqueo es exitoso, entonces también cuenta como "abierto" para Recientes (ver contraejemplo: solo mostrar el diálogo de contraseña sin desbloquear no cuenta).
- **AC3** Dado que es una instalación nueva sin historial todavía, cuando entro a Home, entonces "Recientes" se completa con los documentos más recientes por fecha de archivo (mismo comportamiento que antes de esta HU) — nunca queda vacío solo por falta de historial.
- **AC4** Dado que un documento del historial fue borrado desde fuera de la app, cuando cargo Recientes, entonces ese id se descarta silenciosamente y se completa con otro documento — no aparece un hueco ni un error.

### HU-VIS-06 — Renombrar y eliminar desde el Visor
*(Implementado 2026-08-29 — ver §11.)*

**Como** usuario leyendo un documento en el Visor,
**quiero** poder renombrarlo o eliminarlo sin volver a Biblioteca/Home,
**para** no interrumpir el flujo de lectura por una acción de gestión de archivos.

- **AC1** Dado que toco "Renombrar" desde el menú del Visor y confirmo un nombre nuevo, cuando la operación termina, entonces el título de la barra superior refleja el nuevo nombre de inmediato, sin salir del Visor.
- **AC2** Dado que el documento abierto es un archivo generado por la app, cuando renombro, entonces el archivo real en disco cambia de nombre (no es solo una etiqueta) — verificable listando el almacenamiento de la app.
- **AC3** Dado que el documento abierto es de MediaStore (dispositivo), cuando renombro, entonces se guarda como alias (mismo mecanismo ya usado por Biblioteca/Home) sin requerir permiso de escritura sobre el archivo real.
- **AC4** Dado que toco "Eliminar" desde el menú del Visor, cuando esto ocurre, entonces veo un diálogo de confirmación explícito antes de borrar nada — nunca se elimina con un solo toque.
- **AC5** Dado que confirmo eliminar, cuando el borrado es exitoso, entonces el Visor se cierra automáticamente (vuelve a la pantalla anterior) y el documento no reaparece en Biblioteca/Home.
- **AC6** Dado que el borrado no se pudo completar (por ejemplo, sin permiso sobre un archivo de MediaStore que la app no creó), cuando esto ocurre, entonces veo un aviso y el Visor **no** se cierra — mismo criterio que RNF-VIS-03 (no fallar en silencio).

*(Responde la pregunta abierta de §10: "¿vale la pena renombrar/eliminar desde el Visor, o basta con Biblioteca/Home?" — se decidió que sí, siguiendo el patrón de la mayoría de apps de gestión de documentos similares.)*

---

## 5. Bugs de QA a corregir (trazabilidad)

| Bug (barrido de pruebas v1.0 / mejoras pendientes v1.0.1) | HU que lo cubre | Estado |
|---|---|---|
| Favorito (corazón) en recientes no persiste al salir de la vista | HU-VIS-01 | ✅ Corregido — causa raíz real era el id inconsistente entre Visor y Biblioteca/Home, no una falta de persistencia (`FavoritesRepository` ya guardaba correctamente en `SharedPreferences`). |
| Biblioteca: favoritos no persisten al salir de la vista | HU-VIS-01 | ✅ Corregido, misma causa raíz. |
| Visor: búsqueda no tiene función real | HU-VIS-02 | ✅ Corregido — construido `SearchPdfTextUseCase` (extracción de texto por página con iText7) + navegación entre páginas con coincidencias. |
| Biblioteca: falta discriminar archivos de la app vs. del dispositivo | HU-VIS-04 | **Obsoleto** — ya implementado (`LibraryTab.DEVICE`/`APP_FILES`), sin registro de cuándo se agregó. |
| Home: botón "Abrir" del banner no genera ninguna acción | — | **Obsoleto** — ya lanza `ACTION_OPEN_DOCUMENT` correctamente. |
| Home: accesos rápidos de scanner/seguridad/estudio aislados | — | **Obsoleto** — ya navegan a rutas reales en `DocuSmartNavGraph.kt`. |
| Visor: no permite renombrar ni eliminar desde el visor | RF-VIS-06 | ✅ Resuelto 2026-08-29 — ver §11. |
| Visor: margen superior falla, el PDF "se pierde" arriba | — | No verificado — requiere prueba visual en dispositivo/emulador, no se pudo confirmar ni descartar por lectura de código. |
| Biblioteca: tarjetas de favoritos con tamaños inconsistentes en el carrusel | — | Fuera de alcance de esta pasada (ajuste visual, no funcional). |
| Home: botón "Inicio" de la bottom nav deja de responder tras ir a Convertir | — | No verificado — requiere prueba de navegación en vivo, no se pudo confirmar ni descartar por lectura de código. |
| "Recientes" en Home era solo `loadAllDocuments().take(5)` — un documento abierto hoy pero sin modificar no aparecía como reciente | HU-VIS-05 | ✅ Resuelto 2026-08-25 con `document_history` (Room) — ver §8. |

---

## 6. Plan de pruebas unitarias

| # | Cobertura | Estado |
|---|---|---|
| 1 | `SearchPdfTextUseCaseTest` — coincidencias en la página correcta, sin distinguir mayúsculas, varias páginas con coincidencia, sin coincidencias, query en blanco no toca el archivo. | ✅ 5 tests, en verde |
| 2 | `DocumentRepositoryTest` — borra archivo de la app, archivo inexistente → false, borra vía `ContentResolver`, `ContentResolver` no pudo borrar → false, excepción de permisos → false (no propaga), borrado exitoso también limpia el historial, y 4 tests de `mergeHistoryWithDocuments` (orden por historial, ids obsoletos se descartan, fallback completa el resto, sin historial se comporta como antes). | ✅ 10 tests, en verde |
| 3 | `LibraryViewModel`/`HomeViewModel`/`ViewerViewModel` — no cubiertos (ViewModels con `StateFlow` + Hilt, requieren fixture más elaborado); la lógica de negocio que antes vivía implícita en ellos (borrado real, id consistente, fusión de historial) ya quedó cubierta en el use case/repositorio subyacente. | Pendiente si se necesita cobertura de transiciones de estado. |
| 4 | `DocumentHistoryDaoTest` (**primera prueba de integración del proyecto** — ver §8.1) — inserta, upsert no duplica y actualiza la fecha, orden descendente por fecha, respeta el límite, `remove` funciona y no falla con un id inexistente. Corre contra SQLite real (`BundledSQLiteDriver`), no un fake. | ✅ 6 tests, en verde |
| 5 | `DocumentRepository.renameDocument()` (RF-VIS-06, ver §11) — renombra un archivo real de la app y devuelve la nueva ruta absoluta, un documento de MediaStore usa alias sin tocar el archivo (conserva su id), un archivo de la app que no se pudo mover cae al mismo alias. | ✅ 3 tests, en verde |

Todos los tests nuevos generan PDFs reales con iText7 o usan archivos
temporales reales (`Files.createTempDirectory`), mismo patrón que
`security.md` y `pdf-tools.md` — no mocks del contenido de archivos.
`mergeHistoryWithDocuments()` es lógica pura (sin I/O), separada
justamente para poder testearla con listas comunes sin mockear
Room/MediaStore — un `DocumentHistoryDao` fake respaldado por un mapa cubre
el resto (mismo patrón que `fakeSharedPreferences()` en `security.md`).
El propio DAO sí se prueba contra SQLite real, no el fake — ver §8.1.

---

## 8. Historial de documentos abiertos (Room) — 2026-08-25

Primera tabla Room del proyecto. Antes no había base de datos — todo vivía
en `SharedPreferences`/DataStore (`CONTEXT.md` §2).

- **`document_history`** (`core/data/db/DocumentHistoryEntry.kt`): `documentId`
  (mismo id que Biblioteca/Home/Favoritos — RNF-VIS-02) como clave primaria,
  `lastOpenedAt` (epoch millis).
- **`ViewerViewModel`** registra la apertura en dos puntos: al publicar un
  documento cargado normalmente (`publishLoadedDocument`), y al desbloquear
  exitosamente un PDF protegido (`unlockPdfWithPassword`) — mostrar el
  diálogo de contraseña sin desbloquear **no** cuenta como apertura.
- **`DocumentRepository.loadRecentlyOpened(limit)`** cruza el historial con
  `loadAllDocuments()`: usa el orden del historial primero, descarta ids que
  ya no existen en disco, y completa los cupos restantes con los documentos
  más recientes por fecha de archivo (mismo comportamiento que antes de esta
  HU) para que una instalación nueva sin historial no muestre una lista
  vacía.
- Alcance de esta pasada: solo Home (`recentDocuments`). Biblioteca no tiene
  hoy una sección "últimos abiertos" separada de sus pestañas
  Dispositivo/App — el inventario de pantallas (`CONTEXT.md` §9) la
  menciona como pendiente; con `loadRecentlyOpened()` ya construido, agregarla
  ahí es una extensión directa si se decide priorizarla.
- No se migró la persistencia de favoritos/idioma/tema/premium a Room — esas
  son preferencias simples, DataStore/SharedPreferences sigue siendo lo
  correcto ahí; Room solo tiene sentido para datos relacionales/consultables
  como este historial.

### 8.1 Primera prueba de integración del proyecto (2026-08-25)

Todos los tests hasta ahora eran unitarios puros — lógica de negocio con
fakes/mocks, sin tocar frameworks reales. `DocumentHistoryDaoTest` corre
contra **SQLite real** vía `BundledSQLiteDriver` (`androidx.sqlite`), no un
fake en memoria — verifica el DAO que Room genera de verdad: SQL real,
orden real, conflictos de clave primaria reales.

- Sin Robolectric ni emulador: recomendación oficial de Google para probar
  Room en la JVM (su propia guía desaconseja explícitamente Robolectric para
  esto), y consistente con la filosofía ya establecida en este proyecto de
  evitar Robolectric/instrumentación cuando se puede probar la lógica real
  de otra forma (ver `isBiometricAvailable()` en `security.md`).
- Requiere sustituir la variante Android de `sqlite-bundled` por su
  variante `-jvm` solo en el classpath de test (bloque `androidComponents`
  en `app/build.gradle.kts`) — el artefacto Android no trae los binarios
  nativos que necesita la JVM del test unitario.
- **Hallazgo real durante esta prueba:** `DocumentHistoryDao.recordOpen()`
  usaba `@Upsert` (Room genera internamente un insert, y si choca con la
  clave primaria, un update en un segundo paso) — la excepción de conflicto
  no se tradujo bien contra `BundledSQLiteDriver`, y quedó como
  `android.database.SQLException` sin causa legible en vez de resolverse en
  un update silencioso. Cambiado a `@Insert(onConflict = OnConflictStrategy.REPLACE)`
  (`INSERT OR REPLACE`, una sola sentencia SQL) — equivalente para esta
  entidad de 2 columnas, y sin ese problema. Sin esta prueba de integración
  contra SQLite real, el DAO fake de `DocumentRepositoryTest` nunca habría
  detectado este bug.

---

## 9. Compose UI Testing — flujo #1: abrir documento (2026-08-25)

Primera prueba de Compose UI del proyecto. Instrumentada (corre en
dispositivo/emulador local, no en CI todavía — decisión explícita del
usuario: agregar un emulador a GitHub Actions es más lento/complejo,
mejor evaluarlo cuando haya más pruebas de este tipo). JUnit4 por
herramental de Google (`createAndroidComposeRule`), no JUnit5 como el
resto del proyecto — son mundos separados (`androidTest/` vs `test/`).

- **`ViewerScreenTest`** cubre el flujo #1 del manual de marca ("abrir un
  documento en menos de 3 toques"): abrir un documento muestra su nombre en
  la barra superior, y tocar el ícono de favorito llama a
  `toggleFavorite()` con el id correcto.
- Sin infraestructura de Hilt: `ViewerScreen(documentId, onBack, viewModel)`
  ya acepta `viewModel` como parámetro (default `hiltViewModel()`) — en el
  test se pasa un `ViewerViewModel` construido a mano con `mockk`, mismo
  patrón que los unit tests. `documentId = "1"` resuelve por la vía mock
  interna del ViewModel (`getMockDocument`), sin tocar archivos ni
  `ContentResolver` reales.
- **Bug real encontrado al hacer correr esta prueba (no relacionado con el
  test en sí):** `DocuSmartApplication.onCreate()` llama a
  `AdManager.initialize()` sin condición alguna, que carga un interstitial
  **y un video recompensado** de inmediato — exactamente el hallazgo ya
  señalado en `sentinel_report.json` de mayo ("Ad loading is triggered
  immediately upon initialization"), nunca corregido hasta ahora. Bajo
  instrumentación esto hace que *cualquier* prueba de UI dispare una carga
  de anuncio real, inicializando el decoder de video del emulador — en este
  entorno, eso crasheaba el proceso de la app (crash nativo en el códec de
  video, no una excepción Java). Corregido evitando la inicialización de
  anuncios cuando la app corre bajo instrumentación (detectado por la
  presencia de Espresso en el classpath, ya que
  `ActivityManager.isRunningInUserTestHarness()` es solo para Firebase Test
  Lab, no para `connectedAndroidTest` local — Google lo documenta
  explícitamente). **Sin impacto en producción:** la detección solo es
  verdadera cuando el APK de test se mezcla al instrumentar; el
  comportamiento real de carga de anuncios para usuarios no cambió. El
  problema de fondo (carga de anuncios no perezosa) sigue como backlog,
  ahora con evidencia concreta de que puede causar inestabilidad, no solo
  demorar el arranque.
- Requirió resolver dos problemas de configuración de Gradle nuevos para el
  proyecto: un conflicto de "consistent resolution" de AGP entre el
  classpath de la app y el de `androidTest` (`concurrent-futures` fijo en
  1.1.0 vs 1.2.0 exigido por `androidx.test.ext:junit:1.3.0`, forzado a
  1.2.0), y archivos `META-INF/*.md` duplicados entre JARs de test
  (excluidos con patrón comodín en vez de listarlos uno por uno).
- Dependencias nuevas: `androidx.compose.ui:ui-test-junit4` y
  `io.mockk:mockk-android` (variante de mockk para instrumentación, no la
  de `test/`).

---

## 10. Preguntas abiertas

| Pregunta | Notas |
|---|---|
| ¿Vale la pena renombrar/eliminar desde el Visor (RF-VIS-06), o basta con hacerlo desde Biblioteca/Home? | **Resuelto 2026-08-29** — sí, implementado. Ver §11. |
| ¿Papelera de reciclaje (RF-VIS-07) antes o después de las demás funcionalidades del backlog de Herramientas PDF? | Depende de cuánto valor le da el usuario a poder deshacer un borrado. Si se aborda, puede reusar `core/data/db/` (nueva tabla `trash_entries`). |
| Los 2 hallazgos "no verificados" de la tabla de bugs (margen del PDF, bottom nav tras Convertir) — ¿siguen reproduciéndose en la versión actual? | Requieren prueba manual en dispositivo/emulador; no se pudieron confirmar ni descartar solo leyendo el código. |
| ¿Extender "últimos abiertos" también a una sección de Biblioteca (ya mencionada en el inventario de pantallas)? | `loadRecentlyOpened()` ya está construido y probado — agregarlo a Biblioteca es sobre todo trabajo de UI, no de datos. |

---

## 11. RF-VIS-06/HU-VIS-06 — Renombrar y eliminar desde el Visor (2026-08-29)

Primera funcionalidad implementada tras cerrar el backlog completo de
Herramientas PDF (RF-PDF-06 a 15) — continúa el mismo patrón de trabajo
(use case/repositorio + tests + UI + i18n + verificación real en
dispositivo + docs).

- **Extracción a `DocumentRepository.renameDocument()`:** antes de esta HU,
  la lógica de renombrar (intentar `File.renameTo()` real para archivos de
  la app; si es un documento de MediaStore o el rename real falla, guardar
  un alias en `FavoritesRepository`) estaba **duplicada exactamente igual**
  en `LibraryViewModel.renameDocument()` y `HomeViewModel.renameDocument()`
  — un tercer consumidor (el Visor) habría triplicado ese código. Se
  extrajo a `DocumentRepository.renameDocument(documentId, newName): String`,
  que devuelve el id resultante (la ruta nueva si el archivo se movió de
  verdad, o el mismo id si solo se guardó un alias) — información que
  Library/Home no necesitaban (recargan toda la lista o actualizan por id
  sin importar cuál pasó), pero que el Visor sí necesita: al tener un solo
  documento abierto, debe seguir apuntando al archivo correcto después de
  un rename real (`document.id`/`fileUri` se actualizan en el estado local
  con el id devuelto). `LibraryViewModel`/`HomeViewModel` se refactorizaron
  para delegar en el método nuevo, sin cambiar su comportamiento observable
  (mismos tests existentes siguen en verde, sin modificarlos).
- **`ViewerViewModel.kt`** (modificado) — inyecta `DocumentRepository`
  (nuevo), agrega `showRenameDialog`/`showDeleteConfirm`/`deleteError`/
  `documentDeleted` a `ViewerUiState`, y 6 funciones nuevas
  (`onRenameClick`/`dismissRenameDialog`/`renameDocument`/`onDeleteClick`/
  `dismissDeleteConfirm`/`confirmDelete`/`dismissDeleteError`) siguiendo el
  mismo patrón ya usado por `toggleFavorite()`/`shareDocument()` (operan
  sobre `_uiState.value.document?.id`, trabajo en `viewModelScope.launch`).
  Mensajes de error hardcodeados en español (`"No se pudo eliminar el
  archivo"`), consistente con el resto de errores ya existentes en este
  mismo archivo (`"Documento no encontrado"`, `"Contraseña incorrecta..."`,
  etc.) — el `ViewerViewModel` no sigue el patrón de `*Messages` con
  `stringResource()` que sí usan los use cases de Herramientas PDF; se
  respetó la convención existente del archivo en vez de introducir una
  nueva a mitad de camino.
- **`ViewerDocumentDialogs.kt`** (nuevo, `presentation/components/`) —
  `ViewerRenameDialog` (campo de texto con validación de no-vacío en
  tiempo real) y `ViewerDeleteConfirmDialog` (primer diálogo de
  confirmación de borrado del proyecto — **ni Biblioteca ni Home lo
  tienen hoy**, eliminan directo al tocar el ítem del menú contextual sin
  paso intermedio; se documenta como hueco preexistente, fuera de alcance
  de esta HU). A diferencia de `RenameDocumentDialog` en
  `core/ui/components/DocuSmartDocumentItem.kt` (usado por Biblioteca/Home,
  con texto hardcodeado en español), estos dos diálogos nuevos usan
  `stringResource()` desde el día uno — no se reutilizó el composable
  existente para no heredar ese hueco de i18n en una pantalla nueva.
- **`ViewerTopBar.kt`** (modificado) — en vez de agregar 2 `IconButton` más
  a una barra que ya tenía 4 (back/buscar/favorito/compartir, dejaría 6
  íconos apretados), se agregó un menú `DropdownMenu` detrás de un ícono
  `MoreVert` ("Más opciones") con las entradas "Renombrar"/"Eliminar" —
  patrón más escalable si se agregan más acciones al Visor en el futuro.
- **Navegación tras eliminar:** no se agregó ninguna ruta ni callback
  nuevo a `DocuSmartNavGraph.kt` — al detectar `documentDeleted = true` en
  el estado, `ViewerScreen` simplemente invoca el mismo `onBack()` que ya
  recibe como parámetro (mismo mecanismo que el back real del usuario:
  `popBackStack()` a Biblioteca/Home, o `finish()` si no hay stack). Como
  Biblioteca/Home ya recargan su lista al montar (`LaunchedEffect`/`init`),
  no hace falta pasar ningún resultado explícito de vuelta.
- **3 tests unitarios nuevos** (`DocumentRepositoryTest`, sobre el método
  extraído) — renombrar un archivo real de la app devuelve la nueva ruta y
  el archivo físico cambia de nombre en disco, un documento de MediaStore
  usa alias sin tocar ningún archivo (conserva su id), un archivo de la
  app que no se pudo mover cae al mismo alias. `ViewerViewModel` en sí
  sigue sin test unitario nuevo, mismo criterio ya documentado en §6 fila 3
  (ViewModels con `StateFlow`+Hilt sin cobertura directa, lógica de negocio
  cubierta en la capa de repositorio).
- **`ViewerScreenTest.kt`** (instrumentado) actualizado — el constructor de
  `ViewerViewModel` ahora requiere `DocumentRepository`, agregado como
  `mockk(relaxed = true)` en `buildViewModel()`; los 2 tests existentes
  (nombre visible, tocar favorito) no cambiaron de comportamiento.
- **Gauntlet:** `testDebugUnitTest`/`detekt`/`lintDebug` en verde sin
  necesidad de tocar `config/detekt/baseline.xml` — a diferencia de casi
  todas las funcionalidades de Herramientas PDF, esta no introdujo ningún
  hallazgo nuevo de boilerplate (`copyUriToCache` no aplica acá, el código
  nuevo es más simple).
- **Verificado end-to-end en el dispositivo real (app en español), con
  ambas acciones reales, no solo la UI:** abierto un PDF generado por la
  app (`DocuSmart_OCR_20260829_113452.pdf`, desde la pestaña "Mis
  archivos" de Biblioteca — confirma que el id es una ruta absoluta, no
  `content://`) → menú "Más opciones" → "Renombrar" → validación de campo
  vacío visible en tiempo real al borrar el nombre → escrito un nombre
  nuevo → "Guardar" → el título de la barra superior cambió de inmediato
  sin salir del Visor → confirmado con
  `adb shell run-as com.docsmart ls files/pdftools/` que el archivo físico
  en disco **cambió de nombre de verdad** (ya no aparece
  `DocuSmart_OCR_...pdf`, aparece el nuevo nombre con el mismo tamaño en
  bytes) — no fue un alias cosmético. Acto seguido, mismo documento →
  "Más opciones" → "Eliminar" → diálogo de confirmación mostró el nombre
  ya actualizado (confirma que `document.id`/`name` se propagaron
  correctamente tras el rename) → confirmado → el Visor se cerró solo y
  volvió a Biblioteca → el contador de documentos bajó de 70 a 69 (13→12
  en "Mis archivos") → confirmado de nuevo con `run-as ls` que el archivo
  ya no existe en disco — coincide exactamente con HU-VIS-06 AC1 a AC5.
- Verificado también: `testDebugUnitTest`/`detekt`/`lintDebug` en verde.
