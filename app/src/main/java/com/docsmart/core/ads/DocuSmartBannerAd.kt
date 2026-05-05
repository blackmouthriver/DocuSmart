package com.docsmart.core.ads

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import timber.log.Timber

@Composable
fun DocuSmartBannerAd(
    adUnitId: String,
    adManager: AdManager,
    modifier: Modifier = Modifier
) {
    val isPremium by adManager.isPremium.collectAsState()
    val isInitialized by adManager.isInitialized.collectAsState()

    Timber.d("BannerAd: isPremium=$isPremium, isInitialized=$isInitialized")

    if (isPremium || !isInitialized) return

    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp),
        factory = { context ->
            try {
                Timber.d("BannerAd: creando AdView — adUnitId=$adUnitId")
                AdView(context).apply {
                    setAdSize(AdSize.BANNER)
                    this.adUnitId = adUnitId
                    loadAd(AdRequest.Builder().build())
                    adListener = object : com.google.android.gms.ads.AdListener() {
                        override fun onAdLoaded() {
                            Timber.d("BannerAd: ✅ anuncio cargado")
                        }
                        override fun onAdFailedToLoad(error: com.google.android.gms.ads.LoadAdError) {
                            Timber.e("BannerAd: ❌ error ${error.code}: ${error.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "BannerAd ERROR: ${e.message}")
                android.view.View(context)
            }
        }
    )
}

@Composable
fun AdBannerPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Publicidad",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        )
    }
}