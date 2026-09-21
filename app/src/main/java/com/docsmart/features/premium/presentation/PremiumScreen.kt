package com.docsmart.features.premium.presentation

import android.app.Activity
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.docsmart.R
import com.docsmart.core.analytics.DocuSmartAnalytics
import com.docsmart.core.ui.util.findActivity
import com.docsmart.features.premium.presentation.components.*
import timber.log.Timber

// Extraído para no engordar el cuerpo de PremiumScreen (detekt: LongMethod).
private fun onPurchaseClick(
    activity: Activity?,
    viewModel: PremiumViewModel,
    purchaseErrorMessage: String,
    purchasePendingMessage: String,
): () -> Unit =
    {
        activity?.let {
            viewModel.purchase(it, purchaseErrorMessage, purchasePendingMessage)
        } ?: Timber.e("PremiumScreen: Activity es null, no se puede iniciar la compra")
    }

@Composable
fun PremiumScreen(
    onClose: () -> Unit = {},
    viewModel: PremiumViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    // launchBillingFlow necesita el Activity real, no el Context envuelto
    // que entrega LocalContext.
    val activity = remember(context) { context.findActivity() }

    LaunchedEffect(Unit) { DocuSmartAnalytics.logPremiumScreenViewed() }

    // Muestra errores
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.dismissError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        // Fondo animado global (backlog UX 2026-09-06): transparente para
        // dejar ver la capa pintada una sola vez en MainActivity. Se excluye
        // el inset inferior de systemBars (bug real "línea blanca": este
        // Scaffold lo reservaba por duplicado sobre el que ya reserva
        // MainActivity para DocuSmartBottomBar -- ver StudyScreen.kt para el
        // detalle completo).
        // Rediseño 2026-09-20: sin el inset superior (MainActivity ya lo reserva): el
        // banner dejaba ~32dp vacíos sobre él.
        contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal),
        containerColor = Color.Transparent,
    ) { innerPadding ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            // ── Banner ────────────────────────────────
            item {
                Box {
                    // isPaidPremium, no isPremium: durante el trial
                    // automático sin tarjeta el usuario ya tiene todo
                    // desbloqueado, pero el hero de "¡Eres Premium!" no
                    // debe tapar que todavía puede (y conviene que) se
                    // suscriba antes de que el trial termine.
                    PremiumBanner(isPremium = uiState.isPaidPremium)

                    // Botón cerrar
                    IconButton(
                        onClick = onClose,
                        modifier =
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.settings_close),
                            tint = androidx.compose.ui.graphics.Color.White,
                        )
                    }
                }
            }

            // ── Si ya es cliente pagador (o está en el trial de
            // suscripción de HU-54) ───────────────────
            if (uiState.isPaidPremium) {
                item {
                    PremiumActiveCard(onClose = onClose, trialEndsAtMillis = uiState.trialEndsAtMillis)
                }
            } else {
                // Trial automático sin tarjeta: distinto de lo de arriba --
                // acá no hay ninguna suscripción real, así que además de
                // avisar cuántos días quedan, más abajo se le sigue
                // mostrando cómo suscribirse.
                // No se puede usar un smart-cast de uiState.autoTrialDaysRemaining
                // (uiState es una propiedad delegada por `by`) -- se captura
                // en un val local primero.
                val autoTrialDaysRemaining = uiState.autoTrialDaysRemaining
                if (autoTrialDaysRemaining != null) {
                    item {
                        PremiumAutoTrialCard(daysRemaining = autoTrialDaysRemaining)
                    }
                }
            }

            // ── Aviso persistente de compra pendiente ─
            // Hallazgo 3 (auditoría monetización 2026-09-18, Media): antes de
            // esto, una compra PENDING (pago en efectivo/transferencia, común
            // en Latinoamérica) que seguía pendiente en una revalidación
            // posterior se descartaba como "sin compras" -- ahora
            // isPendingPurchase persiste en el estado (ver PremiumViewModel)
            // y se muestra acá mientras dure, en vez de solo en el snackbar
            // transitorio del momento en que se detectó.
            if (uiState.isPendingPurchase) {
                item {
                    PremiumPendingCard()
                }
            }

            // ── Lista de funciones ────────────────────
            item {
                PremiumFeatureList(
                    features = uiState.features,
                    isPremium = uiState.isPremium,
                    modifier =
                        Modifier.padding(
                            horizontal = 20.dp,
                            vertical = 24.dp,
                        ),
                )
            }

            // ── Planes (para cualquiera que no sea ya cliente pagador,
            // incluido alguien en el trial automático sin tarjeta) ────
            if (!uiState.isPaidPremium) {
                item {
                    PremiumPlanCards(
                        plans = uiState.plans,
                        selectedPlan = uiState.selectedPlan,
                        onPlanSelected = { viewModel.selectPlan(it) },
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }

                // ── Botón de compra ───────────────────
                item {
                    PurchaseActionsSection(uiState, viewModel, activity, onClose)
                }

                // ── Términos ──────────────────────────
                item {
                    Text(
                        text = stringResource(R.string.premium_terms),
                        style = MaterialTheme.typography.labelSmall,
                        color =
                            MaterialTheme.colorScheme.onSurfaceVariant
                                .copy(alpha = 0.5f),
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }
            }
        }
    }
}

