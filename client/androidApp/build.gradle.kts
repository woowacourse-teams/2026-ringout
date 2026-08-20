import com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

abstract class ValidateMapsApiKeyTask : DefaultTask() {
    @get:Input
    var isConfigured = false

    @TaskAction
    fun validate() {
        if (!isConfigured) {
            throw GradleException(
                "MAPS_API_KEY must be set in local.properties or the environment for release builds.",
            )
        }
    }
}

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.googleServices)
    alias(libs.plugins.firebaseCrashlytics)
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

val kakaoNativeAppKey = localProperties.getProperty("KAKAO_NATIVE_APP_KEY")
    ?.takeIf(String::isNotBlank)
    ?: providers.environmentVariable("KAKAO_NATIVE_APP_KEY").orNull.orEmpty()

val escapedKakaoNativeAppKey = kakaoNativeAppKey
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
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.google.places)
    implementation(libs.google.play.app.update)
    implementation(libs.google.play.app.update.ktx)
    implementation(libs.kakao.user)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)

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
        versionCode = 261010001
        versionName = "1.1.0"
        buildConfigField("String", "MAPS_API_KEY", "\"$escapedMapsApiKey\"")
        buildConfigField("String", "KAKAO_NATIVE_APP_KEY", "\"$escapedKakaoNativeAppKey\"")
        manifestPlaceholders["mapsApiKey"] = mapsApiKey
        manifestPlaceholders["kakaoNativeAppKey"] = kakaoNativeAppKey
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
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            configure<CrashlyticsExtension> {
                mappingFileUploadEnabled = true
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

val validateReleaseMapsApiKey by tasks.registering(ValidateMapsApiKeyTask::class) {
    group = "verification"
    description = "Fails release builds when MAPS_API_KEY is not configured."
    isConfigured = mapsApiKey.isNotBlank()
}

tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    dependsOn(validateReleaseMapsApiKey)
}
