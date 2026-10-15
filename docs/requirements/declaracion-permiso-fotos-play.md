# Declaración de permisos de fotos y vídeos (Play Console) — borrador

Fecha: 2026-10-14. Permiso declarado: `READ_MEDIA_IMAGES` (solo imágenes; `READ_MEDIA_VIDEO` y `READ_MEDIA_AUDIO` ya se quitaron en la 1.1.3).
Política: *Photo and Video Permissions* de Google Play. El formulario pide el caso de uso principal y por qué un selector del sistema no basta.

> **Estado:** borrador para que el propietario lo revise y lo pegue en Play Console. No está enviado. La ruta exacta del formulario en Play Console no se pudo confirmar en la documentación de Google: suele aparecer en el aviso del lanzamiento ("Problemas detectados") o en Política y programas → Contenido de la app.

## 1. Qué hace la app con el permiso (verificado en el código)

| Dónde | Qué hace | Archivo |
|---|---|---|
| Biblioteca → Dispositivo | Lista **solo metadatos** (nombre, tamaño, fecha, tipo) de las **50 imágenes más recientes** del dispositivo, junto a los documentos | `DocumentRepository.loadImagesFromMediaStore` |
| Biblioteca → acciones por documento | El usuario elige una imagen y la **convierte a PDF**, la abre en el visor, la **comparte**, la marca favorita, le crea un QR o la mueve a la Carpeta Segura | `LibraryScreen` (`onConvertClick`, `onCreateQrClick`, `onMoveToSecureFolderClick`…) |
| Visor | **Muestra** la imagen elegida. Esos accesos quedan en Recientes/historial y se reabren después, lo que requiere acceso persistente al archivo | `ViewerScreen.ImageViewerContent`, `ViewerViewModel` |
| Papelera de la app | "Eliminar" mueve el registro a la papelera de la app (restaurable); no borra de la galería sin confirmación del sistema | `TrashRepository`, `MediaDeletePermission` |

No hace (por diseño documentado, ver `project_ia_on_device_over_cloud` y los backlogs "100 % local"): no sube imágenes a ningún servidor, no las analiza con IA en la nube, no las usa para publicidad ni las comparte con terceros. El procesamiento (conversión, OCR) es local en el dispositivo.

Matiz a tener presente: la app sí usa AdMob y Firebase/Crashlytics (identificadores y diagnósticos, **no** contenido de imágenes). La papelera de la app es lógica (tabla `trash_entries`); el borrado definitivo de una imagen de la galería pasa por la confirmación del sistema (`MediaDeletePermission`). Antes de afirmar "no sale del dispositivo" en el formulario, conviene contrastarlo con la política de privacidad publicada.

## 2. Texto sugerido para el formulario (español)

**Caso de uso principal (resumen):**
> DocuSmart es un gestor de documentos local. Su Biblioteca reúne en una sola lista los documentos del usuario (PDF, Word, Excel, PowerPoint, texto) y sus imágenes, para que el usuario decida qué hacer con cada una: convertirla a PDF, visualizarla, compartirla, marcarla como favorita o guardarla en una carpeta segura. El usuario tiene control total: la app no extrae, copia ni envía sus archivos; todo se procesa en el dispositivo.

**Por qué se necesita acceso a las imágenes:**
> La función principal de la Biblioteca es mostrar de forma continua y unificada los archivos del usuario, incluidas sus imágenes, junto con favoritos, recientes, búsqueda y papelera. El usuario debe poder ver y volver a abrir esas imágenes entre sesiones sin repetir la selección cada vez. El acceso a imágenes se usa solo para listar nombre, tamaño y fecha (hasta 50 recientes) y para mostrar o convertir la imagen que el usuario elige.

**Por qué el selector del sistema no es suficiente:**
> El selector del sistema es una operación puntual: entrega un archivo temporal y no permite una biblioteca persistente que reúna documentos e imágenes, con recientes, favoritos y búsqueda en un solo lugar, ni reabrir una imagen vista antes. DocuSmart usa el selector del sistema en sus flujos de una sola vez (Convertir, Imagen a PDF, QR, Herramientas PDF), pero la Biblioteca y el Visor requieren acceso persistente a las imágenes del usuario.

