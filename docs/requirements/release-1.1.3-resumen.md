# Release 1.1.3 (versionCode 9) — resumen

> **Reemisión (2026-10-14):** Play Console no permitió publicar el código 8 en producción, así que el mismo contenido (mismo `versionName 1.1.3`, mismo commit funcional) se reemite con **versionCode 9**. Donde este documento diga «código 8», léase 9 para el `.aab` que se sube a producción.

Fecha: 2026-10-14. Sucede a la **1.1.2 (versionCode 7)**, ya en prueba cerrada. Esta versión reúne todo lo fusionado en `main` desde esa compilación (PR #105 a #142). Detalle técnico y mediciones en `seguimiento-post-1.1.2.md`.

## Qué cambia para el usuario

| Área | Cambio | PR |
|---|---|---|
| Visor de PDF | Render **bajo demanda**: un libro de 2 332 páginas pasa de 22–75 s y ~3 GB a 0,2–0,9 s y ~78 MB; ya no se cierra con PDFs de muchas páginas | #108 |
| Modo Estudio | Mismo render bajo demanda; abre al instante aunque el PDF sea enorme | #109 |
| Visor | Páginas pequeñas (A5, 300×400 pt) conservan su proporción: el resaltado y las notas ya no quedan desalineados en teléfonos de alta resolución | #107 |
| Premium | Se elige siempre la oferta con prueba gratis si existe (antes `firstOrNull()` sin orden garantizado); la etiqueta de ahorro se calcula con los precios reales de Play | #110 |
| Premium | Botón **Administrar suscripción** para usuarios Premium (abre las suscripciones de Google Play) | #106 |
| Idioma | Los recursos de la aplicación siguen el idioma elegido en la app, no el del teléfono (mensajes del visor incluidos) | #105 |
| Lector de QR | El historial es un botón con texto bajo el banner, igual que en el Creador | #112 |
| Seguridad | La flecha de volver de «Crea tu PIN» queda anclada arriba a la izquierda, como en las demás pantallas | #115 |
| Escáner | Compartir ya no falla al abrirse el selector en Android 16 | #107 |

## Qué cambia en la ingeniería (sin efecto visible)

- **CI:** shard 6/8 de ~30 min a ~10 min (#129); reintento de compilación ante descargas corruptas (#113); guardia que falla si se pierden pruebas unitarias (#140); guardia que impide `mockk<Activity>()` en pruebas instrumentadas (#129).
- **Pruebas:** 7 pruebas reactivadas (#107); intermitencias corregidas en Seguridad (#111), visor de documentos (#141) y almacenes de preferencias en memoria (#131).
- **Dependencias y herramientas:** Gradle 9.5.1 (#142); guava 33.7.2, annotation 1.11.0, collection 1.7.0, kover 0.9.11 y material-icons-core 1.7.8 (#130).

## Verificación previa a la versión

| Comprobación | Resultado |
|---|---|
| Suite instrumentada completa, Edge 30 Neo (Android 14) | 799 pruebas, 8/8 fragmentos `OK`, sin reintentos |
| Suite instrumentada completa, Moto E22 (Android 12) | 799 pruebas, 8/8 fragmentos `OK`, sin reintentos |
| Pruebas unitarias | 1550, 0 fallas |
| lint, detekt, ktlint | OK |
| M7 compra mensual y anual (build de Play) | OK |
| M8 restaurar compra | OK |
| «Administrar suscripción» → Google Play | OK |
| M12 anuncios (prueba de 3 días y su fin) | OK |
| PDF normal y pequeño, anotaciones con color | OK |
| Notificaciones de Agenda y Notas con logo | llegan con logo; retraso de pocos segundos reportado, **no medido** |
| Idioma en toda la interfaz y mensajes del visor | OK |

## Conocido y no incluido

- **Oferta de prueba de 7 días:** hay que crearla en Play Console; hasta entonces la app compra el plan base.
- **Material 3 1.4.0:** se evaluó alinear Compose con su BOM actual y se dejó para después de esta versión (cambia el color de los `OutlinedButton`).
- **Dependabot:** quedan sin aplicar Kotlin 2.4, iText 9 y Gradle ≥ 9.6 (requiere AGP 9), y mockk 1.14 (su CI cayó en el Visor).
- **Intermitencias de CI conocidas:** cierre de proceso de Compose en pruebas del Visor y descarga corrupta del emulador; se resuelven relanzando el job y no afectan al `.aab`.

## Despliegue recomendado

1. Subir el `.aab` a **prueba cerrada**.
2. Repetir M7/M8 con la build de Play y vigilar Crashlytics 24–48 h.
3. Promover a **producción de forma gradual (10–20 %)**.
