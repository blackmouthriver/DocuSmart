package com.docsmart.features.study.presentation

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.docsmart.R
import com.docsmart.core.ads.AdConstants
import com.docsmart.core.data.db.NoteImageEntity
import com.docsmart.core.data.db.NoteWithImages
import com.docsmart.core.pdf.PdfPageBitmap
import com.docsmart.core.pdf.renderPdfPagesToBitmaps
import com.docsmart.core.ui.components.BannerNavRow
import com.docsmart.core.ui.components.DocuSmartEmptyState
import com.docsmart.core.ui.components.DocuSmartScreenHeader
import com.docsmart.core.ui.components.DocuSmartTopBanner
import com.docsmart.core.ui.theme.SuccessGreen
import com.docsmart.core.ui.theme.WarningAmber
import com.docsmart.core.ui.theme.accentBorder
import com.docsmart.core.ui.theme.accentShadow
import com.docsmart.core.ui.theme.rememberAccentGradient
import com.docsmart.core.ui.theme.rememberBannerGradient
import com.docsmart.core.ui.util.ReloadOnScreenResume
import com.docsmart.core.ui.util.findActivity
import com.docsmart.core.util.DownloadsSaver
import com.docsmart.features.agenda.domain.agendaDateFormatter
import com.docsmart.features.agenda.domain.agendaTimeFormatter
import com.docsmart.features.agenda.domain.formatAgendaDateTime
import com.docsmart.features.scanner.presentation.ScannerMode
import com.docsmart.features.scanner.presentation.rememberDocumentScannerAction
import com.docsmart.features.study.domain.PomodoroEngine
import com.docsmart.features.study.domain.ReadingProgress
import com.docsmart.features.study.domain.StudyNotesExporter
import com.docsmart.features.study.domain.StudyReadingProgressStorage
import com.docsmart.features.study.domain.StudyStats
import com.docsmart.features.study.domain.StudyStatsStorage
import com.docsmart.features.study.domain.StudySummaryExporter
import com.docsmart.features.study.domain.StudyVoicePreference
import com.docsmart.features.study.domain.TextSummarizer
import com.docsmart.features.study.domain.VoicePersona
import com.docsmart.features.study.domain.millisToHoursAndMinutes
import com.docsmart.features.study.domain.pageForParagraph
import com.docsmart.features.study.domain.personaForVoice
import com.docsmart.features.study.domain.personasForVoices
import com.docsmart.features.study.domain.pomodoroCountsByWeekday
import com.docsmart.features.study.presentation.components.NoteLinkDocumentDialog
import com.itextpdf.kernel.geom.Vector
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.canvas.parser.EventType
import com.itextpdf.kernel.pdf.canvas.parser.PdfCanvasProcessor
import com.itextpdf.kernel.pdf.canvas.parser.data.IEventData
import com.itextpdf.kernel.pdf.canvas.parser.data.TextRenderInfo
import com.itextpdf.kernel.pdf.canvas.parser.listener.IEventListener
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Calendar
import java.util.Locale

