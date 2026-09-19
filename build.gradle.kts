// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.spotless) apply false
    alias(libs.plugins.kover) apply false
    alias(libs.plugins.dependency.analysis)
    alias(libs.plugins.sonarqube)
    id("com.google.gms.google-services")    version "4.4.2" apply false
    // Bug real corregido 2026-09-03: 2.9.9 fallaba `assembleRelease` con
    // "groovy/util/XmlSlurper" en uploadCrashlyticsMappingFileRelease --
    // esa versión depende de Groovy en el classpath de build, que Gradle
    // 9.x ya no incluye por defecto. 3.0.6 (plugin v3, sin esa dependencia)
    // resuelve el problema; no usa ninguna de las opciones eliminadas en el
    // salto de versión mayor (mappingFile/strippedNativeLibsDir/
    // symbolGenerator), así que no hace falta ningún otro cambio.
    id("com.google.firebase.crashlytics")   version "3.0.6" apply false
}

// SonarCloud — se ejecuta con `./gradlew sonar` (requiere SONAR_TOKEN en el
// entorno; ver .github/workflows/sonarcloud.yml). La cobertura se toma del
// reporte XML de JaCoCo generado por app/build.gradle.kts.
sonar {
    properties {
        property("sonar.projectKey", "blackmouthriver_DocuSmart")
        property("sonar.organization", "blackmouthriver")
        property("sonar.host.url", "https://sonarcloud.io")
        // Intento revertido 2026-09-09: fijar sonar.sources/sonar.tests acá
        // de forma explícita ("app/src/main/java,build.gradle.kts,..." /
        // "app/src/test/java,app/src/androidTest/java") rompió el análisis
        // por completo -- "File .../MainActivity.kt can't be indexed
        // twice", porque choca con la auto-detección de fuentes/tests que
        // el plugin org.sonarqube ya hace sola para proyectos Android vía
        // AGP (las dos listas terminan solapándose, no reemplazándose).
        //
        // Causa raíz real del "0% de cobertura" encontrada el 2026-09-18
        // (con logs verbose de Sonar, `--info -Dsonar.verbose=true`):
        // rootProject.name = "DocuSmart" hace que este build tenga DOS
        // módulos de Sonar -- "DocuSmart" (raíz) y "app" (subproyecto,
        // donde vive TODO el código Kotlin real). Esta property, puesta
        // acá en la raíz, se evaluaba en AMBOS módulos con la MISMA ruta
        // relativa: el módulo "app" la buscaba mal (relativo a app/, o sea
        // app/app/build/... -- "No coverage report can be found") y el
        // módulo raíz SÍ encontraba el archivo (base dir correcto) pero
        // sus "fuentes analizadas" son las del módulo raíz, vacío de
        // código real -- de ahí "None of the N files... could be matched
        // to the analysed sources" pese a que el reporte era válido.
        // Movida a app/build.gradle.kts (con ruta relativa a `app/`, sin
        // el prefijo) para que se evalúe en el módulo que de verdad tiene
        // el código analizado.
    }
}