**Cómo se protege la privacidad:**
> Las imágenes nunca salen del dispositivo. La app lee únicamente las imágenes que el usuario elige abrir o convertir; no hay análisis ni subida. Solo se solicita el permiso de imágenes (no de vídeo ni de audio).

## 3. English version (por si el formulario lo pide)

**Core use case:**
> DocuSmart is an on-device document manager. Its Library shows the user's documents (PDF, Word, Excel, PowerPoint, text) and images in one list so the user can decide what to do with each file: convert it to PDF, view it, share it, favorite it, or store it in a secure folder. The user is fully in control: the app does not extract, copy or upload any file; everything is processed on the device.

**Why broad access is needed:**
> The Library's core function is a continuous, unified view of the user's files, including their images, with favorites, recents, search and trash. Users must be able to see and re-open those images across sessions without re-selecting them each time. Image access is used only to list name, size and date (up to 50 most recent) and to display or convert the image the user chooses.

**Why the system picker is not sufficient:**
> The system picker is a one-time transaction: it returns a temporary file and cannot back a persistent library that unifies documents and images with recents, favorites and search, nor re-open a previously viewed image. DocuSmart already uses the system picker in its one-off flows (Convert, Image to PDF, QR, PDF tools), but the Library and the Viewer require persistent access to the user's images.

## 4. Lo que NO conviene afirmar

- Que el selector del sistema "no funciona" en la app: ya se usa en varios flujos y Google puede verlo.
- Que la app necesita "todas" las fotos: solo lista hasta 50 y solo abre la que el usuario elige.
- Que el permiso es necesario para convertir una imagen a PDF: ese flujo funciona con el selector.
- Que se pedirá video o audio: ya no se declaran.

## 5. Preparar antes de enviar

1. Video corto (30–60 s) por si el formulario pide un enlace: Biblioteca → Dispositivo → elegir foto → Convertir a PDF → abrir en Visor → Recientes.
2. Confirmar que la política de privacidad publicada diga que las imágenes no salen del dispositivo.
3. Verificar que la ficha de Seguridad de los datos (Data safety) sea consistente con este texto.
4. Si Play rechaza la declaración: plan B en el PR #147 (quitar el permiso) más botón "Agregar fotos" con `OpenMultipleDocuments`.

## 6. Riesgo y límites

- Aprobación **no garantizada**: la política admite acceso amplio como función *principal* (galerías, editores). El eje de DocuSmart son los documentos.
- Si se aprueba, no hay que tocar el manifest. Si se rechaza, la app debe cumplir antes de publicar a producción.
- Esta declaración solo cubre `READ_MEDIA_IMAGES`; es independiente del aviso de 16 KB (PR #146).

## 7. Texto para el campo de Play Console (límite 250 caracteres)

El campo «Describe el uso que hace tu aplicación del permiso READ_MEDIA_IMAGES» admite **250 caracteres**. El texto anterior («…Solo se accede a las imágenes que el usuario selecciona explícitamente mediante el selector del sistema») se contradecía con la necesidad del permiso: si el acceso es solo por el selector, el permiso no hace falta.

**Opción A (220 caracteres, recomendada):**
> La Biblioteca muestra las imágenes recientes del dispositivo junto a los documentos, para que el usuario elija cuál convertir a PDF, ver, compartir o marcar favorita. Todo es local: la app no sube ni extrae las imágenes.

**Opción B (223 caracteres):**
> Gestor de documentos local: la Biblioteca lista las imágenes recientes del dispositivo junto a los documentos y las reabre desde Recientes, para que el usuario las convierta a PDF, vea o comparta. No se suben ni se extraen.

> Hallazgo posterior (2026-10-14): la documentación oficial del selector de fotos permite `takePersistableUriPermission` (hasta 5 000 concesiones). Eso debilita el argumento «el selector no basta» y es la razón de fondo para pasar al selector en la 1.1.5.
