import com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension
import com.google.gms.googleservices.GoogleServicesTask
import com.android.build.api.artifact.SingleArtifact
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
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

// Opt in locally; CI verification always remains unsigned.
val localRelease = !ciVerification && (
    providers.gradleProperty("localRelease").orNull
        ?: localProperties.getProperty("localRelease")
        ?: "false"
    ).toBooleanStrict()

val mapsApiKey = if (ciVerification) {
    "CI_VERIFICATION_ONLY"
} else {
    localProperties.getProperty("MAPS_API_KEY")?.takeIf(String::isNotBlank)
        ?: providers.environmentVariable("MAPS_API_KEY").orNull.orEmpty()
}

val escapedMapsApiKey = mapsApiKey
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")

val kakaoNativeAppKey =
    localProperties.getProperty("KAKAO_NATIVE_APP_KEY")?.takeIf(String::isNotBlank)
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
    implementation(libs.kakao.user)
    implementation(libs.google.places)
    implementation(libs.google.play.app.update)
    implementation(libs.google.play.app.update.ktx)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)
    debugImplementation(projects.developerTools)
}

android {
    namespace = "com.joon.ringout"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.joon.ringout"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 261020101
        versionName = "1.2.1"
        buildConfigField("String", "MAPS_API_KEY", "\"$escapedMapsApiKey\"")
        buildConfigField("String", "KAKAO_NATIVE_APP_KEY", "\"$escapedKakaoNativeAppKey\"")
        manifestPlaceholders["kakaoNativeAppKey"] = kakaoNativeAppKey
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
            signingConfig = when {
                ciVerification -> null
                localRelease -> signingConfigs.getByName("debug")
                else -> signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            configure<CrashlyticsExtension> {
                mappingFileUploadEnabled = !ciVerification && !localRelease
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
    description = "Requires real app configuration and, unless localRelease is enabled, release signing credentials."
    missingSettings = if (ciVerification) emptyList() else buildList {
        if (mapsApiKey.isBlank() || mapsApiKey == "CI_VERIFICATION_ONLY") add("MAPS_API_KEY")
        if (kakaoNativeAppKey.isBlank()) add("KAKAO_NATIVE_APP_KEY")
        if (!localRelease) {
            if (releaseKeystorePath.isNullOrBlank()) add("ANDROID_KEYSTORE_PATH")
            if (releaseKeystorePassword.isNullOrBlank()) add("ANDROID_KEYSTORE_PASSWORD")
            if (releaseKeyAlias.isNullOrBlank()) add("ANDROID_KEY_ALIAS")
            if (releaseKeyPassword.isNullOrBlank()) add("ANDROID_KEY_PASSWORD")
        }
    }
}

tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    dependsOn(validateReleaseConfiguration)
}

// Record the actual compiler inputs, including shared KMP sources, before R8 can erase them.
abstract class RecordReleaseSourceInputsTask : DefaultTask() {
    @get:org.gradle.api.tasks.InputFiles
    @get:org.gradle.api.tasks.PathSensitive(org.gradle.api.tasks.PathSensitivity.RELATIVE)
    abstract val compilerInputs: org.gradle.api.file.ConfigurableFileCollection

    @get:org.gradle.api.tasks.OutputFile
    abstract val reportFile: org.gradle.api.file.RegularFileProperty

    @TaskAction
    fun record() {
        val sources = compilerInputs.asFileTree.files
            .filter { it.extension in setOf("kt", "java") }
            .map { it.canonicalPath }
            .sorted()
        check(sources.isNotEmpty()) { "Release compiler source inputs are missing" }
        reportFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText(sources.joinToString("\n", postfix = "\n"))
        }
    }
}

val releaseSourceReport = tasks.register<RecordReleaseSourceInputsTask>("recordReleaseSourceInputs") {
    val appCompile = tasks.named<KotlinCompile>("compileReleaseKotlin")
    val sharedCompile = project(":shared").tasks.named<KotlinCompile>("compileAndroidMain")
    dependsOn(appCompile, sharedCompile)
    // Task.inputs also includes compiler/plugin classpaths. Reading shared's full
    // inputs here can resolve its configurations under androidApp's project lock
    // while serializing the configuration cache. Only record compiler sources.
    compilerInputs.from(
        appCompile.map { it.sources },
        appCompile.map { it.javaSources },
        sharedCompile.map { it.sources },
        sharedCompile.map { it.javaSources },
    )
    reportFile.set(layout.buildDirectory.file("reports/developer-tools/release-sources.txt"))
}

abstract class ReleaseVerificationArguments : org.gradle.process.CommandLineArgumentProvider {
    @get:Input
    abstract val kind: org.gradle.api.provider.Property<String>

    @get:org.gradle.api.tasks.InputFiles
    @get:org.gradle.api.tasks.PathSensitive(org.gradle.api.tasks.PathSensitivity.RELATIVE)
    abstract val artifactFiles: org.gradle.api.file.ConfigurableFileCollection

    override fun asArguments(): Iterable<String> = listOf(
        "ci/verify_developer_tools.py", "--kind", kind.get(),
        "--artifact-path", artifactFiles.singleFile.absolutePath,
    )
}

// Every local/CI release packaging command checks its own final artifact.
listOf("Apk" to "assembleRelease", "Bundle" to "bundleRelease").forEach { (kind, buildTask) ->
    val verify = tasks.register<Exec>("verifyRelease${kind}DeveloperTools") {
        group = "verification"
        description = "Verifies that the release $kind excludes debug developer tools."
        dependsOn(releaseSourceReport)
        workingDir(rootProject.projectDir)
        executable("python3")
    }
    tasks.matching { it.name == buildTask }.configureEach {
        dependsOn(releaseSourceReport)
        finalizedBy(verify)
    }
}

androidComponents.onVariants(androidComponents.selector().withBuildType("release")) { variant ->
    listOf("Apk", "Bundle").forEach { kind ->
        val arguments = objects.newInstance<ReleaseVerificationArguments>().apply {
            this.kind.set(kind.lowercase())
            artifactFiles.from(
                if (kind == "Apk") variant.artifacts.get(SingleArtifact.APK)
                else variant.artifacts.get(SingleArtifact.BUNDLE),
            )
        }
        tasks.named<Exec>("verifyRelease${kind}DeveloperTools") {
            argumentProviders.add(arguments)
        }
    }
}
