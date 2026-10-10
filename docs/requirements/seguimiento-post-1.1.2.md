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

## Backlog #8 y #9 — Premium: oferta correcta y ahorro real (2026-10-10)

Rama `claude/premium-seleccion-oferta`. **Tampoco está en el `.aab` 1.1.2.**

### #8 — Qué oferta se compra y se muestra

**Problema.** La compra usaba `subscriptionOfferDetails.firstOrNull()` y la UI mostraba el precio y los días de prueba de la fase de esa misma primera oferta. Play Billing **no garantiza el orden** de esa lista. Con un plan base y una oferta de prueba gratuita de 7 días en el mismo producto (lo previsto en Play Console), cualquiera podía venir primero: el usuario compraría una oferta distinta de la que ve, o perdería la prueba.

**Arreglo (`selectSubscriptionOffer` en `BillingManager.kt`).** Una sola función decide la oferta y la usan **la compra, el precio/días de prueba mostrados y el cálculo del fin de la prueba**, así nunca se desincronizan. Play solo lista las ofertas para las que el usuario es elegible (la de prueba desaparece si ya la usó), así que basta este orden:

1. una oferta con fase gratuita interpretable (la de más días; a igual duración, por `offerId`, para ser estable);
2. el plan base (`offerId == null`);
3. cualquier otra, de forma estable (por `offerId`).

Una fase gratis con duración no interpretable (`"raro"`) no cuenta como prueba, igual que ya hacía `planOfferFor`.

**Pendiente del lado de Play Console (no es código):** la oferta de prueba de 7 días **todavía hay que crearla** en el producto anual/mensual. Hasta entonces la app compra el plan base, como antes.

### #9 — Etiqueta "Ahorra 44%"

**Problema.** El texto estaba fijo en 12 idiomas (`premium_savings_44`). El 44 % sale de los precios de respaldo en USD ($2,99 / $19,99 = 44,3 %); con los precios reales en COP (6.900 / 46.900) el ahorro es **43,4 %**.

**Arreglo.** El porcentaje se calcula con los precios reales de Play (`priceAmountMicros` recurrente, ahora en `PlanOffer.priceMicros`):

- `annualSavingsPercent(monthlyMicros, annualMicros)` (`PremiumLogic.kt`): `1 − anual / (mensual × 12)`, **redondeado hacia abajo** para no exagerar la promesa. `null` si falta un precio o el anual no es más barato → el badge se oculta.
- `PremiumPlan.savingsLabelRes` (texto fijo) → `savingsPercent: Int?`; la tarjeta usa `premium_savings_percent` ("Ahorra %1$d%%") en los 12 idiomas.
- `PremiumRepository` sigue dando el valor de respaldo (44) y respeta Remote Config (`premium_show_savings_badge`): si el badge está apagado, los precios reales **no** lo encienden.
- `PremiumViewModel.applyOffer` recalcula al llegar la oferta de Play, tanto en `plans` como en `selectedPlan`. Si Play no entrega algún precio se conserva el de respaldo.

El resultado depende del país: en un teléfono con precios en USD seguirá diciendo 44 %; en Colombia, 43 %.

### Verificación

| | Resultado |
|---|---|
| Unitarias `core.billing` + `features.premium` | 80/80 (8 nuevas de selección de oferta, 4 de `PremiumViewModel`, 6 de `annualSavingsPercent`) |
| Instrumentadas `features.premium` | 23/23 en Edge 30 Neo y en emulador 320×640 |
| ktlint, detekt, `lintDebug`, ensamblado | OK |

**Sin verificar:** contra Play real. Hace falta una versión en prueba cerrada con la oferta de prueba creada para ver, con una cuenta elegible, que el cuadro de compra muestra "7 días gratis" y que, con una cuenta que ya usó la prueba, aparece el plan base. Hasta ese momento la lógica solo está cubierta con simulaciones de `ProductDetails`.

