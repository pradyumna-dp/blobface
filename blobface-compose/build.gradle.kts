import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.mavenPublish)
}

kotlin {
    explicitApi()

    android {
        namespace = "io.github.pradyumnadp.blobface.compose"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions { jvmTarget = JvmTarget.JVM_11 }
    }
    jvm {
        compilerOptions { jvmTarget = JvmTarget.JVM_11 }
    }
    // Compose Multiplatform 1.12 no longer publishes iosX64 artifacts.
    iosArm64()
    iosSimulatorArm64()
    js { browser() }
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs { browser() }

    sourceSets {
        commonMain.dependencies {
            api(project(":blobface-core"))
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
    coordinates(property("blobface.group").toString(), "blobface-compose", property("blobface.version").toString())
    pom {
        name.set("blobface-compose")
        description.set(
            "Compose Multiplatform avatars rendered natively from blobface-core, with idle animation, " +
                "expressions and gaze. An unofficial port of blobatar (https://github.com/Alain00/blobatar).",
        )
        inceptionYear.set("2026")
        url.set("https://github.com/pradyumna-dp/blobface")
        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/licenses/MIT")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set("pradyumna-dp")
                name.set("Pradyumna DP")
                url.set("https://github.com/pradyumna-dp")
            }
        }
        scm {
            url.set("https://github.com/pradyumna-dp/blobface")
            connection.set("scm:git:git://github.com/pradyumna-dp/blobface.git")
            developerConnection.set("scm:git:ssh://git@github.com/pradyumna-dp/blobface.git")
        }
    }
}
