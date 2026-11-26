package com.docsmart.spike

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.webkit.WebViewAssetLoader
import java.io.File
import java.io.IOException

/**
 * SPIKE (WP2), solo debug: sirve el build de la web empaquetado en `assets/web` desde un origen propio
 * y deja medir carga, cabeceras, selector de archivos, descargas, redes bloqueadas y memoria.
 *
 *   adb shell am start -n com.docsmart/com.docsmart.spike.WebSpikeActivity --es path /comprimir-pdf
 *
 * Los registros salen con la etiqueta `WebSpike`. No forma parte de release.
 */
class WebSpikeActivity : ComponentActivity() {
    private lateinit var webView: WebView
    private var pendingChooser: ValueCallback<Array<Uri>>? = null
    private var pageStartedAt = 0L

    private val chooserLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val uris = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
            Log.i(TAG, "selector de archivos -> ${uris?.size ?: 0} archivo(s)")
            pendingChooser?.onReceiveValue(uris)
            pendingChooser = null
        }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WebView.setWebContentsDebuggingEnabled(true)
        val assetLoader =
            WebViewAssetLoader
                .Builder()
                .setDomain(HOST)
                .setHttpAllowed(false)
                .addPathHandler("/", WebAssetHandler(assets))
                .build()

        webView = WebView(this)
        setContentView(webView)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            cacheMode = WebSettings.LOAD_DEFAULT
            mediaPlaybackRequiresUserGesture = true
        }
        webView.addJavascriptInterface(SpikeBridge(), "SpikeBridge")
        webView.webViewClient = buildClient(assetLoader)
        webView.webChromeClient = buildChromeClient()

        val path = intent.getStringExtra("path") ?: "/app/"
        val url = "https://$HOST$path"
        Log.i(TAG, "abriendo $url")
        pageStartedAt = System.currentTimeMillis()
        webView.loadUrl(url)
    }

    private fun buildClient(assetLoader: WebViewAssetLoader) =
        object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest,
            ): WebResourceResponse? {
                val uri = request.url
                if (uri.host == HOST) return assetLoader.shouldInterceptRequest(uri)
                // Todo lo que no es del origen propio se bloquea y se registra (AdSense, analitica, CDN...).
                Log.w(TAG, "RED BLOQUEADA: ${request.method} $uri")
                return WebResourceResponse("text/plain", "utf-8", 403, "Blocked", emptyMap(), null)
            }

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest,
            ): Boolean {
                val own = request.url.host == HOST
                if (!own) Log.w(TAG, "NAVEGACION BLOQUEADA: ${request.url}")
                return !own
            }

            override fun onPageStarted(
                view: WebView,
                url: String,
                favicon: Bitmap?,
            ) {
                pageStartedAt = System.currentTimeMillis()
                view.evaluateJavascript(HOOK_SCRIPT, null)
            }

            override fun onPageFinished(
                view: WebView,
                url: String,
            ) {
                Log.i(TAG, "pagina cargada en ${System.currentTimeMillis() - pageStartedAt} ms: $url")
                view.evaluateJavascript(HOOK_SCRIPT, null)
                view.evaluateJavascript(CAPABILITIES_SCRIPT) { Log.i(TAG, "capacidades: $it") }
            }
        }

    private fun buildChromeClient() =
        object : WebChromeClient() {
            override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                Log.i(TAG, "consola[${message.messageLevel()}] ${message.message()} (${message.sourceId()}:${message.lineNumber()})")
                return true
            }

            override fun onShowFileChooser(
                view: WebView,
                callback: ValueCallback<Array<Uri>>,
                params: FileChooserParams,
            ): Boolean {
                pendingChooser?.onReceiveValue(null)
                pendingChooser = callback
                return try {
                    Log.i(TAG, "selector pedido: tipos=${params.acceptTypes.toList()} multiple=${params.mode}")
                    chooserLauncher.launch(params.createIntent())
                    true
                } catch (error: android.content.ActivityNotFoundException) {
                    Log.e(TAG, "sin selector de archivos", error)
                    pendingChooser = null
                    false
                }
            }
        }

    @Deprecated("Spike: se usa para poder cerrar con atras dentro de la web")
    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }

    /** Puente minimo del spike: recibe el archivo que la web "descarga" y lo deja en la cache. */
    private inner class SpikeBridge {
        @JavascriptInterface
        fun save(
            name: String,
            mime: String,
            base64: String,
        ) {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            val dir = File(cacheDir, "spike").apply { mkdirs() }
            val target = File(dir, name.replace(Regex("[^A-Za-z0-9._-]"), "_"))
            target.writeBytes(bytes)
            Log.i(TAG, "DESCARGA recibida: $name ($mime) ${bytes.size} bytes -> ${target.absolutePath}")
            runOnUiThread { Toast.makeText(this@WebSpikeActivity, "Guardado: $name", Toast.LENGTH_SHORT).show() }
        }
    }

    private companion object {
        const val TAG = "WebSpike"
        const val HOST = "appassets.androidplatform.net"

        /** Intercepta las descargas por blob (`<a download>`), que el WebView no entrega a la app. */
        const val HOOK_SCRIPT = """
            (function () {
              if (window.__spikeHook) return;
              window.__spikeHook = true;
              function grab(a) {
                fetch(a.href).then(function (r) { return r.blob(); }).then(function (b) {
                  var fr = new FileReader();
                  fr.onload = function () {
                    SpikeBridge.save(a.download || 'archivo', b.type, String(fr.result).split(',')[1]);
                  };
                  fr.readAsDataURL(b);
                });
              }
              var original = HTMLAnchorElement.prototype.click;
              HTMLAnchorElement.prototype.click = function () {
                if (this.download && this.href.indexOf('blob:') === 0) { grab(this); return; }
                original.call(this);
              };
              document.addEventListener('click', function (e) {
                var a = e.target && e.target.closest ? e.target.closest('a[download]') : null;
                if (a && a.href.indexOf('blob:') === 0) { e.preventDefault(); grab(a); }
              }, true);
            })();
        """

        const val CAPABILITIES_SCRIPT = """
            JSON.stringify({
              crossOriginIsolated: self.crossOriginIsolated === true,
              sharedArrayBuffer: typeof SharedArrayBuffer !== 'undefined',
              webAssembly: typeof WebAssembly !== 'undefined',
              wasmStreaming: typeof WebAssembly !== 'undefined' && typeof WebAssembly.instantiateStreaming === 'function',
              serviceWorker: 'serviceWorker' in navigator,
              share: typeof navigator.share === 'function',
              showSaveFilePicker: typeof self.showSaveFilePicker === 'function',
              cores: navigator.hardwareConcurrency,
              deviceMemoryGb: navigator.deviceMemory,
              secureContext: self.isSecureContext,
              origin: location.origin,
              ua: navigator.userAgent
            })
        """
    }
}

