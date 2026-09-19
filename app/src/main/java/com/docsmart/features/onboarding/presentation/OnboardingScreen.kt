package com.docsmart.features.onboarding.presentation

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.docsmart.R
import com.docsmart.core.ui.theme.DocuBlue
import com.docsmart.core.ui.theme.IndigoAccent
import com.docsmart.core.ui.theme.SmartBlue
import com.docsmart.core.ui.theme.SuccessGreen
import kotlinx.coroutines.launch

// ── Modelo de slide ───────────────────────────────────────────────────────────
data class OnboardingSlide(
    val icon: ImageVector,
    val iconColor: Color,
    @StringRes val titleRes: Int,
    @StringRes val descRes: Int,
    val gradient: List<Color>,
    // Foto de fondo real (pedido explícito del usuario 2026-09-12, mismo
    // tratamiento que la card de trial de PremiumScreen): el degradado de
    // arriba pasa a dibujarse ENCIMA de la foto como velo semitransparente,
    // en vez de ser el fondo entero -- así cada slide conserva su color de
    // marca pero ya no es un fondo plano.
    @DrawableRes val backgroundRes: Int,
    // Fila 22 del backlog UX: la última slide deja vincular una carpeta del
    // dispositivo (SAF) directo desde el onboarding, en vez de que el
    // usuario tenga que descubrir el banner de Biblioteca por su cuenta.
    val isFolderLinkSlide: Boolean = false,
)

private val slides =
    listOf(
        OnboardingSlide(
            icon = Icons.Rounded.FolderOpen,
            iconColor = Color.White,
            titleRes = R.string.onboarding_1_title,
            descRes = R.string.onboarding_1_desc,
            gradient = listOf(DocuBlue, SmartBlue, IndigoAccent),
            backgroundRes = R.drawable.onboarding_bg_1,
        ),
        OnboardingSlide(
            icon = Icons.Rounded.SwapHoriz,
            iconColor = Color.White,
            titleRes = R.string.onboarding_2_title,
            descRes = R.string.onboarding_2_desc,
            gradient = listOf(IndigoAccent, DocuBlue, SmartBlue),
            backgroundRes = R.drawable.onboarding_bg_2,
        ),
        OnboardingSlide(
            icon = Icons.Rounded.Lock,
            iconColor = Color.White,
            titleRes = R.string.onboarding_3_title,
            descRes = R.string.onboarding_3_desc,
            gradient = listOf(SmartBlue, IndigoAccent, DocuBlue),
            backgroundRes = R.drawable.onboarding_bg_3,
        ),
        OnboardingSlide(
            icon = Icons.Rounded.MenuBook,
            iconColor = Color.White,
            titleRes = R.string.onboarding_4_title,
            descRes = R.string.onboarding_4_desc,
            gradient = listOf(DocuBlue, IndigoAccent, SmartBlue),
            backgroundRes = R.drawable.onboarding_bg_4,
        ),
        OnboardingSlide(
            icon = Icons.Rounded.CreateNewFolder,
            iconColor = Color.White,
            titleRes = R.string.onboarding_5_title,
            descRes = R.string.onboarding_5_desc,
            gradient = listOf(IndigoAccent, SmartBlue, DocuBlue),
            backgroundRes = R.drawable.onboarding_bg_5,
            isFolderLinkSlide = true,
        ),
    )

// ── SharedPreferences helper ──────────────────────────────────────────────────
fun hasCompletedOnboarding(context: Context): Boolean =
    context.getSharedPreferences("docusmart_onboarding", Context.MODE_PRIVATE)
        .getBoolean("completed", false)

fun markOnboardingCompleted(context: Context) {
    context.getSharedPreferences("docusmart_onboarding", Context.MODE_PRIVATE)
        .edit().putBoolean("completed", true).apply()
}

