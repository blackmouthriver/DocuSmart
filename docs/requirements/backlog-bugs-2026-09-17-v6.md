# Auditoría general de punta a punta — 2026-09-17 (octava ronda del día)

**Estado: ✅ Corregido y fusionado.** Pedida por el usuario ("dale, seguí
con una nueva ronda") tras fusionar el commit `ad2b5ee` (séptima ronda).
Cubre áreas sin revisión dedicada hoy: banners de anuncios (cierre de 2
pendientes deliberadamente diferidos en la quinta ronda), pantalla de
Inicio, calidad/consistencia de traducciones en los 12 idiomas, y un
barrido transversal (Analytics/Crashlytics, notificaciones, ciclo de
vida, Premium/Billing).

Metodología: 4 agentes en paralelo, uno por área, contrastando contra los
7 backlogs previos del día para no repetir hallazgos.

## Monetización / Analytics

### G1 — Falta el evento de analytics de compra Premium completada
**Estado: ✅ Corregido** · **Severidad: Alta** · `core/analytics/DocuSmartAnalytics.kt`,
`core/billing/BillingManager.kt:289-304`, `features/premium/presentation/PremiumViewModel.kt:175`

Existe `logPremiumPurchaseAttempt()` pero ningún evento de conversión
exitosa (`logPremiumPurchaseSuccess`/similar). Cuando `handlePurchase()`
confirma `PurchaseState.PURCHASED` y activa Premium, no se loguea nada —
ni siquiera al restaurar compras. El dashboard de Firebase solo puede
medir intentos, nunca conversión real ni distinguir plan mensual/anual —
el dato de negocio más crítico de toda la monetización queda sin
instrumentar.
**Fix sugerido:** nuevo evento (ej. `logPremiumPurchaseSuccess(productId, isRestore)`)
llamado desde `handlePurchase()` cuando `PurchaseState.PURCHASED` y
`!isRestore` (para no inflar conversiones con cada restauración
automática de ON_START).

**Hallazgo real de la revisión adversarial de esta misma ronda (Media):**
faltaba también excluir `purchase.isAcknowledged` -- `purchasesUpdatedListener`
puede reentregar la misma compra sin confirmar más de una vez
(reconexión del `BillingClient`, recreación de la Activity a mitad del
flujo de compra, escenario documentado de Play Billing), y cada
reentrega volvía a disparar el evento de conversión, duplicando la
métrica que este mismo fix buscaba proteger. Corregido agregando
`&& !purchase.isAcknowledged` a la condición -- misma guarda idempotente
que ya evita duplicar `acknowledgePurchase()` un poco más abajo.

## Seguridad / Privacidad

### G2 — Uri de la carpeta vinculada de Descargas expuesta en logs de Crashlytics
**Estado: ✅ Corregido** · **Severidad: Media** · `features/library/data/DownloadsAccessManager.kt:62,65,82`

Mismo patrón que la fuga ya corregida para Carpeta Segura (backlog
`-v2.md`, M10): una Uri de árbol SAF codifica en texto plano el nombre
real de la carpeta elegida por el usuario (`content://.../tree/primary:NombreReal`).
Las líneas 65/82 usan `Timber.e`/`Timber.w`, que sí pasan el filtro de
`CrashlyticsTree` y se suben como breadcrumb en producción. Un usuario
que nombra su carpeta con datos personales y sufre un `SecurityException`
al otorgar/revocar el permiso deja ese nombre visible en Crashlytics.
**Fix sugerido:** mismo criterio que Carpeta Segura -- loguear solo el
tipo de excepción/mensaje genérico, no la Uri completa.

## Banners de anuncios (cierre de A5/A6 de la quinta ronda, `-v3.md`)

### G3 — QR Creador/Lector con padding lateral de 20dp en vez de 16dp
**Estado: ✅ Corregido** · **Severidad: Media** · `features/scanner/presentation/QrScreen.kt:453,893`

Confirmado que sigue sin corregirse (dejado deliberadamente en la ronda
`-v3.md` por considerarlo de mayor riesgo). Revisión más detallada de
esta ronda: **no hace falta** sacar el banner de la `Column` con scroll
compartido -- alcanza con reemplazar el `.padding(20.dp)` uniforme por
`.padding(horizontal = 16.dp, vertical = 20.dp)` en ambos sitios, ya que
el espaciado vertical interno usa `spacedBy()` independiente. Cambio de
un solo valor por sitio, riesgo bajo.

### G4 — Banner del Visor sin margen lateral
**Estado: ✅ Corregido** · **Severidad: Baja-Media** · `features/viewer/presentation/ViewerScreen.kt:389-398`

Confirmado que sigue sin margen lateral (posición abajo, junto a
`ViewerBottomBar`, sigue siendo la decisión correcta -- no se toca). El
banner y la barra inferior están en la misma `Column` vertical sin
solaparse, así que agregar `Modifier.padding(horizontal = 16.dp)` al
banner no colisiona con nada. Se cierra agregando el margen en vez de
mantenerlo como excepción documentada.

### G5 — Agenda usa el `adUnitId` de Modo Estudio en vez de uno propio
**Estado: ⚠️ Evaluado, no corregido -- requiere acción del usuario en
AdMob** · **Severidad: Baja** · `features/agenda/presentation/AgendaScreen.kt:149`

Hallazgo incidental del barrido de banners: `AdConstants.BANNER_STUDY_ID`
en vez de un ID propio de Agenda. No afecta posición/margen ni
funcionamiento (los anuncios siguen mostrándose con normalidad), solo la
atribución de impresiones en la consola de AdMob (Agenda se contabiliza
como si fuera Estudio). **No corregible solo con código**: requiere que
el usuario cree un nuevo bloque de anuncios "Banner" para Agenda en la
consola de AdMob y comparta el ID real (`ca-app-pub-.../...`) -- no se
puede inventar un ID nuevo desde acá sin que apunte a un bloque real.

## Pantalla de Inicio

### G6 — Doble navegación por toque rápido en accesos rápidos y Recientes
**Estado: ✅ Corregido (parcial, ver nota)** · **Severidad: Media** · `core/navegation/DocuSmartNavGraph.kt:524-547`

Ninguna de las lambdas de navegación desde `HomeScreen` usa
`launchSingleTop = true` ni hay guard de re-entrada, ni debounce en
`QuickAccessGrid.kt`/`DocuSmartDocumentItem`. Doble-tap rápido apila el
mismo destino dos veces; "Atrás" reaparece en una segunda instancia en
vez de volver a Home. Ya corregido puntualmente para "Convertir"/"Generar
QR" en rondas anteriores, nunca aplicado a la capa de navegación de Home.

**Hallazgo real de la revisión adversarial de esta misma ronda (Alta):**
el primer intento agregó `launchSingleTop=true` a TODAS las lambdas de
Home, incluidas las 5 funciones compartidas `navigateToConvert`/
`navigateToQrCreator`/`navigateToOcr`/`navigateToSign`/
`navigateToSecureFolder` (usadas también desde Biblioteca/Visor/lista de
sesión del Escáner) y `onStudy`/`onDocumentClick` de Home -- todas
reciben un argumento que VARÍA por llamada (`document`/`tab`/
`documentId`). Con Navigation-Compose 2.8.4, cuando `launchSingleTop`
encuentra el mismo destino ya en el tope del back stack, reutiliza la
entrada existente con sus argumentos ORIGINALES, no los nuevos --
escenario real: Convertir sobre el documento A, volver, Convertir sobre
el documento B sin que el Convertidor salga del tope, mostraría el
documento A de nuevo. **Corregido revirtiendo `launchSingleTop` en los 7
sitios con argumentos variables**, dejándolo solo en los destinos de
Home con argumentos fijos (`onScan`, `onConvert`, `onQuickConvertImageToPdf`,
`onSecurity`, `onSeeAll`, `onQrReader`, `onQrCreator`, `onTrash`) -- el
doble-toque de la MISMA tarjeta/documento sigue protegido para esos 8
casos; el doble-toque en Convertir/Crear QR/OCR/Firmar/Carpeta Segura
sobre un documento específico queda sin ese guard (riesgo residual
Bajo: como mucho una entrada extra en el back stack, no contenido
incorrecto -- se prefirió esto a arriesgar mostrar el documento
equivocado).

### G7 — `contentDescription` duplicado en los 9 accesos rápidos de Home
**Estado: ✅ Corregido** · **Severidad: Media** · `core/ui/components/cards/DocuSmartCards.kt:85`

`DocuSmartQuickAccessCard` pone `contentDescription = label` en el
`Icon` mientras el mismo texto ya es visible en el `Text` de abajo,
dentro del mismo `Box.clickable` (que fusiona la semántica de sus hijos
en un solo nodo). TalkBack anuncia el nombre dos veces por cada uno de
los 9 atajos. `DocuSmartToolCard` en el mismo archivo ya usa
`contentDescription = null` correctamente para el mismo patrón.

### G8 — Parpadeo del estado vacío en Recientes por `isLoading` sin usar
**Estado: ✅ Corregido** · **Severidad: Media** · `features/home/presentation/HomeViewModel.kt:24,47,56,62`,
`HomeScreen.kt`, `RecentDocuments.kt`

`isLoading` se calcula y actualiza pero nunca se lee -- la sección
decide solo por `documents.isEmpty()`. Si `loadRecentlyOpened()` tarda
(almacenamiento externo lento), el usuario ve un instante el estado
vacío ("Sin documentos recientes") aunque sí tenga documentos, que
luego aparecen de golpe. Sin skeleton/spinner.

### G9 — Error de carga de Recientes indistinguible de "sin documentos"
**Estado: ✅ Corregido** · **Severidad: Baja** · `HomeViewModel.kt:60-63`

El `catch` de `loadRecentDocuments()` solo loguea y apaga `isLoading`,
sin exponer ningún estado de error -- a diferencia de `removeDocument()`
que sí usa `deleteError` vía Toast. Un fallo real de lectura se ve
exactamente igual que "no tienes documentos recientes".

### G10 — Carga redundante de Recientes al entrar a Home
**Estado: ✅ Corregido** · **Severidad: Baja** · `HomeViewModel.kt:43` (`init`), `HomeScreen.kt:56-58`
(`LaunchedEffect(Unit)`)

Ambos disparan `loadRecentDocuments()` en la composición inicial,
duplicando la consulta a `loadRecentlyOpened()` sin necesidad.

**Hallazgo real de la revisión adversarial de esta misma ronda (Media):**
el primer intento movió la carga inicial al `init{}` del ViewModel y
quitó el `LaunchedEffect(Unit)` de `HomeScreen.kt` -- pero el ViewModel
SOBREVIVE un cambio de configuración (rotación), a diferencia del
`NavBackStackEntry`/Lifecycle que usa `ReloadOnScreenResume` (que sí se
recrea, y deliberadamente descarta su primer `ON_RESUME` asumiendo que
ya existe una carga inicial propia del llamador). Sin el
`LaunchedEffect(Unit)`, "Recientes" podía quedar desactualizado en
silencio tras rotar el dispositivo, sin ningún disparador que lo
corrigiera hasta la próxima navegación real de ida y vuelta. Corregido
al revés: la carga inicial vuelve al `LaunchedEffect(Unit)` de
`HomeScreen.kt` (si se re-ejecuta en cada composición nueva, incluida la
que sigue a una rotación) y se quita el `init{}` del ViewModel -- mismo
resultado de fondo (una sola carga por entrada real a Home) sin el hueco
de refresco.

## i18n — calidad de traducciones (12 idiomas)

**Confirmado en buen estado** (sin hallazgos): paridad de claves (1057
en los 12 idiomas, sin faltantes/sobrantes), placeholders de formato
(140 claves con `%s`/`%d` revisadas, mismo tipo/cantidad en los 12
idiomas), strings vacíos, traducciones "quedadas en español".

### G11 — Comillas ASCII sin escapar que Android descarta al compilar
**Estado: ✅ Corregido** · **Severidad: Baja** · `strings.xml` (`agenda_delete_confirm_body` en
en/it/ko/pt/zh; `security_delete_confirm_body` en el base español +
ca/de/en/eu/fr/it/ko/pt/zh)

Una comilla doble `"` suelta dentro de un string (no envolviendo todo el
valor) se usa solo como marcador de control de espacios y Android la
descarta al compilar -- el texto se muestra SIN esas comillas visibles.
`agenda_delete_confirm_body` usa comillas tipográficas correctas en
ca/de/eu/fr/ja/ru pero ASCII rectas en en/it/ko/pt/zh (inconsistente).
`security_delete_confirm_body` tiene el mismo problema ya en el string
base español, replicado en 9 idiomas más (solo ja/ru usan comillas que
sobreviven).
**Fix sugerido:** reemplazar `"%1$s"` por comillas tipográficas propias
de cada idioma (mismo criterio que ya usan ca/de/eu/fr/ja/ru).

## Confirmado correcto (no son bugs)

- Notificaciones: solo Agenda/repaso de Notas (ya revisados) y
  `PomodoroTimerService` (foreground service, canal/PendingIntent/manifest
  correctos) -- sin notificaciones de actualización/promoción de Premium.
- `DocuSmartApplication.onCreate()`: orden de inicialización correcto,
  sin dependencias circulares nuevas entre `billingManager`/`premiumManager`.
- Cambio de cuenta de Google sin reiniciar proceso: ventana residual ya
  conocida y aceptada como trade-off del fix M1 de la séptima ronda, no
  es un bug nuevo.
- Menú contextual de documentos en Recientes de Home: paridad completa
  con Biblioteca.
- Banners: Carpeta Segura/Papelera sin banner (correcto, decisión ya
  documentada), Seguridad (menú 2 tarjetas) con banner correcto.

## Resumen por severidad

- **Alta (1):** G1 (falta evento de conversión Premium).
- **Media (6):** G2 (Uri de Descargas en Crashlytics), G3 (QR 20dp),
  G6 (doble navegación Home), G7 (contentDescription duplicado Home),
  G8 (parpadeo estado vacío Home).
- **Baja/Baja-Media (4):** G4 (banner Visor), G5 (adUnitId Agenda), G9
  (error indistinguible), G10 (carga redundante), G11 (comillas i18n).

## Plan de trabajo

Corregir por severidad (Alta → Media → Baja), gauntlet completo +
revisión adversarial antes de pedir aprobación de fusión, mismo criterio
que las rondas anteriores.