// ── Botón de compra / restaurar / continuar gratis ────
// Extraído de PremiumScreen para mantenerla corta (detekt: LongMethod).
@Composable
private fun PurchaseActionsSection(
    uiState: PremiumUiState,
    viewModel: PremiumViewModel,
    activity: Activity?,
    onClose: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (uiState.isPurchasing) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Text(
                    text = stringResource(R.string.premium_processing_purchase),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return
        }

        val purchaseErrorMessage = stringResource(R.string.premium_purchase_error)
        val purchasePendingMessage = stringResource(R.string.premium_purchase_pending)
        val noPurchasesFoundMessage = stringResource(R.string.premium_no_purchases_found)
        val restoreSuccessMessage = stringResource(R.string.premium_restore_success)
        // Hallazgo real de la revisión adversarial de este mismo lote (M12):
        // la primera versión reutilizaba purchaseErrorMessage ("No se pudo
        // completar la compra") también para un fallo de RESTAURAR compras
        // -- un usuario sin conexión que toca "Restaurar compras" vería un
        // mensaje sobre una compra que nunca intentó.
        val restoreErrorMessage = stringResource(R.string.premium_restore_error)

        Button(
            onClick = onPurchaseClick(activity, viewModel, purchaseErrorMessage, purchasePendingMessage),
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = MaterialTheme.shapes.medium,
            enabled = uiState.selectedPlan != null,
        ) {
            Icon(Icons.Rounded.Star, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            // HU-54 RF2: si el plan tiene prueba gratuita, el CTA lo dice
            // explícitamente en vez de mostrar el precio que se cobrará recién
            // después -- para que el primer cobro real no sea sorpresivo.
            Text(
                text =
                    when (val cta = purchaseCtaFor(uiState.selectedPlan)) {
                        PurchaseCta.SelectPlan -> stringResource(R.string.premium_select_plan)
                        is PurchaseCta.StartTrial -> stringResource(R.string.premium_start_trial, cta.days)
                        is PurchaseCta.GetPlan ->
                            stringResource(R.string.premium_get_plan, stringResource(cta.plan.titleRes), cta.plan.price)
                    },
                style = MaterialTheme.typography.labelLarge,
            )
        }

        TextButton(
            onClick = {
                viewModel.restorePurchases(noPurchasesFoundMessage, restoreSuccessMessage, restoreErrorMessage)
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(R.string.premium_restore_purchases),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.premium_continue_free),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ── Card de Premium activo ────────────────────────────
@Composable
private fun PremiumActiveCard(
    onClose: () -> Unit,
    trialEndsAtMillis: Long? = null,
) {
    // HU-54, AC1: mientras la prueba siga vigente, se reemplaza el texto
    // genérico por la fecha real de cobro -- una vez pasada esa fecha (el
    // usuario ya paga, o restauró una compra vieja), vuelve al texto normal.
    // Lint real (NonObservableLocale): java.util.Locale.getDefault() no es
    // observable por Compose -- si el usuario cambia el idioma del sistema
    // sin recrear la Activity, esta card no se recompondría con la fecha en
    // el formato correcto. LocalConfiguration.current sí lo es.
    val locale = LocalConfiguration.current.locales[0]
    val bodyText =
        if (trialEndsAtMillis != null && isSubscriptionTrialActive(trialEndsAtMillis, System.currentTimeMillis())) {
            val formattedDate =
                java.text.SimpleDateFormat("dd/MM/yyyy", locale)
                    .format(java.util.Date(trialEndsAtMillis))
            stringResource(R.string.premium_trial_active_message, formattedDate)
        } else {
            stringResource(R.string.premium_active_body)
        }

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
        shape = MaterialTheme.shapes.large,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.premium_active_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = bodyText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
            )
            Button(
                onClick = onClose,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.premium_continue),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

// ── Card de aviso persistente: compra pendiente de confirmación ──
// Hallazgo 3 (auditoría monetización 2026-09-18, Media): estilo deliberadamente
// distinto de PremiumActiveCard (tertiaryContainer en vez de primaryContainer)
// para que no se confunda visualmente con "ya sos Premium" -- el usuario
// todavía no tiene acceso, solo está esperando la confirmación del pago.
@Composable
private fun PremiumPendingCard() {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
        shape = MaterialTheme.shapes.large,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            ),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Schedule,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.premium_pending_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Text(
                    text = stringResource(R.string.premium_pending_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
                )
            }
        }
    }
}

