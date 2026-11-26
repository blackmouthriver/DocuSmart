package com.docsmart.features.utilities.presentation

import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.docsmart.R
import com.docsmart.core.ui.theme.ErrorRed
import com.docsmart.core.ui.theme.SuccessGreen
import com.docsmart.core.util.DownloadsSaver
import com.docsmart.features.utilities.domain.HashAlgorithm
import com.docsmart.features.utilities.domain.HashResult
import com.docsmart.features.utilities.domain.HashTool
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun HashScreen(
    onBack: () -> Unit = {},
    onHome: (() -> Unit)? = null,
    viewModel: HashViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val onPicked = viewModel::onFilesPicked
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments(), onPicked)

    UtilityScaffold(
        title = stringResource(R.string.util_hash),
        subtitle = stringResource(R.string.util_hash_desc),
        onBack = onBack,
        onHome = onHome,
    ) {
        UtilityCard {
            Button(onClick = { picker.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(text = stringResource(R.string.util_pick_files), modifier = Modifier.padding(start = 8.dp))
            }
            Text(
                text = stringResource(R.string.hash_algorithm),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HashAlgorithm.entries.forEach { algorithm ->
                    FilterChip(
                        selected = state.algorithm == algorithm,
                        onClick = { viewModel.setAlgorithm(algorithm) },
                        label = { Text(algorithm.label) },
                    )
                }
            }
            if (state.algorithm == HashAlgorithm.MD5 || state.algorithm == HashAlgorithm.SHA1) {
                Text(
                    text = stringResource(R.string.hash_weak_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (state.running) {
            UtilityCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
                    Text(stringResource(R.string.hash_computing, state.current, state.files.size))
                }
            }
        }
        if (state.unreadable) {
            Text(
                text = stringResource(R.string.util_file_unreadable),
                color = ErrorRed,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (state.results.isNotEmpty()) {
            state.results.forEach { result -> HashResultCard(result) }
            ChecksumActions(state.results, state.algorithm)
            CompareCard(state.expected, state.results, state.algorithm, viewModel::setExpected)
            TextButton(onClick = viewModel::clear) { Text(stringResource(R.string.util_clear)) }
        }
    }
}

@Composable
private fun HashResultCard(result: HashResult) {
    val context = LocalContext.current
    UtilityCard {
        Text(text = result.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        if (result.size >= 0) {
            Text(
                text = Formatter.formatShortFileSize(context, result.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(text = result.hash, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = { copyText(context, result.hash) }) {
            Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(text = stringResource(R.string.util_copy), modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun ChecksumActions(
    results: List<HashResult>,
    algorithm: HashAlgorithm,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val text = HashTool.checksumText(results)
    val shareTitle = stringResource(R.string.util_share)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { copyText(context, text) }) { Text(stringResource(R.string.hash_copy_list)) }
        OutlinedButton(onClick = { shareText(context, text, shareTitle) }) {
            Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(text = stringResource(R.string.util_share), modifier = Modifier.padding(start = 8.dp))
        }
        OutlinedButton(
            onClick = {
                scope.launch {
                    val name = HashTool.checksumFileName(algorithm)
                    val file = File(context.cacheDir, name).apply { writeText(text) }
                    val ok = DownloadsSaver.saveFile(context, file, "text/plain", name)
                    file.delete()
                    val message = if (ok) R.string.util_saved_downloads else R.string.util_save_failed
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            },
        ) {
            Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(text = stringResource(R.string.hash_save_list), modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun CompareCard(
    expected: String,
    results: List<HashResult>,
    algorithm: HashAlgorithm,
    onExpected: (String) -> Unit,
) {
    UtilityCard {
        Text(
            text = stringResource(R.string.hash_compare_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        OutlinedTextField(
            value = expected,
            onValueChange = onExpected,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.hash_compare_hint)) },
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            minLines = 2,
        )
        if (HashTool.normalize(expected).isNotEmpty()) {
            val detected = HashTool.detectAlgorithm(expected)
            if (detected != null && detected != algorithm) {
                Text(stringResource(R.string.hash_detected, detected.label), style = MaterialTheme.typography.bodySmall)
            }
            val matched = results.any { HashTool.matches(it.hash, expected) }
            Text(
                text = stringResource(if (matched) R.string.hash_match else R.string.hash_mismatch),
                color = if (matched) SuccessGreen else ErrorRed,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
