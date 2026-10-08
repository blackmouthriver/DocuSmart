# Release 1.1.2 (versionCode 7) — resumen de lo hecho

Documento de cierre del lote que va de la **1.1.1 (código 6)** a la **1.1.2 (código 7)**, con fecha 2026-10-08. Estado de `main`: commit `d7e28e3`, CI y Gitleaks en verde, gate de SonarCloud OK.

Los detalles finos de cada ronda siguen en los backlogs numerados (`backlog-bugs-2026-09-17-v4.md`, `-v6.md`, `-v9.md`, `-v10.md`, `-2026-09-20-v17.md`); este archivo es el índice.

## 1. Qué cambió para el usuario

| Área | Cambio | PR |
|---|---|---|
| Visor de PDF | **Crash corregido**: páginas de gran formato (planos, pósters) cerraban la app (`Canvas: trying to draw too large bitmap`, 156 MB, reportado por Crashlytics en la 1.1.0). Las páginas se renderizan con un tope de 8 MP; las A4/carta no cambian | #98 |
| Visor de PDF | Los mensajes que el Visor genera por su cuenta (contraseña incorrecta, error de lectura, error de desbloqueo) salen ahora **en el idioma elegido dentro de la app**, no en el del teléfono | #102 |
| Notificaciones | Los recordatorios de **Agenda y Notas** muestran el logo de DocuSmart (icono pequeño con la silueta del logo, azul de marca e icono grande a color). El Pomodoro no cambia | #99 |
| Creador de QR | El botón **Agregar logo** no funcionaba nunca (causa: `BitmapFactory.decodeStream` devuelve `null` con `inJustDecodeBounds`); ahora carga, escala con proporción y avisa si el archivo no es una imagen. Botón "Ver historial de QR" y "Vaciar historial" como botones claros; Guardar/Compartir rediseñados | #97 |
| Historial de QR | Banner de anuncio nuevo (unidad AdMob `BANNER_QR_HISTORY_ID`); se excluye Seguridad a propósito | #95 |
| Premium | Precios en **COP** visibles en la app (planes base configurados en Play Console); la oferta se refresca también en el plan ya seleccionado; diagnóstico de Billing (logs de consulta incompleta) y reintento si la consulta de productos quedó incompleta | #95 |
| Accesos rápidos | Anti doble-toque (500 ms) al abrir Convertir, Creador de QR, OCR, Firmar y Carpeta Segura: ya no se apila la misma pantalla dos veces | #95 |
| Idiomas | 7 textos migrados a `<plurals>` en los 12 idiomas ("1 día" / "2 días", etc.) | #95 |

## 2. Calidad y CI

| Tema | Resultado |
|---|---|
| Cobertura (ronda 23) | Pruebas nuevas en las pantallas grandes (ComparePdf, etc.) y bugs reales encontrados al escribirlas; gate de `main` pasó a ~81% (#83) |
| Gitleaks | Versión fijada (8.30.1) con verificación SHA-256: una descarga fallida ya no tumba el job por azar (#96) |
| Suite previa a release | `scripts/release-suite.ps1` + `suite-pruebas-release.md`: estática, build, instrumentadas repartidas en N dispositivos en paralelo, y `.aab` bajo pedido (#100) |
| Primera corrida en 2 teléfonos reales | 780 instrumentadas, 9 fallas, todas defectos de pruebas o del entorno de pruebas; **corregidas** (#101). Resultados detallados en `suite-pruebas-resultados-1.1.2.md` |
| CI ocultaba fallas | `ignoreFailures` + `continue-on-error` dejaban el CI verde con **12 de 780** fallas instrumentadas. Corregido con un guardia por shard (#103) y una **corrección** a una conclusión equivocada del 2026-09-26 en `deployment.md` |
| Estado final | CI de `main`: 8 shards, 780 pruebas, 0 fallidas · teléfonos: 780, 0 fallidas · unitarias: 1519, 0 fallos · SonarCloud OK (cobertura nueva 80.6%) |

## 3. Hallazgos que no estaban en el plan

1. **El CI mentía**: ver arriba. Lección: "paso en verde" no es "pruebas pasando" cuando hay `ignoreFailures`.
2. **Idioma del Visor** (corregido #102): el contexto de aplicación nunca lleva el idioma de la app; mismo patrón que H8 en `ScanSessionManager`. Si aparece otro ViewModel con `@ApplicationContext` generando textos de UI, tiene el mismo defecto.
3. **El E22 tenía la build de Play**: correr instrumentadas obliga a desinstalarla (firma distinta). `release-suite.ps1 -UninstallConflicting` lo hace solo si se pide.
4. **Scripts largos en PowerShell 5.1**: `Start-Process -Wait` espera al daemon de Gradle y se cuelga; `*>` guarda en UTF-16 y el stderr de un nativo mata el script con `ErrorActionPreference=Stop`. Resuelto con `cmd /c` + `WaitForExit()` en `release-suite.ps1`.
5. **`ci.yml` solo corre para PRs con base `main`**: un PR apilado sobre otra rama no tiene CI.

## 4. Pendiente y deuda conocida

| Item | Estado |
|---|---|
| Pruebas manuales M1–M15 (`suite-pruebas-release.md`) | Las ejecuta el usuario; no hay registro de resultados en el repo |
| Reinstalar la build de Play en el Moto E22 | Acción del usuario (se desinstaló para las instrumentadas) |
| `QrCreatorFlowsTest.url_generaElCodigoYLoGuardaEnElHistorial` | Intermitente solo en CI, causa sin confirmar; tolerada en `config/ci/instrumented-known-failures.txt` |
| Visor: render de **todas** las páginas a la vez | Un PDF de cientos de páginas puede dar OOM. Arreglo de fondo: render perezoso (página visible ± margen) |
| Premium: oferta de prueba de 7 días | Falta crearla en Play Console y endurecer la app para elegir la oferta con trial (hoy `subscriptionOfferDetails.firstOrNull()`, el orden no está garantizado) |
| Etiqueta "Ahorra 44%" | Con 6.900/46.900 COP el ahorro real es ~43%; ajustar precio o texto |
| Lector de QR | El icono de historial sigue dentro del banner (el Creador ya lo sacó) |
| `app-ads.txt` de AdMob | Tarea de hosting, sigue pendiente (ver memoria del proyecto) |

## 5. Novedades para Play Console (borrador, español, ≤ 500 caracteres)

> • Corregimos un cierre inesperado al abrir PDFs de página muy grande (planos, carteles).
> • El Creador de QR ahora permite agregar tu logo, con avisos claros si el archivo no es una imagen.
> • Los recordatorios de Agenda y Notas muestran el logo de DocuSmart.
> • Mensajes del visor de PDF en el idioma que elegiste en la app.
> • Precios de Premium en tu moneda y mejoras de estabilidad.

Para los demás idiomas, traducir este texto (la ficha de Play pide una versión por idioma).

## 6. Artefacto de release

Ver la sección siguiente de este documento, agregada al generar el `.aab`.
