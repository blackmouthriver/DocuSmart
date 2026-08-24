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
    id("com.google.firebase.crashlytics")   version "2.9.9" apply false
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
    }
}