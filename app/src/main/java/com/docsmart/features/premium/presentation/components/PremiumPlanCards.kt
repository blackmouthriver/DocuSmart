package com.docsmart.features.premium.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.docsmart.core.ui.theme.PremiumGold
import com.docsmart.features.premium.domain.model.PremiumPlan

@Composable
fun PremiumPlanCards(
    plans: List<PremiumPlan>,
    selectedPlan: PremiumPlan?,
    onPlanSelected: (PremiumPlan) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Elige tu plan",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )

        plans.forEach { plan ->
            PlanCard(
                plan = plan,
                isSelected = selectedPlan?.id == plan.id,
                onSelected = { onPlanSelected(plan) }
            )
        }
    }
}

@Composable
private fun PlanCard(
    plan: PremiumPlan,
    isSelected: Boolean,
    onSelected: () -> Unit
) {
    val borderColor = if (isSelected)
        MaterialTheme.colorScheme.primary
    else
        MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .border(
                width = if (isSelected) 1.5.dp else 0.5.dp,
                color = borderColor,
                shape = MaterialTheme.shapes.large
            )
            .selectable(
                selected = isSelected,
                onClick = onSelected
            )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // ── Badge "Recomendado" dentro de la card ─
            if (plan.isPopular) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(
                                topStart = 20.dp,
                                topEnd = 20.dp,
                                bottomStart = 0.dp,
                                bottomEnd = 0.dp
                            )
                        )
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "⭐ Recomendado",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }

            // ── Contenido principal ───────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Radio button
                Icon(
                    imageVector = if (isSelected)
                        Icons.Rounded.RadioButtonChecked
                    else
                        Icons.Rounded.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (isSelected)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )

                // Info del plan
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = plan.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        // Badge de ahorro
                        plan.savingsLabel?.let { label ->
                            Surface(
                                shape = MaterialTheme.shapes.extraSmall,
                                color = if (plan.isPopular)
                                    PremiumGold.copy(alpha = 0.2f)
                                else
                                    MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (plan.isPopular)
                                        PremiumGold
                                    else
                                        MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(
                                        horizontal = 6.dp,
                                        vertical = 2.dp
                                    )
                                )
                            }
                        }
                    }
                    Text(
                        text = plan.period,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Precio
                Text(
                    text = plan.price,
                    style = MaterialTheme.typography.titleLarge,
                    color = if (isSelected)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}