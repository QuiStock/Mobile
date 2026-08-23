plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.spotless)
    alias(libs.plugins.detekt)
    alias(libs.plugins.google.services)
    alias(libs.plugins.google.firebase.crashlytics)
    jacoco
}

kotlin {
    compilerOptions {
        allWarningsAsErrors.set(true)
    }
}

spotless {
    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**/*.kt", "**/generated/**/*.kt")

        ktlint()

        trimTrailingWhitespace()
        endWithNewline()
    }

    kotlinGradle {
        target("**/*.gradle.kts")
        ktlint()
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

tasks.register<JacocoReport>("jacocoTestReport") {
    description = "Generate JaCoCo coverage report"

    dependsOn("testDebugUnitTest")

    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }

    val excludes =
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
            "**/presentation/activity/**",
        )

    val kotlinClasses =
        fileTree(
            layout.buildDirectory.dir(
                "intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes",
            ),
        ) { exclude(excludes) }

    val javaClasses =
        fileTree(layout.buildDirectory.dir("intermediates/javac/debug/classes")) {
            exclude(excludes)
        }

    classDirectories.setFrom(
        files(kotlinClasses, javaClasses),
    )

    sourceDirectories.setFrom(
        files(
            "src/main/java",
            "src/main/kotlin",
        ),
    )

    executionData.setFrom(
        fileTree(layout.buildDirectory) {
            include(
                "**/*.exec",
                "**/*.ec",
            )
        },
    )
}

tasks.register<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    description = "Require at least 80% test coverage."

    dependsOn("jacocoTestReport")

    violationRules {
        rule {
            limit {
                counter = "INSTRUCTION"
                value = "COVEREDRATIO"
                minimum = "0.80".toBigDecimal()
            }
        }
    }

    val report = tasks.named<JacocoReport>("jacocoTestReport")

    classDirectories.setFrom(report.map { it.classDirectories })
    sourceDirectories.setFrom(report.map { it.sourceDirectories })
    executionData.setFrom(report.map { it.executionData })
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

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }

        debug {
            enableUnitTestCoverage = true
            enableAndroidTestCoverage = true
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)

    testImplementation(libs.mockk)
    testImplementation(libs.androidx.arch.core.testing)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)
}
