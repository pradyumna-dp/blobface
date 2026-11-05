package io.github.pradyumnadp.blobface

import io.github.pradyumnadp.blobface.internal.JsMath
import io.github.pradyumnadp.blobface.internal.jsHypot
import kotlin.math.PI
import kotlin.math.abs



import kotlin.math.max
import kotlin.math.min


/** The ten silhouettes of the current generation, with their band weights. */
public enum class BlobfaceShape(
    /** The upstream name, e.g. `"round"`. */
    public val id: String,
    /** Upper edge of this shape's band in the `shape` trait's [0, 1) range. */
    public val upTo: Double,
) {
    /** A soft superellipse. */
    Round("round", 0.22),

    /** A lopsided pebble. */
    Organic("organic", 0.48),

    /** A squarer, tilted superellipse. */
    Boxy("boxy", 0.6),

    /** A stadium. */
    Capsule("capsule", 0.7),

    /** A round body with one or two nubs. */
    Nub("nub", 0.79),

    /** A pebble with lobes on top. */
    Cloud("cloud", 0.86),

    /** A teardrop. */
    Droplet("droplet", 0.915),

    /** A rounded hexagon. */
    Hexagon("hexagon", 0.95),

    /** A small body ringed with petals. */
    Sun("sun", 0.98),

    /** A rounded triangle. */
    Triangle("triangle", 1.0),
    ;

    /** Helpers. */
    public companion object {
        /** The shape a `shape` trait value selects. */
        public fun forTrait(v: Double): BlobfaceShape = entries.firstOrNull { v < it.upTo } ?: entries.last()

        /** A trait value that reliably selects [shape] (its band midpoint). */
        public fun midpoint(shape: BlobfaceShape): Double {
            val lower = if (shape.ordinal == 0) 0.0 else entries[shape.ordinal - 1].upTo
            return (lower + shape.upTo) / 2
        }
    }
}

/** The body geometry in viewBox units (0–100). */
public data class BlobfaceBody(
    /** Center x. */
    public val cx: Double,
    /** Center y. */
    public val cy: Double,
    /** Horizontal radius. */
    public val rx: Double,
    /** Vertical radius. */
    public val ry: Double,
    /** Superellipse squareness. */
    public val n: Double,
    /** Rotation in degrees. */
    public val rot: Double,
    /** Per-vertex radius multipliers (organic/cloud). */
    public val radii: List<Double>,
    /** Polygon side count, when the shape is a polygon. */
    public val sides: Int? = null,
    /** Polygon corner rounding, when the shape is a polygon. */
    public val round: Double? = null,
)

/** An axis-aligned ellipse: the region the eyes must fit inside. */
public data class Ellipse(
    /** Center x. */
    public val cx: Double,
    /** Center y. */
    public val cy: Double,
    /** Horizontal radius. */
    public val rx: Double,
    /** Vertical radius. */
    public val ry: Double,
)

/** A decoration circle (unrounded). */
public data class Petal(
    /** Center x. */
    public val cx: Double,
    /** Center y. */
    public val cy: Double,
    /** Radius. */
    public val r: Double,
)

/** One eye: a rotated superellipse. */
public data class Eye(
    /** Center x. */
    public val cx: Double,
    /** Center y. */
    public val cy: Double,
    /** Horizontal radius (before rotation). */
    public val rx: Double,
    /** Vertical radius (before rotation). */
    public val ry: Double,
    /** Squareness. */
    public val n: Double,
    /** Rotation (lean) in degrees. */
    public val rot: Double,
)

/**
 * The numeric layout for one blobface, before color and serialization.
 * Positions are in the 100×100 viewBox.
 */
