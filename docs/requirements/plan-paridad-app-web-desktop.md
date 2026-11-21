# Plan de paridad y sincronización: app Android, web y Desktop

Fecha: 2026-11-20. Estado: **propuesta para aprobación del propietario**; nada de aquí está implementado salvo lo marcado.

## 1. Punto de partida (verificado en el código de ambos repos)

- **Desktop no es un tercer producto.** DocuSmart Desktop es la web instalada como PWA (`docusmart-web`, W-16). Se mantienen **dos** códigos: la app Android (este repo) y la web.
- La web (`docusmart.online`) ya tiene unas 45 páginas de herramienta en 12 idiomas; la app tiene 15 herramientas PDF (`PdfTool`) y 17 conversiones (`ConversionType`), más Escáner, QR, Estudio, Agenda, Carpeta Segura y Biblioteca.
- **App Links (1.1.6, W-18)** ya enlaza 28 herramientas de la web con su pantalla en la app. La lista sale de `toolPageSlugs.json` de la web y `WebToolLinksTest` falla si el manifiesto no coincide con el mapa.

### Herramientas que están en la web y **no** en la app (candidatas a paridad)

| Grupo | Herramientas | Cómo se haría en Android |
|---|---|---|
| Imagen | Comprimir, redimensionar, recortar, rotar/voltear, ajustar (brillo, contraste, saturación, B/N, enderezar), marca de agua, foto tamaño documento | `Bitmap`/`Canvas` nativos, sin librerías nuevas |
| Formatos de imagen | HEIC→JPG, GIF, ICO, AVIF (salida), SVG→PNG, TIFF→JPG | HEIC con `ImageDecoder` (API 28+); GIF/ICO a mano; SVG y TIFF **por evaluar** (librería y tamaño del APK) |
| Quitar fondo | Segmentación en el dispositivo | **Por evaluar**: modelo de ML Kit/MediaPipe y su peso; debe seguir siendo 100 % local |
| PDF | PDF→Excel, Reparar, PDF/A | PDF→Excel con POI (ya en la app); Reparar y PDF/A **por evaluar** con iText (revisar licencia y tamaño) |
| Archivos | Comprimir y descomprimir ZIP/7Z, extraer RAR/TAR/GZ | ZIP nativo; 7Z/RAR con librerías **por evaluar** (licencia y tamaño) |
| Utilidades | Calcular hash | `MessageDigest`, trivial |

### Lo que la app tiene y la web no (o va en camino)

| Función | Estado en la web |
|---|---|
| Agenda y notas | W-126 en curso (agenda local, `.ics`) |
| Modo Estudio (lectura con voz, Pomodoro) | Notas sí; voz y Pomodoro no planeados |
| Carpeta Segura con PIN, Papelera, Biblioteca | No planeados (dependen del almacenamiento del dispositivo) |
| Premium con Google Play | W-98 (premium compartido) |
| Conversiones extra (BMP, HTML, CSV, PPT) | No planeadas |

## 2. Qué significa «sincronizados»

Dos cosas distintas, con trabajo distinto:

1. **Mismo catálogo de funciones** (paridad de herramientas y textos).
2. **Mismos datos del usuario** en los tres sitios (ajustes, historial, notas, agenda, biblioteca). Ya decidido como opción A en la web: el Google Drive del propio usuario (`appDataFolder`), cifrado en el dispositivo, sin servidor propio (W-94 a W-98).

## 3. Fases