// ── Pantalla principal ────────────────────────────────────────────────────────
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val pagerState = rememberPagerState(pageCount = { slides.size })
    val scope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == slides.size - 1

    val linkedFolderUri by viewModel.linkedFolderUri.collectAsStateWithLifecycle()
    val linkedFolderName =
        remember(linkedFolderUri) {
            linkedFolderUri?.let { viewModel.linkedFolderDisplayName(it) }
        }
    // Hallazgo real de la auditoría general 2026-09-17 (M3), no propagado a
    // este call site en el fix original -- ver el mismo aviso ya agregado en
    // SettingsScreen.kt.
    val linkFolderErrorMessage = stringResource(R.string.library_link_folder_error)
    val linkFolderLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocumentTree(),
        ) { uri ->
            uri?.let {
                if (!viewModel.onDownloadsFolderPicked(it)) {
                    Toast.makeText(context, linkFolderErrorMessage, Toast.LENGTH_SHORT).show()
                }
            }
        }

    Box(modifier = Modifier.fillMaxSize()) {
        // ── Pager de slides ───────────────────────────────────────────────────
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            OnboardingSlideContent(
                slide = slides[page],
                linkedFolderName = linkedFolderName,
                onLinkFolderClick = {
                    linkFolderLauncher.launch(viewModel.downloadsFolderPickerInitialUri())
                },
            )
        }

        // ── Controles inferiores ──────────────────────────────────────────────
        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 48.dp, start = 32.dp, end = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            // Indicadores de página
            PageIndicator(
                pageCount = slides.size,
                currentPage = pagerState.currentPage,
            )

            // Botones
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OnboardingSkipButton(isLastPage, context, onFinished)
                OnboardingNextButton(isLastPage, context, pagerState, scope, onFinished)
            }
        }
    }
}

// ── Botón Saltar — oculto en última página ────────────────────────────────────
@Composable
private fun OnboardingSkipButton(
    isLastPage: Boolean,
    context: Context,
    onFinished: () -> Unit,
) {
    if (!isLastPage) {
        TextButton(onClick = {
            markOnboardingCompleted(context)
            onFinished()
        }) {
            Text(
                text = stringResource(R.string.onboarding_skip),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.7f),
            )
        }
    } else {
        Spacer(Modifier.width(80.dp))
    }
}

// ── Botón Siguiente / Empezar ──────────────────────────────────────────────────
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun OnboardingNextButton(
    isLastPage: Boolean,
    context: Context,
    pagerState: androidx.compose.foundation.pager.PagerState,
    scope: kotlinx.coroutines.CoroutineScope,
    onFinished: () -> Unit,
) {
    Button(
        onClick = {
            if (isLastPage) {
                markOnboardingCompleted(context)
                onFinished()
            } else {
                scope.launch {
                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                }
            }
        },
        shape = RoundedCornerShape(16.dp),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = DocuBlue,
            ),
        modifier = Modifier.height(52.dp),
    ) {
        Text(
            text = stringResource(if (isLastPage) R.string.onboarding_start else R.string.onboarding_next),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
        if (!isLastPage) {
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Rounded.ArrowForward,
                null,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

// ── Contenido de cada slide ───────────────────────────────────────────────────
@Composable
private fun OnboardingSlideContent(
    slide: OnboardingSlide,
    linkedFolderName: String? = null,
    onLinkFolderClick: () -> Unit = {},
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(slide.backgroundRes),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        // El degradado de marca de cada slide se dibuja como velo
        // semitransparente sobre la foto (antes era el fondo entero) --
        // conserva la identidad de color de la app y garantiza contraste
        // para el texto blanco de abajo sin taparle la foto por completo.
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        brush =
                            Brush.verticalGradient(
                                colors = slide.gradient.map { it.copy(alpha = 0.72f) },
                            ),
                    ),
        )
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Spacer(Modifier.weight(1f))

            // Ícono principal
            Box(
                modifier =
                    Modifier
                        .size(140.dp)
                        .background(
                            Color.White.copy(alpha = 0.15f),
                            RoundedCornerShape(40.dp),
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = slide.icon,
                    contentDescription = null,
                    tint = slide.iconColor,
                    modifier = Modifier.size(72.dp),
                )
            }

            Spacer(Modifier.height(48.dp))

            // Título -- sombra agregada junto con la foto de fondo (antes
            // el fondo era un degradado plano, no hacía falta): garantiza
            // contraste sin importar qué tan clara sea la zona de la foto
            // que quede detrás del texto en cada slide.
            val textShadow =
                Shadow(
                    color = Color.Black.copy(alpha = 0.5f),
                    offset = Offset(0f, 2f),
                    blurRadius = 8f,
                )
            Text(
                text = stringResource(slide.titleRes),
                style = MaterialTheme.typography.headlineMedium.copy(shadow = textShadow),
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                lineHeight = 36.sp,
            )

            Spacer(Modifier.height(20.dp))

            // Descripción
            Text(
                text = stringResource(slide.descRes),
                style = MaterialTheme.typography.bodyLarge.copy(shadow = textShadow),
                color = Color.White.copy(alpha = 0.9f),
                textAlign = TextAlign.Center,
                lineHeight = 26.sp,
            )

            if (slide.isFolderLinkSlide) {
                Spacer(Modifier.height(28.dp))
                OnboardingFolderLinkAction(
                    linkedFolderName = linkedFolderName,
                    onLinkClick = onLinkFolderClick,
                )
            }

            Spacer(Modifier.weight(if (slide.isFolderLinkSlide) 1f else 2f))
        }
    }
}

