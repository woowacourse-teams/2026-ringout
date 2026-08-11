import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.googleServices)
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use(::load)
    }
}

val mapsApiKey = localProperties.getProperty("MAPS_API_KEY")
    ?.takeIf(String::isNotBlank)
    ?: providers.environmentVariable("MAPS_API_KEY").orNull.orEmpty()

val escapedMapsApiKey = mapsApiKey
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")

val releaseKeystorePath =
    providers.environmentVariable("ANDROID_KEYSTORE_PATH").orNull

val releaseKeystorePassword =
    providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD").orNull

val releaseKeyAlias =
    providers.environmentVariable("ANDROID_KEY_ALIAS").orNull

val releaseKeyPassword =
    providers.environmentVariable("ANDROID_KEY_PASSWORD").orNull


kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}
dependencies {
    implementation(projects.shared)

    implementation(libs.androidx.activity.compose)
    implementation(libs.google.places)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)
}

android {
    namespace = "com.joon.ringout"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.joon.ringout"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 2026101
        versionName = "1.0.1"
        buildConfigField("String", "MAPS_API_KEY", "\"$escapedMapsApiKey\"")
        manifestPlaceholders["mapsApiKey"] = mapsApiKey
    }
    buildFeatures {
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    signingConfigs {
        create("release") {
            storeFile = releaseKeystorePath?.let { file(it) }
            storePassword = releaseKeystorePassword
            keyAlias = releaseKeyAlias
            keyPassword = releaseKeyPassword
        }
    }

    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
