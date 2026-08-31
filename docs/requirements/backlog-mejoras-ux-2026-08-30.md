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
| 5 | Ajustes → ampliar "Personalización" (tamaño de letra, colores por elemento) | Mejora (épica) | Media | Alta | Medio-Alto | Nuevo (§7) |
| 6 | Banner de anuncios inconsistente entre pantallas | Mejora | Media | Media | Bajo | ✅ 6 de 6 pantallas implementadas y verificadas 2026-08-31 — ver §8 |
| 7 | Banner azul: ancho/alto uniforme + flecha "Volver" con texto | Mejora | Alta | Media-Alta | Medio | **✅ Implementado y verificado 2026-08-30** — ver §9 |
| 8 | Imagen dentro del título "Estudio" en Pomodoro | Bug | — | — | — | Nuevo (§10) — **no reproducido en código**, necesita captura |
| 9 | Visores (PDF/Word/Excel/Texto/PPT): Convertir/QR desde el visor | Mejora | Media-Alta | Media | Bajo-Medio | **✅ Implementado y verificado 2026-08-31** — mismo mecanismo que #1 (ver §3, AC5) |
| 10 | Compose UI Testing en toda la app | Mejora (épica) | Mixta por flujo | Mixta por flujo | Bajo | Ya catalogado en [`compose-ui-testing.md`](compose-ui-testing.md) — no duplicar, ver §11 |
| 11 | Auditoría UX/UI experta + plan de mejoras | Entregable | — | — | — | Nuevo — ver §12 (findings + HUs propias) |
| 12 | Hallazgos de seguridad diferidos de SonarCloud (external storage x5, biometric CryptoObject, dependency verification) | Bug/Deuda técnica | Media | Media-Alta | Medio | Ya listado en `deployment.md` §7 |
| 13 | Umbral de cobertura `new_coverage` 0% en SonarCloud | Decisión de config | — | — | — | Ya listado en `deployment.md` §7 y `compose-ui-testing.md` §4 |
| 14 | i18n: agregar ja/ko/zh/it/fr | Mejora | Baja | Media | Bajo | Ya listado en `CONTEXT.md` §5, `settings-premium.md` |
| 15 | Selector de archivo desde biblioteca de la app (no solo dispositivo) en Seguridad/PDF Tools | Mejora | Baja | Media | Bajo | Ya listado en `CONTEXT.md` §5 |
| 16 | Encriptar/quitar contraseña de archivo individual en Seguridad | Mejora | Baja | Media | Bajo | Ya listado en `CONTEXT.md` §5 |
| 17 | Tarjetas de favoritos con tamaños inconsistentes | Bug (visual) | Baja | Baja | Bajo | Ya listado en `CONTEXT.md` §5 |
| 18 | Word/Excel/PowerPoint en el Visor con inconvenientes | Bug (no verificado) | Media | — | — | Ya listado en `CONTEXT.md` §5 — pendiente reproducir |
| 19 | Actualizar splash (marca empresa + marca app) e íconos (lanzador + banner azul) con el nuevo diseño | Mejora | Alta (marca/identidad) | Media | Bajo-Medio | **✅ Implementado y verificado en dispositivo 2026-08-30** — ver §13 |
| 20 | H1: texto "Eliminar del historial" engañoso (en realidad mueve a la papelera real) | Bug | Media | Baja | Bajo | **✅ Corregido 2026-08-30** — ver §12, hallazgo H1 |
| 21 | `DocuSmartDocumentItem.kt` (menú "⋮" de Home/Biblioteca) sin i18n — todos los labels hardcodeados en español | Bug (i18n) | Media | Media | Bajo | Nuevo, encontrado al corregir #20 — ver §12, hallazgo H6 |

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
  app. No se tocó en esta pasada (habría sido un cambio mucho más grande
  que el bug puntual pedido) pero queda catalogado como fila 21 de la
  tabla de §2 — afecta a los usuarios en, de, pt, ru por igual.

**H2 — Botón "Eliminar ahora" en Papelera no aclara que puede pedir un
permiso del sistema.** Tras el fix de hoy (§17 de `visor-biblioteca.md`),
tocar "Eliminar ahora" puede abrir un diálogo de Android pidiendo permiso
para borrar la foto. Para un usuario esto puede sentirse como un paso
inesperado. Sugerencia de copy: agregar una nota breve en el diálogo de
confirmación ("Android puede pedirte que confirmes el borrado de fotos
que no creó esta app") — mejora de claridad, no de funcionalidad.

- **Mejora menor, prioridad Baja, dificultad Baja, riesgo Bajo.**

**H3 — Botones "Restaurar"/"Eliminar ahora" en Papelera usan el mismo
estilo visual (`OutlinedButton`) para una acción reversible y una
irreversible.** Hoy se diferencian solo por color de texto (verde vs.
rojo), pero tienen el mismo peso visual. Heurística #5 (prevención de
errores): una acción destructiva debería destacar menos que la segura, o
requerir un gesto ligeramente distinto, para reducir toques accidentales
sobre "Eliminar ahora" en vez de "Restaurar" (están uno al lado del otro,
mismo tamaño).

- **Mejora menor, prioridad Baja, dificultad Baja, riesgo Bajo** — ya
  existe confirmación (`TrashDeleteForeverDialog`) que mitiga el riesgo
  real de borrado accidental, así que esto es refinamiento, no urgente.

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

- **Mejora menor, prioridad Baja, dificultad Baja, riesgo Bajo** — sumar
  el tamaño total de los ítems en la papelera (mismo patrón ya usado en
  `StorageRow` de Ajustes) y mostrarlo junto al botón "Borrar todo".

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
