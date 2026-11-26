# Auditoría y plan de paridad: app Android, web y Desktop

Fecha: 2026-11-26. Estado: **decisiones 1 y 3 tomadas el 2026-11-26** (arquitectura híbrida; la web adoptará los límites de la app al final). El resto sigue pendiente. No cambia código; sirve para decidir y planear.

Fuentes (todo leído de `main`, sin mi trabajo en curso): app Android `main` en `732c62f` (versión 1.1.6, código 12, en producción); web `docusmart-web` en `main`/`preproduccion` (`8bad35e0`), con `src/features/tools/core/catalog.ts`, `toolParity.json`, `docs/BACKLOG.md` (205 ítems: 118 hechos y 70 pendientes) y `.github/workflows/ci.yml`.

> **Orden de prioridades vigente (2026-11-25):** primero la web sin errores y su backlog, después tus pendientes (👤) y **solo al final la app**. Este documento prepara ese momento; no propone empezar la app ahora.

## 0. Decisiones tomadas

| # | Decisión | Fecha | Qué implica |
|---|---|---|---|
| 1 | **Arquitectura híbrida (opción B)** para las herramientas que faltan en la app: nativo para lo pequeño y lo que Android hace mejor; la misma herramienta de la web embebida para las medianas y grandes | 2026-11-26 | Hay que construir el anfitrión web de la app con las condiciones de la sección 7 |
| 3 | **La web adopta los límites diarios de la app**, pero **no ahora**: hoy la web sigue sin límites y se implementan cuando todo esté nivelado | 2026-11-26 | Sección 10: prerrequisitos, qué cuenta en cada herramienta y qué textos cambian |

## 1. Resumen

- La web tiene **71 herramientas** en su catálogo (66 con página propia en 12 idiomas). La app ofrece **27** a un usuario hoy.
- Las otras **44** se reparten así: **5 están programadas pero ocultas** en la app desde el primer lanzamiento, **4 ya tienen código en mis ramas** (sin fusionar y sin probar en teléfono), **2 son parciales** y **33 no existen**.
- Para llegar a la paridad hay que **construir 35 herramientas** (esfuerzo nativo: 12 pequeñas, 14 medianas, 9 grandes) y además revisar y mostrar las 5 ocultas y terminar y probar las 4 en rama.
- Hacerlo todo en Kotlin son unas **27 semanas** de trabajo asistido (estimación gruesa) y duplica el mantenimiento para siempre. Un enfoque **híbrido** (nativo para lo pequeño y lo que Android hace mejor; la misma herramienta de la web embebida para las medianas y grandes) baja a unas **9 a 10 semanas** y hace que esas herramientas se actualicen solas junto con la web. Esa fue la decisión más importante y ya está tomada: **híbrido** (sección 7).
- Ya hoy **web y Desktop se actualizan a la vez** (Desktop es la web instalada). Lo que no se actualiza a la vez son las tiendas. La sección 9 propone cómo acercar el resto: un tren de versiones, un manifiesto de herramientas publicado por la web, funciones que se encienden por Remote Config y pruebas cruzadas en el CI.

## 2. Qué tiene la app hoy (1.1.6)

**Herramientas PDF (15, todas visibles):** unir, dividir, comprimir, rotar, numerar páginas, marca de agua, reordenar, comparar, censurar, recortar, editar texto, firmar, llenar formulario, OCR y extraer imágenes. Más proteger y desbloquear con contraseña (pantalla aparte).

**Convertidor:** 17 conversiones programadas, **8 visibles** (imagen a PDF, JPG, PNG, WebP y BMP; PDF a imagen, texto y HTML) y **9 ocultas** (`HIDDEN_FROM_UI`: PDF a Word, Word a PDF/TXT/HTML, Excel a PDF/CSV/HTML, PowerPoint a PDF/TXT).

**Escanear y QR:** escáner de documentos de ML Kit (bordes, filtros, recorte; más completo que el de la web), lector de QR con cámara e historial, creador de QR con tipos de contenido y QR protegido con contraseña (compatible con la web).

**Funciones propias de la app** (sin equivalente en la web, por diseño): Biblioteca (Dispositivo, Mis archivos y Papelera, con «Agregar fotos/documentos» y «Vincular carpeta»), Visor de PDF, Word, Excel, PowerPoint, texto e imágenes con anotaciones, Carpeta Segura con PIN, Modo Estudio con lectura en voz alta (10 voces), notas, Pomodoro con servicio en segundo plano y estadísticas, Agenda con recordatorios, 12 idiomas, anuncios (AdMob) y Premium con Google Play (prueba automática de 3 días).

**Diferencias de comportamiento con la web que conviene decidir:**

