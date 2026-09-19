# Backlog de bugs — Decimocuarta auditoría general (2026-09-19)

Ronda 14: a diferencia de las rondas anteriores (áreas elegidas por criterio manual), esta ronda usó datos reales del reporte de cobertura JaCoCo (recién arreglado en esta misma sesión, ver `project_sonarcloud_coverage_fixed.md`) para identificar archivos de lógica de negocio con 0-2% de cobertura de tests, bajo la premisa de que el código sin testear es donde más probablemente se esconden bugs sin detectar. 4 agentes en paralelo investigaron y corrigieron directamente (no solo catalogaron) los hallazgos en sus áreas, y escribieron tests unitarios nuevos y significativos para la lógica antes sin cubrir.

## Hallazgos — Recordatorios y notificaciones (Agenda, Notas, Pomodoro)

- **H1 (Alta)** `NoteReminderScheduler.kt`: `PendingIntent` sin `data` única (solo `noteId.hashCode()` como requestCode) — dos notas con hashCode colisionado compartían el mismo `PendingIntent`, y `FLAG_UPDATE_CURRENT` reemplazaba en silencio la alarma de una por la otra. **Estado: ✅ Corregido** — `data = Uri.parse("docusmart://note-reminder/$noteId")`, igual que ya tenía `ReminderScheduler` (Agenda).
- **H2 (Alta)** `PomodoroTimerService.kt`: `PomodoroEngine.tick()` llama `stopService()` de forma síncrona apenas termina un bloque, mientras el aviso sonoro/vibración depende de un colector async — si `stopService()` ganaba la carrera, `onDestroy()` cancelaba el scope antes de que el colector alcanzara a avisar, reintroduciendo por una carrera el bug de "fin de sesión silenciosa" que una ronda anterior ya había corregido. **Estado: ✅ Corregido** — `flushPendingCompletionAlert()` en `onDestroy()`, lectura síncrona de `completionEvents.replayCache`.
- **H3 (Media)** `ReminderScheduler.kt`/`NoteReminderScheduler.kt`: logs con `eventId`/`noteId` reales interpolados en el mensaje — `CrashlyticsTree` reenvía todo log ≥WARN a Firebase Crashlytics. **Estado: ✅ Corregido**, redactado a solo el tipo de excepción.
- **H4 (Media)** `ReminderScheduler.kt`/`NoteReminderScheduler.kt`: `cancel()` solo llamaba `manager.cancel(pendingIntent)`, nunca `pendingIntent.cancel()` — el token quedaba vivo indefinidamente en la tabla interna del sistema tras cada recordatorio borrado/reprogramado. **Estado: ✅ Corregido**.

## Hallazgos — Favoritos y compartir/descargas

- **H1 (Alta)** `DocumentSharing.kt`: `shareDocument()` decidía `content://` vs. archivo real esperando que `Uri.parse()` lanzara excepción para el segundo caso — pero `Uri.parse()` nunca lanza. Para documentos generados localmente por la app (Convertidor/Herramientas PDF/Escáner), la rama de `FileProvider` nunca se ejecutaba: **"Compartir" fallaba en silencio para todos los documentos generados por la app**. **Estado: ✅ Corregido** — decide por el `scheme` real de la Uri, extraído a `resolveShareUri()` testeable.
- **H2 (Media)** `DownloadsSaver.kt`: `CancellationException` atrapada por `catch (Exception)` genérico en `saveFile()`/`saveUri()`. **Estado: ✅ Corregido**.
- **H3 (Media)** `DownloadsSaver.kt`: log con ruta real del archivo/carpeta sin redactar. **Estado: ✅ Corregido**.
- **H4 (Media)** `DownloadsSaver.kt`: rama legacy (Android 9-) usaba `overwrite = true` — dos documentos con el mismo nombre, el segundo pisaba el primero en silencio. **Estado: ✅ Corregido**, `uniqueDestination()` (mismo patrón que `SecurityManager`).
- **H5 (Baja, defensa en profundidad)** `DownloadsSaver.kt`: `displayName` no pasaba por `sanitizeOutputFileName()`. **Estado: ✅ Corregido**.

