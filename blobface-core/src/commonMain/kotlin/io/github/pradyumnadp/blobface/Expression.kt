package io.github.pradyumnadp.blobface

import io.github.pradyumnadp.blobface.internal.r3

/**
 * A pose: the channels an expression may move. Scales are factors, `tilt` is
 * degrees, offsets are viewBox units, and `heat`/`shake`/`rock` are 0–1 amounts.
 * The `*2` channels are the right eye's differential.
 */
public data class Pose(
    /** Eye width factor. */
    public val esx: Double = 1.0,
    /** Eye height factor. */
    public val esy: Double = 1.0,
    /** Eye tilt in degrees, mirrored per side. */
    public val tilt: Double = 0.0,
    /** Eye pair vertical offset, positive = down. */
    public val edy: Double = 0.0,
    /** Eye convergence, positive = apart. */
    public val edx: Double = 0.0,
    /** Extra width on the right eye. */
    public val esx2: Double = 0.0,
    /** Extra height on the right eye. */
    public val esy2: Double = 0.0,
    /** Extra tilt on the right eye, before mirroring. */
    public val tilt2: Double = 0.0,
    /** Extra vertical offset on the right eye. */
    public val edy2: Double = 0.0,
    /** How much of the seeded eye lean the pose overrides, 0–1. */
    public val lock: Double = 0.0,
    /** How far the palette shifts toward its tint, 0–1. */
    public val heat: Double = 0.0,
    /** Tremor amplitude, 0–1. */
    public val shake: Double = 0.0,
    /** Seesaw amplitude, 0–1. */
    public val rock: Double = 0.0,
    /** Whole-creature vertical offset, positive = down. */
    public val bdy: Double = 0.0,
) {
    internal fun channels(): List<Pair<String, Double>> = listOf(
        "esx" to esx, "esy" to esy, "tilt" to tilt, "edy" to edy, "edx" to edx,
        "esx2" to esx2, "esy2" to esy2, "tilt2" to tilt2, "edy2" to edy2,
        "lock" to lock, "heat" to heat, "shake" to shake, "rock" to rock, "bdy" to bdy,
    )

    /** Pose helpers. */
    public companion object {
        /** The identity pose: what `idle` holds. */
        public val IDENT: Pose = Pose()

        /**
         * A pose part-way between [a] and [b] (`null` = idle), written
         * `from·(1 − t) + to·t` so that `t = 1` returns [b] exactly.
         */
        public fun lerp(a: Pose?, b: Pose?, t: Double): Pose {
            val f = a ?: IDENT
            val o = b ?: IDENT
            fun l(x: Double, y: Double) = x * (1 - t) + y * t
            return Pose(
                l(f.esx, o.esx), l(f.esy, o.esy), l(f.tilt, o.tilt), l(f.edy, o.edy), l(f.edx, o.edx),
                l(f.esx2, o.esx2), l(f.esy2, o.esy2), l(f.tilt2, o.tilt2), l(f.edy2, o.edy2),
                l(f.lock, o.lock), l(f.heat, o.heat), l(f.shake, o.shake), l(f.rock, o.rock), l(f.bdy, o.bdy),
            )
        }
    }
}

/**
 * A named pose the face holds until you change it. Pass one as
 * [BlobfaceOptions.expression]; [Idle] is the same as passing none.
 */