- La app aplica **contadores diarios por herramienta** a los usuarios gratuitos (`DailyLimitManager`); la web comunica «sin límites diarios». Hay que decidir una sola política.
- **OCR:** la app reconoce solo alfabeto latino (ML Kit); la web reconoce los 12 idiomas del sitio (W-139).
- **PDF generados:** la web ya usa fuentes Unicode (W-136) y mejoró la fidelidad de Word a PDF y de PDF a Word (W-137, W-138). La app tiene esas conversiones **ocultas**, y no sabemos si mantienen la calidad actual: hay que probarlas antes de mostrarlas.
- Dos enlaces del contrato (`leer-codigo-qr` y `powerpoint-a-pdf`) figuran como «sí» en la web pero la app no los enlaza: el contrato y la app están **desfasados** (lo detectará la prueba del PR #167 cuando se actualice su copia del contrato).

## 3. Qué tiene la web (resumen)

El catálogo agrupa las herramientas en 12 categorías. Además de las herramientas, la web ya tiene funciones que la app no tiene o tiene a medias:

| Función de la web | Qué es | En la app |
|---|---|---|
| Flujos encadenados «¿Y ahora?» (W-110) | Al terminar una herramienta sugiere 3 y le pasa el resultado | No |
| Procesamiento por lotes (W-111) | Varios archivos a la vez y ZIP; etiqueta «Varios archivos» | Parcial (convertidor por lotes) |
| Sello de privacidad y aviso sin conexión (W-112) | «Tu archivo no sale de tu dispositivo» en cada herramienta | No |
| Antes y después (W-40 a W-44) | Vista previa del original y del resultado en cada herramienta | Revisar |
| Guardar en Drive y Dropbox directo (W-45, W-46) | Subida desde el navegador | No (la hoja de compartir de Android ya llega a Drive) |
| Asistente local (W-70, W-151) | Chatbot que entiende pedidos y ejecuta acciones | No (W-151.19 lo prevé para después de la paridad) |
| Guías con capturas (W-99 a W-102) | 12 idiomas, para buscadores | No aplica |
| Agenda con `.ics` y notas en Markdown (W-126) | Exportar e importar al calendario | Revisar |
| Resumir con IA local (W-142) | Resumen más completo | Parcial (resumen extractivo en Modo Estudio) |
| Cuenta con sincronización (W-94 a W-98) | Datos y Premium compartidos por cuenta de Google | No |
| iPhone y Desktop Mac/Linux (W-149, W-150) | Capacitor y Tauri, ya planeados | No aplica |
| Apariencia (W-148) | 16 degradados, 10 acentos, tamaño de letra | Parcial |

## 4. Conteo de herramientas por categoría

| Categoría | Herramientas | Sí | Ocultas | En rama | Parciales | No |
|---|---:|---:|---:|---:|---:|---:|
| Organizar | 5 | 5 | 0 | 0 | 0 | 0 |
| Editar | 6 | 6 | 0 | 0 | 0 | 0 |
| Seguridad | 6 | 2 | 0 | 0 | 0 | 4 |
| Documentos | 2 | 0 | 0 | 0 | 0 | 2 |
| Optimizar | 4 | 3 | 0 | 0 | 0 | 1 |
| Escanear | 3 | 2 | 0 | 0 | 1 | 0 |
| Convertir | 21 | 7 | 5 | 4 | 0 | 5 |
| Imagen | 8 | 0 | 0 | 0 | 0 | 8 |
| Multimedia | 8 | 0 | 0 | 0 | 0 | 8 |
| Archivos | 3 | 0 | 0 | 0 | 0 | 3 |
| QR | 2 | 2 | 0 | 0 | 0 | 0 |
| Texto | 3 | 0 | 0 | 0 | 1 | 2 |
| **Total** | **71** | **27** | **5** | **4** | **2** | **33** |

La matriz completa está en el anexo B. Nota: `texto/contrasena` (Generador de contraseñas) se cuenta en Seguridad aunque la web lo guarde en Texto.

## 5. Lo que falta, agrupado

**5.1 Programadas pero ocultas (5), esfuerzo S cada una:** solo hay que revisar la calidad frente a la web y mostrarlas. Cuidado: sus enlaces de la web ya apuntan a ellas.

- **PDF a Word** — Oculta, esfuerzo S. Está programada y la app la oculta desde el primer lanzamiento; revisar y mostrar. La web la hace con diseño (W-138)
- **Word a PDF** — Oculta, esfuerzo S. Programada y oculta; la web ganó fidelidad y fuentes Unicode (W-136, W-137)
- **Excel a PDF** — Oculta, esfuerzo S. Programada y oculta
- **PowerPoint a PDF** — Oculta, esfuerzo S. Programada y oculta
- **Excel a CSV** — Oculta, esfuerzo S. Programada y oculta

**5.2 Con código en mis ramas (4):** falta terminarlas, probarlas en el Edge y el E22 y fusionarlas.

- **Imagen a GIF** — En rama, esfuerzo S. Codificador propio ya escrito y probado (rama `claude/paridad-formatos`)
- **Imagen a ICO** — En rama, esfuerzo S. Ya escrito y probado (rama `claude/paridad-formatos`)
- **HEIC a JPG** — En rama, esfuerzo S. Decodificación nativa desde Android 9 (rama `claude/paridad-formatos`)
- **PDF a Excel** — En rama, esfuerzo S. Mismo algoritmo de columnas que la web; 1 prueba falla (rama `claude/paridad-excel-hash`)

**5.3 Por construir (35):** 12 pequeñas (S), 14 medianas (M) y 9 grandes (L).

- **Reparar PDF** — No, esfuerzo M. Reconstruir el PDF con iText; la web usa pdf-lib porque la recuperación de qpdf no sirve en wasm
- **Quitar metadatos** — No, esfuerzo M. ExifInterface (fotos), iText (PDF) y reescritura del ZIP de Office
- **Hash e integridad** — No, esfuerzo S. `MessageDigest` (SHA-256, SHA-1, MD5)
- **Generador de contraseñas** — No, esfuerzo S. `SecureRandom`
- **Hoja de vida** — No, esfuerzo L. Plantillas, foto 3×4 y salida PDF y DOCX; es mucho diseño de pantalla
- **Cuenta de cobro, cotización y recibo** — No, esfuerzo M. Formulario, numeración y PDF
- **PDF a PDF/A** — No, esfuerzo L. Módulo PDF/A de iText y validar con veraPDF; la web ya lo valida
- **Imagen a texto (OCR)** — Parcial, esfuerzo S. El motor existe (OCR de PDF y de Estudio); falta aceptar una imagen suelta como herramienta
- **Imagen a AVIF** — No, esfuerzo L. Android no trae codificador AVIF: haría falta una librería nativa
- **SVG a PNG** — No, esfuerzo M. Librería AndroidSVG (revisar licencia y peso)
- **TIFF a JPG** — No, esfuerzo L. Android no decodifica TIFF: librería nativa
- **PDF a PowerPoint** — No, esfuerzo L. Páginas como imágenes dentro de diapositivas con POI
- **CSV a Excel** — No, esfuerzo S. Apache POI
- **Comprimir imagen** — No, esfuerzo S. `Bitmap` y `Canvas`; por lotes
- **Redimensionar** — No, esfuerzo S
- **Recortar imagen** — No, esfuerzo M. Hay que construir la pantalla de recorte (la de PDF recorta márgenes, no libre)
- **Rotar y voltear** — No, esfuerzo S
- **Quitar fondo** — No, esfuerzo M. Segmentación de sujeto de ML Kit en el dispositivo (modelo vía Google Play Services)
- **Foto tamaño documento** — No, esfuerzo M. Depende de quitar fondo y de recorte a medida
- **Marca de agua en imagen** — No, esfuerzo M. Texto o logo, posición y opacidad
- **Ajustar imagen** — No, esfuerzo S. `ColorMatrix`: brillo, contraste, saturación, blanco y negro, enderezar
- **Video a MP3 / extraer audio** — No, esfuerzo M. Media3 Transformer. Android no trae codificador MP3 (sí AAC/M4A)
- **Convertir video** — No, esfuerzo L. Media3 Transformer cubre MP4; el resto de formatos pide librería
- **Recortar audio y video** — No, esfuerzo M. Media3 Transformer (recorte)
- **Comprimir video** — No, esfuerzo M. Media3 Transformer con menor bitrate
- **Girar video** — No, esfuerzo S. Media3 Transformer (rotación)
- **Video a GIF** — No, esfuerzo M. Cuadros del video + el codificador GIF ya escrito
- **Unir videos** — No, esfuerzo M. Media3 Transformer (secuencia)
- **Audio a texto** — No, esfuerzo L. Whisper en el dispositivo (ONNX o whisper.cpp) y modelo que se descarga
- **Comprimir en ZIP y 7Z** — No, esfuerzo L. ZIP con `java.util.zip` (S); 7Z con commons-compress y XZ (L); contraseña ZIP (M)
- **Descomprimir ZIP, RAR, 7Z** — No, esfuerzo L. ZIP, TAR y GZ nativos (S); 7Z y RAR con librerías (L, revisar licencia)
- **Renombrar archivos** — No, esfuerzo S. Lógica pura ya escrita en la web (`renameBatch.ts`), portar a Kotlin
- **Contador de palabras** — No, esfuerzo S
- **Comparar textos** — No, esfuerzo M. Algoritmo de diferencias (Myers); la web ya lo tiene
- **Resumir PDF** — Parcial, esfuerzo S. El resumen extractivo ya existe en Modo Estudio (`TextSummarizer`); falta como herramienta de PDF

Leyenda de esfuerzo (trabajo asistido, con pruebas en dos teléfonos): **S** hasta 1 día, **M** 2 a 4 días, **L** 1 a 2 semanas. Son estimaciones gruesas para decidir, no compromisos. Cálculo usado: S = 1, M = 3 y L = 8 días; todo nativo = 12·1 + 14·3 + 9·8 + 5 ocultas + 4 en rama ≈ 135 días (unas 27 semanas). El híbrido hace nativas solo las S (más ocultas y ramas) y embebe el resto con unos 10 a 15 días de anfitrión y puente más medio día por herramienta embebida (unas 9 a 10 semanas).

## 6. Otras mejoras de la web que la app puede adoptar

Ordenadas por valor frente a esfuerzo.

1. **Un solo criterio de límites** (decisión, no código): hoy la app limita por día y la web no.
2. **Sello de privacidad en cada herramienta** (W-112), esfuerzo S: el mensaje que más diferencia a DocuSmart.
3. **«¿Y ahora?» entre herramientas** (W-110), M: sugerir el siguiente paso y pasar el archivo sin volver a elegirlo.
4. **OCR en más idiomas** (W-139): ML Kit tiene modelos de latino, chino, japonés, coreano y devanagari; el ruso y otros piden otro motor. Verificar antes de prometerlo.
5. **Revisar la calidad de las conversiones de Office** (W-136 a W-138) al mostrarlas.
6. **Editar el texto que ya tiene el PDF** (W-140) y **firma con certificado** (W-141): son las dos mejoras de herramientas existentes que la web ya tiene y la app no.
7. **Lotes en PDF y renombrar** (W-111), S a M.
8. **Agenda con exportar e importar `.ics`** (W-126) y, más adelante, Outlook y Google Calendar (W-127, W-128).
9. **Asistente local** (W-151.19), cuando exista el catálogo de acciones en la web.
10. **Cuenta y sincronización** (W-94 a W-98) y **Premium compartido**: lo que de verdad une los tres productos para el usuario.

Lo que la web podría adoptar de la app (queda fuera de este plan): escáner en vivo con detección de bordes (W-147.1), Carpeta Segura, Biblioteca y anotaciones en el visor.

## 7. Decisión de arquitectura para las herramientas que faltan

Hoy el código de las herramientas está escrito dos veces (Kotlin en la app, TypeScript en la web), y cada vez que la web gana una herramienta la app queda atrás. Tres caminos:

| Opción | Cómo | Ventajas | Costos y riesgos |
|---|---|---|---|
| **A. Todo nativo** | Portar las 35 herramientas a Kotlin | Experiencia 100 % nativa; sin WebView | ~27 semanas; el mantenimiento se duplica para siempre; varias piden librerías pesadas (7Z, RAR, TIFF, AVIF, Whisper); sigue desactualizándose |
| **B. Híbrido (recomendada)** | Nativo para las S y para lo que Android hace mejor; el resto se abre **dentro de la app** con la misma herramienta de la web (un `WebView` con el build de la web empaquetado en la app, funciona sin conexión) | ~9 a 10 semanas; las herramientas embebidas **se actualizan con la web**; menos código propio | Las herramientas embebidas se sienten un poco menos nativas; el APK crece (wasm de qpdf, 7-Zip, OCR y modelos que se descargan al usarlos); hay que puentear archivos, descargas y compartir; memoria del WebView en teléfonos de poca RAM |
| **C. App como envoltorio de la web** | Reemplazar la app por Capacitor (como iOS) | Un solo código en todo | Se pierde lo nativo que hoy es mejor (escáner de ML Kit, Carpeta Segura, Biblioteca, visor con anotaciones) o se reescribe como plugins; es una reescritura |

**Decisión: B (2026-11-26).** Mantiene lo que la app hace mejor, cierra la brecha rápido y es lo único que acerca de verdad el «todo a la vez» sin reescribir la app.

### Verificación de la política de Google Play (2026-11-26)

- **Permitido, con condiciones.** Google Play no prohíbe las apps con WebView; marca como *funcionalidad mínima* y spam a las que son **solo** una página web empaquetada ([guía de Android Developers, 2020](https://android-developers.googleblog.com/2020/10/developer-tips-and-guides-common-policy.html?hl=es)). DocuSmart tiene mucha funcionalidad nativa (escáner, Biblioteca, Visor, Carpeta Segura, Estudio), así que no entra en ese caso, siempre que las herramientas embebidas sean un complemento y no la app.
- **El código web dentro de la app no es «código ejecutable descargado».** La política de [Dispositivo y abuso de red](https://support.google.com/googleplay/android-developer/answer/9888379?hl=en) prohíbe descargar código ejecutable (dex, JAR, `.so`) fuera de Google Play, pero exceptúa el que corre en una máquina virtual o intérprete con acceso solo indirecto a las API de Android, con «JavaScript en un WebView» como ejemplo. El código interpretado cargado en ejecución no puede permitir violaciones de otras políticas.
- **Límites de esa excepción:** un WebView que expone una interfaz de JavaScript a contenido web no confiable es un riesgo de incumplimiento; conviene cargar solo contenido propio y con URL acotadas.
- Esto sale de la documentación oficial y de resúmenes de terceros; antes de publicar la primera versión con el anfitrión hay que releer la política vigente y probarlo en un envío a prueba cerrada.

### Condiciones del anfitrión web de la app

1. **Solo contenido propio:** cargar el build de la web empaquetado en la app (`WebViewAssetLoader`) o `docusmart.online`, nunca URL ajenas; la navegación fuera de ese origen se abre en el navegador (`shouldOverrideUrlLoading` con lista de permitidos).
2. **Puente mínimo:** una sola interfaz de JavaScript, solo para el origen propio, con las funciones indispensables (recibir un archivo de la app, entregar el resultado, compartir, guardar).
3. **Sin acceso a archivos locales del WebView** (`setAllowFileAccess(false)`, sin contenido mixto) y sin depurar WebView en producción.
4. **Publicidad:** el build embebido **no carga AdSense** (la publicidad de la app es AdMob y las políticas de AdSense no se pensaron para apps); se apaga por un parámetro del anfitrión. Verificarlo con las políticas de AdSense. Tampoco muestra el banner de analítica propio: el consentimiento lo gestiona la app.
5. **Privacidad intacta:** igual que en la web, el archivo no sale del dispositivo. Actualizar la política de privacidad y la ficha de Seguridad de los datos de Play si cambia algo.
6. **Memoria:** probar OCR, PDF grandes y modelos (quitar fondo, Whisper) en el Edge y el E22 antes de ofrecerlos; si el WebView no aguanta, esa herramienta pasa a nativo o se queda solo en la web.
7. **Actualización del build web:** empaquetado y renovado con cada versión de la app como base; la actualización en caliente desde el sitio es opcional y se decide después (sección 9.3).

### Reparto sugerido con la opción B

- **Nativo (S):** hash, generador de contraseñas, contador de palabras, renombrar archivos, comprimir y extraer ZIP/TAR/GZ, CSV ↔ Excel, GIF, ICO, HEIC, PDF a Excel, imagen a texto, comprimir, redimensionar, rotar, ajustar y marca de agua en imagen, resumir PDF como herramienta, girar video, y mostrar las 5 conversiones ocultas.
- **Embebido (M y L):** quitar fondo y foto tamaño documento, reparar PDF, quitar metadatos, comparar textos, hoja de vida, cuenta de cobro, PDF/A, PDF a PowerPoint, 7Z y RAR, TIFF, AVIF, SVG, recortar imagen, y todo audio y video (incluido audio a texto).

## 8. Plan de implementación de las herramientas

Se ejecuta **después** de que la web esté sin errores y de que cierres tus pendientes (👤), según el orden vigente.

| Fase | Contenido | Duración orientativa |
|---|---|---|
| **A. Cimientos** | ~~Decidir la opción de la sección 7~~ (decidida: híbrido); arreglar el desfase del contrato (`leer-codigo-qr`, `powerpoint-a-pdf`); definir la política de límites; publicar el manifiesto de herramientas (sección 9); si se elige B, construir el anfitrión web de la app (WebView con el build empaquetado, puente de archivos, descargas y compartir) | 2 a 3 semanas |
| **B. Rápidas nativas** | Terminar y probar las 4 en rama; mostrar las 5 ocultas tras revisarlas; las S nativas de la sección 7 | 3 a 4 semanas |
| **C. Medianas** | Quitar metadatos, reparar PDF, recortar imagen, quitar fondo, foto tamaño documento, cuenta de cobro, comparar textos, SVG, audio y video básico (por Media3 o embebidas) | 3 a 4 semanas |
| **D. Grandes** | Hoja de vida, PDF/A, PDF a PowerPoint, 7Z y RAR, TIFF, AVIF, audio a texto, convertir video | 2 a 3 semanas en B (embebidas); 10 o más en A |
| **E. Mejoras de las existentes** | Sección 6: sello de privacidad, «¿Y ahora?», lotes, editar texto existente, firma con certificado, OCR en más idiomas | 3 a 4 semanas |

Cada herramienta nueva sale con pruebas unitarias, prueba en los dos teléfonos reales (Edge y E22), su enlace de App Links y su fila del contrato en `si`. La app **no se publica** hasta pasar la suite completa, como ya se hace.

## 9. Que cada actualización llegue a web, app y Desktop a la vez

### 9.1 Cómo se actualiza cada producto hoy

| Producto | Cómo se publica | Cuándo le llega la mejora al usuario |
|---|---|---|
| Web `docusmart.online` | PR a `preproduccion`; a `main` en paquete con tu visto bueno; el CI despliega en Firebase Hosting (`channelId: live`) | Minutos después del despliegue |
| Desktop (Windows, Mac, Linux) | Es la web instalada como PWA; el service worker avisa «Hay una versión nueva · Actualizar» | Igual que la web, al abrirla |
| Android | `release.yml` genera el AAB firmado al etiquetar; se sube **a mano** a Play Console; revisión de Google | Días después, y con despliegue gradual |
| iPhone (planeado, W-149) | Capacitor; versión nueva en la App Store (W-149.24 propone un ritmo de 2 a 4 semanas) | Días después |
| Desktop Mac/Linux nativo (planeado, W-150) | Tauri con actualizador propio | Al abrir, tras publicar |

**Conclusión:** web y Desktop **ya** se actualizan juntos. «A la vez» exacto no es posible con las tiendas (la revisión de Google y Apple tarda), pero se puede lograr que **la función aparezca al mismo tiempo en todos** y que **ninguna plataforma se quede atrás sin que nos enteremos**.

### 9.2 Las siete piezas

1. **Una sola fuente de verdad del catálogo.** La web (`catalog.ts` + `toolParity.json`) publica en cada despliegue un **manifiesto** `https://docusmart.online/herramientas.json`, versionado, con cada herramienta (clave, categoría, rutas por idioma, plataformas donde está, versión mínima de la app). La app lo lee (con caché) para su lista «Más herramientas» y sus enlaces. Reemplaza el `WebToolLinks` copiado a mano y el script `generate_web_links.py`.
2. **Tren de versiones.** Un calendario fijo, por ejemplo cada 2 semanas: lo que ya está en `preproduccion` se promueve a producción y se corta la versión de la app (y de iPhone cuando exista). Un mismo número de versión de lanzamiento en los repos (etiqueta en ambos) y una tabla `docs/RELEASES.md` con qué incluye cada uno en web, Android, iPhone y Desktop.
3. **Funciones que se encienden juntas.** La app ya usa Firebase Remote Config. Cada herramienta nueva en la app lleva una bandera `tool_<clave>_enabled` (apagada por defecto). Se publica la versión de la app con la función dormida y, cuando llega a producción la web y la versión nueva ya está desplegada al 100 %, se enciende todo el mismo día. Así el usuario ve la función a la vez aunque la tienda haya tardado.
4. **Pruebas cruzadas en el CI.** Ya existe el contrato (F1). Se completa: el CI de la web **falla o avisa** si se agrega una herramienta sin declarar su estado en la app, y un job abre automáticamente un PR en el repo de la app («sincronizar catálogo») con el manifiesto, los enlaces y los textos nuevos. Y se agregan **archivos de prueba compartidos** (`shared-fixtures/`) con entradas y salidas esperadas para los algoritmos que existen en Kotlin y en TypeScript (resumen, diseño de PDF a Excel, QR protegido, renombrado), ejecutados por las suites de ambos.
5. **Textos compartidos.** Los nombres y descripciones de herramientas y los textos comunes se traducen **una vez** (en la web, 12 idiomas) y un script los exporta a `strings.xml`. Se acaban las traducciones duplicadas y desfasadas.
6. **Matriz de estado automática.** Un `docs/MATRIZ.md` generado a partir del manifiesto: cada herramienta en filas y Web, Android, iPhone, Desktop en columnas, con la versión en que llegó. Es la lista de control de cada lanzamiento y responde «¿qué le falta a cada plataforma?» sin auditorías manuales como esta.
7. **Observabilidad.** Las mismas claves de eventos en web y app y la versión en cada evento, para ver adopción por plataforma, y un aviso cuando una versión de la app lleva más de 30 días sin recibir la función de la web.

### 9.3 Con la opción B (embebido, decidida)

Las herramientas embebidas **sí** se actualizan a la vez que la web si el WebView carga el build desde `docusmart.online` con caché de service worker (misma lógica que Desktop), y funcionan sin conexión cuando ya se visitaron. La contrapartida es que dependen de la red la primera vez; la alternativa es empaquetar el build en la app y actualizarlo con cada versión (más predecible, no instantáneo). Se puede combinar: empaquetado como base y actualización en caliente del build web desde el sitio, dentro de lo que permiten las políticas de Google. La política de Google Play lo permite con las condiciones de la sección 7; la actualización en caliente se decide después, con la política vigente a la vista.

### 9.4 Lo que no se puede prometer

- **Instantáneo en las tiendas:** Google y Apple revisan. Con banderas y tren de versiones se llega a «la función aparece el mismo día», no a «el mismo minuto».
- **Cero trabajo duplicado:** sin la opción B o C, las herramientas nativas siguen existiendo dos veces; las pruebas compartidas reducen el riesgo de que diverjan, no lo eliminan.

## 10. Límites diarios: la web adopta los de la app (decisión 2026-11-26)

**Decisión:** la web adoptará los límites de la app, pero **cuando todo esté nivelado**. Hoy la web sigue **sin límites** y no se toca.

**Los límites de la app hoy** (1.1.6, `DailyLimitManager`), para usuarios gratuitos; Premium no tiene límite:

| Contador | Límite diario | Qué cuenta |
|---|---:|---|
| Conversiones | 5 | Cada archivo convertido en el Convertidor |
| Herramientas PDF | 3 por herramienta | Cada uso de unir, dividir, comprimir, rotar, numerar, marca de agua, reordenar, comparar, censurar… (un contador por herramienta) |
| Escaneos guardados | 8 | Cada escaneo que se guarda |
| Extras | +1 por anuncio | Cada anuncio recompensado visto suma un uso al contador correspondiente |

El conteo se reinicia cada día con una hora de confianza (`trustedNowMillis`), que resiste cambios del reloj del teléfono.

**Qué hay que tener antes de ponerlos en la web:**

1. **Cuenta y Premium compartido (W-94 y W-98).** Sin ellos, quien paga Premium en Google Play toparía con los límites en la web. Sin cuenta el límite sería por navegador (se salta borrando datos o cambiando de navegador): sirve como límite «blando»; con cuenta de Google se puede atar a la cuenta.
2. **Qué pasa con los extras.** La app los da con un anuncio recompensado. En la web AdSense no ofrece anuncios recompensados (haría falta Google Ad Manager): decidir entre anuncios recompensados con Ad Manager, solo Premium, o ningún extra.
3. **Qué cuenta en las herramientas que la app no tiene.** Propuesta para decidir: imagen y conversiones de imagen suman a «Conversiones»; audio y video, un contador propio más bajo porque son pesadas; texto, hash, QR, renombrar y generador de contraseñas, sin límite; hoja de vida y cuenta de cobro, sin límite o a decidir.
4. **Textos que hoy prometen «sin límites»:** portada, páginas de herramientas, comparativas y guías en 12 idiomas, base del asistente, política de privacidad (los contadores se guardan en el navegador) y fichas. Se cambian el mismo día.
5. **Riesgo de tráfico y SEO.** «Sin límites» es parte de la propuesta de valor y de las comparativas con la competencia (W-146). Medir antes y después con GA4 y Search Console, y desplegarlo detrás de una bandera para poder revertir.
6. **Una sola política en dos lugares.** Las cifras viven en Remote Config para la app y en la configuración de la web (idealmente el mismo manifiesto de la sección 9), para que no vuelvan a divergir.

**Orden en el tiempo:** 1) web sin errores, 2) backlog de la web, 3) tus pendientes (👤), 4) paridad de la app, 5) cuenta y Premium compartido, 6) **límites en la web**.

