plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.docsmart"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.docsmart.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
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
}

dependencies {
    // ── Core Android ──────────────────────────────────
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // ── Jetpack Compose ───────────────────────────────
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    // ── Navegación ────────────────────────────────────
    implementation(libs.androidx.navigation.compose)

    // ── ViewModel ─────────────────────────────────────
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // ── Hilt (Inyección de dependencias) ─────────────
    implementation(libs.hilt.android)
    implementation(libs.androidx.compose.foundation)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // ── Coroutines ────────────────────────────────────
    // ── Eliminada duplicación — solo una declaración ──
    implementation(libs.kotlinx.coroutines.android)

    // ── Selección de imágenes / archivos ──────────────
    implementation("androidx.activity:activity-ktx:1.9.3")

    // ── Coil para previsualizar imágenes seleccionadas ─
    implementation("io.coil-kt:coil-compose:2.7.0")

    // ── iText para generar PDFs de alta calidad ────────
    implementation("com.itextpdf:itext7-core:7.2.5") {
        exclude(group = "org.bouncycastle")
    }

    // ── AdMob ─────────────────────────────────────────
    implementation("com.google.android.gms:play-services-ads:23.3.0")

    // ── Timber — logging profesional ──────────────────
    // Reemplaza android.util.Log — no loguea en release
    implementation("com.jakewharton.timber:timber:5.0.1")

    // ── Debug ─────────────────────────────────────────
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}