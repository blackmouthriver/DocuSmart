# Módulo: Seguridad

> Parte de [`CONTEXT.md`](../../CONTEXT.md). Cubre: Carpeta Segura (PIN +
> biometría), Contraseña de PDF, y QR protegido con contraseña.
> Decisiones de producto confirmadas con el usuario el 2026-08-24.

**Estado:** bug crítico de borrado de original corregido, y endurecido el
2026-08-24 (RNF-SEC-01): `moveToSecure()` ignoraba el resultado real de
`File.delete()` y siempre reportaba éxito aunque el original hubiera quedado
en su ubicación — ahora propaga el resultado y la UI avisa igual que ya
hacía la vía de importación por `Uri`/SAF. QR migrado a AES-256/GCM con flujo
de lectura construido. Auto-bloqueo (RF-SEC-08), `setupPin()` sin fallo
silencioso, y restablecer PIN con borrado (RF-SEC-09) ya implementados y
verificados en dispositivo real — ver §11, §12, §13. Todos los RF de este
módulo están implementados; el backlog restante es de pruebas automatizadas
(cobertura de escenarios difíciles de forzar en dispositivo real).
**Código relacionado:** `features/security/**`, `features/scanner/presentation/QrScreen.kt`,
`features/scanner/domain/QrCrypto.kt` (nuevo).

---

## 1. Alcance

Tres capacidades independientes que comparten la misma "sección Seguridad":

1. **Carpeta Segura** — archivos movidos a un almacenamiento protegido por PIN de 4 dígitos y, opcionalmente, biometría.
2. **Contraseña de PDF** — proteger/quitar contraseña de un PDF puntual (no requiere PIN de la carpeta segura).
3. **QR protegido** — código QR cuyo contenido solo es legible con una contraseña.

---

## 2. Requerimientos funcionales

### Carpeta Segura
- **RF-SEC-01** El sistema debe permitir configurar un PIN de 4 dígitos la primera vez que se accede a la Carpeta Segura.
- **RF-SEC-02** El sistema debe permitir desbloquear la Carpeta Segura con el PIN configurado.
- **RF-SEC-03** Si el dispositivo tiene biometría disponible, el sistema debe ofrecer desbloqueo por huella/rostro como alternativa al PIN, activable/desactivable por el usuario.
- **RF-SEC-04** El sistema debe permitir proteger un archivo, ya sea seleccionándolo desde el dispositivo (SAF) o desde la biblioteca de documentos generados por la app.
- **RF-SEC-05** Al proteger un archivo, el sistema debe copiarlo al almacenamiento de la Carpeta Segura y **eliminar el archivo original** de su ubicación de origen (ver RNF-SEC-01 sobre limitaciones de permisos).
- **RF-SEC-06** El sistema debe permitir restaurar un archivo desde la Carpeta Segura hacia el almacenamiento general de la app (`converted/`).
- **RF-SEC-07** El sistema debe permitir eliminar permanentemente un archivo de la Carpeta Segura.
- **RF-SEC-08** ✅ El sistema debe bloquear automáticamente la Carpeta Segura (exigir PIN/biometría de nuevo) cuando la app pasa a segundo plano. Implementado 2026-08-26, ver §11.
- **RF-SEC-09** El sistema debe permitir restablecer el PIN desde Ajustes → Seguridad. Al restablecer, el sistema debe **eliminar todos los archivos** actualmente en la Carpeta Segura y advertir de esto antes de confirmar. **✅ Implementado 2026-08-27, ver §13.**

### Contraseña de PDF
- **RF-SEC-10** El sistema debe permitir agregar una contraseña a un archivo PDF (cifrado AES-128, ya implementado con iText7), con una longitud mínima de 4 caracteres.
- **RF-SEC-11** El sistema debe permitir quitar la contraseña de un PDF protegido, solicitando la contraseña actual.
- **RF-SEC-12** El sistema debe permitir guardar el resultado en Descargas o compartirlo directamente.

