# Auditoría general de punta a punta — 2026-09-17 (séptima ronda del día)

**Estado: ✅ Corregido, pendiente de revisión adversarial y aprobación de
fusión.** Pedida por el usuario ("dale, seguí con una nueva ronda") tras
cerrar y fusionar la sexta ronda (commit `8f54236`). Cubre áreas sin
revisión profunda hoy: resto de Ajustes (Idioma/Notificaciones/
Almacenamiento/Ayuda/Restablecer), Monetización/Premium, Onboarding, y
Accesibilidad (TalkBack) transversal a toda la app.

Metodología: 4 agentes en paralelo, uno por área, contrastando contra los
5 backlogs previos del día para no repetir hallazgos.

## Monetización / Premium

### M1 — Una suscripción cancelada/reembolsada puede seguir otorgando Premium indefinidamente
**Estado: ✅ Corregido** · **Severidad: Alta** · `core/billing/BillingManager.kt:123-141`,
`PremiumViewModel.kt:42-46`, `DocuSmartApplication.kt:16-56`

`restorePurchases()` solo corre una vez, en la conexión inicial del
`BillingClient` -- pero `BillingManager` (`@Singleton` de Hilt) solo se
construye la PRIMERA VEZ que algo lo inyecta, y el único punto de
inyección en toda la app es `PremiumViewModel` (la pantalla Premium).
`DocuSmartApplication.onCreate()` inyecta `PremiumManager` (que solo lee
el flag cacheado en SharedPreferences) pero nunca `BillingManager`. Un
usuario que cancela/pide reembolso y no vuelve a abrir esa pantalla
sigue viéndose a sí mismo como Premium indefinidamente, a través de
reinicios de proceso.
**Fix sugerido:** relanzar `restorePurchases()` en cada `ON_START` real
de la app (vía `AppLifecycleTracker`, ya existe en el proyecto), o
inyectar `BillingManager` en `DocuSmartApplication.onCreate()` para
garantizar al menos una revalidación por arranque en frío.
**Fix aplicado:** ambas cosas -- `BillingManager` ahora se inyecta
eager en `DocuSmartApplication.onCreate()` (garantiza la conexión y la
primera `restorePurchases()` en cada arranque en frío) y además se
suscribe a `AppLifecycleTracker` para revalidar en cada `ON_START` real,
con un throttle de 4h (`REVALIDATE_THROTTLE_MS`) para no golpear la API
de Billing en cada cambio de foco.

**Hallazgo real de la revisión adversarial de esta misma ronda (Media):**
el primer intento fijaba el timestamp del throttle de forma incondicional
en `init{}`, antes de saber si `startConnection()` iba a lograr conectar
-- si la primera conexión fallaba (ej. sin red al abrir la app por
primera vez tras instalar), el throttle quedaba "gastado" sin haber
revalidado nada de verdad, bloqueando el mecanismo de respaldo de
`ON_START` hasta por 4h. Corregido: el timestamp ahora es nullable y
solo se fija una vez que `onBillingSetupFinished` confirma una conexión
real (`ready=true`); mientras siga en `null`, cada `ON_START` reintenta
sin esperar el throttle.

## Accesibilidad (TalkBack)

### A1 — Botón mostrar/ocultar contraseña sin `contentDescription` en 3 pantallas
**Estado: ✅ Corregido** · **Severidad: Alta** · `ViewerScreen.kt:2050-2056` (PDF protegido desde el
Visor), `QrScreen.kt:244-249` (desbloquear QR protegido),
`QrScreen.kt:1254-1259` (proteger QR generado)

El backlog 2026-09-17 (B2) corrigió este mismo botón en
`SecurityScreen.kt`/`PdfPasswordScreen.kt` con
`R.string.password_show`/`password_hide`, pero no se replicó acá.
TalkBack solo anuncia "Botón" sin decir qué hace ni si el texto está
visible.
**Fix sugerido:** reutilizar los mismos strings ya existentes en las 3
ubicaciones.

