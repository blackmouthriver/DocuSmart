# Spike del anfitrión web (paquete 2): resultados

Fecha: 2026-11-26. Dispositivo: Motorola Edge 30 Neo (`ZY22G7SB77`, Android 14, 8 GB de RAM, WebView 157). Rama `claude/app-paridad`. Todo es **solo debug**: no entra en release ni cambia la versión (12 / 1.1.6).

## Qué se construyó

- `app/src/debug/java/com/docsmart/spike/WebSpikeActivity.kt`: un `WebView` que sirve el build de la web (`docusmart-web/dist`) desde `assets/web` con `WebViewAssetLoader`, en el origen propio `https://appassets.androidplatform.net`. Resuelve URL sin `.html`, pone los tipos MIME correctos (`.wasm`) y las cabeceras COOP/CORP de la web, bloquea y registra toda red externa, abre el selector de archivos del sistema y recibe las descargas por blob mediante un puente.
- `androidx.webkit:webkit:1.11.0-alpha02` como `debugImplementation` (es la versión que el bloqueo de dependencias ya fija por AdMob, así que **release ya la lleva**).
- Los assets (`app/src/debug/assets/web/`) están en `.gitignore`: se copian a mano desde `dist`, sin modelos de IA ni páginas de idiomas.
- Se abre con: `adb shell am start -S -n com.docsmart/com.docsmart.spike.WebSpikeActivity --es path /comprimir-pdf` (en Git Bash, con `MSYS_NO_PATHCONV=1`). Etiqueta de registro: `WebSpike`.

## Resultados

| Prueba | Resultado |
|---|---|
| Carga de una herramienta desde el origen propio | ✅ 150 a 260 ms, sin red |
| Contexto seguro, WebAssembly (con streaming), service worker | ✅ disponibles |
| `crossOriginIsolated` / `SharedArrayBuffer` | no (igual que la web, que no usa COEP): no hace falta aislamiento |
| `navigator.share` | no existe en el WebView; `showSaveFilePicker` aparece como disponible pero **no se probó** |
| Selector de archivos del sistema | ✅ abre y devuelve el archivo. Para imágenes Android abre el selector de fotos (con «Explorar…» para ir a Descargas) |
| Comprimir PDF (8,5 MB, 6 páginas) | ✅ corre completo, pdf.js dibuja la vista previa y el resultado se descarga. 0 % de reducción por la muestra (ruido casi incompresible), no por el anfitrión |
| Descarga por blob (`<a download>`) | ✅ con un puente que intercepta el clic; 8,5 MB llegaron a la cache de la app |
| OCR (imagen a texto, español) | ✅ reconoció el texto correctamente (tras corregir el `.gz`, ver abajo) |
| Redes externas durante las pruebas | ✅ ninguna (el registro de bloqueos quedó vacío) |
| Memoria | app ~470 a 500 MB de PSS más ~215 MB del proceso del WebView durante OCR. Con 8 GB bien; **falta el E22** |

## Hallazgos que cambian el plan

1. **Los `.gz` se rompen al empaquetar.** El empaquetado de Android quita la extensión y descomprime los assets `.gz` (`spa.traineddata.gz` queda `spa.traineddata`). La web los pide con `.gz` y espera gzip, así que el OCR se quedaba en «Preparando el motor» por un 404. En el spike el anfitrión los vuelve a comprimir al vuelo. **En producción:** renombrar a otra extensión al empaquetar (p. ej. `.gzdata`) y mapearla en el anfitrión.
2. **Peso real.** `dist` son 307 MB; sin modelos de IA (172 MB) quedan 122 MB y **66 MB comprimidos** en el APK. Se puede bajar mucho: las imágenes de SEO (`img`, 18 MB) no hacen falta, el OCR solo necesita español e inglés empaquetados (el resto se descarga al usarlo, 25 MB hoy) y los modelos de IA se descargan al usarlos. Objetivo: **unos 25 MB** extra sobre el AAB actual (59 MB). Verificar con un AAB de release.
3. **Falta un «modo app» en la web** (parámetro del anfitrión) que oculte: la cabecera con «Descargar app», el banner de consentimiento de Analytics/AdSense, el chat de ayuda flotante (tapa botones como «Comprimir PDF»), los anuncios y cualquier enlace de pago. Es la condición 3 de `revision-politicas-hibrido.md`.
4. **El puente en base64 no escala.** Funciona con 8,5 MB; con archivos de cientos de MB hay que enviar por trozos (`WebMessageListener` o un `PathHandler` de subida) y guardar con el selector de documentos del sistema.
5. **Dependencia de AdMob.** El selector de imágenes de Android no ve la carpeta Descargas por defecto: la web pide `image/*` y el usuario debe ir a «Explorar…». Es comportamiento del sistema; el selector de documentos nativo de la app es mejor entrada.

## Pendiente del spike antes de decidir

- Repetir en el **Moto E22** (poca RAM): OCR, comprimir un PDF grande y otra herramienta pesada.
- Probar **7-Zip, quitar fondo y audio/video** (los modelos y el wasm grande).
- Probar sin conexión (modo avión) y el comportamiento con el teléfono bajo memoria.
- Probar `showSaveFilePicker` y los demás caminos de guardado de la web.
- Medir el AAB de release con los assets podados.

## Decisión preliminar

**Ir (condicionado).** No apareció ningún bloqueo técnico: el WebView corre PDF, OCR y descargas, no necesita aislamiento de origen cruzado y no hace llamadas externas. Las condiciones son: modo app en la web, solución del `.gz`, puente por trozos, podar los assets y que el E22 aguante. Decisión definitiva tras el E22.
