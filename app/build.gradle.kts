plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.detekt)
}

configurations.all {
    resolutionStrategy {
        force("androidx.databinding:databinding-common:8.7.0")
    }
    exclude(group = "org.jetbrains", module = "annotations-java5")
}

android {
    namespace   = "com.docsmart"
    compileSdk  = 35

    defaultConfig {
        applicationId         = "com.docsmart"
        minSdk                = 26
        targetSdk             = 35
        versionCode           = 1
        versionName           = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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

detekt {
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    baseline = file("$rootDir/config/detekt/baseline.xml")
    buildUponDefaultConfig = true
    autoCorrect = false
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

    // ── Hilt ──────────────────────────────────────────────────────────────────
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // ── Coil ──────────────────────────────────────────────────────────────────
    implementation(libs.coil.compose)

    // ── Coroutines ────────────────────────────────────────────────────────────
    implementation(libs.kotlinx.coroutines.android)

    // ── iText7 ────────────────────────────────────────────────────────────────
    implementation("com.itextpdf:itext7-core:7.2.5") {
        exclude(group = "org.bouncycastle")
    }
    implementation("org.bouncycastle:bcprov-jdk15on:1.70")
    implementation("org.bouncycastle:bcpkix-jdk15on:1.70")

    // ── Apache POI ────────────────────────────────────────────────────────────
    implementation("org.apache.poi:poi:5.2.3") {
        exclude(group = "com.github.virtuald")
        exclude(group = "org.junit.jupiter")
        exclude(group = "com.zaxxer")
    }
    implementation("org.apache.poi:poi-ooxml:5.2.3") {
        exclude(group = "com.github.virtuald")
        exclude(group = "org.junit.jupiter")
        exclude(group = "com.zaxxer")
    }
    implementation("org.apache.poi:poi-scratchpad:5.2.3") {
        exclude(group = "com.github.virtuald")
        exclude(group = "org.junit.jupiter")
        exclude(group = "com.zaxxer")
    }

    // ── AdMob ─────────────────────────────────────────────────────────────────
    implementation("com.google.android.gms:play-services-ads:23.3.0")

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
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0") { isTransitive = false }

    // ── Guava ─────────────────────────────────────────────────────────────────
    implementation("com.google.guava:guava:32.1.3-android")

    // ── Biometría ─────────────────────────────────────────────────────────────
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.appcompat:appcompat:1.8.0")
    implementation("com.google.android.material:material:1.14.0")

    // ── Firebase BOM ──────────────────────────────────────────────────────────
    implementation(platform("com.google.firebase:firebase-bom:32.7.4"))
    implementation("com.google.firebase:firebase-analytics:23.2.0")
    implementation("com.google.firebase:firebase-crashlytics:18.4.3")

    // ── Testing ───────────────────────────────────────────────────────────────
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("io.mockk:mockk:1.13.13")
    testImplementation("app.cash.turbine:turbine:1.1.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    // El stub de Android para unit tests deja org.json.* sin implementar
    // ("not mocked") — esta dependencia real (mismo paquete org.json) la
    // sustituye solo para los tests, sin afectar el runtime de la app.
    testImplementation("org.json:json:20231013")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

}