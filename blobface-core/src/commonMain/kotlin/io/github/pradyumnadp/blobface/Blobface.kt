package io.github.pradyumnadp.blobface

import io.github.pradyumnadp.blobface.internal.isJsWhitespace
import io.github.pradyumnadp.blobface.internal.r2
import io.github.pradyumnadp.blobface.internal.r2n

/** The plate drawn behind the figure. */
public enum class BlobfaceBackground {
    /** Transparent (the default). */
    None,

    /** A 100×100 square. */
    Square,

    /** A circle (superellipse n = 2). */
    Circle,

    /** A squircle (superellipse n = 6). Upstream's `background: true` means this. */
    Squircle,
}

/**
 * Every option upstream `blobatar(name, opts)` accepts.
 *
 * Note: [size] is an `Int` here; upstream accepts any number and prints it into
 * `width`/`height`.
 */
public data class BlobfaceOptions(
    /** Emits `width`/`height` on the SVG when non-null and non-zero. */
    public val size: Int? = null,
    /** Backdrop plate. Default [BlobfaceBackground.None]. */
    public val background: BlobfaceBackground = BlobfaceBackground.None,
    /** Per-key hex overrides. Bypasses the contrast guarantee. */
    public val palette: BlobfacePalette? = null,
    /** Locks the hue in degrees, so the name drives shape only. */
    public val hue: Double? = null,
    /**
     * Locks the tone as a 0–1 position in the swatch set, pale to ink. Bands are
     * half-open, so exactly `1.0` renders like `0.0`; use `0.999` for ink.
     */
    public val tone: Double? = null,
    /**
     * Pins individual traits as 0–1 positions (or a list to choose among), keyed
     * exactly as the layout reads them, e.g. `mapOf("shape" to TraitOverride.of(0.95))`.
     * [hue] and [tone] take precedence over `traits["hue"]`/`traits["tone"]`.
     */
    public val traits: Map<String, TraitOverride> = emptyMap(),
    /** Applies NFC + trim + lowercase to the name. Default true. */
    public val normalize: Boolean = true,
    /** Enforces the minimum contrast ratios. Default true. */
    public val contrast: Boolean = true,
    /** Adds a `<title>` for screen readers (and a content description in Compose). */
    public val title: String? = null,
    /** The pose the face holds. Default (null) is idle. */
    public val expression: BlobfaceExpression? = null,
)

/** One drawn primitive with its fill, in draw order. */
public sealed interface BlobfaceMark {
    /** The fill color, `#rrggbb`. */
    public val fill: String

    /** A path. */
    public data class Path(public val commands: List<PathCommand>, override val fill: String) : BlobfaceMark {
        /** The path's SVG `d` attribute. */
        public val d: String get() = commands.toSvgPathData()
    }

    /** A circle, with coordinates rounded to two decimals as emitted. */
    public data class Circle(public val cx: Double, public val cy: Double, public val r: Double, override val fill: String) : BlobfaceMark
}

/** The backdrop plate as geometry. */
public data class Backdrop(public val commands: List<PathCommand>, public val fill: String) {
    /** SVG path data. */
    public val d: String get() = commands.toSvgPathData()
}

/**
 * The pieces a renderer that owns its outer `<svg>` needs when animating: the
 * root `<g>` class, the motion custom properties, the backdrop, and the inner
 * markup (which never varies with the expression).
 */
public data class AnimatedSvgParts(
    /** Root `<g>` class, e.g. `mo-root mo-expr`. */
    public val cls: String,
    /** Backdrop, drawn outside the root group. */
    public val background: Backdrop?,
    /** Everything below the root `<g>`. */
    public val inner: String,
    /** Motion custom properties for the `<svg>` style. */
    public val vars: Map<String, String>,
)

/**
 * A resolved blobface: the deterministic face for one name and set of options.
 *
 * ```kotlin
 * val face = Blobface("alain@example.com")
 * face.shape        // BlobfaceShape.Organic
 * face.toSvgString() // "<svg xmlns=… viewBox=\"0 0 100 100\">…</svg>"
 * ```
 */
