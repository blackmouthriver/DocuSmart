# Visor de PDF: render perezoso de páginas (backlog #7)

Fecha: 2026-10-10. **No está en el `.aab` 1.1.2** ya subido a la prueba cerrada; irá en la siguiente versión.

## Problema

`PdfViewerContent` llamaba a `renderPdfPagesToBitmaps()`, que rasterizaba **todas** las páginas por adelantado (2× los puntos del PDF) y las mantenía en memoria; el Visor no mostraba nada hasta terminar. Medido con un libro real de **2 332 páginas** (252×331 pt, 5,3 MB), llamando a esa misma función en dos teléfonos:

| Dispositivo | RAM | Tiempo hasta ver la primera página | Bitmaps en memoria |
|---|---|---|---|
| Motorola Edge 30 Neo | 7,6 GB | 21,7 s | 2 968 MB |
| Moto E22 | 3,9 GB | 74,6 s | 2 968 MB |

El E22 "lo aguanta" gracias al intercambio de memoria del sistema (el `lowmemorykiller` registraba presión), pero el usuario espera más de un minuto y la app ocupa casi 3 GB. En un teléfono de 2 GB lo esperable es que el sistema cierre la app. Con 400 páginas A4 el render eager ocupaba unos 770 MB.

## Solución

`core/pdf/PdfPageSource.kt`: el PDF se abre **una vez** y cada página se renderiza **cuando entra en pantalla**, con una caché LRU acotada por bytes.

- **Apertura:** copia el PDF al caché de la app (`PdfRenderer` exige un descriptor real), abre el renderer y lee solo el número de páginas y el tamaño de la **primera**. No renderiza nada.
- **Tamaño de las demás:** se estima con el de la primera y se corrige al renderizar cada página. Leer el tamaño de las 2 332 páginas por adelantado costaba **7,6 s (Edge) y 25 s (E22)**, por eso no se hace.
- **Render bajo demanda** (`render(index, targetWidthPx)`): `PdfRenderer` solo admite una página abierta a la vez y acceso no concurrente, así que todo se serializa con un `Mutex`. Ancho = ancho de pantalla − relleno, ×1,5 (nitidez para el zoom), con el tope de 8 MP por página que ya existía (Android aborta al dibujar un bitmap > 100 MB).
- **Caché** (`LruByteCache`): LRU por **bytes**, no por cantidad (una página pesa de 1 a 14 MB). Presupuesto = ⅓ de la clase de memoria de la app, entre 48 y 128 MB. Siempre conserva al menos el último elemento. No recicla bitmaps: el que esté dibujándose sigue siendo válido y se libera cuando nadie lo referencia.
- **Interfaz** (`PdfPageItem` en `ViewerScreen.kt`): cada página pide su bitmap al entrar en pantalla y adelanta la siguiente; mientras llega muestra un hueco con la proporción estimada (con indicador de carga), así la lista no salta.
- **Cierre:** al cambiar de documento o cerrar el Visor se cierran el renderer, el descriptor y la copia en caché (en segundo plano, esperando su turno en el mutex). Las copias huérfanas de más de 1 h (proceso matado) se borran al abrir.

## Resultados (mismo libro, mismos teléfonos)

| | Edge: antes | Edge: ahora | E22: antes | E22: ahora |
|---|---|---|---|---|
| Hasta ver la primera página | 21,7 s | **0,18 s** | 74,6 s | **0,9 s** |
| Memoria de bitmaps | 2 968 MB | **78 MB** (presupuesto 85) | 2 968 MB | **78 MB** |
| Leer 100 páginas seguidas | — | 3,0 s (30 ms/pág.) | — | 9,0 s (90 ms/pág.) |
| Saltar a la mitad (10 págs.) | — | 0,3 s | — | 1,0 s |
| Saltar al final (10 págs.) | — | 0,1 s | — | 0,3 s |

## Qué cambia para el usuario