// ── Acción de vincular carpeta (fila 22 backlog UX) ────────────────────────────
@Composable
private fun OnboardingFolderLinkAction(
    linkedFolderName: String?,
    onLinkClick: () -> Unit,
) {
    if (linkedFolderName != null) {
        Row(
            modifier =
                Modifier
                    .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = SuccessGreen,
            )
            Text(
                text = stringResource(R.string.onboarding_folder_linked, linkedFolderName),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
        }
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onLinkClick) {
            Text(
                text = stringResource(R.string.onboarding_folder_change),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.8f),
            )
        }
    } else {
        Button(
            onClick = onLinkClick,
            shape = RoundedCornerShape(16.dp),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = DocuBlue,
                ),
        ) {
            Icon(
                imageVector = Icons.Rounded.CreateNewFolder,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.onboarding_folder_link_button),
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

// ── Indicadores de página ─────────────────────────────────────────────────────
// Hallazgo real de la auditoría general 2026-09-18 (Media, accesibilidad):
// ni el HorizontalPager ni este indicador tenían semántica de accesibilidad
// -- TalkBack no anunciaba en qué slide del onboarding estaba el usuario ni
// cuántas había en total. Mismo patrón ya usado en el Convertidor
// (converter_image_position_a11y, ConverterScreen.kt) -- acá se aplica sobre
// el contenedor del indicador (en vez de sobre cada página del pager) para no
// pisar la semántica propia del título/descripción de cada slide.
@Composable
private fun PageIndicator(
    pageCount: Int,
    currentPage: Int,
) {
    val positionDescription =
        stringResource(
            R.string.onboarding_page_position_a11y,
            currentPage + 1,
            pageCount,
        )
    Row(
        modifier =
            Modifier.semantics(mergeDescendants = true) {
                contentDescription = positionDescription
            },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pageCount) { index ->
            val isSelected = index == currentPage
            val width by animateDpAsState(
                targetValue = if (isSelected) 28.dp else 8.dp,
                label = "indicator_width",
            )
            val color by animateColorAsState(
                targetValue =
                    if (isSelected) {
                        Color.White
                    } else {
                        Color.White.copy(alpha = 0.4f)
                    },
                label = "indicator_color",
            )
            Box(
                modifier =
                    Modifier
                        .height(8.dp)
                        .width(width)
                        .clip(CircleShape)
                        .background(color)
                        .clearAndSetSemantics {},
            )
        }
    }
}
