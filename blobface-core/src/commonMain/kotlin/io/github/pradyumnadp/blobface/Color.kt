package io.github.pradyumnadp.blobface

import io.github.pradyumnadp.blobface.internal.JsMath
import io.github.pradyumnadp.blobface.internal.jsHypot
import io.github.pradyumnadp.blobface.internal.jsHex
import io.github.pradyumnadp.blobface.internal.jsParseHex
import io.github.pradyumnadp.blobface.internal.jsRound
import io.github.pradyumnadp.blobface.internal.jsToInt32
import kotlin.math.PI




import kotlin.math.max
import kotlin.math.min



/** A color in OKLCh: lightness 0–1, chroma, hue in degrees. */
public data class Oklch(public val l: Double, public val c: Double, public val h: Double)

/** The color slots a blobface has. */
public enum class ColorKey {
    /** The backdrop plate. */
    Bg,

    /** The body and its decorations. */
    Head,

    /** Both eyes. */
    Eye,
}

/**
 * A palette of `#rrggbb` hex colors. As an option, `null` entries mean "not
 * overridden"; as a result, every entry is filled.
 */
public data class BlobfacePalette(
    /** Backdrop color. */
    public val bg: String? = null,
    /** Body color. */
    public val head: String? = null,
    /** Eye color. */
    public val eye: String? = null,
) {
    /** Returns this palette with every non-null entry of [other] applied on top. */
    public fun overriddenBy(other: BlobfacePalette?): BlobfacePalette =
        if (other == null) this else BlobfacePalette(other.bg ?: bg, other.head ?: head, other.eye ?: eye)
}

/** OKLCh → linear-light sRGB (may be out of gamut). */
private fun toLinear(l: Double, c: Double, h: Double): DoubleArray {
    val r = (h * PI) / 180
    val a = c * JsMath.cos(r)
    val b = c * JsMath.sin(r)
    val l_ = l + 0.3963377774 * a + 0.2158037573 * b
    val m_ = l - 0.1055613458 * a - 0.0638541728 * b
    val s_ = l - 0.0894841775 * a - 1.291485548 * b
    val ll = l_ * l_ * l_
    val mm = m_ * m_ * m_
    val ss = s_ * s_ * s_
    return doubleArrayOf(
        4.0767416621 * ll - 3.3077115913 * mm + 0.2309699292 * ss,
        -1.2684380046 * ll + 2.6097574011 * mm - 0.3413193965 * ss,
        -0.0041960863 * ll - 0.7034186147 * mm + 1.707614701 * ss,
    )
}

private fun inGamut(rgb: DoubleArray): Boolean = rgb.all { it >= -1e-4 && it <= 1 + 1e-4 }

/** In-gamut linear sRGB, reducing chroma by bisection when needed. */
private fun resolveColor(color: Oklch): DoubleArray {
    var rgb = toLinear(color.l, color.c, color.h)
    if (!inGamut(rgb)) {
        var lo = 0.0
        var hi = color.c
        repeat(12) {
            val mid = (lo + hi) / 2
            if (inGamut(toLinear(color.l, mid, color.h))) lo = mid else hi = mid
        }
        rgb = toLinear(color.l, lo, color.h)
    }
    return DoubleArray(3) { min(1.0, max(0.0, rgb[it])) }
}

private fun luminance(color: Oklch): Double {
    val rgb = resolveColor(color)
    return 0.2126 * rgb[0] + 0.7152 * rgb[1] + 0.0722 * rgb[2]
}

/** WCAG contrast ratio between two OKLCh colors, measured on gamut-mapped sRGB. */
public fun contrast(a: Oklch, b: Oklch): Double {
    val x = luminance(a)
    val y = luminance(b)
    return (max(x, y) + 0.05) / (min(x, y) + 0.05)
}

/**
 * Pushes [fg]'s lightness away from [bg] in steps of 0.02 until the pair clears
 * [min], trying the direction it already leans first.
 */
public fun ensureContrast(fg: Oklch, bg: Oklch, min: Double): Oklch {
    if (contrast(fg, bg) >= min) return fg
    val lean = if (fg.l >= bg.l) 1 else -1
    for (dir in intArrayOf(lean, -lean)) {
        var probe = fg
        for (i in 0 until 60) {
            probe = probe.copy(l = min(1.0, max(0.0, probe.l + dir * 0.02)))
            if (contrast(probe, bg) >= min) return probe
            if (probe.l == 0.0 || probe.l == 1.0) break
        }
    }
    val black = fg.copy(l = 0.0, c = 0.0)
    val white = fg.copy(l = 1.0, c = 0.0)
    return if (contrast(black, bg) >= contrast(white, bg)) black else white
}

