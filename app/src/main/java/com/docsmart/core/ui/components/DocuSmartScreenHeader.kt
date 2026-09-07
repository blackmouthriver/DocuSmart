package com.docsmart.core.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.docsmart.core.ads.AdManager
import com.docsmart.core.ads.DocuSmartBannerAd

// Márgenes/espaciados únicos para el banner de anuncios + el banner de
// título de cada pantalla (pedido explícito del usuario 2026-09-07: se
// veían de tamaños y separaciones distintas entre pantallas -- Inicio
// usaba 24dp de margen superior y 24dp entre anuncio y banner, Convertidor/
// Herramientas PDF sumaban de más por un padding propio del banner, etc.).
// Un solo lugar para estos 3 números evita que vuelvan a divergir.
private val SCREEN_HEADER_HORIZONTAL_MARGIN = 16.dp
private val SCREEN_HEADER_AD_TO_BANNER_GAP = 8.dp

/**
 * Agrupa el banner de anuncios (AdMob, oculto para Premium) y el banner de
 * título de la pantalla en un solo bloque, con el mismo margen horizontal y
 * la misma separación entre ambos en todas las pantallas que los usan
 * juntos (Inicio, Biblioteca, Convertidor, Herramientas PDF, Escáner).
 * `bannerBottomSpacing` es opcional -- solo lo necesitan pantallas cuyo
 * espaciado general entre secciones no alcanza a separar el banner del
 * contenido siguiente por sí solo (ej. Convertidor, que usa `spacedBy(0.dp)`
 * a propósito para el resto de su contenido).
 */
@Composable
fun DocuSmartScreenHeader(
    adUnitId: String,
    adManager: AdManager,
    modifier: Modifier = Modifier,
    bannerBottomSpacing: Dp = 0.dp,
    banner: @Composable () -> Unit
) {
    val isPremium by adManager.isPremium.collectAsState()
    Column(modifier = modifier.padding(horizontal = SCREEN_HEADER_HORIZONTAL_MARGIN)) {
        if (!isPremium) {
            DocuSmartBannerAd(adUnitId = adUnitId, adManager = adManager)
            Spacer(Modifier.height(SCREEN_HEADER_AD_TO_BANNER_GAP))
        }
        banner()
        if (bannerBottomSpacing > 0.dp) {
            Spacer(Modifier.height(bannerBottomSpacing))
        }
    }
}
