plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "ru.lifety.android"
    compileSdk = 37
    defaultConfig {
        applicationId = "ru.lifety.android"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }
    buildFeatures { compose = true }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// AGP 9 supplies Kotlin support for this Android application.
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    coreLibraryDesugaring(libs.android.desugar.jdk)
    implementation(project(":shared"))
    implementation(libs.androidx.activity.compose)
}
