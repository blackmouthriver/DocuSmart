# Release 1.1.5 — resumen (en preparación)

> **Estado:** implementación lista en la rama `claude/selector-fotos-1-1-5`. **Sin subir de versión todavía** (`versionCode`/`versionName` siguen en 10 / 1.1.4): el bump se hace en un PR aparte cuando el propietario lo apruebe.

Sucede a la **1.1.4 (versionCode 10)**, que solo corrigió el requisito de 16 kB de Google Play. Esta versión quita el permiso `READ_MEDIA_IMAGES` y lo reemplaza por el selector de fotos del sistema.

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
| Textos | Botón, mensaje del Visor y la descripción de la pestaña Dispositivo, en los 12 idiomas |

## Qué se ve distinto (hay que decirlo en las notas de versión)

- Las fotos que el usuario ya tenía en la galería **dejan de aparecer solas** en Biblioteca → Dispositivo. Para tenerlas ahí se agregan con «Agregar fotos».
- Las fotos de galería que ya estaban en Recientes/historial de versiones anteriores ya no se pueden abrir; hay que volver a agregarlas.

## Detalle técnico

- `DocumentRepository.addPickedImages(uris)`: `takePersistableUriPermission` por foto (tolera `SecurityException`) y registro en `document_history`.
- `PickedMediaUris.isPickedReference(id)`: los ids `content://media/picker...` son referencias de solo lectura. `DocumentRepository.deleteDocument` las quita sin tocar la original, y `TrashRepository.isMediaStoreId` las excluye del pedido de borrado en lote de MediaStore (`createDeleteRequest`), que habría pedido borrar las fotos reales.
- `ImageViewerContent`: distingue `SecurityException` (sin acceso) de otros fallos de carga.
- Límite del sistema: 5 000 concesiones de medios por app (el sistema quita la más antigua al superarlo).

## Verificación

| Comprobación | Resultado |
|---|---|
| Prueba corta del selector en el Edge (persistencia, reinicio, Visor) | OK, 0 cierres |
| Unitarias completas | 1 559/1 559 + 3 nuevas (ViewModel y papelera); `features.library` 126/126 |
| detekt, ktlint, lint | OK |
| SonarCloud | Fallaba solo por cobertura del código nuevo (73 % < 80 %); se agregaron pruebas de `onPhotosPicked` |
| Manifiesto final de release | Sin ningún `<uses-permission>` de `READ_MEDIA_*` (las coincidencias de texto son comentarios) |
| **Suite instrumentada, Moto E22 (Android 12)** | **8/8 fragmentos OK, 800 pruebas** |
| **Suite instrumentada, Edge 30 Neo (Android 14)** | 7/8 OK; el único fallo (`ViewerDocumentTypesTest.excel_muestraHojasConPestanasYCambiaDeHoja`, «must have a looper») pasó 3/3 sola y la clase completa dio 23/23: intermitencia preexistente, ajena a este cambio |
| Biblioteca y Visor con el APK corregido (Edge) | `features.library` 81/81; `features.viewer` 98/98 (dos veces; un cierre de proceso en la primera corrida, flake conocido de Compose en el Visor) |
| Instalación limpia, sin diálogo de permisos | OK |
| Agregar fotos con el selector, 3 + 2 fotos, todas con acceso persistente | OK |
| Convertir una foto agregada a PDF | OK (`DocuSmart_…pdf`, 63 KB, 0 cierres) |
| Foto vieja de galería abierta en el Visor | Mensaje nuevo «No tenemos acceso a esta imagen…», 0 cierres |
| «Eliminar» una foto agregada: la original no se toca, el permiso se libera | OK tras la corrección (galería sin cambios, original presente, permiso liberado) |
| Compartir desde una foto agregada | **pendiente (manual)** |
| Fotos en la nube (Google Fotos remoto) | **pendiente (manual)** |

## Hallazgo de la prueba manual (corregido en este PR)

Al «Eliminar» una foto agregada, desaparecía de la Biblioteca pero la **Papelera salía vacía**. Causa: `TrashRepository.moveToTrash` inserta en `trash_entries` **y borra la foto del historial**, y la Biblioteca de estas fotos vive *solo* del historial; la Papelera cruza con el inventario y ya no la encontraba. Consecuencias: no se podía restaurar, la fila quedaba huérfana y el permiso persistente seguía ocupado. La foto original nunca se tocó.

Corrección: para `content://media/picker...`, `moveToTrash` la quita directo (libera el permiso persistente, limpia historial y favorito/alias vía `onPermanentlyDeleted`) y no inserta en la papelera, porque no hay nada que restaurar: se vuelve a agregar con «Agregar fotos». Prueba unitaria nueva en `TrashRepositoryTest`.

**Lección:** las pruebas unitarias cubrían `deleteDocument` y «Borrar todo» pero no `moveToTrash`; solo la prueba en el teléfono encontró este camino.

## Riesgos y límites conocidos

- Acceso por foto: si el usuario mueve o borra el original en su galería, el acceso se pierde («No tenemos acceso a esta imagen…»).
- En dispositivos sin el selector del sistema, la librería cae a `ACTION_OPEN_DOCUMENT` (URI distinta: se mantiene el borrado previo de documentos SAF).
- La declaración de fotos y vídeos de Play Console deja de ser necesaria cuando esta versión reemplace a la 1.1.4 en producción.
