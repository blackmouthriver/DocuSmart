# Módulo: Ajustes + Premium

> Parte de [`CONTEXT.md`](../../CONTEXT.md). Cubre Ajustes (idioma, tema,
> almacenamiento, privacidad, ayuda, restablecer, acerca de, compartir,
> calificar) y Premium (planes, compra, restaurar, límites de uso free).

**Estado:** 2 bugs reales corregidos — el límite diario de uso gratis para
Herramientas PDF (requerimiento #16) existía por completo en
`DailyLimitManager` pero nunca se llamaba desde `PdfToolsViewModel`, así que
nunca se aplicaba en la práctica; y "Restablecer configuración" forzaba
español sin importar el idioma del dispositivo (mismo tipo de bug ya
corregido en TTS/reconocimiento de voz de Modo Estudio). **RF-SET-06
resuelto 2026-08-24:** el idioma por defecto de una instalación nueva ahora
también usa el idioma del dispositivo (antes solo se aplicaba en el flujo de
restablecer). **RF-PREM-05 resuelto 2026-08-25:** Play Billing real
conectado (`core/billing/BillingManager.kt`), reemplazando el placeholder
`simulatePurchase()` — ver §8 para el detalle y las limitaciones reales
(no verificable de punta a punta hasta que existan productos en Play
Console). 14 tests nuevos (sin cambios por RF-PREM-05 — ver justificación
en §8). **RF-SET-07 implementado 2026-08-29, ver §12** — color de acento
elegible por el usuario (6 opciones), cierra el backlog completo de este
módulo (el otro backlog pendiente, Escáner, es un módulo aparte).
**Código relacionado:** `features/settings/**`, `features/premium/**`,
`core/ads/DailyLimitManager.kt`, `core/premium/PremiumManager.kt`,
`core/billing/BillingManager.kt` (nuevo), `core/ui/LanguageManager.kt`,
`core/ui/components/DailyLimitDialog.kt` (compartido con Conversión).

---

## 1. Alcance

Dos módulos relacionados por la monetización freemium:

1. **Ajustes** — idioma, tema, almacenamiento/caché, privacidad, ayuda,
   restablecer configuración, acerca de, compartir/calificar la app.
2. **Premium** — planes (mensual/anual, "de por vida" quitado 2026-09-01,
   ver §8.1), compra, restaurar compras, y el límite diario de uso gratis
   que empuja hacia Premium (conversiones y herramientas PDF).

---

## 2. Requerimientos funcionales

- **RF-SET-01** El sistema debe permitir cambiar el idioma de la app entre los 5 soportados (es/en/de/pt/ru), aplicando el cambio de inmediato.
- **RF-SET-02** El sistema debe permitir cambiar el tema (claro/oscuro/sistema).
- **RF-SET-03** El sistema debe mostrar el almacenamiento usado por archivos generados por la app (conversiones + herramientas PDF), con conteo y tamaño real, y permitir borrarlos con confirmación.
- **RF-SET-04** El sistema debe permitir restablecer tema e idioma a sus valores por defecto, y borrar los archivos generados por la app.
- **RF-SET-05** "Restablecer configuración" debe restablecer el idioma al que tendría la app recién instalada en este dispositivo, no a un idioma fijo sin relación con el dispositivo del usuario.
- **RF-PREM-01** El sistema debe mostrar los 3 planes Premium (mensual, anual, de por vida) con precio y ahorro relativo.
- **RF-PREM-02** El sistema debe limitar a los usuarios no-Premium a 5 conversiones y 3 usos por cada herramienta PDF (unir/dividir/comprimir/rotar) al día, reseteando el contador cada día.
- **RF-PREM-03** El sistema debe permitir desbloquear un uso adicional viendo un anuncio recompensado, tanto para conversiones como para herramientas PDF.
- **RF-PREM-04** El sistema debe ocultar los banners de anuncios y los límites de uso para usuarios Premium.

- **RF-PREM-05** ✅ Conectar Play Billing real (comprar/restaurar compras) — antes simulado con un `delay()` y un flag local. Resuelto 2026-08-25, ver §8 para el detalle y las limitaciones reales.
- **RF-SET-06** ✅ Detección de idioma por defecto para un usuario que **nunca** ha abierto la app (requerimiento #13 original) — distinto de RF-SET-05, que es sobre qué pasa al restablecer configuración ya usada. Resuelto 2026-08-24 usando el idioma del dispositivo (no geografía de Play Store, que no es verificable desde el cliente).
- **RF-SET-07** ✅ Personalización de colores/tema por el usuario (requerimiento #19 original). Implementado 2026-08-29, ver §12.

### Backlog
*(vacío — RF-PREM-05/RF-SET-06/RF-SET-07, el backlog original de este módulo, está implementado)*

---

## 3. Requerimientos no funcionales

- **RNF-SET-01 (consistencia de idioma):** ninguna funcionalidad de la app debe forzar un idioma fijo cuando existe una señal razonable del idioma preferido del usuario (idioma configurado de la app, o si no hay ninguno, idioma del dispositivo) — mismo lineamiento que ya aplica a TTS y reconocimiento de voz en Modo Estudio.
- **RNF-PREM-01 (límites por recurso, no globales):** cada herramienta PDF (unir/dividir/comprimir/rotar) debe tener su propio contador diario independiente — usar mucho una herramienta no debe agotar el límite de las demás.
- ~~**RNF-PREM-02** (transparencia del placeholder de billing)~~ — obsoleto desde RF-PREM-05: ya no hay placeholder, `BillingManager` conecta Play Billing real.
- **RNF-PREM-03 (no verificable sin Play Console):** `BillingManager` puede compilar y conectarse correctamente a Play Billing sin que eso implique que las compras funcionan de punta a punta — requiere que los 3 productos existan en Play Console y la app esté subida a una pista de prueba. No asumir que "compila" significa "probado".

---

## 4. Historias de usuario con criterios de aceptación

### HU-SET-01 — Restablecer configuración respeta el idioma del dispositivo
**Como** usuario que configuró la app en un idioma distinto al español,
**quiero** que "Restablecer configuración" no me cambie el idioma a español sin razón,
**para** no tener que volver a configurar el idioma cada vez que restablezco.

- **AC1** Dado que mi dispositivo tiene un idioma soportado por la app (es/en/de/pt/ru/ja/ko/zh/it/fr, ampliado 2026-09-04) configurado como idioma del sistema, cuando toco "Restablecer configuración" y confirmo, entonces la app queda en ese idioma, no en español.
- **AC2** Dado que mi dispositivo tiene un idioma no soportado (por ejemplo, árabe), cuando restablezco, entonces la app usa español como respaldo — no falla ni queda en un estado inconsistente.
- **AC3** El tema (claro/oscuro/sistema) y el borrado de archivos generados por la app siguen funcionando igual que antes.

*(Corrige bug real: el botón llamaba `languageManager.setLanguage(AppLanguage.SPANISH)` incondicionalmente — mismo patrón que forzaba español en TTS y reconocimiento de voz.)*

### HU-PREM-01 — Límite diario real en Herramientas PDF
**Como** usuario no-Premium,
**quiero** que el límite de 3 usos gratis por herramienta PDF se aplique de verdad,
**para** que el plan Premium tenga un valor real y consistente con lo que promete la pantalla de Premium.

- **AC1** Dado que uso una herramienta PDF (por ejemplo, Unir) 3 veces en el mismo día sin ser Premium, cuando intento una cuarta vez, entonces veo el diálogo de límite diario alcanzado, igual que ya ocurre en Conversión.
- **AC2** Dado que veo un anuncio recompensado desde ese diálogo, cuando lo completo, entonces obtengo un uso adicional para esa herramienta.
- **AC3** Dado que agoté el límite de Unir, cuando intento usar Dividir (una herramienta distinta), entonces puedo hacerlo — cada herramienta tiene su propio contador.
- **AC4** Un usuario Premium nunca ve el límite ni el diálogo.

*(Corrige un hueco real: `DailyLimitManager.canUsePdfTool()`/`registerPdfTool()` ya existían completos — con sus 4 contadores independientes y todo — pero `PdfToolsViewModel` nunca los llamaba. El límite estaba "implementado" en el sentido de que el código existía, pero no tenía ningún efecto real para el usuario.)*

### HU-SET-02 — Elegir un color de acento
*(Implementado 2026-08-29 — ver §12.)*

**Como** usuario que quiere que la app se sienta un poco más propia,
**quiero** elegir el color de acento entre varias opciones,
**para** personalizar la apariencia sin depender solo de claro/oscuro/sistema.

- **AC1** Dado que entro a Ajustes → Color de acento, cuando abro el selector, entonces veo 6 opciones con su muestra de color y el nombre.
- **AC2** Dado que elijo un color distinto al actual, cuando lo toco, entonces toda la app (botones, pestaña seleccionada, enlaces) refleja el nuevo acento de inmediato, sin reiniciar la app.
- **AC3** Dado que elijo un acento, cuando cierro y reabro la app, entonces el acento elegido sigue aplicado.
- **AC4** Dado que cambio de tema (claro/oscuro/sistema), cuando lo hago, entonces el acento elegido se mantiene, ajustado a una variante legible para ese tema — no se resetea a Azul.
- **AC5** Dado que toco "Restablecer configuración" en Ajustes, cuando confirmo, entonces el color de acento vuelve a Azul (el valor por defecto), igual que tema e idioma.

---

## 5. Bugs de QA a corregir (trazabilidad)

| Bug / hallazgo | HU que lo cubre | Estado |
|---|---|---|
| Requerimiento #16 "Límite de uso de herramientas para no-premium" — CONTEXT.md lo marcaba como "Pendiente" | HU-PREM-01 | ✅ Corregido — la lógica ya existía sin usar; se conectó a `PdfToolsViewModel` con el mismo patrón que ya usaba Conversión. Se extrajo `DailyLimitDialog` a un componente compartido (antes vivía duplicado y privado en `ConverterScreen.kt`). |
| **Bug real encontrado hoy (no reportado en la QA):** "Restablecer configuración" en Ajustes forzaba español sin importar el idioma del dispositivo. | HU-SET-01 | ✅ Corregido — nuevo `LanguageManager.deviceDefaultLanguage()`. **Extendido 2026-08-24 (RF-SET-06):** `loadLanguage()` ahora también usa `deviceDefaultLanguage()` como respaldo cuando no hay idioma guardado (instalación nueva) — antes ese caso quedaba fijo en español. |
| "Restaurar compras / cancelar suscripción" simulado, falta Play Billing real | RF-PREM-05 | ✅ Resuelto 2026-08-25 — `BillingManager` conecta Play Billing real (comprar, restaurar, precios localizados). Ver §8 para limitaciones reales (no verificable de punta a punta sin productos en Play Console). |
| "Falta personalización de colores/estilos por el usuario" | RF-SET-07 | ✅ Resuelto 2026-08-29 — selector de color de acento (6 opciones), ver §12. |
| Almacenamiento (mostrar uso + borrar caché) | RF-SET-03 | Confirmado funcionando correctamente — cuenta y tamaño reales, borrado real. |
| Ayuda, privacidad, acerca de, compartir, calificar | — | Confirmados funcionando correctamente (intents reales, URLs reales con el package name real). |
| **Bug real encontrado hoy (reportado por el usuario): la app se cierra al cambiar de idioma.** | — | ✅ Corregido — ver §11 para el análisis de causa raíz completo. No era un bug del idioma en sí, sino una condición de carrera preexistente en la inicialización de AdMob, expuesta por el reinicio de `MainActivity` que dispara cualquier cambio de idioma. |

---

## 6. Plan de pruebas unitarias

| # | Cobertura | Estado |
|---|---|---|
| 1 | `DailyLimitManagerTest` — límite de conversiones alcanzado, extra por anuncio aumenta el límite, límite de herramienta PDF alcanzado, contadores independientes por herramienta, extra de herramienta PDF por anuncio, conteo por herramienta. | ✅ 8 tests, en verde |
| 2 | `LanguageManagerTest` — idioma del dispositivo soportado se detecta correctamente, idioma no soportado cae a español, reconoce inglés explícitamente con variante regional (`en-US`), instalación nueva usa el idioma del dispositivo (RF-SET-06), instalación nueva con idioma no soportado cae a español, un idioma ya guardado no se pisa con el del dispositivo. | ✅ 6 tests, en verde |
| 3 | `PremiumManager`/`PremiumViewModel`/`BillingManager` — no cubiertos. `BillingManager` envuelve `BillingClient` (clase del framework, no fácilmente mockeable sin infraestructura de instrumentación pesada) — mismo criterio ya aplicado a `AdManager`, que tampoco tiene tests. | Sin cubrir, consistente con el resto de wrappers de SDKs de terceros del proyecto. |
| 4 | `ThemeManagerTest` (RF-SET-07, ver §12) — sin nada guardado usa Sistema/Azul por defecto, `setAccentColor` persiste sin afectar el tema guardado, un acento guardado se recupera en una instancia nueva, un valor guardado inválido cae a Azul. | ✅ 4 tests, en verde |

---

## 8. Play Billing real — RF-PREM-05 (2026-08-25)

Reemplaza `PremiumManager.simulatePurchase()` (eliminado) por
`core/billing/BillingManager.kt`, usando Play Billing Library 9.1.0.

- **Productos:** `com.docsmart.premium.monthly`/`annual` (suscripción,
  `ProductType.SUBS`) y `com.docsmart.premium.lifetime` (compra única,
  `ProductType.INAPP`) — mismos IDs que ya declaraba `PremiumRepository`
  desde antes, ahora sí conectados a Play Billing real.
- **Flujo:** conecta al iniciar (`startConnection`), consulta
  `ProductDetails` reales de los 3 productos (reemplaza el precio fijo
  `"$2.99"` hardcodeado en `PremiumRepository` por el precio real y
  localizado que devuelve Play Store, en cuanto está disponible), y
  restaura compras existentes automáticamente.
- **Restaurar compras:** `restorePurchases()` ahora consulta de verdad
  (`queryPurchasesAsync` para `SUBS` e `INAPP`) en vez de simular "no se
  encontraron compras" siempre.
- **Confirmación de compra (`acknowledgePurchase`):** obligatoria dentro de
  3 días o Google reembolsa automáticamente — implementada para ambos tipos
  de producto.
- **NO implementado a propósito — verificación server-side:** no se valida
  la firma de la compra contra la clave pública de licencias de Play
  Console (RSA). Esa clave solo existe una vez que la app se crea en Play
  Console, y el proyecto no tiene backend propio para verificar del lado
  del servidor (arquitectura documentada: "solo Firebase gestionado", ver
  `CONTEXT.md` §1). Se confía en el resultado de `BillingClient` +
  `PurchaseState` — razonable para una app de un solo desarrollador sin
  backend, pero vale la pena revisar si el volumen de fraude lo justifica
  más adelante.
- **No verificable de punta a punta todavía:** `queryProductDetails()` no
  encuentra nada hasta que los 3 productos existan en Play Console
  (Monetizar → Productos), lo cual requiere que la app ya esté subida al
  menos a una pista de prueba (ver `docs/requirements/deployment.md`). El
  código compila y se conecta correctamente a Play Billing, pero la compra
  real todavía no se probó de punta a punta — no asumir que "compila"
  significa "funciona".
- **Sin tests nuevos:** `BillingManager` envuelve `BillingClient` (clase de
  framework, no mockeable sin infraestructura pesada) — mismo criterio ya
  aplicado a `AdManager` en este proyecto, que tampoco tiene tests.
- Verificado en verde: `assembleDebug`/`bundleRelease` + `detekt` +
  `testDebugUnitTest` (92 tests, 0 fallos) + `lintDebug` (0 errores).

---

## 8.1 Se quita el plan "De por vida" + bug crítico corregido (2026-09-01)

**Pedido explícito del usuario:** eliminar el plan de pago único ("De por
vida", `com.docsmart.premium.lifetime`). Quedan solo Mensual y Anual, ambos
`ProductType.SUBS`.

- **`PremiumRepository.getAvailablePlans()`**: quitada la tercera entrada
  (`id = "lifetime"`). Recursos de texto asociados quitados en los 5
  locales (`premium_plan_lifetime`, `premium_period_onetime`,
  `premium_savings_best_value`).
- **`BillingManager`**: quitada toda la ruta `ProductType.INAPP` --
  `PRODUCT_LIFETIME`, la consulta `inAppParams` en `queryProductDetails()`,
  el caso especial de `resolveOfferToken()`/`hasValidOffer` en
  `buildPurchaseParams()` (ahora toda compra pasa por `subscriptionOfferDetails`,
  sin rama condicional), y la consulta `ProductType.INAPP` en
  `restorePurchases()`.

**Hallazgo real crítico (bug introducido y corregido en la misma sesión,
no llegó a `main` sin arreglar)**: al simplificar `BillingManager` se quitó
`.enableOneTimeProducts()` de `PendingPurchasesParams.newBuilder()` por
parecer código muerto (ya no se vende ningún producto `INAPP`). Play
Billing Library 9.1.0 exige esa llamada de forma obligatoria pese al
nombre -- sin ella, `PendingPurchasesParams.Builder.build()` lanza
`IllegalArgumentException: Pending purchases for one-time products must
be supported`, y como `BillingManager` es un `@Singleton` de Hilt
inyectado en `PremiumViewModel`, esa excepción ocurre en el constructor la
PRIMERA vez que se compone `PremiumScreen` -- **crash real de la app cada
vez que un usuario real tocara "Premium"**, sin importar el catálogo de
productos. Se detectó por verificación manual en el dispositivo (no por
las pruebas automatizadas: `PremiumScreenTest`/todo intento de Compose UI
Testing sobre esta pantalla mockea `BillingManager` por completo, así que
ninguna prueba automatizada llega a ejecutar su constructor real -- la
verificación manual en dispositivo real sigue siendo necesaria para este
tipo de wrapper de SDK de terceros, tal como ya advierte la fila 3 de la
tabla en §6). Corregido restaurando `.enableOneTimeProducts()` con un
comentario explicando por qué es obligatorio pese a no vender productos de
un solo pago. Verificado en dispositivo real tras el fix: Premium abre sin
crashear, muestra solo Mensual/Anual.

Gauntlet verde tras ambos cambios: `compileDebugKotlin`, `detekt`,
`lintDebug`, `testDebugUnitTest`, `connectedDebugAndroidTest` (30/30).

---

## 9. UMP — consentimiento de anuncios UE/Reino Unido (2026-08-26)

Hallazgo de `deployment.md` §5.3 (auditoría de datos, 2026-08-25): no había
ningún SDK de consentimiento (Google UMP) implementado — requisito de
Google/GDPR antes de mostrar anuncios personalizados a usuarios en la
Unión Europea/Reino Unido.

- **Dependencia nueva:** `com.google.android.ump:user-messaging-platform:4.0.0`
  — artefacto **separado** de `play-services-ads` (no viene incluido),
  versión verificada contra el índice real de Google Maven
  (`dl.google.com/android/maven2`) antes de fijarla, no asumida de la
  documentación de terceros.
- **Se movió la inicialización de anuncios de `DocuSmartApplication` a
  `MainActivity`.** `requestConsentInfoUpdate()` necesita una `Activity`
  real para poder mostrar el formulario de consentimiento — no se puede
  hacer desde `Application.onCreate()`, que no tiene ninguna Activity
  todavía. `MobileAds.initialize()`/`AdManager.initialize()` ahora solo se
  disparan **después** de que `ConsentInformation.canRequestAds()` sea
  verdadero (con o sin formulario mostrado, según si el usuario está en
  una región que lo requiere).
- **Guard de instrumentación reutilizado:** la función
  `isRunningUnderInstrumentation()` (antes privada en
  `DocuSmartApplication`, ya existía desde la sesión de Compose UI
  Testing) se movió a `core/ui/util/InstrumentationUtils.kt` para
  reutilizarla también en `MainActivity` — mismo motivo de siempre: evitar
  que `connectedAndroidTest` dispare una llamada de red real de
  consentimiento/anuncios.
- **Punto de acceso a "Opciones de privacidad" en Ajustes** — exigido por
  la política de UMP: no basta con mostrar el formulario una sola vez al
  abrir la app, tiene que haber una forma de cambiar la decisión después.
  Nuevo ítem condicional en `SettingsScreen.kt` (sección Privacidad),
  visible solo si `consentInformation.privacyOptionsRequirementStatus ==
  REQUIRED`, que abre `UserMessagingPlatform.showPrivacyOptionsForm(...)`.
- **Verificado de punta a punta en el dispositivo real** (motorola edge 30
  neo), los dos escenarios reales:
  1. **Geografía real (fuera de UE/Reino Unido):** `IABTCF_gdprApplies: 0`
     en el log de UMP — no se requiere consentimiento, no se muestra
     ningún formulario, los anuncios se inicializan de inmediato
     (`AdManager: MobileAds inicializado ✅`, interstitial y rewarded
     cargados).
  2. **Geografía UE simulada** (`ConsentDebugSettings.DebugGeography
     .DEBUG_GEOGRAPHY_EEA`, solo en `BuildConfig.DEBUG`, con
     `addTestDeviceHashedId("EB3ECF44CF3E05437B137D30F852213B")` — mismo
     hash que ya se usaba para AdMob, confirmado en el propio log de UMP
     al arrancar: "Use new ConsentDebugSettings.Builder()
     .addTestDeviceHashedId(...)" imprimió exactamente ese valor):
     `IABTCF_gdprApplies: 1`, el formulario real de consentimiento
     ("Publisher Test Ads asks for your consent...") se muestra
     correctamente, y los anuncios **no** se inicializan hasta después de
     tocar "Consent" (confirmado por orden de timestamps en logcat: el
     registro `CONSENT_SIGNAL_SUFFICIENT` sale 0.08s antes de
     `AdManager: iniciando MobileAds`).
  Sin la simulación de geografía EEA registrada (sin
  `addTestDeviceHashedId`), el override **no** tomaba efecto en un
  dispositivo real — confirmado con el primer intento, que resolvió
  `IABTCF_gdprApplies: 0` a pesar de tener `setDebugGeography(EEA)`
  puesto; necesario documentarlo para no repetir la confusión.
- Verificado también: `connectedDebugAndroidTest` (6 pruebas, sin
  regresión) y `testDebugUnitTest`/`detekt`/`lintDebug` en verde.
- **Sin test automatizado** — el flujo de UMP depende de red real
  (`requestConsentInfoUpdate`) y de una `Activity` real mostrando un
  `WebView` del formulario; se optó por la verificación manual de arriba,
  igual que con RF-SEC-08 (`security.md` §11).

---

## 10. Preguntas abiertas

| Pregunta | Notas |
|---|---|
| Play Billing real (RF-PREM-05) ya está conectado en código — ¿cuándo se crean los 3 productos en Play Console para poder probarlo de punta a punta? | Requiere que la app ya esté subida al menos a una pista de prueba (ver `docs/requirements/deployment.md`) antes de poder crear los productos y probar una compra real. |
| ¿Detección de idioma por geografía de Play Store (RF-SET-06) es prioridad, o basta con que el dispositivo decida (como ya corregido en HU-SET-01)? | Resuelto 2026-08-24: se extendió el idioma del dispositivo también al primer inicio (`loadLanguage()`), sin depender de geografía de Play Store — no es algo verificable desde el cliente, y el idioma del dispositivo es el estándar de facto en apps Android. |
| ¿Personalización de colores (RF-SET-07) es prioridad frente a otros pendientes del roadmap? | **Resuelto 2026-08-29** — sí, implementado. Ver §12. |

---

## 11. Crash real al cambiar de idioma (2026-08-29)

Reportado por el usuario en uso manual: "cuando realizo el cambio de
lenguaje a otro la aplicación se cierra". Reproducido de forma confiable
en dispositivo real y confirmado con logcat.

**Causa raíz — no es un bug del idioma, es una condición de carrera
preexistente en AdMob expuesta por el reinicio de Activity:** cualquier
cambio de idioma dispara en `MainActivity` un `LaunchedEffect` que
reinicia la Activity vía `startActivity(... FLAG_ACTIVITY_NEW_TASK or
FLAG_ACTIVITY_CLEAR_TASK)` para que el nuevo locale tome efecto (patrón
correcto y necesario). Ese reinicio vuelve a ejecutar
`onCreate() → requestAdsConsentThenInitializeAds() →
initializeAdsIfAllowed()`, que lanzaba `adManager.initialize()` en
`lifecycleScope.launch(Dispatchers.IO)`. `AdManager.initialize()` llama
`MobileAds.initialize(context) { ...; loadInterstitial() }`, y
`InterstitialAd.load()` (dentro de `loadInterstitial()`) **exige
ejecutarse en el hilo principal** — requisito documentado por Google, no
opcional.

En un arranque en frío normal, el SDK de Google Mobile Ads tarda lo
suficiente en resolver la inicialización como para que su callback de
finalización termine llegando al hilo principal sin problema. Pero en un
reinicio **en caliente** (proceso ya corriendo, clases ya cargadas, SDK
con estado cacheado) la inicialización se resuelve casi instantáneamente
y el callback se ejecuta de forma síncrona en el mismo hilo que lo llamó
— el hilo de `Dispatchers.IO`, no el principal. Stack trace real
capturado en dispositivo:

```
java.lang.IllegalStateException: #008 Must be called on the main UI thread.
	at com.google.android.gms.ads.interstitial.InterstitialAd.load
	at com.docsmart.core.ads.AdManager.loadInterstitial(AdManager.kt:56)
	at com.docsmart.core.ads.AdManager.initialize$lambda$0(AdManager.kt:48)
	at com.google.android.gms.ads.MobileAds.initialize
	at com.docsmart.core.ads.AdManager.initialize(AdManager.kt:45)
	at com.docsmart.MainActivity$initializeAdsIfAllowed$1.invokeSuspend(MainActivity.kt:272)
```

Esto significa que el bug **no era exclusivo del cambio de idioma** — es
el mismo código que corre en cada arranque de la app, solo que el cambio
de idioma es el único punto de la app que provoca un reinicio de
`MainActivity` con el proceso ya "caliente", que es justo la condición
que dispara la carrera. No se encontró ningún otro punto de la app que
reinicie la Activity de la misma forma (búsqueda completa de
`Dispatchers.IO` combinado con llamadas a AdMob/`Toast`/APIs que exigen
hilo principal en todo `app/src/main/java` — sin otros hallazgos).

**Corregido** quitando `Dispatchers.IO` del `lifecycleScope.launch` en
`MainActivity.initializeAdsIfAllowed()` — `MobileAds.initialize()` no
bloquea (ya es asíncrono internamente) y Google documenta explícitamente
que debe llamarse desde el hilo principal, así que no había ninguna razón
real para despacharlo a IO. Verificado en dispositivo con 3 cambios de
idioma consecutivos (en caliente, el escenario que antes crasheaba
siempre) sin ningún crash.

---

## 12. RF-SET-07/HU-SET-02 — Color de acento personalizable (2026-08-29)

Cierra el backlog completo de este módulo, en la misma pasada en la que el
usuario pidió continuar con las HU pendientes de todo el proyecto (ver
también `visor-biblioteca.md` §15 y `study.md` §9).

**Alcance elegido — recolorar el acento, no repintar toda la app:** la app
ya tiene una identidad visual cuidada (banners con gradiente propio,
colores semánticos por tipo de archivo — PDF rojo, Word azul, etc. — en
`Color.kt`, usados directamente en decenas de pantallas, no solo vía
`MaterialTheme.colorScheme`). Dejar que el usuario eligiera un color
arbitrario y aplicarlo a *todo* habría chocado con esa identidad (un banner
"DocuSmart azul" con acento naranja se vería roto, no personalizado). Se
optó por un catálogo curado de 6 colores que solo recolorean
`primary`/`onPrimary`/`primaryContainer`/`onPrimaryContainer` — los tonos
que Material3 usa para botones, la pestaña seleccionada, enlaces y estados
de foco — dejando fondos, superficies, colores de error y los colores por
tipo de archivo exactamente como estaban.

- **`AccentColor.kt`** (nuevo, `core/ui/theme/`) — enum con 6 valores
  (Azul/Morado/Verde/Naranja/Rosa/Turquesa), cada uno con un `AccentTone`
  (`primary`/`onPrimary`/`container`/`onContainer`) para claro y otro para
  oscuro — 8 colores por acento, agrupados en una clase propia en vez de 8
  parámetros sueltos en el constructor del enum (superaba el límite
  `LongParameterList` de detekt en el primer intento). **`BLUE` reutiliza
  exactamente los valores que la app ya tenía** (`DocuBlue`/`PrimaryDark`/
  `SmartBlue`) — es el valor por defecto, así que no cambia nada
  visualmente para quien nunca toca este ajuste nuevo.
- **`ThemeManager.kt`** (modificado) — gana `accentColor: StateFlow<AccentColor>`
  y `setAccentColor()`, persistido en la misma `SharedPreferences` que ya
  usaba para el tema (`"accent_color"`, clave independiente de `"theme"` —
  cambiar uno no afecta al otro, ver AC4 de HU-SET-02).
- **`Theme.kt` — `DocuSmartTheme`** (modificado) — gana un parámetro
  `accentColor: AccentColor = AccentColor.BLUE`; tras calcular el esquema
  base (claro/oscuro/sistema, sin tocar esa lógica existente), aplica
  `.copy(primary=..., onPrimary=..., primaryContainer=..., onPrimaryContainer=...)`
  con el tono claro u oscuro del acento elegido según corresponda. **No se
  aplica sobre Material You dinámico** (`dynamicColor`, hoy sin usar en la
  app) — ahí el acento ya lo decide el wallpaper del sistema, pisarlo no
  tendría sentido.
- **`MainActivity.kt`** (modificado) — colecta `themeManager.accentColor` y
  lo pasa a `DocuSmartTheme`, mismo patrón ya usado para `currentTheme`.
- **`SettingsScreen.kt`** (modificado) — nuevo ítem "Color de acento" en la
  sección Personalización, justo debajo de "Tema", con el nombre del
  acento actual como subtítulo (mismo patrón que "Tema" ya usaba). Abre un
  `AlertDialog` con un `RadioButton` + un círculo de color (`Box` con
  `background(accent.swatch, CircleShape)`) + el nombre por cada una de
  las 6 opciones — mismo patrón visual que el diálogo de idioma/tema ya
  existentes. "Restablecer configuración" ahora también llama
  `themeManager.setAccentColor(AccentColor.BLUE)` junto al tema/idioma que
  ya restablecía (AC5).
- **i18n:** igual que `AppTheme`, el enum guarda una etiqueta en español
  como valor de depuración (`label`), pero la UI nunca la usa directamente
  — un `accentColorLabel()` `@Composable` en `SettingsScreen.kt` mapea
  cada valor a `stringResource()`, mismo patrón exacto que `themeLabel()`
  ya usaba para claro/oscuro/sistema. 6 strings nuevos × 5 idiomas.
- **4 tests nuevos** (`ThemeManagerTest`) — ver fila 4 de §6.
- **detekt:** un hallazgo real corregido durante el desarrollo (no
  baselineado) — el primer diseño del enum `AccentColor` con 10 parámetros
  sueltos en el constructor superaba `LongParameterList` (límite 8);
  agrupados en `AccentTone` (4 campos) en vez de suprimir el hallazgo.

**Verificado end-to-end en el dispositivo real (app en español):**
cambiado el acento a Rosa desde Ajustes → confirmado que el encabezado
"PERSONALIZACIÓN", los íconos de cada ítem, la pestaña "Ajustes" de la
barra inferior y el enlace "Ver todos" de Home cambiaron a rosa de
inmediato, sin reiniciar la app, mientras el banner azul de marca y los
colores por tipo de archivo (Word/Imagen) se mantuvieron intactos (AC1/AC2).
Cerrada y reabierta la app: el acento Rosa se mantuvo (AC3). Cambiado el
tema a Oscuro con Rosa activo: los mismos elementos usaron la variante
oscura del tono rosa (`darkPrimary`/`darkOnPrimary`/etc.), con las
superficies correctamente oscuras — confirma que acento y tema son
independientes y compatibles entre sí (AC4). Restablecido tema a Sistema y
acento a Azul manualmente para dejar el dispositivo en su estado por
defecto tras la verificación.
- Verificado también: `testDebugUnitTest`/`detekt`/`lintDebug` en verde.

---

## 13. Crash real al entrar a Premium — segunda instancia del mismo bug de §11 (2026-08-30)

Reportado por el usuario en uso manual: "al intentar ingresar a
DocuSmart Premium se sale la aplicación" (y, de forma intermitente,
también al entrar a Ajustes). Reproducido de forma confiable en
dispositivo real y confirmado con logcat.

**Causa raíz:** exactamente la misma familia de bug que §11
(`InterstitialAd.load()`/`RewardedAd.load()` exigen hilo principal), pero
en un punto distinto que la búsqueda de §11 no cubrió porque no pasa por
`AdManager.initialize()`. `BillingManager` corre en su propio
`CoroutineScope(SupervisorJob() + Dispatchers.IO)`; al conectar con Play
Billing por primera vez (primera visita a Premium tras instalar/abrir la
app), `onBillingSetupFinished` dispara `restorePurchases()` en ese scope
IO. Si el usuario no tiene compras, `restorePurchases()` llama a
`premiumManager.deactivatePremium()` → `adManager.setPremium(false)` →
`loadInterstitial()`/`loadRewarded()` — mismo `IllegalStateException:
#008 Must be called on the main UI thread` de §11, esta vez originado en
`BillingManager.kt`, no en `MainActivity.kt`. Stack trace real:

```
java.lang.IllegalStateException: #008 Must be called on the main UI thread.
	at com.google.android.gms.ads.interstitial.InterstitialAd.load
	at com.docsmart.core.ads.AdManager.loadInterstitial(AdManager.kt:56)
	at com.docsmart.core.ads.AdManager.setPremium(AdManager.kt:170)
	at com.docsmart.core.premium.PremiumManager.deactivatePremium(PremiumManager.kt:40)
	at com.docsmart.core.billing.BillingManager.restorePurchases(BillingManager.kt:210)
```

**Lección de §11 que no alcanzó:** aquella vez se corrigió el único punto
de llamada conocido (quitar `Dispatchers.IO` de un `lifecycleScope.launch`
puntual) y se documentó explícitamente no haber encontrado otros
puntos — pero `AdManager` es un `Singleton` al que se llega desde
contextos muy distintos (UI en Main, `BillingManager` en IO), y arreglar
cada llamador uno por uno deja la puerta abierta a que aparezca un tercer
punto igual. Esta vez el fix es en `AdManager` mismo, no en el llamador.

**Corregido:** `AdManager.loadInterstitial()`/`loadRewarded()` ahora
despachan explícitamente al hilo principal (`Handler(Looper.getMainLooper())`,
con verificación `Looper.myLooper() == Looper.getMainLooper()` para no
hacer un post innecesario cuando ya se llama desde Main) antes de invocar
`InterstitialAd.load()`/`RewardedAd.load()` — sin importar desde qué
dispatcher los llame quien sea. Esto vuelve a `AdManager` seguro para
llamar desde cualquier hilo, cerrando la clase completa de bug en vez de
un solo síntoma.

**Verificado en dispositivo real:** app reinstalada en frío → Ajustes →
tocar la tarjeta "DocuSmart Premium" (primera conexión a Play Billing del
proceso, el escenario que antes crasheaba siempre) → sin crash, pantalla
Premium cargó normalmente → logcat confirmó `BillingManager: conexión
lista=true` → `AdManager: Interstitial cargado`/`Rewarded cargado` sin
ningún `FATAL EXCEPTION`.

Sin tests nuevos: el bug depende de una carrera entre dispatchers reales
(`Dispatchers.IO` vs. el `Looper` principal de Android), no reproducible
de forma determinística en un test unitario JVM sin Robolectric — mismo
límite ya documentado en §11 para esta clase de bug.

Verificado también: `testDebugUnitTest`/`detekt`/`lintDebug` en verde.
