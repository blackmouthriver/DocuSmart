# DocuSmart — Contexto del proyecto

> Documento vivo de memoria del proyecto. Se actualiza a medida que avanzamos —
> requerimientos, decisiones, hallazgos de QA y roadmap en un solo lugar para no
> perder contexto entre sesiones. Fuentes originales: documentos en
> `C:\Users\HP\Desktop\proyectoDocSmart\` (ver [Fuentes](#fuentes-originales) al final).

**Última actualización:** 2026-08-29 (auditoría completa de avance por HU/RF, ver §2)

**Specs por módulo (FR/NFR + HU con criterios de aceptación):**
- [`docs/requirements/security.md`](docs/requirements/security.md) — Carpeta Segura, contraseña PDF, QR protegido (en refinamiento)
- [`docs/requirements/pdf-tools.md`](docs/requirements/pdf-tools.md) — Unir, Dividir, Comprimir, Rotar PDF (en refinamiento)
- [`docs/requirements/visor-biblioteca.md`](docs/requirements/visor-biblioteca.md) — Visor, Biblioteca, Home/Recientes (en refinamiento)
- [`docs/requirements/conversion.md`](docs/requirements/conversion.md) — 17 combinaciones de conversión (en refinamiento)
- [`docs/requirements/scanner.md`](docs/requirements/scanner.md) — Escanear documento (ML Kit) + lector/creador de QR (en refinamiento)
- [`docs/requirements/settings-premium.md`](docs/requirements/settings-premium.md) — Ajustes + Premium/límites de uso (en refinamiento)
- [`docs/requirements/study.md`](docs/requirements/study.md) — Lectura con voz, notas, Pomodoro (en refinamiento)
- [`docs/requirements/deployment.md`](docs/requirements/deployment.md) — Firma de release, CI/CD, camino a la primera publicación (en progreso)

---

## 1. Qué es DocuSmart

App móvil nativa Android (Kotlin + Jetpack Compose + Hilt + Clean Architecture
por feature) para visualizar, convertir y gestionar documentos de forma
rápida, limpia y no invasiva. Multilenguaje (es/en/de/pt/ru). Con plan
Premium. Destino: Google Play Store (cuenta ya abierta, pendiente de subir).

Contexto académico: el proyecto tiene entregables tipo Scrum (backlog,
sprint backlog, cronograma, roles) — ver [§7](#7-entregables-académicos-pendientes).

---

## 2. Estado actual (auditado 2026-08-24)

- **Build:** compila limpio, lint en 0 errores, detekt integrado con
  baseline (457 hallazgos previos suprimidos), CI en GitHub Actions
  (`.github/workflows/ci.yml`) corriendo build+lint+detekt+tests en cada
  push/PR a `main` (simplificado tras la limpieza de ramas), más
  Dependabot y escaneo de secretos con Gitleaks. Desde 2026-08-25, un
  segundo job (`instrumented-tests`) corre los 3 flujos de Compose UI
  Testing contra un emulador con aceleración KVM en el mismo workflow —
  aún sin verificar en un run real de GitHub Actions, ver
  `docs/requirements/deployment.md` §3.
- **i18n:** claves de string × 5 idiomas (paridad exacta verificada), las 7
  pantallas con texto fijo ya conectadas a `stringResource()`, más
  `ConversionType`/`ConversionSuccess.kt` (2026-08-26, ver módulo
  Conversión abajo).
- **Tests:** 117 tests reales (Seguridad: 48, Herramientas PDF: 8, Visor+Biblioteca: 21, Conversión: 10, Escáner: 10, Ajustes+Premium: 14, Estudio: 5, ejemplo: 1), 0 fallos, verificado corriendo la suite completa. 106 son unitarios puros; 1 clase (`DocumentHistoryDaoTest`, 6 tests) es la primera **prueba de integración** del proyecto, contra SQLite real; 3 son **Compose UI Testing** instrumentadas contra dispositivo real (`ViewerScreenTest`, `ConverterScreenTest`, `SecurityScreenTest` — flujos #1, #2 y #3). Cobertura aún baja en proporción al total de use cases del proyecto (~4.4% de líneas, ver §8 SonarCloud).
- **Base de datos:** Room desde 2026-08-25, primera tabla (`document_history`,
  historial de documentos abiertos — ver §2 "Historial de documentos
  abiertos" y `docs/requirements/visor-biblioteca.md` §8). Favoritos/idioma/
  tema/premium siguen en SharedPreferences/DataStore, deliberadamente — son
  preferencias simples, no datos relacionales.
- **Arquitectura:** Clean Architecture por feature (`domain`/`presentation`),
  formal pero con capa `data/` inconsistente entre features.
- **Commits recientes:** estabilización de build + i18n (`ec095f7`), CI +
  detekt (`c3c3e45`), fix de permisos de gradlew (`c9c3a1f`).

### Bugs reales corregidos hoy
Crash de conversión WebP en Android 8-10, memory leak en `ViewerViewModel`,
selector de idioma inalcanzable en Ajustes, botón de tutorial roto,
reconocimiento de voz forzando español, escape de `%` roto en 2 strings.

### Módulo Seguridad (2026-08-24)
Bug crítico corregido: "carpeta segura no bloquea archivo" — causado por
`SecurityViewModel` copiando archivos sin llamar a `SecurityManager.moveToSecure()`
(que ya existía y sí borraba el original). QR pasó de cifrado XOR débil a
AES-256/GCM + PBKDF2 (`QrCrypto.kt`), y se construyó el flujo completo de
lectura de QR protegido (antes no existía UI para desbloquear, solo el
cifrado al crear). Primeros tests unitarios reales del proyecto: 24 tests
(JUnit5 + MockK), `QrCryptoTest`, `SecurityManagerTest`, `PdfPasswordUseCaseTest`.
Detalle completo en [`docs/requirements/security.md`](docs/requirements/security.md).

**Compose UI Testing — flujo #3 (2026-08-25):** `SecurityScreenTest` cubre
el desbloqueo de la Carpeta Segura con PIN (correcto → desbloquea,
incorrecto → muestra error), usando `SecurityManager` real (no mockeado)
envuelto en un `ContextWrapper` propio para aislar el PIN de prueba del
`docusmart_security` real del dispositivo. Detalle en
[`docs/requirements/security.md` §7](docs/requirements/security.md).

**`SecurityViewModelTest` (2026-08-26):** 20 tests nuevos — transiciones de
`SecurityScreenState`, `error`/`successMessage`, PIN password, archivos,
biometría. Dos hallazgos: RF-SEC-08 (auto-bloqueo al pasar a segundo
plano) no estaba implementado (verificado con `grep`, cero código de
lifecycle en el feature — **implementado el mismo día, ver abajo**); y
`setupPin()` fallaba en silencio si `SecurityManager.setPin()` devolvía
`false` (ni error ni cambio de estado — **corregido 2026-08-27, ver
abajo**). Detalle en
[`docs/requirements/security.md` §10](docs/requirements/security.md).

**`setupPin()` deja de fallar en silencio (2026-08-27):**
`SecurityViewModel.setupPin(pin, errorMessage)` ahora avisa vía
`uiState.error` cuando `SecurityManager.setPin()` devuelve `false`, y
`SetupPinScreen` (antes sin conexión al error del ViewModel) lo muestra y
deja reintentar sin volver a teclear el PIN completo. Verificado en el
dispositivo real que el camino exitoso no cambió; el camino de fallo
queda cubierto por el test unitario (no se pudo forzar de forma realista
en el dispositivo). Detalle en
[`docs/requirements/security.md` §12](docs/requirements/security.md).

**RF-SEC-08 — auto-bloqueo al pasar a segundo plano (2026-08-26):**
`SecurityViewModel.lockIfUnlocked()` + `DisposableEffect` en
`SecurityScreen.kt` sobre `ProcessLifecycleOwner` (no
`LocalLifecycleOwner` — la Activity no declara `configChanges`, así que
rotar la pantalla también dispara `ON_STOP` de esa Activity;
`ProcessLifecycleOwner` sí distingue eso de un backgrounding real).
Verificado manualmente en el dispositivo real: backgrounding real bloquea
correctamente, rotar la pantalla no bloquea de más. 2 tests unitarios
nuevos. Detalle en
[`docs/requirements/security.md` §11](docs/requirements/security.md).

### Módulo Herramientas PDF (2026-08-24)
Bug de arquitectura corregido: Unir y Rotar rasterizaban cada página a bitmap
antes de reconstruir el PDF, destruyendo todo el texto seleccionable/buscable
del resultado — no estaba en ningún reporte de QA, se encontró al auditar el
código antes de escribir tests. Ambos migrados a iText7 (mismo enfoque que ya
usaban Dividir y la contraseña de PDF). Dos hallazgos de la QA de mayo
("dividir no funciona", "comprimir no ofrece guardar/compartir") se
confirmaron **obsoletos** mediante tests reales — ya no se reproducen contra
el código actual. 8 tests nuevos (`SplitPdfUseCaseTest`, `MergePdfUseCaseTest`,
`RotatePdfUseCaseTest`). Backlog de nuevas funcionalidades (numeración, marca
de agua, reordenar páginas, etc.) documentado como RF-PDF-06 a RF-PDF-15.
Detalle completo en [`docs/requirements/pdf-tools.md`](docs/requirements/pdf-tools.md).

### Módulo Visor + Biblioteca + Home (2026-08-24)
3 bugs reales corregidos: (1) favoritos/alias no coincidían entre Visor y
Biblioteca/Home por un id inconsistente (el Visor anteponía `"file://"` a
rutas absolutas, Biblioteca/Home no) — `FavoritesRepository` en sí ya
persistía bien, la causa raíz era el id, no la persistencia; (2) la búsqueda
en el Visor no hacía nada para PDF (el botón aparecía habilitado pero
`PdfViewerContent` no recibía `searchQuery`) — construido `SearchPdfTextUseCase`
(extracción de texto por página con iText7) con navegación entre páginas con
coincidencias; (3) "eliminar" en Biblioteca/Home solo ocultaba el documento
de la lista en memoria, nunca borraba el archivo real — reaparecía al
recargar — corregido con `DocumentRepository.deleteDocument()`. 4 hallazgos
de la QA de mayo confirmados **obsoletos** (ya corregidos antes de esta
sesión, sin registro de cuándo): favoritos, pestañas dispositivo/app, botón
"Abrir" de Home, accesos rápidos de Home. 10 tests nuevos
(`SearchPdfTextUseCaseTest`, `DocumentRepositoryTest`).
Detalle completo en [`docs/requirements/visor-biblioteca.md`](docs/requirements/visor-biblioteca.md).

### Módulo Conversión de documentos (2026-08-24)
Bug de configuración crítico corregido: `app/build.gradle.kts` excluía
`org.apache.xmlbeans` de las 3 dependencias de Apache POI, rompiendo en
silencio **toda** conversión que usara su modelo de objetos OOXML
(`XWPFDocument`, `WorkbookFactory`) con `NoClassDefFoundError` — incluyendo
Word→PDF y Excel→PDF, ya marcadas como "implementadas". Verificado con un
test que lee un .docx real hecho a mano: falla sin xmlbeans, funciona al
quitar la exclusión; `assembleDebug`/`checkDebugDuplicateClasses` confirman
que la exclusión no evitaba ningún conflicto real de build. Se agregaron
reglas ProGuard para que el build de release no elimine esas clases.
Además, 3 opciones del menú de conversión estaban enrutadas al use case
equivocado (Word→Texto y Excel→CSV entregaban un PDF; PPT→PDF fallaba
siempre) — corregidas con 3 use cases nuevos. El hallazgo de QA "muy pocas
opciones de conversión" resultó obsoleto en cuanto a cantidad (17
combinaciones ya declaradas); el problema real era el enrutamiento y el bug
de xmlbeans. 12 tests nuevos.
Detalle completo en [`docs/requirements/conversion.md`](docs/requirements/conversion.md).

**Compose UI Testing — flujo #2 (2026-08-25):** `ConverterScreenTest` cubre
Imagen→WebP con `ImageFormatUseCase` real (no mockeado), protegiendo de
verdad contra el crash de WEBP_LOSSLESS ya corregido. De paso se encontró y
corrigió una causa de inestabilidad al correr varias pruebas de Compose UI
juntas en el dispositivo real (animaciones del sistema activas →
`IllegalStateException: No compose hierarchies found`).
Detalle en [`docs/requirements/conversion.md` §7](docs/requirements/conversion.md).

**i18n del Convertidor — corregido (2026-08-26):** `ConversionType.label`
(campo hardcodeado en español, eliminado del enum) y todo el texto de
`ConversionSuccess.kt` ya pasan por `stringResource()`, con las 5 versiones
de `strings.xml` en paridad exacta. De paso se corrigió un bug funcional
más grave que el de i18n: `ConversionSuccess` es el componente de éxito de
las 17 conversiones, pero el ícono/MIME type/texto de botones asumían
siempre PDF o imagen — mostrando datos equivocados para las ~12
conversiones que no producen ninguno de los dos (Excel→CSV, Word→HTML,
etc.). Verificado en verde: `testDebugUnitTest`/`detekt`/`lintDebug` y
`connectedDebugAndroidTest` (6 pruebas) en el dispositivo real. Detalle en
[`docs/requirements/conversion.md` §8](docs/requirements/conversion.md).

### Módulo Escáner (2026-08-24)
Módulo con menos deuda real de lo que sugería la QA de mayo: la mayoría de
sus hallazgos resultaron obsoletos, no por corregirse hoy sino porque la
captura de documentos ahora delega por completo en Google ML Kit Document
Scanner (ya no hay cámara/recorte/filtros propios que auditar) y porque el
lector de QR con navegación a URL ya estaba implementado (probablemente
desde la reconstrucción de `QrScreen.kt` en el módulo Seguridad). Se
encontraron y corrigieron 2 bugs reales menores: guardar un PDF escaneado en
Descargas fallaba en silencio en Android 8/9 (sin ruta pre-`MediaStore`), y
la detección de tipo de contenido de QR era sensible a mayúsculas (un QR con
esquema en mayúsculas se clasificaba como texto plano, no como URL/email/
teléfono). 10 tests nuevos.
Detalle completo en [`docs/requirements/scanner.md`](docs/requirements/scanner.md).

### Módulo Ajustes + Premium (2026-08-24)
El límite diario de uso gratis para Herramientas PDF (requerimiento #16,
marcado "Pendiente" en este mismo documento) resultó ser un caso más del
patrón de esta sesión: la lógica ya existía completa en `DailyLimitManager`
(`canUsePdfTool`/`registerPdfTool`, con contador independiente por
herramienta) pero nunca se llamaba desde `PdfToolsViewModel` — el límite no
tenía ningún efecto real. Se conectó siguiendo el mismo patrón que ya usaba
Conversión, y se extrajo `DailyLimitDialog` a un componente compartido
(antes vivía duplicado y privado en `ConverterScreen.kt`). Además,
"Restablecer configuración" en Ajustes forzaba español sin importar el
idioma del dispositivo — mismo tipo de bug ya corregido hoy en TTS y
reconocimiento de voz (Modo Estudio) — corregido con
`LanguageManager.deviceDefaultLanguage()`. La compra Premium simulada
(requerimiento #18) se confirmó como placeholder ya documentado en el
propio código ("Fase 10 se conecta Play Billing real"), no un bug oculto —
requiere configuración de Play Console que el usuario debe hacer, así que
no se implementó en esta pasada. 11 tests nuevos. **Actualización
2026-08-25: RF-PREM-05 resuelto** — ver más abajo, sección "Play Billing
real conectado".
Detalle completo en [`docs/requirements/settings-premium.md`](docs/requirements/settings-premium.md).

### Módulo Estudio (2026-08-24)
La nota anterior de este documento (§3, requerimiento #11) daba por corregido
el guardado de notas solo porque la lista existía — al auditar su
serialización se encontraron 2 bugs reales que nadie había detectado: el
serializador manual reemplazaba silenciosamente cualquier comilla doble por
comilla simple en cada guardado (corrupción de datos del usuario), y un
`.reversed()` de más invertía el orden de las notas al recargar la pantalla.
Ambos corregidos reemplazando el serializador a mano por `org.json` (parte
del SDK de Android — se agregó `testImplementation("org.json:json:...")`
porque el stub de Android para unit tests no implementa `org.json.*`).
Además, la lectura en voz alta (TTS) forzaba español sin importar el idioma
del dispositivo — mismo bug ya corregido antes para el reconocimiento de voz
al dictar, pero que persistía del lado de lectura. `StudyScreen.kt` es la
única pantalla grande del proyecto sin `ViewModel` (todo el estado vive en
`remember {}`), documentado como deuda de arquitectura, no corregido en esta
pasada. 5 tests nuevos.
Detalle completo en [`docs/requirements/study.md`](docs/requirements/study.md).

### Avance por dimensión (actualizado 2026-08-29, auditoría completa de los 8 docs de requerimientos)
No hay un único "% completado" honesto — depende del eje. Recalculado contando
cada RF-XXX de los 8 módulos uno por uno (77 RF en total sobre los 7 módulos de
producto), leyendo directamente el estado marcado en cada doc de requisitos
(no la tabla anterior) para evitar arrastrar información desactualizada.

| Dimensión | Avance | Nota |
|---|---|---|
| Infraestructura y calidad base | ~94% | build estable, CI con emulador real corriendo los 3 flujos de Compose UI Testing, i18n completo, RF-SEC-08 (lifecycle) y UMP (consentimiento) implementados — sin cambios desde el 2026-08-27, no se tocó infraestructura en este período |
| Funcionalidad core (77 RF catalogados en los 7 módulos de producto) | **100% (77/77)** | Seguridad 15/15 (RF-SEC-09 cerrado), Ajustes+Premium 12/12 (RF-SET-07 cerrado 2026-08-29), Escáner 7/7 (RF-SCAN-06/07 cerrados 2026-08-29), Visor+Biblioteca 9/9 (RF-VIS-06/07/08 cerrados), Conversión 9/9 (RF-CONV-07/08/09 cerrados), Estudio 10/10 (RF-STU-08/09/10 cerrados 2026-08-29), Herramientas PDF 15/15 (RF-PDF-06 a 15 cerrados) — **con esto, el backlog de RF de los 7 módulos de producto queda en cero**; RF-SCAN-06/07 requirieron además dejar de pedirle a ML Kit el PDF interno (siempre "ganaba" sobre las páginas como imágenes) para que la edición de brillo/contraste/escala fuera alcanzable de verdad, ver `scanner.md` §8 |
| Documentación formal (HU con criterios de aceptación) | ~95% | 8 de 8 módulos con specs propias (los 7 de producto + deployment); todas con tabla de bugs de QA trazada como corregido/obsoleto/backlog; los 3 módulos recién cerrados (PDF, Visor+Biblioteca, Conversión) tienen sus HU con AC y verificación en dispositivo documentadas |
| Pruebas automatizadas | creciendo (228 tests, era 117 el 2026-08-27) | +111 tests en este período: cobertura nueva de `WordFormatDetectionTest`/soporte `.doc` legado (fixture real generado con Word vía COM), `ConverterViewModelBatchTest` (primer test de ViewModel del módulo Conversión), `PdfToTextUseCaseTest`/`PdfToWordUseCaseTest` (use cases que no tenían ningún test — encontraron 2 bugs reales preexistentes, ver abajo), y la suite completa de Herramientas PDF (firma, formularios, OCR, numeración, marca de agua, reordenar, comparar, censurar, recortar, editar texto) |
| Listo para publicar en Play Store | ~80% | Sin cambios desde el 2026-08-27 — todo lo que dependía de código ya está (firma de release, Play Billing real, UMP verificado); lo que falta sigue siendo la subida manual a Play Console, el AdMob App ID real, y ajustar `versionCode`/`versionName` antes de subir |

**Estimado global "producto listo para producción": ~80-85%** (era ~65-70%
el 2026-08-29 antes de cerrar Estudio/Ajustes/Escáner en la misma sesión).
El salto se explica por cerrar el backlog de RF de Estudio (RF-STU-08/09/10
— exportar notas, estadísticas, Pomodoro en segundo plano vía el primer
`Service` del proyecto), de Ajustes+Premium (RF-SET-07 — color de acento
personalizable, 6 opciones) y de Escáner (RF-SCAN-06/07 — brillo/contraste
y reescalado, con un cambio de flujo para que la edición fuera alcanzable
de verdad, ver `scanner.md` §8). **Con esto, el backlog de RF de los 7
módulos de producto queda en cero** — lo que queda ya no es "funcionalidad
sin implementar" sino únicamente los 3 pasos manuales de publicación que
solo el dueño del proyecto puede dar (subida a Play Console, AdMob real,
respaldo del keystore) — sin cambios ahí desde 2026-08-27.

**Dos bugs reales preexistentes encontrados y corregidos al escribir tests
para use cases que nunca tuvieron cobertura (no relacionados con las
features nuevas que los expusieron):**
- `PdfToTextUseCase` cerraba el `PdfDocument` de iText7 y **después** volvía a
  leer `numberOfPages` para construir el resultado — iText7 invalida el
  documento al cerrarlo, así que la conversión **PDF → Texto fallaba
  siempre**, en el 100% de los casos, no solo en el escenario que lo expuso
  (conversión por lotes). Corregido en RF-CONV-08, ver `conversion.md` HU-CONV-06.
- El mensaje de error "PDF sin texto extraíble" de ese mismo use case nunca
  se dispara en la práctica (agrega un encabezado a cada página antes de
  comprobar si el texto está en blanco) — encontrado al mismo tiempo,
  reportado por separado (fuera de alcance de la HU que lo encontró).

### Mejoras y funcionalidades candidatas a agregar a las HU
Por módulo, sin refinar aún — para retomar al planear el siguiente sprint:

- **Seguridad:** cambiar PIN sin borrar archivos (hoy solo "restablecer" destructivo),
  backup/exportación cifrada de la carpeta segura, registro de último acceso.
- **Herramientas PDF:** ✅ backlog de RF cerrado por completo 2026-08-29 (numeración,
  marca de agua, reordenar, comparar, censurar, recortar, editar texto, firma
  manuscrita, formularios, OCR avanzado — ver `pdf-tools.md`). Sin candidatas
  nuevas identificadas todavía.
- **Conversión:** ✅ backlog de RF cerrado por completo 2026-08-29 (`.doc` legado,
  conversión por lotes, PDF → Word con negrita/cursiva/tamaño reales — ver
  `conversion.md`). Candidata nueva, sin refinar: conversión en segundo plano
  para archivos grandes (no estaba en el backlog original de RF, es una mejora
  de UX sobre lo ya implementado).
- **Visor/Biblioteca:** ✅ backlog de RF cerrado por completo 2026-08-29
  (renombrar/eliminar desde el Visor, papelera de reciclaje, resaltado inline
  de búsqueda en PDF — ver `visor-biblioteca.md`). Sin candidatas nuevas
  identificadas todavía.
- **Estudio:** ✅ backlog de RF cerrado por completo 2026-08-29 (exportar notas,
  estadísticas de estudio, Pomodoro en segundo plano — ver `study.md` §9).
  Sin candidatas nuevas identificadas todavía.
- **Premium:** ~~conectar Play Billing real~~ ✅ 2026-08-25 (ver más abajo); límite diario no-premium ya estaba conectado (RF-PREM-02); pendiente: programa de referidos.
- **Ajustes:** ✅ backlog de RF cerrado por completo 2026-08-29 (color de acento
  personalizable, 6 opciones — ver `settings-premium.md` §12). Sin candidatas
  nuevas identificadas todavía.
- **Escáner:** ✅ backlog de RF cerrado por completo 2026-08-29 (brillo/
  contraste y reescalado sobre la imagen ya escaneada — ver `scanner.md` §8).
  Sin candidatas nuevas identificadas todavía.
- **Transversal:** estandarizar banner azul en todas las vistas (pedido repetido
  en QA — confirmado 2026-08-29 que el botón "volver" flotante junto al banner
  en Seguridad/Papelera es un patrón deliberado y consistente entre varias
  pantallas, no un defecto puntual; corregirlo es este ítem, no un fix
  aislado), accesibilidad (TalkBack, fuentes dinámicas), completar idiomas
  pendientes (ja/ko/zh/it/fr).

---

## 3. Requerimientos funcionales (fuente: mensaje del usuario, 2026-08-24)

| # | Requerimiento | Estado conocido |
|---|---|---|
| 1 | Visor universal: Word/Excel/PDF/img/texto, desde dispositivo, link, QR, correo, WhatsApp | Visor de PDF/imagen funciona bien; Word/Excel/PPT con inconvenientes por confirmar (no se tocó en esta pasada). Refinado con HU en [`docs/requirements/visor-biblioteca.md`](docs/requirements/visor-biblioteca.md) |
| 2 | Conversión: imágenes↔pdf/jpg/png/webp/bmp; pdf↔img/texto/word/html; word↔pdf/texto/html; ppt→pdf/texto | Las 17 combinaciones funcionan, incluyendo `.doc` legado además de `.docx` (RF-CONV-07), conversión por lotes (RF-CONV-08) y PDF→Word con negrita/cursiva/tamaño de fuente reales en vez de texto plano (RF-CONV-09) — backlog de RF cerrado por completo 2026-08-29. Ver [`docs/requirements/conversion.md`](docs/requirements/conversion.md) |
| 3 | Herramientas PDF: unir, dividir, comprimir, rotar, editar, firmar, marca de agua, numeración, detector de formularios, recortar, ordenar, proteger con contraseña, OCR avanzado | **Las 15 RF de este módulo están implementadas** (backlog cerrado 2026-08-29) — unir/dividir/comprimir/rotar/proteger/editar/firmar/marca de agua/numeración/formularios/recortar/ordenar/comparar/censurar/OCR. Ver [`docs/requirements/pdf-tools.md`](docs/requirements/pdf-tools.md) |
| 4 | Biblioteca con lista navegable, filtro por formato | Implementado, incluyendo pestañas dispositivo/app (ya existían, confirmado). Refinado con HU |
| 5 | Sub-menú por documento: abrir, favorito, renombrar, compartir, convertir, crear QR; favoritos visibles en biblioteca | Favorito consistente entre Visor/Biblioteca/Home; renombrar y eliminar desde el Visor implementados (RF-VIS-06, 2026-08-29), con papelera de reciclaje de 30 días en vez de borrado directo (RF-VIS-07) |
| 6 | Buscador en vistas relevantes | Funciona en Biblioteca; en el Visor busca por página con iText7 y navega entre coincidencias, con resaltado inline sobre el PDF renderizado (RF-VIS-08, 2026-08-29) — antes solo indicaba la página, ahora se ve el texto encontrado remarcado |
| 7 | Accesos rápidos | **Obsoleto el hallazgo de "aislados"** — confirmado que ya navegan a rutas reales (scanner/seguridad/estudio) |
| 8 | Acceso directo a abrir/convertir | Implementado (banner Home) — botón "Abrir" confirmado funcional (obsoleto el hallazgo de que no hacía nada) |
| 9 | Escanear/foto/leer QR/crear QR, guardado en biblioteca y recientes | Escáner funciona bien (delega captura a Google ML Kit); leer QR con URL/navegación y QR con contraseña ya estaban implementados (hallazgo obsoleto). Refinado con HU en [`docs/requirements/scanner.md`](docs/requirements/scanner.md) |
| 10 | Seguridad: contraseña para PDF y QR, carpeta segura con PIN/huella | Contraseña PDF implementada hoy (i18n); carpeta segura **corregida y endurecida** — copia y elimina el original al proteger, y avisa explícitamente si el borrado no fue posible en vez de reportar éxito falso (RNF-SEC-01, corregido 2026-08-24) |
| 11 | Modo estudio: lectura (con voz), notas (texto y voz), Pomodoro | Implementado y ya i18n; lista de notas guardadas existe, pero tenía 2 bugs reales de persistencia (corrupción de comillas, orden invertido) corregidos hoy. Refinado con HU en [`docs/requirements/study.md`](docs/requirements/study.md) |
| 12 | Ajustes: idioma, tema, almacenamiento, privacidad, tutorial, ayuda, compartir, calificar, restablecer, acerca de, premium | Implementado y refinado con HU — "restablecer" forzaba español sin importar el dispositivo, corregido. Ver [`docs/requirements/settings-premium.md`](docs/requirements/settings-premium.md) |
| 13 | Multilenguaje con default según ubicación geográfica de Play Store | **Resuelto 2026-08-24** con el idioma del dispositivo (no geografía de Play Store, que no es verificable desde el cliente) — antes el idioma por defecto de una instalación nueva era fijo en español. Ver [`docs/requirements/settings-premium.md`](docs/requirements/settings-premium.md) RF-SET-06 |
| 14 | Sección de beneficios plan de pago | Implementado (Premium screen) |
| 15 | Banner para no-premium, desaparece al pagar | Implementado (AdMob banner condicional) |
| 16 | Límite de uso de herramientas para no-premium | **Corregido hoy** — la lógica ya existía (`DailyLimitManager`) pero no estaba conectada a Herramientas PDF; ya funciona igual que en Conversión (5 conversiones + 3 usos por herramienta PDF al día, con anuncio recompensado para +1) |
| 17 | Mínima opción de uso garantizada sin pago | Resuelto junto con el #16 — 3 usos gratis por herramienta PDF, 5 conversiones/día |
| 18 | Restaurar compras / cancelar suscripción | **Resuelto 2026-08-25** — `BillingManager` conecta Play Billing real (RF-PREM-05); restaurar consulta compras reales vía `queryPurchasesAsync`. No verificable de punta a punta hasta crear los productos en Play Console (ver `docs/requirements/deployment.md`) |
| 19 | Tema personalizable (colores, texto, iconos) por el usuario | **No implementado** — hoy solo claro/oscuro/sistema |
| 20 | Mostrar almacenamiento usado + caché, con confirmación al borrar | Implementado en Settings (diálogo de almacenamiento) |
| 21 | Restablecer configuración por defecto | Implementado |
| 22 | Splash con logo de app y de empresa | Implementado (`SplashDocuSmartScreen` + `SplashMouthBlackScreen`) |
| 23 | Rediseñar logo de marca moderno para splash | Logo ya existe (ver §6), aplicado en splash |
| 24 | Seguridad, escalamiento y mantenimiento | En progreso — Fase 0 (estabilización) completada hoy, sigue Fase 1+ del roadmap técnico |
| 25 | Paleta de colores de marca | Documentada en §6, implementación en código por verificar contra el manual |

## 4. Requerimientos no funcionales (a formalizar con el usuario)
- Seguridad: cifrado real de carpeta segura y PDFs protegidos, no loguear datos sensibles.
- Rendimiento: límites de tiempo/memoria en conversión y escaneo.
- Usabilidad/i18n: 0 strings sin traducir antes de publicar (✅ logrado hoy), locale por defecto según Play Store.
- Compatibilidad: minSdk 26 (Android 8.0+).
- Anuncios: nunca en el visor, lectura, o conversión en curso; siempre fuera del contenido (regla de UX del manual de marca).
- Regla de oro UX (manual de marca): el usuario debe poder abrir o convertir un documento en **menos de 3 toques**.

---

## 5. Hallazgos de QA — Barrido de pruebas v1.0 + Mejoras pendientes v1.0.1

Bugs y mejoras identificados por el usuario en pruebas manuales
(pueden estar parcialmente corregidos ya — **verificar contra el código actual
antes de asumir que siguen vigentes**, varios documentos son de mayo 2026):

### Home / Visor / Biblioteca — refinado 2026-08-24, ver [`docs/requirements/visor-biblioteca.md`](docs/requirements/visor-biblioteca.md)
- ~~Botón "Abrir" del banner no genera ninguna acción.~~ **Obsoleto** — ya lanza `ACTION_OPEN_DOCUMENT` correctamente.
- ~~Accesos rápidos de scanner/seguridad/estudio no están atados a ninguna pantalla (aislados).~~ **Obsoleto** — ya navegan a rutas reales.
- ~~Favorito (corazón) en recientes/Biblioteca no persiste al salir de la vista.~~ **Corregido hoy** — causa raíz real: el Visor calculaba el id de favorito con prefijo `"file://"` para rutas absolutas, Biblioteca/Home lo hacían sin prefijo → mismo documento, dos claves distintas. `FavoritesRepository` en sí ya persistía bien.
- Botón "Inicio" de la bottom nav deja de responder después de ir a Convertir — no verificado, requiere prueba de navegación en vivo.
- Falta logo de marca en el banner azul (estandarizar en todas las vistas) — pendiente, ticket de UI transversal.
- ~~Visor: búsqueda no tiene función real.~~ **Corregido hoy** — el botón aparecía habilitado para PDF pero `PdfViewerContent` no recibía `searchQuery`; construido `SearchPdfTextUseCase` (iText7) + navegación entre páginas con coincidencias.
- **Bug real encontrado hoy (no reportado en la QA):** "eliminar" en Biblioteca/Home solo filtraba la lista en memoria, nunca borraba el archivo real — reaparecía al recargar. Corregido: `DocumentRepository.deleteDocument()` borra de verdad (archivo de la app o `ContentResolver` para MediaStore) y solo se quita de la lista si el borrado fue exitoso.
- ~~Visor: no permite renombrar ni eliminar desde el visor.~~ **Corregido 2026-08-29** — RF-VIS-06, con papelera de reciclaje de 30 días en vez de borrado directo (RF-VIS-07).
- Visor: margen superior falla, el PDF "se pierde" arriba — no verificado, requiere prueba visual.
- Word/Excel/texto/PowerPoint presentan inconvenientes (solo PDF/imagen confiables) — no verificado en esta pasada.
- ~~Biblioteca: falta discriminar "archivos creados por la app" vs. "archivos del dispositivo".~~ **Obsoleto** — ya implementado (pestañas `LibraryTab.DEVICE`/`APP_FILES`).
- Tarjetas de favoritos con tamaños inconsistentes en el carrusel horizontal — ajuste visual, no funcional, fuera de alcance de esta pasada.
- Formatos en carrusel esconden opciones — sugerido: grilla en vez de carrusel — pendiente.
- **Bug real encontrado 2026-08-30:** "Eliminar definitivamente" en la Papelera fallaba en silencio para fotos de MediaStore no creadas por la app (`RecoverableSecurityException`, scoped storage) y aun así borraba la entrada de la papelera — el archivo "resucitaba" en Biblioteca/Recientes. Corregido con el flujo de confirmación de sistema (`MediaStore.createDeleteRequest`/`RecoverableSecurityException`) + no limpiar la papelera hasta confirmar el borrado real. De paso se agregó "Borrar todo" (faltaba por completo). Ver [`docs/requirements/visor-biblioteca.md`](docs/requirements/visor-biblioteca.md) §17.
- **Bug real encontrado 2026-08-30:** abrir un archivo desde Drive/WhatsApp con la app ya corriendo creaba una segunda instancia de `MainActivity` (sin `launchMode` declarado) — al volver atrás quedaba una copia de Inicio "pegada" en vez de cerrar la app. Corregido con `launchMode="singleTask"` + manejo reactivo de `onNewIntent()`. Ver [`docs/requirements/visor-biblioteca.md`](docs/requirements/visor-biblioteca.md) §18.

### Convertidor — refinado 2026-08-24, ver [`docs/requirements/conversion.md`](docs/requirements/conversion.md)
- ~~Muy pocas opciones de conversión por formato (2-3 cuando el requerimiento pide más).~~ **Obsoleto en cantidad** — hay 17 combinaciones ya declaradas y visibles. El problema real: 3 opciones enrutaban al use case equivocado (entregaban el formato incorrecto) y 2 más ya "implementadas" fallaban al ejecutarse — ver bug real abajo.
- **Bug real encontrado hoy (más grave que lo buscado, no reportado en la QA):** "Word → PDF" y "Excel → PDF" fallaban con `NoClassDefFoundError` al leer un documento real — `app/build.gradle.kts` excluía `org.apache.xmlbeans` de las dependencias de Apache POI, rompiendo en silencio cualquier conversión que usara su modelo de objetos OOXML. Corregido quitando la exclusión + reglas ProGuard nuevas para release.
- "Word → Texto" entregaba un PDF, "Excel → CSV" entregaba un PDF, "PPT → PDF" fallaba siempre — los 3 estaban enrutados al use case equivocado en `ConverterViewModel.convert()`. Corregidos con 3 use cases nuevos (`WordToTextUseCase`, `ExcelToCsvUseCase`, `PptToPdfUseCase`).
- Banner de publicidad no se visualiza en esta pantalla — no reproducido por lectura de código (conectado igual que en pantallas que sí funcionan), requiere verificación visual.
- Vista en carrusel se ve vacía — sugerido grilla/lista — no verificado, requiere prueba visual.

### Herramientas PDF — refinado 2026-08-24, ver [`docs/requirements/pdf-tools.md`](docs/requirements/pdf-tools.md)
- ~~Dividir PDF no funciona — genera el mismo PDF sin dividir.~~ **Obsoleto** — 4 tests con PDFs reales confirman que el rango extraído es correcto; no se reprodujo contra el código actual.
- ~~Comprimir PDF no indica dónde queda guardado, no ofrece compartir/descargar.~~ **Obsoleto** — `ToolSuccessCard` ya cubre nombre, tamaño y ambas acciones.
- **Rotar PDF**: la vista previa no refleja la rotación real en grados. Mitigado indirectamente — la rotación real ahora se escribe con `PdfPage.setRotation()` (metadato estándar de PDF), no con una matriz de bitmap manual.
- Nombre de archivo antepone "DocuSmart_" — confirmado como decisión de marca deliberada, estandarizado en las 4 herramientas.
- **Bug real encontrado hoy (no reportado en la QA):** Unir y Rotar rasterizaban cada página a imagen antes de reconstruir el PDF, destruyendo todo el texto seleccionable/buscable del resultado. Corregido migrando ambos a iText7 (`copyPagesTo`/`setRotation`), igual que ya hacían Dividir y la contraseña de PDF.
- ~~Faltan (backlog documentado, RF-PDF-06 a RF-PDF-15): numeración, marca de agua, reordenar/eliminar página, recorte, editar, firma, formularios, comparar, censurar, OCR avanzado.~~ **Corregido 2026-08-29** — las 10 quedaron implementadas, backlog de RF de este módulo en cero.
- Pendiente: i18n de las 4 pantallas (aún en español fijo, mismo patrón que Seguridad/QR/Estudio antes de corregirse); selector de archivo desde la Biblioteca de la app (hoy solo desde el dispositivo).

### Seguridad
- Banner con botón "volver" mal ubicado (reduce tamaño del banner) — sugerido: breadcrumb.
- Falta logo corporativo en el banner.
- ~~**Bug crítico:** un archivo "protegido" sigue siendo accesible directamente desde su ruta original.~~ **Corregido** (causa raíz: `moveToSecure()` no se llamaba desde el ViewModel) **y endurecido 2026-08-24** — `moveToSecure()` ahora también propaga si el borrado del original realmente ocurrió (antes reportaba éxito aunque `File.delete()` fallara), y la UI avisa en ese caso en vez de mentir. Ver [`docs/requirements/security.md`](docs/requirements/security.md).
- Selector de archivo a proteger no ofrece elegir desde la biblioteca de la app, solo desde el dispositivo.
- Falta opción explícita de encriptar/quitar contraseña de archivo individual.

### Escáner — refinado 2026-08-24, ver [`docs/requirements/scanner.md`](docs/requirements/scanner.md)
- Funciona bien (captura, recorte, rotar, filtros, aplicar, guardar/compartir) — confirmado: se delega por completo a Google ML Kit Document Scanner, no a una cámara/recorte propios.
- ~~Miniatura de filtros no refleja la imagen real capturada.~~ **Obsoleto por cambio de arquitectura** — no existe una miniatura de filtro propia; la captura entera es de Google ML Kit.
- ~~Faltan: lector de QR con navegación a URL, QR con contraseña compartible.~~ **Obsoleto** — ambos ya implementados (el segundo, en el módulo Seguridad).
- Faltan (backlog, RF-SCAN-06/07): escalar imagen, ajuste de brillo/contraste — Google ML Kit no expone estos controles, requeriría un paso de edición propio.
- **Bug real encontrado hoy (no reportado en la QA):** guardar un documento escaneado en modo "Documento" en Descargas fallaba en silencio en Android 8/9 (`savePdfUriToDownloads` sin implementación pre-Q). Corregido.
- **Bug real encontrado hoy (no reportado en la QA):** detección de tipo de contenido de QR sensible a mayúsculas — un QR con esquema en mayúsculas (`HTTPS://...`) se clasificaba como texto plano en vez de URL. Corregido.

