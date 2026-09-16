package com.docsmart.features.study.presentation

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.docsmart.R
import com.docsmart.core.ads.AdConstants
import com.docsmart.core.ads.DocuSmartBannerAd
import com.docsmart.core.analytics.DocuSmartAnalytics
import com.docsmart.core.pdf.PdfPageBitmap
import com.docsmart.core.pdf.renderPdfPagesToBitmaps
import com.docsmart.core.ui.components.DocuSmartScreenHeader
import com.docsmart.core.ui.components.DocuSmartTopBanner
import com.docsmart.features.study.domain.PomodoroEngine
import com.docsmart.features.study.domain.SavedNote
import com.docsmart.features.study.domain.StudyNotesExporter
import com.docsmart.features.study.domain.ReadingProgress
import com.docsmart.features.study.domain.StudyNotesStorage
import com.docsmart.features.study.domain.StudyReadingProgressStorage
import com.docsmart.features.study.domain.pageForParagraph
import com.docsmart.features.study.domain.StudyStats
import com.docsmart.features.study.domain.StudyStatsStorage
import com.docsmart.features.study.domain.StudySummaryExporter
import com.docsmart.features.study.domain.StudyVoicePreference
import com.docsmart.features.study.domain.TextSummarizer
import com.docsmart.features.study.domain.millisToHoursAndMinutes
import com.docsmart.features.study.domain.pomodoroCountsByWeekday
import com.docsmart.core.ui.theme.SuccessGreen
import com.docsmart.core.ui.theme.WarningAmber
import com.docsmart.core.ui.theme.accentBorder
import com.docsmart.core.ui.theme.accentShadow
import com.docsmart.core.ui.theme.rememberAccentGradient
import com.docsmart.core.util.DownloadsSaver
import com.itextpdf.kernel.geom.Vector
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.canvas.parser.EventType
import com.itextpdf.kernel.pdf.canvas.parser.PdfCanvasProcessor
import com.itextpdf.kernel.pdf.canvas.parser.data.IEventData
import com.itextpdf.kernel.pdf.canvas.parser.data.TextRenderInfo
import com.itextpdf.kernel.pdf.canvas.parser.listener.IEventListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.util.Calendar
import java.util.Locale

