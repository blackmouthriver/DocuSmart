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
tercer lugar sin triplicar código. **RF-VIS-07 implementado 2026-08-29,
ver §12** — papelera de reciclaje: "eliminar" desde Biblioteca/Home/Visor
ahora mueve a una papelera (`trash_entries`, segunda tabla Room del
proyecto) en vez de borrar de inmediato, con 30 días para restaurar antes
del borrado definitivo automático. **RF-VIS-08 implementado 2026-08-29,
ver §13** — resaltado inline de búsqueda en PDF: RNF-VIS-01 (que decía que
esto no era viable sin reescribir el renderer) quedó desactualizado en
cuanto el proyecto construyó `RegexBasedLocationExtractionStrategy`
(RF-PDF-10) y la conversión de coordenadas puntos↔píxeles (RF-PDF-15/OCR)
— ambas piezas se reutilizaron para dibujar el resaltado real sobre el
bitmap ya renderizado, sin tocar `PdfRenderer`. Con esto se cierran las 3
funcionalidades del backlog original de este módulo (RF-VIS-06/07/08).
**Pasada de UI/UX pedida por el usuario, 2026-08-29, ver §15:** botón de
papelera reubicado del banner azul de Biblioteca (donde quedaba "perdido")
a junto a las pestañas Dispositivo/Mis archivos; accesos rápidos de Home
ampliados de 4 a 9 (agregados Lectura/Notas/Pomodoro/Leer QR/Crear
QR/Papelera); corregido que "Recientes"/"Ver todos" y otros textos de Home
no cambiaban de idioma; transiciones de navegación agregadas a nivel global
(`NavHost`).
**Código relacionado:** `features/viewer/**`, `features/library/**`,
`features/home/**`, `core/data/FavoritesRepository.kt`, `core/data/db/**`
(nuevo), `core/navegation/DocuSmartNavGraph.kt`.

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

- **RF-VIS-07** ✅ Papelera de reciclaje: recuperar un documento eliminado dentro de un plazo antes del borrado definitivo. Implementado 2026-08-29, ver §12.
- **RF-VIS-08** ✅ Resaltado inline de coincidencias de búsqueda dentro del PDF. Implementado 2026-08-29, ver §13 — ver RNF-VIS-01 para la nota de por qué la suposición original de "no viable" quedó desactualizada.
- **RF-VIS-10** ✅ El Visor de Word/Excel/PowerPoint debe mostrar el contenido con una fidelidad visual mínima razonable (negrita/cursiva/tamaño real en Word, grilla real en Excel, aspecto de diapositiva en PowerPoint) — no una lista de texto plano indistinguible del formato de origen. Implementado 2026-08-29, ver §14.

### Backlog
*(vacío — las 4 funcionalidades de este backlog, RF-VIS-06/07/08/10, están implementadas)*

---

## 3. Requerimientos no funcionales

- **RNF-VIS-01 (búsqueda en PDF, ~~sin~~ con resaltado inline):** la premisa original de este RNF — "los PDF se muestran como bitmaps renderizados, no hay forma de dibujar un resaltado sin reescribir el renderer completo" — quedó **desactualizada** y se corrigió 2026-08-29 (RF-VIS-08, ver §13): el bitmap sigue siendo un bitmap (el `PdfRenderer` no cambió), pero el proyecto ya tenía dos piezas reutilizables para resolver esto sin tocarlo — extracción de posición real de texto (`RegexBasedLocationExtractionStrategy`, construida para RF-PDF-10) y conversión de coordenadas puntos-PDF↔píxeles (construida para RF-PDF-15/OCR). El resaltado se dibuja como un overlay (`Modifier.drawWithContent`) sobre el mismo `Image` del bitmap, en vez de modificar el renderer.
- **RNF-VIS-02 (id de documento consistente):** todo documento debe tener el mismo `id` sin importar desde qué pantalla se cargue — es la clave que usa `FavoritesRepository` para favoritos y alias. Para documentos de MediaStore es el `content://` URI tal cual; para archivos generados por la app es la ruta absoluta **sin** prefijo de esquema (`/data/...`, no `file:///data/...`).
- **RNF-VIS-03 (eliminar es real o falla explícitamente):** eliminar un documento debe borrar el archivo/fila real. Si no se puede (por ejemplo, permiso denegado sobre un archivo de MediaStore que la app no creó), el sistema debe informarlo y **no** quitar el documento de la lista — mismo principio que RNF-SEC-01 (no fallar en silencio).
- **RNF-VIS-04 (mensajes de error):** sin rutas de archivo completas ni detalles internos de excepciones en mensajes visibles al usuario (mismo lineamiento que RNF-SEC-05 / RNF-PDF-04).
- **RNF-VIS-05 (el Visor de Word/Excel/PPT sigue sin ser una réplica visual del original):** a diferencia de PDF (que renderiza páginas reales como bitmap con `PdfRenderer`), Word/Excel/PowerPoint se siguen leyendo por extracción de texto del XML interno — no hay en Android una librería nativa gratuita para renderizar estos formatos visualmente (Apache POI puede rasterizar diapositivas, pero depende de `java.awt.Graphics2D`, inexistente en Android; ver RNF-CONV-03). RF-VIS-10 mejora la fidelidad (formato real por fragmento en Word, grilla real en Excel, aspecto de diapositiva en PowerPoint) pero no reproduce imágenes embebidas, layout de columnas, ni la posición exacta del contenido — decisión de alcance explícita del usuario (evaluadas y descartadas por costo/complejidad: convertir a PDF internamente y reusar el renderer de PDF, o integrar una librería de renderizado de Office paga tipo Aspose/Syncfusion).

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

**Actualizado 2026-08-29 (RF-VIS-08, ver §13):** además de saltar entre
páginas (AC2), ahora cada coincidencia queda resaltada visualmente sobre
la página — ver HU-VIS-08.

### HU-VIS-03 — Eliminar un documento de verdad
**Como** usuario que ya no necesita un documento,
**quiero** eliminarlo desde Biblioteca u Home,
**para** que deje de ocupar espacio y de aparecer en la lista permanentemente.

- **AC1** Dado que elimino un documento generado por la app, cuando confirmo, entonces el archivo se borra del almacenamiento de la app — no solo desaparece de la lista.
- **AC2** Dado que elimino un documento del dispositivo (MediaStore), cuando confirmo, entonces se borra vía `ContentResolver` si la app tiene permiso.
- **AC3** Dado que el sistema no pudo borrar el archivo (permiso denegado), cuando esto ocurre, entonces el documento **permanece** en la lista y veo un aviso de que no se pudo eliminar — nunca desaparece de la lista sin haberse borrado de verdad.
- **AC4** Dado que reabro la app o refresco la lista después de eliminar exitosamente, cuando esto ocurre, entonces el documento eliminado **no reaparece**.