### Estudio — refinado 2026-08-24, ver [`docs/requirements/study.md`](docs/requirements/study.md)
- Documento de mayo indica que faltaba guardado/lista de notas — **parcialmente correcto pero incompleto**: la lista de notas guardadas sí existe (título/texto/fecha, guardar/eliminar), pero al auditar su serialización se encontraron 2 bugs reales que nadie había detectado (ver abajo).
- **Bug real encontrado hoy (no reportado en la QA):** el serializador manual de notas reemplazaba silenciosamente cualquier comilla doble por comilla simple en cada guardado — corrompía el contenido de las notas del usuario. Corregido reemplazando el serializador a mano por `org.json` (parte del SDK de Android).
- **Bug real encontrado hoy (no reportado en la QA):** el orden de las notas se invertía al recargar la pantalla de Estudio (quedaban más vieja primero, no más nueva primero). Corregido quitando un `.reversed()` de más.
- **Bug real encontrado hoy (no reportado en la QA):** la lectura en voz alta (TTS) forzaba español sin importar el idioma configurado del dispositivo — mismo bug ya corregido antes para el reconocimiento de voz al dictar notas, pero que persistía del lado de lectura. Corregido.
- Lectura se ve como texto plano — mejorar presentación visual (no abordado, es visual no funcional).

