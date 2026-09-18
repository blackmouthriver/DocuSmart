package com.docsmart.core.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.docsmart.R
import com.docsmart.core.ui.AppLanguage

// Backlog UX 2026-09-16/17 (seguimiento #66): rediseño del modal de
// "Idioma" -- hoja inferior (no diálogo centrado) con una grilla de
// tarjetas, cada una con la bandera del idioma y el nombre/región en una
// misma fila (con separación entre ambos, pedido explícito del usuario
// tras ver la primera versión con la bandera a sangre de fondo), y el
// Color de acento reservado para la selección (borde + check).
// Implementación basada en el código de referencia que compartió el
// usuario, adaptada para usar el emoji de bandera ya existente en
// `AppLanguage.flagEmoji` en vez de un recurso de imagen por idioma
// (`flagRes`) -- el proyecto no tiene assets de banderas propios
// todavía; si el usuario prefiere ilustraciones/fotos reales en vez del
// emoji, hay que agregar esos 12 drawables aparte.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguagePickerDialog(
    currentLanguage: AppLanguage,
    onSelect: (AppLanguage) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Text(
                text = stringResource(R.string.settings_select_language),
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = stringResource(R.string.settings_select_language_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
                modifier = Modifier.heightIn(max = 420.dp)
            ) {
                items(AppLanguage.entries, key = { it.code }) { language ->
                    LanguageTile(
                        language = language,
                        selected = language == currentLanguage,
                        onClick = { onSelect(language) }
                    )
                }
            }
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(100),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(stringResource(R.string.settings_close))
            }
            Spacer(Modifier.height(22.dp))
        }
    }
}

@Composable
private fun LanguageTile(language: AppLanguage, selected: Boolean, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val flagAlpha by animateFloatAsState(
        targetValue = when {
            selected -> 1f
            pressed -> 0.80f
            else -> 0.42f
        },
        animationSpec = tween(220),
        label = "flagAlpha"
    )
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.985f else 1f,
        animationSpec = tween(120),
        label = "tileScale"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) accent.copy(alpha = 0.30f) else Color.Transparent,
        animationSpec = tween(200),
        label = "tileBorder"
    )

    // Pedido explícito del usuario: el fondo de la tarjeta no puede ser un
    // color plano -- tiene que ser un degradado lineal (con el Color de
    // acento), no solo la bandera con una máscara de desvanecimiento.
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val gradientStart by animateColorAsState(
        targetValue = if (selected) primaryContainer else surfaceVariant,
        animationSpec = tween(200),
        label = "tileGradientStart"
    )
    val gradientEnd by animateColorAsState(
        targetValue = if (selected) accent.copy(alpha = 0.35f) else surfaceVariant.copy(alpha = 0.4f),
        animationSpec = tween(200),
        label = "tileGradientEnd"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(108.dp)
            .scale(scale)
            .clip(RoundedCornerShape(18.dp))
            .border(1.5.dp, borderColor, RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(listOf(gradientStart, gradientEnd)))
            // H20 (auditoría de accesibilidad TalkBack 2026-09-18): sin
            // role ni estado `selected`, TalkBack no anunciaba cuál de los
            // 12 idiomas estaba elegido -- `selectable` con
            // Role.RadioButton (son mutuamente excluyentes) sí lo hace.
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        // Pedido explícito del usuario tras revisar la tarjeta a sangre:
        // bandera y texto en una sola fila, con separación entre ambos --
        // en vez de la bandera de fondo desvanecida hacia el texto.
        Row(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 14.dp, end = 34.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = language.flagEmoji,
                fontSize = 30.sp,
                modifier = Modifier.alpha(flagAlpha)
            )
            Column {
                Text(
                    text = language.nativeLabel,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = language.regionLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp)
                .size(24.dp)
                .clip(CircleShape)
                .background(if (selected) accent else Color.White.copy(alpha = 0.55f))
                .border(
                    width = 1.5.dp,
                    color = if (selected) accent else Color.White.copy(alpha = 0.85f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            AnimatedVisibility(
                visible = selected,
                enter = fadeIn(tween(160)) + scaleIn(tween(180), initialScale = 0.6f),
                exit = fadeOut(tween(120))
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
