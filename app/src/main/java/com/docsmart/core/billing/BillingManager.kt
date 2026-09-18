package com.docsmart.core.billing

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.docsmart.core.premium.PremiumManager
import com.docsmart.core.util.AppLifecycleTracker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

sealed interface PurchaseResult {
    data object Success : PurchaseResult
    data object Cancelled : PurchaseResult
    data object Pending : PurchaseResult
    data object NoPurchasesToRestore : PurchaseResult
    data class Error(val debugMessage: String) : PurchaseResult
}

/**
 * HU-54: precio real de la fase recurrente de un producto (ya sin la fase de
 * prueba gratuita, que siempre tiene precio 0) más los días de esa prueba si
 * Play Console la tiene configurada para este producto.
 */
data class PlanOffer(val price: String, val trialDays: Int? = null)

/**
 * RF-PREM-05 (docs/requirements/settings-premium.md §7): reemplaza el
 * placeholder simulatePurchase() de PremiumManager por Play Billing real.
 *
 * NOTA — no verificable de punta a punta todavía: los productos
 * com.docsmart.premium.{monthly,annual} deben existir en Play
 * Console (Monetizar → Productos) antes de que queryProductDetails()
 * devuelva algo — y eso requiere que la app ya esté subida al menos a una
 * pista de prueba (ver docs/requirements/deployment.md). Hasta entonces,
 * esta clase se conecta a Play Billing correctamente pero no encuentra
 * productos reales.
 *
 * NOTA — verificación de compra: no se valida la firma de la compra contra
 * la clave pública de licencias de Play Console (RSA) porque esa clave solo
 * existe una vez que la app se crea en Play Console, y el proyecto no tiene
 * backend propio para verificar server-side (arquitectura documentada:
 * "solo Firebase gestionado"). Se confía en el resultado de BillingClient +
 * el chequeo de PurchaseState — razonable para una app de un solo
 * desarrollador sin backend, pero vale la pena revisar si el volumen de
 * fraude lo justifica más adelante.
 */