/** Sirve `assets/web` como raiz del sitio, con `cleanUrls` (sin `.html`) y los tipos MIME correctos. */
private class WebAssetHandler(
    private val assets: android.content.res.AssetManager,
) : WebViewAssetLoader.PathHandler {
    override fun handle(path: String): WebResourceResponse? {
        val clean = path.substringBefore('?').trimStart('/')
        val candidates =
            when {
                clean.isEmpty() -> listOf("index.html")
                clean.endsWith("/") -> listOf("${clean}index.html")
                clean.substringAfterLast('/').contains('.') -> listOf(clean)
                else -> listOf("$clean.html", "$clean/index.html")
            }
        for (candidate in candidates) {
            try {
                val stream = assets.open("web/$candidate")
                return WebResourceResponse(mimeOf(candidate), charsetOf(candidate), 200, "OK", HEADERS, stream)
            } catch (_: IOException) {
                // siguiente candidato
            }
        }
        gzipFallback(clean)?.let { return it }
        Log.w("WebSpike", "404 en assets: $path")
        return WebResourceResponse("text/plain", "utf-8", 404, "Not Found", emptyMap(), null)
    }

    /**
     * HALLAZGO DEL SPIKE: el empaquetado de Android quita el `.gz` de los assets y los descomprime
     * (`spa.traineddata.gz` queda como `spa.traineddata`), pero la web los pide con `.gz` y espera gzip.
     * Aqui se vuelve a comprimir al vuelo. En produccion es mejor renombrar a otra extension al empaquetar.
     */
    private fun gzipFallback(clean: String): WebResourceResponse? {
        if (!clean.endsWith(".gz")) return null
        return try {
            val raw = assets.open("web/${clean.removeSuffix(".gz")}").use { it.readBytes() }
            val packed = java.io.ByteArrayOutputStream(raw.size / 2)
            java.util.zip.GZIPOutputStream(packed).use { it.write(raw) }
            WebResourceResponse(
                "application/octet-stream",
                null,
                200,
                "OK",
                HEADERS,
                java.io.ByteArrayInputStream(packed.toByteArray()),
            )
        } catch (_: IOException) {
            null
        }
    }

    private fun charsetOf(name: String): String? =
        if (mimeOf(name).let { it.startsWith("text/") || it.contains("json") || it.contains("javascript") }) "utf-8" else null

    private fun mimeOf(name: String): String =
        when (name.substringAfterLast('.', "").lowercase()) {
            "html" -> "text/html"
            "js", "mjs" -> "text/javascript"
            "css" -> "text/css"
            "json", "webmanifest", "map" -> "application/json"
            "wasm" -> "application/wasm"
            "svg" -> "image/svg+xml"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            "avif" -> "image/avif"
            "woff2" -> "font/woff2"
            "woff" -> "font/woff"
            "ttf" -> "font/ttf"
            "otf" -> "font/otf"
            "txt" -> "text/plain"
            "xml" -> "application/xml"
            else -> "application/octet-stream"
        }

    private companion object {
        // Las mismas que da Firebase Hosting a la web (para probar el aislamiento tal cual).
        val HEADERS =
            mapOf(
                "Cross-Origin-Opener-Policy" to "same-origin",
                "Cross-Origin-Resource-Policy" to "same-origin",
                "Cache-Control" to "no-cache",
            )
    }
}
