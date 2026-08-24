# Módulo: Escáner (documento + QR)

> Parte de [`CONTEXT.md`](../../CONTEXT.md). Cubre el escaneo de documentos
> (Google ML Kit Document Scanner) y el lector/creador de QR (`QrScreen.kt`,
> ya rediseñado en el módulo Seguridad — ver [`security.md`](security.md)
> RF-SEC-13/14/15 para el cifrado del QR protegido).

**Estado:** módulo con menos deuda real de lo que sugería la QA de mayo — la
mayoría de sus hallazgos resultaron obsoletos, no por haberse corregido en
esta sesión sino porque la implementación cambió de raíz (se adoptó Google
ML Kit Document Scanner en vez de una cámara/recorte propios) o porque el
lector de QR ya se reconstruyó por completo durante el módulo Seguridad. Se
encontraron y corrigieron 2 bugs reales menores: detección de esquema de QR
sensible a mayúsculas, y guardado de PDF escaneado en Descargas roto en
Android 8/9. 10 tests nuevos.
**Código relacionado:** `features/scanner/**`.

---

## 1. Alcance

Dos capacidades independientes:

1. **Escanear documento/foto** — delega la captura, recorte, corrección de
   perspectiva y filtros a Google ML Kit Document Scanner (`GmsDocumentScanning`),
   no a una implementación propia. DocuSmart solo recibe el resultado final
   (imágenes o PDF) y lo lleva a `ScanResultScreen` para nombrarlo, guardarlo
   o compartirlo.
2. **Leer/crear QR** — cámara propia con CameraX + ML Kit Barcode Scanning
   para leer, clasificación de contenido (URL/imagen/documento/email/
   teléfono/texto) con acción específica por tipo, y creación de QR con
   contenido opcionalmente protegido por contraseña (AES-256/GCM, ver
   `security.md`).

---

## 2. Requerimientos funcionales

- **RF-SCAN-01** El sistema debe permitir escanear un documento con corrección automática de perspectiva y recorte, delegando la captura a Google ML Kit Document Scanner.
- **RF-SCAN-02** El resultado del escaneo (imágenes o PDF) debe poder nombrarse, guardarse en Descargas o compartirse directamente, en cualquier versión de Android soportada (API 26+).
- **RF-SCAN-03** El sistema debe leer códigos QR con la cámara y clasificar el contenido (URL, imagen, documento, email, teléfono, texto) para ofrecer la acción correspondiente (abrir en navegador, llamar, enviar correo, copiar, etc.).
- **RF-SCAN-04** La detección de tipo de contenido debe ser insensible a mayúsculas/minúsculas y a espacios en blanco alrededor del valor escaneado.
- **RF-SCAN-05** El sistema debe permitir crear un código QR con contenido de texto/URL, opcionalmente protegido con contraseña (ver `security.md` RF-SEC-13).

### Backlog — no implementado
- **RF-SCAN-06** Ajuste de brillo/contraste sobre la imagen ya escaneada, antes de guardar — Google ML Kit Document Scanner no expone este control; requeriría un paso de edición propio después de recibir el resultado.
- **RF-SCAN-07** Reescalar la imagen escaneada (cambiar resolución/tamaño de salida) — mismo motivo que RF-SCAN-06.

---

## 3. Requerimientos no funcionales

- **RNF-SCAN-01 (sin control sobre la UI de captura):** al delegar la captura completa a Google ML Kit Document Scanner, DocuSmart no tiene forma de personalizar ni corregir su UI de recorte/filtros/miniaturas — cualquier problema ahí depende de una actualización de Play Services, no de código propio.
- **RNF-SCAN-02 (guardar/compartir debe funcionar en todo el rango de API soportado):** minSdk 26 — cualquier flujo de guardado debe tener una ruta pre-Android 10 (API < 29, sin `MediaStore.Downloads`), no solo la ruta moderna con `MediaStore`.

---

## 4. Historias de usuario con criterios de aceptación

### HU-SCAN-01 — Escanear y guardar un documento en cualquier versión de Android
**Como** usuario con un teléfono Android 8 o 9,
**quiero** poder guardar un documento que acabo de escanear,
**para** no depender de la versión de mi sistema operativo para usar la app.

- **AC1** Dado que escaneo un documento en modo "Documento" (ML Kit devuelve el PDF directamente), cuando toco "Guardar en Descargas" en cualquier versión de Android soportada, entonces el archivo se guarda correctamente.
- **AC2** Este comportamiento debe ser el mismo en Android 10+ (vía `MediaStore.Downloads`) y en Android 8-9 (vía el directorio público de Descargas).

*(Corrige bug real: `savePdfUriToDownloads()` no tenía ninguna implementación para API < 29 — devolvía `false` incondicionalmente. En Android 8/9, escanear en modo "Documento" y guardar producía un fallo silencioso sin explicación.)*