@Singleton
class BillingManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val premiumManager: PremiumManager,
    private val appLifecycleTracker: AppLifecycleTracker
) {
    companion object {
        const val PRODUCT_MONTHLY  = "com.docsmart.premium.monthly"
        const val PRODUCT_ANNUAL   = "com.docsmart.premium.annual"
        private val SUBSCRIPTION_PRODUCT_IDS = listOf(PRODUCT_MONTHLY, PRODUCT_ANNUAL)
        private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000
        // Hallazgo real de la auditoría general 2026-09-17 (séptima
        // ronda, Alta -- M1): restorePurchases() antes solo corría en la
        // conexión inicial del BillingClient, y BillingManager (Singleton
        // de Hilt) solo se construía la primera vez que algo lo inyectaba
        // -- el único punto de inyección era PremiumViewModel (la
        // pantalla Premium). Un usuario que cancelaba/pedía reembolso y
        // no volvía a abrir esa pantalla seguía viéndose Premium
        // indefinidamente, incluso entre reinicios de proceso (ver
        // DocuSmartApplication, ahora inyecta este Singleton en
        // onCreate()). Este throttle evita reconsultar Play Billing en
        // cada ON_START real de la app (cambiar de app y volver), sin
        // dejar pasar más de 4h sin revalidar mientras la app siga en uso.
        private const val REVALIDATE_THROTTLE_MS = 4 * 60 * 60 * 1000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val readyDeferred = CompletableDeferred<Boolean>()

    private val _purchaseResult = MutableSharedFlow<PurchaseResult>()
    val purchaseResult: SharedFlow<PurchaseResult> = _purchaseResult.asSharedFlow()

    // productId → precio real (ya sin la fase de prueba) + días de prueba si
    // los tiene, formateados y localizados por Play Store (ej. "$2.99").
    // Reemplaza el precio fijo hardcodeado en PremiumRepository en cuanto
    // Play Billing responde — antes de eso, la UI usa el precio de respaldo.
    private val _planOffers = MutableStateFlow<Map<String, PlanOffer>>(emptyMap())
    val planOffers: StateFlow<Map<String, PlanOffer>> = _planOffers.asStateFlow()

    private var productDetailsCache: Map<String, com.android.billingclient.api.ProductDetails> = emptyMap()
    // Hallazgo real de la revisión adversarial de esta misma ronda: al
    // principio se fijaba en `init{}` de forma incondicional, antes de
    // saber si `startConnection()` realmente iba a lograr conectar --
    // si la primera conexión fallaba (sin red, Play Store no disponible),
    // el throttle quedaba "gastado" sin haber revalidado nada, bloqueando
    // el mecanismo de respaldo de ON_START hasta por 4h. `null` significa
    // "todavía nunca se completó un intento real", y en ese estado el
    // throttle no bloquea nada.
    private var lastRestoreCheckElapsedMs: Long? = null

    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                if (purchases.isNullOrEmpty()) {
                    emitResult(PurchaseResult.Error("Compra sin resultado"))
                } else {
                    purchases.forEach { handlePurchase(it) }
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> emitResult(PurchaseResult.Cancelled)
            else -> emitResult(PurchaseResult.Error(billingResult.debugMessage))
        }
    }

    // enableOneTimeProducts() es obligatorio para PendingPurchasesParams.Builder.build()
    // en esta versión de Play Billing (9.1.0) -- lanza IllegalArgumentException
    // ("Pending purchases for one-time products must be supported") si se omite,
    // sin importar que el catálogo ya no tenga ningún producto INAPP (se quitó
    // "De por vida" el 2026-09-01). No es opcional pese al nombre.
    private val billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(purchasesUpdatedListener)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    init {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                val ready = billingResult.responseCode == BillingClient.BillingResponseCode.OK
                Timber.d("BillingManager: conexión lista=$ready (${billingResult.debugMessage})")
                if (!readyDeferred.isCompleted) readyDeferred.complete(ready)
                if (ready) {
                    lastRestoreCheckElapsedMs = SystemClock.elapsedRealtime()
                    scope.launch {
                        queryProductDetails()
                        restorePurchases()
                    }
                }
            }

            override fun onBillingServiceDisconnected() {
                Timber.w("BillingManager: servicio desconectado (reconexión automática habilitada)")
            }
        })
        appLifecycleTracker.addObserver(LifecycleEventObserver { _, event ->
            if (event != Lifecycle.Event.ON_START) return@LifecycleEventObserver
            val now = SystemClock.elapsedRealtime()
            val last = lastRestoreCheckElapsedMs
            if (last != null && now - last < REVALIDATE_THROTTLE_MS) return@LifecycleEventObserver
            lastRestoreCheckElapsedMs = now
            scope.launch { restorePurchases() }
        })
    }

    private suspend fun queryProductDetails() {
        val subsParams = QueryProductDetailsParams.newBuilder()
            .setProductList(SUBSCRIPTION_PRODUCT_IDS.map {
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(it)
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
            })
            .build()

        val subsResult = billingClient.queryProductDetails(subsParams)
        val allDetails = subsResult.productDetailsList.orEmpty()

        productDetailsCache = allDetails.associateBy { it.productId }
        _planOffers.value = allDetails.associate { it.productId to planOfferOf(it) }
        Timber.d("BillingManager: ${productDetailsCache.size} productos encontrados en Play Console")
    }

    // HU-54: si Play Console tiene configurada una fase de prueba gratuita
    // (freeTrialPeriod) para este producto, aparece como una fase más al
    // inicio de pricingPhaseList con priceAmountMicros = 0 -- se distingue
    // así en vez de por posición, porque Play Billing no garantiza que sea
    // siempre la primera. La fase recurrente real (lo que se le muestra al
    // usuario como precio) es la de mayor precio.
    private fun planOfferOf(details: com.android.billingclient.api.ProductDetails): PlanOffer {
        val phases = details.subscriptionOfferDetails
            ?.firstOrNull()?.pricingPhases?.pricingPhaseList.orEmpty()
        val trialPhase = phases.firstOrNull { it.priceAmountMicros == 0L }
        val recurringPhase = phases.maxByOrNull { it.priceAmountMicros } ?: phases.firstOrNull()
        return PlanOffer(
            price = recurringPhase?.formattedPrice ?: "",
            trialDays = trialPhase?.billingPeriod?.let(::iso8601PeriodToDays)
        )
    }

    /** Devuelve false si Play Billing no está listo o el producto no se encontró. */
    fun launchPurchase(activity: Activity, productId: String): Boolean {
        val billingFlowParams = buildPurchaseParams(productId) ?: return false
        val result = billingClient.launchBillingFlow(activity, billingFlowParams)
        return result.responseCode == BillingClient.BillingResponseCode.OK
    }

    private fun buildPurchaseParams(productId: String): BillingFlowParams? {
        val details = productDetailsCache[productId]
        val offerToken = details?.subscriptionOfferDetails?.firstOrNull()?.offerToken

        if (details == null || offerToken == null) {
            val message = if (details == null) {
                "Producto no disponible todavía"
            } else {
                "Sin oferta disponible para este plan"
            }
            emitResult(PurchaseResult.Error(message))
            return null
        }

        val paramsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .setOfferToken(offerToken)

        return BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(paramsBuilder.build()))
            .build()
    }

    suspend fun restorePurchases() {
        // Bug real corregido 2026-09-08: antes se llamaba a
        // queryPurchasesAsync() sin esperar a que la conexión con Play
        // Billing quedara lista (`readyDeferred` se completaba pero nunca se
        // esperaba en ningún lado) -- si esta función corría antes de que la
        // conexión terminara de establecerse, la consulta podía fallar o
        // devolver una lista vacía sin haber consultado nada de verdad.
        val ready = readyDeferred.await()
        if (!ready) {
            Timber.w("BillingManager: restorePurchases() -- la conexión con Play Billing nunca quedó lista")
            emitResult(PurchaseResult.Error("No se pudo conectar con Google Play"))
            return
        }

        val subs = billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
        )

        // Bug real corregido 2026-09-08: antes no se revisaba si la consulta
        // en sí había fallado (sin red, servicio de Play Store caído, etc.)
        // -- una consulta fallida devuelve `purchasesList` vacía, exactamente
        // igual que "el usuario genuinamente no tiene compras", así que esta
        // función desactivaba Premium a un usuario que sí había pagado, cada
        // vez que la app arranca (esto corre automáticamente en cada inicio,
        // no solo al tocar "Restaurar compras"). `evaluateRestoreOutcome()`
        // deja esa decisión como función pura testeable (ver BillingManagerTest)
        // para que este bug no pueda reaparecer sin que un test lo detecte.
        when (val outcome = evaluateRestoreOutcome(subs.billingResult.responseCode, subs.purchasesList)) {
            RestoreOutcome.QueryFailed -> {
                Timber.w(
                    "BillingManager: restorePurchases() -- la consulta falló, no se toca el estado " +
                        "Premium actual (${subs.billingResult.debugMessage})"
                )
                emitResult(PurchaseResult.Error(subs.billingResult.debugMessage))
            }
            RestoreOutcome.NothingOwned -> {
                premiumManager.deactivatePremium()
                emitResult(PurchaseResult.NoPurchasesToRestore)
            }
            is RestoreOutcome.Owned -> {
                outcome.purchases.forEach { handlePurchase(it, isRestore = true) }
            }
        }
    }

    private fun handlePurchase(purchase: Purchase, isRestore: Boolean = false) {
        when (purchase.purchaseState) {
            Purchase.PurchaseState.PURCHASED -> {
                premiumManager.activatePremium(trialEndsAtMillisFor(purchase))
                if (!purchase.isAcknowledged) {
                    scope.launch {
                        val ackParams = AcknowledgePurchaseParams.newBuilder()
                            .setPurchaseToken(purchase.purchaseToken)
                            .build()
                        val ackResult = billingClient.acknowledgePurchase(ackParams)
                        if (ackResult.responseCode != BillingClient.BillingResponseCode.OK) {
                            Timber.e("BillingManager: no se pudo confirmar la compra — ${ackResult.debugMessage}")
                        }
                    }
                }
                if (!isRestore) emitResult(PurchaseResult.Success)
            }
            Purchase.PurchaseState.PENDING -> {
                if (!isRestore) emitResult(PurchaseResult.Pending)
            }
            else -> {
                if (!isRestore) emitResult(PurchaseResult.Error("Estado de compra desconocido"))
            }
        }
    }

    private fun emitResult(result: PurchaseResult) {
        scope.launch { _purchaseResult.emit(result) }
    }

    // HU-54: Purchase no expone si esta compra específica arrancó con una
    // prueba gratuita -- se deduce comparando el producto comprado contra la
    // fase de prueba cacheada de queryProductDetails(). Si el producto no
    // tiene trial configurado, o la compra es vieja (restaurada mucho
    // después de que el trial terminó), el resultado ya queda en el pasado y
    // la UI simplemente no muestra nada -- no hace falta un flag aparte.
    private fun trialEndsAtMillisFor(purchase: Purchase): Long? {
        val productId = purchase.products.firstOrNull()
        val details = productId?.let { productDetailsCache[it] }
        val phases = details?.subscriptionOfferDetails
            ?.firstOrNull()?.pricingPhases?.pricingPhaseList.orEmpty()
        val trialDays = phases.firstOrNull { it.priceAmountMicros == 0L }
            ?.billingPeriod?.let(::iso8601PeriodToDays)
        return trialDays?.let { purchase.purchaseTime + it * MILLIS_PER_DAY }
    }
}