## 11. Decisiones que necesito de ti

1. ✅ **Decidido (2026-11-26): arquitectura híbrida.** Queda la verificación de la sección 7 y la prueba en un envío a prueba cerrada.
2. **Mostrar las 5 conversiones ocultas** (PDF a Word, Word, Excel y PowerPoint) cuando se revisen: ¿sí? Hoy la web las promociona.
3. ✅ **Decidido (2026-11-26): la web adoptará los límites de la app al final** (sección 10). Falta decidir los puntos 2 y 3 de esa sección (extras y qué cuenta en las herramientas nuevas) cuando se acerque el momento.
4. **Audio y video en la app:** ¿se ofrecen (embebido o Media3), o se quedan solo en la web?
5. **Manifiesto de herramientas, banderas de Remote Config y tren de versiones cada 2 semanas:** ¿se aprueban como mecanismo?
6. **Cuándo arrancar la app:** confirmo que sigue **después** de web sin errores, backlog web y tus pendientes. Si cambia el orden, dímelo.

## 12. Cómo sabremos que está hecho

- La matriz `docs/MATRIZ.md` sin filas «No» salvo las que decidas dejar solo en la web.
- El CI de la web **falla** si una herramienta nueva no declara su estado en la app.
- Una mejora de la web llega a la app (encendida por bandera) en el siguiente tren de versiones, sin trabajo manual de sincronización.
- Cada herramienta nueva: unitarias, prueba en Edge y E22, App Link y contrato en `si`.

