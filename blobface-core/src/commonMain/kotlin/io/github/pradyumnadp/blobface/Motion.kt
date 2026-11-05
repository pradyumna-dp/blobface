package io.github.pradyumnadp.blobface

import io.github.pradyumnadp.blobface.internal.jsInt
import io.github.pradyumnadp.blobface.internal.jsRound
import io.github.pradyumnadp.blobface.internal.r2
import io.github.pradyumnadp.blobface.internal.r2n
import io.github.pradyumnadp.blobface.internal.r3
import kotlin.math.abs
import kotlin.math.floor

/** Idle animation mode, as upstream's `animate` option. */
public enum class BlobfaceAnimate {
    /** Animate while hovered (pointer devices) or pressed (touch). */
    Hover,

    /** Animate continuously. */
    Always,
}

/** The CSS root class for an animated blobface: `mo-root[ mo-always][ mo-expr]`. */
public fun rootClass(mode: BlobfaceAnimate, expressive: Boolean = false): String =
    "mo-root" + (if (mode == BlobfaceAnimate.Always) " mo-always" else "") + (if (expressive) " mo-expr" else "")

/** Per-name idle timings. Phases are positive ("how far into the cycle"). */
public data class IdleSeeds(
    /** Breathe phase, ms. */
    public val phase: Double,
    /** Bob phase, ms. */
    public val bob: Double,
    /** Blink period, ms. */
    public val blink: Double,
    /** Blink phase, ms. */
    public val blinkPhase: Double,
    /** Saccade period, ms. */
    public val saccade: Double,
    /** Saccade phase, ms. */
    public val saccadePhase: Double,
    /** Signed glance direction x. */
    public val lookX: Double,
    /** Signed glance direction y. */
    public val lookY: Double,
    /** Glance magnitude x. */
    public val lookMX: Double,
    /** Glance magnitude y. */
    public val lookMY: Double,
)

/** Derives the idle timings from a trait reader. */
public fun motionSeeds(t: BlobfaceTraits): IdleSeeds {
    val blink = jsRound(t.num("motion.blink", 3500.0, 6500.0))
    val saccade = jsRound(t.num("motion.saccade", 4200.0, 7600.0))
    val lookX = t.num("motion.lookX", 1.0, 2.2)
    val lookY = t.num("motion.lookY", 0.8, 1.7)
    return IdleSeeds(
        phase = jsRound(t.num("motion.phase", 0.0, 2800.0)),
        bob = jsRound(t.num("motion.bob", 0.0, 3400.0)),
        blink = blink,
        blinkPhase = jsRound(t.num("motion.blinkPhase", 0.0, blink)),
        saccade = saccade,
        saccadePhase = jsRound(t.num("motion.saccadePhase", 0.0, saccade)),
        lookX = r2n(lookX) * (if (t.bool("motion.lookXFlip")) -1 else 1),
        lookY = r2n(lookY) * (if (t.bool("motion.lookYFlip")) -1 else 1),
        lookMX = r2n(lookX),
        lookMY = r2n(lookY),
    )
}

/** The idle timings as CSS custom properties (delays negated). */
public fun motionVars(t: BlobfaceTraits): Map<String, String> {
    fun ms(v: Double) = jsInt(-v) + "ms"
    val s = motionSeeds(t)
    return linkedMapOf(
        "--mo-phase" to ms(s.phase),
        "--mo-bob-phase" to ms(s.bob),
        "--mo-blink" to jsInt(s.blink) + "ms",
        "--mo-blink-phase" to ms(s.blinkPhase),
        "--mo-look-x" to r2(s.lookX),
        "--mo-look-mx" to r2(s.lookMX),
        "--mo-look-y" to r2(s.lookY),
        "--mo-look-my" to r2(s.lookMY),
        "--mo-saccade" to jsInt(s.saccade) + "ms",
        "--mo-saccade-phase" to ms(s.saccadePhase),
    )
}

/** `--a:1;--b:2`. */
public fun serializeVars(vars: Map<String, String>): String = vars.entries.joinToString(";") { "${it.key}:${it.value}" }

/** CSS `cubic-bezier` timing functions, evaluated with a Newton solve. */
public object Easing {
    /** A `cubic-bezier(x1, y1, x2, y2)` as a function of elapsed fraction. */
    public fun bezier(x1: Double, y1: Double, x2: Double, y2: Double): (Double) -> Double {
        val cx = 3 * x1
        val bx = 3 * (x2 - x1) - cx
        val ax = 1 - cx - bx
        val cy = 3 * y1
        val by = 3 * (y2 - y1) - cy
        val ay = 1 - cy - by
        return { x ->
            var t = x
            for (i in 0 until 8) {
                val err = ((ax * t + bx) * t + cx) * t - x
                if (abs(err) < 1e-5) break
                val d = (3 * ax * t + 2 * bx) * t + cx
                if (abs(d) < 1e-6) break
                t -= err / d
            }
            ((ay * t + by) * t + cy) * t
        }
    }

