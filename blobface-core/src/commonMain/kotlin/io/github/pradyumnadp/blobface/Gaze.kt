package io.github.pradyumnadp.blobface

import io.github.pradyumnadp.blobface.internal.JsMath
import io.github.pradyumnadp.blobface.internal.jsHypot
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * The pure math of upstream's pointer-gaze layer (`blobatar/gaze`): eyes treated
 * as marks on a sphere that turns toward a target. Renderers drive it per frame;
 * `blobface-compose` does so in `Blobface(gaze = …)`.
 */
public object GazeMath {
    /** Pursuit time constant, ms. */
    public const val SETTLE: Double = 110.0

    /** Aim distance beyond which the eyes jump instead of easing. */
    public const val SNAP: Double = 1.6

    /** Fraction of the face radius inside which the look fades to center. */
    public const val DEADZONE: Double = 0.55

    /** Smallest on-screen motion worth a frame, px. */
    public const val VISIBLE_PX: Double = 0.15

    /** Hold-channel settle tolerance. */
    public const val HOLD_EPS: Double = 0.01

    /** How close to the sphere's rim an eye may turn. */
    public const val LIMB: Double = 0.97

    /** Degrees of tilt per unit of foreshortening shear. */
    public const val TILT: Double = 4.0

    private const val EPS_MIN = 0.002
    private const val EPS_MAX = 0.06

    /** `t² (3 − 2t)`. */
    public fun smoothstep(t: Double): Double = t * t * (3 - 2 * t)

    /** Per-frame pursuit factor for a frame of [dt] ms. */
    public fun pursuit(dt: Double, settle: Double = SETTLE): Double =
        if (settle <= 0) 1.0 else 1 - JsMath.exp(-dt / settle)

    /** Settle tolerance in aim units for a face [width] px wide with [travel] px excursion. */
    public fun threshold(width: Double, travel: Double): Double {
        val perUnit = travel * (width / 100)
        return if (perUnit > 0) min(EPS_MAX, max(EPS_MIN, VISIBLE_PX / perUnit)) else EPS_MAX
    }

    /** An eye's rest position in face-normalized units (unit circle ≈ face). */
    public data class Mark(public val x: Double, public val y: Double)

    /** How an eye moves when the sphere turns: offset, foreshortening, and tilt. */
    public data class Projection(
        /** Offset x, face units. */
        public val dx: Double,
        /** Offset y, face units. */
        public val dy: Double,
        /** Horizontal scale factor (≤ 1). */
        public val sx: Double,
        /** Vertical scale factor (≤ 1). */
        public val sy: Double,
        /** Extra rotation in degrees. */
        public val t: Double,
    )

    private data class Patch(val sx: Double, val sy: Double, val sh: Double)

    private fun patch(x: Double, y: Double, z: Double): Patch {
        val r2 = x * x + y * y
        if (r2 < 1e-9) return Patch(1.0, 1.0, 0.0)
        val d = max(0.0, z)
        return Patch((d * x * x + y * y) / r2, (d * y * y + x * x) / r2, ((d - 1) * x * y) / r2)
    }

    /** Projects mark [m] after turning the sphere by [yaw] and [pitch] radians. */
    public fun project(m: Mark, yaw: Double, pitch: Double): Projection {
        val r2 = m.x * m.x + m.y * m.y
        val k = if (r2 > 1) 1 / sqrt(r2) else 1.0
        val x0 = m.x * k
        val y0 = m.y * k
        val z0 = sqrt(max(0.0, 1 - x0 * x0 - y0 * y0))
        val cy = JsMath.cos(yaw)
        val sy = JsMath.sin(yaw)
        val x1 = x0 * cy + z0 * sy
        val z1 = z0 * cy - x0 * sy
        val cp = JsMath.cos(pitch)
        val sp = JsMath.sin(pitch)
        val y1 = y0 * cp + z1 * sp
        val z2 = z1 * cp - y0 * sp
        val rho = jsHypot(x1, y1)
        val over = z2 <= 0 || rho > LIMB
        val back = if (over) LIMB / (if (rho != 0.0) rho else 1.0) else 1.0
        val px = x1 * back
        val py = y1 * back
        val pz = if (over) sqrt(1 - LIMB * LIMB) else z2
        val rest = patch(x0, y0, z0)
        val now = patch(px, py, pz)
        return Projection(
            dx = px - m.x,
            dy = py - m.y,
            sx = min(1.0, now.sx / (if (rest.sx != 0.0) rest.sx else 1.0)),
            sy = min(1.0, now.sy / (if (rest.sy != 0.0) rest.sy else 1.0)),
            t = TILT * (now.sh - rest.sh),
        )
    }

    /** Result of one pursuit step. */
    public data class Step(
        /** New aim x. */
        public val x: Double,
        /** New aim y. */
        public val y: Double,
        /** Target aim x. */
        public val tx: Double,
        /** Target aim y. */
        public val ty: Double,
        /** Fraction of the gap closed this frame. */
        public val f: Double,
    )

    /**
     * Advances the aim ([x], [y]) toward the target offset ([dx], [dy]) px from the
     * face center, with face [radius] px and pursuit factor [k].
     */
    public fun step(
        x: Double, y: Double, dx: Double, dy: Double, radius: Double, k: Double,
        gain: Double = 1.0, snap: Double = SNAP,
    ): Step {
        val d = jsHypot(dx, dy)
        val near = if (radius > 0) smoothstep(min(1.0, d / (radius * DEADZONE))) else 1.0
        val amp = gain * near
        val tx = if (d > 0) (dx / d) * amp else 0.0
        val ty = if (d > 0) (dy / d) * amp else 0.0
        val f = if (jsHypot(tx - x, ty - y) > snap) 1.0 else k
        return Step(x + (tx - x) * f, y + (ty - y) * f, tx, ty, f)
    }

