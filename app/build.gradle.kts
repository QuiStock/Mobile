import org.gradle.api.file.FileCollection
import org.gradle.testing.jacoco.tasks.JacocoReportBase
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.spotless)
    alias(libs.plugins.detekt)
    alias(libs.plugins.google.services)
    alias(libs.plugins.google.firebase.crashlytics)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.android.libraries.mapsplatform.secrets.gradle.plugin)
    jacoco
}

val localSettings = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.isFile) file.inputStream().use { load(it) }
}
val employeePassword = providers
    .environmentVariable("QUISTOCK_EMPLOYEE_INITIAL_PASSWORD")
    .orElse(localSettings.getProperty("employee.initial.password", ""))

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

spotless {
    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**/*.kt", "**/generated/**/*.kt")

        ktlint()
            .setEditorConfigPath(rootProject.file(".editorconfig"))
            .editorConfigOverride(
                mapOf(
                    "ktlint_standard_multiline-expression-wrapping" to "disabled",
                    "ktlint_function_signature_body_expression_wrapping" to "default",
                    "max_line_length" to "120",
                ),
            )

        trimTrailingWhitespace()
        endWithNewline()
    }

    kotlinGradle {
        target("**/*.gradle.kts")
        ktlint().setEditorConfigPath(rootProject.file(".editorconfig"))
    }
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
}

tasks.withType<dev.detekt.gradle.Detekt>().configureEach {
    reports {
        html.required.set(true)
        sarif.required.set(true)
    }
}

val coverageExcludes =
    listOf(
        "**/R.class",
        "**/R$*.class",
        "**/BuildConfig.*",
        "**/Manifest*.*",
        "**/*Test*.*",
        $$"**/*$Companion.*",
        "**/*Directions*",
        "**/*Binding*",
        "**/*MapperImpl*",
    )

val coverageClassDirectories =
    files(
        fileTree(
            layout.buildDirectory.dir(
                "intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes",
            ),
        ) { exclude(coverageExcludes) },
        fileTree(
            layout.buildDirectory.dir(
                "intermediates/javac/debug/classes",
            ),
        ) { exclude(coverageExcludes) },
    )

val coverageSourceDirectories =
    files(
        "src/main/java",
        "src/main/kotlin",
    )

val generatedCoverageData =
    fileTree(layout.buildDirectory) {
        include(
            "outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec",
            "outputs/code_coverage/debugAndroidTest/connected/**/*.ec",
        )
    }

val downloadedCoverageData =
    fileTree(
        layout.buildDirectory.dir("coverage-input"),
    ) {
        include(
            "**/*.exec",
            "**/*.ec",
        )
    }

fun JacocoReportBase.configureCoverageInputs(coverageData: FileCollection) {
    classDirectories.setFrom(coverageClassDirectories)
    sourceDirectories.setFrom(coverageSourceDirectories)
    executionData.setFrom(coverageData)
}

fun JacocoReport.configureCoverageReports() {
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
}

fun JacocoCoverageVerification.configureCoverageRules() {
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.80".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = "0.70".toBigDecimal()
            }
        }
    }
}

tasks.register<JacocoReport>("jacocoTestReport") {
    description = "Generate JaCoCo coverage report"

    dependsOn("testDebugUnitTest")
    dependsOn("connectedDebugAndroidTest")

    configureCoverageInputs(generatedCoverageData)
    configureCoverageReports()
}

tasks.register<JacocoReport>("jacocoAggregateReport") {
    description = "Generate combine coverage report from downloaded CI data."

    dependsOn("assembleDebug")

    configureCoverageInputs(downloadedCoverageData)
    configureCoverageReports()
}

tasks.register<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    description = "Require at least 80% test coverage."

    dependsOn("jacocoTestReport")

    configureCoverageInputs(generatedCoverageData)
    configureCoverageRules()
}

tasks.register<JacocoCoverageVerification>("jacocoAggregateCoverageVerification") {
    description = "Validate previously generated unit and instrumentation coverage."

    dependsOn("assembleDebug")

    configureCoverageInputs(downloadedCoverageData)
    configureCoverageRules()

    doFirst {
        val existingCoverageFiles = executionData.files.filter(File::exists)

        require(existingCoverageFiles.isNotEmpty()) {
            "No JaCoCo execution data found in build/coverage-input"
        }

        logger.lifecycle(
            "Validating coverage using:\n{}",
            existingCoverageFiles.joinToString("\n"),
        )
    }
}

android {
    namespace = "com.quistock.quistock"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.quistock.quistock"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
        val quotedPassword = employeePassword.get().replace("\\", "\\\\").replace("\"", "\\\"")
        buildConfigField("String", "EMPLOYEE_INITIAL_PASSWORD", "\"$quotedPassword\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        create("demo") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".demo"
            versionNameSuffix = "-demo"
            matchingFallbacks += "debug"
        }
        release {
            optimization {
                enable = true
            }
        }

        debug {
            enableUnitTestCoverage = true
            enableAndroidTestCoverage = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
        resValues = true
        viewBinding = true
    }

    if (providers.gradleProperty("demoTests").isPresent) {
        testBuildType = "demo"
        sourceSets.getByName("androidTest").setRoot("src/demoAndroidTest")
    }
}

// The offline design preview has no Firebase configuration or telemetry.
tasks.configureEach {
    if (name == "processDemoGoogleServices" || (name.contains("Crashlytics") && name.contains("Demo"))) {
        enabled = false
    }
}

dependencies {
    // Android UI (Views and Material)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.material)
    implementation(libs.play.services.maps)

    // Navigation and lifecycle
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)

    // Local persistence
    implementation(libs.androidx.datastore.preferences)

    // Coroutines and serialization
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.serialization.json)

    // Networking
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.kotlinx.serialization)

    // Firebase (versions aligned by the BoM)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.crashlytics)

    // Dependency injection (versions aligned by the BoM)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)

    // Unit tests
    testImplementation(libs.junit)
    testImplementation(libs.androidx.arch.core.testing)
    testImplementation(libs.mockk)
    testImplementation(libs.kotest.assertion.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.koin.test)

    // Instrumented tests
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.mockk.android)
    androidTestImplementation(libs.navigation.test)
}

secrets {
    defaultPropertiesFileName = "secrets.defaults.properties"
}