### QR protegido
- **RF-SEC-13** El sistema debe permitir crear un código QR cuyo contenido esté cifrado con una contraseña (mínimo 4 caracteres).
- **RF-SEC-14** El cifrado del contenido del QR debe ser AES (no el XOR actual — deuda técnica a corregir, ver §5).
- **RF-SEC-15** Al leer un QR protegido con la app DocuSmart, el sistema debe solicitar la contraseña antes de mostrar el contenido real.

---

## 3. Requerimientos no funcionales

- **RNF-SEC-01 (permisos de borrado):** el borrado del archivo original tras proteger (RF-SEC-05) solo es garantizable para archivos dentro del almacenamiento propio de la app o cuando el SAF `Uri` otorga permiso de escritura/borrado (`DELETE` en `DocumentsContract`). Si el sistema no puede borrar el original (permiso denegado por el proveedor de almacenamiento), el sistema debe informarlo explícitamente al usuario en vez de fallar en silencio.
- **RNF-SEC-02 (sin recuperación de PIN):** no debe existir ningún mecanismo de recuperación de PIN que no sea el restablecimiento con pérdida de datos (RF-SEC-09) — es una decisión de seguridad deliberada, no una omisión.
- **RNF-SEC-03 (auto-bloqueo):** el bloqueo automático debe activarse en `ON_STOP` del ciclo de vida, con la excepción de transiciones esperadas iniciadas por la propia app (por ejemplo, compartir un archivo abre una hoja del sistema que técnicamente pasa la app a segundo plano — no debe contarse como "salida" para efectos de bloqueo).
- **RNF-SEC-04 (cifrado):** toda contraseña de usuario debe compararse por hash, nunca en texto plano persistido. **✅ Ya cumplido para el PIN** — `SecurityManager` lo guarda como SHA-256 (`hashPin()`) en SharedPreferences, nunca en texto plano. Nota menor: un PIN de 4 dígitos solo tiene 10 000 combinaciones, así que el hash no lo hace inmune a fuerza bruta si alguien accede al almacenamiento local — la protección real depende del sandboxing de Android, no solo del hash. No aplica a la contraseña de PDF (es la clave de cifrado AES en sí, no algo que se compare por hash) ni a la de QR (mismo caso una vez migrada a AES).
- **RNF-SEC-05 (mensajes de error):** los mensajes de error no deben filtrar rutas de archivo completas ni detalles internos de excepciones al usuario final (hallazgo ya señalado en la auditoría de mayo sobre `PermissionHandler`).

---

## 4. Historias de usuario con criterios de aceptación

### HU-SEC-01 — Configurar PIN por primera vez
**Como** usuario que entra por primera vez a la Carpeta Segura,
**quiero** crear un PIN de 4 dígitos,
**para** proteger el acceso a mis archivos sensibles.

- **AC1** Dado que no tengo PIN configurado, cuando entro a Seguridad → Carpeta Segura, entonces veo la pantalla "Crea tu PIN".
- **AC2** Dado que ingreso 4 dígitos, cuando se completan, entonces se me pide confirmarlos ingresándolos de nuevo.
- **AC3** Dado que el PIN de confirmación no coincide, cuando lo ingreso, entonces veo el mensaje "Los PINs no coinciden" y se reinicia el campo de confirmación.
- **AC4** Dado que ambos PIN coinciden, cuando se confirma, entonces accedo directamente a la Carpeta Segura (ya desbloqueada) y el PIN queda guardado con hash, no en texto plano.

### HU-SEC-02 — Desbloquear con PIN
**Como** usuario con PIN ya configurado,
**quiero** ingresar mi PIN para entrar a la Carpeta Segura,
**para** acceder a mis archivos protegidos.

- **AC1** Dado que ingreso el PIN correcto, cuando se completan los 4 dígitos, entonces entro a la Carpeta Segura.
- **AC2** Dado que ingreso un PIN incorrecto, cuando se completan los 4 dígitos, entonces veo "PIN incorrecto" y el campo se limpia para reintentar.
- **AC3** Dado que la biometría está disponible y activada, cuando entro a la pantalla de desbloqueo, entonces veo también la opción "Usar biometría".

