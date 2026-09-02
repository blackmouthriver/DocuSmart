# Despliegue y publicación

> Parte de [`CONTEXT.md`](../../CONTEXT.md). Cubre firma de release, CI/CD
> de construcción, y el camino hacia la primera publicación en Play Store.

**Estado (2026-08-25):** firma de release configurada y verificada
(`bundleRelease` genera un AAB correctamente firmado). Workflow de GitHub
Actions listo para construir y firmar en cada tag de versión. **Pendiente
del usuario:** configurar los 4 secrets de GitHub (una vez), respaldar el
keystore de forma segura, y hacer la primera subida manual a Play Console
(Google no permite automatizar la primera subida de una app nueva).
**CI de SonarCloud corregido 2026-08-30, ver §7** — llevaba fallando 28 de
las últimas 30 corridas por falta de espacio en disco del runner, no por
ningún retroceso real de código; el dashboard mostraba datos de un análisis
viejo (cobertura 0% incompleta, no una regresión real).

---

## 1. Firma de release

- Keystore generado con `keytool`: PKCS12, RSA 2048, alias
  `docsmart-upload`, válido hasta 2054-01-10 (muy por encima del mínimo que
  exige Google, 2033-10-22).
- Vive en `keystore/docsmart-release.jks` — **gitignored**, nunca se sube al
  repo. Las contraseñas están en `keystore.properties` (también gitignored;
  `keystore.properties.example` es la plantilla committeada).
- `app/build.gradle.kts` lee `keystore.properties` si existe y arma
  `signingConfigs.release` con esos valores. Si el archivo no existe (checkout
  limpio de un colaborador, o tareas de CI que no son de release),
  `assembleDebug`/`testDebugUnitTest`/etc. siguen funcionando normal — el
  build type `release` simplemente queda sin firmar hasta que el archivo
  exista.
- **Verificado localmente:** `./gradlew bundleRelease` genera
  `app/build/outputs/bundle/release/app-release.aab` firmado y verificado
  con `jarsigner -verify` (certificado autofirmado — normal y esperado para
  firma de apps Android, no es un error).

### ⚠️ Respaldo del keystore — acción tuya, no delegable

El archivo `keystore/docsmart-release.jks` y las contraseñas en
`keystore.properties` **solo existen en esta máquina**. Si se pierden:

- No se puede volver a generar el mismo keystore (la clave privada es única).
- No se pueden subir actualizaciones de la app a Play Store con el mismo
  paquete — quedarías forzado a publicar como una app nueva, perdiendo
  reseñas, instalaciones y el historial.

**Antes de seguir:** copia `keystore/docsmart-release.jks` y las 4 líneas de
`keystore.properties` a un gestor de contraseñas o almacenamiento seguro
propio (no un repositorio de código, ni siquiera uno privado sin cifrado
dedicado). Esto es algo que debes hacer tú — no hay forma de que yo lo
persista de forma segura por ti.

### Se ajustó también

- `-Xmx2048m` → `-Xmx4096m` en `gradle.properties`: R8 y el lint del build
  de release se quedaban sin memoria (nunca se había corrido
  `bundleRelease`/`assembleRelease` antes de configurar la firma).
- `app/proguard-rules.pro`: agregadas reglas `-dontwarn` para dependencias
  opcionales de Apache POI/commons-compress (log4j2, slf4j, osgi, zstd/xz,
  anotaciones bnd/findbugs) que R8 detectaba como "clases faltantes" — nunca
  se cargan en runtime en Android, POI las referencia bajo
  `try/catch ClassNotFoundException`.

---

## 2. CI: construir y firmar en cada tag (`.github/workflows/release.yml`)

Se activa con un tag `v*` (ej. `v1.0.0`) o manualmente
(`workflow_dispatch`). Construye el AAB firmado y lo deja como artefacto
descargable del workflow — **no publica a Play Console todavía** (ver §4).

### Secrets de GitHub necesarios (configurar una sola vez)

En GitHub → Settings → Secrets and variables → Actions, o por CLI:

```bash
gh secret set RELEASE_KEYSTORE_BASE64 --body "$(base64 -w0 keystore/docsmart-release.jks)"
gh secret set RELEASE_KEYSTORE_PASSWORD --body "<la storePassword de keystore.properties>"
gh secret set RELEASE_KEY_ALIAS --body "docsmart-upload"
gh secret set RELEASE_KEY_PASSWORD --body "<la keyPassword de keystore.properties — es la misma que storePassword, PKCS12 no admite distintas>"
```

*(En Windows/PowerShell, `base64 -w0` no existe — usar
`[Convert]::ToBase64String([IO.File]::ReadAllBytes("keystore/docsmart-release.jks"))`.)*

Estos 4 valores son secretos — configúralos tú directamente (por CLI local
o por la interfaz de GitHub), no los compartas en el chat.

### Probar el workflow

Una vez configurados los secrets:

```bash
git tag v1.0.0
git push origin v1.0.0
```

Esto dispara el workflow; el AAB firmado queda disponible en la pestaña
Actions → el run correspondiente → Artifacts.

---

## 3. CI: Compose UI Testing en emulador (`.github/workflows/ci.yml`, 2026-08-25)

Job nuevo `instrumented-tests`, corre en paralelo al job `build` existente
(mismos triggers: push/PR a `main`, `workflow_dispatch`). Ejecuta
`connectedDebugAndroidTest` — los 3 flujos críticos de Compose UI Testing
(abrir documento, conversión, PIN de Carpeta Segura) — contra un emulador
Android en el propio runner, no contra el dispositivo físico usado hasta
ahora en desarrollo. Decisión explícita de este proyecto de no montar esto
antes: "agregar un emulador a GitHub Actions es más lento/complejo, mejor
evaluarlo cuando haya más pruebas de este tipo" (ver
`visor-biblioteca.md` §9) — con 3 flujos ya en verde localmente, el costo
ya se justifica.

