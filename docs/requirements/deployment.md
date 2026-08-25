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
2. **Subida manual inicial a Play Console** — pendiente del usuario. Google
   no permite crear la primera versión de una app por API; tiene que
   hacerse una vez desde la consola web:
   - Crear la ficha de la app en Play Console (nombre, categoría, etc.).
   - Completar el **formulario de seguridad de datos** (qué datos recolecta
     la app — pendiente, ver §4).
   - Publicar la **política de privacidad** en una URL accesible (pendiente,
     ver §4) y enlazarla en la ficha.
   - Subir `app-release.aab` (generado localmente o descargado del workflow)
     a una pista interna o cerrada primero, no directo a producción.
3. **Cuenta de servicio de Play Console** — una vez que la app ya tiene al
   menos una versión subida, se puede crear una cuenta de servicio
   (Play Console → Configuración → Acceso a la API) para automatizar
   subidas futuras vía Gradle Play Publisher. No tiene sentido crearla antes
   — no hay nada que actualizar todavía.
4. **Automatizar publicaciones futuras** (después del punto 3): agregar el
   plugin `com.github.triplet.play` a `app/build.gradle.kts`, un secret
   `PLAY_SERVICE_ACCOUNT_JSON`, y un paso en `release.yml` que suba el AAB a
   una pista (empezar por `internal`, no `production`).
5. **Play Billing real** — pendiente, backlog aparte (ver
   `CONTEXT.md` §2, requerimiento #18). También depende de que la app ya
   exista en Play Console con un perfil de pagos configurado.

---

## 4. Pendiente — no abordado en esta pasada

- **Política de privacidad:** falta redactar y publicar en una URL. Se
  puede armar a partir de los datos reales que la app recolecta (Firebase
  Analytics/Crashlytics, AdMob, permisos de cámara/almacenamiento/media).
- **Formulario de seguridad de datos de Play Console:** falta preparar las
  respuestas (qué se recolecta, con qué propósito, si se comparte con
  terceros) para que el usuario las cargue en la consola.
- **`versionCode`/`versionName`** siguen en `1`/`1.0.0` — ajustar antes de
  la primera subida real si corresponde.
- **`targetSdk = 35`** — Play Console exige mantenerse dentro de la ventana
  de versión de Android soportada vigente al momento de publicar; verificar
  el requisito actual antes de subir.