### HU-SEC-03 — Desbloquear con biometría
**Como** usuario con biometría activada,
**quiero** desbloquear con huella o rostro,
**para** no tener que escribir el PIN cada vez.

- **AC1** Dado que toco "Usar biometría", cuando el sistema reconoce mi huella/rostro, entonces entro a la Carpeta Segura.
- **AC2** Dado que la biometría falla o la cancelo, cuando esto ocurre, entonces permanezco en la pantalla de PIN sin mensaje de error alarmante (cancelar no es un error).

### HU-SEC-04 — Proteger un archivo
**Como** usuario con archivos que no quiero que otros vean,
**quiero** moverlos a la Carpeta Segura,
**para** que dejen de estar accesibles desde su ubicación original.

- **AC1** Dado que elijo un archivo desde el selector del dispositivo, cuando confirmo, entonces el archivo se copia a la Carpeta Segura **y se elimina de su ubicación original** (si el permiso lo permite).
- **AC2** Dado que el sistema no pudo eliminar el original (permiso denegado), cuando termina la operación, entonces veo un aviso claro indicando que el archivo original sigue existiendo en su ubicación.
- **AC3** Dado que elijo un archivo desde la biblioteca de la app, cuando confirmo, entonces se aplica el mismo comportamiento (copiar + eliminar original) y el archivo deja de aparecer en Biblioteca/Recientes.
- **AC4** Dado que la protección fue exitosa, cuando vuelvo a la lista, entonces veo el archivo en "Archivos protegidos" y un mensaje de confirmación.

*(Corrige el bug crítico de QA: hoy el archivo protegido sigue siendo accesible desde su ruta original.)*

### HU-SEC-05 — Auto-bloqueo al salir
**Como** usuario preocupado por mi privacidad,
**quiero** que la Carpeta Segura se bloquee si salgo de la app,
**para** que nadie más pueda verla si toma mi teléfono.

- **AC1** Dado que estoy dentro de la Carpeta Segura desbloqueada, cuando la app pasa a segundo plano (`ON_STOP`), entonces se marca como bloqueada.
- **AC2** Dado que la Carpeta Segura quedó bloqueada, cuando vuelvo a abrir la app y navego a Seguridad, entonces se me pide PIN/biometría de nuevo, sin importar que antes estuviera desbloqueada.
- **AC3** Dado que comparto un archivo desde dentro de la Carpeta Segura (se abre la hoja de compartir del sistema), cuando vuelvo de compartir, entonces la Carpeta Segura **no** se bloquea (es una transición esperada, no una salida real).

### HU-SEC-06 — Restablecer PIN con pérdida de archivos
*(Implementado 2026-08-27 — ver §13.)*

**Como** usuario que olvidó su PIN,
**quiero** poder restablecerlo desde Ajustes,
**para** volver a usar la Carpeta Segura, aceptando que pierdo los archivos actuales.

- **AC1** Dado que toco "Restablecer PIN" en Ajustes, cuando lo hago, entonces veo una advertencia explícita: "Se eliminarán todos los archivos de tu Carpeta Segura. Esta acción no se puede deshacer."
- **AC2** Dado que confirmo el restablecimiento, cuando se ejecuta, entonces todos los archivos de la Carpeta Segura se eliminan permanentemente y se me lleva al flujo de "Crear PIN" (HU-SEC-01).

### HU-SEC-07 — Proteger PDF con contraseña
*(Ya implementado hoy — HU documentada para tener criterios de aceptación formales y como base de tests de regresión.)*

**Como** usuario que quiere compartir un PDF de forma segura,
**quiero** agregarle una contraseña,
**para** que solo quien la conozca pueda abrirlo.

- **AC1** Dado que selecciono un PDF y una contraseña de al menos 4 caracteres, cuando confirmo, entonces se genera un PDF cifrado con AES-128.
- **AC1b** Dado que la contraseña tiene menos de 4 caracteres, cuando intento continuar, entonces el botón de proteger permanece deshabilitado.
- **AC2** Dado que las contraseñas ingresada y confirmada no coinciden, cuando intento continuar, entonces el botón permanece deshabilitado y veo "Las contraseñas no coinciden".
- **AC3** Dado que la protección fue exitosa, cuando termina, entonces puedo guardar en Descargas o compartir el resultado directamente.