@Composable
fun StudyScreen(
    onBack: () -> Unit = {},
    initialTab: Int = 0,
    viewModel: StudyViewModel = hiltViewModel()
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
    var selectedTab by remember { mutableIntStateOf(initialTab.coerceIn(0, 2)) }
    // ── Resumen automático (2026-09-08, 100% local -- ver TextSummarizer) ──
    var summarySentences by remember { mutableStateOf<List<String>?>(null) }
    var isSummarizing by remember { mutableStateOf(false) }
    // Pedido explícito del usuario 2026-09-08: Lectura pasa a aceptar solo
    // PDF (antes "*/*" -- los documentos Word daban problemas con el parser
    // propio de esta pantalla). `documentUri` es nuevo -- antes solo se
    // guardaba el texto ya extraído, pero ahora también hace falta el PDF
    // original para mostrarlo mientras la voz lee (ver StudyPdfViewer).
    var documentUri by remember { mutableStateOf<Uri?>(null) }
    var documentText by remember { mutableStateOf<List<String>>(emptyList()) }
    // "Retomar lectura" (2026-09-08): límites de página del documento activo,
    // para poder mostrar "página X de Y" y guardar el progreso por página.
    var pageBoundaries by remember { mutableStateOf<List<Int>>(emptyList()) }
    // Pedido explícito del usuario 2026-09-08: en un dispositivo más lento la
    // extracción del PDF completo tardaba mucho más que en otro -- ahora
    // `documentText`/`pageBoundaries` se actualizan página por página en vez
    // de esperar a que termine todo el documento, y `extractionComplete`
    // indica si aún queda procesamiento en segundo plano.
    var extractionComplete by remember { mutableStateOf(true) }
    val noDocumentLabel = stringResource(R.string.study_no_document)
    var documentName by remember { mutableStateOf(noDocumentLabel) }
    // Pedido explícito del usuario 2026-09-08: además de "Continuar leyendo",
    // poder quitar un PDF de esa lista -- estado propio (no `remember` de una
    // sola vez) para que la tarjeta desaparezca al tocar "Quitar" sin
    // necesidad de salir y volver a entrar a la pestaña Lectura.
    var readingHistory by remember { mutableStateOf(StudyReadingProgressStorage.loadAll(context)) }

    val extractionMessages = StudyExtractionMessages(
        couldNotRead         = stringResource(R.string.study_extract_could_not_read),
        pdfNoText            = stringResource(R.string.study_extract_pdf_no_text),
        pdfErrorTemplate     = stringResource(R.string.study_extract_pdf_error),
        genericErrorTemplate = stringResource(R.string.study_extract_generic_error),
        defaultDocumentName  = stringResource(R.string.study_default_document_name)
    )
    var notes by remember { mutableStateOf("") }
    var highlights by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var isLoadingDoc by remember { mutableStateOf(false) }

    // ── TTS ───────────────────────────────────────────
    val ttsRef = remember { mutableStateOf<TextToSpeech?>(null) }
    val isSpeaking = remember { mutableStateOf(false) }
    val ttsReady = remember { mutableStateOf(false) }
    val currentSpeakingIndex = remember { mutableIntStateOf(-1) }
    // Selector de voz (pedido explícito de testers 2026-09-12): solo voces
    // instaladas en el dispositivo para el idioma actual, nunca las que
    // requieren red (`isNetworkConnectionRequired`) -- se queda 100% local
    // y gratis para todos, sin depender de ningún servicio en la nube.
    val availableVoices = remember { mutableStateOf<List<Voice>>(emptyList()) }
    val selectedVoice = remember { mutableStateOf<Voice?>(null) }
    var showVoicePicker by remember { mutableStateOf(false) }
    // Ver comentario de `extractionComplete` -- si "Leer todo" alcanza el
    // último párrafo ya extraído mientras el resto del PDF sigue procesándose
    // en segundo plano, esto queda en true hasta que aparezcan más párrafos
    // (o termine la extracción) en vez de dar la lectura por terminada.
    val waitingForMoreText = remember { mutableStateOf(false) }

    // ── Pomodoro (RF-STU-10: vive en PomodoroEngine, no en remember{},
    // para que siga corriendo al salir de esta pantalla) ─────────────
    val pomodoroState by PomodoroEngine.state.collectAsState()

    // ── Estadísticas (RF-STU-09) ──────────────────────
    var showStats by remember { mutableStateOf(false) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* No-op: el Pomodoro funciona igual sin el permiso, solo no se ve
          la notificación mientras la app está en segundo plano. */ }

    // ── Inicializar TTS ───────────────────────────────
    DisposableEffect(Unit) {
        var ttsInstance: TextToSpeech? = null
        ttsInstance = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                // Antes forzaba español (Locale("es","ES")) sin importar el idioma
                // configurado — mismo bug que ya se corrigió para el reconocimiento
                // de voz, pero solo del lado de entrada, no de lectura en voz alta.
                val result = ttsInstance?.setLanguage(Locale.getDefault())
                if (result == TextToSpeech.LANG_MISSING_DATA ||
                    result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    ttsInstance?.language = Locale("es", "ES")
                }
                ttsInstance?.setSpeechRate(0.85f)
                ttsInstance?.setPitch(1.05f)

                // Bug real encontrado al verificar en dispositivo (2026-09-12):
                // ttsInstance.language.language puede devolver el código ISO
                // de 3 letras ("spa") mientras que voice.locale.language usa
                // 2 letras ("es") para el mismo idioma -- comparados directo,
                // ninguna voz coincidía nunca (0 voces encontradas en la
                // prueba real). Se normalizan ambos lados a ISO3 antes de
                // comparar.
                val currentIso3Language = runCatching { Locale.getDefault().isO3Language }.getOrNull()
                val voices = ttsInstance?.voices
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
        }
        onDispose {
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
        val resumeFrom = currentSpeakingIndex.intValue + 1
        val uriString = documentUri?.toString()
        if (resumeFrom <= documentText.lastIndex) {
            waitingForMoreText.value = false
            for (idx in resumeFrom..documentText.lastIndex) {
                tts.speak(documentText[idx], TextToSpeech.QUEUE_ADD, null, "study_all_$idx")
            }
        } else if (extractionComplete) {
            // Terminó de extraer y no quedó nada más por leer.
            waitingForMoreText.value = false
            isSpeaking.value = false
            currentSpeakingIndex.intValue = -1
            if (uriString != null) StudyReadingProgressStorage.remove(context, uriString)
        }
    }

    // ── RF-STU-10: pide el permiso de notificaciones (Android 13+) al
    // entrar a la pestaña Pomodoro, para que la notificación de progreso en
    // segundo plano sea visible -- el timer en sí funciona igual sin el
    // permiso, ver comentario en el launcher.
    LaunchedEffect(selectedTab) {
        if (selectedTab != 2 || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@LaunchedEffect
        val granted = ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }

    // "Retomar lectura" (2026-09-08): carga un PDF y, si se le pasa un
    // párrafo de retomo (viene de un progreso guardado), deja la lectura
    // lista para continuar ahí en vez de desde el principio. Compartida
    // entre el selector de documento normal y la lista "Continuar leyendo".
    fun loadDocument(uri: Uri, resumeFromParagraph: Int?) {
        isLoadingDoc = true
        extractionComplete = false
        documentUri  = uri
        documentText = emptyList()
        pageBoundaries = emptyList()
        summarySentences = null // documento nuevo -- el resumen anterior ya no aplica
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
            val result = extractTextFromUri(context, uri, extractionMessages) { partialParagraphs, partialBoundaries ->
                // El callback llega desde Dispatchers.IO (extractPdfText corre
                // ahí) -- se pasa a Main antes de tocar estado de Compose.
                withContext(Dispatchers.Main) {
                    documentText   = partialParagraphs
                    pageBoundaries = partialBoundaries
                    isLoadingDoc   = false
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
            documentText   = result.paragraphs
            documentName   = result.fileName
            pageBoundaries = result.pageBoundaries
            if (!resumeApplied) {
                currentSpeakingIndex.intValue = resumeFromParagraph
                    ?.coerceIn(0, (result.paragraphs.size - 1).coerceAtLeast(0))
                    ?: -1
            }
            isLoadingDoc = false
            extractionComplete = true
        }
    }

    // ── Selector de documento (solo PDF) ──────────────
    // Bug real corregido 2026-09-08: con `GetContent()` (ACTION_GET_CONTENT)
    // `takePersistableUriPermission` no lanzaba excepción pero el permiso NO
    // quedaba realmente persistido -- solo `OpenDocument()`
    // (ACTION_OPEN_DOCUMENT) lo soporta. Por eso "Continuar leyendo" fallaba
    // en silencio después de cerrar la app: al reabrir, tanto la extracción
    // de texto como el visor de PDF recibían SecurityException del
    // DownloadStorageProvider al intentar leer el mismo URI.
    val docLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            @Suppress("TooGenericExceptionCaught")
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                Timber.w(e, "No se pudo tomar permiso persistente sobre $uri")
            }
            val saved = StudyReadingProgressStorage.findFor(context, uri.toString())
            loadDocument(uri, resumeFromParagraph = saved?.paragraphIndex)
        }
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
        containerColor = Color.Transparent
    ) { innerPadding ->
        if (showStats) {
            StudyStatsDialog(
                stats = remember(showStats) { StudyStatsStorage.loadStats(context) },
                onDismiss = { showStats = false }
            )
        }
        if (showVoicePicker) {
            VoiceSelectorDialog(
                voices = availableVoices.value,
                selectedVoice = selectedVoice.value,
                onVoiceSelected = { voice ->
                    ttsRef.value?.voice = voice
                    selectedVoice.value = voice
                    StudyVoicePreference.save(context, voice.name)
                    showVoicePicker = false
                },
                onDismiss = { showVoicePicker = false }
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
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
            DocuSmartScreenHeader(
                adUnitId  = AdConstants.BANNER_STUDY_ID,
                adManager = viewModel.adManager
            ) {
                Column {
                    DocuSmartTopBanner(
                        screenTitle    = stringResource(R.string.study_title),
                        screenSubtitle = documentName
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp, bottom = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.clickable(role = Role.Button) {
                                ttsRef.value?.stop()
                                isSpeaking.value = false
                                onBack()
                            }
                        ) {
                            Icon(
                                imageVector        = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = null,
                                tint               = MaterialTheme.colorScheme.primary,
                                modifier           = Modifier.size(18.dp)
                            )
                            Text(
                                text  = stringResource(R.string.general_back),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        // Pedido explícito del usuario 2026-09-08: los
                        // íconos quedaban sueltos, sin ningún fondo que los
                        // distinguiera del resto de la fila -- ahora cada
                        // uno lleva su propio círculo (borde + fondo
                        // tintado, contraste con el color del ícono).
                        // Botones subidos de 36dp a 48dp (auditoría de
                        // testers 2026-09-12, "botones pequeños").
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            IconButton(
                                onClick = { docLauncher.launch(arrayOf("application/pdf")) },
                                modifier = Modifier
                                    .size(48.dp)
                                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape)
                            ) {
                                Icon(
                                    imageVector        = Icons.Rounded.FolderOpen,
                                    contentDescription = stringResource(R.string.qr_open_document),
                                    tint               = MaterialTheme.colorScheme.primary,
                                    modifier           = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = { showStats = true },
                                modifier = Modifier
                                    .size(48.dp)
                                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape)
                            ) {
                                Icon(
                                    imageVector        = Icons.Rounded.QueryStats,
                                    contentDescription = stringResource(R.string.study_stats_icon_desc),
                                    tint               = MaterialTheme.colorScheme.primary,
                                    modifier           = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // ── Tabs ──────────────────────────────────
            // Seguimiento 2026-09-08: antes quedaba blanco plano -- ahora
            // usa `primaryContainer` (ya recoloreado por el acento elegido
            // en Ajustes, mismo mecanismo que `AccentGradient.kt`) con el
            // texto en `primary`/`onSurfaceVariant` para mantener buen
            // contraste sobre ese fondo tintado.
            val tabs = listOf(
                stringResource(R.string.study_tab_reading),
                stringResource(R.string.study_tab_notes),
                stringResource(R.string.study_tab_pomodoro)
                // "Resumen" oculta para el primer release, ver comentario junto a selectedTab.
            )
            // TabRow (vuelto a usar 2026-09-10, con "Resumen" oculto): con
            // 4 pestañas, TabRow forzaba el mismo ancho fijo y "Pomodoro" se
            // partía en 2 líneas -- por eso se había pasado a
            // ScrollableTabRow, que mide cada pestaña por su contenido (sin
            // llenar el ancho, dejando un hueco vacío a la derecha con solo
            // 3 pestañas). Con 3 pestañas cada una tiene más espacio y
            // "Pomodoro" entra en una sola línea, así que TabRow (ancho
            // fijo, reparte el espacio total) evita el hueco. Si "Resumen"
            // vuelve a activarse (ver `tabs` arriba), hay que volver a
            // ScrollableTabRow para no reintroducir el corte de línea.
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                contentColor   = MaterialTheme.colorScheme.primary
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        selectedContentColor   = MaterialTheme.colorScheme.primary,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        text = {
                            // Hallazgo #57 (revisión general 2026-09-16):
                            // maxLines=1 sin overflow=Ellipsis recortaba el
                            // texto en seco con FontScale.EXTRA_LARGE en vez
                            // de mostrar "...".
                            Text(
                                text = title,
                                fontWeight = if (selectedTab == index)
                                    FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    )
                }
            }

            when (selectedTab) {
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
                0 -> ReadingTab(
                    documentUri = documentUri,
                    isLoading = isLoadingDoc,
                    highlightedCount = highlights.size,
                    isCurrentHighlighted = highlights.contains(currentSpeakingIndex.intValue),
                    isSpeaking = isSpeaking.value,
                    ttsReady = ttsReady.value,
                    isExtractingMore = !extractionComplete,
                    onToggleHighlightCurrent = {
                        val index = currentSpeakingIndex.intValue
                        if (index >= 0) {
                            highlights = if (highlights.contains(index)) {
                                highlights - index
                            } else {
                                highlights + index
                            }
                        }
                    },
                    currentPage = pageForParagraph(currentSpeakingIndex.intValue.coerceAtLeast(0), pageBoundaries),
                    totalPages = pageBoundaries.size,
                    onSpeakAll = {
                        if (isSpeaking.value) {
                            // "Retomar lectura" (2026-09-08): antes esto
                            // reiniciaba `currentSpeakingIndex` a -1, así que
                            // parar y volver a tocar "Leer todo" (en esta
                            // misma sesión o en una futura) siempre empezaba
                            // desde el principio. Ahora se deja tal cual --
                            // "Leer todo" retoma desde ahí la próxima vez.
                            ttsRef.value?.stop()
                            isSpeaking.value = false
                            waitingForMoreText.value = false
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
                            val startIndex = currentSpeakingIndex.intValue
                                .takeIf { it in documentText.indices } ?: 0
                            val uriString = documentUri?.toString()
                            currentTts.setOnUtteranceProgressListener(
                                object : UtteranceProgressListener() {
                                    override fun onStart(utteranceId: String?) {
                                        val index = utteranceId?.substringAfterLast('_')?.toIntOrNull()
                                        isSpeaking.value = true
                                        if (index != null) {
                                            currentSpeakingIndex.intValue = index
                                            if (uriString != null) {
                                                StudyReadingProgressStorage.save(
                                                    context,
                                                    ReadingProgress(
                                                        uri              = uriString,
                                                        documentName     = documentName,
                                                        paragraphIndex   = index,
                                                        totalParagraphs  = documentText.size,
                                                        currentPage      = pageForParagraph(index, pageBoundaries),
                                                        totalPages       = pageBoundaries.size,
                                                        lastReadAtMillis = System.currentTimeMillis()
                                                    )
                                                )
                                            }
                                        }
                                    }
                                    override fun onDone(utteranceId: String?) {
                                        val index = utteranceId?.substringAfterLast('_')?.toIntOrNull()
                                        if (index == null) return
                                        // "Procesamiento incremental": `lastIndex`
                                        // se calculó al tocar "Leer todo", pero
                                        // si el PDF seguía extrayéndose de
                                        // fondo puede que ya haya más párrafos
                                        // ahora que cuando se armó la cola --
                                        // se revisa el tamaño ACTUAL de
                                        // `documentText`, no el de entonces.
                                        val newLastIndex = documentText.lastIndex
                                        when {
                                            index < newLastIndex -> {
                                                ttsRef.value?.let { tts ->
                                                    for (nextIndex in index + 1..newLastIndex) {
                                                        tts.speak(
                                                            documentText[nextIndex],
                                                            TextToSpeech.QUEUE_ADD,
                                                            null,
                                                            "study_all_$nextIndex"
                                                        )
                                                    }
                                                }
                                            }
                                            !extractionComplete -> {
                                                // No hay más texto disponible
                                                // TODAVÍA, pero el PDF sigue
                                                // procesándose -- esperar en
                                                // vez de dar la lectura por
                                                // terminada (ver LaunchedEffect
                                                // que retoma cuando llegue más).
                                                waitingForMoreText.value = true
                                            }
                                            else -> {
                                                // Terminó todo el documento --
                                                // ya no hay nada que retomar.
                                                isSpeaking.value = false
                                                currentSpeakingIndex.intValue = -1
                                                if (uriString != null) {
                                                    StudyReadingProgressStorage.remove(context, uriString)
                                                }
                                            }
                                        }
                                    }
                                    override fun onError(utteranceId: String?) {
                                        // No se resetea el índice -- si falla
                                        // a mitad de un documento largo, "Leer
                                        // todo" debe poder reintentar desde
                                        // ahí, no desde el principio.
                                        isSpeaking.value = false
                                    }
                                }
                            )
                            documentText.withIndex().drop(startIndex).forEachIndexed { queuePos, (index, paragraph) ->
                                currentTts.speak(
                                    paragraph,
                                    if (queuePos == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD,
                                    null,
                                    "study_all_$index"
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
                    onVoiceSelectorClick = { showVoicePicker = true }
                )

                // ── Tab Notas ─────────────────────────
                1 -> NotesTab(
                    notes = notes,
                    onNotesChange = { notes = it },
                    highlights = highlights,
                    documentText = documentText
                )

                // ── Tab Pomodoro ──────────────────────
                2 -> PomodoroTab(
                    minutes = pomodoroState.minutes,
                    seconds = pomodoroState.seconds,
                    isRunning = pomodoroState.isRunning,
                    isBreak = pomodoroState.isBreak,
                    pomodoroCount = pomodoroState.pomodoroCount,
                    onToggle = { PomodoroEngine.toggle(context) },
                    onReset = { PomodoroEngine.reset(context) }
                )

                // ── Tab Resumen (2026-09-08, 100% local) ──
                3 -> SummaryTab(
                    documentText     = documentText,
                    documentName     = documentName,
                    hasDocument      = documentUri != null,
                    summarySentences = summarySentences,
                    isSummarizing    = isSummarizing,
                    onGenerate = {
                        isSummarizing = true
                        scope.launch {
                            summarySentences = withContext(Dispatchers.Default) {
                                TextSummarizer.summarize(documentText)
                            }
                            isSummarizing = false
                        }
                    },
                    onSelectDoc = { docLauncher.launch(arrayOf("application/pdf")) }
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
private fun LoadingIndicator(message: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(modifier = Modifier.size(64.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                modifier    = Modifier.fillMaxSize(),
                color       = MaterialTheme.colorScheme.primary,
                strokeWidth = 5.dp
            )
            // El logo solo (sin fondo propio) se perdía contra el fondo
            // claro de la pantalla -- en todos los demás usos del logo en
            // la app siempre lleva un círculo de color detrás (ver banners),
            // así que se repite el mismo criterio acá.
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter            = painterResource(R.drawable.ic_docusmart_logo),
                    contentDescription = null,
                    modifier           = Modifier.size(24.dp)
                )
            }
        }
        Text(
            text      = message,
            style     = MaterialTheme.typography.bodyMedium,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
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
private fun ReadingTab(
    documentUri: Uri?,
    isLoading: Boolean,
    highlightedCount: Int,
    isCurrentHighlighted: Boolean,
    isSpeaking: Boolean,
    ttsReady: Boolean,
    isExtractingMore: Boolean,
    currentPage: Int,
    totalPages: Int,
    onToggleHighlightCurrent: () -> Unit,
    onSpeakAll: () -> Unit,
    onSelectDoc: () -> Unit,
    readingHistory: List<ReadingProgress>,
    onResumeDocument: (ReadingProgress) -> Unit,
    onDeleteDocument: (ReadingProgress) -> Unit,
    availableVoices: List<Voice> = emptyList(),
    onVoiceSelectorClick: () -> Unit = {}
) {
    Box(modifier = Modifier.fillMaxSize()) {
        when {
            // Pedido explícito del usuario 2026-09-08: la extracción de un
            // PDF real puede tardar bastante (probado con un libro de 55
            // páginas) -- un spinner solo, sin texto, hacía parecer que la
            // app se había colgado. Mismo mensaje que usa StudyPdfViewer más
            // abajo para su propia carga (para el usuario es un solo
            // "cargando documento", sin importar que sean 2 pasos técnicos
            // distintos).
            isLoading -> LoadingIndicator(
                stringResource(R.string.study_loading_document),
                modifier = Modifier.align(Alignment.Center)
            )
            documentUri == null -> ReadingEmptyState(
                readingHistory   = readingHistory,
                onSelectDoc      = onSelectDoc,
                onResumeDocument = onResumeDocument,
                onDeleteDocument = onDeleteDocument
            )
            else -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    // ── Barra TTS ─────────────────────
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = if (isSpeaking)
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        else
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        shadowElevation = 2.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(
                                horizontal = 16.dp, vertical = 10.dp
                            ),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isSpeaking)
                                    Icons.Rounded.VolumeUp
                                else
                                    Icons.Rounded.RecordVoiceOver,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                // "Retomar lectura" (2026-09-08): pedido
                                // explícito del usuario, "llevar el número
                                // de hojas leídas" -- mientras lee, muestra
                                // la página actual en vez del texto genérico.
                                text = when {
                                    isSpeaking && totalPages > 0 ->
                                        stringResource(R.string.study_reading_page, currentPage, totalPages)
                                    isSpeaking -> stringResource(R.string.study_reading_document)
                                    // "Procesamiento incremental": el PDF ya
                                    // se puede leer pero el resto todavía se
                                    // sigue extrayendo de fondo.
                                    isExtractingMore ->
                                        stringResource(R.string.study_extracting_more, totalPages)
                                    else -> stringResource(R.string.study_highlighted_count, highlightedCount)
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            // ── Marcar el párrafo que se está leyendo ─
                            // (reemplaza el resaltado por-fila de antes --
                            // ya no hay filas de párrafo que tocar, así que
                            // "marcar" pasa a aplicar al que suena ahora)
                            // Subido de 36dp a 48dp (auditoría de testers 2026-09-12, "botones pequeños").
                            IconButton(
                                onClick = onToggleHighlightCurrent,
                                enabled = isSpeaking,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = if (isCurrentHighlighted)
                                        Icons.Rounded.Bookmark
                                    else
                                        Icons.Rounded.BookmarkBorder,
                                    contentDescription = stringResource(R.string.study_mark_current_paragraph),
                                    tint = if (isSpeaking) WarningAmber
                                           else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            // ── Elegir voz (pedido explícito de testers
                            // 2026-09-12) -- solo si el motor TTS del
                            // dispositivo tiene más de una voz instalada
                            // para el idioma actual; si solo hay una no
                            // tiene sentido mostrar un selector.
                            if (availableVoices.size > 1) {
                                // Subido de 36dp a 48dp (auditoría de testers 2026-09-12, "botones pequeños").
                                IconButton(
                                    onClick = onVoiceSelectorClick,
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.RecordVoiceOver,
                                        contentDescription = stringResource(R.string.study_choose_voice),
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            // ── Botón leer todo ───────
                            FilledTonalButton(
                                onClick = onSpeakAll,
                                shape = MaterialTheme.shapes.medium,
                                enabled = ttsReady
                            ) {
                                Icon(
                                    imageVector = if (isSpeaking)
                                        Icons.Rounded.Stop
                                    else
                                        Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                val speakButtonLabel = if (isSpeaking) {
                                    stringResource(R.string.study_stop)
                                } else {
                                    stringResource(R.string.study_read_all)
                                }
                                Text(speakButtonLabel)
                            }
                        }
                    }

                    // ── PDF real, la voz lee de fondo ─
                    StudyPdfViewer(
                        uri      = documentUri,
                        modifier = Modifier.fillMaxWidth().weight(1f)
                    )
                }
            }
        }
    }
}

// Extraído de ReadingTab (LongMethod por detekt) -- estado sin documento
// activo. "Retomar lectura" (2026-09-08): con historial pendiente, el
// contenido ya no cabe centrado en pantallas chicas (hasta 10 documentos) --
// pasa a ser una columna con scroll, con la lista arriba del botón "Abrir
// documento". Sin historial, exactamente el mismo estado vacío de siempre.
@Composable
private fun BoxScope.ReadingEmptyState(
    readingHistory  : List<ReadingProgress>,
    onSelectDoc     : () -> Unit,
    onResumeDocument: (ReadingProgress) -> Unit,
    onDeleteDocument: (ReadingProgress) -> Unit
) {
    val emptyStateModifier = if (readingHistory.isEmpty()) {
        Modifier.align(Alignment.Center)
    } else {
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
    }
    Column(
        modifier = emptyStateModifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(
                    // Bug real corregido 2026-09-04 (backlog UX §7,
                    // HU-UX-06): fijo en tonos de azul, ignorando el "Color
                    // de acento" elegido en Ajustes.
                    brush = Brush.linearGradient(rememberAccentGradient()),
                    shape = MaterialTheme.shapes.extraLarge
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.MenuBook,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(40.dp)
            )
        }
        Text(
            text = stringResource(R.string.study_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = stringResource(R.string.study_empty_state_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (readingHistory.isNotEmpty()) {
            Text(
                text  = stringResource(R.string.study_continue_reading),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            readingHistory.forEach { progress ->
                ReadingHistoryCard(
                    progress = progress,
                    onClick  = { onResumeDocument(progress) },
                    onDelete = { onDeleteDocument(progress) }
                )
            }
        }
        Button(
            onClick = onSelectDoc,
            shape = MaterialTheme.shapes.medium
        ) {
            Icon(
                imageVector = Icons.Rounded.FolderOpen,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.qr_open_document))
        }
    }
}

// "Retomar lectura" (2026-09-08): un PDF con progreso guardado, en la lista
// "Continuar leyendo" del estado vacío de Lectura. Tocarlo reabre el archivo
// y deja la voz lista para seguir desde el mismo párrafo/página.
// Pedido explícito del usuario 2026-09-08: además de retomar, poder quitar un
// PDF de esta lista (no borra el archivo, solo el progreso guardado).
@Composable
private fun ReadingHistoryCard(progress: ReadingProgress, onClick: () -> Unit, onDelete: () -> Unit) {
    val shape = MaterialTheme.shapes.medium
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .accentShadow(shape = shape, elevation = 1.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .accentBorder(shape = shape)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector        = Icons.Rounded.MenuBook,
                contentDescription = null,
                tint               = MaterialTheme.colorScheme.primary,
                modifier           = Modifier.size(28.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text       = progress.documentName,
                    style      = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis,
                    color      = MaterialTheme.colorScheme.onSurface
                )
                if (progress.totalPages > 0) {
                    Text(
                        text  = stringResource(
                            R.string.study_resume_page_progress,
                            progress.currentPage,
                            progress.totalPages
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(
                imageVector        = Icons.Rounded.PlayCircle,
                contentDescription = stringResource(R.string.study_resume_reading),
                tint               = MaterialTheme.colorScheme.primary,
                modifier           = Modifier.size(28.dp)
            )
            // Subido de 28dp a 48dp (auditoría de testers 2026-09-12, "botones pequeños").
            IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                Icon(
                    imageVector        = Icons.Rounded.DeleteOutline,
                    contentDescription = stringResource(R.string.study_remove_from_history),
                    tint               = MaterialTheme.colorScheme.error,
                    modifier           = Modifier.size(20.dp)
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
private fun StudyPdfViewer(uri: Uri, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var pages by remember(uri) { mutableStateOf<List<PdfPageBitmap>>(emptyList()) }
    var loadError by remember(uri) { mutableStateOf(false) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(uri) {
        pages = withContext(Dispatchers.IO) {
            try {
                renderPdfPagesToBitmaps(uri, context, cachePrefix = "study")
            } catch (e: Exception) {
                Timber.e(e, "Estudio: error renderizando PDF")
                loadError = true
                emptyList()
            }
        }
    }

    when {
        loadError -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text  = stringResource(R.string.viewer_error),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        pages.isEmpty() -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            LoadingIndicator(stringResource(R.string.study_loading_document))
        }
        else -> LazyColumn(
            state = rememberLazyListState(),
            modifier = modifier
                .onSizeChanged { containerSize = it }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(0.5f, 4f)
                        val maxX = (containerSize.width  * (newScale - 1) / 2f).coerceAtLeast(0f)
                        val maxY = (containerSize.height * (newScale - 1) / 2f).coerceAtLeast(0f)
                        scale   = newScale
                        offsetX = (offsetX + pan.x).coerceIn(-maxX, maxX)
                        offsetY = (offsetY + pan.y).coerceIn(-maxY, maxY)
                    }
                }
                .graphicsLayer(
                    scaleX       = scale,
                    scaleY       = scale,
                    translationX = offsetX,
                    translationY = offsetY
                ),
            contentPadding      = PaddingValues(top = 8.dp, bottom = 16.dp, start = 8.dp, end = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(pages) { pageBitmap ->
                val shape = MaterialTheme.shapes.small
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .accentShadow(shape = shape, elevation = 2.dp)
                        .clip(shape)
                        .background(MaterialTheme.colorScheme.surface)
                        .accentBorder(shape = shape)
                ) {
                    Image(
                        bitmap             = pageBitmap.bitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier           = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

// ── Tab de Notas ──────────────────────────────────────
@Composable
private fun NotesTab(
    notes        : String,
    onNotesChange: (String) -> Unit,
    highlights   : Set<Int>,
    documentText : List<String>
) {
    val context = LocalContext.current

    // ── Estado ────────────────────────────────────────────────────────────────
    var savedNotes    by remember { mutableStateOf(StudyNotesStorage.loadNotes(context)) }
    var currentTitle  by remember { mutableStateOf("") }
    var currentNote   by remember { mutableStateOf(notes) }
    var showDeleteAll by remember { mutableStateOf(false) }
    var isListening   by remember { mutableStateOf(false) }

    val dateFormatter = remember {
        java.text.SimpleDateFormat("dd/MM/yyyy · HH:mm", java.util.Locale.getDefault())
    }

    // ── Reconocimiento de voz ─────────────────────────────────────────────────
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isListening = false
        val matches = result.data
            ?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
        if (!matches.isNullOrEmpty()) {
            val spoken = matches[0]
            currentNote = if (currentNote.isBlank()) spoken
            else "$currentNote $spoken"
            onNotesChange(currentNote)
        }
    }

    val voicePrompt = stringResource(R.string.study_voice_prompt)

    fun startVoiceInput() {
        try {
            val intent = android.content.Intent(
                android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH
            ).apply {
                putExtra(
                    android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, voicePrompt)
                putExtra(android.speech.RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            isListening = true
            speechLauncher.launch(intent)
        } catch (e: Exception) {
            isListening = false
            Timber.e(e, "Error iniciando reconocimiento de voz")
        }
    }

    // ── Diálogo eliminar todas ────────────────────────────────────────────────
    if (showDeleteAll) {
        AlertDialog(
            onDismissRequest = { showDeleteAll = false },
            shape            = MaterialTheme.shapes.large,
            title = { Text(stringResource(R.string.study_delete_all_notes_title)) },
            text  = { Text(stringResource(R.string.study_delete_all_notes_body)) },
            confirmButton = {
                TextButton(onClick = {
                    StudyNotesStorage.saveNotes(context, emptyList())
                    savedNotes    = emptyList()
                    showDeleteAll = false
                }) { Text(stringResource(R.string.general_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAll = false }) { Text(stringResource(R.string.general_cancel)) }
            }
        )
    }

    // Bug real corregido 2026-09-08: todo esto antes vivía en un `Column`
    // fijo (sin scroll) con un `LazyColumn` aparte solo para la lista de
    // notas -- al agregar la tarjeta del editor, el contenido ya no cabía
    // en pantallas chicas y el estado "Sin notas guardadas" quedaba cortado
    // sin forma de hacer scroll para verlo. Ahora es una única `LazyColumn`
    // para toda la pestaña.
    LazyColumn(modifier = Modifier.fillMaxSize()) {

        // ── Párrafos resaltados ───────────────────────────────────────────────
        if (highlights.isNotEmpty() && documentText.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector        = Icons.Rounded.Bookmarks,
                        contentDescription = null,
                        tint               = MaterialTheme.colorScheme.primary,
                        modifier           = Modifier.size(18.dp)
                    )
                    Text(
                        text       = stringResource(R.string.study_highlighted_paragraphs_count, highlights.size),
                        style      = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.primary
                    )
                }
            }
            itemsIndexed(highlights.sorted()) { _, index ->
                if (index < documentText.size) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp),
                        shape    = MaterialTheme.shapes.medium,
                        colors   = CardDefaults.cardColors(
                            containerColor = WarningAmber.copy(alpha = 0.1f)
                        )
                    ) {
                        Row(
                            modifier              = Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Rounded.Bookmark, null,
                                tint     = WarningAmber,
                                modifier = Modifier.size(16.dp))
                            Text(
                                text     = documentText[index],
                                style    = MaterialTheme.typography.bodySmall,
                                color    = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2
                            )
                        }
                    }
                }
            }
            item { HorizontalDivider(modifier = Modifier.padding(top = 8.dp)) }
        }

        // ── Editor de nota nueva ──────────────────────────────────────────────
        // Envuelto en su propia tarjeta (antes flotaba directo sobre el
        // fondo) para que tenga la misma jerarquía visual que las notas
        // guardadas de más abajo, en vez de sentirse como una sección aparte.
        item {
            val shape = MaterialTheme.shapes.large
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .accentShadow(shape = shape, elevation = 1.dp)
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surface)
                    .accentBorder(shape = shape)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector        = Icons.Rounded.EditNote,
                            contentDescription = null,
                            tint               = MaterialTheme.colorScheme.primary,
                            modifier           = Modifier.size(20.dp)
                        )
                        Text(
                            text       = stringResource(R.string.study_new_note),
                            style      = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color      = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Campo título
                    OutlinedTextField(
                        value         = currentTitle,
                        onValueChange = { currentTitle = it },
                        modifier      = Modifier.fillMaxWidth(),
                        label         = { Text(stringResource(R.string.study_note_title_label)) },
                        placeholder   = { Text(stringResource(R.string.study_note_title_placeholder)) },
                        singleLine    = true,
                        leadingIcon   = {
                            Icon(
                                imageVector        = Icons.Rounded.Title,
                                contentDescription = null,
                                tint               = MaterialTheme.colorScheme.primary,
                                modifier           = Modifier.size(20.dp)
                            )
                        },
                        shape  = MaterialTheme.shapes.large,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )

                    // Campo contenido + botón micrófono
                    OutlinedTextField(
                        value         = currentNote,
                        onValueChange = {
                            currentNote = it
                            onNotesChange(it)
                        },
                        modifier      = Modifier.fillMaxWidth().heightIn(min = 90.dp, max = 140.dp),
                        placeholder   = { Text(stringResource(R.string.study_note_content_placeholder)) },
                        trailingIcon  = {
                            // Subido de 40dp a 48dp (auditoría de testers 2026-09-12, "botones pequeños").
                            IconButton(
                                onClick  = { startVoiceInput() },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector        = if (isListening)
                                        Icons.Rounded.MicOff
                                    else
                                        Icons.Rounded.Mic,
                                    contentDescription = stringResource(R.string.study_dictate_note),
                                    tint               = if (isListening)
                                        MaterialTheme.colorScheme.error
                                    else
                                        MaterialTheme.colorScheme.primary,
                                    modifier           = Modifier.size(22.dp)
                                )
                            }
                        },
                        shape  = MaterialTheme.shapes.large,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = if (isListening)
                                MaterialTheme.colorScheme.error
                            else
                                MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )

                    // Indicador de escucha activa
                    if (isListening) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                            modifier              = Modifier.padding(start = 4.dp)
                        ) {
                            Icon(
                                imageVector        = Icons.Rounded.GraphicEq,
                                contentDescription = null,
                                tint               = MaterialTheme.colorScheme.error,
                                modifier           = Modifier.size(16.dp)
                            )
                            Text(
                                text  = stringResource(R.string.study_listening),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    val untitledNoteLabel = stringResource(R.string.study_untitled_note)

                    // Botón guardar
                    Button(
                        onClick = {
                            val text  = currentNote.trim()
                            val title = currentTitle.trim()
                            if (text.isNotBlank()) {
                                val newNote = SavedNote(
                                    id       = System.currentTimeMillis().toString(),
                                    title    = title.ifBlank { untitledNoteLabel },
                                    text     = text,
                                    dateTime = dateFormatter.format(java.util.Date())
                                )
                                val updated = listOf(newNote) + savedNotes
                                StudyNotesStorage.saveNotes(context, updated)
                                DocuSmartAnalytics.logNoteCreated()
                                savedNotes   = updated
                                currentNote  = ""
                                currentTitle = ""
                                onNotesChange("")
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape    = MaterialTheme.shapes.medium,
                        enabled  = currentNote.trim().isNotBlank()
                    ) {
                        Icon(Icons.Rounded.Save, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.study_save_note), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        // ── Lista de notas guardadas ──────────────────────────────────────────
        item {
            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector        = Icons.AutoMirrored.Rounded.Notes,
                        contentDescription = null,
                        tint               = MaterialTheme.colorScheme.primary,
                        modifier           = Modifier.size(18.dp)
                    )
                    Text(
                        text       = if (savedNotes.isEmpty()) stringResource(R.string.study_no_saved_notes)
                        else stringResource(R.string.study_saved_notes_count, savedNotes.size),
                        style      = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.primary
                    )
                }
                if (savedNotes.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StudyExportNotesButton(notes = savedNotes)
                        TextButton(onClick = { showDeleteAll = true }) {
                            Text(
                                text  = stringResource(R.string.study_delete_all),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }

        if (savedNotes.isEmpty()) {
            item {
                Column(
                    modifier            = Modifier.fillMaxWidth().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Misma insignia circular con degradado de acento que usan
                    // los estados vacíos de Lectura y Resumen -- antes era un
                    // ícono gris plano, sin relación visual con el resto de la
                    // pantalla.
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(
                                brush = Brush.linearGradient(rememberAccentGradient()),
                                shape = MaterialTheme.shapes.extraLarge
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector        = Icons.Rounded.NoteAlt,
                            contentDescription = null,
                            tint               = Color.White,
                            modifier           = Modifier.size(30.dp)
                        )
                    }
                    Text(
                        text      = stringResource(R.string.study_no_notes_yet),
                        style     = MaterialTheme.typography.bodyMedium,
                        color     = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            itemsIndexed(savedNotes, key = { _, note -> note.id }) { _, note ->
                val shape = MaterialTheme.shapes.large
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 5.dp)
                        .accentShadow(shape = shape, elevation = 2.dp)
                        .clip(shape)
                        .background(MaterialTheme.colorScheme.surface)
                        .accentBorder(shape = shape)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Cabecera: título + eliminar
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment     = Alignment.CenterVertically,
                                modifier              = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector        = Icons.Rounded.NoteAlt,
                                    contentDescription = null,
                                    tint               = MaterialTheme.colorScheme.primary,
                                    modifier           = Modifier.size(16.dp)
                                )
                                Text(
                                    text       = note.title,
                                    style      = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color      = MaterialTheme.colorScheme.onSurface,
                                    maxLines   = 1,
                                    overflow   = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                            // Subido de 28dp a 48dp (auditoría de testers 2026-09-12, "botones pequeños").
                            IconButton(
                                onClick  = {
                                    val updated = savedNotes.filter { it.id != note.id }
                                    StudyNotesStorage.saveNotes(context, updated)
                                    savedNotes = updated
                                },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector        = Icons.Rounded.DeleteOutline,
                                    contentDescription = stringResource(R.string.study_delete_note_desc),
                                    tint               = MaterialTheme.colorScheme.error,
                                    modifier           = Modifier.size(16.dp)
                                )
                            }
                        }

                        // Fecha
                        Text(
                            text  = note.dateTime,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color     = MaterialTheme.colorScheme.outlineVariant
                        )

                        // Contenido
                        Text(
                            text       = note.text,
                            style      = MaterialTheme.typography.bodyMedium,
                            color      = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 22.sp
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

// ── RF-STU-08: exportar/compartir notas ───────────────
@Composable
private fun StudyExportNotesButton(notes: List<SavedNote>) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    val shareTitle = stringResource(R.string.study_export_share_title)

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Rounded.IosShare,
                contentDescription = stringResource(R.string.study_export_notes),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.study_export_as_text)) },
                onClick = {
                    expanded = false
                    shareStudyNotes(context, notes, asPdf = false, shareTitle = shareTitle)
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.study_export_as_pdf)) },
                onClick = {
                    expanded = false
                    shareStudyNotes(context, notes, asPdf = true, shareTitle = shareTitle)
                }
            )
        }
    }
}

@Suppress("TooGenericExceptionCaught")
private fun shareStudyNotes(context: Context, notes: List<SavedNote>, asPdf: Boolean, shareTitle: String) {
    try {
        val file = if (asPdf) StudyNotesExporter.exportAsPdfFile(context, notes)
        else StudyNotesExporter.exportAsTextFile(context, notes)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = if (asPdf) "application/pdf" else "text/plain"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(android.content.Intent.createChooser(intent, shareTitle))
    } catch (e: Exception) {
        Timber.e(e, "Error exportando notas de estudio")
    }
}

// ── RF-STU-09: estadísticas de estudio ────────────────
@Composable
private fun StudyStatsDialog(stats: StudyStats, onDismiss: () -> Unit) {
    val now = remember { System.currentTimeMillis() }
    val (hours, minutes) = remember(stats) { millisToHoursAndMinutes(stats.totalReadingMillis) }
    val weekdayCounts = remember(stats) { pomodoroCountsByWeekday(stats.pomodoroTimestamps, now) }
    val weekdayLabels = remember {
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
                        value = stringResource(R.string.study_stats_reading_value, hours, minutes)
                    )
                    StudyStatRow(
                        icon = Icons.Rounded.EmojiEvents,
                        label = stringResource(R.string.study_stats_pomodoros_total),
                        value = "${stats.pomodoroTimestamps.size}"
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.study_stats_pomodoros_this_week) +
                                " (${weekdayCounts.sum()})",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        StudyWeekBars(labels = weekdayLabels, counts = weekdayCounts)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.general_close)) }
        }
    )
}

// Selector de voz para "Lectura en voz alta" (pedido explícito de testers
// 2026-09-12): lista las voces on-device disponibles para el idioma actual
// -- nunca las que requieren red, ver el filtro en la inicialización del
// TTS más arriba (100% local y gratis, sin depender de ningún servicio en
// la nube).
@Composable
private fun VoiceSelectorDialog(
    voices: List<Voice>,
    selectedVoice: Voice?,
    onVoiceSelected: (Voice) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.study_choose_voice)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                voices.forEachIndexed { index, voice ->
                    val isSelected = voice.name == selectedVoice?.name
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .clickable { onVoiceSelected(voice) }
                            .padding(vertical = 10.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        RadioButton(selected = isSelected, onClick = { onVoiceSelected(voice) })
                        Text(
                            text = stringResource(
                                R.string.study_voice_option_label,
                                index + 1,
                                voice.locale.displayName,
                                voiceQualityLabel(voice.quality)
                            ),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.general_close)) }
        }
    )
}

@Composable
private fun voiceQualityLabel(quality: Int): String = when (quality) {
    Voice.QUALITY_VERY_HIGH -> stringResource(R.string.study_voice_quality_very_high)
    Voice.QUALITY_HIGH      -> stringResource(R.string.study_voice_quality_high)
    Voice.QUALITY_NORMAL    -> stringResource(R.string.study_voice_quality_normal)
    else                    -> stringResource(R.string.study_voice_quality_low)
}

@Composable
private fun StudyStatRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = WarningAmber, modifier = Modifier.size(22.dp))
        Text(text = label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun StudyWeekBars(labels: List<String>, counts: IntArray) {
    val maxCount = (counts.maxOrNull() ?: 0).coerceAtLeast(1)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        labels.forEachIndexed { index, label ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(text = "${counts[index]}", style = MaterialTheme.typography.labelSmall)
                Box(
                    modifier = Modifier
                        .width(18.dp)
                        .height((32 * counts[index] / maxCount).coerceAtLeast(4).dp)
                        // Bug real corregido 2026-09-04 (backlog UX §7,
                        // HU-UX-06): fijo en azul, ignorando el "Color de
                        // acento" elegido en Ajustes.
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ── Tab Pomodoro ──────────────────────────────────────
@Composable
private fun PomodoroTab(
    minutes: Int,
    seconds: Int,
    isRunning: Boolean,
    isBreak: Boolean,
    pomodoroCount: Int,
    onToggle: () -> Unit,
    onReset: () -> Unit
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
    val lifetimePomodoros = remember(pomodoroCount) {
        StudyStatsStorage.loadStats(context).pomodoroTimestamps.size
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Spacer(Modifier.height(8.dp))

        PomodoroInfoCard()
        PomodoroTypeIndicator(isBreak)
        PomodoroClock(minutes, seconds, isRunning, isBreak)
        PomodoroControls(isRunning, isBreak, onToggle, onReset)
        PomodoroCountCard(lifetimePomodoros)

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun PomodoroTypeIndicator(isBreak: Boolean) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (isBreak) SuccessGreen.copy(alpha = 0.15f)
        else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
    ) {
        Text(
            text = if (isBreak) stringResource(R.string.study_break_label) else stringResource(R.string.study_study_label),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (isBreak) SuccessGreen else MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(
                horizontal = 20.dp, vertical = 10.dp
            )
        )
    }
}

@Composable
private fun PomodoroClock(minutes: Int, seconds: Int, isRunning: Boolean, isBreak: Boolean) {
    Box(
        modifier = Modifier
            .size(200.dp)
            .background(
                brush = Brush.radialGradient(
                    colors = if (isBreak)
                        listOf(SuccessGreen.copy(0.2f), Color.Transparent)
                    else
                        listOf(MaterialTheme.colorScheme.primary.copy(0.2f), Color.Transparent)
                ),
                shape = RoundedCornerShape(100.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                // Solo dígitos 0-9 (sin sensibilidad real de locale) — se evita
                // String.format(Locale.getDefault(), ...) porque llamarlo dentro de
                // un @Composable no es observable ante un cambio de idioma en runtime
                // (lint: NonObservableLocale).
                text = "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}",
                fontSize = 52.sp,
                fontWeight = FontWeight.Bold,
                color = if (isBreak) SuccessGreen else MaterialTheme.colorScheme.primary
            )
            Text(
                text = if (isRunning) stringResource(R.string.study_in_progress) else stringResource(R.string.study_paused),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PomodoroControls(
    isRunning: Boolean,
    isBreak  : Boolean,
    onToggle : () -> Unit,
    onReset  : () -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedButton(
            onClick = onReset,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.height(52.dp)
        ) {
            Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.study_restart))
        }

        Button(
            onClick = onToggle,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.height(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isBreak) SuccessGreen else MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(
                imageVector = if (isRunning) Icons.Rounded.Pause
                else Icons.Rounded.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (isRunning) stringResource(R.string.study_pause) else stringResource(R.string.study_start),
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
private fun PomodoroCountCard(pomodoroCount: Int) {
    val shape = MaterialTheme.shapes.large
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .accentShadow(shape = shape)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .accentBorder(shape = shape)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.EmojiEvents,
                contentDescription = null,
                tint = WarningAmber,
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.study_pomodoros_completed),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.study_pomodoros_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "$pomodoroCount",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = WarningAmber
            )
        }
    }
}

@Composable
private fun PomodoroInfoCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
                .copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = stringResource(R.string.study_pomodoro_technique_info),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
    documentText    : List<String>,
    documentName    : String,
    hasDocument     : Boolean,
    summarySentences: List<String>?,
    isSummarizing   : Boolean,
    onGenerate      : () -> Unit,
    onSelectDoc     : () -> Unit
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
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .background(
                                brush = Brush.linearGradient(rememberAccentGradient()),
                                shape = MaterialTheme.shapes.extraLarge
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Summarize,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                    Text(
                        text = stringResource(R.string.study_tab_summary),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.study_empty_state_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Button(onClick = onSelectDoc, shape = MaterialTheme.shapes.medium) {
                        Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.qr_open_document))
                    }
                }
            }
            isSummarizing -> LoadingIndicator(
                stringResource(R.string.study_summarizing),
                modifier = Modifier.align(Alignment.Center)
            )
            summarySentences == null -> {
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Misma insignia circular con degradado de acento que el
                    // estado "sin documento" de arriba -- antes este estado
                    // usaba un ícono plano más chico, sin relación visual con
                    // el resto de la pantalla.
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .background(
                                brush = Brush.linearGradient(rememberAccentGradient()),
                                shape = MaterialTheme.shapes.extraLarge
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Summarize,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                    Text(
                        text  = stringResource(R.string.study_summary_local_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Button(
                        onClick = onGenerate,
                        shape   = MaterialTheme.shapes.medium,
                        enabled = documentText.isNotEmpty()
                    ) {
                        Icon(Icons.Rounded.Summarize, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.study_generate_summary))
                    }
                }
            }
            else -> SummaryResultView(
                sentences        = summarySentences,
                savedToDownloads = savedToDownloads,
                onSave = {
                    scope.launch {
                        savedToDownloads = saveSummaryToDownloads(context, documentName, summarySentences)
                    }
                },
                onShare = { shareSummary(context, documentName, summarySentences, shareTitle) }
            )
        }
    }
}

// Extraído de SummaryTab (LongMethod por detekt) -- la lista de oraciones
// generadas + la fila de guardar/compartir, una vez que ya hay resumen.
@Composable
private fun SummaryResultView(
    sentences       : List<String>,
    savedToDownloads: Boolean,
    onSave          : () -> Unit,
    onShare         : () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector        = Icons.Rounded.Summarize,
                    contentDescription = null,
                    tint               = MaterialTheme.colorScheme.primary,
                    modifier           = Modifier.size(18.dp)
                )
                Text(
                    text  = stringResource(R.string.study_summary_sentences_count, sentences.size),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = onSave) {
                Icon(
                    imageVector = if (savedToDownloads) Icons.Rounded.CheckCircle else Icons.Rounded.Download,
                    contentDescription = stringResource(R.string.general_save),
                    tint = if (savedToDownloads) SuccessGreen else MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = onShare) {
                Icon(
                    imageVector = Icons.Rounded.Share,
                    contentDescription = stringResource(R.string.general_share),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Cada punto clave en su propia tarjeta numerada -- antes era
            // solo un punto (bullet) suelto sobre el fondo, sin la misma
            // jerarquía de tarjeta que el resto de Modo Estudio (notas,
            // historial de lectura).
            itemsIndexed(sentences) { index, sentence ->
                val shape = MaterialTheme.shapes.medium
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .accentShadow(shape = shape, elevation = 1.dp)
                        .clip(shape)
                        .background(MaterialTheme.colorScheme.surface)
                        .accentBorder(shape = shape)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                    MaterialTheme.shapes.extraLarge
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text  = "${index + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text  = sentence,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Suppress("TooGenericExceptionCaught")
private fun shareSummary(context: Context, documentName: String, sentences: List<String>, shareTitle: String) {
    try {
        val file = StudySummaryExporter.exportAsTextFile(context, documentName, sentences)
        val uri  = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(android.content.Intent.createChooser(intent, shareTitle))
    } catch (e: Exception) {
        Timber.e(e, "Error compartiendo resumen de estudio")
    }
}

@Suppress("TooGenericExceptionCaught")
private suspend fun saveSummaryToDownloads(context: Context, documentName: String, sentences: List<String>): Boolean {
    return try {
        val file = StudySummaryExporter.exportAsTextFile(context, documentName, sentences)
        DownloadsSaver.saveFile(context, file, "text/plain")
    } catch (e: Exception) {
        Timber.e(e, "Error guardando resumen en Descargas")
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
    val couldNotRead        : String,
    val pdfNoText           : String,
    val pdfErrorTemplate    : String, // formato: %1$s
    val genericErrorTemplate: String, // formato: %1$s
    val defaultDocumentName : String
)

data class StudyExtractionResult(
    val paragraphs    : List<String>,
    val fileName      : String,
    // "Retomar lectura" (2026-09-08): cuántos párrafos acumulados hay al
    // terminar cada página del PDF -- permite traducir el índice de párrafo
    // que va leyendo la voz a un número de página real (`pageForParagraph`).
    val pageBoundaries: List<Int> = emptyList()
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
    onPageExtracted: suspend (paragraphs: List<String>, pageBoundaries: List<Int>) -> Unit = { _, _ -> }
): StudyExtractionResult = withContext(Dispatchers.IO) {
    try {
        val fileName = resolveFileName(context, uri, messages)
        val (paragraphs, pageBoundaries) = extractPdfText(context, uri, messages, onPageExtracted)
        StudyExtractionResult(paragraphs, fileName, pageBoundaries)
    } catch (e: Exception) {
        Timber.e(e, "Error extrayendo texto")
        StudyExtractionResult(emptyList(), String.format(messages.genericErrorTemplate, e.message ?: ""))
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
private suspend fun extractPdfText(
    context : Context,
    uri     : Uri,
    messages: StudyExtractionMessages,
    onPageExtracted: suspend (paragraphs: List<String>, pageBoundaries: List<Int>) -> Unit = { _, _ -> }
): Pair<List<String>, List<Int>> {
    return try {
        val cacheFile = File.createTempFile("study_temp", ".pdf", context.cacheDir)
        context.contentResolver.openInputStream(uri)?.use { input ->
            cacheFile.outputStream().use { output -> input.copyTo(output) }
        } ?: return listOf(messages.couldNotRead) to emptyList()

        val pdfDoc = PdfDocument(PdfReader(cacheFile))

        val paragraphs = mutableListOf<String>()
        val pageBoundaries = mutableListOf<Int>()
        for (i in 1..pdfDoc.numberOfPages) {
            val listener = StudyPdfLineListener()
            PdfCanvasProcessor(listener).processPageContent(pdfDoc.getPage(i))
            paragraphs.addAll(groupPdfChunksIntoParagraphs(listener.chunks))
            pageBoundaries.add(paragraphs.size)
            if (paragraphs.isNotEmpty()) onPageExtracted(paragraphs.toList(), pageBoundaries.toList())
        }
        pdfDoc.close()
        cacheFile.delete()

        if (paragraphs.isEmpty()) listOf(messages.pdfNoText) to emptyList() else paragraphs to pageBoundaries
    } catch (e: Exception) {
        Timber.e(e, "Error extrayendo texto PDF")
        listOf(String.format(messages.pdfErrorTemplate, e.message ?: "")) to emptyList()
    }
}

internal data class StudyPdfChunk(val text: String, val y: Float, val fontSize: Float)

private class StudyPdfLineListener : IEventListener {
    val chunks = mutableListOf<StudyPdfChunk>()

    override fun eventOccurred(data: IEventData?, type: EventType) {
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
        val isNewParagraph = previousY != null && !sameLine &&
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
    return paragraphs.map { it.toString().trim() }.filter { it.length > 5 }
}

private fun resolveFileName(context: Context, uri: Uri, messages: StudyExtractionMessages): String {
    return try {
        var name = messages.defaultDocumentName
        context.contentResolver.query(
            uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
            null, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) name = cursor.getString(0) ?: name
        }
        name
    } catch (e: Exception) { messages.defaultDocumentName }
}