@Composable
fun StudyScreen(
    onBack: () -> Unit = {},
    initialTab: Int = 0,
    // #52: fuerza la pestaña Notas si viene seteado
    openNoteId: String? = null,
    onOpenAgenda: () -> Unit = {},
    // Ir a Inicio desde cualquier vista de Modo Estudio.
    onHome: () -> Unit = {},
    viewModel: StudyViewModel = hiltViewModel(),
    // Inyectable para pruebas sin Hilt (NotesTab lo necesita).
    notesViewModel: NotesViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ── Estado general ────────────────────────────────
    // Lanzamiento inicial 2026-09-10: pestaña "Resumen" oculta a pedido del
    // usuario para el primer release (ver `tabs` más abajo) -- el límite
    // baja de 3 a 2 para que initialTab nunca pueda arrancar en la pestaña
    // oculta. TextSummarizer y SummaryTab quedan intactos, solo sin punto
    // de entrada desde la UI; reactivar es agregar de nuevo el 4to string
    // en `tabs` y volver este coerceIn a (0, 3).
    var selectedTab by rememberSaveable { mutableIntStateOf(initialStudyTab(openNoteId, initialTab)) }
    // ── Resumen automático (2026-09-08, 100% local -- ver TextSummarizer) ──
    var summarySentences by remember { mutableStateOf<List<String>?>(null) }
    var isSummarizing by remember { mutableStateOf(false) }
    // Pedido explícito del usuario 2026-09-08: Lectura pasa a aceptar solo
    // PDF (antes "*/*" -- los documentos Word daban problemas con el parser
    // propio de esta pantalla). `documentUri` es nuevo -- antes solo se
    // guardaba el texto ya extraído, pero ahora también hace falta el PDF
    // original para mostrarlo mientras la voz lee (ver StudyPdfViewer).
    // Hallazgo real de la auditoría general 2026-09-17 (quinta pasada):
    // todo este bloque usaba `remember` -- una rotación real del
    // dispositivo (MainActivity no fija orientación ni declara
    // configChanges) recreaba la Activity y perdía el PDF cargado y todo
    // el texto ya extraído, obligando a elegir el archivo de nuevo y
    // esperar otra vez la extracción. Mismo patrón ya corregido como Alta
    // en el Visor (backlog 2026-09-16, #10), replicado acá con
    // `rememberSaveable`.
    var documentUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    // Hallazgo real de la revisión adversarial de correctitud sobre este
    // mismo fix: `documentText` es un String POR PÁRRAFO de todo el
    // documento -- para el caso de uso real de esta pantalla (libros/
    // apuntes largos) puede ser varios MB, y guardarlo en el Bundle de
    // `onSaveInstanceState` arriesga `TransactionTooLargeException` justo
    // en el escenario que este fix quiere proteger (rotar con un documento
    // largo ya cargado). Se mantiene en `remember` -- `documentUri` sí
    // persiste, y el LaunchedEffect de más abajo (junto a `docLauncher`)
    // vuelve a extraerlo automáticamente si hace falta, sin obligar al
    // usuario a elegir el archivo de nuevo.
    var documentText by remember { mutableStateOf<List<String>>(emptyList()) }
    // "Retomar lectura" (2026-09-08): límites de página del documento activo,
    // para poder mostrar "página X de Y" y guardar el progreso por página.
    var pageBoundaries by rememberSaveable(
        stateSaver = listSaver(save = { it }, restore = { it }),
    ) { mutableStateOf<List<Int>>(emptyList()) }
    // Pedido explícito del usuario 2026-09-08: en un dispositivo más lento la
    // extracción del PDF completo tardaba mucho más que en otro -- ahora
    // `documentText`/`pageBoundaries` se actualizan página por página en vez
    // de esperar a que termine todo el documento, y `extractionComplete`
    // indica si aún queda procesamiento en segundo plano.
    var extractionComplete by rememberSaveable { mutableStateOf(true) }
    val noDocumentLabel = stringResource(R.string.study_no_document)
    var documentName by rememberSaveable { mutableStateOf(noDocumentLabel) }
    // Pedido explícito del usuario 2026-09-08: además de "Continuar leyendo",
    // poder quitar un PDF de esa lista -- estado propio (no `remember` de una
    // sola vez) para que la tarjeta desaparezca al tocar "Quitar" sin
    // necesidad de salir y volver a entrar a la pestaña Lectura.
    var readingHistory by remember { mutableStateOf(StudyReadingProgressStorage.loadAll(context)) }

    val extractionMessages =
        StudyExtractionMessages(
            couldNotRead = stringResource(R.string.study_extract_could_not_read),
            pdfNoText = stringResource(R.string.study_extract_pdf_no_text),
            pdfErrorTemplate = stringResource(R.string.study_extract_pdf_error),
            genericErrorTemplate = stringResource(R.string.study_extract_generic_error),
            defaultDocumentName = stringResource(R.string.study_default_document_name),
            outOfMemoryMessage = stringResource(R.string.study_extract_out_of_memory),
        )
    // Se preserva con `rememberSaveable` para no perder una nota a medio
    // escribir (texto/resaltados) si el dispositivo rota mientras se
    // escribe -- ver comentario de `documentUri` más arriba.
    var notes by rememberSaveable { mutableStateOf("") }
    var highlights by rememberSaveable(
        stateSaver = listSaver(save = { it.toList() }, restore = { it.toSet() }),
    ) { mutableStateOf<Set<Int>>(emptySet()) }
    var isLoadingDoc by remember { mutableStateOf(false) }

    // ── TTS ───────────────────────────────────────────
    val ttsRef = remember { mutableStateOf<TextToSpeech?>(null) }
    val isSpeaking = remember { mutableStateOf(false) }
    val ttsReady = remember { mutableStateOf(false) }
    // Hallazgo H2/H3 de la auditoría (ronda 13, Modo Estudio 2026-09-18):
    // antes, si el motor TTS no inicializaba en absoluto o si tanto el
    // idioma del dispositivo como el fallback a español fallaban, el botón
    // "Leer todo" quedaba deshabilitado para siempre sin ninguna
    // explicación visible. Null = sin error.
    val ttsErrorMessage = remember { mutableStateOf<String?>(null) }
    val currentSpeakingIndex = remember { mutableIntStateOf(-1) }
    // Selector de voz (pedido explícito de testers 2026-09-12): solo voces
    // instaladas en el dispositivo para el idioma actual, nunca las que
    // requieren red (`isNetworkConnectionRequired`) -- se queda 100% local
    // y gratis para todos, sin depender de ningún servicio en la nube.
    val availableVoices = remember { mutableStateOf<List<Voice>>(emptyList()) }
    // Pedido explícito del usuario 2026-09-22: un personaje distinto por voz
    // instalada -- `personasForVoices` (VoicePersona.kt) asigna posiciones sin
    // colisión mientras no se superen los personajes curados, a diferencia de
    // `personaForVoice` sola (hash % 10, repetía nombre con más de 10 voces).
    val voicePersonas =
        remember(availableVoices.value) { personasForVoices(availableVoices.value.map { it.name }) }
    val selectedVoice = remember { mutableStateOf<Voice?>(null) }
    var showVoicePicker by remember { mutableStateOf(false) }
    // HU-64 (feedback real de testers de la prueba cerrada, 2026-09-16):
    // nombre de la voz que está sonando como muestra ahora mismo (null si
    // ninguna) -- distinto de `selectedVoice`, que es la voz que se usa
    // para la lectura real. Escuchar una muestra NUNCA cambia la lectura.
    var previewingVoiceName by remember { mutableStateOf<String?>(null) }
    // Ver comentario de `extractionComplete` -- si "Leer todo" alcanza el
    // último párrafo ya extraído mientras el resto del PDF sigue procesándose
    // en segundo plano, esto queda en true hasta que aparezcan más párrafos
    // (o termine la extracción) en vez de dar la lectura por terminada.
    val waitingForMoreText = remember { mutableStateOf(false) }
    // Ronda 16: último índice de párrafo ya encolado en el motor TTS (ver
    // ttsQueueStepAfterUtterance) y "sesión" de lectura -- se incrementa cada
    // vez que la lectura se detiene o se reinicia, para que un callback de
    // UtteranceProgressListener ya encolado en el Handler (onDone/onStart de un
    // párrafo que terminó justo antes de tocar "Detener") no reanude ni
    // re-marque como "leyendo" una lectura que el usuario ya detuvo.
    val ttsQueuedUpTo = remember { mutableIntStateOf(-1) }
    val ttsSession = remember { mutableIntStateOf(0) }
    // Mayor índice de párrafo que ya empezó a sonar en esta lectura: en una lectura
    // secuencial los índices solo suben, así que un retroceso es una repetición y se
    // corta. `readingFinished` = terminó todo el documento (ofrece "Volver a leer").
    val ttsHighestStarted = remember { mutableIntStateOf(-1) }
    val readingFinished = remember { mutableStateOf(false) }
    // Fase 4 (2026-09-22): rango exacto (caracteres dentro del párrafo que suena)
    // que reporta `onRangeStart` del motor TTS, cuando lo soporta -- null mientras
    // no hay lectura o el motor/voz no lo reporta (el modo Texto cae al fallback
    // de resaltar el párrafo completo, como antes).
    val speakingRange = remember { mutableStateOf<IntRange?>(null) }
    // Velocidad de lectura (factor sobre la base): se conserva entre sesiones.
    val readingSpeed = remember { mutableFloatStateOf(StudyVoicePreference.loadSpeed(context)) }
    // Extracción en curso: al elegir otro documento mientras el anterior
    // seguía extrayéndose, las dos corrutinas escribían a la vez sobre el mismo
    // `documentText` (párrafos de un documento mezclados con los del otro).
    var loadJob by remember { mutableStateOf<Job?>(null) }

    fun stopReading() {
        ttsSession.intValue += 1
        ttsRef.value?.stop()
        isSpeaking.value = false
        waitingForMoreText.value = false
        speakingRange.value = null
    }

    // Terminó de leer TODO el documento: se detiene el motor, se invalida
    // cualquier callback pendiente (ttsSession) y se avisa al usuario en vez de
    // dejar que la lectura vuelva a empezar sola. Solo "Volver a leer" la
    // reanuda, desde el principio.
    fun finishReading(uriString: String?) {
        ttsSession.intValue += 1
        ttsRef.value?.stop()
        waitingForMoreText.value = false
        isSpeaking.value = false
        currentSpeakingIndex.intValue = -1
        ttsHighestStarted.intValue = -1
        readingFinished.value = true
        speakingRange.value = null
        if (uriString != null) {
            StudyReadingProgressStorage.remove(context, uriString)
            readingHistory = StudyReadingProgressStorage.loadAll(context)
        }
    }

    // ── Pomodoro (RF-STU-10: vive en PomodoroEngine, no en remember{},
    // para que siga corriendo al salir de esta pantalla) ─────────────
    val pomodoroState by PomodoroEngine.state.collectAsState()

    // ── Estadísticas (RF-STU-09) ──────────────────────
    var showStats by remember { mutableStateOf(false) }
    val notificationPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) {
        /* No-op: el Pomodoro funciona igual sin el permiso, solo no se ve
          la notificación mientras la app está en segundo plano. */
        }

    // ── Inicializar TTS ───────────────────────────────
    // Resuelto en scope de composable (no con context.getString() dentro del
    // callback -- mismo motivo que voiceSampleTemplate más abajo,
    // LocalContextGetResourceValueCall de lint marca ese patrón como error).
    val ttsUnavailableMessage = stringResource(R.string.study_tts_unavailable)
    DisposableEffect(Unit) {
        var ttsInstance: TextToSpeech? = null
        // El callback de inicialización es asíncrono: si la pantalla se destruye
        // antes de que llegue (rotación rápida, salir enseguida), no debe
        // publicar ni configurar un motor que onDispose ya apagó.
        var disposed = false
        ttsInstance =
            TextToSpeech(context) { status ->
                if (disposed) return@TextToSpeech
                if (status == TextToSpeech.SUCCESS) {
                    // Antes forzaba español (Locale("es","ES")) sin importar el idioma
                    // configurado — mismo bug que ya se corrigió para el reconocimiento
                    // de voz, pero solo del lado de entrada, no de lectura en voz alta.
                    var languageInUse = Locale.getDefault()
                    val result = ttsInstance?.setLanguage(languageInUse)
                    var fallbackFailed = false
                    if (result == TextToSpeech.LANG_MISSING_DATA ||
                        result == TextToSpeech.LANG_NOT_SUPPORTED
                    ) {
                        // Hallazgo H2 de la auditoría (ronda 13, 2026-09-18): antes
                        // este fallback no comprobaba su PROPIO resultado -- si
                        // "es-ES" tampoco estaba disponible en el dispositivo,
                        // seguía de largo como si hubiera funcionado, dejando
                        // ttsReady=true con un idioma que en realidad nunca se
                        // pudo setear.
                        languageInUse = Locale("es", "ES")
                        val fallbackResult = ttsInstance?.setLanguage(languageInUse)
                        fallbackFailed = fallbackResult == TextToSpeech.LANG_MISSING_DATA ||
                            fallbackResult == TextToSpeech.LANG_NOT_SUPPORTED
                    }
                    if (fallbackFailed) {
                        ttsErrorMessage.value = ttsUnavailableMessage
                    } else {
                        ttsInstance?.setSpeechRate(BASE_SPEECH_RATE * StudyVoicePreference.loadSpeed(context))
                        ttsInstance?.setPitch(1.05f)

                        // Bug real encontrado al verificar en dispositivo (2026-09-12):
                        // ttsInstance.language.language puede devolver el código ISO
                        // de 3 letras ("spa") mientras que voice.locale.language usa
                        // 2 letras ("es") para el mismo idioma -- comparados directo,
                        // ninguna voz coincidía nunca (0 voces encontradas en la
                        // prueba real). Se normalizan ambos lados a ISO3 antes de
                        // comparar.
                        // Hallazgo H2: se filtra por `languageInUse` (el idioma
                        // que REALMENTE quedó activo, sea el del dispositivo o el
                        // fallback a español), no por Locale.getDefault() -- antes
                        // quedaba desincronizado si hubo fallback.
                        val currentIso3Language = runCatching { languageInUse.isO3Language }.getOrNull()
                        val voices =
                            ttsInstance?.voices
                                ?.filter { voice ->
                                    !voice.isNetworkConnectionRequired &&
                                        runCatching { voice.locale.isO3Language }.getOrNull() == currentIso3Language
                                }
                                ?.sortedByDescending { it.quality }
                                .orEmpty()
                        availableVoices.value = voices
                        val savedVoiceName = StudyVoicePreference.load(context)
                        val matchedVoice = voices.find { it.name == savedVoiceName }
                        if (matchedVoice != null) {
                            ttsInstance?.voice = matchedVoice
                        }
                        selectedVoice.value = matchedVoice ?: ttsInstance?.voice

                        ttsRef.value = ttsInstance
                        ttsReady.value = true
                        Timber.d("TTS listo, ${voices.size} voces disponibles para $currentIso3Language")
                    }
                } else {
                    // Hallazgo H3 de la auditoría (ronda 13, 2026-09-18): el motor
                    // TTS no inicializó en absoluto -- antes no había ninguna rama
                    // para este caso, así que "Leer todo" quedaba deshabilitado
                    // para siempre sin ningún aviso visible.
                    ttsErrorMessage.value = ttsUnavailableMessage
                }
            }
        onDispose {
            disposed = true
            ttsInstance?.stop()
            ttsInstance?.shutdown()
            ttsRef.value = null
            ttsReady.value = false
        }
    }

    // ── RF-STU-09: acumula tiempo de lectura en voz alta real. Se relanza
    // cada vez que isSpeaking cambia; mientras está en true, se suspende en
    // awaitCancellation() -- cuando isSpeaking vuelve a false, Compose
    // cancela este efecto y el bloque finally calcula cuánto duró esa
    // lectura y lo persiste. No mide "tiempo con la pantalla abierta", mide
    // reproducción de TTS real (onSpeak/onSpeakAll comparten el mismo
    // isSpeaking).
    LaunchedEffect(isSpeaking.value) {
        if (!isSpeaking.value) return@LaunchedEffect
        val start = System.currentTimeMillis()
        try {
            awaitCancellation()
        } finally {
            StudyStatsStorage.addReadingTime(context, System.currentTimeMillis() - start)
        }
    }

    // "Procesamiento incremental" (2026-09-08): cuando "Leer todo" alcanza el
    // último párrafo ya extraído mientras el PDF seguía procesándose de
    // fondo, `onDone` deja `waitingForMoreText` en true en vez de terminar la
    // lectura. Este efecto se relanza cada vez que llega una página nueva (o
    // termina la extracción) y retoma la cola apenas hay algo nuevo que leer.
    LaunchedEffect(documentText.size, extractionComplete) {
        if (!waitingForMoreText.value) return@LaunchedEffect
        val tts = ttsRef.value ?: return@LaunchedEffect
        val uriString = documentUri?.toString()
        val step =
            ttsQueueStepAfterUtterance(
                finishedIndex = currentSpeakingIndex.intValue,
                queuedUpTo = ttsQueuedUpTo.intValue,
                lastIndex = documentText.lastIndex,
                extractionComplete = extractionComplete,
            )
        when (step.outcome) {
            TtsQueueOutcome.KEEP_PLAYING -> {
                waitingForMoreText.value = false
                ttsQueuedUpTo.intValue = step.newQueuedUpTo
                for (idx in step.toEnqueue) {
                    tts.speak(documentText[idx], TextToSpeech.QUEUE_ADD, null, "study_all_$idx")
                }
            }
            TtsQueueOutcome.WAIT_FOR_MORE_TEXT -> Unit
            TtsQueueOutcome.FINISHED -> {
                // Terminó de extraer y no quedó nada más por leer.
                finishReading(uriString)
            }
        }
    }

    // ── RF-STU-10: pide el permiso de notificaciones (Android 13+) al
    // entrar a la pestaña Pomodoro, para que la notificación de progreso en
    // segundo plano sea visible -- el timer en sí funciona igual sin el
    // permiso, ver comentario en el launcher.
    LaunchedEffect(selectedTab) {
        if (selectedTab != 2 || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@LaunchedEffect
        val granted =
            ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }

    // "Retomar lectura" (2026-09-08): carga un PDF y, si se le pasa un
    // párrafo de retomo (viene de un progreso guardado), deja la lectura
    // lista para continuar ahí en vez de desde el principio. Compartida
    // entre el selector de documento normal y la lista "Continuar leyendo".
    fun loadDocument(
        uri: Uri,
        resumeFromParagraph: Int?,
    ) {
        // Ronda 16: elegir otro documento con una lectura en curso dejaba al motor
        // TTS leyendo la cola del documento anterior (y onDone encolaba después
        // los párrafos del nuevo), y una extracción previa todavía viva escribía
        // sobre el mismo estado -- se detiene ambas antes de empezar.
        loadJob?.cancel()
        stopReading()
        ttsQueuedUpTo.intValue = -1
        ttsHighestStarted.intValue = -1
        readingFinished.value = false
        isLoadingDoc = true
        extractionComplete = false
        documentUri = uri
        documentText = emptyList()
        pageBoundaries = emptyList()
        summarySentences = null // documento nuevo -- el resumen anterior ya no aplica
        // Hallazgo H4 de la auditoría (ronda 13, 2026-09-18): `highlights` es
        // un Set<Int> de índices de párrafo sin atadura al documento -- si no
        // se reinicia acá, los índices resaltados del documento ANTERIOR se
        // siguen aplicando al nuevo (falso resaltado) cuando este tiene
        // suficientes párrafos.
        highlights = emptySet()
        // Pedido explícito del usuario 2026-09-08: no esperar a que el PDF
        // completo termine de procesarse para poder empezar a leer -- cada
        // vez que una página nueva termina de extraerse (`onPageExtracted`)
        // se refresca `documentText`/`pageBoundaries` y se apaga el spinner,
        // así "Leer todo" queda disponible desde la primera página lista.
        var resumeApplied = false
        scope.launch {
            // Bug real corregido 2026-09-08: `documentName` antes solo se
            // fijaba al TERMINAR toda la extracción -- como ahora "Leer todo"
            // puede arrancar mucho antes de eso, el progreso guardado
            // (`StudyReadingProgressStorage`) quedaba con el nombre por
            // defecto ("Sin documento") en vez del nombre real del PDF. Se
            // resuelve aparte y de una vez, sin esperar el texto.
            documentName = withContext(Dispatchers.IO) { resolveFileName(context, uri, extractionMessages) }
            val result =
                extractTextFromUri(context, uri, extractionMessages) { partialParagraphs, partialBoundaries ->
                    // El callback llega desde Dispatchers.IO (extractPdfText corre
                    // ahí) -- se pasa a Main antes de tocar estado de Compose.
                    withContext(Dispatchers.Main) {
                        documentText = partialParagraphs
                        pageBoundaries = partialBoundaries
                        isLoadingDoc = false
                        // Bug real corregido 2026-09-08: con la extracción
                        // incremental, el primer lote parcial (solo la página 1)
                        // puede tener MUCHOS menos párrafos que el índice
                        // guardado para retomar -- antes esto lo "recortaba" con
                        // `coerceIn` para que entrara en ese lote chico, dejando
                        // la lectura fija cerca del principio para siempre (el
                        // `resumeApplied` ya no dejaba corregirlo después). Ahora
                        // se espera a que llegue un lote que sí contenga ese
                        // párrafo antes de aplicar el retomo.
                        if (!resumeApplied) {
                            when {
                                resumeFromParagraph == null -> {
                                    resumeApplied = true
                                    currentSpeakingIndex.intValue = -1
                                }
                                resumeFromParagraph < partialParagraphs.size -> {
                                    resumeApplied = true
                                    currentSpeakingIndex.intValue = resumeFromParagraph
                                }
                                // si no, seguir esperando más páginas -- todavía
                                // no llegamos al párrafo donde había quedado
                            }
                        }
                    }
                }
            documentText = result.paragraphs
            documentName = result.fileName
            pageBoundaries = result.pageBoundaries
            if (!resumeApplied) {
                currentSpeakingIndex.intValue = resumeFromParagraph
                    ?.coerceIn(0, (result.paragraphs.size - 1).coerceAtLeast(0))
                    ?: -1
            }
            // Hallazgo real de la auditoría general 2026-09-17/18 (décima
            // ronda, Media -- N4): si la extracción falla (documento
            // borrado/movido, sin texto, sin memoria) mientras se estaba
            // "retomando" una lectura guardada, antes la entrada de
            // progreso quedaba fantasma en StudyReadingProgressStorage
            // para siempre -- el usuario volvía a ver "Continuar leyendo"
            // apuntando a un documento roto una y otra vez.
            if (result.isError && resumeFromParagraph != null) {
                StudyReadingProgressStorage.remove(context, uri.toString())
                readingHistory = StudyReadingProgressStorage.loadAll(context)
            }
            isLoadingDoc = false
            extractionComplete = true
        }.also { loadJob = it }
    }

    // ── Selector de documento (solo PDF) ──────────────
    // Bug real corregido 2026-09-08: con `GetContent()` (ACTION_GET_CONTENT)
    // `takePersistableUriPermission` no lanzaba excepción pero el permiso NO
    // quedaba realmente persistido -- solo `OpenDocument()`
    // (ACTION_OPEN_DOCUMENT) lo soporta. Por eso "Continuar leyendo" fallaba
    // en silencio después de cerrar la app: al reabrir, tanto la extracción
    // de texto como el visor de PDF recibían SecurityException del
    // DownloadStorageProvider al intentar leer el mismo URI.
    val docLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri ->
            uri?.let {
                @Suppress("TooGenericExceptionCaught")
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                } catch (e: Exception) {
                    // Solo el tipo: antes se pasaba el Throwable y el Uri real (incluye el
                    // nombre del archivo del usuario) y CrashlyticsTree lo reenvía a Firebase.
                    Timber.w("No se pudo tomar permiso persistente sobre el documento (${e.javaClass.simpleName})")
                }
                val saved = StudyReadingProgressStorage.findFor(context, uri.toString())
                loadDocument(uri, resumeFromParagraph = saved?.paragraphIndex)
            }
        }

    // Re-extrae automáticamente si `documentUri` sobrevivió una rotación
    // (rememberSaveable) pero `documentText` no (ver comentario de más
    // arriba) -- evita que el usuario tenga que volver a elegir el mismo
    // archivo a mano solo por haber rotado el dispositivo.
    LaunchedEffect(documentUri) {
        val uri = documentUri ?: return@LaunchedEffect
        if (documentText.isNotEmpty() || isLoadingDoc) return@LaunchedEffect
        val saved = StudyReadingProgressStorage.findFor(context, uri.toString())
        loadDocument(uri, resumeFromParagraph = saved?.paragraphIndex)
    }

    // HU-64: reproduce una frase corta con ESA voz puntual sin tocar
    // `selectedVoice` -- la lectura real sigue usando la voz ya elegida.
    // Restaura la voz seleccionada al terminar (o fallar) la muestra, ya
    // que `TextToSpeech.voice` es un estado global del motor que aplica a
    // la PRÓXIMA llamada a speak(), sea de una muestra o de "Leer todo".
    // La plantilla se resuelve acá vía stringResource() (reacciona a un
    // cambio de idioma en caliente) en vez de `context.getString()` dentro
    // de la función -- LocalContextGetResourceValueCall de lint marca ese
    // segundo patrón como un error real, no solo estilo.
    val voiceSampleTemplate = stringResource(R.string.study_voice_sample_phrase)

    fun previewVoice(voice: Voice) {
        val tts = ttsRef.value ?: return
        val persona = voicePersonas[voice.name] ?: personaForVoice(voice.name)
        val sampleText = String.format(voiceSampleTemplate, persona.name)
        val voiceToRestore = selectedVoice.value
        previewingVoiceName = voice.name
        tts.voice = voice
        // Hallazgo real de la auditoría general 2026-09-17 (B19):
        // UtteranceProgressListener corre en un hilo interno del motor TTS
        // (no garantizado por Android, varía según fabricante) -- mutar
        // State de Compose ahí es seguro (el snapshot system de Compose
        // soporta escrituras desde cualquier hilo), pero volver a llamar a
        // `tts.voice = ...` reentrante desde ese mismo hilo de callback no
        // está garantizado como seguro en todos los motores OEM. Se
        // despacha al hilo principal para eliminar esa duda.
        val mainHandler = Handler(Looper.getMainLooper())
        tts.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    Timber.d("Reproduciendo muestra de voz: ${voice.name}")
                }

                override fun onDone(utteranceId: String?) {
                    mainHandler.post {
                        if (voiceToRestore != null) tts.voice = voiceToRestore
                        previewingVoiceName = null
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    mainHandler.post {
                        if (voiceToRestore != null) tts.voice = voiceToRestore
                        previewingVoiceName = null
                    }
                }
            },
        )
        tts.speak(sampleText, TextToSpeech.QUEUE_FLUSH, null, "study_voice_preview")
    }

    // Atrás del sistema desde una vista interna vuelve al menú (no sale de Modo Estudio).
    BackHandler(enabled = selectedTab != STUDY_TAB_MENU) {
        stopReading()
        selectedTab = STUDY_TAB_MENU
    }

    Scaffold(
        // Fondo animado global (backlog UX 2026-09-06): transparente para
        // dejar ver la capa pintada una sola vez en MainActivity. Bug real
        // encontrado 2026-09-06 ("línea blanca"): por defecto Scaffold
        // reserva su propio inset de systemBars EN ADICIÓN al que ya
        // reserva el Scaffold principal de MainActivity -- se excluye el
        // inferior porque ya lo maneja MainActivity una sola vez.
        // Seguimiento 2026-09-08: también se excluye el superior, mismo
        // bug real encontrado y corregido ese día en Convertidor/
        // Herramientas PDF -- Modo Estudio tenía el mismo doble descuento
        // (esta pantalla también tiene Scaffold propio), dejando el
        // banner de título más abajo que en el resto de la app.
        contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal),
        containerColor = Color.Transparent,
    ) { innerPadding ->
        if (showStats) {
            StudyStatsDialog(
                stats = remember(showStats) { StudyStatsStorage.loadStats(context) },
                onDismiss = { showStats = false },
            )
        }
        if (showVoicePicker) {
            VoiceSelectorDialog(
                voices = availableVoices.value,
                personas = voicePersonas,
                selectedVoice = selectedVoice.value,
                previewingVoiceName = previewingVoiceName,
                onVoiceSelected = { voice ->
                    ttsRef.value?.voice = voice
                    selectedVoice.value = voice
                    StudyVoicePreference.save(context, voice.name)
                    showVoicePicker = false
                },
                onPreviewVoice = { voice -> previewVoice(voice) },
                onDismiss = {
                    // Una muestra puede seguir sonando si se cierra el
                    // diálogo a mitad de la reproducción -- se corta para
                    // no dejar audio de fondo sin ningún diálogo visible
                    // que lo explique. `stop()` no garantiza que se
                    // dispare onDone/onError (puede llamar a onStop o
                    // nada, según el motor), así que la voz seleccionada
                    // se restaura acá también, no solo en esos callbacks.
                    if (previewingVoiceName != null) {
                        ttsRef.value?.stop()
                        selectedVoice.value?.let { ttsRef.value?.voice = it }
                        previewingVoiceName = null
                    }
                    showVoicePicker = false
                },
            )
        }
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        ) {
            // ── Banner de anuncio + título ────────────
            // Seguimiento 2026-09-08 a pedido explícito del usuario: (1) el
            // anuncio subió arriba del todo, mismo patrón que el resto de
            // las pantallas (DocuSmartScreenHeader: 16dp de margen, 12dp
            // arriba, 8dp de espacio al banner); (2) los íconos de abrir
            // documento/estadísticas salieron del degradado -- quedan en su
            // propia fila, a la altura de "Volver" (que por eso se arma acá
            // a mano en vez de con el `onBack` de DocuSmartTopBanner, para
            // poder ponerlos en la misma fila).
            // Rediseño de acceso (2026-09-19, feedback de testers: "Modo Estudio es
            // confuso"): en vez de pestañas + 3 íconos sueltos, Modo Estudio abre
            // en un menú (Lectura, Notas, Pomodoro, Agenda y calendario). Cada vista
            // lleva su banner de anuncio, el banner de color y "Volver" (al menú)
            // + "Inicio" (a Home). Los atajos de Inicio abren directo la vista.
            val currentView = studyViewForTab(selectedTab)
            DocuSmartScreenHeader(
                adUnitId = AdConstants.BANNER_STUDY_ID,
                adManager = viewModel.adManager,
            ) {
                // Pedido explícito del usuario 2026-09-22: con un documento abierto, la
                // ficha (portada, avance, voz, resaltados) vive DENTRO del banner azul en
                // vez de en su propia tarjeta blanca debajo -- libera espacio vertical
                // para el documento. El logo se mantiene. El resto de las vistas de
                // Modo Estudio siguen con el banner de siempre.
                val openDocumentUri = documentUri
                if (currentView == StudyView.READING && openDocumentUri != null) {
                    ReadingDocumentBanner(
                        documentUri = openDocumentUri,
                        documentName = documentName,
                        currentPage = pageForParagraph(currentSpeakingIndex.intValue.coerceAtLeast(0), pageBoundaries),
                        totalPages = pageBoundaries.size,
                        readingFinished = readingFinished.value,
                        highlightedCount = highlights.size,
                        voiceName =
                            selectedVoice.value?.let {
                                (voicePersonas[it.name] ?: personaForVoice(it.name)).name
                            },
                        onBack = {
                            stopReading()
                            selectedTab = STUDY_TAB_MENU
                        },
                        onHome = {
                            stopReading()
                            onHome()
                        },
                    )
                } else {
                    DocuSmartTopBanner(
                        // Fase 2 del plan de diseño: encabezado compacto en pantallas de trabajo.
                        compact = currentView != StudyView.MENU,
                        // En Lectura el título es "Lectura" (no "Modo Estudio"): pedido del usuario.
                        screenTitle =
                            if (currentView == StudyView.READING) {
                                stringResource(R.string.study_tab_reading)
                            } else {
                                stringResource(R.string.study_title)
                            },
                        screenSubtitle =
                            when (currentView) {
                                StudyView.MENU -> stringResource(R.string.study_menu_subtitle)
                                StudyView.READING -> documentName
                                StudyView.NOTES -> stringResource(R.string.study_tab_notes)
                                StudyView.POMODORO -> stringResource(R.string.study_tab_pomodoro)
                            },
                        onBack = {
                            stopReading()
                            if (currentView == StudyView.MENU) onBack() else selectedTab = STUDY_TAB_MENU
                        },
                        onHome = {
                            stopReading()
                            onHome()
                        },
                    )
                }
            }

            when (selectedTab) {
                // ── Menú de Modo Estudio ──────────────
                STUDY_TAB_MENU ->
                    StudyMenu(
                        onReading = { selectedTab = STUDY_TAB_READING },
                        onNotes = { selectedTab = STUDY_TAB_NOTES },
                        onPomodoro = { selectedTab = STUDY_TAB_POMODORO },
                        onAgenda = onOpenAgenda,
                        modifier = Modifier.fillMaxSize(),
                    )
                // ── Tab Lectura ───────────────────────
                // Rediseñado 2026-09-08 a pedido explícito del usuario: antes
                // mostraba los párrafos extraídos como la vista principal --
                // ahora se ve el PDF real (StudyPdfViewer) mientras el texto
                // extraído sigue existiendo "por debajo", solo para
                // alimentar la voz. Como ya no hay una fila por párrafo que
                // tocar, "marcar" pasa a resaltar el párrafo que se está
                // leyendo en ese momento (único que tiene sentido sin verlos
                // en pantalla) -- sigue alimentando la lista de "Párrafos
                // resaltados" de la pestaña Notas, sin tocarla.
                0 ->
                    ReadingTab(
                        documentUri = documentUri,
                        isLoading = isLoadingDoc,
                        isCurrentHighlighted = highlights.contains(currentSpeakingIndex.intValue),
                        currentSpeakingRange = speakingRange.value,
                        isSpeaking = isSpeaking.value,
                        ttsReady = ttsReady.value,
                        ttsErrorMessage = ttsErrorMessage.value,
                        isExtractingMore = !extractionComplete,
                        readingFinished = readingFinished.value,
                        onToggleHighlightCurrent = {
                            val index = currentSpeakingIndex.intValue
                            if (index >= 0) {
                                highlights =
                                    if (highlights.contains(index)) {
                                        highlights - index
                                    } else {
                                        highlights + index
                                    }
                            }
                        },
                        currentPage = pageForParagraph(currentSpeakingIndex.intValue.coerceAtLeast(0), pageBoundaries),
                        totalPages = pageBoundaries.size,
                        currentSpeakingIndex = currentSpeakingIndex.intValue,
                        speedLabel = readingSpeedLabel(readingSpeed.floatValue),
                        onSpeedClick = {
                            val next = nextReadingSpeed(readingSpeed.floatValue)
                            readingSpeed.floatValue = next
                            StudyVoicePreference.saveSpeed(context, next)
                            ttsRef.value?.setSpeechRate(BASE_SPEECH_RATE * next)
                        },
                        onStepParagraph = { delta ->
                            val target = steppedParagraph(currentSpeakingIndex.intValue, delta, documentText.lastIndex)
                            if (target >= 0) {
                                readingFinished.value = false
                                currentSpeakingIndex.intValue = target
                            }
                        },
                        documentText = documentText,
                        highlights = highlights,
                        onJumpToParagraph = { index ->
                            readingFinished.value = false
                            currentSpeakingIndex.intValue = index
                        },
                        onSpeakAll = {
                            if (isSpeaking.value) {
                                // "Retomar lectura" (2026-09-08): antes esto
                                // reiniciaba `currentSpeakingIndex` a -1, así que
                                // parar y volver a tocar "Leer todo" (en esta
                                // misma sesión o en una futura) siempre empezaba
                                // desde el principio. Ahora se deja tal cual --
                                // "Leer todo" retoma desde ahí la próxima vez.
                                stopReading()
                            } else {
                                val currentTts = ttsRef.value
                                if (currentTts == null || !ttsReady.value || documentText.isEmpty()) {
                                    Timber.e("TTS no está listo")
                                    return@ReadingTab
                                }
                                // Bug real corregido 2026-09-08: "Leer todo" unía
                                // TODOS los párrafos en un solo string y hacía
                                // una única llamada a speak() -- Android limita
                                // cada llamada a ~4000 caracteres
                                // (TextToSpeech.getMaxSpeechInputLength()), así
                                // que con cualquier documento largo esa llamada
                                // fallaba en silencio y no se oía nada. Ahora se
                                // encola un párrafo por llamada (ya probados
                                // individualmente por "leer este párrafo", cada
                                // uno bien por debajo del límite), con
                                // QUEUE_ADD para que se reproduzcan en orden.
                                //
                                // "Retomar lectura": el punto de partida ya no es
                                // siempre 0 -- si `currentSpeakingIndex` quedó en
                                // un párrafo válido (por "Detener" o por venir de
                                // "Continuar leyendo"), se sigue desde ahí.
                                val startIndex =
                                    currentSpeakingIndex.intValue
                                        .takeIf { it in documentText.indices } ?: 0
                                val uriString = documentUri?.toString()
                                readingFinished.value = false
                                ttsHighestStarted.intValue = -1
                                // Ronda 16: se congela la lista que se encola ahora y se
                                // recuerda hasta dónde llegó la cola (ver
                                // ttsQueueStepAfterUtterance), y se invalida cualquier
                                // callback pendiente de una lectura anterior.
                                val queuedParagraphs = documentText
                                ttsQueuedUpTo.intValue = queuedParagraphs.lastIndex
                                ttsSession.intValue += 1
                                val session = ttsSession.intValue
                                // Hallazgo real de la auditoría general
                                // 2026-09-17 (B19, resto): UtteranceProgressListener
                                // corre en un hilo interno del motor TTS, no
                                // garantizado por Android -- se despacha cada
                                // callback al hilo principal (mismo mecanismo ya
                                // aplicado a previewVoice() más arriba), sin
                                // tocar la lógica interna de ninguno de los 3
                                // (mismo orden relativo de ejecución, solo se
                                // corre en el hilo principal en vez del hilo del
                                // motor TTS). Esto además vuelve seguros los
                                // `tts.speak()` reentrantes de onDone() -- antes
                                // se llamaban de vuelta al motor TTS desde su
                                // propio hilo de callback.
                                val mainHandler = Handler(Looper.getMainLooper())
                                currentTts.setOnUtteranceProgressListener(
                                    object : UtteranceProgressListener() {
                                        override fun onStart(utteranceId: String?) {
                                            mainHandler.post {
                                                if (ttsSession.intValue != session) return@post
                                                val index = parseUtteranceIndex(utteranceId)
                                                if (index != null && index < ttsHighestStarted.intValue) {
                                                    // Retrocedió a un párrafo ya leído: es una repetición.
                                                    finishReading(uriString)
                                                    return@post
                                                }
                                                if (index != null) ttsHighestStarted.intValue = index
                                                isSpeaking.value = true
                                                // Nuevo párrafo: el rango exacto de la frase que suena
                                                // (fase 4) se vuelve a conocer recién con el primer
                                                // `onRangeStart` de ESTE párrafo -- limpiarlo acá evita
                                                // que se vea, aunque sea un instante, el rango del
                                                // párrafo anterior sobre el nuevo texto.
                                                speakingRange.value = null
                                                if (index != null) {
                                                    currentSpeakingIndex.intValue = index
                                                    if (uriString != null) {
                                                        StudyReadingProgressStorage.save(
                                                            context,
                                                            ReadingProgress(
                                                                uri = uriString,
                                                                documentName = documentName,
                                                                paragraphIndex = index,
                                                                totalParagraphs = documentText.size,
                                                                currentPage = pageForParagraph(index, pageBoundaries),
                                                                totalPages = pageBoundaries.size,
                                                                lastReadAtMillis = System.currentTimeMillis(),
                                                            ),
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        // Fase 4 (2026-09-22): rango exacto (en caracteres, sobre el
                                        // texto de ESE párrafo) que se está pronunciando ahora mismo --
                                        // disponible desde la API 26 (nuestro minSdk), pero no todos los
                                        // motores/voces lo reportan; cuando no llega, el modo Texto sigue
                                        // resaltando el párrafo completo (ver `ReadingTextView`).
                                        override fun onRangeStart(
                                            utteranceId: String?,
                                            start: Int,
                                            end: Int,
                                            frame: Int,
                                        ) {
                                            mainHandler.post {
                                                if (ttsSession.intValue != session) return@post
                                                if (parseUtteranceIndex(utteranceId) == currentSpeakingIndex.intValue) {
                                                    speakingRange.value = start until end
                                                }
                                            }
                                        }

                                        override fun onDone(utteranceId: String?) {
                                            mainHandler.post {
                                                if (ttsSession.intValue != session) return@post
                                                speakingRange.value = null
                                                val index = parseUtteranceIndex(utteranceId)
                                                if (index == null) return@post
                                                // "Procesamiento incremental": si el PDF seguía
                                                // extrayéndose de fondo puede haber párrafos nuevos
                                                // que todavía NO están en la cola -- solo esos se
                                                // agregan (antes se re-encolaba todo lo posterior
                                                // al párrafo terminado y el texto se repetía).
                                                val step =
                                                    ttsQueueStepAfterUtterance(
                                                        finishedIndex = index,
                                                        queuedUpTo = ttsQueuedUpTo.intValue,
                                                        lastIndex = documentText.lastIndex,
                                                        extractionComplete = extractionComplete,
                                                    )
                                                ttsQueuedUpTo.intValue = step.newQueuedUpTo
                                                ttsRef.value?.let { tts ->
                                                    for (nextIndex in step.toEnqueue) {
                                                        tts.speak(
                                                            documentText[nextIndex],
                                                            TextToSpeech.QUEUE_ADD,
                                                            null,
                                                            "study_all_$nextIndex",
                                                        )
                                                    }
                                                }
                                                when (step.outcome) {
                                                    TtsQueueOutcome.KEEP_PLAYING -> Unit
                                                    // No hay más texto TODAVÍA, pero el PDF sigue
                                                    // procesándose -- esperar en vez de dar la
                                                    // lectura por terminada (ver LaunchedEffect
                                                    // que retoma cuando llegue más).
                                                    TtsQueueOutcome.WAIT_FOR_MORE_TEXT -> {
                                                        waitingForMoreText.value = true
                                                    }
                                                    // Terminó todo el documento -- ya no hay nada
                                                    // que retomar.
                                                    TtsQueueOutcome.FINISHED -> finishReading(uriString)
                                                }
                                            }
                                        }

                                        override fun onError(utteranceId: String?) {
                                            // No se resetea el índice -- si falla
                                            // a mitad de un documento largo, "Leer
                                            // todo" debe poder reintentar desde
                                            // ahí, no desde el principio.
                                            mainHandler.post {
                                                if (ttsSession.intValue == session) {
                                                    isSpeaking.value = false
                                                    speakingRange.value = null
                                                }
                                            }
                                        }
                                    },
                                )
                                queuedParagraphs.withIndex().drop(startIndex).forEachIndexed { queuePos, indexed ->
                                    val (index, paragraph) = indexed
                                    currentTts.speak(
                                        paragraph,
                                        if (queuePos == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD,
                                        null,
                                        "study_all_$index",
                                    )
                                }
                                isSpeaking.value = true
                            }
                        },
                        onSelectDoc = { docLauncher.launch(arrayOf("application/pdf")) },
                        readingHistory = readingHistory,
                        onResumeDocument = { progress ->
                            loadDocument(Uri.parse(progress.uri), resumeFromParagraph = progress.paragraphIndex)
                        },
                        onDeleteDocument = { progress ->
                            StudyReadingProgressStorage.remove(context, progress.uri)
                            readingHistory = StudyReadingProgressStorage.loadAll(context)
                        },
                        availableVoices = availableVoices.value,
                        onVoiceSelectorClick = {
                            // HU-64/RNF1: no tiene sentido escuchar una muestra
                            // de otra voz mientras el documento se sigue
                            // leyendo con la actual -- se pausa antes de abrir
                            // el selector, mismo patrón ya usado para el botón
                            // "Detener" de más arriba.
                            if (isSpeaking.value) stopReading()
                            showVoicePicker = true
                        },
                    )

                // ── Tab Notas ─────────────────────────
                1 ->
                    NotesTab(
                        notes = notes,
                        onNotesChange = { notes = it },
                        highlights = highlights,
                        documentText = documentText,
                        openNoteId = openNoteId,
                        viewModel = notesViewModel,
                    )

                // ── Tab Pomodoro ──────────────────────
                2 ->
                    PomodoroTab(
                        minutes = pomodoroState.minutes,
                        seconds = pomodoroState.seconds,
                        isRunning = pomodoroState.isRunning,
                        isBreak = pomodoroState.isBreak,
                        pomodoroCount = pomodoroState.pomodoroCount,
                        onToggle = { PomodoroEngine.toggle(context) },
                        onReset = { PomodoroEngine.reset() },
                        onShowStats = { showStats = true },
                    )

                // ── Tab Resumen (2026-09-08, 100% local) ──
                3 ->
                    SummaryTab(
                        documentText = documentText,
                        documentName = documentName,
                        hasDocument = documentUri != null,
                        summarySentences = summarySentences,
                        isSummarizing = isSummarizing,
                        onGenerate = {
                            isSummarizing = true
                            scope.launch {
                                summarySentences =
                                    withContext(Dispatchers.Default) {
                                        TextSummarizer.summarize(documentText)
                                    }
                                isSummarizing = false
                            }
                        },
                        onSelectDoc = { docLauncher.launch(arrayOf("application/pdf")) },
                    )
            }
        }
    }
}

// Spinner + mensaje de progreso (2026-09-08, pedido explícito del usuario):
// tanto extraer/renderizar un PDF real como generar el resumen pueden tardar
// -- un spinner solo, sin ningún texto, hacía pensar que la app se había
// colgado en vez de estar trabajando. Seguimiento mismo día: el
// `CircularProgressIndicator` por sí solo se veía demasiado chico/sutil en
// el dispositivo real -- se agranda a 64dp y se le pone el logo de
// DocuSmart en el centro (marca propia en vez de un spinner genérico, con
// el spinner real de Compose alrededor como respaldo si el logo no se
// llegara a ver). Compartido entre Lectura (carga del documento) y Resumen
// (generación).
@Composable
private fun LoadingIndicator(
    message: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(modifier = Modifier.size(64.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 5.dp,
            )
            // El logo solo (sin fondo propio) se perdía contra el fondo
            // claro de la pantalla -- en todos los demás usos del logo en
            // la app siempre lleva un círculo de color detrás (ver banners),
            // así que se repite el mismo criterio acá.
            Box(
                modifier =
                    Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_docusmart_logo),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

// ── Tab de Lectura ────────────────────────────────────
// Rediseñado 2026-09-08 a pedido explícito del usuario: antes mostraba los
// párrafos extraídos como texto plano con resaltado -- ahora muestra el PDF
// real (StudyPdfViewer, mismo renderizador que ya usa el Visor) mientras la
// voz lee de fondo a partir del texto ya extraído (`documentText`, que ya no
// baja hasta esta función -- solo hace falta el conteo de resaltados y el
// URI para dibujar el PDF). Reemplaza a ReadingParagraphRow (fila por
// párrafo, sin uso ahora que no hay párrafos individuales que tocar).
@Composable
internal fun ReadingTab(
    documentUri: Uri?,
    isLoading: Boolean,
    isCurrentHighlighted: Boolean,
    currentSpeakingIndex: Int,
    // Fase 4 (2026-09-22): rango exacto (en caracteres, sobre el párrafo que
    // suena) que reporta el motor TTS en tiempo real, cuando lo soporta --
    // permite resaltar la frase/palabra exacta en el modo Texto, no solo el
    // párrafo completo. Null si el motor no lo reporta (fallback: se sigue
    // viendo el párrafo completo resaltado, como antes).
    currentSpeakingRange: IntRange? = null,
    isSpeaking: Boolean,
    ttsReady: Boolean,
    // Hallazgo H3 de la auditoría (ronda 13, 2026-09-18): null = sin error,
    // no-null = el motor TTS no pudo inicializarse -- se muestra cerca del
    // botón "Leer todo" en vez de dejarlo deshabilitado sin explicación.
    ttsErrorMessage: String?,
    isExtractingMore: Boolean,
    readingFinished: Boolean,
    currentPage: Int,
    totalPages: Int,
    onToggleHighlightCurrent: () -> Unit,
    onSpeakAll: () -> Unit,
    onSelectDoc: () -> Unit,
    readingHistory: List<ReadingProgress>,
    onResumeDocument: (ReadingProgress) -> Unit,
    onDeleteDocument: (ReadingProgress) -> Unit,
    availableVoices: List<Voice> = emptyList(),
    onVoiceSelectorClick: () -> Unit = {},
    speedLabel: String? = null,
    onSpeedClick: () -> Unit = {},
    onStepParagraph: ((delta: Int) -> Unit)? = null,
    documentText: List<String> = emptyList(),
    highlights: Set<Int> = emptySet(),
    onJumpToParagraph: ((Int) -> Unit)? = null,
) {
    // Fase 3 del rediseño de Lectura (2026-09-22): además del PDF real, un modo Texto
    // que sigue la lectura -- útil cuando el PDF es una foto/escaneo denso y ayuda a
    // seguir el punto exacto que está sonando. Empieza siempre en PDF (comportamiento
    // de siempre); Texto es una vista alterna, no reemplaza al visor.
    var viewMode by rememberSaveable { mutableStateOf(ReadingViewMode.PDF) }
    // Con la voz activa, cambiar de párrafo o de velocidad reinicia la lectura desde el
    // punto de retoma: "Leer todo" detiene y otra vez "Leer todo" arranca desde ahí.
    val restartIfSpeaking = {
        if (isSpeaking) {
            onSpeakAll()
            onSpeakAll()
        }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        when {
            // Pedido explícito del usuario 2026-09-08: la extracción de un
            // PDF real puede tardar bastante (probado con un libro de 55
            // páginas) -- un spinner solo, sin texto, hacía parecer que la
            // app se había colgado. Mismo mensaje que usa StudyPdfViewer más
            // abajo para su propia carga (para el usuario es un solo
            // "cargando documento", sin importar que sean 2 pasos técnicos
            // distintos).
            isLoading ->
                LoadingIndicator(
                    stringResource(R.string.study_loading_document),
                    modifier = Modifier.align(Alignment.Center),
                )
            documentUri == null ->
                ReadingEmptyState(
                    readingHistory = readingHistory,
                    onSelectDoc = onSelectDoc,
                    onVoiceSelectorClick = onVoiceSelectorClick,
                    voiceEnabled = availableVoices.isNotEmpty(),
                    onResumeDocument = onResumeDocument,
                    onDeleteDocument = onDeleteDocument,
                )
            else -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Pedido explícito del usuario 2026-09-22: la ficha del documento
                    // (portada, avance, voz, resaltados) se mudó al banner azul de
                    // arriba (`ReadingDocumentBanner` en StudyScreen) para dejarle más
                    // alto al documento -- ya no vive acá como tarjeta aparte.
                    ReadingViewModeSelector(
                        mode = viewMode,
                        onModeChange = { viewMode = it },
                        onVoiceSelectorClick = onVoiceSelectorClick,
                        voiceEnabled = availableVoices.isNotEmpty(),
                        onSelectDoc = onSelectDoc,
                    )
                    // ── PDF real o texto extraído, la voz lee de fondo ─
                    // Rediseño 2026-09-21: el documento ocupa todo el alto disponible;
                    // los controles (abrir, voz, marcar y leer) viven en un reproductor
                    // fijo abajo, al alcance del pulgar, en vez de tres franjas arriba.
                    when (viewMode) {
                        ReadingViewMode.PDF ->
                            StudyPdfViewer(
                                uri = documentUri,
                                currentPage = currentPage,
                                modifier = Modifier.fillMaxWidth().weight(1f),
                            )
                        ReadingViewMode.TEXT ->
                            ReadingTextView(
                                paragraphs = documentText,
                                currentIndex = currentSpeakingIndex,
                                currentRange = currentSpeakingRange,
                                highlightedIndices = highlights,
                                onParagraphClick = { index ->
                                    onJumpToParagraph?.invoke(index)
                                    restartIfSpeaking()
                                },
                                modifier = Modifier.fillMaxWidth().weight(1f),
                            )
                    }
                    ReadingPlayerBar(
                        isSpeaking = isSpeaking,
                        ttsReady = ttsReady,
                        ttsErrorMessage = ttsErrorMessage,
                        isExtractingMore = isExtractingMore,
                        readingFinished = readingFinished,
                        isCurrentHighlighted = isCurrentHighlighted,
                        currentPage = currentPage,
                        totalPages = totalPages,
                        onToggleHighlightCurrent = onToggleHighlightCurrent,
                        onSpeakAll = onSpeakAll,
                        speedLabel = speedLabel,
                        onSpeedClick = {
                            onSpeedClick()
                            restartIfSpeaking()
                        },
                        onPreviousParagraph =
                            onStepParagraph?.let { step ->
                                {
                                    step(-1)
                                    restartIfSpeaking()
                                }
                            },
                        onNextParagraph =
                            onStepParagraph?.let { step ->
                                {
                                    step(1)
                                    restartIfSpeaking()
                                }
                            },
                    )
                }
            }
        }
    }
}

// Fase 3 (2026-09-22): PDF (por defecto) o Texto extraído, con el párrafo que
// suena resaltado y seguimiento automático.
internal enum class ReadingViewMode { PDF, TEXT }

@Composable
private fun ReadingViewModeSelector(
    mode: ReadingViewMode,
    onModeChange: (ReadingViewMode) -> Unit,
    // Pedido explícito del usuario 2026-09-22: un tercer botón acá mismo para elegir
    // voz, además del que ya vive en el reproductor de abajo -- acceso rápido sin
    // tener que bajar hasta la barra de controles.
    onVoiceSelectorClick: () -> Unit,
    voiceEnabled: Boolean,
    onSelectDoc: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = mode == ReadingViewMode.PDF,
            onClick = { onModeChange(ReadingViewMode.PDF) },
            leadingIcon = {
                Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
            },
            label = { Text(stringResource(R.string.study_view_mode_pdf)) },
        )
        FilterChip(
            selected = mode == ReadingViewMode.TEXT,
            onClick = { onModeChange(ReadingViewMode.TEXT) },
            leadingIcon = {
                Icon(Icons.AutoMirrored.Rounded.Notes, contentDescription = null, modifier = Modifier.size(16.dp))
            },
            label = { Text(stringResource(R.string.study_view_mode_text)) },
        )
        AssistChip(
            onClick = onVoiceSelectorClick,
            enabled = voiceEnabled,
            leadingIcon = {
                Icon(Icons.Rounded.RecordVoiceOver, contentDescription = null, modifier = Modifier.size(16.dp))
            },
            label = { Text(stringResource(R.string.study_choose_voice)) },
        )
        // Pedido explícito del usuario 2026-09-22: "abrir documento" sale del
        // reproductor de abajo y se une acá, junto a PDF/Texto/Elegir voz.
        AssistChip(
            onClick = onSelectDoc,
            leadingIcon = {
                Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
            },
            label = { Text(stringResource(R.string.qr_open_document)) },
        )
    }
}

// Texto extraído, un párrafo por fila (misma granularidad que "marcar" y que la cola
// del TTS: no hay una frase individual rastreable dentro del párrafo). El que está
// sonando se resalta y la lista se sigue automáticamente; tocar cualquier párrafo
// mueve el punto de lectura (y reinicia la voz si estaba sonando).
@Composable
private fun ReadingTextView(
    paragraphs: List<String>,
    currentIndex: Int,
    highlightedIndices: Set<Int>,
    onParagraphClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    // Fase 4 (2026-09-22): rango exacto que suena dentro del párrafo actual,
    // cuando el motor TTS lo reporta (ver `onRangeStart` en StudyScreen). Con
    // rango, solo esa frase/palabra se resalta -- sin él, se resalta el
    // párrafo completo (fallback para motores/voces que no lo soportan).
    currentRange: IntRange? = null,
) {
    if (paragraphs.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            LoadingIndicator(stringResource(R.string.study_loading_document))
        }
        return
    }
    val listState = rememberLazyListState()
    LaunchedEffect(currentIndex) {
        if (currentIndex in paragraphs.indices) listState.animateScrollToItem(currentIndex)
    }
    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        itemsIndexed(paragraphs) { index, paragraph ->
            val isCurrent = index == currentIndex
            // `splitForHighlight` (StudyTtsQueue.kt) es la única que interpreta el
            // rango -- acota límites y corrige el "start until end" de onRangeStart,
            // así que acá solo se pregunta si terminó habiendo una frase real.
            val parts = if (isCurrent) splitForHighlight(paragraph, currentRange) else null
            val hasPhraseHighlight = parts != null && parts.highlighted.isNotEmpty()
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .then(
                            // Sin rango exacto (o sin nada resaltable todavía), el párrafo
                            // completo se resalta (fallback de siempre); con frase real, el
                            // párrafo queda plano y solo esa frase se resalta más abajo.
                            if (isCurrent && !hasPhraseHighlight) {
                                Modifier.background(MaterialTheme.colorScheme.primaryContainer)
                            } else {
                                Modifier
                            },
                        ).clickable(role = Role.Button) { onParagraphClick(index) }
                        .padding(10.dp),
            ) {
                if (index in highlightedIndices) {
                    Icon(
                        imageVector = Icons.Rounded.Bookmark,
                        contentDescription = null,
                        tint = WarningAmber,
                        modifier = Modifier.size(16.dp).padding(top = 3.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = readingParagraphText(paragraph, parts),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isCurrent && !hasPhraseHighlight) FontWeight.Medium else FontWeight.Normal,
                    color =
                        if (isCurrent && !hasPhraseHighlight) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                )
            }
        }
    }
}

