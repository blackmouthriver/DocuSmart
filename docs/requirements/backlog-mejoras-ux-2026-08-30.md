# Backlog de mejoras y UX — cola de revisión (2026-08-30)

**Estado: catalogado, sin ejecutar.** Este documento cataloga prioridad,
dificultad y riesgo de romper lo ya construido para cada ítem pedido por
el usuario el 2026-08-30, más los hallazgos propios de una revisión UX/UI
heurística sobre el código y las capturas reales tomadas en dispositivo
durante esta sesión. **Ningún ítem de aquí se implementó** — es la cola de
priorización para decidir qué se aborda y en qué orden.

## 1. Cómo leer este documento

- **Prioridad**: Alta / Media / Baja — impacto en el usuario si no se hace.
- **Dificultad**: Alta / Media / Baja — esfuerzo de implementación.
- **Riesgo**: Alto / Medio / Bajo — probabilidad de romper algo que ya
  funciona (pantallas/flujos que toca, cuántos archivos, si hay lógica de
  negocio de por medio o es solo UI).
- **Tipo**: `Bug` (algo no se comporta como debería) o `Mejora` (HU nueva,
  con Como/quiero/para + criterios de aceptación).

## 2. Tabla resumen — todo el backlog vigente (nuevo + ya existente)

| # | Ítem | Tipo | Prioridad | Dificultad | Riesgo | Origen |
|---|---|---|---|---|---|---|
| 1 | Acceso directo a Convertir/QR desde el menú "⋮" de un archivo (Biblioteca/Recientes/Visores) | Mejora | Alta | Media | Bajo-Medio | **✅ Implementado y verificado en dispositivo 2026-08-31** — ver §3 |
| 2 | Capturar archivo desde cámara para convertir | Mejora | Media | Media | Bajo | **✅ Implementado y verificado en dispositivo 2026-08-31** — ver §4 |
| 3 | Accesos rápidos: carrusel → grilla + "Img→PDF" pre-filtrado | Mejora | Media | Baja | Bajo | **✅ Implementado y verificado 2026-08-30** — ver §5 |
| 4 | Botón Papelera sin título y de tamaño inconsistente en Biblioteca | Bug | Media | Baja | Bajo | **✅ Corregido 2026-08-30** — ver §6 |
| 5 | Ajustes → ampliar "Personalización" (tamaño de letra, colores por elemento) | Mejora (épica) | Media | Alta | Medio-Alto | **🟡 HU-UX-05 (tamaño de letra) ✅ implementada 2026-08-31; HU-UX-06 acotada a propósito 2026-09-04 a un bug real (banner de Home ignoraba el acento elegido) — épica completa de colores por zona descartada por el usuario** — ver §7 |
| 6 | Banner de anuncios inconsistente entre pantallas | Mejora | Media | Media | Bajo | ✅ 6 de 6 pantallas implementadas y verificadas 2026-08-31 — ver §8 |
| 7 | Banner azul: ancho/alto uniforme + flecha "Volver" con texto | Mejora | Alta | Media-Alta | Medio | **✅ Implementado y verificado 2026-08-30** — ver §9 |
| 8 | Imagen dentro del título "Estudio" en Pomodoro | Bug | Baja | Baja | Bajo | **✅ Reproducido y corregido en dispositivo real 2026-09-03** — ver §10 |
| 9 | Visores (PDF/Word/Excel/Texto/PPT): Convertir/QR desde el visor | Mejora | Media-Alta | Media | Bajo-Medio | **✅ Implementado y verificado 2026-08-31** — mismo mecanismo que #1 (ver §3, AC5) |
| 10 | Compose UI Testing en toda la app | Mejora (épica) | Mixta por flujo | Mixta por flujo | Bajo | Ya catalogado en [`compose-ui-testing.md`](compose-ui-testing.md) — no duplicar, ver §11 |
| 11 | Auditoría UX/UI experta + plan de mejoras | Entregable | — | — | — | Nuevo — ver §12 (findings + HUs propias) |
| 12 | Hallazgos de seguridad diferidos de SonarCloud (external storage x5, biometric CryptoObject, dependency verification) | Bug/Deuda técnica | Media | Media-Alta | Medio | Ya listado en `deployment.md` §7 |
| 13 | Umbral de cobertura `new_coverage` 0% en SonarCloud | Decisión de config | — | — | — | Ya listado en `deployment.md` §7 y `compose-ui-testing.md` §4 |
| 14 | i18n: agregar ja/ko/zh/it/fr | Mejora | Baja | Media | Bajo | **✅ Implementado y verificado 2026-09-04** — ver §21 |
| 15 | Selector de archivo desde biblioteca de la app (no solo dispositivo) en Seguridad/PDF Tools | Mejora | Baja | Media | Bajo | **✅ Implementado y verificado en dispositivo real 2026-09-03** — ver §15 |
| 16 | Encriptar/quitar contraseña de archivo individual en Seguridad | Mejora | Baja | Media | Bajo | Ya listado en `CONTEXT.md` §5 |
| 17 | Tarjetas de favoritos con tamaños inconsistentes | Bug (visual) | Baja | Baja | Bajo | Ya listado en `CONTEXT.md` §5 |
| 18 | Word/Excel/PowerPoint en el Visor con inconvenientes | Bug | Media | Media-Alta | Medio | **✅ Reescrito con Apache POI y verificado en dispositivo real 2026-09-03** (incluye fix del bug de espaciado del conversor PDF→Word encontrado en el camino) — ver §18/§19 |
| 19 | Actualizar splash (marca empresa + marca app) e íconos (lanzador + banner azul) con el nuevo diseño | Mejora | Alta (marca/identidad) | Media | Bajo-Medio | **✅ Implementado y verificado en dispositivo 2026-08-30** — ver §13 |
| 20 | H1: texto "Eliminar del historial" engañoso (en realidad mueve a la papelera real) | Bug | Media | Baja | Bajo | **✅ Corregido 2026-08-30** — ver §12, hallazgo H1 |
| 21 | `DocuSmartDocumentItem.kt` (menú "⋮" de Home/Biblioteca) sin i18n — todos los labels hardcodeados en español | Bug (i18n) | Media | Media | Bajo | **✅ Corregido y verificado 2026-09-03** — ver §12, hallazgo H6 |
| 22 | `DocumentRepository.loadPdfsFromDownloads()` no ve PDF/Word/Excel/PowerPoint reales de Descargas sin `owner_package_name` propio (scoped storage); Texto ni siquiera está en el filtro de mimeTypes de esa consulta | Bug | Media-Alta | Media | Medio | **🟡 Corregido lo corregible 2026-09-03 (Texto + permiso falso + API 29-32); la limitación de scoped storage en API 33+ es de la plataforma, sin fix de código posible** — ver §16 |
| 23 | Firebase Analytics/Crashlytics ya declarados a Play Store pero nunca funcionaron (plugin de Gradle sin aplicar, 15 eventos sin conectar, sin árbol de Timber en release) | Bug | Alta | Media | Medio | **✅ Corregido y verificado en dispositivo real (build release firmado) 2026-09-03** — ver §20 |

Los ítems 12-18 **ya estaban catalogados** en sesiones anteriores; se
listan acá solo para tener una única cola de prioridades. Su detalle
completo sigue viviendo en sus documentos originales (enlazados).

---

## 3. Mejora — Acceso directo a Convertir/QR desde un archivo ya seleccionado

Cubre dos pedidos del usuario que comparten exactamente el mismo mecanismo
técnico: el menú "⋮" de un archivo en Biblioteca/Recientes, **y** el menú
de opciones dentro de los Visores (PDF/Word/Excel/Texto/PowerPoint),
ganan dos acciones nuevas: **"Crear QR"** y **"Convertir"**, ambas
saltando el paso de "buscar el archivo" porque ya se sabe cuál es.

**Investigado antes de estimar** (no asumido):
- `QrCreatorScreen` ya soporta adjuntar Imagen o Documento (`QrScreen.kt`
  líneas 769/815, `FilterChip` + `GetContent()`) — el código para
  "adjuntar un archivo a un QR" ya existe. Lo que falta es *saltarse el
  picker* cuando ya se viene con un archivo elegido. El QR resultante
  codifica la URI como texto (no el archivo en sí — un QR no puede
  contener un PDF completo), así que la funcionalidad real es "generar un
  QR que apunte a este archivo", no "empaquetar el archivo en el QR".
  Password ya soportado (`QrScreen.kt`, flujo ya visto en sesiones
  previas).
- `NavRoutes.QrCreator` y `NavRoutes.Converter` **no aceptan parámetros
  hoy** (`NavRoutes.kt:10,26`) — ninguna pantalla puede pre-cargar un
  archivo en ninguna de las dos.
- `ConverterViewModel`/`ConverterScreen` ya tienen la lógica de "formato
  origen → lista de formatos destino válidos" (es como funciona hoy la
  selección manual) — reutilizable tal cual si se le pasa el formato
  origen ya resuelto.

**Riesgo de romper lo construido:** bajo — es una ruta de navegación
*nueva* con parámetros opcionales; las rutas existentes sin parámetros
siguen funcionando igual (parámetro con valor por defecto `null`). El
riesgo real está en el Visor: agregar ítems a un menú ya existente sin
tocar los que ya funcionan (renombrar/eliminar, RF-VIS-06).

### HU-UX-01 — Crear QR desde un archivo ya seleccionado

**Como** usuario que tiene un archivo abierto o seleccionado,
**quiero** generar un QR de ese archivo sin tener que volver a buscarlo,
**para** ahorrar pasos cuando ya sé exactamente qué archivo quiero
compartir por QR.

- **AC1** Dado que toco "⋮" sobre un archivo en Biblioteca o Recientes,
  cuando veo el menú, entonces aparece la opción "Crear QR" (nueva, junto
  a las existentes).
- **AC2** Dado que toco "Crear QR" desde ese menú, cuando se abre la
  pantalla de creación de QR, entonces llega directo al paso de
  "contenido" con el archivo ya adjunto (tipo Imagen o Documento según
  corresponda) — sin mostrar el selector de archivos.
- **AC3** Dado que estoy en ese flujo pre-cargado, cuando elijo protegerlo
  con contraseña, entonces funciona igual que el flujo manual existente
  (sin regresión).
- **AC4** El flujo manual de Crear QR (sin venir de un archivo) sigue
  funcionando exactamente igual que hoy.
- **AC5** Mismo comportamiento desde el menú de opciones de cualquier
  Visor (PDF/Word/Excel/Texto/PowerPoint) con el documento actualmente
  abierto.

### HU-UX-02 — Convertir un archivo ya seleccionado

**Como** usuario que tiene un archivo abierto o seleccionado,
**quiero** enviarlo directo a Convertir sin volver a elegirlo,
**para** ahorrar pasos cuando ya sé qué archivo quiero convertir.

- **AC1** Dado que toco "⋮" sobre un archivo en Biblioteca/Recientes (o el
  menú de un Visor), cuando veo las opciones, entonces aparece "Convertir".
- **AC2** Dado que toco "Convertir" ahí, cuando se abre la pantalla de
  Convertir, entonces el archivo ya está cargado y el formato de origen ya
  está fijado según el tipo real del archivo (PDF/Word/Excel/Imagen/etc.)
  — no hay que volver a seleccionarlo.
- **AC3** La lista de formatos de destino ofrecidos es exactamente la que
  ya existe hoy para ese formato de origen (reutiliza la lógica actual,
  no se inventa una nueva matriz de conversión).
- **AC4** El flujo manual de Convertir (eligiendo origen y archivo a mano)
  sigue funcionando exactamente igual que hoy.

*(Nota de alcance: no incluye construir ninguna conversión nueva — solo
el atajo de navegación + pre-carga sobre las conversiones que ya existen.)*

### Implementado y verificado en dispositivo real (2026-08-31)

- **`DocuSmartDocumentItem.kt`**: el menú "⋮" (`DocumentContextMenu`) ganó
  la opción **"Crear QR"** junto a "Convertir" (ya existía pero no estaba
  conectada en ningún lado — ver más abajo). Ambas son opcionales
  (`onConvertClick`/`onCreateQrClick` nulos por defecto), así que no
  afectan a ningún otro lugar que use este componente sin pasarlas.
- **`NavRoutes.Converter`** ganó `initialFileUri`/`initialFileCategory`
  (además del `initialType` que ya tenía desde el acceso rápido
  "Img→PDF"). **`NavRoutes.QrCreator`** ganó
  `initialFileUri`/`initialFileType`/`initialFileName` (antes no aceptaba
  ningún parámetro).
- **`ConverterViewModel`**: como un mismo formato de origen (p.ej. PDF)
  tiene varios destinos posibles (PDF→Imagen/TXT/Word/HTML), no se puede
  saltar directo a un `ConversionType` como hace "Img→PDF". Se agregó
  `preloadFile(uri, category)` que deja el archivo en espera; en cuanto el
  usuario toca cualquier tipo de conversión de esa misma categoría
  (`onTypeSelected`), el archivo se adjunta automáticamente una sola vez
  (consumo único) — así se ve el listado completo de las 5 categorías
  igual que siempre (AC3), pero sin tener que volver a buscar el archivo
  al elegir el destino (AC2).
- **`QrCreatorScreen`**: mucho más simple que Convertir porque todo su
  estado ya era local (`remember`), sin interdependencias — un
  `LaunchedEffect(initialFileUri)` preselecciona el chip Imagen/Documento
  y adjunta el archivo directo, saltando el picker.
- **`DocuSmartNavGraph.kt`**: se agregaron `DocumentType.toConverterCategoryOrNull()`
  (mapea el tipo real del archivo a la categoría del Convertidor; Texto y
  ZIP devuelven `null` porque no tienen ninguna conversión definida hoy —
  en ese caso se navega igual pero sin precarga, cae al flujo manual sin
  romper nada) y `DocumentType.toQrFileType()` ("image"/"document").
- **Biblioteca** (`LibraryScreen.kt` → `DocumentListSection.kt`) y
  **Recientes** (`HomeScreen.kt` → `RecentDocuments.kt`) ganaron los
  callbacks `onConvertClick`/`onCreateQrClick` (antes "Convertir" en
  Recientes existía pero ignoraba el documento y mandaba siempre al CTA
  genérico; en Biblioteca no existía en absoluto ninguna de las dos
  opciones).