## Hallazgos — Convertidor (casos de uso sin cobertura previa)

- **H1 (Alta)** `ExcelToPdfUseCase.kt`: `writeWorkbookToPdf()` devolvía éxito con solo poder *abrir* el Workbook, sin chequear contenido real — un `.xlsx` con todas las celdas en blanco "convertía" con éxito a un PDF casi vacío, sin avisar (a diferencia de `ExcelToCsvUseCase`/`ExcelToHtmlUseCase`, que sí devuelven error de hoja vacía). **Estado: ✅ Corregido**.
- **H2 (Alta)** `ConvertImageToPdfUseCase.kt`: `outputFile` se creaba como `val` dentro de `writePdfDocumentToFile()` — si `pdfDocument.writeTo(stream)` fallaba a mitad de escritura, ningún catch podía referenciarlo para borrar el `.pdf` parcial/corrupto huérfano (mismo patrón ya corregido en otros 4 conversores, no propagado acá). **Estado: ✅ Corregido**, `outputFile` movido a `var` en `invoke()`.
- **H3 (Alta)** `ConvertImageToPdfUseCase.kt`: `loadBitmapFromUri()` atrapaba `CancellationException` con su propio `catch (Exception)` interno sin relanzarla — el fix ya aplicado en `invoke()` no se había propagado a este método anidado. **Estado: ✅ Corregido**.

`PdfToHtmlUseCase.kt` y `ExcelToHtmlUseCase.kt` se revisaron a fondo (mismos patrones) y ya tenían todos los fixes de sus hermanos propagados correctamente — solo les faltaban tests, agregados en esta ronda.

## Hallazgos — Multimedia, permisos y anuncios

- **H1 (Media)** `PermissionHandler.kt`: `Timber.e(e, "...${e.message}")` en los catch de `takePersistableReadPermission()`/`revokePermission()` — pasaba el `Throwable` completo (reenviado a Crashlytics vía `recordException()`) y el mensaje de una `SecurityException`/`FileNotFoundException` de permisos de URI casi siempre incluye la URI real del documento del usuario. **Estado: ✅ Corregido**, `redactedForLog(e)`.
- **H2 (Media)** `PdfPageBitmap.kt` (`copyContentUriToCache`): mismo patrón — log con mensaje de excepción que suele incluir la URI real. **Estado: ✅ Corregido**.
- **H3 (Alta)** `AdManager.kt`: dos condiciones de carrera reales al mostrar anuncios a pantalla completa — (a) ningún método validaba que la Activity siguiera viva antes de `.show(activity)` (crash real conocido de AdMob, `BadTokenException`), (b) sin guard contra reentrancia, dos conversiones casi simultáneas o doble-toque podían llamar `.show()` dos veces sobre el mismo anuncio. **Estado: ✅ Corregido** — `canShowFullScreenAd(activity)` + `AtomicBoolean isFullScreenAdShowing` con `compareAndSet`.

**Verificado sin bugs**: `ScanSessionManager.kt` (la limpieza de sesión al salir y la fuga de temporales, bugs históricos reales, ya están resueltas en la capa de UI, fuera del alcance de esta ronda). `SoundEffectPlayer.kt` sigue usando `USAGE_MEDIA` correctamente (bug histórico ya corregido, sin regresión).

## Revisión adversarial (2 agentes en paralelo: seguridad + correctitud)

Aplicada sobre los propios fixes de esta ronda, antes de pedir fusión — mismo patrón usado en las 13 rondas anteriores. Ambos agentes encontraron **de forma independiente** el mismo hallazgo real:

