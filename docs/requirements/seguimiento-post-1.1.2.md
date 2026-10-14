# Seguimiento posterior a la 1.1.2

Trabajo del backlog hecho después de generar el `.aab` 1.1.2 (`versionCode 7`). **Nada de lo de aquí está en ese `.aab`**; todo está incluido en la **1.1.3 (`versionCode 8`)**. Complementa `release-1.1.2-resumen.md`.

## Estado del backlog al cierre de la 1.1.3 (2026-10-14)

Versión **1.1.3 (código 8)**, generada desde `main` 7fc479b (PR #143). Numeración del backlog de trabajo posterior a la 1.1.2 (#6 a #19; **no** son números de PR). El porcentaje cuenta ítems cerrados sobre ítems numerados: mide cantidad, no esfuerzo ni riesgo.

| # | Ítem | Estado | Referencia |
|---|---|---|---|
| 6 | Auditoría de idioma: recursos de la Application no seguían el idioma de la app | Hecho | #105, #102 |
| 7 | Visor: render de todas las páginas a la vez (OOM en PDFs grandes) y lo mismo en Modo Estudio | Hecho | #108, #109 |
| 8 | Premium: elegir de forma estable la oferta con prueba gratis | Hecho en código; **falta crear la oferta en Play Console** | #110 |
| 9 | Etiqueta «Ahorra 44 %» fija | Hecho: se calcula con los precios reales de Play | #110 |
| 10 | Lector de QR: historial dentro del banner | Hecho | #112 |
| 11 | `QrCreatorFlowsTest.url_genera...` intermitente | Hecho: causa real en `MemoryPrefsContext` | #131 |
| 12 | Siete pruebas con `@Ignore` | Hecho (y defecto real del visor encontrado) | #107 |
| 13 | `sonarcloud.yml` sin guardia de fallas | **Descartado a propósito**: `ci.yml` ya la impone y duplicarla pondría dos workflows en rojo por el mismo flake | — |
| 14 | Reintento ante fallas de red/descargas corruptas en CI | Hecho | #113 |
| 15 | `app-ads.txt` de AdMob | **Pendiente** (hosting, no es código del repo) | — |
| 16 | Producto anual y oferta de prueba de 7 días en Play Console | **Pendiente** (del propietario) | — |
| 17 | Pantallas con PIN sin revisar tras el rediseño | Hecho (flecha de volver corregida) | #115 |
| 18 | QR por Wi-Fi local | **Diferido** por decisión del propietario | — |
| 19 | Play Integrity | **Sin integrar**; decisión pendiente | — |

**Avance: 10 de 14 cerrados (71 %)**: 9 implementados y verificados + 1 descartado con motivo (#13). Abiertos: 4, de los cuales 3 no dependen de código (#15, #16 y la decisión de #19) y 1 está diferido (#18).

### Hecho adicional que no estaba en la lista numerada

| Ítem | Referencia |
|---|---|
| Shard 6/8 del CI de ~30 min a ~10 min (`mockk<Activity>()` envenenaba la instrumentación) + guardia contra su reintroducción | #129 |
| Intermitencia «must have a looper» en Seguridad | #111 |
| Intermitencia «must have a looper» en el visor de documentos (`rememberDocumentLoad`) | #141 (efecto **sin medición estadística**) |
| Guardia que falla si se pierden pruebas unitarias (mínimo 1400; hoy 1550) | #140 |
| Dependabot: lote de 5 actualizaciones a mano, Gradle 9.5.1, acciones de artefactos; Gradle ≥ 9.6 ignorado por incompatibilidad con AGP 8.x | #130, #142, #122, #123, #133 |
| Suite instrumentada completa en dos teléfonos reales (799 pruebas, 8/8 en cada uno) y pruebas manuales M7, M8, M12, idioma, anotaciones y notificaciones | — |

### Deuda técnica abierta

| Ítem | Detalle | Siguiente paso |
|---|---|---|
| mockk 1.14.11 | Aplicado a mano pasa las unitarias, pero su CI cayó 3 veces seguidas en el shard 0 con cierres de proceso de Compose en pruebas del Visor | Resolver antes la causa de fondo de esas caídas |
| Cierres de proceso de Compose al destruir la actividad (`SlotWriter.moveSlotGapTo`, `runDetachLifecycle`) en pruebas del Visor | Preexistente y intermitente; misma familia que los «looper» (estado escrito desde IO bajo el reloj de pruebas) | Candidato: `PdfPageItem`/`produceState` del render perezoso (#108) |
| Material 3 1.3.1 sobre runtime 1.11.0 | Alinear al BOM 2026.04.01 (M3 1.4.0) cambia el color del texto de los `OutlinedButton` (38 usos) | Después de la 1.1.3, con helper de color y revisión visual (rama borrada; recuperable desde el PR #116) |
| Kotlin 2.4.21 (#124) | Obliga a mover KSP (`2.2.21-2.0.5`), Hilt, Room y detekt a la vez | Proyecto aparte |
| iText 9 (#125, #128, #136) | Migración completa de API (kernel, layout, io, forms, cleanup) | Proyecto aparte |
| Gradle ≥ 9.6 | AGP 8.13.2 usa `InternalProblems`, eliminada en Gradle 9.6 | Migrar antes a AGP 9 (migración mayor) |
| Dependabot sin revisar: #134 Firebase BOM 35, #137 fragment, #138 core-ktx, #139 Spotless 8.10.4 | Cada uno exige el procedimiento manual de locks y checksums | Revisar uno por uno |
| Demora de las notificaciones de Agenda y Notas | Reportada como «se demora un poco»; sin cifra. En el Edge el permiso de alarmas exactas estaba concedido y no había Doze | Medir (¿segundos o minutos?) |

### Qué falta para dar por cerrada la 1.1.3

| Acción | Quién |
|---|---|
| Subir `DocuSmart-1.1.3-code8.aab` a prueba cerrada y repetir M7/M8 con esa build | Propietario |
| Vigilar Crashlytics 24–48 h y promover a producción gradual (10–20 %) | Propietario |
| Crear la oferta de prueba de 7 días y confirmar el producto anual (#16) | Propietario |
| Publicar `app-ads.txt` (#15) | Propietario |
| Borrar los `DocuSmart_PRUEBA_*.pdf` de Descargas en los dos teléfonos y restaurar el idioma | Propietario |
| Decidir si Play Integrity (#19) entra en el alcance | Propietario |

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

## Intermitencia "must have a looper" en `SecurityFolderFlowsTest` — resuelta (2026-10-10)

Cinco pruebas distintas de esa clase llegaron a fallar, de a una por corrida y no siempre, con `IllegalStateException: The current thread must have a looper!` (`vistaPreviaFallida_…`, `archivoPendienteLocal_…`, `restaurar_sinPoderBorrarLaCopia_…`, `botonInicioDeLaCarpeta_…`, `eliminar_pideConfirmacion_…`). Dejó en rojo el CI de `main` en dos ocasiones (tras #107 y tras #109), porque la guardia de #103 ya no tolera fallas nuevas.

**Causa.** `SecurityViewModel` emite estado y eventos desde `Dispatchers.IO`. `SecurityScreen` los recogía con el contexto vacío. En producción el efecto corre en el hilo principal, pero el reloj de pruebas de Compose usa un despachador "unconfined": el colector se **reanuda en el hilo que emite** (IO), y la recomposición del `LazyColumn` (`SecurityScreen.kt`, `SecureFolderContent`) corría en un hilo sin `Looper`; al crear el programador de prefetch pedía el `Choreographer` y lanzaba la excepción. La traza (`ComposeInternal`, hilo distinto del principal, `TestMonotonicFrameClock.performFrame` → `Recomposer.performRecompose` → `LazyLayout` → `Choreographer.getInstance`) lo muestra.

**Arreglo.** Los tres puntos de recolección de la pantalla (`uiState`, `previewRequest`, `pendingOriginalDelete`) usan `Dispatchers.Main.immediate` (`MAIN_COLLECT_CONTEXT`). En producción no cambia nada (ya corrían en el hilo principal); en las pruebas deja de reanudarse la composición en el hilo del emisor.

**Medición** (clase `SecurityFolderFlowsTest`, 19 pruebas, tras un calentamiento, criterio estricto: `OK (19 tests)`):

| | Edge 30 Neo | Emulador 320×640 |
|---|---|---|
| Antes (8 corridas) | 7 OK, **1 falla** | 5 OK, **3 fallas** |
| Después (8 corridas) | 8 OK | 8 OK |

Las 4 fallas de la línea base fueron la misma excepción, en pruebas distintas. Paquete `features.security` completo con el arreglo: 43/43 en ambos.

**Alcance.** Cubre esta pantalla. La caída de proceso `SlotWriter.moveSlotGapTo` al destruir la actividad (descrita en `visor-render-perezoso.md`) tiene otra firma y **sigue pendiente**: ocurre en pruebas del Visor y de PowerPoint, no en `Security*`. Si aparecen más pantallas con la misma excepción de `looper`, el patrón es el mismo: un ViewModel que emite desde IO y una pantalla que recoge con el contexto vacío.

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

## Shard 6/8 del CI: ~30 min en vez de ~10 — resuelto (2026-10-13)

**Síntoma.** En todos los CI desde el 8/10 el shard 6/8 de pruebas instrumentadas tardaba 25–39 min y los otros siete ~10 min, así que cada PR esperaba ~35 min por un solo shard. Las pruebas de Compose de ese shard duraban 10–70 s cada una **desde la primera**, frente a ~0,6 s en los demás.

**Causa.** `BillingManagerConnectionTest.launchPurchase_conProductoQueNoExisteEnElCatalogo_devuelveFalse` hacía `mockk<Activity>()`. MockK retransforma `android.app.Activity` y toda su jerarquía hasta `Context`; desde entonces **cada** `getResources()` & co. del proceso de instrumentación pasa por `AndroidMockKDispatcher` y no se deshace. Como el reparto de pruebas entre shards es por hash y las clases corren en orden alfabético (`core.billing` antes que casi todo lo de UI), esa prueba cayó al principio del shard 6 y envenenó las ~100 pruebas siguientes. Era la misma causa de los 439 cuadros saltados, de las 72 recolecciones de basura (7 en un shard sano) y del `OutOfMemoryError` visto al correr medio centenar de clases en un solo proceso.

**Cómo se encontró.** Los logs de un shard rápido (5/8) y del lento (6/8) del mismo run: la preparación del emulador era idéntica; el lanzamiento de cada actividad de prueba tardaba ~6 s casi exactos en el lento y ms en el rápido. Se bisecó en el emulador y en un teléfono real ordenando las primeras pruebas del shard:

| Secuencia | `NavGraphHelpersTest` (actividad siguiente) |
|---|---|
| sola | 1,3 s |
| tras construir `BillingManager` | 1,1 s |
| **tras `launchPurchase_…` con `mockk<Activity>()`** | **15,0 s** (y la siguiente, 19 s) |
| tras `appLifecycleTracker_…` | 1,4 s |

`mockk<ContentResolver>()` (4 pruebas) **no** envenena (1,3 s); `Activity` sí por colgar de la ruta caliente de todo `Context`.

**Arreglo.**

- La prueba usa una instancia real sin adjuntar (`Activity()` en el hilo principal: crea un `Handler`) en vez de un mock; esa rama de `launchPurchase` no la usa.
- `AndroidTestMockkGuardTest` (JVM, en `app/src/test/.../architecture`) falla si alguna prueba instrumentada vuelve a hacer `mockk<>`/`spyk<>` de `Activity`, `FragmentActivity`, `ComponentActivity`, `Context`, `ContextWrapper` o `Application` (incluida la forma `val x: Context = mockk()`). Verificada en negativo.

**Resultado** (emulador local, partición idéntica a la del CI, `numShards=8`):

| | Antes (CI) | Después (local) |
|---|---|---|
| Shard 6/8, 102 pruebas | ~28 min | **170 s**, `OK (102 tests)` |
| Shards 0/8 y 1/8 (control) | ~3 min de pruebas | 207 s y 193 s |

El shard 0 cerró el proceso una vez en `ViewerDocumentTypesTest` (la caída preexistente `SlotWriter.moveSlotGapTo` de Compose al destruir la actividad); no tiene relación con este cambio. El tiempo real en CI se confirma con el run de este PR.

## Dependabot: por qué sus PRs no pasan solos y cómo se aplican (2026-10-13)

**Por qué fallan todos.** Salvo los de GitHub Actions, los PRs de Dependabot fallan en la compilación con `Dependency verification failed`: el repo tiene *dependency locking* (`app/gradle.lockfile`, `lockAllConfigurations()`) y verificación de checksums (`gradle/verification-metadata.xml`), y Dependabot no regenera esta última. Los de Actions solo fallan en SonarCloud, que no recibe secretos en PRs de Dependabot.

**Procedimiento manual** (un lote de versiones a la vez):

1. Cambiar las versiones (`gradle/libs.versions.toml` y/o `app/build.gradle.kts`).
2. Si cambia un plugin: `./gradlew --write-verification-metadata sha256 help` (hay que registrar sus checksums antes de poder resolverlo).
3. `./gradlew :app:dependencies --write-locks`.
4. `./gradlew --write-verification-metadata sha256 assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug detekt ktlintCheck`.
5. Revisar que el diff de ambos archivos solo traiga lo esperado (componentes nuevos, ninguno eliminado) y dejar correr el CI.

**Estado de los 12 PRs abiertos el 2026-10-13** (#117 a #128):

| PR | Cambio | Decisión |
|---|---|---|
| #119, #120, #121, #126, #127 | guava 33.7.2, annotation 1.11.0, collection 1.7.0, kover 0.9.11, material-icons-core 1.7.8 | aplicados juntos (lote 1) |
| #122, #123 | `download-artifact` / `upload-artifact`: solo el hash del parche | se pueden fusionar tal cual |
| #117 | mockk 1.13.13 → 1.14.11 | aparte: toca todas las pruebas (y MockK ya mostró efectos globales sobre `Activity`) |
| #118 | Gradle 9.5.0 → 9.8.1 | aparte: cambia la herramienta de build |
| #124 | Kotlin 2.2.21 → 2.4.21 | no ahora: obliga a mover KSP (`2.2.21-2.0.5`), Hilt, Room y detekt a la vez |
| #125, #128 | iText forms 7.2.5 → 9.8.0 y cleanup 3.0.2 → 5.0.8 | no ahora: salto mayor, hay que migrar iText completo (kernel, layout, io, sign…) |

**Hallazgo relacionado.** `compose-bom = "2024.11.00"` deja Material 3 en 1.3.1 sobre runtime/ui 1.11.0 (mezcla no soportada). Alinear al BOM `2026.04.01` (Material 3 1.4.0) cambia el color del texto de los `OutlinedButton` de azul a gris (`onSurfaceVariant`); se decidió no hacerlo antes de la 1.1.3 (PR #116 cerrado, rama `claude/alinear-compose-bom` conserva el trabajo).

## `QrCreatorFlowsTest.url_genera...` intermitente: causa real y arreglo (2026-10-13)

**Síntoma.** `url_generaElCodigoYLoGuardaEnElHistorial` fallaba solo en el emulador lento del CI con `ComposeTimeoutException: Condition still not satisfied after 5000 ms` (el historial seguía vacío). Se retiró de la lista de tolerancia en #114 tras 8 corridas limpias, y falló en el primer CI posterior (#130): esa conclusión era demasiado optimista.

**Causa.** Una carrera en la infraestructura de pruebas, no en el producto. La pantalla guarda el historial en una corrutina que, con el despachador "unconfined" del reloj de pruebas de Compose, continúa en un **hilo de trabajo** (el log lo muestra: `qr_created` se imprime desde un hilo distinto del principal). Mientras, la prueba sondea el historial desde el hilo de instrumentación. `MemoryPrefsContext.getSharedPreferences` hacía `prefsByName.getOrPut(name) { fakePrefs() }` sobre un mapa **sin sincronizar**: `getOrPut` no es atómico y crear los `SharedPreferences` falsos (mocks de MockK) es lento en CI, así que ambos hilos creaban el suyo, uno pisaba al otro en el mapa y la entrada quedaba en el que se descartó. Nunca se reproducía sola ni en local (15/15), porque ahí el hilo de guardado termina antes del primer sondeo.

**Arreglo.**

- `MemoryPrefsContext` (`ScannerTestSupport.kt`): `getOrPut` dentro de `synchronized` y el `store` como mapa sincronizado.
- Mismo patrón en `OnboardingScreenTest`, `SettingsScreenTest` y `StudyScreenTest` (que usaban `getOrPut` sin sincronizar); `StudyTestSupport` ya lo tenía sincronizado.

**Verificación local.** `QrCreatorFlowsTest` 23/23 en 3 corridas; Onboarding, Settings y Study 2/2, 3/3 y 2/2. La carrera depende de la velocidad del CI, así que la confirmación real son los próximos CI.

**Lección.** "0 fallas en N corridas" no prueba que una intermitencia esté corregida si no se entiende la causa: aquí la lista de tolerancia se retiró antes de tiempo. Otras intermitencias del mismo tipo (continuación en hilo de trabajo bajo el reloj de pruebas) ya se arreglaron en `SecurityScreen` (#111); la guardia `AndroidTestMockkGuardTest` cubre otro hallazgo parecido (#129).

## Gradle 9.8.1 (#118): no aplicable con AGP 8.x (2026-10-13)

Se probó a mano: wrapper regenerado con `./gradlew wrapper --gradle-version 9.8.1 --gradle-distribution-sha256-sum <hash>` (el hash que proponía Dependabot, `dce76f55…`, coincide con el publicado por Gradle). Gradle 9.8.1 arranca, pero el plugin de Android no se puede aplicar:

> Plugin 'com.android.internal.application' relies on 'org.gradle.api.problems.internal.InternalProblems', a Gradle internal API that was removed in Gradle 9.6.0.

**Conclusión.** Con AGP 8.13.2 el tope compatible es Gradle **9.5.x** (el actual). Subir Gradle exige migrar antes a AGP 9 (Dependabot ya propuso #69, 8.13.2 → 9.4.1, cerrado el 2026-09-21), que es una migración mayor. Además Gradle 9.8.1 trae Kotlin 2.4.10 en su DSL y pide checksums nuevos de `kotlin-stdlib`/`kotlin-reflect` en `verification-metadata.xml`.

**Qué se hizo.** #118 cerrado; `.github/dependabot.yml` ignora `gradle-wrapper >= 9.6.0` con el motivo, para que no vuelva cada semana (quitar la regla al migrar a AGP 9). Orden correcto para esa migración cuando se aborde: AGP 9 → (Kotlin / KSP / Hilt / Room según lo exija) → Gradle.

## Guardia contra la caída silenciosa de pruebas unitarias (2026-10-13)

**Hallazgo (al probar mockk 1.14, #117).** `junit-jupiter-engine` llega hoy al classpath de pruebas **de forma transitiva a través de mockk 1.13.x**. Con mockk 1.14 desaparece del lockfile y `testDebugUnitTest` sigue terminando **en verde** pero ejecutando **17 pruebas en vez de ~1550** (solo los 2 specs de Kotest). Ninguna prueba falla cuando no se ejecuta, así que ni el CI ni ktlint/detekt lo habrían notado: el PR de Dependabot habría entrado "en verde" apagando el 99 % de las unitarias.

**Qué se hizo (sin tocar la versión de mockk).**

- `testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.14.4")` explícito en `app/build.gradle.kts` (el comentario anterior documentaba la suposición de que llegaba "vía otras dependencias").
- `scripts/check_unit_test_count.py` + `config/ci/unit-tests-min.txt` (mínimo 1400; hoy se ejecutan 1550 en 157 clases): paso nuevo del CI tras `testDebugUnitTest` que falla si se ejecutan menos pruebas. Con sus pruebas propias (`scripts/test_check_unit_test_count.py`), incluido el caso real de 17.
- Lock y checksums sin cambios: el motor ya estaba resuelto, ahora además se declara.

**Estado de mockk 1.14.11 (#117 / PR #132).** Aplicado a mano funciona en las unitarias (1550, 0 fallas), pero su CI cayó 3 veces seguidas en el shard 0 con cierres de proceso de Compose en pruebas del Visor (`ViewerDocumentTypesTest`, `ViewerPdfTest`), cuando ese shard pasaba limpio en los CI de `main`. No se pudo aislar si mockk 1.14 empeora esa intermitencia o fue mala suerte (el emulador local, con 15 GB de RAM y ~2 GB libres, no aguantó una comparación larga). Queda abierto, sin fusionar, hasta resolver la causa de fondo de esas caídas (estado de Compose escrito desde hilos de IO bajo el reloj de pruebas).

## Visor de documentos: `rememberDocumentLoad` escribía estado desde IO (2026-10-13)

**Síntoma.** `ViewerDocumentTypesTest.excel_conUnaSolaHojaNoMuestraPestanas` falló en CI con `IllegalStateException: The current thread must have a looper!` (la misma firma que ya se corrigió en Seguridad, #111). La traza de `ComposeInternal` pasa por `ExcelViewerContent` → `LazyColumn` → `LazyLayout` → `Choreographer.getInstance()` en un hilo distinto del principal.

**Causa (por lectura del código y de la traza).** `rememberDocumentLoad` (carga compartida de Word/Excel/PowerPoint/Texto) escribía `isLoading = false` y `hasError = true` **dentro** del bloque `withContext(Dispatchers.IO)`. En producción es inofensivo (el estado se publica y la recomposición vuelve al hilo principal), pero con el despachador "unconfined" del reloj de pruebas de Compose la recomposición corre en el hilo que escribe, sin `Looper`.

**Arreglo.** El bloque de IO solo calcula y devuelve `(dato, falló)`; el estado (`data`, `hasError`, `isLoading`) se escribe después con `withContext(Dispatchers.Main.immediate)`, igual que en `SecurityScreen`. En producción el efecto ya corre en el hilo principal, así que no cambia nada observable. De paso, `CancellationException` ya no se traga como error de lectura.

**Verificación.** ktlint, detekt y ensamblado OK; `ViewerDocumentTypesTest` pasó 23/23 en el emulador local tras el cambio. **No hay una medición estadística de la intermitencia**: el emulador local (15 GB de RAM, ~2 GB libres) no completó una serie larga de repeticiones, así que la confirmación real son los CI siguientes.

**Pendiente relacionado.** Los cierres de proceso de Compose al destruir la actividad en pruebas del Visor (`SlotWriter.moveSlotGapTo`, `runDetachLifecycle`) son de la misma familia (composición tocada fuera del hilo principal) pero con otra firma; este cambio solo cubre `rememberDocumentLoad`. El render perezoso del PDF (`PdfPageItem`/`produceState`, #108) escribe estado tras `withContext(IO)` y es el candidato siguiente.

## Gradle 9.5.0 → 9.5.1 (#135) (2026-10-14)

Parche dentro del tope compatible con AGP 8.13.2 (Gradle < 9.6, ver la sección de Gradle 9.8.1). Aplicado con la tarea `wrapper` y el hash oficial publicado por Gradle (`bafc141b…`, idéntico al que proponía Dependabot).

**Verificación local con Gradle 9.5.1:** `assembleDebug`, `assembleDebugAndroidTest`, `testDebugUnitTest` (**1550 pruebas, 0 fallas**; guardia de conteo OK), `lintDebug`, `detekt` y `ktlintCheck`: BUILD SUCCESSFUL. A diferencia de 9.8.1 (Kotlin 2.4.10 embebido) **no** pidió checksums nuevos: `verification-metadata.xml` y los lockfiles no cambian.

**CI de Dependabot (#135):** solo cayó el shard 5/8, por la falla de infraestructura del emulador (`Error on ZipFile unknown archive` al instalar el SDK; el emulador nunca arrancó), no por el wrapper.