public class Blobface(
    /** The name the face was generated from. */
    public val name: String,
    /** The options it was generated with. */
    public val options: BlobfaceOptions = BlobfaceOptions(),
) {
    /** The trait reader for this name and its overrides. */
    public val traits: BlobfaceTraits = BlobfaceTraits.of(name, options.normalize, options.traits)

    /** The palette before any expression tint (overrides applied). */
    public val basePalette: BlobfacePalette = palette(
        options.hue ?: traits.num("hue", 0.0, 360.0),
        options.contrast,
        options.tone ?: traits("tone"),
    ).overriddenBy(options.palette)

    /** The palette actually painted (tinted by the expression, if it tints). */
    public val palette: BlobfacePalette = tintPalette(basePalette, options.expression)

    /** The unposed layout: the figure as drawn before any expression. */
    public val baseLayout: BlobfaceLayout = computeLayout(traits)

    /** The layout as drawn statically (expression baked into the eyes). */
    public val layout: BlobfaceLayout =
        options.expression?.let { bakePose(baseLayout, it.pose) } ?: baseLayout

    /** The silhouette. */
    public val shape: BlobfaceShape get() = baseLayout.shape

    /** The idle animation timings for this name. */
    public val idleSeeds: IdleSeeds get() = motionSeeds(traits)

    /** The SVG transform wrapping the figure for the expression's `bdy`, or `""`. */
    public val bodyTransform: String get() = options.expression?.let { poseWrap(it.pose) } ?: ""

    /** The backdrop plate, or null when transparent. */
    public val background: Backdrop? get() = backdropFor(options.background, palette)

    /** The static figure as drawing primitives, in draw order (upstream `_marks`). */
    public val marks: List<BlobfaceMark> get() = marksOf(layout, palette)

    /** The static figure as SVG markup, byte-identical to upstream `blobatar(name, opts)`. */
    public fun toSvgString(): String {
        val dim = options.size?.takeIf { it != 0 }?.let { " width=\"$it\" height=\"$it\"" } ?: ""
        val label = options.title?.takeIf { it.isNotEmpty() }?.let { "<title>${escape(it)}</title>" } ?: ""
        val plate = background?.let { "<path d=\"${it.d}\" fill=\"${it.fill}\"/>" } ?: ""
        val body = wrap(render(layout, palette, mo = false), bodyTransform)
        return "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 100 100\"$dim>$label$plate$body</svg>"
    }

    /** A `data:` URI for the static SVG, identical to upstream `blobatarUri`. */
    public fun toDataUri(): String {
        val svg = toSvgString().replace('"', '\'')
        val sb = StringBuilder("data:image/svg+xml,")
        var inSpace = false
        for (c in svg) {
            // JS `\s` is the same set as String.prototype.trim's whitespace.
            if (isJsWhitespace(c)) {
                if (!inSpace) sb.append(' ')
                inSpace = true
                continue
            }
            inSpace = false
            if (c in "%#<>{}|\\^[]`") sb.append('%').append(c.code.toString(16).uppercase()) else sb.append(c)
        }
        return sb.toString()
    }

    /**
     * The animated rendering split for a renderer that owns the outer element
     * (upstream `_parts` with `animate`). Requires upstream's `motion.css` to animate.
     */
    public fun toAnimatedSvgParts(animate: BlobfaceAnimate): AnimatedSvgParts {
        val e = options.expression
        val pose = e?.pose
        val c = tintPalette(basePalette, e)
        val vars = LinkedHashMap<String, String>()
        vars.putAll(motionVars(traits))
        vars["--mo-head"] = c.head!!
        vars["--mo-eye"] = c.eye!!
        if (pose != null) vars.putAll(poseVars(pose))
        return AnimatedSvgParts(
            cls = rootClass(animate, e != null && (poseVars(e.pose).isNotEmpty() || e.tint != null)),
            background = backdropFor(options.background, basePalette),
            inner = render(baseLayout, basePalette, mo = true),
            vars = vars,
        )
    }

    override fun toString(): String = "Blobface(name=$name, shape=${shape.id})"

    /** Convenience entry points. */
    public companion object {
        /** `Blobface(name, options).toSvgString()`. */
        public fun svg(name: String, options: BlobfaceOptions = BlobfaceOptions()): String =
            Blobface(name, options).toSvgString()

        /** `Blobface(name, options).toDataUri()`. */
        public fun dataUri(name: String, options: BlobfaceOptions = BlobfaceOptions()): String =
            Blobface(name, options).toDataUri()
    }
}

