# Plan de actualización de la app (paridad con la web)

Documento vivo. Empezado el 2026-11-26. La web se trabaja en **otro chat** y la app **aquí**, en paralelo. Complementa `plan-paridad-app-web-desktop.md` (auditoría, opciones y plan de lanzamiento conjunto), `revision-politicas-hibrido.md` (políticas) y `herramientas-matriz.json` (71 herramientas, una fila cada una).

## 1. Reglas de trabajo

1. **Todo se sube primero a la rama `claude/app-paridad`** (basada en `main`). Nada va a `main` sin aprobación explícita del propietario.
2. **No se crea una versión nueva** (`versionCode` 12, `versionName` 1.1.6) **hasta que todo funcione**: la versión se sube en el último paquete de trabajo, con la suite completa en verde en los dos teléfonos.
3. **Antes de cada sesión** se corre el informe de sincronización con la web (sección 3) y se anota en el registro (sección 6).
4. Cada herramienta nueva sale con pruebas unitarias, prueba en el Edge y el E22, App Link y su fila del contrato en `si` (cambio que se pasa al chat de la web).
5. Orden vigente: lo pequeño nativo primero, el anfitrión web solo si el spike pasa, y **políticas actualizadas antes de desplegar**.

## 2. Estado de la rama `claude/app-paridad`

| Contenido | Estado |
|---|---|
| Contrato de paridad (F1): `web-tool-parity.json` y `ToolParityContractTest` | En la rama, pruebas verdes |
| HEIC a JPG, imagen a GIF, imagen a ICO (codificadores propios) | En la rama, 206 pruebas verdes; **sin probar en teléfono** |
| PDF a Excel local | En la rama; **1 prueba falla** (`PdfSheetLayoutTest`, «estilo extracto de Word»); sin probar en teléfono |
| `screen.portrait` opcional (advertencia de Play de la 1.1.6) | En la rama |
| `scripts/generate_web_links.py` (regenera App Links desde la web) | En la rama |
| `scripts/web_sync_report.py`, `herramientas-matriz.json`, `web-sync-state.json` | En la rama |
| Versión | **1.1.6 (código 12), sin cambios** |

## 3. Cómo sincronizar con la web (cada sesión)

```bash
python -X utf8 scripts/web_sync_report.py            # informa
python -X utf8 scripts/web_sync_report.py --update   # informa y guarda la nueva base
```

El informe lee la rama `preproduccion` del repo `docusmart-web` (público) y dice: commits nuevos, herramientas nuevas o quitadas del catálogo, cambios del contrato de paridad, filas del backlog que pasaron a hechas y **herramientas sin clasificar** en la matriz de la app. Qué hacer con cada cosa:

| Aparece en el informe | Qué se hace aquí |
|---|---|
| Herramienta nueva en el catálogo | Agregar su fila a `herramientas-matriz.json` y decidir: nativa, embebida o solo web |
| Cambio de una herramienta que la app ya tiene | Revisar si la app debe replicarlo (p. ej. nuevos formatos, mejor fidelidad) |
| Cambio en la política de privacidad o en textos comunes | Copiar a `legal/` y a `strings.xml` |
| Cambio de límites, Premium o cuenta | Avisar al propietario: toca la decisión de límites |

## 4. Paquetes de trabajo

Estado: ⬜ pendiente · 🔄 en curso · ✅ hecho.

| # | Paquete | Qué incluye | Estado |
|---|---|---|---|
| **0** | **Estabilizar la rama** | Corregir la prueba que falla de PDF a Excel; ktlint, detekt, lint y las pruebas unitarias completas en verde; revisar que el manifiesto fusionado sea correcto | 🔄 |
| **1** | **Cumplimiento (políticas)** | Revisión hecha (`revision-politicas-hibrido.md`). Falta: pasar el texto de la política al chat de la web, revisar «Seguridad de los datos» y releer las políticas el día del envío | 🔄 |
| **2** | **Spike del anfitrión web** | Un WebView en una pantalla de prueba que sirva un build de la web empaquetado. Medir en el Edge y el E22: carga y cabeceras (COOP/COEP), selector de archivos, descarga y compartir, Comprimir PDF (qpdf wasm), OCR, 7-Zip, memoria, peso del APK y funcionamiento sin conexión. **Decisión de seguir o no** con la opción híbrida | ⬜ (necesita un teléfono conectado) |
| **3** | **Rápidas nativas (S)** | Hash, generador de contraseñas, contador de palabras, renombrar archivos, imagen a texto, comprimir, redimensionar, rotar y ajustar imagen, CSV ↔ Excel, comprimir y extraer ZIP/TAR/GZ, resumir PDF como herramienta | ⬜ |
| **4** | **Mostrar las conversiones ocultas** | Revisar la calidad de PDF a Word, Word, Excel y PowerPoint frente a la web (fuentes Unicode, fidelidad) y mostrarlas. Decisión del propietario pendiente | ⬜ |
| **5** | **Anfitrión web de producción** | Si el spike pasa: build web empaquetado, puente mínimo, lista de navegación permitida, sin AdSense ni pagos, manifiesto `herramientas.json` y banderas de Remote Config | ⬜ |
| **6** | **Herramientas medianas y grandes** | Las que el reparto asignó a «embebido» (quitar fondo, foto tamaño documento, reparar PDF, quitar metadatos, hoja de vida, cuenta de cobro, PDF/A, 7Z/RAR, TIFF, AVIF, audio y video, audio a texto…), una por una | ⬜ |
| **7** | **Mejoras de las existentes** | Sello de privacidad, «¿Y ahora?», lotes, OCR en más idiomas, editar texto existente, firma con certificado | ⬜ |
| **8** | **Pruebas completas** | Suite unitaria e instrumentada completa en el Edge y el E22, más recorrido manual de lo nuevo | ⬜ |
| **9** | **Cierre y lanzamiento** | **Aquí sí** subir `versionCode` y `versionName`; notas de versión; política y «Seguridad de los datos» publicadas; AAB firmado; prueba cerrada y despliegue gradual | ⬜ |

### Orden recomendado

`0` → `2` (en cuanto haya un teléfono conectado) → `3` en paralelo con `1` → `4` → si el spike pasó `5` y `6`, si no `6` en nativo → `7` → `8` → `9`.

El paquete 2 va temprano a propósito: es el que decide si el plan híbrido sigue en pie, y es mejor saberlo antes de construir el resto.

## 5. Decisiones abiertas

1. **Mostrar las 5 conversiones ocultas** (paquete 4).
2. **Audio y video en la app** (nativo, embebido o solo web).
3. **Manifiesto de herramientas, banderas de Remote Config y tren de versiones** cada 2 semanas.
4. Textos y reglas de **límites** y de **Premium compartido**: se deciden cuando la web los vaya a adoptar (decisión del 2026-11-26: la web adoptará los de la app al final).

## 6. Registro de sincronización con la web

| Fecha | Web (`preproduccion`) | Qué se vio | Qué se hizo aquí |
|---|---|---|---|
| 2026-11-26 | `5cbf8e81` | Base inicial: 71 herramientas en el catálogo, la matriz de la app las cubre todas | Se creó la matriz y el informe |
