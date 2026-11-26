package com.docsmart.features.utilities.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.docsmart.R
import com.docsmart.core.ui.theme.ErrorRed
import com.docsmart.core.ui.theme.SuccessGreen
import com.docsmart.core.ui.theme.WarningAmber
import com.docsmart.features.utilities.domain.PasswordGenerator
import com.docsmart.features.utilities.domain.PasswordOptions
import com.docsmart.features.utilities.domain.PasswordStrength
import kotlin.math.roundToInt

@Composable
fun PasswordScreen(
    onBack: () -> Unit = {},
    onHome: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    var length by rememberSaveable { mutableIntStateOf(PasswordGenerator.DEFAULT_LENGTH) }
    var lower by rememberSaveable { mutableStateOf(true) }
    var upper by rememberSaveable { mutableStateOf(true) }
    var digits by rememberSaveable { mutableStateOf(true) }
    var symbols by rememberSaveable { mutableStateOf(true) }
    var avoidAmbiguous by rememberSaveable { mutableStateOf(false) }
    var exclude by rememberSaveable { mutableStateOf("") }
    var nonce by rememberSaveable { mutableIntStateOf(0) }
    var password by rememberSaveable { mutableStateOf("") }
    val options = PasswordOptions(length, lower, upper, digits, symbols, avoidAmbiguous, exclude)

    // Al girar la pantalla se conserva la contraseña que ya se veía en vez de generar otra.
    var skipInitial by remember { mutableStateOf(password.isNotEmpty()) }
    LaunchedEffect(options, nonce) {
        if (skipInitial) skipInitial = false else password = PasswordGenerator.generate(options)
    }

    UtilityScaffold(
        title = stringResource(R.string.util_password),
        subtitle = stringResource(R.string.util_password_desc),
        onBack = onBack,
        onHome = onHome,
    ) {
        UtilityCard {
            if (password.isEmpty()) {
                Text(stringResource(R.string.pw_pick_one), color = ErrorRed)
            } else {
                Text(
                    text = password,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                StrengthMeter(PasswordGenerator.entropyBits(options))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { copyText(context, password) }, enabled = password.isNotEmpty()) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(text = stringResource(R.string.util_copy), modifier = Modifier.padding(start = 8.dp))
                }
                OutlinedButton(onClick = { nonce++ }) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(text = stringResource(R.string.pw_generate), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }

        UtilityCard {
            val shownLength = PasswordGenerator.clampLength(length)
            Text(stringResource(R.string.pw_length, shownLength), fontWeight = FontWeight.SemiBold)
            Slider(
                value = length.toFloat(),
                onValueChange = { length = it.roundToInt() },
                valueRange = PasswordGenerator.MIN_LENGTH.toFloat()..PasswordGenerator.MAX_LENGTH.toFloat(),
            )
            SwitchRow(R.string.pw_lower, lower) { lower = it }
            SwitchRow(R.string.pw_upper, upper) { upper = it }
            SwitchRow(R.string.pw_digits, digits) { digits = it }
            SwitchRow(R.string.pw_symbols, symbols) { symbols = it }
            SwitchRow(R.string.pw_avoid_ambiguous, avoidAmbiguous) { avoidAmbiguous = it }
            OutlinedTextField(
                value = exclude,
                onValueChange = { exclude = it.take(MAX_EXCLUDE) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.pw_exclude)) },
                singleLine = true,
            )
        }
    }
}

private const val MAX_EXCLUDE = 40
private const val STRENGTH_FULL_BITS = 128f

@Composable
private fun StrengthMeter(bits: Int) {
    val strength = PasswordGenerator.strength(bits)
    val (labelRes, color) =
        when (strength) {
            PasswordStrength.WEAK -> R.string.pw_strength_weak to ErrorRed
            PasswordStrength.FAIR -> R.string.pw_strength_fair to WarningAmber
            PasswordStrength.STRONG -> R.string.pw_strength_strong to SuccessGreen
            PasswordStrength.VERY_STRONG -> R.string.pw_strength_very_strong to SuccessGreen
        }
    LinearProgressIndicator(
        progress = { (bits / STRENGTH_FULL_BITS).coerceIn(0.05f, 1f) },
        modifier = Modifier.fillMaxWidth(),
        color = color,
        trackColor = Color.Transparent.copy(alpha = 0.1f),
    )
    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(labelRes), color = color, fontWeight = FontWeight.SemiBold)
        Text(
            text = stringResource(R.string.pw_entropy, bits),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SwitchRow(
    labelRes: Int,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = stringResource(labelRes), modifier = Modifier.weight(1f).padding(end = 12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
