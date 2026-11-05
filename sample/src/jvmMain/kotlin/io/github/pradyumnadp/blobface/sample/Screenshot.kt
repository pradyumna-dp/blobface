package io.github.pradyumnadp.blobface.sample

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import org.jetbrains.skia.EncodedImageFormat
import java.io.File

/**
 * Renders the sample offscreen to a PNG for the README:
 * `./gradlew :sample:screenshot` → docs/screenshots/desktop.png
 */
fun main(args: Array<String>) {
    val out = File(args.firstOrNull() ?: "docs/screenshots/desktop.png")
    out.parentFile.mkdirs()
    ImageComposeScene(width = 1400, height = 900, density = Density(1f)) { App() }.use { scene ->
        scene.render(0)
        scene.render(16_000_000) // let the first frame settle
        val png = scene.render(32_000_000).encodeToData(EncodedImageFormat.PNG) ?: error("encode failed")
        out.writeBytes(png.bytes)
    }
    println("wrote ${out.absolutePath}")
}
