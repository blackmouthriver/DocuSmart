# Resultados de la suite previa al release 1.1.2 (2026-10-08)

Ejecutada con `scripts/release-suite.ps1` sobre `main` (commit `a1d6f70`). Dispositivos: Motorola Edge 30 Neo (Android 14) y Moto E22 (Android 12), ambos reales.

## Etapas automáticas

| Etapa | Resultado |
|---|---|
| ktlintCheck, detekt, lintDebug | OK |
| testDebugUnitTest | OK — 1519 pruebas, 0 fallos, 0 errores |
| assembleDebug, assembleDebugAndroidTest | OK |
| Instrumentadas (reparto 2 dispositivos, 780 pruebas) | 771 OK, **9 fallidas**, sin ningún crash del proceso |

## Las 9 fallas de las instrumentadas

| Prueba | Dispositivo | Causa | Estado |
|---|---|---|---|
| `NavGraphHelpersTest.ocrYFirmar_…` | Edge | Dos navegaciones seguidas caen dentro de la ventana anti doble-toque de 500 ms de los accesos rápidos; la segunda se descarta. Es comportamiento intencional de la app; la prueba no lo contemplaba | **Corregida** (prueba reinicia el anti-rebote entre navegaciones) |
| `ComparePdfScreenTest.sinNingunPdf…` | Edge | "Comparar PDFs" es título y texto del botón: el buscador devolvía 2 nodos | **Corregida** (prueba) |
| `ConverterScreenFlowsTest` (×2, diálogo de límite) | Edge, E22 | `performScrollTo()` sobre un diálogo sin contenedor desplazable | **Corregida** (prueba: scroll solo si hay scroll) |
| `QrCreatorFlowsTest.logo_conArchivoQueNoEsImagen_…` | Edge | `Toast` llamado desde un hilo sin Looper: en pruebas de Compose el scope reanuda en IO | **Corregida** (el Toast se muestra siempre en el hilo principal) |
| `LibraryScreenExtrasTest` (×2, toast al eliminar / al vincular) | E22 | La lista no muestra "A.pdf" en 20 s; se repite siempre en este teléfono (Android 12, 720x1600) | **Pendiente**, causa sin aislar. Solo afecta a pruebas con ViewModel simulado |
| `VoiceGenderProbeInstrumentedTest` (×2, voz aguda y grave) | Edge, E22 | `detectIsFeminineVoice` devuelve `null` con el `TextToSpeech` simulado en teléfono real; pasa en emulador | **Pendiente**, causa sin aislar. Son pruebas de diagnóstico de la detección de género de voz |

Tras los arreglos se repitieron en su teléfono: Edge 12/12 OK (clase de navegación completa + ComparePdf + Converter + QR logo) y E22 1/1 OK (Converter).

## Pendiente de la suite

- Pruebas manuales M1–M15 (`suite-pruebas-release.md`): no ejecutadas.
- Las 4 fallas pendientes (Library ×2, voz ×2) deben decidirse: aislar la causa o marcarlas como dependientes de dispositivo.
- El Moto E22 quedó sin la build de Play (se desinstaló para poder instalar el debug); reinstalarla desde el enlace de la prueba cerrada antes de M7/M8.