### HU-SEC-08 — Quitar contraseña de PDF
*(Ya implementado hoy.)*

**Como** usuario con un PDF protegido,
**quiero** quitarle la contraseña,
**para** poder compartirlo sin restricción cuando ya no la necesito.

- **AC1** Dado que ingreso la contraseña correcta del PDF, cuando confirmo, entonces se genera una copia sin contraseña.
- **AC2** Dado que ingreso una contraseña incorrecta, cuando confirmo, entonces veo "Contraseña incorrecta. Verifica e intenta de nuevo." sin generar ningún archivo.

### HU-SEC-09 — Crear QR protegido con contraseña
**Como** usuario que quiere compartir información sensible por QR,
**quiero** protegerlo con contraseña,
**para** que solo quien la conozca pueda ver el contenido real.

- **AC1** Dado que activo "Proteger con contraseña" y escribo una contraseña de al menos 4 caracteres, cuando genero el QR, entonces el contenido queda cifrado con AES (no XOR).
- **AC2** Dado que intento generar con una contraseña de menos de 4 caracteres, cuando lo intento, entonces veo "La contraseña debe tener al menos 4 caracteres" y no se genera el QR.

### HU-SEC-10 — Leer QR protegido
**Como** usuario que escanea un QR protegido creado con DocuSmart,
**quiero** que se me pida la contraseña,
**para** poder ver el contenido real solo si la conozco.

- **AC1** Dado que escaneo un QR cuyo contenido está marcado como protegido, cuando se detecta, entonces se me muestra un campo para ingresar la contraseña antes de revelar el contenido.
- **AC2** Dado que ingreso la contraseña correcta, cuando confirmo, entonces veo el contenido real (URL, texto, etc.) y se comporta como un QR normal a partir de ahí.
- **AC3** Dado que ingreso la contraseña incorrecta, cuando confirmo, entonces veo un mensaje de error y puedo reintentar.

> **Confirmado (2026-08-24):** un QR protegido de DocuSmart, escaneado con *otra*
> app de cámara/QR, mostrará el texto cifrado en bruto (ilegible) — es el
> comportamiento esperado, no se agrega ningún prefijo legible fuera del cifrado.

---

## 5. Bugs de QA a corregir (trazabilidad)

| Bug (barrido de pruebas / mejoras pendientes v1.0.1) | HU que lo cubre | Estado |
|---|---|---|
| Archivo "protegido" sigue accesible desde su ruta original — **causa raíz confirmada:** `SecurityManager.moveToSecure(file)` ya copiaba y borraba el original correctamente, pero `SecurityViewModel.importLocalFile()`/`importFileToSecure()` no lo usaban. | HU-SEC-04 | ✅ Corregido — `importLocalFile()` ahora usa `moveToSecure()`; `importFileToSecure()` copia el `Uri` y llama a `DocumentsContract.deleteDocument()`, con aviso si el proveedor no permite borrar (RNF-SEC-01). Picker cambiado de `GetContent()` a `OpenDocument()` para soportar el borrado. **Endurecido 2026-08-24:** `moveToSecure()` ignoraba el resultado de `File.delete()` (podía devolver éxito con el original todavía en su ubicación) — ahora `importLocalFile()` también avisa con `fileProtectedOriginalKept` si el borrado falla, igual que la vía por `Uri`. |
| Selector de archivo a proteger no ofrece elegir desde la biblioteca de la app | HU-SEC-04 (AC3) | Ya existía (`onImportLocalFile`) |
| Banner de Seguridad con el botón "volver" mal ubicado, reduce el tamaño del banner | Fuera de alcance de este doc — ticket de UI aparte | Pendiente |
| Falta logo corporativo en el banner de Seguridad | Ticket de UI aparte (estandarización de banners, ya anotado en CONTEXT.md §5) | Pendiente |
| Cifrado QR es XOR débil, y la lectura de QR protegido no existía | HU-SEC-09, HU-SEC-10 | ✅ Corregido — `QrCrypto.kt` (AES-256/GCM + PBKDF2), y se construyó el diálogo de desbloqueo en `QrReaderScreen` que faltaba por completo. |

