package com.docsmart.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.docsmart.R

// Rediseño UI 2026-09-20 (paleta D + tipografía A): Plus Jakarta Sans para
// títulos e Inter para cuerpo, ambas fuentes variables incluidas en la app
// (res/font) -- sin descarga en runtime, coherente con el modo 100% offline.
// minSdk 26 soporta fuentes variables, un solo archivo cubre todos los pesos.
@OptIn(ExperimentalTextApi::class)
private fun variableFamily(resId: Int): FontFamily =
    FontFamily(
        listOf(
            FontWeight.Normal,
            FontWeight.Medium,
            FontWeight.SemiBold,
            FontWeight.Bold,
            FontWeight.ExtraBold,
        ).map { w ->
            Font(
                resId = resId,
                weight = w,
                variationSettings = FontVariation.Settings(FontVariation.weight(w.weight)),
            )
        },
    )

val PlusJakartaSans = variableFamily(R.font.plus_jakarta_sans)
val Inter = variableFamily(R.font.inter)

val DocuSmartTypography =
    Typography(
        displayLarge =
            TextStyle(
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 32.sp,
                lineHeight = 40.sp,
                letterSpacing = (-0.5).sp,
            ),
        displayMedium =
            Typography().displayMedium.copy(
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.ExtraBold,
            ),
        displaySmall =
            Typography().displaySmall.copy(
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.ExtraBold,
            ),
        headlineSmall =
            Typography().headlineSmall.copy(
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.ExtraBold,
            ),
        headlineLarge =
            TextStyle(
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 28.sp,
                lineHeight = 36.sp,
                letterSpacing = (-0.4).sp,
            ),
        headlineMedium =
            TextStyle(
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 24.sp,
                lineHeight = 32.sp,
                letterSpacing = (-0.3).sp,
            ),
        titleLarge =
            TextStyle(
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp,
                lineHeight = 28.sp,
            ),
        titleMedium =
            TextStyle(
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                lineHeight = 26.sp,
            ),
        titleSmall =
            TextStyle(
                fontFamily = Inter,
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
                lineHeight = 22.sp,
            ),
        bodyLarge =
            TextStyle(
                fontFamily = Inter,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                lineHeight = 24.sp,
            ),
        bodyMedium =
            TextStyle(
                fontFamily = Inter,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            ),
        bodySmall =
            TextStyle(
                fontFamily = Inter,
                fontWeight = FontWeight.Normal,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            ),
        labelLarge =
            TextStyle(
                fontFamily = Inter,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.1.sp,
            ),
        labelMedium =
            TextStyle(
                fontFamily = Inter,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            ),
        labelSmall =
            TextStyle(
                fontFamily = Inter,
                fontWeight = FontWeight.Normal,
                fontSize = 11.sp,
                lineHeight = 14.sp,
            ),
    )

// HU-UX-05 (backlog UX 2026-08-30): tamaño de letra ajustable -- escala
// fontSize/lineHeight de los 15 estilos de Typography (no solo los 12 que
// DocuSmartTypography define explícito; displayMedium/displaySmall/
// headlineSmall caen al default de Material3 pero igual se usan en la app,
// ver ScannerScreen/StudyScreen/SecurityScreen/SplitPdfScreen/HomeBanner).
// letterSpacing no se escala -- ya es mínimo (0.1.sp) y no aporta al riesgo
// real de esta HU (texto cortado/desbordado).
fun Typography.scaledBy(factor: Float): Typography {
    fun TextStyle.scaled() =
        copy(
            fontSize = fontSize * factor,
            lineHeight = lineHeight * factor,
        )
    return copy(
        displayLarge = displayLarge.scaled(),
        displayMedium = displayMedium.scaled(),
        displaySmall = displaySmall.scaled(),
        headlineLarge = headlineLarge.scaled(),
        headlineMedium = headlineMedium.scaled(),
        headlineSmall = headlineSmall.scaled(),
        titleLarge = titleLarge.scaled(),
        titleMedium = titleMedium.scaled(),
        titleSmall = titleSmall.scaled(),
        bodyLarge = bodyLarge.scaled(),
        bodyMedium = bodyMedium.scaled(),
        bodySmall = bodySmall.scaled(),
        labelLarge = labelLarge.scaled(),
        labelMedium = labelMedium.scaled(),
        labelSmall = labelSmall.scaled(),
    )
}
