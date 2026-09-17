# Auditoría general de punta a punta — 2026-09-17 (sexta ronda del día)

**Estado: catalogado, en ejecución.** Pedida por el usuario ("nuevamente
realiza una nueva ronda de revision") tras cerrar y fusionar la quinta
ronda (commit `5510d93`). Cubre áreas que no habían tenido una revisión
profunda dedicada en las rondas de hoy: Biblioteca/Home/Recientes/
Favoritos, Convertidor (como pantalla/flujo completo), Seguridad/Carpeta
Segura/PIN/Contraseña PDF, y Agenda/Papelera.

Metodología: 4 agentes en paralelo, uno por área, cada uno contrastando
contra `backlog-bugs-2026-09-16.md`, `-v2.md`, `-2026-09-17.md` y
`-v3.md` para no repetir hallazgos ya cerrados.

## Biblioteca / Home / Recientes / Favoritos

### B1 — "Recientes" no migra cuando un documento cambia de id
**Estado: ✅ Corregido** · **Severidad: Media** · `DocumentIdentityMaintenance.kt:37-44`,
`DocumentHistoryDao.kt:9-37`

`onIdChanged()` migra favoritos/alias/anotaciones/marcadores/última
página/notas/eventos de Agenda cuando el id de un documento cambia
(renombrar, mover a/desde Carpeta Segura), pero `DocumentHistoryDao` ni
siquiera tiene un `updateDocumentId()` -- la fila de `document_history`
queda apuntando al id viejo (huérfana), y el documento pierde su
posición real en "Recientes" (cae al fallback por fecha de archivo).
**Fix sugerido:** agregar `updateDocumentId()` a `DocumentHistoryDao` e
invocarlo desde `onIdChanged()` junto con las otras 6 tablas.

### B2 — El menú "⋮" de Favoritos no ofrece Convertir/QR/OCR/Firmar/Mover a Carpeta Segura
**Estado: ✅ Corregido** · **Severidad: Baja-Media** · `FavoritesSection.kt:30-63`

A diferencia de `DocumentListSection`/`RecentDocuments`, `FavoritesSection`
no recibe ni pasa esos 5 callbacks al `DocumentContextMenu` interno --
inconsistencia de paridad de funciones para el mismo documento.
**Fix sugerido:** agregar los mismos 5 parámetros opcionales y cablearlos
en `LibraryScreen.kt`.

## Convertidor

### C1 — "Convertir" desde Biblioteca/Visor para un `.txt`/`.zip` descarta el archivo en silencio
**Estado: ✅ Corregido** · **Severidad: Baja-Media** · `DocuSmartNavGraph.kt:333-356`,
`ConverterScreen.kt:95-99`

No existe ningún `ConversionType` de origen Texto/ZIP; el menú "⋮ →
Convertir" aparece igual para esos tipos, pero `initialFileCategory`
queda `null` y `preloadFile()` nunca se llama -- el usuario pierde el
archivo elegido sin ningún aviso.
**Fix sugerido:** ocultar "Convertir" del menú "⋮" para
`DocumentType.TEXT`/`ZIP` en `DocuSmartDocumentItem.kt`.

### C2 — `saveAllToDownloads()` sin ninguna protección de excepciones (ni siquiera genérica)
**Estado: ✅ Corregido** · **Severidad: Media** · `ConverterViewModel.kt:380-403` vs `:441-479`

A diferencia de `saveToDownloads()` (que sí tiene `try/catch(Exception)`),
el guardado por lote no atrapa nada -- un `OutOfMemoryError` copiando un
archivo grande a MediaStore escapa sin atrapar y crashea la app.
**Fix sugerido:** agregar `catch(OutOfMemoryError)`+`catch(Exception)` a
`saveAllToDownloads()`, y el `catch(OutOfMemoryError)` que también le
falta a `saveToDownloads()`.

### C3 — Cancelar una conversión por lotes a mitad de camino deja archivos huérfanos y consume cupo diario sin dejar rastro
**Estado: ✅ Corregido** · **Severidad: Media-Alta** · `ConverterViewModel.kt:318-356`, `:216-235`

`runBatchConversion()` arma TODO el resultado antes de commitear a
`_uiState`; si la corrutina se cancela a mitad del lote (navegar hacia
atrás), los archivos ya convertidos exitosamente quedan en disco sin
ninguna referencia en la UI, pero ya consumieron cupo diario real.
**Fix sugerido:** acumular `batchResults` incrementalmente en
`_uiState` dentro del propio bucle, no recién al final.

## Seguridad / Carpeta Segura / PIN

### S1 — Sin `FLAG_SECURE`: Carpeta Segura queda visible en capturas y en "Apps recientes" sin PIN
**Estado: ✅ Corregido** · **Severidad: Alta** · Ninguna pantalla de la app usa `FLAG_SECURE`

Al mandar la app a segundo plano (Home, multitarea), Android toma una
miniatura del último frame ANTES de que el auto-bloqueo interno pueda
actuar -- esa miniatura (contenido real de un documento protegido, o el
listado de "Archivos protegidos") queda visible en "Apps recientes" sin
pedir PIN. También capturable con captura/grabación de pantalla.
Contradice directamente la promesa de privacidad de Carpeta Segura.
**Fix aplicado:** nuevo `SecureScreenEffect(enabled)` compartido en
`LifecycleEffects.kt`, activo en `SecurityScreen` (mientras `UNLOCKED`) y
en `ViewerScreen` (mientras `isReadOnlyPreview`).

### S2 — El bloqueo por intentos fallidos de PIN se evade adelantando la hora del sistema
**Estado: ✅ Corregido** · **Severidad: Media** · `SecurityManager.kt:88-92`, `:129-139`

El backoff usa `System.currentTimeMillis()` (reloj de pared, ajustable
por el usuario) en vez de `SystemClock.elapsedRealtime()` -- adelantar la
fecha del dispositivo anula el bloqueo por completo, permitiendo probar
los 4 dígitos sin límite de tiempo real.
**Fix aplicado:** el cálculo ahora toma el máximo entre el reloj de pared
y `SystemClock.elapsedRealtime()` (con fallback a `currentTimeMillis()`
en tests JVM sin Robolectric, donde `SystemClock` no está disponible).

### S3 — `secure_preview/` puede persistir indefinidamente si la app muere a mitad de una vista previa
**Estado: ✅ Corregido** · **Severidad: Media** · `SecurityManager.kt:322-338`

`clearPreviewCache()` solo se llama al crear la siguiente copia o en
`ON_STOP` del lifecycle -- un crash/OOM-kill antes de eso deja la copia
sin cifrar en disco sin ninguna forma de purgarla desde la UI.
**Fix aplicado:** barrido defensivo en `DocuSmartApplication.onCreate()`
+ `SettingsViewModel.clearSecurePreviewCache()` incluido en "Limpiar
caché" de Ajustes (borrado directo, nunca vía Papelera -- es una copia
efímera sin cifrar, no un documento real).

