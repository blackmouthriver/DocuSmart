package com.docsmart.core.billing

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.docsmart.core.analytics.DocuSmartAnalytics
import com.docsmart.core.premium.PremiumManager
import com.docsmart.core.util.AppLifecycleTracker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

sealed interface PurchaseResult {
    data object Success : PurchaseResult

    data object Cancelled : PurchaseResult

    data object Pending : PurchaseResult

    data object NoPurchasesToRestore : PurchaseResult

    data class Error(
        val debugMessage: String,
    ) : PurchaseResult
}

/**
 * HU-54: precio real de la fase recurrente de un producto (ya sin la fase de
 * prueba gratuita, que siempre tiene precio 0) más los días de esa prueba si
 * Play Console la tiene configurada para este producto.
 */
data class PlanOffer(
    val price: String,
    val trialDays: Int? = null,
)

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
class BillingManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val premiumManager: PremiumManager,
        private val appLifecycleTracker: AppLifecycleTracker,
    ) {
        companion object {
            const val PRODUCT_MONTHLY = "com.docsmart.premium.monthly"
            const val PRODUCT_ANNUAL = "com.docsmart.premium.annual"
            private val SUBSCRIPTION_PRODUCT_IDS = listOf(PRODUCT_MONTHLY, PRODUCT_ANNUAL)

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

            // Hallazgo 5 (auditoría monetización 2026-09-18, Baja): un solo
            // reintento inmediato tras un delay corto -- no vale la pena un
            // backoff más elaborado para un fallo puntual de acknowledgePurchase()
            // (red intermitente), pero tampoco hay que dejar la compra sin
            // confirmar para siempre sin al menos un segundo intento.
            private const val ACK_RETRY_DELAY_MS = 1500L
        }

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        // Hallazgo 1 (auditoría monetización 2026-09-18, Alta): antes era un
        // `val` -- un CompletableDeferred solo puede completarse UNA VEZ, así que
        // si la primerísima conexión con Play Billing fallaba (sin red,
        // BILLING_UNAVAILABLE), quedaba fijado en `false` para siempre y
        // restorePurchases() (readyDeferred.await()) cortaba con error
        // indefinidamente, incluso si enableAutoServiceReconnection() lograba
        // reconectar después y onBillingSetupFinished volvía a llamarse con
        // ready=true -- esa segunda finalización se ignoraba silenciosamente
        // (`if (!readyDeferred.isCompleted)`). Ahora es un `var`: ver
        // completeReady() más abajo, que reemplaza el Deferred por uno nuevo
        // cuando el actual ya estaba resuelto, para que awaits posteriores
        // reflejen el estado ACTUAL de conexión y no uno cacheado del pasado.
        @Volatile
        private var readyDeferred = CompletableDeferred<Boolean>()

        private val _purchaseResult = MutableSharedFlow<PurchaseResult>()
        val purchaseResult: SharedFlow<PurchaseResult> = _purchaseResult.asSharedFlow()

        // productId → precio real (ya sin la fase de prueba) + días de prueba si
        // los tiene, formateados y localizados por Play Store (ej. "$2.99").
        // Reemplaza el precio fijo hardcodeado en PremiumRepository en cuanto
        // Play Billing responde — antes de eso, la UI usa el precio de respaldo.
        private val _planOffers = MutableStateFlow<Map<String, PlanOffer>>(emptyMap())
        val planOffers: StateFlow<Map<String, PlanOffer>> = _planOffers.asStateFlow()

        @Volatile
        private var productDetailsCache: Map<String, ProductDetails> = emptyMap()

        // Hallazgo real de la revisión adversarial de esta misma ronda: al
        // principio se fijaba en `init{}` de forma incondicional, antes de
        // saber si `startConnection()` realmente iba a lograr conectar --
        // si la primera conexión fallaba (sin red, Play Store no disponible),
        // el throttle quedaba "gastado" sin haber revalidado nada, bloqueando
        // el mecanismo de respaldo de ON_START hasta por 4h. `null` significa
        // "todavía nunca se completó un intento real", y en ese estado el
        // throttle no bloquea nada.
        @Volatile
        private var lastRestoreCheckElapsedMs: Long? = null

        // Hallazgo 6 (auditoría monetización 2026-09-18, Baja): en una carrera
        // estrecha entre purchasesUpdatedListener (compra recién hecha) y una
        // revalidación de ON_START simultánea, la misma compra podía procesarse
        // dos veces antes de que isAcknowledged pasara a true, duplicando el
        // evento de conversión. Se deduplica por purchaseToken en memoria --
        // alcanza para el proceso actual, no necesita persistir entre reinicios
        // porque isAcknowledged ya cubre esa ventana más larga.
        private val loggedPurchaseTokens = ConcurrentHashMap.newKeySet<String>()

        private val purchasesUpdatedListener =
            PurchasesUpdatedListener { billingResult, purchases ->
                when (purchaseUpdateActionFor(billingResult.responseCode, purchases?.size ?: 0)) {
                    PurchaseUpdateAction.HANDLE_PURCHASES -> purchases?.forEach { handlePurchase(it) }
                    PurchaseUpdateAction.EMPTY_RESULT -> emitResult(PurchaseResult.Error("Compra sin resultado"))
                    PurchaseUpdateAction.CANCELLED -> emitResult(PurchaseResult.Cancelled)
                    // Ya es dueño de la suscripción (Premium local perdido o
                    // desincronizado): no es un error real, se resincroniza.
                    PurchaseUpdateAction.ALREADY_OWNED -> recoverAlreadyOwned()
                    PurchaseUpdateAction.FAILED -> emitResult(PurchaseResult.Error(billingResult.debugMessage))
                }
            }

        // enableOneTimeProducts() es obligatorio para PendingPurchasesParams.Builder.build()
        // en esta versión de Play Billing (9.1.0) -- lanza IllegalArgumentException
        // ("Pending purchases for one-time products must be supported") si se omite,
        // sin importar que el catálogo ya no tenga ningún producto INAPP (se quitó
        // "De por vida" el 2026-09-01). No es opcional pese al nombre.
        private val billingClient: BillingClient =
            BillingClient
                .newBuilder(context)
                .setListener(purchasesUpdatedListener)
                .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
                .enableAutoServiceReconnection()
                .build()

        init {
            billingClient.startConnection(
                object : BillingClientStateListener {
                    override fun onBillingSetupFinished(billingResult: BillingResult) {
                        val ready = billingResult.responseCode == BillingClient.BillingResponseCode.OK
                        Timber.d("BillingManager: conexión lista=$ready (${billingResult.debugMessage})")
                        completeReady(ready)
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
                },
            )
            appLifecycleTracker.addObserver(
                LifecycleEventObserver { _, event ->
                    if (event != Lifecycle.Event.ON_START) return@LifecycleEventObserver
                    val now = SystemClock.elapsedRealtime()
                    if (!shouldRevalidateOnStart(now, lastRestoreCheckElapsedMs, REVALIDATE_THROTTLE_MS)) {
                        return@LifecycleEventObserver
                    }
                    lastRestoreCheckElapsedMs = now
                    scope.launch { restorePurchases() }
                },
            )
        }

        // Hallazgo 1: si el Deferred actual ya estaba resuelto (típicamente en
        // `false`, de un intento de conexión anterior fallido), no tiene sentido
        // "completarlo" de nuevo -- Play Billing simplemente ignora esa segunda
        // llamada. Se reemplaza por un Deferred nuevo ya resuelto con el
        // resultado ACTUAL, para que cualquier await() posterior (restorePurchases()
        // llamado después de este punto) vea el estado real de la conexión en
        // vez de quedar pegado al primer resultado cacheado para siempre.
        // `synchronized` porque Play Billing puede invocar onBillingSetupFinished
        // desde su propio hilo interno, y esto se lee/escribe también desde las
        // corrutinas de `scope` (Dispatchers.IO).
        private fun completeReady(ready: Boolean) =
            synchronized(this) {
                val current = readyDeferred
                if (current.isCompleted) {
                    readyDeferred = CompletableDeferred<Boolean>().apply { complete(ready) }
                } else {
                    current.complete(ready)
                }
            }

        @Suppress("TooGenericExceptionCaught")
        private suspend fun queryProductDetails() {
            try {
                val subsParams =
                    QueryProductDetailsParams
                        .newBuilder()
                        .setProductList(
                            SUBSCRIPTION_PRODUCT_IDS.map {
                                QueryProductDetailsParams.Product
                                    .newBuilder()
                                    .setProductId(it)
                                    .setProductType(BillingClient.ProductType.SUBS)
                                    .build()
                            },
                        ).build()

                val subsResult = billingClient.queryProductDetails(subsParams)
                val allDetails = subsResult.productDetailsList.orEmpty()

                productDetailsCache = allDetails.associateBy { it.productId }
                _planOffers.value = allDetails.associate { it.productId to planOfferOf(it) }
                Timber.d("BillingManager: ${productDetailsCache.size} productos encontrados en Play Console")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Hallazgo 2 (auditoría monetización 2026-09-18, Media): esta
                // corrutina corre en `scope` (SupervisorJob, sin manejador de
                // excepciones) desde DocuSmartApplication.onCreate() -- una
                // excepción real de Play Billing acá (no solo un
                // BillingResponseCode de error) tumbaba el proceso entero para
                // cualquier usuario, no solo en la pantalla Premium.
                Timber.e(
                    "BillingManager: excepción al consultar productos de Play Billing (${e.javaClass.simpleName})",
                )
            }
        }

        // HU-54: si Play Console tiene configurada una fase de prueba gratuita
        // (freeTrialPeriod) para este producto, aparece como una fase más al
        // inicio de pricingPhaseList con priceAmountMicros = 0 -- se distingue
        // así en vez de por posición, porque Play Billing no garantiza que sea
        // siempre la primera. La fase recurrente real (lo que se le muestra al
        // usuario como precio) es la de mayor precio.
        private fun planOfferOf(details: ProductDetails): PlanOffer = planOfferFor(details)

        /** Devuelve false si Play Billing no está listo o el producto no se encontró. */
        fun launchPurchase(
            activity: Activity,
            productId: String,
        ): Boolean {
            val billingFlowParams = buildPurchaseParams(productId) ?: return false
            val result = billingClient.launchBillingFlow(activity, billingFlowParams)
            return when (result.responseCode) {
                BillingClient.BillingResponseCode.OK -> true
                // El usuario ya tiene la suscripción: en vez de un error, se
                // resincroniza el estado y se resuelve como compra exitosa.
                BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                    recoverAlreadyOwned()
                    true
                }
                else -> false
            }
        }

        // Si la restauración encontró la compra (Success), se emite Success para
        // que la pantalla salga del estado "comprando"; los demás desenlaces ya
        // los emite restorePurchases() por su cuenta.
        private fun recoverAlreadyOwned() {
            scope.launch {
                if (restorePurchases() is PurchaseResult.Success) emitResult(PurchaseResult.Success)
            }
        }

        private fun buildPurchaseParams(productId: String): BillingFlowParams? {
            val details = productDetailsCache[productId]
            val offerToken = details?.subscriptionOfferDetails?.firstOrNull()?.offerToken

            if (details == null || offerToken == null) {
                emitResult(PurchaseResult.Error(purchaseUnavailableMessage(hasDetails = details != null)))
                return null
            }

            val paramsBuilder =
                BillingFlowParams.ProductDetailsParams
                    .newBuilder()
                    .setProductDetails(details)
                    .setOfferToken(offerToken)

            return BillingFlowParams
                .newBuilder()
                .setProductDetailsParamsList(listOf(paramsBuilder.build()))
                .build()
        }

        /**
         * Devuelve el desenlace además de emitirlo por [purchaseResult]: quien
         * espera la restauración (PremiumViewModel) no puede depender del orden
         * de llegada de la emisión asíncrona. Una restauración que encuentra
         * compras devuelve [PurchaseResult.Success] SIN emitirlo (no es una
         * conversión ni una compra nueva).
         */
        @Suppress("TooGenericExceptionCaught")
        suspend fun restorePurchases(): PurchaseResult {
            // Bug real corregido 2026-09-08: antes se llamaba a
            // queryPurchasesAsync() sin esperar a que la conexión con Play
            // Billing quedara lista -- ver completeReady().
            val ready = readyDeferred.await()
            if (!ready) {
                Timber.w("BillingManager: restorePurchases() -- la conexión con Play Billing nunca quedó lista")
                return emitAndReturn(PurchaseResult.Error("No se pudo conectar con Google Play"))
            }
            return try {
                queryAndApplyRestore()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Hallazgo 2 (auditoría monetización 2026-09-18): una excepción
                // real acá no debe tumbar el proceso. Solo se registra el tipo
                // (CrashlyticsTree reenvía todo >= WARN a Firebase).
                Timber.e("BillingManager: excepción al restaurar compras (${e.javaClass.simpleName})")
                // Deja de contar como revalidación hecha: el próximo ON_START reintenta.
                lastRestoreCheckElapsedMs = null
                emitAndReturn(PurchaseResult.Error("Error al restaurar compras"))
            }
        }

        private suspend fun queryAndApplyRestore(): PurchaseResult {
            val subs =
                billingClient.queryPurchasesAsync(
                    QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build(),
                )
            // Bug real corregido 2026-09-08: una consulta fallida devuelve
            // `purchasesList` vacía igual que "no tiene compras" y desactivaba
            // Premium a un usuario que sí pagó. Ver evaluateRestoreOutcome().
            return when (val outcome = evaluateRestoreOutcome(subs.billingResult.responseCode, subs.purchasesList)) {
                RestoreOutcome.QueryFailed -> {
                    Timber.w(
                        "BillingManager: restorePurchases() -- la consulta falló, no se toca el estado " +
                            "Premium actual (${subs.billingResult.debugMessage})",
                    )
                    // Una consulta fallida no cuenta como revalidación: sin esto el
                    // throttle de 4h bloqueaba el reintento de ON_START.
                    lastRestoreCheckElapsedMs = null
                    emitAndReturn(PurchaseResult.Error(subs.billingResult.debugMessage))
                }
                RestoreOutcome.NothingOwned -> {
                    premiumManager.deactivatePremium()
                    emitAndReturn(PurchaseResult.NoPurchasesToRestore)
                }
                is RestoreOutcome.Owned -> {
                    outcome.purchases.forEach { handlePurchase(it, isRestore = true) }
                    PurchaseResult.Success
                }
                is RestoreOutcome.Pending -> {
                    // Hallazgo 3: una compra pendiente nunca activó Premium, no hay
                    // nada que desactivar; solo se evita el mensaje de "sin compras".
                    Timber.d("BillingManager: restorePurchases() -- compra(s) pendiente(s) de confirmación")
                    emitAndReturn(PurchaseResult.Pending)
                }
            }
        }

        private fun emitAndReturn(result: PurchaseResult): PurchaseResult {
            emitResult(result)
            return result
        }

        @Suppress("TooGenericExceptionCaught")
        private fun handlePurchase(
            purchase: Purchase,
            isRestore: Boolean = false,
        ) {
            val decision = purchaseStateDecisionFor(purchase.purchaseState, isRestore)
            if (decision.activatePremium) {
                run {
                    premiumManager.activatePremium(trialEndsAtMillisFor(purchase))
                    // Hallazgo real de la auditoría general 2026-09-17 (octava
                    // ronda, Alta -- G1): solo existía `logPremiumPurchaseAttempt()`,
                    // nunca un evento de conversión real -- el dashboard de
                    // Firebase no podía distinguir "intentos" de compras
                    // efectivamente concretadas, el dato de negocio más
                    // crítico de toda la monetización. Se excluye `isRestore`
                    // a propósito: una restauración automática en cada
                    // `ON_START` (ver M1, séptima ronda) no es una conversión
                    // nueva, solo re-confirma una compra ya existente --
                    // contarla inflaría la métrica cada vez que el usuario
                    // reabre la app.
                    //
                    // Hallazgo real de la revisión adversarial de esta misma
                    // ronda (Media): faltaba también excluir `isAcknowledged`
                    // -- `purchasesUpdatedListener` puede reentregar la MISMA
                    // compra sin confirmar más de una vez (reconexión del
                    // BillingClient, recreación de la Activity a mitad del
                    // flujo de compra, escenario documentado de Play Billing),
                    // y cada reentrega volvía a disparar el evento de
                    // conversión. `isAcknowledged` ya es la misma guarda
                    // idempotente que evita duplicar `acknowledgePurchase()`
                    // más abajo -- una vez confirmada la compra, cualquier
                    // reentrega posterior llega con `isAcknowledged=true` y
                    // ya no debe contarse de nuevo.
                    if (shouldLogConversion(isRestore, purchase.isAcknowledged) &&
                        loggedPurchaseTokens.add(purchase.purchaseToken)
                    ) {
                        DocuSmartAnalytics.logPremiumPurchaseSuccess(purchase.products.firstOrNull().orEmpty())
                    }
                    if (!purchase.isAcknowledged) {
                        scope.launch {
                            try {
                                val ackResult = acknowledgePurchaseWithRetry(purchase)
                                if (ackResult.responseCode != BillingClient.BillingResponseCode.OK) {
                                    Timber.e(
                                        "BillingManager: no se pudo confirmar la compra tras reintento — " +
                                            ackResult.debugMessage,
                                    )
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                // Hallazgo 2: idem queryProductDetails()/restorePurchases().
                                Timber.e("BillingManager: excepción al confirmar la compra (${e.javaClass.simpleName})")
                            }
                        }
                    }
                }
            }
            decision.emit?.let { emitResult(it) }
        }

        // Hallazgo 5 (auditoría monetización 2026-09-18, Baja): antes un fallo
        // de acknowledgePurchase() (red intermitente) se registraba y se
        // abandonaba -- Play Billing revierte automáticamente una compra no
        // confirmada a los 3 días, así que un usuario con mala señal justo en
        // ese momento podía perder la compra sin ningún reintento. Un solo
        // reintento inmediato tras un delay corto cubre el caso común sin
        // complicar la lógica con un backoff completo.
        private suspend fun acknowledgePurchaseWithRetry(purchase: Purchase): BillingResult {
            val ackParams =
                AcknowledgePurchaseParams
                    .newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
            val first = billingClient.acknowledgePurchase(ackParams)
            if (first.responseCode == BillingClient.BillingResponseCode.OK) return first
            Timber.w("BillingManager: acknowledgePurchase() falló (${first.debugMessage}), reintentando una vez...")
            delay(ACK_RETRY_DELAY_MS)
            return billingClient.acknowledgePurchase(ackParams)
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
        private fun trialEndsAtMillisFor(purchase: Purchase): Long? =
            trialEndsAtMillisOf(purchase, purchase.products.firstOrNull()?.let { productDetailsCache[it] })
    }

// Funciones puras extraídas de BillingManager (su constructor arma un
// BillingClient real y no se puede instanciar en un test JVM).
//
// HU-54: la fase de prueba gratuita aparece como una fase más de
// pricingPhaseList con priceAmountMicros = 0 (se distingue así, no por
// posición); la fase recurrente real (el precio mostrado) es la de mayor precio.
internal fun planOfferFor(details: ProductDetails): PlanOffer {
    val phases = pricingPhasesOf(details)
    val trialPhase = phases.firstOrNull { it.priceAmountMicros == 0L }
    val recurringPhase = phases.maxByOrNull { it.priceAmountMicros } ?: phases.firstOrNull()
    return PlanOffer(
        price = recurringPhase?.formattedPrice ?: "",
        trialDays = trialPhase?.billingPeriod?.let(::iso8601PeriodToDays),
    )
}

// Purchase no expone si la compra arrancó con prueba gratuita: se deduce de la
// fase de prueba cacheada del producto. Sin detalles o sin trial -> null.
internal fun trialEndsAtMillisOf(
    purchase: Purchase,
    details: ProductDetails?,
): Long? {
    val trialDays =
        details
            ?.let(::pricingPhasesOf)
            .orEmpty()
            .firstOrNull { it.priceAmountMicros == 0L }
            ?.billingPeriod
            ?.let(::iso8601PeriodToDays)
    return trialDays?.let { purchase.purchaseTime + it * MILLIS_PER_DAY }
}

private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000

private fun pricingPhasesOf(details: ProductDetails): List<ProductDetails.PricingPhase> =
    details.subscriptionOfferDetails
        ?.firstOrNull()
        ?.pricingPhases
        ?.pricingPhaseList
        .orEmpty()

// HU-54: Play Billing describe la duración de cada fase de precio (incluida
// la de prueba gratuita) como una duración ISO-8601 simple -- "P7D", "P1W",
// "P1M", "P1Y", nunca combinaciones más complejas para este caso de uso.
// Mes/año se aproximan a 30/365 días a propósito: solo importan para
// mostrar "X días gratis" en la UI, no para ningún cálculo de facturación
// real (eso lo hace Play Billing del lado del servidor).
private val ISO8601_PERIOD_REGEX = Regex("^P(?:(\\d+)Y)?(?:(\\d+)M)?(?:(\\d+)W)?(?:(\\d+)D)?$")

internal fun iso8601PeriodToDays(period: String): Int? {
    val groups = ISO8601_PERIOD_REGEX.matchEntire(period)?.groupValues ?: return null
    val totalDays =
        groups[1].toIntOrNull().orZero() * 365 +
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

    data class Owned(
        val purchases: List<Purchase>,
    ) : RestoreOutcome

    // Hallazgo 3 (auditoría monetización 2026-09-18, Media): antes una
    // compra PENDING (sin ninguna PURCHASED) caía en NothingOwned, exactamente
    // igual que "nunca compró nada" -- ver razonamiento completo en el
    // llamador (restorePurchases()).
    data class Pending(
        val purchases: List<Purchase>,
    ) : RestoreOutcome
}

internal fun evaluateRestoreOutcome(
    responseCode: Int,
    purchasesList: List<Purchase>,
): RestoreOutcome {
    if (responseCode != BillingClient.BillingResponseCode.OK) return RestoreOutcome.QueryFailed
    val owned = purchasesList.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
    val pending = purchasesList.filter { it.purchaseState == Purchase.PurchaseState.PENDING }
    // detekt (ReturnCount, máx. 2): un solo `when` en vez de un tercer `return` anticipado.
    return when {
        owned.isNotEmpty() -> RestoreOutcome.Owned(owned)
        pending.isNotEmpty() -> RestoreOutcome.Pending(pending)
        else -> RestoreOutcome.NothingOwned
    }
}

/** Qué hacer ante una llamada de `PurchasesUpdatedListener` (lógica pura, testeable sin BillingClient). */
internal enum class PurchaseUpdateAction { HANDLE_PURCHASES, EMPTY_RESULT, CANCELLED, ALREADY_OWNED, FAILED }

internal fun purchaseUpdateActionFor(
    responseCode: Int,
    purchaseCount: Int,
): PurchaseUpdateAction =
    when (responseCode) {
        BillingClient.BillingResponseCode.OK ->
            if (purchaseCount > 0) PurchaseUpdateAction.HANDLE_PURCHASES else PurchaseUpdateAction.EMPTY_RESULT
        BillingClient.BillingResponseCode.USER_CANCELED -> PurchaseUpdateAction.CANCELLED
        BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> PurchaseUpdateAction.ALREADY_OWNED
        else -> PurchaseUpdateAction.FAILED
    }

/**
 * Throttle de la revalidación en ON_START: `last == null` significa que nunca se
 * completó un intento real, y en ese estado no se bloquea nada.
 */
internal fun shouldRevalidateOnStart(
    nowElapsedMs: Long,
    lastElapsedMs: Long?,
    throttleMs: Long,
): Boolean = lastElapsedMs == null || nowElapsedMs - lastElapsedMs >= throttleMs

internal fun purchaseUnavailableMessage(hasDetails: Boolean): String =
    if (hasDetails) "Sin oferta disponible para este plan" else "Producto no disponible todavía"

/**
 * Efecto de una compra según su estado: si activa Premium y qué resultado se
 * emite. Una restauración (`isRestore`) nunca emite (no es una compra nueva);
 * solo PURCHASED activa Premium.
 */
internal data class PurchaseStateDecision(
    val activatePremium: Boolean,
    val emit: PurchaseResult?,
)

internal fun purchaseStateDecisionFor(
    purchaseState: Int,
    isRestore: Boolean,
): PurchaseStateDecision =
    when (purchaseState) {
        Purchase.PurchaseState.PURCHASED ->
            PurchaseStateDecision(true, if (isRestore) null else PurchaseResult.Success)
        Purchase.PurchaseState.PENDING ->
            PurchaseStateDecision(false, if (isRestore) null else PurchaseResult.Pending)
        else ->
            PurchaseStateDecision(
                false,
                if (isRestore) null else PurchaseResult.Error("Estado de compra desconocido"),
            )
    }

/**
 * El evento de conversión solo cuenta una compra nueva y aún sin confirmar:
 * una restauración o una reentrega ya confirmada no es una conversión nueva.
 */
internal fun shouldLogConversion(
    isRestore: Boolean,
    isAcknowledged: Boolean,
): Boolean = !isRestore && !isAcknowledged
