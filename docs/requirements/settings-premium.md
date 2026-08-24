# Módulo: Ajustes + Premium

> Parte de [`CONTEXT.md`](../../CONTEXT.md). Cubre Ajustes (idioma, tema,
> almacenamiento, privacidad, ayuda, restablecer, acerca de, compartir,
> calificar) y Premium (planes, compra, restaurar, límites de uso free).

**Estado:** 2 bugs reales corregidos — el límite diario de uso gratis para
Herramientas PDF (requerimiento #16) existía por completo en
`DailyLimitManager` pero nunca se llamaba desde `PdfToolsViewModel`, así que
nunca se aplicaba en la práctica; y "Restablecer configuración" forzaba
español sin importar el idioma del dispositivo (mismo tipo de bug ya
corregido en TTS/reconocimiento de voz de Modo Estudio). La compra
simulada de Premium es un placeholder ya documentado en el propio código
("Fase 10 se conecta Play Billing real") — no es un bug oculto, es trabajo
pendiente conocido y no se implementó en esta pasada (ver §7). 11 tests
nuevos.
**Código relacionado:** `features/settings/**`, `features/premium/**`,
`core/ads/DailyLimitManager.kt`, `core/premium/PremiumManager.kt`,
`core/ui/LanguageManager.kt`, `core/ui/components/DailyLimitDialog.kt` (nuevo,
compartido con Conversión).

---

## 1. Alcance

Dos módulos relacionados por la monetización freemium:

1. **Ajustes** — idioma, tema, almacenamiento/caché, privacidad, ayuda,
   restablecer configuración, acerca de, compartir/calificar la app.
2. **Premium** — planes (mensual/anual/de por vida), compra, restaurar
   compras, y el límite diario de uso gratis que empuja hacia Premium
   (conversiones y herramientas PDF).

---

## 2. Requerimientos funcionales

- **RF-SET-01** El sistema debe permitir cambiar el idioma de la app entre los 5 soportados (es/en/de/pt/ru), aplicando el cambio de inmediato.
- **RF-SET-02** El sistema debe permitir cambiar el tema (claro/oscuro/sistema).
- **RF-SET-03** El sistema debe mostrar el almacenamiento usado por archivos generados por la app (conversiones + herramientas PDF), con conteo y tamaño real, y permitir borrarlos con confirmación.
- **RF-SET-04** El sistema debe permitir restablecer tema e idioma a sus valores por defecto, y borrar los archivos generados por la app.
- **RF-SET-05** "Restablecer configuración" debe restablecer el idioma al que tendría la app recién instalada en este dispositivo, no a un idioma fijo sin relación con el dispositivo del usuario.
- **RF-PREM-01** El sistema debe mostrar los 3 planes Premium (mensual, anual, de por vida) con precio y ahorro relativo.
- **RF-PREM-02** El sistema debe limitar a los usuarios no-Premium a 5 conversiones y 3 usos por cada herramienta PDF (unir/dividir/comprimir/rotar) al día, reseteando el contador cada día.
- **RF-PREM-03** El sistema debe permitir desbloquear un uso adicional viendo un anuncio recompensado, tanto para conversiones como para herramientas PDF.
- **RF-PREM-04** El sistema debe ocultar los banners de anuncios y los límites de uso para usuarios Premium.

### Backlog — no implementado
- **RF-PREM-05** Conectar Play Billing real (comprar/restaurar compras) — hoy simulado con un `delay()` y un flag local, ya documentado en el código como pendiente de "Fase 10". Requiere configuración de productos en Play Console antes de poder implementarse.
- **RF-SET-06** Detección de idioma por defecto según geografía de Play Store (requerimiento #13 original) — distinto de RF-SET-05: esto es sobre qué idioma ve un usuario que **nunca** ha abierto la app, no sobre qué pasa al restablecer configuración ya usada.
- **RF-SET-07** Personalización de colores/tema por el usuario (requerimiento #19 original).

---

## 3. Requerimientos no funcionales

- **RNF-SET-01 (consistencia de idioma):** ninguna funcionalidad de la app debe forzar un idioma fijo cuando existe una señal razonable del idioma preferido del usuario (idioma configurado de la app, o si no hay ninguno, idioma del dispositivo) — mismo lineamiento que ya aplica a TTS y reconocimiento de voz en Modo Estudio.
- **RNF-PREM-01 (límites por recurso, no globales):** cada herramienta PDF (unir/dividir/comprimir/rotar) debe tener su propio contador diario independiente — usar mucho una herramienta no debe agotar el límite de las demás.
- **RNF-PREM-02 (transparencia del placeholder de billing):** mientras no exista Play Billing real, el código debe dejar explícito que la compra es simulada (comentario/nombre de función), para que no se confunda con una integración real ya hecha — ya cumplido (`simulatePurchase`, comentario "Fase 10").

---

## 4. Historias de usuario con criterios de aceptación

### HU-SET-01 — Restablecer configuración respeta el idioma del dispositivo
**Como** usuario que configuró la app en un idioma distinto al español,
**quiero** que "Restablecer configuración" no me cambie el idioma a español sin razón,
**para** no tener que volver a configurar el idioma cada vez que restablezco.

- **AC1** Dado que mi dispositivo tiene un idioma soportado por la app (es/en/de/pt/ru) configurado como idioma del sistema, cuando toco "Restablecer configuración" y confirmo, entonces la app queda en ese idioma, no en español.
- **AC2** Dado que mi dispositivo tiene un idioma no soportado (por ejemplo, japonés), cuando restablezco, entonces la app usa español como respaldo — no falla ni queda en un estado inconsistente.
- **AC3** El tema (claro/oscuro/sistema) y el borrado de archivos generados por la app siguen funcionando igual que antes.

*(Corrige bug real: el botón llamaba `languageManager.setLanguage(AppLanguage.SPANISH)` incondicionalmente — mismo patrón que forzaba español en TTS y reconocimiento de voz.)*

### HU-PREM-01 — Límite diario real en Herramientas PDF
**Como** usuario no-Premium,
**quiero** que el límite de 3 usos gratis por herramienta PDF se aplique de verdad,
**para** que el plan Premium tenga un valor real y consistente con lo que promete la pantalla de Premium.

- **AC1** Dado que uso una herramienta PDF (por ejemplo, Unir) 3 veces en el mismo día sin ser Premium, cuando intento una cuarta vez, entonces veo el diálogo de límite diario alcanzado, igual que ya ocurre en Conversión.
- **AC2** Dado que veo un anuncio recompensado desde ese diálogo, cuando lo completo, entonces obtengo un uso adicional para esa herramienta.
- **AC3** Dado que agoté el límite de Unir, cuando intento usar Dividir (una herramienta distinta), entonces puedo hacerlo — cada herramienta tiene su propio contador.
- **AC4** Un usuario Premium nunca ve el límite ni el diálogo.

*(Corrige un hueco real: `DailyLimitManager.canUsePdfTool()`/`registerPdfTool()` ya existían completos — con sus 4 contadores independientes y todo — pero `PdfToolsViewModel` nunca los llamaba. El límite estaba "implementado" en el sentido de que el código existía, pero no tenía ningún efecto real para el usuario.)*

---

## 5. Bugs de QA a corregir (trazabilidad)

| Bug / hallazgo | HU que lo cubre | Estado |
|---|---|---|
| Requerimiento #16 "Límite de uso de herramientas para no-premium" — CONTEXT.md lo marcaba como "Pendiente" | HU-PREM-01 | ✅ Corregido — la lógica ya existía sin usar; se conectó a `PdfToolsViewModel` con el mismo patrón que ya usaba Conversión. Se extrajo `DailyLimitDialog` a un componente compartido (antes vivía duplicado y privado en `ConverterScreen.kt`). |
| **Bug real encontrado hoy (no reportado en la QA):** "Restablecer configuración" en Ajustes forzaba español sin importar el idioma del dispositivo. | HU-SET-01 | ✅ Corregido — nuevo `LanguageManager.deviceDefaultLanguage()`, usado solo en el flujo de restablecer (no se tocó el idioma por defecto de una instalación nueva, que sigue siendo backlog explícito, RF-SET-06). |
| "Restaurar compras / cancelar suscripción" simulado, falta Play Billing real | RF-PREM-05 (backlog) | Confirmado como placeholder ya documentado en el código (`// En Fase 10 se conecta con Play Billing`) — no es un bug oculto, requiere configuración de Play Console que no se puede hacer desde código. No implementado en esta pasada. |
| "Falta personalización de colores/estilos por el usuario" | RF-SET-07 (backlog) | Confirmado vigente, sin implementar. |
| Almacenamiento (mostrar uso + borrar caché) | RF-SET-03 | Confirmado funcionando correctamente — cuenta y tamaño reales, borrado real. |
| Ayuda, privacidad, acerca de, compartir, calificar | — | Confirmados funcionando correctamente (intents reales, URLs reales con el package name real). |

---

## 6. Plan de pruebas unitarias

| # | Cobertura | Estado |
|---|---|---|
| 1 | `DailyLimitManagerTest` — límite de conversiones alcanzado, extra por anuncio aumenta el límite, límite de herramienta PDF alcanzado, contadores independientes por herramienta, extra de herramienta PDF por anuncio, conteo por herramienta. | ✅ 8 tests, en verde |
| 2 | `LanguageManagerTest` — idioma del dispositivo soportado se detecta correctamente, idioma no soportado cae a español, reconoce inglés explícitamente con variante regional (`en-US`). | ✅ 3 tests, en verde |
| 3 | `PremiumManager`/`PremiumViewModel` — no cubiertos; `simulatePurchase`/`restorePurchases` son placeholders que se reemplazarán por completo al conectar Play Billing real (RF-PREM-05), así que no se priorizó cubrirlos con tests que quedarían obsoletos pronto. | Pendiente hasta que se implemente Play Billing real. |

---

## 7. Preguntas abiertas

| Pregunta | Notas |
|---|---|
| ¿Cuándo se aborda Play Billing real (RF-PREM-05)? | Requiere que el usuario configure los productos (`com.docsmart.premium.monthly/annual/lifetime`, ya declarados en `PremiumRepository`) en Play Console antes de poder integrarlo — no es algo que se pueda avanzar solo desde el código. |
| ¿Detección de idioma por geografía de Play Store (RF-SET-06) es prioridad, o basta con que el dispositivo decida (como ya corregido en HU-SET-01)? | Usar el idioma del dispositivo es el estándar de facto en apps Android y ya se corrigió para el flujo de restablecer; extenderlo también al primer inicio (sin depender de geografía de Play Store, que no es algo verificable desde el cliente) sería el siguiente paso natural si se decide abordar #13. |
| ¿Personalización de colores (RF-SET-07) es prioridad frente a otros pendientes del roadmap? | Sin refinar, mencionado como mejora en `CONTEXT.md`. |