- **Aceleración por hardware (KVM):** los runners Linux hospedados por
  GitHub (`ubuntu-latest`, 2 vCPU, gratis) soportan KVM desde 2023; sin el
  paso de habilitar permisos de grupo KVM el emulador correría por software
  y sería demasiado lento para un job de CI. `arch: x86_64` es obligatorio
  para esto — es la única arquitectura acelerada por KVM en estos runners
  (no `arm64-v8a`, aunque el minSdk/compileSdk del proyecto lo soportarían
  en un dispositivo real).
- **API 34 (Android 14), `google_apis`:** iguala la versión real usada para
  verificar estos mismos flujos en desarrollo (motorola edge 30 neo).
- **`disable-animations: true`** en el job de pruebas reproduce en el
  emulador el fix de flakiness ya encontrado en el dispositivo real (ver
  `conversion.md` §7): con animaciones activas, correr varias pruebas de
  Compose UI seguidas falla de forma intermitente con
  `IllegalStateException: No compose hierarchies found`.
- **Cache de AVD** (`actions/cache` sobre `~/.android/avd/*` y
  `~/.android/adb*`, clave fija `avd-34-google_apis-x86_64`) evita
  descargar y arrancar el emulador desde cero en cada corrida — sin esto,
  cada push pagaría el costo completo de crear el AVD.
- Acción usada: `reactivecircus/android-emulator-runner@v2` (la estándar
  de la comunidad para este caso, verificada contra su documentación
  oficial antes de escribir el workflow, no asumida de memoria).
- **Primer intento real (push a `main`, 2026-08-25) falló:**
  `FATAL | Not enough space to create userdata partition. Available:
  6097.70 MB at /home/runner/.android/avd/test.avd, need 7372.80 MB` —
  el runner `ubuntu-latest` no trae suficiente espacio libre de fábrica
  para el AVD, problema conocido y documentado de la comunidad (nada que
  ver con KVM ni con las pruebas en sí). Corregido agregando un paso
  **"Liberar espacio en disco"** (`jlumbroso/free-disk-space@main`) antes
  de crear el AVD, con `android: false` a propósito — ese input borraría
  el SDK de Android preinstalado que el job `build` de este mismo
  workflow ya usa sin pasos extra, y que este job también necesita para
  `connectedDebugAndroidTest`; liberar solo .NET/Haskell/paquetes
  grandes/imágenes Docker/swap/caché de herramientas alcanza de sobra
  para cubrir el ~1.3 GB que faltaba.
- **Segundo intento real falló distinto:** `sdkmanager --licenses` fallaba
  casi al instante, con el mismo `android: false` puesto. Causa: el paso
  de liberar espacio corría *después* de "JDK 17" con `tool-cache: true`
  activado — ese input borra `$AGENT_TOOLSDIRECTORY`
  (`/opt/hostedtoolcache`), que es justo donde `actions/setup-java` acaba
  de instalar el JDK 17 que queda como `JAVA_HOME`; sin ese JDK,
  `sdkmanager` (que es una herramienta Java) no podía correr. Corregido
  con dos cambios: `tool-cache: false` (su valor por defecto — con
  dotnet/haskell/large-packages/docker-images/swap-storage alcanza de
  sobra sin tocarlo) y mover el paso completo al principio del job, antes
  de instalar JDK/Gradle/SDK de Android, para no depender del orden de
  flags para evitar este mismo error en el futuro.
- **Tercer intento real llegó hasta correr las pruebas y ahí se colgó el
  emulador:** `ERROR | detected a hanging thread 'QEMU2 CPU0
  thread'/'QEMU2 main loop'` justo durante `compileDebugKotlin`/
  `mergeExtDexDebugAndroidTest` del script `connectedDebugAndroidTest`.
  Causa: el runner solo tiene 2 vCPU en total, y ese script compila
  Kotlin/dex al vuelo con el emulador ya corriendo (que también pide 2
  núcleos) — compiten por los mismos 2 núcleos físicos y el emulador se
  queda sin CPU el tiempo suficiente para colgarse. Corregido agregando un
  paso **"Compilar APKs de debug y de test"**
  (`assembleDebug assembleDebugAndroidTest`) *antes* de arrancar el
  emulador — así, cuando `connectedDebugAndroidTest` corre más adelante
  con el emulador ya activo, encuentra casi todo up-to-date y solo
  necesita instalar los APKs y correr la instrumentación, mucho más
  liviano en CPU.
- **Cuarto intento real llegó hasta correr las 6 pruebas y 3 fallaron —
  esta vez sí, un bug real de los tests, no del entorno de CI.**
  `ConverterScreenTest.convertirImagenAWebp_muestraResultadoExitoso` y las
  dos de `SecurityScreenTest` fallaban con "could not find node"/"is not
  displayed" justo en la aserción final. Causa: ambos tests usaban
  `waitForIdle()` antes de tocar/afirmar sobre un nodo, en vez del patrón
  `waitUntil` con polling que el propio `ConverterScreenTest` ya usaba
  correctamente más abajo para "¡Conversión exitosa!" — `waitForIdle()`
  sincroniza el frame de Compose, no corrutinas en `Dispatchers.IO`
  (`unlockAndLoadFiles()` en `SecurityViewModel`) ni garantiza que la
  recomposición tras mutar el ViewModel desde `runOnUiThread` ya se haya
  dibujado. En el dispositivo real (más rápido, con GPU real en vez de
  `swiftshader` por software) esa carrera casi siempre se ganaba; en el
  emulador de CI, no. Corregido reemplazando esos `waitForIdle()` por
  `waitUntil` con polling explícito sobre el nodo esperado, y agregando un
  `waitForIdle()` entre cada toque individual del teclado numérico en
  `SecurityScreenTest` como red de seguridad adicional. Verificado en
  verde en el dispositivo real (`connectedDebugAndroidTest` + suite JVM
  completa) antes de reintentar en CI.