// HU-54: Play Billing describe la duración de cada fase de precio (incluida
// la de prueba gratuita) como una duración ISO-8601 simple -- "P7D", "P1W",
// "P1M", "P1Y", nunca combinaciones más complejas para este caso de uso.
// Mes/año se aproximan a 30/365 días a propósito: solo importan para
// mostrar "X días gratis" en la UI, no para ningún cálculo de facturación
// real (eso lo hace Play Billing del lado del servidor).
private val ISO8601_PERIOD_REGEX = Regex("^P(?:(\\d+)Y)?(?:(\\d+)M)?(?:(\\d+)W)?(?:(\\d+)D)?$")

internal fun iso8601PeriodToDays(period: String): Int? {
    val groups = ISO8601_PERIOD_REGEX.matchEntire(period)?.groupValues ?: return null
    val totalDays = groups[1].toIntOrNull().orZero() * 365 +
        groups[2].toIntOrNull().orZero() * 30 +
        groups[3].toIntOrNull().orZero() * 7 +
        groups[4].toIntOrNull().orZero()
    return totalDays.takeIf { it > 0 }
}

private fun Int?.orZero(): Int = this ?: 0

/**
 * Resultado puro de evaluar una respuesta de `queryPurchasesAsync()`,
 * extraído para poder testear la decisión sin construir `BillingManager`
 * (su constructor arma un `BillingClient` real -- ver `BillingManagerTest`).
 */
internal sealed interface RestoreOutcome {
    data object QueryFailed : RestoreOutcome
    data object NothingOwned : RestoreOutcome
    data class Owned(val purchases: List<Purchase>) : RestoreOutcome
}

internal fun evaluateRestoreOutcome(responseCode: Int, purchasesList: List<Purchase>): RestoreOutcome {
    if (responseCode != BillingClient.BillingResponseCode.OK) return RestoreOutcome.QueryFailed
    val owned = purchasesList.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
    return if (owned.isEmpty()) RestoreOutcome.NothingOwned else RestoreOutcome.Owned(owned)
}
