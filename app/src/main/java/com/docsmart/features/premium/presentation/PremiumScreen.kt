package com.docsmart.features.premium.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.docsmart.R
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
                            contentDescription = stringResource(R.string.settings_close),
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
                                    text = stringResource(R.string.premium_processing_purchase),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            val purchaseErrorMessage = stringResource(R.string.premium_purchase_error)
                            val noPurchasesFoundMessage = stringResource(R.string.premium_no_purchases_found)

                            // Botón principal de compra
                            Button(
                                onClick = { viewModel.purchase(purchaseErrorMessage) },
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
                                        stringResource(R.string.premium_get_plan, stringResource(it.titleRes), it.price)
                                    } ?: stringResource(R.string.premium_select_plan),
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }

                            // Restaurar compras
                            TextButton(
                                onClick = { viewModel.restorePurchases(noPurchasesFoundMessage) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = stringResource(R.string.premium_restore_purchases),
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
                                    text = stringResource(R.string.premium_continue_free),
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
                        text = stringResource(R.string.premium_terms),
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
                text = stringResource(R.string.premium_active_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = stringResource(R.string.premium_active_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
            )
            Button(
                onClick = onClose,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.premium_continue),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}