### A2 — Botón "cerrar búsqueda" del Visor sin `contentDescription`
**Estado: ✅ Corregido** · **Severidad: Alta** · `ViewerScreen.kt:626-631`

Botón ícono-solo dentro de `SearchBar` del Visor (pantalla de uso más
frecuente); TalkBack lo anuncia como "Botón" sin indicar que cierra la
búsqueda.
**Fix sugerido:** nuevo string `viewer_search_close`.

### A3 — `DocuSmartSearchBar` con `contentDescription` hardcodeado en español
**Estado: ✅ Corregido** · **Severidad: Media** · `core/ui/components/DocuSmartSearchBar.kt:38-41,48-51`

`contentDescription = "Buscar"`/`"Limpiar"` sin `stringResource` --
mismo tipo de bug ya corregido en 2026-09-16-v2 #15, no aplicado acá.
Usado en la barra de búsqueda de Biblioteca.

### A4 — Pestañas "Dispositivo"/"Mis archivos" de Biblioteca sin rol/estado de selección
**Estado: ✅ Corregido** · **Severidad: Media** · `LibraryScreen.kt:392-409` (`LibraryTabItem`)

`Box` con `.clickable{}` puro, sin `role = Role.Tab`/`selected=`, a
diferencia de la barra de navegación inferior que sí usa
`Modifier.selectable(...)` para el mismo patrón. TalkBack no anuncia
cuál pestaña está activa.

### A5 — Sliders de brillo/contraste del Escáner sin etiqueta accesible
**Estado: ✅ Corregido** · **Severidad: Media** · `ScanResultScreen.kt:1793-1809`

Cada `Slider` tiene un `Text` visual con el valor, pero el propio
`Slider` no tiene `contentDescription`/`stateDescription` -- TalkBack
anuncia solo el porcentaje, sin decir "Brillo" o "Contraste".

## Ajustes (resto)

### S1 — Diálogo de Almacenamiento con división entera, sin rama de Bytes/MB
**Estado: ✅ Corregido** · **Severidad: Media** · `SettingsScreen.kt:220-224,233-261,267-274,1000-1013`

Mismo bug que M2/B14 ya corrigieron en `TrashScreen.kt`/
`DocumentRepository.kt`/`SecurityScreen.kt`/`ScanSessionManager.kt`,
nunca aplicado a este diálogo -- siempre muestra "%d KB" con división
entera (archivos chicos redondean a "0 KB", tamaños grandes se ven como
"46080 KB" en vez de "45,0 MB").

### S2 — "Acerca de": versión hardcodeada "v1.0.0" en los 12 idiomas
**Estado: ✅ Corregido** · **Severidad: Baja-Media** · `strings.xml` (12 idiomas) claves
`settings_about_subtitle`/`settings_about_subtitle_full`

Mismo problema que B16 ya corrigió para el email de soporte
(`BuildConfig.VERSION_NAME`), nunca extendido a estas 2 apariciones.
Coincide con la versión real hoy (1.0.0) pero quedará desactualizado en
silencio en el próximo bump.

### S3 — "Restablecer configuración": condición de carrera entre el reinicio de Activity por idioma y el traspaso a Papelera
**Estado: ✅ Corregido** · **Severidad: Media** · `SettingsScreen.kt:433-452`,
`MainActivity.kt:136-148`, `SettingsViewModel.kt:40-44`

El reset cambia el idioma ANTES de esperar a que termine
`moveConvertedFilesToTrash()` (suspend, un archivo a la vez) -- si el
idioma del dispositivo difiere del activo, `MainActivity` reinicia la
Activity casi de inmediato, cancelando `viewModelScope` a mitad del
traspaso. No reproducido en vivo, mecanismo real verificado por código.

