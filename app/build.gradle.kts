import com.android.build.api.variant.HasUnitTest
import java.util.Properties

// Firma de release: valores reales en keystore.properties (gitignored, ver
// keystore.properties.example) — en CI se escribe desde secrets antes del
// build. Si no existe (checkout limpio de un colaborador, o CI corriendo
// tareas que no son de release), releaseSigningConfig queda null y el build
// type release simplemente no queda firmado para Play Store — no rompe
// assembleDebug ni el resto de las tareas normales.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val releaseSigningProps = if (keystorePropertiesFile.exists()) {
    Properties().apply { load(keystorePropertiesFile.inputStream()) }
} else {
    null
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.detekt)
    jacoco
}

configurations.all {
    resolutionStrategy {
        force("androidx.databinding:databinding-common:8.7.0")
        // androidx.test.ext:junit:1.3.0 (androidTest, agregado para Compose UI
        // Testing) exige concurrent-futures 1.2.0+, pero la resolución
        // "consistente" de AGP entre el classpath de la app y el de
        // androidTest lo dejaba fijo en 1.1.0. Se fuerza 1.2.0 en ambos para
        // que sigan siendo consistentes.
        force("androidx.concurrent:concurrent-futures:1.2.0")
    }
    exclude(group = "org.jetbrains", module = "annotations-java5")
}

android {
    namespace   = "com.docsmart"
    compileSdk  = 36

    defaultConfig {
        applicationId         = "com.docsmart"
        minSdk                = 26
        targetSdk             = 35
        versionCode           = 1
        versionName           = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseSigningProps != null) {
            create("release") {
                storeFile     = rootProject.file(releaseSigningProps.getProperty("storeFile"))
                storePassword = releaseSigningProps.getProperty("storePassword")
                keyAlias      = releaseSigningProps.getProperty("keyAlias")
                keyPassword   = releaseSigningProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
            manifestPlaceholders["firebaseAnalyticsDeactivated"] = true
            manifestPlaceholders["firebaseCrashlyticsEnabled"]   = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            manifestPlaceholders["firebaseAnalyticsDeactivated"] = false
            manifestPlaceholders["firebaseCrashlyticsEnabled"]   = true
            if (releaseSigningProps != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose     = true
        buildConfig = true
    }

    lint {
        // Workaround for https://issuetracker.google.com/issues/374783344:
        // NonNullableMutableLiveDataDetector crashes lint under Kotlin 2.0.21's Analysis API.
        // Safe to disable — this project uses Compose state / StateFlow, not LiveData.
        disable += "NullSafeMutableLiveData"
    }

    bundle {
        // LanguageManager cambia el idioma manualmente en runtime (independiente
        // del idioma del dispositivo). Si Play Store reparte el AAB con split por
        // idioma (comportamiento por defecto), un usuario podría no tener
        // instalados los recursos del idioma que elige dentro de la app.
        language {
            enableSplit = false
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all { it.useJUnitPlatform() }
        }
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                // *.md en vez de listar LICENSE.md/LICENSE-notice.md/etc. una
                // por una: junit-jupiter (transitivo vía androidTest) trae
                // varios archivos de este tipo que chocan entre sí.
                "META-INF/*.md",
                "META-INF/*.kotlin_module",
                "META-INF/versions/9/previous-compilation-data.bin"
            )
            pickFirsts += setOf(
                "META-INF/AL2.0",
                "META-INF/LGPL2.1"
            )
        }
    }
}

// Pruebas de integración de Room (DocumentHistoryDaoTest) usan
// BundledSQLiteDriver para correr contra SQLite real en la JVM, sin
// Robolectric ni un emulador (recomendación oficial de Google, que además
// desaconseja Robolectric explícitamente para esto). El artefacto Android de
// sqlite-bundled no trae los binarios nativos que necesita la JVM del test
// unitario — se sustituye por su variante -jvm solo en el classpath de test.
androidComponents {
    onVariants { variant ->
        (variant as? HasUnitTest)?.unitTest?.let { unitTest ->
            unitTest.runtimeConfiguration.resolutionStrategy.dependencySubstitution {
                substitute(module("androidx.sqlite:sqlite-bundled"))
                    .using(module("androidx.sqlite:sqlite-bundled-jvm:${libs.versions.sqlite.get()}"))
            }
        }
    }
}

detekt {
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    baseline = file("$rootDir/config/detekt/baseline.xml")
    buildUponDefaultConfig = true
    autoCorrect = false
}

jacoco {
    toolVersion = "0.8.12"
}

// Reporte XML de cobertura para SonarCloud, a partir de los unit tests de
// la variante debug (no hay tests instrumentados todavía). Excluye clases
// generadas (Hilt/Dagger/KSP, R, BuildConfig) que no reflejan cobertura real.
tasks.register<JacocoReport>("jacocoTestReport") {
    dependsOn("testDebugUnitTest")

    reports {
        xml.required.set(true)
        html.required.set(true)
    }

    val fileFilter = listOf(
        "**/R.class", "**/R\$*.class", "**/BuildConfig.*", "**/Manifest*.*",
        "**/*Test*.*", "android/**/*.*",
        "**/Hilt_*.*", "**/*_Factory.*", "**/*_MembersInjector.*",
        "**/*Module_*Factory.*", "**/dagger/**", "**/*_HiltModules*.*",
        "**/di/**",
    )
    val debugTree = fileTree("${layout.buildDirectory.get()}/tmp/kotlin-classes/debug") {
        exclude(fileFilter)
    }
    val mainSrc = "$projectDir/src/main/java"

    sourceDirectories.setFrom(files(mainSrc))
    classDirectories.setFrom(files(debugTree))
    executionData.setFrom(
        fileTree("${layout.buildDirectory.get()}/jacoco") {
            include("testDebugUnitTest.exec")
        }
    )
}