    /** CSS `ease-in-out`. */
    public val EASE_IN_OUT: (Double) -> Double = bezier(0.42, 0.0, 0.58, 1.0)

    /** CSS `ease-in`. */
    public val EASE_IN: (Double) -> Double = bezier(0.42, 0.0, 1.0, 1.0)

    /** CSS `ease-out`. */
    public val EASE_OUT: (Double) -> Double = bezier(0.0, 0.0, 0.58, 1.0)

    /** Morph into an expression: 300ms. */
    public const val MORPH_IN_MS: Int = 300

    /** Morph back to idle: 400ms. */
    public const val MORPH_OUT_MS: Int = 400

    /** The morph-in curve, `cubic-bezier(0.45, 0.05, 0.5, 1)`. */
    public val MORPH_IN: (Double) -> Double = bezier(0.45, 0.05, 0.5, 1.0)

    /** The morph-out curve, `ease-in-out`. */
    public val MORPH_OUT: (Double) -> Double = EASE_IN_OUT

    /** Hover lift curve, `cubic-bezier(0.23, 1, 0.32, 1)`. */
    public val LIFT: (Double) -> Double = bezier(0.23, 1.0, 0.32, 1.0)
}

/** One frame of the idle layer, as numbers. See upstream `idle.ts`. */
public data class IdleFrame(
    /** Tremor (x, y), viewBox units. */
    public val shakeX: Double,
    /** Tremor y. */
    public val shakeY: Double,
    /** Breathe scale x about (50, 50). */
    public val breatheX: Double,
    /** Breathe scale y about (50, 50). */
    public val breatheY: Double,
    /** Bob offset y (negative = up). */
    public val bob: Double,
    /** Eye-pair glance x. */
    public val saccadeX: Double,
    /** Eye-pair glance y. */
    public val saccadeY: Double,
    /** Seesaw phase, 1 → -1 → 1. */
    public val rockp: Double,
    /** Blink scaleY in each eye's leaned frame. */
    public val blink: Double,
    /** Foreshortening: magnitude-scaled x. */
    public val wrapMx: Double,
    /** Foreshortening: signed side x. */
    public val wrapSide: Double,
    /** Foreshortening: y. */
    public val wrapSy: Double,
    /** Foreshortening rotation. */
    public val wrapRot: Double,
)

private val SACCADE = arrayOf(
    doubleArrayOf(0.0, 0.0, 0.0), doubleArrayOf(0.15, 0.0, 0.0),
    doubleArrayOf(0.165, -0.8, -0.9), doubleArrayOf(0.31, -0.8, -0.9),
    doubleArrayOf(0.325, 1.0, 0.1), doubleArrayOf(0.47, 1.0, 0.1),
    doubleArrayOf(0.485, -0.15, 0.85), doubleArrayOf(0.63, -0.15, 0.85),
    doubleArrayOf(0.645, 0.75, -0.8), doubleArrayOf(0.79, 0.75, -0.8),
    doubleArrayOf(0.805, -1.0, -0.15), doubleArrayOf(0.985, -1.0, -0.15),
    doubleArrayOf(1.0, 0.0, 0.0),
)

private val WRAP = arrayOf(
    doubleArrayOf(0.0, 0.0, 0.0, 0.0, 0.0), doubleArrayOf(0.15, 0.0, 0.0, 0.0, 0.0),
    doubleArrayOf(0.165, -0.0176, 0.008, -0.027, 0.648), doubleArrayOf(0.31, -0.0176, 0.008, -0.027, 0.648),
    doubleArrayOf(0.325, -0.022, -0.01, -0.003, 0.09), doubleArrayOf(0.47, -0.022, -0.01, -0.003, 0.09),
    doubleArrayOf(0.485, -0.0033, 0.0015, -0.0255, -0.115), doubleArrayOf(0.63, -0.0033, 0.0015, -0.0255, -0.115),
    doubleArrayOf(0.645, -0.0165, -0.0075, -0.024, -0.54), doubleArrayOf(0.79, -0.0165, -0.0075, -0.024, -0.54),
    doubleArrayOf(0.805, -0.022, 0.01, -0.0045, 0.135), doubleArrayOf(0.985, -0.022, 0.01, -0.0045, 0.135),
    doubleArrayOf(1.0, 0.0, 0.0, 0.0, 0.0),
)

private val SHAKE = arrayOf(
    doubleArrayOf(0.0, 0.62, -0.34), doubleArrayOf(0.25, -0.7, 0.22), doubleArrayOf(0.5, 0.38, 0.66),
    doubleArrayOf(0.75, -0.44, -0.6), doubleArrayOf(1.0, 0.62, -0.34),
)

/** Breathe period, ms. */
public const val BREATHE_MS: Double = 2800.0

/** Bob period, ms. */
public const val BOB_MS: Double = 3400.0