- **A1 (Media/Baja según el agente, tratado como real)** `PomodoroTimerService.kt`: el propio fix de H2 (arriba, `flushPendingCompletionAlert()`) no tenía ninguna exclusión mutua con el colector normal de `completionEvents` — en una ventana angosta pero real (microsegundos entre que el colector llama `postCompletionAlert()` y `consumeCompletionEvent()`), ambos caminos podían publicar la misma alerta de "bloque terminado" **dos veces** en vez de una. **Estado: ✅ Corregido** — `Mutex` (`alertMutex`) compartido: el colector usa `withLock{}` (suspend), `flushPendingCompletionAlert()` usa `tryLock()`/`unlock()` (no-suspend, ya que `onDestroy()` no es una función suspend) y se retira sin hacer nada si el colector ya tiene el lock (va a terminar de procesar el evento él solo).
- **Hallazgo de cobertura (Baja, documentado, no corregido)**: el `AtomicBoolean isFullScreenAdShowing` de `AdManager.kt` (el guard central del hallazgo H3 de Multimedia) no tiene ningún test directo ni indirecto, porque vive embebido en funciones que no se pueden instanciar en JVM puro (`Handler(Looper.getMainLooper())` en el constructor de `AdManager`, mismo límite ya documentado para otros use cases). No es un bug de producción, es un hueco de cobertura conocido — mismo criterio que otros límites de Robolectric ya aceptados en el proyecto.
- **Verificado sin regresiones**: el refactor del coordinador en `AdManager.onConversionCompleted()`/`showRewardedAd()` (de 3 a 2 `return` statements para pasar detekt `ReturnCount`) preserva exactamente la lógica original — confirmado independientemente por ambos agentes.
- **3 tests con errores de fixture corregidos por el coordinador** (no bugs de producción): `PdfToHtmlUseCaseTest` (Context mockeado sin `cacheDir`), `ExcelToHtmlUseCaseTest` (fórmula puesta en la fila 0, que siempre se renderiza como encabezado `<th>` por diseño), `ConvertImageToPdfUseCaseTest` (el test de cancelación pasaba por `android.graphics.pdf.PdfDocument` real, no mockeable en JVM — su `.close()` en un `finally` enmascaraba la `CancellationException` real que se quería probar; resuelto exponiendo `loadBitmapFromUri()` como `internal` para testearla en aislamiento).

## Gauntlet y verificación

Gauntlet completo (`compileDebugKotlin`, `detekt`, `lintDebug`, `testDebugUnitTest`, `compileDebugAndroidTestKotlin`) en verde, corrido de forma centralizada tras que los 4 agentes de investigación/fix stallearan por contención de RAM al correr Gradle en paralelo (problema ya documentado de esta máquina, ver `project_ram_constrained_dev_machine`) — se les pidió no volver a correr Gradle y dejar la verificación final centralizada.

Verificado en dispositivo real (Motorola Edge 30 Neo, `ZY22G7SB77`): APK debug instalado y reiniciado en frío, cero `FATAL EXCEPTION` y cero ANR en todo el logcat de la sesión. Confirmado visualmente sin crashes: Inicio, pantalla de Pomodoro (el área del fix de la carrera A1).

## ~18 archivos de test nuevos/ampliados (cobertura real, antes 0-2%)

`ReminderSchedulerTest`, `NoteReminderSchedulerTest`, `AgendaReminderReceiverTest`, `NoteReminderReceiverTest`, `BootRescheduleReceiverTest`, `PomodoroTimerServiceTest`, `FavoritesRepositoryTest`, `DocumentSharingTest`, `DownloadsSaverTest`, `DownloadsAccessManagerTest`, `PdfToHtmlUseCaseTest`, `ExcelToHtmlUseCaseTest`, `ExcelToPdfUseCaseTest`, `ConvertImageToPdfUseCaseTest`, `PermissionHandlerTest`, `PdfPageBitmapTest`, `PdfThumbnailFetcherTest`, `SoundEffectPlayerTest`, `ScanSessionManagerTest`, `AdManagerTest` (ampliado).