dependencies {
    // ── Core Android ──────────────────────────────────────────────────────────
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // ── Compose BOM ───────────────────────────────────────────────────────────
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.foundation)

    // ── Navegación ────────────────────────────────────────────────────────────
    implementation(libs.androidx.navigation.compose)

    // ── Lifecycle / ViewModel ─────────────────────────────────────────────────
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    // ProcessLifecycleOwner (RF-SEC-08: auto-bloqueo de Carpeta Segura al
    // pasar a segundo plano) -- artefacto separado de lifecycle-runtime-ktx.
    implementation(libs.androidx.lifecycle.process)

    // ── Hilt ──────────────────────────────────────────────────────────────────
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // ── Coil ──────────────────────────────────────────────────────────────────
    implementation(libs.coil.compose)

    // ── Coroutines ────────────────────────────────────────────────────────────
    implementation(libs.kotlinx.coroutines.android)

    // ── Room (historial de documentos abiertos) ──────────────────────────────
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    // Driver real de SQLite para pruebas de integración de Room en la JVM
    // (ver sustitución de variante -jvm más arriba en androidComponents).
    testImplementation(libs.androidx.sqlite.bundled)

    // ── iText7 ────────────────────────────────────────────────────────────────
    implementation("com.itextpdf:itext7-core:7.2.5") {
        exclude(group = "org.bouncycastle")
    }
    implementation("org.bouncycastle:bcprov-jdk15on:1.70")
    implementation("org.bouncycastle:bcpkix-jdk15on:1.70")

    // ── Apache POI ────────────────────────────────────────────────────────────
    implementation("org.apache.poi:poi:5.5.1") {
        exclude(group = "com.github.virtuald")
        exclude(group = "org.junit.jupiter")
        exclude(group = "com.zaxxer")
    }
    implementation("org.apache.poi:poi-ooxml:5.5.1") {
        exclude(group = "com.github.virtuald")
        exclude(group = "org.junit.jupiter")
        exclude(group = "com.zaxxer")
    }
    implementation("org.apache.poi:poi-scratchpad:5.5.1") {
        exclude(group = "com.github.virtuald")
        exclude(group = "org.junit.jupiter")
        exclude(group = "com.zaxxer")
    }

    // ── AdMob ─────────────────────────────────────────────────────────────────
    implementation("com.google.android.gms:play-services-ads:23.3.0")
    // UMP (User Messaging Platform) -- consentimiento de anuncios UE/Reino
    // Unido (RF pendiente, ver docs/requirements/settings-premium.md). Es un
    // artefacto separado, NO viene incluido en play-services-ads -- versión
    // verificada contra el índice real de Google Maven antes de fijarla acá.
    implementation("com.google.android.ump:user-messaging-platform:4.0.0")

    // ── Play Billing (Premium: mensual/anual/lifetime) ───────────────────────
    implementation(libs.billing.ktx)

    // ── Timber ────────────────────────────────────────────────────────────────
    implementation("com.jakewharton.timber:timber:5.0.1")

    // ── CameraX ───────────────────────────────────────────────────────────────
    val cameraxVersion = "1.3.4"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")
    implementation("androidx.camera:camera-extensions:$cameraxVersion")

    // ── ML Kit ────────────────────────────────────────────────────────────────
    implementation("com.google.android.gms:play-services-mlkit-document-scanner:16.0.0")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")

    // ── ZXing ─────────────────────────────────────────────────────────────────
    implementation("com.google.zxing:core:3.5.4")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0") { isTransitive = false }

    // ── Guava ─────────────────────────────────────────────────────────────────
    implementation("com.google.guava:guava:32.1.3-android")

    // ── Biometría ─────────────────────────────────────────────────────────────
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.appcompat:appcompat:1.8.0")
    implementation("com.google.android.material:material:1.14.0")

    // ── Firebase BOM ──────────────────────────────────────────────────────────
    // Sin versión propia en los artefactos individuales — el BOM es quien la
    // fija. Antes analytics/crashlytics traían una versión fija por su cuenta,
    // lo que anulaba el propósito del BOM (podían quedar desalineados entre sí).
    implementation(platform("com.google.firebase:firebase-bom:34.18.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-crashlytics")

    // ── Testing ───────────────────────────────────────────────────────────────
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("io.mockk:mockk:1.13.13")
    testImplementation("app.cash.turbine:turbine:1.2.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    // El stub de Android para unit tests deja org.json.* sin implementar
    // ("not mocked") — esta dependencia real (mismo paquete org.json) la
    // sustituye solo para los tests, sin afectar el runtime de la app.
    testImplementation("org.json:json:20231013")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.manifest)
    // ── Compose UI Testing (instrumentado — corre en dispositivo/emulador) ────
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    // mockk-android, no mockk: mockear en el dispositivo necesita soporte
    // dexmaker/bytebuddy-android, distinto del mockk de test/ (JVM).
    androidTestImplementation("io.mockk:mockk-android:1.13.13")
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

}