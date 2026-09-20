# Backlog — Ronda 19 (2026-09-20): estabilizar las pruebas instrumentadas en el emulador de CI

Punto de partida: tras la ronda 18 la cobertura de SonarCloud subió a ~52% (New Code 54.9%), pero el job instrumentado del CI mostraba **72 de 261 pruebas fallando** (job `continue-on-error`, por eso nadie lo veía). Cada prueba que pasa suma cobertura, así que estabilizarlas es la vía más barata para subir el número.

## Causa raíz (reproducida localmente)
- El emulador de CI es de **320×640 px a 160 dpi** (skin 320x640). Se reprodujo creando un AVD local `DocuSmart_CI` con esas dimensiones (`~/.android/avd/DocuSmart_CI`), animaciones desactivadas, y corriendo `am instrument` ahí. Los mismos fallos aparecieron.
- **Bug de las pruebas:** todas forzaban el idioma con `LocalContext provides forceLocale(...)`, pero con Compose UI 1.11 `stringResource` lee `LocalResources`, no `LocalContext`. Con el emulador en inglés, la UI se veía en inglés ("New event", "Title") y las pruebas buscaban textos en español. En el teléfono (en español) nunca se notó. Esto explica gran parte de los fallos "históricos" (14-15 de 30) documentados en `compose-ui-testing.md`.
- **Corrección:** en los 27 archivos de androidTest que forzaban locale se añadió `LocalResources provides <ctx>.resources`.

## Resultado en el emulador tipo CI
72 → 17 fallos, y tras arreglar las pruebas propias que seguían fallando (Agenda ×3, ScanColorMode ×1): quedan **13**, todas de pruebas anteriores a la ronda 18 (Library ×3, QrCreator ×2, Security ×2, Study ×2, Converter, Settings, ViewerScreen, ViewerSearch). Pantalla en blanco/timeouts en algunas (QrCreator/Library/Converter/Settings/Study), `hiltViewModel()` sin Hilt en `NotesTab`, teclado numérico del PIN, contenido bajo el pliegue en el Visor.

## Otros hallazgos
- `waitUntilOrDump` llama a `onRoot()` al vencer el timeout: con un diálogo abierto hay 2 roots y esa llamada lanza `IllegalStateException`, **enmascarando** el fallo real. Conviene volcar con `onAllNodes(isRoot())`.
- Se quitaron 2 pruebas de la pestaña Calendario de `AgendaScreenTest` (inestables en pantalla chica; `AgendaCalendarViewTest` cubre esa vista).

## Pendiente
- Estabilizar las 13 pruebas anteriores restantes (cada una que pase suma cobertura).
- ScanResultScreen (1070 líneas sin cubrir), StudyScreen (1763), QrScreen (667), ViewerScreen (653), DocuSmartNavGraph, SecurityScreen, SettingsScreen, MainActivity, receivers de recordatorios, PomodoroTimerService, Theme/Type/Shape.