### S4 — Defensa en profundidad: Convertir/QR/OCR/Firmar/Mover no tienen guard a nivel de ViewModel (no explotable hoy)
**Estado: ⚠️ Evaluado, no corregido** · **Severidad: Baja** ·
`ViewerTopBar.kt:210-249`, `ViewerScreen.kt:505-510`

Solo están protegidos por estar ocultos en el menú -- a diferencia de
Favorito/Compartir/Renombrar/Eliminar, que además verifican
`isReadOnlyPreview` dentro del propio ViewModel. No corregido: son
lambdas de navegación puras hacia el NavGraph (Convertidor/Creador de
QR/Herramientas PDF), sin ningún método de ViewModel propio donde
agregar hoy el mismo guard -- haría falta tocar el destino de cada
pantalla, un cambio de mayor alcance para un riesgo no explotable
actualmente (el único punto de entrada real ya está cubierto).

## Agenda / Papelera

### A1 — Revocar el permiso de alarma exacta DESPUÉS de crear recordatorios los deja mudos para siempre
**Estado: ✅ Corregido** · **Severidad: Alta** · `ReminderScheduler.kt`, `AgendaScreen.kt:114-115`,
`BootRescheduleReceiver.kt`

Android cancela automáticamente TODAS las alarmas exactas ya programadas
al revocar el permiso -- la app solo actualiza el banner de aviso, nunca
reprograma los recordatorios ya afectados (la única rutina que lo hace,
`BootRescheduleReceiver`, solo corre tras reiniciar el dispositivo).
**Fix aplicado:** nuevo `AgendaRepository.rescheduleAllReminders()`,
invocado desde `AgendaScreen.kt` al detectar la transición
concedido→revocado en `ReloadOnScreenResume`.

