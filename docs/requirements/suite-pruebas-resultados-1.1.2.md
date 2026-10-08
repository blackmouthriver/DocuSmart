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
| `LibraryScreenExtrasTest` (×2, toast al eliminar / al vincular) | E22 (y también Edge al cambiarlas de teléfono) | La pestaña inicial es "Dispositivo", que solo lista documentos `content://`; el documento de prueba (`/app/a.pdf`) es de la app y está en "Mis archivos". Se vio en una captura de pantalla durante la espera | **Corregida** (la prueba selecciona "Mis archivos") |
| `VoiceGenderProbeInstrumentedTest` (×2, voz aguda y grave) | Edge, E22 | El mock `every { tts.voice = any() } just Runs` stubbea `setVoice` (devuelve `Int`) con `Unit`; la llamada real lanza `ClassCastException`, que `detectIsFeminineVoice` captura y devuelve `null` (visto en logcat) | **Corregida** (`returns TextToSpeech.SUCCESS`) |

Tras los arreglos se repitieron en su teléfono: Edge 12/12 OK (clase de navegación completa + ComparePdf + Converter + QR logo) y E22 1/1 OK (Converter). Las 4 que quedaban (Library ×2, voz ×2) se aislaron cambiándolas de teléfono — fallaron igual en ambos, así que no era el dispositivo — y pasan tras el arreglo en los dos: Library 9/9 y voz 6/6, en Edge y en E22.

## Hallazgo sobre el CI: sus fallas de pruebas instrumentales están ocultas

El reporte de los emuladores de CI del PR #98 (run `37685670958`) tiene **12 fallas de 780**, y los 20 checks salieron verdes. Causa: `DeviceProviderInstrumentTestTask.ignoreFailures = true` en `app/build.gradle.kts` y `continue-on-error: true` en `ci.yml`; el detalle solo queda en los artefactos `reporte-compose-ui-testing-shard-N`.

9 de las 12 son las mismas de los teléfonos (ya corregidas arriba). Las otras 3 no fallaron en los teléfonos y se reprodujeron/diagnosticaron en un emulador de pantalla chica (320x640 dp, inglés), igual que el de CI:

| Prueba | Causa | Estado |
|---|---|---|
| `AgendaEventEditorDialogTest.conTituloEnEdicion_guardarHabilitadoEInvocaOnSave` | El contenido del diálogo hace scroll y en 640 dp el botón **Guardar queda fuera de pantalla**; `performClick()` sin scroll cae fuera de la ventana y no llama a `onSave` (`expected:<1> but was:<0>`). Reproducido | **Corregida** (`performScrollTo()` antes del clic) |
| `ViewerPasswordTest.pdfProtegido_contrasenaIncorrecta…` | El mensaje sí aparecía, pero **en inglés** (`Incorrect password. Check it and try again.`): `ViewerViewModel` lo arma con `context.applicationContext`, que usa el idioma del dispositivo, y la prueba lo buscaba en el español forzado. Pasaba en los teléfonos solo porque están en español. Visto en el volcado de semántica | **Corregida** (la prueba espera el texto tal como lo genera el ViewModel). **Hallazgo de producción abierto**, ver abajo |
| `QrCreatorFlowsTest.url_generaElCodigoYLoGuardaEnElHistorial` | `entries.size == 0` justo tras mostrarse el resultado. **No se reproduce** (ni sola, ni con la clase completa de 23 pruebas, ni en teléfonos). Causa no confirmada; hipótesis: intermitencia del emulador lento de CI | **Mitigada, no confirmada** (la prueba espera a que el historial se escriba en vez de leerlo al instante) |

También se mejoró `waitUntilOrDump`: `printToLog` imprimía solo la raíz (`maxDepth` por defecto = 0), inservible para diagnosticar; ahora imprime el árbol completo.

### Hallazgo de producción abierto: mensajes del Visor en el idioma equivocado

`ViewerViewModel.loadDocument()` guarda `context.applicationContext` y con él genera textos (`pdf_pw_wrong_password_retry`, `pdf_pw_read_error`, `viewer_error`…). El contexto de aplicación **nunca lleva el idioma elegido dentro de la app** (solo `MainActivity.attachBaseContext()` lo aplica; ver la nota H8 de `ScanSessionManager`). Un usuario cuyo idioma en la app difiere del del teléfono ve esos mensajes en el idioma del teléfono. No está corregido; pendiente de decisión (arreglo propuesto: construir los textos con un contexto localizado vía `LanguageManager.applyLanguage()`).

Implicación: "CI en verde" no garantiza que las instrumentadas pasen. Decisión pendiente: quitar las salvaguardas una vez que las pruebas estén en 0 fallas, o añadir un paso que lea los reportes y falle si hay fallas nuevas.

## Pendiente de la suite

- Pruebas manuales M1–M15 (`suite-pruebas-release.md`): no ejecutadas.
- Reconfirmar con una corrida completa de las 780 en los dos teléfonos tras los arreglos.
- Decidir si se corrige el idioma de los mensajes del Visor (hallazgo de producción de arriba).
- El Moto E22 quedó sin la build de Play (se desinstaló para poder instalar el debug); reinstalarla desde el enlace de la prueba cerrada antes de M7/M8.