### Ajustes + Premium — refinado 2026-08-24, ver [`docs/requirements/settings-premium.md`](docs/requirements/settings-premium.md)
- **Bug real encontrado hoy (no reportado en la QA):** "Restablecer configuración" forzaba español sin importar el idioma del dispositivo. Corregido con `LanguageManager.deviceDefaultLanguage()`.
- **Hueco real encontrado hoy (requerimiento #16, no un bug de QA pero sí de código):** el límite diario de uso gratis para Herramientas PDF existía por completo en `DailyLimitManager` pero nunca se llamaba desde `PdfToolsViewModel` — no tenía ningún efecto real. Corregido.
- ~~Idioma: falta detección automática al primer inicio.~~ **Resuelto 2026-08-24** — `loadLanguage()` ahora usa el idioma del dispositivo como respaldo cuando no hay ninguno guardado (RF-SET-06). Sigue pendiente ampliar el catálogo de idiomas (agregar japonés, coreano, mandarín, italiano, francés — **es/en/de/pt/ru ya están, faltan ja/ko/zh/it/fr para el pedido completo**) — backlog, no abordado en esta pasada.
- Falta personalización de colores/estilos por el usuario (banner, botones, iconos, nav bar) — backlog.
- ~~Compra Premium simulada.~~ **Resuelto 2026-08-25** — Play Billing real conectado (`BillingManager`), ver sección dedicada más abajo.
- **Bug crítico encontrado 2026-08-30:** la app se cerraba al entrar a Premium (y de forma intermitente a Ajustes) — `IllegalStateException: Must be called on the main UI thread` al cargar un anuncio desde `BillingManager.restorePurchases()` (corre en `Dispatchers.IO`). Segunda instancia de la misma familia de bug que la fila de arriba (§11 de `settings-premium.md`); esta vez corregido en `AdManager` mismo (despacha internamente al hilo principal) en vez de en el llamador. Ver [`docs/requirements/settings-premium.md`](docs/requirements/settings-premium.md) §13.

### General / transversal
- Banner de anuncios: ubicarlo consistente (arriba antes del banner azul, o abajo cerca de la nav bar) en todas las vistas, y ocultarlo por completo para usuarios premium.
- Carga de anuncios no perezosa: `AdManager.initialize()` carga interstitial + video recompensado de inmediato al arrancar la app (hallazgo de `sentinel_report.json`, mayo). Confirmado 2026-08-25 que no es solo un tema de rendimiento — en el emulador de pruebas, esa carga inicializó el decoder de video y crasheó el proceso completo. Cargar bajo demanda (justo antes de mostrar el anuncio) evitaría ambos problemas.
- Estandarizar el banner azul (logo + título) en todas las pantallas.
- **HU creada 2026-08-30** para ampliar Compose UI Testing a todos los flujos de la app (pedido explícito del usuario, ligado a la condición `new_coverage` 0% del Quality Gate de SonarCloud) — inventario completo de las 18 pantallas, priorización y advertencia técnica sobre qué hace falta en CI para que esto realmente mueva la métrica de Sonar. Ver [`docs/requirements/compose-ui-testing.md`](docs/requirements/compose-ui-testing.md).
- **Primer lote de Compose UI Testing implementado 2026-08-31** (RF-QA-01, filas 8-9 de la tabla de flujos): `LibraryScreenTest` (cambiar pestaña Dispositivo/Mis archivos, filtrar por categoría, buscar) y `TrashScreenTest` (restaurar, eliminar uno, "Borrar todo"). Nueva dependencia `androidTestImplementation("androidx.test:rules:1.6.1")` para `GrantPermissionRule` (Biblioteca verifica un permiso real de almacenamiento antes de cargar, no mockeable desde el ViewModel). **Hallazgo real durante la verificación**: filtrar por "PDF" era ambiguo porque el chip de categoría y el badge de tipo del documento muestran el mismo texto — se resolvió probando con "Imagen" (chip "Imágenes" plural vs. badge "Imagen" singular, sin colisión) en vez de forzar un matcher más complejo; PDF/Word/Excel/ZIP quedan con el mismo riesgo si se cubren en lotes futuros. Gauntlet completo verde, incluidas las 2 pruebas antes flagueadas como flaky (pasaron limpio, consistente con carga transitoria del dispositivo). Ver [`docs/requirements/compose-ui-testing.md`](docs/requirements/compose-ui-testing.md) §3.
- **Segundo lote de Compose UI Testing implementado 2026-08-31** (RF-QA-01, filas 6-7): `HomeScreenTest` (ver recientes, tocar favorito, tocar un acceso rápido invoca su callback de navegación, eliminar mueve a la papelera y desaparece de la lista). **Hallazgo real de organización del proyecto**: existe un `HomeViewModel` huérfano en el paquete `converter.presentation` (datos mock, sin ninguna referencia real, confirmado con `grep`) — no es el que usa `HomeScreen` (el real vive en `home.presentation`); se flagueó aparte para eliminarlo. Gauntlet completo verde (16/16 tests instrumentados). Quedan pendientes 3 flujos Alta más (Visor-búsqueda, Herramientas PDF, Ajustes) para lotes futuros. Ver [`docs/requirements/compose-ui-testing.md`](docs/requirements/compose-ui-testing.md) §3.
- **Tercer lote de Compose UI Testing (prioridad Alta) implementado 2026-08-31** (RF-QA-01, filas 4/10/15 — cierra las 7 filas Alta de la tabla): `ViewerSearchTest` (búsqueda en el Visor con un PDF real de 2 páginas generado con iText7, navegación entre coincidencias con vuelta de módulo), `SettingsScreenTest` (cambiar tema/acento/idioma con `ThemeManager`/`LanguageManager` reales aislados de `SharedPreferences` reales, y "Restablecer configuración" vuelve todo a default), `PdfToolsScreenTest` (elegir "Rotar PDF", ejecutar con `RotatePdfUseCase` real sobre un PDF real, ver el mensaje de éxito). **Hallazgo real (bug de test, no de producción)**: el clic sobre "Rotar PDF 90°" no tenía ningún efecto — sin excepción, sin logs, sin cambio de estado. Diagnosticado instrumentando temporalmente `PdfToolsViewModel.execute()` con `Timber.d` y volcando el árbol de semántica a logcat (`printToLog`): el botón sí estaba compuesto (`t=3020px`), pero el viewport visible de la `LazyColumn` llegaba solo hasta `b=2274px` en una pantalla de 2400px de alto — `performClick()` dispara un toque sintético en las coordenadas reales del nodo, y esas coordenadas caían fuera de lo visible, así que el toque no llegaba a nada. Corregido con `.performScrollTo().performClick()`. Gauntlet completo verde: `connectedDebugAndroidTest` (20/20 tests instrumentados), `detekt`, `lintDebug`, `testDebugUnitTest`. Ver [`docs/requirements/compose-ui-testing.md`](docs/requirements/compose-ui-testing.md) §3.
- **Cuarto lote de Compose UI Testing (primero de prioridad Media) implementado 2026-08-31** (RF-QA-01, filas 5 y 13): `ViewerRenameDeleteTest` (renombrar actualiza el nombre en la barra superior, eliminar mueve a la papelera y cierra el Visor — `DocumentRepository`/`TrashRepository` mockeados completos, no reales, por requerir 5 dependencias propias solo para renombrar). `QrCreatorScreenTest` (generar QR de URL sin contraseña, generar QR de texto con contraseña y ver la insignia "Protegido") — cubre solo crear QR, no leer: `QrReaderScreen` depende 100% de CameraX + ML Kit en vivo, sin código propio que testear, mismo criterio ya usado para el Escáner (fila 12), por eso la fila 13 queda marcada 🟡 parcial en la tabla. **Hallazgo real, mismo patrón que en el lote anterior**: la insignia "Protegido" quedaba fuera del viewport visible tras generar el QR — acá la pantalla usa `Column` con `verticalScroll` (no `LazyColumn`), así que `.performScrollTo()` directo sobre el nodo alcanzó, sin necesitar el patrón más elaborado de `SettingsScreenTest`. Gauntlet completo verde: `connectedDebugAndroidTest` (24/24), `detekt`, `lintDebug`, `testDebugUnitTest`. Ver [`docs/requirements/compose-ui-testing.md`](docs/requirements/compose-ui-testing.md) §3.
- **Quinto lote de Compose UI Testing implementado 2026-09-01** (RF-QA-01, fila 14 — Estudio): `StudyScreenTest` (guardar nota → aparece en la lista → eliminarla vacía la lista; Pomodoro iniciar → pausar cambia el botón y la etiqueta "en progreso"/"pausado"). `StudyNotesStorage` (SharedPreferences reales) aislado con el mismo `IsolatedPrefsContext` de `SettingsScreenTest`; `PomodoroEngine` (singleton real, no mockeable, con servicio en primer plano real) se resetea explícitamente antes y después de la prueba para no dejar el servicio corriendo ni filtrar estado a otras pruebas. **Hallazgo real**: entrar directo a la pestaña Pomodoro dispara la solicitud real del permiso `POST_NOTIFICATIONS` (Android 13+) — el diálogo real del sistema tapa la Activity y el árbol de semántica deja de encontrarse por completo (`IllegalStateException: No compose hierarchies found`), ya que los taps no llegan a diálogos de permisos del sistema en este dispositivo (mismo hallazgo de sesiones anteriores). Corregido concediendo el permiso de antemano con `GrantPermissionRule`, mismo patrón que `LibraryScreenTest`. Gauntlet completo verde: `connectedDebugAndroidTest` (26/26), `detekt`, `lintDebug`, `testDebugUnitTest`. Ver [`docs/requirements/compose-ui-testing.md`](docs/requirements/compose-ui-testing.md) §3.
- **Corrección retroactiva de robustez de CI implementada 2026-09-01**: `ViewerSearchTest`, `ViewerRenameDeleteTest`, `QrCreatorScreenTest` y `StudyScreenTest` (ya fusionadas) no usaban `forceLocale` a diferencia de las demás 7 pruebas del proyecto — pasaban en este dispositivo porque su idioma real es español (`es-US`), pero el emulador de CI arranca en inglés, así que las aserciones sobre texto en español habrían fallado ahí sin que nada en este dispositivo lo evidenciara. Corregido envolviendo las 4 con `forceLocale`; `QrCreatorScreenTest`/`StudyScreenTest` además necesitaron reproveer `LocalActivityResultRegistryOwner`/`LocalOnBackPressedDispatcherOwner` por usar `rememberLauncherForActivityResult`. Gauntlet completo reverificado en verde: `connectedDebugAndroidTest` (26/26 — un fallo transitorio de Compose no se repitió en una segunda corrida), `detekt`, `lintDebug`, `testDebugUnitTest`. Ver [`docs/requirements/compose-ui-testing.md`](docs/requirements/compose-ui-testing.md) §3.
- **Sexto lote de Compose UI Testing implementado 2026-09-01** (RF-QA-01, filas 17-18 — cierra el backlog de prioridad Baja): `OnboardingScreenTest` (recorrer las 4 slides hasta "¡Empezar!" marca completado y navega; "Saltar" también) y `SplashScreensTest` (ambas pantallas de splash muestran el logo y navegan automáticamente, aprovechando que `ANIMATOR_DURATION_SCALE=0` en el dispositivo salta directo a los valores finales de animación). Gauntlet completo verde: `connectedDebugAndroidTest` (30/30), `detekt`, `lintDebug`, `testDebugUnitTest`. **Fila 16 (Premium) quedó bloqueada** tras una investigación exhaustiva: `composeRule.waitUntil(...)` nunca resuelve al esperar texto de `PremiumScreen` pese a que el contenido correcto está confirmado presente y estable en el árbol de semántica (vía `printToLog` y sondeos manuales) — se descartó, uno por uno: construcción del ViewModel dentro vs. fuera de `setContent`, presencia/ausencia de `forceLocale`, compilación limpia completa, archivo corrupto (borrado y recreado), colisión de paquete con la producción, y nombre calificado vs. import implícito. Una reproducción mínima pasó UNA vez pero volvió a fallar en corridas posteriores, apuntando a una condición de carrera genuina de Compose Testing (misma familia que un `performMeasureAndLayout` visto como flake transitorio en otro test), con tasa de reproducción mucho más alta en la composición particular de `PremiumScreen`. No se dejó un test conocidamente inestable en el repo; documentado como bloqueado para retomar con una pista nueva. Con esto, del backlog Media/Baja original solo quedan sin cubrir la fila 11 (Contraseña PDF, pendiente de decisión sobre Espresso-Intents) y la fila 16 (bloqueada). Ver [`docs/requirements/compose-ui-testing.md`](docs/requirements/compose-ui-testing.md) §3.
- **Plan "De por vida" eliminado + bug crítico de producción encontrado y corregido 2026-09-01** (pedido explícito del usuario): `PremiumRepository` queda solo con Mensual/Anual; `BillingManager` se simplificó quitando toda la ruta `ProductType.INAPP` (constante, consulta de producto, caso especial de offer token, consulta de restauración). **Hallazgo real crítico**: al simplificar `BillingManager` se quitó por error `.enableOneTimeProducts()` de `PendingPurchasesParams` — Play Billing 9.1.0 lo exige obligatoriamente pese al nombre, y sin él el constructor de `BillingManager` (`@Singleton` de Hilt) lanzaba `IllegalArgumentException`, **crasheando la app real cada vez que un usuario tocaba "Premium"**. Se detectó por verificación manual en el dispositivo real (no por tests automatizados — todo intento de probar `PremiumScreen` mockea `BillingManager` por completo, así que ninguna prueba llega a ejecutar su constructor real). Corregido y reverificado visualmente: Premium abre sin crashear, solo Mensual/Anual. De paso, se intentó una segunda vez la fila 16 del backlog de Compose UI Testing con una técnica distinta (reloj de composición pausado + avance manual de frames) — evitó el `ComposeTimeoutException` pero la corrida completa tardó 53 minutos (inviable para CI), así que la fila sigue bloqueada. Gauntlet verde: `compileDebugKotlin`, `detekt`, `lintDebug`, `testDebugUnitTest`, `connectedDebugAndroidTest` (30/30). Ver [`docs/requirements/settings-premium.md`](docs/requirements/settings-premium.md) §8.1 y [`docs/requirements/compose-ui-testing.md`](docs/requirements/compose-ui-testing.md).
- **Integración de Compose UI Testing con la cobertura de Sonar implementada 2026-09-01** (los 3 pasos que antes estaban documentados como pendientes, a pedido explícito del usuario): `enableAndroidTestCoverage = true` en el build type `debug` (`app/build.gradle.kts`) para que `connectedDebugAndroidTest` también genere datos Jacoco; `jacocoTestReport` ahora depende de `testDebugUnitTest` Y `connectedDebugAndroidTest`, fusionando ambas fuentes de `executionData` en un solo XML; `sonarcloud.yml` ganó un emulador (mismo AVD que `ci.yml`) para poder correr las pruebas instrumentadas antes del análisis (`timeout-minutes` subido de 30 a 60). **Verificado localmente antes de tocar CI** con el dispositivo real conectado: clases que antes mostraban 0% por depender de `Context`/Android framework (sin unit tests propios) ahora muestran cobertura real de Compose UI Testing — `ViewerViewModel` 156/308 líneas, `PdfToolsViewModel` 64/290 — y el reporte fusionado completo llegó a 42.5% de cobertura de línea global. Costo aceptado explícitamente: el job de Sonar ahora también paga el emulador (~15-20 min extra), duplicando ese trabajo con `ci.yml` (no se intentó compartir el job entre workflows, requeriría `workflow_run`). Ver [`docs/requirements/compose-ui-testing.md`](docs/requirements/compose-ui-testing.md) §4.
- **Hallazgo real crítico de proceso, 2026-09-01: el job `instrumented-tests` de `ci.yml` llevaba fallando en CI desde el 2026-08-30, sin que nadie lo notara** — toda esta sesión verificó "gauntlet verde" solo en el dispositivo real (Motorola Edge 30 Neo), nunca revisando `gh run list` hasta que el usuario preguntó por qué la cobertura de Sonar seguía en cero después del paso anterior. Historial real: pasaba en verde desde 2026-08-26 (con 3 flujos); volvió a fallar el 2026-08-30 solo por `ConverterScreenTest` (ya documentada como intermitente); al agregar ~27 pruebas más durante los días siguientes, la tasa de fallas creció a 14-15 de 30 pruebas fallando de forma consistente en el emulador de CI (`ComposeTimeoutException`/"Failed to inject touch input"), pese a pasar de forma confiable en el dispositivo real. Se descartó que `disable-animations: true` no se aplicara (sí se confirmó en el log). Diagnóstico: la imagen `google_apis` del emulador trae SystemUI/Gmail/Maps/GMS de fondo compitiendo por los 2 vCPU del runner — a mayor cantidad de pruebas, mayor contención. Se probó cambiar a `target: aosp_atd` (imagen que Google diseñó para instrumentación en CI, sin esos componentes) en `ci.yml` y `sonarcloud.yml`, disparado manualmente en una rama de prueba sin tocar `main` — **descartado con datos reales de CI: fallan exactamente las mismas 14-15 pruebas, mismo orden, mismos tiempos**, tanto con `google_apis` como con `aosp_atd`. Se probó también, a pedido explícito del usuario, migrar `createAndroidComposeRule` v1→v2 (`StandardTestDispatcher` en vez de `UnconfinedTestDispatcher`) como piloto en `HomeScreenTest` — **también descartado con datos reales de CI: mismo patrón de fallas**, revertido. Diagnóstico revisado: las pruebas que fallan dependen todas de un `coEvery` de MockK sobre una función `suspend` del repositorio resolviendo dentro de `viewModelScope`; las que no dependen de ningún mock `suspend` pasan siempre. Pista para retomar: `mockk/mockk#766` y `#941` documentan `coEvery` bloqueándose específicamente en pruebas de instrumentación Android (no en JVM). Se consiguió evidencia real de bajo nivel agregando `printToLog()` justo en el momento del cuelgue (requirió dos fixes reales de infraestructura de CI: el import de `waitUntil` no existe en esta versión de compose-ui-test, y `android-emulator-runner` ejecuta cada línea de su `script` como un `sh -c` separado, así que el volcado de logcat tuvo que ir en una sola línea con `;`). El árbol de UI capturado muestra que la pantalla queda congelada en el estado *previo* a la acción esperada (sin spinner, sin error) en las 4 pantallas capturadas -- se probó inyectar un `DispatcherProvider` de prueba (`Dispatchers.Main.immediate`, sin thread real) en `ConverterScreenTest` para descartar un cuelgue de corrutinas: **descartado con datos reales de CI, árbol de UI idéntico byte por byte al de antes del fix**. **Confirmado con certeza (no solo sospecha)**: un único `Timber.d` al inicio de `ConverterViewModel.convert()` (el método atado al botón "Convertir a WebP") nunca aparece en el logcat de CI -- `performClick()` de Compose UI Testing no logra entregar el evento de touch real a través de la ventana para estas 7 pantallas puntuales en este emulador de CI (mismo mecanismo que el `AssertionError: Failed to inject touch input` ya visto en `ViewerRenameDeleteTest`, solo que acá sin excepción explícita). Se descartaron con evidencia real de CI: `aosp_atd`, v1/v2 de `createAndroidComposeRule`, timeout insuficiente, y corrutinas/dispatchers (mockeadas y reales) -- ningún cambio de código movió la aguja. El porqué exacto de la falla de inyección de touch queda sin resolver, cerrado por esta sesión. El job `instrumented-tests` queda con 14-15/30 pruebas fallando de forma conocida y documentada en CI, sin afectar la confianza de esas mismas pruebas verificadas en el dispositivo real. Ver `docs/requirements/deployment.md` §3 para el detalle completo del historial.
- **Backlog de mejoras/UX catalogado 2026-08-30** (sin ejecutar todavía, a la espera de priorización del usuario, salvo el ítem de splash/íconos ya implementado): 12 pedidos nuevos (atajos de Convertir/QR desde un archivo, captura por cámara, grilla de accesos rápidos, banner azul uniforme con "Volver", banner de anuncios consistente, ampliar Personalización en Ajustes, botón Papelera inconsistente, etc.) más una revisión UX heurística propia con hallazgos adicionales y preguntas abiertas de producto. Ver [`docs/requirements/backlog-mejoras-ux-2026-08-30.md`](docs/requirements/backlog-mejoras-ux-2026-08-30.md).
- **Rediseño de splash screens + ícono implementado 2026-08-30**: el usuario entregó handoffs de diseño para el splash de mouthblack (círculo mordido) y de DocuSmart (mira + línea de escaneo revelando el documento), más los vectores del ícono adaptativo de la app. Se integraron ambos sobre la navegación existente (sin adoptar la SplashScreen API que sugerían los handoffs), se reemplazó el ícono de la app (antes era la plantilla verde sin personalizar de Android Studio) y el logo del banner azul, y se agregó soporte de accesibilidad (`ANIMATOR_DURATION_SCALE`). Verificado en dispositivo real sin crashes. Pendiente de seguimiento: integrar las tipografías de marca reales (hoy usa las del sistema). Ver [`docs/requirements/backlog-mejoras-ux-2026-08-30.md`](docs/requirements/backlog-mejoras-ux-2026-08-30.md) §13.
- **Tamaño de letra ajustable implementado 2026-08-31** (HU-UX-05, ítem #5): nuevo `FontScale` (Normal/Grande/Muy grande) en `ThemeManager`, aplicado como multiplicador sobre `DocuSmartTypography` (nuevo `Typography.scaledBy()`, escala los 15 estilos, no solo los 12 explícitos) vía un nuevo parámetro `fontScale` en `DocuSmartTheme`. Nuevo ítem en Ajustes → Personalización, con reset a Normal incluido. **Hallazgo real corregido durante la verificación**: las 3 pestañas de Biblioteca (Dispositivo/Mis archivos/Papelera) truncaban con "…" desde el nivel Grande — se resolvió cambiando su layout de "ícono al lado del texto" a "ícono arriba, texto abajo" (decisión de diseño confirmada con el usuario antes de tocar un componente ya afinado en una iteración previa). Verificado en dispositivo real en las 4 pantallas auditadas (Home/Biblioteca/Ajustes/Visor) en los 3 niveles, persistencia y reset confirmados. Trade-off aceptado y documentado: a "Muy grande", la barra de navegación inferior y el badge de tipo de documento envuelven texto a 2 líneas (sin truncar) en vez de mantenerse en 1 línea — no se tocó por ser componentes compartidos por más pantallas. HU-UX-06 (color por elemento, la otra mitad de este ítem del backlog) sigue sin empezar, pendiente de decisión de diseño. Ver [`docs/requirements/backlog-mejoras-ux-2026-08-30.md`](docs/requirements/backlog-mejoras-ux-2026-08-30.md) §7.
- **Captura con cámara para Convertir implementada 2026-08-31** (HU-UX-03, ítem #2): el botón "Capturar con cámara" aparece junto al selector de archivo en Convertir, solo para origen Imagen (la cámara de ML Kit siempre devuelve páginas como imagen, nunca PDF directo) y solo antes de tener ya un archivo elegido. Reutiliza `GmsDocumentScannerOptions` extraído a un nuevo `DocumentScannerLauncher.kt` compartido con `ScannerScreen.kt` (sin duplicar la configuración). Verificado en dispositivo real: el botón no aparece en categorías no-Imagen, cancelar la captura deja el selector sin cambios, y una captura completa queda adjunta automáticamente y se convirtió con éxito de punta a punta. Ver [`docs/requirements/backlog-mejoras-ux-2026-08-30.md`](docs/requirements/backlog-mejoras-ux-2026-08-30.md) §4.
- **Atajos "Convertir"/"Crear QR" desde un archivo ya elegido implementados 2026-08-31** (HU-UX-01/02, ítems #1 y #9): el menú "⋮" de Biblioteca/Recientes y el menú de opciones del Visor ganaron ambas acciones. "Crear QR" llega directo con el archivo adjunto (imagen o documento, saltando el picker); "Convertir" precarga el archivo en `ConverterViewModel` (nuevo `preloadFile()`) para que quede adjunto en cuanto el usuario elija un tipo de conversión de la misma categoría, sin tener que volver a buscarlo — como un mismo origen (p.ej. PDF) tiene varios destinos posibles, no se puede saltar directo a un `ConversionType` como hace el acceso rápido "Img→PDF". Verificado en dispositivo real: generación de QR y conversión completa (Imagen→PDF) funcionando de punta a punta desde el atajo, sin regresiones en el flujo manual. Ver [`docs/requirements/backlog-mejoras-ux-2026-08-30.md`](docs/requirements/backlog-mejoras-ux-2026-08-30.md) §3.
- **Banner de anuncios completado en las 6 pantallas de HU-UX-07, 2026-08-31**: Resultado de escaneo (reusa `converterViewModel.adManager`), Menú de Seguridad (`SecurityMenuViewModel` nuevo), Visor (banner agrupado con la barra de controles inferior, se oculta/muestra junto con ella), Estudio (`StudyViewModel` nuevo, banner visible en las 3 pestañas), Lector QR y Creador QR (`QrViewModel` nuevo compartido entre ambas pantallas; en el Lector el banner solo aparece tras detectar un código, nunca durante la vista de cámara en vivo). Confirmado con el usuario: Contraseña PDF/Carpeta Segura/Papelera quedan sin anuncios; Escáner (pantalla transitoria) queda excluida del alcance. Gauntlet completo verde (compile/detekt/lint/unit/6 instrumented tests) y verificado en dispositivo real, salvo el estado "QR detectado" del Lector y el Resultado de escaneo, que requieren una captura real con cámara. Ver [`docs/requirements/backlog-mejoras-ux-2026-08-30.md`](docs/requirements/backlog-mejoras-ux-2026-08-30.md) §8.
- **Banner azul uniforme con "Volver" integrado, implementado 2026-08-30** (bucket 2 del plan UX): `DocuSmartTopBanner` ganó un parámetro `onBack` opcional que integra la flecha + texto "Volver" dentro del propio banner (antes cada sub-pantalla armaba su propio ícono suelto al lado, robándole ancho al banner). Migradas y verificadas en dispositivo: Papelera, Menú de Seguridad, Contraseña PDF, Carpeta Segura — banner ahora a 100% de ancho en las 4. `ScanResultScreen` (Resultado de escaneo) migrado en código con el mismo patrón (de paso se quitó un `Scaffold`/`TopAppBar` que duplicaba el título) pero sin verificación visual (requiere una captura real con cámara). Las pantallas de la barra inferior (Home/Biblioteca/Convertir/Ajustes/PDF) no cambiaron. Ver [`docs/requirements/backlog-mejoras-ux-2026-08-30.md`](docs/requirements/backlog-mejoras-ux-2026-08-30.md) §9.
- **Primera tanda de "quick wins" del backlog UX implementada 2026-08-30**: accesos rápidos de Home pasaron de carrusel a grilla de 3 columnas (HU-UX-04); botón de Papelera en Biblioteca ahora reutiliza el mismo componente que "Dispositivo"/"Mis archivos" (antes sin título y de ancho fijo distinto); texto engañoso "Eliminar del historial" corregido a "Eliminar". El acceso rápido "Img→PDF" (antes iba al mismo lugar que el CTA genérico "Convertir", duplicado sin sentido) ahora abre el Convertidor ya preseleccionado en Imagen→PDF (`NavRoutes.Converter` ganó un parámetro opcional de ruta, mismo patrón que `NavRoutes.Study`). Hallazgo nuevo detectado en el proceso: `DocuSmartDocumentItem.kt` (menú "⋮" de archivos) no tiene i18n, todo hardcodeado en español. Verificado en dispositivo real, incluyendo una regresión de layout (texto "Dispositivo" cortado a media palabra) y un efecto colateral en `DocuSmartBottomBar` (ruta sin resolver al navegar por la pestaña inferior), ambos detectados y corregidos en la misma pasada. Ver [`docs/requirements/backlog-mejoras-ux-2026-08-30.md`](docs/requirements/backlog-mejoras-ux-2026-08-30.md) §5-6, §12.
- **4 ítems chicos del backlog UX implementados 2026-09-03** (H2, H3, H5, fila 21/H6): nota de aviso en el diálogo "Eliminar ahora" de Papelera sobre el permiso de Android para fotos que la app no creó; "Restaurar" pasa a `FilledTonalButton` (más peso visual, acción segura) para diferenciarse de "Eliminar ahora" (irreversible); Papelera muestra "%1$s en la papelera" arriba de "Borrar todo" (`DocumentUiModel` ganó `sizeBytes: Long = 0L`); `DocuSmartDocumentItem.kt` (menú "⋮" de Home/Biblioteca) y su `RenameDocumentDialog` dejan de tener texto hardcodeado en español, pasan a `stringResource()`. Verificado en dispositivo real: gauntlet completo en verde, 6/6 instrumentadas, los 4 cambios confirmados visualmente. Ver [`docs/requirements/backlog-mejoras-ux-2026-08-30.md`](docs/requirements/backlog-mejoras-ux-2026-08-30.md) §6, §7 (H2/H3/H5), §12 (H6).
- **Selector de archivo desde la biblioteca de la app implementado 2026-09-03** (item #15): Seguridad (Carpeta Segura, Contraseña PDF) y las 12 herramientas de un solo PDF de Herramientas PDF ganaron la opción de elegir un documento ya indexado por la app (`FileSourcePickerDialog` + `AppLibraryPickerViewModel`, nuevos en `core/ui/components`), no solo el selector del sistema. **Hallazgo real encontrado durante la verificación, no corregido (fuera de alcance de este ítem)**: `DocumentRepository.loadPdfsFromDownloads()` devuelve 0 filas en el dispositivo real pese a que hay decenas de PDFs reales en `MediaStore.Downloads` -- todos con `owner_package_name=NULL` (no insertados vía `MediaStore.insert()` desde la app), lo que bajo scoped storage los hace invisibles a la consulta sin `READ_EXTERNAL_STORAGE` real. Confirmado que el selector nuevo funciona correctamente probándolo sin el filtro de PDF (con Imágenes, que sí carga bien vía `READ_MEDIA_IMAGES`). Probablemente afecta también el conteo de la categoría "PDF" en la Biblioteca real -- queda catalogado para investigar aparte. Verificado en dispositivo real: gauntlet en verde, `connectedDebugAndroidTest` de Seguridad/Herramientas PDF (3/3), flujo completo de punta a punta (Carpeta Segura → elegir de biblioteca → proteger). Ver [`docs/requirements/backlog-mejoras-ux-2026-08-30.md`](docs/requirements/backlog-mejoras-ux-2026-08-30.md) §14.

---

## 6. Identidad de marca y sistema visual

Fuente: *Manual de marca DocuSmart* + *Concepto visual de DocuSmart* (documentos
consistentes entre sí). Logo ya diseñado (ver imágenes compartidas: ícono con
documento+escaneo en gradiente azul, wordmark "DocuSmart" en navy+azul).

**Valores de marca:** orden, claridad, tecnología, rapidez, confianza, minimalismo, productividad.
**Personalidad:** profesional pero cercana, animada, tecnológica pero simple.

### Paleta principal
| Nombre | Hex | Uso |
|---|---|---|
| Docu Blue | `#2563EB` | Primario |
| Smart Blue | `#1D4ED8` | Primario fuerte / interacción |
| Indigo Accent | `#4338CA` | Secundario / gradiente |
| Navy Dark | `#0F172A` | Texto fuerte |
| Light Background | `#F8FAFC` | Fondo general |
| Surface White | `#FFFFFF` | Tarjetas / superficies |
| Soft Border | `#E2E8F0` | Bordes |
| Slate Gray | `#64748B` | Texto secundario |

Gradiente de marca: `#2563EB → #4338CA` (splash, banners, tarjetas destacadas, premium).

### Colores de soporte
Éxito `#22C55E` · Advertencia `#F59E0B` · Error `#EF4444` · Info `#06B6D4` · Premium `#FBBF24`

### Colores por tipo de archivo
PDF `#EF4444` · Word `#2563EB` · Excel `#16A34A` · PowerPoint `#F97316` · Imagen `#8B5CF6` · Texto `#64748B` · ZIP `#D97706` · OCR/Escaneado `#06B6D4`

### Material 3 — tokens completos (light + dark)
Ver documento original para la tabla completa de tokens M3 (`primary`,
`onPrimary`, `primaryContainer`, etc. para ambos temas) — **pendiente de
verificar contra el `Theme.kt`/`Color.kt` actual del código** para confirmar
que la implementación coincide con el manual.

### Tipografía
**Inter** (principal). Pesos: Regular 400, Medium 500, SemiBold 600, Bold 700.
Escala: Display 32sp/700, Headline 28sp/700, Title 20-24sp/600-700, Body 14-16sp/400, Label 12-14sp/500-600.
*(Pendiente: confirmar si el código ya usa Inter o la tipografía por defecto de Material)*

### Componentes
- Botones: primario (fondo `#2563EB`, radio 16dp, alto 52dp), secundario (fondo `#DBEAFE`, texto `#1D4ED8`), tonal, outline, destructivo (`#FEE2E2`/`#B91C1C`).
- Tarjetas: fondo blanco, radio 20dp, sombra suave, padding 16dp.
- Inputs: alto 52dp, radio 14dp.
- Radios: botones 16dp, tarjetas 20dp, inputs 14dp, bottom sheet 24dp, chips 999dp (pill).
- Espaciado base 4dp: xs 4 / sm 8 / md 12 / lg 16 / xl 20 / xxl 24 / sección 32.
- Bottom nav: Inicio, Biblioteca, Convertir, PDF, Ajustes.
- Regla de anuncios: nunca en visor/lectura/conversión en proceso; solo en Home, Biblioteca, Herramientas.

---

## 7. Entregables académicos pendientes

Fuente: *mejoras pendientes del proyecto.docx*. El proyecto requiere evidenciar:

1. Definición y planeación (alcance, justificación, público objetivo)
2. Definición tecnológica (frontend/backend/BD/herramientas) + justificación técnica
3. Metodología ágil: Dashboard del proyecto, Product Backlog, Sprint Backlog, cronograma, roles
4. Requerimientos funcionales/no funcionales documentados, casos de uso, reglas de negocio
5. Arquitectura y diseño: diagramas, componentes, flujo de información
6. UI/UX: mockups 100%, prototipo/dummy funcional ≥70%, flujo de navegación

**Esto es adicional al roadmap técnico** — hay que decidir con el usuario si
se necesita generar estos entregables formales (backlog, diagramas, etc.)
como documentos separados.

---

## 8. Roadmap de producto (fuente: pendientes del roadmap.txt)

### Prioridades inmediatas (documento original, estado por re-verificar)
1. **Limpieza/pulido:** Timber en HomeScreen (✅ ya está), banner azul consistente en todas las vistas, ajustes funcionales (idioma/tema/almacenamiento/acerca de — ✅ ya implementados)
2. **Datos reales:** biblioteca con archivos reales (no mock), historial de conversiones, favoritos persistentes (Room/DataStore) — **favoritos persistentes sigue siendo el bug #1 reportado en QA**
3. **Escáner:** CameraX con auto-detección de bordes, recorte manual, modos documento/pizarra/recibo — el escáner actual usa ML Kit Document Scanner, ya cubre gran parte de esto
4. **Convertidor completo:** ampliar todas las combinaciones de conversión (ver tabla de requerimientos)
5. **Monetización V1.1:** límite diario de conversiones (10 img→pdf, 3 merge/split/compress/rotate), Rewarded Ads para desbloquear, banner AdMob en todas las vistas
6. **Features IA (V1.2+):** asistente inteligente al subir archivo, encriptación/desencriptación PDF, carpeta privada PIN/biometría (ya en progreso — ver Security), organización automática con ML, resumen en voz TTS, agenda inteligente, mapas conceptuales

### Tabla de features por versión (prioridad)
| Versión | Feature | Prioridad |
|---|---|---|
| V1.1 | Límite de conversiones diarias + Rewarded Ads | Alta |
| V1.1 | Escáner PRO con ML Kit | Alta |
| V1.2 | Asistente inteligente al subir archivo | Media |
| V1.2 | Encriptación/Desencriptación PDF | Media |
| V1.2 | Carpeta privada con PIN/biometría | Media |
| V2.0 | Organización automática con ML | Baja |
| V2.0 | Resumen en voz TTS | Baja |
| V2.0 | Agenda inteligente (detección de fechas) | Baja |
| V2.0 | Mapas conceptuales | Baja |

### Play Billing real conectado — RF-PREM-05 (2026-08-25)
Reemplaza `PremiumManager.simulatePurchase()` (eliminado) por
`core/billing/BillingManager.kt`, usando Play Billing Library 9.1.0 —
versión mayor nueva (v9), con cambios de API respecto a v6/v7 (verificado
contra la guía oficial vigente antes de escribir código, no supuesto de
memoria).

- Productos: `com.docsmart.premium.monthly`/`annual` (`ProductType.SUBS`) y
  `com.docsmart.premium.lifetime` (`ProductType.INAPP`) — mismos IDs que ya
  declaraba `PremiumRepository` desde antes, ahora sí conectados de verdad.
- Reemplaza el precio fijo hardcodeado (`"$2.99"`) por el precio real y
  localizado que devuelve Play Store en cuanto está disponible.
- `restorePurchases()` consulta compras reales (`queryPurchasesAsync`) en
  vez de simular "no se encontraron compras" siempre.
- Confirmación de compra (`acknowledgePurchase`) implementada para ambos
  tipos de producto — obligatoria dentro de 3 días o Google reembolsa
  automáticamente.
- **Decisión deliberada, no un descuido:** no se valida la firma de la
  compra contra la clave pública de licencias de Play Console (RSA) — esa
  clave solo existe una vez que la app se crea en Play Console, y el
  proyecto no tiene backend propio para verificar server-side. Se confía en
  `BillingClient` + `PurchaseState`, razonable para una app de un solo
  desarrollador sin backend.
- **No verificable de punta a punta todavía:** los 3 productos no existen
  en Play Console, así que `queryProductDetails()` no encuentra nada hasta
  que la app se suba al menos a una pista de prueba. El código compila y se
  conecta correctamente a Play Billing, pero la compra real no se probó de
  punta a punta.
- Sin tests nuevos: `BillingManager` envuelve `BillingClient` (clase de
  framework, no mockeable sin infraestructura pesada) — mismo criterio ya
  aplicado a `AdManager`, que tampoco tiene tests.
- De paso, se extrajo `Context.findActivity()` a
  `core/ui/util/ActivityUtils.kt` — mismo patrón de desenvolver
  `ContextWrapper` que ya usaba `SecurityScreen`, ahora reutilizable (lo usa
  `PremiumScreen` para `launchBillingFlow`, que necesita el `Activity` real).
- Verificado en verde: `assembleDebug`/`bundleRelease` (AAB firmado
  regenerado sin problemas de R8 nuevos) + `detekt` + `testDebugUnitTest`
  (92 tests, 0 fallos) + `lintDebug` (0 errores). Detalle completo en
  [`docs/requirements/settings-premium.md`](docs/requirements/settings-premium.md) §8.

### Política de privacidad + formulario de seguridad de datos (2026-08-25)
Basado en inventario real del código, no en suposiciones: se revisó
`AndroidManifest.xml` completo, los 15 eventos reales de
`DocuSmartAnalytics.kt` uno por uno, se buscó `FirebaseAuth`/claves custom
de Crashlytics/SDK de consentimiento de Google (UMP) en todo el proyecto, y
se confirmó cómo funciona el dictado de voz en `StudyScreen.kt`.

- **Publicada:** https://sites.google.com/view/docusmart-privacidad/inicio
  (Google Sites, no GitHub Pages — GitHub pedía plan pago para activar
  Pages en este repo, así que se optó por la alternativa gratuita). El
  texto original queda en `legal/privacy-policy.html` como fuente/respaldo
  (carpeta separada de `docs/` a propósito: `docs/requirements/` tiene specs
  internas que no deben quedar servidas como sitio público).
  `.github/workflows/pages.yml` queda sin uso por ahora. Correo de
  contacto: `jblackmouthr@gmail.com` (decisión del usuario).
- Formulario de seguridad de datos de Play Console: respuestas listas para
  copiar en `docs/requirements/deployment.md` §4.2. Conclusión clave:
  fotos/videos/documentos se **acceden** localmente pero no se **recolectan**
  (nunca se transmiten fuera del dispositivo) — Play Console distingue
  ambas cosas. Sí se recolectan: actividad en la app (Analytics), logs de
  fallas (Crashlytics), Advertising ID (AdMob).
- **Hallazgo — implementado 2026-08-26:** no había SDK de consentimiento
  (Google UMP), necesario antes de mostrar anuncios personalizados a
  usuarios de la UE/Reino Unido (exigencia de Google, no solo buena
  práctica). Ver detalle en `docs/requirements/settings-premium.md` §9.
- **Hallazgo ya conocido, confirmado de nuevo:** el AdMob App ID en el
  manifest sigue siendo el de prueba público de Google, no uno real.

### Firma de release + CI/CD de build firmado (2026-08-25)
Primer paso concreto hacia publicar en Play Store. El usuario aclaró que ya
tiene cuenta de Google Play Console pero nunca subió ninguna app — esto
importa porque **Google no permite automatizar la primera subida de una app
nueva por API**, tiene que hacerse una vez desde la consola web. El plan se
ajustó a: dejar la firma y el CI de construcción listos para esa primera
subida manual, y la automatización de publicaciones (Gradle Play Publisher)
queda como el paso siguiente natural una vez exista al menos una versión
subida y una cuenta de servicio creada en Play Console.

- Keystore nuevo generado con `keytool` (PKCS12, RSA 2048, válido hasta
  2054) — decisión del usuario, confirmada explícitamente antes de generar
  nada dado lo irreversible de perder la clave. Vive en `keystore/`
  (gitignored), contraseñas en `keystore.properties` (gitignored,
  `keystore.properties.example` committeado como plantilla).
- `signingConfigs.release` en `app/build.gradle.kts` lee ese archivo si
  existe, sin romper el build para colaboradores/CI que no lo tengan.
- Verificado localmente: `./gradlew bundleRelease` genera un AAB firmado,
  confirmado con `jarsigner -verify`.
- **2 problemas reales encontrados al verificar** (nadie había corrido un
  build de release en este proyecto hasta ahora): `-Xmx2048m` no alcanzaba
  para R8/lint del build de release (subido a 4096m), y R8 fallaba por
  clases "faltantes" de dependencias opcionales de Apache POI/commons-compress
  (log4j2, slf4j, osgi, zstd/xz) que nunca se cargan en runtime en
  Android — agregadas reglas `-dontwarn` en `proguard-rules.pro`.
- `.github/workflows/release.yml` nuevo: construye y firma el AAB en cada
  tag `v*`, lo deja como artefacto descargable — no publica a Play Console
  todavía. Requiere 4 secrets de GitHub que el usuario debe configurar él
  mismo (instrucciones en el doc, no se comparten valores de secretos por
  chat).
- Pendiente explícito del usuario, no delegable: respaldar el keystore y
  las contraseñas en un lugar seguro propio — si se pierden, no se puede
  volver a generar el mismo ni actualizar la app una vez publicada con esa
  firma.
- Detalle completo, checklist de publicación, y lo que falta (billing,
  política de privacidad, formulario de seguridad de datos) en
  [`docs/requirements/deployment.md`](docs/requirements/deployment.md).

### Primera prueba de Compose UI: abrir documento (2026-08-25)
Instrumentada (dispositivo/emulador, no en CI todavía — decisión del
usuario: agregar un emulador a GitHub Actions es más lento/complejo, mejor
evaluarlo con más pruebas de este tipo). `ViewerScreenTest` cubre el flujo
#1 del manual de marca ("abrir un documento en menos de 3 toques"), sin
infraestructura de Hilt — `ViewerScreen` ya acepta `viewModel` como
parámetro, así que se le pasa uno construido a mano con `mockk`, mismo
patrón que los unit tests.

**Bug real encontrado al hacer correr esta prueba, no por lectura de
código:** `DocuSmartApplication.onCreate()` llama a
`AdManager.initialize()` sin condición — carga un interstitial y un video
recompensado de inmediato en cada arranque de la app. Es exactamente el
hallazgo ya señalado en `sentinel_report.json` de mayo ("Ad loading is
triggered immediately upon initialization"), nunca corregido. Bajo
instrumentación esto hace que cualquier prueba de UI dispare una carga de
anuncio real, inicializando el decoder de video del emulador — en este
entorno **crasheaba el proceso completo de la app** (crash nativo en el
códec de video). Corregido evitando la inicialización de anuncios cuando la
app corre bajo instrumentación (detectado por la presencia de Espresso en
el classpath — `ActivityManager.isRunningInUserTestHarness()` es solo para
Firebase Test Lab, Google lo documenta explícitamente como no válido para
`connectedAndroidTest` local). Sin impacto en producción: la detección solo
es verdadera bajo test. El problema de fondo (anuncios no cargan de forma
perezosa) sigue como backlog — ahora con evidencia de que puede causar
inestabilidad real, no solo demorar el arranque.

Requirió resolver dos problemas de Gradle nuevos: un conflicto de
"consistent resolution" de AGP entre el classpath de la app y el de
`androidTest` (forzado `concurrent-futures:1.2.0`), y archivos
`META-INF/*.md` duplicados entre JARs de test (excluidos con comodín).
Detalle completo en
[`docs/requirements/visor-biblioteca.md`](docs/requirements/visor-biblioteca.md) §9.

### Primera prueba de integración: Room contra SQLite real (2026-08-25)
Todos los tests hasta ahora eran unitarios puros (lógica con fakes/mocks,
sin frameworks reales). `DocumentHistoryDaoTest` corre contra **SQLite
real** vía `BundledSQLiteDriver` (`androidx.sqlite`, no un fake) — verifica
el DAO que Room genera de verdad, no solo la lógica que se le agregó
encima.

- Sin Robolectric ni emulador: es la recomendación oficial de Google para
  probar Room en la JVM (su propia guía desaconseja Robolectric
  explícitamente para esto) — consistente con la filosofía ya establecida
  en este proyecto de evitar Robolectric/instrumentación cuando se puede
  probar la lógica real de otra forma.
- Requirió un bloque `androidComponents { onVariants { ... } }` en
  `app/build.gradle.kts` para sustituir la variante Android de
  `androidx.sqlite:sqlite-bundled` por su variante `-jvm` solo en el
  classpath de test — el artefacto Android no trae los binarios nativos que
  necesita la JVM del test unitario.
- **Bug real encontrado por esta prueba, no por lectura de código:**
  `DocumentHistoryDao.recordOpen()` usaba `@Upsert` — Room genera
  internamente un insert y, si choca con la clave primaria, un update en un
  segundo paso; esa excepción de conflicto no se tradujo bien contra
  `BundledSQLiteDriver` y quedó como `android.database.SQLException` sin
  causa legible en vez de resolverse en silencio. Cambiado a
  `@Insert(onConflict = OnConflictStrategy.REPLACE)` — una sola sentencia
  SQL, equivalente para esta entidad de 2 columnas. El DAO fake usado en
  `DocumentRepositoryTest` nunca podría haber encontrado este bug porque no
  ejecuta SQL real — exactamente la razón de ser de una prueba de
  integración. Detalle completo en
  [`docs/requirements/visor-biblioteca.md`](docs/requirements/visor-biblioteca.md) §8.1.
- 6 tests nuevos.

### Room: historial real de "Recientes" en Home — RF-VIS-09 (2026-08-25)
Primera tabla Room del proyecto. Antes "Recientes" en Home era literalmente
`loadAllDocuments().take(5)` — un documento abierto hoy pero sin modificar
no aparecía como reciente, porque el orden venía de la fecha de
modificación del archivo, no de uso real.

- Nueva tabla `document_history` (`core/data/db/`): `documentId` (mismo id
  que Biblioteca/Home/Favoritos) + `lastOpenedAt`. Primer `@Module` de Hilt
  del proyecto — todo lo demás usa `@Inject constructor` directo, pero
  `Room.databaseBuilder()` necesita un builder explícito.
- `ViewerViewModel` registra la apertura al publicar un documento cargado
  (no en el mock de demo) y al desbloquear exitosamente un PDF protegido —
  mostrar el diálogo de contraseña sin desbloquear no cuenta.
- `DocumentRepository.loadRecentlyOpened(limit)` cruza el historial con el
  escaneo de archivos existente: prioriza el orden del historial, descarta
  ids de archivos ya borrados/movidos, y completa los cupos restantes con
  los más recientes por fecha de archivo — así una instalación nueva sin
  historial no se queda con Recientes vacío (mismo comportamiento que
  antes). La lógica de fusión se extrajo a una función pura
  (`mergeHistoryWithDocuments`) específicamente para poder testearla sin
  mockear Room/MediaStore.
- Room 2.8.4 (KSP, no KAPT) — se evaluó Room 3.0 pero está en alpha desde
  marzo 2026 bajo coordenadas nuevas (`androidx.room3`); se prefirió la
  serie 2.x estable dado que es la primera tabla del proyecto.
- Alcance de esta pasada: solo Home. Biblioteca no tiene todavía una
  sección "últimos abiertos" propia (mencionada en el inventario de
  pantallas, §9) — con `loadRecentlyOpened()` ya construido, agregarla ahí
  es directo si se prioriza. Detalle completo en
  [`docs/requirements/visor-biblioteca.md`](docs/requirements/visor-biblioteca.md) §8.
- 5 tests nuevos en `DocumentRepositoryTest`.

### Idioma por defecto en instalación nueva — RF-SET-06 (2026-08-24)
Requerimiento #13 original ("multilenguaje con default según ubicación
geográfica de Play Store"). La geografía real de Play Store no es
verificable desde el cliente, así que se usa la señal estándar de facto en
Android: el idioma del dispositivo. `LanguageManager.deviceDefaultLanguage()`
ya existía y ya se usaba para "Restablecer configuración" (HU-SET-01) — el
hueco real era que `loadLanguage()` (el valor inicial de `currentLanguage`,
usado en una instalación nueva sin idioma guardado todavía) seguía
devolviendo español fijo por el propio default de
`prefs.getString("language", AppLanguage.SPANISH.code)`. Cambiado a usar
`deviceDefaultLanguage()` como respaldo cuando no hay nada guardado; un
idioma ya elegido explícitamente (incluido español) no se toca. 3 tests
nuevos en `LanguageManagerTest`.

### Endurecimiento de Carpeta Segura (2026-08-24)
Al retomar "la carpeta segura no bloquea el archivo" se confirmó que el bug
original **ya estaba corregido** (`moveToSecure()` ya se llamaba desde
`SecurityViewModel`) — pero §3 y §5 de este mismo documento seguían
listándolo como abierto, contradiciendo la sección de auditoría de más
arriba (desincronización entre secciones, ya corregida). Al leer el código
para confirmar el estado real se encontró un hueco genuino, ya anticipado
por el propio RNF-SEC-01/AC2 de [`security.md`](docs/requirements/security.md)
pero no implementado del todo: `SecurityManager.moveToSecure()` llamaba a
`File.delete()` sin comprobar su resultado, y siempre devolvía éxito aunque
el borrado hubiera fallado silenciosamente (`File.delete()` no lanza
excepción al fallar, solo devuelve `false`) — el original podía quedar
accesible en su ubicación sin que el usuario se enterara. La vía de
importación por `Uri`/SAF (`importFileToSecure`) ya manejaba esto
correctamente; ahora `importLocalFile()` sigue el mismo patrón. 1 test nuevo
(`SecurityManagerTest`, caso "no existe el original" — no se agregó un caso
de "el borrado falla tras copiar bien" a nivel de filesystem real porque
forzarlo de forma confiable difiere entre Windows y Linux/CI).

### Limpieza de lint + i18n de la barra inferior (2026-08-24, rama `feature/lint-cleanup`)
Verificado con corrida limpia (`--rerun-tasks`) tras toda la sesión anterior:
lint bajó a 0 errores / 162 warnings (no 115 errores como se pensó al inicio
de esta sesión — el primer conteo salió inflado, muy probablemente por
Android Studio corriendo su propio análisis en paralelo y pisando el reporte
a mitad de la corrida). De esos 162 warnings, esta rama corrige:
- 36 strings confirmados sin uso real (verificado con grep contra el código
  actual antes de borrar, no solo confiando en lint) — eliminados de los 5
  idiomas.
- **Bug real encontrado al investigar por qué `nav_home`/`nav_library`/etc.
  aparecían "sin uso":** `DocuSmartBottomBar.kt` tenía las 5 etiquetas de la
  barra de navegación inferior hardcodeadas en español ("Inicio",
  "Biblioteca"...) en vez de `stringResource()` — la barra inferior nunca se
  traducía sin importar el idioma elegido. Corregido: `BottomNavItem` ahora
  guarda `@StringRes val labelRes: Int` en vez del label ya resuelto (la
  lista es `private val` de nivel de módulo, sin contexto de composición, así
  que no puede llamar `stringResource()` directamente ahí) y se resuelve
  dentro del `@Composable`. Se restauraron las 5 claves `nav_*` en los 5
  idiomas (habían sido borradas por error en el primer paso de limpieza,
  antes de encontrar este bug).
- `String.format` sin locale expĺicito en `DocumentRepository.formatSize()`
  (corregido con `Locale.getDefault()`, no es un Composable) y en el timer de
  `StudyScreen` (**no** se usó `Locale.getDefault()` ahí — lint marcó
  `NonObservableLocale` porque leer el locale dentro de un `@Composable` no
  es reactivo a cambios de idioma en runtime; se reemplazó `String.format`
  por `padStart(2, '0')`, ya que el timer solo tiene dígitos 0-9 sin
  sensibilidad real de locale).
- `AppBundleLocaleChanges`: `LanguageManager` cambia idioma manualmente en
  runtime, pero el AAB por defecto reparte el bundle con split por idioma —
  un usuario podía quedarse sin los recursos del idioma que elige dentro de
  la app. Desactivado `bundle { language { enableSplit = false } }`.
- `StaticFieldLeak` en `ViewerViewModel.pendingContext`: falso positivo (ya
  guardaba `applicationContext`, no la Activity) — documentado con
  `@SuppressLint` explicando por qué, sin cambiar el comportamiento.

Verificado en verde: `assembleDebug` + `detekt` + `testDebugUnitTest` (77
tests, 0 fallos) + `lintDebug` (0 errores, 122 warnings restantes — sobre
todo `UseKtx`, `UseTomlInstead` y actualizaciones de dependencia, backlog de
bajo riesgo, no abordado en esta pasada). Commiteado en rama
`feature/lint-cleanup` y fusionado a `main` el mismo día (sin push todavía).

### Roadmap técnico (definido en esta sesión, complementario)
Fase 0 (estabilización) ✅ completada. Fase 1 (CI básico) ✅ completada.
Fase 2 (Dependabot + Gitleaks) ✅ completada 2026-08-24, rama
`feature/dependabot-gitleaks`. Fase 3 (SonarCloud + cobertura) ✅ completada
2026-08-24, rama `feature/sonarcloud-coverage` — ver detalle abajo. Room
para biblioteca/historial ✅ completado 2026-08-25 (alcance: historial de
"Recientes" en Home — ver más abajo). Pruebas de integración ✅ iniciadas
2026-08-25 (`DocumentHistoryDaoTest` contra SQLite real — ver más abajo);
queda por definir si se necesita más cobertura de integración además de
Room, o pasar directo a pruebas de sistema. Compose UI Testing ✅ iniciado
2026-08-25 (`ViewerScreenTest`, flujo "abrir documento" — ver más abajo),
solo local por ahora (decisión del usuario: sin emulador en CI todavía).
Despliegue/publicación, avance sustancial 2026-08-25: firma de release
verificada (`bundleRelease` genera un AAB firmado, probado de punta a punta
instalándolo en un emulador vía `bundletool`), CI de build firmado
funcionando (tag `v1.0.0` corrido en verde en GitHub Actions), secrets de
GitHub configurados por el usuario, keystore respaldado por el usuario,
política de privacidad publicada, formulario de seguridad de datos
preparado, y Play Billing real conectado — ver
[`docs/requirements/deployment.md`](docs/requirements/deployment.md) y
[`docs/requirements/settings-premium.md`](docs/requirements/settings-premium.md) §8.
Único bloqueante real que queda para publicar: la primera subida manual a
Play Console (no automatizable por Google). Siguen: más flujos de Compose
UI Testing si se prioriza, SDK de consentimiento de anuncios (UMP), AdMob
App ID real.

### SonarCloud + cobertura JaCoCo (2026-08-24)
El usuario creó la cuenta SonarCloud y conectó el repo (`blackmouthriver` /
`blackmouthriver_DocuSmart`) — el análisis automático ya corría sin tocar
código (quality gate "Aprobado", 1 cuestión de seguridad, 42 de
mantenibilidad, sin cobertura). Para agregar cobertura real (requiere
análisis por CI, no el automático) se agregó:
- Plugin `org.sonarqube` (7.4.0.8496) en el `build.gradle.kts` raíz, con
  `sonar.projectKey`/`sonar.organization` fijos y
  `sonar.coverage.jacoco.xmlReportPaths` apuntando al reporte de JaCoCo.
- Plugin `jacoco` + tarea `jacocoTestReport` en `app/build.gradle.kts` —
  corre los unit tests existentes y genera el XML de cobertura, excluyendo
  clases generadas (Hilt/Dagger/KSP, R, BuildConfig). Verificado localmente:
  reporte real, ~4.4% de líneas cubiertas hoy (77 tests sobre una base de
  código mucho más grande que solo los use cases probados).
- `.github/workflows/sonarcloud.yml` — corre `jacocoTestReport sonar` en
  cada push/PR a `main`, con `SONAR_TOKEN` como secret de GitHub (el usuario
  lo generó y lo agregó — Claude no maneja tokens directamente).
Pendiente para el usuario: revisar los 42 hallazgos de mantenibilidad y 1 de
seguridad en el dashboard de SonarCloud — el usuario pidió que Claude los
revise y corrija los reales en la siguiente sesión de trabajo sobre esto.

### Hallazgos SonarCloud — seguridad + mantenibilidad (2026-08-24)
Consultados directamente vía API pública de SonarCloud (el proyecto se puso
en público ese día) tras habilitarse el análisis por CI. Confirmado: 1
vulnerabilidad, 42 code smells, 0 security hotspots.

- **Seguridad (`xml:S5332`):** `AndroidManifest.xml` no declaraba
  `android:usesCleartextTraffic` — queda implícitamente habilitado en
  Android 8/9 (minSdk 26). La app no tiene ningún uso real de HTTP sin TLS
  (AdMob/Firebase/ML Kit usan HTTPS). Corregido con
  `android:usesCleartextTraffic="false"` explícito.
- **5 literales duplicados (`kotlin:S1192`):** el ID de prueba de AdMob
  (`AdConstants.kt`, 5 banners que temporalmente comparten el mismo ID de
  prueba — comentado para no perder esa intención) y `"application/pdf"`/
  `"image/jpeg"` repetidos en `PdfToolsScreen.kt`, `ScanResultScreen.kt` y
  `ViewerViewModel.kt` — extraídos a constantes locales por archivo.
- **37 métodos con complejidad cognitiva excesiva (`kotlin:S3776`):** de los
  moderados (16-29) se refactorizaron 15 — descomposición en sub-funciones/
  sub-composables sin cambiar comportamiento, en `PptToTextUseCase`,
  `CompressPdfUseCase`, `WordToHtmlUseCase`, `PdfPasswordUseCase` (además
  eliminó duplicación real entre `protect()`/`removePassword()`),
  `DocuSmartNavGraph`, `DocuSmartDocumentItem`, `OnboardingScreen`,
  `MergePdfScreen`, `ViewerViewModel` (`loadDocument` y, en una segunda
  pasada tras verificar contra el dashboard real de SonarCloud,
  `loadFromUri` — el hallazgo original apuntaba a esta última, no a
  `loadDocument`; el número de línea del reporte cayó dentro del cuerpo de
  `loadDocument` por coincidencia visual, lección para no asumir la función
  solo por la línea sin confirmar con la firma exacta), `ViewerScreen`
  (`TextViewerContent`, `PdfPasswordDialog`, `PdfViewerContent`),
  `SecurityScreen.NumericKeypad` y `StudyScreen` (`ParagraphItem`,
  `PomodoroTab`). Al descomponer `ViewerScreen.kt` en más sub-composables
  se subió `TooManyFunctions.thresholdInFiles` de 20 a 26 en
  `config/detekt/detekt.yml` (documentado el motivo en el propio archivo).
  Los 22 casos extremos (complejidad 41-202: `QrScreen.kt` con 202 y 114,
  `StudyScreen.kt` con 87/66/46, `ViewerScreen.kt` con 73/58/46/41, y otros)
  **quedan sin tocar, deliberadamente** — decisión del usuario: son
  Composables de pantalla completa sin descomponer, refactorizarlos implica
  reescritura sustancial con riesgo real de romper comportamiento visual/de
  interacción sin Compose UI Testing todavía como red de seguridad (solo
  hay 77 unit tests). Quedan documentados aquí como backlog priorizado para
  abordar en una sesión dedicada, idealmente después de agregar Compose UI
  Testing en los flujos críticos.

Verificado en dos rondas: `assembleDebug` + `lintDebug` + `detekt` +
`testDebugUnitTest` (77 tests, 0 fallos) en verde, y confirmado contra el
dashboard real de SonarCloud tras cada push — la primera ronda bajó de 43
a 24 hallazgos abiertos (23 code smells de complejidad + 0 vulnerabilidades
+ 0 duplicados), la segunda corrigió el `loadFromUri` que se había pasado.

### Dependabot + Gitleaks (2026-08-24)
`.github/dependabot.yml` — actualizaciones semanales de dependencias Gradle
y de las Actions usadas en CI. `.github/workflows/gitleaks.yml` — escaneo de
secretos en cada push/PR a `main`, usando el binario de gitleaks directamente
(no la Action del marketplace, para evitar cualquier dependencia de
licenciamiento en repos privados/de organización). Se aprovechó para
corregir `ci.yml`, que todavía disparaba en `desarrollo`/`preproductivo`
(ramas eliminadas en la limpieza de ramas de este mismo día).

**Hallazgo real del primer escaneo local:** `app/google-services.json` (ya
en el repo) contiene una API key de Google que gitleaks detecta por patrón.
Es el archivo de configuración estándar de Firebase — Google documenta
oficialmente que es seguro versionarlo; el riesgo real depende de que la key
esté restringida por paquete+SHA-1 en Google Cloud Console, algo que el
usuario debe verificar por su cuenta (no visible ni verificable desde el
código). Decisión del usuario (2026-08-24): permitir este archivo
específico vía `.gitleaks.toml` (`[allowlist] paths`), no desactivar la
regla globalmente. Pendiente para el usuario: confirmar en Google Cloud
Console que la key tiene restricciones configuradas.

### Migración AGP + Kotlin + KSP + Hilt (2026-08-24)
Motivada por 7 PRs de Dependabot bloqueados — todos fallaban por el mismo
techo: AGP 8.7.0/Kotlin 2.0.21 eran demasiado viejos para el resto del
ecosistema de dependencias. Migración coordinada en rama
`feature/agp-kotlin-migration`:

- AGP 8.7.0 → **8.13.2** (última versión de la serie 8.x). Se evaluó AGP
  9.3.2 primero y se descartó: 4 incompatibilidades internas distintas con
  KSP/Hilt en esa rama nueva del plugin (Kotlin integrado por defecto,
  `kotlinOptions` removido, conflicto de cast interno al reactivar el
  plugin de Kotlin standalone), ninguna resoluble sin comprometer la
  estabilidad del build.
- Kotlin 2.0.21 → **2.2.21**, KSP → **2.2.21-2.0.5**.
- Hilt 2.44 → **2.57** — no la más reciente (2.60.1): 2.59.2 y 2.60.1
  requieren AGP 9.0+ explícitamente; 2.52-2.55 aplican bien en AGP 8.x pero
  su `kotlin-metadata-jvm` embebido no soporta metadata de Kotlin 2.2 (tope
  en 2.1.0) y `hiltJavaCompileDebug` falla. 2.57 es la primera versión que
  resuelve ambos requisitos a la vez.
- compileSdk 35 → **36**. Gradle wrapper → **9.5.0**.
- activity-compose → 1.13.0, coroutines → 1.11.0.
- Firebase BOM: se quitó la versión propia fija en `firebase-analytics`/
  `firebase-crashlytics` (anulaba el propósito del BOM).

**2 bugs reales encontrados durante la migración** (no relacionados con la
causa original, solo visibles al recompilar con las versiones nuevas):
1. `DocuSmartAnalytics.kt` importaba `com.google.firebase.analytics.ktx.analytics`
   y `com.google.firebase.ktx.Firebase` — paquetes `-ktx` que Firebase fusionó
   a los artefactos principales hace tiempo; sin versión propia fija en el
   BOM quedaron irresolubles. Corregido a `com.google.firebase.Firebase` /
   `com.google.firebase.analytics.analytics`.
2. `DocuSmartNavGraph.kt` (rutas Scanner/ScanResult): `getBackStackEntry()`
   dentro de `remember { }` sin key — un lint check nuevo de
   `navigation-compose` (`UnrememberedGetBackStackEntry`) lo marca como
   error porque el handle quedaría obsoleto si la composable se reutiliza
   para otra entrada del backstack. Corregido con
   `remember(backStackEntry) { ... }`, usando la `NavBackStackEntry` que la
   propia composable ya recibe como parámetro.

Verificado en verde: `assembleDebug` + `lintDebug` + `detekt` +
`testDebugUnitTest` (77 tests, 0 fallos). Con esto, los PRs de Dependabot
para Kotlin/KSP/activity-compose/coroutines deberían poder re-evaluarse; el
que fija Hilt en 2.60.1 seguirá bloqueado mientras el proyecto no migre a
AGP 9.x (decisión deliberada de esta migración: quedarse en AGP 8.x hasta
que KSP/Hilt maduren sobre el modo de Kotlin integrado de AGP 9).

---

## 9. Inventario de pantallas (fuente: Contenido, vistas y herramientas)

- **Inicio:** abrir archivo, convertir, escanear, imagen a PDF, caja fuerte (futuro), modo estudio (futuro), recientes, banner de anuncio.
- **Biblioteca:** buscador, categorías por tipo, favoritos, últimos abiertos.
- **Convertidor:** imagen↔pdf, texto a PDF, office a PDF (futuro), PDF a Word (premium/futuro).
- **Herramientas PDF:** unir, dividir, rotar, comprimir, proteger, firmar.
- **Configuración:** tema, carpeta de salida, notificaciones, privacidad, premium, acerca de, limpiar historial.
- **Visor de documento:** vista PDF/documento, buscar, favorito, compartir, anotar, convertir, exportar.

*(Nota: los archivos `diseño de las pantallas*.docx` en el Desktop contienen
mockups visuales de 6 vistas — son imágenes, no texto extraíble. Compartir
capturas puntuales si se necesita referencia visual exacta de una pantalla.)*

---

## 10. Decisiones y preferencias de trabajo del usuario

- Prefiere que yo prepare y ejecute los commits (`git add`/`commit`/`push`)
  cuando lo pide explícitamente, no antes.
- Confía en los cambios técnicos hechos hoy; dará retroalimentación después
  de revisar con calma.
- Quiere avanzar hacia: HU con criterios de aceptación, pruebas unitarias,
  y "todo el marco de trabajo" de calidad (según roadmap ya acordado:
  seguridad con Dependabot/Gitleaks/SonarCloud, Room, pruebas de
  integración, despliegue).
- Sin apuro en el timeline — prioriza calidad antes de publicar.
- Es un proyecto también académico (ver §7), así que puede necesitar
  entregables formales de metodología ágil además del trabajo técnico.
- Formato elegido para requerimientos/HU: specs ligeras por módulo dentro
  de `docs/requirements/`, enlazadas desde este archivo (no un SRS separado
  por ahora).
- Metodología de trabajo por módulo: primero corregir los bugs de QA ya
  confirmados (con su HU como criterio de aceptación), luego tests
  unitarios que cubran ese comportamiento — no documentar todo antes de
  tocar código.
- Empezamos por el módulo **Seguridad** (2026-08-24). Decisiones tomadas:
  borrar el archivo original al proteger, sin recuperación de PIN
  (restablecer borra archivos), migrar cifrado de QR de XOR a AES,
  auto-bloqueo al pasar a segundo plano. Ver
  [`docs/requirements/security.md`](docs/requirements/security.md).
- **Workflow de git (2026-08-24):** de aquí en adelante creo commits pero
  **no hago push** salvo que lo pida explícitamente para ese commit puntual
  — el usuario revisa y sube manualmente. Para cada HU/módulo que se
  trabaje, se crea una rama propia desde `main`, se trabaja ahí, y al
  terminar se fusiona con `main`; el usuario borra la rama manualmente tras
  el push. Ramas `desarrollo` y `preproductivo` eliminadas hoy (local y
  remoto) tras fusionar todo su contenido pendiente en `main` — `preproductivo`
  no tenía ningún cambio único, solo una versión vieja ya superada.
  Repo simplificado a **una sola rama larga (`main`) + ramas de feature
  de corta duración por HU**.
- Tercer módulo refinado (2026-08-24, primero bajo el flujo de ramas por HU):
  **Visor + Biblioteca + Home**, en rama `feature/visor-biblioteca`. Ver
  [`docs/requirements/visor-biblioteca.md`](docs/requirements/visor-biblioteca.md).
- Cuarto módulo refinado (2026-08-24): **Conversión de documentos**, en rama
  `feature/conversion`. Encontrado y corregido un bug de configuración que
  rompía Word→PDF/Excel→PDF en silencio (exclusión de `org.apache.xmlbeans`
  en las dependencias de Apache POI). Ver
  [`docs/requirements/conversion.md`](docs/requirements/conversion.md).
- Quinto módulo refinado (2026-08-24): **Escáner** (documento + QR), en rama
  `feature/scanner`. La mayoría de la QA de mayo resultó obsoleta (cambio a
  Google ML Kit Document Scanner, lector de QR ya reconstruido en el módulo
  Seguridad); 2 bugs reales menores corregidos. Ver
  [`docs/requirements/scanner.md`](docs/requirements/scanner.md).
- Sexto y séptimo módulos refinados en paralelo (2026-08-24, cada uno en su
  propia rama desde `main`):
  - **Estudio** (`feature/study`) — corrupción silenciosa de comillas en
    notas guardadas + orden invertido al recargar + TTS forzando español,
    los 3 corregidos. `StudyScreen.kt` queda documentado como deuda de
    arquitectura (única pantalla grande sin ViewModel). Ver
    [`docs/requirements/study.md`](docs/requirements/study.md).
  - **Ajustes + Premium** (`feature/settings-premium`) — el límite diario de
    uso gratis en Herramientas PDF (requerimiento #16) ya existía en código
    pero nunca se conectó — corregido; "restablecer configuración" forzaba
    español sin importar el dispositivo — corregido. Compra Premium
    confirmada como placeholder intencional (Play Billing real pendiente de
    que el usuario configure Play Console). Ver
    [`docs/requirements/settings-premium.md`](docs/requirements/settings-premium.md).
  - Al fusionar ambas ramas por separado en `main`, el merge dejó contenido
    duplicado/en conflicto en este archivo (bullets de tests repetidos,
    filas de requerimientos repetidas, encabezados sin fusionar) —
    corregido en la limpieza de 2026-08-24 durante el trabajo de SonarCloud
    y, de forma independiente en paralelo, durante la migración de
    AGP/Kotlin/KSP/Hilt (ambas limpiezas se combinaron sin pérdida al
    fusionar las dos ramas en `main`).

---

## 11. Fuentes originales

Documentos en `C:\Users\HP\Desktop\proyectoDocSmart\`:
- `Barrido de pruebas v.1.0 DocSmart.docx` — QA manual, pantalla por pantalla
- `Concepto visual de DocuSmart.docx` — sistema visual completo (colores, tipografía, componentes)
- `Contenido, vistas y herramientas.docx` — inventario de pantallas y funciones
- `diseño de las pantallas proyecto DocSmart.docx` / `diseño de las pantallas.docx` — mockups visuales (6 vistas, contenido en imágenes)
- `Estructura base de la aplicación.docx` — diagrama de estructura (contenido en imagen)
- `Manual de marca DocSmart.docx` — manual de marca condensado
- `mejoras pendientes del proyecto.docx` — entregables académicos (Scrum, arquitectura, UI/UX)
- `mejoras pendientes del proyectoV1.0.1.docx` — hallazgos de QA detallados por pantalla
- `pendientes del roadmap.txt` — roadmap de producto por prioridad y versión
- `Primer escaneo de la aplicación docuSmart.docx` — scan histórico de CodeSentinel (mayo 2026, snapshot antiguo, mayormente superado por el estado actual)

Roadmap técnico de esta sesión: publicado como artifact —
https://claude.ai/code/artifact/03dc0ad3-98d9-4ae9-b4d1-72a2a88cbb32
