# Suite de pruebas previa a un release

Preparada para la versión **1.1.2 (versionCode 7)**. Se corre completa **antes** de generar el `.aab`; el `.aab` es la última etapa y solo se pide cuando todo lo anterior está en verde.

Estado: **preparada, no ejecutada.**

## Qué cambia en 1.1.2 (foco de la regresión)

| Cambio | Dónde | Qué puede romper |
|---|---|---|
| Tope de 8 MP por página al renderizar (crash `Canvas: trying to draw too large bitmap`) | `core/pdf/PdfPageBitmap.kt` → Visor y Modo Estudio | Nitidez/posición de resaltados y notas en PDFs normales y grandes |
| Logo de DocuSmart en notificaciones de Agenda y Notas | `core/ui/util/NotificationBranding.kt`, receivers | Notificación sin icono / icono ilegible; Pomodoro (no cambia) |
| Creador de QR: logo, botones Guardar/Compartir, historial | `QrScreen`, `QrHistoryScreen`, banner de historial | Ya fusionado en 1.1.1+; se revalida en teléfono real |
| Premium: precios en COP, oferta seleccionada, diagnóstico de Billing | `PremiumViewModel`, `BillingManager` | Compra real solo se prueba instalando desde Play |
| `<plurals>` en 12 idiomas, anti doble-toque en accesos rápidos, banner de historial QR | varios | Textos en idiomas no probados |

## Etapa 1 — Automática, sin dispositivo (`-Stages Static,Build`)

`.\scripts\release-suite.ps1 -Stages Static,Build`

Corre, cada una por separado: `ktlintCheck`, `detekt`, `lintDebug`, `testDebugUnitTest`, `assembleDebug`, `assembleDebugAndroidTest`. Criterio: todas con código 0. Duración estimada 25–35 min.

## Etapa 2 — Instrumentadas repartidas entre dispositivos (`-Stages Instrumented`)

`.\scripts\release-suite.ps1 -Stages Instrumented -Devices <serialA>,<serialB>`

- 103 archivos de pruebas instrumentadas. Con **2 dispositivos** cada uno corre la mitad (sharding nativo de `AndroidJUnitRunner`, `numShards=2`, `shardIndex=0/1`) **en paralelo**; con 1 dispositivo corre todo; con N, la N-ésima parte.
- Los dispositivos no tienen que ser iguales; conviene que sean **distintos** (p. ej. emulador API 34 + teléfono real) para cubrir más variedad. El reparto es por número de pruebas, no por duración: si un dispositivo es mucho más lento, cae como cuello de botella.
- CI usa 8 shards en emuladores `aosp_atd`; en dispositivos físicos basta con 2.
- Se desactivan las animaciones durante la corrida y se restauran al final.
- **Las pruebas escriben datos en la app instalada** (el teléfono real debe tener datos desechables; ver memoria de teléfono de pruebas).

Criterio: 0 pruebas fallidas y sin `Process crashed` en cada dispositivo.

## Etapa 3 — Manual en teléfono real (requiere dispositivo físico)

Lo que un emulador **no** puede validar. Se reparte entre los dos teléfonos para balancear (A = teléfono principal con Play instalado desde la prueba cerrada, B = segundo teléfono, idealmente otra marca/Android).

| # | Prueba | Teléfono | Pasos | Resultado esperado |
|---|---|---|---|---|
| M1 | PDF de gran formato (el crash) | A | Abrir un PDF con página > 2000×2000 pt (plano/póster; si no hay, generar uno) | Se abre sin cerrar la app; se puede hacer zoom y desplazarse |
| M2 | PDF normal + resaltado y nota | A | Abrir un PDF A4, resaltar un párrafo, anclar una nota, cerrar y reabrir | Resaltado y nota en el mismo lugar; nitidez igual que antes |
| M3 | Modo Estudio con PDF | B | Abrir el mismo PDF en Modo Estudio | Renderiza y la voz sigue el texto |
| M4 | Notificación de Agenda | A | Crear evento "al momento" a +2 min, bloquear pantalla | Suena; icono pequeño con silueta del logo, icono grande a color; tocarla abre el evento |
| M5 | Notificación de Notas | B | Programar recordatorio de repaso de una nota a +2 min | Igual que M4; tocarla abre la nota |
| M6 | Alarma exacta tras reiniciar | B | Programar evento, reiniciar el teléfono, esperar la hora | La notificación llega (receiver de arranque) |
| M7 | Compra de Premium | A | Con cuenta de prueba, desde la **build instalada por Play**: tocar "Obtener" en cada plan | Hoja de compra de Play con precio en COP; no aparece "No se pudo completar la compra" |
| M8 | Restaurar compra | A | Desinstalar/instalar y tocar "Restaurar" | Premium vuelve a activarse |
| M9 | Lector de QR (cámara) | B | Escanear un QR impreso/en pantalla | Lo lee y abre el historial |
| M10 | Creador de QR | A | Generar con logo (PNG y JPG), Guardar, Compartir, "Ver historial QR", "Vaciar historial" | Sin errores; botones con el estilo nuevo |
| M11 | Seguridad (PIN/biometría) | B | Activar PIN, cerrar la app, volver a entrar; usar huella si hay | Pide PIN/huella; carpeta segura funciona |
| M12 | Anuncios | A, B | Navegar Home, Agenda, Historial QR (usuario no premium) | Banners cargan, sin tapar botones; con Premium no aparecen |
| M13 | Idioma | B | Cambiar a ru, ja, ar/otro RTL, y revisar Premium y Convertir (plurales) | Textos correctos, sin cortes ni "1 días" |
| M14 | Rotación y segundo plano | A | Rotar con un PDF abierto; mandar a segundo plano 1 min y volver | No pierde la página ni se cierra |
| M15 | Memoria (PDF de muchas páginas) | B | Abrir un PDF de 100+ páginas | Abre o muestra error controlado; **registrar** si se cierra (riesgo conocido: render de todas las páginas a la vez) |

Registrar resultado de cada fila (OK / falla / observación) y adjuntar captura o `logcat` en las fallas.

## Etapa 4 — Verificación del paquete (solo al final)

Solo cuando 1–3 estén en verde:

1. `.\scripts\release-suite.ps1 -Stages Bundle` (necesita `keystore.properties`).
2. Confirmar en el `.aab`: `versionCode 7`, `versionName 1.1.2`, firma de release, mapping de R8 generado.
3. Subir a Prueba cerrada y confirmar que la consola no reporta advertencias nuevas.
4. Tras publicar: vigilar Crashlytics 24–48 h (no debe reaparecer `Canvas: trying to draw too large`).

## Qué dispositivos hacen falta

- **Obligatorio:** 1 teléfono real con la prueba cerrada instalada desde Play (M7, M8) y datos desechables.
- **Recomendado:** un segundo teléfono (otra marca/versión) para repartir las pruebas manuales y las instrumentadas, y para M6, M9, M11 y M13.
- El emulador `DocuSmart_CI` sirve para la etapa 2 como dispositivo extra.
