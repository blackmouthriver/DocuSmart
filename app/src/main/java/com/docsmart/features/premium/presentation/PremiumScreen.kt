package com.docsmart.features.premium.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.docsmart.features.premium.presentation.components.*

@Composable
fun PremiumScreen(
    onClose: () -> Unit = {},
    viewModel: PremiumViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Muestra errores
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.dismissError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {

            // ── Banner ────────────────────────────────
            item {
                Box {
                    PremiumBanner(isPremium = uiState.isPremium)

                    // Botón cerrar
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Cerrar",
                            tint = androidx.compose.ui.graphics.Color.White
                        )
                    }
                }
            }

            // ── Si ya es Premium ──────────────────────
            if (uiState.isPremium) {
                item {
                    PremiumActiveCard(onClose = onClose)
                }
            }

            // ── Lista de funciones ────────────────────
            item {
                PremiumFeatureList(
                    features = uiState.features,
                    isPremium = uiState.isPremium,
                    modifier = Modifier.padding(
                        horizontal = 20.dp,
                        vertical = 24.dp
                    )
                )
            }

            // ── Planes (solo si no es premium) ────────
            if (!uiState.isPremium) {
                item {
                    PremiumPlanCards(
                        plans = uiState.plans,
                        selectedPlan = uiState.selectedPlan,
                        onPlanSelected = { viewModel.selectPlan(it) },
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }

                // ── Botón de compra ───────────────────
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (uiState.isPurchasing) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Procesando compra...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            // Botón principal de compra
                            Button(
                                onClick = { viewModel.purchase() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                shape = MaterialTheme.shapes.medium,
                                enabled = uiState.selectedPlan != null
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Star,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = uiState.selectedPlan?.let {
                                        "Obtener ${it.title} — ${it.price}"
                                    } ?: "Selecciona un plan",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }

                            // Restaurar compras
                            TextButton(
                                onClick = { viewModel.restorePurchases() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Restaurar compras",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Continuar gratis
                            TextButton(
                                onClick = onClose,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Continuar con versión gratuita",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                        .copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }

                // ── Términos ──────────────────────────
                item {
                    Text(
                        text = "Al suscribirte aceptas los Términos de Servicio y " +
                                "la Política de Privacidad. La suscripción se renueva " +
                                "automáticamente. Cancela cuando quieras.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                            .copy(alpha = 0.5f),
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }
            }
        }
    }
}

// ── Card de Premium activo ────────────────────────────
@Composable
private fun PremiumActiveCard(onClose: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Tu suscripción está activa",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = "Tienes acceso completo a todas las funciones de DocuSmart Premium",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
            )
            Button(
                onClick = onClose,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Continuar",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}