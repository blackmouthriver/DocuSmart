# Release 1.1.4 (versionCode 10) — resumen

Fecha: 2026-10-14. Sucede a la **1.1.3 (versionCode 9)**. Versión **mínima y deliberada**: solo corrige el requisito de páginas de memoria de 16 kB de Google Play.

## Por qué existe

Play Console bloquea publicar actualizaciones a producción mientras el bundle tenga librerías nativas sin alinear a 16 kB:
«La app debe admitir tamaños de página de memoria de 16 kB» / «Tu última versión de producción no admite tamaños de página de memoria de 16 kB».

La única `.so` con `p_align=4096` en el `.aab` del código 9 era `libimage_processing_util_jni.so`, de `camera-core` 1.3.4.

## Qué cambia

| Área | Cambio | PR |
|---|---|---|
| Dependencias | CameraX 1.3.4 → **1.4.2** (core, camera2, lifecycle, view). Desde 1.4.0 la `.so` viene alineada a 16 kB | #146 |
| Versión | `versionCode 9 → 10`, `versionName 1.1.3 → 1.1.4` | este PR |

No hay cambios de código de la aplicación ni de comportamiento visible.

## Verificación

| Comprobación | Resultado |
|---|---|
| `.so` del APK/AAB (arm64-v8a, x86_64) | todas con `p_align=16384` |
| `zipalign -c -P 16` sobre el APK | `Verification successful` |
| Lector de QR (única pantalla con CameraX) en Edge 30 Neo, Android 14 | Preview + ImageAnalysis activos, QR leído por el usuario, 0 cierres |
| Pruebas instrumentadas `features.scanner.presentation` | 114/114 OK |
| Build, unitarias, lint, detekt, ktlint (PR #146) | OK, 20/20 checks de CI |

## Conocido y NO incluido (a propósito)

- **Permiso `READ_MEDIA_IMAGES`:** Play lo cuestionó en el flujo de producción. Se mantiene en esta versión (decisión del propietario) y se presenta la declaración de fotos y vídeos. Plan de fondo: selector de fotos con acceso persistente en la 1.1.5, sin permiso (rama `claude/quitar-permiso-fotos`, PR #147, sin fusionar).
- Oferta de prueba de 7 días en Play Console, `app-ads.txt` y demás pendientes: ver `seguimiento-post-1.1.2.md`.

## Despliegue

1. Subir el `.aab` (código 10) al canal que corresponda; en «Ver app bundles» de Play Console debe aparecer como compatible con 16 kB.
2. El aviso de 16 kB debe desaparecer de «Estado según las políticas».
3. Despliegue gradual en producción (10–20 %) vigilando Crashlytics 24–48 h.
