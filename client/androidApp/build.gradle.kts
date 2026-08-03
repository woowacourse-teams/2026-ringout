import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use(::load)
    }
}

val kakaoNativeAppKey = localProperties.getProperty("KAKAO_NATIVE_APP_KEY")
    ?.takeIf(String::isNotBlank)
    ?: providers.environmentVariable("KAKAO_NATIVE_APP_KEY").orNull.orEmpty()
val kakaoRestApiKey = localProperties.getProperty("KAKAO_REST_API_KEY")
    ?.takeIf(String::isNotBlank)
    ?: providers.environmentVariable("KAKAO_REST_API_KEY").orNull.orEmpty()

val escapedKakaoNativeAppKey = kakaoNativeAppKey
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}
dependencies {
    implementation(projects.shared)

    implementation(libs.androidx.activity.compose)
    implementation(libs.kakao.maps)

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
        versionCode = 2026006
        versionName = "0.0.6"
        buildConfigField("String", "KAKAO_NATIVE_APP_KEY", "\"$escapedKakaoNativeAppKey\"")
        manifestPlaceholders["kakaoRestApiKey"] = kakaoRestApiKey
    }
    buildFeatures {
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