public class BlobfaceExpression private constructor(
    /** The upstream name, e.g. `"happy"`. */
    public val name: String,
    /** The pose channels. */
    public val pose: Pose,
    /** The color target this expression tints toward, if any. */
    public val tint: Tint? = null,
) {
    /** True when this expression moves or tints anything (the `.mo-expr` predicate). */
    public val isExpressive: Boolean get() = poseVars(pose).isNotEmpty() || tint != null

    override fun toString(): String = "BlobfaceExpression($name)"

    /** The fifteen poses of the upstream roster. */
    public companion object {
        /** No expression. */
        public val Idle: BlobfaceExpression = BlobfaceExpression("idle", Pose.IDENT)

        /** Wide flat arcs riding high. */
        public val Happy: BlobfaceExpression = BlobfaceExpression(
            "happy",
            Pose(1.72, 0.3, 8.0, -1.5, 1.5, 0.08, 0.05, -16.0, 0.0, 1.0, 0.0, 0.0, 0.0, -2.2),
        )

        /** Small eyes, low and drifted apart. */
        public val Sad: BlobfaceExpression = BlobfaceExpression(
            "sad",
            Pose(0.6, 0.56, 26.0, 3.6, 1.9, -0.05, -0.07, -7.0, 0.0, 1.0, 0.0, 0.0, 0.0, 2.6),
        )

        /** A hard `\ /` of flat bars over a body that runs hot and trembles. */
        public val Mad: BlobfaceExpression = BlobfaceExpression(
            "mad",
            Pose(1.85, 0.26, -33.0, 0.4, 0.6, 0.0, -0.03, 5.0, 0.0, 1.0, 0.62, 0.55, 0.0, 0.8),
            Tint.HOT,
        )

        /** Eyes enlarged. */
        public val Surprised: BlobfaceExpression = BlobfaceExpression(
            "surprised",
            Pose(1.34, 1.2, -6.0, -1.05, 0.5, 0.05, 0.07, 3.0, 0.0, 1.0, 0.0, 0.0, 0.0, -1.4),
        )

        /** One eye closed. */
        public val Wink: BlobfaceExpression = BlobfaceExpression(
            "wink",
            Pose(1.32, 0.76, 5.0, -0.6, 0.8, 0.26, -0.56, -11.0, 0.0, 1.0, 0.0, 0.0, 0.0, -1.1),
        )

        /** Level flat bars, low. */
        public val Sleepy: BlobfaceExpression = BlobfaceExpression(
            "sleepy",
            Pose(1.14, 0.22, 0.0, 2.4, 0.3, -0.04, 0.03, 4.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.2),
        )

        /** Half-lidded, tilted in parallel. */
        public val Smug: BlobfaceExpression = BlobfaceExpression(
            "smug",
            Pose(1.3, 0.42, 18.0, -0.5, 0.5, 0.06, -0.06, -36.0, 0.0, 1.0, 0.0, 0.0, 0.0, -1.0),
        )

        /** One eye narrowed. */
        public val Unsure: BlobfaceExpression = BlobfaceExpression(
            "unsure",
            Pose(0.95, 1.02, 4.0, -0.2, 0.3, 0.24, -0.44, -18.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0),
        )

        /** Small eyes held high and together, trembling. */
        public val Scared: BlobfaceExpression = BlobfaceExpression(
            "scared",
            Pose(0.78, 0.96, -12.0, -1.5, -0.8, -0.04, 0.05, 4.0, 0.0, 1.0, 0.0, 0.35, 0.0, -0.6),
        )

        /** Tall narrow eyes drawn together, rose-tinted. */
        public val Love: BlobfaceExpression = BlobfaceExpression(
            "love",
            Pose(0.86, 1.28, -14.0, -0.5, -0.35, 0.05, 0.06, 6.0, 0.0, 1.0, 0.6, 0.0, 0.0, -1.6),
            Tint.ROSE,
        )

        /** Small squeezed eyes, blushing. */
        public val Shy: BlobfaceExpression = BlobfaceExpression(
            "shy",
            Pose(0.62, 0.5, 10.0, 1.4, -0.2, -0.05, -0.04, -8.0, 0.0, 1.0, 0.55, 0.0, 0.0, 0.9),
            Tint.BLUSH,
        )

        /** Slumped bars, green and queasy. */
        public val Sick: BlobfaceExpression = BlobfaceExpression(
            "sick",
            Pose(1.25, 0.34, 20.0, 1.8, 0.8, 0.05, -0.05, -6.0, 0.0, 1.0, 0.6, 0.18, 0.0, 1.4),
            Tint.BILE,
        )

        /** Eyes at two heights, trading places. */
        public val Thinking: BlobfaceExpression = BlobfaceExpression(
            "thinking",
            Pose(1.15, 0.62, 0.0, 4.2, 0.4, 0.02, 0.06, 0.0, -8.4, 1.0, 0.0, 0.0, 0.8, -0.4),
        )

        /** Every expression, idle first. */
        public val entries: List<BlobfaceExpression> = listOf(
            Idle, Happy, Sad, Mad, Surprised, Wink, Sleepy, Smug, Unsure, Scared, Love, Shy, Sick, Thinking,
        )

        /** Looks an expression up by its upstream name. */
        public fun byName(name: String): BlobfaceExpression? = entries.firstOrNull { it.name == name }

        /** A custom pose (not part of the upstream roster). */
        public fun custom(name: String, pose: Pose, tint: Tint? = null): BlobfaceExpression =
            BlobfaceExpression(name, pose, tint)
    }
}

