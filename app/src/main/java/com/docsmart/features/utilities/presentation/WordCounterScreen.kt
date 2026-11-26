package com.docsmart.features.utilities.presentation

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.docsmart.R
import com.docsmart.core.ui.theme.ErrorRed
import com.docsmart.features.utilities.domain.TextCounter
import com.docsmart.features.utilities.domain.TextStats
import com.docsmart.features.utilities.domain.WordCount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

private const val DEBOUNCE_MS = 150L
private const val SECONDS_PER_MINUTE = 60

private class CharLimit(
    val labelRes: Int,
    val max: Int,
)

// Los límites que más se consultan al escribir (igual que la web).
private val CHAR_LIMITS =
    listOf(
        CharLimit(R.string.wc_limit_x, 280),
        CharLimit(R.string.wc_limit_sms, 160),
        CharLimit(R.string.wc_limit_seo_title, 60),
        CharLimit(R.string.wc_limit_seo_desc, 160),
        CharLimit(R.string.wc_limit_instagram, 2200),
    )

private data class CounterResult(
    val stats: TextStats,
    val top: List<WordCount>,
)

@Composable
fun WordCounterScreen(
    onBack: () -> Unit = {},
    onHome: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val locale: Locale = LocalConfiguration.current.locales[0]
    var text by rememberSaveable { mutableStateOf("") }
    val result by produceState(CounterResult(TextCounter.stats("", locale), emptyList()), text, locale) {
        // Con textos largos no se recalcula en cada tecla: se espera un instante y se hace fuera del hilo principal.
        delay(DEBOUNCE_MS)
        value =
            withContext(Dispatchers.Default) {
                CounterResult(
                    TextCounter.stats(text, locale),
                    TextCounter.topWords(text, locale, minLength = TextCounter.minWordLength(locale.language)),
                )
            }
    }

    UtilityScaffold(
        title = stringResource(R.string.util_counter),
        subtitle = stringResource(R.string.util_counter_desc),
        onBack = onBack,
        onHome = onHome,
    ) {
        UtilityCard {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
                placeholder = { Text(stringResource(R.string.wc_hint)) },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { pasteInto(context)?.let { text = it } }) {
                    Icon(Icons.Rounded.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(text = stringResource(R.string.util_paste), modifier = Modifier.padding(start = 8.dp))
                }
                if (text.isNotEmpty()) TextButton(onClick = { text = "" }) { Text(stringResource(R.string.util_clear)) }
            }
        }
        StatsCard(result.stats)
        if (result.top.isNotEmpty()) TopWordsCard(result.top)
        if (text.isNotEmpty()) LimitsCard(result.stats.characters)
    }
}

private fun pasteInto(context: Context): String? {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    return clipboard.primaryClip
        ?.takeIf { it.itemCount > 0 }
        ?.getItemAt(0)
        ?.coerceToText(context)
        ?.toString()
}

@Composable
private fun StatsCard(stats: TextStats) {
    val reading = durationText(stats.readingMinutes)
    val speaking = durationText(stats.speakingMinutes)
    val tiles =
        listOf(
            R.string.wc_words to stats.words.toString(),
            R.string.wc_characters to stats.characters.toString(),
            R.string.wc_characters_no_spaces to stats.charactersNoSpaces.toString(),
            R.string.wc_sentences to stats.sentences.toString(),
            R.string.wc_paragraphs to stats.paragraphs.toString(),
            R.string.wc_lines to stats.lines.toString(),
            R.string.wc_reading_time to reading,
            R.string.wc_speaking_time to speaking,
        )
    UtilityCard {
        tiles.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { (labelRes, value) ->
                    StatTile(stringResource(labelRes), value, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun StatTile(
    label: String,
    value: String,
    modifier: Modifier,
) {
    androidx.compose.foundation.layout.Column(modifier) {
        Text(text = value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun durationText(minutes: Double): String {
    val total = (minutes * SECONDS_PER_MINUTE).roundToInt()
    val min = total / SECONDS_PER_MINUTE
    val sec = total % SECONDS_PER_MINUTE
    return if (min > 0) {
        stringResource(R.string.wc_duration_min_sec, min, sec)
    } else {
        stringResource(R.string.wc_duration_sec, sec)
    }
}

@Composable
private fun TopWordsCard(top: List<WordCount>) {
    UtilityCard {
        SectionTitle(R.string.wc_top_words)
        top.forEach { entry ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(entry.word)
                Text(
                    text = "${entry.count}  (${entry.percent}%)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LimitsCard(characters: Int) {
    UtilityCard {
        SectionTitle(R.string.wc_limits)
        CHAR_LIMITS.forEach { limit ->
            val over = characters > limit.max
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = stringResource(limit.labelRes), modifier = Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.wc_limit_used, characters, limit.max),
                    color = if (over) ErrorRed else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (over) FontWeight.Bold else FontWeight.Normal,
                )
            }
            LinearProgressIndicator(
                progress = { (characters.toFloat() / limit.max).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                color = if (over) ErrorRed else MaterialTheme.colorScheme.primary,
            )
        }
    }
}