    /** The face as the gaze layer sees it: eye marks and the sphere's radii in viewBox units. */
    public data class Face(public val marks: List<Mark>, public val rx: Double, public val ry: Double)

    /**
     * Measures the face of a layout the way upstream's DOM `survey` does: the
     * body group's bounding box, shrunk until it fits inside the filled outline
     * on 16 rays, minus the eyes' half-extent.
     */
    public fun survey(layout: BlobfaceLayout): Face {
        val head = buildList {
            for (p in layout.petals) add(circlePolygon(p.cx, p.cy, p.r))
            for (e in layout.extra) add(flatten(e))
            add(flatten(layout.bodyPath))
        }
        var minX = Double.POSITIVE_INFINITY
        var minY = Double.POSITIVE_INFINITY
        var maxX = Double.NEGATIVE_INFINITY
        var maxY = Double.NEGATIVE_INFINITY
        for (poly in head) for (pt in poly) {
            minX = min(minX, pt[0]); maxX = max(maxX, pt[0]); minY = min(minY, pt[1]); maxY = max(maxY, pt[1])
        }
        val w = maxX - minX
        val h = maxY - minY
        val cx0 = minX + w / 2
        val cy0 = minY + h / 2
        fun hit(t: Double, c: Double, s: Double): Boolean {
            val px = cx0 + (t * w / 2) * c
            val py = cy0 + (t * h / 2) * s
            return head.any { inside(it, px, py) }
        }
        var fit = 1.0
        for (a in 0 until 16) {
            val r = (a / 16.0) * PI * 2
            val c = JsMath.cos(r)
            val s = JsMath.sin(r)
            if (hit(1.0, c, s)) continue
            var lo = 0.0
            var hi = 1.0
            repeat(12) {
                val mid = (lo + hi) / 2
                if (hit(mid, c, s)) lo = mid else hi = mid
            }
            fit = min(fit, lo)
        }
        var ix = 0.0
        var iy = 0.0
        val centres = layout.eyes.map { e ->
            val poly = flatten(eyePath(e))
            val ex0 = poly.minOf { it[0] }
            val ex1 = poly.maxOf { it[0] }
            val ey0 = poly.minOf { it[1] }
            val ey1 = poly.maxOf { it[1] }
            ix = max(ix, (ex1 - ex0) / 2)
            iy = max(iy, (ey1 - ey0) / 2)
            Mark((ex0 + ex1) / 2 - cx0, (ey0 + ey1) / 2 - cy0)
        }
        var rx = max(1.0, (w / 2) * fit - ix)
        var ry = max(1.0, (h / 2) * fit - iy)
        var need = 0.0
        for (c in centres) need = max(need, jsHypot(c.x / rx, c.y / ry))
        if (need > 0.85) {
            rx *= need / 0.85
            ry *= need / 0.85
        }
        return Face(centres.map { Mark(it.x / rx, it.y / ry) }, rx, ry)
    }

    private fun circlePolygon(cx: Double, cy: Double, r: Double): List<DoubleArray> =
        List(48) { i ->
            val a = 2 * PI * i / 48
            doubleArrayOf(cx + r * JsMath.cos(a), cy + r * JsMath.sin(a))
        }

    /** Flattens path commands into a polygon (curves sampled at 16 steps). */
    internal fun flatten(cmds: List<PathCommand>): List<DoubleArray> {
        val out = ArrayList<DoubleArray>()
        var x = 0.0
        var y = 0.0
        for (c in cmds) when (c) {
            is PathCommand.MoveTo -> { x = c.x; y = c.y; out += doubleArrayOf(x, y) }
            is PathCommand.LineTo -> { x = c.x; y = c.y; out += doubleArrayOf(x, y) }
            is PathCommand.HorizontalTo -> { x = c.x; out += doubleArrayOf(x, y) }
            is PathCommand.VerticalTo -> { y = c.y; out += doubleArrayOf(x, y) }
            is PathCommand.CubicTo -> {
                for (i in 1..16) {
                    val t = i / 16.0
                    val u = 1 - t
                    out += doubleArrayOf(
                        u * u * u * x + 3 * u * u * t * c.x1 + 3 * u * t * t * c.x2 + t * t * t * c.x,
                        u * u * u * y + 3 * u * u * t * c.y1 + 3 * u * t * t * c.y2 + t * t * t * c.y,
                    )
                }
                x = c.x; y = c.y
            }
            is PathCommand.QuadTo -> {
                for (i in 1..16) {
                    val t = i / 16.0
                    val u = 1 - t
                    out += doubleArrayOf(u * u * x + 2 * u * t * c.x1 + t * t * c.x, u * u * y + 2 * u * t * c.y1 + t * t * c.y)
                }
                x = c.x; y = c.y
            }
            PathCommand.Close -> Unit
        }
        return out
    }

    /** Non-zero winding point-in-polygon. */
    private fun inside(poly: List<DoubleArray>, px: Double, py: Double): Boolean {
        var wn = 0
        for (i in poly.indices) {
            val a = poly[i]
            val b = poly[(i + 1) % poly.size]
            val cross = (b[0] - a[0]) * (py - a[1]) - (px - a[0]) * (b[1] - a[1])
            if (a[1] <= py) {
                if (b[1] > py && cross > 0) wn++
            } else if (b[1] <= py && cross < 0) {
                wn--
            }
        }
        return wn != 0 || poly.any { abs(it[0] - px) < 1e-9 && abs(it[1] - py) < 1e-9 }
    }
}
