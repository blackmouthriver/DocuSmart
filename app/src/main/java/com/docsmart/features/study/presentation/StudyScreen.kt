package com.docsmart.features.study.presentation

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.docsmart.R
import com.docsmart.core.ads.AdConstants
import com.docsmart.core.ads.DocuSmartBannerAd
import com.docsmart.core.analytics.DocuSmartAnalytics
import com.docsmart.features.study.domain.PomodoroEngine
import com.docsmart.features.study.domain.SavedNote
import com.docsmart.features.study.domain.StudyNotesExporter
import com.docsmart.features.study.domain.StudyNotesStorage
import com.docsmart.features.study.domain.StudyStats
import com.docsmart.features.study.domain.StudyStatsStorage
import com.docsmart.features.study.domain.millisToHoursAndMinutes
import com.docsmart.features.study.domain.pomodoroCountsByWeekday
import com.docsmart.core.ui.theme.DocuBlue
import com.docsmart.core.ui.theme.SuccessGreen
import com.docsmart.core.ui.theme.WarningAmber
import com.docsmart.core.ui.theme.rememberAccentGradient
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
import java.util.zip.ZipInputStream

@Composable
fun StudyScreen(
    onBack: () -> Unit = {},
    initialTab: Int = 0,
    viewModel: StudyViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isPremium by viewModel.adManager.isPremium.collectAsStateWithLifecycle()

    // ── Estado general ────────────────────────────────
    var selectedTab by remember { mutableIntStateOf(initialTab.coerceIn(0, 2)) }
    var documentText by remember { mutableStateOf<List<String>>(emptyList()) }
    var documentHeadingIndices by remember { mutableStateOf<Set<Int>>(emptySet()) }
    val noDocumentLabel = stringResource(R.string.study_no_document)
    var documentName by remember { mutableStateOf(noDocumentLabel) }

    val extractionMessages = StudyExtractionMessages(
        noTextFound          = stringResource(R.string.study_extract_no_text_found),
        couldNotOpen          = stringResource(R.string.study_extract_could_not_open),
        couldNotRead          = stringResource(R.string.study_extract_could_not_read),
        pdfNoText             = stringResource(R.string.study_extract_pdf_no_text),
        pdfErrorTemplate       = stringResource(R.string.study_extract_pdf_error),
        genericErrorTemplate   = stringResource(R.string.study_extract_generic_error),
        defaultDocumentName    = stringResource(R.string.study_default_document_name)
    )
    var notes by remember { mutableStateOf("") }
    var highlights by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var isLoadingDoc by remember { mutableStateOf(false) }

    // ── TTS ───────────────────────────────────────────
    val ttsRef = remember { mutableStateOf<TextToSpeech?>(null) }
    val isSpeaking = remember { mutableStateOf(false) }
    val ttsReady = remember { mutableStateOf(false) }
    val currentSpeakingIndex = remember { mutableIntStateOf(-1) }

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
                ttsRef.value = ttsInstance
                ttsReady.value = true
                Timber.d("TTS listo")
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

    // ── Selector de documento ─────────────────────────
    val docLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            isLoadingDoc = true
            scope.launch {
                val result = extractTextFromUri(context, uri, extractionMessages)
                documentText = result.paragraphs
                documentHeadingIndices = result.headingIndices
                documentName = result.fileName
                isLoadingDoc = false
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            StudyTopBar(
                documentName = documentName,
                onBack = {
                    ttsRef.value?.stop()
                    isSpeaking.value = false
                    onBack()
                },
                onSelectDoc = { docLauncher.launch("*/*") },
                onShowStats = { showStats = true }
            )
        }
    ) { innerPadding ->
        if (showStats) {
            StudyStatsDialog(
                stats = remember(showStats) { StudyStatsStorage.loadStats(context) },
                onDismiss = { showStats = false }
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // ── Tabs ──────────────────────────────────
            val tabs = listOf(
                stringResource(R.string.study_tab_reading),
                stringResource(R.string.study_tab_notes),
                stringResource(R.string.study_tab_pomodoro)
            )
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                text = title,
                                fontWeight = if (selectedTab == index)
                                    FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            // ── AdMob — solo para usuarios free (backlog UX §8), visible
            // en las 3 pestañas por igual, no solo en una ────────────────
            if (!isPremium) {
                DocuSmartBannerAd(
                    adUnitId  = AdConstants.BANNER_STUDY_ID,
                    adManager = viewModel.adManager,
                    modifier  = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }

            when (selectedTab) {
                // ── Tab Lectura ───────────────────────
                0 -> ReadingTab(
                    documentText = documentText,
                    headingIndices = documentHeadingIndices,
                    isLoading = isLoadingDoc,
                    highlights = highlights,
                    isSpeaking = isSpeaking.value,
                    ttsReady = ttsReady.value,
                    currentSpeakingIndex = currentSpeakingIndex.intValue,
                    onToggleHighlight = { index ->
                        highlights = if (highlights.contains(index)) {
                            highlights - index
                        } else {
                            highlights + index
                        }
                    },
                    onSpeak = { text, index ->
                        if (isSpeaking.value) {
                            ttsRef.value?.stop()
                            isSpeaking.value = false
                            currentSpeakingIndex.intValue = -1
                        } else {
                            ttsRef.value?.setOnUtteranceProgressListener(
                                object : UtteranceProgressListener() {
                                    override fun onStart(utteranceId: String?) {
                                        isSpeaking.value = true
                                        currentSpeakingIndex.intValue = index
                                    }
                                    override fun onDone(utteranceId: String?) {
                                        isSpeaking.value = false
                                        currentSpeakingIndex.intValue = -1
                                    }
                                    override fun onError(utteranceId: String?) {
                                        isSpeaking.value = false
                                        currentSpeakingIndex.intValue = -1
                                    }
                                }
                            )
                            ttsRef.value?.speak(
                                text,
                                TextToSpeech.QUEUE_FLUSH,
                                null,
                                "study_${System.currentTimeMillis()}"
                            )
                        }
                    },
                    onSpeakAll = {
                        if (isSpeaking.value) {
                            ttsRef.value?.stop()
                            isSpeaking.value = false
                            currentSpeakingIndex.intValue = -1
                        } else {
                            val currentTts = ttsRef.value
                            if (currentTts == null || !ttsReady.value) {
                                Timber.e("TTS no está listo")
                                return@ReadingTab
                            }
                            val fullText = documentText.joinToString(". ")
                            if (fullText.isBlank()) return@ReadingTab

                            currentTts.setOnUtteranceProgressListener(
                                object : UtteranceProgressListener() {
                                    override fun onStart(utteranceId: String?) {
                                        isSpeaking.value = true
                                    }
                                    override fun onDone(utteranceId: String?) {
                                        isSpeaking.value = false
                                        currentSpeakingIndex.intValue = -1
                                    }
                                    override fun onError(utteranceId: String?) {
                                        isSpeaking.value = false
                                    }
                                }
                            )
                            val result = currentTts.speak(
                                fullText,
                                TextToSpeech.QUEUE_FLUSH,
                                null,
                                "study_all_${System.currentTimeMillis()}"
                            )
                            Timber.d("TTS resultado: $result")
                            if (result == TextToSpeech.SUCCESS) {
                                isSpeaking.value = true
                            }
                        }
                    },
                    onSelectDoc = { docLauncher.launch("*/*") }
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
            }
        }
    }
}

// ── Top Bar ───────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StudyTopBar(
    documentName: String,
    onBack: () -> Unit,
    onSelectDoc: () -> Unit,
    onShowStats: () -> Unit
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = stringResource(R.string.study_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = documentName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = stringResource(R.string.general_back))
            }
        },
        actions = {
            IconButton(onClick = onShowStats) {
                Icon(
                    imageVector = Icons.Rounded.QueryStats,
                    contentDescription = stringResource(R.string.study_stats_icon_desc),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = onSelectDoc) {
                Icon(
                    imageVector = Icons.Rounded.FolderOpen,
                    contentDescription = stringResource(R.string.qr_open_document),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
}

// ── Tab de Lectura ────────────────────────────────────
@Composable
private fun ReadingTab(
    documentText: List<String>,
    headingIndices: Set<Int> = emptySet(),
    isLoading: Boolean,
    highlights: Set<Int>,
    isSpeaking: Boolean,
    ttsReady: Boolean,
    currentSpeakingIndex: Int,
    onToggleHighlight: (Int) -> Unit,
    onSpeak: (String, Int) -> Unit,
    onSpeakAll: () -> Unit,
    onSelectDoc: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        when {
            isLoading -> CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.primary
            )
            documentText.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .background(
                                // Bug real corregido 2026-09-04 (backlog UX
                                // §7, HU-UX-06): fijo en tonos de azul,
                                // ignorando el "Color de acento" elegido en
                                // Ajustes.
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
            else -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    // ── Barra TTS ─────────────────────
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = if (isSpeaking)
                            DocuBlue.copy(alpha = 0.1f)
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
                                text = if (isSpeaking)
                                    stringResource(R.string.study_reading_document)
                                else
                                    stringResource(
                                        R.string.study_paragraphs_highlighted_count,
                                        documentText.size, highlights.size
                                    ),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
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
                                Text(if (isSpeaking) stringResource(R.string.study_stop) else stringResource(R.string.study_read_all))
                            }
                        }
                    }

                    // ── Texto fluido con resaltado ────
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            horizontal = 20.dp,
                            vertical = 16.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        itemsIndexed(documentText) { index, paragraph ->
                            ReadingParagraphRow(
                                paragraph = paragraph,
                                index = index,
                                isLast = index == documentText.size - 1,
                                isHeading = headingIndices.contains(index),
                                isHighlighted = highlights.contains(index),
                                isSpeakingThis = currentSpeakingIndex == index,
                                ttsReady = ttsReady,
                                onToggleHighlight = onToggleHighlight,
                                onSpeak = onSpeak
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Párrafo individual ────────────────────────────────
// Extraído de ReadingTab (antes tenía el render de cada párrafo inline,
// LongMethod por detekt) -- reemplaza además a ParagraphItem/HighlightButton/
// SpeakButton/paragraphCardColor, que quedaron como código muerto (ninguna
// función del archivo las llamaba) desde que ReadingTab pasó a dibujar el
// párrafo como texto fluido con Row en vez de una Card por párrafo.
@Composable
private fun ReadingParagraphRow(
    paragraph        : String,
    index            : Int,
    isLast           : Boolean,
    isHeading        : Boolean,
    isHighlighted    : Boolean,
    isSpeakingThis   : Boolean,
    ttsReady         : Boolean,
    onToggleHighlight: (Int) -> Unit,
    onSpeak          : (String, Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                when {
                    isSpeakingThis -> DocuBlue.copy(alpha = 0.08f)
                    isHighlighted  -> WarningAmber.copy(alpha = 0.1f)
                    else           -> Color.Transparent
                }
            )
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        // ── Texto (encabezados en negrita, igual que el Visor de Word) ─
        Text(
            text = paragraph,
            style = MaterialTheme.typography.bodyMedium,
            fontSize = if (isHeading) 18.sp else 16.sp,
            fontWeight = if (isHeading) FontWeight.Bold else FontWeight.Normal,
            lineHeight = 26.sp,
            color = when {
                isSpeakingThis -> DocuBlue
                isHeading      -> MaterialTheme.colorScheme.primary
                else           -> MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.weight(1f)
        )

        // ── Botones acción ────────
        Column {
            IconButton(
                onClick = { onToggleHighlight(index) },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = if (isHighlighted) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                    contentDescription = null,
                    tint = if (isHighlighted) WarningAmber
                           else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(16.dp)
                )
            }
            IconButton(
                onClick = { onSpeak(paragraph, index) },
                modifier = Modifier.size(28.dp),
                enabled = ttsReady
            ) {
                Icon(
                    imageVector = if (isSpeakingThis) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp,
                    contentDescription = null,
                    tint = if (isSpeakingThis) DocuBlue
                           else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }

    if (!isLast) {
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
            thickness = 0.5.dp
        )
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

    Column(modifier = Modifier.fillMaxSize()) {

        // ── Párrafos resaltados ───────────────────────────────────────────────
        if (highlights.isNotEmpty() && documentText.isNotEmpty()) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text       = stringResource(R.string.study_highlighted_paragraphs_count, highlights.size),
                    style      = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.onSurface
                )
                LazyColumn(
                    modifier            = Modifier.fillMaxWidth().heightIn(max = 140.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(highlights.sorted()) { _, index ->
                        if (index < documentText.size) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
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
                }
            }
            HorizontalDivider()
        }

        // ── Editor de nota nueva ──────────────────────────────────────────────
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text       = stringResource(R.string.study_new_note),
                style      = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.onSurface
            )

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
                    IconButton(
                        onClick  = { startVoiceInput() },
                        modifier = Modifier.size(40.dp)
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

        HorizontalDivider()

        // ── Lista de notas guardadas ──────────────────────────────────────────
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text(
                text       = if (savedNotes.isEmpty()) stringResource(R.string.study_no_saved_notes)
                else stringResource(R.string.study_saved_notes_count, savedNotes.size),
                style      = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.onSurface
            )
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

        if (savedNotes.isEmpty()) {
            Column(
                modifier            = Modifier.fillMaxWidth().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector        = Icons.Rounded.NoteAlt,
                    contentDescription = null,
                    modifier           = Modifier.size(48.dp),
                    tint               = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
                Text(
                    text      = stringResource(R.string.study_no_notes_yet),
                    style     = MaterialTheme.typography.bodyMedium,
                    color     = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier       = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(savedNotes, key = { _, note -> note.id }) { _, note ->
                    Card(
                        modifier  = Modifier.fillMaxWidth(),
                        shape     = MaterialTheme.shapes.large,
                        colors    = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(2.dp)
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
                                IconButton(
                                    onClick  = {
                                        val updated = savedNotes.filter { it.id != note.id }
                                        StudyNotesStorage.saveNotes(context, updated)
                                        savedNotes = updated
                                    },
                                    modifier = Modifier.size(28.dp)
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
            }
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(32.dp)
    ) {
        Spacer(Modifier.height(16.dp))

        PomodoroTypeIndicator(isBreak)
        PomodoroClock(minutes, seconds, isRunning, isBreak)
        PomodoroControls(isRunning, isBreak, onToggle, onReset)
        PomodoroCountCard(pomodoroCount)
        PomodoroInfoCard()
    }
}

@Composable
private fun PomodoroTypeIndicator(isBreak: Boolean) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (isBreak) SuccessGreen.copy(alpha = 0.15f)
        else DocuBlue.copy(alpha = 0.15f)
    ) {
        Text(
            text = if (isBreak) stringResource(R.string.study_break_label) else stringResource(R.string.study_study_label),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (isBreak) SuccessGreen else DocuBlue,
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
                        listOf(DocuBlue.copy(0.2f), Color.Transparent)
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
                color = if (isBreak) SuccessGreen else DocuBlue
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
                containerColor = if (isBreak) SuccessGreen else DocuBlue
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(2.dp)
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

// Mensajes localizados, resueltos en la capa de presentación (stringResource)
// y pasados hacia abajo — estas funciones de extracción no tienen Context de recursos.
data class StudyExtractionMessages(
    val noTextFound       : String,
    val couldNotOpen      : String,
    val couldNotRead      : String,
    val pdfNoText         : String,
    val pdfErrorTemplate  : String, // formato: %1$s
    val genericErrorTemplate: String, // formato: %1$s
    val defaultDocumentName: String
)

// Resultado de extracción: encabezados detectados vienen como índices dentro
// de `paragraphs` (no un tipo por-párrafo aparte) para no tocar la lógica de
// resaltado/TTS existente, que ya identifica párrafos por índice sobre
// `documentText` (ver `highlights: Set<Int>`, `currentSpeakingIndex`).
data class StudyExtractionResult(
    val paragraphs: List<String>,
    val headingIndices: Set<Int>,
    val fileName: String
)

// ── Extraer texto de documento ────────────────────────
private suspend fun extractTextFromUri(
    context: Context,
    uri: Uri,
    messages: StudyExtractionMessages
): StudyExtractionResult = withContext(Dispatchers.IO) {
    try {
        val mimeType = context.contentResolver.getType(uri) ?: ""
        val fileName = resolveFileName(context, uri, messages)

        val (paragraphs, headingIndices) = when {
            mimeType.contains("pdf") -> extractPdfText(context, uri, messages) to emptySet()
            mimeType.contains("word") || mimeType.contains("msword") ||
                    mimeType.contains("wordprocessingml") -> extractWordText(context, uri, messages)
            mimeType.contains("text") -> extractPlainText(context, uri, messages) to emptySet()
            mimeType.contains("powerpoint") || mimeType.contains("presentation") ->
                extractPptText(context, uri, messages) to emptySet()
            else -> extractPlainText(context, uri, messages) to emptySet()
        }

        StudyExtractionResult(paragraphs, headingIndices, fileName)
    } catch (e: Exception) {
        Timber.e(e, "Error extrayendo texto")
        StudyExtractionResult(emptyList(), emptySet(), String.format(messages.genericErrorTemplate, e.message ?: ""))
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
private fun extractPdfText(context: Context, uri: Uri, messages: StudyExtractionMessages): List<String> {
    return try {
        val cacheFile = File.createTempFile("study_temp", ".pdf", context.cacheDir)
        context.contentResolver.openInputStream(uri)?.use { input ->
            cacheFile.outputStream().use { output -> input.copyTo(output) }
        } ?: return listOf(messages.couldNotRead)

        val pdfDoc = PdfDocument(PdfReader(cacheFile))

        val paragraphs = mutableListOf<String>()
        for (i in 1..pdfDoc.numberOfPages) {
            val listener = StudyPdfLineListener()
            PdfCanvasProcessor(listener).processPageContent(pdfDoc.getPage(i))
            paragraphs.addAll(groupPdfChunksIntoParagraphs(listener.chunks))
        }
        pdfDoc.close()
        cacheFile.delete()

        if (paragraphs.isEmpty()) listOf(messages.pdfNoText) else paragraphs
    } catch (e: Exception) {
        Timber.e(e, "Error extrayendo texto PDF")
        listOf(String.format(messages.pdfErrorTemplate, e.message ?: ""))
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

// RF: antes convertía TODO <w:p> en un simple salto de línea antes de
// quitar etiquetas, perdiendo la información de estilo (<w:pPr>) necesaria
// para saber si un párrafo es un encabezado -- igual mejora ya aplicada al
// Visor de Word (WordViewerContent), reutilizando la misma detección de
// estilo "Heading/Title/Título/H1-H6".
private fun extractWordText(
    context: Context,
    uri: Uri,
    messages: StudyExtractionMessages
): Pair<List<String>, Set<Int>> {
    return try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val documentXml = findWordDocumentXml(input)
                ?: return@use listOf(messages.noTextFound) to emptySet()
            val (paragraphs, headingIndices) = parseWordParagraphsWithHeadings(documentXml)
            if (paragraphs.isEmpty()) listOf(messages.noTextFound) to emptySet()
            else paragraphs to headingIndices
        } ?: listOf(messages.couldNotOpen) to emptySet()
    } catch (e: Exception) {
        listOf(String.format(messages.genericErrorTemplate, e.message ?: "")) to emptySet()
    }
}

private fun findWordDocumentXml(input: java.io.InputStream): String? {
    val zip = ZipInputStream(input)
    var entry = zip.nextEntry
    while (entry != null) {
        if (entry.name == "word/document.xml") return zip.readBytes().toString(Charsets.UTF_8)
        entry = zip.nextEntry
    }
    return null
}

// Hallazgo real verificado en dispositivo con un .docx generado por Word en
// español: el identificador de estilo interno (w:pStyle w:val) NO siempre es
// en inglés como se asumía -- Word en español escribió "Ttulo1" (con tilde
// quitada), no "Heading1". Mismo criterio ya corregido en WordViewerContent
// (ver WORD_HEADING_STYLE_REGEX en ViewerScreen.kt) -- se duplica acá en vez
// de importarlo entre features para no acoplar Estudio al Visor por un
// patrón de 3 líneas.
private val STUDY_HEADING_STYLE_REGEX = Regex(
    "w:val=\"(heading|title|titulo|ttulo|berschrift|titel|заголовок|название|h[123456])",
    RegexOption.IGNORE_CASE
)

internal fun parseWordParagraphsWithHeadings(xml: String): Pair<List<String>, Set<Int>> {
    val paragraphs = mutableListOf<String>()
    val headingIndices = mutableSetOf<Int>()
    val paraRegex = Regex("<w:p[ >](.*?)</w:p>", RegexOption.DOT_MATCHES_ALL)

    paraRegex.findAll(xml).forEach { match ->
        val paraXml = match.value
        val text = paraXml
            .replace(Regex("<w:rPr>.*?</w:rPr>", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("<w:pPr>.*?</w:pPr>", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("<[^>]+>"), "")
            .replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
            .replace(Regex("\\s+"), " ").trim()
        if (text.length <= 3) return@forEach
        if (paraXml.contains(STUDY_HEADING_STYLE_REGEX)) headingIndices.add(paragraphs.size)
        paragraphs.add(text)
    }
    return paragraphs to headingIndices
}

private fun extractPptText(context: Context, uri: Uri, messages: StudyExtractionMessages): List<String> {
    return try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val zip = ZipInputStream(input)
            var entry = zip.nextEntry
            val slideMap = mutableMapOf<Int, String>()
            while (entry != null) {
                if (entry.name.startsWith("ppt/slides/slide") &&
                    entry.name.endsWith(".xml") && !entry.name.contains("_rels")) {
                    val num = entry.name.removePrefix("ppt/slides/slide")
                        .removeSuffix(".xml").toIntOrNull() ?: 0
                    val content = zip.readBytes().toString(Charsets.UTF_8)
                    val text = content.replace(Regex("<a:p[ >]"), "\n")
                        .replace(Regex("<[^>]+>"), "")
                        .split("\n").map { it.trim() }.filter { it.length > 2 }
                        .joinToString(" — ")
                    if (text.isNotBlank()) slideMap[num] = "Slide $num: $text"
                }
                entry = zip.nextEntry
            }
            zip.close()
            slideMap.toSortedMap().values.toList()
                .ifEmpty { listOf(messages.noTextFound) }
        } ?: listOf(messages.couldNotOpen)
    } catch (e: Exception) {
        listOf(String.format(messages.genericErrorTemplate, e.message ?: ""))
    }
}

private fun extractPlainText(context: Context, uri: Uri, messages: StudyExtractionMessages): List<String> {
    return try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            input.bufferedReader().readText()
                .split("\n")
                .map { it.trim() }
                .filter { it.length > 2 }
        } ?: listOf(messages.couldNotRead)
    } catch (e: Exception) {
        listOf(String.format(messages.genericErrorTemplate, e.message ?: ""))
    }
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