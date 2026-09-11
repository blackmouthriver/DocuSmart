# ── DocuSmart ProGuard Rules ──────────────────────────

# Optimización de app 2026-09-11: la regla `-keep class com.docsmart.** { *; }`
# que vivía acá mantenía el 100% del código propio sin ofuscar ni reducir --
# era la causante principal del 13% de optimización reportado por Play
# Console (bibliotecas nativas 52MB de DEX). Investigado antes de sacarla:
# ningún dato propio se (de)serializa por reflexión (todo el almacenamiento
# JSON de Modo Estudio usa org.json.JSONObject/JSONArray a mano, no Gson/
# kotlinx.serialization), Room protege sus propias @Entity vía sus reglas
# de consumidor incluidas en el AAR (sin necesidad de -keep manual, ya
# funcionaba así antes de esta regla), y Hilt ya tiene sus propias reglas
# explícitas más abajo. Sin reemplazo -- si algo específico necesita
# mantenerse (encontrado durante la verificación de un build de release
# real), se agrega acá con el alcance más chico posible, no como blanket
# rule de nuevo.

# Firebase / Google Play Services -- Optimización 2026-09-11: se sacó el
# blanket `-keep class com.google.firebase.** { *; }` / `com.google.android.gms.**`
# que mantenía TODO Firebase y TODO Play Services (incluye AdMob) sin
# ofuscar. Ambos AAR ya traen sus propias reglas de consumidor (así
# funciona Firebase/GMS hace años, por eso la mayoría de apps no necesitan
# -keep manual) -- se deja solo `-dontwarn` por las referencias cruzadas
# entre módulos, y las reglas de Crashlytics de abajo (esas sí concretas:
# necesarias para que los stack traces de reportes de fallos sean legibles).
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# Crashlytics
-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.Exception
-keep class com.google.firebase.crashlytics.** { *; }

# iText7
-keep class com.itextpdf.** { *; }
-dontwarn com.itextpdf.**

# Apache POI (requiere xmlbeans para leer/escribir .docx/.xlsx — sin -keep,
# R8 puede eliminar clases que XmlBeans carga por reflexión en build de release)
-keep class org.apache.poi.** { *; }
-dontwarn org.apache.poi.**
-keep class org.apache.xmlbeans.** { *; }
-dontwarn org.apache.xmlbeans.**
-keep class org.openxmlformats.schemas.** { *; }
-dontwarn org.openxmlformats.schemas.**

# Dependencias opcionales de POI/commons-compress (log4j2, slf4j, osgi,
# zstd/xz, anotaciones bnd/findbugs) — nunca se cargan en runtime en Android,
# solo se referencian bajo try/catch ClassNotFoundException. R8 en modo
# estricto falla si no se le avisa explícitamente que puede ignorarlas.
-dontwarn org.apache.logging.log4j.**
-dontwarn org.apache.commons.compress.**
-dontwarn org.slf4j.**
-dontwarn org.tukaani.xz.**
-dontwarn org.osgi.**
-dontwarn aQute.bnd.**
-dontwarn edu.umd.cs.findbugs.annotations.**
-dontwarn com.github.luben.zstd.**

# Bug real corregido 2026-09-03: assembleRelease fallaba en R8 (fatal, no
# solo warning) — org.apache.commons.imaging (transitiva de POI, parsers de
# formatos de imagen exóticos como PCX/RGBE que DocuSmart nunca ejercita)
# referencia java.awt.image.*/java.awt.color.* -- clases que directamente
# no existen en el android.jar de compileSdk (mismo límite ya documentado
# para Rectangle2D/Dimension en el visor de PowerPoint), así que R8 no
# puede resolverlas al minificar. Nunca se llega a esos code paths en
# runtime real, igual que el resto de dependencias opcionales de POI.
-dontwarn java.awt.**

# ZXing -- Optimización 2026-09-11: sacado el -keep, ZXing es Java puro sin
# carga dinámica de clases (solo lo usa QrScreen.kt para codificar el QR
# generado); consumer-rules del AAR (si trae alguna) alcanzan.
-dontwarn com.google.zxing.**

# ML Kit -- SIN TOCAR a propósito: escáner de documentos, lectura de
# códigos de barra y reconocimiento de texto cargan módulos dinámicamente
# vía Play Services, con patrones de reflexión conocidos. Mayor riesgo real
# de esta lista, se deja para una pasada aparte con verificación exhaustiva.
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# CameraX -- Optimización 2026-09-11: sacado el -keep, mismo criterio que
# Firebase/GMS (AndroidX ya trae sus propias consumer-rules); solo se usa
# para el Lector de QR en vivo (QrScreen.kt).
-dontwarn androidx.camera.**

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }

# Compose -- Optimización 2026-09-11: sacado el -keep. Compose Compiler
# genera código que se llama directo (no por reflexión) y AndroidX ya trae
# sus propias consumer-rules para lo que sí necesita preservarse
# (composables anotados, etc.) -- es la dependencia más grande de la app,
# mayor ganancia potencial de tamaño de esta lista. Verificación exhaustiva
# en dispositivo real antes de fusionar: toda la app es Compose, así que
# cualquier problema real aparecería como un crash o pantalla en blanco al
# navegar, no algo sutil.
-dontwarn androidx.compose.**

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**

# Timber
-keep class timber.log.** { *; }
-dontwarn timber.log.**

# Mantener nombres de clases para crashes legibles
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions