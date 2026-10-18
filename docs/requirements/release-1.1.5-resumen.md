# Release 1.1.5 — resumen (en preparación)

> **Estado:** implementación lista en la rama `claude/selector-fotos-1-1-5`. **Sin subir de versión todavía** (`versionCode`/`versionName` siguen en 10 / 1.1.4): el bump se hace en un PR aparte cuando el propietario lo apruebe.

Sucede a la **1.1.4 (versionCode 10)**, que solo corrigió el requisito de 16 kB de Google Play. Esta versión quita el permiso `READ_MEDIA_IMAGES`, lo reemplaza por el selector de fotos del sistema y suma «Agregar documentos», una papelera que ahora sí restaura documentos agregados y «Vincular carpeta» más discreto.

## Por qué

La revisión de producción de Play marcó «Usar selectores de sistema alternativos para fotos o vídeos». DocuSmart no es una galería: las imágenes entran por selectores del sistema. El único uso del permiso era listar en Biblioteca las 50 fotos más recientes de la galería, y la Biblioteca entera quedaba bloqueada tras él.

Una prueba corta (2026-10-14, Edge 30 Neo, Android 14) demostró que el selector de fotos entrega un permiso de URI **persistente** (`takePersistableUriPermission`) que sobrevive a matar la app y a **reiniciar el teléfono**, y que esas fotos abren en el Visor.

## Qué cambia para el usuario

| Área | Cambio |
|---|---|
| Permisos | Ya no se declara `READ_MEDIA_IMAGES` ni `READ_MEDIA_VISUAL_USER_SELECTED`. En Android 13+ la app no pide ningún permiso de medios. `READ_EXTERNAL_STORAGE` sigue solo hasta Android 12 (`maxSdkVersion=32`) |
| Biblioteca → Dispositivo | **Botón «Agregar fotos»**: abre el selector del sistema; las fotos elegidas quedan en la Biblioteca (acceso persistente + historial) |
| Biblioteca | Ya no se bloquea con «Sin permisos» en Android 13+; deja de listar automáticamente la galería de otras apps |
| «Eliminar» sobre una foto agregada | Solo la quita de DocuSmart (libera el permiso persistente y la saca del historial). **Nunca** pide borrar la foto original de la galería |
| Visor | Si una foto ya no es accesible (p. ej. una foto de galería guardada en Recientes antes de este cambio), muestra «No tenemos acceso a esta imagen. Vuelve a agregarla desde Biblioteca → Agregar fotos» en vez del error genérico |
| **Agregar documentos** | Segundo botón, en una **tarjeta** con el encabezado centrado «Trae tus documentos y fotos desde tu celular a DocuSmart y gestiónalos a tu gusto» (12 idiomas) y botones de esquinas rectas, junto a «Agregar fotos» y de la misma altura: selector de archivos del sistema, multi-selección (PDF, Word, Excel, PowerPoint, texto y zip), sin ningún permiso. Los documentos quedan en la Biblioteca con acceso persistente de lectura y escritura (cae a solo lectura si el proveedor no concede escritura) |
| **Papelera real para documentos** | Un documento agregado o abierto con «Abrir» ahora aparece en la Papelera tras «Eliminar» y se puede **restaurar**. Antes la Papelera salía vacía |
| **Vincular carpeta** | Se conserva, pero pasa de tarjeta grande a un enlace pequeño dentro de la tarjeta «Agregar», bajo los botones; se agregan eventos de analítica para decidir con datos si se mantiene |
| Textos | Botón, mensaje del Visor y la descripción de la pestaña Dispositivo, en los 12 idiomas |

## Qué se ve distinto (hay que decirlo en las notas de versión)

- Las fotos que el usuario ya tenía en la galería **dejan de aparecer solas** en Biblioteca → Dispositivo. Para tenerlas ahí se agregan con «Agregar fotos».
- Las fotos de galería que ya estaban en Recientes/historial de versiones anteriores ya no se pueden abrir; hay que volver a agregarlas.

## Detalle técnico

- `DocumentRepository.addPickedImages(uris)`: `takePersistableUriPermission` por foto (tolera `SecurityException`) y registro en `document_history`.
- `PickedMediaUris.isPickedReference(id)`: los ids `content://media/picker...` son referencias de solo lectura. `DocumentRepository.deleteDocument` las quita sin tocar la original, y `TrashRepository.isMediaStoreId` las excluye del pedido de borrado en lote de MediaStore (`createDeleteRequest`), que habría pedido borrar las fotos reales.
- `ImageViewerContent`: distingue `SecurityException` (sin acceso) de otros fallos de carga.
- Límite del sistema: 5 000 concesiones de medios por app (el sistema quita la más antigua al superarlo).
- `DocumentRepository.addPickedDocuments(uris)`: intenta `takePersistableUriPermission` de lectura y escritura y cae a solo lectura; siempre registra en `document_history`.
- `TrashRepository.loadTrashedDocuments` describe (nombre, tamaño, tipo) por su id los documentos que **solo viven del historial** y que `moveToTrash` sacó de él; `restoreFromTrash` los vuelve a registrar en el historial. Para las fotos del selector no hay papelera: «Eliminar» las quita directo y libera el permiso.
- `DocuSmartAnalytics`: `library_files_added` (tipo y cantidad) y `library_folder_linked`.
- Se quitaron las cadenas de la tarjeta grande de «Vincular carpeta» (`library_link_downloads_title` y `_body`).

## Verificación

| Comprobación | Resultado |
|---|---|
| Prueba corta del selector en el Edge (persistencia, reinicio, Visor) | OK, 0 cierres |
| Unitarias completas | 1 559/1 559 + 3 nuevas (ViewModel y papelera); `features.library` 126/126 |
| detekt, ktlint, lint | OK |
| SonarCloud | Fallaba solo por cobertura del código nuevo (73 % < 80 %); se agregaron pruebas de `onPhotosPicked` |
| Manifiesto final de release | Sin ningún `<uses-permission>` de `READ_MEDIA_*` (las coincidencias de texto son comentarios) |
| Suite instrumentada, Moto E22, corrida anterior (solo cambios de fotos) | 8/8 fragmentos OK, 800 pruebas |
| Suite instrumentada, Edge 30 Neo, corrida anterior (solo cambios de fotos) | 7/8 OK; el único fallo (`ViewerDocumentTypesTest.excel_muestraHojasConPestanasYCambiaDeHoja`, «must have a looper») pasó 3/3 sola y la clase completa dio 23/23: intermitencia preexistente, ajena a este cambio |
| Biblioteca y Visor con el APK corregido (Edge) | `features.library` 81/81; `features.viewer` 98/98 (dos veces; un cierre de proceso en la primera corrida, flake conocido de Compose en el Visor) |
| Instalación limpia, sin diálogo de permisos | OK |
| Agregar fotos con el selector, 3 + 2 fotos, todas con acceso persistente | OK |
| Convertir una foto agregada a PDF | OK (`DocuSmart_…pdf`, 63 KB, 0 cierres) |
| Foto vieja de galería abierta en el Visor | Mensaje nuevo «No tenemos acceso a esta imagen…», 0 cierres |
| «Eliminar» una foto agregada: la original no se toca, el permiso se libera | OK tras la corrección (galería sin cambios, original presente, permiso liberado) |
| Agregar 3 documentos con el selector (modo `0x3`, persistidos) | OK |
| Eliminar → Papelera lista el documento → Restaurar → reaparece en Biblioteca | OK |
| «Eliminar ahora» borra el archivo real (solo el de prueba); los demás quedan intactos | OK |
| Unitarias `features.library` con los cambios de documentos | 133/133 (7 nuevas); instrumentadas `features.library` 85/85 en Edge y E22 (incluye `DocumentPickingTest`) |
| **Suite instrumentada completa con todos los cambios (commit `84ada52`), Edge 30 Neo (Android 14)** | **8/8 fragmentos OK.** Un cierre de proceso conocido del Visor en el fragmento 3 (`powerPointCorrupto…`), resuelto por el reintento automático |
| **Suite instrumentada completa con todos los cambios, Moto E22 (Android 12)** | **8/8 fragmentos OK.** El fragmento 0 falló una vez con 11/117 (todas «No compose hierarchies found», en componentes sin relación con este PR, y tardó 547 s) y **repetido pasó 117/117** en 403 s: tropiezo del arranque, no del código |
| **Unitarias completas** | **1 569 en 158 clases, 0 fallas** (mínimo de la guardia: 1 400) |
| Compartir desde una foto agregada | OK (probado a mano por el propietario, 2026-10-18) |
| Traer fotos y documentos desde Google Drive con los selectores del sistema | OK (probado a mano por el propietario, 2026-10-18). Fotos de Google Fotos remoto: sin probar por separado |