### A2 — "Eliminar" evento de Agenda sin diálogo de confirmación
**Estado: ✅ Corregido** · **Severidad: Media-Alta** · `AgendaEventEditorDialog.kt:196-206`

Borrado directo y permanente (no hay papelera para eventos) con un solo
toque, sin `AlertDialog` -- a diferencia de Carpeta Segura/Papelera de
documentos.
**Fix sugerido:** reutilizar el mismo patrón de confirmación.

### A3 — "Guardar" evento sin guard de doble-toque -- crea eventos duplicados
**Estado: ✅ Corregido** · **Severidad: Media** · `AgendaViewModel.kt:170-199`

Un doble-toque rápido en "Guardar" al crear un evento nuevo dispara
`createEvent()` dos veces (dos eventos idénticos, cada uno con su propia
alarma).
**Fix sugerido:** guard síncrono antes de lanzar la corrutina, mismo
criterio ya aplicado en otras pantallas.

### A4 — El resaltado de "hoy" en el calendario queda obsoleto si la pantalla sigue abierta pasada la medianoche
**Estado: ✅ Corregido** · **Severidad: Baja-Media** · `AgendaCalendarView.kt:185`

`val today = remember { LocalDate.now() }` nunca se recalculaba.
**Fix aplicado:** se reprograma solo hasta la medianoche siguiente
(`LaunchedEffect` autoreprogramable), sin sondear cada minuto.

### A5 — Entradas huérfanas en Papelera si se desvincula la carpeta SAF mientras un documento está ahí
**Estado: ⚠️ Evaluado, no corregido** · **Severidad: Baja** ·
`TrashRepository.kt:193-215`, `DownloadsAccessManager.kt:75-86`

`purgeExpiredTrash()` nunca limpia la fila si `deleteDocument()` falla
por permiso ya liberado (vs. necesita permiso) -- basura de estado sin
efecto práctico, no pérdida de datos. No corregido en esta pasada:
requiere distinguir de forma confiable "permiso liberado" de otros
motivos reales de fallo (archivo bloqueado, error transitorio de
almacenamiento) antes de decidir borrar la fila -- un fix apurado acá
arriesga borrar entradas de Papelera recuperables por un fallo
transitorio real. Impacto (filas muertas invisibles, sin pérdida de
datos) no amerita ese riesgo sin más tiempo de análisis.

## Resumen por severidad

- **Alta (2/2 corregidas):** S1 (FLAG_SECURE), A1 (permiso de alarma revocado).
- **Media-Alta (2/2 corregidas):** C3 (batch cancelado), A2 (eliminar evento sin confirmar).
- **Media (5/5 corregidas):** B1, C2, S2, S3, A3.
- **Baja/Baja-Media (3/5 corregidas):** B2, C1, A4 corregidas; S4 y A5
  evaluadas y no corregidas (impacto mínimo hoy, requieren un cambio de
  mayor alcance o más análisis para no arriesgar una regresión).

## Estado final

**11 de 13 hallazgos corregidos** (las 2 Altas, las 2 Media-Alta, las 5
Media, y 2 de las 3 Baja/Baja-Media -- S4 y A5 quedan documentadas como
evaluadas). Gauntlet completo (`compileDebugKotlin`+`detekt`+`lintDebug`+
`testDebugUnitTest`+`compileDebugAndroidTestKotlin`) en verde, incluidos
los tests actualizados (`DocumentIdentityMaintenanceTest`,
`TrashRepositoryTest`, `DocumentRepositoryTest` con el nuevo método de la
interfaz `DocumentHistoryDao`, `SettingsScreenTest` instrumentado con el
nuevo parámetro `securityManager`).

## Revisión adversarial (seguridad + correctitud) sobre este mismo lote

