# Ajuste UX — Modo Estudio con menú de entrada (2026-09-19)

Feedback de testers: "el acceso a Modo Estudio es confuso" (pestañas + tres íconos sueltos en el encabezado).

## Flujo nuevo
- Modo Estudio abre en un **menú** con 4 tarjetas: Lectura, Notas, Pomodoro y Agenda y calendario.
- **Lectura**: historial "Continuar leyendo"; con documento abierto, botones con texto "Abrir documento" y "Elegir voz" (abre el modal de voces); sin documento, "Elegir voz" bajo "Abrir documento".
- **Notas**: sin cambios (ya tenía recordatorios y notas guardadas).
- **Pomodoro**: botón "Ver estadísticas" al final (antes un ícono en el encabezado).
- **Agenda y calendario**: pantalla existente (lista/calendario), ahora también con "Inicio".
- Cada vista: banner de anuncio + banner de color + "Volver" + "Inicio". "Volver" desde Lectura/Notas/Pomodoro regresa al menú; desde el menú sale de Modo Estudio; Atrás del sistema igual.
- Inicio: nueva tarjeta **Agenda** en accesos rápidos (10 tarjetas). Los atajos Lectura/Notas/Pomodoro se mantienen y abren directo su vista (decisión del usuario).

## Técnico
- `DocuSmartTopBanner` acepta `onHome` opcional (botón "Inicio" a la derecha de "Volver"); sin él, las demás pantallas no cambian.
- `StudyMenu.kt` (menú + `studyViewForTab`/`initialStudyTab`, 5 tests); `selectedTab` pasa a `rememberSaveable` (-1 = menú).
- Strings nuevos en 12 idiomas (`strings_r18_study_menu.xml`).

## Verificación
Compilación, ktlint, detekt, lint y suite completa en verde. En el Motorola: Inicio (tarjeta Agenda), atajo Lectura → vista con Volver/Inicio, modal "Elegir voz", Volver → menú, Pomodoro con "Ver estadísticas" y Atrás del sistema → menú, Notas, Agenda (Volver → menú de Estudio), rotación dentro de Pomodoro sin perder la vista, Inicio → Home; cero `FATAL EXCEPTION`/ANR.

## Segunda pasada (feedback del usuario tras probar el menú)
- **Inicio**: nueva tarjeta **Modo Estudio** en accesos rápidos (abre el menú); ahora son 11 tarjetas (Modo Estudio, Lectura, Notas, Pomodoro y Agenda conviven).
- **Lectura**: el banner y el título dicen "Lectura" (no "Modo Estudio"). Vista sin documento: "Lectura" → "DocuSmart le ayuda a leer por ti tus PDF" → **Abrir documento** → "DocuSmart te ofrece un ayudante para leer tus documentos" → **Elegir voz** → "Continuar leyendo" con el historial. "Abrir documento" ya no se corta (2 líneas).
- **Bug: la lectura se repetía al terminar el documento.** No se pudo reproducir con PDFs de 1 página (termina bien), así que se blindó: `finishReading()` detiene el motor TTS, invalida callbacks pendientes (`ttsSession`), quita el progreso guardado y muestra "Terminaste de leer el documento" con **Volver a leer** (reinicia desde el principio solo si el usuario lo pide). Además `ttsHighestStarted` corta cualquier retroceso a un párrafo ya leído (una repetición).
- Textos nuevos en 12 idiomas (`strings_r18_study_menu.xml`).

Verificación en el Motorola: tarjeta Modo Estudio en Inicio → menú; vista Lectura con el orden pedido; lectura completa de un PDF hasta el final (~110 s): se detuvo, mostró "Terminaste de leer el documento / Volver a leer" y siguió detenida 40 s más; cero `FATAL EXCEPTION`/ANR. Compilación, ktlint, detekt, lint y la suite completa en verde.
