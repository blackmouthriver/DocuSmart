# Despliegue y publicación

> Parte de [`CONTEXT.md`](../../CONTEXT.md). Cubre firma de release, CI/CD
> de construcción, y el camino hacia la primera publicación en Play Store.

**Estado (2026-08-25):** firma de release configurada y verificada
(`bundleRelease` genera un AAB correctamente firmado). Workflow de GitHub
Actions listo para construir y firmar en cada tag de versión. **Pendiente
del usuario:** configurar los 4 secrets de GitHub (una vez), respaldar el
keystore de forma segura, y hacer la primera subida manual a Play Console
(Google no permite automatizar la primera subida de una app nueva).

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
