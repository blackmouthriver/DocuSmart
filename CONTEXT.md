# DocuSmart — Contexto del proyecto

> Documento vivo de memoria del proyecto. Se actualiza a medida que avanzamos —
> requerimientos, decisiones, hallazgos de QA y roadmap en un solo lugar para no
> perder contexto entre sesiones. Fuentes originales: documentos en
> `C:\Users\HP\Desktop\proyectoDocSmart\` (ver [Fuentes](#fuentes-originales) al final).

**Última actualización:** 2026-08-24

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
  push/PR a `main`/`desarrollo`/`preproductivo`.
- **i18n:** 384 claves de string × 5 idiomas, las 7 pantallas con texto
  fijo ya conectadas a `stringResource()`. Verificado con paridad exacta.
- **Tests:** 0% cobertura real (solo `ExampleUnitTest.kt` de plantilla).
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

---

## 3. Requerimientos funcionales (fuente: mensaje del usuario, 2026-08-24)

| # | Requerimiento | Estado conocido |
|---|---|---|
| 1 | Visor universal: Word/Excel/PDF/img/texto, desde dispositivo, link, QR, correo, WhatsApp | Visor de PDF/imagen funciona; Word/Excel/PPT **con inconvenientes** (confirmado en barrido de pruebas) |
| 2 | Conversión: imágenes↔pdf/jpg/png/webp/bmp; pdf↔img/texto/word/html; word↔pdf/texto/html; ppt→pdf/texto | Solo un subconjunto implementado (img→pdf, pdf→img limitado, word→pdf/texto, excel→pdf/csv, ppt→pdf). Falta ampliar todos los combos |
| 3 | Herramientas PDF: unir, dividir, comprimir, rotar, editar, firmar, marca de agua, numeración, detector de formularios, recortar, ordenar, proteger con contraseña, OCR avanzado | Unir/dividir/comprimir/rotar/proteger existen pero **con bugs** (ver §5). Editar, firmar, marca de agua, numeración, formularios, recortar, ordenar, OCR avanzado: **no implementados** |
| 4 | Biblioteca con lista navegable, filtro por formato | Implementado, con bugs de favoritos (§5) |
| 5 | Sub-menú por documento: abrir, favorito, renombrar, compartir, convertir, crear QR; favoritos visibles en biblioteca | Parcial — falta renombrar; favoritos no persisten al salir de la vista |
| 6 | Buscador en vistas relevantes | Funciona en Biblioteca; **no funciona en el Visor** (bug confirmado) |
| 7 | Accesos rápidos | Implementados visualmente; varios no llevan a ninguna pantalla real (scanner, seguridad, estudio aislados) |
| 8 | Acceso directo a abrir/convertir | Implementado (banner Home) |
| 9 | Escanear/foto/leer QR/crear QR, guardado en biblioteca y recientes | Escáner funciona bien; falta leer QR con URL/navegación y QR con contraseña compartible |
| 10 | Seguridad: contraseña para PDF y QR, carpeta segura con PIN/huella | Contraseña PDF implementada hoy (i18n); **carpeta segura no bloquea realmente el acceso al archivo por su ruta original** (bug crítico confirmado) |
| 11 | Modo estudio: lectura (con voz), notas (texto y voz), Pomodoro | Implementado y ya i18n; falta guardar/listar notas (¡ya corregido — ver StudyScreen actual, tiene lista de notas guardadas!) — verificar que el barrido de pruebas quedó desactualizado en este punto |
| 12 | Ajustes: idioma, tema, almacenamiento, privacidad, tutorial, ayuda, compartir, calificar, restablecer, acerca de, premium | Implementado (Settings ya conectado a i18n hoy) |
| 13 | Multilenguaje con default según ubicación geográfica de Play Store | **Pendiente** — hoy el idioma por defecto es fijo (español), falta detectar locale del dispositivo/tienda |
| 14 | Sección de beneficios plan de pago | Implementado (Premium screen) |
| 15 | Banner para no-premium, desaparece al pagar | Implementado (AdMob banner condicional) |
| 16 | Límite de uso de herramientas para no-premium | **Pendiente** — está en el roadmap (V1.1, prioridad alta) |
| 17 | Mínima opción de uso garantizada sin pago | Por definir junto con el límite de uso |
| 18 | Restaurar compras / cancelar suscripción | Restaurar existe (simulado); falta conectar a Play Billing real |
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

### Home
- Botón "Abrir" del banner no genera ninguna acción.
- Accesos rápidos de scanner/seguridad/estudio no están atados a ninguna pantalla (aislados).
- Favorito (corazón) en recientes no persiste al salir de la vista.
- Botón "Inicio" de la bottom nav deja de responder después de ir a Convertir (a verificar si sigue vigente).
- Falta logo de marca en el banner azul (estandarizar en todas las vistas).

### Visor
- Búsqueda no tiene función real.
- Favorito no persiste.
- No permite renombrar ni eliminar desde el visor.
- Margen superior falla, el PDF "se pierde" arriba.
- Word/Excel/texto/PowerPoint presentan inconvenientes (solo PDF/imagen confiables).

### Biblioteca
- Favoritos no persisten al salir de la vista.
- Tarjetas de favoritos con tamaños inconsistentes en el carrusel horizontal.
- Formatos en carrusel esconden opciones — sugerido: grilla en vez de carrusel.
- Falta discriminar "archivos creados por la app" vs. "archivos del dispositivo".

### Convertidor
- Muy pocas opciones de conversión por formato (2-3 cuando el requerimiento pide más).
- Banner de publicidad no se visualiza en esta pantalla.
- Vista en carrusel se ve vacía — sugerido grilla/lista.

### Herramientas PDF
- **Dividir PDF no funciona** — genera el mismo PDF sin dividir.
- **Comprimir PDF** no indica dónde queda guardado el archivo, y no ofrece compartir/descargar tras comprimir.
- **Rotar PDF**: la vista previa no refleja la rotación real en grados.
- Nombre de archivo antepone "DocuSmart_" automáticamente (confirmar si es deseado).
- Faltan: contraseña, quitar contraseña, eliminar página, reordenar, firma, recorte, marca de agua, numeración, editar, formularios, comparar, censurar.

### Seguridad
- Banner con botón "volver" mal ubicado (reduce tamaño del banner) — sugerido: breadcrumb.
- Falta logo corporativo en el banner.
- **Bug crítico:** un archivo "protegido" sigue siendo accesible directamente desde su ruta original — la protección no bloquea el acceso real.
- Selector de archivo a proteger no ofrece elegir desde la biblioteca de la app, solo desde el dispositivo.
- Falta opción explícita de encriptar/quitar contraseña de archivo individual.

### Escáner
- Funciona bien (captura, recorte, rotar, filtros, aplicar, guardar/compartir).
- Miniatura de filtros no refleja la imagen real capturada.
- Faltan: escalar imagen, ajuste de brillo/contraste, lector de QR con navegación a URL, QR con contraseña compartible.

### Estudio
- (Documento de mayo indica que faltaba guardado/lista de notas — **el código actual ya tiene lista de notas guardadas**, parece corregido; confirmar con el usuario si ya lo probó en la versión actual).
- Lectura se ve como texto plano — mejorar presentación visual.

### Ajustes
- Idioma: falta detección geográfica automática y más idiomas (agregar portugués, alemán, ruso, japonés, coreano, mandarín, italiano, francés — **es/en/de/pt/ru ya están, faltan ja/ko/zh/it/fr para el pedido completo**).
- Falta personalización de colores/estilos por el usuario (banner, botones, iconos, nav bar).

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

### Roadmap técnico (definido en esta sesión, complementario)
Fase 0 (estabilización) ✅ completada. Fase 1 (CI básico) ✅ completada hoy.
Siguen: pruebas unitarias de UseCases críticos, HU con criterios de
aceptación, Dependabot/Gitleaks/SonarCloud, Room para biblioteca/historial,
pruebas de integración/sistema, despliegue y publicación.

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
