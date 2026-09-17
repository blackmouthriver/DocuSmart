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
| 17 | Tarjetas de favoritos con tamaños inconsistentes | Bug (visual) | Baja | Baja | Bajo | **🟢 Verificado en dispositivo real 2026-09-05 — no reproducible con el código actual** — ver §23 |
| 18 | Word/Excel/PowerPoint en el Visor con inconvenientes | Bug | Media | Media-Alta | Medio | **✅ Reescrito con Apache POI y verificado en dispositivo real 2026-09-03** (incluye fix del bug de espaciado del conversor PDF→Word encontrado en el camino) — ver §18/§19 |
| 19 | Actualizar splash (marca empresa + marca app) e íconos (lanzador + banner azul) con el nuevo diseño | Mejora | Alta (marca/identidad) | Media | Bajo-Medio | **✅ Implementado y verificado en dispositivo 2026-08-30** — ver §13 |
| 20 | H1: texto "Eliminar del historial" engañoso (en realidad mueve a la papelera real) | Bug | Media | Baja | Bajo | **✅ Corregido 2026-08-30** — ver §12, hallazgo H1 |
| 21 | `DocuSmartDocumentItem.kt` (menú "⋮" de Home/Biblioteca) sin i18n — todos los labels hardcodeados en español | Bug (i18n) | Media | Media | Bajo | **✅ Corregido y verificado 2026-09-03** — ver §12, hallazgo H6 |
| 22 | `DocumentRepository.loadPdfsFromDownloads()` no ve PDF/Word/Excel/PowerPoint reales de Descargas sin `owner_package_name` propio (scoped storage); Texto ni siquiera está en el filtro de mimeTypes de esa consulta | Bug | Media-Alta | Media | Medio | **🟡 Corregido lo corregible 2026-09-03 (Texto + permiso falso + API 29-32); la limitación de scoped storage en API 33+ es de la plataforma, sin fix de código posible** — ver §16 |
| 23 | Firebase Analytics/Crashlytics ya declarados a Play Store pero nunca funcionaron (plugin de Gradle sin aplicar, 15 eventos sin conectar, sin árbol de Timber en release) | Bug | Alta | Media | Medio | **✅ Corregido y verificado en dispositivo real (build release firmado) 2026-09-03** — ver §20 |
| 24 | `DocuSmartBottomBar.kt`: pestaña activa sin animación de tamaño/forma/color | Mejora | Media | Media | Bajo | **✅ Implementado y verificado en dispositivo real 2026-09-05** — ver §24 |
| 25 | Miniatura real del archivo (no solo ícono/color por tipo) en Biblioteca, Recientes y Favoritos | Mejora | Media | Alta | Medio | **✅ Implementado y verificado en dispositivo real 2026-09-06 para Imagen y PDF (Word/Excel/PowerPoint quedan con ícono/color, no pedidos en esta pasada). El PDF necesitó un segundo fix: Coil mapea `file://` a `File` antes del Fetcher, así que hacía falta un `Fetcher.Factory<File>` además del de `Uri`** — ver §26 y CONTEXT.md §8 "Bug real: la miniatura de PDF nunca funcionó..." |
| 26 | Fondo animado en toda la app, con el color de acento, y opción de apagarlo | Mejora | Media | Alta | Medio | **✅ Implementado y verificado en dispositivo real 2026-09-06** — ver §25 |
| 27 | Sombra de tarjetas/listas con el color de acento (Material3 `Card` no permite tintar la sombra directamente) | Mejora | Baja | Media | Bajo | **✅ Implementado y verificado en dispositivo real 2026-09-06, acotado a Inicio/Biblioteca/Recientes/Favoritos (pedido explícito del usuario, no las ~30 tarjetas restantes de la app)** — ver §27 |
| 28 | Border con color de acento en las mismas tarjetas/listas, mismo criterio que la barra de navegación | Mejora | Baja | Media | Bajo | **✅ Implementado y verificado en dispositivo real 2026-09-06, mismo alcance que el ítem 27** — ver §27 |
| 29 | Extender sombra + border con color de acento (ítems 27/28) al resto de `Card` de la app (~30 sitios: Convertidor, Herramientas PDF, Seguridad, Escáner/QR, Estudio, Ajustes, Visor) | Mejora | Baja | Media-Alta (son ~30 sitios) | Medio (tocar tantos archivos a la vez sube el riesgo de regresión visual) | **⬜ Pendiente** — alcance descartado a propósito por el usuario en la pasada de los ítems 27/28, queda catalogado para una futura sesión — ver §27 |
| 30 | "Limpiar caché" y "Restablecer configuración" borraban documentos convertidos/procesados de forma permanente, sin pasar por la Papelera y contradiciendo el propio texto del diálogo ("Los documentos no se eliminarán") | Bug (pérdida de datos) | Alta | Baja | Bajo | **✅ Corregido y verificado en dispositivo real 2026-09-06** — ver CONTEXT.md §8, "Bug real: Restablecer configuración y Limpiar caché..." |
| 31 | Miniatura de PDF nunca funcionaba de verdad para archivos generados por la app (Coil mapea `file://` a `File` antes del Fetcher) | Bug | Media | Baja | Bajo | **✅ Corregido y verificado en dispositivo real 2026-09-06** — ver CONTEXT.md §8, "Bug real: la miniatura de PDF nunca funcionó..." |
| 32 | Texto de "Vincular carpeta" en Biblioteca poco claro/con errores de redacción sobre lo que realmente hace | Bug (UX/i18n) | Baja | Baja | Bajo | **✅ Corregido en los 10 idiomas soportados y verificado en dispositivo real 2026-09-06** — ver CONTEXT.md §8, "Texto de Vincular carpeta poco claro" |
| 33 | Escáner: solo generaba PDF; agregar exportar a JPG/WebP y una opción de "alta resolución" para el PDF, exclusiva de Premium | Mejora | Media | Media-Alta | Medio | **🟡 Implementado y verificado en dispositivo real 2026-09-06 (formatos JPG/WebP, candado Premium, PDF estándar sin regresión); la diferencia visual de "alta resolución" activada queda sin verificar por no poder simular una cuenta Premium genuina en este entorno** — ver CONTEXT.md §8, "Escáner: exportar a imagen + alta resolución Premium" |
| 34 | Colores del menú Escáner (documento + QR) no seguían el color de acento; faltaban bordes/sombreado en botones y tarjetas | Bug + Mejora | Media | Media | Bajo | **✅ Corregido y verificado en dispositivo real 2026-09-06** — ver CONTEXT.md §8, "Color de acento en todo el menú Escáner" |
| 35 | Escáner: brillo/contraste solo con slider sin porcentaje; sin opción de escanear otro documento tras guardar/compartir; falta una lista de los archivos escaneados en la sesión; la fila de cada archivo solo tenía "compartir" (sin las opciones de Biblioteca/Recientes); los JPG/WebP de 2+ páginas no quedaban en la misma lista que el PDF | Mejora | Media | Media | Bajo-Medio | **✅ Implementado y verificado en dispositivo real 2026-09-06 (lista de sesión + "Escanear otro documento" tras guardar o compartir; brillo/contraste con `Slider` 0-100; cada fila reutiliza el menú "⋮" completo de Biblioteca/Recientes; PDF y JPG/WebP -- de una o varias páginas -- terminan siempre en la misma lista)** — ver CONTEXT.md §8, "Escáner: chips de brillo/contraste + lista de sesión tras guardar/compartir" y sus dos seguimientos |
| 36 | Bug real: en el Escáner, "Generar" no avanzaba (ni avisaba) al alcanzar el límite diario de conversiones — `ScanResultScreen` no mostraba el `DailyLimitDialog` que sí tienen Convertidor/Herramientas PDF, pese a compartir el mismo `ConverterViewModel.convert()` | Bug | Alta | Baja | Bajo | **✅ Corregido y verificado en dispositivo real 2026-09-06** — ver CONTEXT.md §8, "Seguimiento (mismo día): bug real de Generar + brillo/contraste vuelve a slider" |
| 37 | Bug real: "Escala" (editor de página del Escáner) no mostraba ningún cambio visible al elegir un porcentaje | Bug | Media | Baja | Bajo | **✅ Corregido y verificado en dispositivo real 2026-09-06** — ver CONTEXT.md §8, "Tercer seguimiento (mismo día): bug real de Escala + límite diario de escaneos guardados + ampliar páginas por documento" |
| 38 | Escáner: agregar un límite diario propio de "escaneos guardados" (8/día, independiente del de conversiones) con anuncio/Premium al agotarse; evaluar si se puede ampliar el tope de 10 páginas por documento | Mejora | Media | Media-Alta | Medio | **✅ Implementado y verificado en dispositivo real 2026-09-06 (contador nuevo de 8/día al guardar/compartir; botón "Agregar página" para sumar páginas extra al documento actual, gratis bajo 10 y con anuncio/Premium por cada página adicional, ya que el tope de captura de ML Kit no se puede gatear a mitad de escaneo); el bypass real de Premium en ambos límites queda sin verificar por no poder simular una cuenta Premium genuina en este entorno** — ver CONTEXT.md §8, "Tercer seguimiento" |
| 39 | Bug real (revisión final antes de fusionar): 2 de las 4 ramas de `ScanResultScreen` (antes de generar, y el resultado de lote de imágenes) no tenían "Volver al inicio" — único callejón sin salida real, ya que el Escáner no tiene barra de navegación inferior; de paso se encontró que el "Volver al inicio" ya existente en otra rama tampoco limpiaba `ScanSessionManager`, dejando archivos "fantasma" de sesiones abandonadas | Bug | Alta | Baja | Bajo | **✅ Corregido y verificado en dispositivo real 2026-09-06 (las 4 ramas ya llevan a Inicio, las 3 limpian la sesión por igual)** — ver CONTEXT.md §8, "Revisión final antes de fusionar" |
| 40 | Mejora (revisión final antes de fusionar): la flecha "Volver" vivía integrada dentro del degradado de `DocuSmartTopBanner` (texto/ícono blancos) — pedido explícito del usuario: sacarla del área con color y dejarla debajo | Mejora | Baja | Baja | Bajo (cambio visual en un componente compartido por ~10 pantallas, sin tocar su lógica) | **✅ Implementado y verificado en dispositivo real 2026-09-06 (probado en Seguridad y en el Escáner)** — ver CONTEXT.md §8, "Revisión final antes de fusionar" |

| 41 | Escáner — Filtros de color al escanear (Blanco y negro, Escala de grises, Resaltar texto) | Mejora | Media | Media | Bajo | **✅ Implementado y verificado en dispositivo real 2026-09-14** (commit `692a0c3`) — ver §34.1 |
| 42 | Escáner — Acceso directo a OCR/Firmar/Carpeta Segura desde el resultado del escaneo | Mejora | Media | Baja-Media | Bajo | **✅ Implementado y verificado en dispositivo real 2026-09-14** (commit `5d37ac0`) — ver §34.1 |
| 43 | Creador de QR — Nuevos tipos de contenido (Wi-Fi, Contacto/vCard, Evento de calendario) | Mejora | Media | Media | Bajo | **✅ Implementado y verificado en dispositivo real 2026-09-14** (commit `251290e`) — ver §34.2 |
| 44 | Creador/Lector de QR — Historial de códigos creados y leídos | Mejora | Media | Media | Bajo | **✅ Implementado y verificado en dispositivo real 2026-09-14** (commit `4fcd8c4`) — ver §34.2 |
| 45 | Creador de QR — Diseño personalizado (color/logo en el centro) | Mejora | Baja | Media-Alta | Bajo-Medio | **✅ Implementado y verificado en dispositivo real 2026-09-14/15** (commit `0de5a2c`) — ver §34.2 |
| 46 | Visor — Anotaciones (resaltar texto, notas adhesivas) sobre el PDF | Mejora (épica) | Alta | Alta | Medio-Alto | **✅ Implementado y verificado en dispositivo real 2026-09-15/16** (commit `0c85c0d`) — ver §34.3 |
| 47 | Visor — Marcadores de página | Mejora | Media | Baja-Media | Bajo | **✅ Implementado y verificado en dispositivo real 2026-09-16** — ver §34.3 |
| 48 | Visor — Recordar la última página vista por documento (distinto de "Retomar lectura" en audio, ya existente en Modo Estudio §33) | Mejora | Media | Baja | Bajo | **✅ Implementado y verificado en dispositivo real 2026-09-16** — ver §34.3 |
| 49 | Notas (Modo Estudio) — Adjuntar una imagen o recorte escaneado a una nota | Mejora | Media | Media | Bajo | **✅ Implementado y verificado en dispositivo real 2026-09-17** — ver §34.4 |
| 50 | Notas — Vincular una nota a un documento específico de la Biblioteca | Mejora | Baja | Media | Bajo | **✅ Implementado y verificado en dispositivo real 2026-09-16** — ver §34.4 |
| 51 | Notas — Exportar una nota a PDF/Word (reutilizando el Convertidor) | Mejora | Media | Baja-Media | Bajo | **✅ Implementado y verificado en dispositivo real 2026-09-16** — ver §34.4 |
| 52 | Notas — Recordatorio de repaso (notificación local) | Mejora | Baja | Media | Bajo-Medio | 🆕 Propuesto 2026-09-10 — ver §34.4 |
| 53 | Herramientas PDF — Extraer imágenes embebidas de un PDF | Mejora | Baja | Media | Bajo | **✅ Implementado y verificado en dispositivo real 2026-09-16** — ver §34.5 |
| 54 | Monetización — Prueba gratuita de Premium (trial de 7 días) | Mejora | Alta | Media | Medio | 🆕 Propuesto 2026-09-10 — ver §34.6 |
| 55 | Monetización — Plan anual con descuento | Mejora | Alta | Baja-Media | Bajo | 🆕 Propuesto 2026-09-10 — ver §34.6 |
| 56 | Monetización — Programa de referidos (código de invitación) | Mejora | Media | Alta | Medio-Alto | 🆕 Propuesto 2026-09-10 — ver §34.6 |
| 57 | Monetización — Mediación de AdMob (sumar otra red publicitaria) | Mejora | Media | Media | Bajo-Medio | 🆕 Propuesto 2026-09-10 — ver §34.6 |
| 58 | IA on-device — Traducción de documentos (ML Kit Translation, 100% local) | Mejora | Media | Media | Bajo | 🆕 Propuesto 2026-09-10 — ver §34.7 |
| 59 | IA on-device — Clasificación automática del tipo de documento al escanear | Mejora | Media | Alta | Medio | 🆕 Propuesto 2026-09-10 — ver §34.7 |
| 60 | IA generativa on-device (Gemini Nano/AICore) — Flashcards de estudio desde una nota | Mejora | Media | Alta | Medio-Alto | 🆕 Propuesto 2026-09-10 — requiere decisión de negocio, ver §34.7 |
| 61 | IA en la nube — Chat con el documento (preguntas y respuestas sobre un PDF) | Mejora | Alta | Alta | Alto | 🆕 Propuesto 2026-09-10 — requiere decisión de negocio previa (privacidad + costo), ver §34.7 |
| 62 | IA en la nube — Extracción estructurada de datos de recibos/facturas | Mejora | Media | Alta | Alto | 🆕 Propuesto 2026-09-10 — requiere decisión de negocio previa (privacidad + costo), ver §34.7 |
| 63 | Fondo animado — más movimiento perceptible (refinamiento del ítem 26) | Mejora | Media-Alta | Baja | Bajo | **✅ Implementado y verificado en dispositivo real 2026-09-16** — ver §36 |
| 64 | Modo Estudio — Selector de voz con avatar, nombre y muestra de audio (refinamiento de la lectura por voz) | Mejora | Media | Media | Bajo | **✅ Implementado y verificado en dispositivo real 2026-09-16** — ver §37 |
| 65 | Agenda/Calendario — Guardar eventos, reuniones y entregas con recordatorio (+ vista de calendario, banners y permiso de alarma exacta) | Mejora (épica) | Media-Alta | Alta | Medio | **✅ Implementado y verificado en dispositivo real (Motorola Edge 30 Neo) 2026-09-17 — AC1-AC6, incluye 2 bugs reales encontrados y corregidos (permiso de notificaciones, permiso de alarma exacta)** — ver §38 |
| 66 | Modal de "Idioma" (Ajustes) — rediseño con tarjetas degradadas por idioma, color de acento, hover y transiciones (referencia visual entregada por el usuario) | Mejora (visual) | Media | Media | Bajo | **✅ Implementado y verificado en dispositivo real 2026-09-17** — ver §39 |
| 67 | Modo Estudio — Selector de voz: reemplazar el círculo de color por un personaje/avatar ilustrado por voz (femenino/masculino), referencia visual entregada por el usuario | Mejora (visual) | Media | Media-Alta | Bajo | **✅ Implementado y verificado en dispositivo real 2026-09-17** — ver §40 |

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

## 23. Bug backlog #17 — Tarjetas de favoritos con tamaños inconsistentes

**Investigado y no reproducido en dispositivo real 2026-09-05.**

El código de `FavoriteDocumentCard` (composable privado en
[`FavoritesSection.kt`](../../app/src/main/java/com/docsmart/features/library/presentation/components/FavoritesSection.kt))
ya tiene un tamaño fijo explícito para cada tarjeta:

```kotlin
Card(
    modifier = Modifier
        .width(150.dp)
        .height(160.dp),
    ...
)
```

