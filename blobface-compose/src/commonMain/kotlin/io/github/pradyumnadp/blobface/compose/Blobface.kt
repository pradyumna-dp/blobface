package io.github.pradyumnadp.blobface.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.pradyumnadp.blobface.BlobfaceBackground
import io.github.pradyumnadp.blobface.BlobfaceExpression
import io.github.pradyumnadp.blobface.BlobfaceMark
import io.github.pradyumnadp.blobface.BlobfaceOptions
import io.github.pradyumnadp.blobface.BlobfacePalette
import io.github.pradyumnadp.blobface.Easing
import io.github.pradyumnadp.blobface.GazeMath
import io.github.pradyumnadp.blobface.Pose
import io.github.pradyumnadp.blobface.TraitOverride
import io.github.pradyumnadp.blobface.idleAt
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import io.github.pradyumnadp.blobface.Blobface as BlobfaceModel

/** How a [Blobface] animates. */
public enum class BlobfaceAnimation {
    /** Static: one still drawing. Expressions still apply, without a morph. */
    None,

    /**
     * Idle motion (breathe, bob, blink, glance and a lift) while the face is
     * hovered with a mouse or pressed on a touch screen. Expression changes morph.
     */
    Hover,

    /** Idle motion all the time. For a single prominent face, not a grid. */
    Always,
}

/** Upstream renders without a size; `48.dp` is the size every upstream README example uses. */
public val DefaultBlobfaceSize: Dp = 48.dp

/**
 * A deterministic avatar for [name], drawn natively with Compose paths.
 *
 * The same name and options always produce the same face as blobatar 2.7.0.
 *
 * ```kotlin
 * Blobface("alain@example.com")
 * Blobface(user.email, size = 96.dp, background = BlobfaceBackground.Squircle,
 *          expression = BlobfaceExpression.Happy, animate = BlobfaceAnimation.Hover)
 * ```
 *
 * @param name any string; normalized (NFC, trim, lowercase) unless [normalize] is false.
 * @param size the square size of the face.
 * @param background the plate behind the figure; transparent by default.
 * @param palette per-key hex overrides (bypasses the contrast guarantee).
 * @param hue locks the hue in degrees.
 * @param tone locks the tone as a 0–1 position, pale to ink.
 * @param traits pins individual traits; see [BlobfaceOptions.traits].
 * @param normalize apply name normalization (default true).
 * @param contrast enforce the contrast floors (default true).
 * @param title accessibility label (content description); null leaves it decorative.
 * @param expression the pose the face holds (null = idle).
 * @param animate idle animation mode.
 * @param gaze optional gaze driver; see [rememberBlobfaceGaze].
 */
@Composable
public fun Blobface(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = DefaultBlobfaceSize,
    background: BlobfaceBackground = BlobfaceBackground.None,
    palette: BlobfacePalette? = null,
    hue: Double? = null,
    tone: Double? = null,
    traits: Map<String, TraitOverride> = emptyMap(),
    normalize: Boolean = true,
    contrast: Boolean = true,
    title: String? = null,
    expression: BlobfaceExpression? = null,
    animate: BlobfaceAnimation = BlobfaceAnimation.None,
    gaze: BlobfaceGazeState? = null,
) {
    val options = BlobfaceOptions(
        background = background, palette = palette, hue = hue, tone = tone, traits = traits,
        normalize = normalize, contrast = contrast, title = title, expression = expression,
    )
    Blobface(name, options, modifier, size, animate, gaze)
}

/** [Blobface] taking a ready-made [BlobfaceOptions] (its `size` is ignored; use [size]). */
@Composable
public fun Blobface(
    name: String,
    options: BlobfaceOptions,
    modifier: Modifier = Modifier,
    size: Dp = DefaultBlobfaceSize,
    animate: BlobfaceAnimation = BlobfaceAnimation.None,
    gaze: BlobfaceGazeState? = null,
) {
    val sem = options.title?.takeIf { it.isNotEmpty() }?.let { t -> Modifier.semantics { contentDescription = t } } ?: Modifier
    val base = modifier.size(size).then(sem)
    val reduced = rememberReducedMotion()
    if (animate == BlobfaceAnimation.None && gaze == null) {
        val face = remember(name, options) { StaticFace(BlobfaceModel(name, options)) }
        Canvas(base) { face.draw(this) }
    } else {
        AnimatedBlobface(name, options, base, animate, gaze, reduced)
    }
}

