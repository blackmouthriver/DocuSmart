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
descargable del workflow — **no publica a Play Console todavía** (ver §3).

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

## 3. Camino a la primera publicación (checklist)

1. ~~Firma de release configurada~~ ✅ (esta sesión).
2. ~~Política de privacidad + formulario de seguridad de datos preparados~~ ✅
   (esta sesión, ver §4) — falta solo que actives GitHub Pages (una vez) y
   cargues las respuestas en Play Console.
3. **Subida manual inicial a Play Console** — pendiente del usuario. Google
   no permite crear la primera versión de una app por API; tiene que
   hacerse una vez desde la consola web:
   - Crear la ficha de la app en Play Console (nombre, categoría, etc.).
   - Completar el **formulario de seguridad de datos** con las respuestas de §4.2.
   - Enlazar la **política de privacidad** (§4.1) en la ficha, una vez que
     Pages esté activo y la URL responda.
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
6. **Play Billing real** — pendiente, backlog aparte (ver
   `CONTEXT.md` §2, requerimiento #18). También depende de que la app ya
   exista en Play Console con un perfil de pagos configurado.

---

## 4. Política de privacidad y formulario de seguridad de datos (2026-08-25)

Basado en inventario real del código, no en suposiciones — ver §4.3 para el
detalle de qué se revisó.

### 4.1 Política de privacidad

- Redactada en `legal/privacy-policy.html`, pensada para publicarse vía
  GitHub Pages (`.github/workflows/pages.yml`, se activa con cualquier push
  a `legal/**`).
- **Carpeta separada de `docs/`** a propósito: `docs/requirements/` tiene
  specs internas (hallazgos de seguridad, bugs, decisiones de producto) que
  no deben quedar servidas como sitio web público.
- Correo de contacto: `jblackmouthr@gmail.com` (decisión del usuario).
- **Pendiente de tu parte, no delegable:** activar Pages una sola vez —
  Settings del repo → Pages → Source: "GitHub Actions" (no "Deploy from a
  branch"). Sin este paso el workflow corre pero no hay dónde servir el
  resultado. Una vez activo, la URL queda en
  `https://blackmouthriver.github.io/DocuSmart/privacy-policy.html` — es la
  que se pega en Play Console.
- **No es asesoría legal:** el texto se basa en un inventario técnico
  exhaustivo del código (ver §4.3), pero para una app que va a monetizar con
  anuncios y eventualmente compras, vale la pena que alguien con criterio
  legal le eche un vistazo antes de publicar, sobre todo si en el futuro se
  agregan más categorías de datos.
- Solo en español por ahora — la app soporta 5 idiomas, pero la URL única ya
  desbloquea la publicación; traducir la política es una mejora aparte, no
  bloqueante.

### 4.2 Formulario de seguridad de datos de Play Console

Respuestas para copiar directamente en Play Console → Política de la app →
Seguridad de los datos. La app **cifra todo el tráfico** (`usesCleartextTraffic
= false`, corregido en la limpieza de SonarCloud) y **permite solicitar
borrado de datos** vía el correo de contacto de la política.

| Categoría (Play Console) | ¿Se recolecta? | Detalle |
|---|---|---|
| Ubicación (aproximada/precisa) | No | — |
| Información personal (nombre, email, ID de usuario, etc.) | No | Sin cuentas, sin login (no hay Firebase Auth) |
| Información financiera | No (todavía) | `simulatePurchase()` es un stub local — revisar esta fila cuando se conecte Play Billing real (ver §3) |
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

### 4.3 Cómo se armó este inventario (para que quede trazable)

Revisado directamente en el código, no supuesto:
- `AndroidManifest.xml` completo (permisos declarados y su alcance por versión de Android).
- `DocuSmartAnalytics.kt` — los 15 eventos reales que se envían a Firebase, uno por uno, confirmando que ninguno incluye contenido de archivos/notas/QR, solo metadatos categóricos.
- Búsqueda de `FirebaseAuth`/`firebase.auth` en todo el proyecto → no hay, confirmado que no existen cuentas de usuario.
- Búsqueda de `setCustomKey`/Crashlytics → no hay claves custom agregadas, solo el reporte estándar de Firebase.
- Búsqueda de `ConsentInformation`/`UserMessagingPlatform` (SDK de consentimiento de Google) → **no está implementado** (ver hallazgo abajo).
- `StudyScreen.kt` — confirmado que el dictado de voz usa `RecognizerIntent` (delega al sistema), no un `SpeechRecognizer` propio ni un servicio de voz en la nube contratado por la app.
- `AndroidManifest.xml` → el AdMob App ID configurado es **el ID de prueba público de Google** (`ca-app-pub-3940256099942544~...`), no uno real — ya venía comentado como pendiente ("reemplazar con el tuyo al publicar").

**Hallazgo real, no abordado en esta pasada:** no hay SDK de consentimiento
(Google UMP) implementado. Si se van a mostrar anuncios personalizados a
usuarios en la Unión Europea/Reino Unido, Google exige recolectar
consentimiento explícito antes (política de consentimiento de UE de
Google/GDPR) — hoy la app no lo pide. Vale la pena resolverlo antes de
activar anuncios reales en producción, no solo antes de publicar.

---

## 5. Otros pendientes menores antes de la primera subida

- **`versionCode`/`versionName`** siguen en `1`/`1.0.0` — ajustar antes de
  la primera subida real si corresponde.
- **`targetSdk = 35`** — Play Console exige mantenerse dentro de la ventana
  de versión de Android soportada vigente al momento de publicar; verificar
  el requisito actual antes de subir.
- **AdMob App ID de prueba** en `AndroidManifest.xml` — reemplazar por el
  real antes de publicar (ver hallazgo en §4.3).
