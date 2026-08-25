# ── DocuSmart ProGuard Rules ──────────────────────────

# Mantener modelos de datos
-keep class com.docsmart.** { *; }

# Firebase
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**

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

# ZXing
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# ML Kit
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# CameraX
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }

# Compose
-keep class androidx.compose.** { *; }
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