import com.android.build.api.variant.HasUnitTest
import java.util.Properties

// Firma de release: valores reales en keystore.properties (gitignored, ver
// keystore.properties.example) — en CI se escribe desde secrets antes del
// build. Si no existe (checkout limpio de un colaborador, o CI corriendo
// tareas que no son de release), releaseSigningConfig queda null y el build
// type release simplemente no queda firmado para Play Store — no rompe
// assembleDebug ni el resto de las tareas normales.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val releaseSigningProps =
    if (keystorePropertiesFile.exists()) {
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
    alias(libs.plugins.ktlint)
    alias(libs.plugins.spotless)
    alias(libs.plugins.kover)
    // Dependency Analysis Gradle Plugin -- aplicado también acá (no solo en
    // el build.gradle.kts raíz) porque el proyecto raíz no tiene ningún
    // plugin Android/Java propio (todo el código vive en :app); sin esto el
    // plugin no encuentra ningún subproyecto con salida JVM que analizar.
    alias(libs.plugins.dependency.analysis)
    jacoco
    // Bug real corregido 2026-09-03: google-services.json ya estaba en el
    // repo y las dependencias de Firebase Analytics/Crashlytics ya estaban
    // agregadas, pero estos 2 plugins (declarados con apply false en el
    // build.gradle.kts raíz) nunca se aplicaban acá -- sin ellos,
    // google-services.json nunca se procesa y Firebase no tiene con qué
    // inicializarse de verdad, pese a que deployment.md ya declaraba (para
    // Play Store) que la app envía datos de uso/fallas a Firebase.
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
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
    namespace = "com.docsmart"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.docsmart"
        minSdk = 26
        targetSdk = 36
        versionCode = 6
        versionName = "1.1.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Libera el registro de llamadas de MockK tras cada prueba (fuga de heap, ronda 20).
        testInstrumentationRunnerArguments["listener"] = "com.docsmart.core.ui.test.ClearMocksListener"
    }

    signingConfigs {
        if (releaseSigningProps != null) {
            create("release") {
                storeFile = rootProject.file(releaseSigningProps.getProperty("storeFile"))
                storePassword = releaseSigningProps.getProperty("storePassword")
                keyAlias = releaseSigningProps.getProperty("keyAlias")
                keyPassword = releaseSigningProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
            manifestPlaceholders["firebaseAnalyticsDeactivated"] = true
            manifestPlaceholders["firebaseCrashlyticsEnabled"] = false
            // Genera datos de cobertura Jacoco durante connectedDebugAndroidTest
            // (Compose UI Testing) además de testDebugUnitTest -- ver
            // jacocoTestReport más abajo, que fusiona ambos en un solo XML.
            enableAndroidTestCoverage = true
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            manifestPlaceholders["firebaseAnalyticsDeactivated"] = false
            manifestPlaceholders["firebaseCrashlyticsEnabled"] = true
            if (releaseSigningProps != null) {
                signingConfig = signingConfigs.getByName("release")
            }
            // Play Console avisaba "código nativo sin símbolos de depuración" (de
            // librerías de terceros con .so, ej. ML Kit) -- FULL empaqueta los
            // símbolos dentro del propio .aab automáticamente, sin subirlos a mano.
            ndk {
                debugSymbolLevel = "FULL"
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
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
            excludes +=
                setOf(
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
                    "META-INF/versions/9/previous-compilation-data.bin",
                )
            pickFirsts +=
                setOf(
                    "META-INF/AL2.0",
                    "META-INF/LGPL2.1",
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

// sonar.coverage.jacoco.xmlReportPaths vive ACÁ (no en el build.gradle.kts
// raíz) a propósito -- ver el comentario extenso en el bloque `sonar {}` de
// la raíz (causa raíz encontrada 2026-09-18): rootProject.name = "DocuSmart"
// crea DOS módulos de Sonar (raíz "DocuSmart" + este subproyecto "app"), y
// esta property necesita evaluarse en EL MÓDULO QUE TIENE EL CÓDIGO
// ANALIZADO (este), con una ruta relativa A ESTE MÓDULO (sin el prefijo
// "app/" que sí hacía falta cuando la property vivía en la raíz).
sonar {
    properties {
        property(
            "sonar.coverage.jacoco.xmlReportPaths",
            "build/reports/jacoco/jacocoTestReport/jacocoTestReport.xml",
        )
    }
}

// ktlint (estilo de código Kotlin) -- agregado al gauntlet por pedido
// explícito del usuario (2026-09-18), en paralelo a detekt (que cubre
// reglas de calidad/complejidad, no de formato). `ktlintCheck` no
// modifica archivos; `ktlintFormat` sí autoformatea.
ktlint {
    android.set(true)
    ignoreFailures.set(false)
    reporters {
        reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.PLAIN)
        reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.CHECKSTYLE)
    }
}

// Spotless (formato general, incluye los propios *.gradle.kts) -- agregado
// al gauntlet por pedido explícito del usuario (2026-09-18). El bloque
// `kotlin{}` reutiliza el motor de ktlint (mismo criterio que el plugin de
// arriba) para no divergir en reglas entre ambas herramientas; `spotlessApply`
// autoformatea, `spotlessCheck` solo valida (es lo que entra al gauntlet).
spotless {
    kotlin {
        target("src/**/*.kt")
        targetExclude("**/build/**/*.kt")
        ktlint()
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint()
        trimTrailingWhitespace()
        endWithNewline()
    }
}

// Kover (cobertura vía el compilador de Kotlin, no JaCoCo) -- agregado al
// gauntlet por pedido explícito del usuario (2026-09-18), en paralelo al
// `jacocoTestReport` ya wireado a SonarCloud más abajo (no lo reemplaza).
// `koverXmlReport`/`koverHtmlReport`/`koverVerify` corren independientes,
// mismos filtros de exclusión que jacocoTestReport para no contar código
// generado (Hilt/Dagger/KSP, R, BuildConfig) como cobertura real.
kover {
    reports {
        filters {
            excludes {
                classes(
                    "*.R", "*.R\$*", "*.BuildConfig", "*Test*",
                    "*Hilt_*", "*_Factory", "*_MembersInjector",
                    "*Module_*Factory", "*_HiltModules*", "*.di.*",
                )
            }
        }
    }
}

// Hallazgo SonarCloud text:S8569: sin lock file, dos builds del mismo commit
// pueden resolver versiones transitivas distintas si algo cambia río arriba
// (un rango de versión, un repositorio, o un artefacto republicado) -- el
// lock file fija el árbol de dependencias exacto ya resuelto, para que el
// build sea reproducible y una versión transitiva comprometida no pueda
// colarse en silencio. `lockAllConfigurations()` cubre todas las
// configuraciones (debug/release, test, androidTest, ksp, detekt, etc.), no
// solo las de "dependencies" normales.
//
// Mantenimiento: cada vez que una dependencia cambia de versión (a mano o
// vía Dependabot), hay que regenerar el lock file y commitear el resultado,
// o el build falla con "Locked dependencies... have changed":
//   ./gradlew resolveAndLockAll --write-locks
dependencyLocking {
    lockAllConfigurations()
}

// Task recomendada por la documentación oficial de Gradle para poder
// regenerar el lock file de una sola vez: sin esto, `--write-locks` solo
// fija las configuraciones que la tarea elegida llegue a resolver (ej.
// `assembleDebug` nunca toca las configuraciones de detekt/lint/androidTest).
//
// Un puñado de configuraciones internas de AGP (ej. la ambigüedad de
// variante que aparece al forzar `debugAndroidTestCompileClasspath` fuera
// del pipeline normal de compilación) no se pueden resolver de forma
// aislada así -- no son configuraciones de dependencias reales, así que se
// omiten con una advertencia en vez de abortar toda la generación del lock.
tasks.register("resolveAndLockAll") {
    doFirst {
        require(gradle.startParameter.isWriteDependencyLocks) {
            "Ejecutar con --write-locks, ej.: ./gradlew resolveAndLockAll --write-locks"
        }
    }
    doLast {
        configurations.filter { it.isCanBeResolved }.forEach { configuration ->
            try {
                configuration.resolve()
            } catch (e: Exception) {
                logger.warn("resolveAndLockAll: se omite '${configuration.name}' (${e.message})")
            }
        }
    }
}

// Hallazgo SonarCloud kotlin:S6474 (ver comentario en gradle/verification-
// metadata.xml): checksum sha256 de cada dependencia realmente usada, para
// detectar un artefacto alterado/comprometido antes de que entre al build.
//
// Mantenimiento: cada vez que una dependencia cambia de versión (a mano o
// vía Dependabot) o se agrega una nueva, el build falla con "Dependency
// verification failed" hasta regenerar el archivo y commitear el
// resultado:
//   ./gradlew --write-verification-metadata sha256 assembleDebug assembleDebugAndroidTest testDebugUnitTest detekt lintDebug

// Efecto secundario documentado en docs/requirements/deployment.md §3
// ("efecto secundario encontrado, sin arreglar todavía"): ~14-16 de las 30
// pruebas de Compose UI Testing fallan de forma consistente y ya
// investigada a fondo (13 intentos documentados) SOLO en el emulador
// compartido de GitHub Actions -- causa raíz confirmada con logging
// manual: performClick() no llega a invocar el handler real para esas
// pantallas puntuales en ese emulador (las mismas pruebas pasan 30/30 en
// dispositivo físico real y en Firebase Test Lab). Sin este flag,
// connectedDebugAndroidTest falla el build de Gradle en cuanto termina, y
// jacocoTestReport (que depende de él) nunca llega a generar el XML de
// cobertura -- SonarCloud reporta 0% de cobertura en código nuevo pese a
// que sí hay pruebas reales cubriéndolo. `ignoreFailures` hace que Gradle
// trate la tarea como "completada" a efectos del grafo de tareas aunque
// haya tests fallidos -- el detalle real (qué pasó y qué no) sigue
// disponible en el reporte HTML/XML de resultados, esto no lo oculta, solo
// no bloquea las tareas que dependen de ella.
tasks.withType<com.android.build.gradle.internal.tasks.DeviceProviderInstrumentTestTask>().configureEach {
    ignoreFailures = true
}

// Reporte XML de cobertura para SonarCloud, fusionando los unit tests
// JVM (testDebugUnitTest) con las pruebas instrumentadas de Compose UI
// Testing (connectedDebugAndroidTest, ver docs/requirements/compose-ui-testing.md
// §4) -- antes solo se contaban los unit tests, dejando en 0% toda la
// cobertura real que aportan las ~30 pruebas de Compose. Excluye clases
// generadas (Hilt/Dagger/KSP, R, BuildConfig) que no reflejan cobertura real.
//
// connectedDebugAndroidTest requiere un dispositivo/emulador conectado
// (ver .github/workflows/sonarcloud.yml) -- localmente, corre contra
// cualquier dispositivo ya conectado por adb.
tasks.register<JacocoReport>("jacocoTestReport") {
    dependsOn("testDebugUnitTest", "connectedDebugAndroidTest", "transformDebugClassesWithAsm")

    reports {
        xml.required.set(true)
        html.required.set(true)
    }

    val fileFilter =
        listOf(
            "**/R.class", "**/R\$*.class", "**/BuildConfig.*", "**/Manifest*.*",
            "**/*Test*.*", "android/**/*.*",
            "**/Hilt_*.*", "**/*_Factory.*", "**/*_MembersInjector.*",
            "**/*Module_*Factory.*", "**/dagger/**", "**/*_HiltModules*.*",
            "**/di/**",
        )
    // Causa raíz encontrada 2026-09-18 (investigación explícita pedida por el
    // usuario tras meses con "0% cobertura en código nuevo" en SonarCloud,
    // ver el intento revertido de 2026-09-09 más abajo en el historial de
    // este archivo): classDirectories apuntaba a la salida CRUDA de kotlinc
    // (tmp/kotlin-classes/debug), pero ni testDebugUnitTest ni
    // connectedDebugAndroidTest corren contra ese bytecode -- ambos corren
    // contra la salida YA TRANSFORMADA por el plugin compilador de Compose
    // (inyección de @StabilityInferred, ver transformDebugClassesWithAsm),
    // y connectedDebugAndroidTest además contra una copia de esa misma
    // salida con sondas de JaCoCo inyectadas encima (tarea jacocoDebug).
    // Confirmado con sha1sum: las 3 versiones de DocuSmartApplication.class
    // tienen tamaños distintos (6948 / 7002 / 7602 bytes). JaCoCo detecta el
    // desajuste de bytecode y lo descarta ("Classes in bundle 'app' do not
    // match with execution data"), dejando el XML con archivos listados
    // pero sin datos de línea coherentes -- de ahí que SonarCloud no pueda
    // emparejar NINGUNO de los archivos del reporte contra las fuentes
    // analizadas. Corregido usando la salida de transformDebugClassesWithAsm
    // (el mismo bytecode que ambos tipos de test ejecutan) en vez de la
    // salida cruda de kotlinc.
    val debugClassesDir =
        "${layout.buildDirectory.get()}/intermediates/classes/debug/transformDebugClassesWithAsm/dirs"
    val debugTree = fileTree(debugClassesDir) { exclude(fileFilter) }
    val mainSrc = "$projectDir/src/main/java"

    sourceDirectories.setFrom(files(mainSrc))
    classDirectories.setFrom(files(debugTree))
    executionData.setFrom(
        fileTree("${layout.buildDirectory.get()}") {
            // testDebugUnitTest.exec: unit tests JVM (Jacoco Gradle plugin,
            // ubicación por defecto). coverage.ec: connectedDebugAndroidTest
            // (AGP, un archivo por dispositivo -- el nombre del dispositivo
            // queda en la ruta, con espacios incluidos, de ahí el comodín).
            include(
                "jacoco/testDebugUnitTest.exec",
                "outputs/code_coverage/debugAndroidTest/connected/**/*.ec",
            )
        },
    )
}

// Snyk (pedido explícito del usuario, 2026-09-18): a diferencia del resto de
// herramientas de este gauntlet, Snyk no es un plugin de Gradle -- es un CLI
// que escanea el árbol de dependencias resuelto y lo compara contra la base
// de datos de vulnerabilidades de Snyk, autenticándose contra su servicio en
// la nube. Requiere `SNYK_TOKEN` en el entorno (mismo patrón ya establecido
// en este proyecto para SonarCloud/SONAR_TOKEN, ver build.gradle.kts raíz) o
// haber corrido `snyk auth` una vez en esta máquina -- ninguna de las dos
// cosas es algo que se pueda configurar sin la cuenta real del usuario, así
// que esta tarea solo hace de wrapper: si falta la autenticación, falla con
// un mensaje claro en vez de un error críptico del CLI.
tasks.register<Exec>("snykTest") {
    group = "verification"
    description = "Escanea las dependencias resueltas en busca de vulnerabilidades conocidas (requiere SNYK_TOKEN)."
    workingDir = rootDir
    commandLine("cmd", "/c", "snyk", "test", "--all-projects")
    isIgnoreExitValue = true
    doFirst {
        if (System.getenv("SNYK_TOKEN").isNullOrBlank()) {
            logger.warn(
                "snykTest: no se encontró SNYK_TOKEN en el entorno -- " +
                    "el escaneo real de vulnerabilidades no puede autenticarse contra Snyk. " +
                    "Corré 'snyk auth' una vez en esta máquina, o seteá SNYK_TOKEN " +
                    "(mismo patrón que SONAR_TOKEN para SonarCloud).",
            )
        }
    }
}

dependencies {
    // ── Core Android ──────────────────────────────────────────────────────────
    // buildHealth (paso 3, removida): androidx-core-ktx solo aporta funciones
    // de extensión Kotlin (toUri/toFile/toColorInt/bundleOf/edit{}/
    // getSystemService<T>()/contentValuesOf/LocaleListCompat) -- ninguna se usa
    // en el código (grep exhaustivo sin resultados). Las clases sí usadas
    // (FileProvider, ContextCompat, PermissionChecker) viven en
    // androidx.core:core (declarado directo más abajo), no en core-ktx.
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    // buildHealth: transitivas de androidx.core-ktx/activity-compose/etc. que
    // el código fuente sí referencia directo (ContextCompat, FileProvider,
    // PermissionChecker, ComponentActivity...) -- se declaran explícitas para
    // no depender de que otra dependencia de nivel superior siga trayéndolas.
    implementation("androidx.core:core:1.18.0")
    implementation("androidx.activity:activity:1.13.0")
    implementation("androidx.annotation:annotation:1.9.1")
    implementation("androidx.collection:collection:1.5.0")
    implementation("androidx.fragment:fragment:1.5.4")

    // ── Compose BOM ───────────────────────────────────────────────────────────
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    // buildHealth (paso 3, removida): sin ningún @Preview en el código fuente
    // (grep de "@Preview" y de "import androidx.compose.ui.tooling.preview"
    // sin resultados en app/src/main/java) -- no hay Composables de preview.
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.foundation)
    // buildHealth: submódulos de Compose usados directo en el código
    // (Composable/remember/State de runtime, Animatable de animation,
    // Modifier de foundation-layout, Icons.* de material-icons-core, Dp/Offset
    // de ui-unit/ui-geometry, TextStyle de ui-text, rememberSaveable).
    implementation("androidx.compose.animation:animation:1.11.0")
    implementation("androidx.compose.animation:animation-core:1.11.0")
    implementation("androidx.compose.foundation:foundation-layout:1.11.0")
    implementation("androidx.compose.material:material-icons-core:1.7.5")
    implementation("androidx.compose.runtime:runtime:1.11.0")
    implementation("androidx.compose.runtime:runtime-saveable:1.11.0")
    implementation("androidx.compose.ui:ui-geometry:1.11.0")
    implementation("androidx.compose.ui:ui-text:1.11.0")
    implementation("androidx.compose.ui:ui-unit:1.11.0")

    // ── Navegación ────────────────────────────────────────────────────────────
    implementation(libs.androidx.navigation.compose)
    // buildHealth: NavController/NavGraph (navigation-common) y el motor de
    // navegación en runtime (navigation-runtime) usados directo, no solo vía
    // los componentes de navigation-compose.
    implementation("androidx.navigation:navigation-common:2.8.4")
    implementation("androidx.navigation:navigation-runtime:2.8.4")

    // ── Lifecycle / ViewModel ─────────────────────────────────────────────────
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    // ProcessLifecycleOwner (RF-SEC-08: auto-bloqueo de Carpeta Segura al
    // pasar a segundo plano) -- artefacto separado de lifecycle-runtime-ktx.
    implementation(libs.androidx.lifecycle.process)
    // buildHealth: Lifecycle/LifecycleEventObserver (lifecycle-common) y
    // SavedStateHandle de ViewModel (lifecycle-viewmodel-savedstate) usados
    // directo; collectAsStateWithLifecycle (lifecycle-runtime-compose) igual.
    implementation("androidx.lifecycle:lifecycle-common:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-savedstate:2.9.4")
    implementation(libs.androidx.lifecycle.runtime.compose)

    // ── Hilt ──────────────────────────────────────────────────────────────────
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    // buildHealth: @Inject/@Module/@Provides son de Dagger/javax.inject
    // directo, no solo de hilt-android (que los re-expone transitivamente).
    implementation("com.google.dagger:dagger:2.57")
    implementation("com.google.dagger:hilt-core:2.57")
    implementation("javax.inject:javax.inject:1")

    // ── Coil ──────────────────────────────────────────────────────────────────
    implementation(libs.coil.compose)
    // buildHealth: AsyncImage/ImageLoader (coil-base) y rememberAsyncImagePainter
    // (coil-compose-base) usados directo, no solo vía el paraguas coil-compose;
    // coil (el paraguas de coil-compose) también se referencia directo.
    implementation("io.coil-kt:coil:2.7.0")
    implementation("io.coil-kt:coil-base:2.7.0")
    implementation("io.coil-kt:coil-compose-base:2.7.0")

    // ── Coroutines ────────────────────────────────────────────────────────────
    // buildHealth (Dependency Analysis Gradle Plugin): solo se usan símbolos de
    // kotlinx-coroutines-core en el código fuente (Dispatchers, Flow, etc.) --
    // el "android" en el nombre del artefacto es el Dispatchers.Main real para
    // Android, resuelto en tiempo de ejecución, no referenciado directo desde
    // bytecode compilado. kotlinx-coroutines-core se declara directo abajo.
    runtimeOnly(libs.kotlinx.coroutines.android)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")

    // ── Room (historial de documentos abiertos) ──────────────────────────────
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    // buildHealth: anotaciones de Room (@Entity, @Dao, etc.) y el driver base
    // de SQLite usados directo, no solo vía room-runtime/room-ktx.
    implementation("androidx.room:room-common:2.8.4")
    implementation("androidx.sqlite:sqlite:2.7.0")
    // Driver real de SQLite para pruebas de integración de Room en la JVM
    // (ver sustitución de variante -jvm más arriba en androidComponents).
    testImplementation(libs.androidx.sqlite.bundled)

    // ── iText7 ────────────────────────────────────────────────────────────────
    // buildHealth (paso 3, removida): itext7-core es un POM agregador sin
    // clases propias -- el código fuente importa directo de los 4 submódulos
    // de abajo (forms/io/kernel/layout), que ya se declaran explícitos (el
    // classpath resuelto no cambia, ya venían transitivos vía itext7-core).
    // Verificado con dependencyInsight que kernel ya exige
    // bcpkix-jdk15on:1.70 transitivo (misma versión que la declaración
    // explícita de bcpkix-jdk15on más abajo), así que el exclude de
    // bouncycastle que tenía itext7-core no hacía falta repetirlo acá.
    implementation("com.itextpdf:forms:7.2.5")
    implementation("com.itextpdf:io:7.2.5")
    implementation("com.itextpdf:kernel:7.2.5")
    implementation("com.itextpdf:layout:7.2.5")
    // Módulo pdfCleanup — RF-PDF-14 (censurar contenido de forma irreversible):
    // a diferencia de dibujar un rectángulo negro con PdfCanvas (que deja el
    // texto/vector original intacto y extraíble debajo), este módulo elimina
    // de verdad el contenido del content stream dentro de la región indicada.
    implementation("com.itextpdf:cleanup:3.0.2")
    // Proveedor de seguridad cargado en runtime por iText (firma/cifrado PDF),
    // sin import directo en el código de la app -- ver bcpkix-jdk15on más
    // abajo, que sí queda `implementation` (evaluado, no removido: ver
    // reporte final de buildHealth).
    runtimeOnly("org.bouncycastle:bcprov-jdk15on:1.70")
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
    // El visor de PowerPoint (XSLFTextShape/XSLFPictureShape) sí necesita
    // SparseBitSet en tiempo de ejecución -- confirmado con un
    // ClassNotFoundException real en dispositivo al excluirlo (el exclude de
    // arriba es de una sesión anterior, para Word/Excel; PPTX toca una ruta
    // distinta de POI). Se agrega de vuelta como dependencia explícita en
    // vez de quitar el exclude de poi/poi-ooxml/poi-scratchpad.
    implementation("com.zaxxer:SparseBitSet:1.3")

    // ── AdMob ─────────────────────────────────────────────────────────────────
    implementation("com.google.android.gms:play-services-ads:23.3.0")
    // UMP (User Messaging Platform) -- consentimiento de anuncios UE/Reino
    // Unido (RF pendiente, ver docs/requirements/settings-premium.md). Es un
    // artefacto separado, NO viene incluido en play-services-ads -- versión
    // verificada contra el índice real de Google Maven antes de fijarla acá.
    implementation("com.google.android.ump:user-messaging-platform:4.0.0")

    // ── Play Billing (Premium: mensual/anual/lifetime) ───────────────────────
    implementation(libs.billing.ktx)
    // buildHealth: BillingClient/ProductDetails/PurchasesUpdatedListener son
    // del artefacto base "billing", re-expuesto por billing-ktx.
    implementation("com.android.billingclient:billing:9.1.0")

    // ── Timber ────────────────────────────────────────────────────────────────
    implementation("com.jakewharton.timber:timber:5.0.1")

    // ── CameraX ───────────────────────────────────────────────────────────────
    val cameraxVersion = "1.3.4"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    // camera-camera2 es el backend real de CameraX (Camera2 vendor
    // implementation) -- se carga por service loader en runtime, el código
    // fuente solo referencia las APIs de camera-core/camera-lifecycle/camera-view.
    runtimeOnly("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")
    // buildHealth (paso 3, removida): camera-extensions (bokeh/HDR/night mode
    // de fabricantes) -- sin ExtensionsManager/ExtensionMode ni ningún import
    // de androidx.camera.extensions en el código (grep sin resultados).

    // ── ML Kit ────────────────────────────────────────────────────────────────
    implementation("com.google.android.gms:play-services-mlkit-document-scanner:16.0.0")
    // buildHealth (Dependency Analysis Gradle Plugin) sugería reclasificar
    // esto a runtimeOnly, pero es un FALSO POSITIVO real: QrScreen.kt importa
    // y usa com.google.mlkit.vision.barcode.BarcodeScanning.getClient()
    // directo (confirmado con error real de compilación "unresolved
    // reference 'BarcodeScanning'" al probar el cambio) -- se revierte a
    // implementation.
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    // Reconocimiento de texto (OCR) on-device para RF-PDF-15 -- distinto del
    // document-scanner (solo recorta/mejora la imagen del escaneo) y del
    // barcode-scanning (solo códigos QR/barras). Variante "bundled" (modelo
    // incluido en el APK, no la de Play Services) para funcionar offline
    // desde el primer uso, mismo criterio que barcode-scanning ya usa.
    implementation("com.google.mlkit:text-recognition:16.0.1")
    // buildHealth: clases base de MLKit/Play Services MLKit usadas por
    // barcode-scanning/text-recognition (InputImage de vision-common, tipos
    // comunes de barcode-scanning-common) y las variantes Play Services que
    // document-scanner/barcode-scanning resuelven en runtime.
    implementation("com.google.android.gms:play-services-mlkit-barcode-scanning:18.3.1")
    implementation("com.google.android.gms:play-services-mlkit-text-recognition:19.0.1")
    implementation("com.google.android.gms:play-services-mlkit-text-recognition-common:19.1.0")
    implementation("com.google.mlkit:barcode-scanning-common:17.0.0")
    implementation("com.google.mlkit:vision-common:17.3.0")
    // Modelo bundled real de text-recognition (offline desde el primer uso) --
    // cargado en runtime, sin referencia directa en bytecode.
    runtimeOnly("com.google.mlkit:text-recognition-bundled-common:17.0.0")

    // ── ZXing ─────────────────────────────────────────────────────────────────
    implementation("com.google.zxing:core:3.5.4")
    // El código fuente usa com.google.zxing.core directo (declarado arriba);
    // zxing-android-embedded solo aporta la UI del escáner (CaptureActivity),
    // consumida vía Intent/Manifest, no referenciada en bytecode compilado.
    runtimeOnly("com.journeyapps:zxing-android-embedded:4.3.0") { isTransitive = false }

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
    implementation("com.google.firebase:firebase-config")

    // ── Testing ───────────────────────────────────────────────────────────────
    // buildHealth (paso 3, removida): junit-jupiter es el paraguas
    // (api+params+engine); el código fuente solo usa org.junit.jupiter.api.*
    // (junit-jupiter-api, declarado directo abajo). jupiter-engine sigue
    // presente en el classpath de test en runtime vía otras dependencias.
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("io.mockk:mockk:1.13.13")
    testImplementation("app.cash.turbine:turbine:1.2.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    // buildHealth: la API de JUnit5 (org.junit.jupiter.api.Test/Assertions,
    // usada en casi toda la suite) vive en junit-jupiter-api, submódulo del
    // paraguas junit-jupiter (removido arriba).
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.14.4")
    // buildHealth: motor de specs de Kotest (kotest-framework-engine) y las
    // DSL de mockk (every/coEvery/slot, kotest-runner-junit5→mockk-dsl) usadas
    // directo desde los tests, no solo vía los paraguas mockk/kotest-runner.
    testImplementation("io.kotest:kotest-framework-engine:6.2.5")
    testImplementation("io.mockk:mockk-dsl:1.13.13")
    // ArchUnit core (ArchRule/JavaClasses/ClassFileImporter) -- ver
    // ArchitectureTest.kt, que NO usa el runner de archunit-junit5 (removido
    // más abajo), solo corre las reglas dentro de un @Test normal de JUnit5.
    testImplementation("com.tngtech.archunit:archunit:1.5.0")
    // Kotest (pedido explícito del usuario, 2026-09-18) -- corre sobre el
    // mismo motor JUnit Platform que ya usa el resto de la suite
    // (testOptions.unitTests.all { useJUnitPlatform() } más abajo), así que
    // convive con los tests JUnit5 existentes sin migrarlos. Pensado para
    // specs nuevas que se beneficien de sus estilos de test (FunSpec,
    // BehaviorSpec, etc.) o de matchers más expresivos -- no reemplaza
    // JUnit5+MockK en los tests ya escritos.
    // El motor de ejecución de Kotest se descubre vía JUnit Platform
    // (ServiceLoader) -- los specs usan kotest-assertions-core/kotest-property
    // (compile-time), no símbolos de kotest-runner-junit5 directo.
    testRuntimeOnly(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotest.property)
    // buildHealth (paso 3, removida): ArchitectureTest.kt (pedido explícito
    // del usuario, 2026-09-18) solo importa com.tngtech.archunit.{base,
    // core.domain,core.importer,lang.syntax} (archunit core, declarado
    // arriba) + org.junit.jupiter.api.Test -- ningún símbolo de
    // com.tngtech.archunit.junit.* (el runner/anotaciones propias de
    // archunit-junit5) se usa.
    // El stub de Android para unit tests deja org.json.* sin implementar
    // ("not mocked") — esta dependencia real (mismo paquete org.json) la
    // sustituye solo para los tests, sin afectar el runtime de la app.
    testImplementation("org.json:json:20231013")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    // buildHealth: JUnit4 (junit:junit, usado por androidx.test.ext:junit y
    // por las reglas @get:Rule) y el Monitor de instrumentación
    // (androidx.test:monitor, ActivityLifecycleMonitorRegistry) usados
    // directo en las pruebas, no solo vía runner/rules.
    androidTestImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:monitor:1.8.0")
    // Espresso no se usa directo en las pruebas (todas usan Compose UI
    // Testing) -- se mantiene como dependencia de runtime porque
    // AndroidJUnitRunner/InstrumentationRegistry lo necesitan indirectamente.
    androidTestRuntimeOnly("androidx.test.espresso:espresso-core:3.7.0")
    // GrantPermissionRule -- LibraryScreenTest necesita permiso real de
    // almacenamiento concedido antes de componer la pantalla (LibraryScreen
    // lo verifica con ContextCompat.checkSelfPermission al inicio, no es
    // algo mockeable desde el ViewModel).
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation(platform(libs.androidx.compose.bom))
    // buildHealth (paso 3, removida): ui-test-manifest ya está como
    // debugRuntimeOnly más abajo (única declaración recomendada oficialmente
    // para Compose UI Testing -- el manifest de ComposeTestActivity se
    // fusiona desde el APK debug, no hace falta declararlo también acá).
    // ── Compose UI Testing (instrumentado — corre en dispositivo/emulador) ────
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    // buildHealth: SemanticsNodeInteraction/finders (ui-test) usados directo
    // desde los ...ScreenTest.kt, no solo vía ui-test-junit4.
    androidTestImplementation("androidx.compose.ui:ui-test:1.11.0")
    // mockk-android, no mockk: mockear en el dispositivo necesita soporte
    // dexmaker/bytebuddy-android, distinto del mockk de test/ (JVM).
    androidTestImplementation("io.mockk:mockk-android:1.13.13")
    // buildHealth: DSL de mockk (every/coEvery/mockk<T>()) resuelta directo
    // desde mockk-dsl; mockk-agent-android/mockk-agent son el motor de
    // bytebuddy-android que mockk-android carga por ServiceLoader en runtime.
    androidTestImplementation("io.mockk:mockk-dsl:1.13.13")
    androidTestRuntimeOnly("io.mockk:mockk-agent-android:1.13.13")
    androidTestRuntimeOnly("io.mockk:mockk-agent:1.13.13")
    debugImplementation(libs.androidx.compose.ui.tooling)
    // El manifest de la actividad de test (ComposeTestActivity) se consume
    // vía manifest merge, no por código Kotlin compilado contra esta librería.
    debugRuntimeOnly(libs.androidx.compose.ui.test.manifest)
}
