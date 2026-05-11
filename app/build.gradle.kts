plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

// ── Resolver duplicados de databinding ───────────────
configurations.all {
    resolutionStrategy {
        force("androidx.databinding:databinding-common:8.7.0")
    }
    exclude(group = "org.jetbrains", module = "annotations-java5")
}

android {
    namespace = "com.docsmart"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.docsmart"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // ── Evitar conflictos de dependencias ─────────────
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

dependencies {
    // ── Core Android ──────────────────────────────────
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // ── Compose BOM ───────────────────────────────────
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.foundation)

    // ── Navegación ────────────────────────────────────
    implementation(libs.androidx.navigation.compose)

    // ── Lifecycle / ViewModel ─────────────────────────
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // ── Hilt ──────────────────────────────────────────
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // ── Coil (imágenes) ───────────────────────────────
    implementation(libs.coil.compose)

    // ── Coroutines ────────────────────────────────────
    implementation(libs.kotlinx.coroutines.android)

    // ── iText7 (PDF) ──────────────────────────────────
    implementation("com.itextpdf:itext7-core:7.2.5") {
        exclude(group = "org.bouncycastle")
    }

    // ── Apache POI — Word, Excel, PowerPoint ──────────
    implementation("org.apache.poi:poi:5.2.3") {
        exclude(group = "org.apache.xmlbeans")
        exclude(group = "com.github.virtuald")
        exclude(group = "org.junit.jupiter")
        exclude(group = "com.zaxxer")
    }
    implementation("org.apache.poi:poi-ooxml:5.2.3") {
        exclude(group = "org.apache.xmlbeans")
        exclude(group = "com.github.virtuald")
        exclude(group = "org.junit.jupiter")
        exclude(group = "com.zaxxer")
        exclude(group = "org.apache.xmlbeans", module = "xmlbeans")
    }

    // ── AdMob ─────────────────────────────────────────
    implementation("com.google.android.gms:play-services-ads:23.3.0")

    // ── Timber (logging) ──────────────────────────────
    implementation("com.jakewharton.timber:timber:5.0.1")

    // ── CameraX ───────────────────────────────────────
    val cameraxVersion = "1.3.4"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")
    implementation("androidx.camera:camera-extensions:$cameraxVersion")

    // ── ML Kit Document Scanner ───────────────────────
    implementation("com.google.android.gms:play-services-mlkit-document-scanner:16.0.0-beta1")

    // ── Testing ───────────────────────────────────────
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

// ── Biometría ─────────────────────────────────────────
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")

}