// ---------------------------------------------------------------------------
// Static

private class StaticFace(model: BlobfaceModel) {
    val backdrop = model.background?.let { it.commands.toComposePath() to hexColor(it.fill) }
    val bdy = model.options.expression?.pose?.bdy ?: 0.0
    val marks = model.marks.map { m ->
        when (m) {
            is BlobfaceMark.Path -> DrawMark.P(m.commands.toComposePath(), hexColor(m.fill))
            is BlobfaceMark.Circle -> DrawMark.C(Offset(m.cx.toFloat(), m.cy.toFloat()), m.r.toFloat(), hexColor(m.fill))
        }
    }

    fun draw(scope: DrawScope) = with(scope) {
        val k = min(size.width, size.height) / 100f
        scale(k, k, pivot = Offset.Zero) {
            backdrop?.let { (p, c) -> drawPath(p, c) }
            translate(0f, bdy.toFloat()) { for (m in marks) m.draw(this) }
        }
    }
}

private sealed interface DrawMark {
    fun draw(s: DrawScope)
    class P(val path: Path, val color: Color) : DrawMark {
        override fun draw(s: DrawScope) = s.drawPath(path, color)
    }
    class C(val center: Offset, val r: Float, val color: Color) : DrawMark {
        override fun draw(s: DrawScope) = s.drawCircle(color, r, center)
    }
}

// ---------------------------------------------------------------------------
// Animated: the motion.css layer stack, evaluated per frame.

private class AnimatedGeometry(model: BlobfaceModel) {
    val layout = model.baseLayout
    val seeds = model.idleSeeds
    val backdrop = model.background?.let { it.commands.toComposePath() }
    val bg = model.basePalette.bg?.let(::hexColor) ?: Color.Transparent
    val petals = layout.petals.map {
        Triple(Offset(r2f(it.cx), r2f(it.cy)), r2f(it.r), Unit)
    }
    val extra = layout.extra.map { it.toComposePath() }
    val body = layout.bodyPath.toComposePath()
    val eyes = layout.eyes.map { e ->
        EyeGeo(
            path = io.github.pradyumnadp.blobface.Primitives.superellipse(e.cx, e.cy, e.rx, e.ry, e.n, e.rot).toComposePath(),
            cx = r2f(e.cx), cy = r2f(e.cy), lean = r2f(e.rot),
        )
    }
    val face: GazeMath.Face = GazeMath.survey(layout)
}

private class EyeGeo(val path: Path, val cx: Float, val cy: Float, val lean: Float)

/**
 * A running pose/fill transition. CSS takes a transition's duration and curve
 * from the state it is heading to: 300ms on the morph-in curve when adopting an
 * expression, 400ms ease-in-out when returning to idle.
 */
private class Morph(var from: Pose, var to: Pose, var fromHead: Color, var fromEye: Color, var toHead: Color, var toEye: Color) {
    var intoExpression: Boolean = false

    fun ease(t: Float): Double =
        if (t >= 1f) 1.0 else (if (intoExpression) MORPH_IN_EASING else MORPH_OUT_EASING).transform(t).toDouble()
}

private val LIFT_EASING = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
private val AMP_EASING = CubicBezierEasing(0f, 0f, 0.58f, 1f) // ease-out
private val MORPH_IN_EASING = CubicBezierEasing(0.45f, 0.05f, 0.5f, 1f)
private val MORPH_OUT_EASING = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