/** The pose as CSS custom properties, emitting only channels that differ from identity (never `heat`). */
public fun poseVars(p: Pose): Map<String, String> {
    val out = LinkedHashMap<String, String>()
    val ident = Pose.IDENT.channels()
    p.channels().forEachIndexed { i, (k, v) ->
        if (k == "heat") return@forEachIndexed
        if (v != ident[i].second) out["--mo-$k"] = r3(v)
    }
    return out
}

/** The palette an expression wears: base mixed `heat` of the way toward its tint pair. */
public fun tintPalette(pal: BlobfacePalette, expression: BlobfaceExpression?): BlobfacePalette {
    val t = expression?.tint ?: return pal
    val (head, eye) = tinted(pal.head!!, pal.eye!!, t)
    return pal.copy(
        head = mixHex(pal.head, head, expression.pose.heat),
        eye = mixHex(pal.eye, eye, expression.pose.heat),
    )
}

/** Bakes a static pose into eye geometry. Returns the posed layout and the body offset (`bdy`). */
public fun bakePose(l: BlobfaceLayout, p: Pose): BlobfaceLayout = l.copy(
    eyes = l.eyes.mapIndexed { i, e ->
        val right = i != 0
        e.copy(
            cx = e.cx + p.edx * (if (right) 1 else -1),
            cy = e.cy + p.edy + (if (right) p.edy2 else 0.0),
            rx = e.rx * (p.esx + (if (right) p.esx2 else 0.0)),
            ry = e.ry * (p.esy + (if (right) p.esy2 else 0.0)),
            rot = e.rot * (1 - p.lock) + (p.tilt + (if (right) p.tilt2 else 0.0)) * (if (right) 1 else -1),
        )
    },
)

/** The SVG wrapper transform for a pose's body offset, or `""` when there is none. */
public fun poseWrap(p: Pose): String = if (p.bdy != 0.0) "translate(0 ${r3(p.bdy)})" else ""

/**
 * One eye's morph transform as numbers, for renderers that animate between poses
 * without re-baking geometry. Apply in order:
 * `translate(tx, ty) rotate(rotate) scale(sx, sy) rotate(-lean) translate(-cx, -cy)`.
 */
public data class EyeTransform(
    /** Posed center x. */
    public val tx: Double,
    /** Posed center y. */
    public val ty: Double,
    /** Outer rotation in degrees. */
    public val rotate: Double,
    /** Horizontal scale in the eye's own frame. */
    public val sx: Double,
    /** Vertical scale in the eye's own frame. */
    public val sy: Double,
    /** The drawn lean, in degrees. */
    public val lean: Double,
    /** Drawn center x. */
    public val cx: Double,
    /** Drawn center y. */
    public val cy: Double,
) {
    /** The SVG transform list, byte-identical to upstream `poseTransforms`. */
    public fun toSvg(): String =
        "translate(${r3(tx)} ${r3(ty)}) rotate(${r3(rotate)}) scale(${r3(sx)} ${r3(sy)})" +
            " rotate(${r3(-lean)}) translate(${r3(-cx)} ${r3(-cy)})"
}

/** The same composition as [bakePose], as one transform per eye. [rockp] is the seesaw phase. */
public fun poseTransforms(l: BlobfaceLayout, p: Pose, rockp: Double = 1.0): List<EyeTransform> =
    l.eyes.mapIndexed { i, e ->
        val wrap = if (i != 0) 1.0 else -1.0
        val sel = if (i != 0) 1.0 else 0.0
        val lean = e.rot
        val ph = sel * (1 - p.rock) + p.rock * ((1 + wrap * rockp) / 2)
        EyeTransform(
            tx = e.cx + p.edx * wrap,
            ty = e.cy + p.edy + ph * p.edy2,
            rotate = (p.tilt + sel * p.tilt2) * wrap + lean * (1 - p.lock),
            sx = p.esx + sel * p.esx2,
            sy = p.esy + sel * p.esy2,
            lean = lean,
            cx = e.cx,
            cy = e.cy,
        )
    }