Esto contradice de entrada el síntoma reportado ("tamaños
inconsistentes"), ya que un `Modifier.width/height` fijo no puede
variar de una tarjeta a otra por código. Se confirmó además que
`FavoritesSection` se usa en un único lugar de toda la app
(`LibraryScreen.kt`), descartando que exista una segunda
implementación de tarjeta de favoritos con otra lógica.

**Prueba en dispositivo real** (Motorola Edge 30 Neo, ZY22G7SB77,
Android 14): se marcaron como favoritos 3 documentos con nombres de
longitud muy distinta (`screen2.png`, nombre corto; `screen1.png`;
`IMG-20260509-WA0012.jpg`, nombre largo que se trunca con "…" en la
lista) para forzar el caso más propenso a causar una diferencia visual
(texto de 1 línea vs. texto que casi llena las 2 líneas permitidas).
Las 3 tarjetas del carrusel horizontal de Favoritos en Biblioteca
renderizaron con **exactamente el mismo tamaño** (150×160dp), sin
ninguna diferencia visible de ancho ni alto.

No se pudo probar con tipos de archivo distintos a "Imagen" (PDF,
Word, Excel) porque la biblioteca de prueba de este dispositivo solo
contiene imágenes (0 documentos PDF/Word registrados) — igualmente,
el tipo de archivo solo afecta el color/label del recuadro interno de
72dp (que tiene `.clip()`), no las dimensiones del `Card` externo, así
que no hay mecanismo en el código actual por el que variaría el
tamaño de la tarjeta.

**Conclusión**: el bug no existe con el código actual. Es probable que
la nota original (agregada en una revisión UX/UI heurística anterior,
sin verificación en dispositivo — ver `CONTEXT.md` línea "ajuste
visual, no funcional, fuera de alcance de esta pasada") describiera un
estado del código anterior a que el `Card` tuviera tamaño fijo, o una
observación que no llegó a confirmarse. Se marca el ítem #17 como
verificado/no reproducible en vez de aplicar un cambio sin un defecto
real que corregir (evitar tocar código sin una causa raíz confirmada).
Si en el futuro se observa el problema de nuevo, lo más útil sería una
captura de pantalla del momento exacto en que ocurre, para comparar
tarjeta por tarjeta.

## 24. Mejora — Animación de tamaño/forma/color en la barra de navegación inferior

El usuario pidió un ajuste visual a `DocuSmartBottomBar.kt`: que la
pestaña activa cambie de tamaño, forma y color con una animación al
tocarla, aportando un diseño de referencia (código Kotlin + captura)
con una pastilla que se eleva del bar con un spring.

**Adaptado, no copiado tal cual**: el diseño de referencia traía
colores fijos (`Color(0xFF2338A8)`, etc.), lo que habría reintroducido
el mismo bug corregido más temprano en la misma sesión en
HomeBanner/DocuSmartTopBanner/PremiumBanner/ScannerScreen/
SecurityScreen (gradientes con azul fijo que ignoraban el "Color de
acento" elegido en Ajustes, HU-UX-06 §7). Se conectó la pastilla activa
a `rememberAccentGradient()` (mismo helper de
`core/ui/theme/AccentGradient.kt`) y el resto de colores a
`MaterialTheme.colorScheme`, manteniendo intacta la lógica real de
navegación (`BottomNavItem`, `bottomNavItems`, `routesWithBottomBar`,
`stringResource()`, `NavRoutes`) para no romper la integración con
`MainActivity.kt`/`NavController` ni el i18n corregido en 2026-08-24
(ver `CONTEXT.md` §"Limpieza de lint + i18n de la barra inferior").

**Primera iteración y feedback del usuario**: la primera versión
elevaba la pastilla activa muy por fuera del bar (`-34dp` de offset) y
reservaba 40dp extra de alto en el `Box` exterior para no recortarla.
El usuario reportó que esto se veía mal: "queda una franja blanca que
tapa la imagen de fondo". Causa: ese hueco extra se rellenaba con un
halo de color plano (`MaterialTheme.colorScheme.background`) que no
siempre coincidía visualmente con lo que había detrás. Pidió además:
que la pastilla se mantenga casi dentro del bar y sobresalga poco (no
que "se eleve por la parte superior"); que si sobresale, se vea el
fondo real y no una franja de color; forma circular fija (no
"pastilla" ovalada); transiciones más suaves, no bruscas; y un bar en
general más grande.

**Corrección aplicada**:
- El `Box` exterior ahora mide exactamente `BarHeight` (sin hueco
  extra reservado a `Scaffold`) — la pastilla activa sobresale por
  *overflow* natural (un `Box` de Compose no recorta a sus hijos sin
  un `.clip()` explícito), así que lo que asoma por encima del bar es
  el contenido real de la pantalla, ligeramente tapado, no un color
  sintético.
- Se eliminó el halo (`background(color = haloColor...)`) por
  completo.
- Forma circular fija: `ItemCorner = ItemBox / 2` siempre, en vez de
  animar entre círculo (inactivo) y cuadrado redondeado (activo).
- Bar más grande: `BarHeight` 84dp → 100dp, `ItemBox` 58dp → 64dp,
  íconos 22/26dp → 24/28dp.
- Elevación más discreta: `LiftOffset` -34dp → -14dp.
- Transiciones más suaves: springs `Spring.DampingRatioMediumBouncy`
  (rebote elástico notorio) → `Spring.DampingRatioLowBouncy`; tweens de
  color/tamaño de 300ms lineales → 420ms con `FastOutSlowInEasing`.

**Verificado en dispositivo real** (Motorola Edge 30 Neo, ZY22G7SB77)
en ambas iteraciones: las 5 pestañas (Inicio/Biblioteca/Convertir/PDF/
Ajustes) elevan, cambian de forma y de color correctamente; probado
cambiando el "Color de acento" en vivo (Azul → Naranja → Morado →
Azul) confirmando que la pastilla lo sigue sin tocar código. Gauntlet
en verde en ambas iteraciones: `compileDebugKotlin` + `detekt` +
`lintDebug` + `testDebugUnitTest`.

**Tercera iteración — feedback del usuario, mismo día (2026-09-06)**:
dos problemas más reportados: (1) los títulos habían desaparecido por
completo (ninguna de las 5 pestañas los mostraba, activa o no); (2) no
se percibía animación/transición al cambiar de pestaña, se sentía
instantáneo. **Causa real del (1)**: el `Row` de ítems tenía un alto
fijo (`BarHeight` = 100dp) y `.navigationBarsPadding()` aplicado
*dentro* de ese mismo alto fijo — en un dispositivo con navegación de 3
botones, el padding real del sistema dejaba muy poco alto disponible
dentro de esos 100dp, y el `Text` del título (último elemento del
`Column`, después del ícono) se quedaba sin espacio. Corregido de raíz:
el `Row` ya no tiene alto fijo (crece según su contenido real +
`.navigationBarsPadding()` + `.padding(vertical = 14.dp)`); el fondo
del bar usa `Modifier.matchParentSize()` dentro de un `Box` sin alto
fijo, ajustándose automáticamente al alto real que el `Row` necesite en
cada dispositivo, sin adivinar un número que pueda no alcanzar. De
paso se aprovechó para otro pedido explícito del usuario: el título
ahora solo se muestra para la pestaña **activa** (antes intentaba
mostrarse en las 5 permanentemente), con `AnimatedVisibility` (fade +
slide, 160ms de demora tras el ícono para que se sienta en dos tiempos
en vez de todo a la vez). Para el (2): springs de
`stiffness = Spring.StiffnessLow` (200f) a `130f` (más lento); tweens
de color/tamaño de 420ms a 480ms; sumado a la demora del título, que
por sí sola ya hace mucho más perceptible la transición al animar dos
elementos en secuencia en vez de uno solo. Verificado en dispositivo
real -- nota: esta vuelta se probó en un **Moto E22 (ZY32HFP5QL)**, no
el Motorola Edge 30 Neo de iteraciones anteriores (el usuario cambió de
dispositivo de prueba) -- en 3 de las 5 pestañas
(Inicio/Convertir/Ajustes): título visible correctamente solo en la
pestaña activa, círculo limpio, sin regresiones. Gauntlet en verde una
vez más.

**Cuarta iteración — el usuario prueba en varios equipos a propósito
(mismo día)**: reportó, con captura, que la barra volvió a quedar muy
alta y el círculo activo sobresalía demasiado (pidió reducirlo a la
mitad); también pidió juntar más la letra del título al ícono,
conservando el fondo sin franjas blancas. Ajustado: `BarVerticalPadding`
14dp → 8dp, `ItemBox` 64dp → 60dp, `LiftOffset` -14dp → -7dp (mitad
exacta), íconos 28/24dp → 26/22dp, espaciador ícono-título 4dp → 1dp.
Verificado en el emulador `sdk_gphone64_x86_64` (el dispositivo físico
de esta ronda no estaba conectado a la máquina en el momento del
ajuste): barra visiblemente más compacta, círculo sobresale poco,
título pegado al ícono, sin franja blanca. Gauntlet en verde una vez
más.

**Quinta iteración — mismo día, tras agregar el fondo animado (ver
§25)**: el usuario reportó, con captura, una franja de color visible
justo encima de la barra al entrar a Convertir y Herramientas PDF, más
la barra todavía grande y el círculo "no sale de la barra" -- pidió
que sobresalga a la mitad (de su propio tamaño, no de la elevación
anterior) y una barra más delgada. Causa real de la franja:
`DocuSmartAnimatedBackground` medía el alto total de pantalla (Box
exterior de `MainActivity`, sin descontar la barra) mientras
`DocuSmartNavGraph` sí respetaba `innerPadding` -- con alturas
distintas, una forma del fondo posicionada en `h - 216dp` caía casi
exactamente sobre el borde superior de la barra. Corregido moviendo el
fondo DENTRO del mismo `Box` con `innerPadding` que ya usa el NavGraph,
así ambos miden la misma altura real. Tamaño: `ItemBox` 60dp → 52dp,
`BarVerticalPadding` 8dp → 6dp, `BarCorner` 26dp → 22dp, íconos 26/22dp
→ 22/18dp, y `LiftOffset = -(ItemBox / 2)` (fórmula explícita en vez de
un valor suelto, para que "la mitad" quede garantizada por
construcción). Verificado en dispositivo real (Motorola Edge 30 Neo,
acento Naranja): franja desaparecida en ambas pantallas, barra más
delgada, círculo sobresaliendo claramente a la mitad. Gauntlet en
verde una vez más. (Nota operativa: durante esta verificación un toque
coincidió con una videollamada entrante real de WhatsApp contestada
por accidente -- se intentó colgar de inmediato, el dispositivo se
desconectó de adb en ese momento, y el usuario confirmó después que
todo quedó bien de su lado; no se leyó ni analizó ese contenido.)

**Sexta iteración — con captura del usuario**: pese al fix anterior,
seguía viéndose una franja en Convertir/PDF y aparecieron dos arcos
negros nuevos en las esquinas superiores de la barra. Diagnóstico
inicial incorrecto: se atribuyó a `elevation = 18dp` en la sombra de
la superficie del bar y se quitó -- no cambió nada; se confirmó con
una prueba de aislamiento (arcos idénticos incluso con el fondo
animado desactivado) que la causa no dependía del fondo animado en
absoluto. **Causa real**: la superficie del bar se recorta con
`RoundedCornerShape` redondeada solo arriba -- las esquinas
triangulares que quedan dentro del `Box` exterior pero fuera de esa
forma no las pinta nada, y con `Scaffold(containerColor =
Color.Transparent)` (necesario para el fondo animado), esas esquinas
dejaban ver el fondo de la ventana de la `Activity`
(`android:windowBackground` = `#0F172A`, azul marino casi negro usado
para el splash). Corregido pintando el `Box` exterior sin recortar con
`MaterialTheme.colorScheme.surface` antes del hijo recortado.
Verificado con capturas recortadas (PowerShell `System.Drawing`) en
Convertir, PDF y Ajustes, con el fondo animado encendido y apagado:
arcos eliminados en los tres casos. Gauntlet en verde una vez más.

**Séptima iteración — con nueva captura del usuario**: sin arcos
negros, pero seguía viéndose una franja/línea blanca en Convertir y
Herramientas PDF, tapando el último botón visible de la lista ("Rotar
PDF", "Imagen → BMP"). **Causa real**: `enableEdgeToEdge()` sin
parámetros usa `SystemBarStyle.auto(...)` por defecto, que en ciertas
versiones/API de Android dibuja un **scrim blanco semitransparente del
propio sistema operativo** en la zona de la barra de navegación, para
mantener legibles los botones/gestos del sistema sobre cualquier
contenido. Con la barra anterior (más alta) ese scrim quedaba oculto
bajo su superficie opaca; al achicarla en esta misma sesión, el scrim
(altura fija, independiente de mi barra) empezó a asomar por encima,
tapando contenido. Corregido especificando
`SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)` para
status bar y navigation bar -- la barra propia ya resuelve su
contraste con `MaterialTheme.colorScheme`. Verificado en dispositivo
real: "Rotar PDF" e "Imagen → BMP" completamente visibles, sin
ninguna franja. Gauntlet en verde una vez más.

**Octava iteración — el fix del scrim no era suficiente**: la franja
seguía tapando el último botón visible; el usuario pidió investigar
por qué Home/Biblioteca no tienen el mismo problema. Comparando su
estructura (sin `Scaffold` propio) contra Convertir/PDF/Seguridad/
Premium/QR/Estudio (cada una con su propio `Scaffold` anidado para el
`snackbarHost`), se encontró la causa real: `Scaffold` por defecto usa
`contentWindowInsets = WindowInsets.systemBars`, así que cada
`Scaffold` anidado reservaba SU PROPIO inset de systemBars además del
que ya reserva, una sola vez, el `Scaffold` principal de `MainActivity`
para `DocuSmartBottomBar` -- un doble descuento que dejaba una franja
de fondo plano extra encima de la barra, solo en pantallas con
`Scaffold` propio. Corregido en los 7 archivos con
`contentWindowInsets = WindowInsets.systemBars.only(
WindowInsetsSides.Top + WindowInsetsSides.Horizontal)` -- excluye solo
el inferior (ya manejado por `MainActivity`), conservando el superior
intacto. Verificado en dispositivo real haciendo scroll hasta el final
absoluto de Convertir y Herramientas PDF: sin ninguna franja en todo
el recorrido, en ambas pantallas. Gauntlet en verde una vez más.

**Novena iteración — mejora estética, no un bug**: con todo corregido,
el usuario pidió que la barra (superficie neutra blanco/gris) también
tome el Color de acento -- se "perdía" contra el fondo animado al
hacer scroll -- y un borde superior más oscuro para separarla, sin
dejar de contrastar con el botón activo, combinando con cualquier
acento. Implementado: degradado de la superficie
`lerp(colorScheme.surface, accent, 0.14f)` →
`lerp(colorScheme.surfaceVariant, accent, 0.24f)` (tinte suave, mucho
menos intenso que la pastilla activa, que usa el acento a toda
intensidad); borde superior `lerp(accent, Color.Black, 0.3f)` con
`Modifier.border()` en vez de una sombra física (evita repetir el
problema de sombras con esquinas mixtas). El color de relleno de
esquinas (fix de "arcos negros") se actualizó para combinar con la
nueva parada superior del degradado. Verificado en dispositivo real
con 3 acentos distintos (Naranja, Turquesa, Azul): la barra toma un
tinte coordinado en los tres casos, con borde separador visible al
hacer scroll, y el botón activo sigue contrastando bien encima.
Gauntlet en verde una vez más.

**Décima iteración — cierre del punto de la barra**: último ajuste
pedido: los íconos inactivos se quedaban en gris neutro
(`onSurfaceVariant`), contrastando poco contra la barra ya teñida.
Corregido con `lerp(colorScheme.onSurfaceVariant, colorScheme.primary,
0.45f)` -- mismo patrón que el tinte de la barra. Verificado en
dispositivo real con acento Morado (claro) y Azul (oscuro, cambio de
tema accidental durante la prueba que sirvió para confirmar que el
diseño también funciona en modo oscuro): íconos con tono coordinado al
acento en ambos casos. Con esto se da por cerrado el punto de la barra
de navegación (petición explícita del usuario). Gauntlet en verde una
vez más.

**Hallazgo colateral — limpieza de datos de prueba**: durante la
verificación en dispositivo, el usuario notó capturas propias
(`screen15.png`...`screen21.png`) mezcladas con sus fotos reales en
Biblioteca/Favoritos. Causa: los `adb shell screencap` de esta y
sesiones anteriores se guardaban directo en `/sdcard/`, que la
pestaña "Dispositivo" de Biblioteca escanea e indexa como documentos
reales. Se encontraron y eliminaron **134 archivos `.png`/`.xml` de
prueba** acumulados de sesiones de QA anteriores (idiomas, editor,
visor, escáner, Pomodoro, seguridad, ajustes, etc.), y se desmarcó
(sin borrar) una foto real de WhatsApp del usuario que había quedado
favorita por error durante esta prueba. Corrección de proceso: los
screenshots de ahora en adelante se guardan en `/data/local/tmp/`
(no escaneado por la app), nunca directo en `/sdcard/`.

**Pendiente para después (#25 en la tabla)**: el usuario pidió,
explícitamente para más adelante y no en esta sesión, mostrar una
miniatura real del archivo (portada del PDF, primera diapositiva,
contenido de la imagen, etc.) en vez de solo el ícono/color por tipo,
en Biblioteca, Recientes, Convertir y en general donde se listan
documentos.

## 25. Mejora — Fondo animado en toda la app, con el color de acento

El usuario aportó un diseño de referencia (código Kotlin) para un fondo
animado con formas geométricas con degradado que derivan lentamente,
pidiendo que se agregue a la app, que tenga un aspecto parecido a los
banners (por defecto azules) y que las formas se muevan (no se vean
estáticas). Antes de implementar se le hicieron 3 preguntas de diseño
(alcance, personalización, interruptor on/off), respondidas así:
**toda la aplicación**, siguiendo el **"Color de acento" existente**
(no un selector aparte), con un **interruptor para apagarlo** en
Ajustes.

**Adaptado, no copiado tal cual** — igual que con la barra de
navegación (ver §24), el diseño de referencia traía 3 paletas de color
fijas (índigo/violeta/turquesa) sin relación con el acento elegido, lo
que habría repetido el mismo bug ya corregido esta sesión. Se conectó
a `rememberAccentGradient()` (el mismo helper que ya usan
HomeBanner/DocuSmartTopBanner/etc.): las 4 formas geométricas usan
pares de colores tomados de esa lista de 3 tonos (claro/acento/oscuro),
así que cambian junto con el "Color de acento" elegido, sin agregar
una opción de personalización aparte. También se usó
`MaterialTheme.colorScheme.background` real (respeta claro/oscuro/
sistema) en vez del `0xFFEEF2FF` fijo del diseño de referencia.

**Alcance real ("toda la app") con una excepción deliberada**: el
fondo se pinta una única vez en `MainActivity.kt` (capa 0, detrás de
`DocuSmartNavGraph`), y los 7 `Scaffold` anidados de pantallas propias
(`StudyScreen`, `SecurityScreen`, `PremiumScreen`, `QrScreen` ×2,
`PdfToolsScreen`, `PdfPasswordScreen`, `ConverterScreen`) más
`ScanResultScreen` cambiaron su `containerColor`/fondo de
`MaterialTheme.colorScheme.background` a `Color.Transparent` para
dejarlo ver. **Excepción**: `ViewerScreen.kt` (la superficie real de
lectura de PDF/Word/Excel/PowerPoint) se dejó con fondo sólido fijo a
propósito -- un fondo animado detrás de un documento que se está
leyendo perjudicaría la legibilidad, no es solo estético (decisión de
producto, no consultada explícitamente pero documentada acá para que
el usuario la revise si no está de acuerdo).

**Interruptor**: `ThemeManager.animatedBackgroundEnabled`
(`StateFlow<Boolean>`, persistido en `SharedPreferences`, default
`true`), con `setAnimatedBackgroundEnabled()`. Nueva fila
"Fondo animado" en Ajustes → Personalización (`SettingsSwitchItem`,
mismo estilo visual que las demás filas pero con `Switch` en vez de
chevron+diálogo). String i18n agregado a los 10 idiomas soportados
(`settings_animated_background`/`_subtitle`).

**Accesibilidad**: si el sistema tiene "Quitar animaciones" activado
(`Settings.Global.ANIMATOR_DURATION_SCALE == 0`), las formas se pintan
quietas en vez de animar -- ver `DocuSmartAnimatedBackground.kt`.
También se redujo de 5 a 4 capas respecto al diseño de referencia
(se quitó el contorno que giraba en cada frame, la más costosa según
la propia nota del diseño original) para aligerar el costo de
renderizado al aplicarse en toda la app, no solo en una pantalla.

**Bug real encontrado y corregido en el camino**: `ThemeManagerTest`
(4 tests) empezó a fallar con `MockKException` al agregar
`animatedBackgroundEnabled` -- el fake de `SharedPreferences`
compartido (`FakeAndroidPrefs.kt`) solo mockeaba
`getString`/`putString`/`getLong`/`putLong`, no
`getBoolean`/`putBoolean`. Corregido extendiendo el fake con el mismo
patrón (`slot` + `answers` respaldado por el mapa en memoria).

**Verificado en dispositivo real** (Motorola Edge 30 Neo, ZY22G7SB77,
acento Rosa): fondo animado visible en Inicio, Biblioteca y Estudio/
Pomodoro sin afectar la legibilidad (las tarjetas siguen opacas);
interruptor probado apagando y reactivando, cae a fondo sólido limpio
y vuelve a animar correctamente sin reiniciar la app. Gauntlet en
verde: `compileDebugKotlin` + `detekt` + `lintDebug` +
`testDebugUnitTest`.

## 26. Mejora — Miniatura real del documento en Biblioteca/Favoritos/Recientes (ítem #25)

El usuario, revisando la app pantalla por pantalla, pidió retomar el
ítem #25 (anotado "para después" en §24/§25): mostrar una miniatura
real del contenido del archivo en vez de solo el recuadro genérico de
tipo ("Imagen", "PDF", etc.) en Biblioteca, Favoritos y Recientes de
Inicio.

**Investigación previa** (agente de exploración, solo lectura): reveló
que Biblioteca (`DocumentListSection.kt`) y Recientes de Home
(`RecentDocuments.kt`) ya comparten un único composable
(`DocuSmartDocumentItem`, en
`core/ui/components/DocuSmartDocumentItem.kt`) -- un cambio ahí cubre
ambas pantallas de una vez. Solo `FavoriteDocumentCard`
(`FavoritesSection.kt`) duplica el mismo patrón por separado. Coil
2.7.0 ya estaba en el proyecto (usado en Convertidor/ScanResultScreen)
pero sin decodificador de PDF -- Android no tiene uno nativo para ese
formato, solo `PdfRenderer` (entrega un `Bitmap` ya compuesto, no un
stream de bytes de imagen que Coil pueda decodificar directamente).

**Implementado**:
- `DocumentThumbnail` (nuevo composable compartido, mismo archivo que
  `DocuSmartDocumentItem`): usa `SubcomposeAsyncImage` con
  `document.toContentUri()` como modelo para tipo `IMAGE`/`PDF`, con
  el ícono/color de siempre como estado de carga y de error (fallback
  automático si la miniatura no carga). El resto de tipos
  (Word/Excel/PowerPoint/Texto/ZIP/OCR) siguen mostrando solo el
  ícono/color -- renderizarlos a imagen requiere mucho más trabajo
  (convertir el documento primero) y no fue pedido en esta pasada.
- `PdfThumbnailFetcher` (nuevo, `core/media/PdfThumbnailFetcher.kt`):
  `Fetcher`/`Fetcher.Factory` de Coil que usa `PdfRenderer` para
  renderizar la primera página del PDF a un `Bitmap` de 300px de
  ancho. Se activa automáticamente para cualquier Uri cuyo MIME type
  o extensión sea PDF.
- `DocuSmartApplication` ahora implementa `ImageLoaderFactory` de
  Coil, registrando el Fetcher una sola vez a nivel de app -- así
  `AsyncImage`/`SubcomposeAsyncImage` funcionan igual para imágenes y
  PDFs en cualquier pantalla futura, sin pasar un `ImageLoader` propio
  en cada uso.
- `DocuSmartDocumentItem` (filas de Biblioteca/Recientes) y
  `FavoriteDocumentCard` (tarjetas de Favoritos) ahora usan
  `DocumentThumbnail` en vez de duplicar el recuadro de ícono/color a
  mano.

**Verificado en dispositivo real**: miniaturas reales visibles en las
tres pantallas pedidas (Recientes en Inicio, lista completa de
Biblioteca, tarjetas de Favoritos), con fotos reales de WhatsApp del
dispositivo de prueba. **No se pudo verificar visualmente la
miniatura de PDF** -- la biblioteca de este dispositivo no tiene
ningún PDF real, y un intento de generar uno con el propio
Convertidor de la app (Imagen → PDF) no completó el flujo en varios
intentos (se abandonó el intento por no ser el objetivo central del
cambio). Queda documentado como limitación de esta verificación, no
como comportamiento confirmado -- el código usa `PdfRenderer` de
forma directa (API estándar de Android desde API 21), pero una
verificación visual real con un PDF de verdad queda pendiente para
una próxima sesión. Gauntlet en verde:
`compileDebugKotlin` + `detekt` + `lintDebug` + `testDebugUnitTest`.

**Pendiente para después**: miniaturas para Word/Excel/PowerPoint
(requiere renderizar el documento a imagen primero, no solo leer el
tipo) -- no implementado, no pedido en esta pasada.

## 27. Mejora — Sombra de tarjetas/listas con el color de acento

El usuario pidió, el mismo día tras las miniaturas, extender el "Color
de acento" también a las sombras de las tarjetas y listas.

**Investigación previa** (agente de exploración, solo lectura):
confirmó que Material3 `Card` en la versión resuelta del proyecto
(`material3-android:1.3.1`, vía `compose-bom = "2024.11.00"`) **no
expone** `ambientColor`/`spotColor` en `CardDefaults.cardElevation()`
ni en la firma de `Card()` -- no hay forma de tintar la sombra sin
reemplazar el `Card` por un contenedor propio con
`Modifier.shadow(elevation, shape, ambientColor, spotColor)` manual,
el mismo patrón ya usado en `DocuSmartBottomBar.kt` para el círculo de
la pestaña activa. El mismo agente encontró **~34 sitios** con
`Card(... elevation = CardDefaults.cardElevation(...) > 0.dp)`
repartidos por casi toda la app (Inicio, Biblioteca, Convertidor,
Herramientas PDF, Seguridad, Escáner/QR, Estudio, Ajustes, Visor).

Dado el tamaño real del cambio, se le preguntó al usuario el alcance
antes de tocar código -- eligió acotarlo a las tarjetas ya trabajadas
esta sesión (accesos rápidos de Inicio, filas de documentos en
Biblioteca/Recientes, tarjetas de Favoritos), dejando el resto de la
app (~30 sitios más) con la sombra neutra de Material3 por ahora, para
minimizar riesgo de que algo se vea mal en una pantalla no revisada a
fondo en esta pasada.

**Implementado**: `Modifier.accentShadow(shape, elevation, alpha)`
(nueva extensión, `core/ui/theme/AccentGradient.kt`, mismo archivo que
`rememberAccentGradient()`) -- sombra tenue (`alpha` default 0.35f,
bastante menos intensa que la pastilla de la barra de navegación, que
usa 0.55f, para no saturar visualmente cuando se aplica a muchas
tarjetas pequeñas en una lista) tintada con `colorScheme.primary`.
Aplicado reemplazando `Card()` por `Box`/`Column` +
`.accentShadow().clip().background()` en:
- `DocuSmartQuickAccessCard` (`core/ui/components/cards/DocuSmartCards.kt`)
  -- accesos rápidos de Inicio.
- La tarjeta que envuelve la lista completa de `DocuSmartDocumentItem`
  en `DocumentListSection.kt` (Biblioteca) y `RecentDocuments.kt`
  (Recientes de Inicio) -- se reemplazó por un `Column` simple (el
  `content` de `Card` ya asumía `ColumnScope`, así que el layout
  interno no cambió).
- `FavoriteDocumentCard` en `FavoritesSection.kt`.

**Verificado en dispositivo real**: en modo oscuro la sombra tintada
es sutil (esperable, poco contraste contra un fondo ya oscuro); en
modo claro, con capturas ampliadas (PowerShell `System.Drawing`), se
confirmó un tinte turquesa/acento claramente visible alrededor de las
tarjetas de accesos rápidos de Inicio, de la tarjeta de la lista de
Recientes y de la lista completa de Biblioteca. Gauntlet en verde:
`compileDebugKotlin` + `detekt` + `lintDebug` + `testDebugUnitTest`.

**Pendiente para después**: extender el mismo tratamiento al resto de
`Card` de la app (~30 sitios en Convertidor/Herramientas PDF/
Seguridad/Escáner/Estudio/Ajustes/Visor) -- no implementado, alcance
descartado a propósito por el usuario en esta pasada.

### Ítem 28: border con color de acento en las mismas tarjetas/listas

Mismo día, antes de fusionar el cambio de sombras de arriba, el
usuario pidió agregar también un borde a esas mismas tarjetas/listas,
con el mismo criterio que ya usa `DocuSmartBottomBar.kt` para el borde
superior de la barra (`lerp(accent, Color.Black, 0.3f).copy(alpha =
0.4f)`), y que reaccione también al color de acento elegido.

**Implementado**: `Modifier.accentBorder(shape, width, darken, alpha)`
(nueva extensión, `core/ui/theme/AccentGradient.kt`, junto a
`accentShadow()`), usando `Modifier.border()` con el color ya derivado
del acento. Aplicado en las mismas 4 ubicaciones que ya tenían
`accentShadow()` (mismo alcance, no las ~30 tarjetas restantes de la
app): `DocuSmartQuickAccessCard`, la tarjeta de la lista de documentos
en `DocumentListSection.kt` (Biblioteca) y `RecentDocuments.kt`
(Recientes), y `FavoriteDocumentCard` en `FavoritesSection.kt`.

**Verificado en dispositivo real** (acento turquesa activo, capturas
ampliadas con PowerShell `System.Drawing`): borde visible y coherente
con el acento en los accesos rápidos de Inicio, en la lista de
Recientes, en la lista completa de Biblioteca y en la tarjeta de
Favoritos (para esta última se marcó temporalmente un documento como
favorito, solo para forzar la aparición de la sección "Favoritos" en
Biblioteca, y se desmarcó de inmediato tras la captura, sin dejar
cambios residuales en los datos del usuario). Gauntlet en verde:
`compileDebugKotlin` + `detekt` + `lintDebug` + `testDebugUnitTest`.

## 29. Mejora — Consistencia de banners entre pantallas + botones fuera del banner de Inicio

Tras revisar 7 capturas (Inicio, Biblioteca, Convertidor, Herramientas
PDF, Ajustes, Seguridad, Documento escaneado), el usuario señaló que el
banner superior de cada pantalla se veía con tamaño y separación
distintos entre sí, y pidió: (1) sacar los botones "Abrir"/"Convertir"
del banner de Inicio, (2) unificar los márgenes izquierdo/derecho del
banner, más pegados al borde del dispositivo, (3) reducir el espacio
entre el banner de anuncios y el banner de título ("levemente pegado"),
y (4) subir el banner de anuncios (menos padding superior).

**Causa raíz**: cada pantalla había ido acumulando su propio criterio de
márgenes (20dp por-item en Inicio/Biblioteca/Convertidor/Herramientas
PDF vs. 16-24dp a nivel de contenedor en Ajustes/Seguridad/Documento
escaneado), de `contentPadding` superior (0/20dp/24dp) y de espacio
entre el banner de anuncios y el de título (según el
`verticalArrangement.spacedBy` de cada pantalla, sin un valor pensado
específicamente para esa relación) — Ajustes y Seguridad además sumaban
un `padding(vertical = 24dp)` extra solo alrededor del banner de
anuncios.

**Implementado**:
- Nuevo componente compartido `DocuSmartScreenHeader`
  (`core/ui/components/DocuSmartScreenHeader.kt`) para las 4 pantallas
  con padding por-item (Inicio, Biblioteca, Convertidor, Herramientas
  PDF): agrupa banner de anuncios + 8dp fijo + banner de título en un
  `Column` con 16dp de margen horizontal y sin padding superior.
- Para Ajustes, Seguridad y Documento escaneado (padding horizontal a
  nivel de contenedor, donde el componente compartido duplicaría el
  margen) se aplicó el mismo criterio a mano: un `Column` propio con
  banner de anuncios + `Spacer(8.dp)` + banner de título como un solo
  hijo/ítem, para que el `spacedBy` de cada pantalla no sume espacio
  extra entre ambos.
- `contentPadding`/`padding` superior quitado en las 7 pantallas.
- Inicio (`HomeBanner.kt`): botones "Abrir"/"Convertir" sacados de
  dentro del banner (ver corrección más abajo).

**Verificado en dispositivo real** (Motorola Edge 30 Neo, ZY22G7SB77,
reinstalación limpia): las 7 pantallas con el mismo margen horizontal,
mismo espacio pequeño entre banner de anuncios y banner de título, y
banner de anuncios pegado arriba — confirmado con capturas de las 6
pantallas de navegación directa y de Documento escaneado al final de un
flujo completo de escaneo con el Escáner de Google ML Kit. Gauntlet en
verde: `compileDebugKotlin` + `detekt` + `lintDebug` +
`testDebugUnitTest`.

### Seguimiento (mismo día): corrección de 2 observaciones

Al revisar el resultado, el usuario aclaró que el pedido sobre Inicio
era sacar los botones del banner (dejarlos fuera, debajo), no
eliminarlos — y señaló que Convertidor y Herramientas PDF seguían con
más espacio arriba del banner que Inicio/Biblioteca/Ajustes.

**Corregido — botones de Inicio**: revertidas las 10 cadenas
`home_open`/`home_convert` y restaurada la fila de botones en
`HomeBanner.kt`, ahora como un `Column` propio fuera del `Box`
degradado (banner arriba, 16dp de espacio, fila de botones debajo) —
mismo patrón ya usado con la flecha "Volver" de `DocuSmartTopBanner`.
Como el fondo debajo del banner ya no es el degradado sino el normal de
la pantalla, los botones pasan de blanco/blanco al color de acento.

**Corregido — margen superior de Convertidor/Herramientas PDF**: causa
raíz era que estas 2 pantallas (únicas de las 7 con `Scaffold` propio,
para su `SnackbarHost`) reservaban el inset superior de `systemBars`
(`WindowInsetsSides.Top`) por duplicado — el `Scaffold` de
`MainActivity.kt` ya reserva ese mismo inset una vez para toda la
navegación, y estas 2 pantallas lo volvían a reservar en su propio
`Scaffold`, sumando una barra de estado extra de espacio arriba que las
otras 5 pantallas (sin `Scaffold` propio) nunca tuvieron. Mismo patrón
de bug ya conocido para el inset inferior (línea blanca sobre
`DocuSmartBottomBar`), pero nadie había notado que también afectaba al
superior. Corregido cambiando `contentWindowInsets` de
`WindowInsets.systemBars.only(Top + Horizontal)` a
`WindowInsets.systemBars.only(Horizontal)` en ambos archivos.

**Verificado en dispositivo real** (Moto E22, ZY32HFP5QL): tras
`assembleDebug` + reinstalación limpia, Inicio muestra los botones
como fila debajo del banner en color de acento, y Convertidor/
Herramientas PDF arrancan el banner exactamente a la misma altura que
Inicio, sin el hueco extra. Gauntlet en verde: `compileDebugKotlin` +
`detekt` + `lintDebug` + `testDebugUnitTest`.

**Fusionado y subido a `main`** con aprobación explícita del usuario
(commit `c77e3e5`).

## 30. Bug real — usuarios Premium reales podían seguir viendo anuncios toda la sesión

Tras fusionar el ítem 29, el usuario pidió verificar que Premium oculte
el banner de anuncios (sin dejar hueco) y también los anuncios de
video/intersticiales. Auditoría de solo lectura sobre `AdManager`/
`PremiumManager`/`BillingManager`.

**Lo que ya estaba bien**: las 7 pantallas condicionan banner+`Spacer`
al mismo `if (!isPremium)` (banner de título sube sin hueco);
`DocuSmartBannerAd` tiene además su propio `return` temprano si
`isPremium`; los 3 disparadores de rewarded (límite de conversión, de
herramienta PDF, de "agregar página") y el único disparador de
intersticial (`onConversionCompleted`) ya estaban condicionados a
`isPremium` antes de ofrecer la UI de "ver anuncio".

**Bug real encontrado**: `AdManager._isPremium` (lo que leen las 7
pantallas y lo que bloquea intersticial/rewarded) arranca en `false` y
solo se sincroniza vía `PremiumManager.activatePremium()` -- pero
`PremiumManager` (el único que lee el estado persistido en
`SharedPreferences` al construirse) solo se instanciaba de forma
perezosa al abrir la pantalla Premium (única que lo inyecta). Un
suscriptor Premium real que no visitara esa pantalla en una sesión
seguía viendo anuncios (banner + elegible para intersticial/rewarded)
toda esa sesión, pese a tener el estado correcto ya guardado en disco.

**Corregido**:
- `PremiumManager.kt`: `init { adManager.setPremium(_isPremium.value) }`
  -- sincroniza `AdManager` apenas se construye `PremiumManager`.
- `DocuSmartApplication.kt`: se inyecta `PremiumManager` (mismo patrón
  que `remoteConfigManager`) para forzar esa construcción desde el
  arranque de la app, sin crear dependencia circular con `AdManager`.
- `AdManager.showRewardedAd()`: agregado el guard `if (_isPremium.value) return`
  que ya tenían el resto de las funciones de anuncios (antes solo
  quedaba a salvo indirectamente vía `setPremium(true)` anulando el
  `rewardedAd` cacheado).

**Verificado en dispositivo real** (Moto E22, ZY32HFP5QL): simulado un
suscriptor Premium real sin abrir nunca la pantalla Premium (inyectando
`is_premium=true` en `shared_prefs/docusmart_premium.xml` vía
`adb shell run-as` + reinicio limpio de la app). Confirmado que Inicio
y Convertidor arrancan sin banner de anuncios y con el banner de título
ya arriba, sin hueco. Estado de prueba limpiado con `pm clear`. Gauntlet
en verde: `compileDebugKotlin` + `detekt` + `lintDebug` +
`testDebugUnitTest`.

### Seguimiento (mismo día): rediseño del contenido del banner en todas las pantallas

Confirmado el fix de Premium, el usuario pidió 4 ajustes más sobre el
banner antes de fusionar: espacio pequeño arriba (no pegado al borde),
logo+"DocuSmart" a nivel (misma fila) quitando texto repetido, título
centrado debajo de esa fila (no al lado), y subtítulo justificado.

**Implementado**:
- `DocuSmartScreenHeader.kt` y los 3 patrones manuales (Ajustes/
  Seguridad/Documento escaneado): margen superior de 12dp (antes 0dp).
- `DocuSmartTopBanner.kt` (9 pantallas): reestructurado a fila compacta
  logo+marca arriba, título centrado a todo el ancho y subtítulo
  justificado a todo el ancho debajo (ya no comparten fila con el logo).
- `HomeBanner.kt`: eliminado el texto "DocuSmart" repetido
  (`R.string.app_name`) debajo de "Docu"+"Smart"; título centrado y
  subtítulo justificado.

**Nota para el usuario**: el "justificado" no se nota visualmente en la
mayoría de las pantallas porque esos subtítulos usan saltos de línea
manuales (`\n`) en vez de ajuste automático -- la justificación de
Android solo estira el espacio entre palabras en líneas cortadas por
ancho, no en líneas ya cortadas a mano. El código aplica
`TextAlign.Justify` tal como se pidió; si se quiere ver el efecto,
haría falta además quitar esos saltos manuales -- pendiente de
confirmación del usuario.

**Verificado en dispositivo real** (Moto E22, ZY32HFP5QL): Inicio,
Convertidor, Ajustes y Seguridad -- logo+"DocuSmart" a nivel, sin texto
repetido, título centrado, espacio pequeño arriba, "Volver" de
Seguridad intacto. Gauntlet en verde: `compileDebugKotlin` + `detekt` +
`lintDebug` + `testDebugUnitTest`.

### Segundo seguimiento (mismo día): subtítulo centrado en vez de justificado

El usuario pidió quitar los saltos de línea manuales del subtítulo y
confirmar que se viera justificado, pero en el mismo mensaje también
pidió que ese texto quedara centrado -- dos alineaciones que no pueden
coexistir. Al preguntar, eligió **centrado** (igual que el título).

**Implementado**: quitado el salto de línea manual (`\n`) de
`home_subtitle` en los 10 idiomas (único subtítulo del banner que lo
tenía); `HomeBanner.kt`/`DocuSmartTopBanner.kt` cambian el subtítulo de
`TextAlign.Justify` a `TextAlign.Center`.

**Verificado en dispositivo real** (Moto E22, ZY32HFP5QL): en Inicio el
subtítulo ya es una sola línea centrada; en Convertidor, igual,
centrado como el título. Gauntlet en verde: `compileDebugKotlin` +
`detekt` + `lintDebug` + `testDebugUnitTest`.

**Fusionado y subido a `main`** con aprobación explícita del usuario
(commit `e37a3a8`).

## 31. Modo Estudio — Lectura solo PDF, "Leer todo" corregido, PDF visible al leer, acento y contraste

El usuario pidió arreglar Lectura antes de construir una nueva función
de resumen automático (pendiente, ver abajo): solo PDF (Word daba
problemas), arreglar "Leer todo" (no funcionaba), mostrar el PDF real
mientras la voz lee (no los párrafos extraídos), color de acento en
Lectura/Notas/Pomodoro, y contraste en el encabezado y las pestañas.

**Bug real — "Leer todo"**: unía todos los párrafos en un solo string y
hacía una única llamada a `TextToSpeech.speak()` -- Android limita cada
llamada a ~4000 caracteres, así que con cualquier documento largo (PDF
real de 55 páginas usado para probar) fallaba en silencio. Corregido
encolando un párrafo por llamada (`QUEUE_FLUSH` + `QUEUE_ADD`).

**Implementado**:
- Selector de archivo restringido a `application/pdf`; eliminado todo
  el código de extracción de Word/PPT/texto plano y sus tests, ya sin
  uso.
- `core/pdf/PdfPageBitmap.kt` (nuevo): renderizador de páginas a bitmap
  extraído de `ViewerScreen.kt` al necesitarse también en Estudio --
  ahora compartido entre el Visor y Estudio.
- `StudyPdfViewer` (nuevo): Lectura muestra el PDF real (con zoom/pan)
  en vez de la lista de párrafos -- el texto extraído sigue existiendo
  por debajo solo para alimentar la voz. "Marcar" pasa a resaltar el
  párrafo que se está leyendo en ese momento (ya no hay filas de
  párrafo que tocar), sigue alimentando "Párrafos resaltados" en Notas.
- Color de acento: `DocuBlue` (fijo) en usos de marca/estado activo
  (párrafo en lectura, Pomodoro "en foco") pasa al acento elegido;
  `WarningAmber`/`SuccessGreen` (resaltado / "en descanso") quedan
  fijos por su significado, mismo criterio que error/éxito/advertencia
  en el resto de la app.
- Encabezado: reemplazado el `TopAppBar` plano (única pantalla sin el
  banner degradado) por el `DocuSmartTopBanner` compartido -- de paso
  se corrigió el mismo bug de inset superior duplicado ya arreglado en
  Convertidor/Herramientas PDF.

**Verificado en dispositivo real** (Motorola Edge 30 Neo, ZY22G7SB77)
con un PDF real de 55 páginas: selector de archivos con Word/Excel/
PowerPoint deshabilitados; PDF real visible (portada/contraportada, no
párrafos); "Leer todo" funciona de verdad con este mismo archivo que
antes fallaba en silencio; marcar mientras lee aparece en Notas;
Pomodoro y encabezado con el color de acento y contraste correctos.
Gauntlet en verde: `compileDebugKotlin` + `detekt` + `lintDebug` +
`testDebugUnitTest`.

**Pendiente, a pedido explícito del usuario**: función de resumen
automático del PDF -- elegido 100% local (extracción de frases clave,
sin nube) para no romper la promesa de la política de privacidad ni
sumar costo por uso. No implementada todavía.

### Seguimiento (mismo día): anuncio arriba, íconos fuera del degradado, pestañas con acento

Confirmado el rediseño anterior, 3 ajustes más antes de fusionar: subir
el anuncio (quedaba debajo de las pestañas) a la parte superior, sacar
los íconos de abrir documento/estadísticas de dentro del degradado
azul y ponerlos a la altura de "Volver", y que las pestañas no queden
blancas planas sino también afectadas por el acento (cuidando el
contraste del texto).

**Implementado**: banner de título + fila "Volver"/íconos ahora dentro
de `DocuSmartScreenHeader` (mismo componente que Inicio/Biblioteca/
Convertidor/Herramientas PDF) -- el anuncio queda arriba del todo.
`DocuSmartTopBanner` se llama sin `onBack`/`actions`; la fila "Volver" +
íconos se arma a mano en `StudyScreen.kt` (específico de Estudio, no se
tocó el componente compartido). `TabRow` pasa de `colorScheme.surface`
a `colorScheme.primaryContainer.copy(alpha=0.35f)` (mismo mecanismo que
`AccentGradient.kt` para tintar con el acento), con
`selectedContentColor`/`unselectedContentColor` explícitos para mantener
contraste.

**Verificado en dispositivo real** (Motorola Edge 30 Neo, ZY22G7SB77):
anuncio arriba del todo, íconos a la altura de "Volver" (ya no sobre el
degradado), pestañas con fondo celeste tintado y buen contraste de
texto. Gauntlet en verde: `compileDebugKotlin` + `detekt` + `lintDebug`
+ `testDebugUnitTest`.

## 32. Modo Estudio — Resumen automático 100% local + mensajes de progreso

Última parte pendiente de Modo Estudio: resumen automático del PDF,
100% local (sin IA en la nube, condición explícita del usuario ya
acordada) para no romper la política de privacidad ni sumar costo.

**Implementado**:
- `TextSummarizer.kt`: algoritmo extractivo (variante de Luhn) --
  puntúa oraciones por frecuencia de palabras significativas
  (español+inglés) y devuelve las mejor puntuadas en orden original. 5
  tests, encontraron un bug real (`coerceIn` con rango inválido si
  `maxSentences` < mínimo garantizado).
- Nueva 4ta pestaña "Resumen" (reusa el `documentText` de Lectura, no
  vuelve a pedir el PDF). `StudySummaryExporter.kt` + guardar en
  Descargas (MediaStore) y compartir (`FileProvider`), mismos patrones
  ya usados en el resto de la app.
- `ScrollableTabRow` en vez de `TabRow`: con 4 pestañas, "Pomodoro" se
  partía en 2 líneas con ancho fijo -- corregido de paso.

**Seguimiento mismo día -- mensajes de progreso**: cargar un PDF real
de 55 páginas tarda ~2 minutos; un spinner sin texto parecía la app
colgada. Se agregó `LoadingIndicator` compartido (mensaje + spinner)
para la carga del documento y la generación del resumen. Iteración
adicional: el spinner lleva ahora el logo de DocuSmart sobre un círculo
tintado (rodeado por el `CircularProgressIndicator` real de Compose,
grosor 5dp) en vez de un spinner genérico solo. Nota: una captura de
pantalla de un spinner indeterminado solo muestra un arco en un punto
de su rotación (se ve como un punto) -- en vivo gira de forma continua;
confirmación final pendiente del usuario mirando el dispositivo.

**Verificado en dispositivo real** (Motorola Edge 30 Neo, ZY22G7SB77)
con el PDF real de 55 páginas: mensajes de carga visibles, resumen se
genera y muestra como lista, guardar/compartir funcionan. Gauntlet en
verde: `compileDebugKotlin` + `detekt` + `lintDebug` +
`testDebugUnitTest`.

### Seguimiento (mismo día): confirmación del spinner + borde/fondo en los íconos de "Volver"

El primer dispositivo de prueba tenía las animaciones del sistema
desactivadas (Opciones de desarrollador), por eso no se percibía el
giro del spinner -- no era un bug. Confirmado en un segundo dispositivo
(Moto E22, ZY32HFP5QL, animaciones activas): 2 capturas seguidas
muestran el arco en posiciones distintas, girando con normalidad.

De paso: los íconos de abrir documento/estadísticas (junto a "Volver")
ahora tienen su propio círculo -- borde (acento 40% opacidad) y fondo
tintado (acento 10% opacidad), mismo criterio ya usado en el resto de
la app de no dejar un ícono solo sobre fondo plano.

**Verificado en dispositivo real** (Moto E22, ZY32HFP5QL): spinner
girando confirmado con 2 capturas consecutivas; íconos con borde/fondo
correctos. Gauntlet en verde: `compileDebugKotlin` + `detekt` +
`lintDebug` + `testDebugUnitTest`.

Ajuste puntual mismo día: los 2 íconos quedaban muy pegados -- espacio
entre ambos sube de 8dp a 16dp. Verificado en el mismo Moto E22.

## 33. Modo Estudio — "Retomar lectura" con progreso guardado, extracción incremental del PDF y opción de quitar de la lista

Pedido explícito del usuario: al detener la lectura en voz alta de un
PDF largo y cerrar la app, antes tocaba volver a empezar desde el
principio. Se agregó `StudyReadingProgressStorage` (SharedPreferences +
JSON, mismo patrón que Notas/Estadísticas): guarda por URI el párrafo
donde quedó la lectura, página actual/total y nombre del documento
(hasta 10, el más viejo libera su permiso persistente). "Leer todo"
ahora retoma desde ahí en vez de reiniciar, y una tarjeta "Continuar
leyendo" en el estado vacío de Lectura permite reabrir cualquiera de
esos documentos.

**Seguimiento, pedido explícito del usuario**: además de retomar,
poder **quitar un PDF de esa lista** (sin borrar el archivo real) --
ícono de basura en cada tarjeta, quita la entrada al instante.

**Seguimiento, pedido explícito del usuario**: en un dispositivo más
lento (Moto E22) la extracción del mismo PDF tardaba notablemente más
que en otro -- preguntó si se podía procesar en segundo plano y
empezar a leer con solo una parte ya lista. `extractPdfText` ahora
avisa (`onPageExtracted`) después de CADA página en vez de esperar el
documento completo: el spinner desaparece y "Leer todo" queda
disponible con la primera página lista, mientras el resto se sigue
procesando de fondo (barra de estado: "Procesando el resto del
documento… (N páginas listas)"). Si la lectura alcanza el último
párrafo ya extraído antes de que termine todo, no se corta -- espera y
retoma sola en cuanto llegan más párrafos.

**Bug real encontrado y corregido en la verificación**: el selector de
documentos usaba `GetContent()` (ACTION_GET_CONTENT), cuyo permiso
persistente NO sobrevive un reinicio de la app pese a no lanzar
excepción al pedirlo -- solo `OpenDocument()` (ACTION_OPEN_DOCUMENT) lo
soporta de verdad. Efecto real: "Continuar leyendo" fallaba en
silencio tras cerrar la app (`SecurityException` del
`DownloadStorageProvider` tanto en la extracción de texto como en el
visor de PDF), y como la app leía en voz alta el mensaje de error como
si fuera el documento, el progreso guardado terminaba borrándose solo
al "terminar" esa lectura. Corregido cambiando el selector a
`OpenDocument()` (mismo selector del sistema, sin cambio visible). De
paso se corrigió que el nombre del documento guardado en el progreso
quedaba en un valor genérico ("Sin documento"/"Documento") porque solo
se fijaba al terminar TODA la extracción -- ahora se resuelve aparte y
de una vez, sin esperar el texto.

**Verificado en dispositivo real** (Moto E22, ZY32HFP5QL, con "It -
Stephen King.pdf"): extracción incremental confirmada (lectura arranca
y sigue sin cortes mientras el resto se extrae de fondo); ciclo
completo detener → cerrar app → reabrir → "Continuar leyendo" con el
nombre real → retoma la lectura real, sin `SecurityException` en
logcat; botón "Quitar de la lista" confirmado (tarjeta desaparece al
instante, JSON persistido queda vacío). Gauntlet en verde:
`compileDebugKotlin` + `detekt` + `lintDebug` + `testDebugUnitTest`.

**Pendiente**: aprobación explícita del usuario para fusionar y subir
todo lo anterior (Lectura, Resumen, header/contraste/íconos y
"Retomar lectura") a `main` (no fusionado todavía).

## 34. Auditoría de nuevas funcionalidades propuestas (Escáner, QR, Visor, Notas, Herramientas PDF, Monetización, IA) — 2026-09-10

El usuario pidió una lluvia de ideas de mejoras para estas 6 áreas
mientras se esperaba la revisión de Google Play, con una condición
explícita: **contrastarlas contra lo que ya existe** antes de
catalogarlas, para no proponer trabajo duplicado. Se investigó cada
idea contra el código real (grep + lectura de archivos, no supuesto)
antes de escribir esta sección — varias ideas originales del
brainstorming **ya estaban implementadas** y se descartaron de esta
lista; se documenta cuáles para que quede el registro.

**Descartadas por ya existir (hallazgo real de esta auditoría, no
había que agregarlas):**
- **Herramientas PDF**: de las ideas propuestas, **Reordenar páginas**
  (`PdfTool.REORDER_PAGES`), **Comparar dos PDFs** (`PdfTool.COMPARE`),
  **Redactar/censurar** (`PdfTool.REDACT`) y **Firma digital**
  (`PdfTool.SIGN`) ya están completamente implementadas — confirmado
  en `PdfToolsViewModel.kt` (enum `PdfTool` con 13 herramientas reales)
  y sus pantallas en `PdfToolsScreen.kt`/`SignPdfScreen.kt`. La app ya
  tiene 13 herramientas PDF, no 9 como se asumió al proponer la idea.
- **Creador de QR**: los tipos de contenido Email y Teléfono
  (`QrContentType.EMAIL`/`PHONE`) ya existen, además de URL/Texto/
  Imagen/Documento — confirmado en `QrScreen.kt`. Solo Wi-Fi, Contacto
  y Evento de calendario son realmente nuevos (ítem 43).
- **Modo Estudio**: "Lectura en voz alta del PDF" y "Continuar leyendo"
  (retomar donde quedó la lectura) **ya existen**, implementados en la
  pestaña "Lectura" con `StudyReadingProgressStorage` — ver §33. No se
  proponen de nuevo. Lo que sí sigue faltando es el equivalente
  *visual* (recordar la última página vista al mirar el PDF en el
  Visor, no al escucharlo) — ítem 48, una función distinta.
- **Modo Estudio**: "Resumen automático" ya existe, 100% local
  (algoritmo extractivo, sin IA en la nube) — ver §32. Esto también
  fija el precedente de diseño para la sección de IA más abajo (§34.7):
  el usuario ya eligió una vez, explícitamente, no depender de una API
  de IA en la nube para esta función por costo y por la promesa de
  privacidad ya publicada.

### §34.1 — Escáner

#### HU-41 — Filtros de color al escanear

**Como** usuario que escanea un documento,
**quiero** elegir entre Color, Blanco y negro, Escala de grises o
Resaltar texto antes de guardar,
**para** mejorar la legibilidad según el tipo de documento (una
pizarra, un recibo térmico desteñido, un texto a lápiz).

**Investigado**: `DocumentScannerLauncher.kt` solo expone
`SCANNER_MODE_FULL`/`SCANNER_MODE_BASE` de ML Kit — controla qué UI de
edición se muestra, no el color del resultado. ML Kit Document Scanner
no expone un filtro de color propio; los 4 modos tendrían que aplicarse
como post-proceso sobre el bitmap ya capturado (matriz de color/umbral
para B&N, desaturación para escala de grises).

- **RF1** El resultado de cada página capturada permite elegir uno de
  4 modos de color antes de guardar: Color (actual, sin cambios),
  Blanco y negro, Escala de grises, Resaltar texto (aumenta contraste y
  fuerza casi-blanco el fondo).
- **RF2** El modo elegido se aplica a todas las páginas del documento
  actual por defecto, pero se puede cambiar página por página antes de
  guardar.
- **RNF1** Aplicar el filtro a una página de resolución típica (hasta
  4000×3000px) no debe agregar más de ~500ms de espera perceptible por
  página.
- **RNF2** El procesamiento es 100% local (matriz de color de Android
  Graphics), sin llamadas de red.
- **AC1** Dado que termino de capturar una página, cuando reviso el
  resultado, entonces veo los 4 modos de color como chips seleccionables
  con una vista previa en miniatura de cada uno.
- **AC2** Dado que elijo un modo distinto de Color, cuando guardo el
  documento, entonces el archivo final refleja ese filtro.
- **AC3** El modo "Color" (por defecto) no cambia el comportamiento
  actual — sin regresión para quien no toca esta opción.

#### HU-42 — Acceso directo a OCR/Firmar/Carpeta Segura desde el resultado del escaneo

**Como** usuario que acaba de escanear un documento,
**quiero** poder pasarlo directo a OCR, Firmar, o Carpeta Segura,
**para** no tener que guardarlo primero y volver a buscarlo en
Biblioteca para esas mismas acciones.

**Investigado**: mismo mecanismo ya construido y verificado en HU-UX-01/
HU-UX-02 (§3) para "Crear QR"/"Convertir" desde un archivo ya
seleccionado — `NavRoutes` con parámetros opcionales + "consumo único"
del archivo precargado. Reutilizable tal cual apuntando a
`PdfTool.OCR`/`PdfTool.SIGN` y al flujo de mover a Carpeta Segura ya
existente en Seguridad.

- **RF1** El resultado del escaneo (`ScanResultScreen`) agrega 3
  acciones nuevas junto a "Compartir": "Hacer buscable" (OCR), "Firmar",
  "Mover a Carpeta Segura".
- **RF2** Cada acción navega directo a la pantalla correspondiente con
  el archivo ya cargado, sin volver a mostrar el selector de archivos.
- **RNF1** Ninguna de las 3 acciones duplica el archivo en disco
  innecesariamente — reutiliza la misma referencia de archivo ya
  guardado por el escáner.
- **AC1** Dado que acabo de guardar/compartir un documento escaneado,
  cuando veo la lista de acciones, entonces aparecen las 3 nuevas.
- **AC2** Dado que toco "Hacer buscable", cuando se abre Herramientas
  PDF, entonces el archivo ya está cargado en la herramienta OCR.
- **AC3** El flujo manual (ir a Herramientas PDF y elegir el archivo a
  mano) sigue funcionando igual.

### §34.2 — Creador/Lector de QR

#### HU-43 — Nuevos tipos de contenido: Wi-Fi, Contacto, Evento de calendario

**Como** usuario que quiere compartir algo más que un link o texto,
**quiero** generar un QR de tipo Wi-Fi (conectar a una red sin escribir
la contraseña), Contacto (tarjeta vCard) o Evento (agregar a
calendario),
**para** cubrir los casos de uso más comunes de un generador de QR sin
salir de DocuSmart.

- **RF1** 3 nuevos `QrContentType`: `WIFI` (SSID + contraseña +
  seguridad WPA/WEP/ninguna), `CONTACT` (nombre, teléfono, correo —
  formato vCard 3.0), `EVENT` (título, fecha/hora inicio-fin, lugar —
  formato iCalendar).
- **RF2** Cada tipo tiene su propio formulario de campos (igual patrón
  que los 6 tipos ya existentes) y genera el string con el formato
  estándar correspondiente antes de codificarlo en el QR.
- **RNF1** El QR generado debe ser legible por el lector nativo de
  Android/iOS (formato estándar, sin extensiones propietarias) — un QR
  Wi-Fi de DocuSmart tiene que poder escanearse con la cámara nativa de
  cualquier teléfono y ofrecer "Conectar".
- **AC1** Dado que elijo "Wi-Fi" como tipo, cuando completo SSID +
  contraseña y genero el QR, entonces escanearlo con la cámara nativa
  de Android ofrece conectar a esa red.
- **AC2** Dado que elijo "Contacto", cuando genero el QR, entonces
  escanearlo ofrece "Agregar contacto" con los datos correctos.
- **AC3** Dado que elijo "Evento", cuando genero el QR, entonces
  escanearlo ofrece agregarlo al calendario del dispositivo.
- **AC4** Los 6 tipos existentes (URL/Texto/Imagen/Documento/Email/
  Teléfono) no cambian.

#### HU-44 — Historial de códigos QR creados y leídos

**Como** usuario que genera o lee QR seguido,
**quiero** ver un historial de los últimos códigos,
**para** reutilizar uno sin tener que rehacerlo o volver a escanearlo.

**Investigado**: no existe ninguna persistencia de QR hoy (`grep`
"historial"/"Historial"/"QrHistory" sin resultados en `features/
scanner`) — es una entidad nueva de punta a punta.

- **RF1** Cada QR creado o leído con éxito se guarda (contenido,
  tipo, fecha) en una lista local, máximo 50 entradas (las más viejas
  se descartan).
- **RF2** Pantalla "Historial" accesible desde Creador y Lector de QR,
  con opción de volver a generar (Creador) o copiar/abrir el contenido
  (Lector), y de eliminar una entrada individual o vaciar todo.
- **RNF1** Persistencia local (Room o `SharedPreferences`+JSON, mismo
  patrón ya usado por Notas/Progreso de lectura) — no sale del
  dispositivo.
- **RNF2** Un QR protegido con contraseña (función ya existente) guarda
  en el historial el contenido *cifrado*, no el texto plano, para no
  debilitar esa protección con una copia sin cifrar.
- **AC1** Dado que genero un QR nuevo, cuando abro el Historial,
  entonces aparece como la entrada más reciente.
- **AC2** Dado que leo un QR con la cámara, cuando abro el Historial
  desde el Lector, entonces también aparece ahí.
- **AC3** Eliminar una entrada no afecta ninguna otra ni borra ningún
  archivo real vinculado.

#### HU-45 — Diseño personalizado del QR (color y logo)

**Como** usuario que comparte un QR con otras personas,
**quiero** elegir un color o agregar un logo pequeño al centro,
**para** que se vea más profesional o reconocible como propio.

**Investigado**: la generación de QR actual (librería usada en
`QrCreatorScreen`) no fue confirmada en esta pasada si soporta módulos
coloreados/logo embebido de fábrica — **a verificar antes de estimar
en detalle**, puede requerir reemplazar la librería de generación o
post-procesar el bitmap con cuidado de no romper la lectura.

- **RF1** Selector de color para los módulos del QR (con verificación
  de contraste mínimo contra el fondo, para no generar un QR
  illegible).
- **RF2** Opción de subir una imagen pequeña (logo) que se superpone al
  centro del QR, con el nivel de corrección de errores del QR subido
  automáticamente para compensar el área tapada.
- **RNF1** Todo QR generado con color/logo debe seguir siendo legible
  por un lector estándar — criterio de aceptación no negociable, se
  valida escaneando el resultado con la cámara nativa antes de dar por
  terminada la funcionalidad.
- **AC1** Dado que elijo un color de contraste insuficiente, cuando
  intento generar, entonces la app avisa y sugiere un color más oscuro/
  claro en vez de generar un QR potencialmente illegible.
- **AC2** Dado que agrego un logo, cuando escaneo el QR resultante con
  la cámara nativa de otro teléfono, entonces lee correctamente el
  contenido.

### §34.3 — Visor (modo lector de documentos)

#### HU-46 — Anotaciones: resaltar texto y notas adhesivas (épica)

**Como** usuario que lee un PDF/documento importante,
**quiero** resaltar frases y dejar notas adhesivas sobre el documento,
**para** repasar después lo más relevante sin salir de la app.

**Investigado**: `ViewerViewModel.pdfSearchHighlights` es un mecanismo
temporal de resultados de búsqueda (se limpia al cerrar la búsqueda),
no una anotación persistente del usuario — confirmado, no hay ningún
sistema de anotaciones hoy.

*(Épica — se listan RF/AC de alto nivel; el diseño detallado de cómo se
persiste una anotación por página/offset de texto queda para cuando se
decida abordarla, dada la Dificultad Alta.)*

- **RF1** Seleccionar texto en un documento (PDF/Word/Texto ya
  renderizado) ofrece "Resaltar" con al menos 3 colores.
- **RF2** Tocar un punto del documento (no sobre texto seleccionable,
  p.ej. una imagen escaneada) ofrece "Agregar nota" — un ícono anclado
  a esa posición que al tocarlo muestra el texto de la nota.
- **RF3** Las anotaciones persisten por documento y sobreviven cerrar/
  reabrir la app.
- **RNF1** Las anotaciones no modifican el archivo original — se
  guardan aparte (capa superpuesta), para no arriesgar corromper el PDF
  del usuario.
- **RNF2** Compartir un documento anotado ofrece explícitamente elegir
  entre "con anotaciones" (aplana la capa sobre una copia nueva) o
  "original sin anotaciones" — nunca sobrescribe el archivo fuente.
- **AC1** Dado que resalto una frase, cuando cierro y reabro el
  documento, entonces el resaltado sigue ahí en la misma posición.
- **AC2** Dado que un documento no tiene anotaciones, cuando lo
  comparto, entonces se comparte exactamente igual que hoy (sin
  regresión).

#### HU-47 — Marcadores de página

**Como** usuario que lee documentos largos,
**quiero** marcar páginas específicas,
**para** volver directo a ellas sin deslizar/buscar.

- **RF1** Ícono de marcador en la barra del Visor por página; tocar lo
  activa/desactiva.
- **RF2** Lista de marcadores del documento actual, accesible desde un
  botón en la barra superior, que salta a la página al tocar uno.
- **RNF1** Persistencia local por documento (mismo patrón que Progreso
  de lectura de Modo Estudio).
- **AC1** Dado que marco la página 12, cuando cierro y reabro el
  documento, entonces el marcador sigue en la página 12.
- **AC2** Dado que tengo 3 marcadores, cuando abro la lista, entonces
  veo las 3 páginas y saltar a cualquiera funciona.

#### HU-48 — Recordar la última página vista (visual, distinta del audio ya existente)

**Como** usuario que cierra la app a mitad de leer un documento,
**quiero** que el Visor me lleve a la última página que vi,
**para** no perder el lugar visualmente (esto es aparte de "Retomar
lectura" en audio, que ya existe para Modo Estudio).

**Investigado**: `ViewerViewModel.currentPage` es solo estado en
memoria de la sesión actual (`MutableStateFlow`, no persistido) —
confirmado por grep, sin ningún campo de persistencia de página por
documento en el Visor.

- **RF1** Al cerrar el Visor, la última página vista se guarda
  asociada a la URI/id del documento.
- **RF2** Al reabrir el mismo documento desde Biblioteca/Recientes, el
  Visor abre directo en esa página (no en la 1).
- **RNF1** Guardar la página no debe agregar demora perceptible al
  cerrar el Visor (escritura asíncrona).
- **AC1** Dado que estoy en la página 8 de un PDF de 20 y cierro la
  app, cuando reabro ese mismo documento, entonces el Visor abre en la
  página 8.
- **AC2** Un documento abierto por primera vez sigue abriendo en la
  página 1 (sin cambio para el caso nuevo).

### Implementado y verificado en dispositivo real (2026-09-16)

- **HU-47/HU-48, ambas cerradas juntas** (mismo mecanismo de
  persistencia): 2 tablas Room nuevas (`page_bookmarks`, clave primaria
  compuesta `documentId`+`page`; `last_viewed_page`, una fila por
  documento) vía `MIGRATION_3_4` (`version` 3→4). `ViewerBottomBar`
  ganó un ícono de marcador (izquierda/derecha del indicador de
  página) para abrir la lista (`ViewerBookmarksSheet`, `ModalBottomSheet`)
  y marcar/desmarcar la página actual. El salto de página (marcador
  tocado, o "última página vista" al abrir) se generalizó en
  `ViewerUiState.pendingPageJump`, unificado con el mecanismo ya
  existente de salto a resultado de búsqueda.
- **Refactor de paso, motivado por agregar estas 2 tablas**: el mismo
  bloque de 2-3 líneas (`favoritesRepository.migrateId()` +
  `annotationDao.updateDocumentId()`, o `removeAlias()`+`removeFavorite()`+
  `deleteByDocument()`) estaba repetido en 11 sitios (`TrashRepository`
  ×6, `SecurityViewModel` ×4, `DocumentRepository.renameDocument()` ×1)
  — agregar las 2 tablas nuevas ahí habría dejado 22 llamadas más
  dispersas. Se extrajo `DocumentIdentityMaintenance` (`onIdChanged`/
  `onPermanentlyDeleted`), reemplazando los 11 sitios existentes además
  de cubrir las 2 tablas nuevas.
- **Bug real encontrado en la revisión propia del código, antes de
  verificar en dispositivo**: `pendingPageJump` nunca se limpiaba si el
  salto quedaba fuera de rango (ej. una "última página vista" guardada
  de una versión más larga del mismo documento, luego reemplazado por
  una más corta) -- quedaba pegado en el estado y bloqueaba cualquier
  salto de búsqueda futuro en la misma sesión (el salto pendiente
  siempre tiene prioridad sobre la búsqueda). Corregido: se limpia
  siempre que se resuelve, haya saltado o no.
- Gauntlet completo (`compileDebugKotlin`, `detekt`, `lintDebug`,
  `testDebugUnitTest`, `compileDebugAndroidTestKotlin`) en verde,
  incluidos tests de integración reales contra SQLite (`PageBookmarkDaoTest`,
  `LastViewedPageDaoTest`, mismo patrón que `DocumentHistoryDaoTest`) y
  `DocumentIdentityMaintenanceTest`. Un intento de test de migración
  real (abrir un archivo .db en versión 3, migrar, verificar) se
  descartó: `BundledSQLiteDriver` (el driver de test, JVM puro) falla
  incluso al reabrir un archivo con el MISMO esquema sin ninguna
  migración de por medio -- limitación del driver de pruebas, no del
  SQL de la migración real (verificado a mano, línea por línea, contra
  el mismo patrón ya usado por `MIGRATION_2_3`, en producción).
- Verificado en dispositivo real (Motorola Edge 30 Neo, PDF real de 8
  páginas): marcar la página 1 y la página 6 desde la barra inferior,
  abrir la lista y ver ambas ordenadas ascendente, tocar "Página 1"
  desde la página 6 y confirmar el salto real, quitar un marcador desde
  la lista (ícono de basura) y confirmar que desaparece de inmediato.
  Cerrar el documento en la página 5, forzar el cierre de la app,
  reabrir el mismo documento y confirmar que abre directo en la página
  5. Sin errores en logcat (`AndroidRuntime:E`/`com.docsmart:E`) en
  ningún punto del flujo.

### §34.4 — Notas (Modo Estudio)

#### HU-49 — Adjuntar una imagen o recorte escaneado a una nota

**Como** usuario tomando notas de estudio,
**quiero** adjuntar una foto o un recorte de un documento escaneado,
**para** no tener que describir con palabras algo que es más claro
como imagen (un diagrama, una fórmula).

- **RF1** Botón "Adjuntar imagen" en el editor de notas: cámara,
  galería, o un archivo ya escaneado en la Biblioteca (mismo picker ya
  usado en Convertir/Seguridad).
- **RF2** La imagen se muestra en línea dentro de la nota, en el punto
  donde se insertó.
- **RNF1** La imagen adjunta se copia al almacenamiento privado de la
  app (mismo criterio que Carpeta Segura) — si el usuario borra el
  original de su galería, la nota no pierde la imagen.
- **AC1** Dado que adjunto una foto a una nota, cuando la guardo y
  reabro, entonces la imagen sigue visible en el mismo lugar.
- **AC2** Borrar la nota borra también la copia de la imagen asociada
  (no deja archivos huérfanos).

**✅ Implementado y verificado en dispositivo real (Motorola Edge 30
Neo) 2026-09-17.** `NoteEditorCard` agrega botones "Adjuntar imagen"
(`GetMultipleContents`, selector del sistema) y "Escanear" (mismo
escáner ML Kit que Convertidor, vía la nueva función pública
`rememberDocumentScannerAction` extraída de `ConverterScreen.kt` a
`DocumentScannerLauncher.kt` para reutilizarla sin duplicar código).
`NoteRepository.createNote()` copia cada imagen a
`filesDir/note_images/<noteId>_<posición>.jpg` antes de insertar la
fila `NoteImageEntity` (RNF1: no depende de la URI temporal del
proveedor externo). Diferencia con RF2: en vez de insertar la imagen
en línea dentro del texto en el punto exacto de inserción, se muestra
en un carrusel horizontal (`NoteImagesCarousel`, mismo patrón visual
que `SelectedImagesCarousel` del Convertidor) debajo del campo de
texto — más simple de implementar y de usar, y AC1 se cumple igual
(la imagen persiste y se ve al reabrir). `NoteListItem` agrega un
carrusel de miniaturas de solo lectura por nota, con un visor de
imagen a pantalla completa (`Dialog`) al tocar una miniatura. AC2 ya
estaba cubierto por `NoteRepository.deleteNote()` (borra los archivos
de imagen del disco antes de la fila). Verificado en dispositivo real:
adjuntar 2 imágenes desde la galería, guardar, ver las miniaturas en
la lista, abrir y cerrar el visor de pantalla completa, y eliminar la
nota (borra la fila y, por código, los archivos asociados).

#### HU-50 — Vincular una nota a un documento de la Biblioteca

**Como** usuario que toma notas sobre un PDF específico,
**quiero** vincular la nota a ese documento,
**para** encontrar mis apuntes junto al archivo cuando lo vuelva a
abrir.

- **RF1** Al crear/editar una nota, opción "Vincular documento" que
  abre el selector de la Biblioteca.
- **RF2** Desde el Visor de ese documento, un ícono/badge muestra que
  tiene notas vinculadas y permite abrirlas.
- **RNF1** Un documento puede tener varias notas vinculadas; una nota
  solo puede vincularse a un documento a la vez (relación 1 nota → 0/1
  documento, N notas → 1 documento).
- **AC1** Dado que vinculo una nota a "Contrato.pdf", cuando abro ese
  PDF en el Visor, entonces veo el indicador de nota vinculada.
- **AC2** Borrar el documento vinculado no borra la nota (la nota queda
  sin vínculo, no se pierde el contenido escrito).

#### HU-51 — Exportar una nota a PDF/Word

**Como** usuario que quiere compartir o imprimir una nota,
**quiero** exportarla a PDF o Word,
**para** usarla fuera de la app (imprimir, entregar una tarea).

**Investigado**: `StudySummaryExporter.kt` ya hace exactamente esto
para el Resumen automático (exporta a Descargas + compartir) —
reutilizable como base técnica para notas de texto libre.

- **RF1** Botón "Exportar" en una nota, con opción PDF o Word (.docx).
- **RF2** El archivo generado conserva el texto e imágenes adjuntas
  (ver HU-49) en el mismo orden que la nota.
- **RNF1** Reutiliza el mismo mecanismo de guardado en Descargas
  (MediaStore) + compartir (`FileProvider`) ya usado en toda la app —
  no un flujo nuevo.
- **AC1** Dado que exporto una nota a PDF, cuando abro el archivo
  generado, entonces el texto y las imágenes coinciden con la nota
  original.
- **AC2** Exportar a Word (.docx) produce un archivo abrible por Word/
  Google Docs sin errores de formato.

#### HU-52 — Recordatorio de repaso

**Como** usuario que estudia con notas,
**quiero** recibir un recordatorio para repasar una nota,
**para** no olvidarme de volver a ella.

**Investigado**: `PomodoroTimerService` ya establece el patrón de
notificaciones locales del proyecto (foreground service +
`POST_NOTIFICATIONS`) — un recordatorio de repaso es más simple (no
necesita foreground, alcanza con `WorkManager`/`AlarmManager` para una
notificación puntual).

- **RF1** Al guardar una nota, opción "Recordarme repasar esto" con
  intervalos predefinidos (mañana, en 3 días, en 1 semana) o fecha
  personalizada.
- **RF2** Notificación local a esa fecha/hora que, al tocarla, abre la
  nota directamente.
- **RNF1** No requiere conexión a internet (notificación 100% local,
  sin servidor propio).
- **RNF2** Respeta que el usuario haya denegado `POST_NOTIFICATIONS` —
  si no hay permiso, la opción se muestra deshabilitada con una
  explicación, no falla en silencio.
- **AC1** Dado que programo un recordatorio para "en 3 días", cuando
  llega esa fecha, entonces recibo la notificación con el título de la
  nota.
- **AC2** Tocar la notificación abre esa nota específica.

### §34.5 — Herramientas PDF

Como se documentó al inicio de esta sección, de las ideas originales de
esta categoría, **13 de 14 ya existen** en el código (Unir, Dividir,
Comprimir, Rotar, Numerar páginas, Marca de agua, Reordenar páginas,
Comparar, Redactar, Recortar, Editar texto, Firmar, Rellenar
formulario, OCR). La única novedad real encontrada:

#### HU-53 — Extraer imágenes embebidas de un PDF

**Como** usuario con un PDF que contiene fotos/diagramas,
**quiero** extraer esas imágenes como archivos separados,
**para** reutilizarlas sin tener que recortarlas a mano de una captura
de pantalla.

**Investigado**: no existe `PdfTool.EXTRACT_IMAGES` ni equivalente en
el enum de 13 herramientas confirmado por grep — no implementado.

- **RF1** Nueva herramienta "Extraer imágenes" en el menú de
  Herramientas PDF: recibe un PDF, detecta todas las imágenes
  embebidas (vía iText, ya usado por el resto de herramientas PDF del
  proyecto) y las guarda como archivos JPG/PNG individuales.
- **RF2** Si el PDF no tiene ninguna imagen embebida, se informa
  claramente en vez de generar un resultado vacío sin explicación.
- **RNF1** Un PDF de hasta 50 páginas con imágenes debe procesarse en
  un tiempo razonable (mismo umbral de referencia que Comprimir/OCR ya
  existentes en el proyecto).
- **AC1** Dado un PDF con 5 imágenes embebidas, cuando ejecuto
  "Extraer imágenes", entonces obtengo 5 archivos de imagen guardados
  en Descargas/Biblioteca.
- **AC2** Dado un PDF sin imágenes (solo texto), cuando ejecuto la
  herramienta, entonces veo un mensaje claro ("Este PDF no tiene
  imágenes para extraer") en vez de un resultado vacío.

### §34.6 — Monetización

#### HU-54 — Prueba gratuita de Premium (trial)

**Como** usuario indeciso sobre pagar Premium,
**quiero** probarlo gratis por unos días,
**para** decidir con confianza si vale la pena suscribirme.

**Investigado**: Google Play Billing soporta *free trials* nativos
configurables por producto/plan base en Play Console
(`freeTrialPeriod`), sin necesidad de backend propio — `BillingManager`
tendría que reconocer el estado "en período de prueba" además de
"activo"/"inactivo" que ya maneja.

- **RF1** El plan mensual (y el nuevo plan anual de HU-55, si se
  implementa junto) ofrece 7 días de prueba gratuita antes del primer
  cobro, configurado del lado de Play Console.
- **RF2** `PremiumScreen` muestra claramente "7 días gratis, luego
  $X/mes" en vez de solo el precio, para que no sea sorpresivo el
  primer cobro.
- **RF3** El usuario puede cancelar durante el trial sin cargo (ya lo
  garantiza Play Billing de fábrica, no es lógica de la app).
- **RNF1** El estado "en trial" debe distinguirse de "pagando" en
  `PremiumManager` únicamente para fines de mensaje en la UI — el
  *gating* de funciones Premium debe tratarlos igual (ambos
  desbloquean todo).
- **AC1** Dado que inicio el trial, cuando reviso Ajustes, entonces
  veo que tengo Premium activo con la fecha en que empezaría a
  cobrarse.
- **AC2** Dado que cancelo durante el trial, cuando llega la fecha de
  cobro, entonces no se genera ningún cargo y Premium se desactiva.

#### HU-55 — Plan anual con descuento

**Como** usuario que ya decidió que quiere Premium,
**quiero** pagar una vez al año con descuento en vez de mes a mes,
**para** ahorrar dinero si sé que lo voy a seguir usando.

**Investigado**: `PremiumPlan.kt` ya modela `id`/`price`/`periodRes`/
`savingsLabelRes`/`productId` como una lista de planes — el modelo de
datos **ya está preparado** para más de un plan (el campo
`savingsLabelRes` sugiere que esto ya se pensó al diseñarlo). Agregar
un plan anual es sumar un `PremiumPlan` más a la lista existente y su
producto correspondiente en Play Console, no rediseñar nada.

- **RF1** Nuevo plan "Anual" en `PremiumScreen`, con su propio
  `productId` de Play Billing, mostrando el ahorro vs. 12 meses del
  plan mensual (usa `savingsLabelRes`, ya existente en el modelo).
- **RF2** Marcado como "Más conveniente" (usa `isPopular`, ya existente
  en `PremiumPlan`) si así se decide.
- **RNF1** Sin cambios en la lógica de gating de `PremiumManager` — un
  usuario con plan anual activo se trata exactamente igual que uno con
  plan mensual activo.
- **AC1** Dado que elijo el plan anual, cuando completo la compra,
  entonces Premium se activa igual que con el plan mensual.
- **AC2** El plan mensual existente sigue funcionando sin cambios.

#### HU-56 — Programa de referidos

**Como** usuario contento con la app,
**quiero** invitar amigos y que ambos ganemos algo,
**para** tener un motivo concreto para recomendarla.

**Investigado**: no hay backend propio en el proyecto (arquitectura
100% local + servicios de terceros ya declarados) — un programa de
referidos que dé días Premium gratis necesita **validar códigos de
invitación del lado del servidor** para evitar que un usuario se
auto-invite o comparta un código reutilizable infinitas veces; esto es
la razón real de la Dificultad Alta/Riesgo Medio-Alto, no la parte de
UI.

- **RF1** Cada usuario Premium (o todos, a decidir) tiene un código de
  invitación único, compartible por WhatsApp/redes.
  Requiere backend propio para generarlo y validarlo (fuera del
  alcance de "solo cliente Android" de este proyecto hoy).
- **RF2** Al canjear un código válido y no usado antes, ambas cuentas
  (quien invita y quien es invitado) reciben N días de Premium gratis.
- **RNF1** El backend debe impedir: auto-referido (mismo dispositivo/
  cuenta), reutilización del mismo código por más de una cuenta nueva,
  y abuso por creación masiva de cuentas.
- **RNF2** Requiere definir una identidad de usuario estable (hoy
  DocuSmart no tiene cuentas de usuario, ver política de privacidad
  §3 "no pedimos que crees una cuenta") — **decisión de producto previa
  necesaria**: ¿se introduce alguna forma de identidad (aunque sea
  anónima, tipo ID de instalación) solo para este programa, aceptando
  que eso es un cambio de postura respecto a "sin cuentas"?
- **AC1** Dado que comparto mi código y un amigo lo usa al instalar
  por primera vez, cuando confirma, entonces ambos vemos Premium
  extendido por N días.
- **AC2** Un código ya usado no puede volver a canjearse por otra
  cuenta.

*(Recomendación: esta es la idea de mayor esfuerzo/riesgo de las 4 de
Monetización — requiere decidir primero si vale la pena introducir
backend + alguna noción de identidad de usuario antes de estimarla en
detalle.)*

#### HU-57 — Mediación de AdMob

**Como** dueño de la app,
**quiero** que AdMob compita con otras redes publicitarias por cada
espacio de anuncio,
**para** aumentar el ingreso por impresión sin cambiar la experiencia
del usuario.

- **RF1** Configurar mediación en la consola de AdMob (sumar al menos
  una red adicional, p.ej. Meta Audience Network) para los 12 bloques
  de anuncio ya creados (§ sesión anterior, AdConstants.kt).
- **RF2** Agregar el SDK de mediación correspondiente como dependencia
  Gradle nueva.
- **RNF1** No debe cambiar la frecuencia ni ubicación de anuncios ya
  definida (`INTERSTITIAL_MIN_CONVERSIONS`, `INTERSTITIAL_MIN_INTERVAL_MS`
  en `AdConstants.kt`) — es una optimización de backend publicitario,
  invisible para el usuario salvo por qué anuncio específico ve.
- **AC1** Dado que se activa la mediación, cuando se cargan anuncios,
  entonces la consola de AdMob muestra impresiones repartidas entre
  las redes configuradas (no solo Google Ads).
- **AC2** Ningún flujo existente (banners/interstitial/rewarded) cambia
  de comportamiento para el usuario.

*(Recomendación: esperar a tener datos reales de tráfico/eCPM en
producción antes de invertir esfuerzo acá — sin usuarios reales
todavía no hay señal de si vale la pena.)*

### §34.7 — Funcionalidades con IA

Antes de proponer nada acá, un precedente real del propio proyecto
(§32): al construir "Resumen automático", el usuario **ya eligió
explícitamente** un algoritmo 100% local en vez de una API de IA en la
nube, por dos razones concretas — no romper la promesa de privacidad
ya publicada ("no leemos ni subimos el contenido de tus documentos... a
ningún servidor", política de privacidad, sección 3) y no sumar costo
variable por uso. Esa decisión aplica como criterio de diseño para
cualquier función de IA nueva: se separan en 3 niveles según qué tan
lejos se alejan de ese precedente.

**Nivel A — IA on-device (ML Kit), sin costo, sin cambiar la política
de privacidad actual:**

#### HU-58 — Traducción de documentos

**Como** usuario con un documento en otro idioma,
**quiero** traducirlo dentro de la app,
**para** entenderlo sin salir a pegar el texto en otra app.

**Investigado**: ML Kit Translation corre 100% on-device (modelos de
idioma se descargan una vez, sin llamada de red por traducción) — el
proyecto ya usa ML Kit para escaneo/OCR, mismo SDK, sin sumar una
dependencia de un proveedor nuevo.

- **RF1** Botón "Traducir" en el Visor de PDF/Texto, con selector de
  idioma destino (de los que ya existen como idiomas de la app: es,
  en, pt, ja, ko, zh, it, fr, más los que soporte ML Kit).
- **RF2** El texto traducido se muestra superpuesto/reemplazando la
  vista de texto extraído (mismo mecanismo que ya extrae texto para
  Lectura en voz alta de Modo Estudio).
- **RNF1** El modelo de idioma se descarga una sola vez (con aviso de
  tamaño ~30MB antes de descargar por datos móviles) y se reutiliza
  para futuras traducciones a ese idioma.
- **RNF2** 100% local tras la descarga del modelo — no envía el
  contenido del documento a ningún servidor, consistente con la
  política de privacidad ya publicada (sin necesidad de actualizarla).
- **AC1** Dado que traduzco un PDF en inglés a español, cuando termina,
  entonces veo el texto traducido, no el original.
- **AC2** Sin conexión y sin el modelo descargado, la app avisa
  claramente en vez de fallar en silencio.

#### HU-59 — Clasificación automática del tipo de documento al escanear

**Como** usuario que escanea documentos variados,
**quiero** que la app sugiera automáticamente una categoría (DNI,
factura, contrato, receta médica),
**para** organizarlos sin etiquetarlos a mano cada vez.

- **RF1** Al terminar de escanear, ML Kit Entity Extraction/Text
  Classification (on-device) analiza el texto detectado y sugiere una
  de un set cerrado de categorías predefinidas.
- **RF2** La sugerencia se muestra como un chip editable ("Parece un
  recibo — ¿es correcto?") antes de guardar, nunca se asigna sin
  confirmación.
- **RNF1** 100% local — mismo criterio de privacidad que HU-58.
- **RNF2** Una clasificación incorrecta no debe tener consecuencias
  destructivas (solo afecta una etiqueta/carpeta sugerida, nunca borra
  ni mueve el archivo sin confirmar).
- **AC1** Dado que escaneo un recibo, cuando reviso el resultado,
  entonces veo una sugerencia de categoría razonable la mayoría de las
  veces (no 100% garantizado, es una heurística).
- **AC2** Rechazar la sugerencia guarda el documento sin categoría,
  igual que hoy.

**Nivel B — IA generativa on-device (Gemini Nano vía Android AICore),
sigue sin salir del dispositivo pero con una limitación real de
hardware:**

#### HU-60 — Flashcards de estudio generadas desde una nota

**Como** estudiante con una nota larga,
**quiero** que se generen automáticamente tarjetas de pregunta/
respuesta,
**para** repasar de forma activa sin armarlas a mano.

**Investigado**: el algoritmo extractivo de `TextSummarizer.kt` no
sirve para esto (elige oraciones existentes, no genera preguntas
nuevas) — se necesita capacidad *generativa* real. Android AICore
(Gemini Nano) permite generación de texto 100% on-device, pero
**solo en un subconjunto de dispositivos de gama alta con NPU
compatible** (no todos los Android soportan AICore hoy) — limitación
de hardware real, no de diseño.

- **RF1** Botón "Generar flashcards" en una nota, disponible solo en
  dispositivos con AICore/Gemini Nano disponible.
- **RF2** En dispositivos sin soporte, el botón no aparece (no un
  error) — se detecta la disponibilidad antes de ofrecer la función.
- **RNF1** 100% local en los dispositivos donde corre — no sale de la
  política de privacidad actual.
- **RNF2** Como es contenido generado (no extractivo), debe marcarse
  claramente como "generado por IA" y permitir edición manual antes de
  guardar — no asumir que el texto generado es perfecto.
- **AC1** Dado un dispositivo compatible con una nota de al menos 200
  palabras, cuando genero flashcards, entonces obtengo al menos 3
  pares pregunta/respuesta relacionados con el contenido real de la
  nota.
- **AC2** Dado un dispositivo sin AICore, cuando abro una nota,
  entonces no veo la opción (evita frustrar con un botón que fallaría).

**Nivel C — IA generativa en la nube (API externa), la más potente pero
requiere una decisión de negocio previa, no solo de código:**

#### HU-61 — Chat con el documento

**Como** usuario con un PDF largo,
**quiero** hacerle preguntas puntuales ("¿cuál es el monto total?"),
**para** encontrar información sin leer todo el documento.

**Requiere decisión de negocio ANTES de estimar en detalle** — esta
función, a diferencia de todas las anteriores, envía contenido del
documento a un servicio externo (una API de IA como Claude/Gemini) por
la naturaleza misma de lo que hace. Esto choca directo con dos cosas ya
publicadas:
1. La política de privacidad dice explícitamente "no leemos ni subimos
   el contenido de tus documentos... a ningún servidor" — habría que
   reescribir esa sección y, probablemente, el cuestionario de
   Seguridad de datos de Play Console (nueva categoría de dato
   compartido con un tercero).
2. Tiene **costo variable real por uso** (tokens de la API), a
   diferencia de todo lo demás en la app, que es costo fijo (ML
   Kit/AdMob) o nulo (procesamiento local).

- **RF1** (si se aprueba avanzar) Campo de pregunta libre en el Visor
  de PDF; el texto ya extraído del documento (mismo mecanismo que
  Lectura/Resumen) se envía junto con la pregunta a una API de IA.
- **RF2** Exclusivo Premium, con un límite diario incluso para
  Premium (para controlar costo), gating similar al ya usado para
  conversiones/escaneos.
- **RF3** Aviso explícito la primera vez que se usa: "Esta función
  envía el texto del documento a un servicio externo para poder
  responder tu pregunta" — consentimiento informado, no letra chica.
- **RNF1** Requiere actualizar Política de Privacidad y la declaración
  de Seguridad de datos de Play Console antes de publicar esta función
  (no después) — cambia una promesa ya hecha a los usuarios actuales.
- **RNF2** El documento no debe almacenarse del lado del servidor más
  allá de lo estrictamente necesario para responder (sin retención),
  y hay que confirmar la política de retención de datos del proveedor
  de IA elegido antes de integrarlo.
- **RNF3** Manejo de costo: límite diario configurable, y monitoreo de
  gasto real vs. presupuesto (alertas si el uso agregado supera un
  umbral).
- **AC1** Dado que pregunto algo sobre un PDF abierto, cuando la IA
  responde, entonces la respuesta se basa en el contenido real de ese
  documento (no inventa datos que no están).
- **AC2** Dado que alcancé el límite diario, cuando intento preguntar
  de nuevo, entonces veo un aviso claro (mismo patrón que
  `DailyLimitDialog` ya usado en Convertidor/Escáner).
- **AC3** La primera vez que se usa, el aviso de envío a un servicio
  externo se muestra y requiere aceptación explícita.

#### HU-62 — Extracción estructurada de datos de recibos/facturas

**Como** usuario que junta recibos para gastos,
**quiero** que la app extraiga automáticamente monto, fecha y
proveedor,
**para** llevar un registro sin tipear cada dato a mano.

**Mismo precedente y mismas 2 condiciones que HU-61** (privacidad +
costo) — se documenta por separado porque el caso de uso y el gating
son distintos (es una extracción puntual por documento, no una
conversación abierta).

- **RF1** Botón "Extraer datos" sobre un recibo/factura ya escaneado;
  envía el texto ya extraído (OCR local existente) a una API de IA
  pidiendo un formato estructurado (monto, fecha, proveedor, moneda).
- **RF2** El resultado se muestra editable antes de guardar (la IA
  puede equivocarse, especialmente con formatos de recibo poco
  comunes) y se puede exportar a CSV/Excel (reutiliza el Convertidor).
- **RNF1** Mismas RNF1/RNF2/RNF3 de HU-61 (actualizar política de
  privacidad, sin retención del lado del proveedor, control de costo).
- **AC1** Dado un recibo con monto y fecha legibles, cuando extraigo
  datos, entonces el monto y la fecha mostrados coinciden con el
  recibo real la mayoría de las veces (no 100%, es una extracción por
  IA, siempre editable).
- **AC2** El texto extraído por OCR (ya existente, local) no cambia —
  esta función es un paso adicional opcional sobre ese resultado, no
  un reemplazo.

## 35. Priorización recomendada de §34

No es una decisión mía tomarla sola — es una recomendación basada en
impacto/esfuerzo/riesgo, para que el usuario decida el orden real.

**Alta prioridad (alto impacto, esfuerzo/riesgo manejable):**
- HU-55 (Plan anual) — el modelo de datos ya está listo, es la de menor
  esfuerzo de todo Monetización con impacto directo en ingresos.
- HU-54 (Trial de Premium) — sube conversión, Play Billing ya lo
  soporta nativo.
- HU-48 (Recordar última página del Visor) — esfuerzo bajo, se nota en
  cada sesión de lectura.
- HU-47 (Marcadores de página) — esfuerzo bajo, complementa HU-48.
- HU-51 (Exportar nota a PDF/Word) — reutiliza infraestructura ya
  construida (`StudySummaryExporter`), esfuerzo bajo.
- HU-58 (Traducción on-device) — diferenciador real, sin costo, sin
  tocar la política de privacidad.

**Media prioridad:**
- HU-41/HU-42 (filtros de color + accesos directos del Escáner).
- HU-43/HU-44 (nuevos tipos de QR + historial).
- HU-49/HU-50/HU-52 (adjuntar imagen, vincular documento, recordatorio
  — Notas).
- HU-59 (clasificación automática de documento).
- HU-57 (mediación de AdMob) — esperar señal real de tráfico primero.

**Baja prioridad / requieren decisión previa antes de estimar:**
- HU-45 (QR con logo) — a confirmar si la librería actual lo soporta.
- HU-46 (anotaciones del Visor) — alto valor pero épica grande, requiere
  diseño previo de cómo se persiste una anotación.
- HU-53 (extraer imágenes de PDF) — única novedad real de Herramientas
  PDF, pero de nicho.
- HU-56 (referidos) — requiere decidir si se introduce backend +
  identidad de usuario antes de estimar en serio.
- HU-60 (flashcards con IA on-device) — limitado a hardware con
  AICore, adopción real incierta hoy.
- HU-61/HU-62 (IA en la nube) — **no estimar esfuerzo de implementación
  todavía**; lo primero es una decisión de negocio consciente: ¿vale la
  pena romper la promesa de "100% local" ya publicada y asumir costo
  variable por uso, a cambio de estas 2 funciones? Si la respuesta es
  sí, recién ahí se define alcance/proveedor/límites en detalle.

## 36. Ajuste — Más movimiento en el fondo animado (refinamiento del ítem 26)

Pedido explícito del usuario 2026-09-10: el fondo animado con el color
de acento (implementado 2026-09-06, ver §26 y `DocuSmartAnimatedBackground.kt`)
se ve **demasiado sutil** — quiere que tenga más movimiento perceptible.

**Investigado**: `DocuSmartAnimatedBackground.kt` ya tiene 4 formas
(`AccentSquare`) que derivan con `drift()`, un `animateFloat` infinito
0→1→0 (`RepeatMode.Reverse`). Valores actuales:
- 3 formas de fondo con ciclos de **26s/30s/23s** y desplazamiento de
  solo **14-22dp** + rotación de **6-16°**.
- 1 "destello" con ciclo de pulso de **12s** (solo cambia opacidad, no
  se mueve).
- Ya respeta accesibilidad: si el sistema tiene "Quitar animaciones"
  activado, las formas quedan quietas (`reduceMotion`) — este
  comportamiento no debe tocarse.

Con ciclos tan largos (23-30 segundos) y desplazamientos tan chicos
(equivalentes a unos pocos milímetros en pantalla), el movimiento real
es casi imperceptible salvo mirando fijo un buen rato — coincide con lo
reportado por el usuario.

#### HU-63 — Fondo animado con movimiento más perceptible

**Como** usuario que activó el fondo animado,
**quiero** notar el movimiento sin tener que mirar fijo,
**para** que se sienta la app "viva" como se pretendía al pedir esta
función originalmente (§26).

- **RF1** Reducir la duración de los ciclos de `drift()` (hoy 23-30s)
  a un rango que se note sin resultar frenético — punto de partida
  sugerido: 10-14s por forma, manteniendo duraciones distintas entre
  las 3 formas para que no se sincronicen visualmente entre sí (mismo
  criterio de diseño ya usado hoy).
- **RF2** Aumentar el rango de desplazamiento (`x`/`y`, hoy 14-22dp) y
  de rotación (hoy 6-16°) de cada `AccentSquare`, lo suficiente para
  que el ojo perciba el recorrido sin que las formas lleguen a tapar
  contenido real ni salirse notoriamente del área visible.
- **RF3** Mantener el "destello" (`pulse`) como variación de opacidad
  únicamente, o evaluar sumarle también un desplazamiento leve si al
  probar en dispositivo se ve bien — a decidir en la implementación,
  no es un requisito estricto.
- **RNF1** No debe afectar el rendimiento percibido de la app — las
  animaciones corren en una capa decorativa detrás del contenido
  (`MainActivity`), deben seguir sin interceptar toques ni causar
  jank visible en dispositivos de gama media (verificar en el Moto E22,
  el dispositivo más lento con el que se probó §26 originalmente).
- **RNF2** El comportamiento de accesibilidad (`reduceMotion`, formas
  quietas si el sistema tiene animaciones desactivadas) no debe
  cambiar.
- **RNF3** El interruptor "Apagar fondo animado" ya existente en
  Ajustes (`ThemeManager.animatedBackgroundEnabled`) sigue funcionando
  igual — este ajuste solo cambia la intensidad del movimiento cuando
  está activado, no agrega ni quita el interruptor.
- **AC1** Dado que tengo el fondo animado activado, cuando miro la
  pantalla sin fijarme especialmente, entonces percibo el movimiento
  de las formas de fondo (a diferencia de hoy, que requiere mirar
  fijo).
- **AC2** Dado que el sistema tiene "Quitar animaciones" activado,
  cuando abro la app, entonces el fondo se sigue viendo quieto (sin
  regresión).
- **AC3** Dado que apago el fondo animado en Ajustes, cuando navego por
  la app, entonces no se ve ningún movimiento (sin regresión del
  interruptor existente).
- **AC4** Ninguna forma tapa contenido real (texto, botones) en ningún
  punto de su recorrido, en las pantallas principales (Home,
  Biblioteca, Convertidor).

**Prioridad recomendada**: Media-Alta — es un ajuste puntual (tuning de
valores existentes, no una función nueva), de esfuerzo y riesgo bajos,
sobre una función que el usuario ya consideró importante al pedirla
originalmente.

## 37. Modo Estudio — Selector de voz con avatar, nombre y muestra de audio

Pedido explícito del usuario 2026-09-16, a partir de **feedback real de
testers de la prueba cerrada** de Play Console: a los testers les gusta
Modo Estudio, pero encuentran confuso el flujo de "Leer con voz".

**Investigado**: la app **ya tiene** un selector de voz
(`VoiceSelectorDialog`, `StudyScreen.kt`), agregado el 2026-09-12 a
partir de una ronda anterior de feedback de testers que pedía "más
opciones de voz". Hoy es un `AlertDialog` con una lista de
`RadioButton`, cada uno con la etiqueta `"Voz %1$d — %2$s (%3$s)"`
(ej. "Voz 1 — Español (España) (Muy alta)") — **sin ícono, sin nombre
propio, sin indicación de género, y sin forma de escuchar una muestra
antes de elegir**: tocar el radio button aplica la voz de inmediato.
Esta HU es una **mejora sobre ese diálogo existente**, no un selector
nuevo desde cero.

- La lista de voces ya se arma filtrando `TextToSpeech.getVoices()` por
  idioma actual y descartando las que requieren conexión de red, y ya
  se ordena por calidad (`Voice.quality`) — esa lógica de descubrimiento
  no cambia.
- **Limitación real de la API de Android**: `android.speech.tts.Voice`
  **no expone el género de la voz** como campo público — no hay forma
  confiable y portable entre fabricantes/motores TTS de saber si una
  voz es "masculina" o "femenina" a partir de metadata oficial. La
  única opción realista es una asignación curada por la propia app
  (nombre + ícono + género visual) por cada voz de la lista, no una
  lectura directa del sistema.
- El ícono de "Elegir voz" en la barra de Lectura hoy solo aparece si
  hay más de una voz disponible (`availableVoices.size > 1`) — a
  reconsiderar en esta mejora, ver RF4.
- Persistencia ya existente (`StudyVoicePreference`, SharedPreferences)
  guarda solo el `name` técnico de la voz (ej.
  `"es-es-x-eef-local"`) — sigue siendo la clave estable para mapear
  siempre la misma voz técnica al mismo nombre/ícono curado (AC4).

#### HU-64 — Selector de voz con personaje visual y prueba de audio

**Como** usuario de Modo Estudio que quiere elegir cómo se lee su
documento en voz alta,
**quiero** ver cada voz disponible con un ícono de personaje, un
nombre, y poder escuchar una muestra antes de elegirla,
**para** decidir con confianza en vez de adivinar qué significa "Voz
1", "Voz 2".

- **RF1** Cada voz de la lista muestra un ícono/avatar de personaje
  (ej. un `Icon`/ilustración simple, no necesariamente arte
  ilustrado complejo) y un nombre propio (ej. "Sofía", "Mateo") en vez
  de "Voz N".
- **RF2** Ya que la API no expone género real, la app **asigna** un
  nombre + avatar con una identidad de género visual (femenina/
  masculina) a partir de una lista curada, de forma **determinística**
  por el `name` técnico de la voz (ej. hash estable del `name` técnico
  → índice en la lista curada) — así la MISMA voz del dispositivo
  siempre muestra el MISMO nombre/avatar entre sesiones (AC4), sin
  pretender que la app "sabe" el género real de la síntesis.
- **RF3** Cada fila de la lista tiene un botón "Escuchar muestra" que
  reproduce una frase corta de marca (ej. "Hola, soy Sofía. Así vas a
  escuchar tus documentos en DocuSmart.") usando ESA voz puntual, sin
  cambiar todavía la voz seleccionada para la lectura real.
- **RF4** Seleccionar una voz de la lista sigue aplicándola a la
  lectura, igual que hoy (sin regresión) — evaluar en la
  implementación si, dado que ahora la muestra de audio aporta valor
  aunque haya una sola voz instalada, conviene quitar la condición
  actual `availableVoices.size > 1` que oculta el botón "Elegir voz"
  cuando solo hay una opción.
- **RF5** El texto de la muestra de audio debe existir en los 12
  idiomas soportados por la app (mismo criterio de i18n ya usado en el
  resto del proyecto), no solo en español.
- **RNF1** Si hay una lectura en curso cuando se abre el selector,
  reproducir una muestra no debe mezclarse audiblemente con esa
  lectura ni dejarla en un estado inconsistente al cerrar el diálogo
  (pausar la lectura en curso mientras el diálogo está abierto es la
  opción más simple, a confirmar en la implementación).
- **RNF2** Debe funcionar con cualquier cantidad de voces que exponga
  el dispositivo (desde 1 hasta 10+, varía por fabricante/motor TTS
  instalado) sin romper el layout de la lista.
- **AC1** Dado que abro el selector de voces en Lectura, veo una lista
  donde cada voz tiene un ícono de personaje, un nombre, y un botón
  para escuchar una muestra.
- **AC2** Dado que toco "Escuchar muestra" en una voz, escucho una
  frase corta con esa voz, y la voz seleccionada para lectura NO
  cambia hasta que explícitamente elijo esa fila.
- **AC3** Dado que elijo una voz de la lista, la lectura posterior usa
  esa voz (sin regresión del comportamiento actual).
- **AC4** Dado que cierro y reabro la app (o el selector) más tarde, la
  misma voz técnica del dispositivo muestra siempre el mismo nombre e
  ícono que mostró antes — no se reasigna al azar entre sesiones.
- **AC5** En un dispositivo con una sola voz instalada para el idioma
  actual, el selector sigue siendo usable y consistente (ver RF4).

**Plan de pruebas sugerido** (mismo criterio ya usado en el resto del
proyecto):
- Unitarias: función pura de asignación determinística voz-técnica →
  (nombre, ícono, género visual) — dado el mismo `name` técnico, mismo
  resultado siempre; distintos `name` se reparten razonablemente entre
  las identidades curadas.
- UI: `VoiceSelectorDialog` renderiza N filas con ícono+nombre+botón de
  muestra para una lista mock de voces; tocar "Escuchar muestra" invoca
  el callback correcto sin disparar el callback de selección.
- Dispositivo real: verificar con las voces reales instaladas (varía
  por fabricante) que la muestra suena, que elegir una voz sigue
  funcionando, y que reabrir el selector no reordena/renombra las
  voces ya vistas.

**Prioridad recomendada**: Media — mejora de UX puntual y acotada (un
diálogo ya existente), pedida por usuarios reales de la prueba cerrada,
de riesgo bajo (no toca la lógica de síntesis de voz ya probada, solo
la presentación del selector).

**Implementado y verificado en dispositivo real (2026-09-16, Motorola
Edge 30 Neo)**: 3 tests unitarios nuevos para `personaForVoice()`
(determinismo, distribución, nunca vacío). En vivo, con las voces reales
del dispositivo (6 voces instaladas): el diálogo muestra avatar+nombre+
idioma/calidad+botón de muestra por fila (AC1); tocar "Escuchar muestra"
reproduce la frase de marca con esa voz puntual sin cambiar la voz
seleccionada para lectura, y el botón vuelve solo a su estado normal al
terminar el audio (AC2); elegir una voz de la lista sigue aplicándola a
"Leer todo" (AC3); reabrir el selector más tarde en la misma sesión
muestra exactamente el mismo nombre/avatar por voz que la primera vez
(AC4); abrir el selector mientras el documento se estaba leyendo pausó
la lectura antes de mostrar el diálogo (RNF1). Sin crashes en logcat en
toda la sesión de pruebas.
- **Hallazgo operativo, no un bug de esta HU**: a mitad de la
  verificación el dispositivo rotó (accidental, `accelerometer_rotation`
  estaba en 1) y Modo Estudio perdió el documento cargado -- el estado
  de lectura (`documentUri`, voz seleccionada, etc.) vive en `remember{}`
  local a la pantalla, no en un ViewModel ni `rememberSaveable`, así que
  cualquier cambio de configuración (rotación, idioma del sistema, etc.)
  lo reinicia. Preexistente, fuera del alcance de HU-64 -- queda anotado
  como posible ítem futuro de robustez si se repite en la prueba cerrada.

## 38. Agenda/Calendario — Eventos, reuniones y entregas con recordatorio

Pedido explícito del usuario 2026-09-16, mismo origen que §37 (feedback
real de testers de la prueba cerrada): quieren poder guardar eventos,
reuniones, entregas y fechas importantes, y que la app se los recuerde.

**Investigado — antes de escribir código hace falta resolver 2
decisiones de producto** (ver "Decisiones pendientes" al final):

- **No existe WorkManager ni AlarmManager en el proyecto** (confirmado
  por búsqueda exhaustiva) — esta HU sería la primera vez que la app
  programa algo para el futuro sin que esté abierta. El mismo vacío ya
  estaba anotado para la HU-52 pendiente (recordatorio de repaso de
  notas, `NoteEntity.reminderAt` ya existe en el esquema pero sin
  scheduler) — **ambas HUs necesitan la misma infraestructura de
  programación de recordatorios**, tiene sentido construirla una sola
  vez y que las dos la reutilicen.
- **Sí existe** un patrón de notificación reutilizable para el
  *disparo* del recordatorio: `PomodoroTimerService.kt` ya crea un
  canal de notificación y llama `NotificationManager.notify(...)`, y
  `AndroidManifest.xml`/`StudyScreen.kt` ya tienen el permiso
  `POST_NOTIFICATIONS` y su flujo de solicitud en tiempo de ejecución
  funcionando en producción. Falta solo la parte de *programar* ese
  disparo para una fecha/hora futura arbitraria.
- **Sí existe** un patrón de selector de fecha/hora reutilizable tal
  cual: `QrContentForms.kt` (creador de QR tipo "Evento", HU-43) ya usa
  `DatePicker`/`TimePicker` de Material3 sobre `LocalDateTime`, con dos
  botones (fecha/hora) que abren cada uno su diálogo. La Agenda debería
  reusar exactamente este patrón en vez de crear uno nuevo.
- **Esquema de datos**: nueva tabla Room (ej. `agenda_events`),
  `MIGRATION_5_6` (`DocuSmartDatabase.kt` pasa de versión 5 a 6),
  siguiendo el mismo patrón de migración explícita ya usado 3 veces en
  el proyecto (nunca `dropAllTables`, para no perder datos reales de
  usuarios que ya están usando la app en producción).
- **Ubicación en la navegación**: hoy Modo Estudio tiene sus pestañas
  (Lectura/Notas/Pomodoro, más una 4ª "Resumen" ya armada pero oculta
  para el primer release) como estado interno de una sola pantalla, no
  como rutas de navegación separadas. Reuniones/entregas no están
  necesariamente ligadas a "estudiar un documento" — el precedente del
  propio proyecto (Papelera, QR, Herramientas PDF son rutas de
  navegación de primer nivel, independientes de Modo Estudio) sugiere
  que Agenda encaja mejor como **sección propia de primer nivel** que
  como una pestaña más dentro de Modo Estudio. Queda como decisión a
  confirmar con el usuario (ver más abajo), no una conclusión cerrada.

#### HU-65 — Agenda con recordatorios de eventos

**Como** usuario que organiza su trabajo con DocuSmart,
**quiero** guardar eventos, reuniones y fechas de entrega con un
recordatorio,
**para** no depender de otra app de calendario para lo que ya gestiono
en DocuSmart.

- **RF1** Nueva pantalla propia "Agenda" (ruta de `NavRoutes` separada,
  diseño propio — no una pestaña más del `TabRow` de Modo Estudio),
  alcanzable desde una tarjeta/botón de entrada dentro de la superficie
  de Modo Estudio, donde crear un evento con: título (obligatorio),
  descripción (opcional), fecha y hora (obligatorio, reutilizando el
  patrón de `QrContentForms.kt`).
- **RF2** Vincular el evento a un documento existente de la Biblioteca
  (mismo mecanismo ya usado por Notas en HU-50 —
  `AppLibraryPickerViewModel`), para casos como "entrega del informe
  X" apuntando al PDF real — incluido en el alcance de la v1.
- **RF3** Editar y eliminar un evento ya creado.
- **RF4** Lista de próximos eventos ordenada por fecha, con indicación
  visual de "hoy" / "próximo" / "vencido".
- **RF5** Recordatorio configurable: notificación local en el momento
  exacto del evento, o con antelación (ej. 15 min / 1 hora / 1 día
  antes, a definir el set de opciones) — funciona aunque la app esté
  cerrada.
- **RNF1** Persistencia local en Room, nueva `MIGRATION_5_6` (ver
  arriba) — nunca depende de que la app esté abierta ni de conexión a
  internet (100% local, mismo criterio de privacidad que el resto de
  la app).
- **RNF2** Las notificaciones programadas deben sobrevivir un reinicio
  del dispositivo (rearmar los recordatorios pendientes al recibir
  `BOOT_COMPLETED`, ya que un `AlarmManager`/`WorkManager` programado
  se pierde si el dispositivo se apaga y prende de nuevo).
- **RNF3** Reutiliza el flujo de permiso `POST_NOTIFICATIONS` ya
  probado en producción (Pomodoro) — no duplicar la lógica de
  solicitud.
- **AC1** Dado que creo un evento con fecha/hora futura, lo veo en la
  lista de Agenda ordenado correctamente.
- **AC2** Dado que llega la hora programada (o la antelación elegida)
  con la app cerrada, recibo una notificación local con el título del
  evento.
- **AC3** Dado que toco la notificación, la app abre directo el
  detalle del evento (o el documento vinculado, si lo tiene).
- **AC4** Dado que edito o elimino un evento con recordatorio ya
  programado, el recordatorio viejo se cancela/actualiza — nunca quedan
  dos notificaciones para el mismo evento ni una notificación de un
  evento ya borrado.
- **AC5** Dado que reinicio el dispositivo con eventos pendientes, los
  recordatorios se siguen disparando en su fecha/hora correcta después
  del reinicio.
- **AC6** Dado que vinculo un evento a un documento de la Biblioteca y
  luego ese documento se renombra o se mueve a Carpeta Segura, el
  evento no queda roto (mismo criterio de mantenimiento de identidad ya
  centralizado en `DocumentIdentityMaintenance`).

**Plan de pruebas sugerido**:
- Unitarias: cálculo de la próxima fecha de disparo dado
  evento+antelación; lógica de "hoy/próximo/vencido" de la lista;
  cancelación/reprogramación al editar o borrar un evento con
  recordatorio activo.
- Integración con Room: DAO real (`Room.inMemoryDatabaseBuilder`) para
  CRUD de eventos, igual patrón que `NoteDaoTest`/`PageBookmarkDaoTest`.
- Dispositivo real: crear un evento con recordatorio a 1-2 minutos,
  cerrar la app completamente, confirmar que la notificación llega a
  horario; forzar un reinicio del dispositivo con un recordatorio
  pendiente y confirmar que sigue funcionando después; revisar logcat
  por crashes en todo el flujo, como en el resto de HUs de esta
  sesión.

### Decisiones de producto (resueltas por el usuario, 2026-09-16)

1. **Precisión del recordatorio: alarma exacta.** Se usa
   `AlarmManager.setExactAndAllowWhileIdle()` (o el mecanismo exacto
   equivalente en versiones nuevas de Android) en vez de `WorkManager`,
   priorizando que el recordatorio suene justo a la hora programada.
   Implica declarar y pedir el permiso
   `SCHEDULE_EXACT_ALARM`/`USE_EXACT_ALARM` (Android 12+) — a
   documentar explícitamente en el formulario de seguridad de datos/
   declaración de permisos de Play Console antes de subir la versión
   que incluya esta HU, dado que la app está en prueba cerrada camino a
   producción.
2. **Ubicación en la navegación: pantalla propia, con entrada desde
   Modo Estudio, sin ser una pestaña del `TabRow`.** Decisión textual
   del usuario: *"dentro de modo estudio pero no como pestaña,
   desacoplalos y generá una nueva visual"*. Es decir: Agenda es su
   propia ruta/pantalla de `NavRoutes` (con su propio diseño, no
   forzada dentro del `TabRow` de Lectura/Notas/Pomodoro/Resumen), pero
   el punto de entrada para llegar a ella vive dentro de la superficie
   de Modo Estudio (ej. una tarjeta/botón destacado en la pantalla de
   Modo Estudio), no en el grid de accesos rápidos de Home ni en la
   barra de navegación inferior global.
3. **Alcance v1: incluye vínculo a documentos.** RF2 (vincular un
   evento a un documento de la Biblioteca) queda dentro de la primera
   versión, no se difiere.

**Prioridad recomendada**: Media-Alta — pedido por usuarios reales,
decisiones de producto ya resueltas. Sigue siendo una épica genuina
(primera infraestructura de recordatorios programados del proyecto) —
evaluar si conviene implementarla junto con HU-52 (que necesita la
misma base de recordatorios) en vez de por separado, ya que ambas
comparten el mismo mecanismo de alarma exacta + notificación +
reprogramación tras reinicio.

### Implementado y verificado en emulador (2026-09-17) — falta confirmar en dispositivo real

RF1-RF5 y RNF1-RNF3 completos: pantalla propia "Agenda" (entrada desde
Modo Estudio), CRUD de eventos con Room (`MIGRATION_5_6`), vínculo a
documento (RF2, reutiliza `AppLibraryPickerViewModel` como Notas),
recordatorio con `AlarmManager.setExactAndAllowWhileIdle()` (con
degradación a alarma inexacta si falta el permiso), reprogramación tras
reinicio (`BootRescheduleReceiver`) y permiso `POST_NOTIFICATIONS`
propio de la pantalla (ver bug real corregido abajo).

Verificado en el emulador `DocuSmart_Test` con evidencia directa de
`dumpsys alarm`/`dumpsys notification` (no solo inspección visual):

- **AC1**: evento creado aparece en la lista, ordenado por fecha.
- **AC2**: la alarma programada (`dumpsys alarm`, `origWhen` exacto)
  disparó la notificación real a la hora programada.
- **AC3**: al tocar la notificación, la app abre directo el diálogo de
  edición del evento correcto (mismo id).
- **AC4**: editar el recordatorio cancela la alarma vieja y programa
  exactamente una nueva (sin duplicados); eliminar el evento cancela su
  alarma (`Reason=alarm_cancelled`).
- **AC5**: tras un `adb reboot` del emulador, la alarma se reprogramó
  correctamente (mismo horario). El primer intento de
  `BootRescheduleReceiver` fue matado por Android ("bg anr") porque el
  emulador (2GB RAM) tardó demasiado en levantar el proceso durante el
  arranque masivo de apps tras el reinicio — el propio sistema
  reintregó el broadcast en un arranque posterior que sí completó a
  tiempo. Artefacto conocido de esta máquina de pruebas con poca RAM,
  no un bug de la app — igual queda pendiente confirmarlo en el
  dispositivo real, que no tiene esa restricción de memoria.
- **AC6**: cubierto por `DocumentIdentityMaintenanceTest.kt` (unitario;
  mismo mecanismo ya probado por Notas/HU-50).

**Bug real encontrado y corregido antes de dar esto por cerrado**: el
pedido de permiso `POST_NOTIFICATIONS` (RNF3) solo existía dentro de la
pestaña Pomodoro de Modo Estudio (`StudyScreen.kt`). Un usuario que
entra a Agenda sin haber abierto nunca Pomodoro nunca veía ese diálogo
en Android 13+, y sus recordatorios se programaban pero la notificación
jamás se mostraba (`NotificationManager.notify()` no falla, solo no
hace nada sin el permiso). Se agregó el mismo pedido, con el mismo
patrón, al entrar a `AgendaScreen.kt`.

**Pendiente**: confirmación final en el Motorola Edge 30 Neo real (el
usuario difirió esta prueba explícitamente mientras el equipo no estaba
disponible) antes de considerar la HU-65 cerrada de forma definitiva.

**Seguimiento mismo día (2026-09-17)**: pedido explícito del usuario --
mientras esperaba la prueba en dispositivo real, pidió agregar dentro de
Agenda una vista de calendario (mensual, con puntos en los días con
eventos, decisión confirmada por el usuario entre 3 opciones), cambiar el
título a "Agenda y calendario", y aplicar el banner de anuncios + banner
azul con título (mismo criterio que el resto de las pantallas de la app,
ver `DocuSmartScreenHeader`/`DocuSmartTopBanner`) -- alternable con la
lista existente vía `TabRow` (Lista/Calendario). Se implementa como parte
de la misma HU-65 (no como HU nueva) ya que es un ajuste sobre una
pantalla que todavía no se fusionó a `main`.

Verificado en dispositivo real (Motorola Edge 30 Neo): título/subtítulo,
tabs Lista/Calendario, grilla mensual (offset de días de la semana
correcto, "hoy" resaltado, punto en días con evento, selección actualiza
el detalle del día con el mismo `AgendaEventCard` reutilizado de la
lista), navegación entre meses.

**Segundo bug real encontrado en la misma prueba de dispositivo**: al
crear el evento de prueba en el Motorola, `dumpsys alarm`/`appops`
confirmó que el permiso especial `SCHEDULE_EXACT_ALARM` estaba denegado
(`Uid mode: SCHEDULE_EXACT_ALARM` no estaba en `allow`) -- el recordatorio
igual sonó porque `ReminderScheduler` ya degradaba con elegancia a una
alarma inexacta (RF5), pero sonó ~4-5 minutos tarde y la app nunca le
avisaba al usuario que podía corregirlo. Se agregó un banner en
`AgendaScreen.kt` (mismo patrón que Card/Surface del resto de la app,
visible solo si `AlarmManager.canScheduleExactAlarms()` es `false` en
API 31+) con un botón que abre directamente
`Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM`, y se refresca el estado
al volver a la pantalla vía `ReloadOnScreenResume` (mismo mecanismo que
`LibraryScreen` para permisos). Verificado en el propio Motorola: el
botón abre la pantalla del sistema correcta, y el banner desaparece al
volver una vez concedido el permiso.

Corregido además un lint real encontrado en esta misma pasada
(`NonObservableLocale`): `AgendaCalendarView.kt` usaba
`Locale.getDefault()` directamente dentro de composables para los
nombres de mes/día de la semana -- no es observable por Compose, así que
un cambio de idioma del sistema sin recrear la Activity dejaría el
calendario en el idioma viejo. Se resolvió con
`LocalConfiguration.current.locales[0]` (mismo fix ya usado en
`PremiumScreen.kt`/`SettingsScreen.kt` para el mismo lint).

## 39. Ajustes — Rediseño del modal de "Idioma" (tarjetas degradadas + color de acento)

Pedido explícito del usuario 2026-09-16 (mientras se esperaba la prueba
de HU-65 en dispositivo real), con una imagen de referencia adjunta.

**Corrección sobre lo escrito originalmente acá**: al leer el código
real (no de memoria) resultó que el modal actual NO tenía tarjetas
degradadas -- era una lista simple de filas (emoji de bandera + nombre +
check al final, fondo plano/transparente). La descripción inicial de
este ítem estaba mal.

**✅ Implementado y verificado en dispositivo real (Motorola Edge 30
Neo) 2026-09-17**, tras varias idas y vueltas de diseño con el usuario
(quedan documentadas porque cada una descartó un enfoque razonable pero
no era lo que el usuario tenía en mente -- útil si se vuelve a tocar
este componente):

1. Primer intento: grilla de 2 columnas (confirmado por el usuario) con
   degradado derivado 100% del Color de acento, sin bandera visible.
   Rechazado: *"no pero el degradado es también con la bandera del
   idioma"*.
2. Segundo intento: degradado diagonal de 3 colores propios de cada
   idioma (inspirados en su bandera, codificados a mano en
   `flagGradientColors()`), sin usar el acento. El usuario compartió de
   nuevo la imagen de referencia y pidió seguirla al pie de la letra.
3. Tercer intento: bandera (emoji real de `AppLanguage.flagEmoji`) a
   sangre de fondo con una máscara de desvanecimiento
   (`BlendMode.DstIn`) hacia el texto, calcado del código Kotlin
   completo que compartió el usuario (`LanguageGridSheet`/
   `LanguageTile`) -- incluye pasar de `Dialog` a `ModalBottomSheet`.
   El usuario confirmó que se veía bien pero pidió un ajuste de layout:
   *"coloca el texto en fila junto a la bandera con una separación"*.
4. Cuarto intento: bandera + texto en una sola fila (con
   `Arrangement.spacedBy`), pero al quitar la máscara de desvanecimiento
   se perdió el degradado -- el usuario pidió recuperarlo: *"no es
   aplicando un degradado lineal combinado con la propiedad de fondo o
   usar una máscara de desvanecimiento"*.
5. **Diseño final**: bandera (emoji) y texto en una fila con separación
   (`Row` + `Arrangement.spacedBy(10.dp)`), sobre una tarjeta cuyo
   **fondo** es un `Brush.linearGradient` -- tenue
   (`surfaceVariant` → `surfaceVariant` con alpha) para las no
   seleccionadas, marcado (`primaryContainer` → `primary` con alpha)
   para la seleccionada, ambos extremos animados con
   `animateColorAsState` al cambiar la selección. Badge circular de
   check (relleno + borde animados, `AnimatedVisibility` con
   scale+fade para el ícono), borde de la tarjeta tintado con el acento
   solo cuando está seleccionada, y escala sutil al presionar
   (`collectIsPressedAsState` + `animateFloatAsState`) como equivalente
   táctil de "hover". La hoja pasó de `Dialog` a `ModalBottomSheet`
   (esquinas superiores redondeadas), con un botón "Cerrar" a todo el
   ancho al final, siguiendo el código de referencia del usuario.
- Se agregó `regionLabel` a `AppLanguage` (`LanguageManager.kt`): nombre
  del país/región en el propio idioma (mismo criterio que `nativeLabel`
  ya usado), mostrado como subtítulo de cada tarjeta -- ej. "Español /
  España", "Català / Espanya", "日本語 / 日本". Con `maxLines=1` +
  `TextOverflow.Ellipsis` en ambas líneas para nombres largos ("United
  Kingdom" trunca a "United Kingdo…" en vez de partir la palabra a la
  mitad, bug real encontrado en la propia revisión visual).
- **Nota para el futuro**: el proyecto no tiene assets de banderas
  propios (imágenes/vectores) -- se usa el emoji Unicode de
  `flagEmoji`, que en este dispositivo (Motorola, fuente de emojis de
  Android) se renderiza como una bandera ondeante con buena calidad
  visual. Si en otro dispositivo/fuente de emoji se ve peor, la mejora
  sería agregar 12 drawables de bandera propios.
- Verificado en vivo en el Motorola Edge 30 Neo: las 12 tarjetas se ven
  y traducen correctamente (probado cambiando a 中文 y a Português, con
  el modal reabriéndose completamente traducido y el check en la
  tarjeta correcta), scroll dentro de la grilla funciona, degradado de
  fondo visible en ambos estados, y el cambio de idioma se aplica
  correctamente en ambas direcciones.
- Gauntlet completo (`compileDebugKotlin`+`detekt`+`lintDebug`+
  `testDebugUnitTest`) en verde.
- **Todavía sin fusionar** -- pendiente de "¿Fusiono y hago push?".

## 40. Modo Estudio — Personajes/avatares ilustrados para las voces (reemplazo del círculo de color)

Pedido explícito del usuario 2026-09-16 (mismo momento que el ítem
anterior), con dos imágenes de referencia adjuntas (mosaico de rostros
femeninos y masculinos con un ícono de "onda de audio" superpuesto).
Refina la HU-64 ya implementada y verificada (`VoicePersona.kt`,
`VoiceSelectorDialog.kt`): hoy cada voz muestra un círculo de color +
inicial/ícono genérico; el usuario quiere un **personaje ilustrado**
distinto por voz (uno por cada una de las 10 personas de
`VOICE_PERSONAS`, coherente con su género), en vez del círculo de color
plano. El usuario dejó la ubicación exacta a criterio de quien
implemente ("coloca el personaje donde consideres que es adecuado") --
el reemplazo natural es el avatar circular que hoy ocupa
`VoiceSelectorDialog.kt` (lista de voces) y, si alcanza el espacio, el
mismo avatar en miniatura junto al nombre de la voz activa en el header
de Lectura.

**Bloqueante real antes de implementar (no es una decisión de producto,
es una restricción práctica)**: las imágenes de referencia que compartió
el usuario son fotografías realistas de personas (estilo stock
fotográfico/IA fotorrealista) -- no se puede confirmar que el usuario
tenga licencia de uso comercial sobre esas fotos concretas para
distribuirlas dentro de una app publicada en Play Store, y usar rostros
fotorrealistas de personas que no existen (o que sí existen, si son
fotos de banco de imágenes) trae su propio riesgo de derechos de imagen.
**Antes de generar/incluir los assets finales hay que confirmar con el
usuario** si: (a) tiene licencia comercial verificable de esas imágenes
concretas para usarlas tal cual, o (b) prefiere que se generen
ilustraciones propias (vectoriales/flat, estilo consistente con el resto
del ícono set de Material Rounded que ya usa toda la app) inspiradas en
la composición de la referencia (rostro + onda de audio) pero sin
reutilizar las fotos entregadas -- la opción (b) es la recomendada por
ser la única sin riesgo de derechos y además consistente con el resto
del lenguaje visual vectorial de DocuSmart (íconos Material, no fotos,
en toda la app).

**✅ Implementado y verificado en dispositivo real (Motorola Edge 30
Neo) 2026-09-17.** El bloqueante de arriba se resolvió: las imágenes de
referencia no eran fotos de bancos de imágenes ni de personas reales --
**el usuario las generó él mismo con Gemini** (IA generativa), así que
no hay riesgo real de derechos de imagen de terceros. El usuario recortó
manualmente 12 imágenes individuales (6 femeninas + 6 masculinas,
formato avatar circular con fondo decorativo e ícono de onda de audio
ya incluido) y las dejó en `Descargas/personajes/` -- se usaron las
primeras 5 de cada set (las 2 restantes quedaron sin usar, de sobra).

- Se agregó `avatarDrawableRes: Int` a `VoicePersona`
  (`VoicePersona.kt`), uno por persona, manteniendo `avatarColor` como
  anillo de color alrededor del avatar (mismo criterio de identidad por
  color que ya tenía cada personaje, en vez de reemplazarlo del todo).
- En `VoiceSelectorDialog` (`StudyScreen.kt`) el `Box` con
  `Icon(Face)` + fondo de color se reemplazó por un `Image` circular
  (`ContentScale.Crop` + `Modifier.clip(CircleShape)` +
  `Modifier.border(persona.avatarColor)`).
- **Las 10 imágenes se convirtieron a WebP** (pedido explícito del
  usuario, "formato más liviano sin perder calidad") -- de ~537 KB
  total en PNG a **~80 KB total** (calidad 90, ~85% más liviano,
  sin pérdida perceptible). Viven en `res/drawable-nodpi/` (mismo
  directorio que ya usaban otros assets de imagen fija de la app --
  `onboarding_bg_*.jpg`, `premium_trial_bg.jpg` -- no se generan
  variantes por densidad porque son imágenes ya rasterizadas a un
  tamaño fijo, igual criterio que esos otros assets).
- Gauntlet completo (`compileDebugKotlin`+`detekt`+`lintDebug`+
  `testDebugUnitTest`) en verde.
- Verificado en vivo en el Motorola Edge 30 Neo: las 10 personas
  muestran su avatar real con el anillo de color correspondiente en el
  selector de voz, sin regresión en el resto del diálogo (reproducir
  muestra, seleccionar voz).
- **Todavía sin fusionar** -- pendiente de "¿Fusiono y hago push?".