// Construye el AnnotatedString con la frase exacta resaltada dentro del párrafo
// (fase 4); sin partes resaltables, devuelve el texto tal cual.
@Composable
private fun readingParagraphText(
    paragraph: String,
    parts: HighlightedText?,
): AnnotatedString {
    if (parts == null || parts.highlighted.isEmpty()) return AnnotatedString(paragraph)
    val highlightStyle =
        SpanStyle(
            background = MaterialTheme.colorScheme.tertiaryContainer,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            fontWeight = FontWeight.SemiBold,
        )
    return buildAnnotatedString {
        append(parts.before)
        withStyle(highlightStyle) { append(parts.highlighted) }
        append(parts.after)
    }
}

// Banner de Lectura con la ficha del documento integrada (fase 4, 2026-09-22,
// pedido explícito del usuario): antes la portada/avance/voz/resaltados vivían
// en una tarjeta blanca aparte, debajo del banner -- ahora comparten el mismo
// degradado, dejándole más alto al documento. El logo se mantiene, igual que en
// el resto de los banners compactos; "Volver"/"Inicio" siguen debajo (mismo
// `BannerNavRow` que usa `DocuSmartTopBanner`, para no duplicar esa lógica).
@Composable
internal fun ReadingDocumentBanner(
    documentUri: Uri,
    documentName: String,
    currentPage: Int,
    totalPages: Int,
    readingFinished: Boolean,
    highlightedCount: Int,
    voiceName: String?,
    onBack: () -> Unit,
    onHome: () -> Unit,
) {
    val gradient = rememberBannerGradient()
    Column(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.large)
                    .background(brush = Brush.linearGradient(gradient))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Pedido explícito del usuario 2026-09-22 ("quita el logo de
                // DocuSmart... dejémoslo como el modelo"): sin el logo, la portada
                // queda como lo primero que se ve, igual que en la maqueta.
                Box(
                    modifier =
                        Modifier
                            .width(48.dp)
                            .height(64.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(Color.White.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    // Portada real -- Coil ya sabe renderizar la primera página de un PDF
                    // (PdfThumbnailFetcher, mismo mecanismo que Biblioteca/Favoritos). Si
                    // falla (documento no PDF, sin permiso, etc.), queda el ícono de libro.
                    Icon(
                        imageVector = Icons.Rounded.MenuBook,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                    AsyncImage(
                        model = documentUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = documentName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (totalPages > 0) {
                        Text(
                            text = stringResource(R.string.study_total_pages, totalPages),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.78f),
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            val fraction = listenedFraction(currentPage, totalPages, readingFinished)
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)),
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.3f),
            )
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                ReadingBannerChip(stringResource(R.string.study_listened_percent, (fraction * 100).toInt()))
                ReadingBannerChip(stringResource(R.string.study_highlighted_count, highlightedCount))
                if (voiceName != null) {
                    ReadingBannerChip(stringResource(R.string.study_voice_chip, voiceName))
                }
            }
        }
        BannerNavRow(onBack = onBack, onHome = onHome)
    }
}