/** OKLCh → `#rrggbb`. */
public fun toHex(color: Oklch): String {
    val sb = StringBuilder("#")
    for (v in resolveColor(color)) {
        val s = if (v <= 0.0031308) 12.92 * v else 1.055 * JsMath.pow(v, 1 / 2.4) - 0.055
        sb.append(jsHex(jsRound(s * 255)).padStart(2, '0'))
    }
    return sb.toString()
}

/** `#rrggbb` → OKLCh. Parses like JS `parseInt(hex.slice(1), 16)`. */
public fun fromHex(hex: String): Oklch {
    val n = jsToInt32(jsParseHex(if (hex.isEmpty()) "" else hex.substring(1)))
    val ch = intArrayOf((n shr 16) and 255, (n shr 8) and 255, n and 255).map {
        val s = it / 255.0
        if (s <= 0.04045) s / 12.92 else JsMath.pow((s + 0.055) / 1.055, 2.4)
    }
    val r = ch[0]
    val g = ch[1]
    val b = ch[2]
    val l = JsMath.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
    val m = JsMath.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
    val s = JsMath.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
    val aa = 1.9779984951 * l - 2.428592205 * m + 0.4505937099 * s
    val bb = 0.0259040371 * l + 0.7827717662 * m - 0.808675766 * s
    return Oklch(
        l = 0.2104542553 * l + 0.793617785 * m - 0.0040720468 * s,
        c = jsHypot(aa, bb),
        h = (JsMath.atan2(bb, aa) * 180) / PI,
    )
}

/** Blends two colors in OKLab (`color-mix(in oklab, a, b t)`). */
public fun mix(a: Oklch, b: Oklch, t: Double): Oklch {
    fun rad(v: Double) = (v * PI) / 180
    val ax = a.c * JsMath.cos(rad(a.h))
    val ay = a.c * JsMath.sin(rad(a.h))
    val bx = b.c * JsMath.cos(rad(b.h))
    val by = b.c * JsMath.sin(rad(b.h))
    val x = ax + (bx - ax) * t
    val y = ay + (by - ay) * t
    return Oklch(l = a.l + (b.l - a.l) * t, c = jsHypot(x, y), h = (JsMath.atan2(y, x) * 180) / PI)
}

/** [mix] between two hex colors, as hex. */
public fun mixHex(a: String, b: String, t: Double): String = toHex(mix(fromHex(a), fromHex(b), t))

/**
 * Byte-wise sRGB interpolation between two hex colors, matching CSS
 * `transition: fill`. Used while morphing between expressions.
 */
public fun fadeHex(a: String, b: String, t: Double): String {
    val out = StringBuilder("#")
    var i = 1
    while (i < 7) {
        val from = jsParseHex(a.safeSlice(i, i + 2))
        val to = jsParseHex(b.safeSlice(i, i + 2))
        val v = jsRound(from + (to - from) * t)
        out.append(if (v < 16) "0" else "").append(jsHex(v))
        i += 2
    }
    return out.toString()
}

private fun String.safeSlice(from: Int, to: Int): String =
    if (from >= length) "" else substring(from, kotlin.math.min(to, length))

/**
 * Where a tinting expression heads: a hue it reaches outright, a lightness it
 * moves [pull] of the way toward, and a chroma floor.
 */
public data class Tint(
    /** Target hue in degrees. */
    public val h: Double,
    /** Target lightness. */
    public val l: Double,
    /** Fraction of the way to [l] the body travels, 0–1. */
    public val pull: Double,
    /** Chroma floor. */
    public val c: Double,
) {
    /** The four upstream tint targets. */
    public companion object {
        /** Red, for `mad`. */
        public val HOT: Tint = Tint(h = 27.0, l = 0.58, pull = 0.6, c = 0.18)

        /** Warm pink, for `love`. */
        public val ROSE: Tint = Tint(h = 358.0, l = 0.72, pull = 0.55, c = 0.16)

        /** Pale blush, for `shy`. */
        public val BLUSH: Tint = Tint(h = 12.0, l = 0.84, pull = 0.4, c = 0.1)

        /** Queasy green, for `sick`. */
        public val BILE: Tint = Tint(h = 142.0, l = 0.66, pull = 0.6, c = 0.13)

        /** Every target, by name. */
        public val ALL: List<Pair<String, Tint>> =
            listOf("hot" to HOT, "rose" to ROSE, "blush" to BLUSH, "bile" to BILE)
    }
}