**Hallazgo real de la revisión adversarial de esta misma ronda (Media):**
el fix inicial sí eliminó la condición de carrera original, pero
`showResetDialog = false` seguía ejecutándose de forma síncrona sin
esperar a que la corrutina terminara -- un fallo real de E/S al mover a
Papelera quedaba tragado en silencio, el usuario veía el diálogo
cerrarse como si todo hubiera salido bien. Corregido con
`try/catch` + `Toast` (`settings_reset_error`, mismo patrón ya usado en
esta pantalla para el error de carpeta vinculada) para al menos avisar
del fallo.

### S4 — Diálogos de Ajustes (Ayuda/Acerca de/Almacenamiento) sin scroll
**Estado: ✅ Corregido** · **Severidad: Media** · `SettingsScreen.kt:16` (import muerto de
`verticalScroll`), `:372-416`, `:463-516`, `:226-297`

Ningún `AlertDialog` de Ajustes tiene `.verticalScroll()` en su
contenido -- con "Muy grande" + un idioma verboso (alemán/ruso), el
diálogo de Ayuda (4 preguntas) puede exceder la altura disponible sin
forma de desplazarse. El import sin usar sugiere un fix que se empezó y
no se terminó. No verificado en vivo.

## Onboarding

### O1 — Reingresar al tutorial desde Ajustes deja el back stack sucio
**Estado: ✅ Corregido** · **Severidad: Media** · `DocuSmartNavGraph.kt:627-631,453-457`,
`SettingsScreen.kt:888-897`

"Ver tutorial" desde Ajustes navega a Onboarding sin limpiar bien el
back stack (`[Home, Settings, Onboarding]`), y al terminar solo saca
Onboarding sin `launchSingleTop`, empujando un Home nuevo encima
(`[Home, Settings, Home]`). El usuario necesita presionar Atrás 3 veces
para salir en vez de 1; repetir "Ver tutorial" hace crecer el stack sin
límite.
**Fix sugerido:** en `onFinished`, `popUpTo(Home.route){inclusive=false}`
+ `launchSingleTop=true` en vez de apuntar a `Onboarding.route`.

### O2 — El permiso de almacenamiento se pide de golpe durante el splash, sin contexto
**Estado: ✅ Corregido** · **Severidad: Media** · `MainActivity.kt:98,311-333`

`requestStoragePermissions()` corre sincrónicamente en `onCreate()`,
antes de que el onboarding explique para qué es -- contrasta con el
patrón ya usado para `POST_NOTIFICATIONS` (pedido en contexto real). Un
usuario nuevo puede denegarlo por reflejo sin entender para qué es, sin
ningún mecanismo posterior que lo vuelva a pedir con contexto.

## Resumen por severidad

- **Alta (3):** M1 (suscripción cancelada sigue dando Premium), A1
  (contraseña sin descripción ×3), A2 (cerrar búsqueda del Visor).
- **Media (8):** DailyLimitManager evadible por fecha (ver nota abajo),
  A3, A4, A5, S1, S3, S4, O1, O2.
- **Baja-Media (1):** S2.

**Nota:** el hallazgo del límite diario evadible por fecha del sistema
(`DailyLimitManager.kt:87-123`, Media) se documenta acá pero se agrupa
junto a M1 en el plan de trabajo por tocar el mismo dominio
(Monetización). **Estado: ✅ Corregido** -- mismo patrón de ancla de
reloj confiable ya usado para el bloqueo por PIN (ver sexta ronda):
`checkAndResetIfNewDay()` ahora exige al menos `MIN_REAL_MS_BETWEEN_RESETS`
(20h) de tiempo real (`elapsedRealtimeMillisSafe()`) transcurrido desde
el último reseteo antes de aceptar un cambio de fecha del sistema como
"nuevo día".

## Plan de trabajo

Corregir por severidad (Alta → Media → Baja), gauntlet completo +
revisión adversarial antes de pedir aprobación de fusión, mismo criterio
que las rondas anteriores.