@Composable
private fun ReadingBannerChip(text: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = Color.White.copy(alpha = 0.18f),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

// Reproductor fijo de Lectura (rediseño 2026-09-21): panel con el degradado del
// banner (contraste garantizado con texto blanco), estado y progreso arriba, y
// abajo abrir / voz / marcar y el botón grande de leer.
@Composable
private fun ReadingPlayerBar(
    isSpeaking: Boolean,
    ttsReady: Boolean,
    ttsErrorMessage: String?,
    isExtractingMore: Boolean,
    readingFinished: Boolean,
    isCurrentHighlighted: Boolean,
    currentPage: Int,
    totalPages: Int,
    onToggleHighlightCurrent: () -> Unit,
    onSpeakAll: () -> Unit,
    speedLabel: String?,
    onSpeedClick: () -> Unit,
    onPreviousParagraph: (() -> Unit)?,
    onNextParagraph: (() -> Unit)?,
) {
    val gradient = rememberBannerGradient()
    val speedDescription = stringResource(R.string.study_reading_speed)
    val panelShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .shadow(8.dp, panelShape)
                .clip(panelShape)
                .background(Brush.linearGradient(gradient))
                .padding(horizontal = 12.dp)
                .padding(top = 12.dp, bottom = 8.dp),
    ) {
        ReadingStatusLine(
            isSpeaking = isSpeaking,
            isExtractingMore = isExtractingMore,
            readingFinished = readingFinished,
            currentPage = currentPage,
            totalPages = totalPages,
        )
        ReadingProgressLine(
            isExtractingMore = isExtractingMore,
            fraction = listenedFraction(currentPage, totalPages, readingFinished),
            hasPages = totalPages > 0,
        )
        // Hallazgo H3 (ronda 13, 2026-09-18): si el motor TTS no pudo iniciar,
        // se explica aquí en vez de dejar "Leer todo" deshabilitado sin motivo.
        if (!ttsReady && ttsErrorMessage != null) {
            Text(
                text = ttsErrorMessage,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Pedido explícito del usuario 2026-09-22: "abrir" y "voz" se mudaron
            // arriba, junto a PDF/Texto -- acá solo queda "marcar", que sí es una
            // acción propia de la lectura en curso.
            // Marca el párrafo que suena ahora (alimenta "Párrafos resaltados" de Notas).
            ReadingWellIconButton(
                icon = if (isCurrentHighlighted) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                contentDescription = stringResource(R.string.study_mark_current_paragraph),
                onClick = onToggleHighlightCurrent,
                enabled = isSpeaking,
            )
            Spacer(Modifier.weight(1f))
            if (speedLabel != null) {
                Box(
                    // H17 (auditoría de accesibilidad TalkBack 2026-09-18): objetivo
                    // táctil mínimo de 48dp -- mismo mínimo que ya exige el resto de la
                    // app (ver BannerNavAction), 44dp quedaba por debajo.
                    modifier =
                        Modifier
                            .height(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(alpha = 0.15f))
                            .clickable(role = Role.Button, onClick = onSpeedClick)
                            .semantics { contentDescription = speedDescription }
                            .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = speedLabel,
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onPreviousParagraph != null) {
                IconButton(onClick = onPreviousParagraph, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.Rounded.SkipPrevious,
                        contentDescription = stringResource(R.string.study_previous_paragraph),
                        tint = Color.White.copy(alpha = 0.75f),
                        modifier = Modifier.size(26.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
            }
            // Círculo blanco con solo el ícono, a partir de una maqueta que el usuario
            // compartió (2026-09-22) -- la etiqueta ("Leer todo"/"Detener"/"Volver a
            // leer") sigue viva como descripción de accesibilidad, no como texto visible.
            val playLabel =
                when {
                    isSpeaking -> stringResource(R.string.study_stop)
                    readingFinished -> stringResource(R.string.study_read_again)
                    else -> stringResource(R.string.study_read_all)
                }
            Box(
                modifier =
                    Modifier
                        .size(58.dp)
                        .clip(CircleShape)
                        .background(if (ttsReady) Color.White else Color.White.copy(alpha = 0.4f))
                        .clickable(enabled = ttsReady, role = Role.Button, onClick = onSpeakAll)
                        .semantics { contentDescription = playLabel },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isSpeaking) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = if (ttsReady) gradient[1] else Color.White,
                    modifier = Modifier.size(28.dp),
                )
            }
            if (onNextParagraph != null) {
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = onNextParagraph, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.Rounded.SkipNext,
                        contentDescription = stringResource(R.string.study_next_paragraph),
                        tint = Color.White.copy(alpha = 0.75f),
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
        }
    }
}

// Botón de ícono en una caja translúcida ("well"), a partir de la maqueta que el
// usuario compartió (2026-09-22) -- reemplaza al `IconButton` plano de antes para
// las tres acciones secundarias del reproductor (abrir, voz, marcar).
@Composable
private fun ReadingWellIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        // H17 (auditoría de accesibilidad TalkBack 2026-09-18): objetivo táctil
        // mínimo de 48dp -- 44dp quedaba por debajo del mínimo que ya exige el
        // resto de la app (ver BannerNavAction).
        modifier =
            modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center,
    ) {
        IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.matchParentSize()) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = Color.White.copy(alpha = if (enabled) 1f else 0.45f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

// Estado de la lectura; sin texto cuando no pasa nada (los datos en reposo van
// en la tira de chips de arriba).
@Composable
private fun ReadingStatusLine(
    isSpeaking: Boolean,
    isExtractingMore: Boolean,
    readingFinished: Boolean,
    currentPage: Int,
    totalPages: Int,
) {
    val text =
        when {
            readingFinished -> stringResource(R.string.study_reading_finished)
            isSpeaking && totalPages > 0 -> stringResource(R.string.study_reading_page, currentPage, totalPages)
            isSpeaking -> stringResource(R.string.study_reading_document)
            // El PDF ya se puede leer pero el resto se sigue extrayendo.
            isExtractingMore -> stringResource(R.string.study_extracting_more, totalPages)
            else -> null
        }
    if (text != null) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

// Fracción ya escuchada: la página actual (1-based) aún no está completa, por eso
// se cuentan las anteriores; al terminar la lectura es 100 %.
internal fun listenedFraction(
    currentPage: Int,
    totalPages: Int,
    readingFinished: Boolean,
): Float =
    when {
        totalPages <= 0 -> 0f
        readingFinished -> 1f
        else -> ((currentPage - 1).coerceAtLeast(0).toFloat() / totalPages).coerceIn(0f, 1f)
    }

// Barra fina de progreso: avance de páginas al leer; indeterminada mientras se
// termina de extraer el texto del resto del documento.
@Composable
private fun ReadingProgressLine(
    isExtractingMore: Boolean,
    fraction: Float,
    hasPages: Boolean,
) {
    val lineModifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(4.dp).clip(RoundedCornerShape(50))
    val track = Color.White.copy(alpha = 0.3f)
    if (isExtractingMore) {
        LinearProgressIndicator(modifier = lineModifier, color = Color.White, trackColor = track)
    } else if (hasPages) {
        LinearProgressIndicator(
            progress = { fraction },
            modifier = lineModifier,
            color = Color.White,
            trackColor = track,
        )
    }
}

// Extraído de ReadingTab (LongMethod por detekt) -- estado sin documento
// activo. "Retomar lectura" (2026-09-08): con historial pendiente, el
// contenido ya no cabe centrado en pantallas chicas (hasta 10 documentos) --
// pasa a ser una columna con scroll, con la lista arriba del botón "Abrir
// documento". Sin historial, exactamente el mismo estado vacío de siempre.
@Composable
private fun BoxScope.ReadingEmptyState(
    readingHistory: List<ReadingProgress>,
    onSelectDoc: () -> Unit,
    onVoiceSelectorClick: () -> Unit,
    voiceEnabled: Boolean,
    onResumeDocument: (ReadingProgress) -> Unit,
    onDeleteDocument: (ReadingProgress) -> Unit,
) {
    val emptyStateModifier =
        if (readingHistory.isEmpty()) {
            Modifier.align(Alignment.Center)
        } else {
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        }
    Column(
        modifier = emptyStateModifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(80.dp)
                    .background(
                        // Bug real corregido 2026-09-04 (backlog UX §7,
                        // HU-UX-06): fijo en tonos de azul, ignorando el "Color
                        // de acento" elegido en Ajustes.
                        brush = Brush.linearGradient(rememberAccentGradient()),
                        shape = MaterialTheme.shapes.extraLarge,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.MenuBook,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(40.dp),
            )
        }
        Text(
            text = stringResource(R.string.study_tab_reading),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.study_reading_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(
            onClick = onSelectDoc,
            shape = MaterialTheme.shapes.medium,
        ) {
            Icon(
                imageVector = Icons.Rounded.FolderOpen,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.qr_open_document))
        }
        Text(
            text = stringResource(R.string.study_reading_helper),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        OutlinedButton(
            onClick = onVoiceSelectorClick,
            enabled = voiceEnabled,
            shape = MaterialTheme.shapes.medium,
        ) {
            Icon(
                imageVector = Icons.Rounded.RecordVoiceOver,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.study_choose_voice))
        }
        if (readingHistory.isNotEmpty()) {
            Text(
                text = stringResource(R.string.study_continue_reading),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            readingHistory.forEach { progress ->
                ReadingHistoryCard(
                    progress = progress,
                    onClick = { onResumeDocument(progress) },
                    onDelete = { onDeleteDocument(progress) },
                )
            }
        }
    }
}

// "Retomar lectura" (2026-09-08): un PDF con progreso guardado, en la lista
// "Continuar leyendo" del estado vacío de Lectura. Tocarlo reabre el archivo
// y deja la voz lista para seguir desde el mismo párrafo/página.
// Pedido explícito del usuario 2026-09-08: además de retomar, poder quitar un
// PDF de esta lista (no borra el archivo, solo el progreso guardado).
@Composable
private fun ReadingHistoryCard(
    progress: ReadingProgress,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val shape = MaterialTheme.shapes.medium
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .accentShadow(shape = shape, elevation = 1.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface)
                .accentBorder(shape = shape)
                .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.MenuBook,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = progress.documentName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (progress.totalPages > 0) {
                    Text(
                        text =
                            stringResource(
                                R.string.study_resume_page_progress,
                                progress.currentPage,
                                progress.totalPages,
                            ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Icon(
                imageVector = Icons.Rounded.PlayCircle,
                contentDescription = stringResource(R.string.study_resume_reading),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
            // Subido de 28dp a 48dp (auditoría de testers 2026-09-12, "botones pequeños").
            IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = stringResource(R.string.study_remove_from_history),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

// ── Visor de PDF embebido en Lectura ──────────────────
// Mismo patrón que el Visor de documentos (renderPdfPagesToBitmaps,
// extraído a core/pdf/PdfPageRenderer.kt al necesitarse acá también) pero
// sin la lógica de resaltado de búsqueda -- Estudio no la necesita.
@Composable
private fun StudyPdfViewer(
    uri: Uri,
    currentPage: Int,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var pages by remember(uri) { mutableStateOf<List<PdfPageBitmap>>(emptyList()) }
    var loadError by remember(uri) { mutableStateOf(false) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    val listState = rememberLazyListState()

    // Hallazgo real de la auditoría general 2026-09-17 (quinta pasada):
    // `currentPage` solo alimentaba el texto "Página X de Y" -- la imagen
    // visible se quedaba fija donde el usuario la había dejado mientras
    // "Leer todo" avanzaba de página en segundo plano. `currentPage` es
    // 1-based (ver `study_reading_page`).
    LaunchedEffect(currentPage, pages) {
        val index = pdfViewerPageIndex(currentPage, pages.size)
        if (pages.isNotEmpty()) listState.animateScrollToItem(index)
    }

    LaunchedEffect(uri) {
        pages =
            withContext(Dispatchers.IO) {
                try {
                    renderPdfPagesToBitmaps(uri, context, cachePrefix = "study")
                } catch (e: CancellationException) {
                    // Hallazgo H5 de la auditoría (ronda 13, 2026-09-18): si `uri`
                    // cambia mientras el render está en curso, la cancelación no
                    // debe quedar atrapada por el catch genérico de abajo --
                    // mismo mecanismo ya aplicado en otros puntos de este mismo
                    // archivo (ver líneas ~2764, ~3503, ~3576).
                    throw e
                } catch (e: Exception) {
                    Timber.e("Estudio: error renderizando PDF (${e.javaClass.simpleName})")
                    loadError = true
                    emptyList()
                }
            }
    }

    // Hallazgo H6 de la auditoría (ronda 13, 2026-09-18): mismo fix ya
    // aplicado en ViewerScreen.kt (R11, riesgo real de OOM) -- cada bitmap
    // ARGB_8888 de página es 2x el tamaño de la página, hasta 240-400MB
    // nativos residentes para un documento de 30-50 páginas si nunca se
    // reciclan. `remember(uri)` arriba ya fuerza una lista nueva de `pages`
    // por documento, así que este DisposableEffect(uri) libera la lista
    // ANTERIOR de forma determinista, tanto al cambiar de documento como al
    // salir de Estudio.
    DisposableEffect(uri) {
        onDispose {
            pages.forEach { it.bitmap.recycle() }
        }
    }

    when {
        loadError ->
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.viewer_error),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        pages.isEmpty() ->
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                LoadingIndicator(stringResource(R.string.study_loading_document))
            }
        else ->
            LazyColumn(
                state = listState,
                modifier =
                    modifier
                        .onSizeChanged { containerSize = it }
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val next =
                                    applyPdfGesture(
                                        current = PdfTransform(scale, offsetX, offsetY),
                                        zoom = zoom,
                                        panX = pan.x,
                                        panY = pan.y,
                                        containerWidth = containerSize.width.toFloat(),
                                        containerHeight = containerSize.height.toFloat(),
                                    )
                                scale = next.scale
                                offsetX = next.offsetX
                                offsetY = next.offsetY
                            }
                        }
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offsetX,
                            translationY = offsetY,
                        ),
                contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp, start = 8.dp, end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(pages) { index, pageBitmap ->
                    val shape = MaterialTheme.shapes.small
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .accentShadow(shape = shape, elevation = 2.dp)
                                .clip(shape)
                                .background(MaterialTheme.colorScheme.surface)
                                .accentBorder(shape = shape),
                    ) {
                        Image(
                            bitmap = pageBitmap.bitmap.asImageBitmap(),
                            // Hallazgo real de la auditoría general 2026-09-17
                            // (quinta pasada): sin contentDescription, TalkBack
                            // no anuncia en qué página está durante "Leer todo"
                            // -- justo una función pensada para accesibilidad.
                            // Mismo string ya usado por el Visor principal.
                            contentDescription = stringResource(R.string.viewer_page_content_desc, index + 1),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
    }
}

// ── Tab de Notas ──────────────────────────────────────
@Composable
internal fun NotesTab(
    notes: String,
    onNotesChange: (String) -> Unit,
    highlights: Set<Int>,
    documentText: List<String>,
    openNoteId: String? = null,
    viewModel: NotesViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val savedNotes = uiState.notes

    // ── Estado ────────────────────────────────────────────────────────────────
    var currentTitle by remember { mutableStateOf("") }
    var currentNote by remember { mutableStateOf(notes) }
    var showDeleteAll by remember { mutableStateOf(false) }
    var isListening by remember { mutableStateOf(false) }
    // Backlog UX #49: imágenes elegidas para la nota que se está escribiendo
    // todavía, se limpia al guardar (o al descartar una con la X).
    var currentImageUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    // Backlog UX #52: recordatorio de repaso de la nota que se está
    // escribiendo -- `reminderChip` es solo para resaltar el FilterChip
    // elegido (NONE si no hay ninguno), `reminderAt` es la fecha/hora real
    // que se manda a guardar. Ambos se resetean juntos al guardar.
    var reminderChip by remember { mutableStateOf(NoteReminderChip.NONE) }
    var reminderAt by remember { mutableStateOf<Long?>(null) }
    // RNF2: si el usuario negó POST_NOTIFICATIONS, la sección de
    // recordatorio queda deshabilitada -- sin esto se podría programar una
    // alarma cuya notificación nunca va a poder mostrarse, en silencio.
    var notificationsGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    context, android.Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val notificationPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) { granted -> notificationsGranted = granted }
    // Hallazgo real de la auditoría general 2026-09-17 (B20): a diferencia
    // de Agenda (canScheduleExactAlarms, ver AgendaScreen.kt), acá
    // notificationsGranted solo se actualizaba desde el diálogo del sistema
    // -- si el usuario lo negaba y después activaba el permiso a mano desde
    // Ajustes del sistema, la sección de recordatorio de Notas seguía
    // deshabilitada indefinidamente al volver, sin que la app se enterara.
    ReloadOnScreenResume {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationsGranted = ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
    val notesListState = rememberLazyListState()

    // ── Adjuntar imagen (galería o recorte escaneado) ────────────────────────
    val galleryLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.GetMultipleContents(),
        ) { uris -> if (uris.isNotEmpty()) currentImageUris = currentImageUris + uris }

    // "Recorte escaneado" del pedido original (#49) -- mismo escáner de ML
    // Kit que ya usa Convertir/Escáner, en modo PHOTO (más liviano que
    // DOCUMENT, sin el flujo multi-página de escanear un documento entero)
    // y con pageLimit=1 porque acá se adjunta de a una imagen por vez.
    val onScanImage =
        rememberDocumentScannerAction(
            activity = context.findActivity(),
            mode = ScannerMode.PHOTO,
            pageLimit = 1,
            onPagesScanned = { pages -> currentImageUris = currentImageUris + pages },
            onScanError = { message -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show() },
        )

    // Ronda 16: "dd/MM/yyyy · HH:mm" fijo mostraba día/mes en el orden equivocado
    // para usuarios de EE.UU./Japón/China y forzaba 24h -- ahora sigue el idioma.
    val notesLocale = LocalConfiguration.current.locales[0]
    val dateFormatter =
        remember(notesLocale) {
            java.text.DateFormat.getDateTimeInstance(
                java.text.DateFormat.MEDIUM,
                java.text.DateFormat.SHORT,
                notesLocale,
            )
        }

    // ── Reconocimiento de voz ─────────────────────────────────────────────────
    val speechLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            isListening = false
            val matches =
                result.data
                    ?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
            if (!matches.isNullOrEmpty()) {
                val spoken = matches[0]
                currentNote =
                    if (currentNote.isBlank()) {
                        spoken
                    } else {
                        "$currentNote $spoken"
                    }
                onNotesChange(currentNote)
            }
        }

    val voicePrompt = stringResource(R.string.study_voice_prompt)

    fun startVoiceInput() {
        try {
            val intent =
                android.content.Intent(
                    android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH,
                ).apply {
                    putExtra(
                        android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                    )
                    putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                    putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, voicePrompt)
                    putExtra(android.speech.RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                }
            isListening = true
            speechLauncher.launch(intent)
        } catch (e: Exception) {
            isListening = false
            Timber.e("Error iniciando reconocimiento de voz (${e.javaClass.simpleName})")
        }
    }

    // ── Diálogo eliminar todas ────────────────────────────────────────────────
    if (showDeleteAll) {
        AlertDialog(
            onDismissRequest = { showDeleteAll = false },
            shape = MaterialTheme.shapes.large,
            title = { Text(stringResource(R.string.study_delete_all_notes_title)) },
            text = { Text(stringResource(R.string.study_delete_all_notes_body)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteAllNotes()
                    showDeleteAll = false
                }) { Text(stringResource(R.string.general_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAll = false }) { Text(stringResource(R.string.general_cancel)) }
            },
        )
    }

    // Backlog UX #52, AC2: al llegar desde una notificación de recordatorio,
    // hace scroll hasta la nota específica y la marca `isHighlighted` en
    // NoteListItem -- se recalcula cada vez que cambia la lista (llega
    // async desde Room) hasta encontrarla. El offset suma los ítems fijos
    // de arriba (editor + cabecera de la lista) más los de la sección de
    // párrafos resaltados, si está presente -- ver highlightedParagraphsSection().
    // `highlights`/`documentText` también son claves: la extracción del
    // documento sigue en curso en segundo plano (ver comentario de
    // `extractionComplete` más arriba) y puede agregar párrafos resaltados
    // DESPUÉS de este primer cálculo, corriendo el offset de la lista de
    // notas -- sin esto, el scroll quedaba calculado con datos viejos.
    LaunchedEffect(openNoteId, savedNotes, highlights, documentText) {
        val targetId = openNoteId ?: return@LaunchedEffect
        val noteIndex = savedNotes.indexOfFirst { it.note.id == targetId }
        // Ítems fijos: sección de resaltados (si hay), NoteEditorCard y NotesListHeader.
        val scrollIndex = notesListScrollIndex(noteIndex, highlights.size, documentText.size)
        if (scrollIndex == null) return@LaunchedEffect
        notesListState.animateScrollToItem(scrollIndex)
    }

    // Bug real corregido 2026-09-08: todo esto antes vivía en un `Column`
    // fijo (sin scroll) con un `LazyColumn` aparte solo para la lista de
    // notas -- al agregar la tarjeta del editor, el contenido ya no cabía
    // en pantallas chicas y el estado "Sin notas guardadas" quedaba cortado
    // sin forma de hacer scroll para verlo. Ahora es una única `LazyColumn`
    // para toda la pestaña.
    LazyColumn(state = notesListState, modifier = Modifier.fillMaxSize()) {
        // ── Párrafos resaltados ───────────────────────────────────────────────
        // Extraída a highlightedParagraphsSection() (detekt: LongMethod).
        highlightedParagraphsSection(highlights, documentText)

        // ── Editor de nota nueva ──────────────────────────────────────────────
        // Envuelto en su propia tarjeta (antes flotaba directo sobre el
        // fondo) para que tenga la misma jerarquía visual que las notas
        // guardadas de más abajo, en vez de sentirse como una sección aparte.
        // Extraído a NoteEditorCard() (detekt: LongMethod).
        item {
            NoteEditorCard(
                currentTitle = currentTitle,
                onTitleChange = { currentTitle = it },
                currentNote = currentNote,
                onNoteChange = {
                    currentNote = it
                    onNotesChange(it)
                },
                isListening = isListening,
                onVoiceClick = { startVoiceInput() },
                imageUris = currentImageUris,
                onGalleryClick = { galleryLauncher.launch("image/*") },
                onScanClick = onScanImage,
                onRemoveImage = { uri -> currentImageUris = currentImageUris - uri },
                reminderChip = reminderChip,
                reminderAt = reminderAt,
                onReminderChange = { chip, millis ->
                    reminderChip = chip
                    reminderAt = millis
                },
                notificationsGranted = notificationsGranted,
                onRequestNotifications = {
                    notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                },
                onSave = { title, text ->
                    viewModel.createNote(title, text, currentImageUris, reminderAt)
                    currentNote = ""
                    currentTitle = ""
                    currentImageUris = emptyList()
                    reminderChip = NoteReminderChip.NONE
                    reminderAt = null
                    onNotesChange("")
                },
            )
        }

        // ── Lista de notas guardadas ──────────────────────────────────────────
        // Extraída a NotesListHeader()/NotesEmptyState() (detekt: LongMethod).
        item {
            NotesListHeader(
                notes = savedNotes,
                onDeleteAllClick = { showDeleteAll = true },
            )
        }

        if (savedNotes.isEmpty()) {
            item { NotesEmptyState() }
        } else {
            itemsIndexed(savedNotes, key = { _, note -> note.note.id }) { _, noteWithImages ->
                NoteListItem(
                    noteWithImages = noteWithImages,
                    dateFormatter = dateFormatter,
                    isHighlighted = noteWithImages.note.id == openNoteId,
                    onLinkClick = { viewModel.showLinkDialog(noteWithImages.note.id) },
                    onEditClick = { viewModel.startEditingNote(noteWithImages.note.id) },
                    onDeleteClick = { viewModel.deleteNote(noteWithImages) },
                )
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    NotesTabDialogs(
        uiState = uiState,
        savedNotes = savedNotes,
        viewModel = viewModel,
        notificationsGranted = notificationsGranted,
        onRequestNotifications = {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        },
    )
}

// Extraída de NotesTab() (detekt: LongMethod) -- diálogos de vincular
// documento (backlog UX #50) y de editar una nota ya guardada (B22,
// auditoría general 2026-09-17).
@Composable
private fun NotesTabDialogs(
    uiState: NotesUiState,
    savedNotes: List<NoteWithImages>,
    viewModel: NotesViewModel,
    notificationsGranted: Boolean,
    onRequestNotifications: () -> Unit,
) {
    uiState.linkDocumentDialogForNoteId?.let { noteId ->
        val currentDocumentId = savedNotes.firstOrNull { it.note.id == noteId }?.note?.documentId
        NoteLinkDocumentDialog(
            currentDocumentId = currentDocumentId,
            onDismiss = { viewModel.dismissLinkDialog() },
            onSelect = { doc -> viewModel.linkDocument(noteId, doc.id) },
            onUnlink = { viewModel.linkDocument(noteId, null) },
        )
    }

    uiState.editingNoteId?.let { noteId ->
        val noteWithImages = savedNotes.firstOrNull { it.note.id == noteId } ?: return@let
        NoteEditDialog(
            noteWithImages = noteWithImages,
            notificationsGranted = notificationsGranted,
            onRequestNotifications = onRequestNotifications,
            onDismiss = { viewModel.cancelEditingNote() },
            onSave = { title, text, reminderAt, keptImages, removedImages, newImageUris ->
                viewModel.updateNote(noteId, title, text, reminderAt, keptImages, removedImages, newImageUris)
            },
        )
    }
}

// Extraída de NotesTab() (detekt: LongMethod) -- párrafos resaltados del
// documento actualmente en Lectura, con acceso rápido a citarlos en la nota.
private fun LazyListScope.highlightedParagraphsSection(
    highlights: Set<Int>,
    documentText: List<String>,
) {
    if (highlights.isEmpty() || documentText.isEmpty()) return
    item {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Bookmarks,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = stringResource(R.string.study_highlighted_paragraphs_count, highlights.size),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
    itemsIndexed(highlights.sorted()) { _, index ->
        if (index < documentText.size) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = WarningAmber.copy(alpha = 0.1f)),
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Rounded.Bookmark, null, tint = WarningAmber, modifier = Modifier.size(16.dp))
                    Text(
                        text = documentText[index],
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                    )
                }
            }
        }
    }
    item { HorizontalDivider(modifier = Modifier.padding(top = 8.dp)) }
}

// Extraída de NotesTab() (detekt: LongMethod) -- contador + exportar todas/
// eliminar todas.
@Composable
private fun NotesListHeader(
    notes: List<NoteWithImages>,
    onDeleteAllClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.Notes,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text =
                    if (notes.isEmpty()) {
                        stringResource(R.string.study_no_saved_notes)
                    } else {
                        stringResource(R.string.study_saved_notes_count, notes.size)
                    },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (notes.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StudyExportNotesButton(notes = notes)
                TextButton(onClick = onDeleteAllClick) {
                    Text(
                        text = stringResource(R.string.study_delete_all),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

// Extraída de NotesTab() (detekt: LongMethod) -- estado vacío con el componente común.
@Composable
private fun NotesEmptyState() {
    DocuSmartEmptyState(
        icon = Icons.Rounded.NoteAlt,
        title = stringResource(R.string.study_no_notes_yet),
    )
}

// Extraída de NotesTab() (detekt: LongMethod) -- tarjeta del editor de
// nota nueva (título + contenido + dictado por voz + guardar). El fallback
// "Sin título" se resuelve acá (necesita stringResource, que solo puede
// llamarse en un @Composable) antes de invocar onSave().
@Composable
private fun NoteEditorCard(
    currentTitle: String,
    onTitleChange: (String) -> Unit,
    currentNote: String,
    onNoteChange: (String) -> Unit,
    isListening: Boolean,
    onVoiceClick: () -> Unit,
    imageUris: List<Uri>,
    onGalleryClick: () -> Unit,
    onScanClick: () -> Unit,
    onRemoveImage: (Uri) -> Unit,
    reminderChip: NoteReminderChip,
    reminderAt: Long?,
    onReminderChange: (NoteReminderChip, Long?) -> Unit,
    notificationsGranted: Boolean,
    onRequestNotifications: () -> Unit,
    onSave: (title: String, text: String) -> Unit,
) {
    val shape = MaterialTheme.shapes.large
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .accentShadow(shape = shape, elevation = 1.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface)
                .accentBorder(shape = shape),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.EditNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = stringResource(R.string.study_new_note),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            // Campo título
            OutlinedTextField(
                value = currentTitle,
                onValueChange = onTitleChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.study_note_title_label)) },
                placeholder = { Text(stringResource(R.string.study_note_title_placeholder)) },
                singleLine = true,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Title,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                },
                shape = MaterialTheme.shapes.large,
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    ),
            )

            // Campo contenido + botón micrófono
            OutlinedTextField(
                value = currentNote,
                onValueChange = onNoteChange,
                modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp, max = 140.dp),
                placeholder = { Text(stringResource(R.string.study_note_content_placeholder)) },
                trailingIcon = {
                    // Subido de 40dp a 48dp (auditoría de testers 2026-09-12, "botones pequeños").
                    IconButton(onClick = onVoiceClick, modifier = Modifier.size(48.dp)) {
                        Icon(
                            imageVector = if (isListening) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                            contentDescription = stringResource(R.string.study_dictate_note),
                            tint =
                                if (isListening) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                            modifier = Modifier.size(22.dp),
                        )
                    }
                },
                shape = MaterialTheme.shapes.large,
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedBorderColor =
                            if (isListening) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    ),
            )

            // Indicador de escucha activa
            if (isListening) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.GraphicEq,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = stringResource(R.string.study_listening),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            // Backlog UX #49: adjuntar imagen (galería) o recorte escaneado
            // (mismo escáner de ML Kit que Convertir/Escáner) a la nota que
            // se está escribiendo.
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onGalleryClick) {
                    Icon(
                        imageVector = Icons.Rounded.Image,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.study_note_attach_image), style = MaterialTheme.typography.labelMedium)
                }
                TextButton(onClick = onScanClick) {
                    Icon(
                        imageVector = Icons.Rounded.DocumentScanner,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.study_note_scan_image), style = MaterialTheme.typography.labelMedium)
                }
            }
            if (imageUris.isNotEmpty()) {
                NoteImagesCarousel(uris = imageUris, onRemove = onRemoveImage)
            }

            // Backlog UX #52: "Recordarme repasar esto".
            NoteReminderSection(
                reminderChip = reminderChip,
                reminderAt = reminderAt,
                onReminderChange = onReminderChange,
                notificationsGranted = notificationsGranted,
                onRequestNotifications = onRequestNotifications,
            )

            val untitledNoteLabel = stringResource(R.string.study_untitled_note)

            // Botón guardar
            Button(
                onClick = {
                    val text = currentNote.trim()
                    if (text.isNotBlank()) onSave(currentTitle.trim().ifBlank { untitledNoteLabel }, text)
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = MaterialTheme.shapes.medium,
                enabled = currentNote.trim().isNotBlank(),
            ) {
                Icon(Icons.Rounded.Save, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.study_save_note), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

// Backlog UX #49: miniaturas de las imágenes elegidas para la nota que
// todavía no se guardó -- mismo patrón que
// ConverterScreen.SelectedImagesCarousel (URIs temporales del selector/
// escáner, no archivos ya copiados a filesDir todavía).
@Composable
private fun NoteImagesCarousel(
    uris: List<Uri>,
    onRemove: (Uri) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(uris, key = { it.toString() }) { uri ->
            Box(modifier = Modifier.size(72.dp)) {
                AsyncImage(
                    model = uri,
                    contentDescription = stringResource(R.string.study_note_image_desc),
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .clip(MaterialTheme.shapes.medium),
                )
                IconButton(
                    onClick = { onRemove(uri) },
                    modifier = Modifier.size(36.dp).align(Alignment.TopEnd),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(20.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    shape = MaterialTheme.shapes.extraSmall,
                                ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.study_note_remove_image),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }
            }
        }
    }
}

// Backlog UX #52: cuál de los presets de recordatorio está elegido (o
// ninguno/personalizado) -- separado de `reminderAt` (la fecha/hora real en
// millis) solo para saber qué FilterChip resaltar, ambos viven juntos en
// NotesTab y se resetean a la vez al guardar la nota.
internal enum class NoteReminderChip { NONE, TOMORROW, DAYS_3, WEEK_1, CUSTOM }

// Ronda 17: noteReminderPresetMillis() vive ahora en StudyScreenLogic.kt.

// Backlog UX #52: "Recordarme repasar esto" -- presets absolutos (mañana/
// 3 días/1 semana, siempre a las 9:00, hora de estudio típica) o fecha/hora
// personalizada. RNF2: si el usuario negó POST_NOTIFICATIONS, la sección
// queda deshabilitada con una explicación en vez de dejar programar una
// alarma cuya notificación nunca va a poder mostrarse.
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun NoteReminderSection(
    reminderChip: NoteReminderChip,
    reminderAt: Long?,
    onReminderChange: (NoteReminderChip, Long?) -> Unit,
    notificationsGranted: Boolean,
    onRequestNotifications: () -> Unit,
) {
    var showCustomPicker by remember { mutableStateOf(false) }
    val reminderLocale = LocalConfiguration.current.locales[0]
    val reminderIs24Hour = android.text.format.DateFormat.is24HourFormat(LocalContext.current)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.NotificationsActive,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = stringResource(R.string.study_note_reminder_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (!notificationsGranted) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.study_note_reminder_notifications_disabled),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onRequestNotifications) {
                    Text(stringResource(R.string.study_note_reminder_enable_notifications))
                }
            }
            return
        }

        // Rediseño 2026-09-20: eran 5 chips en una fila desplazable y los dos últimos
        // ("En 1 semana", "Personalizado") quedaban fuera de pantalla sin ninguna pista
        // de que se podía deslizar. FlowRow los ajusta al ancho en dos líneas.
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            FilterChip(
                selected = reminderChip == NoteReminderChip.NONE,
                onClick = { onReminderChange(NoteReminderChip.NONE, null) },
                label = { Text(stringResource(R.string.agenda_reminder_none), maxLines = 1) },
            )
            FilterChip(
                selected = reminderChip == NoteReminderChip.TOMORROW,
                onClick = { onReminderChange(NoteReminderChip.TOMORROW, noteReminderPresetMillis(1)) },
                label = { Text(stringResource(R.string.study_note_reminder_tomorrow), maxLines = 1) },
            )
            FilterChip(
                selected = reminderChip == NoteReminderChip.DAYS_3,
                onClick = { onReminderChange(NoteReminderChip.DAYS_3, noteReminderPresetMillis(3)) },
                label = { Text(stringResource(R.string.study_note_reminder_3_days), maxLines = 1) },
            )
            FilterChip(
                selected = reminderChip == NoteReminderChip.WEEK_1,
                onClick = { onReminderChange(NoteReminderChip.WEEK_1, noteReminderPresetMillis(7)) },
                label = { Text(stringResource(R.string.study_note_reminder_1_week), maxLines = 1) },
            )
            FilterChip(
                selected = reminderChip == NoteReminderChip.CUSTOM,
                onClick = { showCustomPicker = true },
                label = { Text(stringResource(R.string.study_note_reminder_custom), maxLines = 1) },
            )
        }

        if (reminderChip != NoteReminderChip.NONE && reminderAt != null) {
            Text(
                text =
                    stringResource(
                        R.string.study_note_reminder_scheduled_desc,
                        formatAgendaDateTime(reminderAt, reminderLocale, reminderIs24Hour),
                    ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            // Hallazgo real de la auditoría general 2026-09-17: mismo
            // problema que en Agenda -- NoteReminderScheduler.schedule()
            // descarta en silencio una fecha ya pasada, alcanzable acá si
            // se elige "Personalizada" y no se cambia la fecha/hora
            // precargada (o se elige una anterior a "ahora" por error).
            if (isReminderInPast(reminderAt, System.currentTimeMillis())) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.WarningAmber,
                        contentDescription = null,
                        tint = WarningAmber,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = stringResource(R.string.study_note_reminder_already_past),
                        style = MaterialTheme.typography.labelSmall,
                        color = WarningAmber,
                    )
                }
            }
        }
    }

    if (showCustomPicker) {
        NoteReminderDateTimeDialog(
            initialMillis =
                reminderAt?.takeIf { reminderChip == NoteReminderChip.CUSTOM }
                    ?: System.currentTimeMillis(),
            onConfirm = { millis ->
                onReminderChange(NoteReminderChip.CUSTOM, millis)
                showCustomPicker = false
            },
            onDismiss = { showCustomPicker = false },
        )
    }
}

