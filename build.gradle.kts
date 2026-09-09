// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.detekt) apply false
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
        property(
            "sonar.coverage.jacoco.xmlReportPaths",
            "app/build/reports/jacoco/jacocoTestReport/jacocoTestReport.xml"
        )
        // Bug real corregido 2026-09-09: sin esto, la auto-detección del
        // plugin org.sonarqube encontraba bien los archivos del proyecto
        // para métricas generales (ncloc, cantidad de archivos), pero el
        // importador de cobertura JaCoCo XML no lograba emparejar NINGUNO
        // de los archivos del reporte contra las fuentes analizadas ("None
        // of the 186 files in coverage report could be matched") --
        // reportando 0% de cobertura pese a tener datos reales. La propia
        // documentación de Sonar indica que la importación de cobertura
        // depende de que sonar.sources apunte de forma explícita y precisa
        // al directorio de fuentes, sin depender de la auto-detección.
        // Se incluyen también los build.gradle.kts/settings.gradle.kts --
        // sin listarlos acá, restringir sonar.sources a solo el código de
        // la app los sacaría del análisis (ahí viven los hallazgos ya
        // corregidos kotlin:S6474/text:S8569 sobre dependencias).
        property(
            "sonar.sources",
            "app/src/main/java,build.gradle.kts,app/build.gradle.kts,settings.gradle.kts"
        )
        property("sonar.tests", "app/src/test/java,app/src/androidTest/java")
    }
}