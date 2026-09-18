package com.docsmart

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.docsmart.core.analytics.CrashlyticsTree
import com.docsmart.core.billing.BillingManager
import com.docsmart.core.media.PdfThumbnailFetcher
import com.docsmart.core.premium.PremiumManager
import com.docsmart.core.remoteconfig.RemoteConfigManager
import com.docsmart.core.security.SecurityManager
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class DocuSmartApplication : Application(), ImageLoaderFactory {

    @Inject lateinit var remoteConfigManager: RemoteConfigManager

    // Bug real corregido 2026-09-07 (ya no aplica desde la consolidación
    // 2026-09-09 de la doble fuente de verdad Premium/AdManager --
    // AdManager.isPremium ahora es un passthrough directo de
    // PremiumManager.isPremium, correcto en cuanto se lee, sin ningún paso
    // de sincronización que forzar). Se deja esta inyección temprana solo
    // para loguear el estado Premium al arrancar.
    @Inject lateinit var premiumManager: PremiumManager

    @Inject lateinit var securityManager: SecurityManager

    // Hallazgo real de la auditoría general 2026-09-17 (séptima ronda,
    // Alta -- M1): BillingManager es un Singleton de Hilt que solo se
    // construía la primera vez que algo lo inyectaba -- el único punto de
    // inyección en toda la app era PremiumViewModel (la pantalla
    // Premium). Un usuario que cancelaba/pedía reembolso de su suscripción
    // y no volvía a abrir esa pantalla seguía viéndose Premium
    // indefinidamente, incluso entre reinicios de proceso, porque
    // restorePurchases() (que sí revalida contra Play Billing y
    // desactiva Premium si corresponde) nunca volvía a correr. Esta
    // inyección temprana garantiza al menos una revalidación real en
    // cada arranque en frío -- BillingManager también se re-suscribe a
    // ON_START vía AppLifecycleTracker para cubrir sesiones largas sin
    // reinicio de proceso (ver BillingManager.kt).
    @Inject lateinit var billingManager: BillingManager

    override fun onCreate() {
        super.onCreate()

        // Hallazgo real de la auditoría general 2026-09-17 (sexta ronda,
        // Media -- S3): clearPreviewCache() solo se llamaba al crear la
        // siguiente copia de vista previa o al bloquear Carpeta Segura en
        // ON_STOP -- si el proceso moría de forma abrupta (crash, OOM-kill
        // del sistema) mientras existía una copia efímera sin cifrar en
        // cacheDir/secure_preview/, esa copia quedaba en disco sin ninguna
        // forma de purgarla desde la UI. Barrido defensivo en cada
        // arranque en frío.
        securityManager.clearPreviewCache()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        } else {
            // En debug los Timber.e/Timber.w ya se ven en Logcat -- en
            // release, sin ningún árbol plantado, se perdían por completo
            // (bug real corregido 2026-09-03, encontrado al arreglar la
            // integración de Firebase: ningún error de producción llegaba
            // a ningún lado antes de esto).
            Timber.plant(CrashlyticsTree())
        }

        Timber.d("DocuSmartApplication: estado Premium al arrancar = ${premiumManager.isPremium.value}")
        remoteConfigManager.refresh()
    }

    // Miniaturas de PDF en las listas de documentos (backlog UX #25) --
    // Coil llama a este método una sola vez y reutiliza el mismo
    // ImageLoader (con su caché) en toda la app, así que basta con
    // registrar el Fetcher acá para que `AsyncImage`/`SubcomposeAsyncImage`
    // funcionen igual para PDFs que para imágenes normales en cualquier
    // pantalla, sin tener que pasar un ImageLoader propio en cada uso.
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components {
                add(PdfThumbnailFetcher.UriFactory())
                add(PdfThumbnailFetcher.FileFactory())
            }
            .build()
}