// Backlog UX #52: mismo patrón Material3 DatePicker/TimePicker que
// AgendaEventEditorDialog.AgendaDateTimeRow (HU-65)/QrDateTimeRow (HU-43),
// envuelto acá en un Dialog propio con Aceptar/Cancelar en vez de una fila
// persistente -- la nota no tiene un editor ya abierto sobre el que anclar
// una fila fija.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteReminderDateTimeDialog(
    initialMillis: Long,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember {
        mutableStateOf(
            Instant.ofEpochMilli(initialMillis).atZone(ZoneId.systemDefault()).toLocalDateTime(),
        )
    }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    val locale = LocalConfiguration.current.locales[0]
    val is24Hour = android.text.format.DateFormat.is24HourFormat(LocalContext.current)
    val dateFormat = remember(locale) { agendaDateFormatter(locale) }
    val timeFormat = remember(locale, is24Hour) { agendaTimeFormatter(locale, is24Hour) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = stringResource(R.string.study_note_reminder_custom),
                    style = MaterialTheme.typography.titleMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.CalendarMonth, null, modifier = Modifier.size(16.dp))
                        Text(value.format(dateFormat), modifier = Modifier.padding(start = 6.dp))
                    }
                    OutlinedButton(onClick = { showTimePicker = true }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Schedule, null, modifier = Modifier.size(16.dp))
                        Text(value.format(timeFormat), modifier = Modifier.padding(start = 6.dp))
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.general_cancel)) }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = {
                        onConfirm(value.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
                    }) { Text(stringResource(R.string.general_accept)) }
                }
            }
        }
    }

    if (showDatePicker) {
        val initial = value.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val state = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        val newDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        value = LocalDateTime.of(newDate, value.toLocalTime())
                    }
                    showDatePicker = false
                }) { Text(stringResource(R.string.general_accept)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.general_cancel)) }
            },
        ) { DatePicker(state = state) }
    }

    if (showTimePicker) {
        val state = rememberTimePickerState(initialHour = value.hour, initialMinute = value.minute, is24Hour = is24Hour)
        Dialog(onDismissRequest = { showTimePicker = false }) {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
                Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    TimePicker(state = state)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = { showTimePicker = false }) {
                            Text(stringResource(R.string.general_cancel))
                        }
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = {
                            value = value.withHour(state.hour).withMinute(state.minute)
                            showTimePicker = false
                        }) { Text(stringResource(R.string.general_accept)) }
                    }
                }
            }
        }
    }
}

