package com.docsmart.core.billing

import android.app.Activity
import android.content.Context
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
 * RF-PREM-05 (docs/requirements/settings-premium.md §7): reemplaza el
 * placeholder simulatePurchase() de PremiumManager por Play Billing real.
 *
 * NOTA — no verificable de punta a punta todavía: los productos
 * com.docsmart.premium.{monthly,annual,lifetime} deben existir en Play
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
    private val premiumManager: PremiumManager
) {
    companion object {
        const val PRODUCT_MONTHLY  = "com.docsmart.premium.monthly"
        const val PRODUCT_ANNUAL   = "com.docsmart.premium.annual"
        const val PRODUCT_LIFETIME = "com.docsmart.premium.lifetime"
        private val SUBSCRIPTION_PRODUCT_IDS = listOf(PRODUCT_MONTHLY, PRODUCT_ANNUAL)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val readyDeferred = CompletableDeferred<Boolean>()

    private val _purchaseResult = MutableSharedFlow<PurchaseResult>()
    val purchaseResult: SharedFlow<PurchaseResult> = _purchaseResult.asSharedFlow()

    // productId → precio formateado y localizado por Play Store (ej. "$2.99").
    // Reemplaza el precio fijo hardcodeado en PremiumRepository en cuanto
    // Play Billing responde — antes de eso, la UI usa el precio de respaldo.
    private val _formattedPrices = MutableStateFlow<Map<String, String>>(emptyMap())
    val formattedPrices: StateFlow<Map<String, String>> = _formattedPrices.asStateFlow()

    private var productDetailsCache: Map<String, com.android.billingclient.api.ProductDetails> = emptyMap()

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
        val inAppParams = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(PRODUCT_LIFETIME)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
            ))
            .build()

        val subsResult = billingClient.queryProductDetails(subsParams)
        val inAppResult = billingClient.queryProductDetails(inAppParams)
        val allDetails = subsResult.productDetailsList.orEmpty() + inAppResult.productDetailsList.orEmpty()

        productDetailsCache = allDetails.associateBy { it.productId }
        _formattedPrices.value = allDetails.associate { details ->
            val price = details.oneTimePurchaseOfferDetails?.formattedPrice
                ?: details.subscriptionOfferDetails?.firstOrNull()
                    ?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice
                ?: ""
            details.productId to price
        }
        Timber.d("BillingManager: ${productDetailsCache.size} productos encontrados en Play Console")
    }

    /** Devuelve false si Play Billing no está listo o el producto no se encontró. */
    fun launchPurchase(activity: Activity, productId: String): Boolean {
        val billingFlowParams = buildPurchaseParams(productId) ?: return false
        val result = billingClient.launchBillingFlow(activity, billingFlowParams)
        return result.responseCode == BillingClient.BillingResponseCode.OK
    }

    private fun buildPurchaseParams(productId: String): BillingFlowParams? {
        val details = productDetailsCache[productId]
        val offerToken = details?.let { resolveOfferToken(productId, it) }
        val hasValidOffer = productId == PRODUCT_LIFETIME || offerToken != null

        if (details == null || !hasValidOffer) {
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
        offerToken?.let { paramsBuilder.setOfferToken(it) }

        return BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(paramsBuilder.build()))
            .build()
    }

    // INAPP (lifetime) no usa offerToken; SUBS toma la oferta del plan base.
    private fun resolveOfferToken(productId: String, details: com.android.billingclient.api.ProductDetails): String? =
        if (productId == PRODUCT_LIFETIME) null
        else details.subscriptionOfferDetails?.firstOrNull()?.offerToken

    suspend fun restorePurchases() {
        val subs = billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
        )
        val inApp = billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        )
        val owned = (subs.purchasesList + inApp.purchasesList)
            .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }

        if (owned.isEmpty()) {
            premiumManager.deactivatePremium()
            emitResult(PurchaseResult.NoPurchasesToRestore)
            return
        }
        owned.forEach { handlePurchase(it, isRestore = true) }
    }

    private fun handlePurchase(purchase: Purchase, isRestore: Boolean = false) {
        when (purchase.purchaseState) {
            Purchase.PurchaseState.PURCHASED -> {
                premiumManager.activatePremium()
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
}
