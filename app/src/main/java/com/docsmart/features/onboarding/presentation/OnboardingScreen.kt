package com.docsmart.features.onboarding.presentation

import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.docsmart.R
import com.docsmart.core.ui.theme.DocuBlue
import com.docsmart.core.ui.theme.IndigoAccent
import com.docsmart.core.ui.theme.SmartBlue
import com.docsmart.core.ui.theme.SuccessGreen
import com.docsmart.core.ui.theme.WarningAmber
import kotlinx.coroutines.launch

// ── Modelo de slide ───────────────────────────────────────────────────────────
data class OnboardingSlide(
    val icon         : ImageVector,
    val iconColor    : Color,
    @StringRes val titleRes: Int,
    @StringRes val descRes : Int,
    val gradient     : List<Color>
)

private val slides = listOf(
    OnboardingSlide(
        icon      = Icons.Rounded.FolderOpen,
        iconColor = Color.White,
        titleRes  = R.string.onboarding_1_title,
        descRes   = R.string.onboarding_1_desc,
        gradient  = listOf(DocuBlue, SmartBlue, IndigoAccent)
    ),
    OnboardingSlide(
        icon      = Icons.Rounded.SwapHoriz,
        iconColor = Color.White,
        titleRes  = R.string.onboarding_2_title,
        descRes   = R.string.onboarding_2_desc,
        gradient  = listOf(IndigoAccent, DocuBlue, SmartBlue)
    ),
    OnboardingSlide(
        icon      = Icons.Rounded.Lock,
        iconColor = Color.White,
        titleRes  = R.string.onboarding_3_title,
        descRes   = R.string.onboarding_3_desc,
        gradient  = listOf(SmartBlue, IndigoAccent, DocuBlue)
    ),
    OnboardingSlide(
        icon      = Icons.Rounded.MenuBook,
        iconColor = Color.White,
        titleRes  = R.string.onboarding_4_title,
        descRes   = R.string.onboarding_4_desc,
        gradient  = listOf(DocuBlue, IndigoAccent, SmartBlue)
    )
)

// ── SharedPreferences helper ──────────────────────────────────────────────────
fun hasCompletedOnboarding(context: Context): Boolean =
    context.getSharedPreferences("docusmart_onboarding", Context.MODE_PRIVATE)
        .getBoolean("completed", false)

fun markOnboardingCompleted(context: Context) {
    context.getSharedPreferences("docusmart_onboarding", Context.MODE_PRIVATE)
        .edit().putBoolean("completed", true).apply()
}

fun resetOnboarding(context: Context) {
    context.getSharedPreferences("docusmart_onboarding", Context.MODE_PRIVATE)
        .edit().putBoolean("completed", false).apply()
}

// ── Pantalla principal ────────────────────────────────────────────────────────
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val context    = LocalContext.current
    val pagerState = rememberPagerState(pageCount = { slides.size })
    val scope      = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == slides.size - 1

    Box(modifier = Modifier.fillMaxSize()) {

        // ── Pager de slides ───────────────────────────────────────────────────
        HorizontalPager(
            state    = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            OnboardingSlideContent(slide = slides[page])
        }

        // ── Controles inferiores ──────────────────────────────────────────────
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 48.dp, start = 32.dp, end = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            // Indicadores de página
            PageIndicator(
                pageCount   = slides.size,
                currentPage = pagerState.currentPage
            )

            // Botones
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                OnboardingSkipButton(isLastPage, context, onFinished)
                OnboardingNextButton(isLastPage, context, pagerState, scope, onFinished)
            }
        }
    }
}

// ── Botón Saltar — oculto en última página ────────────────────────────────────
@Composable
private fun OnboardingSkipButton(isLastPage: Boolean, context: Context, onFinished: () -> Unit) {
    if (!isLastPage) {
        TextButton(onClick = {
            markOnboardingCompleted(context)
            onFinished()
        }) {
            Text(
                text  = stringResource(R.string.onboarding_skip),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.7f)
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
    context   : Context,
    pagerState: androidx.compose.foundation.pager.PagerState,
    scope     : kotlinx.coroutines.CoroutineScope,
    onFinished: () -> Unit
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
        shape  = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.White,
            contentColor   = DocuBlue
        ),
        modifier = Modifier.height(52.dp)
    ) {
        Text(
            text       = stringResource(if (isLastPage) R.string.onboarding_start else R.string.onboarding_next),
            style      = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
        if (!isLastPage) {
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Rounded.ArrowForward, null,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// ── Contenido de cada slide ───────────────────────────────────────────────────
@Composable
private fun OnboardingSlideContent(slide: OnboardingSlide) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.linearGradient(colors = slide.gradient)
            )
    ) {
        Column(
            modifier            = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(Modifier.weight(1f))

            // Ícono principal
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .background(
                        Color.White.copy(alpha = 0.15f),
                        RoundedCornerShape(40.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector        = slide.icon,
                    contentDescription = null,
                    tint               = slide.iconColor,
                    modifier           = Modifier.size(72.dp)
                )
            }

            Spacer(Modifier.height(48.dp))

            // Título
            Text(
                text       = stringResource(slide.titleRes),
                style      = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color      = Color.White,
                textAlign  = TextAlign.Center,
                lineHeight = 36.sp
            )

            Spacer(Modifier.height(20.dp))

            // Descripción
            Text(
                text      = stringResource(slide.descRes),
                style     = MaterialTheme.typography.bodyLarge,
                color     = Color.White.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
                lineHeight = 26.sp
            )

            Spacer(Modifier.weight(2f))
        }
    }
}

// ── Indicadores de página ─────────────────────────────────────────────────────
@Composable
private fun PageIndicator(pageCount: Int, currentPage: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment     = Alignment.CenterVertically
    ) {
        repeat(pageCount) { index ->
            val isSelected = index == currentPage
            val width by animateDpAsState(
                targetValue = if (isSelected) 28.dp else 8.dp,
                label       = "indicator_width"
            )
            val color by animateColorAsState(
                targetValue = if (isSelected) Color.White
                else Color.White.copy(alpha = 0.4f),
                label       = "indicator_color"
            )
            Box(
                modifier = Modifier
                    .height(8.dp)
                    .width(width)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}