import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    androidLibrary {
        namespace = "com.joon.ringout.developertools"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        // Xcode links one umbrella framework in Debug, never two copies of shared.
        target.binaries.framework(listOf(NativeBuildType.DEBUG)) {
            baseName = "Shared"
            isStatic = true
            export(projects.shared)
        }
    }
    sourceSets {
        commonMain.dependencies {
            api(projects.shared)
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
        }
    }
}
