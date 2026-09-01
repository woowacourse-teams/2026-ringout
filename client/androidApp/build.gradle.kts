import com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension
import com.google.gms.googleservices.GoogleServicesTask
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

abstract class ValidateReleaseConfigurationTask : DefaultTask() {
    @get:Input
    var missingSettings: List<String> = emptyList()

    @TaskAction
    fun validate() {
        if (missingSettings.isNotEmpty()) {
            throw GradleException(
                "Release configuration is missing: ${missingSettings.joinToString()}. " +
                    "Use -PciVerification=true only for an unsigned, non-distributable CI build.",
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

// Verification is explicit: missing release secrets never enable this mode implicitly.
val ciVerification = providers.gradleProperty("ciVerification")
    .map { it.toBooleanStrict() }
    .getOrElse(false)

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (!ciVerification && localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use(::load)
    }
}

val mapsApiKey = if (ciVerification) {
    "CI_VERIFICATION_ONLY"
} else {
    localProperties.getProperty("MAPS_API_KEY")?.takeIf(String::isNotBlank)
        ?: providers.environmentVariable("MAPS_API_KEY").orNull.orEmpty()
}

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

val appVersionCode = providers.environmentVariable("APP_VERSION_CODE").orNull?.let { value ->
    value.toIntOrNull()?.takeIf { it in 1..2_100_000_000 }
        ?: throw GradleException("APP_VERSION_CODE must be an integer between 1 and 2100000000.")
} ?: 261010019

val googleServicesJsonPath = providers.environmentVariable("GOOGLE_SERVICES_JSON_PATH").orNull
if (ciVerification || !googleServicesJsonPath.isNullOrBlank()) {
    // Configure after Google's onVariants callback has registered the task and its defaults.
    androidComponents.onVariants { variant ->
        val variantName = variant.name.replaceFirstChar(Char::uppercaseChar)
        tasks.named<GoogleServicesTask>("process${variantName}GoogleServices").configure {
            googleServicesJsonFiles.set(
                listOf(
                    if (ciVerification) rootProject.file("ci/google-services.ci.json")
                    else file(requireNotNull(googleServicesJsonPath)),
                ),
            )
            googleServicesJsonFiles.disallowChanges()
        }
    }
}

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
        versionCode = appVersionCode
        versionName = "1.1.0"
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
            signingConfig = if (ciVerification) null else signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            configure<CrashlyticsExtension> {
                mappingFileUploadEnabled = !ciVerification
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

val validateReleaseConfiguration by tasks.registering(ValidateReleaseConfigurationTask::class) {
    group = "verification"
    description = "Requires real app configuration and signing credentials outside CI verification."
    missingSettings = if (ciVerification) emptyList() else buildList {
        if (mapsApiKey.isBlank() || mapsApiKey == "CI_VERIFICATION_ONLY") add("MAPS_API_KEY")
        if (releaseKeystorePath.isNullOrBlank()) add("ANDROID_KEYSTORE_PATH")
        if (releaseKeystorePassword.isNullOrBlank()) add("ANDROID_KEYSTORE_PASSWORD")
        if (releaseKeyAlias.isNullOrBlank()) add("ANDROID_KEY_ALIAS")
        if (releaseKeyPassword.isNullOrBlank()) add("ANDROID_KEY_PASSWORD")
    }
}

tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    dependsOn(validateReleaseConfiguration)
}