- **Visor** (AC5, ítem #9): `ViewerTopBar.kt` ganó dos `DropdownMenuItem`
  nuevos ("Convertir"/"Crear QR", con sus propios strings localizados en
  5 idiomas) antes de Renombrar/Eliminar en el menú "⋮" ya existente
  (RF-VIS-06) — riesgo bajo porque solo se agregan ítems, no se toca
  ningún callback existente.
- Detekt: agregar los nuevos parámetros a `ConverterScreen`,
  `QrCreatorScreen` y `ViewerScreen` invalidó 3 entradas de
  `LongMethod` en el baseline (mismo patrón de la sesión anterior) — se
  regeneraron solo esas 3 líneas a mano, sin tocar las demás.
- Gauntlet completo verificado: `compileDebugKotlin`, `detekt`,
  `lintDebug`, `testDebugUnitTest` y `connectedDebugAndroidTest` (6/6) en
  verde. Verificado en dispositivo real (Motorola Edge 30 Neo): "Crear
  QR" desde Biblioteca llega con la imagen ya adjunta sin mostrar el
  selector (se generó el QR con éxito); "Convertir" desde Biblioteca
  muestra las 5 categorías completas y, al elegir "Imagen → PDF", el
  archivo ya está adjunto (se completó la conversión con éxito); desde el
  Visor, ambas opciones del menú "⋮" navegan con el documento
  actualmente abierto precargado. Sin `FATAL EXCEPTION` en logcat en
  ningún punto de la verificación.

---

## 4. Mejora — Capturar archivo desde cámara para convertir

**✅ Implementado y verificado en dispositivo real 2026-08-31**

**Investigado:** no existe ningún mecanismo de cámara propio (CameraX/
Camera2) en el proyecto. El único punto de captura de imagen es el
escáner de documentos de Google ML Kit (`GmsDocumentScannerOptions`,
`ScannerScreen.kt`), que abre su propia UI gestionada por Play Services.
**Reutilizar esa misma API es el camino de menor esfuerzo y menor riesgo**
— evita escribir/mantener una cámara propia desde cero.

**Riesgo:** bajo — es una fuente de entrada *adicional* en un flujo que ya
acepta archivos por otras vías (picker); no reemplaza nada existente.

### HU-UX-03 — Convertir un documento capturado con la cámara

**Como** usuario que tiene un documento físico en papel,
**quiero** capturarlo con la cámara y convertirlo directamente,
**para** no depender de tener el archivo ya guardado en el dispositivo.

- **AC1** Dado que estoy en Convertir eligiendo el archivo de origen,
  cuando abro el selector, entonces veo una opción nueva "Capturar con
  cámara" junto a la opción de elegir del dispositivo.
- **AC2** Dado que elijo "Capturar con cámara", cuando completo la
  captura (reutilizando el flujo de ML Kit Document Scanner ya probado en
  Escáner), entonces el resultado queda cargado como archivo de origen
  para convertir, con el mayor número de formatos de destino que la
  captura lo permita (como mínimo: imagen y PDF, los dos formatos que ya
  produce el escáner hoy).
- **AC3** Cancelar la captura vuelve al selector de origen sin cambiar
  nada, sin crashear ni dejar estado a medias.
- **AC4** El resto de las fuentes de origen (picker de archivos) siguen
  funcionando igual que hoy.

### Implementado y verificado en dispositivo real (2026-08-31)

- **`launchDocumentScanner()`** (nuevo `DocumentScannerLauncher.kt`,
  paquete `scanner.presentation`): se extrajo la configuración de
  `GmsDocumentScannerOptions` que antes vivía como función privada dentro
  de `ScannerScreen.kt`, para reutilizarla tal cual desde Convertir sin
  duplicarla. `ScannerScreen.kt` ahora llama a esta misma función
  compartida (sin cambio de comportamiento).
- **`ConverterScreen.kt`**: el botón "Capturar con cámara" aparece junto
  al selector de archivo, pero **solo cuando el origen es Imagen** (`type.
  fromFormat == "Imagen"`) y **solo antes de tener ya un archivo elegido**
  — la cámara de ML Kit siempre devuelve páginas como imagen (nunca un
  PDF directo, ver comentario ya existente en `ScannerScreen.kt`), así
  que ofrecerlo para PDF/Word/Excel/PowerPoint no tendría sentido.
- **`ConverterViewModel.onScanError()`**: nuevo, reutiliza el mismo
  mecanismo de Snackbar que ya tenían los demás errores de esta pantalla.
- Cancelar la captura (botón "X" del escáner) no dispara ningún callback
  — el selector de origen queda exactamente como estaba (AC3).
- Verificado en dispositivo real (Motorola Edge 30 Neo): el botón
  aparece en Imagen→PDF y NO aparece en PDF→TXT (ni en ninguna otra
  categoría); "Capturar con cámara" abre correctamente la UI de ML Kit
  ("Posiciona el documento en el marco"); cancelar vuelve sin cambios;
  completar una captura real deja la foto adjunta automáticamente como
  archivo de origen (sin volver a mostrar el selector); la conversión
  Imagen→PDF con el archivo capturado se completó con éxito de punta a
  punta. Sin `FATAL EXCEPTION` en logcat.
- Gauntlet completo verificado: `compileDebugKotlin`, `detekt`,
  `lintDebug`, `testDebugUnitTest` y `connectedDebugAndroidTest` (6/6) en
  verde.

---

## 5. Mejora/Bug — Accesos rápidos: carrusel → grilla + renombrar "Img→PDF"

**✅ Implementado y verificado en dispositivo real 2026-08-30, AC1-AC3
completos y AC4 resuelto con la opción (c)** (recomendada, ver abajo, en
vez de un renombrado directo). `QuickAccessGrid.kt` pasó de `LazyRow` a
una grilla fija de 3 columnas (`items.chunked(3)` + `Row`s con
`Modifier.weight(1f)` por tarjeta, sin `LazyVerticalGrid` para evitar
problemas de scroll anidado dentro del `Column` de Home) — los 9 accesos
se ven de un vistazo, sin deslizar. Los 9 `onClick` no cambiaron.

**AC4 — resuelto sin renombrar, diferenciando de verdad el destino:** en
vez de renombrar "Img→PDF" a "Convertir" (que hubiera dejado dos botones
"Convertir" idénticos en Home), el acceso rápido ahora navega al
Convertidor **ya preseleccionado en Imagen → PDF** (salta la pantalla de
elegir categoría/formato), mientras el CTA grande "Convertir" sigue
siendo el genérico. Con esto el label "Img→PDF" pasa a describir
exactamente lo que hace — no hizo falta cambiarlo.

- `NavRoutes.Converter` ganó un parámetro opcional de ruta
  (`"converter?initialType={initialType}"`, mismo patrón ya usado por
  `NavRoutes.Study`) con `createRoute(initialType: String? = null)`.
- `ConverterScreen` acepta `initialType: String?` y preselecciona el
  `ConversionType` correspondiente vía `LaunchedEffect(initialType)` —
  solo si el usuario no eligió ya un tipo manualmente (no pisa una
  selección en curso).
- `HomeScreen` gana `onQuickConvertImageToPdf` (por defecto = `onConvert`,
  no rompe si algo más construye `HomeScreen` sin pasarlo) — solo el
  acceso rápido lo usa; el CTA grande y el menú "⋮" de un archivo siguen
  en `onConvert` sin cambios.
- Efecto colateral corregido: al agregar el parámetro opcional a la ruta,
  `DocuSmartBottomBar` navegaba con la plantilla sin resolver
  (`"converter?initialType={initialType}"` literal) al tocar la pestaña
  "Convertir" de la barra inferior — se agregó `navigateRoute` separado de
  `route` (que sigue siendo la plantilla, usada para detectar la pestaña
  activa) para que la pestaña siga navegando a la ruta genérica resuelta.
- De paso, `ConverterScreen` había quedado justo en el límite de
  `LongMethod` de detekt al agregar la línea del `LaunchedEffect` — se
  aprovechó para eliminar una duplicación real: las 5 secciones por
  categoría (Imagen/PDF/Word/Excel/PowerPoint) eran el mismo código
  repetido 5 veces con solo título/ícono/color distintos, ahora es una
  lista `CONVERSION_CATEGORIES` recorrida en un `forEach`.

**Verificado en dispositivo real:** acceso rápido "Img→PDF" abre directo
en "Imagen → PDF" sin la pantalla de selección; el CTA grande "Convertir"
sigue mostrando la selección manual completa sin preselección; la pestaña
"Convertir" de la barra inferior sigue resaltándose y navegando
correctamente. Sin crashes.

**Investigado:** `QuickAccessGrid.kt` (pese al nombre) es un `LazyRow`
(línea 134) con 9 ítems: Escanear, Img→PDF, Seguridad, Lectura, Notas,
Pomodoro, Leer QR, Crear QR, Papelera. Confirmado que "Img→PDF"
(`onImageToPdfClick = onConvert`, `HomeScreen.kt:96`) navega **exactamente
al mismo lugar** que el botón grande "Convertir" de Home — el label
"Img→PDF" es una etiqueta vieja que ya no describe lo que hace (el
Convertidor soporta muchos más pares de formatos hoy).

**Riesgo:** bajo — es un cambio de layout (LazyRow → grilla) y de texto,
sin tocar lógica de negocio ni los `onClick` existentes.

### HU-UX-04 — Accesos rápidos en grilla ordenada

**Como** usuario que quiere llegar rápido a una función,
**quiero** ver los accesos rápidos en una grilla fija y ordenada,
**para** identificarlos todos de un vistazo sin tener que deslizar.

- **AC1** Los 9 accesos rápidos actuales se muestran en una grilla (p.ej.
  3 columnas) sin scroll horizontal — todos visibles sin deslizar (puede
  requerir scroll vertical de la pantalla completa, no del carrusel en sí).
- **AC2** Todas las tarjetas tienen el mismo tamaño entre sí (ancho y alto
  uniformes), a diferencia del carrusel actual.
- **AC3** Los 9 `onClick` existentes no cambian de destino — solo cambia
  el layout visual.
- **AC4** El acceso hoy llamado "Img→PDF" pasa a llamarse "Convertir".

**Decisión que necesito del usuario antes de implementar (no es mía para
decidir sola):** hoy "Convertir" (el CTA grande) y "Img→PDF" ya van al
mismo lugar — al renombrar el segundo, Home tendría **dos botones
llamados "Convertir"** yendo al mismo sitio. ¿Prefieres (a) igual
renombrarlo así porque no molesta tener dos accesos al mismo destino, (b)
quitar el acceso rápido duplicado y usar ese espacio para otra función, o
(c) diferenciarlo de verdad — que el acceso rápido abra Convertir
pre-filtrado en Imagen→PDF específicamente, ahora que sí tiene sentido su
nombre?

---

## 6. Bug — Botón de Papelera sin título y de tamaño inconsistente

**✅ Corregido y verificado en dispositivo 2026-08-30.** `LibraryTrashButton`
(ancho fijo `56.dp`, sin label) se eliminó por completo — la Papelera
ahora reutiliza el mismo `LibraryTabItem` que "Dispositivo"/"Mis
archivos" (ícono + label "Papelera" + contador, `Modifier.weight(1f)`),
así que los 3 son literalmente el mismo componente, no una réplica visual
aproximada.

**Regresión encontrada y corregida en la misma verificación:** al pasar
"Dispositivo"/"Mis archivos"/"Papelera" a igual ancho (antes 2 pestañas
se repartían el espacio entre solo 2, ahora entre 3), el label
"Dispositivo" se envolvía a media palabra ("Disposi" / "vo"). Se agregó
`maxLines = 1` + `overflow = TextOverflow.Ellipsis` a ambos `Text` de
`LibraryTabItem` (label y contador) — ahora trunca con "…" en vez de
partirse.

---

## 7. Mejora (épica) — Ampliar "Personalización" en Ajustes

**✅ HU-UX-05 implementada y verificada en dispositivo real 2026-08-31**
(HU-UX-06 sigue pendiente de diseño, sin empezar — ver más abajo).

**Investigado:** la sección "Personalización" **ya existe** en
`SettingsScreen.kt:535` con 4 ítems (Idioma, Tutorial, Tema, Color de
acento) — no hay que crearla, hay que **ampliarla**. Lo pedido (tamaño de
letra para baja visión, colores de banner/íconos/cards/nav bar, estilo y
color de letra) es una visión de personalización mucho más amplia que
tocaría el sistema de theming de toda la app (`DocuSmartTheme`,
`ThemeManager`, `AccentColor`).

**Por qué la dificultad es Alta y el riesgo Medio-Alto:** cambiar el
tamaño de fuente global significa tocar la escala tipográfica
(`MaterialTheme.typography`) que usan literalmente todas las pantallas —
un cambio mal probado ahí puede romper layouts en cascada (textos que se
cortan, botones que crecen de más). Personalizar colores por elemento
(banner/ícono/card/nav bar de forma independiente, no solo un "acento"
global como hoy) es rediseñar el sistema de color actual, no agregar una
opción más.

**Recomendación de secuencia (no todo junto):**
1. Primero, tamaño de letra (accesibilidad real, alto valor, impacto
   acotado si se implementa como un multiplicador de escala sobre la
   tipografía existente).
2. Después, evaluar personalización de color por elemento como iniciativa
   aparte, más grande.

### HU-UX-05 — Tamaño de letra ajustable (accesibilidad)

**Como** usuario con dificultad visual,
**quiero** aumentar el tamaño del texto de la app,
**para** poder leer cómodamente sin depender de la configuración de todo
el sistema operativo.

- **AC1** Dado que entro a Ajustes → Personalización, cuando busco esta
  opción, entonces encuentro un ítem "Tamaño de letra" con al menos 3
  niveles (p.ej. Normal/Grande/Muy grande).
- **AC2** Dado que elijo un nivel distinto, cuando vuelvo a cualquier
  pantalla de la app, entonces el texto se ve escalado de forma
  consistente, sin recortarse ni desbordar sus contenedores en las
  pantallas ya auditadas (mínimo: Home, Biblioteca, Ajustes, Visor).
- **AC3** El valor elegido persiste al cerrar y reabrir la app (mismo
  patrón que `ThemeManager`/`LanguageManager`).
- **AC4** "Restablecer configuración" también restablece el tamaño de
  letra a Normal.

### Implementado y verificado en dispositivo real (2026-08-31)

- **`ThemeManager`**: nuevo `enum FontScale(label, scale)` con 3 niveles
  (NORMAL=1.0, LARGE=1.15, EXTRA_LARGE=1.3) + `StateFlow<FontScale>` +
  persistencia en `SharedPreferences`, mismo patrón exacto que
  `AppTheme`/`AccentColor` ya existentes.
- **`Type.kt`**: nueva función `Typography.scaledBy(factor)` que escala
  `fontSize`/`lineHeight` de los 15 estilos de `Typography` (no solo los
  12 que `DocuSmartTypography` define explícito -- `displayMedium`/
  `displaySmall`/`headlineSmall` caen al default de Material3 pero
  igual se usan en `ScannerScreen`/`StudyScreen`/`SecurityScreen`/
  `SplitPdfScreen`/`HomeBanner`, verificado antes de asumir que bastaba
  con escalar los 12).
- **`DocuSmartTheme`** ganó un parámetro `fontScale: Float = 1f`
  aplicado como `DocuSmartTypography.scaledBy(fontScale)` en el
  `MaterialTheme` de toda la app; `MainActivity.kt` lo alimenta desde
  `themeManager.fontScale`.
- **`SettingsScreen.kt`**: nuevo ítem "Tamaño de letra" en Personalización
  (después de Color de acento) con su diálogo de selección (mismo patrón
  de `RadioButton` que Tema/Color de acento) y su reset en "Restablecer
  configuración" (con el texto del diálogo actualizado en los 5 idiomas
  para mencionarlo).
- **Hallazgo real durante la verificación (no asumido, encontrado
  probando en dispositivo):** las 3 pestañas de Biblioteca (Dispositivo/
  Mis archivos/Papelera, `LibraryTabItem` en `LibraryScreen.kt`) truncaban
  con "…" desde el nivel "Grande" (1.15x) -- el ancho de 3 columnas
  compartido entre ícono y texto en una `Row` no alcanzaba. Permitir 2
  líneas no fue suficiente por sí solo ("Dispositivo" se partía a media
  palabra, "Mis archivos" seguía truncado) -- se resolvió cambiando el
  layout de esas 3 tarjetas de "ícono al lado del texto" a "ícono arriba,
  texto centrado abajo" (mismo patrón que una barra de navegación
  inferior), que le da al texto todo el ancho de la tarjeta. Decisión
  confirmada con el usuario antes de tocar el diseño visual de un
  componente ya afinado en una iteración previa de esta sesión.
- Verificado en dispositivo real (Motorola Edge 30 Neo) en las 4
  pantallas auditadas por el AC2, en los 3 niveles:
  - **Ajustes**: sin cortes, "Tamaño de letra: Normal" visible y
    seleccionable.
  - **Home**: banner, accesos rápidos y textos escalan sin desbordar.
  - **Biblioteca**: tras el fix de layout, "Dispositivo"/"Mis archivos"/
    "Papelera" se ven completos (sin "…") incluso en "Muy grande".
  - **Visor**: sin cortes (el nombre de archivo en la barra superior ya
    se truncaba antes de esta HU para nombres largos -- comportamiento
    preexistente no relacionado con el escalado).
  - **AC3** confirmado: el nivel elegido persiste a través de múltiples
    reinstalaciones/reinicios de la app durante la verificación.
  - **AC4** confirmado: "Restablecer configuración" vuelve el tamaño de
    letra a "Normal" (y el resto de la UI, incluida la barra de
    navegación inferior, vuelve a verse sin envolver).
- **Trade-off aceptado, documentado, no corregido:** en el nivel más
  extremo ("Muy grande"), la barra de navegación inferior (`Navigation
  BarItem` nativo de Material3) envuelve etiquetas largas como
  "Biblioteca"/"Convertir" a 2 líneas partiendo la palabra, y el badge de
  tipo de documento (`DocuSmartDocumentItem.kt`, usado en Home/Biblioteca/
  Papelera/Favoritos) hace lo mismo con "Imagen"/"Imag-en". Ninguno de los
  dos trunca información (a diferencia del caso de Biblioteca que sí se
  corrigió) -- se acepta como límite conocido de esta HU en vez de tocar
  componentes compartidos por más pantallas, de mayor alcance.
- Gauntlet completo verificado: `compileDebugKotlin`, `detekt`,
  `lintDebug`, `testDebugUnitTest` en verde. `connectedDebugAndroidTest`
  reveló 2 fallas preexistentes y no relacionadas (confirmado con
  `git stash`/`git stash pop` contra `main` limpio) --
  `ingresarPinIncorrecto_muestraMensajeDeError`
  (`SecurityScreenTest`) y `convertirImagenAWebp_muestraResultadoExitoso`
  (`ConverterScreenTest`), ambas con timeout de exactamente 20000ms --
  flagueadas por separado para investigación, no forman parte del alcance
  de esta HU.

### HU-UX-06 — Personalización de color por elemento (banner, íconos, cards, barra de navegación)

*(Épica — no se detallan ACs completos hasta que se decida abordarla;
requiere antes una decisión de diseño de qué elementos son
personalizables y con qué paleta, para no terminar con combinaciones que
rompan el contraste/legibilidad.)*

**Como** usuario que quiere una app más "suya",
**quiero** elegir colores para banner, íconos, cards y barra de
navegación por separado (no solo un acento único como hoy),
**para** tener más control visual sobre la apariencia de la app.

- Pendiente de diseño: catálogo cerrado de combinaciones válidas (para
  evitar que el usuario arme una combinación ilegible) vs. selector de
  color libre (más riesgo de accesibilidad, requeriría validar contraste
  automáticamente).

### Retomado 2026-09-04: bug real encontrado, alcance reducido a propósito

Antes de diseñar la épica completa, se investigó el sistema de theming
actual para fundamentar la pregunta de diseño pendiente. Hallazgo real:
`HomeBanner.kt` tenía su degradado de color **fijo en tonos de azul**
(`DocuBlue`/`SmartBlue`/`IndigoAccent`) y el botón "Abrir" su texto
fijo en `DocuBlue` -- ambos **ignoraban por completo** el "Color de
acento" que el usuario ya puede elegir en Ajustes desde antes de esta
sesión. Era el único elemento de Home que no respetaba esa elección
(la barra de navegación inferior sí, vía los valores por defecto de
Material3 que ya leen `colorScheme.primary`; los íconos de Accesos
rápidos usan colores fijos por diseño, uno distinto por categoría, algo
intencional y no relacionado).

Presentado este hallazgo al usuario, con las 3 opciones de alcance
originales (arreglar solo el bug / colores independientes por zona /
selector libre), **eligió arreglar solo el bug** -- no la épica
completa de colores independientes por banner/íconos/cards/nav bar.