- **Quinto intento real: el `waitUntil` ya no fallaba al instante, pero se
  agotaba a los 10s** con `ComposeTimeoutException: Condition still not
  satisfied after 10000 ms` en los mismos 3 tests — confirma que el fix
  anterior (carrera corregida) era correcto, pero 10s de margen no
  alcanzan en el emulador de CI (`swiftshader` por software, 2 vCPU).
  Corregido subiendo esos `timeoutMillis` de 10 000 a 20 000 en los 3
  `waitUntil` de `ConverterScreenTest`/`SecurityScreenTest`. Verificado de
  nuevo en verde en el dispositivo real antes de reintentar.
- **Sexto intento real: seguía agotando el `waitUntil`, ahora a los 20s
  también** — subir el timeout no cambiaba nada, señal de que no era un
  problema de velocidad sino de que la condición nunca se cumplía. Causa
  real encontrada: el emulador arranca en **inglés** por defecto (system
  image `google_apis`), y los 3 tests buscan literales en **español**
  ("Carpeta Segura", "PIN incorrecto", "Convertir a WebP" — este último sí
  pasa por `stringResource(R.string.converter_to_format, ...)`, la nota
  del código que decía lo contrario estaba equivocada). El proyecto no
  tiene una carpeta `values-es/` propia — el español vive en `values/`
  (el fallback por defecto) — así que cualquier locale que no sea inglés/
  alemán/portugués/ruso (los 4 que sí tienen carpeta dedicada) cae en
  español de todas formas. Primer intento de arreglo: agregar
  `adb shell settings put system system_locales es-ES` +
  `am broadcast -a android.intent.action.LOCALE_CHANGED` al script del
  paso de pruebas, antes de `connectedDebugAndroidTest`.