private fun escape(s: String): String = buildString {
    for (c in s) when (c) {
        '&' -> append("&amp;")
        '<' -> append("&lt;")
        '>' -> append("&gt;")
        else -> append(c)
    }
}

private fun wrap(body: String, t: String) = if (t.isNotEmpty()) "<g transform=\"$t\">$body</g>" else body

internal fun backdropFor(bg: BlobfaceBackground, p: BlobfacePalette): Backdrop? = when (bg) {
    BlobfaceBackground.None -> null
    BlobfaceBackground.Square -> Backdrop(
        listOf(
            PathCommand.MoveTo(0.0, 0.0), PathCommand.HorizontalTo(100.0),
            PathCommand.VerticalTo(100.0), PathCommand.HorizontalTo(0.0), PathCommand.Close,
        ),
        p.bg!!,
    )
    BlobfaceBackground.Circle -> Backdrop(Primitives.superellipse(50.0, 50.0, 50.0, 50.0, 2.0), p.bg!!)
    BlobfaceBackground.Squircle -> Backdrop(Primitives.superellipse(50.0, 50.0, 50.0, 50.0, 6.0), p.bg!!)
}

internal fun eyePath(e: Eye): List<PathCommand> = Primitives.superellipse(e.cx, e.cy, e.rx, e.ry, e.n, e.rot)

internal fun marksOf(l: BlobfaceLayout, p: BlobfacePalette): List<BlobfaceMark> {
    val head = p.head!!
    return buildList {
        for (d in l.petals) add(BlobfaceMark.Circle(r2n(d.cx), r2n(d.cy), r2n(d.r), head))
        for (d in l.extra) add(BlobfaceMark.Path(d, head))
        add(BlobfaceMark.Path(l.bodyPath, head))
        for (e in l.eyes) add(BlobfaceMark.Path(eyePath(e), p.eye!!))
    }
}

/** Mirrors `compose().render` in upstream `styles/compose.ts`. */
internal fun render(l: BlobfaceLayout, p: BlobfacePalette, mo: Boolean): String {
    val sb = StringBuilder()
    sb.append("<g fill=\"").append(p.head).append("\">")
    for (d in l.petals) sb.append("<circle cx=\"${r2(d.cx)}\" cy=\"${r2(d.cy)}\" r=\"${r2(d.r)}\"/>")
    for (d in l.extra) sb.append("<path d=\"${d.toSvgPathData()}\"/>")
    sb.append("<path d=\"${l.bodyPath.toSvgPathData()}\"/>")
    sb.append("</g>")
    sb.append("<g fill=\"").append(p.eye).append('"').append(if (mo) " class=\"mo-eyes\"" else "").append('>')
    l.eyes.forEachIndexed { i, e ->
        val path = "<path d=\"${eyePath(e).toSvgPathData()}\"/>"
        if (mo) {
            sb.append("<g class=\"mo-eye\" style=\"--mo-wrap:${if (i != 0) 1 else -1};--mo-lean:${r2(e.rot)};")
                .append("transform-origin:${r2(e.cx)}px ${r2(e.cy)}px\">").append(path).append("</g>")
        } else {
            sb.append(path)
        }
    }
    sb.append("</g>")
    val body = sb.toString()
    return if (mo) "<g class=\"mo-breathe\"><g class=\"mo-bob\">$body</g></g>" else body
}