public data class BlobfaceLayout(
    /** Which silhouette was selected. */
    public val shape: BlobfaceShape,
    /** The core body. */
    public val body: BlobfaceBody,
    /** The region the eyes were fitted into. */
    public val face: Ellipse,
    /** Decoration circles, drawn in the body color. */
    public val petals: List<Petal>,
    /** Extra outlines unioned with the body (the droplet's taper). */
    public val extra: List<List<PathCommand>>,
    /** Left eye first, then right. */
    public val eyes: List<Eye>,
) {
    /** The body outline as path commands. */
    public val bodyPath: List<PathCommand>
        get() = when (shape) {
            BlobfaceShape.Organic, BlobfaceShape.Cloud ->
                Primitives.blobPath(body.cx, body.cy, body.rx, body.ry, body.radii, body.rot)
            BlobfaceShape.Capsule -> Primitives.box(body.cx, body.cy, body.rx - body.ry, body.ry)
            BlobfaceShape.Hexagon, BlobfaceShape.Triangle ->
                Primitives.polygon(body.cx, body.cy, body.rx, body.ry, body.sides!!, body.round!!, body.rot)
            else -> Primitives.superellipse(body.cx, body.cy, body.rx, body.ry, body.n, body.rot)
        }
}

private class MutableBody(
    var cx: Double, var cy: Double, var rx: Double, var ry: Double,
    var n: Double, var rot: Double, val radii: List<Double>,
    var sides: Int? = null, var round: Double? = null,
) {
    fun freeze() = BlobfaceBody(cx, cy, rx, ry, n, rot, radii, sides, round)
}

private fun core(shape: BlobfaceShape): Double = when (shape) {
    BlobfaceShape.Round -> 1.0
    BlobfaceShape.Organic -> 0.98
    BlobfaceShape.Boxy -> 0.86
    BlobfaceShape.Capsule -> 1.02
    BlobfaceShape.Nub -> 0.88
    BlobfaceShape.Cloud -> 0.78
    BlobfaceShape.Droplet -> 0.78
    BlobfaceShape.Hexagon -> 1.05
    BlobfaceShape.Sun -> 0.7
    BlobfaceShape.Triangle -> 1.15
}

private fun shrunk(b: MutableBody, k: Double) = Ellipse(b.cx, b.cy, b.rx * k, b.ry * k)

/** Computes the layout from a trait reader. Mirrors `compose(BANDS, faceFit).layout`. */
internal fun computeLayout(t: BlobfaceTraits): BlobfaceLayout {
    val shape = BlobfaceShape.forTrait(t("shape"))
    val r = t.num("body.r", 31.0, 38.0) * core(shape)
    val bodyX = 50 + t.jitter("body.x", 1.5)
    val bodyY = 50 + t.jitter("body.y", 1.5)
    val ry = r * t.num("body.ratio", 0.92, 1.08)
    val n = t.num("body.n", 1.9, 2.5)
    val pts = t.int("body.pts", 6, 8)
    val radii = List(pts) { i -> 1 + t.jitter("body.r$i", 0.16) }
    val b = MutableBody(bodyX, bodyY, r, ry, n, 0.0, radii)

    // shape.body
    when (shape) {
        BlobfaceShape.Boxy -> {
            b.n = t.num("body.n", 3.4, 6.0)
            b.rot = t.num("body.rot", -20.0, 20.0)
        }
        BlobfaceShape.Capsule -> b.ry *= t.num("capsule.squat", 0.55, 0.68)
        BlobfaceShape.Droplet -> { b.cy += 0.22 * b.ry; b.n = 2.0 }
        BlobfaceShape.Hexagon -> {
            b.sides = 6
            b.rot = t.num("body.rot", -12.0, 12.0)
            b.round = t.num("poly.round", 0.24, 0.5)
        }
        BlobfaceShape.Triangle -> {
            b.sides = 3
            b.rot = t.num("body.rot", -5.0, 5.0)
            b.round = t.num("poly.round", 0.24, 0.5)
        }
        else -> Unit
    }

    // shape.face (default: the body itself)
    val face = when (shape) {
        BlobfaceShape.Organic, BlobfaceShape.Cloud -> shrunk(b, b.radii.min() * 0.95)
        BlobfaceShape.Capsule -> shrunk(b, 0.94)
        BlobfaceShape.Droplet -> Ellipse(b.cx, b.cy + b.ry * 0.05, b.rx * 0.88, b.ry * 0.88)
        BlobfaceShape.Hexagon -> shrunk(b, 0.84)
        BlobfaceShape.Triangle -> Ellipse(b.cx, b.cy + b.ry * 0.1, b.rx * 0.54, b.ry * 0.36)
        else -> Ellipse(b.cx, b.cy, b.rx, b.ry)
    }

    // shape.decorate
    val petals = ArrayList<Petal>()
    val extra = ArrayList<List<PathCommand>>()
    when (shape) {
        BlobfaceShape.Capsule -> for (s in intArrayOf(-1, 1)) petals += Petal(b.cx + s * (b.rx - b.ry), b.cy, b.ry)
        BlobfaceShape.Nub -> {
            val count = t.int("nub.n", 1, 2)
            for (i in 0 until count) {
                val a = t.num("nub.a$i", 0.0, 2 * PI)
                petals += Petal(
                    b.cx + JsMath.cos(a) * b.rx * 0.88,
                    b.cy + JsMath.sin(a) * b.rx * 0.88,
                    b.rx * t.num("nub.r$i", 0.24, 0.4),
                )
            }
        }
        BlobfaceShape.Cloud -> {
            val count = t.int("cloud.n", 4, 6)
            for (i in 0 until count) {
                val a = PI + (PI * (i + 0.5)) / count
                petals += Petal(
                    b.cx + JsMath.cos(a) * b.rx * 0.8,
                    b.cy + JsMath.sin(a) * b.rx * 0.5,
                    b.rx * t.num("cloud.r$i", 0.44, 0.62),
                )
            }
        }
        BlobfaceShape.Droplet -> extra += Primitives.taper(b.cx, b.cy, b.rx, b.ry, t.num("droplet.tip", 1.4, 1.65))
        BlobfaceShape.Sun -> {
            val count = t.int("sun.n", 6, 9)
            val dist = b.rx * t.num("sun.dist", 1.0, 1.08)
            val pr = b.rx * t.num("sun.r", 0.2, 0.26)
            val off = t.num("sun.rot", 0.0, 2 * PI)
            for (i in 0 until count) {
                val a = off + (2 * PI * i) / count
                petals += Petal(b.cx + JsMath.cos(a) * dist, b.cy + JsMath.sin(a) * dist, pr)
            }
        }
        else -> Unit
    }

    val body = b.freeze()
    return BlobfaceLayout(shape, body, face, petals, extra, faceFit(t, body, face))
}