Dos agentes en paralelo revisaron el diff completo. Encontraron **2
hallazgos reales**, ambos corregidos en el mismo lote:

- **[Seguridad, Media] El fix de S2 (backoff de PIN) seguía siendo
  evadible con un ataque de 2 pasos**: adelantar el reloj del sistema
  (elapsedRealtime todavía protegía) y LUEGO reiniciar el dispositivo,
  lo que reinicia elapsedRealtime a un valor chico y descarta esa
  protección, cayendo de nuevo al reloj de pared ya manipulado (que
  sigue así tras el reinicio, el RTC no se autocorrige). **Corregido**:
  se reemplazó el `maxOf(remainingWall, remainingElapsed)` por un "reloj
  de confianza" anclado (`trustedNowMillis()`) que reconstruye el tiempo
  real transcurrido usando solo `elapsedRealtime()` desde el último
  punto de confianza persistido, tomando el MÍNIMO contra el reloj de
  pared actual -- adelantar el reloj de pared ya no puede hacer avanzar
  el tiempo de confianza más rápido que el tiempo real. Si se detecta un
  reinicio, el ancla se congela en su último valor de confianza en vez
  de saltar al reloj ya manipulado. Costo aceptado: un reinicio real a
  mitad de un bloqueo legítimo puede hacer esperar el bloqueo completo
  de nuevo (acotado por `PIN_MAX_LOCKOUT_MS`, unos minutos) -- costo de
  UX menor aceptable para cerrar un bypass real.
- **[Correctitud, Baja-Media] El fix de A4 (calendario) podía congelarse
  para siempre**: `LaunchedEffect(today)` solo se relanza cuando la
  CLAVE cambia de valor -- si el usuario atrasaba el reloj del sistema
  mientras la pantalla estaba abierta, `LocalDate.now()` al despertar
  podía devolver la MISMA fecha ya vigente, sin producir un cambio
  observable, deteniendo el mecanismo de auto-actualización por
  completo. **Corregido**: reemplazado por un único `LaunchedEffect(Unit)`
  con un `while(true)` interno, que no depende de que el valor cambie
  para volver a programarse.

Gauntlet completo re-ejecutado tras estos 2 fixes adicionales: verde.

## Verificación en dispositivo (emulador)

El Motorola Edge 30 Neo (`ZY22G7SB77`) se desconectó del USB en algún
punto de esta ronda y no volvió a aparecer en `adb devices` tras
reintentar y reiniciar el servidor adb -- se verificó en su lugar con el
emulador `DocuSmart_Test` (arrancado en frío, sin snapshot):

- **S1 (`FLAG_SECURE`) confirmado con evidencia concreta**: se configuró
  un PIN real, se desbloqueó Carpeta Segura, y una captura de pantalla
  tomada en ese estado (`adb exec-out screencap`) salió **completamente
  en negro**. Una captura de control tomada en la pantalla "Seguridad"
  (no sensible) se ve normal -- confirma que el bloqueo es selectivo,
  no un problema global de captura.
- **S2 (bloqueo de PIN con reloj de confianza) confirmado inspeccionando
  el estado real** en
  `/data/data/com.docsmart/shared_prefs/docusmart_security.xml`: 5
  fallos reales dispararon un bloqueo de 30s (`pin_lockout_until` =
  `pin_trust_anchor_wall` + 30000, coincide con `PIN_BASE_LOCKOUT_MS`);
  tras expirar ese bloqueo, un 6to fallo lo extendió correctamente a 60s
  (backoff exponencial, `extraFailures=1`); mientras el bloqueo seguía
  activo, un intento adicional de 4 dígitos **no incrementó
  `pin_fail_count`** -- confirma que el guard bloquea la verificación
  real, no solo el mensaje en pantalla.
- Q1/A5/A2/A3/etc. (el resto de los 13 hallazgos) no se re-verificaron
  en vivo por tiempo -- cubiertos por gauntlet completo + revisión
  adversarial de dos agentes sobre el diff completo, que ya encontraron
  y corrigieron 2 regresiones reales introducidas por los propios fixes
  de esta ronda (ver arriba).

Pendiente: aprobación explícita del usuario para fusionar.