private const val TINT_FLOOR = 4.55
private val DARK_SURFACE = Oklch(0.145, 0.0, 0.0)
private const val SURFACE_FLOOR = 1.5

/**
 * The (head, eye) hex pair a tinting expression heads toward, derived from the
 * colors the face is actually wearing. Contrast ≥ 4.55:1 is held at every point
 * along the mix, not merely at both ends.
 */
public fun tinted(head: String, eye: String, t: Tint): Pair<String, String> {
    val base = fromHex(head)
    val baseEye = fromHex(eye)
    var hotHead = Oklch(l = base.l + (t.l - base.l) * t.pull, c = max(base.c, t.c), h = t.h)
    hotHead = ensureContrast(hotHead, DARK_SURFACE, SURFACE_FLOOR)
    var hotEye = ensureContrast(baseEye, hotHead, TINT_FLOOR)
    val dir = if (hotEye.l >= hotHead.l) 1 else -1
    val headHex = toHex(hotHead)
    for (pass in 0 until 40) {
        val eyeHex = toHex(hotEye)
        var worst = Double.POSITIVE_INFINITY
        for (i in 0..10) {
            val tt = i / 10.0
            worst = min(worst, contrast(fromHex(mixHex(eye, eyeHex, tt)), fromHex(mixHex(head, headHex, tt))))
        }
        if (worst >= TINT_FLOOR) return headHex to eyeHex
        val l = min(1.0, max(0.0, hotEye.l + dir * 0.02))
        if (l == hotEye.l) return headHex to eyeHex
        hotEye = hotEye.copy(l = l)
    }
    return headHex to toHex(hotEye)
}

/** The six authored tone swatches as (upper edge, L, C). */
private val TONES: List<Triple<Double, Double, Double>> = listOf(
    Triple(0.2, 0.86, 0.085), // pastel
    Triple(0.36, 0.9, 0.028), // pale neutral
    Triple(0.62, 0.73, 0.135), // mid
    Triple(0.8, 0.62, 0.165), // deep
    Triple(0.93, 0.87, 0.16), // bright
    Triple(1.0, 0.34, 0.035), // ink
)

private fun toneAt(v: Double): Triple<Double, Double, Double> = TONES.firstOrNull { v < it.first } ?: TONES[0]

/**
 * Minimum contrast floors as (foreground, background, ratio), applied in order:
 * body vs backdrop ≥ 1.25, eyes vs body ≥ 4.5.
 */
public val FLOORS: List<Triple<ColorKey, ColorKey, Double>> = listOf(
    Triple(ColorKey.Head, ColorKey.Bg, 1.25),
    Triple(ColorKey.Eye, ColorKey.Head, 4.5),
)

/** The palette in OKLCh before hex encoding. */
public fun ramp(hue: Double, enforce: Boolean = true, tone: Double = 0.0): Map<ColorKey, Oklch> {
    val sw = toneAt(tone)
    val head = ensureContrast(Oklch(sw.second, sw.third, hue), DARK_SURFACE, SURFACE_FLOOR)
    val r = linkedMapOf(
        ColorKey.Bg to Oklch(0.965, 0.01, hue),
        ColorKey.Head to head,
        ColorKey.Eye to if (head.l >= 0.5) Oklch(0.17, 0.02, hue) else Oklch(0.97, 0.012, hue),
    )
    if (enforce) {
        for ((fg, bg, min) in FLOORS) r[fg] = ensureContrast(r.getValue(fg), r.getValue(bg), min)
    }
    return r
}

/** The hex palette for a hue (degrees) and tone position (0–1). */
public fun palette(hue: Double, enforce: Boolean = true, tone: Double = 0.0): BlobfacePalette {
    val r = ramp(hue, enforce, tone)
    return BlobfacePalette(
        bg = toHex(r.getValue(ColorKey.Bg)),
        head = toHex(r.getValue(ColorKey.Head)),
        eye = toHex(r.getValue(ColorKey.Eye)),
    )
}
