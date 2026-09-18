# Backlog de bugs — Duodécima auditoría general (2026-09-18)

Ronda 12: 4 agentes de investigación en paralelo sobre áreas no auditadas a fondo en rondas anteriores (Convertidor en profundidad, Monetización/Premium/Billing, Agenda en profundidad, Accesibilidad TalkBack sistemática), combinada con una verificación E2E en el dispositivo físico (Motorola Edge 30 Neo) que incluyó una conversión real de PDF (Rotar PDF) ejecutada de punta a punta.

## Hallazgos — Convertidor en profundidad

- **H1 (Alta)** 14 `UseCase` de conversión + `ConverterViewModel.kt`: `CancellationException` nunca se relanza — "Cancelar" no cancela nada, y un lote sigue consumiendo cupo diario después de que el usuario se fue de la pantalla. **Estado: ✅ Corregido**
- **H2 (Media-Alta)** `PdfToWordUseCase.kt`: `.docx` huérfano/corrupto si falla a mitad de escritura (el `File` de salida se declaraba dentro del `try`, sin poder borrarlo en el catch). **Estado: ✅ Corregido**
- **H3 (Alta)** `ConvertImageToPdfUseCase.kt`, `ConversionSuccess.kt` (×2): URIs/rutas reales de archivos del usuario expuestas a Crashlytics vía Timber. **Estado: ✅ Corregido**
- **H4 (Media)** `ExcelToCsvUseCase.kt`: el `Workbook` no se cierra si algo lanza a mitad de conversión. **Estado: ✅ Corregido**
- **H5 (Media)** `ExcelToHtmlUseCase.kt`: bufferea en memoria todas las hojas del workbook aunque solo usa una. **Estado: evaluado, ver nota de alcance**
- **H6 (Baja/Media)** `ImageFormatUseCase.kt`: `bitmapToBmp()` triplica el uso de memoria para imágenes de alta resolución. **Estado: evaluado, ver nota de alcance**

## Hallazgos — Monetización/Premium/Billing

- **H1 (Alta)** `BillingManager.kt`: `readyDeferred` (resoluble una sola vez) deja "Restaurar compras" roto para siempre si la primera conexión con Play Billing falla — impacto real en reinstalaciones/cambios de dispositivo con mala señal. **Estado: ✅ Corregido**
- **H2 (Media)** `BillingManager.kt`: sin manejador de excepciones en el `scope` — una excepción real de Play Billing (no solo un `BillingResponseCode` de error) puede tumbar la app entera, incluso en `onCreate()`. **Estado: ✅ Corregido**
- **H3 (Media)** Una compra `PENDING` se trata igual que "nunca compró nada" en revalidaciones posteriores — relevante para métodos de pago que tardan en confirmar. **Estado: ✅ Corregido**
- **H4 (Media)** El trial automático de 3 días no degrada `isPremium` a `false` si la app queda en primer plano sin interrupción durante todo el trial. **Estado: ✅ Corregido**
- **H5 (Baja)** `acknowledgePurchase()` fallido no se reintenta. **Estado: ver resumen del agente**
- **H6 (Baja)** Posible doble evento de analítica en una carrera estrecha entre el listener de compras y una revalidación simultánea. **Estado: ver resumen del agente**

## Hallazgos — Agenda en profundidad

- **H1 (Alta)** `AgendaRepository.kt`: condición de carrera entre editar/eliminar el mismo evento sin serialización — puede resucitar un evento borrado con una alarma huérfana activa. **Estado: ✅ Corregido**
- **H2 (Media)** `ReminderScheduler.kt`: `PendingIntent` sin protección contra colisión de `hashCode()` entre eventos distintos (mismo problema ya corregido en `AgendaReminderReceiver.kt`, no propagado acá). **Estado: ✅ Corregido**
- **H3 (Media)** `AgendaCalendarView.kt`: el calendario no anuncia "seleccionado" a TalkBack, además de falta de `role` y objetivo táctil pequeño. **Estado: ✅ Corregido**
- **H4 (Media)** `AgendaEventCard.kt`: la insignia VENCIDO/HOY/PRÓXIMO no se refresca al cruzar la medianoche (mismo problema ya corregido para el círculo "hoy" del calendario, no propagado acá). **Estado: ✅ Corregido**
- **H5 (Media)** `AgendaScreen.kt`: rotar el dispositivo tras cerrar un editor abierto desde notificación lo reabre (`openEventId` nunca se consume). **Estado: ✅ Corregido**
- **H6 (Media, accesibilidad)** `AgendaEventCard.kt` sin `role = Role.Button`. **Estado: ✅ Corregido**

## Hallazgos — Accesibilidad TalkBack sistemática

24 hallazgos across Inicio, Biblioteca, Convertidor, Ajustes, Agenda y componentes compartidos. El patrón dominante: falta sistemática de `role = Role.Button`/`Role.RadioButton` en elementos `.clickable`/`.combinedClickable` en toda la app (aplicado correctamente en componentes nuevos como `LibraryTabItem`/`DocuSmartBottomBar`, nunca propagado al resto), y estado "seleccionado" invisible para TalkBack en selectores de color de acento, calendario e idioma.

