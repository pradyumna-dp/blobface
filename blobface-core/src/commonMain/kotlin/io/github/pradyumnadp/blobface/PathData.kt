package io.github.pradyumnadp.blobface

import io.github.pradyumnadp.blobface.internal.JsMath
import io.github.pradyumnadp.blobface.internal.r2
import io.github.pradyumnadp.blobface.internal.r2n
import kotlin.math.PI

import kotlin.math.max
import kotlin.math.min


import kotlin.math.sqrt

/**
 * One SVG path command with absolute coordinates already rounded to two decimals,
 * exactly as they appear in the emitted `d` attribute. Renderers can draw these
 * directly (Compose does), and [toSvgPathData] serializes them byte-identically
 * to upstream.
 */
public sealed interface PathCommand {
    /** `M x y`. */
    public data class MoveTo(public val x: Double, public val y: Double) : PathCommand

    /** `L x y`. */
    public data class LineTo(public val x: Double, public val y: Double) : PathCommand

    /** `H x`. */
    public data class HorizontalTo(public val x: Double) : PathCommand

    /** `V y`. */
    public data class VerticalTo(public val y: Double) : PathCommand

    /** `C x1 y1 x2 y2 x y`. */
    public data class CubicTo(
        public val x1: Double, public val y1: Double,
        public val x2: Double, public val y2: Double,
        public val x: Double, public val y: Double,
    ) : PathCommand

    /** `Q x1 y1 x y`. */
    public data class QuadTo(
        public val x1: Double, public val y1: Double,
        public val x: Double, public val y: Double,
    ) : PathCommand

    /** `Z`. */
    public data object Close : PathCommand
}

/** Serializes commands to SVG path data, e.g. `M1 2C3 4 5 6 7 8Z`. */
public fun List<PathCommand>.toSvgPathData(): String {
    val sb = StringBuilder()
    for (c in this) {
        when (c) {
            is PathCommand.MoveTo -> sb.append('M').append(r2(c.x)).append(' ').append(r2(c.y))
            is PathCommand.LineTo -> sb.append('L').append(r2(c.x)).append(' ').append(r2(c.y))
            is PathCommand.HorizontalTo -> sb.append('H').append(r2(c.x))
            is PathCommand.VerticalTo -> sb.append('V').append(r2(c.y))
            is PathCommand.CubicTo -> sb.append('C')
                .append(r2(c.x1)).append(' ').append(r2(c.y1)).append(' ')
                .append(r2(c.x2)).append(' ').append(r2(c.y2)).append(' ')
                .append(r2(c.x)).append(' ').append(r2(c.y))
            is PathCommand.QuadTo -> sb.append('Q')
                .append(r2(c.x1)).append(' ').append(r2(c.y1)).append(' ')
                .append(r2(c.x)).append(' ').append(r2(c.y))
            PathCommand.Close -> sb.append('Z')
        }
    }
    return sb.toString()
}

/** Geometry primitives, ported from blobatar's `shape.ts`. */
public object Primitives {
    private fun q(v: Double) = r2n(v)

    /**
     * A superellipse `|x/a|^n + |y/b|^n = 1` approximated by four cubic Béziers,
     * rotated [rot] degrees about its center.
     */
    public fun superellipse(cx: Double, cy: Double, rx: Double, ry: Double, n: Double = 4.0, rot: Double = 0.0): List<PathCommand> {
        val k = min(1.0, (8 * JsMath.pow(2.0, -1 / n) - 4) / 3)
        val a = rx
        val b = ry
        val ak = a * k
        val bk = b * k
        val pts = arrayOf(
            doubleArrayOf(a, 0.0),
            doubleArrayOf(a, bk), doubleArrayOf(ak, b), doubleArrayOf(0.0, b),
            doubleArrayOf(-ak, b), doubleArrayOf(-a, bk), doubleArrayOf(-a, 0.0),
            doubleArrayOf(-a, -bk), doubleArrayOf(-ak, -b), doubleArrayOf(0.0, -b),
            doubleArrayOf(ak, -b), doubleArrayOf(a, -bk), doubleArrayOf(a, 0.0),
        )
        val t = (rot * PI) / 180
        val cs = JsMath.cos(t)
        val sn = JsMath.sin(t)
        fun x(i: Int) = q(cx + pts[i][0] * cs - pts[i][1] * sn)
        fun y(i: Int) = q(cy + pts[i][0] * sn + pts[i][1] * cs)
        val out = ArrayList<PathCommand>(6)
        out += PathCommand.MoveTo(x(0), y(0))
        var i = 1
        while (i < 13) {
            out += PathCommand.CubicTo(x(i), y(i), x(i + 1), y(i + 1), x(i + 2), y(i + 2))
            i += 3
        }
        out += PathCommand.Close
        return out
    }

