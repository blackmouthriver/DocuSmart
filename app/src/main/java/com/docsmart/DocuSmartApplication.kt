package com.docsmart

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.docsmart.core.analytics.CrashlyticsTree
import com.docsmart.core.media.PdfThumbnailFetcher
import com.docsmart.core.premium.PremiumManager
import com.docsmart.core.remoteconfig.RemoteConfigManager
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class DocuSmartApplication : Application(), ImageLoaderFactory {

    @Inject lateinit var remoteConfigManager: RemoteConfigManager

    // Bug real corregido 2026-09-07: PremiumManager solo se construía al
    // abrir la pantalla Premium (única que lo inyecta), y solo al
    // construirse sincroniza el estado Premium persistido con AdManager
    // -- así que un suscriptor real que no visitaba esa pantalla seguía
    // viendo anuncios. Se inyecta acá para forzar esa construcción (y su
    // sincronización) desde el arranque de la app.
    @Inject lateinit var premiumManager: PremiumManager

    override fun onCreate() {
        super.onCreate()

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
