package com.docsmart.features.utilities.presentation

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.docsmart.R
import com.docsmart.features.utilities.domain.CaseMode
import com.docsmart.features.utilities.domain.RenameBatch
import com.docsmart.features.utilities.domain.RenameOptions

private const val MAX_PREVIEW = 100

@Composable
fun RenameFilesScreen(
    onBack: () -> Unit = {},
    onHome: (() -> Unit)? = null,
    viewModel: RenameViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val onPicked = viewModel::onFilesPicked
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments(), onPicked)

    LaunchedEffect(state.messageRes) {
        state.messageRes?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.consumeMessage()
        }
    }

    UtilityScaffold(
        title = stringResource(R.string.util_rename),
        subtitle = stringResource(R.string.util_rename_desc),
        onBack = onBack,
        onHome = onHome,
    ) {
        UtilityCard {
            Button(onClick = { picker.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(text = stringResource(R.string.util_pick_files), modifier = Modifier.padding(start = 8.dp))
            }
            if (state.files.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.rn_files_count, state.files.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (state.files.isNotEmpty()) {
            OptionsCard(state.options, viewModel::setOptions)
            PreviewCard(state, viewModel)
        }
    }
}

@Composable
private fun OptionsCard(
    options: RenameOptions,
    onChange: (RenameOptions) -> Unit,
) {
    UtilityCard {
        OptionField(R.string.rn_new_name, options.newName) { onChange(options.copy(newName = it)) }
        OptionField(R.string.rn_find, options.find) { onChange(options.copy(find = it)) }
        OptionField(R.string.rn_replace, options.replace) { onChange(options.copy(replace = it)) }
        OptionField(R.string.rn_prefix, options.prefix) { onChange(options.copy(prefix = it)) }
        OptionField(R.string.rn_suffix, options.suffix) { onChange(options.copy(suffix = it)) }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(text = stringResource(R.string.rn_numbering), modifier = Modifier.weight(1f))
            Switch(checked = options.numbering, onCheckedChange = { onChange(options.copy(numbering = it)) })
        }
        if (options.numbering) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val half = Modifier.weight(1f)
                NumberField(R.string.rn_start, options.start, half) { onChange(options.copy(start = it)) }
                NumberField(R.string.rn_digits, options.digits, half) { onChange(options.copy(digits = it)) }
            }
        }
        SectionTitle(R.string.rn_case)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                CaseMode.KEEP to R.string.rn_case_keep,
                CaseMode.LOWER to R.string.rn_case_lower,
                CaseMode.UPPER to R.string.rn_case_upper,
            ).forEach { (mode, labelRes) ->
                FilterChip(
                    selected = options.caseMode == mode,
                    onClick = { onChange(options.copy(caseMode = mode)) },
                    label = { Text(stringResource(labelRes)) },
                )
            }
        }
    }
}

@Composable
private fun OptionField(
    labelRes: Int,
    value: String,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(labelRes)) },
        singleLine = true,
    )
}

@Composable
private fun NumberField(
    labelRes: Int,
    value: Int,
    modifier: Modifier,
    onChange: (Int) -> Unit,
) {
    OutlinedTextField(
        value = value.toString(),
        onValueChange = { text -> onChange(text.filter { it.isDigit() }.take(NUMBER_MAX_CHARS).toIntOrNull() ?: 0) },
        modifier = modifier,
        label = { Text(stringResource(labelRes)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}

private const val NUMBER_MAX_CHARS = 6

@Composable
private fun PreviewCard(
    state: RenameUiState,
    viewModel: RenameViewModel,
) {
    val newNames = state.newNames
    val changed = RenameBatch.changesAnything(state.files.map { it.name }, state.options)
    UtilityCard {
        SectionTitle(R.string.rn_preview)
        state.files.zip(newNames).take(MAX_PREVIEW).forEach { (file, name) ->
            Text(
                text = file.name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(text = name, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (!changed) {
            Text(stringResource(R.string.rn_no_changes), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            text = stringResource(R.string.rn_zip_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = viewModel::saveZip, enabled = changed && !state.working, modifier = Modifier.fillMaxWidth()) {
            if (state.working) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Text(text = stringResource(R.string.rn_working), modifier = Modifier.padding(start = 8.dp))
            } else {
                Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(text = stringResource(R.string.rn_save_zip), modifier = Modifier.padding(start = 8.dp))
            }
        }
        TextButton(onClick = viewModel::clear) { Text(stringResource(R.string.util_clear)) }
    }
}