## Anexo B. Matriz completa de herramientas

Estado de la app: **Sí** la ofrece, **Oculta** programada pero no visible, **En rama** con código sin fusionar, **Parcial** existe el motor pero no la herramienta, **No** no existe. Esfuerzo: S, M, L (sección 5). Enfoque: **N** nativo en Kotlin, **E** embebida (la de la web dentro de la app), **N/E** cualquiera.

| # | Herramienta | Categoría | App hoy | Esfuerzo nativo | Enfoque | Nota |
|---:|---|---|---|:---:|:---:|---|
| 1 | Unir PDF (`pdf/unir`) | Organizar | Sí | — | — |  |
| 2 | Dividir PDF (`pdf/dividir`) | Organizar | Sí | — | — |  |
| 3 | Rotar páginas (`pdf/rotar`) | Organizar | Sí | — | — |  |
| 4 | Reordenar páginas (`pdf/reordenar`) | Organizar | Sí | — | — |  |
| 5 | Numerar páginas (`pdf/numerar`) | Organizar | Sí | — | — |  |
| 6 | Editar PDF (`pdf/editar`) | Editar | Sí | — | — | La web también edita el texto que ya tiene el PDF y la app solo agrega texto (W-140) |
| 7 | Firmar PDF (`pdf/firmar`) | Editar | Sí | — | — | La web suma firma con certificado .p12 (W-141); la app no |
| 8 | Llenar formulario (`pdf/formulario`) | Editar | Sí | — | — |  |
| 9 | Marca de agua (`pdf/marca`) | Editar | Sí | — | — |  |
| 10 | Recortar PDF (`pdf/recortar`) | Editar | Sí | — | — |  |
| 11 | Censurar PDF (`pdf/censurar`) | Editar | Sí | — | — |  |
| 12 | Proteger con contraseña (`pdf/proteger`) | Seguridad | Sí | — | — |  |
| 13 | Desbloquear PDF (`pdf/desbloquear`) | Seguridad | Sí | — | — |  |
| 14 | Reparar PDF (`pdf/reparar`) | Seguridad | No | M | N | Reconstruir el PDF con iText; la web usa pdf-lib porque la recuperación de qpdf no sirve en wasm |
| 15 | Quitar metadatos (`metadatos/quitar`) | Seguridad | No | M | N | ExifInterface (fotos), iText (PDF) y reescritura del ZIP de Office |
| 16 | Hash e integridad (`hash/calcular`) | Seguridad | No | S | N | `MessageDigest` (SHA-256, SHA-1, MD5) |
| 17 | Generador de contraseñas (`texto/contrasena`) | Seguridad | No | S | N | `SecureRandom` |
| 18 | Hoja de vida (`documentos/cv`) | Documentos | No | L | N/E | Plantillas, foto 3×4 y salida PDF y DOCX; es mucho diseño de pantalla |
| 19 | Cuenta de cobro, cotización y recibo (`documentos/cobro`) | Documentos | No | M | N/E | Formulario, numeración y PDF |
| 20 | Comprimir PDF (`pdf/comprimir`) | Optimizar | Sí | — | — | La web ya comprime varios PDF a la vez (W-111) |
| 21 | PDF a PDF/A (`pdf/pdfa`) | Optimizar | No | L | E | Módulo PDF/A de iText y validar con veraPDF; la web ya lo valida |
| 22 | Comparar PDF (`pdf/comparar`) | Optimizar | Sí | — | — |  |
| 23 | Extraer imágenes (`pdf/imagenes`) | Optimizar | Sí | — | — |  |
| 24 | Escanear documento (`pdf/escanear`) | Escanear | Sí | — | — | La app es mejor: escáner de ML Kit con bordes, filtros y recorte |
| 25 | OCR de PDF (`pdf/ocr`) | Escanear | Sí | — | — | La app solo reconoce alfabeto latino (ML Kit); la web reconoce los 12 idiomas (W-139) |
| 26 | Imagen a texto (OCR) (`imagen/texto`) | Escanear | Parcial | S | N | El motor existe (OCR de PDF y de Estudio); falta aceptar una imagen suelta como herramienta |
| 27 | Imagen a PDF (`convertir/img-pdf`) | Convertir | Sí | — | — |  |
| 28 | Imagen a JPG (`convertir/img-jpg`) | Convertir | Sí | — | — |  |
| 29 | Imagen a PNG (`convertir/img-png`) | Convertir | Sí | — | — |  |
| 30 | Imagen a WebP (`convertir/img-webp`) | Convertir | Sí | — | — |  |
| 31 | Imagen a BMP (`convertir/img-bmp`) | Convertir | Sí | — | — |  |
| 32 | Imagen a GIF (`convertir/img-gif`) | Convertir | En rama | S | N | Codificador propio ya escrito y probado (rama `claude/paridad-formatos`) |
| 33 | Imagen a AVIF (`convertir/img-avif`) | Convertir | No | L | E | Android no trae codificador AVIF: haría falta una librería nativa |
| 34 | Imagen a ICO (`convertir/img-ico`) | Convertir | En rama | S | N | Ya escrito y probado (rama `claude/paridad-formatos`) |
| 35 | HEIC a JPG (`convertir/heic-jpg`) | Convertir | En rama | S | N | Decodificación nativa desde Android 9 (rama `claude/paridad-formatos`) |
| 36 | SVG a PNG (`convertir/svg-png`) | Convertir | No | M | N | Librería AndroidSVG (revisar licencia y peso) |
| 37 | TIFF a JPG (`convertir/tiff-jpg`) | Convertir | No | L | E | Android no decodifica TIFF: librería nativa |
| 38 | PDF a imagen (`convertir/pdf-img`) | Convertir | Sí | — | — |  |
| 39 | PDF a texto (`convertir/pdf-txt`) | Convertir | Sí | — | — |  |
| 40 | PDF a Word (`convertir/pdf-word`) | Convertir | Oculta | S | N | Está programada y la app la oculta desde el primer lanzamiento; revisar y mostrar. La web la hace con diseño (W-138) |
| 41 | PDF a Excel (`convertir/pdf-excel`) | Convertir | En rama | S | N | Mismo algoritmo de columnas que la web; 1 prueba falla (rama `claude/paridad-excel-hash`) |
| 42 | Word a PDF (`convertir/word-pdf`) | Convertir | Oculta | S | N | Programada y oculta; la web ganó fidelidad y fuentes Unicode (W-136, W-137) |
| 43 | Excel a PDF (`convertir/excel-pdf`) | Convertir | Oculta | S | N | Programada y oculta |
| 44 | PowerPoint a PDF (`convertir/ppt-pdf`) | Convertir | Oculta | S | N | Programada y oculta |
| 45 | PDF a PowerPoint (`convertir/pdf-ppt`) | Convertir | No | L | E | Páginas como imágenes dentro de diapositivas con POI |
| 46 | CSV a Excel (`convertir/csv-excel`) | Convertir | No | S | N | Apache POI |
| 47 | Excel a CSV (`convertir/excel-csv`) | Convertir | Oculta | S | N | Programada y oculta |
| 48 | Comprimir imagen (`imagen/comprimir`) | Imagen | No | S | N | `Bitmap` y `Canvas`; por lotes |
| 49 | Redimensionar (`imagen/redimensionar`) | Imagen | No | S | N |  |
| 50 | Recortar imagen (`imagen/recortar`) | Imagen | No | M | N | Hay que construir la pantalla de recorte (la de PDF recorta márgenes, no libre) |
| 51 | Rotar y voltear (`imagen/rotar`) | Imagen | No | S | N |  |
| 52 | Quitar fondo (`imagen/fondo`) | Imagen | No | M | N/E | Segmentación de sujeto de ML Kit en el dispositivo (modelo vía Google Play Services) |
| 53 | Foto tamaño documento (`imagen/documento`) | Imagen | No | M | N/E | Depende de quitar fondo y de recorte a medida |
| 54 | Marca de agua en imagen (`imagen/marca`) | Imagen | No | M | N | Texto o logo, posición y opacidad |
| 55 | Ajustar imagen (`imagen/ajustar`) | Imagen | No | S | N | `ColorMatrix`: brillo, contraste, saturación, blanco y negro, enderezar |
| 56 | Video a MP3 / extraer audio (`multimedia/audio`) | Multimedia | No | M | N/E | Media3 Transformer. Android no trae codificador MP3 (sí AAC/M4A) |
| 57 | Convertir video (`multimedia/convertir`) | Multimedia | No | L | E | Media3 Transformer cubre MP4; el resto de formatos pide librería |
| 58 | Recortar audio y video (`multimedia/recortar`) | Multimedia | No | M | N | Media3 Transformer (recorte) |
| 59 | Comprimir video (`multimedia/comprimir`) | Multimedia | No | M | N | Media3 Transformer con menor bitrate |
| 60 | Girar video (`multimedia/girar`) | Multimedia | No | S | N | Media3 Transformer (rotación) |
| 61 | Video a GIF (`multimedia/gif`) | Multimedia | No | M | N | Cuadros del video + el codificador GIF ya escrito |
| 62 | Unir videos (`multimedia/unir`) | Multimedia | No | M | N | Media3 Transformer (secuencia) |
| 63 | Audio a texto (`multimedia/texto`) | Multimedia | No | L | E | Whisper en el dispositivo (ONNX o whisper.cpp) y modelo que se descarga |
| 64 | Comprimir en ZIP y 7Z (`archivos/crear`) | Archivos | No | L | N/E | ZIP con `java.util.zip` (S); 7Z con commons-compress y XZ (L); contraseña ZIP (M) |
| 65 | Descomprimir ZIP, RAR, 7Z (`archivos/extraer`) | Archivos | No | L | N/E | ZIP, TAR y GZ nativos (S); 7Z y RAR con librerías (L, revisar licencia) |
| 66 | Renombrar archivos (`archivos/renombrar`) | Archivos | No | S | N | Lógica pura ya escrita en la web (`renameBatch.ts`), portar a Kotlin |
| 67 | Crear QR (`qr/crear`) | QR | Sí | — | — |  |
| 68 | Leer QR (`qr/leer`) | QR | Sí | — | — |  |
| 69 | Contador de palabras (`texto/contar`) | Texto | No | S | N |  |
| 70 | Comparar textos (`texto/comparar`) | Texto | No | M | N | Algoritmo de diferencias (Myers); la web ya lo tiene |
| 71 | Resumir PDF (`texto/resumir`) | Texto | Parcial | S | N | El resumen extractivo ya existe en Modo Estudio (`TextSummarizer`); falta como herramienta de PDF |