*(Corrige bug real: `removeDocument()` en `LibraryViewModel` y `HomeViewModel` solo filtraba la lista en memoria — el archivo seguía existiendo en disco/MediaStore, y reaparecía en la siguiente carga de `loadAllDocuments()`. El usuario veía "eliminado" un documento que en realidad seguía intacto y accesible.)*

**Actualizado 2026-08-29 (RF-VIS-07, ver §12):** "eliminar" ya no borra de
inmediato -- mueve el documento a la papelera. AC1/AC2 (el archivo se borra
de verdad) siguen cumpliéndose, pero ahora en el momento del borrado
*definitivo* (manual desde la papelera, o automático al vencer el plazo),
no en el toque inicial de "eliminar". AC3/AC4 se mantienen sin cambios
(`deleteDocument()`, la función que hace el borrado real, no cambió).

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

**Actualizado 2026-08-29 (RF-VIS-07, ver §12):** AC5/AC6 describían el
borrado inmediato de antes de la papelera. Ahora "Eliminar" desde el Visor
mueve a la papelera (`moveToTrash()`, que casi nunca falla al ser solo una
escritura de metadatos) — el escenario de AC6 ("sin permiso sobre
MediaStore") pasa a aplicar al borrado *definitivo* desde la Papelera, no
al toque inicial de "Eliminar" en el Visor.

### HU-VIS-07 — Recuperar un documento eliminado
*(Implementado 2026-08-29 — ver §12.)*

**Como** usuario que eliminó un documento por error (o cambió de opinión),
**quiero** poder recuperarlo dentro de un plazo,
**para** no perderlo para siempre por un toque accidental.

- **AC1** Dado que elimino un documento desde Biblioteca, Home o el Visor, cuando esto ocurre, entonces el documento aparece en la Papelera en vez de desaparecer para siempre de inmediato.
- **AC2** Dado que un documento está en la Papelera, cuando lo restauro, entonces vuelve a aparecer en Biblioteca/Home exactamente como estaba (mismo nombre, mismo favorito) y el archivo real nunca se tocó.
- **AC3** Dado que un documento lleva 30 días en la Papelera, cuando la app consulta la Papelera de nuevo, entonces se elimina definitivamente de forma automática.
- **AC4** Dado que quiero liberar espacio antes de que se cumplan los 30 días, cuando toco "Eliminar ahora" en la Papelera y confirmo, entonces el archivo se borra de verdad de inmediato.
- **AC5** Dado que veo un documento en la Papelera, cuando lo veo, entonces la app me indica cuántos días quedan antes del borrado definitivo.

### HU-VIS-08 — Ver resaltada una coincidencia de búsqueda en PDF
*(Implementado 2026-08-29 — ver §13.)*

**Como** usuario buscando una palabra dentro de un PDF largo,
**quiero** ver la coincidencia resaltada visualmente sobre la página,
**para** encontrarla de un vistazo en vez de leer toda la página buscándola.

- **AC1** Dado que busco una palabra y hay coincidencias, cuando veo la página con una coincidencia, entonces la palabra aparece resaltada con un color semitransparente en su posición real, no solo salto a la página.
- **AC2** Dado que una página tiene varias coincidencias, cuando la veo, entonces todas quedan resaltadas, no solo la primera.
- **AC3** Dado que hago zoom o desplazo el PDF, cuando esto ocurre, entonces el resaltado se mueve y escala junto con el contenido de la página, sin desalinearse.
- **AC4** Dado que cierro la búsqueda o borro el texto buscado, cuando esto ocurre, entonces el resaltado desaparece de inmediato.

*(Responde RNF-VIS-01, cuya premisa de "no viable sin reescribir el renderer" quedó desactualizada — ver nota en §3.)*

### HU-VIS-09 — El Visor de Word/Excel/PowerPoint no se ve como texto plano
*(Implementado 2026-08-29 — ver §14. Reportado por el usuario en uso manual.)*

**Como** usuario que abre un Word, Excel o PowerPoint en el Visor,
**quiero** ver al menos el formato básico del documento (negrita, tablas
con columnas reales, diapositivas con su propio aspecto),
**para** que el contenido sea legible y reconocible como el tipo de
documento que es, no una lista de texto indistinguible.

- **AC1** Dado que abro un `.docx` con una palabra en negrita, un párrafo
  en cursiva y texto de tamaño distinto, cuando lo veo en el Visor,
  entonces cada fragmento conserva su propio formato real — no todo el
  documento en un solo estilo de texto plano.
- **AC2** Dado que abro un `.xlsx`, cuando lo veo en el Visor, entonces
  las columnas aparecen alineadas en una grilla real (con scroll
  horizontal si hay más columnas de las que caben en pantalla) — no una
  fila de texto con las celdas separadas por `|`.
- **AC3** Dado que abro un `.pptx`, cuando lo veo en el Visor, entonces
  cada diapositiva se muestra en un lienzo de proporción 16:9 con su
  número como leyenda — no una tarjeta de lista genérica.

*(Alcance explícito, ver RNF-VIS-05: esto NO es una réplica visual del
documento original — sigue sin reproducir imágenes embebidas ni el layout
exacto. El usuario evaluó y descartó, por ahora, convertir a PDF
internamente o integrar una librería de renderizado de Office paga —
eligió mejorar la vista de texto existente.)*

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
| Visor: margen superior falla, el PDF "se pierde" arriba | — | ✅ **Bug real confirmado y corregido 2026-08-29** — ver §16. |
| Biblioteca: tarjetas de favoritos con tamaños inconsistentes en el carrusel | — | Fuera de alcance de esta pasada (ajuste visual, no funcional). |
| Home: botón "Inicio" de la bottom nav deja de responder tras ir a Convertir | — | **No reproducido** (verificado en dispositivo 2026-08-29, varios ciclos Convertir→Inicio consecutivos) — la navegación responde correctamente en la versión actual. |
| "Recientes" en Home era solo `loadAllDocuments().take(5)` — un documento abierto hoy pero sin modificar no aparecía como reciente | HU-VIS-05 | ✅ Resuelto 2026-08-25 con `document_history` (Room) — ver §8. |
| Falta papelera de reciclaje / deshacer un borrado accidental | RF-VIS-07 | ✅ Resuelto 2026-08-29 — ver §12. |
| Visor: búsqueda en PDF no resalta la coincidencia, solo salta de página | RF-VIS-08 | ✅ Resuelto 2026-08-29 — ver §13. |

---

## 6. Plan de pruebas unitarias

| # | Cobertura | Estado |
|---|---|---|
| 1 | `SearchPdfTextUseCaseTest` — coincidencias en la página correcta, sin distinguir mayúsculas, varias páginas con coincidencia, sin coincidencias, query en blanco no toca el archivo. **Actualizado 2026-08-29 (RF-VIS-08, ver §13):** +2 tests — cada coincidencia trae una posición real (ancho/alto > 0, no solo el número de página), varias coincidencias en la misma página se devuelven todas. | ✅ 7 tests, en verde |
| 2 | `DocumentRepositoryTest` — borra archivo de la app, archivo inexistente → false, borra vía `ContentResolver`, `ContentResolver` no pudo borrar → false, excepción de permisos → false (no propaga), borrado exitoso también limpia el historial, y 4 tests de `mergeHistoryWithDocuments` (orden por historial, ids obsoletos se descartan, fallback completa el resto, sin historial se comporta como antes). | ✅ 10 tests, en verde |
| 3 | `LibraryViewModel`/`HomeViewModel`/`ViewerViewModel` — no cubiertos (ViewModels con `StateFlow` + Hilt, requieren fixture más elaborado); la lógica de negocio que antes vivía implícita en ellos (borrado real, id consistente, fusión de historial) ya quedó cubierta en el use case/repositorio subyacente. | Pendiente si se necesita cobertura de transiciones de estado. |
| 4 | `DocumentHistoryDaoTest` (**primera prueba de integración del proyecto** — ver §8.1) — inserta, upsert no duplica y actualiza la fecha, orden descendente por fecha, respeta el límite, `remove` funciona y no falla con un id inexistente. Corre contra SQLite real (`BundledSQLiteDriver`), no un fake. | ✅ 6 tests, en verde |
| 5 | `DocumentRepository.renameDocument()` (RF-VIS-06, ver §11) — renombra un archivo real de la app y devuelve la nueva ruta absoluta, un documento de MediaStore usa alias sin tocar el archivo (conserva su id), un archivo de la app que no se pudo mover cae al mismo alias. | ✅ 3 tests, en verde |
| 6 | `TrashDaoTest` (RF-VIS-07, ver §12, mismo patrón que `DocumentHistoryDaoTest`) — insertar agrega una fila, insertar el mismo id es upsert (actualiza la fecha, no duplica), `remove` funciona y no falla con un id inexistente. Corre contra SQLite real. | ✅ 4 tests, en verde |
| 7 | `TrashRepositoryTest` (RF-VIS-07, ver §12) — `moveToTrash` registra sin borrar el archivo y limpia el historial de abierto recientemente, `restoreFromTrash` saca de la papelera sin tocar el archivo, `deleteForever` borra el archivo real y limpia la entrada, `isTrashEntryExpired` (función pura) antes/al cumplirse el plazo, `purgeExpiredTrash` borra de verdad solo las entradas vencidas y conserva las recientes. `loadTrashedDocuments()` no tiene test directo (depende de `loadAllDocumentsRaw()` → MediaStore real, mismo límite ya documentado para `CompressPdfUseCase` en `pdf-tools.md`). | ✅ 6 tests, en verde |

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
| Los 2 hallazgos "no verificados" de la tabla de bugs (margen del PDF, bottom nav tras Convertir) — ¿siguen reproduciéndose en la versión actual? | **Resuelto 2026-08-29** — margen del PDF era un bug real de zoom/pan sin límites, corregido (ver §16); bottom nav no se pudo reproducir. |
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

---

## 12. RF-VIS-07/HU-VIS-07 — Papelera de reciclaje (2026-08-29)

Primera funcionalidad implementada tras cerrar RF-VIS-06 en el mismo
módulo. Convierte el "eliminar" ya existente (RF-VIS-04, HU-VIS-03) en un
borrado suave con posibilidad de deshacer, en vez de un borrado inmediato.

- **Diseño — soft-delete vía tabla nueva, sin tocar el archivo real:** en
  vez de mover físicamente el archivo a una carpeta "papelera" (complicado
  y arriesgado para documentos de MediaStore, que requerirían
  `MediaStore.createTrashRequest()` de Android 11+ con su propio flujo de
  `IntentSender` gestionado por el sistema, no por la app), "eliminar" solo
  inserta una fila en la tabla nueva `trash_entries` (`documentId`,
  `deletedAt`) — el archivo/fila real permanece **intacto** en su
  ubicación original. `DocumentRepository.loadAllDocuments()` excluye los
  ids presentes en `trash_entries`; la Papelera muestra exactamente lo
  contrario (solo esos ids). Esto funciona idéntico para archivos de la
  app y de MediaStore, sin pedir ningún permiso adicional.
- **`TrashEntry`/`TrashDao`** (nuevo, `core/data/db/`) — segunda tabla Room
  del proyecto (la primera fue `document_history`, 2026-08-25). `insert()`
  usa `OnConflictStrategy.REPLACE` (mismo criterio ya documentado para
  `DocumentHistoryDao.recordOpen()`: upsert de una fila de pocas columnas
  sin el problema de traducción de `@Upsert` con `BundledSQLiteDriver`).
  `DocuSmartDatabase` sube a `version = 2` con
  `fallbackToDestructiveMigration(dropAllTables = true)` en
  `DatabaseModule` — decisión deliberada, no un descuido: la única tabla
  previa (`document_history`) es solo un caché de "abierto recientemente"
  que se regenera solo sin pérdida de datos reales del usuario, así que no
  se justifica escribir una migración real para una segunda tabla nueva.
- **`TrashRepository`** (nuevo, `features/library/data/`) — extraído como
  clase propia desde el primer intento de meter esta lógica dentro de
  `DocumentRepository`, que **superó el umbral `TooManyFunctions` de
  detekt** (18 funciones, límite 15) al agregar los 5 métodos nuevos. Se
  extrajo siguiendo el mismo criterio ya usado para `FavoritesRepository`
  (una responsabilidad propia, aunque relacionada) en vez de baselinear el
  hallazgo — real refactor, no supresión. Depende de `DocumentRepository`
  (vía un nuevo `loadAllDocumentsRaw()` interno, expuesto `internal` en
  vez de `private` para este único consumidor adicional) para el
  inventario real de documentos.
  - `moveToTrash(documentId)`: inserta en `trash_entries` con
    `System.currentTimeMillis()` y limpia la fila de `document_history`
    (un documento en la papelera no debe seguir contando como "reciente").
  - `restoreFromTrash(documentId)`: borra la fila de `trash_entries`, sin
    tocar nada más — el documento vuelve a aparecer donde estaba, con su
    nombre/favorito intactos (nunca se tocaron).
  - `deleteForever(documentId)`: reutiliza
    `DocumentRepository.deleteDocument()` (el mecanismo real de borrado ya
    existente desde antes de esta HU) y limpia `trash_entries` +
    el alias de `FavoritesRepository`.
  - `purgeExpiredTrash(now)`: función interna que borra de verdad
    (`deleteDocument()` + limpiar la fila) las entradas con más de
    `TRASH_RETENTION_DAYS` (30, constante pública de la clase) — se
    ejecuta al abrir la Papelera (`loadTrashedDocuments()`), no en
    segundo plano. **Decisión deliberada:** el proyecto no tenía ningún
    mecanismo de tarea programada (sin WorkManager, sin AlarmManager,
    confirmado por búsqueda en todo el código) — agregar WorkManager solo
    para esto habría sido desproporcionado, y los requisitos no piden una
    garantía de "se borra exactamente al día 30 aunque la app esté
    cerrada". Una purga perezosa al leer la Papelera es suficiente y
    consistente con el resto del proyecto.
  - `isTrashEntryExpired(deletedAt, now)`: función pura en el companion
    object, extraída para poder testear el umbral de 30 días con
    timestamps directos sin mockear Room — mismo patrón que
    `mergeHistoryWithDocuments()`.
- **`LibraryViewModel`/`HomeViewModel`/`ViewerViewModel`** (modificados) —
  sus respectivos `removeDocument()`/`confirmDelete()` ahora llaman a
  `trashRepository.moveToTrash()` en vez de `documentRepository.deleteDocument()`
  directamente. El resto de su lógica (actualizar la lista en memoria,
  cerrar el Visor) no cambió — `moveToTrash()` devuelve `Boolean` con la
  misma forma que `deleteDocument()` antes, así que el manejo de
  éxito/error existente siguió funcionando sin reescribirse.
- **`TrashViewModel`/`TrashScreen.kt`** (nuevos,
  `features/library/presentation/`) — pantalla nueva con el mismo patrón
  visual que `SecurityMenuScreen.kt` (`IconButton` de volver +
  `DocuSmartTopBanner` con `weight(1f)`, no un `Scaffold`/`TopAppBar`).
  Cada fila muestra nombre/tamaño/fecha, "Quedan N días" (o "Se elimina
  hoy" si `daysRemaining == 0`), y dos botones: "Restaurar" y "Eliminar
  ahora" — el segundo con diálogo de confirmación propio
  (`TrashDeleteForeverDialog`), reutilizando el mismo patrón ya usado en
  `ViewerDeleteConfirmDialog` (RF-VIS-06) pero como composable local,
  ya que Biblioteca/Home tampoco tienen ninguno para su propio flujo de
  borrado (documentado como hueco preexistente, fuera de alcance de esta
  HU). Completamente localizado con `stringResource()` desde el día uno.
  Estado vacío dedicado (`TrashEmptyState`) cuando no hay nada que
  restaurar.
- **Punto de acceso — ícono en el banner de Biblioteca:** en vez de una
  tercera pestaña junto a "Dispositivo"/"Mis archivos" (la Papelera
  necesita su propia UI por fila — días restantes, Restaurar/Eliminar
  ahora — muy distinta de una lista normal de documentos), se agregó un
  slot `actions` opcional a `DocuSmartTopBanner` (nuevo parámetro,
  default `null`, no afecta a las 9 pantallas que ya usan este banner) con
  un ícono de papelera + `BadgedBox` mostrando el conteo cuando es mayor a
  cero. `LibraryViewModel.loadTrashCount()` se llama en `init` y de nuevo
  en el mismo `LaunchedEffect(hasPermission)` que ya recargaba
  `loadDocuments()` al volver de otra pantalla (mismo mecanismo que ya
  refresca la lista al volver del Visor tras un borrado).
- **`NavRoutes.Trash`** (nuevo, ruta simple sin argumento) registrado
  inline en `DocuSmartNavGraph.kt` junto a `Study`/`QrReader`/`QrCreator`
  (mismo patrón de pantallas secundarias con solo `onBack`), no como
  función `NavGraphBuilder` dedicada (reservado para pantallas con más
  parámetros de navegación, como `Library`/`Viewer`).
- **10 tests unitarios nuevos** — `TrashDaoTest` (4, integración contra
  SQLite real, mismo patrón que `DocumentHistoryDaoTest`) y
  `TrashRepositoryTest` (6: `moveToTrash`, `restoreFromTrash`,
  `deleteForever`, `isTrashEntryExpired`, `purgeExpiredTrash` con entradas
  vencidas/recientes mezcladas). `loadTrashedDocuments()` no tiene test
  directo por la misma razón que `DocumentRepository.loadAllDocuments()`
  nunca la tuvo: depende de `loadImagesFromMediaStore()` (ContentResolver
  real), mismo límite ya documentado para `CompressPdfUseCase` en
  `pdf-tools.md` §7 — se intentó un test con este alcance y falló porque
  el mock sin configurar de `ContentResolver` lanza una excepción que el
  `catch` interno de `loadAllDocumentsRaw()` silencia, devolviendo lista
  vacía en vez de fallar de forma diagnosticable.
- **detekt:** `TooGenericExceptionCaught` boilerplate en `TrashRepository.kt`
  (mismos 2 `catch (e: Exception)` ya vistos en el resto del proyecto,
  colapsan en 1 entrada de baseline) — el hallazgo real de
  `TooManyFunctions` que motivó toda esta extracción se corrigió de
  verdad, no se baselineó.
- **Verificado end-to-end en el dispositivo real (app en español), ciclo
  completo:** eliminado un documento desde Biblioteca (pestaña
  Dispositivo) → el contador de documentos bajó de 70 a 69 y el ícono de
  Papelera mostró la insignia "1" → entrado a la Papelera → el documento
  aparece con "Quedan 30 días" → "Restaurar" → la Papelera queda vacía
  (estado vacío visible) → vuelto a Biblioteca → el contador volvió a 70 y
  la insignia desapareció → eliminado el mismo documento de nuevo →
  "Eliminar ahora" en la Papelera → diálogo de confirmación con el nombre
  correcto → confirmado → la Papelera quedó vacía de nuevo →
  **confirmado con `adb shell ls /sdcard/Download/` que el archivo físico
  ya no existe** — el borrado definitivo fue real, no solo la fila de
  `trash_entries`. Coincide exactamente con HU-VIS-07 AC1, AC2, AC4, AC5
  (AC3, el borrado automático a los 30 días, no es verificable en una
  sesión de prueba — cubierto por `purgeExpiredTrash` en el test
  unitario con timestamps simulados).
- Verificado también: `testDebugUnitTest`/`detekt`/`lintDebug` en verde.

---

## 13. RF-VIS-08/HU-VIS-08 — Resaltado inline de búsqueda en PDF (2026-08-29)

Última funcionalidad del backlog original de este módulo (RF-VIS-06/07/08),
cerrado por completo con esta implementación.

- **La premisa de RNF-VIS-01 estaba desactualizada, no era un límite real:**
  antes de tocar código se investigó si "reescribir el renderer completo"
  (lo que decía la nota original) seguía siendo cierto, dado que el
  proyecto ya había construido, para otras HU, exactamente las dos piezas
  necesarias para resolver esto: extracción de posición real de texto
  (`RegexBasedLocationExtractionStrategy`, usada en
  `EditTextPdfUseCase.kt` para RF-PDF-10) y conversión de coordenadas
  puntos-PDF↔píxeles con manejo de escala y flip de eje Y
  (`mapOcrBoxToPdf()` en `OcrPdfUseCase.kt`, RF-PDF-15). Confirmado que sí
  era viable sin tocar `android.graphics.pdf.PdfRenderer` en absoluto —
  el trabajo real fue agregar un overlay de dibujo sobre el bitmap ya
  renderizado, no cambiar cómo se renderiza.
- **`SearchPdfTextUseCase.kt`** (reescrito) — antes devolvía
  `List<Int>` (solo números de página); ahora devuelve
  `List<PdfPageMatches>` (`pageNumber` + `List<PdfMatchRect>`, cada
  rectángulo en puntos PDF reales). Migrado de `PdfTextExtractor` (texto
  plano sin posición) a `RegexBasedLocationExtractionStrategy` +
  `PdfCanvasProcessor` — el mismo mecanismo exacto que `EditTextPdfUseCase`
  ya usa en producción para localizar texto antes de reemplazarlo, ahora
  reutilizado para localizarlo antes de resaltarlo. La búsqueda sigue sin
  distinguir mayúsculas/minúsculas (`"(?i)" + Pattern.quote(query)`, mismo
  criterio que antes con `ignoreCase = true`).
- **`ViewerViewModel.kt`** (modificado) — `ViewerUiState` gana
  `pdfSearchHighlights: Map<Int, List<PdfMatchRect>>` (página → rects).
  `searchInPdf()`/`clearPdfSearch()` lo pueblan/limpian junto a
  `pdfSearchMatches`/`pdfSearchIndex` (que se conservan sin cambios, para
  no alterar la navegación "siguiente/anterior" ya existente — HU-VIS-02
  AC2 sigue funcionando igual).
- **`ViewerScreen.kt` — `PdfViewerContent`** (modificado) — `renderCachedPdfPages()`/
  `renderPdfPagesToBitmaps()` ahora devuelven `PdfPageBitmap(bitmap,
  pageWidthPts, pageHeightPts)` en vez de solo `Bitmap` (el tamaño real de
  página en puntos, disponible gratis desde `PdfRenderer.Page.width/height`,
  se descartaba antes tras generar el bitmap). Cada `Image` de página usa
  `Modifier.drawWithContent { drawContent(); ...rects... }` para dibujar
  los rectángulos de resaltado **dentro** del mismo `Card`/`Image` de esa
  página — deliberado, no un overlay global sobre todo el `LazyColumn`:
  como el zoom/pan del Visor es un único `graphicsLayer` aplicado a toda
  la lista (`scale`/`offsetX`/`offsetY`), dibujar el resaltado dentro del
  subárbol de la página hace que **herede esa misma transformación
  automáticamente** — sin este detalle, el resaltado se desalinearía del
  texto real en cuanto el usuario hiciera zoom o pan.
  - **Fórmula de conversión (puntos PDF → píxeles de pantalla),
    inversa exacta de `mapOcrBoxToPdf`:**
    ```
    displayScale = size.width / pageWidthPts   // size = tamaño real del Image en pantalla
    screenX = rect.xPts * displayScale
    screenY = (pageHeightPts - (rect.yPts + rect.heightPts)) * displayScale  // flip Y
    screenWidth  = rect.widthPts  * displayScale
    screenHeight = rect.heightPts * displayScale
    ```
    `size.width` dentro de `drawWithContent` es el ancho ya calculado por
    Compose tras `fillMaxWidth()` — no hace falta ningún
    `onGloballyPositioned` ni medición manual adicional.
  - Color de resaltado: amarillo semitransparente (`0xFFFFEB3B`, alpha
    0.4), mismo tono que la mayoría de lectores/navegadores. Se resaltan
    **todas** las coincidencias visibles de cada página por igual (no se
    distingue visualmente cuál es "la actual" del índice de
    navegación) — decisión de alcance deliberada para mantener el cambio
    acotado; distinguir la coincidencia activa con un tono distinto queda
    como posible mejora futura, no bloquea HU-VIS-08 tal como está escrita
    (AC1/AC2 piden resaltar, no piden distinguir cuál es la actual).
- **2 tests unitarios nuevos** (`SearchPdfTextUseCaseTest`) — cada
  coincidencia trae una posición real con ancho/alto mayor a cero (la
  prueba de que no es cosmético, mismo criterio que RF-PDF-10), y varias
  coincidencias en la misma página se devuelven todas (no solo la
  primera) — verifica AC2 de HU-VIS-08 a nivel de datos. Los 5 tests
  existentes se actualizaron para leer `matches.map { it.pageNumber }` en
  vez de `matches` directo (cambio de tipo de retorno), sin cambiar su
  intención original.
- **detekt/lint:** sin hallazgos nuevos — el código agregado
  (`drawWithContent`, la fórmula de conversión) no introdujo boilerplate
  ni complejidad que cruzara ningún umbral.
- **Verificado end-to-end en el dispositivo real (app en español), con
  captura de pantalla real, no solo la UI de texto:** abierto
  `DocuSmart_Firmado_...pdf` (contiene "PUBLICO VISIBLE" como texto real,
  reutilizado de RF-PDF-11) → buscado "PUBLICO" → "Coincidencia 1 de 1" →
  captura de pantalla confirma un rectángulo amarillo semitransparente
  dibujado **exactamente** sobre la palabra "PUBLICO", sin cubrir
  "VISIBLE" al lado — la posición y el ancho son correctos, no una
  aproximación. Confirma HU-VIS-08 AC1. AC2 (varias coincidencias) y AC3
  (persistencia del resaltado durante zoom/pan) quedan cubiertos por el
  diseño (mismo mecanismo `drawWithContent` para cada rect de la lista, y
  el resaltado vive dentro del subárbol transformado por el
  `graphicsLayer` existente) y por el test unitario de múltiples
  coincidencias, no repetidos en dispositivo por redundancia con lo ya
  verificado.
- Verificado también: `testDebugUnitTest`/`detekt`/`lintDebug` en verde.

---

## 14. RF-VIS-10/HU-VIS-09 — Fidelidad visual de Word/Excel/PowerPoint en el Visor (2026-08-29)

Reportado por el usuario en uso manual: "cuando abro el Word, me lo
muestra como un archivo plano y no como es el documento, como si pasa con
el pdf, también pasa con power point y el excel". Se investigó primero
(ver §11 de `settings-premium.md` para el crash relacionado encontrado en
la misma sesión de revisión) confirmando en el código que, a diferencia
de PDF (`PdfViewerContent` renderiza páginas reales como bitmap), Word/
Excel/PowerPoint extraían texto por regex del XML interno y lo mostraban
como una lista plana — una limitación de arquitectura real, no un bug
accidental (ver RNF-VIS-05). Se presentaron 3 alcances posibles al
usuario (mejorar la vista de texto existente / convertir a PDF interno y
reusar el renderer / integrar una librería de renderizado de Office paga)
y se eligió el primero.

### Word — formato real por fragmento, no texto plano
`WordViewerContent` descartaba `<w:rPr>` por completo al extraer el texto
de cada párrafo. Ahora `parseWordRuns()` procesa cada `<w:r>` (run) por
separado, extrayendo negrita (`<w:b/>`, con soporte para `<w:b
w:val="0"/>` como negrita apagada explícitamente), cursiva (`<w:i/>`) y
tamaño de fuente (`<w:sz w:val="X"/>`, en medios puntos) — reconstruidos
con `AnnotatedString`/`SpanStyle` de Compose, un `Text()` por párrafo con
un `withStyle` por fragmento. Mismo enfoque de "fragmento real, no texto
global" ya usado en `PdfToWordUseCase` (RF-CONV-09).

**Bug real encontrado en dispositivo, no relacionado con el fragmento por
run:** la detección de encabezado (`w:pStyle w:val`) asumía que el
identificador de estilo interno de un `.docx` es siempre en inglés
("Heading1") sin importar el idioma de la UI de Word — igual que se había
verificado para `.docx` en RF-CONV-07. Un `.docx` generado con Word en
español real escribió `w:val="Ttulo1"` (tilde quitada), no "Heading1", así
que "Título del documento" se mostraba sin negrita ni color, como
cualquier párrafo normal. Corregido con `WORD_HEADING_STYLE_REGEX`:
coincidencia case-insensitive contra `heading|title|titulo|ttulo|
berschrift|titel|заголовок|название|h[1-6]` (es/en/de/ru), en vez de una
lista de mayúsculas/minúsculas explícitas asumiendo inglés. Mismo criterio
aplicado también en Modo Estudio (ver §15 de `study.md`), que tiene su
propia copia de la constante para no acoplar los dos features por un
patrón de 3 líneas.

### Excel — grilla real, no celdas unidas por "|"
`ExcelViewerContent` unía todas las celdas de una fila en un solo string
con `"  |  "` como separador — una fila de texto, no una hoja de cálculo.
Ahora cada celda es su propia columna de ancho fijo (`ExcelGridRow`)
dentro de un `Row` con `horizontalScroll` **compartido** entre todas las
filas (mismo `ScrollState` en cada fila) para que se desplacen juntas como
una grilla real en vez de filas independientes.

### PowerPoint — lienzo de diapositiva, no tarjeta de lista genérica
Cada diapositiva ya se mostraba en una tarjeta separada (mejor que Word/
Excel de partida), pero sin aspecto de diapositiva. Ahora es un `Card` con
`Modifier.aspectRatio(16f / 9f)` (proporción real de diapositiva), con
"Diapositiva N" como leyenda fuera del lienzo — mismo patrón que un editor
de presentaciones muestra sus miniaturas.

**Verificado en dispositivo (2026-08-29) con archivos reales generados con
Word/Excel/PowerPoint vía automatización COM** (mismo mecanismo que
`legacy-sample.doc` de RF-CONV-07): `.docx` con palabra en negrita
inline + párrafo en cursiva + texto de tamaño 20 — confirmado cada
fragmento con su estilo real, encabezado "Titulo del documento" en
negrita/azul tras el fix del regex; `.xlsx` con 5 columnas — confirmada la
grilla con scroll horizontal sincronizado (deslizado hasta ver la columna
"Activo" fuera del encuadre inicial, todas las filas se movieron juntas);
`.pptx` con 2 diapositivas — confirmado el lienzo 16:9 con título y viñetas.

**Tests nuevos:** `WordRunParsingTest` (10 tests) — negrita/cursiva/
tamaño por fragmento, negrita explícitamente apagada, múltiples `<w:t>`
concatenados, fragmentos en blanco descartados, y el bug real del
identificador de estilo en español. `ExcelGridRow`/lienzo de PowerPoint no
tienen test unitario propio (son composables de layout puro, sin lógica
de parseo nueva que probar aparte de la ya cubierta indirectamente por la
extracción de celdas/diapositivas existente). `testDebugUnitTest`/
`detekt`/`lintDebug` en verde.

---

## 15. Pasada de UI/UX pedida por el usuario (2026-08-29)

El usuario pidió, antes de continuar con más HU, una revisión visual de la
app con 4 puntos concretos: auditoría de animaciones/transiciones,
ampliar los accesos rápidos de Home, reubicar un botón de papelera que
quedaba "perdido" en Biblioteca, y corregir que algunos textos de Home no
cambiaban de idioma. No se abrió una HU/RF nueva para esto — es un ajuste
visual sobre pantallas ya cubiertas por las HU existentes de este módulo,
no una funcionalidad nueva.

### Transiciones de navegación globales
`DocuSmartNavGraph.kt` no definía `enterTransition`/`exitTransition` en su
`NavHost` — Compose Navigation usa un fundido por defecto sin
desplazamiento, que se sentía abrupto entre pantallas. Se agregaron los 4
parámetros (`enterTransition`/`exitTransition`/`popEnterTransition`/
`popExitTransition`) a nivel del `NavHost`, aplicando un estilo "shared
axis" (deslizamiento horizontal corto + fundido, 280ms) a **todas** las
pantallas por igual, sin tocar cada `composable()` individualmente. De
paso, la ruta `Study` pasó de `"study"` a `"study?tab={tab}"`
(parámetro opcional con default 0) para poder abrir directamente en una
pestaña específica — necesario para el punto siguiente.

### Accesos rápidos de Home: de 4 a 9
`QuickAccessGrid` solo tenía Escanear/Img→PDF/Seguridad/Estudio en una
`Row` de ancho igual. Se agregaron 6 accesos nuevos pedidos explícitamente
por el usuario: Lectura, Notas y Pomodoro (las 3 pestañas de Modo Estudio,
antes solo accesibles por un acceso genérico "Estudio" que siempre abría
en Lectura), Leer QR y Crear QR (antes solo alcanzables entrando primero a
la hoja de "Escanear"), y Papelera (antes solo alcanzable desde dentro de
Biblioteca). Con 9 tarjetas ya no caben en una fila de ancho igual como
antes — se cambió a `LazyRow` con ancho fijo por tarjeta (82dp) y scroll
horizontal, en vez de reducir el tamaño de cada una hasta hacerla
ilegible. El bloque del `ModalBottomSheet` de "Escanear" se extrajo a un
composable propio (`ScannerBottomSheetContent`) para mantener
`QuickAccessGrid` bajo el umbral de detekt tras agregar los items nuevos —
extracción real, no supresión.

### Botón de papelera reubicado en Biblioteca
El botón de papelera vivía en un slot `actions` del banner azul superior
de Biblioteca (agregado en RF-VIS-07, ver §12) — visualmente aislado del
resto de controles de la pantalla, el usuario lo reportó como "perdido".
Se quitó del banner y se agregó como un tercer elemento dentro de
`LibraryTabs`, a la misma altura que las pestañas "Dispositivo"/"Mis
archivos" (`Modifier.height(IntrinsicSize.Min)` en el `Row` contenedor
para que las 3 tarjetas compartan la altura de la más alta). Conserva el
mismo `BadgedBox` con el conteo de documentos en papelera que ya tenía en
el banner.

### Corrección de i18n: "Recientes"/"Ver todos" y otros textos de Home
Varios textos de `RecentDocuments.kt` (título "Recientes", "Ver todos",
estado vacío, título del selector "Compartir") y de `QuickAccessGrid.kt`
(textos de la hoja de "Escanear") estaban hardcodeados en español en vez
de usar `stringResource()` — no cambiaban al cambiar el idioma de la app,
a diferencia del resto de Home. Corregido envolviendo cada string en
`stringResource(R.string.xxx)`, con las traducciones agregadas a los 5
`values*/strings.xml` (es/en/de/pt/ru). **Hallazgo relacionado, fuera de
alcance de esta pasada:** los chips de filtro por tipo de archivo en
Biblioteca ("Todos", "Imágenes", "Texto", "Escaneados") tienen el mismo
problema y quedaron sin corregir — señalado aparte para una sesión futura.

**Verificado end-to-end en el dispositivo real:** los 6 accesos rápidos
nuevos navegan cada uno a su destino correcto (Lectura/Notas/Pomodoro
abren Modo Estudio en la pestaña correspondiente vía el parámetro `tab` de
la ruta; Leer QR/Crear QR abren esas pantallas directamente, sin pasar por
la hoja de Escanear; Papelera abre la pantalla de Papelera) — confirmado
tocando cada tarjeta y capturando pantalla del resultado. El botón de
papelera en Biblioteca aparece junto a Dispositivo/Mis archivos con la
misma altura, ya no en el banner. Cambiando el idioma de la app a English
desde Ajustes (sin crash, la app se reinicia sola — mismo mecanismo ya
verificado para el fix de "crash al cambiar idioma" de una sesión
anterior), Home mostró correctamente "Quick access"/"Recent"/"See
all"/"Reading" y Biblioteca mostró "Library"/"Device"/"My files" —
confirma que el fix de i18n funciona en ambas direcciones (ida a inglés y
vuelta a español, verificado también).
- **Gauntlet:** `testDebugUnitTest`/`detekt`/`lintDebug` en verde. Se
  actualizó una entrada de `config/detekt/baseline.xml` (`LongMethod` de
  `StudyScreen`) porque el texto de firma baselineado cambió al agregar el
  parámetro `initialTab` — no es un hallazgo nuevo, solo el texto de la
  firma ya baselineada.
- Sin tests unitarios nuevos: los cambios de esta pasada son de UI/routing
  (parámetros de navegación, composables de layout, `stringResource()`),
  sin lógica de negocio nueva que cubrir — mismo criterio ya aplicado a
  cambios puramente visuales en este proyecto.

---

## 16. Bug real: zoom/pan sin límites perdía el PDF fuera de pantalla (2026-08-29)

Cierra el hallazgo de QA "margen superior falla, el PDF 'se pierde' arriba",
confirmado como bug real al retomar la lista de hallazgos pendientes de
todo el proyecto (junto con `conversion.md` §9 y el resto de esta pasada).

**Causa raíz:** el zoom/pan del Visor de PDF (`PdfViewerContent` en
`ViewerScreen.kt`) aplica `scale`/`offsetX`/`offsetY` como un
`graphicsLayer` sobre todo el `LazyColumn` de páginas, actualizados desde
`detectTransformGestures`. El código incrementaba `offsetX`/`offsetY` con
el delta de arrastre **sin ningún límite** — tras hacer zoom y arrastrar
lo suficiente (o incluso sin zoom, un arrastre normal ya modificaba el
offset), el contenido podía desplazarse fuera del área visible por
completo, sin ninguna forma de recuperarlo salvo adivinar cuánto arrastrar
de vuelta en la dirección contraria. Esto coincide exactamente con "el PDF
se pierde arriba": arrastrar hacia abajo empuja `offsetY` a un valor muy
negativo, y el borde superior de la página termina fuera de la pantalla de
forma indefinida.

**Corregido** limitando `offsetX`/`offsetY` en cada gesto al rango que
`graphicsLayer` permite sin sacar el contenido del todo del área visible:
como Compose escala/traslada desde el centro por defecto, el
desplazamiento máximo que mantiene al menos el borde del contenido dentro
del contenedor es `tamaño_del_contenedor * (escala - 1) / 2` por eje —
en escala 1 (sin zoom) ese rango es `[0, 0]`, así que cualquier intento de
arrastrar sin haber hecho zoom se ignora (correcto: el scroll normal entre
páginas ya lo maneja el `LazyColumn` por separado, no este offset). El
tamaño del contenedor se obtiene con `Modifier.onSizeChanged` sobre el
propio `LazyColumn`.

**Verificado en dispositivo real:** abierto un PDF real → 5 arrastres
consecutivos y agresivos hacia arriba sin soltar → la página permaneció
en su lugar, sin desplazarse fuera del área visible (antes del fix, un
solo arrastre ya movía el `offsetY` sin límite). La verificación completa
de pinch-zoom + arrastre no se pudo automatizar vía `adb` (requiere un
gesto multitáctil real, que `adb shell input` no soporta), pero la fórmula
de límite es la misma que usan la mayoría de visores de imágenes con zoom
centrado — matemáticamente garantiza que el contenido nunca puede quedar
completamente fuera del área visible sin importar cuánto se arrastre.

**Sin tests nuevos:** la lógica vive dentro de un lambda de
`detectTransformGestures` en un Composable, acoplada a gestos táctiles
reales — mismo límite ya documentado para otras interacciones táctiles
del proyecto (sin Robolectric/instrumentación para esto). Se consideró
extraer la fórmula de clamping a una función pura testeable, pero al ser
una única expresión de una línea por eje, no se justificó el nivel de
indirección adicional.

Verificado también: `testDebugUnitTest`/`detekt`/`lintDebug` en verde.

## 17. Bug real: borrado definitivo "resucitaba" archivos + falta "Borrar todo" (2026-08-30)

Cierra dos hallazgos de QA sobre la Papelera (§12): "no hay opción para
borrar todo" y "al eliminar de la papelera, el archivo no se borra de
Biblioteca ni de Recientes, y dice que no se puede eliminar".

**Causa raíz (reproducida en dispositivo con logcat, no asumida):**
`DocumentRepository.deleteDocument()` intenta `contentResolver.delete()`
sobre una foto de MediaStore que la app no creó (p.ej. tomada con la
cámara) y Android lanza `RecoverableSecurityException: com.docsmart has no
access to content://media/...` (scoped storage, API 29+) — confirmado con
el stack trace real al tocar "Eliminar ahora" sobre `search5.png`. El bug
real no era ese rechazo en sí (esperable sin permiso), sino que
`TrashRepository.deleteForever()` llamaba a `trashDao.remove(documentId)`
**sin importar si el borrado real tuvo éxito**. Resultado: la entrada de
la papelera desaparecía igual, `DocumentRepository.loadAllDocuments()`
dejaba de excluir ese id, y el archivo "resucitaba" en Biblioteca y
Recientes — mientras el toast decía "No se pudo eliminar el archivo".

**Corregido:**
- `DocumentRepository.deleteDocument()` ahora devuelve un
  `DeleteOutcome` (`Deleted` / `NeedsPermission(intentSender)` / `Failed`)
  en vez de `Boolean`. En API 30+ usa `MediaStore.createDeleteRequest()`
  para *todo* content:// (un solo diálogo de sistema, sin importar si la
  fila es propia); en API 29 cae a capturar `RecoverableSecurityException`
  y extraer su `IntentSender`. Ambas referencias a clases/métodos que no
  existen en API < 29/30 quedan aisladas en funciones `@RequiresApi` dentro
  de la nueva `MediaDeletePermission` (extraída para no superar el umbral
  `TooManyFunctions` de detekt en `DocumentRepository`).