// Extraída de NotesTab() (detekt: LongMethod, disparado al sumar los
// botones de vincular/exportar de #50/#51) -- una tarjeta de nota guardada.
@Composable
private fun NoteListItem(
    noteWithImages: NoteWithImages,
    dateFormatter: java.text.DateFormat,
    // Backlog UX #52, AC2: true si esta es la nota a la que apuntaba la
    // notificación de recordatorio recién tocada -- fondo tintado para que
    // sea fácil de encontrar en la lista tras el scroll automático.
    isHighlighted: Boolean = false,
    onLinkClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    val note = noteWithImages.note
    val shape = MaterialTheme.shapes.large
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 5.dp)
                .accentShadow(shape = shape, elevation = 2.dp)
                .clip(shape)
                .background(
                    if (isHighlighted) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                )
                .accentBorder(shape = shape),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Cabecera: título + acciones
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.NoteAlt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = note.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
                // Backlog UX #50: vincular a un documento de la Biblioteca
                // -- ícono resaltado en acento cuando ya tiene un vínculo.
                IconButton(onClick = onLinkClick, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector =
                            if (note.documentId != null) {
                                Icons.Rounded.Link
                            } else {
                                Icons.Rounded.LinkOff
                            },
                        contentDescription =
                            stringResource(
                                if (note.documentId != null) {
                                    R.string.note_linked_document_desc
                                } else {
                                    R.string.note_link_document_title
                                },
                            ),
                        tint =
                            if (note.documentId != null) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        modifier = Modifier.size(16.dp),
                    )
                }
                // Backlog UX #51: exportar esta nota sola a PDF/Word --
                // distinto del botón de la cabecera de la lista, que
                // exporta TODAS juntas.
                StudyExportSingleNoteButton(note = noteWithImages)
                // B22 (auditoría general 2026-09-17): editar título/texto/
                // imágenes/recordatorio de una nota ya guardada -- antes no
                // existía, asimetría frente a Agenda (que sí permite editar
                // un evento).
                IconButton(onClick = onEditClick, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = stringResource(R.string.study_edit_note_desc),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                }
                // Subido de 28dp a 48dp (auditoría de testers 2026-09-12, "botones pequeños").
                IconButton(onClick = onDeleteClick, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = stringResource(R.string.study_delete_note_desc),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            // Fecha
            Text(
                text = dateFormatter.format(java.util.Date(note.createdAt)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            // Contenido
            Text(
                text = note.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 22.sp,
            )

            // Backlog UX #49: imágenes/recortes ya adjuntos a la nota
            // guardada -- de solo lectura acá (agregar/quitar imágenes se
            // hace desde "Editar", ver NoteEditDialog). Tocar una miniatura
            // la abre en grande.
            if (noteWithImages.images.isNotEmpty()) {
                var expandedImagePath by remember { mutableStateOf<String?>(null) }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(noteWithImages.images, key = { it.id }) { image ->
                        AsyncImage(
                            model = File(image.filePath),
                            contentDescription = stringResource(R.string.study_note_image_desc),
                            contentScale = ContentScale.Crop,
                            modifier =
                                Modifier
                                    .size(64.dp)
                                    .clip(MaterialTheme.shapes.medium)
                                    .clickable { expandedImagePath = image.filePath },
                        )
                    }
                }
                expandedImagePath?.let { path ->
                    Dialog(onDismissRequest = { expandedImagePath = null }) {
                        AsyncImage(
                            model = File(path),
                            contentDescription = stringResource(R.string.study_note_image_desc),
                            contentScale = ContentScale.Fit,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.large)
                                    .clickable { expandedImagePath = null },
                        )
                    }
                }
            }
        }
    }
}

