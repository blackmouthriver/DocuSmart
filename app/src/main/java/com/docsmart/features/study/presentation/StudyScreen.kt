package com.docsmart.features.study.presentation

import android.content.Context
import android.net.Uri
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
import com.docsmart.R
import com.docsmart.features.study.domain.SavedNote
import com.docsmart.features.study.domain.StudyNotesStorage
import com.docsmart.core.ui.theme.DocuBlue
import com.docsmart.core.ui.theme.IndigoAccent
import com.docsmart.core.ui.theme.SmartBlue
import com.docsmart.core.ui.theme.SuccessGreen
import com.docsmart.core.ui.theme.WarningAmber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.util.Locale
import java.util.zip.ZipInputStream

@Composable
fun StudyScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ── Estado general ────────────────────────────────
    var selectedTab by remember { mutableIntStateOf(0) }
    var documentText by remember { mutableStateOf<List<String>>(emptyList()) }
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

    // ── Pomodoro ──────────────────────────────────────
    var pomodoroMinutes by remember { mutableIntStateOf(25) }
    var pomodoroSeconds by remember { mutableIntStateOf(0) }
    var isRunning by remember { mutableStateOf(false) }
    var isBreak by remember { mutableStateOf(false) }
    var pomodoroCount by remember { mutableIntStateOf(0) }

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



    // ── Timer Pomodoro ────────────────────────────────
    LaunchedEffect(isRunning) {
        while (isRunning) {
            delay(1000)
            if (pomodoroSeconds > 0) {
                pomodoroSeconds--
            } else if (pomodoroMinutes > 0) {
                pomodoroMinutes--
                pomodoroSeconds = 59
            } else {
                // Tiempo terminado
                isRunning = false
                if (!isBreak) {
                    pomodoroCount++
                    isBreak = true
                    pomodoroMinutes = 5
                    pomodoroSeconds = 0
                } else {
                    isBreak = false
                    pomodoroMinutes = 25
                    pomodoroSeconds = 0
                }
            }
        }
    }

    // ── Selector de documento ─────────────────────────
    val docLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            isLoadingDoc = true
            scope.launch {
                val result = extractTextFromUri(context, uri, extractionMessages)
                documentText = result.first
                documentName = result.second
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
                onSelectDoc = { docLauncher.launch("*/*") }
            )
        }
    ) { innerPadding ->
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

            when (selectedTab) {
                // ── Tab Lectura ───────────────────────
                0 -> ReadingTab(
                    documentText = documentText,
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
                    minutes = pomodoroMinutes,
                    seconds = pomodoroSeconds,
                    isRunning = isRunning,
                    isBreak = isBreak,
                    pomodoroCount = pomodoroCount,
                    onToggle = { isRunning = !isRunning },
                    onReset = {
                        isRunning = false
                        isBreak = false
                        pomodoroMinutes = 25
                        pomodoroSeconds = 0
                    }
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
    onSelectDoc: () -> Unit
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
                                brush = Brush.linearGradient(
                                    listOf(DocuBlue, IndigoAccent)
                                ),
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
                            // ── Párrafo como texto fluido ─
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        when {
                                            currentSpeakingIndex == index ->
                                                DocuBlue.copy(alpha = 0.08f)
                                            highlights.contains(index) ->
                                                WarningAmber.copy(alpha = 0.1f)
                                            else -> Color.Transparent
                                        }
                                    )
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                // ── Texto ─────────────────
                                Text(
                                    text = paragraph,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontSize = 16.sp,
                                    lineHeight = 26.sp,
                                    color = when {
                                        currentSpeakingIndex == index -> DocuBlue
                                        else -> MaterialTheme.colorScheme.onSurface
                                    },
                                    modifier = Modifier.weight(1f)
                                )

                                // ── Botones acción ────────
                                Column {
                                    // Resaltar
                                    IconButton(
                                        onClick = { onToggleHighlight(index) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (highlights.contains(index))
                                                Icons.Rounded.Bookmark
                                            else
                                                Icons.Rounded.BookmarkBorder,
                                            contentDescription = null,
                                            tint = if (highlights.contains(index))
                                                WarningAmber
                                            else
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                                    .copy(alpha = 0.4f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    // Leer párrafo
                                    IconButton(
                                        onClick = { onSpeak(paragraph, index) },
                                        modifier = Modifier.size(28.dp),
                                        enabled = ttsReady
                                    ) {
                                        Icon(
                                            imageVector = if (currentSpeakingIndex == index)
                                                Icons.Rounded.VolumeOff
                                            else
                                                Icons.Rounded.VolumeUp,
                                            contentDescription = null,
                                            tint = if (currentSpeakingIndex == index)
                                                DocuBlue
                                            else
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                                    .copy(alpha = 0.4f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }

                            // ── Separador sutil ───────────
                            if (index < documentText.size - 1) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant
                                        .copy(alpha = 0.3f),
                                    thickness = 0.5.dp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Párrafo individual ────────────────────────────────
@Composable
private fun ParagraphItem(
    text: String,
    index: Int,
    isHighlighted: Boolean,
    isSpeaking: Boolean,
    onToggleHighlight: () -> Unit,
    onSpeak: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = when {
                isSpeaking -> DocuBlue.copy(alpha = 0.15f)
                isHighlighted -> WarningAmber.copy(alpha = 0.15f)
                else -> MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(
            if (isHighlighted || isSpeaking) 4.dp else 1.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top
        ) {
            // ── Número de párrafo ─────────────────────
            Text(
                text = "${index + 1}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(20.dp)
            )

            // ── Texto ─────────────────────────────────
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isSpeaking) DocuBlue
                else MaterialTheme.colorScheme.onSurface,
                lineHeight = 22.sp,
                modifier = Modifier.weight(1f)
            )

            // ── Acciones ──────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // Resaltar
                IconButton(
                    onClick = onToggleHighlight,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = if (isHighlighted)
                            Icons.Rounded.Bookmark
                        else
                            Icons.Rounded.BookmarkBorder,
                        contentDescription = "Resaltar",
                        tint = if (isHighlighted) WarningAmber
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
                // Leer párrafo
                IconButton(
                    onClick = onSpeak,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = if (isSpeaking)
                            Icons.Rounded.VolumeOff
                        else
                            Icons.Rounded.VolumeUp,
                        contentDescription = "Leer",
                        tint = if (isSpeaking) DocuBlue
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
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
                TextButton(onClick = { showDeleteAll = true }) {
                    Text(
                        text  = stringResource(R.string.study_delete_all),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
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

        // ── Indicador tipo ────────────────────────────
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

        // ── Reloj circular ────────────────────────────
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
                    text = String.format("%02d:%02d", minutes, seconds),
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

        // ── Botones control ───────────────────────────
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

        // ── Contador de pomodoros ─────────────────────
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

        // ── Info técnica ──────────────────────────────
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

// ── Extraer texto de documento ────────────────────────
private suspend fun extractTextFromUri(
    context: Context,
    uri: Uri,
    messages: StudyExtractionMessages
): Pair<List<String>, String> = withContext(Dispatchers.IO) {
    try {
        val mimeType = context.contentResolver.getType(uri) ?: ""
        val fileName = resolveFileName(context, uri, messages)

        val text = when {
            mimeType.contains("pdf") -> extractPdfText(context, uri, messages)
            mimeType.contains("word") || mimeType.contains("msword") ||
                    mimeType.contains("wordprocessingml") -> extractWordText(context, uri, messages)
            mimeType.contains("text") -> extractPlainText(context, uri, messages)
            mimeType.contains("powerpoint") || mimeType.contains("presentation") ->
                extractPptText(context, uri, messages)
            else -> extractPlainText(context, uri, messages)
        }

        Pair(text, fileName)
    } catch (e: Exception) {
        Timber.e(e, "Error extrayendo texto")
        Pair(emptyList(), String.format(messages.genericErrorTemplate, e.message ?: ""))
    }
}

private fun extractPdfText(context: Context, uri: Uri, messages: StudyExtractionMessages): List<String> {
    return try {
        // ── Copiar al cache ───────────────────────────
        val cacheFile = File(context.cacheDir, "study_temp.pdf")
        context.contentResolver.openInputStream(uri)?.use { input ->
            cacheFile.outputStream().use { output -> input.copyTo(output) }
        } ?: return listOf(messages.couldNotRead)

        // ── Extraer texto con iText7 ──────────────────
        val paragraphs = mutableListOf<String>()
        val pdfDoc = com.itextpdf.kernel.pdf.PdfDocument(
            com.itextpdf.kernel.pdf.PdfReader(cacheFile)
        )

        for (i in 1..pdfDoc.numberOfPages) {
            val pageText = com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
                .getTextFromPage(pdfDoc.getPage(i))
                .trim()

            if (pageText.isNotBlank()) {
                // ── Dividir por párrafos ──────────────
                pageText.split("\n")
                    .map { it.trim() }
                    .filter { it.length > 5 }
                    .forEach { paragraphs.add(it) }
            }
        }

        pdfDoc.close()

        if (paragraphs.isEmpty()) {
            listOf(messages.pdfNoText)
        } else {
            paragraphs
        }
    } catch (e: Exception) {
        Timber.e(e, "Error extrayendo texto PDF")
        listOf(String.format(messages.pdfErrorTemplate, e.message ?: ""))
    }
}

private fun extractWordText(context: Context, uri: Uri, messages: StudyExtractionMessages): List<String> {
    return try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val zip = ZipInputStream(input)
            var entry = zip.nextEntry
            val result = mutableListOf<String>()
            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    val content = zip.readBytes().toString(Charsets.UTF_8)
                    content.replace(Regex("<w:p[ >]"), "\n")
                        .replace(Regex("<[^>]+>"), "")
                        .replace("&amp;", "&")
                        .split("\n")
                        .map { it.trim() }
                        .filter { it.length > 3 }
                        .also { result.addAll(it) }
                    break
                }
                entry = zip.nextEntry
            }
            zip.close()
            result.ifEmpty { listOf(messages.noTextFound) }
        } ?: listOf(messages.couldNotOpen)
    } catch (e: Exception) {
        listOf(String.format(messages.genericErrorTemplate, e.message ?: ""))
    }
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