// ── Card del trial automático sin tarjeta ─────────────
// Pedido explícito del usuario 2026-09-12: todo el que instala la app tiene
// unos días de Premium completo gratis sin suscribirse a nada -- esta card
// es "el mecanismo que le va diciendo al usuario los días" que quedan,
// distinta de PremiumActiveCard porque acá SÍ conviene seguir mostrando los
// planes debajo (todavía no es cliente pagador).
//
// Ajuste pedido por el usuario 2026-09-12: foto de fondo con opacidad, 100%
// del ancho de pantalla (sin el margen horizontal que sí tienen las demás
// cards) y pegada al banner azul de arriba (sin padding vertical propio ni
// esquinas redondeadas, para que no se note un corte entre los dos) -- por
// eso es un Box liso en vez de un Card con su elevación/forma de siempre.
//
// Segundo ajuste, mismo día: más opacidad en la foto + zoom lento tipo
// "Ken Burns" -- pero subirle la opacidad a la foto hacía que el texto se
// viera perdido, así que se agrega un degradado oscuro (scrim) entre la
// imagen y el texto para garantizar contraste sin bajarle intensidad a la
// foto, y el texto pasa a blanco fijo con sombra (no onTertiaryContainer,
// que depende del tema y podía quedar oscuro sobre un fondo con foto).
// Shadow en la card entera para que se vea "flotando" sobre el resto.
@Composable
private fun PremiumAutoTrialCard(daysRemaining: Int) {
    val infiniteTransition = rememberInfiniteTransition(label = "premiumTrialBg")
    val imageScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 9000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "premiumTrialBgScale",
    )
    val textShadow =
        Shadow(
            color = Color.Black.copy(alpha = 0.6f),
            offset = Offset(0f, 2f),
            blurRadius = 6f,
        )

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .shadow(elevation = 10.dp, clip = false)
                // El zoom animado de la foto (scale > 1) se dibuja más grande
                // que la card -- sin este clip (van DESPUÉS del shadow a
                // propósito, para que la sombra sí pueda salirse de los bordes
                // y la foto no) se veía "salir" de la tarjeta hacia el banner
                // de arriba y el contenido de abajo. Bug real reportado por el
                // usuario 2026-09-12 al ver la animación en el dispositivo.
                .clip(RectangleShape)
                .background(MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Image(
            painter = painterResource(R.drawable.premium_trial_bg),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alpha = 0.55f,
            modifier =
                Modifier
                    .matchParentSize()
                    .scale(imageScale),
        )
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.15f), Color.Black.copy(alpha = 0.55f)),
                        ),
                    ),
        )
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.premium_auto_trial_title),
                style = MaterialTheme.typography.titleLarge.copy(shadow = textShadow),
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Text(
                text = stringResource(R.string.premium_auto_trial_body, daysRemaining),
                style = MaterialTheme.typography.bodyMedium.copy(shadow = textShadow),
                color = Color.White.copy(alpha = 0.95f),
                textAlign = TextAlign.Center,
            )
        }
    }
}
