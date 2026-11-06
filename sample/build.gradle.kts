import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The blobface sample: shared Compose UI plus the Desktop (jvm), Web (js/wasm)
// and iOS (framework "Shared") entry points. Android's launcher lives in
// :sample-android because AGP 9 does not allow an application in a KMP module.
// Not published.

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    androidLibrary {
        namespace = "io.github.pradyumnadp.blobface.sample"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions { jvmTarget = JvmTarget.JVM_11 }
    }
    jvm()
    listOf(iosArm64(), iosSimulatorArm64()).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }
    js {
        browser { commonWebpackConfig { outputFileName = "sample.js" } }
        binaries.executable()
    }
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser { commonWebpackConfig { outputFileName = "sample.js" } }
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":blobface-compose"))
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
        }
    }
}

compose.desktop {
    application {
        mainClass = "io.github.pradyumnadp.blobface.sample.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "blobface-sample"
            packageVersion = "1.0.0"
        }
    }
}

// Offscreen render of the sample for the README screenshots.
tasks.register<JavaExec>("screenshot") {
    group = "documentation"
    val jvm = kotlin.jvm()
    dependsOn(jvm.compilations["main"].compileTaskProvider)
    classpath = files(jvm.compilations["main"].output.allOutputs, jvm.compilations["main"].runtimeDependencyFiles)
    mainClass.set("io.github.pradyumnadp.blobface.sample.ScreenshotKt")
    args(rootProject.layout.projectDirectory.file("docs/screenshots/desktop.png").asFile.absolutePath)
}

// Pixel comparison of the Compose renderer against the upstream-identical SVG.
tasks.register<JavaExec>("renderCheck") {
    group = "verification"
    val jvm = kotlin.jvm()
    dependsOn(jvm.compilations["main"].compileTaskProvider)
    classpath = files(jvm.compilations["main"].output.allOutputs, jvm.compilations["main"].runtimeDependencyFiles)
    mainClass.set("io.github.pradyumnadp.blobface.sample.RenderCheckKt")
    args(layout.buildDirectory.dir("render-check").get().asFile.absolutePath)
}