@Composable
private fun AnimatedBlobface(
    name: String,
    options: BlobfaceOptions,
    modifier: Modifier,
    animate: BlobfaceAnimation,
    gaze: BlobfaceGazeState?,
    reduced: Boolean,
) {
    // Geometry depends on everything but the expression: the expression morphs.
    val geoKey = options.copy(expression = null)
    val geo = remember(name, geoKey) { AnimatedGeometry(BlobfaceModel(name, geoKey)) }
    val target = remember(name, options) { BlobfaceModel(name, options) }
    val expr = options.expression
    val targetPose = expr?.pose ?: Pose.IDENT
    val targetHead = hexColor(target.palette.head ?: "#000000")
    val targetEye = hexColor(target.palette.eye ?: "#000000")
    val expressive = expr?.isExpressive == true

    var hovered by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }
    var sawMouse by remember { mutableStateOf(false) }
    var localPointer by remember { mutableStateOf<Offset?>(null) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    var widthPx by remember { mutableFloatStateOf(1f) }
    val active = !reduced && (animate == BlobfaceAnimation.Always || hovered || pressed)
    val lifted = !reduced && animate != BlobfaceAnimation.None && (hovered || pressed)

    val amp = remember { Animatable(if (active) 1f else 0f) }
    val lift = remember { Animatable(0f) }
    val morphT = remember { Animatable(1f) }
    val morph = remember { Morph(targetPose, targetPose, targetHead, targetEye, targetHead, targetEye) }
    var time by remember { mutableLongStateOf(0L) }
    var clockStart by remember { mutableLongStateOf(-1L) }

    // Gaze driver state.
    val g = remember { GazeRuntime() }
    val sharedPointer = LocalBlobfacePointer.current

    LaunchedEffect(active) {
        amp.animateTo(if (active) 1f else 0f, tween(if (reduced) 0 else 400, easing = AMP_EASING))
    }
    LaunchedEffect(lifted) {
        lift.animateTo(if (lifted) 1f else 0f, tween(if (lifted) 220 else 160, easing = LIFT_EASING))
    }
    LaunchedEffect(targetPose, targetHead, targetEye) {
        if (morph.to == targetPose && morph.toHead == targetHead && morph.toEye == targetEye) return@LaunchedEffect
        val ease = morph.ease(morphT.value)
        morph.from = Pose.lerp(morph.from, morph.to, ease)
        morph.fromHead = lerpSrgb(morph.fromHead, morph.toHead, ease.toFloat())
        morph.fromEye = lerpSrgb(morph.fromEye, morph.toEye, ease.toFloat())
        morph.to = targetPose
        morph.toHead = targetHead
        morph.toEye = targetEye
        morph.intoExpression = expressive
        if (reduced) {
            morphT.snapTo(1f)
        } else {
            morphT.snapTo(0f)
            morphT.animateTo(1f, tween(if (expressive) Easing.MORPH_IN_MS else Easing.MORPH_OUT_MS, easing = androidx.compose.animation.core.LinearEasing))
        }
    }

    val gazeState = rememberUpdatedState(gaze)
    // The frame clock runs only while something can move, so a grid of idle faces costs nothing.
    LaunchedEffect(reduced) {
        if (reduced) return@LaunchedEffect
        snapshotFlow {
            amp.value > 0f || lift.isRunning || morphT.isRunning ||
                morph.to.shake != 0.0 || morph.to.rock != 0.0 || morph.from.shake != 0.0 || morph.from.rock != 0.0 ||
                gazeState.value != null
        }.collect { needed ->
            while (needed) {
                withFrameMillis { now ->
                    if (clockStart < 0) clockStart = now
                    time = now - clockStart
                }
                val gs = gazeState.value
                if (gs != null) {
                    g.update(gs, geo.face, origin, widthPx, sharedPointer?.position ?: localPointer, pressed)
                } else if (g.h != 0.0 || g.x != 0.0 || g.y != 0.0) {
                    g.reset()
                }
                if (!(amp.value > 0f || lift.isRunning || morphT.isRunning || morph.to.shake != 0.0 ||
                        morph.to.rock != 0.0 || gazeState.value != null)
                ) {
                    break
                }
            }
        }
    }

    val pointerModifier = Modifier
        .onGloballyPositioned {
            origin = it.centerInWindow()
            widthPx = max(1f, it.size.width.toFloat())
        }
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val e = awaitPointerEvent(PointerEventPass.Initial)
                    val c = e.changes.firstOrNull() ?: continue
                    val topLeft = origin - Offset(size.width / 2f, size.height / 2f)
                    if (c.type == PointerType.Touch || c.type == PointerType.Stylus) {
                        pressed = c.pressed && e.type != PointerEventType.Exit
                        localPointer = if (pressed) topLeft + c.position else null
                    } else {
                        sawMouse = true
                        when (e.type) {
                            PointerEventType.Enter, PointerEventType.Move -> {
                                hovered = true
                                localPointer = topLeft + c.position
                            }
                            PointerEventType.Exit -> {
                                hovered = false
                                localPointer = null
                            }
                            else -> Unit
                        }
                    }
                }
            }
        }

    Canvas(modifier.then(pointerModifier)) {
        @Suppress("UNUSED_EXPRESSION") time // read to redraw per frame
        val ease = morph.ease(morphT.value)
        val pose = Pose.lerp(morph.from, morph.to, ease)
        val head = lerpSrgb(morph.fromHead, morph.toHead, ease.toFloat())
        val eye = lerpSrgb(morph.fromEye, morph.toEye, ease.toFloat())
        val a = amp.value.toDouble()
        val hold = g.h
        val seeds = geo.seeds.let {
            it.copy(lookX = it.lookX * (1 - hold), lookY = it.lookY * (1 - hold), lookMX = it.lookMX * (1 - hold), lookMY = it.lookMY * (1 - hold))
        }
        // Upstream pauses the root's loops on touch-only devices unless the face is
        // animating; a face that has seen a mouse behaves like a desktop one.
        val shakeRuns = !reduced && (animate == BlobfaceAnimation.Always || hovered || pressed || sawMouse)
        val f = if (reduced) idleAt(seeds, 0.0, 0.0, 0.0) else idleAt(seeds, time.toDouble(), a, if (shakeRuns) pose.shake else 0.0)
        drawAnimated(geo, pose, f, head, eye, lift.value, g, reduced)
    }
}