- **H1-H11, H13-H15, H17, H20-H23 (20 hallazgos, Alta/Media/Baja)**: componentes compartidos y pantallas de Inicio/Biblioteca/Convertidor/Ajustes. **Estado: ✅ Corregidos** (ver resumen del agente para detalle archivo:línea de cada uno)
- **H16, H18**: calendario y tarjeta de evento de Agenda — corregidos junto con los hallazgos propios de Agenda (mismo archivo, evitando conflicto entre agentes en paralelo).
- **H12 (Media)**: falta de `liveRegion` en las pantallas de progreso/éxito del Convertidor — **fuera de alcance de esta ronda** (archivo compartido con el grupo de corrección del Convertidor, que no lo cubrió explícitamente; queda pendiente para una futura ronda).
- **H19 (Media)**: falta de `paneTitle` en el editor de eventos de Agenda — **fuera de alcance de esta ronda**, documentado para el futuro.
- **H24 (Media, sin confirmar)**: posible contraste insuficiente de texto blanco semitransparente sobre el degradado de color de acento (variable según elección del usuario en Ajustes) — necesita verificación visual con los acentos más claros antes de decidir un fix; **queda documentado, no corregido**.

## Revisión adversarial (seguridad + correctitud)

2 agentes en paralelo revisaron el diff completo de esta ronda. Encontraron el mismo hallazgo real desde dos ángulos distintos, más una regresión Alta que invalidaba uno de los propios fixes de esta ronda:

- **Alta** — `AgendaRepository.kt`: el `Mutex` por `eventId` (hallazgo Agenda H1) usaba `getOrPut` de `kotlin.collections.*`, que NO es atómico incluso sobre un `ConcurrentHashMap` — dos corrutinas pidiendo el lock del mismo evento por primera vez podían crear dos `Mutex()` distintos y correr en paralelo sin exclusión mutua real, exactamente la carrera que el fix decía resolver. **Estado: ✅ Corregido** (`computeIfAbsent`, atómico nativo de `ConcurrentHashMap`).
- **Media** — `PremiumManager.kt`: carrera real entre `activatePremium()`/`deactivatePremium()` (llamados desde `BillingManager`, hilo IO) y el nuevo `scheduleAutoTrialExpiryCheck()` (hilo Default, hallazgo Billing H4) — un usuario comprando justo en el instante en que expira su trial automático podía terminar viéndose "no premium" pese a la compra recién confirmada. **Estado: ✅ Corregido** (`synchronized` compartido entre las tres escrituras de estado).

## Herramientas nuevas en el gauntlet (pedido explícito del usuario, 2026-09-18)

Se agregaron **ktlint**, **Spotless** y **Kover** al proyecto (plugins + configuración en `build.gradle.kts`/`app/build.gradle.kts`/`gradle/libs.versions.toml`), verificados funcionando (`ktlintCheck`, `spotlessCheck`, `koverXmlReport` corren correctamente). Kover 0.9.1 tenía un bug real de compatibilidad (`ConcurrentModificationException` en `kotlinx.kover.gradle.plugin.locators.AndroidKt`) con este proyecto (AGP 8.13.2 + testFixtures) — resuelto subiendo a 0.9.9. La metadata de verificación de dependencias (`gradle/verification-metadata.xml`) se regeneró para cubrir las nuevas dependencias.

**No se autoformateo el código existente**: el código nunca fue formateado con ktlint antes, así que `ktlintCheck`/`spotlessCheck` reportan un backlog real de violaciones de estilo en todo el proyecto (confirmado, no hipotético). Corregir eso es un cambio masivo y no relacionado con esta ronda de bugs — queda pendiente para una decisión explícita del usuario (aplicar `ktlintFormat`/`spotlessApply` de una vez, o ir corrigiendo gradualmente).

## Verificación E2E en dispositivo

Dos pasadas (antes y después de las correcciones de la revisión adversarial), cero crashes (`FATAL EXCEPTION`) en logcat en ambas.

**Antes de corregir**: arranque en frío sin crash. Convertidor: navegación completa por las 5 categorías de conversión de imagen, selector de archivos de DocuSmart (Biblioteca interna) funcionando. Herramientas PDF: **conversión real ejecutada de punta a punta** (Rotar PDF 90° sobre un documento real de la Biblioteca) — resultado correcto, contador de límite diario ("Usos hoy: 1/3") funcionando, opciones de Guardar/Compartir/Nueva operación.

**Después de corregir** (build final instalado): arranque en frío, Seguridad (Carpeta Segura/Contraseña PDF), Pomodoro, Agenda (Lista con datos reales, Calendario) — la selección de un día del calendario confirmó en vivo que el fix de accesibilidad H16 funciona (`checked="true"` en el nodo clickeable tras seleccionar, visible también como el círculo sólido vs. el círculo tenue de "hoy"), Premium (carga limpia, sin la tarjeta de "pago pendiente" al no haber ninguna compra pendiente real, confirmando que el nuevo estado no aparece espuriamente en el camino normal).