| Fase | Contenido | Entrega |
|---|---|---|
| **F0 Higiene** | `screen.portrait` opcional (aviso de la 1.1.6); cerrar en Crashlytics los fallos de 1.0.0 ya corregidos; obtener y corregir el `IndexOutOfBounds` de la 1.1.5 | Próxima versión (código 13) |
| **F1 Contrato de paridad** | `parity.json` en la web con cada herramienta marcada `app: si/no/planeada`. Prueba en ambos repos que **falla** si una herramienta aparece en un lado y no está declarada en el otro. Es lo que mantiene «al mismo nivel» sin depender de la memoria de nadie | Web + app, solo pruebas y datos |
| **F2a Herramientas simples en Android** | Hash; herramientas de imagen (comprimir, redimensionar, recortar, rotar, ajustar, marca de agua, foto tamaño documento); HEIC→JPG, GIF, ICO; PDF→Excel | Una pantalla «Herramientas de imagen» + 2 conversiones; sin dependencias nuevas |
| **F2b Herramientas con librería** | Archivos ZIP/7Z/RAR; Reparar PDF; PDF/A; SVG y TIFF; Quitar fondo | Cada una tras una evaluación corta de licencia, tamaño del APK y privacidad |
| **F3 Sincronización base** | W-94: inicio de sesión con Google en la app y la web, `appDataFolder`, formato versionado y cifrado compartido (la web ya tiene un precedente: `QrCrypto.kt` y su prueba cruzada) | Depende de textos y política de privacidad |
| **F4 Datos por etapas** | W-95 ajustes e historial → W-96 notas, estudio y agenda → W-97 biblioteca; W-98 premium compartido en paralelo | Una etapa por versión |
| **F5 Operación** | Textos fuente compartidos en los 12 idiomas, changelog común y cadencia de versiones (la web despliega al fusionar; la app, por versión de Play) | Continuo |

Orden recomendado: **F0 → F1 → F2a → F3 → F2b → F4**. F1 va temprano porque sin él cualquier paridad se vuelve a desalinear en pocas semanas.

## 4. Decisiones que necesito del propietario

1. ¿Se aprueba la opción de F1 (el contrato `parity.json`), que toca ambos repos?
2. F2b: ¿se acepta sumar librerías que aumentan el APK (7Z/RAR, TIFF, SVG, modelo de quitar fondo) o se prefiere limitar la app a lo nativo?
3. F3: aprobar el cambio del texto de privacidad («en tu dispositivo o en **tu** Drive, cifrado») y la ficha de Seguridad de los datos de Play Console.
4. Alcance de «web ← app»: ¿Carpeta Segura y Papelera quedan solo en la app?

## 5. Criterio de «hecho» por fase

- F0: aviso de Play resuelto en «Ver app bundles»; Crashlytics sin esos grupos abiertos.
- F1: CI en rojo si se agrega una herramienta sin declarar su estado en el otro lado.
- F2: cada herramienta nueva con pruebas unitarias, prueba en dos teléfonos reales y enlace de App Links (W-18) agregado.
- F3/F4: un cambio hecho en un dispositivo aparece en los otros dos sin pérdida de datos y con una copia del perdedor en caso de conflicto.

## 6. Fallos de Crashlytics revisados (2026-11-20, últimos 90 días)

| Fallo | Versiones y dispositivos | Evidencia en el código actual | Decisión |
|---|---|---|---|
| `Canvas: trying to draw too large(… bytes) bitmap` en `ViewerScreen$PdfViewerContent` (3 eventos, 2 usuarios) | 1.0.0 (1) y 1.1.0 (2); Asus con Android 9 y Motorola | `PdfPageSource.lazyPageBitmapSize` limita cada página a `VIEWER_PAGE_MAX_PIXELS = 8 000 000` (unos 32 MB) desde el render por página (#108). Ninguna versión posterior a 1.1.0 lo registra | Cerrar en Crashlytics; si reaparece en 1.1.3 o posterior, reabrir |
| `IllegalArgumentException: All requested items must be referenced by specific ID` / `must be Media items` en `MediaDeletePermission.createBulkDeleteRequest` (2 + 2 eventos, 1 usuario cada uno) | Solo 1.0.0; Tecno KJ7s con Android 14; llegaba desde `TrashRepository.deleteForever` → `DocumentRepository.deleteDocument` | `createBulkDeleteRequest` captura cualquier excepción y devuelve `null`; desde la 1.1.5 lo propio se borra directo con `deleteOwned` y `createDeleteRequest` ya no recibe documentos | Cerrar en Crashlytics |
| `IndexOutOfBoundsException: Index -1 out of bounds for length 1` (`jdk.internal.util.Preconditions`) | 1.1.5; 1 evento, 1 usuario | Los accesos por índice del Visor (Excel), Estudio y Reordenar páginas tienen guarda; sin el stack trace no se puede ubicar | **Pendiente**: falta el seguimiento de pila del evento |