- Abre al instante, sea cual sea el tamaño del PDF.
- Memoria acotada (~80 MB de páginas) en lugar de crecer con el documento.
- Con un desplazamiento muy rápido (un "fling") puede verse un instante un hueco con indicador de carga antes de que aparezca la página; con lectura normal no se nota (30 ms por página en el Edge, 90 ms en el E22).
- Las páginas se renderizan a 1,5× el ancho de pantalla en vez de 2× los puntos del PDF: **más nítidas en páginas pequeñas** (antes se estiraban).
- En un PDF con páginas de **tamaños mezclados** la lista puede dar un pequeño salto al cargar una página de otro formato, y saltar a una página lejana (búsqueda, marcador, última página vista) puede quedar algo menos preciso hasta que se renderizan las intermedias.
- La copia temporal del PDF permanece en caché mientras el Visor está abierto (antes se borraba justo después de renderizar).
- Zoom, anotaciones, resaltados de búsqueda, marcadores, indicador de página y contraseña funcionan igual.

## Pendiente / no incluido

- **Modo Estudio:** migrado en la rama `claude/estudio-render-perezoso` (ver la sección siguiente); ya no queda ningún uso de `renderPdfPagesToBitmaps()`.
- Sin verificar a mano en un teléfono real: abrir el libro en la interfaz, desplazarse y comprobar a ojo que no hay huecos molestos.

## Pruebas

- Unitarias: `LruByteCacheTest` (8: presupuesto, orden LRU, elemento mayor que el presupuesto, reemplazo, `removeWhere`, `clear`) y `LazyPageBitmapSizeTest` (6: proporción, página pequeña al ancho pedido, tope de 8 MP, caso del crash de Crashlytics, entradas inválidas).
- Instrumentadas: `PdfPageSourceTest` (9: abrir sin renderizar, ancho/proporción, caché acotada tras 150 páginas, caché hit, índices inválidos, 40 renders concurrentes, cierre + borrado de la copia, doble cierre, archivo que no es PDF) y `ViewerPdfTest.pdfDeMuchasPaginas_…` (400 páginas: abre en < 15 s, se llega a la última y la memoria nativa crece < 300 MB).

## Verificación final

Edge 30 Neo (Android 14) y emulador 320×640 (Android 16), en paralelo:

- Prueba de 400 páginas por la interfaz: **3 de 3** en cada uno. `PdfPageSourceTest` 9/9 y paquete `core.pdf` 9/9 en ambos.
- Paquete `viewer` (94 pruebas): 94/94 en cada corrida salvo una del Edge que cerró el proceso por el defecto de Compose descrito más abajo (preexistente).
- Paquete `study` (66 pruebas, no tocado por este cambio): 66/66 en 7 de 8 corridas por dispositivo. Una vez, en una corrida seguida del paquete `viewer`, falló **en ambos dispositivos a la vez**; no se reprodujo ni sola ni en secuencia en 8 intentos posteriores (6 repeticiones + 2) y **no se identificó la causa**. Si reaparece, revisar si alguna prueba de Estudio depende de la hora.
- Unitarias de `core.pdf`: 14/14. ktlint y detekt OK.
- El Moto E22 estuvo desconectado en esta tanda; los números del libro en el E22 son de la medición previa y posterior descritas arriba.

## Hallazgo aparte: cierre intermitente del proceso en las pruebas del Visor (preexistente)

Al repetir el paquete completo del Visor en el Edge se vio un cierre del proceso (`FATAL EXCEPTION: main` → `Unable to destroy activity` → `ArrayIndexOutOfBoundsException: length=640; index=-16` en `SlotWriter.moveSlotGapTo`, runtime de Compose) en una prueba al azar de la clase. **No lo introduce este cambio**: se midió igual en `main` sin él (1 caída de 6 corridas; con el cambio, 1 de 6 en la última tanda). Es una corrupción de la tabla de composición al destruir la actividad, de la misma familia que la intermitencia "must have a looper" de `SecurityFolderFlowsTest` (composición ejecutada fuera del hilo principal por el reloj de pruebas de Compose). Puede dejar un job de CI en rojo de vez en cuando: relanzarlo. Una actualización de Compose podría corregirlo (hay actualizaciones de dependencias pendientes en Dependabot).

