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
| Unitarias de Biblioteca (`features.library.*`) | 123/123 OK (9 nuevas) |
| detekt, ktlint, lint, suite unitaria completa | ver resultado del PR |
| Suite instrumentada completa en dos teléfonos reales | **pendiente** |
| Android 12 (Moto E22): botón, elegir fotos, abrir en Visor, quitar | **pendiente** |
| Convertir a PDF y compartir desde una foto agregada | **pendiente** |
| Fotos en la nube (Google Fotos remoto) | **pendiente** |

## Riesgos y límites conocidos

- Acceso por foto: si el usuario mueve o borra el original en su galería, el acceso se pierde («No tenemos acceso a esta imagen…»).
- En dispositivos sin el selector del sistema, la librería cae a `ACTION_OPEN_DOCUMENT` (URI distinta: se mantiene el borrado previo de documentos SAF).
- La declaración de fotos y vídeos de Play Console deja de ser necesaria cuando esta versión reemplace a la 1.1.4 en producción.