**Corregido**: `HomeBanner.kt` deriva su degradado y el color del texto
del botón "Abrir" de `MaterialTheme.colorScheme.primary` (ya resuelto
al acento + tema claro/oscuro correctos por `DocuSmartTheme`) en vez de
constantes fijas -- `listOf(lerp(primary, White, 0.12f), primary,
lerp(primary, Black, 0.22f))` reproduce el mismo efecto visual de
degradado de 3 tonos que tenía el diseño original, ahora anclado al
acento real elegido en vez de siempre azul.

**Verificado en dispositivo real (Motorola Edge 30 Neo)**: gauntlet
completo en verde. Con el acento en "Rosa", el banner de Home cambia a
un degradado rosa y el botón "Abrir" muestra su texto/ícono en rosa,
en vez de quedarse azul como antes del fix. Con el acento de vuelta en
"Azul" (el valor por defecto), el banner se ve visualmente idéntico al
diseño original -- sin regresión para quien nunca toca este ajuste.

**HU-UX-06 (colores independientes por banner/íconos/cards/nav bar)
sigue sin implementar** -- el usuario decidió no abordar esa épica más
grande por ahora; queda tal cual estaba documentada arriba si se
retoma más adelante.

### Ampliado 2026-09-04: el mismo bug estaba en más banners, no solo Home

Al ver el fix del banner de Home, el usuario pidió extenderlo a
**todos** los banners azules de la app, no solo ese. Se buscó
`DocuBlue`/`SmartBlue`/`IndigoAccent` en degradados y fondos de toda la
app (no solo donde ya se sabía que había código duplicado) y se
encontraron varios más con el mismo problema exacto:

- **`DocuSmartTopBanner.kt`** -- el componente **compartido por 9
  pantallas** (Ajustes, Seguridad, Herramientas PDF y sus
  sub-pantallas, etc.), el de mayor impacto de todos.
- **`PremiumBanner.kt`** -- el banner hero de la pantalla Premium.
- **`ScannerScreen.kt`** -- el fondo de pantalla completa mientras
  carga el escáner.
- **`SecurityScreen.kt`** (2 lugares) -- el fondo de pantalla completa
  del menú principal de Seguridad, y el de la pantalla "Configura tu
  PIN" (esta última también con su botón "Configurar PIN" en texto
  azul fijo, mismo patrón que el botón "Abrir" de Home).
- **`StudyScreen.kt`** -- el ícono decorativo del estado vacío de Modo
  Estudio, y las barras del gráfico "Pomodoros esta semana".

**Refactor**: se extrajo la lógica de degradado a un helper compartido
`rememberAccentGradient()` (nuevo, `core/ui/theme/AccentGradient.kt`)
para no duplicar el cálculo de `lerp` en cada archivo -- `HomeBanner.kt`
también se actualizó para usarlo, sin cambiar su comportamiento.

**Hallazgo adicional en el camino**: `DocuSmartCards.kt` tenía una
función `DocuSmartGradientCard` con el mismo bug, comentada como "banner
principal del Home" -- pero resultó ser **código muerto**, sin ningún
call site en toda la app (`HomeBanner.kt` ya tiene su propia
implementación desde antes, esta función quedó huérfana). Eliminada en
vez de corregida.

**Dejado a propósito sin tocar** (no son banners decorativos, son
colores con significado semántico o de marca fija):
- **`StudyScreen.kt`**: el indicador de "leyendo en voz alta" (TTS
  activo) y el reloj/badge de Pomodoro (azul = estudio, verde =
  descanso) usan `DocuBlue` para codificar un ESTADO, no como color de
  marca decorativo -- cambiarlos rompería esa asociación de color
  aprendida por el usuario entre pantallas.
- **`SplashDocuSmartScreen.kt`** y **`OnboardingScreen.kt`**: pantallas
  de identidad de marca / primer contacto con la app, antes de que el
  usuario explore Ajustes -- se mantienen con la paleta azul original
  a propósito, mismo criterio que cualquier splash/onboarding de marca.
- Íconos con color fijo por categoría en Accesos rápidos
  (`QuickAccessGrid.kt`) y menús similares -- diseño intencional de
  variedad visual por tipo de función, ya confirmado fuera de alcance
  en la ronda anterior de esta misma HU.

**Verificado en dispositivo real (Motorola Edge 30 Neo)** con acento
"Verde": el banner de Ajustes (vía `DocuSmartTopBanner`), el menú
principal y la pantalla "Configura tu PIN" de Seguridad (fondo +
botón), y el banner hero de Premium cambiaron correctamente a verde.
El fondo de carga de Escáner comparte el mismo código exacto ya
verificado en Seguridad, no se forzó la cámara real del dispositivo
para no arriesgar quedar atascado en un flujo externo de ML Kit.
Devuelto el acento a "Azul" al terminar: todo se ve idéntico al diseño
original, sin regresión. Gauntlet completo en verde.

---

## 8. Mejora — Banner de anuncios consistente en todas las pantallas (no-premium)

**✅ Implementado y verificado en dispositivo real 2026-08-31**
(6 de 6 pantallas de AC1). Decisión del usuario confirmada: Contraseña
PDF, Carpeta Segura y Papelera quedan **sin** banner (recomendación
adoptada).

**Investigado:** `DocuSmartBannerAd` aparece hoy en 5 de 18 rutas: Home,
Biblioteca, Convertir, Herramientas PDF, Ajustes — todas con el mismo
criterio de gating (`if (!isPremium) { ... }`, patrón ya establecido y
correcto). **Faltaría en:** Escáner, Resultado de escaneo, Seguridad,
Contraseña PDF, Estudio, QR (menú/Lector/Creador), Carpeta Segura,
Papelera, Visor. Premium correctamente nunca debe tener banner (es la
pantalla de venta), y las 2 pantallas de Splash tampoco (demasiado
transitorias).

**Riesgo:** bajo técnicamente (patrón ya probado, se replica) — el riesgo
real es de producto/UX, no técnico (ver decisión abajo).

### HU-UX-07 — Banner de anuncios consistente para usuarios no-premium

**Como** usuario no-premium,
**quiero** ver la publicidad de forma consistente en todas las pantallas
de contenido,
**para** que el modelo de monetización sea predecible (y para que el
incentivo de pasar a Premium sea claro y parejo).

- **AC1** Cada pantalla de contenido principal (Estudio, Visor, Seguridad,
  Resultado de escaneo, QR Creador/Lector) muestra `DocuSmartBannerAd`
  para usuarios no-premium, con el mismo patrón de gating ya usado en las
  5 pantallas que ya lo tienen.
- **AC2** Un usuario Premium no ve el banner en ninguna pantalla (esto ya
  funciona hoy donde el banner existe — se verifica que se mantenga).
- **AC3** Premium y las 2 pantallas de Splash quedan explícitamente
  excluidas.

**✅ Decisión confirmada por el usuario:** Contraseña PDF, Carpeta Segura
y Papelera quedan sin banner (recomendación adoptada tal cual).

### Implementado y verificado en dispositivo real (2026-08-31)

- **Resultado de escaneo** (`ScanResultScreen.kt`): reutiliza
  `converterViewModel.adManager` (ya lo recibía como parámetro, sin
  necesidad de agregar nada nuevo) — riesgo mínimo. **No se pudo
  verificar visualmente** (requiere una captura real con cámara, mismo
  límite ya documentado en §9 para esta pantalla).
- **Menú de Seguridad** (`SecurityMenuScreen.kt`): no tenía ningún
  ViewModel — se creó `SecurityMenuViewModel` mínimo (solo expone
  `adManager`, sin lógica propia) inyectado vía `hiltViewModel()`.
  Verificado en dispositivo: banner visible arriba del banner azul.
- **Visor** (`ViewerScreen.kt`): `ViewerViewModel` ganó `adManager` en su
  constructor. El banner se agrupa en una `Column` junto con
  `ViewerBottomBar`, mostrándose/ocultándose junto con el resto de los
  controles (`uiState.showControls`) en vez de quedar fijo tapando el
  documento — el modo de lectura inmersiva del Visor ya oculta/muestra su
  propia barra de controles, así que el anuncio sigue esa misma
  convención en vez de romperla. Verificado en dispositivo: banner visible
  junto a los controles inferiores.
- Se actualizó `ViewerScreenTest.kt` (prueba instrumentada existente) para
  pasar el nuevo parámetro `adManager` mockeado — **hallazgo real durante
  la verificación**: un mock relajado de `AdManager` sin stub explícito de
  `isPremium`/`isInitialized` causa un `ClassCastException` al leer esos
  `StateFlow<Boolean>` desde Compose (mismo problema ya documentado antes
  en `ConverterScreenTest`). Se estabilizó con `isPremium = true` para que
  el test no dependa de que el anuncio realmente cargue.
- Las 6 pruebas instrumentadas (`connectedDebugAndroidTest`) confirmadas
  en verde tras el cambio.

### Completado — Estudio, Lector QR y Creador QR (2026-08-31)

**Estudio, Lector de QR y Creador de QR no tenían ningún ViewModel**
(todo su estado era `remember`/objetos locales) — se agregó uno mínimo a
cada uno (mismo patrón ya usado en Menú de Seguridad: solo expone
`adManager`, sin lógica propia):

- `StudyViewModel` — inyectado en `StudyScreen`. El banner se ubica
  después del `TabRow` y antes del contenido de cada pestaña, por lo que
  se ve igual en Lectura, Notas y Pomodoro (no solo en una). Verificado en
  dispositivo en las 3 pestañas.
- `QrViewModel` — compartido entre `QrReaderScreen` y `QrCreatorScreen`
  (cada pantalla recibe su propia instancia por scope de
  `NavBackStackEntry`).
  - **Creador de QR**: banner como primer elemento del formulario,
    arriba de los chips de tipo (URL/Texto/Email/...). Verificado en
    dispositivo.
  - **Lector de QR**: banner **solo** en el estado "código detectado"
    (cuando ya hay un resultado), nunca durante la vista de cámara en
    vivo — para no obstruir el escaneo. Verificado en dispositivo que la
    vista de cámara en vivo permanece sin banner; el estado "detectado"
    no se pudo verificar visualmente en este ciclo (requiere apuntar la
    cámara real a un QR físico, mismo límite ya documentado para
    Resultado de escaneo) pero sigue el mismo patrón exacto ya probado en
    Creador de QR y Estudio.
- **Escáner** (`ScannerScreen`) queda **excluido** del alcance, confirmado
  por el usuario: pantalla transitoria de carga (abre la cámara de ML Kit
  casi de inmediato), sin tiempo de pantalla real para un banner.
- Detekt: agregar el parámetro `viewModel` a las 3 funciones invalidó sus
  entradas de `LongMethod` en `config/detekt/baseline.xml` (ya estaban al
  límite antes de este cambio). Se regeneraron **solo esas 3 líneas**
  manualmente en vez de correr `detektBaseline` completo — ese task
  hubiera borrado ~70 supresiones preexistentes no relacionadas.
- Gauntlet completo verificado: `compileDebugKotlin`, `detekt`,
  `lintDebug`, `testDebugUnitTest`, `connectedDebugAndroidTest` (6/6) —
  todo en verde. Sin `FATAL EXCEPTION` en logcat en ninguna de las 3
  pantallas visitadas.

**HU-UX-07 completa: 6 de 6 pantallas de AC1 implementadas y
verificadas.**

---

## 9. Mejora — Banner azul uniforme (100% ancho, alto contenido) + "Volver" con texto

**✅ Implementado y verificado en dispositivo real 2026-08-30** (AC1, AC3,
AC4 completos; AC2 resuelto como efecto directo del mismo cambio, ver
abajo).

**Investigado:** `DocuSmartTopBanner` **no tiene** un parámetro de flecha
de "volver" — cada pantalla arma su propio `Row` con `IconButton` +
`Icon(ArrowBack)` al lado del banner (`TrashScreen.kt`,
`SecurityMenuScreen.kt`, `PdfPasswordScreen.kt`, `ScanResultScreen.kt`,
entre otras — al menos 6 pantallas con este patrón ad-hoc, ninguna con
texto "Volver"). Otras pantallas (Convertir, Ajustes) usan el banner
**sin** flecha porque son destinos de la barra inferior, no
sub-pantallas — eso está bien así, no debería cambiar.

Esto **reabre** una conclusión de `security.md`: en esta misma sesión se
documentó la ubicación del botón "volver" como "un patrón deliberado y
consistente entre pantallas, no un bug aislado" — el usuario ahora pide
explícitamente cambiar ese patrón. Ya no aplica "no tocar, es deliberado";
pasa a ser un rediseño consciente pedido por el usuario.

**Por qué el riesgo es Medio:** el arreglo correcto es agregar el
parámetro de "volver" **al componente compartido** `DocuSmartTopBanner`
(un solo lugar), pero después hay que **migrar cada pantalla** que hoy
arma su propio `Row` ad-hoc para que use el parámetro nuevo en vez de su
`IconButton` suelto — son al menos 6-8 archivos a tocar y verificar uno
por uno en dispositivo (no es un cambio "seguro" de hacer a ciegas con
buscar-y-reemplazar, cada pantalla puede tener detalles distintos
alrededor del banner).

### HU-UX-08 — Banner azul uniforme con "Volver" visible

**Como** usuario navegando por sub-pantallas de la app,
**quiero** que el banner azul se vea igual en todas partes y que el botón
de volver diga "Volver" (no solo un ícono),
**para** tener una navegación más clara y predecible.

- **✅ AC1** `DocuSmartTopBanner` ganó `onBack: (() -> Unit)? = null` — la
  flecha (`Icons.AutoMirrored.Rounded.ArrowBack`) + el texto "Volver"
  (string `general_back`, ya existente y traducido a los 5 idiomas) se
  muestran integrados arriba del contenido principal, en blanco sobre el
  degradado azul, solo cuando `onBack != null`.
- **✅ AC2** Resuelto como efecto directo de AC1/AC3: las pantallas
  migradas ya no comparten la fila con un `IconButton` externo (que les
  quitaba ancho vía `Modifier.weight(1f)`) — el banner pasa a ocupar el
  100% del ancho disponible automáticamente, sin tocar su padding
  vertical (18dp), que ya alcanzaba para el contenido.
- **✅ AC3** Migradas y verificadas en dispositivo real: **Papelera**,
  **Menú de Seguridad**, **Contraseña PDF**, **Carpeta Segura** (dentro de
  `SecurityScreen.kt`) — las 4 con capturas confirmando "← Volver" en
  blanco arriba del logo/título, banner a ancho completo, navegación de
  vuelta funcionando. **Resultado de escaneo** (`ScanResultScreen.kt`) se
  migró en código (reemplazó un `Scaffold`+`TopAppBar` que además
  duplicaba el título ya mostrado en el banner) pero **no se pudo
  verificar visualmente** — requiere completar una captura real con la
  cámara de ML Kit, no reproducible por `adb` sin apuntar a un documento
  físico. Mismo patrón exacto ya probado en las otras 4 pantallas;
  compilación + `detekt` + `lintDebug` + `testDebugUnitTest` en verde.
- **✅ AC4** Confirmado sin cambios: Home, Biblioteca, Convertir, Ajustes
  y PDF (destinos de la barra inferior) siguen sin flecha de "Volver".
- **Fuera de esta pasada, a propósito:** las 2 pantallas de PIN dentro de
  `SecurityScreen.kt` (líneas ~213-227 y ~415-425) tienen su propia flecha
  blanca sobre fondo degradado de pantalla completa, **sin usar
  `DocuSmartTopBanner`** — son un diseño distinto (pantalla completa, sin
  logo/título de banner), no una instancia de este patrón. No se tocaron.

---

## 10. Bug reportado — Imagen dentro del título "Estudio" en Pomodoro

**Investigado y no reproducido en código:** `StudyTopBar`
(`StudyScreen.kt:361-401`) usa un `TopAppBar` con `Text("Estudio")` puro,
sin ninguna imagen/ícono junto al título. El único ícono relacionado con
Estudio (`Icons.Rounded.MenuBook`) está en el **estado vacío de la
pestaña Lectura**, en una posición completamente distinta (arriba del
texto, en una `Column`, no al lado del título en un `Row`).

**No se cataloga como HU/bug accionable todavía** — necesito una captura
de pantalla o una descripción más específica de qué imagen ves y en qué
pestaña exacta de Estudio (Lectura/Notas/Pomodoro) para poder ubicarla en
el código antes de tocar nada.