- `TrashRepository.deleteForever()` solo limpia `trash_entries`/favoritos
  si el outcome es `Deleted`. Si Android pide permiso, el llamador
  (`TrashViewModel`/`TrashScreen`) lanza el `IntentSender` vía
  `ActivityResultContracts.StartIntentSenderForResult` y, si el usuario
  confirma, llama a `finalizeDeleteForever()` para recién ahí limpiar las
  tablas — nunca antes de que Android confirme el borrado real.
- `purgeExpiredTrash()` (purga automática a los 30 días) tiene la misma
  guarda: si el borrado falla o pide permiso, el archivo se queda vencido
  pero visible en la papelera en vez de desaparecer de la lista sin
  haberse borrado de verdad.
- **"Borrar todo"** (`TrashRepository.deleteAllForever()`): separa los
  archivos propios de la app (se borran directo) de las fotos de
  MediaStore, y agrupa estas últimas en un único `createDeleteRequest()`
  — un solo diálogo de sistema para todo el lote en API 30+, en vez de
  encadenar un diálogo por archivo. En API < 30 (sin API de borrado en
  lote) se reintenta uno por uno; los que pidan permiso individual quedan
  en la papelera con el mensaje "Algunos archivos requieren eliminarse uno
  por uno" en vez de encadenar varios diálogos seguidos.