/** Fits the eye cluster against the face region on both axes. */
private fun faceFit(t: BlobfaceTraits, b: BlobfaceBody, face: Ellipse): List<Eye> {
    val rx = b.rx
    val er0 = t.num("eye.rx", 0.075, 0.105) * rx
    val ratio = t.num("eye.ratio", 1.9, 3.2)
    val scale = t.num("eye.scale", 0.78, 1.24)
    val stretch = t.num("eye.stretch", 0.85, 1.18)
    val clearance = t.num("eye.gap", 0.1, 0.24) * rx
    val wide = er0 * max(1.0, scale)
    val tall = er0 * ratio * max(1.0, scale * stretch)
    val gap0 = wide + rx * 0.03 + clearance

    val gx = t.jitter("gaze.x", 0.09) * face.rx
    val gy = t.num("gaze.y", -0.2, 0.08) * face.ry
    val dy = t.jitter("eye.dy", 0.04) * face.ry
    val reach = jsHypot(wide, tall)
    val need = jsHypot(
        (abs(gx) + gap0 + reach) / face.rx,
        (abs(gy) + abs(dy) + reach) / face.ry,
    )
    val fit = if (need > 0.9) 0.9 / need else 1.0

    val er = er0 * fit
    val eyeRy = er * ratio
    val gap = gap0 * fit
    val room = max(0.0, min(1.0, clearance / tall))
    val bound = min(12.0, (JsMath.asin(room) * 180) / PI)
    val lean = t.num("eye.lean", -1.0, 1.0) * bound
    val lean2 = max(-12.0, min(12.0, lean + t.jitter("eye.lean2", 3.5)))

    val cx = face.cx + gx * fit
    val cy = face.cy + gy * fit
    return listOf(
        Eye(cx - gap, cy, er, eyeRy, t.num("eye.n", 3.5, 6.0), lean),
        Eye(cx + gap, cy + dy * fit, er * scale, eyeRy * scale * stretch, t.num("eye.n", 3.5, 6.0), lean2),
    )
}