Se probó, y se **revirtió**, escribir el estado de cada página en el hilo principal (`withContext(Dispatchers.Main.immediate)`): no cambió la frecuencia del cierre y su justificación resultó equivocada.

## Segunda parte: Modo Estudio (2026-10-10)

`StudyScreen.kt` (`ReadingTab` / `StudyPdfViewer`) era el último consumidor del render eager: rasterizaba todo el PDF al abrir Lectura, mantenía todos los bitmaps en memoria y no mostraba nada hasta terminar. Ahora usa `PdfPageSource`, igual que el Visor.

- **Ciclo de vida:** `ReadingTab` abre la fuente al elegir documento y la cierra al cambiar de documento o salir de Lectura; **no** al alternar PDF/Texto (sigue sin releer el documento, como pidió el usuario el 2026-09-22). El `State` de la fuente es la clave del `DisposableEffect`: con `rememberUpdatedState` la limpieza vería ya la fuente del documento nuevo y la anterior quedaría abierta.
- **Interfaz:** `StudyPdfPage` pide su bitmap al entrar en pantalla, adelanta la siguiente y muestra un hueco con la proporción estimada mientras llega. El desplazamiento a la página que lee la voz usa `pageCount` (el primer render ya no espera al documento completo).
- **Defecto corregido de paso:** el `Image` de Estudio tampoco tenía `aspectRatio`, así que una página más angosta que la pantalla se medía con la altura de su propio bitmap (mismo defecto que se arregló en el Visor con #107). Con el render a ancho de pantalla × 1,5 y `aspectRatio` ya no ocurre.
- **`PdfPageSource.open`:** ahora comprueba la cancelación antes de devolver la fuente y cierra renderer, descriptor y copia si el documento cambió mientras se abría (antes quedaban abiertos hasta cerrar el proceso). Además la copia a medias se borra si falla el copiado.
- **Código eliminado:** `renderPdfPagesToBitmaps`, `renderCachedPdfPages`, `renderAllPages`, `viewerPageBitmapSize` y su escala fija 2×. `PdfPageBitmapTest` ahora cubre `copyPdfUriToCache` (origen ilegible → `false`, sin copia a medias); el caso del crash de Crashlytics sigue cubierto por `LazyPageBitmapSizeTest`.
- **Prueba nueva:** `StudyReadingTest.visorDePdfReal_conMuchasPaginas_abreAlInstanteYLlegaALaUltima` (300 páginas: la primera aparece en < 15 s y se llega a la última).

### Verificación

| | Edge 30 Neo (API 34) | Emulador 320×640 (API 36) |
|---|---|---|
| Paquete `study` | 67/67 | 67/67 |
| Paquete `core.pdf` | 9/9 | 9/9 |
| Paquete `viewer` (98) | 97/98 | 98/98 |

Edge `viewer`: falló `ViewerDocumentTypesTest.powerPointCorrupto_…` con `performMeasureAndLayout called during measure layout` (reloj de pruebas de Compose; es una prueba de PowerPoint y no toca PDF ni Estudio). Al repetir la clase 3 veces: 2 OK y 1 cierre del proceso con `SlotWriter.moveSlotGapTo` al destruir la actividad, el defecto preexistente de Compose descrito arriba. Ninguna de las dos es nueva ni está en código tocado por este cambio.

**Sin verificar a mano:** abrir un PDF real en Lectura, alternar PDF/Texto, dejar que "Leer todo" avance de página y comprobar que el visor sigue a la voz sin huecos molestos; cambiar de documento y volver.
