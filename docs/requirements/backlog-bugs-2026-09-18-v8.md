# Backlog de bugs — Undécima auditoría general (2026-09-18)

Ronda 11: 4 agentes de investigación en paralelo sobre áreas no auditadas a fondo en rondas anteriores (Escáner/OCR, QR + Visor + Búsqueda en Biblioteca, Notificaciones/Permisos/Backup/Ciclo de vida, Pomodoro + Carpeta Segura/PIN), combinada con una verificación E2E manual en el dispositivo físico (Motorola Edge 30 Neo).

## Verificación E2E en dispositivo (previa a esta ronda)

Antes de investigar, se confirmó que el CI de Gitleaks que había fallado en el commit `5c26f17` (ronda 10) fue una falla transitoria de infraestructura (el paso de descarga del binario de gitleaks falló en 73ms, antes de cualquier operación de red real completa) — no un secreto expuesto. Se re-ejecutó el job y pasó limpio, confirmando la hipótesis.

Se instaló el build más reciente en el dispositivo real y se verificó en vivo: Inicio, Escáner (cámara/permisos, sin capturar por falta de documento físico), Lector de QR, Carpeta Segura (pantalla de PIN, sin intentar desbloquear para no arriesgar el bloqueo por intentos fallidos en datos reales del usuario), Papelera (restaurar un documento real gestionado por MediaStore — confirmó que el fix P2 de la ronda 10 funciona correctamente en producción), Biblioteca (búsqueda, categorías), Ajustes (apariencia, almacenamiento). Cero crashes en logcat durante toda la sesión.

## Hallazgos — Escáner y OCR

- **H1 (Alta)** `ScannerScreen.kt`: rotar la pantalla durante la captura relanza el escáner de ML Kit por segunda vez (sin flag que sobreviva la recreación de la Activity). **Estado: ✅ Corregido**
- **H4 (Alta)** `ScanImageEditor.kt`: `OutOfMemoryError` no capturado al ajustar imágenes escaneadas de alta resolución (Error no es subclase de Exception). **Estado: ✅ Corregido**
- **H7 (Alta/Media)** `ScanResultScreen.kt`: doble toque en "Agregar página" puede lanzar el escáner dos veces sobre el mismo launcher. **Estado: ✅ Corregido**
- **H5 (Media/Alta)** `ScanResultScreen.kt`: ruta absoluta real de archivo en un log que Crashlytics sube a la nube. **Estado: ✅ Corregido**
- **H3 (Media)** `OcrPdfUseCase.kt`: el cliente `TextRecognizer` de ML Kit nunca se cierra (fuga de recursos nativos). **Estado: ✅ Corregido**
- **H6 (Media)** `ScanResultScreen.kt`: `CancellationException` tragada en `shareFileAwaitingSelection`. **Estado: ✅ Corregido**
- **H2 (Media)** `QrScreen.kt`: revocar el permiso de cámara en runtime deja una pantalla negra sin salida en el Lector de QR. **Estado: ✅ Corregido** (implementado junto con los hallazgos de QR)
- **H8 (Media/Baja)** `ScanSessionManager.kt`: el tamaño de archivo ignora el idioma elegido en la app (usa `@ApplicationContext`, no localizado). **Estado: ✅ Corregido (parcial, ver nota de la ronda)**

## Hallazgos — QR, Visor y Búsqueda en Biblioteca

- **H1 (Alta)** `PdfPageBitmap.kt`/`ViewerScreen.kt`: el Visor renderiza TODAS las páginas del PDF como bitmaps completos en memoria simultánea, sin reciclar. **Estado: ✅ Mitigado (ver nota — reescritura completa a renderizado perezoso queda fuera de alcance)**
- **H2 (Alta)** `QrScreen.kt`: el Creador de QR pierde todo el formulario al rotar la pantalla. **Estado: ✅ Corregido**
- **H3 (Media)** `QrScreen.kt`: el Lector de QR pierde el resultado escaneado al rotar. **Estado: ✅ Corregido**
- **H4 (Media/Alta)** `ViewerViewModel.kt`: carrera real en la búsqueda dentro de PDF (resultados viejos pueden pisar a los nuevos). **Estado: ✅ Corregido**
- **H5 (Media)** Varios: `CancellationException` tragada en 5 puntos del Visor (`SearchPdfTextUseCase`, `FlattenAnnotationsPdfUseCase`, `ViewerViewModel` ×4). **Estado: ✅ Corregido**
- **H6 (Media)** `QrScreen.kt`: los PNG generados/compartidos se acumulan para siempre en caché. **Estado: ✅ Corregido**
- **H7 (Baja)** `QrContentType.kt`: detección de vCard/vEvent sensible a mayúsculas/minúsculas, inconsistente con su propio parser inverso. **Estado: ✅ Corregido**
- **H8 (Media)** `ViewerScreen.kt`: el visor de texto/CSV/Markdown carga el archivo completo en memoria sin límite. **Estado: ✅ Corregido**

