package io.github.pradyumnadp.blobface.sample

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.use
import io.github.pradyumnadp.blobface.BlobfaceBackground
import io.github.pradyumnadp.blobface.BlobfaceExpression
import io.github.pradyumnadp.blobface.BlobfaceOptions
import io.github.pradyumnadp.blobface.compose.Blobface
import io.github.pradyumnadp.blobface.compose.BlobfaceAnimation
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Data
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.Surface
import org.jetbrains.skia.svg.SVGDOM
import java.io.File
import kotlin.math.abs
import io.github.pradyumnadp.blobface.Blobface as BlobfaceModel

/**
 * Compares the native Compose rendering against the SVG (byte-identical to
 * upstream blobatar) rasterized by Skia's SVG renderer, pixel by pixel.
 *
 * `./gradlew :sample:renderCheck` — fails if any face differs beyond anti-aliasing.
 */
fun main(args: Array<String>) {
    val px = 160
    val outDir = File(args.firstOrNull() ?: "build/render-check").apply { mkdirs() }
    val names = listOf("alain@example.com", "bo@corp.io", "quinn@corp.io", "kai@example.com", "sami@uni.edu", "femi@uni.edu",
        "milo@mail.dev", "tara@uni.edu", "🦊", "日本語", "", "Zoë") + (0 until 60).map { "user$it@example.com" }
    val variants = listOf(
        BlobfaceOptions(),
        BlobfaceOptions(background = BlobfaceBackground.Squircle, expression = BlobfaceExpression.Happy),
        BlobfaceOptions(background = BlobfaceBackground.Circle, expression = BlobfaceExpression.Mad),
        BlobfaceOptions(background = BlobfaceBackground.Square, expression = BlobfaceExpression.Thinking, tone = 0.999),
        BlobfaceOptions(expression = BlobfaceExpression.Wink, hue = 120.0),
    )
    var worst = 0.0
    var worstName = ""
    var count = 0
    for (animated in listOf(false, true)) for ((vi, opts) in variants.withIndex()) for (name in names) {
        val svg = BlobfaceModel(name, opts.copy(size = px)).toSvgString()
        val ref = rasterizeSvg(svg, px)
        val mine = ImageComposeScene(px, px, Density(1f)) {
            Box(Modifier.fillMaxSize()) {
                Blobface(name, opts, size = px.dp, animate = if (animated) BlobfaceAnimation.Hover else BlobfaceAnimation.None)
            }
        }.use { it.render() }
        val diff = diffRatio(ref, mine)
        count++
        if (diff > worst) { worst = diff; worstName = "$name / variant $vi / animated=$animated" }
        if (diff > 0.01) {
            File(outDir, "fail-$animated-$vi-${name.hashCode()}-ref.png").writeBytes(ref.encodeToData(EncodedImageFormat.PNG)!!.bytes)
            File(outDir, "fail-$animated-$vi-${name.hashCode()}-compose.png").writeBytes(mine.encodeToData(EncodedImageFormat.PNG)!!.bytes)
            println("DIFF ${"%.4f".format(diff)}  $name  variant=$vi animated=$animated")
        }
    }
    println("renderCheck: $count faces, worst differing-pixel ratio ${"%.4f".format(worst)} ($worstName)")
    if (worst > 0.01) error("render mismatch above 1% of pixels")
}

private fun rasterizeSvg(svg: String, px: Int): Image {
    val dom = SVGDOM(Data.makeFromBytes(svg.toByteArray()))
    val surface = Surface.makeRasterN32Premul(px, px)
    dom.setContainerSize(px.toFloat(), px.toFloat())
    dom.render(surface.canvas)
    return surface.makeImageSnapshot()
}

/** Fraction of pixels whose channels differ by more than 48/255 (anti-aliasing tolerance). */
private fun diffRatio(a: Image, b: Image): Double {
    val ba = Bitmap().apply { allocN32Pixels(a.width, a.height); Canvas(this).drawImage(a, 0f, 0f) }
    val bb = Bitmap().apply { allocN32Pixels(b.width, b.height); Canvas(this).drawImage(b, 0f, 0f) }
    var bad = 0
    for (y in 0 until a.height) for (x in 0 until a.width) {
        val p = ba.getColor(x, y)
        val q = bb.getColor(x, y)
        var m = 0
        for (s in intArrayOf(0, 8, 16, 24)) m = maxOf(m, abs(((p ushr s) and 255) - ((q ushr s) and 255)))
        if (m > 48) bad++
    }
    return bad.toDouble() / (a.width * a.height)
}