## Hallazgo: «Borrar todo» y «Eliminar ahora» no borraban archivos propios (corregido en este PR)

Reportado por el propietario y reproducido en el Edge (Android 14) con 5 archivos que la propia app había guardado en Descargas (4 `.txt` y 1 PDF): «Borrar todo» confirmaba y **no pasaba nada**, los archivos seguían en disco y no había ningún aviso. Es un defecto **anterior a este PR**, no una regresión.

- **Causa:** en Android 11+ `DocumentRepository.deleteDocument` solo pedía el borrado con `MediaStore.createDeleteRequest()`, que **solo acepta imagen, vídeo y audio** y rechaza un PDF o un `.txt` «pase lo que pase» (`IllegalArgumentException`, ya documentado en `MediaDeletePermission`). Nunca se intentaba el borrado directo, que sí funciona para filas propias de la app. Además `deleteAllForever` devolvía «Hecho» aunque todo hubiera fallado.
- **Corrección:** `MediaDeletePermission.deleteOwned()` borra directo; `deleteDocument` y `deleteAllForever` lo intentan primero y solo piden el diálogo del sistema para lo ajeno; si nada se pudo borrar devuelven el nuevo `BulkDeleteOutcome.Failed` y la pantalla muestra «No se pudo eliminar el archivo».
- **Verificación:** mismos 5 archivos en el Edge: 5 de 5 borrados del disco, Papelera vacía, el resto de Descargas intacto, 0 cierres. Cuatro pruebas unitarias nuevas que no compilaban sin el arreglo; `features.library` 137/137 unitarias y 85/85 instrumentadas en Edge y E22.
- **Límite:** los documentos que **no** son de la app y no son imagen/vídeo/audio (p. ej. un PDF que dejó el navegador) siguen sin poder borrarse desde la Papelera en Android 11+: Android no lo permite sin acceso a todos los archivos. Ahora al menos avisa.

## Hallazgo de la prueba manual de fotos (corregido en este PR)

Al «Eliminar» una foto agregada, desaparecía de la Biblioteca pero la **Papelera salía vacía**. Causa: `TrashRepository.moveToTrash` inserta en `trash_entries` **y borra la foto del historial**, y la Biblioteca de estas fotos vive *solo* del historial; la Papelera cruza con el inventario y ya no la encontraba. Consecuencias: no se podía restaurar, la fila quedaba huérfana y el permiso persistente seguía ocupado. La foto original nunca se tocó.

Corrección: para `content://media/picker...`, `moveToTrash` la quita directo (libera el permiso persistente, limpia historial y favorito/alias vía `onPermanentlyDeleted`) y no inserta en la papelera, porque no hay nada que restaurar: se vuelve a agregar con «Agregar fotos». Prueba unitaria nueva en `TrashRepositoryTest`.

**Lección:** las pruebas unitarias cubrían `deleteDocument` y «Borrar todo» pero no `moveToTrash`; solo la prueba en el teléfono encontró este camino.

## Riesgos y límites conocidos

- Acceso por foto: si el usuario mueve o borra el original en su galería, el acceso se pierde («No tenemos acceso a esta imagen…»).
- En dispositivos sin el selector del sistema, la librería cae a `ACTION_OPEN_DOCUMENT` (URI distinta: se mantiene el borrado previo de documentos SAF).
- La declaración de fotos y vídeos de Play Console deja de ser necesaria cuando esta versión reemplace a la 1.1.4 en producción.
- «Eliminar ahora» sobre un documento agregado borra el archivo real de su carpeta (es lo que ya hacía con «Abrir»); la confirmación lo advierte. Si el proveedor solo concedió lectura, el borrado falla y el documento se queda en la Papelera.
- La multi-selección del selector de archivos del sistema exige mantener pulsado un archivo para empezar a seleccionar; si el usuario toca uno a uno se agregan de a uno.
- Los nombres de las fotos agregadas aparecen como el identificador del selector (p. ej. `1000158199.jpg`), no con su nombre original: el selector de fotos los anonimiza. Los documentos conservan su nombre.
