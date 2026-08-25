# DocuSmart — Contexto del proyecto

> Documento vivo de memoria del proyecto. Se actualiza a medida que avanzamos —
> requerimientos, decisiones, hallazgos de QA y roadmap en un solo lugar para no
> perder contexto entre sesiones. Fuentes originales: documentos en
> `C:\Users\HP\Desktop\proyectoDocSmart\` (ver [Fuentes](#fuentes-originales) al final).

**Última actualización:** 2026-08-24 (limpieza de lint + bug de i18n en barra inferior + endurecimiento de carpeta segura + idioma por defecto en instalación nueva)

**Specs por módulo (FR/NFR + HU con criterios de aceptación):**
- [`docs/requirements/security.md`](docs/requirements/security.md) — Carpeta Segura, contraseña PDF, QR protegido (en refinamiento)
- [`docs/requirements/pdf-tools.md`](docs/requirements/pdf-tools.md) — Unir, Dividir, Comprimir, Rotar PDF (en refinamiento)
- [`docs/requirements/visor-biblioteca.md`](docs/requirements/visor-biblioteca.md) — Visor, Biblioteca, Home/Recientes (en refinamiento)
- [`docs/requirements/conversion.md`](docs/requirements/conversion.md) — 17 combinaciones de conversión (en refinamiento)
- [`docs/requirements/scanner.md`](docs/requirements/scanner.md) — Escanear documento (ML Kit) + lector/creador de QR (en refinamiento)
- [`docs/requirements/settings-premium.md`](docs/requirements/settings-premium.md) — Ajustes + Premium/límites de uso (en refinamiento)
- [`docs/requirements/study.md`](docs/requirements/study.md) — Lectura con voz, notas, Pomodoro (en refinamiento)

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
  Dependabot y escaneo de secretos con Gitleaks.
- **i18n:** 384 claves de string × 5 idiomas, las 7 pantallas con texto
  fijo ya conectadas a `stringResource()`. Verificado con paridad exacta.
- **Tests:** 81 tests reales (Seguridad: 24, Herramientas PDF: 8, Visor+Biblioteca: 10, Conversión: 9, Escáner: 10, Ajustes+Premium: 14, Estudio: 5, ejemplo: 1), 0 fallos, verificado corriendo la suite completa en `main` ya fusionado. Cobertura aún baja en proporción al total de use cases del proyecto (~4.4% de líneas, ver §8 SonarCloud).
- **Base de datos:** no hay — todo en SharedPreferences/DataStore.
  Biblioteca/historial no están indexados de forma estructurada.
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
no se implementó en esta pasada. 11 tests nuevos.
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

### Avance por dimensión (estimado 2026-08-24)
No hay un único "% completado" honesto — depende del eje:

| Dimensión | Avance | Nota |
|---|---|---|
| Infraestructura y calidad base | ~90% | build estable, CI, i18n completo, 1er módulo con HU+tests |
| Funcionalidad core (25 requerimientos) | ~60-65% | ~11 sólidos, ~9 parciales con bugs, ~2-3 sin empezar |
| Documentación formal (HU con criterios de aceptación) | ~85% | 7 de ~7-8 módulos formalizados (Seguridad, Herramientas PDF, Visor+Biblioteca, Conversión, Escáner, Ajustes+Premium, Estudio) |
| Pruebas automatizadas | ~19% | 77 tests cubriendo 17 archivos de decenas |
| Listo para publicar en Play Store | ~30% | falta billing real, política de privacidad, formulario de seguridad de datos, límites premium, ads de producción |

**Estimado global "producto listo para producción": ~35-40%.** No es un problema
de código faltante — es que lo que falta (bugs en funciones centrales, billing
simulado, casi sin red de pruebas) es justo lo que separa un prototipo funcional
de un producto publicable.

### Mejoras y funcionalidades candidatas a agregar a las HU
Por módulo, sin refinar aún — para retomar al planear el siguiente sprint:

- **Seguridad:** cambiar PIN sin borrar archivos (hoy solo "restablecer" destructivo),
  backup/exportación cifrada de la carpeta segura, registro de último acceso.
- **Herramientas PDF:** dividir/comprimir/rotar ya verificados y corregidos
  (ver módulo arriba); pendiente agregar numeración de páginas y marca de agua
  (rápidas, alto valor) antes que firma digital u OCR avanzado (más costosas);
  comparar/censurar PDF como diferencial (backlog en `pdf-tools.md`).
- **Conversión:** PDF → Word editable (killer feature premium), conversión por
  lotes, conversión en segundo plano para archivos grandes.
- **Visor/Biblioteca:** ya refinado (ver módulo abajo) — pendiente: renombrar/
  eliminar desde el Visor (RF-VIS-06), resaltado inline de búsqueda en PDF
  (RF-VIS-08), papelera de reciclaje (RF-VIS-07) (backlog en `visor-biblioteca.md`).
- **Estudio:** exportar notas, estadísticas de estudio (tiempo leído, pomodoros/semana).
- **Premium:** conectar Play Billing real y límite diario no-premium (bloqueantes
  para publicar), programa de referidos.
- **Transversal:** estandarizar banner azul en todas las vistas (pedido repetido
  en QA), accesibilidad (TalkBack, fuentes dinámicas), completar idiomas
  pendientes (ja/ko/zh/it/fr).

---

## 3. Requerimientos funcionales (fuente: mensaje del usuario, 2026-08-24)

| # | Requerimiento | Estado conocido |
|---|---|---|
| 1 | Visor universal: Word/Excel/PDF/img/texto, desde dispositivo, link, QR, correo, WhatsApp | Visor de PDF/imagen funciona bien; Word/Excel/PPT con inconvenientes por confirmar (no se tocó en esta pasada). Refinado con HU en [`docs/requirements/visor-biblioteca.md`](docs/requirements/visor-biblioteca.md) |
| 2 | Conversión: imágenes↔pdf/jpg/png/webp/bmp; pdf↔img/texto/word/html; word↔pdf/texto/html; ppt→pdf/texto | Las 17 combinaciones ya estaban declaradas y visibles — el problema real era enrutamiento incorrecto (3 opciones daban el formato equivocado) y un bug de dependencias que rompía Word→PDF/Excel→PDF en silencio. Corregido y refinado con HU (ver [`docs/requirements/conversion.md`](docs/requirements/conversion.md)) |
| 3 | Herramientas PDF: unir, dividir, comprimir, rotar, editar, firmar, marca de agua, numeración, detector de formularios, recortar, ordenar, proteger con contraseña, OCR avanzado | Unir/dividir/comprimir/rotar/proteger implementados y refinados con HU (ver [`docs/requirements/pdf-tools.md`](docs/requirements/pdf-tools.md)) — bug de arquitectura en Unir/Rotar corregido hoy (rasterizaban a imagen), "dividir no funciona" confirmado obsoleto con tests. Editar, firmar, marca de agua, numeración, formularios, recortar, ordenar, comparar, censurar, OCR avanzado: **backlog documentado, no implementado** |
| 4 | Biblioteca con lista navegable, filtro por formato | Implementado, incluyendo pestañas dispositivo/app (ya existían, confirmado). Refinado con HU |
| 5 | Sub-menú por documento: abrir, favorito, renombrar, compartir, convertir, crear QR; favoritos visibles en biblioteca | Favorito ahora consistente entre Visor/Biblioteca/Home (bug de id corregido hoy); falta renombrar/eliminar desde el Visor específicamente (backlog) |
| 6 | Buscador en vistas relevantes | Funciona en Biblioteca; en el Visor **corregido hoy** — antes el botón aparecía habilitado para PDF pero no hacía nada, ahora busca por página con iText7 y navega entre coincidencias |
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
| 18 | Restaurar compras / cancelar suscripción | Restaurar existe (simulado); falta conectar a Play Billing real — confirmado como placeholder ya documentado en el código, no un bug oculto (backlog RF-PREM-05) |
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
- Visor: no permite renombrar ni eliminar desde el visor — confirmado vigente, backlog (RF-VIS-06).
- Visor: margen superior falla, el PDF "se pierde" arriba — no verificado, requiere prueba visual.
- Word/Excel/texto/PowerPoint presentan inconvenientes (solo PDF/imagen confiables) — no verificado en esta pasada.
- ~~Biblioteca: falta discriminar "archivos creados por la app" vs. "archivos del dispositivo".~~ **Obsoleto** — ya implementado (pestañas `LibraryTab.DEVICE`/`APP_FILES`).
- Tarjetas de favoritos con tamaños inconsistentes en el carrusel horizontal — ajuste visual, no funcional, fuera de alcance de esta pasada.
- Formatos en carrusel esconden opciones — sugerido: grilla en vez de carrusel — pendiente.

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
- Faltan (backlog documentado, RF-PDF-06 a RF-PDF-15): numeración, marca de agua, reordenar/eliminar página, recorte, editar, firma, formularios, comparar, censurar, OCR avanzado.
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
- Compra Premium simulada — confirmado como placeholder ya documentado en el código ("Fase 10 se conecta Play Billing real"), requiere configuración de Play Console, no implementado en esta pasada.

### General / transversal
- Banner de anuncios: ubicarlo consistente (arriba antes del banner azul, o abajo cerca de la nav bar) en todas las vistas, y ocultarlo por completo para usuarios premium.
- Estandarizar el banner azul (logo + título) en todas las pantallas.

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
`feature/lint-cleanup`, sin push ni merge a `main` todavía — pendiente de
que el usuario decida.

### Roadmap técnico (definido en esta sesión, complementario)
Fase 0 (estabilización) ✅ completada. Fase 1 (CI básico) ✅ completada.
Fase 2 (Dependabot + Gitleaks) ✅ completada 2026-08-24, rama
`feature/dependabot-gitleaks`. Fase 3 (SonarCloud + cobertura) ✅ completada
2026-08-24, rama `feature/sonarcloud-coverage` — ver detalle abajo. Siguen:
Room para biblioteca/historial, pruebas de integración/sistema, Compose UI
Testing en flujos críticos, despliegue y publicación.

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