// B22 (auditoría general 2026-09-17): editor de una nota ya guardada
// (título/texto/imágenes/recordatorio) -- antes solo existía el editor de
// nota NUEVA (NoteEditorCard). Se implementa como un Dialog aparte (no se
// reutiliza NoteEditorCard, que solo trabaja con List<Uri> del selector/
// escáner) para no tocar el flujo de creación ya establecido y probado.
// Cada imagen ya guardada se expone como su propia `Uri.fromFile(...)`
// (Coil la carga igual que cualquier otro Uri) para poder reutilizar
// NoteImagesCarousel/NoteReminderSection tal cual, sin duplicar esa UI.
@Composable
internal fun NoteEditDialog(
    noteWithImages: NoteWithImages,
    notificationsGranted: Boolean,
    onRequestNotifications: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (
        title: String,
        text: String,
        reminderAt: Long?,
        keptImages: List<NoteImageEntity>,
        removedImages: List<NoteImageEntity>,
        newImageUris: List<Uri>,
    ) -> Unit,
) {
    val note = noteWithImages.note
    var title by remember(note.id) { mutableStateOf(note.title) }
    var text by remember(note.id) { mutableStateOf(note.text) }
    val originalImagesByUri =
        remember(note.id) {
            noteWithImages.images.associateBy { Uri.fromFile(File(it.filePath)) }
        }
    var imageUris by remember(note.id) { mutableStateOf(originalImagesByUri.keys.toList()) }
    // Cualquier reminderAt existente se muestra como "Personalizada" -- no
    // hay forma de saber si el valor guardado vino de un preset (mañana/
    // 3 días/1 semana) o de una fecha elegida a mano, y los presets se
    // recalculan relativos a "ahora" cada vez.
    var reminderChip by remember(note.id) {
        mutableStateOf(if (note.reminderAt != null) NoteReminderChip.CUSTOM else NoteReminderChip.NONE)
    }
    var reminderAt by remember(note.id) { mutableStateOf(note.reminderAt) }

    val context = LocalContext.current
    val galleryLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.GetMultipleContents(),
        ) { uris -> if (uris.isNotEmpty()) imageUris = imageUris + uris }
    val onScanImage =
        rememberDocumentScannerAction(
            activity = context.findActivity(),
            mode = ScannerMode.PHOTO,
            pageLimit = 1,
            onPagesScanned = { pages -> imageUris = imageUris + pages },
            onScanError = { message -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show() },
        )
    val untitledNoteLabel = stringResource(R.string.study_untitled_note)

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
            Column(
                modifier =
                    Modifier
                        .padding(20.dp)
                        .heightIn(max = 560.dp)
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.EditNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = stringResource(R.string.study_edit_note),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.study_note_title_label)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.large,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp, max = 140.dp),
                    placeholder = { Text(stringResource(R.string.study_note_content_placeholder)) },
                    shape = MaterialTheme.shapes.large,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { galleryLauncher.launch("image/*") }) {
                        Icon(Icons.Rounded.Image, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.study_note_attach_image),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    TextButton(onClick = onScanImage) {
                        Icon(Icons.Rounded.DocumentScanner, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.study_note_scan_image),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
                if (imageUris.isNotEmpty()) {
                    NoteImagesCarousel(uris = imageUris, onRemove = { uri -> imageUris = imageUris - uri })
                }

                NoteReminderSection(
                    reminderChip = reminderChip,
                    reminderAt = reminderAt,
                    onReminderChange = { chip, millis ->
                        reminderChip = chip
                        reminderAt = millis
                    },
                    notificationsGranted = notificationsGranted,
                    onRequestNotifications = onRequestNotifications,
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.general_cancel)) }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        enabled = text.trim().isNotBlank(),
                        onClick = {
                            val trimmedText = text.trim()
                            if (trimmedText.isBlank()) return@Button
                            val diff = diffNoteImages(imageUris, originalImagesByUri)
                            onSave(
                                title.trim().ifBlank { untitledNoteLabel },
                                trimmedText,
                                reminderAt,
                                diff.keptImages,
                                diff.removedImages,
                                diff.newImageUris,
                            )
                        },
                    ) { Text(stringResource(R.string.general_save)) }
                }
            }
        }
    }
}

// ── RF-STU-08/backlog UX #51: exportar/compartir notas ───────────────
private enum class StudyExportFormat { TEXT, PDF, WORD }

@Composable
private fun StudyExportNotesButton(notes: List<NoteWithImages>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }
    // Hallazgo real de la auditoría general 2026-09-17/18 (décima ronda,
    // Alta -- N1): shareStudyNotes() corría síncrono en el hilo principal
    // (I/O + decodificación de imágenes con iText/POI) sin ningún guard --
    // riesgo real de ANR con varias notas con imágenes, y sin guard un
    // doble-toque podía lanzar 2 exportaciones seguidas (nombre de
    // archivo con timestamp a nivel de segundo, pueden colisionar). Mismo
    // patrón ya usado en ViewerViewModel.shareWithAnnotations()
    // (corrutina + flag de carga).
    var isExporting by remember { mutableStateOf(false) }
    val shareTitle = stringResource(R.string.study_export_share_title)

    fun exportAs(format: StudyExportFormat) {
        if (isExporting) return
        isExporting = true
        scope.launch {
            shareStudyNotes(context, notes, format, shareTitle)
            isExporting = false
        }
    }

    Box {
        IconButton(onClick = { expanded = true }, enabled = !isExporting) {
            if (isExporting) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Icon(
                    imageVector = Icons.Rounded.IosShare,
                    contentDescription = stringResource(R.string.study_export_notes),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.study_export_as_text)) },
                onClick = {
                    expanded = false
                    exportAs(StudyExportFormat.TEXT)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.study_export_as_pdf)) },
                onClick = {
                    expanded = false
                    exportAs(StudyExportFormat.PDF)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.note_export_as_word)) },
                onClick = {
                    expanded = false
                    exportAs(StudyExportFormat.WORD)
                },
            )
        }
    }
}

// Backlog UX #51, RF1: exportar UNA nota sola (distinto del botón de
// arriba, que exporta todas juntas) -- solo PDF/Word, según lo pedido
// (RF1: "con opción PDF o Word").
@Composable
private fun StudyExportSingleNoteButton(note: NoteWithImages) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }
    // Ver el mismo hallazgo (N1) en StudyExportNotesButton más arriba.
    var isExporting by remember { mutableStateOf(false) }
    val shareTitle = stringResource(R.string.study_export_share_title)

    fun exportAs(format: StudyExportFormat) {
        if (isExporting) return
        isExporting = true
        scope.launch {
            shareStudyNotes(context, listOf(note), format, shareTitle)
            isExporting = false
        }
    }

    Box {
        IconButton(onClick = { expanded = true }, enabled = !isExporting, modifier = Modifier.size(48.dp)) {
            if (isExporting) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
                Icon(
                    imageVector = Icons.Rounded.IosShare,
                    contentDescription = stringResource(R.string.note_export_note_desc),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.study_export_as_pdf)) },
                onClick = {
                    expanded = false
                    exportAs(StudyExportFormat.PDF)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.note_export_as_word)) },
                onClick = {
                    expanded = false
                    exportAs(StudyExportFormat.WORD)
                },
            )
        }
    }
}

// Hallazgo real de la auditoría general 2026-09-17/18 (décima ronda, Alta
// -- N1): antes corría entero en el hilo principal -- StudyNotesExporter
// hace I/O + decodificación de imágenes con iText/POI, igual de pesado
// que ViewerViewModel.shareWithAnnotations() (que sí usa
// viewModelScope.launch). Ahora la generación del archivo corre en
// Dispatchers.IO; solo el Intent final (rápido) vuelve al hilo que llamó.
@Suppress("TooGenericExceptionCaught")
private suspend fun shareStudyNotes(
    context: Context,
    notes: List<NoteWithImages>,
    format: StudyExportFormat,
    shareTitle: String,
) {
    try {
        val (file, mimeType) =
            withContext(Dispatchers.IO) {
                when (format) {
                    StudyExportFormat.TEXT -> StudyNotesExporter.exportAsTextFile(context, notes) to "text/plain"
                    StudyExportFormat.PDF -> StudyNotesExporter.exportAsPdfFile(context, notes) to "application/pdf"
                    StudyExportFormat.WORD ->
                        StudyNotesExporter.exportAsWordFile(context, notes) to
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                }
            }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent =
            android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        context.startActivity(android.content.Intent.createChooser(intent, shareTitle))
    } catch (e: CancellationException) {
        // Hallazgo real de la revisión adversarial de esta misma ronda
        // (Media): si el usuario navega fuera de la pantalla mientras se
        // genera el archivo (rememberCoroutineScope() cancela la
        // corrutina), el catch genérico de abajo tragaba la
        // CancellationException como si fuera un error más -- rompe la
        // cancelación estructurada (esta corrutina "cancelada" seguía
        // corriendo hasta el final en vez de detenerse de verdad). Mismo
        // patrón ya aplicado hoy en OcrPdfUseCase/CompressPdfUseCase:
        // relanzarla siempre, nunca tragarla.
        throw e
    } catch (e: Exception) {
        Timber.e("Error exportando notas de estudio (${e.javaClass.simpleName})")
    }
}

// ── RF-STU-09: estadísticas de estudio ────────────────
@Composable
internal fun StudyStatsDialog(
    stats: StudyStats,
    onDismiss: () -> Unit,
) {
    val now = remember { System.currentTimeMillis() }
    val (hours, minutes) = remember(stats) { millisToHoursAndMinutes(stats.totalReadingMillis) }
    val weekdayCounts = remember(stats) { pomodoroCountsByWeekday(stats.pomodoroTimestamps, now) }
    val weekdayLabels =
        remember {
            val formatter = java.text.SimpleDateFormat("EEEEE", Locale.getDefault())
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
            (0 until 7).map { offset ->
                val label = formatter.format(calendar.time).uppercase(Locale.getDefault())
                calendar.add(Calendar.DAY_OF_YEAR, 1)
                label
            }
        }
    val hasAnyStats = stats.totalReadingMillis > 0 || stats.pomodoroTimestamps.isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        title = { Text(stringResource(R.string.study_stats_title)) },
        text = {
            if (!hasAnyStats) {
                Text(stringResource(R.string.study_stats_empty))
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    StudyStatRow(
                        icon = Icons.Rounded.VolumeUp,
                        label = stringResource(R.string.study_stats_total_reading),
                        value = stringResource(R.string.study_stats_reading_value, hours, minutes),
                    )
                    StudyStatRow(
                        icon = Icons.Rounded.EmojiEvents,
                        label = stringResource(R.string.study_stats_pomodoros_total),
                        value = "${stats.pomodoroTimestamps.size}",
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text =
                                stringResource(R.string.study_stats_pomodoros_this_week) +
                                    " (${weekdayCounts.sum()})",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        StudyWeekBars(labels = weekdayLabels, counts = weekdayCounts)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.general_close)) }
        },
    )
}

// Selector de voz para "Lectura en voz alta" (pedido explícito de testers
// 2026-09-12): lista las voces on-device disponibles para el idioma actual
// -- nunca las que requieren red, ver el filtro en la inicialización del
// TTS más arriba (100% local y gratis, sin depender de ningún servicio en
// la nube).
//
// HU-64 (feedback real de testers de la prueba cerrada, 2026-09-16): la
// versión anterior mostraba "Voz 1 — Español (Muy alta)" y aplicaba la
// voz apenas se tocaba el radio button, sin forma de escuchar cómo suena
// antes de elegir. Ahora cada fila tiene un avatar de personaje + nombre
// (ver VoicePersona.kt) y un botón para escuchar una muestra sin cambiar
// todavía la voz de lectura.
@Composable
internal fun VoiceSelectorDialog(
    voices: List<Voice>,
    selectedVoice: Voice?,
    previewingVoiceName: String?,
    onVoiceSelected: (Voice) -> Unit,
    onPreviewVoice: (Voice) -> Unit,
    onDismiss: () -> Unit,
    // Pedido explícito del usuario 2026-09-22: un personaje distinto por voz --
    // `personasForVoices(voices)` (calculado por quien llama, para no repetirlo
    // acá en cada recomposición). Con `null` (compatibilidad hacia atrás, o
    // pruebas que no lo necesitan) cae en `personaForVoice` sola, la de antes.
    personas: Map<String, VoicePersona>? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.study_choose_voice)) },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(voices, key = { it.name }) { voice ->
                    val persona =
                        remember(voice.name, personas) {
                            personas?.get(voice.name) ?: personaForVoice(voice.name)
                        }
                    val isSelected = voice.name == selectedVoice?.name
                    val isPreviewing = voice.name == previewingVoiceName
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.small)
                                .clickable { onVoiceSelected(voice) }
                                .padding(vertical = 8.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Image(
                            painter = painterResource(persona.avatarDrawableRes),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier =
                                Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .border(1.5.dp, persona.avatarColor, CircleShape),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = persona.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            )
                            Text(
                                text =
                                    stringResource(
                                        R.string.study_voice_option_detail,
                                        voice.locale.displayName,
                                        voiceQualityLabel(voice.quality),
                                    ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        IconButton(
                            onClick = { onPreviewVoice(voice) },
                            enabled = !isPreviewing,
                        ) {
                            Icon(
                                imageVector = if (isPreviewing) Icons.Rounded.VolumeUp else Icons.Rounded.PlayArrow,
                                contentDescription =
                                    stringResource(
                                        if (isPreviewing) {
                                            R.string.study_voice_preview_playing
                                        } else {
                                            R.string.study_voice_preview
                                        },
                                    ),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        RadioButton(selected = isSelected, onClick = { onVoiceSelected(voice) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.general_close)) }
        },
    )
}

@Composable
private fun voiceQualityLabel(quality: Int): String =
    when (quality) {
        Voice.QUALITY_VERY_HIGH -> stringResource(R.string.study_voice_quality_very_high)
        Voice.QUALITY_HIGH -> stringResource(R.string.study_voice_quality_high)
        Voice.QUALITY_NORMAL -> stringResource(R.string.study_voice_quality_normal)
        else -> stringResource(R.string.study_voice_quality_low)
    }

@Composable
private fun StudyStatRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = WarningAmber, modifier = Modifier.size(22.dp))
        Text(text = label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun StudyWeekBars(
    labels: List<String>,
    counts: IntArray,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        labels.forEachIndexed { index, label ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(text = "${counts[index]}", style = MaterialTheme.typography.labelSmall)
                Box(
                    modifier =
                        Modifier
                            .width(18.dp)
                            .height(weekBarHeightDp(counts[index], counts).dp)
                            // Bug real corregido 2026-09-04 (backlog UX §7,
                            // HU-UX-06): fijo en azul, ignorando el "Color de
                            // acento" elegido en Ajustes.
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)),
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ── Tab Pomodoro ──────────────────────────────────────
@Composable
internal fun PomodoroTab(
    minutes: Int,
    seconds: Int,
    isRunning: Boolean,
    isBreak: Boolean,
    pomodoroCount: Int,
    onToggle: () -> Unit,
    onReset: () -> Unit,
    onShowStats: () -> Unit,
) {
    // Bug real corregido 2026-09-08: mismo problema que se encontró y
    // corrigió en Notas -- este `Column` no tenía scroll, así que en
    // pantallas más chicas "Pomodoros completados" y la tarjeta de info
    // quedaban cortados fuera de la pantalla, sin forma de verlos.
    //
    // Feedback real de testers 2026-09-12: "no se entiende para qué sirve
    // Pomodoro" -- la explicación y el contador ya existían, pero quedaban
    // al final de la pantalla, después del reloj y los controles, y el
    // contador se reiniciaba a 0 en cada apertura de la app (contaba solo
    // la sesión en memoria de `PomodoroEngine`, no el historial real). Se
    // sube la explicación al principio, antes que nada más, y el contador
    // pasa a mostrar el total persistido (`StudyStatsStorage`, últimos 90
    // días) en vez del contador de la sesión actual.
    val context = LocalContext.current
    val lifetimePomodoros =
        remember(pomodoroCount) {
            StudyStatsStorage.loadStats(context).pomodoroTimestamps.size
        }
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Spacer(Modifier.height(8.dp))

        PomodoroInfoCard()
        PomodoroTypeIndicator(isBreak)
        PomodoroClock(minutes, seconds, isRunning, isBreak)
        PomodoroControls(isRunning, isBreak, onToggle, onReset)
        PomodoroCountCard(lifetimePomodoros)
        // Estadísticas de estudio (antes un ícono suelto en el encabezado).
        OutlinedButton(
            onClick = onShowStats,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Rounded.QueryStats, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.study_stats_icon_desc), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun PomodoroTypeIndicator(isBreak: Boolean) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color =
            if (isBreak) {
                SuccessGreen.copy(alpha = 0.15f)
            } else {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            },
    ) {
        Text(
            text = if (isBreak) stringResource(R.string.study_break_label) else stringResource(R.string.study_study_label),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (isBreak) SuccessGreen else MaterialTheme.colorScheme.primary,
            modifier =
                Modifier.padding(
                    horizontal = 20.dp,
                    vertical = 10.dp,
                ),
        )
    }
}

@Composable
private fun PomodoroClock(
    minutes: Int,
    seconds: Int,
    isRunning: Boolean,
    isBreak: Boolean,
) {
    Box(
        modifier =
            Modifier
                .size(200.dp)
                .background(
                    brush =
                        Brush.radialGradient(
                            colors =
                                if (isBreak) {
                                    listOf(SuccessGreen.copy(0.2f), Color.Transparent)
                                } else {
                                    listOf(MaterialTheme.colorScheme.primary.copy(0.2f), Color.Transparent)
                                },
                        ),
                    shape = RoundedCornerShape(100.dp),
                ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                // Solo dígitos 0-9 (sin sensibilidad real de locale) — se evita
                // String.format(Locale.getDefault(), ...) porque llamarlo dentro de
                // un @Composable no es observable ante un cambio de idioma en runtime
                // (lint: NonObservableLocale).
                text = formatPomodoroClock(minutes, seconds),
                fontSize = 52.sp,
                fontWeight = FontWeight.Bold,
                color = if (isBreak) SuccessGreen else MaterialTheme.colorScheme.primary,
            )
            Text(
                text = if (isRunning) stringResource(R.string.study_in_progress) else stringResource(R.string.study_paused),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PomodoroControls(
    isRunning: Boolean,
    isBreak: Boolean,
    onToggle: () -> Unit,
    onReset: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedButton(
            onClick = onReset,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.height(52.dp),
        ) {
            Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.study_restart))
        }

        Button(
            onClick = onToggle,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.height(52.dp),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = if (isBreak) SuccessGreen else MaterialTheme.colorScheme.primary,
                ),
        ) {
            Icon(
                imageVector =
                    if (isRunning) {
                        Icons.Rounded.Pause
                    } else {
                        Icons.Rounded.PlayArrow
                    },
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (isRunning) stringResource(R.string.study_pause) else stringResource(R.string.study_start),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun PomodoroCountCard(pomodoroCount: Int) {
    val shape = MaterialTheme.shapes.large
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .accentShadow(shape = shape)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface)
                .accentBorder(shape = shape),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.EmojiEvents,
                contentDescription = null,
                tint = WarningAmber,
                modifier = Modifier.size(24.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.study_pomodoros_completed),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.study_pomodoros_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "$pomodoroCount",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = WarningAmber,
            )
        }
    }
}