### HU-SCAN-02 — Detectar el tipo de contenido de un QR sin importar mayúsculas
**Como** usuario que escanea códigos QR generados por distintas herramientas,
**quiero** que la app reconozca una URL/email/teléfono sin importar cómo esté escrito el esquema,
**para** ver siempre la acción correcta (abrir en navegador, llamar, etc.).

- **AC1** Dado que escaneo un QR con el esquema en mayúsculas (`HTTPS://...`, `MAILTO:...`, `TEL:...`), cuando se procesa, entonces se clasifica igual que su equivalente en minúsculas.
- **AC2** Dado que el contenido escaneado tiene espacios al inicio o final, cuando se procesa, entonces se ignoran para la clasificación.

*(Corrige bug real: `detectQrContentType()` comparaba el esquema contra el valor sin normalizar — un QR con esquema en mayúsculas caía en la categoría TEXT en vez de URL/EMAIL/PHONE, y el usuario no veía el botón de acción correspondiente.)*

---

## 5. Bugs de QA a corregir (trazabilidad)

| Bug (barrido de pruebas v1.0 / mejoras pendientes v1.0.1) | HU que lo cubre | Estado |
|---|---|---|
| "Miniatura de filtros no refleja la imagen real capturada" | — | **Obsoleto por cambio de arquitectura** — la captura ya no usa una cámara/recorte/filtros propios: se delega por completo a Google ML Kit Document Scanner, que no expone ninguna miniatura de filtro al código de DocuSmart. El hallazgo no aplica a la implementación actual. |
| "Falta lector de QR con navegación a URL" | — | **Obsoleto** — ya implementado (`detectQrContentType` + `openUrl`), con clasificación por tipo de contenido y botón de acción específico. No queda claro cuándo se agregó; probablemente durante la reconstrucción de `QrScreen.kt` en el módulo Seguridad. |
| "Falta QR con contraseña compartible" | — | **Obsoleto** — implementado en el módulo Seguridad (`QrCrypto.kt`, AES-256/GCM). Ver `security.md` RF-SEC-13/14/15. |
| Faltan: escalar imagen, ajuste de brillo/contraste | RF-SCAN-06, RF-SCAN-07 (backlog) | Confirmado vigente — Google ML Kit Document Scanner no expone estos controles; requeriría un paso de edición propio, no implementado en esta sesión. |
| **Bug real encontrado hoy (no reportado en la QA):** guardar un documento escaneado en modo "Documento" en Descargas fallaba en silencio en Android 8/9 (`savePdfUriToDownloads` sin implementación pre-Q). | HU-SCAN-01 | ✅ Corregido — agregada la ruta pre-Q (escritura directa al directorio público de Descargas), igual patrón que ya usaba `saveFileToDownloads` en el mismo archivo. |
| **Bug real encontrado hoy (no reportado en la QA):** detección de tipo de contenido de QR sensible a mayúsculas/espacios — un QR con esquema en mayúsculas se clasificaba como texto plano en vez de URL/email/teléfono. | HU-SCAN-02 | ✅ Corregido — normalización a minúsculas + `trim()` antes de clasificar. |

---

## 6. Plan de pruebas unitarias

| # | Cobertura | Estado |
|---|---|---|
| 1 | `QrContentTypeTest` — URL en minúsculas/mayúsculas, con espacios, con extensión de imagen/documento, mailto/tel en mayúsculas/minúsculas, `content://` con y sin extensión reconocida, texto plano sin esquema. | ✅ 10 tests, en verde |
| 2 | `QrCryptoTest` (ya existente, módulo Seguridad) — sigue en verde, sin cambios. | ✅ 6 tests, en verde |
| 3 | `ScanResultScreen` (guardar/compartir) — no cubierto con tests unitarios (funciones `private` a nivel de archivo Compose, requeriría refactor a una capa de datos separada para testear sin Robolectric/instrumentación). | Pendiente si se justifica el refactor. |

`detectQrContentType`/`QrContentType` se cambiaron de `private` a `internal`
dentro de `QrScreen.kt` específicamente para poder testearlos desde un
archivo de test del mismo módulo, sin exponerlos como API pública de la app.

---

## 7. Preguntas abiertas

| Pregunta | Notas |
|---|---|
| ¿Vale la pena construir un paso de edición propio (brillo/contraste/escala) después del resultado de ML Kit? | Es la única funcionalidad genuinamente faltante de este módulo; el resto de la QA de mayo ya no aplica. |
| ¿El modo "Foto" (`SCANNER_MODE_BASE`) necesita opciones adicionales respecto al modo "Documento" (`SCANNER_MODE_FULL`)? | No evaluado en esta pasada — ambos usan el mismo flujo de `ScanResultScreen` después de capturar. |