    /** A quadratic arc (unused by the current generation, kept for parity). */
    public fun arc(cx: Double, cy: Double, w: Double, depth: Double): List<PathCommand> = listOf(
        PathCommand.MoveTo(q(cx - w), q(cy)),
        PathCommand.QuadTo(q(cx), q(cy + depth), q(cx + w), q(cy)),
    )

    /** A closed Catmull-Rom spline through radii sampled around an ellipse. */
    public fun blobPath(cx: Double, cy: Double, rx: Double, ry: Double, radii: List<Double>, rot: Double = 0.0): List<PathCommand> {
        val n = radii.size
        val t0 = (rot * PI) / 180
        val p = radii.mapIndexed { i, m ->
            val a = t0 + (2 * PI * i) / n
            doubleArrayOf(cx + rx * m * JsMath.cos(a), cy + ry * m * JsMath.sin(a))
        }
        fun at(i: Int) = p[((i % n) + n) % n]
        val out = ArrayList<PathCommand>(n + 2)
        out += PathCommand.MoveTo(q(at(0)[0]), q(at(0)[1]))
        for (i in 0 until n) {
            val (x0, y0) = at(i - 1).let { it[0] to it[1] }
            val (x1, y1) = at(i).let { it[0] to it[1] }
            val (x2, y2) = at(i + 1).let { it[0] to it[1] }
            val (x3, y3) = at(i + 2).let { it[0] to it[1] }
            out += PathCommand.CubicTo(
                q(x1 + (x2 - x0) / 6), q(y1 + (y2 - y0) / 6),
                q(x2 - (x3 - x1) / 6), q(y2 - (y3 - y1) / 6),
                q(x2), q(y2),
            )
        }
        out += PathCommand.Close
        return out
    }

    /** A regular polygon with corners cut back by [round] and joined by quadratics. */
    public fun polygon(cx: Double, cy: Double, rx: Double, ry: Double, sides: Int, round: Double = 0.3, rot: Double = 0.0): List<PathCommand> {
        val k = if (round > 0) (if (round < 1) round / 2 else 0.5) else 0.0
        val t0 = (rot * PI) / 180 - PI / 2
        val v = List(sides) { i ->
            val a = t0 + (2 * PI * i) / sides
            doubleArrayOf(cx + rx * JsMath.cos(a), cy + ry * JsMath.sin(a))
        }
        fun at(i: Int) = v[((i % sides) + sides) % sides]
        fun cut(i: Int, j: Int): Pair<Double, Double> {
            val a = at(i)
            val b = at(j)
            return q(a[0] + (b[0] - a[0]) * k) to q(a[1] + (b[1] - a[1]) * k)
        }
        val out = ArrayList<PathCommand>()
        val s = cut(0, -1)
        out += PathCommand.MoveTo(s.first, s.second)
        for (i in 0 until sides) {
            val p = at(i)
            val c = cut(i, i + 1)
            out += PathCommand.QuadTo(q(p[0]), q(p[1]), c.first, c.second)
            if (k < 0.5) {
                val l = cut(i + 1, i)
                out += PathCommand.LineTo(l.first, l.second)
            }
        }
        out += PathCommand.Close
        return out
    }

    /** A plain box: the straight run of a capsule. */
    public fun box(cx: Double, cy: Double, rx: Double, ry: Double): List<PathCommand> {
        val l = q(cx - rx)
        val r = q(cx + rx)
        return listOf(
            PathCommand.MoveTo(l, q(cy - ry)),
            PathCommand.HorizontalTo(r),
            PathCommand.VerticalTo(q(cy + ry)),
            PathCommand.HorizontalTo(l),
            PathCommand.Close,
        )
    }

    /** The tapered point of a droplet: tangents from an apex to the body ellipse. */
    public fun taper(cx: Double, cy: Double, rx: Double, ry: Double, tip: Double): List<PathCommand> {
        val t = max(1.05, tip)
        val tx = rx * sqrt(1 - 1 / (t * t))
        val ty = cy - ry / t
        val apex = cy - t * ry
        val px = tx * 0.14
        val py = ty + 0.86 * (apex - ty)
        return listOf(
            PathCommand.MoveTo(q(cx - tx), q(ty)),
            PathCommand.LineTo(q(cx - px), q(py)),
            PathCommand.QuadTo(q(cx), q(apex), q(cx + px), q(py)),
            PathCommand.LineTo(q(cx + tx), q(ty)),
            PathCommand.Close,
        )
    }
}
