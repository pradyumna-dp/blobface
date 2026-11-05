package io.github.pradyumnadp.blobface.compose

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import io.github.pradyumnadp.blobface.PathCommand
import kotlin.time.TimeSource

/** Converts blobface path commands (viewBox units) to a Compose [Path]. */
public fun List<PathCommand>.toComposePath(): Path {
    val p = Path()
    var x = 0f
    var y = 0f
    for (c in this) {
        when (c) {
            is PathCommand.MoveTo -> { x = c.x.toFloat(); y = c.y.toFloat(); p.moveTo(x, y) }
            is PathCommand.LineTo -> { x = c.x.toFloat(); y = c.y.toFloat(); p.lineTo(x, y) }
            is PathCommand.HorizontalTo -> { x = c.x.toFloat(); p.lineTo(x, y) }
            is PathCommand.VerticalTo -> { y = c.y.toFloat(); p.lineTo(x, y) }
            is PathCommand.CubicTo -> {
                p.cubicTo(c.x1.toFloat(), c.y1.toFloat(), c.x2.toFloat(), c.y2.toFloat(), c.x.toFloat(), c.y.toFloat())
                x = c.x.toFloat(); y = c.y.toFloat()
            }
            is PathCommand.QuadTo -> {
                p.quadraticTo(c.x1.toFloat(), c.y1.toFloat(), c.x.toFloat(), c.y.toFloat())
                x = c.x.toFloat(); y = c.y.toFloat()
            }
            PathCommand.Close -> p.close()
        }
    }
    return p
}

/** Parses `#rrggbb` / `#rgb` into a [Color]; anything else draws black, as an invalid SVG fill would. */
public fun hexColor(hex: String): Color {
    val h = hex.removePrefix("#")
    val full = when (h.length) {
        3 -> h.map { "$it$it" }.joinToString("")
        6 -> h
        else -> return Color.Black
    }
    val v = full.toLongOrNull(16) ?: return Color.Black
    return Color(0xFF000000 or v)
}

/** sRGB component lerp, matching CSS `transition: fill`. */
internal fun lerpSrgb(a: Color, b: Color, t: Float): Color = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = a.alpha + (b.alpha - a.alpha) * t,
)

private val clockOrigin = TimeSource.Monotonic.markNow()

internal fun monoMillis(): Long = clockOrigin.elapsedNow().inWholeMilliseconds + 1