private fun DrawScope.drawAnimated(
    geo: AnimatedGeometry,
    p: Pose,
    f: io.github.pradyumnadp.blobface.IdleFrame,
    head: Color,
    eye: Color,
    lift: Float,
    g: GazeRuntime,
    reduced: Boolean,
) {
    val k = min(size.width, size.height) / 100f
    scale(k, k, pivot = Offset.Zero) {
        // The plate sits outside the motion root: it does not lift or breathe.
        geo.backdrop?.let { drawPath(it, geo.bg) }
        val c = Offset(50f, 50f)
        withTransform({
            // .mo-root: `translate` (shake), then `transform` (hover lift) about the center.
            translate(f.shakeX.toFloat(), f.shakeY.toFloat())
            translate(0f, -1.5f * lift)
            scale(1f + 0.04f * lift, 1f + 0.04f * lift, pivot = c)
        }) {
            // .mo-breathe
            scale(f.breatheX.toFloat(), f.breatheY.toFloat(), pivot = c) {
                // .mo-bob: translate(0, bdy) then translateY(bob)
                translate(0f, (p.bdy + f.bob).toFloat()) {
                    for ((ctr, r, _) in geo.petals) drawCircle(head, r, ctr)
                    for (x in geo.extra) drawPath(x, head)
                    drawPath(geo.body, head)
                    // .mo-eyes: the glance
                    translate(f.saccadeX.toFloat(), f.saccadeY.toFloat()) {
                        geo.eyes.forEachIndexed { i, e -> drawEye(i, e, p, f, eye, g, reduced) }
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawEye(
    i: Int,
    e: EyeGeo,
    p: Pose,
    f: io.github.pradyumnadp.blobface.IdleFrame,
    color: Color,
    g: GazeRuntime,
    reduced: Boolean,
) {
    val wrap = if (i != 0) 1.0 else -1.0
    val sel = if (i != 0) 1.0 else 0.0
    val x = p.esx + p.esx2 * sel
    val y = p.esy + p.esy2 * sel
    val t = p.tilt + p.tilt2 * sel
    val rockp = if (reduced || p.rock == 0.0) 1.0 else f.rockp
    val ph = sel * (1 - p.rock) + p.rock * ((1 + wrap * rockp) / 2)
    val gz = g.eye(i)
    val o = Offset(e.cx, e.cy)
    // .mo-eye: translate, rotate, then transform = rotate(lean) scale rotate(-lean), about the eye center.
    withTransform({
        translate((p.edx * wrap + gz.dx).toFloat(), (p.edy + p.edy2 * ph + gz.dy).toFloat())
        rotate((t * wrap - e.lean * p.lock + gz.t).toFloat(), pivot = o)
        rotate(e.lean, pivot = o)
        scale((x * gz.sx).toFloat(), (y * gz.sy).toFloat(), pivot = o)
        rotate(-e.lean, pivot = o)
        // .mo-eye > path: mo-wrap (rotate, scale) then mo-blink (transform) about its own box center.
        rotate((f.wrapRot * wrap).toFloat(), pivot = o)
        scale((1 + f.wrapMx + f.wrapSide * wrap).toFloat(), (1 + f.wrapSy).toFloat(), pivot = o)
        rotate(e.lean, pivot = o)
        scale(1f, f.blink.toFloat(), pivot = o)
        rotate(-e.lean, pivot = o)
    }) {
        drawPath(e.path, color)
    }
}

/** Per-eye gaze offsets in viewBox units. */
private data class EyeGaze(val dx: Double = 0.0, val dy: Double = 0.0, val sx: Double = 1.0, val sy: Double = 1.0, val t: Double = 0.0)

/** The gaze driver's per-face state, stepped once per frame (upstream `gaze()`'s `frame`). */
private class GazeRuntime {
    var x = 0.0
    var y = 0.0
    var h = 0.0
    private var last = 0L
    private var eyes: List<EyeGaze> = emptyList()

    fun eye(i: Int): EyeGaze = eyes.getOrNull(i) ?: EyeGaze()

    fun reset() {
        x = 0.0; y = 0.0; h = 0.0; last = 0L; eyes = emptyList()
    }

    fun update(state: BlobfaceGazeState, face: GazeMath.Face, center: Offset, width: Float, pointer: Offset?, @Suppress("UNUSED_PARAMETER") pressed: Boolean) {
        val now = monoMillis()
        val dt = if (last != 0L) min((now - last).toDouble(), 64.0) else 16.0
        last = now
        val tgt = state.target
        val resting = tgt is GazeTarget.None || tgt is GazeTarget.Rest
        val hold = if (tgt is GazeTarget.None) 0.0 else 1.0
        val aim: Offset? = when (tgt) {
            is GazeTarget.Point -> tgt.position
            is GazeTarget.Anchor -> tgt.anchor.center
            GazeTarget.Pointer -> pointer
            else -> null
        }
        val far = -1e6
        val dx = if (resting) 0.0 else (aim?.x?.toDouble() ?: far) - center.x
        val dy = if (resting) 0.0 else (aim?.y?.toDouble() ?: far) - center.y
        val radius = max(1.0, width / 2.0)
        val k = GazeMath.pursuit(dt, state.settle)
        val s = GazeMath.step(x, y, dx, dy, radius, k, snap = state.snap)
        x = s.x
        y = s.y
        val travel = state.travel.toDouble()
        val eps = GazeMath.threshold(width.toDouble(), travel)
        if (abs(s.tx - x) <= eps && abs(s.ty - y) <= eps) {
            x = s.tx; y = s.ty
        }
        h += (hold - h) * k
        if (abs(hold - h) <= GazeMath.HOLD_EPS) h = hold
        val yaw = travel / face.rx
        val pitch = travel / face.ry
        eyes = face.marks.map { m ->
            val pr = GazeMath.project(m, x * yaw, y * pitch)
            EyeGaze(pr.dx * face.rx, pr.dy * face.ry, pr.sx, pr.sy, pr.t)
        }
    }
}

private fun r2f(v: Double): Float = (kotlin.math.round(v * 100) / 100).toFloat()
