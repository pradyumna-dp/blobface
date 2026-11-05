import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.mavenPublish)
}

kotlin {
    explicitApi()

    android {
        namespace = "io.github.pradyumnadp.blobface.core"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions { jvmTarget = JvmTarget.JVM_11 }
        withHostTest {}
    }
    jvm {
        compilerOptions { jvmTarget = JvmTarget.JVM_11 }
    }
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    js(IR) {
        browser { testTask { enabled = false } }
        nodejs { testTask { useMocha { timeout = "120s" } } }
    }
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser { testTask { enabled = false } }
        nodejs()
    }

    sourceSets {
        commonTest {
            kotlin.srcDir(layout.buildDirectory.dir("generated/fixturePath/kotlin"))
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.serialization.json)
            }
        }
    }
}

// The parity fixtures are large JSON files. Rather than fight each platform's
// resource packaging, tests read them straight from disk via an absolute path
// generated here (JVM, Android host, Node for js/wasm, and the iOS simulator
// can all reach the host filesystem).
val fixtureDir = layout.projectDirectory.dir("src/commonTest/resources").asFile.absolutePath
val generateFixturePath by tasks.registering {
    val out = layout.buildDirectory.file("generated/fixturePath/kotlin/FixturePath.kt")
    val dir = fixtureDir
    inputs.property("dir", dir)
    outputs.file(out)
    doLast {
        val escaped = dir.replace("\\", "/")
        out.get().asFile.apply {
            parentFile.mkdirs()
            writeText(
                "package io.github.pradyumnadp.blobface\n\n" +
                    "internal const val FIXTURE_DIR: String = \"$escaped\"\n",
            )
        }
    }
}
tasks.matching { it.name.startsWith("compile") && it.name.contains("Test") }.configureEach {
    dependsOn(generateFixturePath)
}
tasks.matching { it.name.contains("TestKotlinMetadata") || it.name.contains("SourcesJar") }.configureEach {
    dependsOn(generateFixturePath)
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
    coordinates(property("blobface.group").toString(), "blobface-core", property("blobface.version").toString())
    pom {
        name.set("blobface-core")
        description.set(
            "Deterministic geometric avatars from any string, for Kotlin Multiplatform. " +
                "An unofficial port of blobatar (https://github.com/Alain00/blobatar).",
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
