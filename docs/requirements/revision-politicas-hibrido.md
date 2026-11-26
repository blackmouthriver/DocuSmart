# Revisión de políticas para la app híbrida (WebView)

Fecha: 2026-11-26. Alcance: **qué políticas de Google Play, AdMob/AdSense y privacidad hay que cumplir** para publicar una versión de la app que muestre dentro de ella herramientas de DocuSmart Web (decisión de arquitectura híbrida, `plan-paridad-app-web-desktop.md`).

> **Veredicto:** el enfoque híbrido es **viable y no bloquea el despliegue**, con condiciones concretas (sección 3). No hay ninguna política que lo prohíba. Sí hay que actualizar la **política de privacidad** y **revisar la ficha «Seguridad de los datos»** antes de publicar, y probarlo en la prueba cerrada.
>
> Esta revisión sale de la documentación oficial y de resúmenes de terceros encontrados por búsqueda; **no sustituye la lectura de las políticas vigentes el día del envío** (las páginas de Google cambian) ni una revisión legal.

## 1. Fuentes consultadas

| Tema | Fuente |
|---|---|
| Código descargado y WebView | [Dispositivo y abuso de red (Google Play)](https://support.google.com/googleplay/android-developer/answer/9888379?hl=en) |
| Funcionalidad mínima y WebView | [Guía de Android Developers sobre infracciones comunes (2020)](https://android-developers.googleblog.com/2020/10/developer-tips-and-guides-common-policy.html?hl=es) |
| Seguridad de los datos | [Formulario de Seguridad de los datos (Play Console)](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en) |
| Pagos | [Política de pagos de Google Play](https://support.google.com/googleplay/android-developer/answer/9858738?hl=en) |
| Anuncios en apps y WebView | [Políticas del programa AdSense / AdMob](https://support.google.com/admob/answer/48182?hl=en), [requisitos para contenido web dentro de apps (Ad Manager)](https://support.google.com/admanager/answer/6310245?hl=en) |

## 2. Matriz de políticas

| # | Política | Qué dice (resumen) | Cómo nos afecta | Acción antes de desplegar |
|---|---|---|---|---|
| 1 | **Funcionalidad mínima / spam** | Las apps que son solo una web empaquetada se marcan como spam | DocuSmart tiene mucha funcionalidad nativa (escáner, Biblioteca, Visor, Carpeta Segura, Estudio); las herramientas web serían un complemento | Mantener lo nativo como núcleo; no quitar funciones nativas por la web |
| 2 | **Código ejecutable descargado** | Prohíbe descargar dex, JAR o `.so` fuera de Play; **exceptúa** el código que corre en un intérprete con acceso solo indirecto a Android (ejemplo: JavaScript en un WebView). El código interpretado no puede permitir violaciones de otras políticas | JavaScript y WebAssembly del build web corren dentro del WebView | **Empaquetar** el build web (incluido el wasm) dentro de la app; los modelos de IA son datos, no código, y pueden descargarse al usarlos. Nada de dex ni `.so` descargados |
| 3 | **Interfaz JavaScript del WebView** | Exponer una interfaz a contenido web no confiable es un riesgo de incumplimiento; conviene cargar solo contenido propio y con URL acotadas | El anfitrión tendrá un puente con la app (recibir y entregar archivos) | Puente mínimo, solo para el origen propio, lista de navegación permitida, sin acceso a archivos locales del WebView |
| 4 | **Seguridad de los datos** | Hay que declarar la recolección de datos de un WebView **si la app controla el código que entrega**; no hace falta si el usuario navega la web abierta | El contenido embebido es nuestro: cuenta como código de la app | Que el anfitrión **no** envíe nada nuevo (analítica propia apagada, ver 6). Entonces las respuestas actuales (Firebase Analytics y Crashlytics, AdMob, Play Billing) **no cambian**; aun así, repasar el formulario y dejar constancia |
| 5 | **Política de privacidad** | Obligatoria para completar el formulario y debe describir lo que se recolecta | La política actual cubre la app y DocuSmart Web por separado; no menciona las herramientas web dentro de la app | **Actualizar** (sección 4). La fuente de la política es el repo de la web (`privacidad.html`); `legal/privacy-policy.html` de la app es una copia: primero se edita allá |
| 6 | **Anuncios** | AdMob y Ad Manager pueden mostrar anuncios junto a un WebView con el SDK de anuncios; el contenido del WebView está sujeto a las mismas reglas que en el navegador; las políticas de AdSense no se pensaron para código dentro de apps | La web usa AdSense; la app usa AdMob | El build embebido **no carga AdSense** ni el banner de consentimiento de la web; la publicidad sigue siendo solo la de la app. Se activa con un parámetro del anfitrión |
| 7 | **Permisos** | Pedir solo lo necesario; permisos de fotos y videos tienen política propia | Las herramientas web eligen archivos con el selector del sistema (como ya hace la 1.1.5, sin permisos de medios) | **No pedir permisos nuevos.** El micrófono (audio a texto) y la cámara quedan fuera del WebView: si se quieren, van nativos y con declaración |
| 8 | **Pagos** | Los bienes digitales vendidos en la app deben usar Google Play Billing; no se puede llevar al usuario a otro medio de pago ni enlazar páginas que lo hagan | El plan prevé Premium compartido con la web (W-98) | El anfitrión **no muestra ninguna pantalla ni enlace de pago**; si la web agrega «Hazte Premium», se oculta dentro de la app. Antes de W-98, revisar con la política vigente el caso «comprado en Play, usado en la web» (en sentido contrario, comprado en la web y usado en la app, no está claro y se evita) |
| 9 | **Tamaño de la app** | Hay límites de tamaño del paquete y de descarga | El build web trae wasm (qpdf, 7-Zip, OCR) y varios MB de idiomas; hoy el AAB pesa unos 59 MB | Medir el aumento en el spike; los modelos grandes se descargan al usarlos. Verificar el límite vigente en Play Console |
| 10 | **Seguridad del WebView** | Buenas prácticas de Android | Superficie nueva | Sin tráfico en claro, errores SSL no ignorados, depuración de WebView solo en debug, JavaScript solo para contenido propio |
| 11 | **Ficha y metadatos** | Lo anunciado debe coincidir con lo que hace la app | Aparecen herramientas nuevas | Actualizar descripción y novedades solo cuando existan |

## 3. Condiciones para publicar (lista de control)

1. **Build web empaquetado** en la app (no código ejecutable descargado).
2. **Anfitrión endurecido:** origen propio, lista de navegación permitida, puente mínimo, sin acceso a archivos locales.
3. **Sin AdSense, sin banner de analítica web y sin enlaces de pago** dentro de la app (parámetro del anfitrión).
4. **Sin permisos nuevos** en el manifiesto.
5. **Política de privacidad actualizada y publicada** antes de enviar la versión a revisión.
6. **Seguridad de los datos revisada** y registrada.
7. **Prueba cerrada** y despliegue gradual (10 a 20 %) con Crashlytics vigilado 24 a 48 horas.

## 4. Política de privacidad: qué cambiar

Dónde: `docusmart-web/site/privacidad.html` (se trabaja en el otro chat) y luego copiar a `legal/privacy-policy.html` de la app, que dice «Editar allá y copiar aquí». La política actual ya cubre la app y DocuSmart Web por separado (secciones sobre AdMob, Firebase, Crashlytics, Play Billing y DocuSmart Web).

**Texto propuesto** (borrador condicionado a que se cumpla la sección 3; se ajusta cuando exista el anfitrión):

> **Herramientas de DocuSmart Web dentro de la app.** Algunas herramientas de la app (por ejemplo, varias de imagen, de audio y video, de texto y de documentos) usan el mismo motor que DocuSmart Web y se muestran dentro de la app en una vista web que solo carga contenido de DocuSmart. Funcionan igual que en el navegador: tu archivo se procesa dentro de tu dispositivo y no se sube a ningún servidor; ni nosotros ni Google recibimos el archivo ni su contenido. En esa vista no se cargan anuncios de AdSense ni se usan cookies de seguimiento propias del sitio: la publicidad de la app sigue siendo Google AdMob y la analítica de uso sigue siendo Firebase Analytics, con los datos ya descritos en esta política. Las preferencias de esas herramientas (idioma, tema, historial local) se guardan en el almacenamiento de la propia app y se borran al desinstalarla o al borrar sus datos.

También conviene revisar que la lista de «Datos que recopilamos» siga siendo cierta y que el apartado de contacto y de retención no cambie.

## 5. Seguridad de los datos (Play Console)

Con las condiciones de la sección 3, las respuestas actuales **no deberían cambiar** (no hay datos nuevos). Pasos: abrir *Contenido de la app → Seguridad de los datos*, repasar cada tipo declarado frente al comportamiento real del anfitrión y guardar. Si más adelante el anfitrión envía algo a un servidor (por ejemplo, analítica propia), hay que declararlo antes de publicar.

## 6. Riesgos y puntos que aún hay que verificar

| Riesgo | Cómo se cubre |
|---|---|
| Que un revisor considere la app un envoltorio | Núcleo nativo intacto y descripción de la ficha centrada en las funciones nativas |
| Que el WebView no aguante OCR, PDF grandes o modelos en teléfonos de poca RAM | Spike en el Edge y el E22 antes de construir; si una herramienta no cabe, pasa a nativo o se queda solo en la web |
| WebAssembly con hilos exige aislamiento de origen cruzado (COOP/COEP), que la web da con cabeceras de Firebase | El anfitrión debe poner esas cabeceras al servir el build empaquetado; el spike lo comprueba |
| Cambios de las políticas de Google | Releer las políticas el día del envío |
| Anuncios: la política de AdSense frente a su uso dentro de apps | No cargar AdSense en el build embebido (ya en las condiciones) |
| Premium compartido (W-98) y pagos | Revisión específica antes de implementarlo |

## 7. Qué se hace y cuándo

| Qué | Quién | Cuándo |
|---|---|---|
| Esta revisión | Hecho | 2026-11-26 |
| Pasar el texto propuesto de la sección 4 al chat de la web | Propietario | Antes de la prueba cerrada |
| Construir el anfitrión con las condiciones de la sección 3 | App | Tras el spike (paquete de trabajo 2 del plan) |
| Releer las políticas y repasar «Seguridad de los datos» | Propietario y app | Justo antes de enviar la versión |
| Prueba cerrada y despliegue gradual | Propietario | Con la versión final |
