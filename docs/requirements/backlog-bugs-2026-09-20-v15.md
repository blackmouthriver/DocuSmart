# Backlog de bugs — Decimoctava auditoría general (2026-09-20)

Ronda 18: subir la cobertura de Sonar (New Code 38.5%; total 37.4% tras la ronda 17) sobre la lista de archivos Compose al 0%. Hallazgo clave: el CI **ya** corre un emulador con `connectedDebugAndroidTest` y fusiona esa cobertura en SonarCloud; esas pantallas estaban en 0% porque nadie les había escrito pruebas instrumentadas. Se optó por eso (no Robolectric): cero dependencias nuevas.

## Pruebas nuevas (~250)
- **Instrumentadas (Compose, `app/src/androidTest`)**, ~220 en 4 áreas: Agenda y diálogos de vincular documento; Convertidor y Herramientas PDF (formularios y vistas previas con PDF real de iText); core UI/Inicio/Biblioteca/Premium/PdfPassword; Escáner/QR (resultado, formularios, historial, chips de color, ScannerScreen).
- **JVM**: `AppLibraryPickerViewModelTest`, `MediaUriUtilsTest`, `CrashlyticsTreeTest`, `PdfPasswordDialogViewModelTest`, `DatabaseMigrationsTest`, `ScanImageEditorViewModelTest`.
- Ejecutadas en el Motorola con `am instrument` (sin desinstalar la app): todas las nuevas pasan (tras corregir 4 tests propios). Suite JVM completa en verde.

## Bugs reales
- **Media**: `AppLibraryPickerViewModel` sin manejo de errores: una excepción de Room tumbaba la app y, si se capturaba, `isLoading` quedaba en true (spinner eterno en los diálogos de vincular documento).
- **Baja-Media**: `canonicalMediaUri` no capturaba `NumberFormatException` (URI de colección de MediaStore tumbaba la app).
- **Baja-Media (privacidad)**: 5 pantallas de PDF (`Crop/Redact/Reorder/Rotate/Sign`) enviaban a Crashlytics el `Throwable` completo (posible ruta/URI).
- Tests que ya existían y que **rompió el rediseño de Modo Estudio/Inicio** (ocultos porque el job instrumentado de CI es `continue-on-error`): `StudyScreenTest.guardarNota_*` (navegaba por la pestaña "Notas", ya no hay pestañas → ahora `initialTab = 1`) y `HomeScreenTest` ×3 (las 2 tarjetas nuevas empujan "Recientes" fuera de la zona compuesta del LazyColumn → ahora se desplaza hasta el documento).

## Fragilidad para el emulador de CI (revisión dedicada)
Corregido: 5 archivos de scanner resolvían los textos esperados en el idioma del dispositivo mientras la UI se fuerza a es-ES (en CI, inglés → habrían fallado casi todos); selector de hora 12/24h; `performScrollTo` en nodos que pueden quedar fuera de pantalla; arrastre de reordenar.

## Evaluado y no corregido
- **Ya fallaban en CI antes de esta ronda** (job `continue-on-error`): Home, Library, Trash, QrCreator, Security, Settings, Study (`hiltViewModel()` sin Hilt en `NotesTab`), Viewer. En el teléfono fallan además: `SecurityScreenTest` ×2, `QrCreatorScreenTest.crearQrDeUrl_*`, `StudyScreenTest.guardarNota_*`, `ViewerSearchTest`. Conviene una ronda dedicada a estabilizar la suite instrumentada existente: cada test que pasa suma cobertura en Sonar.
- `CategoryFilter` con rótulos ("Todos", "Imágenes", "Texto", "Escaneados") hardcodeados en español; `AdBanner` con texto hardcodeado y sin uso (código muerto).
- Sin cubrir por diseño: launchers de cámara/ML Kit, intents externos, `PdfPasswordScreen` con archivo elegido (usa `hiltViewModel()` en `FileSourcePickerDialog`), `MainActivity`.
- `connectedDebugAndroidTest` desinstala la app (borra datos): en dispositivo real usar `am instrument`.

## Verificación
ktlint, detekt (baseline regenerado), lint, suite JVM completa y compilación de androidTest en verde. La cobertura de Sonar de esta ronda se mide tras el push (el job instrumentado del CI corre en el emulador).