---

## 6. Plan de pruebas unitarias

| # | Cobertura | Estado |
|---|---|---|
| 1 | `PdfPasswordUseCaseTest` — proteger exitoso, archivo no legible, archivo vacío, quitar contraseña exitoso, contraseña incorrecta → `WrongPassword`. Usa PDFs reales generados con iText7 en memoria, no mocks del cifrado. | ✅ 5 tests, en verde |
| 2 | `SecurityManagerTest` — PIN (`setPin`/`verifyPin`/`hasPin`/`clearPin`/`resetPinAndWipeFiles`, ver §13), biometría (preferencia), `moveToSecure` (copia + borra original, y fallo limpio si el original no existe), `moveFromSecure`, `deleteSecureFile`, `getSecureFiles` (orden), `getSecureFolderSize`. `isBiometricAvailable()` queda fuera (requiere Robolectric/instrumentación por `PackageManager`). No se agregó un test de "copia OK pero borrado falla" a nivel de filesystem real: forzarlo de forma confiable difiere entre Windows (dev) y Linux (CI) — la lógica de honestidad del resultado sí queda cubierta por el test de "no existe". | ✅ 14 tests, en verde |
| 3 | `QrCryptoTest` — round-trip cifrado/descifrado, contraseña incorrecta → `null`, datos corruptos → `null` (no excepción), no-determinismo del cifrado (salt/IV), texto largo/Unicode. | ✅ 6 tests, en verde |
| 4 | `SecurityViewModelTest` (2026-08-26, ampliado 2026-08-27) — transiciones de `SecurityScreenState` (`goToSetupPin`/`goToLocked`/`verifyPin` correcto e incorrecto/`setupPin` exitoso y fallido), manejo de `error`/`successMessage` (`importLocalFile` en sus 3 ramas, `dismissError`/`dismissSuccess`), `protectPdfWithPassword`/`removePdfPassword` (Success/Error/WrongPassword), `deleteFile`/`restoreFile`/`reloadFiles`, `toggleBiometric`, `lockIfUnlocked` (RF-SEC-08, ver §11), `resetPin` (RF-SEC-09, ver §13). `authenticateWithBiometric()`, `savePdfToDownloads()` e `importFileToSecure()` quedan fuera (instancian `BiometricPrompt`/`ContentValues`/`MediaStore`/`ContentResolver` reales, requieren Robolectric/instrumentación — mismo motivo que ya excluye `isBiometricAvailable()` en `SecurityManagerTest`). El disparador real de `lockIfUnlocked()` (el `DisposableEffect` sobre `ProcessLifecycleOwner` en `SecurityScreen.kt`) no tiene test automatizado — verificado manualmente en el dispositivo real en su lugar (§11). | ✅ 23 tests, en verde |

Herramientas: JUnit5 + MockK + Turbine, configurado en `app/build.gradle.kts`
(`testOptions.unitTests.all { useJUnitPlatform() }`). 46 tests nuevos + 1 de
ejemplo, 0 fallos.

---

## 7. Compose UI Testing — flujo #3: desbloqueo con PIN (2026-08-25)