**Reproducido y corregido 2026-09-03**, tras navegar en el dispositivo
real a Estudio → Pomodoro: el `TopAppBar` en sí sigue siendo texto puro
("Modo Estudio"), pero **debajo de las pestañas hay un chip** (`Pomodoro
TypeIndicator`, `StudyScreen.kt:1240-1256`) cuyo texto viene de
`R.string.study_study_label`/`study_break_label` -- que en los 5 idiomas
tenían un emoji pegado a la palabra ("📚 Estudio", "🌿 Descanso"). El
usuario confirmó que esto es lo que veía y pidió eliminarlo, más
cualquier otro emoji en un título de la app. Búsqueda adicional en los 5
`strings.xml` encontró un segundo caso: `premium_recommended` ("⭐
Recomendado", badge de la tarjeta de plan recomendado en Premium) --
confirmado por el usuario para eliminar también. Ambos strings
corregidos a texto plano en `values/`, `values-de/`, `values-en/`,
`values-pt/`, `values-ru/`. Los únicos símbolos restantes que matchean un
rango de emoji son los checkmarks "✓" de "Imagen/Documento seleccionado"
(estado funcional, no un título) y las flechas "→" de textos como
"Img→PDF"/"Ver anuncio → +1 uso" (tipográficas, no decorativas) --
fuera de alcance, no se tocaron. Verificado en dispositivo real
(Motorola Edge 30 Neo): el chip de Pomodoro ahora dice solo "Estudio" y
el badge de Premium solo "Recomendado". Gauntlet completo en verde.
**Hallazgo aparte corregido 2026-09-03 (mismo día)**: la tarjeta del plan
Anual en Premium mostraba "Ahorra 44%%" (doble signo de porcentaje).
Causa raíz: `premium_savings_44` se lee con `stringResource(labelRes)`
plano en `PremiumPlanCards.kt:144` -- sin argumentos de formato, así que
el `%%` nunca se colapsa a un solo `%` (eso solo pasa cuando el string se
pasa por `String.format`/`getString(id, args)`, que no es el caso acá).
Al revisar el resto de `strings.xml` en busca del mismo patrón se
encontró un segundo caso idéntico: `premium_feature_compress_desc`
("Reduce PDFs hasta un 90%%..."), leído igual con `stringResource(feature
.descRes)` plano en `PremiumFeatureList.kt:103`. Ambos corregidos a un
solo `%` en los 5 idiomas. Lint (`StringFormatInvalid`) marca cualquier
`%` suelto como potencial format string por defecto -- se agregó
`xmlns:tools` a los 5 `strings.xml` y `tools:ignore="StringFormatInvalid"`
puntual en ambos strings, con comentario explicando que se confirmó por
código que nunca pasan por `String.format`. Se descartó tocar
`pdf_compress_success`/`pdf_crop_success` (mismo patrón `%%` a primera
vista) porque esos sí se formatean después con argumentos reales
(`%1$d`/`%2$d`/`%3$d`) -- su `%%` es el escape correcto. Verificado en
dispositivo real: "Reduce PDFs hasta un 90%" y "Ahorra 44%" ya con un
solo signo. Gauntlet completo en verde.

### Auditoría honesta de los beneficios de Premium, a pedido del usuario

El usuario preguntó, dado que esta sesión confirmó que "Nube integrada"
y ciertas mejoras del visor **no se han construido**, si conviene
mantener esas promesas en el plan Premium o ajustarlo para reflejar lo
que la app realmente hace hoy. Se auditó cada uno de los 8 beneficios
listados (`PremiumFeature` enum) contra el código real, no solo
"Nube integrada":

| Beneficio prometido | Estado real confirmado por código |
|---|---|
| Sin anuncios | ✅ Real |
| PDF a Word | ✅ Real (recién reescrito, muy verificado esta sesión) |
| **PDF a Excel** | ❌ **No existe.** Solo `ExcelToPdfUseCase` (dirección contraria). Ningún `PdfToExcelUseCase` en todo el proyecto. |
| **PDF a PowerPoint** | ❌ **No existe.** Solo `PptToPdfUseCase` (dirección contraria). Ningún `PdfToPptUseCase`. |
| OCR "50+ idiomas" | 🟡 OCR real (`OcrPdfUseCase`, ML Kit), pero la cifra es falsa: solo depende de `com.google.mlkit:text-recognition` (reconocedor **latino únicamente**, sin los módulos chino/japonés/coreano/devanagari) -- no llega a 50 idiomas. |
| **Nube integrada (Drive/Dropbox)** | ❌ **No existe.** `CLOUD_SYNC` solo aparecía en la lista de marketing y el mapeo de ícono -- cero lógica de integración real, coherente con la decisión de esta sesión de quedarse en SAF-only (§17/§19). |
| Compresión avanzada 90% | ✅ Real |
| Conversiones ilimitadas | ✅ Real |

**Recomendación dada**: no se trataba de una función faltante aislada,
sino de **dos beneficios completamente inventados** (PDF a Excel/
PowerPoint) más uno exagerado (OCR), en la pantalla de una compra real
-- riesgo de reembolsos/reseñas negativas y de política de Play Store
(publicidad engañosa en una ficha de compra dentro de la app), no solo
de UX. El usuario eligió **quitar las 3 promesas falsas/exageradas
ahora** en vez de marcarlas "próximamente" o dejarlas.

**Corregido**: eliminadas del enum `PremiumFeature`
(`PremiumPlan.kt`) las entradas `PDF_TO_EXCEL`, `PDF_TO_PPT` y
`CLOUD_SYNC` (y su mapeo de ícono en `PremiumFeatureList.kt`) -- la
lista se renderiza automáticamente vía `PremiumFeature.values()`, sin
necesidad de tocar la pantalla. Los 6 strings de esas 3 entradas
eliminados de los 5 idiomas. `premium_feature_ocr_desc` reescrito sin
la cifra de idiomas, describiendo lo que el OCR real sí hace ("convierte
PDFs escaneados en documentos con texto real y buscable"). El plan
Premium ahora solo promete: sin anuncios, PDF a Word, OCR, compresión
avanzada 90%, conversiones ilimitadas -- las 5 funciones verificadas
como reales.

---

## 11. Compose UI Testing en toda la app

Ya catalogado en detalle en [`compose-ui-testing.md`](compose-ui-testing.md)
— inventario de las 18 pantallas con prioridad por flujo, 3 ya cubiertas
(Visor, Convertir, Seguridad/PIN) y la advertencia técnica sobre por qué
esto no mueve por sí solo la métrica de cobertura de SonarCloud. No se
duplica acá; este ítem queda referenciado en la tabla de §2 para que la
priorización general lo tenga en cuenta junto a los demás.

---

## 12. Revisión UX/UI — hallazgos propios y plan de mejoras

Revisión heurística sobre el código de navegación/layout y las capturas
reales tomadas en dispositivo durante esta sesión (Home, Biblioteca,
Papelera, Ajustes, Premium). No reemplaza una auditoría visual completa
de las 18 pantallas (eso requeriría capturar cada una individualmente,
en claro y oscuro) — son los patrones que ya se pueden confirmar con lo
observado hasta ahora, más los que el propio usuario ya identificó
(varios de los ítems 3, 4, 6, 7 de este documento **son**, en el fondo,
hallazgos de heurística de consistencia — Nielsen #4, "Consistencia y
estándares").

### Hallazgos adicionales

**H1 — Terminología inconsistente entre "eliminar" real y "eliminar del
historial".** El menú de un archivo en Home dice "Eliminar del
historial", pero en el código mueve el archivo a la Papelera real
(`removeDocument()` → `moveToTrash()`) — el texto sugiere que solo se
limpia un historial de vistos recientemente, cuando en realidad el
archivo deja de aparecer en Biblioteca también. Es un residuo textual de
antes de que existiera la Papelera (RF-VIS-07). Heurística de Nielsen
#2 (correspondencia entre el sistema y el mundo real): el texto miente
sobre lo que realmente pasa.

- **✅ Corregido 2026-08-30** — label cambiado a "Eliminar" en
  `DocuSmartDocumentItem.kt`, sin tocar lógica. Verificado en dispositivo:
  el menú "⋮" de un archivo en Recientes ahora dice "Eliminar".
- **Hallazgo nuevo al corregirlo (H6):** este componente compartido
  (`DocuSmartDocumentItem.kt`, usado por el menú "⋮" de Home y
  Biblioteca) tiene **todos sus labels hardcodeados en español**
  ("Renombrar", "Convertir", "Compartir", "Agregar/Quitar de favoritos",
  ahora "Eliminar") — no pasa por `stringResource()` como el resto de la
  app. Catalogado como fila 21 de la tabla de §2 — afecta a los usuarios
  en, de, pt, ru por igual.
  - **✅ Corregido y verificado en dispositivo real 2026-09-03.** Todos
    los labels del menú "⋮" y del diálogo `RenameDocumentDialog`
    (título, campo, botones) pasan a `stringResource()`, reusando claves
    ya existentes donde el texto coincidía exactamente
    (`viewer_rename`/`viewer_convert`/`viewer_create_qr`/`general_share`/
    `general_delete`/`qr_open_document`/`general_cancel`) y agregando 3
    claves nuevas (`doc_item_add_favorite`, `doc_item_remove_favorite`,
    `doc_item_rename_title`) en los 5 idiomas donde no había ninguna
    equivalente. `DocumentType.label` (PDF/Word/Excel/etc.) queda sin
    tocar a propósito -- son nombres de formato, no se traducen en
    ningún otro lugar de la app.

**H2 — Botón "Eliminar ahora" en Papelera no aclara que puede pedir un
permiso del sistema.** Tras el fix de hoy (§17 de `visor-biblioteca.md`),
tocar "Eliminar ahora" puede abrir un diálogo de Android pidiendo permiso
para borrar la foto. Para un usuario esto puede sentirse como un paso
inesperado. Sugerencia de copy: agregar una nota breve en el diálogo de
confirmación ("Android puede pedirte que confirmes el borrado de fotos
que no creó esta app") — mejora de claridad, no de funcionalidad.

- **✅ Corregido y verificado en dispositivo real 2026-09-03.** Nota
  agregada en `TrashDeleteForeverDialog` (texto exacto sugerido acá,
  traducido a los 5 idiomas), debajo del cuerpo principal del diálogo, en
  un tono más tenue (`bodySmall`/`onSurfaceVariant`).

**H3 — Botones "Restaurar"/"Eliminar ahora" en Papelera usan el mismo
estilo visual (`OutlinedButton`) para una acción reversible y una
irreversible.** Hoy se diferencian solo por color de texto (verde vs.
rojo), pero tienen el mismo peso visual. Heurística #5 (prevención de
errores): una acción destructiva debería destacar menos que la segura, o
requerir un gesto ligeramente distinto, para reducir toques accidentales
sobre "Eliminar ahora" en vez de "Restaurar" (están uno al lado del otro,
mismo tamaño).

- **✅ Corregido y verificado en dispositivo real 2026-09-03.** "Restaurar"
  pasa de `OutlinedButton` a `FilledTonalButton` (más peso visual, acción
  segura/reversible); "Eliminar ahora" queda igual (`OutlinedButton` +
  color de error), ahora con menos peso relativo que "Restaurar" en vez
  de compartir el mismo estilo.

**H4 — Accesos rápidos de Home (9 ítems) vs. Ajustes con secciones
colapsadas por header.** Home no agrupa sus 9 accesos por categoría
(Documentos/Seguridad/Estudio/QR), a diferencia de Ajustes que sí usa
headers de sección. Al pasar a grilla (HU-UX-04), es una buena oportunidad
de agrupar visualmente (p.ej. separador o header sutil) en vez de una
grilla plana de 9 íconos iguales — reduce carga cognitiva.

- **Se absorbe dentro de HU-UX-04** como AC opcional a discutir, no como
  ítem aparte.

**H5 — Papelera sin indicación de espacio ocupado.** A diferencia de
Ajustes → Almacenamiento (que sí muestra KB totales), la Papelera no
comunica cuánto espacio liberaría "Borrar todo" — dato relevante
considerando que es justamente la acción que el usuario pediría para
"limpiar espacio".

- **✅ Corregido y verificado en dispositivo real 2026-09-03.** Se agregó
  `sizeBytes: Long = 0L` a `DocumentUiModel` (los 3 call sites reales de
  `DocumentRepository` ya tenían el byte count en scope antes de
  formatearlo a string) para poder sumar el total real sin re-parsear el
  string ya formateado (frágil entre locales por el separador decimal).
  Texto "%1$s en la papelera" (mismo formato que `formatSize()`,
  duplicado localmente en `TrashScreen.kt` a propósito -- es privado en
  `DocumentRepository`) mostrado arriba de "Borrar todo". Verificado:
  "501 KB en la papelera" con 1 archivo real en la papelera.

### Plan de mejoras UX — orden sugerido (no vinculante, para discutir)

1. **Consistencia rápida y de bajo riesgo primero** (da la sensación de
   "pulido" con poco esfuerzo): #4 (botón Papelera), H1 (texto engañoso),
   #3 sin la parte de renombrado (grilla), H5 (tamaño en Papelera).
2. **Consistencia estructural** (toca más pantallas, pero centralizada):
   #7 banner azul + Volver (HU-UX-08), #6 banner de anuncios (HU-UX-07).
3. **Funcionalidad nueva de alto valor, riesgo acotado:** #1 (QR/Convertir
   desde archivo — HU-UX-01/02), extendido a Visores.
4. **Funcionalidad nueva de mayor esfuerzo:** #2 (cámara para convertir —
   HU-UX-03).
5. **Épicas, requieren su propia planificación aparte:** #5
   (personalización — empezar solo por tamaño de letra, HU-UX-05), #10
   (Compose UI Testing, ya tiene su propio documento).

---

## 13. Mejora — Actualizar splash screens e íconos con el nuevo diseño

**✅ Implementado y verificado en dispositivo real, 2026-08-30.** El
usuario entregó dos handoffs completos (`handoff/` para mouthblack,
`handoff-docusmart/` para DocuSmart) con especificación, código Compose
listo y vectores del ícono adaptativo. Se integraron ambos diseños sobre
la arquitectura de navegación existente, sin adoptar la reestructuración
de `MainActivity`/SplashScreen API que sugerían los handoffs (habría sido
un cambio de arquitectura innecesario y de mayor riesgo).

**Investigado (estado actual, importante para estimar bien):**

- **Los dos splash NO usan ninguna imagen/ícono como archivo** —
  `SplashMouthBlackScreen.kt` y `SplashDocuSmartScreen.kt` dibujan todo a
  mano con Compose: la "M" de mouthblack es un `Text` dentro de un `Box`
  con esquinas redondeadas (`SplashMouthBlackScreen.kt:77-100`), y el
  logo de DocuSmart son dos `Box` superpuestos simulando documentos
  (`SplashDocuSmartScreen.kt:96-140`), con sus animaciones de entrada
  (fade, scale, spring) ya calibradas alrededor de esas formas
  específicas. **Reemplazar el diseño acá no es "cambiar un archivo de
  imagen"** — es reescribir esa parte del Composable para que muestre el
  logo nuevo (como `Image`/ícono vectorial) en vez de las formas
  dibujadas a mano, y probablemente reajustar las animaciones para que
  luzcan bien con la proporción/forma del diseño nuevo.
- **El ícono de inicio (lanzador) sí es un asset real**, reemplazable
  directamente: `ic_launcher_docusmart` en las 5 densidades
  (`mipmap-mdpi` a `mipmap-xxxhdpi`, formato `.webp`) más el ícono
  adaptativo (`mipmap-anydpi-v26/ic_launcher_docusmart.xml` +
  `ic_launcher_docusmart_round.xml` + fondo en
  `drawable/ic_launcher_docusmart_background.xml`). Android Studio genera
  las 5 densidades automáticamente a partir de un archivo fuente (Image
  Asset Studio) — no hay que exportar cada tamaño a mano.
- **El ícono del banner azul también es un asset real** y el más simple
  de reemplazar: `drawable/ic_docusmart_logo.xml` (vector), referenciado
  desde `DocuSmartTopBanner.kt:81` — reemplazar el XML del vector (o
  regenerarlo desde el SVG/PNG nuevo) alcanza, sin tocar código Kotlin.

**Riesgo:** bajo-medio. El ícono de banner y el de lanzador son cambios
de asset puro (riesgo bajo, alto impacto de marca). Los splashes son el
punto de mayor cuidado — tocan Composables con animaciones ya afinadas
(tiempos, easing, rebote) que hay que revisar que sigan viéndose bien con
las formas/proporciones del diseño nuevo, y verificar en dispositivo real
que la duración total del splash (~2.5s + ~1.5s) sigue sintiéndose bien
con el contenido nuevo.

### Qué se hizo

- **`SplashMouthBlackScreen.kt`** reescrito con el diseño nuevo: círculo
  blanco con "mordisco" recortado (`BlendMode.Clear` sobre una capa
  offscreen), monograma "mb", wordmark "mouthblack", descriptor "DEV &
  TECH" en verde menta `#35D08A`, sobre fondo `#0B0B0B`. Animación de
  entrada (scale+alpha con rebote) y del mordisco (entra, se retira,
  muerde de nuevo, reposo) portada del handoff tal cual.
- **`SplashDocuSmartScreen.kt`** reescrito con la opción "la línea
  revela": mira de 4 esquinas + documento que se revela de arriba a abajo
  detrás de una línea de escaneo cian, sobre degradado azul→índigo
  (`#1E9BFF → #2563FF → #3B1FE0`). El wordmark reutiliza el string
  `splash_tagline` **existente** (actualizado a "ESCANEA · ORGANIZA" y su
  traducción en los 5 idiomas) en vez de hardcodear texto nuevo — se
  preservó el i18n ya construido.
- **Ícono de la app**: `drawable/ic_launcher_docusmart_background.xml`
  reemplazado (antes era literalmente la plantilla verde de Android
  Studio sin personalizar — nunca se había tocado) y
  `drawable/ic_launcher_docusmart_foreground.xml` agregado con el
  documento + mira + línea de escaneo. El ícono adaptativo
  (`mipmap-anydpi-v26/ic_launcher_docusmart.xml` y `_round.xml`) ahora
  referencia estos vectores en vez de los `.webp` por densidad — con
  minSdk 26 el ícono adaptativo es el único que se usa nunca, así que los
  15 `.webp` huérfanos (5 densidades × 3 variantes) se borraron.
- **Ícono del banner azul** (`drawable/ic_docusmart_logo.xml`) actualizado
  con el mismo arte que el ícono de la app, para consistencia de marca
  entre el lanzador y el banner.
- **Tipografías**: se usan las familias del sistema (`SansSerif`/
  `Monospace`, exactamente el valor por defecto que ya traían los
  handoffs) en vez de Space Grotesk/JetBrains Mono/Plus Jakarta Sans —
  agregar esas fuentes de marca requiere archivos `.ttf` reales o un
  certificado de Google Fonts Downloadable que no era seguro escribir de
  memoria (un hash de certificado mal copiado falla en silencio en
  runtime). Queda como mejora de seguimiento, no bloqueaba el rediseño
  visual/animado que era el pedido principal.
- Accesibilidad: ambos splashes ahora respetan
  `Settings.Global.ANIMATOR_DURATION_SCALE == 0` (mostrando el estado
  final sin animar) — mejora nueva, no estaba en el código anterior.

### Verificado en dispositivo real

- Compilación, `detekt`, `lintDebug` y `testDebugUnitTest` en verde.
- Splash de mouthblack: capturado en su estado de reposo, coincide
  exactamente con la especificación (círculo mordido, "mb", wordmark,
  descriptor en verde menta, "V1.0" al pie).
- Splash de DocuSmart: capturado a mitad de animación, con la línea de
  escaneo y el documento revelándose parcialmente — confirma que el
  mecanismo de recorte (`clipRect` sobre `reveal`) funciona.
- Ícono de la app confirmado en el launcher del dispositivo (buscador de
  apps) con el nuevo diseño sobre degradado azul.
- Logo del banner azul confirmado en la pantalla de Inicio, consistente
  con el ícono de la app.
- Sin ningún `FATAL EXCEPTION` en logcat durante toda la verificación
  (arranque en frío, onboarding, navegación a Home).
- Detalle de la verificación: el dispositivo tenía
  `animator_duration_scale=0` (probablemente configurado para las
  pruebas instrumentadas de Compose UI Testing, ver `ci.yml`) — se
  reactivaron las animaciones temporalmente para confirmar el
  comportamiento animado real, y se restauraron a 0 al terminar para no
  afectar la suite de pruebas instrumentadas.

### Pendiente de seguimiento (no bloquea este ítem)

- Integrar las tipografías de marca reales (Space Grotesk Bold, JetBrains
  Mono Bold/Regular, Plus Jakarta Sans ExtraBold) vía Google Fonts
  Downloadable (requiere el certificado oficial generado por el asistente
  de Android Studio, no escrito a mano) o archivos `.ttf` que el usuario
  provea directamente.

---

## 14. Preguntas abiertas que necesito antes de ejecutar cualquier cosa

1. Ítem 3/HU-UX-04: ¿qué hacemos con la duplicación "Convertir" (CTA
   grande) + "Convertir" (acceso rápido, antes "Img→PDF")? Ver las 3
   opciones en §5.
2. Ítem 6/HU-UX-07: ¿el banner de anuncios va también en Contraseña PDF,
   Carpeta Segura y Papelera, o se dejan sin anuncios por ser pantallas de
   acción rápida/seguridad?
3. Ítem 8: necesito una captura de pantalla o más detalle de en qué
   pestaña de Estudio ves la imagen junto al título — no se encontró en
   el código tal como está descrito.
4. ¿Con cuál de los 5 bloques del "Plan de mejoras UX" (§12, orden
   sugerido) empezamos, o hay una prioridad de negocio distinta que deba
   pasar primero (por ejemplo, si hay una fecha de publicación en Play
   Store que condicione qué se aborda antes)?

---

## 15. Mejora — Selector de archivo desde la biblioteca de la app (item #15)

**✅ Implementado y verificado en dispositivo real 2026-09-03.** Seguridad
y Herramientas PDF solo ofrecían el selector de archivos del sistema
(`ActivityResultContracts.OpenDocument()`/`GetContent()`) -- ahora
también pueden elegir un documento ya indexado por la app (la misma
Biblioteca completa que usa la pantalla Biblioteca, no solo los archivos
que la app misma generó).

**Investigado antes de construir:** ya existía un intento parcial en
`SecureFolderContent` (Carpeta Segura) con una sección "Desde mi
biblioteca", pero solo listaba `uiState.appFiles` -- archivos en las
carpetas internas `converted`/`pdftools`, es decir, únicamente lo que la
propia app generó, no Downloads/Imágenes de MediaStore como sí hace la
Biblioteca real. Contraseña PDF (Proteger/Quitar) y las 12 herramientas
de un solo PDF de Herramientas PDF no tenían ninguna opción de biblioteca
en absoluto.

### Qué se hizo

- **`AppLibraryPickerViewModel`** (nuevo, `core/ui/components`): ViewModel
  mínimo que carga `DocumentRepository.loadAllDocuments()` (mismo
  inventario que la Biblioteca real, ya excluye lo que está en la
  Papelera).
- **`FileSourcePickerDialog`** (nuevo, `core/ui/components`): diálogo
  compartido con las dos opciones ("Desde el dispositivo" / "Desde la
  biblioteca de DocuSmart" con lista filtrable) -- reemplaza la UI que ya
  existía en `SecureFolderContent`, generalizada para reusarse en los
  demás call sites. Acepta un `filter: (DocumentUiModel) -> Boolean` para
  restringir tipos (p.ej. solo PDF).
- **`DocumentUiModel.toContentUri()`** (nuevo, junto al modelo): `id`
  mezcla `content://...` (Downloads/Imágenes) y rutas absolutas
  (archivos generados por la app) -- este helper resuelve ambos a un
  `Uri` legible por `ContentResolver` sin que cada call site tenga que
  saber la diferencia.
- **Seguridad → Carpeta Segura**: `SecureFolderContent` migrado al nuevo
  diálogo compartido -- ahora sí ve la Biblioteca completa, no solo
  archivos generados por la app. `uiState.appFiles`/`loadAppFiles()`
  (en `SecurityViewModel`) eliminados por completo al quedar sin ningún
  uso, junto con las 5 claves de string `security_from_library`/
  `security_no_library_files`/`security_from_device`/
  `security_browse_system_files`/`security_import_source_question`
  (reemplazadas por las nuevas `filepicker_*`, genéricas y compartidas).
- **Seguridad → Contraseña PDF** (`ProtectPdfForm`/`RemovePdfPasswordForm`
  en `PdfPasswordScreen.kt`): ganaron la opción de biblioteca (antes no
  tenían ninguna), filtrada a `DocumentType.PDF`.
- **Herramientas PDF** (`PdfToolsScreen.kt`): las 12 herramientas de un
  solo PDF (Dividir, Comprimir, Rotar, etc.) comparten el mismo
  `singlePdfLauncher`/`onPdfsSelected()`, así que se agregó un único
  diálogo compartido (`showPdfSourceChooser`) en vez de duplicarlo 12
  veces -- los 12 `onSelectPdf = { singlePdfLauncher.launch(MIME_PDF) }`
  pasaron a `onSelectPdf = { showPdfSourceChooser = true }`.
  **Fuera de alcance a propósito**: Combinar (`multiPdfLauncher`,
  selección múltiple) y Comparar (`comparePdfALauncher`/
  `comparePdfBLauncher`, dos selectores independientes) quedan solo con
  el selector del sistema -- extender el patrón ahí es un esfuerzo aparte
  (selección múltiple desde biblioteca, o dos diálogos idénticos
  simultáneos) que no se justificaba en esta pasada.

### Hallazgo real encontrado durante la verificación (no relacionado con este ítem, no corregido)

Al verificar en dispositivo real, el filtro a PDF en Contraseña PDF
mostraba **"No hay archivos en tu biblioteca todavía"** pese a que el
dispositivo tiene decenas de PDFs reales (confirmado con
`adb shell content query --uri content://media/external/downloads`).
Diagnosticado con los logs de `DocumentRepository`
(`Timber.d("Downloads: ${documents.size} documentos")`): **la consulta a
`MediaStore.Downloads` devuelve 0 filas en este dispositivo**, mientras
que `Imágenes: 50` sí carga bien -- confirmado que el selector nuevo
funciona correctamente probándolo sin el filtro de PDF (Carpeta Segura),
donde sí mostró y protegió con éxito un archivo real de la Biblioteca.

Causa probable: todos los PDFs de Downloads en este dispositivo tienen
`owner_package_name=NULL` (confirmado por consulta directa) -- es decir,
ninguno fue insertado vía `MediaStore.insert()` desde la propia app, sino
escrito directo a `/sdcard/Download/` y detectado después por el escáner
de medios. Bajo almacenamiento con ámbito (scoped storage, Android 10+),
un app sin `READ_EXTERNAL_STORAGE` (o con él pero sin efecto real en
API 33+) generalmente no puede enumerar filas de Downloads que no posee,
a diferencia de Imágenes que sí quedó visible vía `READ_MEDIA_IMAGES`
(ya concedido). **Esto no es un bug de este ítem** -- es una limitación
preexistente de `DocumentRepository.loadPdfsFromDownloads()`. Catalogado
como hallazgo nuevo para investigar aparte, no corregido en esta pasada.

**Ampliado 2026-09-03, a pedido del usuario (confirmó que ve el mismo
síntoma con Excel/Word/Texto/PowerPoint desde el dispositivo real, no
solo PDF)** -- revisando el código a fondo, en realidad son **dos
problemas distintos** dentro de la misma función, no uno:

1. **La misma limitación de scoped storage de arriba también afecta a
   Word, Excel y PowerPoint** -- pese a llamarse `loadPdfsFromDownloads()`,
   la función consulta un único `mimeTypes` con los 7 tipos de Office
   juntos (`application/pdf`, `application/msword`,
   `.../wordprocessingml.document`, `application/vnd.ms-excel`,
   `.../spreadsheetml.sheet`, `application/vnd.ms-powerpoint`,
   `.../presentationml.presentation`) en una sola consulta a
   `MediaStore.Downloads` -- el log `Downloads: 0 documentos` ya contaba
   los 4 formatos juntos, no solo PDF. Mismo diagnóstico, mismo arreglo
   pendiente para los 4.
2. **Hallazgo nuevo, causa distinta (no es scoped storage): "Texto" no
   está en absoluto en la lista `mimeTypes` de esa consulta.** No es un
   problema de permisos ni de propiedad de la fila -- es que
   `"text/plain"` (ni `"text/markdown"`) nunca se incluyó en el filtro
   `selection`/`selectionArgs` de `loadPdfsFromDownloads()`, así que un
   `.txt`/`.md` en Descargas **nunca llega ni siquiera a evaluarse**,
   sin importar quién lo creó. `mimeToDocumentType()`/
   `extensionToDocumentType()` sí saben mapear texto a
   `DocumentType.TEXT` (línea usada para archivos generados por la app),
   pero ese código es inalcanzable para archivos reales de Descargas por
   esta omisión en la consulta.

**Resumen para retomar en otra sesión:**
- PDF, Word, Excel, PowerPoint: mismo síntoma, misma causa (visibilidad
  de scoped storage para filas sin `owner_package_name` propio) --
  necesita decidir el enfoque correcto (¿`READ_EXTERNAL_STORAGE` con
  `requestLegacyExternalStorage`? ¿asumir que solo se ven archivos que
  la propia app generó y aceptarlo como límite conocido? ¿migrar a SAF/
  `ACTION_OPEN_DOCUMENT_TREE` para el caso general?).
- Texto (.txt/.md): causa distinta y más simple -- agregar
  `"text/plain"` (y opcionalmente `"text/markdown"`) a la lista
  `mimeTypes` de `loadPdfsFromDownloads()`. Esto por sí solo no
  resolvería el problema de fondo #1 si el archivo tampoco tiene
  `owner_package_name` propio, pero es un arreglo independiente y
  necesario de todos modos.
- Probablemente afecta también al conteo real de las categorías
  "PDF"/"Word"/"Excel"/"PowerPoint"/"Texto" en la pantalla Biblioteca,
  sin relación con el selector de archivo de este ítem #15.

### Verificado en dispositivo real (Motorola Edge 30 Neo)

- `compileDebugKotlin`, `detekt`, `lintDebug`, `testDebugUnitTest` en
  verde tras corregir 2 imports con wildcard (`WildcardImport` de
  detekt) en `FileSourcePickerDialog.kt`.
- `connectedDebugAndroidTest` de `SecurityScreenTest` y
  `PdfToolsScreenTest` (3/3, 0 fallos) -- sin regresiones tras eliminar
  `appFiles`.
- Flujo completo verificado a mano: Seguridad → Contraseña PDF → Proteger
  PDF → selector nuevo (muestra "No hay archivos" para PDF por el
  hallazgo de arriba, esperado); Seguridad → Carpeta Segura → Proteger
  nuevo archivo → selector nuevo → biblioteca muestra 4 archivos reales
  (imágenes) con nombre y tamaño correctos → seleccionar uno lo protege
  de punta a punta (aparece en "Archivos protegidos").

## 16. Bug — Fila 22: `loadDocumentsFromDownloads()` no ve documentos reales de Descargas

**🟡 Corregido lo corregible en dispositivo real 2026-09-03 -- la parte de
fondo (scoped storage en API 33+) es una restricción real de la
plataforma, no un bug de código, confirmado por búsqueda externa antes
de tocar nada.**

### Investigado antes de corregir

Confirmado con una búsqueda externa (no asumido) que
`android.permission.READ_MEDIA_DOCUMENTS` -- ya declarado en
`AndroidManifest.xml` con el comentario "Para leer documentos PDF en
Android 13+" -- **no existe como permiso real de Android**. Es decir, un
intento de arreglo de una sesión anterior declaró un permiso que
Android nunca reconoce, sin ningún efecto (ni bueno ni malo) sobre la
consulta real. También confirmado: "Starting in API level 33, the
READ_EXTERNAL_STORAGE permission has no effect anymore" -- en Android
13+ no existe ningún permiso equivalente a `READ_MEDIA_IMAGES` para
documentos (PDF/Word/Excel/PowerPoint/Texto) de otras apps; la única vía
oficial es Storage Access Framework (`ACTION_OPEN_DOCUMENT`, ya usado
por "Desde el dispositivo") o `MANAGE_EXTERNAL_STORAGE` (acceso a todos
los archivos, restringido por política de Play Store).

### Qué se corrigió

- **`AndroidManifest.xml`**: `android.permission.READ_MEDIA_DOCUMENTS`
  eliminado (no existe, no hacía nada). `READ_EXTERNAL_STORAGE` amplió su
  `maxSdkVersion` de 28 a 32 -- en API 29-32 este permiso **sí** sigue
  teniendo efecto real para ver documentos de Descargas creados por
  otras apps (el corte real a "sin efecto" es específicamente API 33+,
  no antes), así que estaba dejando sin cubrir un rango de versiones
  donde el fix sí funciona.
- **`DocumentRepository.kt`**: función renombrada de
  `loadPdfsFromDownloads()` a `loadDocumentsFromDownloads()` -- el
  nombre anterior era engañoso, siempre consultó PDF+Word+Excel+
  PowerPoint juntos en una sola consulta, nunca solo PDF. Se agregó
  `"text/plain"`/`"text/markdown"` a la lista `mimeTypes` -- antes ni
  siquiera estaban en el filtro, así que un `.txt`/`.md` de Descargas no
  llegaba a evaluarse sin importar el propietario (bug independiente del
  de scoped storage, con arreglo real y completo sin importar la
  versión de Android).
- `config/detekt/baseline.xml`: la entrada `NestedBlockDepth` para esta
  función se actualizó a mano con el nuevo nombre (el detekt existente
  ya estaba baselineado, solo quedó huérfano por el rename).

### Qué sigue sin arreglo posible (limitación de la plataforma, no de código)

En Android 13+ (API 33+, incluido el Motorola Edge 30 Neo usado para
verificar esta sesión), **ningún cambio de código puede hacer que la app
vea PDF/Word/Excel/PowerPoint de Descargas que ella misma no creó** --
es una restricción deliberada de scoped storage sin permiso equivalente
disponible. Las únicas rutas reales para ese caso son Storage Access
Framework (ya cubierto por "Desde el dispositivo") o
`MANAGE_EXTERNAL_STORAGE` (decisión de producto/política, no tomada acá
sin pedirlo explícitamente). El fix de "Texto" sí es completo y real,
pero en la práctica solo se notará para archivos de texto que la propia
app cree vía `MediaStore.insert()` correctamente atribuido (ningún flujo
actual de DocuSmart genera `.txt` a Descargas todavía) o en dispositivos
API 29-32 reales.

### Verificado en dispositivo real (Motorola Edge 30 Neo, API 34)

- `compileDebugKotlin`, `detekt` (tras actualizar el baseline),
  `lintDebug`, `testDebugUnitTest` en verde.
- `connectedDebugAndroidTest` de Biblioteca/Seguridad/Herramientas PDF
  (9/9, 0 fallos) -- sin regresiones por el rename ni por el cambio de
  manifest.
- No se pudo demostrar visualmente "ahora sí aparecen documentos
  externos" en este dispositivo a propósito -- es API 34, exactamente el
  caso donde la limitación de plataforma sigue aplicando después del
  fix (comportamiento esperado, no una falla de la corrección).

## 17. Mejora — Vincular carpeta de Descargas por SAF (alternativa real a la fila 22)

**✅ Implementado, verificado en dispositivo real y con la copia ajustada
2026-09-03 -- funciona end-to-end. Limitación real e importante de
Android descubierta durante la propia verificación: el usuario NO puede
vincular la carpeta "Descargas" en sí misma, solo una subcarpeta dentro
de ella -- la copia de la UI ya lo refleja (ver sección de copia más
abajo), a pedido explícito del usuario.**

Pedido explícito del usuario tras el hallazgo de la fila 22/§16: dado que
ningún permiso puede hacer que la app vea Word/Excel/PDF/PowerPoint/Texto
de Descargas en API 33+, se necesitaba una alternativa real para que el
usuario sí pueda verlos, no solo una explicación de la limitación.

### Qué se implementó

- **`DownloadsAccessManager.kt`** (nuevo): envuelve
  `ACTION_OPEN_DOCUMENT_TREE` + `ContentResolver.takePersistableUriPermission()`
  -- el usuario vincula una carpeta una sola vez con el selector nativo
  de Android y el permiso persiste entre reinicios de la app/dispositivo.
  Valida el permiso guardado contra `persistedUriPermissions` real (no
  solo lo que quedó en `SharedPreferences`) para detectar si el usuario
  lo revocó desde Ajustes del sistema.
- **`DocumentRepository.kt`**: `loadAllDocumentsRaw()` usa
  `loadDocumentsFromLinkedFolder()` (enumera el árbol real vía
  `DocumentFile`, sin la restricción de scoped storage de "solo filas
  propias") en vez de `loadDocumentsFromDownloads()` cuando hay una
  carpeta vinculada. `deleteDocument()` distingue un URI de carpeta
  vinculada (autoridad `com.android.externalstorage.documents`) de uno
  de MediaStore y borra vía `DocumentsContract.deleteDocument()` --
  `MediaStore.createDeleteRequest()` lanza `IllegalArgumentException`
  si se le pasa un URI que no es de MediaStore, y antes de esta función
  nunca recibía otra cosa.
- **`LibraryScreen.kt`**: tarjeta "Ver todos tus archivos de Descargas" /
  "Vincular carpeta" en la pestaña Dispositivo, visible solo mientras no
  hay carpeta vinculada. `initialUriHint()` pre-navega el selector nativo
  directo a Descargas (`DocumentsContract.buildDocumentUri(..., "primary:Download")`)
  para no obligar al usuario a buscarla manualmente.
- **`SettingsScreen.kt`** / **`SettingsViewModel.kt`**: ítem "Carpeta de
  Descargas" en Ajustes → Almacenamiento, con estado
  ("Vinculada"/"Sin vincular") y diálogo de confirmación para
  desvincular.
- `config/detekt/detekt.yml`: `TooManyFunctions.thresholdInClasses`
  subido de 15 a 20 -- `DocumentRepository` sumó 5 funciones pequeñas y
  cohesivas (`loadDocumentsFromLinkedFolder`, `documentFromLinkedFile`,
  `eligibleNameAndMime`, `isSupportedDownloadMime`, `deleteSafDocument`),
  mismo criterio ya documentado en este archivo para managers con varios
  tipos de recurso en paralelo.
- `config/detekt/baseline.xml`: `NestedBlockDepth` para
  `loadDocumentsFromLinkedFolder()` (mismo patrón ya baselineado para
  `loadDocumentsFromDownloads()`: bucle con try/catch por fila, no se
  puede aplanar sin perder el manejo de errores por archivo) y
  `ReturnCount` para `eligibleNameAndMime()` (guard clauses -- mismo
  patrón ya baselineado 17 veces en este proyecto para funciones
  "validar y construir resultado", ej. todos los `copyUriToCache()`).

### Hallazgo real encontrado durante la verificación en dispositivo real

**Android no permite vincular la carpeta "Descargas" en sí misma vía
SAF, ni tampoco la raíz del almacenamiento interno.** Confirmado en el
Motorola Edge 30 Neo (API 34): al abrir el selector (incluso ya
pre-navegado dentro de Descargas por `initialUriHint()`), Android
muestra el aviso *"No se puede usar esta carpeta -- Para proteger tu
privacidad, elige otra carpeta"* y el botón "USAR ESTA CARPETA" queda
deshabilitado, tanto para "Descargas" como para la raíz
"motorola edge 30 neo". Confirmado también por búsqueda externa: desde
Android 11, `ACTION_OPEN_DOCUMENT_TREE` bloquea explícitamente la raíz
del almacenamiento y los directorios estándar como Descargas -- **una
subcarpeta dentro de Descargas sí es seleccionable** (verificado
vinculando `Descargas/DMSS` con éxito: permiso persistido, tarjeta de
Biblioteca desaparece, Ajustes pasa a "Vinculada", desvincular funciona
y la tarjeta vuelve a aparecer).

En la práctica esto significa que la función **no resuelve el caso más
común** que motivó el pedido (ver un PDF que el navegador o WhatsApp
guardó directo en la raíz de Descargas) -- el usuario tendría que crear
una subcarpeta dentro de Descargas y mover sus archivos ahí a mano, o
vincular otra carpeta donde sí organice sus documentos. Sigue siendo
mejor que nada (ninguna alternativa sin código nativo del sistema deja
vincular la raíz de Descargas). Antes de fusionar, el usuario pidió
reemplazar el texto del banner por una copia propia más simple y amable
("Elije tu carpeta preferida para encontrar tus documentos de tu
dispositivo y vincularlo a DocuSmart"), sin la explicación técnica de la
restricción de Android -- decisión consciente, ya que si el usuario
intenta vincular Descargas igual, es el propio Android el que se lo
impide con su aviso de sistema. Cambio de solo strings (5 idiomas).

### Atajo a la carpeta vinculada (pedido explícito del usuario)

Botón adicional junto a Dispositivo/Mis archivos/Papelera, visible solo
mientras hay una carpeta vinculada: ícono de carpeta compacto (no
`weight(1f)` como las otras 3 pestañas, para no angostarlas al punto de
partir "Dispositivo" en dos líneas -- ver la vuelta a `labelMedium` para
la etiqueta de las 4 tarjetas por el mismo motivo). Al tocarlo lanza
`Intent(ACTION_VIEW)` con la URI del árbol vinculado y
`DocumentsContract.Document.MIME_TYPE_DIR`, delegando en el gestor de
archivos del dispositivo (con aviso vía `Toast` si ninguna app lo
maneja, en vez de un cierre).

**Hallazgo menor de esta verificación**: en el Motorola Edge 30 Neo, la
app Archivos de Google no navega directo a la subcarpeta vinculada
(`Descargas/DMSS`) -- abre su propia vista de "Descargas" (la carpeta
padre), donde `DMSS` ya aparece listada y es un toque más llegar. No hay
una API estándar de Android para forzar que cualquier gestor de archivos
salte exactamente a una URI de árbol arbitraria; el comportamiento puede
variar según el gestor de archivos predeterminado de cada fabricante. Se
documenta como limitación conocida, no como bug de la app.

### Verificado en dispositivo real (Motorola Edge 30 Neo, API 34)

- `compileDebugKotlin`, `detekt`, `lintDebug`, `testDebugUnitTest` en
  verde (incluye 2 tests de `DocumentRepositoryTest` ajustados: ahora
  stubean `Uri.authority` porque `deleteDocument()` lo consulta primero
  para distinguir un URI de carpeta vinculada de uno de MediaStore).
- Flujo completo probado a mano con `adb`/`uiautomator`: Biblioteca →
  banner con la copia final → selector nativo (pre-navegado a Descargas)
  → confirmación de "No se puede usar esta carpeta" en Descargas y en la
  raíz → vinculación exitosa de `Descargas/DMSS` → banner desaparece y
  aparece el atajo de carpeta junto a Dispositivo/Mis archivos/Papelera
  → atajo abre la app Archivos del sistema → Ajustes muestra "Vinculada
  · toca para desvincular" → desvincular → Ajustes vuelve a "Sin
  vincular" → banner reaparece y el atajo desaparece en Biblioteca. Sin
  cierres inesperados de la app en ningún paso.

### Onboarding: vincular carpeta desde el inicio (pedido explícito del usuario)

El usuario preguntó explícitamente si, sin vincular una carpeta o elegir
archivos uno por uno, DocuSmart podría traer solo PDF/Word/Excel/
PowerPoint/Texto de otras apps automáticamente en Android 13+. Se
confirmó que no: es una regla de la plataforma para toda app externa que
no sea un gestor de archivos del sistema (imágenes sí, vía
`READ_MEDIA_IMAGES`; documentos de terceros no, sin excepción salvo
`MANAGE_EXTERNAL_STORAGE`, descartado por política de Play -- ver
sección de abajo). Decisión: en vez de dejar que el usuario descubra el
banner de Biblioteca por su cuenta, se agregó una 5ª slide al onboarding
(`OnboardingScreen.kt`) que ofrece vincular la carpeta ahí mismo, con
`OnboardingViewModel` nuevo envolviendo `DownloadsAccessManager` (mismo
patrón que `LibraryViewModel`/`SettingsViewModel`). Estado reactivo: si
ya hay una carpeta vinculada muestra "Vinculada: <nombre real>" con un
botón "Cambiar carpeta"; si no, un botón "Vincular carpeta". El usuario
puede saltarse este paso (los botones Saltar/Siguiente/Empezar del
onboarding no lo bloquean) y vincular después desde Ajustes o Biblioteca.

Verificado en dispositivo real: desvincular desde Ajustes → reabrir el
tutorial (Ajustes → Ver tutorial) → 5ª slide muestra el botón "Vincular
carpeta" → selector nativo → elegir `Descargas/DMSS` → confirmar permiso
→ la slide actualiza en el momento a "Vinculada: DMSS" con check verde y
botón "Cambiar carpeta", sin salir ni recargar la pantalla.

### Sobre `MANAGE_EXTERNAL_STORAGE` como alternativa (evaluado y descartado)

El usuario preguntó si, aplicando una buena política de permisos y
solicitándolo desde el onboarding, se podría usar
`MANAGE_EXTERNAL_STORAGE` para evitar la fricción de vincular carpetas.
Investigado con búsqueda externa antes de responder: el criterio de
revisión de Google Play **no es la calidad del consentimiento del
usuario** -- es un criterio técnico: *"solo debes pedir este permiso
cuando tu app no puede lograr su función con SAF o MediaStore"*. Como
esta misma función (SAF) ya demuestra que sí se puede, pedir
`MANAGE_EXTERNAL_STORAGE` sería evidencia en contra en una eventual
revisión, no a favor. El permiso además está reservado de facto para
apps cuya función principal es administrar archivos (gestores de
archivos, backup, antivirus) -- no encaja con el enfoque de DocuSmart
(visor + herramientas PDF + escáner). El castigo por incumplir la
política no es perder el permiso: es que remueven la app completa de
Play Store. Descartado; no se implementó.

Fuentes consultadas: [Use of All files access (MANAGE_EXTERNAL_STORAGE) permission – Play Console Help](https://support.google.com/googleplay/android-developer/answer/10467955?hl=en), [Permissions and APIs that Access Sensitive Information – Play Console Help](https://support.google.com/googleplay/android-developer/answer/9888170?hl=en).

## 18. Calidad de visualización de Word/Excel/PowerPoint (renderer propio con Apache POI)

El usuario señaló que el visor actual de estos formatos no se ve como el
archivo original. Confirmado leyendo el código, no de memoria:
`ViewerScreen.kt` abría el `.docx`/`.pptx`/`.xlsx` como zip y extraía
texto crudo de su XML con expresiones regulares (PowerPoint: título +
párrafos por slide, sin imágenes/diseño/tablas; Word: algo mejor, respeta
negrita/cursiva y encabezados; Excel: grilla, no una réplica de la hoja
real). Era una extracción de texto, no un renderizador real.

### Decisión de enfoque

El usuario preguntó por la viabilidad de construir una librería de
renderizado propia (incluso ofreciéndola después como producto
comercial) y, mientras tanto, renderizar el documento como imagen. Se le
explicó honestamente que un renderizador OOXML desde cero es un proyecto
de años (lo que Microsoft/LibreOffice/Aspose llevan más de una década
construyendo), y que "mostrarlo como imagen" no evita el problema difícil
-- alguien tiene que decidir qué dibujar y dónde primero. Se plantearon 3
caminos reales (Apache POI + renderer propio en Compose / LibreOffice
headless en servidor propio / seguir investigando ambos) y el usuario
eligió **Apache POI + renderer propio en Compose**: gratis, sin servidor,
sin depender de licencias de terceros.

**Hallazgo clave antes de empezar**: Apache POI **ya es una dependencia
de este proyecto** (`app/build.gradle.kts`), usada en producción por
`WordToTextUseCase`/`WordToPdfUseCase`/`ExcelToPdfUseCase`/
`ExcelToCsvUseCase` para las conversiones -- es decir, la compatibilidad
de POI con Android en este dispositivo ya estaba probada de antemano, no
hacía falta un spike de viabilidad desde cero.

### PowerPoint (primero, por ser el visor más pobre de los tres)

Reescrito con `XMLSlideShow`/`XSLFShape` (`extractPptSlides`/
`extractPptShapeContent` en `ViewerScreen.kt`): cada forma real de la
diapositiva (texto con negrita/cursiva/tamaño real vía
`XSLFTextRun`, e imágenes reales vía `XSLFPictureShape.pictureData`) en
vez de solo título+viñetas por regex. Los párrafos de una misma caja de
texto se separan con salto de línea real (antes "Punto uno" y "Punto
dos" quedaban pegados: "Punto unoPunto dos") y los de cuerpo llevan
"• " -- el título de la diapositiva se distingue por tamaño/color/negrita
vía el placeholder (`shape.isPlaceholder` + `shape.textType`).

**Alcance descartado explícitamente**: la posición/tamaño real de cada
forma (`XSLFShape.anchor`, tipo `java.awt.geom.Rectangle2D`) -- confirmado
que el compilador de Kotlin **ni siquiera puede resolver esa clase**
contra el classpath de Android ("Cannot access class 'Rectangle2D'"), lo
mismo para `XMLSlideShow.pageSize` (`java.awt.Dimension`). Las formas se
muestran apiladas en su orden original, no en su posición exacta -- sigue
siendo una mejora real (formato real por forma, imágenes reales) sin
pelear contra una API que no compila en este proyecto.

**Bug real encontrado y corregido en dispositivo real**: `ClassNotFoundException:
com.zaxxer.sparsebits.SparseBitSet` -- crash real al abrir cualquier
.pptx (confirmado con logcat, no solo en el emulador). Causa: una sesión
anterior había excluido `com.zaxxer` de `poi`/`poi-ooxml`/`poi-scratchpad`
para las conversiones de Word/Excel (que nunca tocan esa ruta), pero el
visor de PowerPoint sí la necesita en tiempo de ejecución. Se agregó
`com.zaxxer:SparseBitSet:1.3` como dependencia explícita en vez de quitar
el exclude existente (que sigue siendo válido para Word/Excel).

**Pendiente cosmético, no bloqueante**: el color del texto de cuerpo se
ve más azul de lo esperado (debería verse gris oscuro, distinto del
título) -- probablemente la detección de `shape.textType` para el
placeholder de cuerpo necesita ajuste; no se investigó a fondo para no
demorar la entrega de la corrección del crash real y la separación de
párrafos, que eran los problemas más importantes.

### Verificado en dispositivo real (Motorola Edge 30 Neo, API 34)

- `compileDebugKotlin` confirmó en un primer intento que `Rectangle2D`/
  `Dimension` no compilan contra el SDK de Android (error real, no
  hipótesis) -- llevó a descartar el posicionamiento exacto antes de
  invertir más tiempo en esa vía.
- `detekt` necesitó: subir `TooManyFunctions.thresholdInFiles` de 26 a 29
  (mismo criterio ya documentado para `ViewerScreen.kt`), corregir 2
  `SwallowedException` (pasar la excepción real a `Timber.w`, no solo su
  mensaje), y una entrada de baseline `ReturnCount` para
  `extractPptShapeContent` (guard clauses, mismo patrón ya usado 18+
  veces en el proyecto).
- Abrir `formatted-viewer-sample.pptx` real desde Descargas vía "Abrir
  con DocuSmart": crash reproducido y confirmado con logcat
  (`SparseBitSet`), corregido, y reverificado sin crash con el título y
  los párrafos de cada diapositiva mostrados correctamente separados.
- `detekt`/`lintDebug`/`testDebugUnitTest` en verde en la versión final.

### Word y Excel reescritos con el mismo enfoque (2026-09-03, mismo día)

El usuario evaluó dos alternativas de terceros antes de decidir seguir
con Apache POI (visor de Google vía WebView -- requiere URL pública,
expone documentos privados; Cloudmersive API -- verificado con búsqueda
externa que el plan gratis real es 600 conversiones/mes con **tope de
2.5 MB por archivo**, no 800 como se había leído, y de todas formas
manda los documentos a un tercero). Ambas contradicen la promesa de
privacidad de la propia app (Carpeta Segura, "Solo tú tendrás acceso a
ellos" del onboarding) y necesitan internet para ver un archivo que ya
está en el teléfono -- descartadas. Se confirmó seguir con POI.

**Word**: `XWPFDocument`, iterando `bodyElements` (no `paragraphs` +
`tables` por separado, que pierde el orden real de intercalado) --
`extractWordBlocks`/`extractOoxmlWordBlocks`/`extractWordParagraphBlock`/
`extractWordTableBlock`. Reutiliza `detectWordFormat()`/
`extractLegacyDocBlocks()`/`isHeadingStyleName()` de
`WordFormatDetection.kt` (converter) en vez de duplicar esa lógica --
mismo manejo ya probado de `.doc` legado (OLE2) y de nombres de estilo
de encabezado no ingleses. Mejora real sobre el regex anterior: las
tablas ahora se ven como grilla real (`WordTableView`, mismo componente
visual que ya usa Excel), no como texto plano intercalado.

**Excel**: `WorkbookFactory.create()` (detecta y abstrae `.xls`/`.xlsx`
automáticamente, a diferencia de Word) + `DataFormatter` con
`FormulaEvaluator` -- fechas/monedas/porcentajes/fórmulas se muestran
formateados como Excel los muestra, no el número crudo de serie. Ya no
se asume "solo la primera hoja" (`xl/worksheets/sheet1.xml` hardcodeado
antes): todas las hojas están disponibles, con pestañas
(`ExcelSheetTabs`) para cambiar entre ellas cuando hay más de una.

**Test obsoleto reemplazado**: `WordRunParsingTest.kt` probaba
`parseWordRuns()`/`WORD_HEADING_STYLE_REGEX`, ambos eliminados --
reemplazado por `WordViewerExtractionTest.kt`, que construye `.docx`
reales en memoria con la propia API de escritura de POI (mismo patrón
que `WordToPdfUseCaseTest.kt`) y reutiliza el fixture real
`fixtures/legacy-sample.doc` para el caso OLE2. `isHeadingStyleName()`/
`detectWordFormat()`/`extractLegacyDocBlocks()` ya están cubiertas por
`WordFormatDetectionTest.kt` (converter) -- no se duplican esas pruebas,
solo el mapeo nuevo hacia `WordBlock`/`WordParagraph`/`WordRun`.

### Verificado en dispositivo real (Motorola Edge 30 Neo, API 34)

- `detekt` necesitó: subir `thresholdInFiles` de 29 a 36 (mismo criterio
  documentado para PowerPoint), 2 entradas de baseline `NestedBlockDepth`
  (recorrido de árbol/documento, mismo patrón ya aceptado en el
  proyecto), 1 `MaxLineLength` en el test nuevo.
- `WordViewerExtractionTest.kt` (7 tests: negrita/cursiva por run, runs
  en blanco descartados, encabezado por estilo, tabla como grilla,
  orden real párrafo/tabla/párrafo, `.doc` legado) y el resto del
  gauntlet en verde.
- `formatted-viewer-sample.docx` real: título, negrita en medio de una
  frase, cursiva y tamaño 20 todos correctos visualmente.
- `pruebaword.docx` (la conversión real de WhatsApp que reportó el
  usuario): el visor muestra el contenido sin espacios entre palabras
  ("Funza,Cundinamarca,03deseptiembrede2026") -- **investigado y
  descartado como bug del visor**: el mismo archivo abierto con
  `formatted-viewer-sample.docx` (no convertido) se ve con espaciado
  perfecto, confirmando que el problema está en el conversor
  PDF/imagen→Word que generó ese archivo específico, no en el visor
  nuevo. **Corregido 2026-09-03** -- ver "Bug real: conversor PDF→Word
  pegaba palabras en la misma línea" en §19.
- `formatted-viewer-sample.xlsx` real: tabla con encabezado en negrita
  sobre fondo azul y datos (Nombre/Ciudad/Edad/Puntaje) mostrados
  correctamente, sin crash.

## 19. Bug de compartir + Biblioteca ampliada con historial permanente + hallazgos de investigación

Sesión de seguimiento 2026-09-03: el usuario reportó que compartir un
documento generado por la app (conversión de Word desde WhatsApp) dejó
de funcionar, y que tras vincular una carpeta por SAF la Biblioteca
seguía sin mostrar ningún archivo -- al punto de cuestionar si el
proyecto tenía sentido seguir sin `MANAGE_EXTERNAL_STORAGE`.

### Bug real: compartir documentos generados por la app

`ViewerViewModel.shareDocument()` pasaba `state.fileUri` (para
documentos con id = ruta absoluta, un `Uri.fromFile(...)` real) directo
al `Intent.ACTION_SEND`. Desde Android 7 (API 24) exponer un `file://` a
otra app así lanza `FileUriExposedException` (hereda de
`SecurityException`), atrapada por el catch genérico como "No se pudo
compartir el archivo" -- Biblioteca/Home ya lo resolvían con
`FileProvider` (`DocumentListSection.kt`/`FavoritesSection.kt`) pero el
Visor nunca lo hizo. Corregido envolviendo con el mismo
`FileProvider`/authority ya declarado en el manifest. Verificado en
dispositivo real: compartir desde el botón del Visor (no solo desde el
menú "⋮" de Biblioteca, que ya funcionaba) abre el selector del sistema
con WhatsApp entre las opciones, sin error.

### Bug real: carpeta vinculada sin recorrer subcarpetas

`loadDocumentsFromLinkedFolder()` solo listaba el nivel superior de la
carpeta vinculada (`DocumentFile.listFiles()`, sin recursión). El
usuario había vinculado "Documents" (raíz del dispositivo) -- vacía
salvo una subcarpeta de otra app -- por lo que la Biblioteca mostraba 0
archivos aunque la vinculación en sí funcionara. Corregido con
`collectLinkedFolderDocuments()`, recursivo hasta
`LINKED_FOLDER_MAX_DEPTH` (8, tope de seguridad, no límite esperado en
uso normal).

### Ampliación: Biblioteca con historial permanente de documentos abiertos

Pregunta del usuario que motivó este cambio: sin vincular una carpeta o
elegir archivos, ¿de verdad no hay forma de que el dispositivo se vea
igual que antes? Confirmado que no (ver §17/§18) -- pero se identificó
que el mecanismo de historial que ya alimenta "Recientes" en Inicio
(`documentHistoryDao`, registra cada apertura real vía
`ViewerViewModel.recordHistoryOpen`, incluyendo aperturas por "Abrir con
DocuSmart" desde otra app) solo se consultaba con un límite acotado para
esa pantalla. Se agregó `DocumentHistoryDao.allEntries()` (historial
completo, sin límite) y `DocumentRepository.loadDocumentsFromHistory()`,
que resuelve cada id del historial a un `DocumentUiModel` real (vía
`ContentResolver` para `content://`, vía `File` para rutas absolutas) y
lo suma a `loadAllDocumentsRaw()`. Efecto práctico: cualquier documento
que el usuario abra alguna vez -- recibido por WhatsApp/Gmail y abierto
con "Abrir con DocuSmart", o elegido con el selector de archivos -- queda
visible en Biblioteca de forma permanente, no solo mientras esté entre
los 5 más recientes de Inicio.

**Corrección relacionada, encontrada en el camino**:
`LibraryViewModel.isDeviceDocument()` clasificaba como "Mis archivos"
(app-generado) cualquier `content://` que no viniera de una lista fija
de prefijos conocidos (`content://media`, `content://com.android`,
`content://downloads`) -- incorrecto para un documento del historial
proveniente de un proveedor de contenido de otra app (WhatsApp, Gmail),
que sí es "del dispositivo". Simplificado a "cualquier `content://` es
del dispositivo" (los documentos que la app genera siempre usan una ruta
absoluta como id, nunca un `content://`), regla más simple y más
correcta que la lista de prefijos.

### Investigado a pedido del usuario, sin cambios de código

- **"En versiones pasadas veía Word/PDF del dispositivo sin vincular nada"**:
  revisado el historial completo de git de `app/build.gradle.kts` --
  `targetSdkVersion` fue 35/36 en TODA la historia del proyecto, nunca
  hubo un `targetSdkVersion` ≤ 28 que hubiera permitido el
  comportamiento de almacenamiento legado. La explicación más plausible
  es que lo que el usuario recuerda son PDFs generados por la propia app
  (herramientas PDF/escáner/conversión, que sí aparecen automático
  siempre) o imágenes (que también son automáticas) -- no documentos
  ajenos, que nunca pudieron verse sin acción explícita en ninguna
  versión de este proyecto.
- **"¿Podemos pedir el permiso de carpetas desde Ajustes del sistema,
  como hacen otras apps?"**: se le explicó que eso es exactamente
  `MANAGE_EXTERNAL_STORAGE` ("Acceso a todos los archivos") visto desde
  otra puerta (aparece en una sección de "Acceso especial" separada de
  la pantalla de permisos estándar que compartió, no es un permiso
  distinto) -- mismo riesgo de política de Play ya evaluado en §17. El
  usuario decidió no perseguirlo y mantener el enfoque solo-SAF.

### Verificado en dispositivo real (Motorola Edge 30 Neo, API 34)

- `detekt` necesitó: 1 `SwallowedException` corregido (pasar la
  excepción real a `Timber.w` al leer el mimeType de un documento del
  historial), subir `TooManyFunctions.thresholdInClasses` de 20 a 26
  (mismo criterio ya documentado), y una entrada de baseline
  `NestedBlockDepth` para `collectLinkedFolderDocuments` (recorrido
  recursivo de árbol, mismo patrón ya baselineado para
  `loadDocumentsFromDownloads`/`loadDocumentsFromLinkedFolder`).
  `shareableUri()` se reescribió para tener 2 returns en vez de 3, sin
  necesitar baseline.
- 2 fakes de `DocumentHistoryDao` en tests (`DocumentRepositoryTest`,
  `TrashRepositoryTest`) actualizados para implementar `allEntries()`.
- `detekt`/`lintDebug`/`testDebugUnitTest` en verde.
- Compartir desde el botón del Visor: verificado sin error, selector del
  sistema con WhatsApp disponible.
- Biblioteca → Mis archivos mostró correctamente `pruebaword.docx`
  (conversión real desde WhatsApp); Dispositivo pasó de 50 a 52 archivos
  tras abrir un documento externo vía "Abrir con DocuSmart", confirmando
  que el historial permanente sí amplía la lista.

### Bug real: conversor PDF→Word pegaba palabras en la misma línea

Investigación pedida por el usuario 2026-09-03 sobre el hallazgo de
`pruebaword.docx` (ver §18): el visor NO tenía el bug -- el `.docx` que
generaba `PdfToWordUseCase` (conversión PDF→Word, RF-CONV-09) sí. Causa
raíz: muchos generadores de PDF (el que produjo el PDF original recibido
por WhatsApp, entre ellos) no codifican el espacio entre palabras como un
carácter `" "` real -- dibujan cada palabra como una operación de texto
(`Tj`) separada y simplemente desplazan el cursor horizontalmente para
crear el hueco visual, sin ningún glifo de por medio.
`TextRenderInfo.getText()` solo devuelve los glifos de CADA operación por
separado, así que `FormattedTextListener`/`buildDocx()` solo insertaban
un espacio cuando detectaban un salto de línea real dentro del mismo
párrafo (`isWrappedLine`) -- nunca cuando dos fragmentos compartían la
MISMA línea con ese hueco horizontal, que es exactamente el caso de
"Funza," + "Cundinamarca," → "Funza,Cundinamarca,".

Corregido: `TextChunk` ahora también registra `xStart`/`xEnd` (extremos
horizontales de la línea base de cada fragmento, vía
`TextRenderInfo.baseline`). `buildDocx()` (refactorizado en
`classifyChunkPlacement()` para no exceder la complejidad ciclomática
permitida) calcula, cuando dos fragmentos comparten línea, el hueco entre
el `xEnd` del anterior y el `xStart` del actual; si supera
`WORD_GAP_MULTIPLIER` (0.2, empírico -- el ancho de un espacio real ronda
0.2-0.3x el tamaño de fuente; un valor menor separaría letras con kerning
normal dentro de una palabra) se antepone un espacio al nuevo fragmento,
igual que ya se hacía para los saltos de línea envueltos.

Test nuevo en `PdfToWordUseCaseTest.kt` que reproduce el caso exacto (dos
`showText()` en la misma Y, con 100pt de separación en X, sin espacio
literal) y verifica que el `.docx` resultante contiene "Funza,
Cundinamarca," con el espacio insertado. `detekt`/`lintDebug`/
`testDebugUnitTest` en verde tras el refactor.

**Verificado en dispositivo real (Motorola Edge 30 Neo, API 34)**:
reconvertido el PDF real recibido por WhatsApp (`VACACIONES ADILA.pdf`,
`Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Documents/`,
53.07 kB) usando el flujo normal de la app (Convertir → PDF → Word,
seleccionando el archivo con el selector del sistema). El `.docx`
resultante se extrajo directamente del almacenamiento de la app
(`run-as com.docsmart`) para inspeccionar el XML crudo: el texto ahora
dice "Funza, Cundinamarca, 03 de septiembres de 2026" con todos los
espacios correctos, en vez de "Funza,Cundinamarca,03deseptiembrede2026"
como antes del fix. Confirmado con el resto del cuerpo de la carta
(fechas, "Señores", "Asunto:", firma) también correctamente espaciado.

---

## 20. Firebase Analytics + Crashlytics: de "declarado pero roto" a funcionando de verdad

El usuario pidió sumar Firebase a la app para aprovechar analítica,
revisión de errores y (a futuro) monetización. Antes de agregar nada
nuevo se investigó qué tanto ya existía -- y resultó que **ya había una
integración de Firebase hecha en una sesión anterior, pero nunca llegó a
funcionar**:

- Ya existía un proyecto Firebase real (`docusmart-8904e`) con
  `google-services.json` en el repo, dependencias de
  `firebase-analytics`/`firebase-crashlytics` en `app/build.gradle.kts`,
  y una clase `DocuSmartAnalytics.kt` con 15 eventos ya escritos.
- **Pero los plugins de Gradle que procesan `google-services.json`**
  (`com.google.gms.google-services`, `com.google.firebase.crashlytics`,
  declarados con `apply false` en el `build.gradle.kts` raíz) **nunca se
  aplicaban** en `app/build.gradle.kts` -- sin ellos, Firebase no tiene
  con qué inicializarse de verdad pese a que el archivo de configuración
  esté presente.
- Los 15 eventos de `DocuSmartAnalytics` tenían **cero call sites** en
  toda la app -- escritos, nunca invocados desde ningún flujo real.
- Crashlytics no tenía ninguna instrumentación (`recordException`,
  `setCustomKey`) y, más grave: `DocuSmartApplication.onCreate()` solo
  plantaba un árbol de Timber (`Timber.DebugTree()`) en builds `DEBUG` --
  en `release` **no había ningún árbol plantado**, así que los cientos de
  `Timber.e`/`Timber.w` ya existentes en el proyecto (muchos agregados
  esta misma sesión al corregir `SwallowedException` de detekt) se
  perdían por completo en producción, sin llegar ni a Logcat ni a
  ningún lado.
- Hallazgo más serio: `docs/requirements/deployment.md` (la declaración
  de "Seguridad de los datos" que se sube a Play Store) **ya afirmaba**
  que la app envía datos de uso y de fallas a Firebase -- una promesa a
  Google que, según el código, nunca se estaba cumpliendo.

Dado el alcance (arreglar lo roto + agregar Test Lab + Remote Config
para monetización sería una sola tanda enorme), el usuario eligió
**arreglar primero Analytics + Crashlytics** y dejar Test Lab/Remote
Config para después.

### Corregido

- **Plugins aplicados** en `app/build.gradle.kts` (`plugins { ... }`):
  `com.google.gms.google-services`, `com.google.firebase.crashlytics`.
- **Meta-data del manifest conectado**: `AndroidManifest.xml` ganó
  `firebase_analytics_collection_deactivated` y
  `firebase_crashlytics_collection_enabled`, ambos apuntando a los
  `manifestPlaceholders` que ya existían en `app/build.gradle.kts`
  (`firebaseAnalyticsDeactivated`/`firebaseCrashlyticsEnabled` -- ya
  estaban definidos por build type, debug=desactivado/release=activado,
  pero nunca se leían desde ningún lado).
- **`CrashlyticsTree.kt`** (nuevo, `core/analytics/`): un `Timber.Tree`
  que reenvía cualquier `Timber.w`/`Timber.e` con una excepción real
  hacia `FirebaseCrashlytics.recordException()` (no fatal, visible en la
  consola sin tumbar la app), y todo `INFO`+ como breadcrumb
  (`Crashlytics.log()`) para dar contexto. Se planta en
  `DocuSmartApplication.onCreate()` solo en builds no-`DEBUG` -- cubre
  automáticamente TODOS los `Timber.e`/`Timber.w` ya existentes en el
  proyecto, sin tener que agregar una llamada manual en cada `catch`.
- **Los 15 eventos de `DocuSmartAnalytics` conectados a flujos reales**:
  - `logScreenView`: centralizado en un único
    `NavController.OnDestinationChangedListener` dentro de
    `DocuSmartNavGraph` -- cubre las ~19 pantallas del grafo sin tocar
    cada `composable {}` una por una. `screenNameForRoute()` (un mapa,
    no un `when` de 19 ramas -- refactor necesario tras el primer intento
    exceder el límite de complejidad ciclomática de detekt) recorta el
    `route` (que conserva placeholders tipo `{documentId}`) al nombre
    base de la pantalla.
  - `logConversion`/`logConversionSuccess`/`logConversionError`:
    `ConverterViewModel.convert()` (ruta de un solo archivo) y
    `runBatchConversion()` (por archivo, en un lote).
  - `logPdfTool`: `PdfToolsViewModel.selectTool()` (solo cuando
    `tool != PdfTool.NONE`, para no contar el reset).
  - `logDocumentOpened`: `ViewerViewModel.publishLoadedDocument()`.
  - `logDocumentFavorited`: `ViewerViewModel.toggleFavorite()` (solo al
    marcar como favorito, no al desmarcar).
  - `logScanCompleted`: `onScanComplete` en `DocuSmartNavGraph`
    (`uris.size` = páginas escaneadas).
  - `logQrScanned`: el listener de éxito de `BarcodeScanning` en
    `QrScreen.kt` (`QrReaderScreen`).
  - `logQrCreated`: el botón "Generar" de `QrCreatorScreen`, con
    `usePassword` real.
  - `logStudySessionStarted`/`logPomodoroCompleted`: `PomodoroEngine`
    (`start()` cuando no es un descanso; `tick()` al cerrar un bloque de
    estudio, con el conteo ya actualizado).
  - `logNoteCreated`: el botón "Guardar nota" de `StudyScreen.kt`
    (no el de eliminar ni el de borrar todas).
  - `logPremiumScreenViewed`: `LaunchedEffect(Unit)` en `PremiumScreen`.
  - `logPremiumPurchaseAttempt`: `PremiumViewModel.purchase()`, con
    `plan.id` ("monthly"/"annual", estable, no localizado).
  - `logError` (el 16° método, genérico) se dejó **sin conectar a
    propósito** -- para "revisar errores" `CrashlyticsTree` ya cubre esto
    mejor (con stack trace real por excepción), conectar además el
    evento de Analytics genérico sería redundante.
- **`deployment.md` actualizado** para reflejar que la declaración de
  Seguridad de los datos ahora sí es cierta, no solo la intención.

### Pendiente para una siguiente sesión (a pedido del usuario, no en esta tanda)

Firebase Test Lab (pruebas en dispositivos reales en la nube, requiere
revisar plan de facturación del proyecto Firebase y una cuenta de
servicio para CI) y Firebase Remote Config/A-B Testing (experimentos de
precio/paywall para monetización) quedan explícitamente fuera de esta
tanda -- el usuario pidió arreglar primero lo que ya se prometía a Play
Store.

### Verificado

- **Gauntlet completo en verde** (`compileDebugKotlin`, `detekt`, `lintDebug`,
  `testDebugUnitTest`, 268 tests) tras dos rondas de fixes reales
  encontrados en el camino:
  - 4 tests de `ConverterViewModelBatchTest` empezaron a fallar con
    `RuntimeException: Method putString in android.os.BaseBundle not
    mocked` -- los tests unitarios JVM puros (sin Robolectric) no pueden
    ejecutar código real de `android.os.Bundle`. Causa raíz: analítica
    nunca debe poder tumbar al que la llama, ni en producción ni en un
    test. Corregido envolviendo el cuerpo de cada evento de
    `DocuSmartAnalytics` en un `safely { }` que atrapa cualquier
    excepción y solo la registra con Timber -- nunca la relanza.
  - `screenNameForRoute()` (el primer intento, un `when` de 19 ramas)
    excedía el límite de complejidad ciclomática de detekt -- refactor a
    un `Map<String, String>` (`SCREEN_NAMES_BY_ROUTE`), más simple de
    paso.
- **`assembleRelease` falló dos veces antes de compilar, ambos bugs reales
  preexistentes, no causados por este cambio** (nunca se había compilado
  un release exitoso con Firebase antes de esta sesión):
  1. R8 fallaba (fatal, no solo warning) por `Missing class
     java.awt.Dimension` y ~20 clases más de `java.awt.image`/
     `java.awt.color`, referenciadas por `org.apache.commons.imaging`
     (transitiva de Apache POI, parsers de formatos de imagen exóticos
     como PCX/RGBE que DocuSmart nunca ejercita) -- mismo límite ya
     documentado para `Rectangle2D`/`Dimension` en el visor de
     PowerPoint (§18), pero esta vez a nivel de R8 en vez de compilación
     Kotlin. Corregido con `-dontwarn java.awt.**` en
     `proguard-rules.pro`.
  2. `uploadCrashlyticsMappingFileRelease` fallaba con
     `groovy/util/XmlSlurper` -- el plugin de Crashlytics 2.9.9 (el que
     ya estaba declarado desde antes) depende de Groovy en el classpath
     de build, que Gradle 9.5 ya no incluye por defecto. Corregido
     subiendo el plugin a 3.0.6 (plugin v3, sin esa dependencia;
     confirmado por búsqueda externa que no usa ninguna de las opciones
     eliminadas en el salto de versión mayor -- `mappingFile`,
     `strippedNativeLibsDir`, `symbolGenerator` -- ninguna presente en
     este proyecto).
- **Verificado en dispositivo real (Motorola Edge 30 Neo) con un build
  `release` firmado real** (`assembleRelease`, instalado tras desinstalar
  el debug previo por firmas distintas) -- necesario porque en `debug`
  `firebaseAnalyticsDeactivated=true` desactiva la recolección a
  propósito:
  - Logcat confirmó inicialización real de Firebase por primera vez en
    la historia del proyecto: `FirebaseApp: ... initializing all
    Firebase APIs`, `FirebaseInitProvider: FirebaseApp initialization
    successful`, `FirebaseCrashlytics: Initializing Firebase Crashlytics
    20.1.0`.
  - Con `adb shell setprop debug.firebase.analytics.app com.docsmart` +
    `adb shell setprop log.tag.FA VERBOSE`: navegando por Inicio →
    Biblioteca → Herramientas PDF, logcat mostró exactamente `Logging
    screen view with name, class: Home, Home`, luego `Library, Library`,
    luego `PdfTools, PdfTools` -- coincide exacto con
    `SCREEN_NAMES_BY_ROUTE`, confirmando que el listener centralizado
    de `logScreenView` funciona de punta a punta.
  - Al tocar "Comprimir PDF" (`PdfToolsViewModel.selectTool()`), logcat
    registró un nuevo `Logging telemetry for logEvent from database`
    inmediatamente después -- el evento personalizado `pdf_tool_used`
    quedó encolado para subir.
  - Confirmada una solicitud HTTPS real desde el proceso de la app hacia
    `firebaselogging-pa.googleapis.com/v1/firelog/legacy/batchlog` (el
    transporte real de Firebase) -- evidencia de red, no solo de logs
    locales.
  - **Hallazgo de testing, no de producto**: el diálogo estándar de
    Android para permiso de fotos (`GrantPermissionsActivity`) no
    respondió a `adb shell input tap`/`touchscreen tap` en ningún
    intento -- sí respondió a `KEYCODE_BACK`. Se resolvió para efectos de
    esta verificación concediendo el permiso directo con `adb shell pm
    grant` en vez de interactuar con el diálogo. No es un bug de
    DocuSmart, es un comportamiento del propio diálogo del sistema en
    este dispositivo/versión de Android ante taps sintéticos.
  - Dispositivo devuelto a un build `debug` normal al terminar (mismo
    proceso: desinstalar + instalar, por la firma distinta).

---

## 21. i18n — 5 idiomas nuevos (ja/ko/zh/it/fr)

Ítem #14 de la tabla de §2, ya catalogado desde antes en `CONTEXT.md` y
`settings-premium.md` pero nunca implementado. La app ya soportaba
es/en/de/pt/ru; el usuario pidió sumar japonés, coreano, chino, italiano
y francés.

**Implementado**: agregadas 5 entradas al enum `AppLanguage`
(`LanguageManager.kt`) -- `JAPANESE("ja", ...)`, `KOREAN("ko", ...)`,
`CHINESE("zh", ...)`, `ITALIAN("it", ...)`, `FRENCH("fr", ...)`. La
pantalla de selección de idioma en Ajustes itera `AppLanguage.entries`
automáticamente, así que no hizo falta tocar ninguna UI aparte.

**Traducción de los 724 strings existentes**: dado el volumen (724
strings × 5 idiomas), se delegó la traducción a 5 agentes en paralelo
(uno por idioma), cada uno con instrucciones estrictas: mismas 724
claves `name` en el mismo orden, preservar especificadores de formato
(`%1$d`/`%2$s`/etc., reordenables si la gramática del idioma lo pide,
pero nunca alterados en tipo/cantidad), preservar los 2
`tools:ignore="StringFormatInvalid"` con un solo `%` literal (no `%%`),
nunca traducir "DocuSmart"/"mouthblack technology", comentarios XML sin
la secuencia `--` (rompe el parser), apóstrofes escapados donde aplica
(crítico en italiano/francés). Cada agente verificó su propio archivo
(conteo de entradas, diff de `name`s contra el fuente, parseo XML,
verificación de especificadores) antes de reportar. Verificación
independiente después: los 5 archivos parsean como XML válido con
exactamente 724 elementos `<string>` cada uno, mismos `name`s en el
mismo orden que el fuente en español.

**Bug de test encontrado y corregido en el camino**:
`LanguageManagerTest.kt` tenía 2 tests que usaban `Locale("ja")`
(japonés) como ejemplo de "idioma NO soportado" -- al agregar japonés
real al enum, esos tests correctamente empezaron a fallar (el
comportamiento del código cambió bien, el supuesto del test quedó
obsoleto). Corregido cambiando el locale de prueba a `Locale("ar")`
(árabe, que sigue sin soporte). Mismo ajuste reflejado en el AC2 de
`settings-premium.md` (que también citaba japonés como ejemplo de
idioma no soportado).

**Bug real encontrado y corregido durante la verificación en dispositivo**:
el diálogo "Seleccionar idioma" (`SettingsScreen.kt`) renderiza su lista
con un `Column` simple dentro del slot `text` de un `AlertDialog`, sin
scroll. Con 5 idiomas cabía todo sin problema; al llegar a 10, el
diálogo (que no crece más allá de un alto máximo fijado por
`AlertDialog`) no alcanzaba para mostrar la última fila (Francés) --
confirmado con volcado de accesibilidad (`uiautomator dump`): el
`RadioButton` de esa fila SÍ se dibujaba, pero su `Text` (nombre e
código del idioma) tenía texto vacío, invisible e imposible de
seleccionar. Corregido agregando
`Modifier.verticalScroll(rememberScrollState())` al `Column` -- ahora
los 10 idiomas son alcanzables con scroll dentro del diálogo, y el
mismo fix cubre cualquier idioma futuro que se agregue.

**Verificado en dispositivo real (Motorola Edge 30 Neo)**: gauntlet
completo en verde (`compileDebugKotlin`, `detekt`, `lintDebug`,
`testDebugUnitTest`, 268 tests) tras el fix. Con scroll, "Français"
queda completamente visible y seleccionable. Se seleccionó 日本語
(japonés) desde el diálogo y toda la UI de Home/Ajustes cambió
correctamente a japonés natural ("ドキュメントを すべてこの一か所に",
"開く", "変換", "クイックアクセス", "スキャン", "設定", etc.), con
"Docu Smart"/"DocuSmart" intactos sin traducir. Confirmado también que
el diálogo de idioma en sí se traduce (título "言語を選択"). Idioma
devuelto a español al terminar.

---

## 22. Monetización: Firebase Remote Config + Firebase Test Lab en CI

Segunda mitad de lo pedido junto con Firebase Analytics/Crashlytics
(§20) -- quedó en cola explícitamente para una siguiente sesión, ahora
retomada a pedido del usuario.

### Remote Config para el paywall de Premium

Antes de esto, qué plan se destaca como "Recomendado" y si se muestra
el badge de ahorro estaban fijos en el código
(`PremiumRepository.getAvailablePlans()`: `isPopular = true` siempre
para el anual, `savingsLabelRes` siempre presente) -- para cambiar
cualquiera de los dos había que publicar una actualización.

**Implementado**:
- `RemoteConfigManager` (nuevo, `core/remoteconfig/`): wrapper de
  `FirebaseRemoteConfig` con 2 parámetros: `premium_annual_highlighted`
  (bool) y `premium_show_savings_badge` (bool). Valores por defecto en
  `res/xml/remote_config_defaults.xml` iguales al comportamiento previo
  (ambos `true`) -- nada cambia hasta que alguien configure algo
  distinto en la consola de Firebase. `minimumFetchIntervalInSeconds`
  en 0 en debug (para poder iterar sin esperar caché), 3600 en release.
- `DocuSmartApplication.onCreate()` llama `remoteConfigManager.refresh()`
  al arrancar -- `fetchAndActivate()` es best-effort, nunca bloquea ni
  rompe la app si falla (sin red, etc.).
- `PremiumRepository.getAvailablePlans()` ahora lee ambos valores y
  arma los planes en base a eso: `isPopular` del mensual/anual se
  intercambia según `isAnnualPlanHighlighted()`, y `savingsLabelRes`
  del anual queda `null` (oculta el badge, `PremiumPlanCards.kt` ya
  manejaba `null` de antes) si `showSavingsBadge()` es falso.
- **Deliberadamente NO se hizo dinámico el precio mostrado** ($2.99/
  $19.99): el cargo real siempre lo determina Play Billing vía
  `queryProductDetails()` (que ya sobreescribe el precio fijo en
  `PremiumViewModel.observePrices()`) -- mostrar un precio distinto por
  Remote Config sin que coincida con lo que Play realmente cobra sería
  engañoso y un riesgo de política, no un ajuste cosmético seguro.
- Test unitario nuevo (`PremiumRepositoryTest.kt`, 3 casos): plan anual
  destacado + badge visible por defecto; Remote Config puede destacar
  el mensual en vez del anual; Remote Config puede ocultar el badge.

**Verificado en dispositivo real**: sin ningún parámetro configurado
todavía en la consola (como está ahora), la pantalla Premium se ve
idéntica a antes -- Anual destacado, badge "Ahorra 44%" visible, sin
errores. Logcat sin ningún error de Remote Config relacionado con las
2 claves nuevas (un warning de una clave interna de Firebase no
relacionada, `useCCJForAutoRestoreEncryption`, es ruido normal del SDK).

**Pendiente del lado del usuario, no se puede hacer desde acá**:
configurar valores reales en Firebase Console → Remote Config
(https://console.firebase.google.com/project/docusmart-8904e/config)
para efectivamente correr un experimento -- por ejemplo, usar la
funcionalidad de A/B Testing de Firebase para repartir tráfico entre
`premium_show_savings_badge=true` y `=false` y medir cuál convierte
mejor en `premium_purchase_attempt` (ya instrumentado, ver §20).

### Firebase Test Lab en CI

`ci.yml` ya tiene un job `instrumented-tests` marcado
`continue-on-error: true` desde 2026-09-02 porque 14-15/30 pruebas de
Compose UI Testing fallan de forma reproducible SOLO en el emulador de
GitHub Actions (`ComposeTimeoutException` al inyectar touch), pese a
pasar de forma confiable en el dispositivo real -- ver
`deployment.md` §3 para el historial completo de intentos de
diagnóstico. Firebase Test Lab corre las mismas pruebas en dispositivos
reales/virtuales de Google en vez de ese emulador, dando una señal de
CI potencialmente más confiable sin depender de tener el dispositivo
físico a mano.

**Implementado**: nuevo workflow
`.github/workflows/firebase-test-lab.yml`. Disparo **manual**
(`workflow_dispatch`) a propósito, no en cada push/PR.

**Corrección importante 2026-09-04**: la primera versión de esta
sección (y del comentario en el propio YAML) decía que Test Lab
requiere el plan de pago **Blaze** -- **investigado y confirmado que
es incorrecto** tras la pregunta del usuario sobre el costo (no puede
costear un plan de pago por ahora). Fuente:
[documentación oficial de cuotas y precios de Test Lab](https://firebase.google.com/docs/test-lab/usage-quotas-pricing).
El plan **Spark** (gratis, sin tarjeta de crédito) sí puede usar Test
Lab, con una cuota diaria real de **hasta 15 pruebas/día en total: 10
en dispositivos virtuales + 5 en físicos**, compartida por todo el
proyecto de GCP. Vincular una cuenta de facturación al proyecto lo
sube automáticamente a Blaze -- para quedarse en el plan gratis basta
con no hacer eso. El disparo manual del workflow se mantiene de todos
modos porque esa cuota de 15/día es compartida con cualquier otro uso
de Test Lab del proyecto (incluida la consola web), no porque haga
falta evitar cargos de un plan de pago.

El job compila `assembleDebug assembleDebugAndroidTest`, autentica con
`google-github-actions/auth` usando una cuenta de servicio, y ejecuta
`gcloud firebase test android run --type instrumentation` contra un
dispositivo **virtual** `MediumPhone.arm` en API 34 (mismo Android que
el dispositivo real de desarrollo) -- elegido a propósito sobre uno
físico porque los virtuales tienen el doble de cuota gratis diaria
(10 vs. 5). El job entero está gateado con
`if: ${{ secrets.GCP_SA_KEY != '' }}` -- se puede fusionar ya mismo sin
romper nada; se activa solo cuando el secret exista.

**Pendiente del lado del usuario, no se puede hacer desde acá** (son
pasos de consola web de Google Cloud, no de código, y **no requieren
tarjeta ni plan de pago**):
1. Crear una cuenta de servicio de Google Cloud (proyecto
   "docusmart-8904e") con el rol "Firebase Test Lab Admin" -- Google
   Cloud Console → IAM y administración → Cuentas de servicio → Crear
   cuenta de servicio.
2. Generar una clave JSON para esa cuenta de servicio (Cuentas de
   servicio → la cuenta creada → Claves → Agregar clave → JSON).
3. Guardar el contenido completo de ese JSON como secret de este
   repositorio en GitHub: Settings → Secrets and variables → Actions →
   New repository secret, nombre `GCP_SA_KEY`.
4. Una vez configurado, correr el workflow manualmente desde la
   pestaña Actions de GitHub ("Firebase Test Lab" → Run workflow) para
   confirmar que funciona de punta a punta.
5. Importante: al crear el proyecto de GCP/cuenta de servicio, NO
   vincular ninguna cuenta de facturación si se quiere permanecer en
   el plan gratis Spark -- eso es lo único que dispararía la subida
   automática a Blaze.

No verificado de punta a punta en esta sesión (no se puede sin el
secret, que solo el usuario puede crear) -- el YAML se validó
sintácticamente (parseable, misma estructura que `ci.yml`/`release.yml`
ya en uso) y las versiones de las 2 GitHub Actions nuevas
(`google-github-actions/auth`, `google-github-actions/setup-gcloud`) se
confirmaron como las últimas disponibles al momento, ancladas por
commit SHA siguiendo la misma convención que el resto de los
workflows del proyecto.