@Composable
private fun PomodoroInfoCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors =
            CardDefaults.cardColors(
                containerColor =
                    MaterialTheme.colorScheme.primaryContainer
                        .copy(alpha = 0.3f),
            ),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = stringResource(R.string.study_pomodoro_technique_info),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ── Tab Resumen ───────────────────────────────────────
// Nuevo 2026-09-08, a pedido explícito del usuario, con una condición suya:
// 100% local (sin IA en la nube) para no romper la promesa de la política
// de privacidad ("los documentos nunca salen del dispositivo") ni sumar
// costo por uso -- ver `TextSummarizer.kt` para el detalle del algoritmo
// extractivo. Reusa el mismo `documentText` que ya carga Lectura -- no pide
// el PDF de nuevo.
@Composable
private fun SummaryTab(
    documentText: List<String>,
    documentName: String,
    hasDocument: Boolean,
    summarySentences: List<String>?,
    isSummarizing: Boolean,
    onGenerate: () -> Unit,
    onSelectDoc: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var savedToDownloads by remember(summarySentences) { mutableStateOf(false) }
    val shareTitle = stringResource(R.string.study_summary_share_title)

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            !hasDocument -> {
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(80.dp)
                                .background(
                                    brush = Brush.linearGradient(rememberAccentGradient()),
                                    shape = MaterialTheme.shapes.extraLarge,
                                ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Summarize,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(40.dp),
                        )
                    }
                    Text(
                        text = stringResource(R.string.study_tab_summary),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.study_empty_state_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Button(onClick = onSelectDoc, shape = MaterialTheme.shapes.medium) {
                        Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.qr_open_document))
                    }
                }
            }
            isSummarizing ->
                LoadingIndicator(
                    stringResource(R.string.study_summarizing),
                    modifier = Modifier.align(Alignment.Center),
                )
            summarySentences == null -> {
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // Misma insignia circular con degradado de acento que el
                    // estado "sin documento" de arriba -- antes este estado
                    // usaba un ícono plano más chico, sin relación visual con
                    // el resto de la pantalla.
                    Box(
                        modifier =
                            Modifier
                                .size(80.dp)
                                .background(
                                    brush = Brush.linearGradient(rememberAccentGradient()),
                                    shape = MaterialTheme.shapes.extraLarge,
                                ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Summarize,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(40.dp),
                        )
                    }
                    Text(
                        text = stringResource(R.string.study_summary_local_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Button(
                        onClick = onGenerate,
                        shape = MaterialTheme.shapes.medium,
                        enabled = documentText.isNotEmpty(),
                    ) {
                        Icon(Icons.Rounded.Summarize, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.study_generate_summary))
                    }
                }
            }
            else ->
                SummaryResultView(
                    sentences = summarySentences,
                    savedToDownloads = savedToDownloads,
                    onSave = {
                        scope.launch {
                            savedToDownloads = saveSummaryToDownloads(context, documentName, summarySentences)
                        }
                    },
                    onShare = { shareSummary(context, documentName, summarySentences, shareTitle) },
                )
        }
    }
}

// Extraído de SummaryTab (LongMethod por detekt) -- la lista de oraciones
// generadas + la fila de guardar/compartir, una vez que ya hay resumen.
@Composable
private fun SummaryResultView(
    sentences: List<String>,
    savedToDownloads: Boolean,
    onSave: () -> Unit,
    onShare: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Summarize,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(R.string.study_summary_sentences_count, sentences.size),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            IconButton(onClick = onSave) {
                Icon(
                    imageVector = if (savedToDownloads) Icons.Rounded.CheckCircle else Icons.Rounded.Download,
                    contentDescription = stringResource(R.string.general_save),
                    tint = if (savedToDownloads) SuccessGreen else MaterialTheme.colorScheme.primary,
                )
            }
            IconButton(onClick = onShare) {
                Icon(
                    imageVector = Icons.Rounded.Share,
                    contentDescription = stringResource(R.string.general_share),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Cada punto clave en su propia tarjeta numerada -- antes era
            // solo un punto (bullet) suelto sobre el fondo, sin la misma
            // jerarquía de tarjeta que el resto de Modo Estudio (notas,
            // historial de lectura).
            itemsIndexed(sentences) { index, sentence ->
                val shape = MaterialTheme.shapes.medium
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .accentShadow(shape = shape, elevation = 1.dp)
                            .clip(shape)
                            .background(MaterialTheme.colorScheme.surface)
                            .accentBorder(shape = shape),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .size(24.dp)
                                    .background(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                        MaterialTheme.shapes.extraLarge,
                                    ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "${index + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Text(
                            text = sentence,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Suppress("TooGenericExceptionCaught")
private fun shareSummary(
    context: Context,
    documentName: String,
    sentences: List<String>,
    shareTitle: String,
) {
    try {
        val file = StudySummaryExporter.exportAsTextFile(context, documentName, sentences)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent =
            android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        context.startActivity(android.content.Intent.createChooser(intent, shareTitle))
    } catch (e: Exception) {
        Timber.e("Error compartiendo resumen de estudio (${e.javaClass.simpleName})")
    }
}

@Suppress("TooGenericExceptionCaught")
private suspend fun saveSummaryToDownloads(
    context: Context,
    documentName: String,
    sentences: List<String>,
): Boolean {
    return try {
        val file = StudySummaryExporter.exportAsTextFile(context, documentName, sentences)
        DownloadsSaver.saveFile(context, file, "text/plain")
    } catch (e: Exception) {
        Timber.e("Error guardando resumen en Descargas (${e.javaClass.simpleName})")
        false
    }
}

// Mensajes localizados, resueltos en la capa de presentación (stringResource)
// y pasados hacia abajo — estas funciones de extracción no tienen Context de
// recursos. Reducido 2026-09-08 a solo PDF (pedido explícito del usuario:
// Lectura deja de aceptar Word, que daba problemas con el parser propio de
// esta pantalla) -- se quitan los mensajes que solo usaban las rutas de
// Word/PPT/texto plano, ya eliminadas.
data class StudyExtractionMessages(
    val couldNotRead: String,
    val pdfNoText: String,
    // formato: %1$s
    val pdfErrorTemplate: String,
    // formato: %1$s
    val genericErrorTemplate: String,
    val defaultDocumentName: String,
    // Ver N3 (décima ronda) más abajo, en extractTextFromUri().
    val outOfMemoryMessage: String,
)

data class StudyExtractionResult(
    val paragraphs: List<String>,
    val fileName: String,
    // "Retomar lectura" (2026-09-08): cuántos párrafos acumulados hay al
    // terminar cada página del PDF -- permite traducir el índice de párrafo
    // que va leyendo la voz a un número de página real (`pageForParagraph`).
    val pageBoundaries: List<Int> = emptyList(),
    // Hallazgo real de la auditoría general 2026-09-17/18 (décima ronda,
    // Media -- N4): antes, si el documento se borraba/movía mientras
    // había progreso de lectura guardado, el mensaje de error de
    // extracción se devolvía COMO SI fuera un párrafo real del documento
    // (dentro de `paragraphs`) -- "Continuar leyendo" mostraba ese texto
    // de error en vez de avisar que el documento ya no existe, y la
    // entrada quedaba fantasma en `StudyReadingProgressStorage` para
    // siempre. Este flag deja distinguir ambos casos en el llamador.
    val isError: Boolean = false,
)

// ── Extraer texto de un PDF ───────────────────────────
// "Procesamiento incremental" (2026-09-08, pedido explícito del usuario tras
// notar que un dispositivo más lento tardaba más en extraer el mismo PDF):
// antes esta función solo devolvía resultado cuando TERMINABA de procesar
// todas las páginas. Ahora recibe `onPageExtracted`, invocado después de cada
// página con el texto acumulado hasta ese punto, para que la pantalla pueda
// mostrar el documento y habilitar "Leer todo" desde la primera página lista
// en vez de esperar el 100% del documento.
private suspend fun extractTextFromUri(
    context: Context,
    uri: Uri,
    messages: StudyExtractionMessages,
    onPageExtracted: suspend (paragraphs: List<String>, pageBoundaries: List<Int>) -> Unit = { _, _ -> },
): StudyExtractionResult =
    withContext(Dispatchers.IO) {
        try {
            val fileName = resolveFileName(context, uri, messages)
            val (paragraphs, pageBoundaries, extractionFailed) = extractPdfText(context, uri, messages, onPageExtracted)
            StudyExtractionResult(paragraphs, fileName, pageBoundaries, isError = extractionFailed)
        } catch (e: CancellationException) {
            // Hallazgo real de la auditoría general 2026-09-18 (Media): si el
            // usuario navega fuera de Modo Lectura mientras una extracción
            // larga está en curso, el catch genérico de abajo tragaba la
            // CancellationException como isError = true, y loadDocument()
            // borraba el progreso de "Continuar leyendo" de un documento que en
            // realidad nunca falló. Mismo patrón ya aplicado en
            // shareStudyNotes de este mismo archivo: relanzarla siempre, nunca
            // tragarla.
            throw e
        } catch (e: Exception) {
            Timber.e("Error extrayendo texto (${e.javaClass.simpleName})")
            StudyExtractionResult(
                emptyList(),
                String.format(messages.genericErrorTemplate, e.message ?: ""),
                isError = true,
            )
        } catch (e: OutOfMemoryError) {
            // Hallazgo real de la auditoría general 2026-09-17/18 (décima
            // ronda, Media -- N3): OutOfMemoryError no hereda de Exception --
            // mismo patrón ya corregido en 11 conversores del Convertidor
            // (P1, quinta ronda), nunca extendido a Lectura, que acumula
            // todos los párrafos de un PDF grande en memoria.
            Timber.e("StudyScreen: sin memoria extrayendo el documento (${e.javaClass.simpleName})")
            StudyExtractionResult(
                emptyList(),
                String.format(messages.genericErrorTemplate, messages.outOfMemoryMessage),
                isError = true,
            )
        }
    }

// RF: antes dividía por CADA salto de línea del PDF (`pageText.split("\n")`),
// así que una oración larga que el PDF ajusta en 2-3 líneas visuales se
// mostraba y se leía en voz alta como 2-3 "párrafos" distintos, cortados a
// mitad de frase. Ahora agrupa por espaciado vertical real entre líneas
// (misma heurística ya verificada en PdfToWordUseCase/RF-CONV-09: un salto
// > 1.6x el tamaño de fuente = párrafo nuevo, uno menor = ajuste de línea
// dentro del mismo párrafo lógico) para que "leer este párrafo" lea un
// párrafo real, no medio renglón.
// Hallazgo real de la revisión general 2026-09-16 (cuarta pasada, #28):
// pdfDoc.close()/cacheFile.delete() manuales solo se alcanzaban en el
// camino feliz -- un PDF protegido/corrupto elegido en "Abrir documento"
// (PdfDocument(PdfReader(cacheFile)) lanza, o cualquier excepción a mitad
// del loop de páginas) dejaba el PdfDocument/PdfReader sin cerrar y el
// archivo temporal huérfano en cacheDir en cada intento. Mismo patrón ya
// corregido en PdfToTextUseCase (hallazgo #39): cacheFile se declara
// afuera del try para poder borrarlo en el finally, y .use{} cierra
// pdfDoc pase lo que pase.
private suspend fun extractPdfText(
    context: Context,
    uri: Uri,
    messages: StudyExtractionMessages,
    onPageExtracted: suspend (paragraphs: List<String>, pageBoundaries: List<Int>) -> Unit = { _, _ -> },
): Triple<List<String>, List<Int>, Boolean> {
    var cacheFile: File? = null
    return try {
        cacheFile = File.createTempFile("study_temp", ".pdf", context.cacheDir)
        context.contentResolver.openInputStream(uri)?.use { input ->
            cacheFile.outputStream().use { output -> input.copyTo(output) }
        } ?: return Triple(listOf(messages.couldNotRead), emptyList(), true)

        val paragraphs = mutableListOf<String>()
        val pageBoundaries = mutableListOf<Int>()
        PdfDocument(PdfReader(cacheFile)).use { pdfDoc ->
            extractPdfPages(pdfDoc, paragraphs, pageBoundaries, onPageExtracted)
        }

        if (paragraphs.isEmpty()) {
            Triple(listOf(messages.pdfNoText), emptyList(), true)
        } else {
            Triple(paragraphs, pageBoundaries, false)
        }
    } catch (e: CancellationException) {
        // Ver comentario equivalente en extractTextFromUri: relanzar
        // siempre, nunca tragar la cancelación como si fuera un error de
        // extracción real.
        throw e
    } catch (e: Exception) {
        Timber.e("Error extrayendo texto PDF (${e.javaClass.simpleName})")
        Triple(listOf(String.format(messages.pdfErrorTemplate, e.message ?: "")), emptyList(), true)
    } finally {
        cacheFile?.delete()
    }
}

// Extraído de extractPdfText() (detekt: NestedBlockDepth, disparado al
// hoistear cacheFile fuera del try para el fix del hallazgo #28).
private suspend fun extractPdfPages(
    pdfDoc: PdfDocument,
    paragraphs: MutableList<String>,
    pageBoundaries: MutableList<Int>,
    onPageExtracted: suspend (paragraphs: List<String>, pageBoundaries: List<Int>) -> Unit,
) {
    for (i in 1..pdfDoc.numberOfPages) {
        val listener = StudyPdfLineListener()
        PdfCanvasProcessor(listener).processPageContent(pdfDoc.getPage(i))
        paragraphs.addAll(groupPdfChunksIntoParagraphs(listener.chunks))
        pageBoundaries.add(paragraphs.size)
        if (paragraphs.isNotEmpty()) onPageExtracted(paragraphs.toList(), pageBoundaries.toList())
    }
}

internal data class StudyPdfChunk(val text: String, val y: Float, val fontSize: Float)

private class StudyPdfLineListener : IEventListener {
    val chunks = mutableListOf<StudyPdfChunk>()

    override fun eventOccurred(
        data: IEventData?,
        type: EventType,
    ) {
        val info = data as? TextRenderInfo ?: return
        if (info.text.isEmpty()) return
        val y = info.baseline.startPoint.get(Vector.I2)
        chunks.add(StudyPdfChunk(info.text, y, info.fontSize))
    }

    override fun getSupportedEvents() = mutableSetOf(EventType.RENDER_TEXT)
}

internal fun groupPdfChunksIntoParagraphs(chunks: List<StudyPdfChunk>): List<String> {
    if (chunks.isEmpty()) return emptyList()
    val paragraphs = mutableListOf<StringBuilder>()
    var current = StringBuilder()
    var previousY: Float? = null

    chunks.forEach { chunk ->
        val sameLine = previousY != null && kotlin.math.abs(previousY!! - chunk.y) <= 1f
        val isNewParagraph =
            previousY != null && !sameLine &&
                (previousY!! - chunk.y) > 1.6f * chunk.fontSize
        val isWrappedLine = previousY != null && !sameLine && !isNewParagraph

        if (isNewParagraph) {
            paragraphs.add(current)
            current = StringBuilder()
        }
        if (isWrappedLine && current.isNotEmpty() && !chunk.text.startsWith(" ")) current.append(' ')
        current.append(chunk.text)
        previousY = chunk.y
    }
    paragraphs.add(current)
    // Ronda 16: un párrafo más largo que el límite del motor TTS se parte en
    // trozos (ver splitForSpeech) en vez de perderse en silencio al leerlo.
    return paragraphs.map { it.toString().trim() }.filter { it.length > 5 }.flatMap { splitForSpeech(it) }
}

private fun resolveFileName(
    context: Context,
    uri: Uri,
    messages: StudyExtractionMessages,
): String {
    return try {
        var name = messages.defaultDocumentName
        context.contentResolver.query(
            uri,
            arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) name = cursor.getString(0) ?: name
        }
        name
    } catch (e: Exception) {
        messages.defaultDocumentName
    }
}