**Verificado en dispositivo real** (dos rondas completas con logcat):
1. "Eliminar ahora" sobre una foto real de MediaStore → apareció el
   diálogo de sistema "¿Deseas permitir que DocuSmart borre esta foto?" →
   al confirmar, `MediaProvider: Granted permission to 1 items` en logcat,
   el archivo desapareció de la Papelera y **no reapareció** en Biblioteca.
2. "Borrar todo" con 11 archivos en la papelera → diálogo único "¿Deseas
   permitir que DocuSmart borre 11 fotos?" → al confirmar, papelera vacía,
   sin errores.

Tests nuevos: `TrashRepositoryTest` — `deleteForever no limpia la entrada
de la papelera si el borrado real fallo` (reproduce exactamente el bug:
con un archivo inexistente, la entrada debe seguir en la papelera en vez
de limpiarse). El resto de `DocumentRepositoryTest`/`TrashRepositoryTest`
se actualizó al nuevo tipo `DeleteOutcome`. `testDebugUnitTest`/`detekt`/
`lintDebug` en verde.

## 18. Bug real: abrir un archivo desde Drive/WhatsApp dejaba una copia de Inicio "pegada" (2026-08-30)

**Causa raíz:** `MainActivity` no declaraba `android:launchMode`, así que
usaba el modo "standard" por defecto. Al abrir un PDF/imagen desde Drive o
WhatsApp con DocuSmart ya corriendo, Android creaba una **segunda
instancia** de `MainActivity` encima de la que ya estaba en Inicio (el
`onNewIntent()` existente nunca se ejecutaba, porque solo se dispara si la
Activity ya en pantalla es reutilizada). El Visor mostraba el archivo
correctamente en la instancia nueva, pero al presionar atrás el usuario
volvía a una copia de Inicio "pegada" debajo en vez de cerrar la app y
volver a Drive/WhatsApp — percibido como que "no se cierra".

