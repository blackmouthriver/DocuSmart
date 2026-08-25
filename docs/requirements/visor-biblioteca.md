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
modificación en disco. 15 tests nuevos (10 + 5 de esta pasada).
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
- **RF-VIS-03** El sistema debe permitir renombrar un documento desde Biblioteca/Home (ya implementado); el Visor no ofrece esta acción (ver backlog, RF-VIS-06).
- **RF-VIS-04** El sistema debe permitir eliminar un documento desde Biblioteca/Home, y la eliminación debe ser real (el archivo deja de existir), no solo ocultarlo de la lista.
- **RF-VIS-05** La Biblioteca debe separar los documentos del dispositivo de los generados por la app en pestañas distintas.
- **RF-VIS-09** ✅ "Recientes" en Home debe reflejar los documentos que el usuario realmente abrió más recientemente en el Visor, no solo la fecha de modificación del archivo en disco. Resuelto 2026-08-25 con una tabla Room (`document_history`) — ver §8.

### Backlog — no implementado
- **RF-VIS-06** Renombrar y eliminar un documento directamente desde el Visor (hoy la barra superior solo tiene volver/buscar/favorito/compartir).
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
| Visor: no permite renombrar ni eliminar desde el visor | RF-VIS-06 (backlog) | Confirmado vigente — no hay UI para esto en `ViewerTopBar`. No implementado en esta sesión. |
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

Todos los tests nuevos generan PDFs reales con iText7 o usan archivos
temporales reales (`Files.createTempDirectory`), mismo patrón que
`security.md` y `pdf-tools.md` — no mocks del contenido de archivos.
`mergeHistoryWithDocuments()` es lógica pura (sin I/O), separada
justamente para poder testearla con listas comunes sin mockear
Room/MediaStore — un `DocumentHistoryDao` fake respaldado por un mapa cubre
el resto (mismo patrón que `fakeSharedPreferences()` en `security.md`).

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

---

## 9. Preguntas abiertas

| Pregunta | Notas |
|---|---|
| ¿Vale la pena renombrar/eliminar desde el Visor (RF-VIS-06), o basta con hacerlo desde Biblioteca/Home? | La mayoría de apps similares lo ofrecen en ambos lugares; queda pendiente de prioridad. |
| ¿Papelera de reciclaje (RF-VIS-07) antes o después de las demás funcionalidades del backlog de Herramientas PDF? | Depende de cuánto valor le da el usuario a poder deshacer un borrado. Si se aborda, puede reusar `core/data/db/` (nueva tabla `trash_entries`). |
| Los 2 hallazgos "no verificados" de la tabla de bugs (margen del PDF, bottom nav tras Convertir) — ¿siguen reproduciéndose en la versión actual? | Requieren prueba manual en dispositivo/emulador; no se pudieron confirmar ni descartar solo leyendo el código. |
| ¿Extender "últimos abiertos" también a una sección de Biblioteca (ya mencionada en el inventario de pantallas)? | `loadRecentlyOpened()` ya está construido y probado — agregarlo a Biblioteca es sobre todo trabajo de UI, no de datos. |