## Hallazgos — Notificaciones, permisos, backup y ciclo de vida

- **H1 (Media/Alta)** `LibraryScreen.kt`: Biblioteca queda atascada en "Sin permisos" si el usuario concede el permiso manualmente desde Ajustes del sistema sin salir de la pestaña. **Estado: ✅ Corregido**
- **H2 (Media)** `BootRescheduleReceiver.kt`: crash silencioso no manejado si falla la consulta a Room al reprogramar recordatorios tras un reinicio del dispositivo. **Estado: ✅ Corregido**
- **H3 (Media)** `StudyScreen.kt` (`NotesTab`): título, imágenes adjuntas y recordatorio de una nota en edición no sobreviven la muerte del proceso por memoria baja (solo el cuerpo del texto sí, vía `rememberSaveable`). **Estado: ✅ Corregido** (implementado junto con los hallazgos de Pomodoro/StudyScreen)
- **H4 (Media)** `QrScreen.kt`: la detección de denegación permanente del permiso de cámara falla en el primer intento de cada sesión nueva. **Estado: ✅ Corregido** (implementado junto con los hallazgos de QR)

No se encontraron hallazgos de código en el área de backup/WorkManager: el proyecto no usa `WorkManager`/`JobScheduler`/`BackupAgent`, y la purga perezosa de la Papelera es una decisión de producto ya documentada explícitamente en `TrashRepository.kt`, no un bug.

## Hallazgos — Pomodoro y Carpeta Segura/PIN

- **H1 (Alta)** `PomodoroEngine.kt`: el temporizador no usa ningún ancla de tiempo real; con el CPU dormido (pantalla apagada, sin wake lock) el conteo se desincroniza del tiempo real transcurrido. **Estado: ✅ Corregido**
- **H2 (Alta)** `PomodoroTimerService.kt`: fin de sesión/descanso totalmente silencioso — sin sonido, sin vibración, la notificación solo desaparece. **Estado: ✅ Corregido**
- **H3 (Media)** `StudyScreen.kt`: `CancellationException` tragada en la extracción de texto de Modo Lectura, pudiendo borrar el progreso de "Continuar leyendo" de un documento que nunca falló (solo se interrumpió por navegación). **Estado: ✅ Corregido**

La Carpeta Segura/PIN ya estaba notablemente endurecida por rondas previas (PBKDF2+salt, reloj de confianza, backoff de intentos, `FLAG_SECURE`, sin deep links externos hacia rutas internas) — no se encontraron hallazgos nuevos de seguridad real en esta ronda.

## Nota de alcance — Visor: renderizado perezoso de páginas (H1)

Reescribir el renderizado del Visor para que solo mantenga en memoria la página visible ± un margen pequeño (liberando bitmaps fuera de rango) es la corrección completa e ideal para el riesgo de OOM en documentos largos, pero implica un cambio arquitectónico sobre un componente central ya en producción. Para esta ronda se aplicó la mitigación de menor riesgo: liberación determinista (`bitmap.recycle()`) de todas las páginas cuando el documento cambia o el Visor se cierra, en vez de depender solo del recolector de basura. Esto no reduce el pico de memoria de un documento muy largo abierto de una vez, pero elimina la acumulación entre aperturas sucesivas de distintos documentos. La reescritura completa a renderizado perezoso queda pendiente para una futura ronda dedicada.

## Nota de alcance — Pomodoro: wake lock

Se evaluó agregar un `PARTIAL_WAKE_LOCK` para mantener el CPU despierto durante una sesión activa (evitaría por completo la desincronización, no solo corregirla al despertar). Se decidió no agregarlo en esta ronda: implica un nuevo permiso en el manifest con impacto en batería para sesiones de hasta 25 minutos, y el fix de ancla de tiempo real (H1) ya corrige la causa raíz de la inexactitud — el cronómetro mostrará el tiempo correcto en cuanto el dispositivo despierte, aunque no se actualice segundo a segundo mientras duerme. Queda documentado como posible mejora futura si se reciben reportes de usuarios sobre esto.
