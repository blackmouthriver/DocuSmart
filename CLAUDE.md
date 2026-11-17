# DocuSmart (Android) — guía para Claude

App Android nativa para ver, convertir y gestionar documentos. **En producción en Google Play**
(`com.docsmart`, versión 1.1.5 / código 11 al 2026-11-17). Kotlin + Jetpack Compose + Hilt + Room,
Clean Architecture por feature. Repo hermano: `../docusmart-web` (web en https://docusmart.online,
con su propio `CLAUDE.md`).

**Memoria larga del proyecto:** `CONTEXT.md` (≈2 800 líneas: historia, decisiones, QA y roadmap).
No leerlo entero; ir a la sección que haga falta (§2 estado, §8 roadmap, §10 preferencias del usuario).
Specs por módulo en `docs/requirements/*.md`.

## Comandos

```bash
./gradlew assembleDebug                 # compilar
./gradlew lintDebug                     # Android Lint
./gradlew detekt                        # análisis estático (con baseline en config/detekt)
./gradlew testDebugUnitTest             # pruebas unitarias (JUnit 5 + MockK, ArchUnit)
./gradlew connectedDebugAndroidTest     # pruebas instrumentadas (Compose UI) en dispositivo
```

El CI (`.github/workflows/ci.yml`) corre build, lint, detekt y pruebas unitarias. Verifica además que no
baje el número de pruebas (`scripts/check_unit_test_count.py` + `config/ci/unit-tests-min.txt`) y corre
las instrumentadas en un emulador repartidas en 8 fragmentos.
Release: `scripts/release-suite.ps1`; firma con `keystore.properties` (nunca versionar ni mostrar sus valores).

## Estructura

```
app/src/main/java/com/docsmart/
├─ core/       Transversal: ads, analytics, billing, data/db (Room), media, navegation, pdf,
│              permission, premium, remoteconfig, security, ui (components, theme, util), util
└─ features/<feature>/{data,domain,presentation}
               agenda · converter · home · library · onboarding · pdftools · premium ·
               scanner · security · settings · splash · study · viewer
app/src/test/java/com/docsmart/          Unitarias, misma ruta que el código (+ architecture/, testutil/)
app/src/androidTest/                     Instrumentadas (Compose UI Testing)
app/src/main/res/values-<idioma>/        Textos en 12 idiomas
docs/requirements/  scripts/  config/{ci,detekt}  legal/  store-screenshots/
```

- No todas las features tienen las tres capas (home, settings y splash solo tienen `presentation`).
- `ArchitectureTest` (ArchUnit) garantiza que `domain` y `data` nunca dependan de `presentation`: respetarlo.
- `core/navegation` está mal escrito pero es el nombre real del paquete. No renombrarlo de paso: si se hace,
  que sea en un PR propio.

## Convenciones

- **Idiomas (12):** es (por defecto, `values/`), en, pt, de, ru, ja, ko, zh, it, fr, ca, eu. Toda cadena visible
  va en `strings.xml` con paridad exacta entre los 12 idiomas, y se usa con `stringResource()`.
- **IA y procesamiento en el dispositivo:** evitar servicios en la nube (costo y promesa de privacidad "100 % local").
- **Premium:** 3 días de prueba automática al instalar (por `firstInstallTime`), distinta del trial de suscripción.
- Commits, comentarios y documentación en español.

## Flujo de trabajo

- Responder siempre en español al usuario.
- Rama por tarea desde `main` + PR. **Nunca commit/merge/push a `main` sin aprobación explícita** para ese lote.
- Antes de pedir la fusión: una pasada general de punta a punta, no solo del cambio puntual.
- Antes de confiar en un resumen de sesión, verificar con `git log`/`git status` qué está realmente fusionado.
- **Teléfono real:** se puede usar para pruebas E2E e instrumentadas; los documentos de prueba son desechables.
- **Capturas por adb:** guardarlas en `/data/local/tmp/`, nunca en `/sdcard/` (se indexarían como documentos del usuario).

## Trampas conocidas

- La máquina de desarrollo tiene 15 GB de RAM: builds que fallan o ANR pueden deberse a falta de memoria,
  no a un bug de la app. Cerrar procesos pesados (otro Gradle, emulador) antes de concluir.
- Apache POI necesita `org.apache.xmlbeans`: excluirlo rompió en silencio Word/Excel → PDF. No excluirlo de las
  dependencias ni quitar su `-keep` en `app/proguard-rules.pro`.
- Las pruebas instrumentadas en CI tienen fallos conocidos listados en `config/ci/instrumented-known-failures.txt`.
