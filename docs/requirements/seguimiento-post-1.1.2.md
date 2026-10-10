# Seguimiento posterior a la 1.1.2

Trabajo del backlog hecho después de generar el `.aab` 1.1.2 (`versionCode 7`). **Nada de lo de aquí está en ese `.aab`**; irá en la siguiente versión. Complementa `release-1.1.2-resumen.md`.

## Backlog #12 — reactivar las pruebas con `@Ignore` (2026-10-10)

Había **7 pruebas** ignoradas en 5 archivos. Se reactivaron las 7: ninguna estaba rota de forma "inestable"; cada una fallaba por una causa concreta y distinta, que el motivo del `@Ignore` ("temporización", "geometría del dispositivo") ocultaba.

| Prueba | Causa real | Arreglo |
|---|---|---|
| `ConverterScreenFlowsTest.convertirVariasImagenes…` | Buscaba el texto `"Convertir a PNG"`, pero con 2 archivos el botón es el de lote ("Convertir 2 archivos") | La prueba usa el plural del botón de lote |
| `ViewerPdfTest.anotacionesExistentes_tocarUnResaltado…` y `…_eliminarUnaNota…` | **Dos causas.** (1) La prueba tocaba el nodo "Página 1", que es la lista entera: el `LazyColumn` fusiona la semántica de su única página y su centro no es el de la página. (2) **Defecto real de la app, ver abajo** | (1) Se toca el nodo de la imagen (`useUnmergedTree = true`); (2) arreglo en `ViewerScreen.kt` |
| `ScanResultScreenTest.compartir_abreElSelector…` | En Android 16 el selector guarda el `IntentSender` bajo `EXTRA_CHOOSER_RESULT_INTENT_SENDER`, no bajo la clave antigua; la prueba solo leía la antigua | La prueba lee la clave nueva (API 34+) y cae a la antigua |
| `ScanResultScreenTest.pdfDeEscaner_compartirCopia…` | `Lifecycle.addObserver` exige el hilo principal y, tras `copyUriToCache()` (salta a IO), la corrutina seguía en el hilo de quien llama | `shareOnMainThread()`: se garantiza el hilo principal (en la app real ya lo era) |
| `SecurityFolderFlowsTest.archivoPendienteContentInexistente…` | Estaba bien; pasaba en todo | Solo se quitó el `@Ignore` |
| `ViewerPasswordTest.pdfProtegido_rechaza…YAbreConLaCorrecta` | Estaba bien; pasaba en todo | Solo se quitó el `@Ignore` |

### Defecto real de la app encontrado: páginas pequeñas desalineadas en el Visor

Los números lo muestran: para el PDF de prueba (300×400 pt) la página medía **308×411 px** (proporción 0,75) en el emulador de 320 px, pero **1042×800 px** (proporción 1,30) en un teléfono de 1080 px. Causa: el bitmap de la página se renderiza a 2× los puntos del PDF; cuando ese bitmap es **más angosto que la pantalla**, Compose medía la altura del `Image` con la del propio bitmap en vez de escalarla con el ancho. La página se dibujaba pequeña y centrada, mientras que resaltados, notas, resaltado de búsqueda y el hit-test del toque se calculan con `size.width / pageWidthPts` (el ancho completo) y quedaban **desalineados**.

Afecta a páginas pequeñas en teléfonos de alta resolución (una A5 en 1080 px: 840 px de bitmap < 1038 px de ancho útil) y, probablemente, a casi cualquier página en una tableta. Una A4 en un teléfono de 1080 px no se ve afectada (1190 px > 1038 px).

**Arreglo:** `.aspectRatio(pageAspectRatio(pageBitmap))` en el `Image` de cada página (`ViewerScreen.kt`). Para páginas grandes la proporción coincide con la del bitmap y no cambia nada. Verificado midiendo: tras el arreglo el nodo del Edge mide 1042×1389 (0,75). Prueba de regresión nueva: `paginaPequena_conservaLaProporcionDelPdf…`.

**Sin verificar a mano:** abrir un PDF pequeño en un teléfono real, anotar y comprobar a ojo que el resaltado queda sobre el texto.

### Resultados

| Dispositivo | Pruebas | Resultado |
|---|---|---|
| Emulador 320×640 (API 36) | 7 reactivadas, 3 intentos cada una; paquete `viewer` 97 + clases Converter/ScanResult | todo OK; `SecurityFolderFlowsTest` 1 falla intermitente (ver abajo) |
| Edge 30 Neo (API 34) | 7 reactivadas; paquete `viewer` 97 + clases | OK |
| Unitarias | 1520 | 0 fallos |

El Moto E22 estaba desconectado (offline) durante esta tanda y no se probó.

> **Corrección sobre una medición intermedia.** Una primera tanda de repeticiones dio "56 de 56 pasan". Era errónea: el script contaba como bueno cualquier intento sin fallo aunque la prueba no se ejecutara de verdad. Se rehízo exigiendo código de estado 0 (corrió y pasó; ni fallo ni ignorada) y esos resultados son los que valen.

## Pendiente nuevo: intermitencia "must have a looper" en `SecurityFolderFlowsTest`

Cuatro pruebas distintas de esa clase han fallado, de a una por corrida y no siempre, con `IllegalStateException: The current thread must have a looper!` (`vistaPreviaFallida_…`, `archivoPendienteLocal_fallido_…`, `restaurarFallido_…`; antes también el `@Ignore` de `archivoPendienteContentInexistente`). La traza pasa por `TestMonotonicFrameClock.performFrame` componiendo el `LazyColumn` de `SecurityScreen.kt:759` en un hilo sin `Looper` (el programador de prefetch del `LazyColumn` pide el `Choreographer`). Es un problema de la infraestructura de pruebas (en la app real la composición es siempre en el hilo principal), pero **puede poner en rojo el CI de vez en cuando** ahora que falla ante fallas nuevas: si pasa, relanzar el job (`gh run rerun <id> --failed`) y no tratarlo como regresión.