## Anexo A. Fallos de Crashlytics revisados (2026-11-20, últimos 90 días)

| Fallo | Versiones y dispositivos | Evidencia en el código actual | Decisión |
|---|---|---|---|
| `Canvas: trying to draw too large(… bytes) bitmap` en `ViewerScreen$PdfViewerContent` (3 eventos, 2 usuarios) | 1.0.0 (1) y 1.1.0 (2); Asus con Android 9 y Motorola | `PdfPageSource.lazyPageBitmapSize` limita cada página a `VIEWER_PAGE_MAX_PIXELS = 8 000 000` (unos 32 MB) desde el render por página (#108). Ninguna versión posterior a 1.1.0 lo registra | Cerrar en Crashlytics; si reaparece en 1.1.3 o posterior, reabrir |
| `IllegalArgumentException: All requested items must be referenced by specific ID` / `must be Media items` en `MediaDeletePermission.createBulkDeleteRequest` (2 + 2 eventos, 1 usuario cada uno) | Solo 1.0.0; Tecno KJ7s con Android 14; llegaba desde `TrashRepository.deleteForever` → `DocumentRepository.deleteDocument` | `createBulkDeleteRequest` captura cualquier excepción y devuelve `null`; desde la 1.1.5 lo propio se borra directo con `deleteOwned` y `createDeleteRequest` ya no recibe documentos | Cerrar en Crashlytics |
| `IndexOutOfBoundsException: Index -1 out of bounds for length 1` (`jdk.internal.util.Preconditions`) | 1.1.5; 1 evento, 1 usuario | Los accesos por índice del Visor (Excel), Estudio y Reordenar páginas tienen guarda; sin el stack trace no se puede ubicar | **Pendiente**: falta el seguimiento de pila del evento |