Tercera prueba de Compose UI del proyecto (ver también
[`visor-biblioteca.md` §9](visor-biblioteca.md#9-compose-ui-testing--flujo-1-abrir-documento-2026-08-25)
y [`conversion.md` §7](conversion.md#7-compose-ui-testing--flujo-2-conversión-2026-08-25)).
Misma infraestructura: JUnit4/`createAndroidComposeRule`, instrumentada
contra dispositivo real, sin Hilt.

- **`SecurityScreenTest`** cubre el desbloqueo de la Carpeta Segura con PIN:
  PIN correcto lleva a `SecurityScreenState.UNLOCKED` (se ve "Carpeta
  Segura"), PIN incorrecto muestra "PIN incorrecto" en pantalla y no
  desbloquea.
- **`SecurityManager` no se mockea** — a diferencia de `AdManager`/
  `DailyLimitManager` en los flujos #1 y #2, aquí se usa la instancia real
  (misma decisión que ya se tomó con `ImageFormatUseCase` en el flujo #2):
  es una clase concreta simple (hash SHA-256 + SharedPreferences), y
  mockearla habría probado el ViewModel/UI sin dar ninguna protección de
  regresión real sobre RF-SEC-01/02 (configurar/verificar PIN). El
  `Context` real de instrumentación se envuelve en un `ContextWrapper`
  propio (`IsolatedPrefsContext`) que solo redefine `getSharedPreferences()`
  y `getFilesDir()`, para que el PIN de prueba no toque el
  `docusmart_security` real del dispositivo (el mismo que usa la app
  instalada) — todo lo demás, incluyendo `BiometricManager.from(context)`
  dentro de `isBiometricAvailable()`, sigue siendo el `Context` real por
  delegación de `ContextWrapper`.
- **Intentado primero y descartado:** `spyk(realContext)` de MockK, para
  interceptar los mismos dos métodos sin escribir una clase nueva. Falla en
  dispositivo real con `MockKException: Can't instantiate proxy for class
  android.app.ContextImpl` — es una clase final del framework que el
  agente de mocking inline de MockK no puede interceptar (a diferencia de
  clases de la propia app, donde `mockk`/`spyk` sí funcionan sobre
  clases finales gracias a `mockk-android`). `ContextWrapper` es la vía
  estándar de Android para este caso y no depende de ningún framework de
  mocking.

---

## 8. Preguntas resueltas (2026-08-24)

| Pregunta | Decisión |
|---|---|
| ¿Qué pasa con el archivo original al proteger? | Se elimina tras copiar (RF-SEC-05) |
| ¿Recuperación de PIN olvidado? | No — restablecer borra los archivos (RF-SEC-09, RNF-SEC-02) |
| ¿Cifrado de QR protegido? | Migrar de XOR a AES (RF-SEC-14) |
| ¿Auto-bloqueo? | Sí, al pasar a segundo plano (RF-SEC-08, RNF-SEC-03) |
| Longitud mínima de contraseña de PDF | 4 caracteres, igual que QR (RF-SEC-10) |
| QR protegido leído fuera de DocuSmart | Muestra texto cifrado en bruto, sin indicador adicional (comportamiento esperado) |

## 9. Preguntas abiertas

Ninguna pendiente por ahora — todas las decisiones de producto para este módulo quedaron resueltas en §7.

## 10. Hallazgos de `SecurityViewModelTest` (2026-08-26)

- **RF-SEC-08 (auto-bloqueo al pasar a segundo plano) — implementado
  (2026-08-26).** Verificado con `grep` en todo el feature que no había
  ningún `DefaultLifecycleObserver`, manejo de `ON_STOP`/`ON_PAUSE`, ni
  llamada automática a `goToLocked()` — solo se llamaba manualmente desde
  el botón "Volver" de la pantalla de cambiar PIN. La app quedaba
  desbloqueada indefinidamente en segundo plano hasta que el usuario
  cerraba la app o navegaba manualmente hacia atrás. Ver §11 para el
  detalle de la implementación.
- **`setupPin()` fallaba en silencio — corregido (2026-08-27).** Si
  `SecurityManager.setPin()` devolvía `false` (ej. error de
  `SharedPreferences`), el `ViewModel` no actualizaba `error` ni cambiaba
  `screenState` — el usuario se quedaba mirando el teclado numérico sin
  ninguna indicación de que su PIN no se guardó. Ver §12 para el detalle.

## 11. RF-SEC-08 — auto-bloqueo al pasar a segundo plano (2026-08-26)

- **`SecurityViewModel.lockIfUnlocked()`** (nuevo) — vuelve a `LOCKED` solo
  si el estado actual es `UNLOCKED`; no toca `SETUP_PIN` a propósito (si el
  usuario está a mitad de configurar un PIN nuevo y recibe una
  notificación, no queremos descartar ese flujo — los dígitos tecleados
  viven en estado local del Composable, no en el ViewModel, así que no
  tocar `screenState` los preserva al volver). 2 tests unitarios nuevos en
  `SecurityViewModelTest`.
- **`SecurityScreen.kt`** — `DisposableEffect` que registra un
  `LifecycleEventObserver` sobre `ProcessLifecycleOwner.get().lifecycle` y
  llama `viewModel.lockIfUnlocked()` en `ON_STOP`.
  **`ProcessLifecycleOwner`, no `LocalLifecycleOwner`, a propósito:** la
  `Activity` del proyecto no declara `android:configChanges`, así que
  rotar la pantalla también dispara `ON_STOP`/`ON_START` de esa instancia
  de `Activity` (se destruye y recrea) — usar `LocalLifecycleOwner`
  habría bloqueado la Carpeta Segura en cada rotación, no solo al pasar a
  segundo plano de verdad. `ProcessLifecycleOwner` distingue ambos casos
  (no despacha `ON_STOP` si una nueva `Activity` arranca enseguida por un
  cambio de configuración) — verificado que es la recomendación oficial
  vigente antes de escribir el código, no asumida de memoria. Nueva
  dependencia `androidx.lifecycle:lifecycle-process` (mismo `lifecycle =
  "2.8.7"` ya usado en el resto del proyecto).
- **Verificado manualmente en el dispositivo real, los dos escenarios que
  motivaron la elección:**
  1. Configurar PIN → desbloqueada → botón Home (segundo plano) → volver a
     abrir la app → pide PIN de nuevo (no muestra la carpeta directo).
     PIN correcto vuelve a desbloquear con normalidad.
  2. Desbloqueada → rotar a horizontal y de vuelta a vertical → sigue
     mostrando la carpeta desbloqueada, **no** pide PIN de nuevo.
- Verificado también: `connectedDebugAndroidTest` (6 pruebas, sin
  regresión en `SecurityScreenTest`/`ConverterScreenTest`/
  `ViewerScreenTest`) y `testDebugUnitTest`/`detekt`/`lintDebug` en verde.
- **No cubierto por un test automatizado** — simular una transición real
  de `ProcessLifecycleOwner` desde un test de Compose UI (vs. desde la
  Activity individual de la prueba) es frágil/no directo con las
  herramientas actuales del proyecto; se optó por verificación manual en
  el dispositivo real (arriba) en vez de forzar un test automatizado poco
  confiable. La lógica pura (`lockIfUnlocked()`) sí queda cubierta por
  unit tests.

---

## 12. `setupPin()` deja de fallar en silencio (2026-08-27)

Hallazgo de `SecurityViewModelTest` (§10): si `SecurityManager.setPin()`
devolvía `false`, el `ViewModel` no hacía nada — ni error, ni cambio de
estado. El usuario se quedaba mirando el teclado numérico tras confirmar
su PIN sin ninguna indicación de que no se guardó.

- **`SecurityViewModel.setupPin(pin, errorMessage)`** — ahora recibe un
  mensaje de error (mismo patrón que `verifyPin(pin, incorrectPinMessage)`,
  resuelto vía `stringResource()` en la capa de presentación) y actualiza
  `uiState.error` cuando `setPin()` devuelve `false`. Nuevo recurso
  `security_setup_pin_error` en los 5 idiomas.
- **`SetupPinScreen` (antes sin ninguna conexión al `uiState.error` del
  ViewModel)** — nuevo parámetro `externalError`, mostrado con el mismo
  `Text` que ya usaba el error local de "los PINs no coinciden". Un
  `LaunchedEffect(externalError)` limpia `confirmPin` para que el usuario
  pueda reintentar sin volver a teclear el PIN completo desde cero (mismo
  comportamiento que ya tenía el caso de "PINs no coinciden"), y avisa al
  ViewModel (`viewModel.dismissError()`) para no repetir el mensaje si el
  Composable se recompone por otra razón.
- Actualizados los 2 tests unitarios de `setupPin` en
  `SecurityViewModelTest` y los 2 sitios que llamaban `setupPin(pin)` en
  `SecurityScreenTest` a la nueva firma de 2 argumentos.
- **Verificado en el dispositivo real:** el camino exitoso (PIN configurado
  correctamente) sigue funcionando exactamente igual que antes — el nuevo
  `LaunchedEffect` no interfiere cuando `externalError` es `null`. El
  camino de fallo (`setPin()` devolviendo `false`) no se pudo forzar de
  forma realista en el dispositivo real sin modificar código a propósito
  para simular el fallo; queda cubierto por el test unitario
  correspondiente, no por verificación manual.
- Verificado también: `connectedDebugAndroidTest` (6 pruebas, sin
  regresión) y `testDebugUnitTest`/`detekt`/`lintDebug` en verde.

---

## 13. RF-SEC-09/HU-SEC-06 — Restablecer PIN con pérdida de archivos (2026-08-27)

Único mecanismo de "recuperación" de PIN permitido por diseño (RNF-SEC-02):
restablecer implica perder los archivos protegidos, no hay recuperación sin
pérdida. El punto de entrada tenía que quedar en la pantalla **bloqueada**,
no dentro de "Configuración" de la Carpeta Segura ya desbloqueada — un
usuario que olvidó su PIN por definición no puede llegar a esa segunda
pantalla.

- **`SecurityManager.resetPinAndWipeFiles()`** (nuevo) — borra todos los
  archivos de `secureFolder` y llama a `clearPin()` existente.
- **`SecurityViewModel.resetPin()`** (nuevo) — llama al método anterior en
  `Dispatchers.IO` y actualiza `uiState` a `screenState = SETUP_PIN`,
  `hasPin = false`, `secureFiles = emptyList()`, llevando directo al flujo
  de "Crear PIN" (HU-SEC-01, AC2).
- **`PinUnlockScreen` (pantalla `LOCKED`)** — nuevo enlace "¿Olvidaste tu
  PIN?" bajo el teclado numérico (solo visible cuando `hasPin`, igual que el
  resto de esa rama). Al tocarlo se muestra un `AlertDialog` de advertencia
  explícita ("Se eliminarán todos los archivos de tu Carpeta Segura. Esta
  acción no se puede deshacer.", AC1) con "Cancelar"/"Restablecer y borrar"
  antes de ejecutar — no actúa directo al primer toque.
- Nuevos recursos en los 5 idiomas: `security_forgot_pin`,
  `security_reset_pin_dialog_title`, `security_reset_pin_dialog_body`,
  `security_reset_pin_confirm`. Paridad de claves verificada con `diff`
  entre los 5 `strings.xml`.
- Tests unitarios nuevos: `SecurityManagerTest` (`resetPinAndWipeFiles`
  elimina PIN y archivos) y `SecurityViewModelTest` (`resetPin` simula el
  caso real — desbloqueado con archivos, auto-bloqueado por RF-SEC-08, y
  solo entonces restablecido — y verifica que `secureFiles`/`hasPin` se
  limpian igual aunque el estado ya fuera `LOCKED`).
- **Verificado end-to-end en el dispositivo real:** PIN configurado →
  archivo colocado directamente en `files/secure/` vía `run-as` (para no
  depender del selector de archivos del sistema) → carpeta bloqueada →
  "¿Olvidaste tu PIN?" → diálogo de advertencia → "Restablecer y borrar" →
  la app navega a "Crea tu PIN" → confirmado por fuera de la app que
  `files/secure/` quedó vacío y que `pin_hash` desapareció de
  `shared_prefs/docusmart_security.xml` → se configuró un PIN nuevo sin
  problema, aterrizando en la Carpeta Segura vacía (0 archivos protegidos).
- Verificado también: `testDebugUnitTest`/`detekt`/`lintDebug` en verde.