/** Seesaw period, ms. */
public const val ROCK_MS: Double = 900.0

/** Tremor period, ms. */
public const val SHAKE_MS: Double = 112.0

private fun cycle(t: Double, phase: Double, period: Double): Double {
    val u = (t + phase) / period
    return u - floor(u)
}

private fun alternate(t: Double, phase: Double, period: Double): Double {
    val u = (t + phase) / period
    val n = floor(u)
    val f = u - n
    return if (n % 2 != 0.0) 1 - f else f
}

private fun stops(u: Double, table: Array<DoubleArray>, col: Int): Double {
    for (i in table.indices.reversed()) {
        val row = table[i]
        if (u < row[0]) continue
        val next = table.getOrNull(i + 1) ?: return row[col]
        val span = next[0] - row[0]
        return if (span <= 0) row[col] else row[col] + (next[col] - row[col]) * ((u - row[0]) / span)
    }
    return table[0][col]
}

/**
 * Evaluates every idle loop at time [t] (ms) with amplitude [amp] (0 = still,
 * 1 = fully animated) and tremor [shake].
 */
public fun idleAt(s: IdleSeeds, t: Double, amp: Double, shake: Double = 0.0): IdleFrame {
    val breathe = Easing.EASE_IN_OUT(alternate(t, s.phase, BREATHE_MS))
    val bob = Easing.EASE_IN_OUT(alternate(t, s.bob, BOB_MS))
    val sac = cycle(t, s.saccadePhase, s.saccade)
    val sh = cycle(t, 0.0, SHAKE_MS)
    val r = cycle(t, 0.0, ROCK_MS)
    val rockp = if (r < 0.5) 1 - 2 * Easing.EASE_IN_OUT(r * 2) else -1 + 2 * Easing.EASE_IN_OUT(r * 2 - 1)
    val b = cycle(t, s.blinkPhase, s.blink)
    val blink = when {
        b < 0.972 -> 1.0
        b < 0.986 -> 1 - 0.92 * amp * Easing.EASE_IN((b - 0.972) / 0.014)
        else -> 1 - 0.92 * amp * (1 - Easing.EASE_OUT((b - 0.986) / 0.014))
    }
    return IdleFrame(
        shakeX = stops(sh, SHAKE, 1) * shake,
        shakeY = stops(sh, SHAKE, 2) * shake,
        breatheX = 1 + 0.022 * amp * breathe,
        breatheY = 1 - 0.018 * amp * breathe,
        bob = -1.1 * amp * bob,
        saccadeX = stops(sac, SACCADE, 1) * s.lookX * amp,
        saccadeY = stops(sac, SACCADE, 2) * s.lookY * amp,
        rockp = rockp,
        blink = blink,
        wrapMx = stops(sac, WRAP, 1) * s.lookMX * amp,
        wrapSide = stops(sac, WRAP, 2) * s.lookX * amp,
        wrapSy = stops(sac, WRAP, 3) * s.lookMY * amp,
        wrapRot = stops(sac, WRAP, 4) * s.lookX * s.lookY * amp,
    )
}

/** The idle frame as SVG transform strings, byte-identical to upstream `idleTransforms`. */
public data class IdleTransforms(
    /** Root (tremor). */
    public val root: String,
    /** Breathe group. */
    public val breathe: String,
    /** Bob group (includes `bdy`). */
    public val bob: String,
    /** Eye-pair group (glance). */
    public val eyes: String,
    /** Per-eye pose transform. */
    public val eye: List<String>,
    /** Per-eye foreshortening + blink. */
    public val glance: List<String>,
)

/** Composes [idleAt] with a pose, as SVG transform strings. */
public fun idleTransforms(l: BlobfaceLayout, p: Pose, f: IdleFrame): IdleTransforms = IdleTransforms(
    root = "translate(${r3(f.shakeX)} ${r3(f.shakeY)})",
    breathe = "translate(50 50) scale(${r3(f.breatheX)} ${r3(f.breatheY)}) translate(-50 -50)",
    bob = "translate(0 ${r3(p.bdy + f.bob)})",
    eyes = "translate(${r3(f.saccadeX)} ${r3(f.saccadeY)})",
    eye = poseTransforms(l, p, f.rockp).map { it.toSvg() },
    glance = l.eyes.mapIndexed { i, e ->
        val side = if (i != 0) 1 else -1
        val lean = e.rot
        "translate(${r3(e.cx)} ${r3(e.cy)})" +
            " rotate(${r3(f.wrapRot * side)})" +
            " scale(${r3(1 + f.wrapMx + f.wrapSide * side)} ${r3(1 + f.wrapSy)})" +
            " rotate(${r3(lean)})" +
            " scale(1 ${r3(f.blink)})" +
            " rotate(${r3(-lean)})" +
            " translate(${r3(-e.cx)} ${r3(-e.cy)})"
    },
)
