# Backlog de bugs — Decimosexta auditoría general (2026-09-19)

Ronda 16: revisión de todo lo no auditado a fondo (Estudio/Agenda/Pomodoro, navegación/Ajustes/Onboarding, núcleo/seguridad) más el subsistema QR, tras el reporte de usuarios de que "imágenes y documentos no se abren al escanear desde otro celular". 4 agentes en paralelo sin Gradle; el de QR se estancó a mitad de una edición (dejó un escape `\s` inválido en `QrContentFormat.kt`, corregido) y su trabajo se verificó a mano.

## Núcleo / Seguridad
- **Alta**: `DailyLimitManager` — el ancla del reloj de confianza no avanzaba; tras un reinicio los contadores diarios no se reseteaban hasta acumular ese uptime. Ahora la lógica es pura (`computeTrustedClock`), avanza en cada uso (la revisión adversarial detectó que solo avanzaba al cambiar de día) y hay migración única (`reset_clock_version`).
- Media: `KEY_EXTRACT_IMAGES` no se reseteaba (Extraer imágenes agotado para siempre en free); lecturas-modificaciones-escrituras sin lock (`@Synchronized`).
- Media: `resetPinAndWipeFiles` no borraba `secure_preview`; `verifyPin` con salt corrupto tumbaba el desbloqueo y comparaba sin tiempo constante; mover a/desde Carpeta Segura dejaba copias parciales (ahora se limpian, salvo si el destino ya existía).
- Baja: nombres de salida > 60 caracteres (tope sin partir surrogates); nombre real de archivo en un log WARN a Crashlytics.

## Navegación / Ajustes / Onboarding
- **Alta**: `MainActivity` reprocesaba el Intent original al rotar/restaurar (reabría Visor/Agenda/Nota); cambio de idioma con `CLEAR_TASK` mandaba al splash (ahora `recreate()`); idioma de primer arranque inconsistente (`resolveLanguageCode`).
- Media: `takePersistableUriPermission` sin el flag (SecurityException en Drive/WhatsApp); "Restablecer" tragaba `CancellationException`; `openPlayStore`/`sendSupportEmail` sin app disponible; "Ver tutorial" reseteaba `completed`; analytics recibía `agenda?openEventId={…}` como nombre de pantalla; logs con Throwable.
- `backup_rules.xml`/`data_extraction_rules.xml` ya excluyen todos los dominios explícitamente.

## Estudio / Agenda / Pomodoro
- **Alta**: "Leer todo" (TTS) repetía el texto (cada `onDone` re-encolaba); callbacks tardíos tras Detener; cambiar de documento no detenía la lectura; párrafos > 4000 caracteres se omitían en silencio (`splitForSpeech`).
- Media: reprogramación tras reinicio abortaba todo si un `schedule()` fallaba; mes/fecha/hora fijos en español y 24h; primer día de semana fijo en lunes; estadísticas de Pomodoro erróneas en cambio de hora (DST); logs con datos reales a Crashlytics.

## Subsistema QR
- Reader/Creator: `isProtectedPayload` (un QR de texto "PROTECTED: …" ya no exige contraseña imposible); esquema `HTTPS://` en mayúsculas; `openUrl` solo abre http/https/mailto/tel; se rechazan `content://` del propio paquete (no otorgar lectura a datos de DocuSmart); `loadBitmapFromUrl` solo http(s); vCard/VEVENT con CRLF (RFC 2426/5545), campos recortados, parseo de UTC (`Z`), vCard con grupos y líneas plegadas, `WIFI:` anclado, SAE/WPA3.
- Creador: URL/email/teléfono ahora se arman con `buildUrlQrPayload`/`buildEmailQrPayload`/`buildPhoneQrPayload` ("httpbin.org", espacios, paréntesis).
- **NO resuelto (decisión del usuario: diferido)**: los QR de Imagen/Documento llevan un `content://` local que no abre en otro teléfono. Diseño elegido y prototipado (servidor HTTP de un solo archivo en la Wi-Fi local, token de 128 bits, vence a los 10 min) — el código quedó FUERA del repo (scratchpad de la sesión `qr-wifi-share/`: `LocalFileServer.kt`, `LanAddress.kt` y sus tests) hasta retomarlo. Requiere UI (info de misma Wi-Fi, cuenta atrás, botón Detener), strings en 12 idiomas y revisión de seguridad propia.

## Evaluado y no corregido (bajo)
- `agendaWeekStart`/`is24Hour`: el locale de la app no lleva país (`Locale(idioma)`), el primer día de semana sale del país por defecto del idioma; `deviceDefaultLanguage()` usa `Locale.getDefault()` ya sobrescrito.
- `extractWifiField` no distingue `\;P:` dentro de un SSID (caso rarísimo).
- Helpers `encodeQrMatrix`/`qrPixelScale`/`renderQrPixels` aún sin cablear en `generateQrBitmap` (tamaño fijo 512 y UTF-8 siempre).
- `DailyLimitManager`: el tiempo con el teléfono apagado se pierde (conservador a propósito).
- `extractionFailed` en Estudio: el mensaje de error se lee/guarda como párrafo; Pomodoro con Doze; `ACTION_SEND` no soportado; `MY_PACKAGE_REPLACED` para alarmas tras actualizar (vale la pena evaluarlo); permisos de almacenamiento denegados sin aviso.

## Verificación
Suite completa en verde (compilación main/test/androidTest, detekt con baseline regenerado, ktlint, lintDebug). Dispositivo real (Motorola Edge 30 Neo, vertical): arranque en frío, Inicio, Crear QR (URL "httpbin.org de" genera QR), rotación horizontal/vertical sin cerrar la app, Ajustes, Estudio (Lectura/Notas/Pomodoro) y Agenda (lista y calendario, fecha larga en español, semana desde lunes); cero `FATAL EXCEPTION`/ANR. No probado en vivo: TTS "Leer todo", escaneo de QR de terceros, Onboarding.