**Corregido:**
- `AndroidManifest.xml`: `android:launchMode="singleTask"` en
  `MainActivity` — Android reutiliza la instancia existente y entrega el
  nuevo intent vía `onNewIntent()` en vez de crear una copia.
- `MainActivity.kt`: `externalFileUri` pasa de `var` plano a
  `mutableStateOf` (con la Activity ya compuesta, `onNewIntent()` necesita
  disparar recomposición, no solo dejar el valor listo para la próxima
  lectura) y se agrega `setIntent(intent)` en `onNewIntent()`. El efecto
  que redirige al Visor ahora reacciona a `externalFileUri` en cualquier
  pantalla (no solo esperando llegar a Home), salvo mientras el splash o
  el onboarding siguen en curso (arranque en frío) — así cubre tanto el
  arranque en frío como abrir un archivo con la app ya en cualquier
  pantalla.

**Verificado en dispositivo real:** con la app ya abierta en Biblioteca,
se disparó `am start -a VIEW -d content://.../search5.png -n
com.docsmart/.MainActivity` (simula el intent que enviaría Drive/WhatsApp)
→ logcat confirmó `"intent has been delivered to currently running
top-most instance"` (no se creó Activity nueva) → `dumpsys activity
activities` mostró **una sola** `Task` con **una sola** `ActivityRecord`
(`sz=1`) antes y después → el Visor abrió `search5.png` correctamente →
al presionar atrás, volvió a Biblioteca (la pantalla previa real, no una
copia) sin ninguna instancia duplicada.