- **Séptimo intento real: ese primer arreglo de locale falló distinto**
  — `am broadcast` con `LOCALE_CHANGED` es un broadcast protegido del
  sistema; `shell` (uid 2000) no tiene permiso para enviarlo
  (`SecurityException: Permission Denial`), ni siquiera con `adb root`
  (tampoco funciona en un emulador `google_apis` normal: "adbd cannot run
  as root in production builds" en algunas imágenes). Probado también
  `cmd locale set-app-locales <paquete> --locales es-ES` (locale por-app,
  sin necesitar broadcast ni root) — este sí funciona, confirmado en un
  emulador local API 34 `google_apis` idéntico al de CI, pero AGP
  reinstala/desinstala la app entre cada corrida de
  `connectedDebugAndroidTest` (parte de su limpieza automática), borrando
  el override antes de que el siguiente intento de fijarlo desde CI
  pudiera sobrevivir. **Se abandonó el enfoque de tocar el locale del
  sistema operativo/emulador desde CI** — demasiado frágil para lo que
  se necesitaba.
- **Fix definitivo: forzar el locale a nivel de código del test, no del
  SO.** `com.docsmart.core.ui.test.forceLocale()` (nuevo,
  `app/src/androidTest/.../core/ui/test/LocaleTestUtils.kt`) crea un
  `Context` con `Configuration.setLocale(es-ES)` vía
  `createConfigurationContext()`, provisto solo dentro del `setContent {}`
  de `ConverterScreenTest`/`SecurityScreenTest` con
  `CompositionLocalProvider(LocalContext provides ...)`. Determinístico en
  cualquier dispositivo/emulador/API, sin depender de nada del entorno de
  CI. **Efecto secundario encontrado al aplicarlo:** `LocalContext` dejó
  de encadenar de vuelta a la `Activity` real (`createConfigurationContext()`
  no es un `ContextWrapper`, a diferencia de `IsolatedPrefsContext` en
  `SecurityScreenTest`), y `rememberLauncherForActivityResult()` (usado
  por el picker de archivos en ambas pantallas) se resuelve por
  `LocalActivityResultRegistryOwner`/`LocalOnBackPressedDispatcherOwner`,
  no por `LocalContext` — sin re-proveer esos dos apuntando a
  `composeRule.activity`, fallaba con "No ActivityResultRegistryOwner was
  provided" al componer, aunque el test nunca abriera el picker de
  verdad. Corregido re-proveyendo ambos junto con `LocalContext`.
  Verificado en verde en un emulador local API 34 `google_apis` (idéntico
  al de CI) y en el dispositivo real, más la suite JVM completa, antes de
  reintentar en CI.
- Octavo intento en verificación tras todos los fixes (ver §2 de
  `CONTEXT.md` para el resultado final una vez confirmado).
- **Noveno hallazgo, 2026-09-01 — `target: google_apis` reemplazado por
  `aosp_atd`.** El job pasó en verde con los 3 flujos originales
  (2026-08-26 en adelante, ver historial de runs), pero volvió a fallar a
  partir de 2026-08-30 (commit "corrige bugs de QA") -- en ese momento
  solo por `ConverterScreenTest` (ya documentado como intermitente en
  `conversion.md` §7, no una regresión nueva). A medida que se agregaron
  ~27 pruebas más de Compose UI Testing durante los días siguientes (ver
  `compose-ui-testing.md`), la tasa de fallas creció de 1 prueba
  intermitente a **14-15 de 30 pruebas fallando de forma consistente**,
  todas con `ComposeTimeoutException`/"Failed to inject touch input" --
  pese a que las mismas pruebas pasan de forma confiable en el
  dispositivo real (Motorola Edge 30 Neo). Se confirmó que
  `disable-animations: true` sí se aplica correctamente en el emulador
  (los 3 `adb shell settings put global ..._scale 0.0` aparecen en el
  log) -- descartado como causa. Diagnóstico: la imagen `google_apis`
  trae SystemUI, Gmail, Maps y el resto del stack GMS corriendo de fondo,
  compitiendo por los 2 vCPU del runner -- cuanto más grande la suite, más
  contención. `aosp_atd` (Android Test Device) es la imagen que Google
  diseñó específicamente para instrumentación en CI, sin esos componentes
  (~33% menos tiempo de prueba reportado por terceros). Ningún test de
  este proyecto llama a Google Play Services/GMS real (`AdManager`/
  `BillingManager`/`PremiumManager` siempre mockeados en `androidTest/`),
  así que no depende de las Google APIs que `aosp_atd` tampoco trae.
  Aplicado en `ci.yml` y `sonarcloud.yml` (mismo AVD, clave de caché
  actualizada a `avd-34-aosp_atd-x86_64`). **Verificado con una corrida
  real disparada manualmente contra una rama de prueba (sin tocar
  `main`): NO tuvo ningún efecto** -- fallan exactamente las mismas 14-15
  pruebas, en el mismo orden, con los mismos tiempos (~20-21s cada una),
  tanto con `google_apis` como con `aosp_atd`. Descarta limpiamente la
  contención de CPU/GMS de fondo como causa. Se mantiene `aosp_atd`
  igual (no empeora nada, es la imagen recomendada por Google para CI),
  pero el problema real sigue sin resolver.
- **Décimo intento, 2026-09-01 — migración a v2 `createAndroidComposeRule`,
  descartada con datos.** Comparando qué pruebas fallan sistemáticamente
  contra cuáles pasan siempre (mismo mock de `AdManager` en todas): los
  tests que fallan dependen de que un `coEvery` de MockK sobre una
  función `suspend` del repositorio (ej. `loadRecentlyOpened()`) resuelva
  dentro de un `viewModelScope.launch` antes de que el contenido
  aparezca; los que no dependen de ningún mock `suspend` para su
  aserción pasan siempre. Coincide con la descripción oficial de v1
  (`UnconfinedTestDispatcher`, ejecución inmediata que puede enmascarar
  condiciones de carrera reales) vs v2 (`StandardTestDispatcher`, más
  fiel a producción) -- pero un piloto real en CI (`HomeScreenTest`
  migrado a `androidx.compose.ui.test.junit4.v2.createAndroidComposeRule`,
  sin ningún otro cambio) **falló exactamente igual, mismo patrón de
  tiempos**. Conclusión técnica: la distinción v1/v2 aplica al
  dispatcher interno de Compose Testing (`LaunchedEffect` dentro de la
  composición), no al `Dispatchers.Main` real que usa `viewModelScope` en
  un test instrumentado sobre dispositivo/emulador real -- v2 no toca en
  absoluto las corrutinas de los ViewModels, que es donde vive el
  problema real. Revertido, no se migró el resto de archivos.
  **Pista nueva para retomar más adelante**: existen issues documentados
  de MockK (`mockk/mockk#766`, `mockk/mockk#941`) sobre `coEvery`
  bloqueándose para siempre específicamente en pruebas de instrumentación
  Android (funcionan bien en JVM/unit tests) -- coincide con el síntoma
  observado acá. No se investigó más a fondo por costo/beneficio en esta
  sesión; **el job `instrumented-tests` queda con ~14-15 de 30 pruebas
  fallando de forma conocida y documentada en CI**, sin afectar la
  confianza de esas mismas pruebas verificadas en el dispositivo real.
- **Undécimo intento, 2026-09-02 — la teoría de MockK no explica todo el
  patrón; probado y descartado que sea simple falta de tiempo.** Al leer
  el log completo (no solo los nombres) de la corrida de CI más reciente,
  las 14 pruebas que fallan son siempre las mismas: `ConverterScreenTest`
  (1), `HomeScreenTest` (3), `LibraryScreenTest` (3), `TrashScreenTest`
  (2), `QrCreatorScreenTest` (1), `SettingsScreenTest` (2) y
  `ViewerRenameDeleteTest` (2) -- pero **3 de esos 7 archivos
  (`ConverterScreenTest`, `QrCreatorScreenTest`, `SettingsScreenTest`) no
  usan `coEvery` sobre ninguna función `suspend`**, lo que debilita la
  teoría de MockK como causa única. `ConverterScreenTest` en particular
  usa a propósito la instancia *real* de `ImageFormatUseCase` (para dar
  protección de regresión real contra un bug de `WEBP_LOSSLESS`
  encontrado antes en esta sesión), que hace conversión de `Bitmap` real
  dentro de `withContext(Dispatchers.IO)`.

  Se probó experimentalmente si era simple falta de tiempo: se subió
  `timeoutMillis` de 20 000 a 60 000 en los 9 `waitUntil()` de esos 7
  archivos y se disparó una corrida real de CI
  (run [33636806671](https://github.com/blackmouthriver/DocuSmart/actions/runs/33636806671)).
  **Resultado: fallan exactamente las mismas 14 pruebas, y cada una
  consume el presupuesto completo de 60 s antes de fallar** (antes
  consumían los 20 s completos) -- descarta limpiamente que sea
  "necesitan más tiempo en hardware más lento"; es una condición que
  nunca se cumple, no una que tarda. Cambio revertido (no aporta,
  solo alarga la corrida cuando falla).

  Se descartó también que fuera por el banner de anuncios real
  (`DocuSmartBannerAd`/`AdManager.isPremium`): el valor mockeado de
  `isPremium` no separa limpiamente las pruebas que fallan de las que
  pasan (ej. `PdfToolsScreenTest` mockea `isPremium = false` igual que
  `ConverterScreenTest` y pasa sin problema).

  **Estado al cierre de esta sesión**: causa raíz exacta aún sin
  confirmar. Las dos teorías más plausibles que quedan abiertas son (a)
  un cuelgue real específico de MockK en instrumentación Android
  (`mockk/mockk#766`/`#941`, no descartado, solo sin poder explicar los 3
  archivos sin `coEvery`) y (b) algo compartido entre esos 7 archivos que
  aún no se identificó (posible candidato: todos renderizan una
  `LazyColumn` con `DocumentUiModel`/iconos de tipo de documento, o
  hacen algún trabajo real de `Bitmap`/recursos gráficos, a diferencia de
  los que sí pasan). Para seguir, hace falta evidencia de más bajo nivel
  que un log de Gradle -- por ejemplo, un `adb shell am dumpheap`/thread
  dump del proceso instrumentado en el momento exacto del cuelgue, o
  logging manual (`Log.d`) agregado temporalmente dentro de las
  corrutinas sospechosas para ver en qué línea exacta se traban en CI.
- **Duodécimo intento, 2026-09-02 — esa evidencia de más bajo nivel se
  consiguió, pero descarta la teoría de "corrutinas que nunca resumen".**
  Se agregó `waitUntilOrDump()` (`com.docsmart.core.ui.test`), que llama
  a `printToLog()` justo cuando `ComposeTimeoutException` se dispara, en
  las 9 llamadas `waitUntil()` de los 7 archivos afectados. Se corrigieron
  dos problemas reales de infraestructura para poder leer el resultado:
  (1) el import `androidx.compose.ui.test.waitUntil` no existe como
  función top-level en esta versión de compose-ui-test -- ya es un
  miembro heredado de `ComposeTestRule`, no requiere import; (2)
  `reactivecircus/android-emulator-runner` parte el input `script` por
  saltos de línea y ejecuta **cada línea como un `sh -c` separado**
  (`for (const script of scripts) { await exec.exec('sh', ['-c', script]) }`
  en su `main.ts`/`script-parser.ts`) -- un script de varias líneas con
  `set +e`/`$?` entre líneas no sirve de nada porque el bucle se corta en
  la primera línea que falla; hubo que volcar logcat completo a la salida
  del propio step en **una sola línea** con `;`.

  Con logcat real capturado en el momento exacto del cuelgue
  (run [33645612376](https://github.com/blackmouthriver/DocuSmart/actions/runs/33645612376)),
  el árbol de semántica (`printToLog`) mostró en las 4 pantallas
  capturadas (`HomeScreenTest`, `LibraryScreenTest`, `QrCreatorScreenTest`,
  `ConverterScreenTest`) el mismo patrón: la pantalla queda congelada en
  el estado *anterior* a que complete la acción esperada -- en Home falta
  la sección de documentos recientes, en Library el contador dice "2
  documentos" pero la lista está vacía, en QrCreator sigue en el
  formulario sin generar el QR, y en Converter el botón "Convertir a
  WebP" sigue visible sin ningún cambio, como si el click nunca hubiera
  ocurrido.

  Se probó la hipótesis más obvia que explicaría esto: que las corrutinas
  reales que saltan a `Dispatchers.IO`/`Default` (`ImageFormatUseCase` en
  Converter) nunca resuman en este emulador de CI. Se implementó
  `DispatcherProvider` (inyectable, real `Dispatchers.IO` en producción)
  y se probó `ConverterScreenTest` con un `DispatcherProvider` de prueba
  que usa `Dispatchers.Main.immediate` para `io` -- **sin ningún thread
  real de por medio**. Verificado con una corrida real de CI
  (run [33647977125](https://github.com/blackmouthriver/DocuSmart/actions/runs/33647977125)):
  **falla exactamente igual, con el árbol de semántica capturado
  IDÉNTICO byte por byte al de antes del fix** -- el botón "Convertir a
  WebP" sigue mostrándose sin cambios. Esto descarta limpiamente que sea
  un problema de corrutinas/dispatchers: revertido (commit `35ec071`).

  **Nueva lectura de la evidencia**: dado que la UI queda exactamente en
  el estado *previo a la acción* (no a mitad de un cálculo, no con un
  spinner, no con un error) en las 4 pantallas capturadas, la sospecha
  más consistente con los datos ahora es que el **click/acción del
  usuario nunca llega a ejecutarse** en este emulador de CI para estos
  casos puntuales -- coincide con el otro patrón de falla ya visto
  (`AssertionError: Failed to inject touch input`) en
  `ViewerRenameDeleteTest`, solo que acá no lanza esa excepción
  explícita, simplemente no tiene efecto. **No investigado aún**: por qué
  la inyección de touch/acción fallaría silenciosamente solo en estas
  pantallas y no en las que sí pasan.
- **Décimo tercer intento, 2026-09-02 — confirmado con certeza: el click
  nunca invoca el handler.** Antes de seguir cambiando código de
  producción a ciegas, se agregó un único `Timber.d("CI_HANG_DIAG:
  convert() invocado")` en la primera línea de
  `ConverterViewModel.convert()` (el método atado a `onClick` del botón
  "Convertir a WebP") y se corrió una vez más en CI
  (run [33651702883](https://github.com/blackmouthriver/DocuSmart/actions/runs/33651702883)).
  **Ese log nunca aparece en el logcat capturado, ni una sola vez** --
  confirma con certeza (no solo indicios del árbol de UI) que
  `performClick()` sobre "Convertir a WebP" no logra invocar el método
  del ViewModel en este emulador de CI. Diagnóstico revertido (cumplió su
  propósito).

  **Conclusión de esta sesión**: la causa raíz real es que
  `performClick()` de Compose UI Testing -- que en `AndroidComposeTestRule`
  inyecta un evento de touch real a través de la ventana, no invoca la
  acción de semántica directamente -- falla en entregar el evento
  específicamente para estas 7 pantallas en el emulador de CI (mismo
  mecanismo, cree, que produce el `AssertionError: Failed to inject touch
  input` explícito visto en `ViewerRenameDeleteTest`, solo que acá sin
  excepción visible, simplemente sin efecto). Se descartaron con
  evidencia real de CI: contención de recursos (`aosp_atd`), dispatcher
  de Compose Testing (v1 vs v2), timeout insuficiente, corrutinas que no
  resumen (mockeadas o reales). **Por qué la inyección de touch falla
  específicamente para estos botones/pantallas y no para los que sí
  pasan queda sin resolver** -- necesitaría reproducir el problema con
  herramientas de más bajo nivel que este proyecto no tiene automatizadas
  todavía (ej. grabación de pantalla del emulador de CI, o Espresso
  `UiController` con logging de coordenadas reales de inyección).

---

## 4. Camino a la primera publicación (checklist)

1. ~~Firma de release configurada~~ ✅ (esta sesión).
2. ~~Política de privacidad publicada y formulario de seguridad de datos
   preparado~~ ✅ (esta sesión, ver §5) — falta solo cargar las respuestas
   en Play Console.
3. **Subida manual inicial a Play Console** — pendiente del usuario. Google
   no permite crear la primera versión de una app por API; tiene que
   hacerse una vez desde la consola web:
   - Crear la ficha de la app en Play Console (nombre, categoría, etc.).
   - Completar el **formulario de seguridad de datos** con las respuestas de §5.2.
   - Enlazar la **política de privacidad** (§5.1) en la ficha.
   - Subir `app-release.aab` (generado localmente o descargado del workflow)
     a una pista interna o cerrada primero, no directo a producción.
4. **Cuenta de servicio de Play Console** — una vez que la app ya tiene al
   menos una versión subida, se puede crear una cuenta de servicio
   (Play Console → Configuración → Acceso a la API) para automatizar
   subidas futuras vía Gradle Play Publisher. No tiene sentido crearla antes
   — no hay nada que actualizar todavía.
5. **Automatizar publicaciones futuras** (después del punto 4): agregar el
   plugin `com.github.triplet.play` a `app/build.gradle.kts`, un secret
   `PLAY_SERVICE_ACCOUNT_JSON`, y un paso en `release.yml` que suba el AAB a
   una pista (empezar por `internal`, no `production`).
6. ~~Play Billing real~~ ✅ código conectado (RF-PREM-05, ver
   `settings-premium.md` §8) — pendiente solo de que la app exista en Play
   Console con un perfil de pagos configurado para poder probar compras
   reales (bloqueado por el punto 3, no por código).

---

## 5. Política de privacidad y formulario de seguridad de datos (2026-08-25)

Basado en inventario real del código, no en suposiciones — ver §5.3 para el
detalle de qué se revisó.

### 5.1 Política de privacidad

- **Publicada:** https://sites.google.com/view/docusmart-privacidad/inicio
  — es la URL que va en Play Console.
- Redactada originalmente en `legal/privacy-policy.html` (queda en el repo
  como fuente/respaldo del texto, en una carpeta separada de `docs/` a
  propósito: `docs/requirements/` tiene specs internas que no deben quedar
  servidas como sitio público). Publicación final vía **Google Sites**, no
  GitHub Pages — GitHub pedía plan pago para Pages en este repo (razón no
  confirmada, probablemente visibilidad del repo), así que se optó por la
  alternativa gratuita sin fricción. `.github/workflows/pages.yml` queda en
  el repo sin uso por ahora — no hace daño dejarlo, se puede borrar o
  retomar más adelante si cambia la situación de GitHub.
- Correo de contacto: `jblackmouthr@gmail.com` (decisión del usuario).
- **No es asesoría legal:** el texto se basa en un inventario técnico
  exhaustivo del código (ver §5.3), pero para una app que va a monetizar con
  anuncios y eventualmente compras, vale la pena que alguien con criterio
  legal le eche un vistazo antes de publicar, sobre todo si en el futuro se
  agregan más categorías de datos.
- Solo en español por ahora — la app soporta 5 idiomas, pero la URL única ya
  desbloquea la publicación; traducir la política es una mejora aparte, no
  bloqueante.

### 5.2 Formulario de seguridad de datos de Play Console

Respuestas para copiar directamente en Play Console → Política de la app →
Seguridad de los datos. La app **cifra todo el tráfico** (`usesCleartextTraffic
= false`, corregido en la limpieza de SonarCloud) y **permite solicitar
borrado de datos** vía el correo de contacto de la política.

| Categoría (Play Console) | ¿Se recolecta? | Detalle |
|---|---|---|
| Ubicación (aproximada/precisa) | No | — |
| Información personal (nombre, email, ID de usuario, etc.) | No | Sin cuentas, sin login (no hay Firebase Auth) |
| Información financiera | **Sí** | Play Billing real ya conectado (RF-PREM-05, ver `settings-premium.md` §8) — la compra la procesa Google Play, DocuSmart no recibe ni almacena datos de tarjeta/pago, solo el resultado de la transacción. |
| Salud y estado físico | No | — |
| Mensajes | No | — |
| **Fotos y videos** | **No** | La app *accede* a fotos/documentos que el usuario elige (permiso de medios), pero no se transmiten a ningún servidor — se procesan 100% en el dispositivo. Play Console cuenta "recolectado" como transmitido fuera del dispositivo, no como accedido localmente. |
| **Archivos y documentos** | **No** | Misma razón — conversión/visualización/protección con contraseña corren local (iText7, Apache POI, en el propio dispositivo). |
| Calendario | No | — |
| Contactos | No | — |
| **Actividad en la app** | **Sí** | Eventos de uso (pantalla vista, tipo de conversión iniciada, herramienta PDF usada, páginas escaneadas, etc.) vía Firebase Analytics — nunca el contenido real de un documento/nota/QR, solo categorías. Propósito: **Analytics**. Compartido con: Google (Firebase). |
| Navegación web | No | — |
| **Info. de la app y rendimiento** | **Sí** | Logs de fallas (stack trace, modelo de dispositivo, versión de Android/app) vía Firebase Crashlytics. Propósito: **Analytics** (diagnóstico). Compartido con: Google (Firebase). |
| **Identificadores del dispositivo u otros** | **Sí** | Advertising ID, usado por Google AdMob. Propósito: **Publicidad**. Compartido con: Google (AdMob). |
| **Audio** | **No** | El dictado de notas usa `RecognizerIntent.ACTION_RECOGNIZE_SPEECH` (un Intent estándar de Android) — delega en la app de reconocimiento de voz del sistema; DocuSmart nunca captura ni procesa el audio directamente, solo recibe el texto resultante. Mismo principio que un selector de archivos del sistema. |

### 5.3 Cómo se armó este inventario (para que quede trazable)

Revisado directamente en el código, no supuesto:
- `AndroidManifest.xml` completo (permisos declarados y su alcance por versión de Android).
- `DocuSmartAnalytics.kt` — los 15 eventos reales que se envían a Firebase, uno por uno, confirmando que ninguno incluye contenido de archivos/notas/QR, solo metadatos categóricos.
- Búsqueda de `FirebaseAuth`/`firebase.auth` en todo el proyecto → no hay, confirmado que no existen cuentas de usuario.
- Búsqueda de `setCustomKey`/Crashlytics → no hay claves custom agregadas, solo el reporte estándar de Firebase.
- Búsqueda de `ConsentInformation`/`UserMessagingPlatform` (SDK de consentimiento de Google) → **implementado 2026-08-26**, ver `settings-premium.md` §9.
- `StudyScreen.kt` — confirmado que el dictado de voz usa `RecognizerIntent` (delega al sistema), no un `SpeechRecognizer` propio ni un servicio de voz en la nube contratado por la app.
- `AndroidManifest.xml` → el AdMob App ID configurado es **el ID de prueba público de Google** (`ca-app-pub-3940256099942544~...`), no uno real — ya venía comentado como pendiente ("reemplazar con el tuyo al publicar").

**Hallazgo corregido (2026-08-26):** no había SDK de consentimiento (Google
UMP) implementado. Si se muestran anuncios personalizados a usuarios en la
Unión Europea/Reino Unido, Google exige recolectar consentimiento
explícito antes (política de consentimiento de UE de Google/GDPR) — la app
no lo pedía. Implementado y verificado en el dispositivo real, ver
`settings-premium.md` §9.

---

## 6. Otros pendientes menores antes de la primera subida

- **`versionCode`/`versionName`** siguen en `1`/`1.0.0` — ajustar antes de
  la primera subida real si corresponde.
- **`targetSdk = 35`** — Play Console exige mantenerse dentro de la ventana
  de versión de Android soportada vigente al momento de publicar; verificar
  el requisito actual antes de subir.
- **AdMob App ID de prueba** en `AndroidManifest.xml` — reemplazar por el
  real antes de publicar (ver hallazgo en §5.3).

---

## 7. CI de SonarCloud roto por falta de espacio en disco (2026-08-30)

El usuario reportó la puerta de calidad de SonarCloud como "Fallido"
(cobertura 0,0%, calificación de seguridad D, duplicación 3,1-3,2%) al
revisar el dashboard antes de continuar con los pasos de publicación.
Investigado con `gh run list`/`gh run view` antes de tocar nada.

**Causa raíz real, no un problema de código:** `gh run list
--workflow=sonarcloud.yml` mostró 28 de las últimas 30 corridas en
`failure`, todas con el mismo error en el log del job:

```
System.IO.IOException: No space left on device
```

El runner `ubuntu-latest` se queda sin disco a mitad del build (iText7 +
Apache POI + caché de Gradle/Sonar) antes de que `jacocoTestReport` termine
de generar el XML de cobertura. Esto significa:

- La **cobertura 0,0%** en el dashboard no es una regresión real de
  testing — el reporte nunca se genera/sube porque el job falla antes.
- Los **27-37 hallazgos de seguridad/mantenibilidad** que sí se ven en el
  dashboard vienen del último análisis que sí llegó a completarse (uno de
  los pocos `success` en el historial), no de código agregado después.
- La puerta de calidad "Fallido" está evaluando datos de un análisis
  incompleto, no el estado real del código en `main`.

**Ya se había resuelto exactamente este mismo problema antes**, para el
job `instrumented-tests` de `ci.yml` (ver §3) — solo nunca se replicó al
workflow de SonarCloud cuando se agregó después.

**Corregido** agregando el mismo paso `jlumbroso/free-disk-space@...` (con
`tool-cache: false`, `android: false`) al principio de `sonarcloud.yml`,
antes de cualquier paso que instale JDK/Gradle/SDK — mismo orden y mismos
flags ya verificados en `ci.yml` por el mismo motivo (evitar borrar el JDK
recién instalado o el SDK de Android que el propio build necesita).

**De paso, se atendieron los 2 hallazgos reales visibles en el último
análisis completo:**
- **"Utilice el hash SHA de confirmación completo para esta dependencia"**
  (regla de Seguridad/Alto sobre GitHub Actions, repetida en `ci.yml`/
  `release.yml`/`sonarcloud.yml`/`gitleaks.yml`/`pages.yml`) — las Actions
  de terceros
  estaban fijadas por tag (`@v7`, `@main`, etc.), que es una referencia
  mutable: quien controle ese tag podría apuntarlo a código malicioso sin
  que el repo lo note. Corregido fijando las 12 referencias de Action en
  los 5 workflows a su SHA de commit completo (resuelto vía `gh api
  repos/<owner>/<repo>/git/refs/tags/<tag>`), con el tag original como
  comentario (`@<sha> # v7`) para que Dependabot (ya configurado para
  `github-actions` en `dependabot.yml`) las siga pudiendo actualizar solo.
- **Duplicación de código (3,1-3,2%, umbral 3,0%):** 3 clases de test
  agregadas en esta sesión (`StudyNotesStorageTest`, `StudyStatsStorageTest`,
  `ThemeManagerTest`) repetían casi línea por línea el mismo helper de
  `SharedPreferences` fake respaldado por un mapa en memoria. Extraído a
  `app/src/test/java/com/docsmart/testutil/FakeAndroidPrefs.kt`
  (`fakePrefsStore()` + `fakeContextWithPrefs(store)`, soporta tanto
  `String` como `Long` para cubrir los 3 casos), y los 3 archivos
  actualizados para usarlo en vez de su copia privada. No se tocó
  `LanguageManagerTest.kt` (helper similar pero preexistente, de una sesión
  anterior, con una forma distinta) — no es la causa de este pico de
  duplicación y tocarlo hubiera sido alcance extra no pedido.

**Verificado con el push real:** el job de SonarCloud terminó en verde
(9m26s, antes fallaba siempre) — confirmado con `gh run watch`. Con el
análisis ya completo, se consultó la puerta de calidad real vía la API
pública de SonarCloud
(`api/qualitygates/project_status?projectKey=blackmouthriver_DocuSmart`):

| Condición (código nuevo) | Antes (datos incompletos) | Con el análisis completo |
|---|---|---|
| Duplicación | 3,1-3,2% (❌) | **0,1% (✅)** — confirma que el fix del helper de test funcionó |
| Hotspots de seguridad revisados | — | **100% (✅)** |
| Fiabilidad / Mantenibilidad | — | **A / A (✅)** |
| Calificación de seguridad | D (dato viejo/incompleto) | **B (❌)** — real, ver abajo |
| Cobertura | 0,0% (dato incompleto) | **0,0% (❌)** — real, ver abajo |

### Calificación de seguridad B — 20 hallazgos reales corregidos parcialmente

Con el análisis completo, `api/issues/search?...&inNewCodePeriod=true`
mostró 20 hallazgos reales de seguridad en "código nuevo" (todo lo tocado
desde la última versión etiquetada, que cubre básicamente toda la sesión
de trabajo del 2026-08-29/30, no solo estos commits de CI). Corregidos los
de bajo riesgo y esfuerzo acotado, documentados para revisión posterior los
que implican una decisión de arquitectura:

- **"Avoid expanding secrets in a run block"** (`release.yml`, 4
  instancias) — el bloque que arma `keystore.properties` interpolaba
  `${{ secrets.X }}` directamente en el texto del script de shell; GitHub
  sustituye ese texto de forma literal antes de que el shell lo vea, así
  que un valor de secret con comillas/backticks/`$()` se ejecutaría como
  parte del script en vez de tratarse como dato opaco. Corregido pasando
  los 4 secrets por un bloque `env:` y referenciándolos como variables de
  shell (`$RELEASE_KEYSTORE_PASSWORD`, etc.) dentro del script.
- **"Not enforcing HTTPS"** (`gitleaks.yml`, 2 instancias) — las llamadas
  `curl -sSL` ya usaban URLs `https://`, pero `-L` (seguir redirecciones)
  seguiría una redirección a `http://` si el servidor alguna vez
  respondiera con una. Corregido agregando `--proto '=https' --tlsv1.2` a
  ambas llamadas.
- **"Set keyboardOptions to disable the keyboard cache"** (5 instancias:
  `PdfPasswordScreen.kt` ×3, `QrScreen.kt` ×2, `ViewerScreen.kt` ×1) — los
  campos de contraseña de PDF y de QR protegido usaban
  `PasswordVisualTransformation()` para ocultar visualmente el texto, pero
  no declaraban `keyboardType = KeyboardType.Password` en
  `keyboardOptions` — sin eso, el teclado del sistema puede guardar el
  texto tecleado en su caché de sugerencias/diccionario personalizado.
  Corregido agregando `KeyboardOptions(keyboardType = KeyboardType.Password)`
  a los 5 campos. Verificado en dispositivo real: el campo "Nueva
  contraseña" de Proteger PDF sigue enmascarando el texto igual que antes,
  con el teclado QWERTY estándar (no numérico) respondiendo con
  normalidad.
- **Sin tocar, pendiente de revisión con más contexto (no son errores de
  código, son decisiones que requieren evaluar el diseño existente):**
  - **"Make sure accessing the Android external storage is safe here"**
    (CRITICAL ×4: `ScanResultScreen.kt`, `SecurityViewModel.kt`,
    `ConverterViewModel.kt`, `PdfToolsViewModel.kt`) — son los usos ya
    documentados de `Environment.getExternalStoragePublicDirectory()`
    para el fallback pre-Android 10 (RNF-SCAN-02), un patrón deliberado y
    necesario para soportar minSdk 26. Probablemente aplica marcarlos como
    "Seguro" en el dashboard de SonarCloud (acción de revisión, no de
    código) en vez de cambiar el código.
  - **"Make sure performing a biometric authentication without a
    CryptoObject is safe here"** (`SecurityViewModel.kt:120`) — Carpeta
    Segura usa biometría como desbloqueo de conveniencia junto al PIN, no
    como el mecanismo criptográfico que protege los archivos en sí;
    agregar un `CryptoObject` real requeriría atar la biometría a una
    clave del Keystore de Android, un cambio de diseño más grande que
    vale la pena evaluar aparte, no un ajuste de una línea.
  - **Verificación de dependencias (Gradle) / `verification-metadata.xml`
    faltante** — requiere generar y mantener un archivo de metadatos de
    verificación de dependencias (`./gradlew --write-verification-metadata`),
    una iniciativa de endurecimiento de la cadena de suministro aparte,
    no relacionada con este CI.

### Cobertura 0,0% en código nuevo — no es un bug, es un límite conocido de arquitectura

A diferencia de la duplicación/seguridad, esta condición **no se corrigió**
porque no es algo que se arregle escribiendo más código en una pasada
puntual: la gran mayoría de las líneas "nuevas" desde la última versión
etiquetada son Composables de Compose UI y código atado directamente al
framework de Android (`PomodoroTimerService`, `ScanImageEditor` con
`Bitmap`/`Canvas`, etc.) que este proyecto, por convención ya establecida y
documentada repetidamente en `study.md`/`scanner.md`/`settings-premium.md`,
**no cubre con unit tests** — solo la lógica de negocio pura, que sí tiene
tests pero es una fracción pequeña del total de líneas nuevas. El umbral de
80% de la puerta "Sonar way" es el default genérico de SonarCloud, no algo
calibrado para un proyecto Android con esta arquitectura (sin Compose UI
Testing exhaustivo). Dos caminos posibles, ambos fuera de alcance de una
corrección de código:
1. Ajustar el umbral de cobertura de la puerta de calidad en la
   configuración del proyecto en SonarCloud (acción del usuario en el
   dashboard).
2. Invertir en Compose UI Testing más exhaustivo (ya hay 3 flujos cubiertos,
   ver `visor-biblioteca.md` §9) — una iniciativa grande aparte, no algo
   para resolver de pasada.
